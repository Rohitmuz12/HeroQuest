package com.example.heroquest.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.heroquest.entity.Enemy
import com.example.heroquest.entity.Platform
import com.example.heroquest.entity.Player
import com.example.heroquest.input.TouchButton
import com.example.heroquest.input.VirtualJoystick

enum class GameState { READY, PLAYING, GAME_OVER, VICTORY }

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var groundY = 0f
    private var heroHeight = 0f

    private lateinit var player: Player
    private val enemies = mutableListOf<Enemy>()
    private val platforms = mutableListOf<Platform>()
    private var levelEndX = 0f

    private lateinit var joystick: VirtualJoystick
    private lateinit var jumpButton: TouchButton
    private lateinit var attackButton: TouchButton

    private var cameraX = 0f
    private var state = GameState.READY

    private val bgPaint = Paint().apply { color = Color.parseColor("#2D2436") }
    private val skylinePaint = Paint().apply { color = Color.parseColor("#3D3250") }
    private val hpBarBgPaint = Paint().apply { color = Color.parseColor("#3D2F5C") }
    private val hpBarFillPaint = Paint().apply { color = Color.parseColor("#E84B4B") }
    private val textPaint = Paint().apply {
        color = Color.WHITE; isAntiAlias = true; textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
    }
    private val dimPaint = Paint().apply { color = Color.parseColor("#AA000000") }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height
        groundY = screenHeight * 0.82f
        heroHeight = screenHeight * 0.22f

        setupLevel()
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
        platforms.clear()
        // One long ground platform spanning the whole level, plus a couple of raised
        // platforms partway through to give the "jump across platforms" feel.
        levelEndX = screenWidth * 4f
        platforms.add(Platform(left = -200f, top = groundY, right = levelEndX, bottom = groundY + 400f))
        platforms.add(Platform(left = screenWidth * 1.3f, top = groundY - heroHeight * 1.1f, right = screenWidth * 1.7f, bottom = groundY - heroHeight * 1.1f + 40f))
        platforms.add(Platform(left = screenWidth * 2.1f, top = groundY - heroHeight * 1.6f, right = screenWidth * 2.45f, bottom = groundY - heroHeight * 1.6f + 40f))

        player = Player(startX = screenWidth * 0.15f, startY = groundY, heightPx = heroHeight)

        enemies.clear()
        enemies.add(Enemy(startX = screenWidth * 1.0f, groundY = groundY, heightPx = heroHeight))
        enemies.add(Enemy(startX = screenWidth * 1.9f, groundY = groundY, heightPx = heroHeight))
        enemies.add(Enemy(startX = screenWidth * 3.0f, groundY = groundY, heightPx = heroHeight))
        enemies.add(Enemy(startX = screenWidth * 3.5f, groundY = groundY, heightPx = heroHeight))
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
            centerX = screenWidth - margin - buttonRadius * 2.6f,
            centerY = screenHeight - margin - buttonRadius * 1.4f,
            radius = buttonRadius * 0.85f, label = "JUMP", color = Color.parseColor("#4B9CE8")
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
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    joystick.endTouch(pointerId)
                    jumpButton.release(pointerId)
                    attackButton.release(pointerId)
                }
            }
        }
        return true
    }

    private fun handlePointerDown(pointerId: Int, x: Float, y: Float) {
        if (state == GameState.READY || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            startOrRestartGame()
            return
        }
        when {
            joystick.isWithinActivationRange(x, y) -> joystick.startTouch(pointerId, x, y)
            jumpButton.isWithinRange(x, y) -> jumpButton.press(pointerId)
            attackButton.isWithinRange(x, y) -> attackButton.press(pointerId)
        }
    }

    private fun startOrRestartGame() {
        setupLevel()
        cameraX = 0f
        state = GameState.PLAYING
    }

    fun update(dt: Float) {
        if (state != GameState.PLAYING) return

        val moveInput = joystick.horizontalValue()
        val jumpPressedThisFrame = jumpButton.consumedPressThisFrame
        val attackPressedThisFrame = attackButton.consumedPressThisFrame

        player.update(dt, moveInput, jumpPressedThisFrame, attackPressedThisFrame, platforms)
        jumpButton.clearFrameFlags()
        attackButton.clearFrameFlags()

        for (enemy in enemies) {
            enemy.update(dt, player.x, player.isAlive)
        }

        resolveCombat()

        // Camera follows the player, clamped so it never shows past the level bounds.
        cameraX = (player.x - screenWidth * 0.35f).coerceIn(0f, (levelEndX - screenWidth).coerceAtLeast(0f))

        if (!player.isAlive) {
            state = GameState.GAME_OVER
        } else if (enemies.all { !it.isAlive } && player.x >= levelEndX - screenWidth * 0.5f) {
            state = GameState.VICTORY
        }
    }

    private fun resolveCombat() {
        val playerAttackBox = player.attackHitbox()
        if (playerAttackBox != null && !player.hasAttackLanded()) {
            for (enemy in enemies) {
                if (enemy.isAlive && enemy.bounds().intersects(playerAttackBox)) {
                    enemy.takeDamage(18)
                    player.markAttackLanded()
                }
            }
        }

        for (enemy in enemies) {
            val enemyAttackBox = enemy.attackHitbox()
            if (enemyAttackBox != null && !enemy.hasAttackLanded() && player.isAlive) {
                if (player.bounds().intersects(enemyAttackBox)) {
                    player.takeDamage(8)
                    enemy.markAttackLanded()
                }
            }
        }
    }

    fun render(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgPaint)

        // Simple parallax skyline rectangles for depth, cheap to draw.
        val skylineOffset = cameraX * 0.4f
        var bx = -(skylineOffset % (screenWidth * 0.3f))
        while (bx < screenWidth.toFloat()) {
            canvas.drawRect(bx, groundY - screenHeight * 0.3f, bx + screenWidth * 0.18f, groundY, skylinePaint)
            bx += screenWidth * 0.3f
        }

        for (platform in platforms) platform.draw(canvas, cameraX)
        for (enemy in enemies) enemy.draw(canvas, cameraX)
        player.draw(canvas, cameraX)

        drawHud(canvas)

        when (state) {
            GameState.READY -> drawOverlay(canvas, "HERO QUEST", "Tap anywhere to start")
            GameState.GAME_OVER -> drawOverlay(canvas, "DEFEATED", "Tap anywhere to retry")
            GameState.VICTORY -> drawOverlay(canvas, "VICTORY!", "Tap anywhere to play again")
            GameState.PLAYING -> {
                joystick.draw(canvas)
                jumpButton.draw(canvas)
                attackButton.draw(canvas)
            }
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

        val remainingEnemies = enemies.count { it.isAlive }
        canvas.drawText("Enemies remaining: $remainingEnemies", screenWidth * 0.5f, screenHeight * 0.06f, textPaint)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), dimPaint)
        val titlePaint = Paint(textPaint).apply { textSize = screenWidth * 0.06f }
        canvas.drawText(title, screenWidth / 2f, screenHeight * 0.42f, titlePaint)
        val subPaint = Paint(textPaint).apply { textSize = screenWidth * 0.025f; alpha = 200 }
        canvas.drawText(subtitle, screenWidth / 2f, screenHeight * 0.5f, subPaint)
    }
}
