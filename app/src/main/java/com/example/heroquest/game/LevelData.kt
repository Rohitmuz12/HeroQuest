package com.example.heroquest.game

/**
 * One enemy's placement within a level, expressed as a fraction of the level's
 * total width (0.0 = far left, 1.0 = far right) so positions scale correctly
 * regardless of actual screen width — same approach the original hardcoded
 * level used (screenWidth * 1.0f, * 1.9f, etc.), just now data instead of code.
 */
data class EnemySpawn(val xFraction: Float)

/**
 * One platform's placement and size, also expressed as fractions of level
 * width/height so it scales with screen size.
 */
data class PlatformSpec(
    val xFraction: Float,
    val widthFraction: Float,
    val heightAboveGroundFraction: Float // how far above ground level, as a fraction of hero height
)

/**
 * A single story-mode level: its length, enemy placements, optional extra
 * platforms, narrative text shown before it starts, and whether it ends in
 * a boss fight instead of a normal "clear all enemies" objective.
 */
data class LevelData(
    val levelNumber: Int,
    val title: String,
    val introText: String,
    val lengthInScreens: Float,
    val enemySpawns: List<EnemySpawn>,
    val platformSpecs: List<PlatformSpec>,
    val hasBoss: Boolean = false,
    val outroText: String = ""
)

/**
 * The full story-mode level roster. Adding a 6th level means adding one more
 * entry here — nothing else in the game needs to change to support it, since
 * GameView reads this list rather than having level layout hardcoded.
 */
object LevelRoster {

    val levels: List<LevelData> = listOf(
        LevelData(
            levelNumber = 1,
            title = "The Outskirts",
            introText = "Trouble has come to the outskirts of town. Clear the road of those responsible.",
            lengthInScreens = 3f,
            enemySpawns = listOf(EnemySpawn(0.35f), EnemySpawn(0.75f)),
            platformSpecs = listOf(
                PlatformSpec(xFraction = 0.5f, widthFraction = 0.12f, heightAboveGroundFraction = 0.85f)
            ),
            outroText = "The road is clear, for now. But this was only the beginning."
        ),
        LevelData(
            levelNumber = 2,
            title = "Old Quarry Road",
            introText = "The path narrows here. More of them are waiting, and the ground itself works against you.",
            lengthInScreens = 4f,
            enemySpawns = listOf(EnemySpawn(0.25f), EnemySpawn(0.5f), EnemySpawn(0.8f)),
            platformSpecs = listOf(
                PlatformSpec(xFraction = 0.33f, widthFraction = 0.1f, heightAboveGroundFraction = 0.85f),
                PlatformSpec(xFraction = 0.55f, widthFraction = 0.09f, heightAboveGroundFraction = 1.15f)
            ),
            outroText = "Quarry's behind you. Something bigger is moving up ahead."
        ),
        LevelData(
            levelNumber = 3,
            title = "The Bridge",
            introText = "A narrow bridge, no room to retreat. Whatever's waiting on the other side wants you to cross.",
            lengthInScreens = 4f,
            enemySpawns = listOf(EnemySpawn(0.2f), EnemySpawn(0.45f), EnemySpawn(0.65f), EnemySpawn(0.85f)),
            platformSpecs = listOf(
                PlatformSpec(xFraction = 0.4f, widthFraction = 0.1f, heightAboveGroundFraction = 0.85f)
            ),
            outroText = "The bridge holds. Just barely."
        ),
        LevelData(
            levelNumber = 4,
            title = "The Approach",
            introText = "You can feel it now — whatever's been pulling the strings is close. It's sent everything it has left.",
            lengthInScreens = 4.5f,
            enemySpawns = listOf(EnemySpawn(0.15f), EnemySpawn(0.35f), EnemySpawn(0.55f), EnemySpawn(0.75f), EnemySpawn(0.9f)),
            platformSpecs = listOf(
                PlatformSpec(xFraction = 0.3f, widthFraction = 0.1f, heightAboveGroundFraction = 0.85f),
                PlatformSpec(xFraction = 0.6f, widthFraction = 0.1f, heightAboveGroundFraction = 1.15f)
            ),
            outroText = "Silence. The path ahead leads to only one place."
        ),
        LevelData(
            levelNumber = 5,
            title = "The Reckoning",
            introText = "This is it. Whatever has been behind all of this stands before you now.",
            lengthInScreens = 2.5f,
            enemySpawns = emptyList(), // boss-only level, no regular enemies
            platformSpecs = emptyList(),
            hasBoss = true,
            outroText = "It's over. The outskirts, the road, the bridge — all of it, finally at peace."
        )
    )

    fun byLevelNumber(number: Int): LevelData = levels.first { it.levelNumber == number }

    fun totalLevels(): Int = levels.size
}
