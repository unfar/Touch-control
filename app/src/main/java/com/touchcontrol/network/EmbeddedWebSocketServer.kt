package com.touchcontrol.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

/**
 * 嵌入式 WebSocket 服务端
 *
 * 用于平板模式：接收手机端的 WebSocket 连接，解析 JSON 指令，
 * 回调 [onCommand] 让 AccessibilityService 执行触摸模拟。
 *
 * 安全特性：启动时生成随机 Token，连接握手时校验，防劫持。
 */
class EmbeddedWebSocketServer(
    private val port: Int = 9090,
) {
    companion object {
        private const val TAG = "WSServer"
        private val WS_GUID = "258EAFA5-E914-47DA-95CA-5AB9DC11B85B".toByteArray()
        private const val TOKEN_LENGTH = 6
        private const val TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val DISCOVERY_PORT = 9091
        private const val DISCOVERY_MSG = "TOUCHCONTROL_DISCOVER"
        private const val DISCOVERY_RESPONSE = "TOUCHCONTROL_SERVER"
        private const val DEVICE_NAME = "Android平板"

        fun generateToken(): String {
            return (1..TOKEN_LENGTH)
                .map { TOKEN_CHARS[Random.nextInt(TOKEN_CHARS.length)] }
                .joinToString("")
        }
    }

    sealed class ServerState {
        data object Stopped : ServerState()
        data object Starting : ServerState()
        data class Running(val host: String, val port: Int) : ServerState()
        data class Error(val message: String) : ServerState()
    }

    sealed class ClientState {
        data object Disconnected : ClientState()
        data class Connected(val host: String) : ClientState()
    }

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _clientState = MutableStateFlow<ClientState>(ClientState.Disconnected)
    val clientState: StateFlow<ClientState> = _clientState.asStateFlow()

    /** 当前的连接 Token（每次启动随机生成） */
    @Volatile
    var token: String = generateToken()
        private set

    /** 收到指令回调 (JSON 字符串 → Unit) */
    var onCommand: ((String) -> Unit)? = null

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var clientJob: Job? = null
    private var discoveryJob: Job? = null
    private var discoverySocket: DatagramSocket? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastClientActivity = 0L
    private val CLIENT_TIMEOUT_MS = 60_000L

    // ── 公开辅助方法 ──────────────────────────────────

    /** 获取手机端连接的完整 URL */
    fun getConnectionUrl(): String? {
        val state = _serverState.value
        if (state !is ServerState.Running) return null
        return "ws://${state.host}:${state.port}/?token=$token"
    }

    /** 获取二维码编码数据 */
    fun getQrData(): String? {
        val state = _serverState.value
        if (state !is ServerState.Running) return null
        return "touchcontrol://${state.host}:${state.port}/${token}"
    }

    // ── 服务启停 ──────────────────────────────────────

    fun start() {
        if (serverJob?.isActive == true) return
        token = generateToken() // 每次启动重新生成
        _serverState.value = ServerState.Starting

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                serverSocket?.reuseAddress = true
                serverSocket?.soTimeout = 5000

                val host = getLocalIpAddress()
                _serverState.value = ServerState.Running(host ?: "0.0.0.0", port)
                Log.i(TAG, "服务端已启动: $host:$port  token=$token")

                // 启动 UDP 发现响应器
                launchDiscoveryResponder(host ?: "0.0.0.0")

                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: continue
                        Log.i(TAG, "收到连接: ${socket.inetAddress.hostAddress}")

                        disconnectClient()
                        clientSocket = socket
                        handleClient(socket)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "服务端异常", e)
                _serverState.value = ServerState.Error(e.localizedMessage ?: "服务端异常")
            } finally {
                cleanup()
            }
        }
    }

    fun stop() {
        disconnectClient()
        serverJob?.cancel()
        serverJob = null
        serverSocket?.close()
        serverSocket = null
        discoveryJob?.cancel()
        discoveryJob = null
        discoverySocket?.close()
        discoverySocket = null
        _serverState.value = ServerState.Stopped
    }

    private fun disconnectClient() {
        clientJob?.cancel()
        clientJob = null
        clientSocket?.close()
        clientSocket = null
        _clientState.value = ClientState.Disconnected
    }

    // ── WebSocket 连接处理 ────────────────────────────

    private suspend fun handleClient(socket: Socket) = coroutineScope {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        // 1. WebSocket 握手 + Token 验证
        val handshakeResult = performHandshake(input, output, socket)
        if (handshakeResult != true) {
            socket.close()
            return@coroutineScope
        }
        Log.i(TAG, "WebSocket 握手成功 + Token 验证通过")
        _clientState.value = ClientState.Connected(
            socket.inetAddress.hostAddress ?: "unknown"
        )
        lastClientActivity = System.currentTimeMillis()

        // 2. 读取帧循环
        clientJob = launch {
            try {
                val buffer = ByteArray(8192)
                while (isActive) {
                    if (System.currentTimeMillis() - lastClientActivity > CLIENT_TIMEOUT_MS) {
                        Log.i(TAG, "客户端超时，断开")
                        break
                    }

                    val header = ByteArray(2)
                    val headerRead = readFully(input, header, 2)
                    if (headerRead < 2) break

                    val opcode = header[0].toInt() and 0x0F
                    val masked = (header[1].toInt() and 0x80) != 0
                    var payloadLen = (header[1].toInt() and 0x7F).toLong()

                    when {
                        payloadLen == 126L -> {
                            val ext = ByteArray(2)
                            readFully(input, ext, 2)
                            payloadLen = (((ext[0].toInt() and 0xFF) shl 8) or (ext[1].toInt() and 0xFF)).toLong()
                        }
                        payloadLen == 127L -> {
                            val ext = ByteArray(8)
                            readFully(input, ext, 8)
                            payloadLen = 0L
                            for (i in 0..7) {
                                payloadLen = (payloadLen shl 8) or ((ext[i].toInt() and 0xFF).toLong())
                            }
                        }
                    }

                    val maskKey = if (masked) {
                        val key = ByteArray(4)
                        readFully(input, key, 4)
                        key
                    } else null

                    val payload = ByteArray(payloadLen.toInt())
                    readFully(input, payload, payloadLen.toInt())

                    if (masked && maskKey != null) {
                        for (i in payload.indices) {
                            payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                        }
                    }

                    lastClientActivity = System.currentTimeMillis()

                    when (opcode) {
                        0x1 -> { // 文本帧
                            val text = String(payload, Charsets.UTF_8)
                            onCommand?.invoke(text)
                        }
                        0x8 -> { // 关闭帧
                            Log.i(TAG, "客户端发送关闭帧")
                            sendFrame(output, 0x8, ByteArray(0))
                            break
                        }
                        0x9 -> sendFrame(output, 0xA, ByteArray(0)) // Ping → Pong
                        0xA -> {} // Pong 忽略
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "客户端连接断开", e)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "客户端处理异常", e) }
            finally {
                _clientState.value = ClientState.Disconnected
                try { socket.close() } catch (_: Exception) {}
            }
        }
        clientJob?.join()
    }

    /**
     * WebSocket 握手 + Token 校验
     *
     * 客户端连接 URL: ws://host:port/?token=XXXX
     * 从 HTTP GET 路径中提取 token 查询参数并校验。
     *
     * @return true=握手成功且 Token 正确, false=拒绝连接
     */
    private fun performHandshake(
        input: InputStream,
        output: OutputStream,
        socket: Socket,
    ): Boolean {
        try {
            val reader = BufferedReader(InputStreamReader(input))
            val requestLine = reader.readLine() ?: return false

            if (!requestLine.startsWith("GET ")) return false

            // 提取路径中的 token 参数
            val parts = requestLine.split(" ")
            val path = parts.getOrNull(1) ?: return false

            val clientToken = extractTokenFromPath(path)
            if (clientToken == null || clientToken != token) {
                Log.w(TAG, "❌ Token 不匹配: 期望=$token, 收到=$clientToken")
                // 返回 403
                val reject = "HTTP/1.1 403 Forbidden\r\n\r\nInvalid token"
                output.write(reject.toByteArray())
                output.flush()
                return false
            }

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                if (l.isBlank()) break
                val colon = l.indexOf(':')
                if (colon > 0) {
                    headers[l.substring(0, colon).trim().lowercase()] =
                        l.substring(colon + 1).trim()
                }
            }

            val wsKey = headers["sec-websocket-key"] ?: return false
            val accept = computeAcceptKey(wsKey)

            val response = buildString {
                append("HTTP/1.1 101 Switching Protocols\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Accept: $accept\r\n")
                append("\r\n")
            }

            output.write(response.toByteArray())
            output.flush()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "握手失败", e)
            return false
        }
    }

    /** 从请求路径中提取 token 查询参数 */
    private fun extractTokenFromPath(path: String): String? {
        val qIndex = path.indexOf('?')
        if (qIndex < 0) return null
        val query = path.substring(qIndex + 1)
        return query.split("&")
            .firstOrNull { it.startsWith("token=") }
            ?.substringAfter("token=")
            ?.takeIf { it.isNotBlank() }
    }

    /** 发送 WebSocket 帧 */
    private fun sendFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
        try {
            val frame = ByteArrayOutputStream()
            frame.write(0x80 or opcode)
            if (payload.size < 126) {
                frame.write(payload.size)
            } else {
                frame.write(126)
                frame.write((payload.size shr 8) and 0xFF)
                frame.write(payload.size and 0xFF)
            }
            frame.write(payload)
            output.write(frame.toByteArray())
            output.flush()
        } catch (_: Exception) {}
    }

    // ── 工具 ───────────────────────────────────────────

    private fun computeAcceptKey(key: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(key.toByteArray())
        sha1.update(WS_GUID)
        return Base64.getEncoder().encodeToString(sha1.digest())
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, total, length - total)
            if (read < 0) return total
            total += read
        }
        return total
    }

    private fun cleanup() {
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        try { discoverySocket?.close() } catch (_: Exception) {}
        clientSocket = null
        serverSocket = null
        discoverySocket = null
        _clientState.value = ClientState.Disconnected
    }

    /**
     * 启动 UDP 发现响应器
     * 监听 DISCOVERY_PORT，收到发现消息后回复服务信息
     */
    private fun launchDiscoveryResponder(localHost: String) {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            try {
                val socket = DatagramSocket(DISCOVERY_PORT)
                discoverySocket = socket
                socket.broadcast = true
                socket.soTimeout = 5000
                Log.i(TAG, "UDP 发现响应器已启动，端口: $DISCOVERY_PORT")

                val buffer = ByteArray(256)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        if (msg == DISCOVERY_MSG) {
                            val response = "$DISCOVERY_RESPONSE:$port:$DEVICE_NAME:$token"
                            val respData = response.toByteArray()
                            val respPacket = DatagramPacket(
                                respData, respData.size,
                                packet.address, packet.port
                            )
                            socket.send(respPacket)
                            Log.i(TAG, "已响应发现请求: ${packet.address.hostAddress}")
                        }
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "UDP 发现响应器异常", e)
            } finally {
                try { discoverySocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.flatMap { it.inetAddresses?.asSequence() ?: emptySequence() }
                ?.firstOrNull {
                    !it.isLoopbackAddress &&
                    it is Inet4Address &&
                    !it.hostAddress.startsWith("169.254")
                }
                ?.hostAddress
        } catch (e: Exception) { null }
    }
}
