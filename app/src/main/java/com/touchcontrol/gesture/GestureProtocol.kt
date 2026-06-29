package com.touchcontrol.gesture

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * WebSocket 通信协议 —— 手机端发给服务端的全部指令
 */
object GestureProtocol {

    private val json = Json { ignoreUnknownKeys = true }

    // ── 鼠标指令 ──────────────────────────────────────

    /** 鼠标移动（相对位移） */
    @Serializable
    data class MouseMove(
        val type: String = "mouse",
        val action: String = "move",
        val dx: Float,
        val dy: Float,
    )

    /** 鼠标按键 */
    @Serializable
    data class MouseClick(
        val type: String = "mouse",
        val action: String,
        val button: String = "left",
    )

    /** 鼠标滚轮 */
    @Serializable
    data class MouseScroll(
        val type: String = "mouse",
        val action: String = "scroll",
        val dy: Float,
        val dx: Float = 0f,
    )

    // ── 键盘指令 ──────────────────────────────────────

    @Serializable
    data class KeyPress(
        val type: String = "key",
        val action: String = "press",
        val key: String,
        val modifiers: List<String> = emptyList(),
    )

    @Serializable
    data class KeyRelease(
        val type: String = "key",
        val action: String = "release",
        val key: String,
        val modifiers: List<String> = emptyList(),
    )

    @Serializable
    data class KeyTap(
        val type: String = "key",
        val action: String = "tap",
        val key: String,
        val modifiers: List<String> = emptyList(),
    )

    /** 输入一段文本 */
    @Serializable
    data class TypeText(
        val type: String = "key",
        val action: String = "type",
        val text: String,
    )

    // ── 系统指令 ──────────────────────────────────────

    @Serializable
    data class Ping(
        val type: String = "system",
        val action: String = "ping",
    )

    @Serializable
    data class Pong(
        val type: String = "system",
        val action: String = "pong",
    )

    // ── 序列化 ────────────────────────────────────────

    fun encode(message: Any): String = json.encodeToString(message)
}
