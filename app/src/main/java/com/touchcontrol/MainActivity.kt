package com.touchcontrol

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.touchcontrol.accessibility.TouchControlService
import com.touchcontrol.data.SettingsRepository
import com.touchcontrol.network.ConnectionState
import com.touchcontrol.network.DiscoveredServer
import com.touchcontrol.network.EmbeddedWebSocketServer
import com.touchcontrol.network.ServerDiscovery
import com.touchcontrol.network.WebSocketClient
import com.touchcontrol.ui.navigation.Screen
import com.touchcontrol.ui.screens.*
import com.touchcontrol.ui.theme.TouchControlTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var webSocketClient: WebSocketClient
    private lateinit var webSocketServer: EmbeddedWebSocketServer
    private lateinit var serverDiscovery: ServerDiscovery
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        webSocketClient = WebSocketClient()
        webSocketServer = EmbeddedWebSocketServer()
        serverDiscovery = ServerDiscovery(this)
        settingsRepository = SettingsRepository(this)

        // 平板模式：连接辅助服务和 WebSocket 服务端
        webSocketServer.onCommand = { json ->
            TouchControlService.instance?.executeCommand(json)
        }

        setContent {
            val darkMode by settingsRepository.darkMode.collectAsState(initial = true)

            TouchControlTheme(darkTheme = darkMode) {
                MainApp(
                    webSocketClient = webSocketClient,
                    webSocketServer = webSocketServer,
                    serverDiscovery = serverDiscovery,
                    settingsRepository = settingsRepository,
                    context = this@MainActivity,
                )
            }
        }
    }

    override fun onDestroy() {
        webSocketClient.disconnect()
        webSocketServer.stop()
        super.onDestroy()
    }
}

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
)

/**
 * 检查无障碍服务是否已开启
 */
fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_GENERIC
    )
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

@Composable
fun MainApp(
    webSocketClient: WebSocketClient,
    webSocketServer: EmbeddedWebSocketServer,
    serverDiscovery: ServerDiscovery,
    settingsRepository: SettingsRepository,
    context: Context,
) {
    // 当前模式（持久化记住）
    var currentMode by remember { mutableStateOf(AppMode.NOT_SELECTED) }
    val scope = rememberCoroutineScope()

    // 首次启动检查已保存的模式
    LaunchedEffect(Unit) {
        val saved = settingsRepository.getAppMode()
        currentMode = saved
    }

    // 如果未选择模式 → 显示选择页
    if (currentMode == AppMode.NOT_SELECTED) {
        ModeSelectionScreen(
            onModeSelected = { mode ->
                currentMode = mode
                scope.launch { settingsRepository.saveAppMode(mode) }
            }
        )
        return
    }

    // 平板模式
    if (currentMode == AppMode.TABLET_RECEIVER) {
        val isServiceRunning = remember {
            isAccessibilityServiceEnabled(context, TouchControlService::class.java)
        }

        TabletReceiverScreen(
            server = webSocketServer,
            isServiceRunning = isServiceRunning,
            onToggleService = {
                // 打开无障碍设置
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            onSwitchMode = {
                currentMode = AppMode.NOT_SELECTED
                webSocketServer.stop()
                scope.launch { settingsRepository.saveAppMode(AppMode.NOT_SELECTED) }
            },
        )
        return
    }

    // ── 手机模式 ──────────────────────────────────────
    var selectedScreen by remember { mutableStateOf(Screen.Touchpad) }
    var showConnection by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var discoveredServers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    val connectionState by webSocketClient.connectionState.collectAsState()
    val savedHost by settingsRepository.host.collectAsState(initial = "")
    val savedPort by settingsRepository.port.collectAsState(initial = 9090)
    val cursorSpeed by settingsRepository.cursorSpeed.collectAsState(initial = 1f)
    val scrollSpeed by settingsRepository.scrollSpeed.collectAsState(initial = 1f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = !showConnection,
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
            if (showScanner) {
                // QR 扫码器
                com.touchcontrol.ui.components.QrCodeScanner(
                    onCodeScanned = { data ->
                        val parsed = com.touchcontrol.ui.components.QrCodeGenerator.parseQrData(data)
                        if (parsed != null) {
                            val (host, port, token) = parsed
                            scope.launch {
                                settingsRepository.saveHost(host)
                                settingsRepository.savePort(port)
                                webSocketClient.connect(host, port, token)
                            }
                        }
                        showScanner = false
                    },
                    onClose = { showScanner = false },
                )
            } else if (showConnection) {
                ConnectionScreenWrapper(
                    connectionState = connectionState,
                    savedHost = savedHost,
                    savedPort = savedPort,
                    discoveredServers = discoveredServers,
                    isScanning = isScanning,
                    onHostChange = { host -> scope.launch { settingsRepository.saveHost(host) } },
                    onPortChange = { port -> scope.launch { settingsRepository.savePort(port) } },
                    onConnect = {
                        if (savedHost.isNotBlank()) {
                            webSocketClient.connect(savedHost, savedPort)
                        }
                    },
                    onDisconnect = { webSocketClient.disconnect() },
                    onScan = {
                        scope.launch {
                            isScanning = true
                            discoveredServers = serverDiscovery.discover(3000)
                            isScanning = false
                        }
                    },
                    onSelectServer = { server ->
                        scope.launch {
                            settingsRepository.saveHost(server.host)
                            settingsRepository.savePort(server.port)
                            webSocketClient.connect(server.host, server.port)
                        }
                    },
                    onBack = { showConnection = false },
                    onSwitchMode = {
                        currentMode = AppMode.NOT_SELECTED
                        webSocketClient.disconnect()
                        scope.launch { settingsRepository.saveAppMode(AppMode.NOT_SELECTED) }
                    },
                    onStartScan = { showScanner = true },
                )
            } else {
                when (selectedScreen) {
                    Screen.Touchpad -> {
                        TouchpadScreen(
                            connectionState = connectionState,
                            cursorSpeed = cursorSpeed,
                            scrollSpeed = scrollSpeed,
                            onSendMessage = { msg -> webSocketClient.send(msg) },
                            onNavigateToConnection = { showConnection = true },
                        )
                    }
                    Screen.Keyboard -> {
                        KeyboardScreen(
                            connectionState = connectionState,
                            onSendMessage = { msg -> webSocketClient.send(msg) },
                        )
                    }
                    Screen.Settings -> {
                        SettingsScreen(
                            settingsRepository = settingsRepository,
                            onSwitchMode = {
                                currentMode = AppMode.NOT_SELECTED
                                webSocketClient.disconnect()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreenWrapper(
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
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    onStartScan: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接电脑") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onSwitchMode) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "切换模式")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ConnectionScreen(
                connectionState = connectionState,
                savedHost = savedHost,
                savedPort = savedPort,
                discoveredServers = discoveredServers,
                isScanning = isScanning,
                onHostChange = onHostChange,
                onPortChange = onPortChange,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onScan = onScan,
                onSelectServer = onSelectServer,
                onStartScan = onStartScan,
            )
        }
    }
}
