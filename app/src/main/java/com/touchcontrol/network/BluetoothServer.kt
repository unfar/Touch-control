package com.touchcontrol.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * 平板端蓝牙服务端
 *
 * 手机通过蓝牙 RFCOMM 连接后，发送 JSON 指令，
 * 解析后回调 [onCommand] 让 AccessibilityService 执行。
 */
class BluetoothServer {

    companion object {
        private const val TAG = "BTServer"
        val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        private const val SERVICE_NAME = "TouchControl"
    }

    sealed class ServerState {
        data object Stopped : ServerState()
        data object Listening : ServerState()
        data class Connected(val deviceName: String) : ServerState()
        data class Error(val message: String) : ServerState()
    }

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    /** 收到指令回调 (JSON 字符串 → Unit) */
    var onCommand: ((String) -> Unit)? = null

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var listenJob: Job? = null
    private var readJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 启动蓝牙服务端监听 */
    fun start(adapter: BluetoothAdapter) {
        stop()
        adapter.cancelDiscovery()
        _state.value = ServerState.Listening
        Log.i(TAG, "蓝牙服务端启动中...")

        listenJob = scope.launch {
            try {
                val socket = adapter.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME, SERVICE_UUID
                )
                serverSocket = socket
                Log.i(TAG, "蓝牙服务端正在监听...")

                // 等待连接
                val client = socket.accept()
                clientSocket = client
                val deviceName = client.remoteDevice.name ?: "未知设备"
                _state.value = ServerState.Connected(deviceName)
                Log.i(TAG, "蓝牙已连接: $deviceName")

                // 读取数据
                startReading(client)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "蓝牙监听异常", e)
                _state.value = ServerState.Error(e.localizedMessage ?: "蓝牙监听失败")
            }
        }
    }

    /** 读取客户端发送的数据 */
    private fun startReading(socket: BluetoothSocket) {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                val input: InputStream = socket.inputStream
                val buffer = ByteArray(4096)
                val sb = StringBuilder()

                while (isActive) {
                    val bytes = input.read(buffer)
                    if (bytes < 0) break

                    sb.append(String(buffer, 0, bytes, Charsets.UTF_8))

                    // 处理完整消息（以换行分隔）
                    val text = sb.toString()
                    val lines = text.split("\n")
                    sb.clear()
                    // 最后一段可能不完整，保留
                    for (i in 0 until lines.size - 1) {
                        val msg = lines[i].trim()
                        if (msg.isNotEmpty()) {
                            Log.i(TAG, "收到指令: $msg")
                            onCommand?.invoke(msg)
                        }
                    }
                    if (lines.last().isNotEmpty()) {
                        sb.append(lines.last())
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "蓝牙读取断开", e)
            } finally {
                _state.value = ServerState.Stopped
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    /** 停止蓝牙服务端 */
    fun stop() {
        listenJob?.cancel()
        listenJob = null
        readJob?.cancel()
        readJob = null
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        serverSocket = null
        _state.value = ServerState.Stopped
    }
}
