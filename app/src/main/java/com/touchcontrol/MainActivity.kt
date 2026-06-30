package com.touchcontrol

import android.accessibilityservice.AccessibilityServiceInfo
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.touchcontrol.accessibility.TouchControlService
import com.touchcontrol.data.SettingsRepository
import com.touchcontrol.network.BluetoothServer
import com.touchcontrol.network.BluetoothClient
import com.touchcontrol.ui.navigation.Screen
import com.touchcontrol.ui.screens.*
import com.touchcontrol.ui.theme.TouchControlTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothServer: BluetoothServer
    private lateinit var bluetoothClient: BluetoothClient
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bluetoothServer = BluetoothServer()
        bluetoothClient = BluetoothClient()
        settingsRepository = SettingsRepository(this)

        // 指令路由到无障碍服务
        val commandHandler: (String) -> Unit = { json ->
            TouchControlService.instance?.executeCommand(json)
        }
        bluetoothServer.onCommand = commandHandler

        setContent {
            val darkMode by settingsRepository.darkMode.collectAsState(initial = true)

            TouchControlTheme(darkTheme = darkMode) {
                MainApp(
                    bluetoothServer = bluetoothServer,
                    bluetoothClient = bluetoothClient,
                    settingsRepository = settingsRepository,
                    context = this@MainActivity,
                )
            }
        }
    }

    override fun onDestroy() {
        bluetoothClient.disconnect()
        bluetoothServer.stop()
        super.onDestroy()
    }
}

// ── 模式枚举 ──

enum class AppMode {
    NOT_SELECTED,
    PHONE_CONTROLLER,
    TABLET_RECEIVER,
}

// ── 底部导航项 ──

data class BottomNavItem(
    val screen: Screen,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

// ── 辅助函数 ──

fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_GENERIC
    )
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

@Composable
fun MainApp(
    bluetoothServer: BluetoothServer,
    bluetoothClient: BluetoothClient,
    settingsRepository: SettingsRepository,
    context: Context,
) {
    var currentMode by remember { mutableStateOf(AppMode.NOT_SELECTED) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val saved = settingsRepository.getAppMode()
        currentMode = saved
    }

    // ── 模式选择 ──
    if (currentMode == AppMode.NOT_SELECTED) {
        ModeSelectionScreen(
            onModeSelected = { mode ->
                currentMode = mode
                scope.launch { settingsRepository.saveAppMode(mode) }
            }
        )
        return
    }

    // ═══════════════════════════════════════════
    // 平板模式（被控端 — 运行蓝牙服务端）
    // ═══════════════════════════════════════════
    if (currentMode == AppMode.TABLET_RECEIVER) {
        val isServiceRunning = remember {
            isAccessibilityServiceEnabled(context, TouchControlService::class.java)
        }

        // 自动启动蓝牙服务端
        LaunchedEffect(Unit) {
            val btAdapter = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothAdapter
            if (btAdapter?.isEnabled == true) {
                bluetoothServer.start(btAdapter)
            }
        }

        TabletReceiverScreen(
            bluetoothServer = bluetoothServer,
            isServiceRunning = isServiceRunning,
            onToggleService = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            onSwitchMode = {
                currentMode = AppMode.NOT_SELECTED
                bluetoothServer.stop()
                scope.launch { settingsRepository.saveAppMode(AppMode.NOT_SELECTED) }
            },
        )
        return
    }

    // ═══════════════════════════════════════════
    // 手机模式（控制器 — 蓝牙扫描 + 触摸板）
    // ═══════════════════════════════════════════
    var selectedScreen by remember { mutableStateOf(Screen.Touchpad) }
    var showBtScan by remember { mutableStateOf(false) }

    val btState by bluetoothClient.state.collectAsState()
    val cursorSpeed by settingsRepository.cursorSpeed.collectAsState(initial = 1f)
    val scrollSpeed by settingsRepository.scrollSpeed.collectAsState(initial = 1f)

    // 蓝牙扫描状态
    var scannedBtDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isBtScanning by remember { mutableStateOf(false) }

    fun scanBluetooth() {
        scope.launch {
            isBtScanning = true
            scannedBtDevices = withContext(Dispatchers.IO) {
                val btAdapter = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothAdapter
                if (btAdapter?.isEnabled == true) {
                    btAdapter.bondedDevices?.filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }?.toList()
                        ?: emptyList()
                } else emptyList()
            }
            isBtScanning = false
        }
    }

    fun connectBluetooth(device: BluetoothDevice) {
        scope.launch {
            bluetoothClient.connect(device)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = !showBtScan,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    listOf(
                        BottomNavItem(Screen.Touchpad, Icons.Filled.TouchApp, "触摸板"),
                        BottomNavItem(Screen.Keyboard, Icons.Filled.Keyboard, "键盘"),
                        BottomNavItem(Screen.Settings, Icons.Filled.Settings, "设置"),
                    ).forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selectedScreen == item.screen,
                            onClick = { selectedScreen = item.screen },
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (showBtScan) {
                // 蓝牙设备列表页
                BtDeviceListScreen(
                    btState = btState,
                    scannedDevices = scannedBtDevices,
                    isScanning = isBtScanning,
                    onScan = { scanBluetooth() },
                    onConnect = { device -> connectBluetooth(device) },
                    onDisconnect = { bluetoothClient.disconnect() },
                    onBack = { showBtScan = false },
                    onSwitchMode = {
                        currentMode = AppMode.NOT_SELECTED
                        bluetoothClient.disconnect()
                        scope.launch { settingsRepository.saveAppMode(AppMode.NOT_SELECTED) }
                    },
                )
            } else {
                when (selectedScreen) {
                    Screen.Touchpad -> {
                        TouchpadScreen(
                            btState = btState,
                            cursorSpeed = cursorSpeed,
                            scrollSpeed = scrollSpeed,
                            onSendMessage = { msg ->
                                if (btState is BluetoothClient.ConnectionState.Connected) {
                                    bluetoothClient.send(
                                        com.touchcontrol.gesture.GestureProtocol.encode(msg)
                                    )
                                }
                                true
                            },
                            onNavigateToConnection = { showBtScan = true },
                        )
                    }
                    Screen.Keyboard -> {
                        KeyboardScreen(
                            btState = btState,
                            onSendMessage = { msg ->
                                bluetoothClient.send(com.touchcontrol.gesture.GestureProtocol.encode(msg))
                            },
                        )
                    }
                    Screen.Settings -> {
                        SettingsScreen(
                            settingsRepository = settingsRepository,
                            onSwitchMode = {
                                currentMode = AppMode.NOT_SELECTED
                                bluetoothClient.disconnect()
                                scope.launch { settingsRepository.saveAppMode(AppMode.NOT_SELECTED) }
                            },
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
