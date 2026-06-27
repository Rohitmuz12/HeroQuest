package com.example.heroquest.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.heroquest.entity.AnimState
import com.example.heroquest.entity.Boss
import com.example.heroquest.entity.Enemy
import com.example.heroquest.entity.ParticleSystem
import com.example.heroquest.entity.Platform
import com.example.heroquest.entity.Player
import com.example.heroquest.input.TouchButton
import com.example.heroquest.input.VirtualJoystick
import kotlin.random.Random

enum class GameState { MAIN_MENU, STORY_INTRO, PLAYING, STORY_OUTRO, GAME_OVER, VICTORY }

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var groundY = 0f
    private var heroHeight = 0f

    private lateinit var player: Player
    private val enemies = mutableListOf<Enemy>()
    private var boss: Boss? = null
    private val platforms = mutableListOf<Platform>()
    private var levelEndX = 0f
    private var currentLevelNumber = 1
    private val particles = ParticleSystem()
    private data class GlowBlob(val worldX: Float, val y: Float, val radius: Float, val color: Int)
    private val ambientGlows = mutableListOf<GlowBlob>()

    private val saveManager = SaveManager(context)
    private val soundManager = SoundManager()
    private var previousPlayerState = AnimState.IDLE

    private lateinit var joystick: VirtualJoystick
    private lateinit var jumpButton: TouchButton
    private lateinit var attackButton: TouchButton
    private lateinit var dashButton: TouchButton
    private var playButtonBounds = RectF()
    private var continueButtonBounds = RectF()

    private var cameraX = 0f
    private var state = GameState.MAIN_MENU

    private var shakeTime = 0f
    private var shakeMagnitude = 0f

    private val playerGlowColor = 0xFFE8B84B.toInt()
    private val enemyGlowColor = 0xFFB04A4A.toInt()
    private val bossGlowColor = 0xFF8C3FA8.toInt()

    private val skylinePaint = Paint().apply { color = Color.parseColor("#3D3250") }
    private val hpBarBgPaint = Paint().apply { color = Color.parseColor("#3D2F5C") }
    private val hpBarFillPaint = Paint().apply { color = Color.parseColor("#E84B4B") }
    private val bossHpFillPaint = Paint().apply { color = Color.parseColor("#8C3FA8") }
    private val textPaint = Paint().apply {
        color = Color.WHITE; isAntiAlias = true; textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
    }
    private val dimPaint = Paint().apply { color = Color.parseColor("#AA000000") }

    private lateinit var bgGradientPaint: Paint
    private val ambientGlowPaint = Paint().apply { isAntiAlias = true }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height
        groundY = screenHeight * 0.82f
        heroHeight = screenHeight * 0.22f

        bgGradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, screenHeight.toFloat(),
                Color.parseColor("#1A1326"), Color.parseColor("#3D2F5C"),
                Shader.TileMode.CLAMP
            )
        }

        setupControls()

        textPaint.textSize = screenWidth * 0.025f

        gameThread = GameThread(holder, this)
        gameThread?.running = true
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        gameThread?.running = false
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) { }
        }
        soundManager.release()
    }

    fun resume() {
        if (gameThread == null || gameThread?.isAlive == false) {
            gameThread = GameThread(holder, this)
            gameThread?.running = true
            gameThread?.start()
        }
    }

    fun pause() {
        gameThread?.running = false
    }

    private fun setupLevel() {
        val levelData = LevelRoster.byLevelNumber(currentLevelNumber)

        platforms.clear()
        levelEndX = screenWidth * levelData.lengthInScreens
        platforms.add(Platform(left = -200f, top = groundY, right = levelEndX, bottom = groundY + 400f))
        for (spec in levelData.platformSpecs) {
            val platformWidth = levelEndX * spec.widthFraction
            val platformLeft = levelEndX * spec.xFraction - platformWidth / 2f
            val platformTop = groundY - heroHeight * spec.heightAboveGroundFraction
            platforms.add(Platform(left = platformLeft, top = platformTop, right = platformLeft + platformWidth, bottom = platformTop + 40f))
        }

        player = Player(startX = screenWidth * 0.15f, startY = groundY, heightPx = heroHeight)

        enemies.clear()
        for (spawn in levelData.enemySpawns) {
            enemies.add(Enemy(startX = levelEndX * spawn.xFraction, groundY = groundY, heightPx = heroHeight))
        }

        boss = if (levelData.hasBoss) {
            Boss(startX = levelEndX * 0.7f, groundY = groundY, heightPx = heroHeight * 1.4f)
        } else {
            null
        }

        particles.clear()
        ambientGlows.clear()
        val glowColors = intArrayOf(0x33E8B84B, 0x338C6BE8, 0x336BE8D2)
        repeat(24) { i ->
            ambientGlows.add(
                GlowBlob(
                    worldX = Random.nextFloat() * levelEndX,
                    y = groundY - Random.nextFloat() * screenHeight * 0.55f,
                    radius = screenWidth * (0.04f + Random.nextFloat() * 0.05f),
                    color = glowColors[i % glowColors.size]
                )
            )
        }
    }

    private fun setupControls() {
        val margin = screenWidth * 0.02f
        val joystickRadius = screenHeight * 0.16f
        joystick = VirtualJoystick(
            centerX = margin + joystickRadius,
            centerY = screenHeight - margin - joystickRadius,
            baseRadius = joystickRadius
        )

        val buttonRadius = screenHeight * 0.11f
        attackButton = TouchButton(
            centerX = screenWidth - margin - buttonRadius,
            centerY = screenHeight - margin - buttonRadius,
            radius = buttonRadius, label = "ATK", color = Color.parseColor("#E84B4B")
        )
        jumpButton = TouchButton(
            centerX = screenWidth - margin - buttonRadius * 3.4f,
            centerY = screenHeight - margin - buttonRadius * 1.4f,
            radius = buttonRadius * 0.85f, label = "JUMP", color = Color.parseColor("#4B9CE8")
        )
        // DASH sits directly above JUMP (same centerX, smaller centerY i.e. higher
        // up the screen), far enough vertically that their circles don't overlap:
        // vertical gap = (3.6 - 1.4) * buttonRadius = 2.2 * buttonRadius, while the
        // sum of their radii is only (0.85 + 0.75) * buttonRadius = 1.6 * buttonRadius.
        // 2.2 > 1.6, so there's a real gap, not just a non-overlap by coincidence.
        dashButton = TouchButton(
            centerX = screenWidth - margin - buttonRadius * 3.4f,
            centerY = screenHeight - margin - buttonRadius * 3.6f,
            radius = buttonRadius * 0.75f, label = "DASH", color = Color.parseColor("#5CC76A")
        )

        // Main menu button bounds — centered rectangles, Play above Continue.
        val menuButtonWidth = screenWidth * 0.28f
        val menuButtonHeight = screenHeight * 0.13f
        val menuButtonCenterX = screenWidth / 2f
        playButtonBounds = RectF(
            menuButtonCenterX - menuButtonWidth / 2f, screenHeight * 0.5f,
            menuButtonCenterX + menuButtonWidth / 2f, screenHeight * 0.5f + menuButtonHeight
        )
        continueButtonBounds = RectF(
            menuButtonCenterX - menuButtonWidth / 2f, screenHeight * 0.68f,
            menuButtonCenterX + menuButtonWidth / 2f, screenHeight * 0.68f + menuButtonHeight
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                handlePointerDown(pointerId, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    joystick.moveTouch(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                joystick.endTouch(pointerId)
                jumpButton.release(pointerId)
                attackButton.release(pointerId)
                dashButton.release(pointerId)
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    joystick.endTouch(pointerId)
                    jumpButton.release(pointerId)
                    attackButton.release(pointerId)
                    dashButton.release(pointerId)
                }
            }
        }
        return true
    }

    private fun handlePointerDown(pointerId: Int, x: Float, y: Float) {
        when (state) {
            GameState.MAIN_MENU -> handleMainMenuTap(x, y)
            GameState.STORY_INTRO -> {
                soundManager.playButtonTap()
                state = GameState.PLAYING
            }
            GameState.STORY_OUTRO -> {
                soundManager.playButtonTap()
                advanceToNextLevelOrFinish()
            }
            GameState.GAME_OVER -> {
                soundManager.playButtonTap()
                beginLevel(currentLevelNumber) // retry the same level
            }
            GameState.VICTORY -> {
                soundManager.playButtonTap()
                state = GameState.MAIN_MENU
            }
            GameState.PLAYING -> {
                when {
                    joystick.isWithinActivationRange(x, y) -> joystick.startTouch(pointerId, x, y)
                    jumpButton.isWithinRange(x, y) -> jumpButton.press(pointerId)
                    attackButton.isWithinRange(x, y) -> attackButton.press(pointerId)
                    dashButton.isWithinRange(x, y) -> dashButton.press(pointerId)
                }
            }
        }
    }

    private fun handleMainMenuTap(x: Float, y: Float) {
        if (playButtonBounds.contains(x, y)) {
            soundManager.playButtonTap()
            beginLevel(1)
        } else if (saveManager.hasProgress() && continueButtonBounds.contains(x, y)) {
            soundManager.playButtonTap()
            beginLevel(saveManager.furthestLevelReached)
        }
    }

    /** Starts story mode at the given level: builds the level and shows its intro text first. */
    private fun beginLevel(levelNumber: Int) {
        currentLevelNumber = levelNumber
        setupLevel()
        cameraX = 0f
        state = GameState.STORY_INTRO
    }

    /** Called after the player taps through a level's outro text. */
    private fun advanceToNextLevelOrFinish() {
        if (currentLevelNumber >= LevelRoster.totalLevels()) {
            state = GameState.VICTORY
            soundManager.playVictory()
        } else {
            saveManager.furthestLevelReached = currentLevelNumber + 1
            beginLevel(currentLevelNumber + 1)
        }
    }

    fun update(dt: Float) {
        particles.update(dt)
        if (shakeTime > 0f) {
            shakeTime -= dt
            shakeMagnitude *= 0.88f
        } else {
            shakeMagnitude = 0f
        }

        if (state != GameState.PLAYING) return

        val moveInput = joystick.horizontalValue()
        val jumpPressedThisFrame = jumpButton.consumePendingPress()
        val attackPressedThisFrame = attackButton.consumePendingPress()
        val dashPressedThisFrame = dashButton.consumePendingPress()

        player.update(dt, moveInput, jumpPressedThisFrame, attackPressedThisFrame, dashPressedThisFrame, platforms)

        val currentPlayerState = player.currentState()
        if (currentPlayerState != previousPlayerState) {
            when (currentPlayerState) {
                AnimState.JUMP -> soundManager.playJump()
                AnimState.PUNCH -> soundManager.playPunch()
                AnimState.KICK -> soundManager.playKick()
                AnimState.FINISHER -> soundManager.playFinisher()
                AnimState.DASH, AnimState.AIR_DASH -> soundManager.playDash()
                AnimState.HIT -> soundManager.playHitTaken()
                else -> { /* no sound for IDLE/RUN/FALL/JUMP_ATTACK/DEFEATED transitions */ }
            }
        }
        previousPlayerState = currentPlayerState

        if (currentPlayerState == AnimState.DASH || currentPlayerState == AnimState.AIR_DASH) {
            val dashDirX = if (player.rig.facingRight) 1f else -1f
            particles.emitDashTrail(player.x, player.y - heroHeight * 0.5f, playerGlowColor, dashDirX)
        }

        for (enemy in enemies) {
            enemy.update(dt, player.x, player.isAlive)
        }
        boss?.update(dt, player.x, player.isAlive)

        resolveCombat()

        // Camera follows the player, clamped so it never shows past the level bounds.
        cameraX = (player.x - screenWidth * 0.35f).coerceIn(0f, (levelEndX - screenWidth).coerceAtLeast(0f))

        if (!player.isAlive) {
            state = GameState.GAME_OVER
            soundManager.playGameOver()
        } else {
            val regularEnemiesCleared = enemies.all { !it.isAlive }
            val bossCleared = boss?.let { !it.isAlive } ?: true // true if there's no boss this level
            if (regularEnemiesCleared && bossCleared && player.x >= levelEndX - screenWidth * 0.5f) {
                state = GameState.STORY_OUTRO
                soundManager.playLevelComplete()
            }
        }
    }

    private fun resolveCombat() {
        val playerAttackBox = player.attackHitbox()
        if (playerAttackBox != null && !player.hasAttackLanded()) {
            for (enemy in enemies) {
                if (enemy.isAlive && enemy.bounds().intersects(playerAttackBox)) {
                    val wasAlive = enemy.isAlive
                    val isFinisher = player.currentState() == AnimState.FINISHER
                    enemy.takeDamage(player.currentMoveDamage())
                    player.markAttackLanded()

                    val hand = player.rig.leadHandWorldOffset(player.currentState())
                    val sparkX = if (hand != null) player.x + hand.first else enemy.x
                    val sparkY = if (hand != null) player.y + hand.second else enemy.y - heroHeight * 0.5f
                    particles.emitHitSpark(sparkX, sparkY, playerGlowColor)
                    if (isFinisher) {
                        particles.emitHitSpark(sparkX, sparkY, 0xFFFFD23F.toInt())
                        triggerShake(0.22f, 16f)
                    } else {
                        triggerShake(0.15f, 10f)
                    }

                    if (wasAlive && !enemy.isAlive) {
                        particles.emitDefeatBurst(enemy.x, enemy.y - heroHeight * 0.5f, enemyGlowColor)
                        soundManager.playEnemyDefeated()
                        triggerShake(0.25f, 18f)
                    }
                }
            }

            val currentBoss = boss
            if (currentBoss != null && currentBoss.isAlive && currentBoss.bounds().intersects(playerAttackBox)) {
                val wasAlive = currentBoss.isAlive
                val isFinisher = player.currentState() == AnimState.FINISHER
                currentBoss.takeDamage(player.currentMoveDamage())
                player.markAttackLanded()

                val hand = player.rig.leadHandWorldOffset(player.currentState())
                val sparkX = if (hand != null) player.x + hand.first else currentBoss.x
                val sparkY = if (hand != null) player.y + hand.second else currentBoss.y - heroHeight * 0.7f
                particles.emitHitSpark(sparkX, sparkY, playerGlowColor)
                if (isFinisher) {
                    particles.emitHitSpark(sparkX, sparkY, 0xFFFFD23F.toInt())
                    triggerShake(0.22f, 16f)
                } else {
                    triggerShake(0.15f, 10f)
                }

                if (wasAlive && !currentBoss.isAlive) {
                    particles.emitDefeatBurst(currentBoss.x, currentBoss.y - heroHeight * 0.7f, bossGlowColor)
                    soundManager.playEnemyDefeated()
                    triggerShake(0.4f, 26f)
                }
            }
        }

        for (enemy in enemies) {
            val enemyAttackBox = enemy.attackHitbox()
            if (enemyAttackBox != null && !enemy.hasAttackLanded() && player.isAlive) {
                if (player.bounds().intersects(enemyAttackBox)) {
                    player.takeDamage(8)
                    enemy.markAttackLanded()
                    particles.emitHitSpark(player.x, player.y - heroHeight * 0.5f, enemyGlowColor)
                    triggerShake(0.18f, 14f)
                }
            }
        }

        val currentBoss = boss
        if (currentBoss != null && player.isAlive) {
            val bossAttackBox = currentBoss.attackHitbox()
            if (bossAttackBox != null && !currentBoss.hasAttackLanded() && player.bounds().intersects(bossAttackBox)) {
                player.takeDamage(currentBoss.currentMoveDamage())
                currentBoss.markAttackLanded()
                particles.emitHitSpark(player.x, player.y - heroHeight * 0.5f, bossGlowColor)
                triggerShake(0.22f, 18f)
            }
        }
    }

    private fun triggerShake(duration: Float, magnitude: Float) {
        // Take the stronger of the two if multiple hits land the same frame, rather
        // than overwriting with whichever happened to resolve last.
        if (magnitude > shakeMagnitude) {
            shakeTime = duration
            shakeMagnitude = magnitude
        }
    }

    fun render(canvas: Canvas) {
        if (state == GameState.MAIN_MENU) {
            drawMainMenu(canvas)
            return
        }

        canvas.save()

        if (shakeTime > 0f) {
            val dx = (Random.nextFloat() - 0.5f) * shakeMagnitude
            val dy = (Random.nextFloat() - 0.5f) * shakeMagnitude
            canvas.translate(dx, dy)
        }

        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgGradientPaint)

        // Ambient glow blobs drifting through the background, parallaxed slower than
        // the foreground so they read as distant atmosphere, not part of the level geometry.
        val glowParallax = cameraX * 0.5f
        for (glow in ambientGlows) {
            val screenX = glow.worldX - glowParallax
            if (screenX < -glow.radius * 2 || screenX > screenWidth + glow.radius * 2) continue
            ambientGlowPaint.shader = RadialGradient(
                screenX, glow.y, glow.radius, glow.color, 0x00000000, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(screenX, glow.y, glow.radius, ambientGlowPaint)
        }

        // Simple parallax skyline rectangles for additional depth.
        val skylineOffset = cameraX * 0.4f
        var bx = -(skylineOffset % (screenWidth * 0.3f))
        while (bx < screenWidth.toFloat()) {
            canvas.drawRect(bx, groundY - screenHeight * 0.3f, bx + screenWidth * 0.18f, groundY, skylinePaint)
            bx += screenWidth * 0.3f
        }

        for (platform in platforms) platform.draw(canvas, cameraX)
        for (enemy in enemies) enemy.draw(canvas, cameraX)
        boss?.draw(canvas, cameraX)
        player.draw(canvas, cameraX)
        particles.draw(canvas, cameraX)

        drawHud(canvas)

        canvas.restore()

        when (state) {
            GameState.STORY_INTRO -> drawStoryScreen(canvas, isIntro = true)
            GameState.STORY_OUTRO -> drawStoryScreen(canvas, isIntro = false)
            GameState.GAME_OVER -> drawOverlay(canvas, "DEFEATED", "Tap anywhere to retry")
            GameState.VICTORY -> drawOverlay(canvas, "VICTORY!", "Tap anywhere to return to menu")
            GameState.PLAYING -> {
                joystick.draw(canvas)
                jumpButton.draw(canvas)
                attackButton.draw(canvas)
                dashButton.draw(canvas)
            }
            GameState.MAIN_MENU -> { /* handled separately via early return above */ }
        }
    }

    private fun drawHud(canvas: Canvas) {
        val barWidth = screenWidth * 0.3f
        val barHeight = screenHeight * 0.04f
        val barX = screenWidth * 0.03f
        val barY = screenHeight * 0.04f

        canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, hpBarBgPaint)
        val hpFraction = player.currentHp.toFloat() / player.maxHp.toFloat()
        canvas.drawRect(barX, barY, barX + barWidth * hpFraction, barY + barHeight, hpBarFillPaint)

        val currentBoss = boss
        if (currentBoss != null) {
            val bossBarWidth = screenWidth * 0.4f
            val bossBarX = screenWidth - screenWidth * 0.03f - bossBarWidth
            canvas.drawRect(bossBarX, barY, bossBarX + bossBarWidth, barY + barHeight, hpBarBgPaint)
            val bossHpFraction = currentBoss.currentHp.toFloat() / currentBoss.maxHp.toFloat()
            canvas.drawRect(bossBarX, barY, bossBarX + bossBarWidth * bossHpFraction, barY + barHeight, bossHpFillPaint)
            canvas.drawText("BOSS", bossBarX + bossBarWidth / 2f, barY - screenHeight * 0.01f, textPaint)
        } else {
            val remainingEnemies = enemies.count { it.isAlive }
            canvas.drawText("Enemies remaining: $remainingEnemies", screenWidth * 0.5f, screenHeight * 0.06f, textPaint)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), dimPaint)
        val titlePaint = Paint(textPaint).apply { textSize = screenWidth * 0.06f }
        canvas.drawText(title, screenWidth / 2f, screenHeight * 0.42f, titlePaint)
        val subPaint = Paint(textPaint).apply { textSize = screenWidth * 0.025f; alpha = 200 }
        canvas.drawText(subtitle, screenWidth / 2f, screenHeight * 0.5f, subPaint)
    }

    private fun drawMainMenu(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgGradientPaint)

        val titlePaint = Paint(textPaint).apply { textSize = screenWidth * 0.07f }
        canvas.drawText("HERO QUEST", screenWidth / 2f, screenHeight * 0.3f, titlePaint)

        drawMenuButton(canvas, playButtonBounds, "PLAY", Color.parseColor("#E8B84B"))

        if (saveManager.hasProgress() && saveManager.furthestLevelReached > 1) {
            drawMenuButton(canvas, continueButtonBounds, "CONTINUE (Lv ${saveManager.furthestLevelReached})", Color.parseColor("#4B9CE8"))
        }
    }

    private fun drawMenuButton(canvas: Canvas, bounds: RectF, label: String, color: Int) {
        val buttonPaint = Paint().apply { this.color = color; isAntiAlias = true }
        canvas.drawRoundRect(bounds, screenHeight * 0.02f, screenHeight * 0.02f, buttonPaint)
        val labelPaint = Paint(textPaint).apply { textSize = screenWidth * 0.03f; color = Color.parseColor("#1A1326") }
        canvas.drawText(label, bounds.centerX(), bounds.centerY() + labelPaint.textSize * 0.35f, labelPaint)
    }

    /** Shown between levels: the level's intro text before it starts, or outro text after it's cleared. */
    private fun drawStoryScreen(canvas: Canvas, isIntro: Boolean) {
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), dimPaint)
        val levelData = LevelRoster.byLevelNumber(currentLevelNumber)

        val headerPaint = Paint(textPaint).apply { textSize = screenWidth * 0.045f }
        val header = if (isIntro) "LEVEL $currentLevelNumber: ${levelData.title}" else "LEVEL $currentLevelNumber COMPLETE"
        canvas.drawText(header, screenWidth / 2f, screenHeight * 0.35f, headerPaint)

        val bodyText = if (isIntro) levelData.introText else levelData.outroText
        val bodyPaint = Paint(textPaint).apply { textSize = screenWidth * 0.025f; alpha = 220 }
        drawWrappedText(canvas, bodyText, screenWidth / 2f, screenHeight * 0.45f, screenWidth * 0.7f, bodyPaint)

        val promptPaint = Paint(textPaint).apply { textSize = screenWidth * 0.02f; alpha = 180 }
        val prompt = if (isIntro) "Tap to begin" else "Tap to continue"
        canvas.drawText(prompt, screenWidth / 2f, screenHeight * 0.75f, promptPaint)
    }

    /** Simple word-wrapping text draw, since Canvas has no built-in multi-line text support. */
    private fun drawWrappedText(canvas: Canvas, text: String, centerX: Float, startY: Float, maxWidth: Float, paint: Paint) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (paint.measureText(candidate) > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(candidate)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())

        val lineHeight = paint.textSize * 1.4f
        for ((index, line) in lines.withIndex()) {
            canvas.drawText(line, centerX, startY + index * lineHeight, paint)
        }
    }
}
