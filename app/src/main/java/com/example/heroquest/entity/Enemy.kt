package com.example.heroquest.entity

import android.graphics.Canvas

/**
 * A ground-based enemy: patrols, then chases and attacks the player when in
 * range. Stats, attack move, speed, and color all derive from `type`, so the
 * 4 enemy variants are real data differences, not just palette swaps.
 */
class Enemy(startX: Float, groundY: Float, private val heightPx: Float, private val type: EnemyType = EnemyType.BRAWLER) {

    var x = startX
    val y = groundY // enemies don't leave their platform's surface in Phase 1

    var maxHp = hpFor(type)
    var currentHp = maxHp
    val isAlive: Boolean get() = currentHp > 0

    private val moveSpeed = speedFor(type)
    private val attackMove = attackMoveFor(type)
    private val attackDamage = damageFor(type)
    private val attackRange = heightPx * reachFor(type)
    private val chaseRange = heightPx * 4f
    private val attackCooldownMax = cooldownFor(type)
    private var attackCooldown = 0f
    private var attackTimer = 0f
    private val attackDuration = durationFor(type)
    private var attackHasHit = false

    private var state = AnimState.IDLE
    var facingRight = true
        private set

    val rig = HumanoidRig(bodyColor = bodyColorFor(type), limbColor = limbColorFor(type), heightPx = heightPx)
    val width = heightPx * widthFractionFor(type)

    private var hitFlashTimer = 0f

    fun bounds(): HitBox = HitBox(x - width / 2f, y - heightPx, x + width / 2f, y)

    fun attackHitbox(): HitBox? {
        if (state != attackMove) return null
        val progress = attackTimer / attackDuration
        if (progress < 0.3f || progress > 0.8f) return null

        val reach = heightPx * (if (attackMove == AnimState.KICK) 0.65f else 0.5f)
        val dir = if (facingRight) 1f else -1f
        val centerX = x + dir * reach * 0.5f
        return HitBox(centerX - reach / 2f, y - heightPx * 0.75f, centerX + reach / 2f, y - heightPx * 0.15f)
    }

    fun hasAttackLanded(): Boolean = attackHasHit
    fun markAttackLanded() { attackHasHit = true }

    fun currentMoveDamage(): Int = attackDamage

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

        if (state == attackMove) {
            attackTimer += dt
            if (attackTimer >= attackDuration) {
                state = AnimState.IDLE
                attackTimer = 0f
                attackHasHit = false
            }
        } else if (!playerAlive) {
            state = AnimState.IDLE
        } else if (distanceToPlayer <= attackRange && attackCooldown <= 0f) {
            state = attackMove
            attackTimer = 0f
            attackHasHit = false
            attackCooldown = attackCooldownMax
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

    fun draw(canvas: Canvas, cameraX: Float) {
        canvas.save()
        canvas.translate(x - cameraX, y)
        rig.draw(canvas, state)
        canvas.restore()
    }

    companion object {
        private fun hpFor(type: EnemyType): Int = when (type) {
            EnemyType.BRAWLER -> 40
            EnemyType.BRUTE -> 70
            EnemyType.STRIKER -> 28
            EnemyType.GUARD -> 50
        }

        private fun speedFor(type: EnemyType): Float = when (type) {
            EnemyType.BRAWLER -> 160f
            EnemyType.BRUTE -> 100f
            EnemyType.STRIKER -> 230f
            EnemyType.GUARD -> 140f
        }

        private fun attackMoveFor(type: EnemyType): AnimState = when (type) {
            EnemyType.BRAWLER -> AnimState.PUNCH
            EnemyType.BRUTE -> AnimState.KICK
            EnemyType.STRIKER -> AnimState.PUNCH
            EnemyType.GUARD -> AnimState.PUNCH
        }

        private fun damageFor(type: EnemyType): Int = when (type) {
            EnemyType.BRAWLER -> 8
            EnemyType.BRUTE -> 14
            EnemyType.STRIKER -> 6
            EnemyType.GUARD -> 12
        }

        private fun reachFor(type: EnemyType): Float = when (type) {
            EnemyType.BRAWLER -> 0.7f
            EnemyType.BRUTE -> 0.85f
            EnemyType.STRIKER -> 0.65f
            EnemyType.GUARD -> 0.7f
        }

        private fun cooldownFor(type: EnemyType): Float = when (type) {
            EnemyType.BRAWLER -> 1.2f
            EnemyType.BRUTE -> 1.6f
            EnemyType.STRIKER -> 0.7f
            EnemyType.GUARD -> 2.0f
        }

        private fun durationFor(type: EnemyType): Float = when (type) {
            EnemyType.BRAWLER -> 0.4f
            EnemyType.BRUTE -> 0.5f
            EnemyType.STRIKER -> 0.32f
            EnemyType.GUARD -> 0.45f
        }

        private fun widthFractionFor(type: EnemyType): Float = when (type) {
            EnemyType.BRUTE -> 0.42f
            else -> 0.32f
        }

        private fun bodyColorFor(type: EnemyType): Int = when (type) {
            EnemyType.BRAWLER -> 0xFFB04A4A.toInt()
            EnemyType.BRUTE -> 0xFF8C5A3F.toInt()
            EnemyType.STRIKER -> 0xFFB0A04A.toInt()
            EnemyType.GUARD -> 0xFF4A6CB0.toInt()
        }

        private fun limbColorFor(type: EnemyType): Int = when (type) {
            EnemyType.BRAWLER -> 0xFF7A3030.toInt()
            EnemyType.BRUTE -> 0xFF5C3A28.toInt()
            EnemyType.STRIKER -> 0xFF7A6E30.toInt()
            EnemyType.GUARD -> 0xFF304A7A.toInt()
        }
    }
}
