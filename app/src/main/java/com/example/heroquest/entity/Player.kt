package com.example.heroquest.entity

import android.graphics.Canvas

data class HitBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun intersects(other: HitBox): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }
}

/**
 * The player-controlled hero: platformer physics (gravity, jump, ground/platform
 * collision) plus a melee attack with an active hitbox window, and a state machine
 * that drives which pose HumanoidRig renders.
 */
class Player(startX: Float, startY: Float, private val heightPx: Float) {

    var x = startX
    var y = startY // y = position of feet (ground contact point)
    private var velocityX = 0f
    private var velocityY = 0f

    private val gravity = 2200f
    private val jumpVelocity = -1350f
    private val moveSpeed = 420f

    var isOnGround = false
        private set

    var maxHp = 100
    var currentHp = maxHp
    val isAlive: Boolean get() = currentHp > 0

    private var state = AnimState.IDLE
    private var attackTimer = 0f
    private val attackDuration = 0.35f
    private var attackHasHit = false // ensures one attack swing only lands one hit
    private var hitFlashTimer = 0f
    private var invulnerableTimer = 0f

    val rig = HumanoidRig(bodyColor = 0xFFE8B84B.toInt(), limbColor = 0xFFB88A2E.toInt(), heightPx = heightPx)

    val width = heightPx * 0.32f // approximate body width for collision

    fun bounds(): HitBox = HitBox(x - width / 2f, y - heightPx, x + width / 2f, y)

    /** Active only during the attack's "hit window," roughly the forward-swing portion of the animation. */
    fun attackHitbox(): HitBox? {
        if (state != AnimState.ATTACK) return null
        val progress = attackTimer / attackDuration
        if (progress < 0.25f || progress > 0.75f) return null // only mid-swing counts as the hit frame

        val reach = heightPx * 0.55f
        val facingDir = if (rig.facingRight) 1f else -1f
        val centerX = x + facingDir * reach * 0.5f
        return HitBox(centerX - reach / 2f, y - heightPx * 0.75f, centerX + reach / 2f, y - heightPx * 0.15f)
    }

    fun hasAttackLanded(): Boolean = attackHasHit
    fun markAttackLanded() { attackHasHit = true }

    fun isInvulnerable(): Boolean = invulnerableTimer > 0f

    fun takeDamage(amount: Int) {
        if (isInvulnerable() || !isAlive) return
        currentHp = (currentHp - amount).coerceAtLeast(0)
        hitFlashTimer = 0.3f
        invulnerableTimer = 0.6f
        state = if (isAlive) AnimState.HIT else AnimState.DEFEATED
        attackTimer = 0f
    }

    /** Called once per frame with the current joystick input and attack button state. */
    fun update(dt: Float, moveInput: Float, jumpPressed: Boolean, attackPressed: Boolean, platforms: List<Platform>) {
        if (!isAlive) {
            rig.update(dt, AnimState.DEFEATED, 0f)
            return
        }

        if (hitFlashTimer > 0f) hitFlashTimer -= dt
        if (invulnerableTimer > 0f) invulnerableTimer -= dt

        val isAttacking = state == AnimState.ATTACK
        if (isAttacking) {
            attackTimer += dt
            if (attackTimer >= attackDuration) {
                state = AnimState.IDLE
                attackTimer = 0f
                attackHasHit = false
            }
        } else if (state == AnimState.HIT) {
            // Let the hit recoil pose hold briefly, then return control to the player.
            attackTimer += dt
            if (attackTimer >= 0.25f) {
                state = AnimState.IDLE
                attackTimer = 0f
            }
        } else {
            // Movement is only processed when not mid-attack or mid-hitstun, so attacks
            // commit you to the swing rather than letting you cancel into movement.
            velocityX = moveInput * moveSpeed
            if (moveInput > 0.05f) rig.setFacing(true)
            if (moveInput < -0.05f) rig.setFacing(false)

            if (jumpPressed && isOnGround) {
                velocityY = jumpVelocity
                isOnGround = false
            }

            if (attackPressed) {
                state = AnimState.ATTACK
                attackTimer = 0f
                attackHasHit = false
            }
        }

        // Gravity + vertical integration
        val previousY = y
        velocityY += gravity * dt
        y += velocityY * dt
        x += velocityX * dt

        // Ground/platform collision: land on a platform if we were above it last frame
        // and are at or below its surface this frame, moving downward.
        isOnGround = false
        for (platform in platforms) {
            if (platform.containsX(x) && velocityY >= 0f && previousY <= platform.top + 1f && y >= platform.top) {
                y = platform.top
                velocityY = 0f
                isOnGround = true
            }
        }

        // Resolve the rig's animation state from physics, unless we're mid-attack/hit
        // (those states are time-driven above and shouldn't be overwritten here).
        if (!isAttacking && state != AnimState.HIT) {
            state = when {
                !isOnGround && velocityY < 0f -> AnimState.JUMP
                !isOnGround && velocityY >= 0f -> AnimState.FALL
                kotlin.math.abs(velocityX) > 10f -> AnimState.RUN
                else -> AnimState.IDLE
            }
        }

        val speedFraction = (kotlin.math.abs(velocityX) / moveSpeed).coerceIn(0f, 1f)
        rig.update(dt, state, speedFraction)
    }

    fun draw(canvas: Canvas, cameraX: Float) {
        canvas.save()
        canvas.translate(x - cameraX, y)
        rig.draw(canvas, state)
        canvas.restore()
    }

    fun currentState(): AnimState = state
}
