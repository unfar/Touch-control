package com.touchcontrol.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchcontrol.gesture.TouchpadEngine
import com.touchcontrol.network.ConnectionState
import com.touchcontrol.network.BluetoothClient

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchpadScreen(
    connectionState: ConnectionState,
    btConnectionState: BluetoothClient.ConnectionState = BluetoothClient.ConnectionState.Disconnected,
    cursorSpeed: Float,
    scrollSpeed: Float,
    onSendMessage: (Any) -> Boolean,
    onNavigateToConnection: () -> Unit,
) {
    val isConnected = connectionState is ConnectionState.Connected ||
            btConnectionState is BluetoothClient.ConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // 顶部栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 连接状态
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isBt = btConnectionState is BluetoothClient.ConnectionState.Connected
                    val statusColor = when {
                        isBt -> Color(0xFF00D68F)
                        connectionState is ConnectionState.Connected -> Color(0xFF00D68F)
                        connectionState is ConnectionState.Connecting -> Color(0xFFFFB347)
                        else -> Color(0xFF666680)
                    }
                    val statusIcon = if (isBt) Icons.Filled.Bluetooth else Icons.Filled.Wifi
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = statusColor,
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = when {
                            isBt -> "蓝牙: ${(btConnectionState as BluetoothClient.ConnectionState.Connected).deviceName}"
                            connectionState is ConnectionState.Connected -> "${connectionState.host}"
                            connectionState is ConnectionState.Connecting -> "连接中"
                            else -> "未连接"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 连接按钮
                if (!isConnected) {
                    FilledTonalButton(
                        onClick = onNavigateToConnection,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("连接", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 触摸板主体
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!isConnected) {
                // 未连接状态
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "未连接至服务端",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "返回设置页连接电脑",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            } else {
                // 触摸板 Surface
                TouchpadSurface(
                    cursorSpeed = cursorSpeed,
                    scrollSpeed = scrollSpeed,
                    onSendMessage = onSendMessage,
                )
            }
        }

        // 底部操作栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                GestureHint(icon = Icons.Filled.TouchApp, label = "点击 = 左键")
                GestureHint(icon = Icons.Filled.TouchApp, label = "双指 = 右键/滚动")
                GestureHint(icon = Icons.Filled.OpenWith, label = "长按 + 拖 = 拖拽")
            }
        }
    }
}

@Composable
private fun GestureHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 10.sp,
        )
    }
}

// ── 触摸板 Surface ──────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchpadSurface(
    cursorSpeed: Float,
    scrollSpeed: Float,
    onSendMessage: (Any) -> Boolean,
) {
    // 触摸板引擎
    val engine = remember {
        TouchpadEngine(cursorSpeed = cursorSpeed, scrollSpeed = scrollSpeed)
    }

    // 触控点可视化
    val touchPoints = remember { mutableStateListOf<Offset>() }
    var isTwoFinger by remember { mutableStateOf(false) }
    var showingRightClick by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // 关联引擎回调
    LaunchedEffect(cursorSpeed, scrollSpeed) {
        engine.onMouseMove = { dx, dy ->
            onSendMessage(com.touchcontrol.gesture.GestureProtocol.MouseMove(dx = dx, dy = dy))
        }
        engine.onMouseClick = { button ->
            onSendMessage(com.touchcontrol.gesture.GestureProtocol.MouseClick(
                action = "click", button = button
            ))
            if (button == "right") {
                showingRightClick = true
            }
        }
        engine.onMouseDoubleClick = { button ->
            repeat(2) {
                onSendMessage(com.touchcontrol.gesture.GestureProtocol.MouseClick(
                    action = "click", button = button
                ))
            }
        }
        engine.onMouseDown = { button ->
            onSendMessage(com.touchcontrol.gesture.GestureProtocol.MouseClick(
                action = "down", button = button
            ))
        }
        engine.onMouseUp = { button ->
            onSendMessage(com.touchcontrol.gesture.GestureProtocol.MouseClick(
                action = "up", button = button
            ))
        }
        engine.onScroll = { dy, dx ->
            onSendMessage(com.touchcontrol.gesture.GestureProtocol.MouseScroll(
                dy = dy, dx = dx
            ))
        }
        engine.onZoom = { factor ->
            if (factor > 1) {
                onSendMessage(com.touchcontrol.gesture.GestureProtocol.KeyTap(
                    key = "ctrl_plus", modifiers = listOf("ctrl")
                ))
            } else {
                onSendMessage(com.touchcontrol.gesture.GestureProtocol.KeyTap(
                    key = "ctrl_minus", modifiers = listOf("ctrl")
                ))
            }
        }
        engine.onDragStart = { isDragging = true }
        engine.onDragEnd = { isDragging = false }
        engine.updateSpeed(cursorSpeed, scrollSpeed)
    }

    // 右键反馈动画
    LaunchedEffect(showingRightClick) {
        if (showingRightClick) {
            kotlinx.coroutines.delay(800)
            showingRightClick = false
        }
    }

    // 跟踪之前的触控点数，用于检测 down/up 事件
    var previousPointerCount by remember { mutableIntStateOf(0) }

    // 提取 MaterialTheme 颜色（Canvas 内无法直接访问）
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val surfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val textPaintColor = surfaceVariantColor.hashCode()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        // 触摸板区域指示线
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 四角圆角指示
            val cornerSize = 30f
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(2f, 2f),
                size = androidx.compose.ui.geometry.Size(w - 4f, h - 4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerSize),
                style = Stroke(width = 1.5f),
            )

            // 中心提示文字
            val paint = android.graphics.Paint().apply {
                color = textPaintColor
                textSize = 40f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "👆 在此滑动",
                w / 2f, h / 2f,
                paint,
            )
        }

        // 右键反馈
        if (showingRightClick) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ArrowRightAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }

    // 使用 Compose 指针 API 直接处理触摸，无需 MotionEvent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(cursorSpeed, scrollSpeed) {
                awaitPointerEventScope {
                    while (true) {
                        val pointerEvent = awaitPointerEvent()
                        val changes = pointerEvent.changes
                        val currentCount = changes.size

                        // 更新触控点可视化
                        touchPoints.clear()
                        for (change in changes) {
                            touchPoints.add(change.position)
                        }
                        isTwoFinger = currentCount >= 2

                        // 构建引擎需要的指针数据
                        val activePointers = changes.mapIndexed { index, change ->
                            index to (change.position.x to change.position.y)
                        }

                        // 判断事件类型
                        val actionType = when {
                            currentCount > previousPointerCount -> "down"
                            currentCount == 0 -> "up"
                            else -> "move"
                        }

                        // 交给引擎处理
                        engine.onComposeTouchData(activePointers, actionType)

                        previousPointerCount = currentCount

                        // 标记事件已消费
                        if (changes.isNotEmpty()) {
                            changes.forEach { it.consume() }
                        }
                    }
                }
            },
    )
}
