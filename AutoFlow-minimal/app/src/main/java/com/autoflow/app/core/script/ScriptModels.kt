package com.autoflow.app.core.script

import android.graphics.Bitmap
import com.google.gson.annotations.SerializedName

/**
 * Script models for automation task definition
 */

data class AutomationScript(
    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String = "",

    @SerializedName("steps")
    val steps: List<ScriptStep>
)

sealed class ScriptStep {
    abstract val description: String
}

data class FindAndClickStep(
    @SerializedName("template_name")
    val templateName: String,

    @SerializedName("timeout")
    val timeout: Long = 5000,

    @SerializedName("retry_count")
    val retryCount: Int = 3,

    @SerializedName("retry_interval")
    val retryInterval: Long = 1000,

    @SerializedName("description")
    override val description: String
) : ScriptStep()

data class FindAndSwipeStep(
    @SerializedName("template_name")
    val templateName: String,

    @SerializedName("swipe_distance")
    val swipeDistance: Float = 500f,

    @SerializedName("duration")
    val duration: Long = 500L,

    @SerializedName("direction")
    val direction: SwipeDirection = SwipeDirection.UP,

    @SerializedName("timeout")
    val timeout: Long = 5000,

    @SerializedName("description")
    override val description: String
) : ScriptStep()

data class WaitStep(
    @SerializedName("duration")
    val duration: Long,

    @SerializedName("description")
    override val description: String
) : ScriptStep()

data class FindAndWaitStep(
    @SerializedName("template_name")
    val templateName: String,

    @SerializedName("timeout")
    val timeout: Long = 10000,

    @SerializedName("description")
    override val description: String
) : ScriptStep()

data class ConditionStep(
    @SerializedName("template_name")
    val templateName: String,

    @SerializedName("expected_found")
    val expectedFound: Boolean = true,

    @SerializedName("timeout")
    val timeout: Long = 5000,

    @SerializedName("description")
    override val description: String
) : ScriptStep()

data class LoopStep(
    @SerializedName("count")
    val count: Int,

    @SerializedName("steps")
    val steps: List<ScriptStep>,

    @SerializedName("description")
    override val description: String
) : ScriptStep()

data class SwipeSliderStep(
    @SerializedName("template_name")
    val templateName: String,

    @SerializedName("start_percent")
    val startPercent: Float = 0f,

    @SerializedName("end_percent")
    val endPercent: Float = 1f,

    @SerializedName("duration")
    val duration: Long = 1000L,

    @SerializedName("description")
    override val description: String
) : ScriptStep()

enum class SwipeDirection {
    UP, DOWN, LEFT, RIGHT
}

// Execution state
data class ExecutionState(
    val currentStep: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val errorMessage: String? = null,
    val logs: MutableList<String> = mutableListOf()
)

// Result classes
sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Failure(val message: String, val step: Int) : ExecutionResult()
    data class Timeout(val message: String, val step: Int) : ExecutionResult()
    object Stopped : ExecutionResult()
}
