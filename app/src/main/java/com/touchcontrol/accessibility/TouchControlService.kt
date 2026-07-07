package com.touchcontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import android.media.AudioManager
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
    }

    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f

    /** 鼠标光标悬浮层 */
    private var cursorOverlay: CursorOverlayManager? = null
    private var cursorVisible = true

    /** 震动反馈 */
    private fun vibrateClick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(20L, 255)
                )
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(20L)
            }
        } catch (_: Exception) {}
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "TouchControl 服务已连接 (onServiceConnected)")

        // 初始化光标悬浮层
        cursorOverlay = CursorOverlayManager(this)
        if (cursorVisible) {
            cursorOverlay?.show()
        }

        // 安全获取屏幕尺寸（Android 16 上 this.display 可能不可用）
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val disp = display
                if (disp != null) {
                    val metrics = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION")
                    disp.getRealMetrics(metrics)
                    screenWidth = metrics.widthPixels.toFloat()
                    screenHeight = metrics.heightPixels.toFloat()
                    cursorX = 0.5f
                    cursorY = 0.5f
                    Log.i(TAG, "屏幕尺寸: ${screenWidth.toInt()}x${screenHeight.toInt()}")
                    return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "通过 display 获取屏幕尺寸失败: ${e.message}")
        }

        // Fallback: 使用 WindowManager
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null) {
                @Suppress("DEPRECATION")
                val dm = android.util.DisplayMetrics().also {
                    wm.defaultDisplay.getRealMetrics(it)
                }
                screenWidth = dm.widthPixels.toFloat()
                screenHeight = dm.heightPixels.toFloat()
                cursorX = 0.5f
                cursorY = 0.5f
                Log.i(TAG, "屏幕尺寸(WM): ${screenWidth.toInt()}x${screenHeight.toInt()}")
            } else {
                Log.w(TAG, "WindowManager 不可用，使用默认尺寸")
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取屏幕尺寸失败: ${e.message}")
        }
    }

    /**
     * 显式启动时也初始化 instance（ColorOS/Android 16 兼容）
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (instance == null) {
            instance = this
            Log.i(TAG, "TouchControl 服务已连接 (onStartCommand)")
            // 初始化光标
            cursorOverlay = CursorOverlayManager(this)
            if (cursorVisible) {
                cursorOverlay?.show()
            }
            // 获取屏幕尺寸
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (wm != null) {
                    val dm = android.util.DisplayMetrics().also {
                        wm.defaultDisplay.getRealMetrics(it)
                    }
                    screenWidth = dm.widthPixels.toFloat()
                    screenHeight = dm.heightPixels.toFloat()
                    cursorX = 0.5f
                    cursorY = 0.5f
                }
            } catch (_: Exception) {}
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // 隐藏光标悬浮层
        cursorOverlay?.hide()
        cursorOverlay = null
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
                "touch" -> handleTouch(msg, action)
                "mouse" -> handleMouse(msg, action)
                "key"   -> handleKey(msg, action)
                "system" -> handleSystem(msg, action)
            }
        } catch (e: Exception) {
            Log.e(TAG, "指令解析失败: $jsonString", e)
        }
    }

    /** 连续触摸状态 */
    private var isTouchActive = false
    private var touchX = 0f
    private var touchY = 0f

    /**
     * 触摸板模式：用 willContinue StrokeDescription 实现连续触摸
     */
    private fun handleTouch(msg: JSONObject, action: String) {
        when (action) {
            "down" -> {
                // 手指按下 → 从传过来的位置开始触摸
                isTouchActive = true
                val pctX = msg.optDouble("pctX", 0.5).toFloat().coerceIn(0f, 1f)
                val pctY = msg.optDouble("pctY", 0.5).toFloat().coerceIn(0f, 1f)
                cursorX = pctX
                cursorY = pctY
                val sx = pctX * screenWidth
                val sy = pctY * screenHeight
                touchX = sx
                touchY = sy
                refreshCursorPosition()
                val path = Path().apply { moveTo(sx, sy) }
                val stroke = GestureDescription.StrokeDescription(
                    path, 0, 2000L, true  // willContinue=true
                )
                val gesture = GestureDescription.Builder()
                    .addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
                Log.i(TAG, "触摸按下 ($sx, $sy) 百分比 ($pctX, $pctY)")
            }
            "move" -> {
                if (!isTouchActive) return
                // 读取绝对百分比坐标
                val pctX = msg.optDouble("pctX", -1.0).toFloat()
                val pctY = msg.optDouble("pctY", -1.0).toFloat()
                if (pctX < 0f || pctY < 0f) return
                // 直接使用绝对百分比坐标（触摸板与平板屏幕等比映射）
                cursorX = pctX.coerceIn(0f, 1f)
                cursorY = pctY.coerceIn(0f, 1f)
                refreshCursorPosition()

                val screenX = cursorX * screenWidth
                val screenY = cursorY * screenHeight

                // 注入连续移动 stroke（从上一个触摸位置到当前位置）
                val path = Path().apply {
                    moveTo(touchX, touchY)
                    lineTo(screenX, screenY)
                }
                touchX = screenX
                touchY = screenY
                val stroke = GestureDescription.StrokeDescription(
                    path, 0, 60L, true  // willContinue=true
                )
                val gesture = GestureDescription.Builder()
                    .addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
            }
            "up" -> {
                if (!isTouchActive) return
                // 手指抬起 → 最终 stroke（不继续）
                val path = Path().apply {
                    moveTo(touchX, touchY)
                    lineTo(cursorX * screenWidth, cursorY * screenHeight)
                }
                val stroke = GestureDescription.StrokeDescription(
                    path, 0, 50L, false  // willContinue=false → 触摸结束
                )
                val gesture = GestureDescription.Builder()
                    .addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
                isTouchActive = false
                refreshCursorPosition()
                Log.i(TAG, "触摸抬起")
            }
        }
    }

    private fun handleMouse(msg: JSONObject, action: String) {
        when (action) {
            "cursor" -> {
                // 绝对定位光标（不注入触摸）
                val x = msg.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f)
                val y = msg.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)
                cursorX = x
                cursorY = y
                refreshCursorPosition()
                Log.i(TAG, "光标定位: ($x, $y)")
            }
            "move" -> {
                val dx = msg.optDouble("dx", 0.0).toFloat()
                val dy = msg.optDouble("dy", 0.0).toFloat()

                if (isDragging) {
                    val newX = (cursorX + dx / screenWidth).coerceIn(0f, 1f)
                    val newY = (cursorY + dy / screenHeight).coerceIn(0f, 1f)
                    cursorX = newX
                    cursorY = newY
                    refreshCursorPosition()
                    // 从上次拖拽位置到当前位置连续拖动
                    performDrag(dragStartX, dragStartY, cursorX, cursorY)
                    dragStartX = cursorX
                    dragStartY = cursorY
                } else {
                    // 纯鼠标模式：滑动只移动光标，不注入触摸
                    val newX = (cursorX + dx / screenWidth).coerceIn(0f, 1f)
                    val newY = (cursorY + dy / screenHeight).coerceIn(0f, 1f)
                    cursorX = newX
                    cursorY = newY
                    refreshCursorPosition()
                }
            }
            "click" -> {
                val button = msg.optString("button", "left")
                if (button == "right") {
                    performLongClick()
                } else {
                    performTap()
                }
                // 点击后刷新光标（可能拖动后点击）
                refreshCursorPosition()
            }
            "down" -> {
                isDragging = true
                dragStartX = cursorX
                dragStartY = cursorY
                performLongClick()
                refreshCursorPosition()
            }
            "up" -> {
                isDragging = false
                refreshCursorPosition()
            }
            "scroll" -> {
                val dy = msg.optDouble("dy", 0.0).toFloat()
                val dx = msg.optDouble("dx", 0.0).toFloat()
                performScroll(dy, dx)
                refreshCursorPosition()
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
                    "volume_up"     -> { val am = getSystemService(AUDIO_SERVICE) as AudioManager; am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
                    "volume_down"   -> { val am = getSystemService(AUDIO_SERVICE) as AudioManager; am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
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
     * 单指点击当前光标位置（可靠点击）
     *
     * 使用纯静态按压（无位移），系统识别为触摸点击。
     * 时长 40ms 模拟快速点击。
     */
    private fun performTap() {
        val x = cursorX * screenWidth
        val y = cursorY * screenHeight

        // 纯静态按压 — 不移动，系统识别为点击
        val path = Path().apply { moveTo(x, y) }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                path, 0, 40L  // 40ms = 快速点击
            ))
            .build()

        Log.i(TAG, "点击: (${cursorX},${cursorY}) → 屏幕 ($x, $y) dur=40ms")
        vibrateClick()
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
     * 比例滚动
     *
     * dy/dx 是用户手指在触摸板上移动的物理像素距离。
     * 映射为平板上对应的滚动距离，方向跟随手指。
     *
     * @param dy 触摸板上的垂直移动距离（像素，正=下）
     * @param dx 触摸板上的水平移动距离（像素，正=右）
     */
    private fun performScroll(dy: Float, dx: Float) {
        val cx = cursorX * screenWidth
        val cy = cursorY * screenHeight

        // 直接用手指移动距离（px）（限制最大 600px 防暴走）
        val maxScroll = 600f
        val scrollX = dx.coerceIn(-maxScroll, maxScroll)
        val scrollY = dy.coerceIn(-maxScroll, maxScroll)

        // 滚动持续时间：随距离增大（最短 40ms，最长 300ms）
        val scrollDist = kotlin.math.sqrt(scrollX * scrollX + scrollY * scrollY)
        val duration = (scrollDist * 0.5f + 40f).toLong().coerceIn(40L, 300L)

        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + scrollX, cy + scrollY)
        }

        Log.i(TAG, "滚动: dx=$scrollX dy=$scrollY duration=${duration}ms")
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                path, 0, duration
            ))
            .build()

        dispatchGestureSafely(gesture)
        refreshCursorPosition()
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

    /**
     * 刷新鼠标光标悬浮层位置
     */
    private fun refreshCursorPosition() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            cursorOverlay?.updatePosition(cursorX, cursorY)
        }
    }
}
