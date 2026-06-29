package com.touchcontrol.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchcontrol.network.ConnectionState
import com.touchcontrol.network.DiscoveredServer

@Composable
fun ConnectionScreen(
    connectionState: ConnectionState,
    savedHost: String,
    savedPort: Int,
    discoveredServers: List<DiscoveredServer>,
    isScanning: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
    onSelectServer: (DiscoveredServer) -> Unit,
    onStartScan: (() -> Unit)? = null,
) {
    var hostInput by remember { mutableStateOf(savedHost) }
    var portInput by remember { mutableStateOf(savedPort.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        // 应用标题
        Icon(
            imageVector = Icons.Filled.TouchApp,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "TouchControl",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "手机变触摸板",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        // 连接状态指示
        val statusColor by animateColorAsState(
            targetValue = when (connectionState) {
                is ConnectionState.Connected -> Color(0xFF00D68F)
                is ConnectionState.Connecting -> Color(0xFFFFB347)
                is ConnectionState.Failed -> Color(0xFFFF4757)
                is ConnectionState.Disconnected -> Color(0xFF666680)
            },
            label = "statusColor"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = when (connectionState) {
                    is ConnectionState.Connected -> "已连接 ${connectionState.host}:${connectionState.port}"
                    is ConnectionState.Connecting -> "连接中…"
                    is ConnectionState.Failed -> "连接失败: ${connectionState.error}"
                    is ConnectionState.Disconnected -> "未连接"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        // 手动连接表单
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
                Text(
                    text = "手动连接",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = {
                            hostInput = it
                            onHostChange(it)
                        },
                        label = { Text("IP 地址") },
                        placeholder = { Text("192.168.1.x") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = {
                            portInput = it
                            onPortChange(it.toIntOrNull() ?: 9090)
                        },
                        label = { Text("端口") },
                        placeholder = { Text("9090") },
                        singleLine = true,
                        modifier = Modifier.width(100.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                val isConnected = connectionState is ConnectionState.Connected
                Button(
                    onClick = {
                        if (isConnected) onDisconnect() else onConnect()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.PowerSettingsNew else Icons.Filled.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isConnected) "断开连接" else "连接")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 扫码连接 ──
        if (onStartScan != null) {
            Button(
                onClick = onStartScan,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("扫一扫连接", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 自动发现
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "局域网发现",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FilledTonalButton(
                        onClick = onScan,
                        enabled = !isScanning,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (isScanning) "扫描中" else "扫描")
                    }
                }

                if (discoveredServers.isEmpty() && !isScanning) {
                    Text(
                        text = "点击「扫描」搜索局域网的 TouchControl 服务端",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                discoveredServers.forEach { server ->
                    Surface(
                        onClick = { onSelectServer(server) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${server.host}:${server.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 底部提示
        Text(
            text = "确保电脑已安装并运行 TouchControl 服务端",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}
