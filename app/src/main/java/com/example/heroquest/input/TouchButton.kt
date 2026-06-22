package com.example.heroquest.input

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.sqrt

/**
 * A simple circular tap button (jump/attack). Tracks press state per-pointer so
 * multitouch (e.g. holding the joystick while also tapping attack) works correctly.
 */
class TouchButton(
    private val centerX: Float,
    private val centerY: Float,
    private val radius: Float,
    private val label: String,
    color: Int
) {
    private var activePointerId: Int = -1
    var consumedPressThisFrame = false
        private set

    private val fillPaint = Paint().apply { this.color = color; alpha = 160; isAntiAlias = true }
    private val pressedFillPaint = Paint().apply { this.color = color; alpha = 230; isAntiAlias = true }
    private val textPaint = Paint().apply {
        this.color = 0xFFFFFFFF.toInt()
        textSize = radius * 0.55f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    fun isWithinRange(touchX: Float, touchY: Float): Boolean {
        val dx = touchX - centerX
        val dy = touchY - centerY
        return sqrt(dx * dx + dy * dy) <= radius
    }

    fun press(pointerId: Int) {
        activePointerId = pointerId
        consumedPressThisFrame = true
    }

    fun release(pointerId: Int) {
        if (pointerId == activePointerId) activePointerId = -1
    }

    fun isPressed(): Boolean = activePointerId != -1

    /** Call once per frame after reading consumedPressThisFrame, to reset the one-shot flag. */
    fun clearFrameFlags() {
        consumedPressThisFrame = false
    }

    fun draw(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, radius, if (isPressed()) pressedFillPaint else fillPaint)
        canvas.drawText(label, centerX, centerY + textPaint.textSize * 0.35f, textPaint)
    }
}
