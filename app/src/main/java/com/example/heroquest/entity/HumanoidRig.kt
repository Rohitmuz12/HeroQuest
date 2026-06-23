package com.example.heroquest.entity

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin

/**
 * Draws a humanoid figure as a glowing energy-being: thick rounded capsule limbs
 * (not thin lines) with a soft outer glow that pulses on idle and flares brighter
 * on attack. Still pure geometry — no sprite sheets or image assets needed.
 *
 * Coordinates are relative to (0,0) = the figure's feet/ground contact point,
 * with -y pointing up. Caller translates the canvas to the figure's world
 * position before calling draw().
 */
class HumanoidRig(
    private val bodyColor: Int,
    private val limbColor: Int,
    private val heightPx: Float
) {
    private var animTime = 0f
    private var glowPulseTime = 0f
    var facingRight = true
        private set

    // Thick capsule strokes instead of thin lines — this is what gives the
    // figure actual visual "mass" instead of reading as a wireframe.
    private val bodyPaint = Paint().apply { color = bodyColor; isAntiAlias = true; strokeWidth = heightPx * 0.22f; strokeCap = Paint.Cap.ROUND }
    private val limbPaint = Paint().apply { color = limbColor; isAntiAlias = true; strokeWidth = heightPx * 0.16f; strokeCap = Paint.Cap.ROUND }
    private val headPaint = Paint().apply { color = bodyColor; isAntiAlias = true }
    private val highlightPaint = Paint().apply { color = 0x55FFFFFF.toInt(); isAntiAlias = true }
    private val glowPaint = Paint().apply { isAntiAlias = true }

    // Proportions, scaled off overall height so the rig scales cleanly with heightPx.
    private val headRadius = heightPx * 0.14f
    private val torsoLength = heightPx * 0.32f
    private val upperLimbLength = heightPx * 0.18f
    private val lowerLimbLength = heightPx * 0.18f
    private val hipY = -heightPx * 0.36f       // hip joint height above feet
    private val shoulderY = hipY - torsoLength  // shoulder joint height above feet

    fun setFacing(right: Boolean) {
        facingRight = right
    }

    fun update(dt: Float, state: AnimState, moveSpeedFraction: Float) {
        // Run cycle speed scales with how fast the character is actually moving,
        // so a slow jog and a full sprint look visually distinct, not just same
        // animation at different character speeds.
        val cycleSpeed = when (state) {
            AnimState.RUN -> 8f + moveSpeedFraction * 6f
            AnimState.ATTACK -> 14f
            else -> 4f
        }
        animTime += dt * cycleSpeed
        glowPulseTime += dt * 3f
    }

    fun draw(canvas: Canvas, state: AnimState) {
        val dir = if (facingRight) 1f else -1f
        val pose = poseFor(state, dir)

        val torsoTopY = shoulderY + pose.bob
        val hipYAdjusted = hipY + pose.bob
        val centerY = (torsoTopY + hipYAdjusted) / 2f

        // Outer glow: a soft radial halo behind the whole figure. Pulses gently at
        // idle, holds a brighter steady glow during an attack for impact.
        val glowStrength = if (state == AnimState.ATTACK) 1f else 0.7f + 0.3f * sin(glowPulseTime)
        val glowRadius = heightPx * 0.7f * (0.9f + 0.15f * glowStrength)
        glowPaint.shader = RadialGradient(
            pose.torsoLean, centerY, glowRadius,
            applyAlpha(bodyColor, (glowStrength * 110).toInt()), 0x00000000,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(pose.torsoLean, centerY, glowRadius, glowPaint)

        // Torso (hip to shoulder) — drawn as a thick capsule stroke
        canvas.drawLine(0f, hipYAdjusted, pose.torsoLean, torsoTopY, bodyPaint)

        // Legs: hip -> knee -> foot
        drawLimb(canvas, 0f, hipYAdjusted, pose.leftLeg, upperLimbLength, lowerLimbLength, limbPaint)
        drawLimb(canvas, 0f, hipYAdjusted, pose.rightLeg, upperLimbLength, lowerLimbLength, limbPaint)

        // Arms: shoulder -> elbow -> hand
        drawLimb(canvas, pose.torsoLean, torsoTopY, pose.leftArm, upperLimbLength * 0.9f, lowerLimbLength * 0.9f, limbPaint)
        drawLimb(canvas, pose.torsoLean, torsoTopY, pose.rightArm, upperLimbLength * 0.9f, lowerLimbLength * 0.9f, limbPaint)

        // Head, sitting atop the torso, drawn after limbs so it's never occluded
        val headCenterY = torsoTopY - headRadius * 1.1f
        canvas.drawCircle(pose.torsoLean, headCenterY, headRadius, headPaint)
        // Small bright highlight for a glassy, energy-being look (same trick as the space game's orb)
        canvas.drawCircle(pose.torsoLean - headRadius * 0.3f, headCenterY - headRadius * 0.3f, headRadius * 0.32f, highlightPaint)
    }

    /** Draws a 2-segment capsule limb (e.g. thigh+shin or upper arm+forearm), bending toward `angleDeg` from straight down. */
    private fun drawLimb(canvas: Canvas, originX: Float, originY: Float, angleDeg: Float, upperLen: Float, lowerLen: Float, paint: Paint) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val midX = originX + sin(rad).toFloat() * upperLen
        val midY = originY + kotlin.math.cos(rad).toFloat() * upperLen
        // Lower segment bends slightly more in the same direction for a natural look.
        val lowerRad = Math.toRadians((angleDeg * 1.3f).toDouble())
        val endX = midX + sin(lowerRad).toFloat() * lowerLen
        val endY = midY + kotlin.math.cos(lowerRad).toFloat() * lowerLen

        canvas.drawLine(originX, originY, midX, midY, paint)
        canvas.drawLine(midX, midY, endX, endY, paint)
    }

    /** Returns `color` with its alpha channel replaced by `alpha` (0-255), used for the glow gradient. */
    private fun applyAlpha(color: Int, alpha: Int): Int {
        val clampedAlpha = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (clampedAlpha shl 24)
    }

    /** Returns the hand/weapon-tip position of the leading attack arm, for trail/spark effects. */
    fun leadHandWorldOffset(state: AnimState): Pair<Float, Float>? {
        if (state != AnimState.ATTACK) return null
        val dir = if (facingRight) 1f else -1f
        val pose = poseFor(state, dir)
        val torsoTopY = shoulderY + pose.bob
        val leadArmAngle = if (dir > 0) pose.leftArm else pose.rightArm
        val rad = Math.toRadians(leadArmAngle.toDouble())
        val lowerRad = Math.toRadians((leadArmAngle * 1.3f).toDouble())
        val upperLen = upperLimbLength * 0.9f
        val lowerLen = lowerLimbLength * 0.9f
        val midX = pose.torsoLean + sin(rad).toFloat() * upperLen
        val midY = torsoTopY + kotlin.math.cos(rad).toFloat() * upperLen
        val endX = midX + sin(lowerRad).toFloat() * lowerLen
        val endY = midY + kotlin.math.cos(lowerRad).toFloat() * lowerLen
        return Pair(endX, endY)
    }

    /**
     * Computes limb angles (degrees from straight-down, positive = swung forward
     * in the facing direction) and body offsets for the given state at the current
     * animTime.
     */
    private fun poseFor(state: AnimState, dir: Float): Pose {
        return when (state) {
            AnimState.IDLE -> {
                val sway = sin(animTime) * 4f
                Pose(leftArm = sway, rightArm = -sway, leftLeg = 0f, rightLeg = 0f, torsoLean = sway * 0.3f, bob = sin(animTime) * heightPx * 0.01f)
            }
            AnimState.RUN -> {
                val phase = animTime
                val legSwing = sin(phase) * 35f
                val armSwing = sin(phase) * 28f
                Pose(
                    leftArm = -armSwing * dir, rightArm = armSwing * dir,
                    leftLeg = legSwing * dir, rightLeg = -legSwing * dir,
                    torsoLean = 6f * dir, bob = kotlin.math.abs(sin(phase * 2f)) * -heightPx * 0.04f
                )
            }
            AnimState.JUMP -> Pose(
                leftArm = -50f * dir, rightArm = -50f * dir,
                leftLeg = 25f * dir, rightLeg = -15f * dir,
                torsoLean = 4f * dir, bob = 0f
            )
            AnimState.FALL -> Pose(
                leftArm = -30f * dir, rightArm = -30f * dir,
                leftLeg = -10f * dir, rightLeg = 15f * dir,
                torsoLean = -4f * dir, bob = 0f
            )
            AnimState.ATTACK -> {
                // Fast forward swing on the lead arm, driven by a sharp sin curve for snap.
                val swing = sin(animTime.coerceAtMost(Math.PI.toFloat())) * 90f
                Pose(
                    leftArm = if (dir > 0) swing else -20f * dir,
                    rightArm = if (dir > 0) -20f * dir else swing,
                    leftLeg = 8f * dir, rightLeg = -8f * dir,
                    torsoLean = 10f * dir, bob = 0f
                )
            }
            AnimState.HIT -> Pose(
                leftArm = -15f * dir, rightArm = 25f * dir,
                leftLeg = -8f * dir, rightLeg = 8f * dir,
                torsoLean = -12f * dir, bob = heightPx * 0.015f
            )
            AnimState.DEFEATED -> Pose(
                leftArm = 70f * dir, rightArm = 70f * dir,
                leftLeg = 60f * dir, rightLeg = 60f * dir,
                torsoLean = 80f * dir, bob = heightPx * 0.3f
            )
        }
    }

    private data class Pose(
        val leftArm: Float,
        val rightArm: Float,
        val leftLeg: Float,
        val rightLeg: Float,
        val torsoLean: Float,
        val bob: Float
    )
}
