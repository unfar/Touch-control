package com.touchcontrol.network

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.OutputStream

/**
 * 手机端蓝牙客户端
 *
 * 扫描并连接到平板端蓝牙服务端，发送 JSON 指令。
 */
class BluetoothClient {

    companion object {
        private const val TAG = "BTClient"
    }

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data class Connected(val deviceName: String) : ConnectionState()
        data class Failed(val error: String) : ConnectionState()
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    /** 连接到蓝牙设备 */
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Connecting
        try {
            val sock = device.createRfcommSocketToServiceRecord(
                BluetoothServer.SERVICE_UUID
            )
            withTimeout(10_000L) { sock.connect() }
            socket = sock
            outputStream = sock.outputStream
            _state.value = ConnectionState.Connected(device.name ?: "未知设备")
            Log.i(TAG, "蓝牙已连接到: ${device.name}")
            true
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "蓝牙连接超时 (10s)")
            _state.value = ConnectionState.Failed("连接超时，请确认平板端已启动服务")
            false
        } catch (e: IOException) {
            Log.e(TAG, "蓝牙连接失败", e)
            _state.value = ConnectionState.Failed(e.localizedMessage ?: "连接失败")
            false
        }
    }

    /** 发送 JSON 指令 */
    fun send(json: String) {
        try {
            val data = (json + "\n").toByteArray(Charsets.UTF_8)
            outputStream?.write(data)
            outputStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "蓝牙发送失败", e)
            _state.value = ConnectionState.Failed("发送失败")
        }
    }

    /** 断开连接 */
    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        outputStream = null
        socket = null
        _state.value = ConnectionState.Disconnected
    }
}
