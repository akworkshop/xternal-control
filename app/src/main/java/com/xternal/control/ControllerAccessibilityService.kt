package com.xternal.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class ControllerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ControllerAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not tracking accessibility events, only injecting navigation/input actions
    }

    override fun onInterrupt() {
        // No-op
    }

    fun performBackAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performHomeAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performRecentsAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun dispatchClick(displayId: Int, x: Float, y: Float): Boolean {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDisplayId(displayId)
            }
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun dispatchScroll(displayId: Int, startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, 100)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDisplayId(displayId)
            }
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun dispatchZoom(displayId: Int, centerX: Float, centerY: Float, isZoomIn: Boolean): Boolean {
        val path1 = Path()
        val path2 = Path()

        val stroke1: GestureDescription.StrokeDescription
        val stroke2: GestureDescription.StrokeDescription

        if (isZoomIn) {
            val startRadius = 20f
            val endRadius = 220f
            // Pinch-open: Fingers move outwards from center
            path1.moveTo(centerX - startRadius, centerY)
            path1.lineTo(centerX - endRadius, centerY)

            path2.moveTo(centerX + startRadius, centerY)
            path2.lineTo(centerX + endRadius, centerY)

            stroke1 = GestureDescription.StrokeDescription(path1, 0, 250)
            stroke2 = GestureDescription.StrokeDescription(path2, 0, 250)
        } else {
            // Pinch-close: Fingers move inwards towards center (slow and larger range for Gallery, slow to prevent Maps momentum zoom out)
            val startRadius = 220f
            val endRadius = 20f
            path1.moveTo(centerX - startRadius, centerY)
            path1.lineTo(centerX - endRadius, centerY)

            path2.moveTo(centerX + startRadius, centerY)
            path2.lineTo(centerX + endRadius, centerY)

            stroke1 = GestureDescription.StrokeDescription(path1, 0, 450)
            stroke2 = GestureDescription.StrokeDescription(path2, 0, 450)
        }

        val builder = GestureDescription.Builder().apply {
            addStroke(stroke1)
            addStroke(stroke2)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDisplayId(displayId)
            }
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun performBackOnDisplay(displayId: Int, x: Float, y: Float) {
        // Shift focus first using a tiny 1-pixel gesture
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y + 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDisplayId(displayId)
            }
        }
        try {
            dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    fun performRecentsOnDisplay(displayId: Int, x: Float, y: Float) {
        // Shift focus first using a tiny 1-pixel gesture
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y + 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDisplayId(displayId)
            }
        }
        try {
            dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }
}
