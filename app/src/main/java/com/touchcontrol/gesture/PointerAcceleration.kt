package com.touchcontrol.gesture

import kotlin.math.abs
import kotlin.math.pow

/**
 * 鼠标指针加速算法
 *
 * 基于标准鼠标加速曲线（类 macOS 风格）：
 * - 小位移 → 低速高精度（微操友好）
 * - 中位移 → 线性跟随
 * - 大位移 → 高速响应（快速跨屏）
 *
 * 曲线公式：output = sign(input) × (|input|^1.6 × gain + |input| × baseSpeed)
 *
 * 参考：macOS Pointer Acceleration, Microsoft Mouse Acceleration
 */
object PointerAcceleration {

    /**
     * 基础速度倍率（无加速时的基础速度）
     * 值越大，光标移动越快
     */
    private const val BASE_SPEED = 1.8f

    /**
     * 加速增益系数
     * 值越大，快速移动时光标加速越明显
     */
    private const val ACCEL_GAIN = 3.5f

    /**
     * 加速曲线指数（1.0=线性，>1.0=加速）
     * 推荐范围 1.4~2.0
     */
    private const val CURVE_POWER = 1.6f

    /**
     * 最小有效移动阈值（归一化坐标）
     * 低于此值的微小抖动被过滤
     */
    private const val DEAD_ZONE = 0.0005f

    /**
     * 最大单帧移动量（归一化坐标）
     * 防止蓝牙瞬时异常导致光标飞走
     */
    private const val MAX_DELTA = 0.15f

    /**
     * 对归一化位移应用加速曲线
     *
     * @param normalizedDelta 归一化位移（-1.0 ~ 1.0），触摸板上的位移 / 触摸板尺寸
     * @return 加速后的归一化位移
     */
    fun accelerate(normalizedDelta: Float): Float {
        val absDelta = abs(normalizedDelta)
        if (absDelta < DEAD_ZONE) return 0f

        val clamped = absDelta.coerceAtMost(MAX_DELTA)
        val sign = if (normalizedDelta > 0) 1f else -1f

        // 加速曲线：基础速度 + 指数加速
        val linear = clamped * BASE_SPEED
        val exponential = clamped.pow(CURVE_POWER) * ACCEL_GAIN
        val output = linear + exponential

        return sign * output
    }

    /**
     * 对 dx/dy 对应用加速
     *
     * @param dx 触摸板上的 x 位移（归一化）
     * @param dy 触摸板上的 y 位移（归一化）
     * @return 加速后的 (dx, dy)
     */
    fun accelerate(dx: Float, dy: Float): Pair<Float, Float> {
        return accelerate(dx) to accelerate(dy)
    }

    /**
     * 双指滚动加速（比鼠标加速更柔和）
     */
    fun scrollAccelerate(normalizedDelta: Float): Float {
        val absDelta = abs(normalizedDelta)
        if (absDelta < DEAD_ZONE) return 0f

        val clamped = absDelta.coerceAtMost(MAX_DELTA)
        val sign = if (normalizedDelta > 0) 1f else -1f

        // 滚动用更线性的曲线
        val output = clamped * 2.5f + clamped.pow(1.3f) * 1.5f
        return sign * output
    }

    /**
     * 将触摸板像素位移转换为归一化位移
     *
     * @param deltaPx 触摸板上的像素位移
     * @param touchpadSize 触摸板对应轴的总像素尺寸
     * @return 归一化位移
     */
    fun normalize(deltaPx: Float, touchpadSize: Float): Float {
        if (touchpadSize <= 0f) return 0f
        return deltaPx / touchpadSize
    }
}
