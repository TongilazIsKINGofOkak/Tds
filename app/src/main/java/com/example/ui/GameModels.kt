package com.example.ui

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class Difficulty(val label: String, val spawnInterval: Long, val enemySpeedMultiplier: Float) {
    EASY("Легко", 2000L, 0.7f),
    NORMAL("Нормально", 1500L, 1.0f),
    HARD("Сложно", 1000L, 1.4f)
}

enum class GameState {
    MENU, PLAYING, PAUSED, GAME_OVER
}

enum class EnemyType(val color: Color, val maxHp: Int, val radius: Float, val score: Int, val speed: Float) {
    BASIC(Color(0xFF00FF66), 1, 40f, 100, 4f),
    FAST(Color(0xFFFFFF00), 1, 30f, 150, 7f),
    TANK(Color(0xFFFF9900), 3, 60f, 300, 2.5f),
    BOSS(Color(0xFFFF007F), 50, 120f, 1000, 1.5f)
}

enum class PowerUpType(val symbol: String, val color: Color) {
    HEAL("H", Color(0xFF00FF66)),
    TRIPLE_SHOT("T", Color(0xFFFFFF00)),
    SHIELD("S", Color(0xFF00FFFF)),
    BLAST("B", Color(0xFFFF007F))
}

data class Star(
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float
)

data class Player(
    var x: Float = 540f,
    var y: Float = 1600f,
    val radius: Float = 50f,
    var health: Int = 3,
    val maxHealth: Int = 3,
    var isInvulnerable: Boolean = false,
    var invulnerableUntil: Long = 0L,
    var tripleShotUntil: Long = 0L,
    var shieldActive: Boolean = false
)

data class Bullet(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float = 10f,
    val isFromPlayer: Boolean,
    val color: Color
)

data class Enemy(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    val type: EnemyType,
    var health: Int,
    var speed: Float,
    val radius: Float,
    var fireCooldown: Long = 0L
)

data class PowerUp(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    val type: PowerUpType,
    val radius: Float = 35f,
    val speedY: Float = 3f
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var size: Float,
    var alpha: Float,
    var lifeSpan: Int,
    val maxLife: Int
)
