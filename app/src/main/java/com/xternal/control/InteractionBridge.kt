package com.xternal.control

import android.os.Handler
import android.os.Looper

object InteractionBridge {
    private val handler = Handler(Looper.getMainLooper())

    var cursorMoveListener: ((Float, Float) -> Unit)? = null
    var clickListener: (() -> Unit)? = null
    var rightClickListener: (() -> Unit)? = null
    var scrollListener: ((Float) -> Unit)? = null
    var textInputListener: ((String) -> Unit)? = null
    var appLaunchListener: ((String) -> Unit)? = null
    var zoomListener: ((Boolean) -> Unit)? = null
    var isKeyboardActive: Boolean = false

    fun sendCursorMove(dx: Float, dy: Float) {
        runOnMain { cursorMoveListener?.invoke(dx, dy) }
    }

    fun sendClick() {
        runOnMain { clickListener?.invoke() }
    }

    fun sendRightClick() {
        runOnMain { rightClickListener?.invoke() }
    }

    fun sendScroll(scrollDy: Float) {
        runOnMain { scrollListener?.invoke(scrollDy) }
    }

    fun sendTextInput(text: String) {
        runOnMain { textInputListener?.invoke(text) }
    }

    fun sendAppLaunch(packageName: String) {
        runOnMain { appLaunchListener?.invoke(packageName) }
    }

    fun sendZoom(isZoomIn: Boolean) {
        runOnMain { zoomListener?.invoke(isZoomIn) }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }
}
