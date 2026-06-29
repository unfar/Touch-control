package com.touchcontrol.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.touchcontrol.network.EmbeddedWebSocketServer
import com.touchcontrol.network.EmbeddedWebSocketServer.ClientState

@Composable
fun TabletReceiverScreen(
    server: EmbeddedWebSocketServer,
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val serverState by server.serverState.collectAsState()
    val clientState by server.clientState.collectAsState()

    val statusColor by animateColorAsState(
        targetValue = when {
            serverState is EmbeddedWebSocketServer.ServerState.Running &&
            clientState is ClientState.Connected -> Color(0xFF00D68F)
            serverState is EmbeddedWebSocketServer.ServerState.Running -> Color(0xFF6C63FF)
            else -> Color(0xFF666680)
        },
        label = "statusColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // 返回切换模式
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onSwitchMode) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("切换模式")
            }
        }

        Spacer(Modifier.height(8.dp))

        // 状态指示
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Tablet,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = statusColor,
            )
        }
        Spacer(Modifier.height(12.dp))

        Text(
            text = "平板模式",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "等待手机端连接中…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        // 状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 服务状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = statusColor,
                        )
                        Column {
                            Text("服务端", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = when (serverState) {
                                    is EmbeddedWebSocketServer.ServerState.Running -> "运行中"
                                    is EmbeddedWebSocketServer.ServerState.Starting -> "启动中"
                                    is EmbeddedWebSocketServer.ServerState.Error -> "出错"
                                    else -> "未启动"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = serverState is EmbeddedWebSocketServer.ServerState.Running,
                        onCheckedChange = { running ->
                            if (running && !isServiceRunning) {
                                // 需要辅助功能先开启
                            }
                            if (running) server.start() else server.stop()
                        },
                    )
                }

                HorizontalDivider()

                // IP 地址和端口
                if (serverState is EmbeddedWebSocketServer.ServerState.Running) {
                    val running = serverState as EmbeddedWebSocketServer.ServerState.Running
                    InfoRow(
                        icon = Icons.Filled.Lan,
                        label = "连接地址",
                        value = "ws://${running.host}:${running.port}",
                    )
                    InfoRow(
                        icon = Icons.Filled.DevicesOther,
                        label = "客户端状态",
                        value = when (clientState) {
                            is ClientState.Connected -> "已连接: ${clientState.host}"
                            else -> "等待手机连接…"
                        },
                    )
                } else {
                    InfoRow(
                        icon = Icons.Filled.Lan,
                        label = "端口",
                        value = "9090",
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 二维码连接 ──
        if (serverState is EmbeddedWebSocketServer.ServerState.Running) {
            val qrData = server.getQrData()
            val token = server.token

            if (qrData != null) {
                val qrBitmap = remember(qrData) {
                    com.touchcontrol.ui.components.QrCodeGenerator.generate(qrData)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "📷 扫一扫连接",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )

                        // QR 码图片
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "连接二维码",
                                modifier = Modifier.size(200.dp),
                            )
                        }

                        // Token 显示
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "配对码: $token",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Text(
                            text = "打开手机 TouchControl → 扫码连接",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        // 辅助功能指引
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Filled.CheckCircle else Icons.Filled.Accessibility,
                        contentDescription = null,
                        tint = if (isServiceRunning) Color(0xFF00D68F) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (isServiceRunning) "无障碍服务已开启 ✅" else "无障碍服务未开启",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (!isServiceRunning) {
                    Text(
                        text = "平板模式需要开启无障碍服务才能模拟触摸操作。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("前往设置开启")
                    }

                    Text(
                        text = "找到「TouchControl」→ 开启「TouchControl 服务」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 使用说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "使用说明",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "① 确保平板和手机在同一 WiFi 网络",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "② 开启无障碍服务并启动此服务端",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "③ 在手机 TouchControl 上选择「手机模式」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "④ 输入上方显示的 IP:端口 连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "⑤ 连接成功后在手机上滑动即可控制平板",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
