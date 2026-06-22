package com.example.heroquest.entity

import android.graphics.Canvas
import android.graphics.Paint

/**
 * A solid axis-aligned platform. World coordinates: x grows right, y grows down
 * (standard screen convention), so `top` is numerically less than `bottom`.
 */
class Platform(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    private val paint = Paint().apply { color = 0xFF4A3F5C.toInt(); isAntiAlias = true }
    private val edgePaint = Paint().apply { color = 0xFF6B5C8C.toInt(); isAntiAlias = true; strokeWidth = 4f }

    fun draw(canvas: Canvas, cameraX: Float) {
        val screenLeft = left - cameraX
        val screenRight = right - cameraX
        canvas.drawRect(screenLeft, top, screenRight, bottom, paint)
        canvas.drawLine(screenLeft, top, screenRight, top, edgePaint)
    }

    fun containsX(worldX: Float): Boolean = worldX >= left && worldX <= right
}
