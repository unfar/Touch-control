package com.touchcontrol.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Touchpad : Screen("touchpad", "触摸板", Icons.Filled.TouchApp)
    data object Keyboard : Screen("keyboard", "键盘", Icons.Filled.Keyboard)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}
