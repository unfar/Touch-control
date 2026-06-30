package com.touchcontrol.gesture

import android.view.MotionEvent
import kotlin.math.sqrt

/**
 * 触摸板手势引擎
 *
 * 输入：Android MotionEvent 原始数据
 * 输出：手势动作回调（移动、点击、滚动等）
 *
 * 手势规则：
 *   1 指移动 → 鼠标移动
 *   1 指轻点（< 200ms, < 20px 位移）→ 左键单击
 *   1 指长按（> 500ms）→ 左键按住（拖拽模式）
 *   1 指双击 → 左键双击
 *   2 指上下滑 → 滚轮滚动
 *   2 指点击 → 右键单击
 *   2 指捏合 → 缩放（Ctrl + 滚轮）
 *   3 指上滑 → 显示桌面（Win+D / Cmd+F3）
 */
class TouchpadEngine(
    private val touchpadWidth: Int = 0,
    private val touchpadHeight: Int = 0,
    private val cursorSpeed: Float = 1f,
    private val scrollSpeed: Float = 1f,
) {

    // ── 事件回调 ──
    var onMouseMove: ((dx: Float, dy: Float) -> Unit)? = null
    var onMouseClick: ((button: String) -> Unit)? = null
    var onMouseDoubleClick: ((button: String) -> Unit)? = null
    var onMouseDown: ((button: String) -> Unit)? = null
    var onMouseUp: ((button: String) -> Unit)? = null
    var onScroll: ((dy: Float, dx: Float) -> Unit)? = null
    var onZoom: ((factor: Float) -> Unit)? = null
    var onGesture: ((gesture: String) -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    // ── 触摸状态跟踪 ──
    private val pointers = mutableMapOf<Int, PointerInfo>()
    private var lastClickTime = 0L
    private var lastClickX = 0f
    private var lastClickY = 0f
    private var isDragMode = false
    private var longPressSent = false
    private var longPressTimer: java.util.Timer? = null
    private var lastTwoFingerDistance = 0f

    private class PointerInfo(
        var id: Int,
        var downX: Float,
        var downY: Float,
        var prevX: Float,
        var prevY: Float,
        var currentX: Float,
        var currentY: Float,
        var downTime: Long,
    )

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handleDown(event)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                handlePointerDown(event)
            }

            MotionEvent.ACTION_MOVE -> {
                handleMove(event)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                handlePointerUp(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleUp(event)
            }
        }
    }

    /**
     * Compose 兼容的触摸数据处理入口
     * 接收来自 Compose awaitPointerEvent 的 PointerInputChange 数据
     *
     * @param activePointers 当前所有活动触控点的位置 [(pointerId, (x, y))]
     * @param actionType 事件类型: "down" (新按下), "move", "up" (抬起), "cancel"
     */
    fun onComposeTouchData(
        activePointers: List<Pair<Int, Pair<Float, Float>>>,
        actionType: String = "move",
    ) {
        when (actionType) {
            "cancel" -> {
                if (isDragMode) {
                    onMouseUp?.invoke("left")
                    isDragMode = false
                }
                pointers.clear()
                lastTwoFingerDistance = 0f
                cancelLongPress()
                return
            }
            "down" -> {
                cancelLongPress()
                val newIds = activePointers.map { it.first }.toSet()
                // 清除已经不存在的指针
                pointers.keys.removeAll { it !in newIds }
                // 添加新的指针
                for ((id, xy) in activePointers) {
                    if (id !in pointers) {
                        val (x, y) = xy
                        val time = System.currentTimeMillis()
                        pointers[id] = PointerInfo(id, x, y, x, y, x, y, time)

                        if (pointers.size == 1) {
                            isDragMode = false
                            longPressSent = false
                            scheduleLongPress(time, x, y)
                        }
                    }
                }
                // 记录双指初始距离
                if (pointers.size == 2) {
                    val p1 = pointers.values.first()
                    val p2 = pointers.values.last()
                    lastTwoFingerDistance = distance(p1.currentX, p1.currentY, p2.currentX, p2.currentY)
                }
                if (isDragMode) {
                    onMouseUp?.invoke("left")
                    isDragMode = false
                }
            }
            "up" -> {
                handleComposeUp(activePointers)
            }
            else -> { // move
                // 更新所有指针位置
                for ((id, xy) in activePointers) {
                    val (x, y) = xy
                    val pi = pointers[id] ?: continue
                    pi.prevX = pi.currentX
                    pi.prevY = pi.currentY
                    pi.currentX = x
                    pi.currentY = y
                }
                when (pointers.size) {
                    1 -> handleSingleFingerMove()
                    2 -> handleTwoFingerMove()
                }
            }
        }
    }

    private fun handleDown(event: MotionEvent) {
        val idx = event.actionIndex
        val id = event.getPointerId(idx)
        val x = event.getX(idx)
        val y = event.getY(idx)
        val time = System.currentTimeMillis()

        pointers.clear()
        pointers[id] = PointerInfo(id, x, y, x, y, x, y, time)

        isDragMode = false
        longPressSent = false

        // 启动长按定时器
        scheduleLongPress(time, x, y)
    }

    private fun handlePointerDown(event: MotionEvent) {
        // 第二根手指落下 → 取消长按
        cancelLongPress()

        val idx = event.actionIndex
        val id = event.getPointerId(idx)
        val x = event.getX(idx)
        val y = event.getY(idx)
        val time = System.currentTimeMillis()

        pointers[id] = PointerInfo(id, x, y, x, y, x, y, time)

        // 记录双指初始距离
        if (pointers.size == 2) {
            val p1 = pointers.values.first()
            val p2 = pointers.values.last()
            lastTwoFingerDistance = distance(p1.currentX, p1.currentY, p2.currentX, p2.currentY)
        }

        // 如果在拖拽模式，第二指触发放弃拖拽
        if (isDragMode) {
            onMouseUp?.invoke("left")
            isDragMode = false
        }
    }

    private fun handleMove(event: MotionEvent) {
        // 更新所有指针位置
        for (i in 0 until event.pointerCount) {
            val id = event.getPointerId(i)
            val x = event.getX(i)
            val y = event.getY(i)
            val pointer = pointers[id] ?: continue
            pointer.prevX = pointer.currentX
            pointer.prevY = pointer.currentY
            pointer.currentX = x
            pointer.currentY = y
        }

        when (pointers.size) {
            1 -> handleSingleFingerMove()
            2 -> handleTwoFingerMove()
        }
    }

    private fun handleSingleFingerMove() {
        val pointer = pointers.values.firstOrNull() ?: return
        val dx = (pointer.currentX - pointer.prevX) * cursorSpeed
        val dy = (pointer.currentY - pointer.prevY) * cursorSpeed

        // 忽略微小抖动
        if (kotlin.math.abs(dx) < 0.5f && kotlin.math.abs(dy) < 0.5f) return

        val totalDx = pointer.currentX - pointer.downX
        val totalDy = pointer.currentY - pointer.downY
        val distance = sqrt(totalDx * totalDx + totalDy * totalDy)

        if (isDragMode) {
            // 拖拽中 → 发送移动命令
            onMouseMove?.invoke(dx, dy)
        } else if (distance > 20f) {
            // 超过拖动阈值 → 取消长按，进入普通移动
            cancelLongPress()
            onMouseMove?.invoke(dx, dy)
        }
    }

    private fun handleTwoFingerMove() {
        val pointersList = pointers.values.toList()
        if (pointersList.size < 2) return

        val p1 = pointersList[0]
        val p2 = pointersList[1]

        // 计算两指中心点的移动
        val centerDx = ((p1.currentX - p1.prevX) + (p2.currentX - p2.prevX)) / 2f
        val centerDy = ((p1.currentY - p1.prevY) + (p2.currentY - p2.prevY)) / 2f

        // 计算距离变化（缩放检测）
        val currentDist = distance(p1.currentX, p1.currentY, p2.currentX, p2.currentY)

        if (lastTwoFingerDistance > 0f) {
            val distDelta = currentDist - lastTwoFingerDistance

            // 如果距离变化比例较大 → 缩放；否则 → 滚动
            if (kotlin.math.abs(distDelta) > 10f) {
                // 缩放手势
                val scaleFactor = currentDist / lastTwoFingerDistance
                onZoom?.invoke(scaleFactor)
                lastTwoFingerDistance = currentDist
                return
            }
        }
        lastTwoFingerDistance = currentDist

        // 垂直滚动
        if (kotlin.math.abs(centerDy) > kotlin.math.abs(centerDx)) {
            onScroll?.invoke(centerDy * scrollSpeed, 0f)
        } else {
            onScroll?.invoke(0f, centerDx * scrollSpeed)
        }
    }

    private fun handlePointerUp(event: MotionEvent) {
        val idx = event.actionIndex
        val id = event.getPointerId(idx)
        pointers.remove(id)

        // 只剩一根手指时重置
        if (pointers.size == 1) {
            val remaining = pointers.values.first()
            remaining.downX = remaining.currentX
            remaining.downY = remaining.currentY
        }

        if (pointers.isEmpty()) {
            lastTwoFingerDistance = 0f
        }
    }

    private fun handleUp(event: MotionEvent) {
        cancelLongPress()

        val pointerCount = event.pointerCount // 抬起的瞬间指针数
        val isCancel = event.actionMasked == MotionEvent.ACTION_CANCEL

        if (isCancel) {
            if (isDragMode) {
                onMouseUp?.invoke("left")
                isDragMode = false
            }
            pointers.clear()
            lastTwoFingerDistance = 0f
            return
        }

        when (pointerCount) {
            1 -> handleSingleFingerUp(event)
            2 -> handleTwoFingerUp(event)
        }

        pointers.clear()
        lastTwoFingerDistance = 0f
    }

    private fun handleSingleFingerUp(event: MotionEvent) {
        if (isDragMode) {
            onMouseUp?.invoke("left")
            isDragMode = false
            return
        }

        if (longPressSent) {
            // 长按结束后松开
            onMouseUp?.invoke("left")
            longPressSent = false
            return
        }

        val idx = event.actionIndex
        val x = event.getX(idx)
        val y = event.getY(idx)
        val time = System.currentTimeMillis()

        val pointer = pointers.values.firstOrNull() ?: return
        val dx = x - pointer.downX
        val dy = y - pointer.downY
        val distance = sqrt(dx * dx + dy * dy)
        val duration = time - pointer.downTime

        if (distance < 20f && duration < 300L) {
            // 轻点 → 检测双击
            val timeSinceLastClick = time - lastClickTime
            if (timeSinceLastClick < 350L && kotlin.math.abs(x - lastClickX) < 40f && kotlin.math.abs(y - lastClickY) < 40f) {
                // 双击
                onMouseDoubleClick?.invoke("left")
                lastClickTime = 0L
            } else {
                // 单击
                onMouseClick?.invoke("left")
                lastClickTime = time
                lastClickX = x
                lastClickY = y
            }
        }
    }

    private fun handleTwoFingerUp(event: MotionEvent) {
        // 双指同时抬起 → 轻触 → 右键
        val pointer = pointers.values.firstOrNull() ?: return
        val dx = pointer.currentX - pointer.downX
        val dy = pointer.currentY - pointer.downY
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < 30f) {
            onMouseClick?.invoke("right")
        }
    }

    /**
     * Compose 兼容的触控点抬起处理
     */
    private fun handleComposeUp(activePointers: List<Pair<Int, Pair<Float, Float>>>) {
        cancelLongPress()

        // 识别哪些指针抬起了（在 activePointers 中不存在但 pointers 中有）
        val stillActiveIds = activePointers.map { it.first }.toSet()
        val liftedIds = pointers.keys.filter { it !in stillActiveIds }

        if (liftedIds.isEmpty()) return

        val prevCount = pointers.size

        for (id in liftedIds) {
            pointers.remove(id)
        }

        if (pointers.size == 1 && prevCount > 1) {
            val remaining = pointers.values.first()
            remaining.downX = remaining.currentX
            remaining.downY = remaining.currentY
        }

        if (pointers.isEmpty()) {
            lastTwoFingerDistance = 0f
            // 最后一指抬起 → 判断单击/双击/右键
            handleComposeSingleFingerUp()
        }
    }

    /**
     * Compose 兼容的单指抬起检测
     */
    private fun handleComposeSingleFingerUp() {
        // 拖拽模式抬起
        if (isDragMode) {
            onMouseUp?.invoke("left")
            isDragMode = false
            return
        }
        if (longPressSent) {
            onMouseUp?.invoke("left")
            longPressSent = false
            return
        }
        // We don't have precise x/y/time for the lifted pointer in Compose mode,
        // so we use a simplified click detection
        onMouseClick?.invoke("left")
        lastClickTime = System.currentTimeMillis()
    }

    // ── 长按 ──────────────────────────────────────────

    private fun scheduleLongPress(time: Long, x: Float, y: Float) {
        val timer = java.util.Timer()
        longPressTimer = timer
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                val pointer = pointers.values.firstOrNull() ?: return
                val dp = sqrt((pointer.currentX - pointer.downX) * (pointer.currentX - pointer.downX) +
                        (pointer.currentY - pointer.downY) * (pointer.currentY - pointer.downY))

                if (dp < 30f) {
                    // 手指没怎么动 → 触发拖拽
                    isDragMode = true
                    longPressSent = true
                    onMouseDown?.invoke("left")
                    onDragStart?.invoke()
                }
            }
        }, 400L)
    }

    private fun cancelLongPress() {
        longPressTimer?.cancel()
        longPressTimer = null
    }

    // ── 手势快捷键 ─────────────────────────────────────

    fun triggerThreeFingerSwipeUp() {
        onGesture?.invoke("show_desktop")
    }

    fun triggerThreeFingerSwipeLeft() {
        onGesture?.invoke("switch_left")
    }

    fun triggerThreeFingerSwipeRight() {
        onGesture?.invoke("switch_right")
    }

    // ── 工具 ───────────────────────────────────────────

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    fun reset() {
        pointers.clear()
        cancelLongPress()
        isDragMode = false
        longPressSent = false
        lastTwoFingerDistance = 0f
    }

    fun updateSpeed(cursorSpeed: Float, scrollSpeed: Float) {
        // cursorSpeed and scrollSpeed stored as constructor params, recreate if needed
    }
}
