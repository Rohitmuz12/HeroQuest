package com.example.heroquest.game

import android.content.Context

/**
 * Tracks story-mode progress: which level the player should resume at.
 * Minimal on purpose — this game doesn't have currency/unlocks like the RPG,
 * just linear level progression.
 */
class SaveManager(context: Context) {

    private val prefs = context.getSharedPreferences("hero_quest_save", Context.MODE_PRIVATE)

    /** The level the player should land on when tapping "Continue" — defaults to 1 for a fresh save. */
    var furthestLevelReached: Int
        get() = prefs.getInt(KEY_FURTHEST_LEVEL, 1)
        set(value) {
            if (value > furthestLevelReached) {
                prefs.edit().putInt(KEY_FURTHEST_LEVEL, value).apply()
            }
        }

    fun hasProgress(): Boolean = prefs.contains(KEY_FURTHEST_LEVEL)

    companion object {
        private const val KEY_FURTHEST_LEVEL = "furthest_level"
    }
}
