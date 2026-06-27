package com.example.heroquest.game

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Placeholder sound system using Android's built-in ToneGenerator — no audio
 * asset files exist yet, so this produces simple procedural beeps just to
 * confirm the timing/wiring is correct. Swapping in real sound effects later
 * means replacing the internals of playX() methods (e.g. with SoundPool and
 * actual .ogg/.mp3 files) without touching any call site elsewhere in the game.
 *
 * Volume/tone choices are deliberately simple and short — these are meant to
 * be replaced, not polished.
 */
class SoundManager {

    private var toneGenerator: ToneGenerator? = null
    var isMuted = false

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (e: RuntimeException) {
            // Some devices/emulators can fail to acquire the tone generator;
            // fail silently rather than crash the game over missing sound.
            toneGenerator = null
        }
    }

    private fun play(tone: Int, durationMs: Int) {
        if (isMuted) return
        toneGenerator?.startTone(tone, durationMs)
    }

    fun playJump() = play(ToneGenerator.TONE_PROP_BEEP, 80)
    fun playPunch() = play(ToneGenerator.TONE_SUP_PIP, 60)
    fun playKick() = play(ToneGenerator.TONE_SUP_PIP, 70)
    fun playFinisher() = play(ToneGenerator.TONE_CDMA_PIP, 150)
    fun playDash() = play(ToneGenerator.TONE_PROP_ACK, 90)
    fun playHitTaken() = play(ToneGenerator.TONE_SUP_ERROR, 120)
    fun playEnemyDefeated() = play(ToneGenerator.TONE_PROP_PROMPT, 140)
    fun playLevelComplete() = play(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
    fun playVictory() = play(ToneGenerator.TONE_CDMA_CONFIRM, 400)
    fun playGameOver() = play(ToneGenerator.TONE_CDMA_ABBR_ALERT, 350)
    fun playButtonTap() = play(ToneGenerator.TONE_PROP_BEEP2, 50)

    /** Must be called when the game view is destroyed, to release the native ToneGenerator resource. */
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
