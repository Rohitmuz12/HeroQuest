package com.example.heroquest.entity

import android.graphics.Canvas

data class HitBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun intersects(other: HitBox): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }
}

/**
 * The player-controlled hero: platformer physics (gravity, jump, ground/platform
 * collision) plus a full moveset — punch, kick, jump-attack, dash, and a combo
 * finisher — each with its own hitbox timing window, and a state machine that
 * drives which pose HumanoidRig renders.
 *
 * Combo system: landing PUNCH or KICK within `comboWindow` of the previous hit
 * increases comboCount. Reaching `comboCountForFinisher` consecutive hits and
 * attacking again triggers FINISHER instead of another punch/kick — a slower,
 * harder-hitting move. The combo resets if too much time passes between hits
 * or if the player gets hit themselves.
 */
class Player(startX: Float, startY: Float, private val heightPx: Float) {

    var x = startX
    var y = startY // y = position of feet (ground contact point)
    private var velocityX = 0f
    private var velocityY = 0f

    private val gravity = 2200f
    private val jumpVelocity = -1350f
    private val moveSpeed = 420f
    private val dashSpeed = 1100f
    private val dashDuration = 0.22f

    var isOnGround = false
        private set

    var maxHp = 100
    var currentHp = maxHp
    val isAlive: Boolean get() = currentHp > 0

    private var state = AnimState.IDLE
    private var actionTimer = 0f
    private var actionHasHit = false // ensures one swing only lands one hit

    // Per-move durations and damage. Kept as simple constants rather than a data
    // table since there are only 5 moves — a table would be over-engineering here,
    // but if a 6th/7th move gets added later this is the natural point to refactor
    // into a Move(duration, damage, reach) data class instead.
    private val punchDuration = 0.30f
    private val punchDamage = 12
    private val kickDuration = 0.32f
    private val kickDamage = 14
    private val jumpAttackDuration = 0.4f
    private val jumpAttackDamage = 20
    private val finisherDuration = 0.55f
    private val finisherDamage = 38

    private var hitFlashTimer = 0f
    private var invulnerableTimer = 0f

    // --- Combo tracking ---
    private var comboCount = 0
    private var comboResetTimer = 0f
    private val comboWindow = 0.9f // seconds allowed between hits before the combo resets
    private val comboCountForFinisher = 3

    val rig = HumanoidRig(bodyColor = 0xFFE8B84B.toInt(), limbColor = 0xFFB88A2E.toInt(), heightPx = heightPx)

    val width = heightPx * 0.32f // approximate body width for collision

    fun bounds(): HitBox = HitBox(x - width / 2f, y - heightPx, x + width / 2f, y)

    fun currentComboCount(): Int = comboCount

    /** Active only during each move's "hit window" — roughly the forward-swing portion of its animation. */
    fun attackHitbox(): HitBox? {
        val (duration, reachMultiplier, windowStart, windowEnd) = when (state) {
            AnimState.PUNCH -> Quad(punchDuration, 0.55f, 0.25f, 0.75f)
            AnimState.KICK -> Quad(kickDuration, 0.65f, 0.3f, 0.75f)
            AnimState.JUMP_ATTACK -> Quad(jumpAttackDuration, 0.6f, 0.2f, 0.6f)
            AnimState.FINISHER -> Quad(finisherDuration, 0.85f, 0.45f, 0.7f)
            else -> return null
        }
        val progress = actionTimer / duration
        if (progress < windowStart || progress > windowEnd) return null

        val reach = heightPx * reachMultiplier
        val facingDir = if (rig.facingRight) 1f else -1f
        val centerX = x + facingDir * reach * 0.5f
        // Jump-attack's hitbox sits lower (it's an overhead strike landing near the
        // ground in front of the player) rather than centered at chest height.
        val verticalCenter = if (state == AnimState.JUMP_ATTACK) y - heightPx * 0.25f else y - heightPx * 0.45f
        return HitBox(
            centerX - reach / 2f, verticalCenter - heightPx * 0.3f,
            centerX + reach / 2f, verticalCenter + heightPx * 0.3f
        )
    }

    /** Small helper tuple since attackHitbox() needs to branch on 4 values per move. */
    private data class Quad(val duration: Float, val reachMultiplier: Float, val windowStart: Float, val windowEnd: Float)

    fun currentMoveDamage(): Int = when (state) {
        AnimState.PUNCH -> punchDamage
        AnimState.KICK -> kickDamage
        AnimState.JUMP_ATTACK -> jumpAttackDamage
        AnimState.FINISHER -> finisherDamage
        else -> 0
    }

    fun hasAttackLanded(): Boolean = actionHasHit
    fun markAttackLanded() {
        actionHasHit = true
        comboCount += 1
        comboResetTimer = comboWindow
    }

    fun isInvulnerable(): Boolean = invulnerableTimer > 0f

    fun takeDamage(amount: Int) {
        if (isInvulnerable() || !isAlive) return
        currentHp = (currentHp - amount).coerceAtLeast(0)
        hitFlashTimer = 0.3f
        invulnerableTimer = 0.6f
        state = if (isAlive) AnimState.HIT else AnimState.DEFEATED
        actionTimer = 0f
        comboCount = 0 // getting hit breaks your combo
        comboResetTimer = 0f
    }

    private fun isActionState(s: AnimState): Boolean {
        return s == AnimState.PUNCH || s == AnimState.KICK || s == AnimState.JUMP_ATTACK ||
            s == AnimState.DASH || s == AnimState.FINISHER
    }

    private fun durationFor(s: AnimState): Float = when (s) {
        AnimState.PUNCH -> punchDuration
        AnimState.KICK -> kickDuration
        AnimState.JUMP_ATTACK -> jumpAttackDuration
        AnimState.DASH -> dashDuration
        AnimState.FINISHER -> finisherDuration
        else -> 0f
    }

    /** Called once per frame with the current input state. */
    fun update(
        dt: Float,
        moveInput: Float,
        jumpPressed: Boolean,
        attackPressed: Boolean,
        dashPressed: Boolean,
        platforms: List<Platform>
    ) {
        if (!isAlive) {
            rig.update(dt, AnimState.DEFEATED, 0f)
            return
        }

        if (hitFlashTimer > 0f) hitFlashTimer -= dt
        if (invulnerableTimer > 0f) invulnerableTimer -= dt

        if (comboResetTimer > 0f) {
            comboResetTimer -= dt
            if (comboResetTimer <= 0f) comboCount = 0
        }

        val isInAction = isActionState(state)
        if (isInAction) {
            actionTimer += dt
            if (actionTimer >= durationFor(state)) {
                state = AnimState.IDLE
                actionTimer = 0f
                actionHasHit = false
            } else if (state == AnimState.DASH) {
                // Dash moves the player directly rather than going through the normal
                // moveInput->velocityX path, so it covers ground at a fixed burst speed
                // regardless of joystick position during the dash.
                val dashDir = if (rig.facingRight) 1f else -1f
                velocityX = dashDir * dashSpeed
            }
        } else if (state == AnimState.HIT) {
            actionTimer += dt
            if (actionTimer >= 0.25f) {
                state = AnimState.IDLE
                actionTimer = 0f
            }
        } else {
            // Movement/inputs are only processed when not mid-action or mid-hitstun,
            // so committing to a move means committing to its full animation.
            velocityX = moveInput * moveSpeed
            if (moveInput > 0.05f) rig.setFacing(true)
            if (moveInput < -0.05f) rig.setFacing(false)

            if (jumpPressed && isOnGround) {
                velocityY = jumpVelocity
                isOnGround = false
            }

            if (dashPressed) {
                state = AnimState.DASH
                actionTimer = 0f
                actionHasHit = false
            } else if (attackPressed) {
                state = chooseNextAttack()
                actionTimer = 0f
                actionHasHit = false
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

        // Resolve the rig's animation state from physics, unless we're mid-action/hit
        // (those states are time-driven above and shouldn't be overwritten here).
        // IMPORTANT: re-check the LIVE `state` field here, not a snapshot captured
        // earlier in this function — a stale snapshot was the root cause of a real
        // bug where freshly-triggered attacks got silently overwritten back to IDLE
        // within the same frame, before ever being rendered.
        if (!isActionState(state) && state != AnimState.HIT) {
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

    /**
     * Decides which attack to play when the attack button is pressed: jump-attack
     * if airborne, otherwise advance the punch/kick combo chain, escalating to
     * FINISHER once comboCountForFinisher consecutive hits have landed.
     */
    private fun chooseNextAttack(): AnimState {
        if (!isOnGround) return AnimState.JUMP_ATTACK
        if (comboCount >= comboCountForFinisher) return AnimState.FINISHER
        // Alternate punch/kick each step of the chain so the combo reads as a
        // real sequence (punch, kick, punch, kick...) rather than spamming one move.
        return if (comboCount % 2 == 0) AnimState.PUNCH else AnimState.KICK
    }

    fun draw(canvas: Canvas, cameraX: Float) {
        canvas.save()
        canvas.translate(x - cameraX, y)
        rig.draw(canvas, state)
        canvas.restore()
    }

    fun currentState(): AnimState = state
}
