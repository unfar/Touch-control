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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.Manifest
import android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED
import android.bluetooth.BluetoothDevice.ACTION_FOUND
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
            val service = TouchControlService.instance
            if (service != null) {
                service.executeCommand(json)
            } else {
                android.util.Log.e("TouchControl", "TouchControlService.instance 为 null，无法执行指令: $json")
            }
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

// ── 底部导航项 ──

data class BottomNavItem(
    val screen: Screen,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

// ── 辅助函数 ──

fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    // 方法1: 实例检测（仅对 TouchControlService 有效）
    if (serviceClass == com.touchcontrol.accessibility.TouchControlService::class.java) {
        if (com.touchcontrol.accessibility.TouchControlService.instance != null) return true
    }
    // 方法2: 从系统设置检测（ColorOS/Android 16 兼容）
    try {
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabledServices?.contains(context.packageName) == true) return true
    } catch (_: Exception) {}
    // 方法3: AccessibilityManager API（部分设备可能不返回结果）
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return list.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
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
        var isServiceRunning by remember {
            mutableStateOf(isAccessibilityServiceEnabled(context, TouchControlService::class.java))
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isServiceRunning = isAccessibilityServiceEnabled(
                        context, TouchControlService::class.java
                    )
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 自动启动蓝牙服务端
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val btAdapter = bluetoothManager?.adapter
        val btEnabled = btAdapter?.isEnabled == true
        LaunchedEffect(Unit) {
            if (btEnabled) {
                bluetoothServer.start(btAdapter!!)
            }
        }

        TabletReceiverScreen(
            bluetoothServer = bluetoothServer,
            isServiceRunning = isServiceRunning,
            btEnabled = btEnabled,
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
    var selectedScreen: Screen by remember { mutableStateOf(Screen.Touchpad) }
    var showBtScan by remember { mutableStateOf(false) }

    val btState by bluetoothClient.state.collectAsState()
    val cursorSpeed by settingsRepository.cursorSpeed.collectAsState(initial = 1f)
    val scrollSpeed by settingsRepository.scrollSpeed.collectAsState(initial = 1f)

    // 蓝牙扫描状态
    var scannedBtDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isBtScanning by remember { mutableStateOf(false) }

    // ── 实际扫描逻辑（权限已确保） ──
    val doScan: () -> Unit = {
        android.util.Log.i("TouchControl", ">>> doScan() called")
        scope.launch {
            isBtScanning = true
            scannedBtDevices = emptyList()
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val btAdapter = bluetoothManager?.adapter
            android.util.Log.i("TouchControl", "btManager=$bluetoothManager btAdapter=$btAdapter enabled=${btAdapter?.isEnabled}")
            if (btAdapter?.isEnabled == true) {
                val bonded = withContext(Dispatchers.IO) {
                    btAdapter.bondedDevices?.toList() ?: emptyList()
                }
                android.util.Log.i("TouchControl", "bondedDevices count=${bonded.size}")
                bonded.forEach { android.util.Log.i("TouchControl", "  bonded: ${it.name} addr=${it.address}") }
                scannedBtDevices = bonded
                android.util.Log.i("TouchControl", "calling startDiscovery()")
                btAdapter.startDiscovery()
            } else {
                android.util.Log.i("TouchControl", "BT not enabled!")
                isBtScanning = false
            }
        }
    }

    // ── 运行时权限请求 ──
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.entries.all { it.value }
        if (allGranted) {
            showBtScan = true
            doScan()
        }
    }

    // 进入蓝牙列表时自动扫描
    LaunchedEffect(showBtScan) {
        if (showBtScan) {
            doScan()
        }
    }
    // ── 蓝牙连接 ──
    fun connectBluetooth(device: BluetoothDevice) {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        mgr?.adapter?.cancelDiscovery()
        scope.launch {
            bluetoothClient.connect(device)
        }
    }

    // ── 蓝牙发现广播接收器 ──
    val discoveryReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (device != null) {
                            scannedBtDevices = (scannedBtDevices + device).distinctBy { it.address }
                        }
                    }
                    ACTION_DISCOVERY_FINISHED -> {
                        isBtScanning = false
                    }
                }
            }
        }
    }

    // 注册/注销广播
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(ACTION_FOUND)
            addAction(ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
        onDispose {
            context.unregisterReceiver(discoveryReceiver)
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            mgr?.adapter?.cancelDiscovery()
        }
    }

    // 防误退：再按一次退出
    var backPressCount by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 蓝牙连接成功后自动返回触摸板
    LaunchedEffect(btState) {
        if (btState is BluetoothClient.ConnectionState.Connected) {
            showBtScan = false
        }
    }

    LaunchedEffect(backPressCount) {
        if (backPressCount > 0) {
            snackbarHostState.showSnackbar(
                message = "再按一次退出 TouchControl",
                duration = SnackbarDuration.Short,
            )
            kotlinx.coroutines.delay(2000)
            backPressCount = 0
        }
    }

    BackHandler {
        if (backPressCount == 0) {
            backPressCount = 1
        } else {
            context.run { (this as? android.app.Activity)?.finish() }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    onScan = { doScan() },
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
                            onNavigateToConnection = {
                                val hasPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                } else true
                                if (!hasPerms) {
                                    permissionLauncher.launch(arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ))
                                } else {
                                    showBtScan = true
                                }
                            },
                        )
                    }
                    Screen.Keyboard -> {
                        KeyboardScreen(
                            btState = btState,
                            onSendMessage = { msg ->
                                bluetoothClient.send(com.touchcontrol.gesture.GestureProtocol.encode(msg))
                                true
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
                }
            }
        }
    }
}
