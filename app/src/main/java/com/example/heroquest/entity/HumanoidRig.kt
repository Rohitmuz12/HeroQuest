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
    private var attackAnimTime = 0f
    private var glowPulseTime = 0f
    private var previousState = AnimState.IDLE
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
            else -> 4f
        }

        if (isActionState(state)) {
            // Reset the action timer exactly once, the frame this action starts —
            // NOT animTime, which keeps running continuously across every other state
            // and would otherwise already be far past the swing's useful sin() range
            // by the time an action begins, making the motion invisible. This bug
            // bit the original single-attack version; every action state added since
            // must go through this same reset-on-entry timer, never animTime directly.
            //
            // attackAnimTime tracks RAW elapsed seconds since the action began (not
            // pre-scaled), so each pose formula below normalizes it against that
            // move's own actual duration. A shared pre-scaled constant here would
            // silently desync once two different entities (e.g. Player's finisher at
            // 0.55s vs Boss's slam at ~1.0s) use the same AnimState with different
            // real durations — which is exactly what happened before this was fixed.
            if (previousState != state) {
                attackAnimTime = 0f
            }
            attackAnimTime += dt
        } else {
            animTime += dt * cycleSpeed
        }

        previousState = state
        glowPulseTime += dt * 3f
    }

    private fun isActionState(state: AnimState): Boolean {
        return state == AnimState.PUNCH || state == AnimState.KICK ||
            state == AnimState.JUMP_ATTACK || state == AnimState.DASH || state == AnimState.FINISHER
    }

    fun draw(canvas: Canvas, state: AnimState) {
        val dir = if (facingRight) 1f else -1f
        val pose = poseFor(state, dir)

        val torsoTopY = shoulderY + pose.bob
        val hipYAdjusted = hipY + pose.bob
        val centerY = (torsoTopY + hipYAdjusted) / 2f

        // Outer glow: a soft radial halo behind the whole figure. Pulses gently at
        // idle, holds a brighter steady glow during an action for impact.
        val glowStrength = if (isActionState(state)) 1f else 0.7f + 0.3f * sin(glowPulseTime)
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

    /** Returns the hand/foot/weapon-tip position of the active move, for trail/spark effects. */
    fun leadHandWorldOffset(state: AnimState): Pair<Float, Float>? {
        if (!isActionState(state)) return null
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
            AnimState.PUNCH -> {
                // Fast forward swing on the lead arm. Normalized against ~0.18s to
                // peak — quick enough that even the longest real PUNCH duration in
                // use (Boss: 0.4s) still completes the swing well before the move ends,
                // rather than holding mid-swing as if frozen.
                val swing = sin((attackAnimTime / 0.18f).coerceAtMost(Math.PI.toFloat())) * 90f
                Pose(
                    leftArm = if (dir > 0) swing else -20f * dir,
                    rightArm = if (dir > 0) -20f * dir else swing,
                    leftLeg = 8f * dir, rightLeg = -8f * dir,
                    torsoLean = 10f * dir, bob = 0f
                )
            }
            AnimState.KICK -> {
                // A sharp forward leg swing, body leaning back for balance/reach.
                val swing = sin((attackAnimTime / 0.2f).coerceAtMost(Math.PI.toFloat())) * 75f
                Pose(
                    leftArm = -25f * dir, rightArm = 35f * dir,
                    leftLeg = if (dir > 0) swing else -12f * dir,
                    rightLeg = if (dir > 0) -12f * dir else swing,
                    torsoLean = -8f * dir, bob = 0f
                )
            }
            AnimState.JUMP_ATTACK -> {
                // Both arms driven downward together, like a hammer-fist coming down.
                val swing = sin((attackAnimTime / 0.22f).coerceAtMost(Math.PI.toFloat())) * 70f
                Pose(
                    leftArm = -60f + swing * 0.4f, rightArm = -60f + swing * 0.4f,
                    leftLeg = 20f * dir, rightLeg = -10f * dir,
                    torsoLean = 6f * dir, bob = -heightPx * 0.05f
                )
            }
            AnimState.DASH -> {
                // Low, forward-leaning sprint pose — more extreme than a normal run stride.
                Pose(
                    leftArm = -45f * dir, rightArm = 45f * dir,
                    leftLeg = 40f * dir, rightLeg = -40f * dir,
                    torsoLean = 20f * dir, bob = -heightPx * 0.02f
                )
            }
            AnimState.FINISHER -> {
                // A bigger, slower wind-up-then-release swing. Normalized against 1.0s
                // so it correctly spans the boss's combined telegraph+strike duration
                // (0.5s + 0.5s); the player's shorter 0.55s finisher will simply show
                // a slightly-compressed but still complete version of the same curve,
                // which reads fine since it's a deliberately bigger, slower move anyway.
                val phase = (attackAnimTime / 1.0f).coerceIn(0f, 1f) * (Math.PI.toFloat() * 2f)
                val swing = sin(phase) * 130f
                Pose(
                    leftArm = if (dir > 0) swing else -40f * dir,
                    rightArm = if (dir > 0) -40f * dir else swing,
                    leftLeg = 15f * dir, rightLeg = -15f * dir,
                    torsoLean = 18f * dir, bob = 0f
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
