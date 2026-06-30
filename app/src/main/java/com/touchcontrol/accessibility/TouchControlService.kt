package com.touchcontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.json.JSONObject

/**
 * 触摸控制 AccessibilityService
 *
 * 平板端：接收 WebSocket 服务端解析后的指令，
 * 通过 [dispatchGesture] 模拟触摸/手势操作。
 */
class TouchControlService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchService"
        var instance: TouchControlService? = null

        /** 虚拟光标位置（屏幕坐标百分比 0.0~1.0） */
        var cursorX = 0.5f
        var cursorY = 0.5f

        /** 屏幕尺寸（由服务连接时获取） */
        var screenWidth = 1080f
        var screenHeight = 1920f

        private const val CLICK_DURATION_MS = 40L
        private const val LONG_PRESS_MS = 400L
        private const val SCROLL_STEP = 80f
    }

    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "TouchControl 服务已连接")

        // 获取屏幕尺寸
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

        if (display != null) {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels.toFloat()
            screenHeight = metrics.heightPixels.toFloat()
            cursorX = 0.5f
            cursorY = 0.5f
            Log.i(TAG, "屏幕尺寸: ${screenWidth.toInt()}x${screenHeight.toInt()}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "TouchControl 服务已销毁")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ── 指令执行 ──────────────────────────────────────

    /**
     * 执行从手机发来的 JSON 指令
     */
    fun executeCommand(jsonString: String) {
        try {
            val msg = JSONObject(jsonString)
            val type = msg.optString("type", "")
            val action = msg.optString("action", "")

            when (type) {
                "mouse" -> handleMouse(msg, action)
                "key"   -> handleKey(msg, action)
                "system" -> handleSystem(msg, action)
            }
        } catch (e: Exception) {
            Log.e(TAG, "指令解析失败: $jsonString", e)
        }
    }

    private fun handleMouse(msg: JSONObject, action: String) {
        when (action) {
            "move" -> {
                val dx = msg.optDouble("dx", 0.0).toFloat()
                val dy = msg.optDouble("dy", 0.0).toFloat()

                if (isDragging) {
                    // 拖拽中 → 移动后发送新的 drag 路径
                    val newX = (cursorX + dx / screenWidth).coerceIn(0f, 1f)
                    val newY = (cursorY + dy / screenHeight).coerceIn(0f, 1f)
                    cursorX = newX
                    cursorY = newY
                    // dispatchGesture 不支持拖拽中分段移动，用较长的 stroke 模拟
                    performDrag(dragStartX, dragStartY, cursorX, cursorY)
                } else {
                    // 普通移动
                    val newX = (cursorX + dx / screenWidth).coerceIn(0f, 1f)
                    val newY = (cursorY + dy / screenHeight).coerceIn(0f, 1f)
                    cursorX = newX
                    cursorY = newY
                }
            }
            "click" -> {
                val button = msg.optString("button", "left")
                if (button == "right") {
                    performLongClick()
                } else {
                    performTap()
                }
            }
            "down" -> {
                // 按下鼠标（拖拽开始）
                isDragging = true
                dragStartX = cursorX
                dragStartY = cursorY
                // 发送一个长按点（开始拖拽信号的"锚点"）
                performLongClick()
            }
            "up" -> {
                // 松开鼠标（拖拽结束）
                isDragging = false
            }
            "scroll" -> {
                val dy = msg.optDouble("dy", 0.0).toFloat()
                val dx = msg.optDouble("dx", 0.0).toFloat()
                performScroll(dy, dx)
            }
        }
    }

    private fun handleKey(msg: JSONObject, action: String) {
        val key = msg.optString("key", "")
        val modifiers = msg.optJSONArray("modifiers")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        } ?: emptyList()

        when (action) {
            "tap" -> {
                when (key.lowercase()) {
                    "escape"        -> performGlobalAction(GLOBAL_ACTION_BACK)
                    "backspace"     -> performGlobalAction(GLOBAL_ACTION_BACK) // 当作返回
                    "home"          -> performGlobalAction(GLOBAL_ACTION_HOME)
                    "recents"       -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                    "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    "quick_settings"-> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                    "enter"         -> performGlobalAction(GLOBAL_ACTION_BACK) // 去掉，用 tap
                    "volume_up"     -> performGlobalAction(GLOBAL_ACTION_VOLUME_UP)
                    "volume_down"   -> performGlobalAction(GLOBAL_ACTION_VOLUME_DOWN)
                    "power_dialog"  -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                    "split_screen"  -> performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                    "show_desktop"  -> performGlobalAction(GLOBAL_ACTION_HOME)
                    else -> {
                        // 普通文本键、修饰键组合等 → 用文字输入
                        // 对 AccessibilityService，直接输入字符需要 IME 能力
                        // 简单方案：忽略修饰键，对可打印字符尝试输入
                        if (modifiers.isEmpty() && key.length == 1 && key[0].isLetterOrDigit()) {
                            // 单个字符 → 可以执行粘贴输入（需要剪贴板辅助）
                            val clip = android.content.ClipboardManager::class.java
                            // 简化为发送文字到剪贴板再粘贴
                        }
                    }
                }
            }
            "type" -> {
                val text = msg.optString("text", "")
                typeText(text)
            }
        }
    }

    private fun handleSystem(msg: JSONObject, action: String) {
        when (action) {
            "ping" -> {
                // Pong 由服务端自己处理
            }
        }
    }

    // ── 手势执行 ──────────────────────────────────────

    /**
     * 单指点击当前光标位置
     */
    private fun performTap() {
        val x = cursorX * screenWidth
        val y = cursorY * screenHeight
        val path = Path().apply { moveTo(x, y) }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                path, 0, CLICK_DURATION_MS
            ))
            .build()

        dispatchGestureSafely(gesture)
    }

    /**
     * 长按（右键模拟）
     */
    private fun performLongClick() {
        val x = cursorX * screenWidth
        val y = cursorY * screenHeight
        val path = Path().apply { moveTo(x, y) }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                path, 0, LONG_PRESS_MS
            ))
            .build()

        dispatchGestureSafely(gesture)
    }

    /**
     * 拖拽路径
     */
    private fun performDrag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val sx = fromX * screenWidth
        val sy = fromY * screenHeight
        val ex = toX * screenWidth
        val ey = toY * screenHeight

        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                path, 0, 300L  // 拖拽持续时间
            ))
            .build()

        dispatchGestureSafely(gesture)
    }

    /**
     * 滑动滚动
     */
    private fun performScroll(dy: Float, dx: Float) {
        val cx = cursorX * screenWidth
        val cy = cursorY * screenHeight
        val scrollDistance = SCROLL_STEP.coerceIn(20f, 200f)

        // 滚动方向：dy > 0 手指上滑（往下滚）；dy < 0 手指下滑（往上滚）
        val scrollY = if (dy > 0) -scrollDistance else scrollDistance
        val scrollX = if (dx > 0) -scrollDistance else scrollDistance

        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + scrollX, cy + scrollY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                path, 0, 100L
            ))
            .build()

        dispatchGestureSafely(gesture)
    }

    /**
     * 输入文本（通过剪贴板 + 粘贴）
     */
    private fun typeText(text: String) {
        if (text.isEmpty()) return

        // 复制文本到剪贴板，提示用户手动粘贴（GLOBAL_ACTION_PASTE 在 SDK 中不存在）
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TouchControl", text))
        Log.i(TAG, "文本已复制到剪贴板: $text")
    }

    private fun dispatchGestureSafely(gesture: GestureDescription) {
        dispatchGesture(gesture, null, null)
    }
}
