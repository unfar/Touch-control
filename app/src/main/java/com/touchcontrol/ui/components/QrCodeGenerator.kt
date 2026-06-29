package com.touchcontrol.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二维码生成工具
 *
 * 用于平板端：将连接信息编码为二维码，供手机扫码连接。
 * 数据格式: touchcontrol://IP:PORT/TOKEN
 */
object QrCodeGenerator {

    /**
     * 生成 QR 码 Bitmap
     *
     * @param content  二维码内容
     * @param size     输出尺寸（像素），默认 512
     * @return Bitmap 或 null（内容过长时）
     */
    fun generate(content: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    )
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析扫码数据
     *
     * 格式: touchcontrol://IP:PORT/TOKEN
     * 或: touchcontrol://connect?host=IP&port=PORT&token=TOKEN
     *
     * @return Triple(host, port, token) 或 null
     */
    fun parseQrData(data: String): Triple<String, Int, String>? {
        return try {
            val uri = data.trim()

            // 格式1: touchcontrol://192.168.1.100:9090/ABC123
            if (uri.startsWith("touchcontrol://")) {
                val rest = uri.removePrefix("touchcontrol://")
                val slashIndex = rest.lastIndexOf('/')
                if (slashIndex > 0) {
                    val hostPort = rest.substring(0, slashIndex)
                    val token = rest.substring(slashIndex + 1)
                    val colonIndex = hostPort.lastIndexOf(':')
                    if (colonIndex > 0) {
                        val host = hostPort.substring(0, colonIndex)
                        val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: 9090
                        return Triple(host, port, token)
                    }
                }
            }

            // 格式2: touchcontrol://connect?host=192.168.1.100&port=9090&token=ABC123
            if (uri.contains("?")) {
                val query = uri.substringAfter("?")
                val params = query.split("&").associate {
                    val kv = it.split("=", limit=2)
                    kv[0] to (kv.getOrNull(1) ?: "")
                }
                val host = params["host"]
                val port = params["port"]?.toIntOrNull() ?: 9090
                val token = params["token"]
                if (host != null && token != null) {
                    return Triple(host, port, token)
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }
}
