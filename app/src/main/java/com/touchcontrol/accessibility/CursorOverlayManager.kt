package com.touchcontrol.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.touchcontrol.R

/**
 * 基于 cursor_17.png 的鼠标光标覆盖层
 *
 * 使用 drawable/cursor_arrow.png 作为光标图标，
 * 通过 WindowManager 叠加在屏幕上并跟随触摸位置移动。
 */
class CursorOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "CursorOverlay"
        /** 光标宽度（dp）—— 和 drawable-mdpi 尺寸一致 */
        private const val CURSOR_SIZE_DP = 21
        /** 热点偏移（dp）：箭头尖端在图片中的位置 */
        private const val HOT_X_DP = 2
        private const val HOT_Y_DP = 0
    }

    private var windowManager: WindowManager? = null
    private var cursorView: ImageView? = null
    private var isShowing = false

    fun show() {
        if (isShowing) return
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return.also { Log.w(TAG, "WindowManager 不可用") }

        val iv = ImageView(context).apply {
            setImageResource(R.drawable.cursor_arrow)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        cursorView = iv

        val dm = context.resources.displayMetrics
        val sizePx = (CURSOR_SIZE_DP * dm.density + 0.5f).toInt()

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }

        try {
            windowManager?.addView(iv, params)
            isShowing = true
            Log.i(TAG, "光标已显示 ${sizePx}x${sizePx}")
        } catch (e: Exception) {
            Log.e(TAG, "显示光标失败", e)
        }
    }

    fun hide() {
        if (!isShowing) return
        cursorView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        cursorView = null; isShowing = false
    }

    fun updatePosition(cursorX: Float, cursorY: Float) {
        if (!isShowing || cursorView == null || windowManager == null) return
        try {
            val dm = context.resources.displayMetrics
            val tx = (cursorX * dm.widthPixels).toInt()
            val ty = (cursorY * dm.heightPixels).toInt()

            val hotX = (HOT_X_DP * dm.density + 0.5f).toInt()
            val hotY = (HOT_Y_DP * dm.density + 0.5f).toInt()
            val px = tx - hotX
            val py = ty - hotY

            val p = cursorView?.layoutParams as? WindowManager.LayoutParams ?: return
            if (p.x != px || p.y != py) {
                p.x = px; p.y = py
                windowManager?.updateViewLayout(cursorView!!, p)
            }
        } catch (_: Exception) {}
    }

    fun isVisible(): Boolean = isShowing
}
