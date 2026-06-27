package com.example.heroquest.entity

import android.graphics.Canvas

/**
 * The story-mode boss: significantly more HP than a regular Enemy, and a
 * 3-move pattern instead of one repeated swing — a quick PUNCH, a longer-reach
 * KICK, and a telegraphed FINISHER-pose slam that pauses briefly before
 * striking, so it's a fair, readable "big attack" rather than an instant
 * unavoidable hit. Move selection cycles through a pattern rather than being
 * random, so the fight has a learnable rhythm.
 */
class Boss(startX: Float, groundY: Float, private val heightPx: Float) {

    var x = startX
    val y = groundY

    var maxHp = 320
    var currentHp = maxHp
    val isAlive: Boolean get() = currentHp > 0

    private val moveSpeed = 130f
    private val punchRange = heightPx * 0.65f
    private val kickRange = heightPx * 0.95f
    private val slamRange = heightPx * 1.1f
    private val chaseRange = heightPx * 5f

    private var attackCooldown = 0f
    private val attackCooldownMax = 1.0f
    private var actionTimer = 0f
    private var actionHasHit = false

    private val punchDuration = 0.4f
    private val punchDamage = 10
    private val kickDuration = 0.45f
    private val kickDamage = 16
    // The slam has an explicit telegraph window before the hit window — the boss
    // visibly winds up (FINISHER pose's early frames read as a wind-up) and pauses,
    // giving the player a real chance to react/dodge/punish rather than eating an
    // attack that struck the instant it began.
    private val slamTelegraphDuration = 0.5f
    private val slamStrikeDuration = 0.5f
    private val slamDamage = 24

    /** Cycles through the move pattern: punch, punch, kick, slam, repeat. */
    private val movePattern = listOf(AnimState.PUNCH, AnimState.PUNCH, AnimState.KICK, AnimState.FINISHER)
    private var movePatternIndex = 0

    private var state = AnimState.IDLE
    var facingRight = true
        private set

    val rig = HumanoidRig(bodyColor = 0xFF8C3FA8.toInt(), limbColor = 0xFF5C2870.toInt(), heightPx = heightPx)
    val width = heightPx * 0.36f

    private var hitFlashTimer = 0f
    private var isTelegraphing = false

    fun bounds(): HitBox = HitBox(x - width / 2f, y - heightPx, x + width / 2f, y)

    fun attackHitbox(): HitBox? {
        if (isTelegraphing) return null // no hitbox during the wind-up — that's the whole point

        val (duration, reachMultiplier, windowStart, windowEnd) = when (state) {
            AnimState.PUNCH -> Quad(punchDuration, 0.5f, 0.3f, 0.8f)
            AnimState.KICK -> Quad(kickDuration, 0.65f, 0.3f, 0.8f)
            AnimState.FINISHER -> Quad(slamStrikeDuration, 0.8f, 0.2f, 0.7f)
            else -> return null
        }
        val progress = actionTimer / duration
        if (progress < windowStart || progress > windowEnd) return null

        val reach = heightPx * reachMultiplier
        val dir = if (facingRight) 1f else -1f
        val centerX = x + dir * reach * 0.5f
        return HitBox(centerX - reach / 2f, y - heightPx * 0.8f, centerX + reach / 2f, y - heightPx * 0.1f)
    }

    private data class Quad(val duration: Float, val reachMultiplier: Float, val windowStart: Float, val windowEnd: Float)

    fun currentMoveDamage(): Int = when (state) {
        AnimState.PUNCH -> punchDamage
        AnimState.KICK -> kickDamage
        AnimState.FINISHER -> slamDamage
        else -> 0
    }

    fun hasAttackLanded(): Boolean = actionHasHit
    fun markAttackLanded() { actionHasHit = true }

    /** True while winding up the slam — UI/VFX can use this to show a warning indicator. */
    fun isTelegraphingAttack(): Boolean = isTelegraphing

    fun takeDamage(amount: Int) {
        if (!isAlive) return
        currentHp = (currentHp - amount).coerceAtLeast(0)
        hitFlashTimer = 0.2f
        if (!isAlive) {
            state = AnimState.DEFEATED
        }
    }

    fun update(dt: Float, playerX: Float, playerAlive: Boolean) {
        if (!isAlive) {
            rig.update(dt, AnimState.DEFEATED, 0f)
            return
        }

        if (hitFlashTimer > 0f) hitFlashTimer -= dt
        if (attackCooldown > 0f) attackCooldown -= dt

        val distanceToPlayer = kotlin.math.abs(playerX - x)
        facingRight = playerX >= x

        val rangeForNextMove = rangeFor(movePattern[movePatternIndex])

        if (isTelegraphing) {
            actionTimer += dt
            if (actionTimer >= slamTelegraphDuration) {
                isTelegraphing = false
                actionTimer = 0f
                actionHasHit = false
            }
        } else if (isActionState(state)) {
            actionTimer += dt
            val duration = durationFor(state)
            if (actionTimer >= duration) {
                state = AnimState.IDLE
                actionTimer = 0f
                actionHasHit = false
                movePatternIndex = (movePatternIndex + 1) % movePattern.size
            }
        } else if (!playerAlive) {
            state = AnimState.IDLE
        } else if (distanceToPlayer <= rangeForNextMove && attackCooldown <= 0f) {
            val nextMove = movePattern[movePatternIndex]
            attackCooldown = attackCooldownMax
            if (nextMove == AnimState.FINISHER) {
                // Slam begins with a telegraph: state is set so the rig shows the
                // wind-up pose, but no hitbox is active and actionTimer counts the
                // telegraph duration first, not the strike duration.
                isTelegraphing = true
                state = AnimState.FINISHER
                actionTimer = 0f
                actionHasHit = false
            } else {
                state = nextMove
                actionTimer = 0f
                actionHasHit = false
            }
        } else if (distanceToPlayer <= chaseRange) {
            val dir = if (playerX > x) 1f else -1f
            x += dir * moveSpeed * dt
            state = AnimState.RUN
        } else {
            state = AnimState.IDLE
        }

        rig.setFacing(facingRight)
        val speedFraction = if (state == AnimState.RUN) 1f else 0f
        rig.update(dt, state, speedFraction)
    }

    private fun isActionState(s: AnimState): Boolean {
        return s == AnimState.PUNCH || s == AnimState.KICK || s == AnimState.FINISHER
    }

    private fun durationFor(s: AnimState): Float = when (s) {
        AnimState.PUNCH -> punchDuration
        AnimState.KICK -> kickDuration
        AnimState.FINISHER -> slamStrikeDuration
        else -> 0f
    }

    private fun rangeFor(s: AnimState): Float = when (s) {
        AnimState.PUNCH -> punchRange
        AnimState.KICK -> kickRange
        AnimState.FINISHER -> slamRange
        else -> punchRange
    }

    fun draw(canvas: Canvas, cameraX: Float) {
        canvas.save()
        canvas.translate(x - cameraX, y)
        rig.draw(canvas, state)
        canvas.restore()
    }
}
