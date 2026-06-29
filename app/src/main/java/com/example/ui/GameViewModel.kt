package com.example.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HighScore
import com.example.data.HighScoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Random
import java.util.UUID

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = HighScoreRepository(database.highScoreDao())

    val topScores: StateFlow<List<HighScore>> = repository.topScores
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val soundManager = SoundManager()

    // Game states
    private val _gameState = MutableStateFlow(GameState.MENU)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _highScore = MutableStateFlow(0)
    val highScore: StateFlow<Int> = _highScore.asStateFlow()

    private val _difficulty = MutableStateFlow(Difficulty.NORMAL)
    val difficulty: StateFlow<Difficulty> = _difficulty.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Game collections
    val playerState = MutableStateFlow(Player())
    val bullets = MutableStateFlow<List<Bullet>>(emptyList())
    val enemies = MutableStateFlow<List<Enemy>>(emptyList())
    val powerUps = MutableStateFlow<List<PowerUp>>(emptyList())
    val particles = MutableStateFlow<List<Particle>>(emptyList())
    val stars = MutableStateFlow<List<Star>>(emptyList())

    // Game balance & loop tracking
    private val random = Random()
    private var lastSpawnTime = 0L
    private var lastPlayerFireTime = 0L
    private var bossActive = false
    private var scoreUntilNextBoss = 600

    init {
        // Load high score
        viewModelScope.launch {
            _highScore.value = repository.getMaxScore()
        }
        // Initialize starfield
        generateStarfield()
    }

    fun setDifficulty(diff: Difficulty) {
        _difficulty.value = diff
    }

    fun toggleMute() {
        soundManager.toggleMute()
        _isMuted.value = soundManager.getMuted()
    }

    private fun generateStarfield() {
        val list = mutableListOf<Star>()
        for (i in 0 until 60) {
            list.add(
                Star(
                    x = random.nextFloat() * 1080f,
                    y = random.nextFloat() * 1920f,
                    speed = 2f + random.nextFloat() * 6f,
                    size = 2f + random.nextFloat() * 4f,
                    alpha = 0.3f + random.nextFloat() * 0.7f
                )
            )
        }
        stars.value = list
    }

    fun startGame() {
        _score.value = 0
        bossActive = false
        scoreUntilNextBoss = 600
        bullets.value = emptyList()
        enemies.value = emptyList()
        powerUps.value = emptyList()
        particles.value = emptyList()
        playerState.value = Player(
            x = 540f,
            y = 1600f,
            health = when (_difficulty.value) {
                Difficulty.EASY -> 5
                Difficulty.NORMAL -> 3
                Difficulty.HARD -> 2
            },
            maxHealth = when (_difficulty.value) {
                Difficulty.EASY -> 5
                Difficulty.NORMAL -> 3
                Difficulty.HARD -> 2
            }
        )
        lastSpawnTime = System.currentTimeMillis()
        lastPlayerFireTime = System.currentTimeMillis()
        _gameState.value = GameState.PLAYING
        
        // Refresh high score just in case
        viewModelScope.launch {
            _highScore.value = repository.getMaxScore()
        }
    }

    fun pauseGame() {
        if (_gameState.value == GameState.PLAYING) {
            _gameState.value = GameState.PAUSED
        }
    }

    fun resumeGame() {
        if (_gameState.value == GameState.PAUSED) {
            _gameState.value = GameState.PLAYING
        }
    }

    fun exitToMenu() {
        _gameState.value = GameState.MENU
    }

    // Relative drag move logic to keep finger out of the spaceship's way
    fun movePlayerRelative(dx: Float, dy: Float) {
        if (_gameState.value != GameState.PLAYING) return
        val current = playerState.value
        val newX = (current.x + dx).coerceIn(current.radius, 1080f - current.radius)
        // Allow moving anywhere in the bottom 70% of the screen
        val newY = (current.y + dy).coerceIn(600f, 1920f - current.radius)
        playerState.value = current.copy(x = newX, y = newY)
    }

    fun onTick(currentTimeMs: Long, deltaTimeMs: Long) {
        if (_gameState.value != GameState.PLAYING) {
            // Update stars even in menu/game over for nice moving background
            val currentStars = stars.value.map { star ->
                var newY = star.y + star.speed * (deltaTimeMs / 16.6f) * 0.3f
                if (newY > 1920) {
                    newY = 0f
                    star.x = random.nextFloat() * 1080f
                }
                star.copy(y = newY)
            }
            stars.value = currentStars
            return
        }

        val frameFactor = deltaTimeMs / 16.6f

        // 1. Move stars (faster in-game)
        val currentStars = stars.value.map { star ->
            var newY = star.y + star.speed * frameFactor
            if (newY > 1920) {
                newY = 0f
                star.x = random.nextFloat() * 1080f
            }
            star.copy(y = newY)
        }
        stars.value = currentStars

        // 2. Invulnerability and Triple-shot timers
        val player = playerState.value
        if (player.isInvulnerable && currentTimeMs > player.invulnerableUntil) {
            playerState.value = player.copy(isInvulnerable = false)
        }
        val isTripleActive = player.tripleShotUntil > currentTimeMs

        // 3. Player Firing (Auto-shoot)
        val fireCooldown = 180L // Fast shoot
        if (currentTimeMs - lastPlayerFireTime >= fireCooldown) {
            lastPlayerFireTime = currentTimeMs
            soundManager.playShoot()
            
            val newBullets = bullets.value.toMutableList()
            if (isTripleActive) {
                // Mid
                newBullets.add(Bullet(x = player.x, y = player.y - 40f, vx = 0f, vy = -20f, isFromPlayer = true, color = Color(0xFF00FFFF)))
                // Left
                newBullets.add(Bullet(x = player.x - 20f, y = player.y - 20f, vx = -4f, vy = -18f, isFromPlayer = true, color = Color(0xFFFFFF00)))
                // Right
                newBullets.add(Bullet(x = player.x + 20f, y = player.y - 20f, vx = 4f, vy = -18f, isFromPlayer = true, color = Color(0xFFFFFF00)))
            } else {
                newBullets.add(Bullet(x = player.x, y = player.y - 40f, vx = 0f, vy = -22f, isFromPlayer = true, color = Color(0xFF00FF66)))
            }
            bullets.value = newBullets
        }

        // 4. Bullet Movement
        val bulletList = bullets.value.mapNotNull { b ->
            val nextX = b.x + b.vx * frameFactor
            val nextY = b.y + b.vy * frameFactor
            if (nextY < 0 || nextY > 1920 || nextX < 0 || nextX > 1080) {
                null // Off screen
            } else {
                b.copy(x = nextX, y = nextY)
            }
        }
        bullets.value = bulletList

        // 5. Spawning Enemies
        val currentDiff = _difficulty.value
        val spawnInterval = currentDiff.spawnInterval
        if (currentTimeMs - lastSpawnTime >= spawnInterval) {
            lastSpawnTime = currentTimeMs
            val activeEnemies = enemies.value.toMutableList()

            // Check if we should spawn Boss
            if (_score.value >= scoreUntilNextBoss && !bossActive) {
                bossActive = true
                soundManager.playPowerUp() // Play warning-like sound
                activeEnemies.add(
                    Enemy(
                        x = 540f,
                        y = -150f,
                        type = EnemyType.BOSS,
                        health = EnemyType.BOSS.maxHp + (_score.value / 1000) * 10, // Boss scales up
                        speed = EnemyType.BOSS.speed,
                        radius = EnemyType.BOSS.radius
                    )
                )
            } else if (!bossActive) {
                // Normal spawn
                val x = 80f + random.nextFloat() * 920f
                val type = when {
                    _score.value < 400 -> if (random.nextFloat() < 0.25f) EnemyType.FAST else EnemyType.BASIC
                    _score.value < 1000 -> {
                        val r = random.nextFloat()
                        if (r < 0.2f) EnemyType.TANK else if (r < 0.5f) EnemyType.FAST else EnemyType.BASIC
                    }
                    else -> {
                        val r = random.nextFloat()
                        if (r < 0.35f) EnemyType.TANK else if (r < 0.65f) EnemyType.FAST else EnemyType.BASIC
                    }
                }
                activeEnemies.add(
                    Enemy(
                        x = x,
                        y = -50f,
                        type = type,
                        health = type.maxHp,
                        speed = type.speed * currentDiff.enemySpeedMultiplier,
                        radius = type.radius
                    )
                )
            }
            enemies.value = activeEnemies
        }

        // 6. Enemy updates & firing
        val updatedEnemies = mutableListOf<Enemy>()
        val enemyBullets = mutableListOf<Bullet>()

        for (enemy in enemies.value) {
            var nextY = enemy.y
            var nextX = enemy.x
            
            if (enemy.type == EnemyType.BOSS) {
                // Boss enters to y = 350 and moves side-to-side
                if (enemy.y < 350f) {
                    nextY += enemy.speed * frameFactor
                } else {
                    // Sway left and right
                    val timeScale = currentTimeMs / 1000.0
                    nextX = 540f + (sin(timeScale) * 350f).toFloat()
                }

                // Boss firing
                if (currentTimeMs > enemy.fireCooldown) {
                    enemy.fireCooldown = currentTimeMs + 1200L // Fast boss shooting
                    soundManager.playHit()
                    // 3-bullet spread downward
                    enemyBullets.add(Bullet(x = enemy.x - 40f, y = enemy.y + 100f, vx = -3f, vy = 10f, isFromPlayer = false, color = Color(0xFFFF007F)))
                    enemyBullets.add(Bullet(x = enemy.x, y = enemy.y + 120f, vx = 0f, vy = 12f, isFromPlayer = false, color = Color(0xFFFF007F)))
                    enemyBullets.add(Bullet(x = enemy.x + 40f, y = enemy.y + 100f, vx = 3f, vy = 10f, isFromPlayer = false, color = Color(0xFFFF007F)))
                }
            } else {
                nextY += enemy.speed * frameFactor
                // Tank enemies sway slightly and fire periodically
                if (enemy.type == EnemyType.TANK) {
                    nextX += sin(currentTimeMs / 300.0).toFloat() * 1.5f * frameFactor
                    if (currentTimeMs > enemy.fireCooldown) {
                        enemy.fireCooldown = currentTimeMs + 2000L
                        enemyBullets.add(Bullet(x = enemy.x, y = enemy.y + enemy.radius, vx = 0f, vy = 8f, isFromPlayer = false, color = Color(0xFFFF9900)))
                    }
                }
            }

            if (nextY < 2100f) { // Remove if way past bottom
                updatedEnemies.add(enemy.copy(x = nextX, y = nextY))
            } else {
                // Enemy reached the bottom uncaught: deduct player score slightly or health
                if (enemy.type != EnemyType.BOSS) {
                    val p = playerState.value
                    if (!p.isInvulnerable) {
                        damagePlayer()
                    }
                } else {
                    // Boss wraps around
                    updatedEnemies.add(enemy.copy(y = -150f))
                }
            }
        }
        enemies.value = updatedEnemies

        // Add enemy bullets to main list
        if (enemyBullets.isNotEmpty()) {
            bullets.value = bullets.value + enemyBullets
        }

        // 7. PowerUps movement
        val updatedPowerUps = powerUps.value.mapNotNull { p ->
            val nextY = p.y + p.speedY * frameFactor
            if (nextY > 2000f) null else p.copy(y = nextY)
        }
        powerUps.value = updatedPowerUps

        // 8. Collisions: Player Bullets vs Enemies
        val currentBullets = bullets.value.toMutableList()
        val currentEnemies = enemies.value.toMutableList()
        val currentPowerUps = powerUps.value.toMutableList()
        val activeParticles = particles.value.toMutableList()

        val bulletsToRemove = mutableSetOf<String>()
        val enemiesToDamage = mutableMapOf<String, Int>() // id -> damage taken

        for (b in currentBullets) {
            if (!b.isFromPlayer) continue
            for (enemy in currentEnemies) {
                val dist = distance(b.x, b.y, enemy.x, enemy.y)
                if (dist < b.radius + enemy.radius) {
                    bulletsToRemove.add(b.id)
                    enemiesToDamage[enemy.id] = (enemiesToDamage[enemy.id] ?: 0) + 1
                    // hit particle
                    createHitParticles(b.x, b.y, b.color, activeParticles)
                    soundManager.playHit()
                    break
                }
            }
        }

        // Apply enemy damage and handle deaths
        val finalEnemies = mutableListOf<Enemy>()
        for (enemy in currentEnemies) {
            val damage = enemiesToDamage[enemy.id] ?: 0
            if (damage > 0) {
                enemy.health -= damage
                if (enemy.health <= 0) {
                    // Enemy destroyed!
                    _score.value += enemy.type.score
                    soundManager.playExplosion()
                    createExplosion(enemy.x, enemy.y, enemy.type.color, activeParticles)

                    if (enemy.type == EnemyType.BOSS) {
                        bossActive = false
                        scoreUntilNextBoss = _score.value + 800
                        // Spawn multiple power ups
                        for (i in 0 until 3) {
                            val puType = PowerUpType.values()[random.nextInt(PowerUpType.values().size)]
                            currentPowerUps.add(
                                PowerUp(
                                    x = enemy.x - 80f + random.nextFloat() * 160f,
                                    y = enemy.y - 40f + random.nextFloat() * 80f,
                                    type = puType
                                )
                            )
                        }
                    } else {
                        // 15% chance to drop powerup
                        if (random.nextFloat() < 0.15f) {
                            val puType = PowerUpType.values()[random.nextInt(PowerUpType.values().size)]
                            currentPowerUps.add(PowerUp(x = enemy.x, y = enemy.y, type = puType))
                        }
                    }
                } else {
                    finalEnemies.add(enemy)
                }
            } else {
                finalEnemies.add(enemy)
            }
        }
        enemies.value = finalEnemies

        // 9. Collisions: Enemy Bullets or Enemies vs Player
        val playerRadius = playerState.value.radius
        val p = playerState.value
        
        if (!p.isInvulnerable) {
            // Check bullets
            for (b in currentBullets) {
                if (b.isFromPlayer) continue
                val dist = distance(b.x, b.y, p.x, p.y)
                val checkRadius = if (p.shieldActive) playerRadius + 30f else playerRadius
                if (dist < b.radius + checkRadius) {
                    bulletsToRemove.add(b.id)
                    damagePlayer()
                    break
                }
            }

            // Check enemies directly
            for (enemy in enemies.value) {
                val dist = distance(enemy.x, enemy.y, p.x, p.y)
                val checkRadius = if (p.shieldActive) playerRadius + 30f else playerRadius
                if (dist < enemy.radius + checkRadius) {
                    // Collision!
                    damagePlayer()
                    // Damage the enemy too
                    enemy.health -= 1
                    if (enemy.health <= 0) {
                        createExplosion(enemy.x, enemy.y, enemy.type.color, activeParticles)
                        soundManager.playExplosion()
                        if (enemy.type == EnemyType.BOSS) {
                            bossActive = false
                            scoreUntilNextBoss = _score.value + 800
                        }
                    }
                    break
                }
            }
        }

        // Clean up collided bullets
        bullets.value = currentBullets.filter { it.id !in bulletsToRemove }

        // Remove dead enemies if they were killed by player ramming
        enemies.value = enemies.value.filter { it.health > 0 }

        // 10. Collisions: Player vs PowerUps
        val finalPowerUps = mutableListOf<PowerUp>()
        for (pu in currentPowerUps) {
            val dist = distance(pu.x, pu.y, p.x, p.y)
            if (dist < pu.radius + playerRadius) {
                collectPowerUp(pu.type)
                // spark effects
                createHitParticles(pu.x, pu.y, pu.type.color, activeParticles)
            } else {
                finalPowerUps.add(pu)
            }
        }
        powerUps.value = finalPowerUps

        // 11. Particles Update
        val updatedParticles = activeParticles.mapNotNull { part ->
            part.x += part.vx * frameFactor
            part.y += part.vy * frameFactor
            part.lifeSpan -= deltaTimeMs.toInt()
            if (part.lifeSpan <= 0) {
                null
            } else {
                part.alpha = (part.lifeSpan.toFloat() / part.maxLife.toFloat()).coerceIn(0f, 1f)
                part.copy()
            }
        }
        particles.value = updatedParticles
    }

    private fun damagePlayer() {
        val current = playerState.value
        if (current.isInvulnerable) return

        if (current.shieldActive) {
            // Destroy shield first
            playerState.value = current.copy(
                shieldActive = false,
                isInvulnerable = true,
                invulnerableUntil = System.currentTimeMillis() + 1500L
            )
            soundManager.playExplosion()
            return
        }

        val nextHealth = current.health - 1
        if (nextHealth <= 0) {
            // Game Over
            playerState.value = current.copy(health = 0)
            soundManager.playExplosion()
            _gameState.value = GameState.GAME_OVER
            saveScore()
        } else {
            playerState.value = current.copy(
                health = nextHealth,
                isInvulnerable = true,
                invulnerableUntil = System.currentTimeMillis() + 1500L
            )
            soundManager.playExplosion()
        }
    }

    private fun collectPowerUp(type: PowerUpType) {
        soundManager.playPowerUp()
        val current = playerState.value
        when (type) {
            PowerUpType.HEAL -> {
                val newHp = (current.health + 1).coerceAtMost(current.maxHealth)
                playerState.value = current.copy(health = newHp)
            }
            PowerUpType.TRIPLE_SHOT -> {
                playerState.value = current.copy(
                    tripleShotUntil = System.currentTimeMillis() + 10000L // 10 seconds
                )
            }
            PowerUpType.SHIELD -> {
                playerState.value = current.copy(shieldActive = true)
            }
            PowerUpType.BLAST -> {
                // Clear all normal enemies on screen and damage boss
                val activeParticles = particles.value.toMutableList()
                val currentEnemies = enemies.value
                for (enemy in currentEnemies) {
                    createExplosion(enemy.x, enemy.y, enemy.type.color, activeParticles)
                    _score.value += if (enemy.type == EnemyType.BOSS) 0 else enemy.type.score
                }
                particles.value = activeParticles

                // Keep only boss with reduced health
                val finalEnemies = mutableListOf<Enemy>()
                for (enemy in currentEnemies) {
                    if (enemy.type == EnemyType.BOSS) {
                        enemy.health = (enemy.health - 15).coerceAtLeast(1)
                        finalEnemies.add(enemy)
                    }
                }
                enemies.value = finalEnemies
                bullets.value = emptyList() // clear bullets too
            }
        }
    }

    private fun createExplosion(x: Float, y: Float, color: Color, list: MutableList<Particle>) {
        val count = 20 + random.nextInt(15)
        for (i in 0 until count) {
            val angle = random.nextFloat() * 2f * Math.PI
            val speed = 2f + random.nextFloat() * 12f
            val vx = (cos(angle) * speed).toFloat()
            val vy = (sin(angle) * speed).toFloat()
            val maxLife = 400 + random.nextInt(500)
            list.add(
                Particle(
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    color = color,
                    size = 4f + random.nextFloat() * 8f,
                    alpha = 1f,
                    lifeSpan = maxLife,
                    maxLife = maxLife
                )
            )
        }
    }

    private fun createHitParticles(x: Float, y: Float, color: Color, list: MutableList<Particle>) {
        val count = 5 + random.nextInt(5)
        for (i in 0 until count) {
            val angle = random.nextFloat() * 2f * Math.PI
            val speed = 1f + random.nextFloat() * 4f
            val vx = (cos(angle) * speed).toFloat()
            val vy = (sin(angle) * speed).toFloat()
            val maxLife = 200 + random.nextInt(200)
            list.add(
                Particle(
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    color = color,
                    size = 2f + random.nextFloat() * 4f,
                    alpha = 1f,
                    lifeSpan = maxLife,
                    maxLife = maxLife
                )
            )
        }
    }

    private fun saveScore() {
        val finalScore = _score.value
        if (finalScore <= 0) return
        viewModelScope.launch {
            repository.insertScore(HighScore(score = finalScore, playerName = "Игрок"))
            _highScore.value = repository.getMaxScore()
        }
    }

    // Helper math functions
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun cos(angle: Double): Double = kotlin.math.cos(angle)
    private fun sin(angle: Double): Double = kotlin.math.sin(angle)
}
