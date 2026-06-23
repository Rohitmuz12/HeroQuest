package com.example.heroquest.input

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * A simple circular tap button (jump/attack). Tracks press state per-pointer so
 * multitouch (e.g. holding the joystick while also tapping attack) works correctly.
 *
 * Touch events arrive on the UI thread; update() runs on a separate GameThread.
 * A plain Boolean "was pressed this frame" flag is NOT safe here — the UI thread
 * could set it and the game thread could clear it again before update() ever
 * reads it, silently dropping the tap. AtomicInteger fixes this: press() always
 * increments, consumePendingPresses() atomically reads-and-resets, so every tap
 * is guaranteed to be seen by exactly one frame's update(), never lost or double
 * counted, regardless of how the two threads happen to interleave.
 */
class TouchButton(
    private val centerX: Float,
    private val centerY: Float,
    private val radius: Float,
    private val label: String,
    color: Int
) {
    private var activePointerId: Int = -1
    private val pendingPresses = AtomicInteger(0)

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

    /** Called from the UI thread (onTouchEvent) when this button is tapped. */
    fun press(pointerId: Int) {
        activePointerId = pointerId
        pendingPresses.incrementAndGet()
    }

    /** Called from the UI thread (onTouchEvent) when the pointer lifts. */
    fun release(pointerId: Int) {
        if (pointerId == activePointerId) activePointerId = -1
    }

    fun isPressed(): Boolean = activePointerId != -1

    /**
     * Called once per frame from the GameThread's update(). Returns true if at
     * least one press happened since the last call, and atomically resets the
     * counter — so a press is consumed exactly once, with no race against press().
     */
    fun consumePendingPress(): Boolean {
        return pendingPresses.getAndSet(0) > 0
    }

    fun draw(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, radius, if (isPressed()) pressedFillPaint else fillPaint)
        canvas.drawText(label, centerX, centerY + textPaint.textSize * 0.35f, textPaint)
    }
}
