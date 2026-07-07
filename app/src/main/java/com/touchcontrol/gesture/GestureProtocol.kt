package com.touchcontrol.gesture

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * WebSocket 通信协议 —— 手机端发给服务端的全部指令
 */
object GestureProtocol {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── 触摸指令 ──────────────────────────────────────

    /** 触摸按下（手指放到触摸板上 — 带绝对位置） */
    @Serializable
    data class TouchDown(
        val type: String = "touch",
        val action: String = "down",
        val pctX: Float = 0.5f,  // 0.0~1.0
        val pctY: Float = 0.5f,  // 0.0~1.0
    )

    /** 触摸抬起（手指离开触摸板） */
    @Serializable
    data class TouchUp(
        val type: String = "touch",
        val action: String = "up",
    )

    /** 触摸移动（手指在触摸板上滑动 — 绝对百分比坐标 0.0~1.0） */
    @Serializable
    data class TouchMove(
        val type: String = "touch",
        val action: String = "move",
        val pctX: Float,  // 0.0~1.0 触摸板上的百分比位置
        val pctY: Float,  // 0.0~1.0 触摸板上的百分比位置
    )

    // ── 鼠标指令（兼容） ──────────────────────────────

    /** 鼠标移动（相对位移） */
    @Serializable
    data class MouseMove(
        val type: String = "mouse",
        val action: String = "move",
        val dx: Float,
        val dy: Float,
    )

    /** 光标绝对定位（仅移动覆盖层，不注入触摸） */
    @Serializable
    data class CursorAbs(
        val type: String = "mouse",
        val action: String = "cursor",
        val x: Float,  // 0.0~1.0
        val y: Float,  // 0.0~1.0
    )

    /** 鼠标按键 */
    @Serializable
    data class MouseClick(
        val type: String = "mouse",
        val action: String,
        val button: String = "left",
    )

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

    fun encode(protocol: Any): String = when (protocol) {
        is TouchDown -> json.encodeToString(protocol)
        is TouchUp -> json.encodeToString(protocol)
        is TouchMove -> json.encodeToString(protocol)
        is MouseMove -> json.encodeToString(protocol)
        is CursorAbs -> json.encodeToString(protocol)
        is MouseClick -> json.encodeToString(protocol)
        is MouseScroll -> json.encodeToString(protocol)
        is KeyPress -> json.encodeToString(protocol)
        is KeyRelease -> json.encodeToString(protocol)
        is KeyTap -> json.encodeToString(protocol)
        is TypeText -> json.encodeToString(protocol)
        is Ping -> json.encodeToString(protocol)
        is Pong -> json.encodeToString(protocol)
        else -> throw IllegalArgumentException("未知协议类型: ${protocol::class.simpleName}")
    }
}
