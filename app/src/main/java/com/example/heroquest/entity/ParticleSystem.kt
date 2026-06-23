package com.example.heroquest.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val radius: Float,
    val color: Int
)

/**
 * Lightweight particle system for combat impact: a burst of sparks where an
 * attack lands, and a bigger burst when an enemy is defeated.
 * Same proven approach as Dodge Drop's ParticleSystem.
 */
class ParticleSystem {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint().apply { isAntiAlias = true }

    fun emitHitSpark(x: Float, y: Float, color: Int) {
        repeat(16) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = 180f + Random.nextFloat() * 260f
            particles.add(
                Particle(
                    x = x, y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    life = 0.25f + Random.nextFloat() * 0.2f,
                    maxLife = 0.45f,
                    radius = Random.nextFloat() * 5f + 2f,
                    color = color
                )
            )
        }
        // A few bright white sparks mixed in for a sharper "clang" feel.
        repeat(6) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = 260f + Random.nextFloat() * 200f
            particles.add(
                Particle(
                    x = x, y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    life = 0.15f, maxLife = 0.15f,
                    radius = Random.nextFloat() * 3f + 1.5f,
                    color = Color.WHITE
                )
            )
        }
    }

    fun emitDefeatBurst(x: Float, y: Float, color: Int) {
        repeat(30) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = 100f + Random.nextFloat() * 300f
            particles.add(
                Particle(
                    x = x, y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    life = 0.4f + Random.nextFloat() * 0.4f,
                    maxLife = 0.8f,
                    radius = Random.nextFloat() * 7f + 3f,
                    color = color
                )
            )
        }
    }

    fun update(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.92f
            p.vy *= 0.92f
            p.life -= dt
            if (p.life <= 0f) iterator.remove()
        }
    }

    fun draw(canvas: Canvas, cameraX: Float) {
        for (p in particles) {
            val lifeRatio = (p.life / p.maxLife).coerceIn(0f, 1f)
            paint.color = p.color
            paint.alpha = (lifeRatio * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x - cameraX, p.y, p.radius * lifeRatio, paint)
        }
    }

    fun clear() = particles.clear()
}
