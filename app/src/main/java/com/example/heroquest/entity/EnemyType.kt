package com.example.heroquest.entity

/**
 * The 4 regular enemy variants. Each carries its own stats/behavior tuning,
 * read by Enemy's constructor — adding a 5th type means adding one more case
 * here plus one more branch in Enemy's stat-lookup, nothing else changes.
 */
enum class EnemyType {
    BRAWLER,  // balanced all-rounder, the default/intro enemy
    BRUTE,    // slow, tanky, hits hard with a longer-reach kick instead of a punch
    STRIKER,  // fast, fragile, attacks more frequently with a quick punch
    GUARD     // patient — waits longer between attacks, but each one hits harder
}
