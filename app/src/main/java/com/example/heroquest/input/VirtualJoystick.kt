package com.example.heroquest.input

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A draggable virtual joystick. Tracks a single touch pointer by id, anchored to
 * wherever the player first touches within its base region, and reports a
 * normalized horizontal value in [-1, 1] for movement.
 */
class VirtualJoystick(
    private val centerX: Float,
    private val centerY: Float,
    private val baseRadius: Float
) {
    private var activePointerId: Int = -1
    private var knobOffsetX = 0f
    private var knobOffsetY = 0f

    private val baseFillPaint = Paint().apply { color = 0x33FFFFFF.toInt(); isAntiAlias = true }
    private val baseStrokePaint = Paint().apply { color = 0x55FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    private val knobPaint = Paint().apply { color = 0x99FFFFFF.toInt(); isAntiAlias = true }

    /** True if the given touch point falls within range to start using this joystick. */
    fun isWithinActivationRange(touchX: Float, touchY: Float): Boolean {
        val dx = touchX - centerX
        val dy = touchY - centerY
        return sqrt(dx * dx + dy * dy) <= baseRadius * 1.8f
    }

    fun startTouch(pointerId: Int, touchX: Float, touchY: Float) {
        activePointerId = pointerId
        updateKnob(touchX, touchY)
    }

    fun moveTouch(pointerId: Int, touchX: Float, touchY: Float) {
        if (pointerId != activePointerId) return
        updateKnob(touchX, touchY)
    }

    fun endTouch(pointerId: Int) {
        if (pointerId != activePointerId) return
        activePointerId = -1
        knobOffsetX = 0f
        knobOffsetY = 0f
    }

    fun isActive(): Boolean = activePointerId != -1

    private fun updateKnob(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance <= baseRadius) {
            knobOffsetX = dx
            knobOffsetY = dy
        } else {
            val angle = atan2(dy, dx)
            knobOffsetX = cos(angle) * baseRadius
            knobOffsetY = sin(angle) * baseRadius
        }
    }

    /** Horizontal input in [-1, 1]. Vertical is intentionally unused — this is a side-scroller. */
    fun horizontalValue(): Float {
        if (!isActive()) return 0f
        return (knobOffsetX / baseRadius).coerceIn(-1f, 1f)
    }

    fun draw(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, baseRadius, baseFillPaint)
        canvas.drawCircle(centerX, centerY, baseRadius, baseStrokePaint)
        val knobRadius = baseRadius * 0.45f
        canvas.drawCircle(centerX + knobOffsetX, centerY + knobOffsetY, knobRadius, knobPaint)
    }
}
