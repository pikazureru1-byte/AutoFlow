package com.autoflow.app.core.script

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.autoflow.app.core.automation.AutomationService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

class ScriptExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ScriptExecutor"
    }

    private var currentJob: Job? = null
    private var isRunning = false
    private var isPaused = false

    private val _executionState = MutableStateFlow(ExecutionState())
    val executionState: StateFlow<ExecutionState> = _executionState

    private val templateCache = mutableMapOf<String, Bitmap>()

    // Listener for execution events
    var listener: ExecutionListener? = null

    interface ExecutionListener {
        fun onStepStart(step: ScriptStep, stepIndex: Int)
        fun onStepComplete(step: ScriptStep, stepIndex: Int)
        fun onError(error: String, stepIndex: Int)
        fun onComplete()
        fun onLog(message: String)
    }

    fun execute(script: AutomationScript) {
        if (isRunning) {
            Log.w(TAG, "Script already running")
            return
        }

        isRunning = true
        _executionState.value = ExecutionState(isRunning = true)

        currentJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                Log.d(TAG, "Starting script: ${script.name}")
                listener?.onLog("Starting script: ${script.name}")

                executeSteps(script.steps, 0)

                withContext(Dispatchers.Main) {
                    _executionState.value = _executionState.value.copy(
                        isRunning = false,
                        currentStep = script.steps.size
                    )
                    isRunning = false
                    listener?.onComplete()
                    Log.d(TAG, "Script completed successfully")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Script execution cancelled")
                listener?.onLog("Script stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Script execution failed", e)
                _executionState.value = _executionState.value.copy(
                    isRunning = false,
                    errorMessage = e.message
                )
                listener?.onError(e.message ?: "Unknown error", _executionState.value.currentStep)
            }
        }
    }

    private suspend fun executeSteps(steps: List<ScriptStep>, startIndex: Int) {
        for ((index, step) in steps.withIndex()) {
            if (!isRunning) break

            // Check pause state
            while (isPaused) {
                delay(100)
                if (!isRunning) break
            }

            val stepIndex = startIndex + index

            withContext(Dispatchers.Main) {
                _executionState.value = _executionState.value.copy(currentStep = stepIndex)
            }

            Log.d(TAG, "Executing step $stepIndex: ${step.description}")
            listener?.onStepStart(step, stepIndex)
            listener?.onLog("Step ${stepIndex + 1}: ${step.description}")

            try {
                val result = executeStep(step)

                when (result) {
                    is ExecutionResult.Success -> {
                        listener?.onLog("  ✓ Success: ${result.message}")
                    }
                    is ExecutionResult.Failure -> {
                        listener?.onError(result.message, result.step)
                        throw Exception(result.message)
                    }
                    is ExecutionResult.Timeout -> {
                        listener?.onError("Timeout: ${result.message}", result.step)
                        throw Exception("Timeout at step ${result.step}: ${result.message}")
                    }
                    ExecutionResult.Stopped -> {
                        return
                    }
                }

                listener?.onStepComplete(step, stepIndex)

            } catch (e: Exception) {
                Log.e(TAG, "Step $stepIndex failed: ${e.message}")
                throw e
            }
        }
    }

    private suspend fun executeStep(step: ScriptStep): ExecutionResult {
        val automationService = AutomationService.instance

        return when (step) {
            is FindAndClickStep -> {
                executeFindAndClick(step, automationService)
            }
            is FindAndSwipeStep -> {
                executeFindAndSwipe(step, automationService)
            }
            is WaitStep -> {
                delay(step.duration)
                ExecutionResult.Success("Waited ${step.duration}ms")
            }
            is FindAndWaitStep -> {
                executeFindAndWait(step, automationService)
            }
            is ConditionStep -> {
                executeCondition(step, automationService)
            }
            is LoopStep -> {
                executeLoop(step)
            }
            is SwipeSliderStep -> {
                executeSwipeSlider(step, automationService)
            }
        }
    }

    private suspend fun executeFindAndClick(
        step: FindAndClickStep,
        service: AutomationService?
    ): ExecutionResult {
        if (service == null) return ExecutionResult.Failure("Automation service not available", 0)

        val template = loadTemplate(step.templateName)
            ?: return ExecutionResult.Failure("Template not found: ${step.templateName}", 0)

        for (attempt in 1..step.retryCount) {
            val screenBitmap = service.captureScreen() ?: return ExecutionResult.Failure("Failed to capture screen", 0)

            val matcher = com.autoflow.app.core.image.ImageMatcher()
            val matchPoint = matcher.findTemplate(screenBitmap, template)

            if (matchPoint != null) {
                val success = service.performClick(matchPoint.x, matchPoint.y)
                if (success) {
                    return ExecutionResult.Success("Clicked at (${matchPoint.x}, ${matchPoint.y})")
                }
            }

            listener?.onLog("  Attempt $attempt/${step.retryCount} failed, retrying...")
            delay(step.retryInterval)
        }

        return ExecutionResult.Timeout("Template not found after ${step.retryCount} attempts", 0)
    }

    private suspend fun executeFindAndSwipe(
        step: FindAndSwipeStep,
        service: AutomationService?
    ): ExecutionResult {
        if (service == null) return ExecutionResult.Failure("Automation service not available", 0)

        val template = loadTemplate(step.templateName)
            ?: return ExecutionResult.Failure("Template not found: ${step.templateName}", 0)

        val screenBitmap = service.captureScreen() ?: return ExecutionResult.Failure("Failed to capture screen", 0)

        val matcher = com.autoflow.app.core.image.ImageMatcher()
        val matchPoint = matcher.findTemplate(screenBitmap, template)
            ?: return ExecutionResult.Failure("Template not found: ${step.templateName}", 0)

        val (startX, startY, endX, endY) = when (step.direction) {
            SwipeDirection.UP -> listOf(
                matchPoint.x, matchPoint.y,
                matchPoint.x, matchPoint.y - step.swipeDistance
            )
            SwipeDirection.DOWN -> listOf(
                matchPoint.x, matchPoint.y,
                matchPoint.x, matchPoint.y + step.swipeDistance
            )
            SwipeDirection.LEFT -> listOf(
                matchPoint.x, matchPoint.y,
                matchPoint.x - step.swipeDistance, matchPoint.y
            )
            SwipeDirection.RIGHT -> listOf(
                matchPoint.x, matchPoint.y,
                matchPoint.x + step.swipeDistance, matchPoint.y
            )
        }

        val success = service.performSwipe(startX, startY, endX, endY, step.duration)
        return if (success) {
            ExecutionResult.Success("Swiped from ($startX, $startY) to ($endX, $endY)")
        } else {
            ExecutionResult.Failure("Swipe failed", 0)
        }
    }

    private suspend fun executeFindAndWait(
        step: FindAndWaitStep,
        service: AutomationService?
    ): ExecutionResult {
        if (service == null) return ExecutionResult.Failure("Automation service not available", 0)

        val template = loadTemplate(step.templateName)
            ?: return ExecutionResult.Failure("Template not found: ${step.templateName}", 0)

        val startTime = System.currentTimeMillis()
        val matcher = com.autoflow.app.core.image.ImageMatcher()

        while (System.currentTimeMillis() - startTime < step.timeout) {
            val screenBitmap = service.captureScreen()
            if (screenBitmap != null) {
                val matchPoint = matcher.findTemplate(screenBitmap, template)
                if (matchPoint != null) {
                    return ExecutionResult.Success("Found at (${matchPoint.x}, ${matchPoint.y})")
                }
            }
            delay(500)
        }

        return ExecutionResult.Timeout("Template not found within ${step.timeout}ms", 0)
    }

    private suspend fun executeCondition(
        step: ConditionStep,
        service: AutomationService?
    ): ExecutionResult {
        if (service == null) return ExecutionResult.Failure("Automation service not available", 0)

        val template = loadTemplate(step.templateName)
            ?: return ExecutionResult.Failure("Template not found: ${step.templateName}", 0)

        val startTime = System.currentTimeMillis()
        val matcher = com.autoflow.app.core.image.ImageMatcher()

        while (System.currentTimeMillis() - startTime < step.timeout) {
            val screenBitmap = service.captureScreen()
            if (screenBitmap != null) {
                val found = matcher.findTemplate(screenBitmap, template, 0.7f) != null
                if (found == step.expectedFound) {
                    return ExecutionResult.Success(
                        if (step.expectedFound) "Condition met: template found"
                        else "Condition met: template not found"
                    )
                }
            }
            delay(300)
        }

        return ExecutionResult.Timeout("Condition check timeout", 0)
    }

    private suspend fun executeLoop(step: LoopStep): ExecutionResult {
        for (i in 1..step.count) {
            if (!isRunning) return ExecutionResult.Stopped

            listener?.onLog("Loop $i/${step.count}")
            executeSteps(step.steps, 0)
        }
        return ExecutionResult.Success("Loop completed ${step.count} times")
    }

    private suspend fun executeSwipeSlider(
        step: SwipeSliderStep,
        service: AutomationService?
    ): ExecutionResult {
        if (service == null) return ExecutionResult.Failure("Automation service not available", 0)

        val template = loadTemplate(step.templateName)
            ?: return ExecutionResult.Failure("Template not found: ${step.templateName}", 0)

        val screenBitmap = service.captureScreen() ?: return ExecutionResult.Failure("Failed to capture screen", 0)

        val matcher = com.autoflow.app.core.image.ImageMatcher()
        val matchPoint = matcher.findTemplate(screenBitmap, template)
            ?: return ExecutionResult.Failure("Slider not found: ${step.templateName}", 0)

        // Estimate slider width (assuming ~200px for typical slider)
        val sliderWidth = 200f
        val startX = matchPoint.x - sliderWidth / 2 + (sliderWidth * step.startPercent)
        val endX = matchPoint.x - sliderWidth / 2 + (sliderWidth * step.endPercent)
        val y = matchPoint.y

        val success = service.performSwipe(startX, y, endX, y, step.duration)
        return if (success) {
            ExecutionResult.Success("Slid from $startX to $endX")
        } else {
            ExecutionResult.Failure("Slider swipe failed", 0)
        }
    }

    private fun loadTemplate(name: String): Bitmap? {
        // Check cache first
        templateCache[name]?.let { return it }

        // Try to load from assets
        return try {
            val inputStream: InputStream = context.assets.open("templates/$name.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            templateCache[name] = bitmap
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Template not found in assets: $name")
            null
        }
    }

    fun stop() {
        isRunning = false
        currentJob?.cancel()
        _executionState.value = ExecutionState(isRunning = false)
        Log.d(TAG, "Script execution stopped")
    }

    fun pause() {
        isPaused = true
        _executionState.value = _executionState.value.copy(isPaused = true)
    }

    fun resume() {
        isPaused = false
        _executionState.value = _executionState.value.copy(isPaused = false)
    }
}
