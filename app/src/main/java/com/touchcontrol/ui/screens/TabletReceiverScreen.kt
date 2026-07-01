package com.touchcontrol.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.touchcontrol.network.BluetoothServer

@Composable
fun TabletReceiverScreen(
    bluetoothServer: BluetoothServer,
    isServiceRunning: Boolean,
    btEnabled: Boolean,
    onToggleService: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val btState by bluetoothServer.state.collectAsState()

    val statusColor by animateColorAsState(
        targetValue = when {
            !isServiceRunning -> Color(0xFFFF4757)
            btState is BluetoothServer.ServerState.Connected -> Color(0xFF00D68F)
            btState is BluetoothServer.ServerState.Listening -> Color(0xFF6C63FF)
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

        // ── ⚠️ 无障碍服务未开启 → 红色横幅 ──
        if (!isServiceRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF9B2C2C).copy(alpha = 0.9f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Text("无障碍服务未开启", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(
                        "平板模式需要无障碍服务才能模拟触摸操作。请前往系统设置开启「TouchControl 服务」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF9B2C2C)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("前往设置开启", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── 🔵 蓝牙未开启 → 提示 ──
        if (!btEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A365D).copy(alpha = 0.9f)
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.BluetoothDisabled,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "蓝牙未开启",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        "平板模式需要蓝牙服务端运行，请在系统设置中开启蓝牙。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1A365D)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("前往开启蓝牙", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // 返回切换模式
        Row(modifier = Modifier.fillMaxWidth()) {
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
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = statusColor,
            )
        }
        Spacer(Modifier.height(12.dp))

        Text("平板模式", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = when (btState) {
                is BluetoothServer.ServerState.Connected ->
                    "已连接: ${(btState as BluetoothServer.ServerState.Connected).deviceName}"
                is BluetoothServer.ServerState.Listening -> "等待蓝牙连接…"
                is BluetoothServer.ServerState.Error ->
                    "错误: ${(btState as BluetoothServer.ServerState.Error).message}"
                else -> "启动中…"
            },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = statusColor)
                        Column {
                            Text("蓝牙服务端", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = when (btState) {
                                    is BluetoothServer.ServerState.Listening -> "运行中"
                                    is BluetoothServer.ServerState.Connected -> "已连接"
                                    is BluetoothServer.ServerState.Error -> "出错"
                                    else -> "启动中"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 连接信息
                InfoRow(
                    icon = Icons.Filled.BluetoothConnected,
                    label = "状态",
                    value = when (btState) {
                        is BluetoothServer.ServerState.Connected ->
                            "已连接: ${(btState as BluetoothServer.ServerState.Connected).deviceName}"
                        is BluetoothServer.ServerState.Listening -> "等待手机连接…"
                        is BluetoothServer.ServerState.Error ->
                            "错误: ${(btState as BluetoothServer.ServerState.Error).message}"
                        else -> "未启动"
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

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
                Text("使用说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("① 确保手机和平板的蓝牙已开启", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("② 在系统蓝牙设置中配对两台设备", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("③ 开启无障碍服务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("④ 在手机 TouchControl 上选择「手机模式」", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("⑤ 扫描蓝牙设备并连接", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("⑥ 连接成功后在手机上滑动即可控制平板", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
