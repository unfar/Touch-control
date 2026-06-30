package com.touchcontrol.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.*

/**
 * 简单的局域网服务发现
 *
 * 手机在局域网广播服务器名称 "TouchControlServer"
 * 这里用 UDP + 服务名广播的方式发现
 *
 * 快速方案：发送 UDP 发现包，服务端响应
 */
class ServerDiscovery(private val context: Context) {

    companion object {
        private const val DISCOVERY_PORT = 9091
        private const val DISCOVERY_MSG = "TOUCHCONTROL_DISCOVER"
        private const val RESPONSE_PREFIX = "TOUCHCONTROL_SERVER"
        private const val TIMEOUT_MS = 2000L
    }

    /** 返回发现的服务器列表 (host:port) */
    suspend fun discover(timeoutMs: Long = TIMEOUT_MS): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val broadcastAddr = wifiInfo?.let {
            getBroadcastAddress()
        } ?: "255.255.255.255"

        val socket = DatagramSocket()
        socket.broadcast = true
        socket.soTimeout = timeoutMs.toInt()

        // 发送发现广播
        val sendData = DISCOVERY_MSG.toByteArray()
        val sendPacket = DatagramPacket(
            sendData, sendData.size,
            InetAddress.getByName(broadcastAddr),
            DISCOVERY_PORT
        )
        socket.send(sendPacket)

        // 等待响应
        val servers = mutableListOf<DiscoveredServer>()
        val buffer = ByteArray(256)
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val response = String(packet.data, 0, packet.length, Charsets.UTF_8)

                if (response.startsWith(RESPONSE_PREFIX)) {
                    val parts = response.removePrefix(RESPONSE_PREFIX).trim().split(":")
                    val host = packet.address.hostAddress ?: continue
                    val port = parts.getOrNull(0)?.toIntOrNull() ?: 9090
                    val name = parts.getOrNull(1) ?: "Unknown"
                    val token = parts.getOrNull(2)?.takeIf { it.isNotBlank() }

                    if (servers.none { it.host == host && it.port == port }) {
                        servers.add(DiscoveredServer(host, port, name, token))
                    }
                }
            } catch (e: SocketTimeoutException) {
                break
            }
        }

        socket.close()
        servers.toList()
    }

    private fun getBroadcastAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) return broadcast.hostAddress
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}

data class DiscoveredServer(
    val host: String,
    val port: Int,
    val name: String,
    val token: String? = null,
)
