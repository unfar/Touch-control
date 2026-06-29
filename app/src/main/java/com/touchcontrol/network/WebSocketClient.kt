package com.touchcontrol.network

import com.touchcontrol.gesture.GestureProtocol
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val host: String, val port: Int) : ConnectionState()
    data class Failed(val error: String) : ConnectionState()
}

class WebSocketClient {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    /** 当前连接的 Token（手机端发起时设置） */
    @Volatile
    var currentToken: String? = null
        private set

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val incomingMessages = Channel<String>(Channel.BUFFERED)

    /**
     * 连接到 WebSocket 服务端
     *
     * @param host   IP 地址
     * @param port   端口
     * @param token  可选 Token（平板端防劫持用）
     */
    fun connect(host: String, port: Int, token: String? = null) {
        disconnect()
        _connectionState.value = ConnectionState.Connecting
        currentToken = token

        // 构建 URL，附带 Token
        val url = if (token != null) {
            "ws://$host:$port/?token=$token"
        } else {
            "ws://$host:$port/"
        }

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.Connected(host, port)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                incomingMessages.trySend(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = t.localizedMessage ?: "连接失败"
                // 403 表示 Token 被拒绝
                if (response?.code == 403) {
                    _connectionState.value = ConnectionState.Failed("Token 验证失败，请重新扫码")
                } else {
                    _connectionState.value = ConnectionState.Failed(msg)
                }
            }
        })
    }

    fun send(message: Any): Boolean {
        val text = GestureProtocol.encode(message)
        return webSocket?.send(text) ?: false
    }

    fun sendRaw(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "user disconnect")
        webSocket = null
        currentToken = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun ping() {
        send(GestureProtocol.Ping())
    }
}
