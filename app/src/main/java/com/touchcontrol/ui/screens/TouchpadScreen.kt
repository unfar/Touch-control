package com.touchcontrol.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.touchcontrol.gesture.GestureProtocol
import com.touchcontrol.gesture.PointerAcceleration
import com.touchcontrol.network.BluetoothClient
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchpadScreen(
    btState: BluetoothClient.ConnectionState = BluetoothClient.ConnectionState.Disconnected,
    cursorSpeed: Float = 1f,
    scrollSpeed: Float = 1f,
    onSendMessage: (Any) -> Boolean,
    onNavigateToConnection: () -> Unit,
) {
    val isConnected = btState is BluetoothClient.ConnectionState.Connected
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
    ) {
        // 顶部栏
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isBt = btState is BluetoothClient.ConnectionState.Connected
                    val statusColor = when {
                        isBt -> Color(0xFF00D68F)
                        btState is BluetoothClient.ConnectionState.Connecting -> Color(0xFFFFB347)
                        else -> Color(0xFF666680)
                    }
                    Icon(
                        imageVector = if (isBt) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                        contentDescription = null, modifier = Modifier.size(14.dp), tint = statusColor,
                    )
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(statusColor))
                    Text(
                        text = when {
                            isBt -> "已连接: ${(btState as BluetoothClient.ConnectionState.Connected).deviceName}"
                            btState is BluetoothClient.ConnectionState.Connecting -> "连接中…"
                            btState is BluetoothClient.ConnectionState.Failed -> "失败: ${(btState as BluetoothClient.ConnectionState.Failed).error}"
                            else -> "未连接"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isConnected) {
                    FilledTonalButton(
                        onClick = onNavigateToConnection, shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("连接", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ── 触摸板主体 ──
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
            if (!isConnected) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.WifiOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text("未连接至平板", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                SmartTouchpad(isLandscape = isLandscape, scrollSpeed = scrollSpeed, onSendMessage = onSendMessage)
            }
        }

        // 底部
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp, 3.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ScreenRotation, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isLandscape) "👆 点击=单击 · 滑动=移动 · 按住拖动 · 双指=滚动 · 双指轻触=右键"
                    else "👆 点击单击 · 滑动移动 · 按住拖动 · 双指滚动/右键",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private const val TAG = "Touchpad"

private enum class TouchState { IDLE, TRACKING, DRAGGING, SCROLLING }

private const val TAP_TIME_MS = 280L
private const val TABLET_DIAG = 3600f

/**
 * 震动反馈
 */
private fun Context.vibrateClick() {
    try {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(30L, 255))  // 30ms, 最大振幅
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(15L)
        }
    } catch (_: Exception) {}
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SmartTouchpad(
    isLandscape: Boolean,
    scrollSpeed: Float,
    onSendMessage: (Any) -> Boolean,
) {
    val context = LocalContext.current
    val aspect = if (isLandscape) 3000f / 2120f else 2120f / 3000f

    var touchpadW by remember { mutableFloatStateOf(1f) }
    var touchpadH by remember { mutableFloatStateOf(1f) }

    // ── 状态机 ──
    var state by remember { mutableStateOf(TouchState.IDLE) }

    // 活动指针数（手动维护，不依赖 changes.size）
    var activePtrCount by remember { mutableIntStateOf(0) }

    // 当前单指位置
    var curX by remember { mutableFloatStateOf(0f) }
    var curY by remember { mutableFloatStateOf(0f) }

    // 按下位置
    var downX by remember { mutableFloatStateOf(0f) }
    var downY by remember { mutableFloatStateOf(0f) }
    var downTime by remember { mutableLongStateOf(0L) }

    // 上次 Move 位置
    var lastX by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }

    // 双指
    var twoCx by remember { mutableFloatStateOf(0f) }
    var twoCy by remember { mutableFloatStateOf(0f) }
    var twoStartDist by remember { mutableFloatStateOf(0f) }

    // 界面状态
    var isActive by remember { mutableStateOf(false) }
    var touchPointAlpha by remember { mutableFloatStateOf(0f) }

    val bgColor = if (isActive) Color(0xFF1A1A3E) else Color(0xFF0E0E1E)
    val borderColor = if (isActive) Color(0xFF8B83FF) else Color(0xFF555577)
    val glowColor = if (isActive) Color(0xFF6C63FF) else Color(0xFF3A3A5C)

    // 触摸点渐隐动画
    LaunchedEffect(touchPointAlpha) {
        if (touchPointAlpha > 0f) {
            delay(150)
            touchPointAlpha = 0f
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.98f else 0.92f)
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                drawRoundRect(glowColor.copy(alpha = 0.3f), Offset(-3f, -3f), Size(w + 6f, h + 6f), CornerRadius(18f), style = Stroke(4f))
                drawRoundRect(bgColor, Offset.Zero, Size(w, h), CornerRadius(14f))
                drawRoundRect(borderColor, Offset(1f, 1f), Size(w - 2f, h - 2f), CornerRadius(13f), style = Stroke(2f))
                drawRoundRect(Color(0xFF0A0A18), Offset(3f, 3f), Size(w - 6f, h - 6f), CornerRadius(11f))
                val gp = android.graphics.Paint().apply { color = android.graphics.Color.argb(20,255,255,255); strokeWidth = 0.5f; isAntiAlias = true }
                for (i in 1..3) { val f = i * 0.25f; drawContext.canvas.nativeCanvas.drawLine(w*f,3f,w*f,h-3f,gp); drawContext.canvas.nativeCanvas.drawLine(3f,h*f,w-3f,h*f,gp) }
                val cc = if (isActive) Color(0xFF6C63FF).copy(alpha = 0.4f) else Color(0xFF2A2A4A).copy(alpha = 0.3f)
                drawLine(cc, Offset(w/2f-24f, h/2f), Offset(w/2f+24f, h/2f), 1f); drawLine(cc, Offset(w/2f, h/2f-24f), Offset(w/2f, h/2f+24f), 1f)
                drawRoundRect(borderColor, Offset.Zero, Size(w, h), CornerRadius(14f), style = Stroke(3f))
                val ha = if (isActive) 0.25f else 0.10f
                val hp = android.graphics.Paint().apply { color = android.graphics.Color.argb((255*ha).toInt(),170,170,200); textSize = 34f; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
                drawContext.canvas.nativeCanvas.drawText(if(isLandscape)"平板 横屏" else "平板 竖屏", w/2f, h/2f+12f, hp)

                // 触摸点指示器
                if (touchPointAlpha > 0f && state != TouchState.IDLE) {
                    val tp = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb((255*touchPointAlpha*0.3f).toInt().coerceIn(0,255),108,99,255)
                        isAntiAlias = true
                    }
                    val tx = curX; val ty = curY
                    drawContext.canvas.nativeCanvas.drawCircle(tx, ty, 40f, tp)
                    tp.color = android.graphics.Color.argb((255*touchPointAlpha*0.6f).toInt().coerceIn(0,255),108,99,255)
                    tp.style = android.graphics.Paint.Style.STROKE
                    tp.strokeWidth = 3f
                    drawContext.canvas.nativeCanvas.drawCircle(tx, ty, 24f, tp)
                }

                touchpadW = w; touchpadH = h
            }

    // ── 触摸处理 ──
            Box(
                Modifier.fillMaxSize().pointerInput(aspect, scrollSpeed) {
                    awaitPointerEventScope {
                        // 指针跟踪表：PointerId → 当前位置
                        val pointers = mutableMapOf<PointerId, Offset>()

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val changes = event.changes
                            val now = System.currentTimeMillis()

                            // ── 更新指针跟踪表 ──
                            for (ch in changes) {
                                if (ch.pressed) {
                                    pointers[ch.id] = ch.position
                                } else {
                                    pointers.remove(ch.id)
                                }
                            }
                            val ptrCount = pointers.size

                            when (event.type) {
                                PointerEventType.Press -> {
                                    isActive = true

                                    if (ptrCount == 1) {
                                        // ── 单指按下 ──
                                        val p = pointers.values.first()
                                        downX = p.x; downY = p.y
                                        curX = p.x; curY = p.y
                                        lastX = p.x; lastY = p.y
                                        downTime = now

                                        // 光标瞬移到手指位置
                                        val pctX = (p.x / touchpadW).coerceIn(0f, 1f)
                                        val pctY = (p.y / touchpadH).coerceIn(0f, 1f)
                                        onSendMessage(GestureProtocol.CursorAbs(x = pctX, y = pctY))

                                        state = TouchState.TRACKING
                                        touchPointAlpha = 1f
                                        Log.i(TAG, "↓ 按下 位置(${p.x.toInt()},${p.y.toInt()}) 百分比($pctX,$pctY)")
                                    } else if (ptrCount >= 2) {
                                        // ── 双指按下 ──
                                        state = TouchState.SCROLLING
                                        val allPtrs = pointers.values.toList()
                                        val p1 = allPtrs[0]; val p2 = allPtrs[1]
                                        twoCx = (p1.x + p2.x) / 2f; twoCy = (p1.y + p2.y) / 2f
                                        twoStartDist = sqrt((p1.x-p2.x)*(p1.x-p2.x) + (p1.y-p2.y)*(p1.y-p2.y))
                                        Log.i(TAG, "✌️ 双指开始 — 指针数=$ptrCount 距离=${twoStartDist.toInt()}")
                                    }
                                    changes.forEach { it.consume() }
                                }

                                PointerEventType.Move -> {
                                    when (state) {
                                        TouchState.TRACKING -> {
                                            if (ptrCount == 1) {
                                                val p = pointers.values.first()
                                                curX = p.x; curY = p.y
                                                val dx = curX - lastX
                                                val dy = curY - lastY
                                                val totalMove = abs(curX - downX) + abs(curY - downY)
                                                val threshold = 0.03f * maxOf(touchpadW, touchpadH)

                                                // 检查是否应该进入拖动模式（长按 + 未怎么移动）
                                                val holdTime = now - downTime
                                                if (holdTime > 400L && totalMove < threshold && totalMove < threshold * 0.5f) {
                                                    // 长按 → 进入拖动模式
                                                    state = TouchState.DRAGGING
                                                    onSendMessage(GestureProtocol.MouseClick(
                                                        action = "down", button = "left"
                                                    ))
                                                    // 发送当前位置作为第一次移动
                                                    val ndx = PointerAcceleration.normalize(dx, touchpadW)
                                                    val ndy = PointerAcceleration.normalize(dy, touchpadH)
                                                    val (adx, ady) = PointerAcceleration.accelerate(ndx, ndy)
                                                    val scale = TABLET_DIAG
                                                    if (abs(adx) > 0.001f || abs(ady) > 0.001f) {
                                                        onSendMessage(GestureProtocol.MouseMove(
                                                            dx = adx * scale, dy = ady * scale
                                                        ))
                                                    }
                                                    lastX = curX; lastY = curY
                                                    Log.i(TAG, "↗ 长按 → 开始拖动")
                                                } else {
                                                    // 普通滑动 → 只移动光标
                                                    val ndx = PointerAcceleration.normalize(dx, touchpadW)
                                                    val ndy = PointerAcceleration.normalize(dy, touchpadH)
                                                    val (adx, ady) = PointerAcceleration.accelerate(ndx, ndy)
                                                    val scale = TABLET_DIAG
                                                    if (abs(adx) > 0.001f || abs(ady) > 0.001f) {
                                                        onSendMessage(GestureProtocol.MouseMove(
                                                            dx = adx * scale, dy = ady * scale
                                                        ))
                                                    }
                                                    lastX = curX; lastY = curY
                                                }
                                                touchPointAlpha = 0.6f
                                            } else if (ptrCount >= 2) {
                                                // 突然变成双指 → 切换到 SCROLLING
                                                state = TouchState.SCROLLING
                                                val allPtrs = pointers.values.toList()
                                                val p1 = allPtrs[0]; val p2 = allPtrs[1]
                                                twoCx = (p1.x + p2.x) / 2f; twoCy = (p1.y + p2.y) / 2f
                                                twoStartDist = sqrt((p1.x-p2.x)*(p1.x-p2.x) + (p1.y-p2.y)*(p1.y-p2.y))
                                                Log.i(TAG, "✌️ Move 中切换到双指")
                                            }
                                        }

                                        TouchState.DRAGGING -> {
                                            if (ptrCount >= 1) {
                                                val p = pointers.values.first()
                                                curX = p.x; curY = p.y
                                                val dx = curX - lastX
                                                val dy = curY - lastY
                                                val ndx = PointerAcceleration.normalize(dx, touchpadW)
                                                val ndy = PointerAcceleration.normalize(dy, touchpadH)
                                                val (adx, ady) = PointerAcceleration.accelerate(ndx, ndy)
                                                val scale = TABLET_DIAG
                                                if (abs(adx) > 0.001f || abs(ady) > 0.001f) {
                                                    onSendMessage(GestureProtocol.MouseMove(
                                                        dx = adx * scale, dy = ady * scale
                                                    ))
                                                }
                                                lastX = curX; lastY = curY
                                                touchPointAlpha = 0.6f
                                            }
                                        }

                                        TouchState.SCROLLING -> {
                                            if (ptrCount >= 2) {
                                                val allPtrs = pointers.values.toList()
                                                val p1 = allPtrs[0]; val p2 = allPtrs[1]
                                                val cx = (p1.x+p2.x)/2f; val cy = (p1.y+p2.y)/2f
                                                val dcx = cx - twoCx; val dcy = cy - twoCy
                                                twoCx = cx; twoCy = cy

                                                val ndx = PointerAcceleration.scrollAccelerate(
                                                    PointerAcceleration.normalize(dcx, touchpadW)
                                                )
                                                val ndy = PointerAcceleration.scrollAccelerate(
                                                    PointerAcceleration.normalize(dcy, touchpadH)
                                                )
                                                val scale = TABLET_DIAG * scrollSpeed * 2f
                                                if (abs(ndx) > 0.001f || abs(ndy) > 0.001f) {
                                                    onSendMessage(GestureProtocol.MouseScroll(
                                                        dy = ndy * scale, dx = ndx * scale
                                                    ))
                                                }
                                            } else if (ptrCount == 1) {
                                                // 一根手指抬起 → 切回 TRACKING
                                                val p = pointers.values.first()
                                                curX = p.x; curY = p.y
                                                lastX = p.x; lastY = p.y
                                                downX = p.x; downY = p.y
                                                downTime = now
                                                state = TouchState.TRACKING
                                                Log.i(TAG, "✌️→👆 双指变单指")
                                            }
                                        }

                                        else -> {} // IDLE
                                    }
                                    changes.forEach { it.consume() }
                                }

                                PointerEventType.Release -> {
                                    when (state) {
                                        TouchState.TRACKING -> {
                                            // ── 抬起（未拖动）→ 判断单击 ──
                                            val elapsed = now - downTime
                                            Log.i(TAG, "👆 抬起 (${elapsed}ms, 指针=$ptrCount)")
                                            if (ptrCount == 0) {
                                                if (elapsed < TAP_TIME_MS) {
                                                    context.vibrateClick()
                                                    onSendMessage(GestureProtocol.MouseClick(
                                                        action = "click", button = "left"
                                                    ))
                                                } else {
                                                    context.vibrateClick()
                                                    onSendMessage(GestureProtocol.MouseClick(
                                                        action = "click", button = "left"
                                                    ))
                                                }
                                                state = TouchState.IDLE
                                                isActive = false
                                                touchPointAlpha = 0f
                                            }
                                        }

                                        TouchState.DRAGGING -> {
                                            if (ptrCount == 0) {
                                                Log.i(TAG, "↗ 拖动结束 → 抬起")
                                                onSendMessage(GestureProtocol.MouseClick(
                                                    action = "up", button = "left"
                                                ))
                                                state = TouchState.IDLE
                                                isActive = false
                                                touchPointAlpha = 0f
                                            }
                                        }

                                        TouchState.SCROLLING -> {
                                            if (ptrCount == 0) {
                                                Log.i(TAG, "✌️ 双指抬起")
                                                context.vibrateClick()
                                                onSendMessage(GestureProtocol.MouseClick(
                                                    action = "click", button = "right"
                                                ))
                                                state = TouchState.IDLE
                                                isActive = false
                                                touchPointAlpha = 0f
                                            }
                                            // ptrCount == 1 在 Move 中处理
                                        }

                                        else -> {
                                            state = TouchState.IDLE
                                            isActive = false
                                            touchPointAlpha = 0f
                                        }
                                    }
                                    changes.forEach { it.consume()}
                                }

                                PointerEventType.Unknown -> {
                                    if (state == TouchState.DRAGGING) {
                                        onSendMessage(GestureProtocol.MouseClick(
                                            action = "up", button = "left"
                                        ))
                                    }
                                    state = TouchState.IDLE
                                    isActive = false
                                    touchPointAlpha = 0f
                                    pointers.clear()
                                    changes.forEach { it.consume() }
                                }

                                else -> {
                                    changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}
