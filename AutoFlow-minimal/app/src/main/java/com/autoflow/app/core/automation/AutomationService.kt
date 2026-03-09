package com.autoflow.app.core.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.autoflow.app.core.script.ScriptExecutor
import com.autoflow.app.core.script.ScriptModels
import com.autoflow.app.core.image.ImageMatcher
import com.autoflow.app.ui.floating.FloatingControlActivity
import kotlinx.coroutines.*

class AutomationService : AccessibilityService() {

    companion object {
        private const val TAG = "AutomationService"
        var instance: AutomationService? = null
            private set

        var isRunning = false
            private set
    }

    private var scriptExecutor: ScriptExecutor? = null
    private var imageMatcher: ImageMatcher? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isCapturing = false
    private var imageReader: ImageReader? = null
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        imageMatcher = ImageMatcher()
        scriptExecutor = ScriptExecutor(this)
        Log.d(TAG, "AutomationService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d(TAG, "AutomationService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "AutomationService destroyed")
    }

    // Public API methods

    fun performClick(x: Float, y: Float): Boolean {
        return try {
            val gestureResult = dispatchGesture(
                android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK,
                null,
                null
            )
            // Alternative: use accessibility to find and click
            val success = findAndClickAtPosition(x, y)
            Log.d(TAG, "Click at ($x, $y): $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error performing click", e)
            false
        }
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        return try {
            val path = android.graphics.Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            val builder = android.view.MotionEvent.Builder(android.os.SystemClock.uptimeMillis())
            builder.setPath(path)
            builder.setEdgeFriction(0f)
            builder.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)

            // Simplified gesture dispatch
            val success = dispatchGenericMotionEvent(
                android.view.MotionEvent.obtain(
                    android.os.SystemClock.uptimeMillis(),
                    android.os.SystemClock.uptimeMillis() + duration,
                    android.view.MotionEvent.ACTION_MOVE,
                    startX + (endX - startX) * 0.5f,
                    startY + (endY - startY) * 0.5f,
                    1f
                )
            )

            Log.d(TAG, "Swipe from ($startX, $startY) to ($endX, $endY): $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error performing swipe", e)
            false
        }
    }

    private fun findAndClickAtPosition(x: Float, y: Float): Boolean {
        // Try to find a clickable node at the position
        val rootNode = rootInActiveWindow ?: return false

        try {
            val bounds = android.graphics.Rect()
            val iterator = rootNode.iterator()

            while (iterator.hasNext()) {
                val node = iterator.next()
                node.getBoundsInScreen(bounds)

                if (bounds.contains(x.toInt(), y.toInt())) {
                    if (node.isClickable) {
                        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    } else {
                        // Try to find parent that is clickable
                        var parent = node.parent
                        while (parent != null) {
                            if (parent.isClickable) {
                                parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                                return true
                            }
                            parent = parent.parent
                        }
                    }
                }
            }
        } finally {
            rootNode.recycle()
        }

        // Fallback: simulate touch via accessibility
        return try {
            val event = android.view.MotionEvent.obtain(
                android.os.SystemClock.uptimeMillis(),
                android.os.SystemClock.uptimeMillis(),
                android.view.MotionEvent.ACTION_DOWN,
                x, y, 1f
            )
            dispatchGenericMotionEvent(event)
            Thread.sleep(50)
            val eventUp = android.view.MotionEvent.obtain(
                android.os.SystemClock.uptimeMillis(),
                android.os.SystemClock.uptimeMillis(),
                android.view.MotionEvent.ACTION_UP,
                x, y, 1f
            )
            dispatchGenericMotionEvent(eventUp)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallback click failed", e)
            false
        }
    }

    fun captureScreen(): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = display ?: return null
                val bitmap = display.captureContent()
                bitmap
            } else {
                // Legacy approach
                val decorView = window?.decorView ?: return null
                decorView.isDrawingCacheEnabled = true
                val bitmap = Bitmap.createBitmap(decorView.drawingCache)
                decorView.isDrawingCacheEnabled = false
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen", e)
            null
        }
    }

    fun findImageOnScreen(templateBitmap: Bitmap, threshold: Float = 0.8f): android.graphics.PointF? {
        val screenBitmap = captureScreen() ?: return null
        return imageMatcher?.findTemplate(screenBitmap, templateBitmap, threshold)
    }

    fun executeScript(script: ScriptModels.AutomationScript) {
        serviceScope.launch {
            scriptExecutor?.execute(script)
        }
    }

    fun stopScript() {
        scriptExecutor?.stop()
    }

    fun isScriptRunning(): Boolean {
        return scriptExecutor?.isRunning == true
    }

    fun openFloatingControl() {
        val intent = Intent(this, FloatingControlActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
