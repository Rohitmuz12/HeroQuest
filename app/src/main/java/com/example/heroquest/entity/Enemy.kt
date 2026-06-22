package com.example.heroquest.entity

import android.graphics.Canvas

/**
 * A ground-based enemy: patrols, then chases and attacks the player when in range.
 * Deliberately simpler than Player (no jumping) since Phase 1 enemies are melee
 * brawlers on the same platform tier as the player, not platform-hopping foes.
 */
class Enemy(startX: Float, groundY: Float, private val heightPx: Float) {

    var x = startX
    val y = groundY // enemies don't leave their platform's surface in Phase 1

    var maxHp = 40
    var currentHp = maxHp
    val isAlive: Boolean get() = currentHp > 0

    private val moveSpeed = 160f
    private val attackRange = heightPx * 0.7f
    private val chaseRange = heightPx * 4f
    private val attackCooldownMax = 1.2f
    private var attackCooldown = 0f
    private var attackTimer = 0f
    private val attackDuration = 0.4f
    private var attackHasHit = false

    private var state = AnimState.IDLE
    var facingRight = true
        private set

    val rig = HumanoidRig(bodyColor = 0xFFB04A4A.toInt(), limbColor = 0xFF7A3030.toInt(), heightPx = heightPx)
    val width = heightPx * 0.32f

    private var hitFlashTimer = 0f

    fun bounds(): HitBox = HitBox(x - width / 2f, y - heightPx, x + width / 2f, y)

    fun attackHitbox(): HitBox? {
        if (state != AnimState.ATTACK) return null
        val progress = attackTimer / attackDuration
        if (progress < 0.3f || progress > 0.8f) return null

        val reach = heightPx * 0.5f
        val dir = if (facingRight) 1f else -1f
        val centerX = x + dir * reach * 0.5f
        return HitBox(centerX - reach / 2f, y - heightPx * 0.75f, centerX + reach / 2f, y - heightPx * 0.15f)
    }

    fun hasAttackLanded(): Boolean = attackHasHit
    fun markAttackLanded() { attackHasHit = true }

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

        if (state == AnimState.ATTACK) {
            attackTimer += dt
            if (attackTimer >= attackDuration) {
                state = AnimState.IDLE
                attackTimer = 0f
                attackHasHit = false
            }
        } else if (!playerAlive) {
            state = AnimState.IDLE
        } else if (distanceToPlayer <= attackRange && attackCooldown <= 0f) {
            state = AnimState.ATTACK
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
}
