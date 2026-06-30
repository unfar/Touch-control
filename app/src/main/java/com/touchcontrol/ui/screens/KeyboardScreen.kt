package com.touchcontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchcontrol.gesture.GestureProtocol
import com.touchcontrol.network.BluetoothClient

data class KeyDef(
    val label: String,
    val keyCode: String,
    val width: Int = 1,
    val modifiers: List<String> = emptyList(),
    val isToggle: Boolean = false,
)

private val ROW_1 = listOf(
    KeyDef("Q", "q"), KeyDef("W", "w"), KeyDef("E", "e"), KeyDef("R", "r"),
    KeyDef("T", "t"), KeyDef("Y", "y"), KeyDef("U", "u"), KeyDef("I", "i"),
    KeyDef("O", "o"), KeyDef("P", "p"),
)

private val ROW_2 = listOf(
    KeyDef("A", "a"), KeyDef("S", "s"), KeyDef("D", "d"), KeyDef("F", "f"),
    KeyDef("G", "g"), KeyDef("H", "h"), KeyDef("J", "j"), KeyDef("K", "k"),
    KeyDef("L", "l"),
)

private val ROW_3 = listOf(
    KeyDef("Z", "z"), KeyDef("X", "x"), KeyDef("C", "c"), KeyDef("V", "v"),
    KeyDef("B", "b"), KeyDef("N", "n"), KeyDef("M", "m"),
)

private val MODIFIERS = listOf(
    KeyDef("Ctrl", "ctrl", width = 2, isToggle = true),
    KeyDef("Alt", "alt", width = 2, isToggle = true),
    KeyDef("Shift", "shift", width = 2, isToggle = true),
    KeyDef("Win", "meta", width = 2, isToggle = true),
)

private val SPECIALS = listOf(
    KeyDef("Tab", "tab", width = 2),
    KeyDef("Caps", "caps_lock", width = 2),
    KeyDef("Enter", "enter", width = 3),
    KeyDef("←", "left", width = 1),
    KeyDef("→", "right", width = 1),
    KeyDef("↑", "up", width = 1),
    KeyDef("↓", "down", width = 1),
    KeyDef("Esc", "escape", width = 1),
    KeyDef("⌫", "backspace", width = 2),
    KeyDef("Del", "delete", width = 1),
)

private val F_KEYS = listOf(
    KeyDef("F1", "f1"), KeyDef("F2", "f2"), KeyDef("F3", "f3"), KeyDef("F4", "f4"),
    KeyDef("F5", "f5"), KeyDef("F6", "f6"), KeyDef("F7", "f7"), KeyDef("F8", "f8"),
    KeyDef("F9", "f9"), KeyDef("F10", "f10"), KeyDef("F11", "f11"), KeyDef("F12", "f12"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    btState: BluetoothClient.ConnectionState = BluetoothClient.ConnectionState.Disconnected,
    onSendMessage: (Any) -> Boolean,
) {
    val isConnected = btState is BluetoothClient.ConnectionState.Connected
    var activeModifiers by remember { mutableStateOf(setOf<String>()) }
    var textInput by remember { mutableStateOf("") }
    var showingNumpad by remember { mutableStateOf(false) }
    var showingFnKeys by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        // 顶部栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("键盘", style = MaterialTheme.typography.titleSmall)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = showingFnKeys,
                        onClick = { showingFnKeys = !showingFnKeys },
                        label = { Text("F", fontSize = 12.sp) },
                        leadingIcon = if (showingFnKeys) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }
                        } else null,
                        shape = RoundedCornerShape(8.dp),
                    )
                    FilterChip(
                        selected = showingNumpad,
                        onClick = { showingNumpad = !showingNumpad },
                        label = { Text("123", fontSize = 12.sp) },
                        leadingIcon = if (showingNumpad) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }
                        } else null,
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        }

        // 文本输入框
        TextField(
            value = textInput,
            onValueChange = { textInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("输入文本后发送…") },
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSendMessage(GestureProtocol.TypeText(text = textInput))
                            textInput = ""
                        }
                    },
                    enabled = isConnected && textInput.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Send, "发送")
                }
            },
            shape = RoundedCornerShape(12.dp),
            enabled = isConnected,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
        )

        // 键盘区
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { KeyRow(keys = MODIFIERS, activeModifiers = activeModifiers, onKeyPress = {
                if (it.isToggle) {
                    activeModifiers = if (activeModifiers.contains(it.keyCode))
                        activeModifiers - it.keyCode else activeModifiers + it.keyCode
                }
                onSendMessage(GestureProtocol.KeyTap(key = it.keyCode, modifiers = activeModifiers.toList()))
            }) }
            if (showingFnKeys) {
                item { KeyRow(keys = F_KEYS, activeModifiers = activeModifiers, onKeyPress = {
                    onSendMessage(GestureProtocol.KeyTap(key = it.keyCode, modifiers = activeModifiers.toList()))
                }) }
            }
            item { KeyRow(keys = ROW_1, activeModifiers = activeModifiers, onKeyPress = {
                val key = if (activeModifiers.contains("shift")) it.keyCode.uppercase() else it.keyCode
                onSendMessage(GestureProtocol.KeyTap(key = key, modifiers = activeModifiers.toList()))
            }) }
            item { KeyRow(keys = ROW_2, activeModifiers = activeModifiers, onKeyPress = {
                val key = if (activeModifiers.contains("shift")) it.keyCode.uppercase() else it.keyCode
                onSendMessage(GestureProtocol.KeyTap(key = key, modifiers = activeModifiers.toList()))
            }) }
            item { KeyRow(keys = ROW_3, activeModifiers = activeModifiers, onKeyPress = {
                val key = if (activeModifiers.contains("shift")) it.keyCode.uppercase() else it.keyCode
                onSendMessage(GestureProtocol.KeyTap(key = key, modifiers = activeModifiers.toList()))
            }) }
            item { KeyRow(keys = SPECIALS, activeModifiers = activeModifiers, onKeyPress = {
                onSendMessage(GestureProtocol.KeyTap(key = it.keyCode, modifiers = activeModifiers.toList()))
            }) }
            if (showingNumpad) {
                item { KeyRow(keys = listOf(KeyDef("7","7"),KeyDef("8","8"),KeyDef("9","9")), activeModifiers = activeModifiers, onKeyPress = { onSendMessage(GestureProtocol.KeyTap(key=it.keyCode)) }) }
                item { KeyRow(keys = listOf(KeyDef("4","4"),KeyDef("5","5"),KeyDef("6","6")), activeModifiers = activeModifiers, onKeyPress = { onSendMessage(GestureProtocol.KeyTap(key=it.keyCode)) }) }
                item { KeyRow(keys = listOf(KeyDef("1","1"),KeyDef("2","2"),KeyDef("3","3")), activeModifiers = activeModifiers, onKeyPress = { onSendMessage(GestureProtocol.KeyTap(key=it.keyCode)) }) }
                item { KeyRow(keys = listOf(KeyDef("0","0",width=2),KeyDef(".","period")), activeModifiers = activeModifiers, onKeyPress = { onSendMessage(GestureProtocol.KeyTap(key=it.keyCode)) }) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun KeyRow(
    keys: List<KeyDef>,
    activeModifiers: Set<String>,
    onKeyPress: (KeyDef) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { keyDef ->
            val isActive = activeModifiers.contains(keyDef.keyCode)
            Button(
                onClick = { onKeyPress(keyDef) },
                modifier = Modifier.weight(keyDef.width.toFloat()).height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = keyDef.label,
                    fontSize = if (keyDef.label.length > 2) 12.sp else 14.sp,
                    fontWeight = if (keyDef.label.length == 1) FontWeight.Medium else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
