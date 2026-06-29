package com.touchcontrol.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.touchcontrol.data.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onSwitchMode: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val cursorSpeed by settingsRepository.cursorSpeed.collectAsState(initial = 1f)
    val scrollSpeed by settingsRepository.scrollSpeed.collectAsState(initial = 1f)
    val hapticEnabled by settingsRepository.hapticFeedback.collectAsState(initial = true)
    val darkMode by settingsRepository.darkMode.collectAsState(initial = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
        )

        // 切换角色
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("切换角色", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "切换到平板模式 / 重新选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onSwitchMode,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("切换")
                }
            }
        }

        // 光标速度
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Mouse, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("光标速度", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${"%.1f".format(cursorSpeed)}x",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = cursorSpeed,
                    onValueChange = { scope.launch { settingsRepository.saveCursorSpeed(it) } },
                    valueRange = 0.3f..3.0f,
                    steps = 26,
                )
            }
        }

        // 滚动速度
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.SwipeVertical, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("滚动速度", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${"%.1f".format(scrollSpeed)}x",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = scrollSpeed,
                    onValueChange = { scope.launch { settingsRepository.saveScrollSpeed(it) } },
                    valueRange = 0.3f..3.0f,
                    steps = 26,
                )
            }
        }

        // 触感反馈
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Vibration, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("触感反馈", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "点击按键时震动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = hapticEnabled,
                    onCheckedChange = { scope.launch { settingsRepository.saveHapticFeedback(it) } },
                )
            }
        }

        // 深色模式
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("深色模式", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "界面深色主题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = darkMode,
                    onCheckedChange = { scope.launch { settingsRepository.saveDarkMode(it) } },
                )
            }
        }

        // 关于
        Spacer(Modifier.weight(1f))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "TouchControl v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "手机变触摸板 · 平板变被控端",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
