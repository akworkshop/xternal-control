package com.xternal.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            InteractionBridge.sendForegroundPackageChanged(pkg)
        }
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

    fun captureDisplayScreenshot(displayId: Int, onComplete: (android.graphics.Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    displayId,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val hardwareBuffer = screenshot.hardwareBuffer
                                val colorSpace = screenshot.colorSpace
                                val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                val copy = bitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                hardwareBuffer.close()
                                onComplete(copy)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                onComplete(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            onComplete(null)
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(null)
            }
        } else {
            onComplete(null)
        }
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

    fun dispatchLongClick(displayId: Int, x: Float, y: Float): Boolean {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 800)
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
            val startRadius = 130f
            val endRadius = 30f
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
        val dispatched = try {
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
            true
        }
        if (!dispatched) {
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

    enum class MediaAction {
        PLAY_PAUSE,
        FAST_FORWARD,
        REWIND
    }

    fun dispatchDoubleClick(displayId: Int, x: Float, y: Float): Boolean {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke1 = GestureDescription.StrokeDescription(clickPath, 0, 40)
        val stroke2 = GestureDescription.StrokeDescription(clickPath, 120, 40)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke1)
            addStroke(stroke2)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayId != -1) {
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

    fun performMediaAction(action: MediaAction, displayId: Int, displayWidth: Int, displayHeight: Int): Boolean {
        // Step 1: Search for media action accessibility nodes in active windows
        val matchedNode = findMediaActionNode(action)
        if (matchedNode != null) {
            val clicked = matchedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) return true
        }

        // Step 2: Fallback to gesture injection on target display
        val targetWidth = if (displayWidth > 0) displayWidth.toFloat() else 1920f
        val targetHeight = if (displayHeight > 0) displayHeight.toFloat() else 1080f

        return when (action) {
            MediaAction.PLAY_PAUSE -> {
                // Tapping center of display toggles playback or reveals/triggers controls in video player
                dispatchClick(displayId, targetWidth * 0.5f, targetHeight * 0.5f)
            }
            MediaAction.FAST_FORWARD -> {
                // Double tap on right 25% of screen skips 10s forward in YouTube, Gallery, VLC, etc.
                dispatchDoubleClick(displayId, targetWidth * 0.75f, targetHeight * 0.5f)
            }
            MediaAction.REWIND -> {
                // Double tap on left 25% of screen skips 10s backward in YouTube, Gallery, VLC, etc.
                dispatchDoubleClick(displayId, targetWidth * 0.25f, targetHeight * 0.5f)
            }
        }
    }

    private fun findMediaActionNode(action: MediaAction): AccessibilityNodeInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                windows?.forEach { win ->
                    win.root?.let { roots.add(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        rootInActiveWindow?.let { if (!roots.contains(it)) roots.add(it) }

        val searchKeywords: List<String> = when (action) {
            MediaAction.PLAY_PAUSE -> listOf("play", "pause", "play_pause", "btn_play", "btn_pause", "exo_play", "exo_pause")
            MediaAction.FAST_FORWARD -> listOf("forward", "fast-forward", "seek forward", "skip forward", "ffwd", "exo_ffwd")
            MediaAction.REWIND -> listOf("rewind", "seek back", "skip back", "rew", "exo_rew")
        }

        for (root in roots) {
            val node = searchNodeByKeywords(root, searchKeywords)
            if (node != null) return node
        }
        return null
    }

    private fun searchNodeByKeywords(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isMatch = keywords.any { kw ->
            desc.contains(kw) || text.contains(kw) || viewId.contains(kw)
        }
        if (isMatch && node.isClickable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = searchNodeByKeywords(child, keywords)
            if (res != null) return res
        }
        return null
    }
}
