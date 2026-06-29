package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.Difficulty
import com.example.ui.EnemyType
import com.example.ui.GameState
import com.example.ui.GameViewModel
import com.example.ui.PowerUpType
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GameApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun GameApp(
    modifier: Modifier = Modifier,
    gameViewModel: GameViewModel = viewModel()
) {
    val gameState by gameViewModel.gameState.collectAsStateWithLifecycle()

    // Stars background ticks continuously across all screens
    LaunchedEffect(Unit) {
        var lastTime = System.currentTimeMillis()
        while (true) {
            withFrameMillis {
                val now = System.currentTimeMillis()
                val delta = (now - lastTime).coerceIn(1L, 100L)
                gameViewModel.onTick(now, delta)
                lastTime = now
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
    ) {
        // Shared continuous background stars rendering
        StarsBackground(viewModel = gameViewModel)

        when (gameState) {
            GameState.MENU -> {
                MenuScreen(
                    viewModel = gameViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
            GameState.PLAYING, GameState.PAUSED -> {
                PlayingScreen(
                    viewModel = gameViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
            GameState.GAME_OVER -> {
                GameOverScreen(
                    viewModel = gameViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Screen edge vignette overlay (placed on top of everything)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val radialGradient = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        center = center,
                        radius = size.maxDimension / 1.1f
                    )
                    drawRect(brush = radialGradient)
                }
        )
    }
}

@Composable
fun StarsBackground(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val starsList by viewModel.stars.collectAsStateWithLifecycle()

    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw deep slate background
        drawRect(color = Color(0xFF09090B))

        // Draw radial grid pattern [radial-gradient(#1e1b4b_1px,transparent_1px)] with background-size: 24.dp
        val stepPx = 24.dp.toPx()
        val dotRadius = 1.2f.dp.toPx()
        val gridColor = Color(0xFF1E1B4B)

        var x = 0f
        while (x < size.width) {
            var y = 0f
            while (y < size.height) {
                drawCircle(
                    color = gridColor,
                    radius = dotRadius,
                    center = Offset(x, y)
                )
                y += stepPx
            }
            x += stepPx
        }

        for (star in starsList) {
            // Draw standard resolution scaled stars
            val scaleX = size.width / 1080f
            val scaleY = size.height / 1920f
            val scale = minOf(scaleX, scaleY)

            val offsetX = (size.width - 1080f * scale) / 2f
            val offsetY = (size.height - 1920f * scale) / 2f

            val sx = offsetX + star.x * scale
            val sy = offsetY + star.y * scale

            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.size * scale,
                center = Offset(sx, sy)
            )
        }
    }
}

@Composable
fun MenuScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val topScores by viewModel.topScores.collectAsStateWithLifecycle()
    val currentDiff by viewModel.difficulty.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val globalHighScore by viewModel.highScore.collectAsStateWithLifecycle()

    // Pulsing title effect
    val infiniteTransition = rememberInfiniteTransition(label = "title_pulse")
    val titleScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header and Volume toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .testTag("mute_button")
                    .background(Color(0xFF101030).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { viewModel.toggleMute() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isMuted) "SOUND OFF" else "SOUND ON",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Title Area
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Text(
                text = "NEON STRIKER",
                fontSize = (38f * titleScale).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF00E5FF),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "РЕТРО ТОП-ДАУН ШУТЕР",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFFF007F),
                letterSpacing = 4.sp
            )
        }

        // Best Score Card
        // Best Score Card
        if (globalHighScore > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B).copy(alpha = 0.85f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ЛИЧНЫЙ РЕКОРД:",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$globalHighScore PTS",
                        color = Color(0xFFFFFF00),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Difficulty Chips Selection
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "СЛОЖНОСТЬ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Difficulty.values().forEach { diff ->
                    val isSelected = currentDiff == diff
                    val chipColor = when (diff) {
                        Difficulty.EASY -> Color(0xFF00FF66)
                        Difficulty.NORMAL -> Color(0xFFFFFF00)
                        Difficulty.HARD -> Color(0xFFFF007F)
                    }
                    val testTagStr = "difficulty_${diff.name.lowercase()}"

                    Box(
                        modifier = Modifier
                            .testTag(testTagStr)
                            .clickable { viewModel.setDifficulty(diff) }
                            .background(
                                if (isSelected) chipColor.copy(alpha = 0.25f) else Color(0xFF18181B).copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) chipColor else Color(0xFF27272A),
                                shape = RoundedCornerShape(8.dp)
                              )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = diff.label,
                            color = if (isSelected) chipColor else Color(0xFFA1A1AA),
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Play Button Action
        Button(
            onClick = { viewModel.startGame() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .testTag("play_button")
                .fillMaxWidth(0.85f)
                .height(56.dp)
                .drawBehind {
                    // Draw blurred glowing shadow
                    drawRoundRect(
                        color = Color(0xFF4F46E5).copy(alpha = 0.35f),
                        topLeft = Offset(-8f, -8f),
                        size = androidx.compose.ui.geometry.Size(size.width + 16f, size.height + 16f),
                        cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                    )
                }
                .border(3.dp, Color(0xFF818CF8), RoundedCornerShape(28.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Icon",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ЗАПУСТИТЬ ИГРУ",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Top 10 Highscores list
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .weight(1f, fill = false)
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "РЕЙТИНГ ПИЛОТОВ",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF007F),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Center
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B).copy(alpha = 0.8f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .border(1.dp, Color(0xFF27272A), RoundedCornerShape(16.dp))
            ) {
                if (topScores.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Список пилотов пуст.\nБудь первым, кто установит рекорд!",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(topScores) { index, scoreEntry ->
                            val rankColor = when (index) {
                                0 -> Color(0xFFFFFF00) // Gold
                                1 -> Color(0xFFCCC2DC) // Silver
                                2 -> Color(0xFFCD7F32) // Bronze
                                else -> Color.White.copy(alpha = 0.8f)
                            }
                            val sdf = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
                            val dateStr = sdf.format(Date(scoreEntry.timestamp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${index + 1} ",
                                        color = rankColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = scoreEntry.playerName,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "($dateStr)",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = "${scoreEntry.score} PTS",
                                    color = rankColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick playing instructions
        Text(
            text = "ИНСТРУКЦИЯ:\nУдерживай палец и плавно води в любом месте для уклонения.\nОгонь автоматический. Собирай выпадающие улучшения!",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.45f),
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun PlayingScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val scoreVal by viewModel.score.collectAsStateWithLifecycle()
    val currentDiff by viewModel.difficulty.collectAsStateWithLifecycle()
    val isPaused = viewModel.gameState.collectAsStateWithLifecycle().value == GameState.PAUSED
    val enemiesList by viewModel.enemies.collectAsStateWithLifecycle()

    val boss = remember(enemiesList) { enemiesList.find { it.type == EnemyType.BOSS } }

    Box(modifier = modifier.fillMaxSize()) {
        // High fidelity retro gameplay viewport canvas
        GameCanvas(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // Heads Up Display overlays (Top Screen HUD)
        val currentTime = System.currentTimeMillis()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score text in Vibrant Palette HUD style
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "SCORE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA1A1AA), // zinc-400
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = String.format("%06d", scoreVal),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Right side: Health Pill and Weapon Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Health pill: bg-zinc-900/80, rounded-full, border zinc-800
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF18181B).copy(alpha = 0.8f), CircleShape)
                                .border(1.dp, Color(0xFF27272A), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "♥", // favorite heart icon
                                color = Color(0xFFEF4444), // red-500
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            // Progress bar track: bg-zinc-800
                            val hpRatio = player.health.toFloat() / player.maxHealth.toFloat()
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(8.dp)
                                    .background(Color(0xFF27272A), CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(hpRatio.coerceIn(0f, 1f))
                                        .background(Color(0xFFEF4444), CircleShape)
                                )
                            }
                        }

                        // Weapon/Power-up Status pill
                        val tripleTimeRemaining = player.tripleShotUntil - currentTime
                        val (statusLabel, statusColor, statusSymbol) = when {
                            player.shieldActive -> Triple("SHIELD ACTIVE", Color(0xFF00FFFF), "🛡")
                            tripleTimeRemaining > 0 -> Triple("TRIPLE SHOT", Color(0xFFFFFF00), "⚡")
                            player.isInvulnerable -> Triple("INVULNERABLE", Color(0xFFFF007F), "🛡")
                            else -> Triple("AMMO 30/30", Color.White.copy(alpha = 0.7f), "⚡")
                        }

                        Row(
                            modifier = Modifier
                                .background(Color(0xFF18181B).copy(alpha = 0.8f), CircleShape)
                                .border(1.dp, Color(0xFF27272A), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = statusSymbol,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = statusLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Pause button
                    Box(
                        modifier = Modifier
                            .testTag("pause_button")
                            .size(40.dp)
                            .background(Color(0xFF18181B).copy(alpha = 0.8f), CircleShape)
                            .border(1.dp, Color(0xFF27272A), CircleShape)
                            .clickable { viewModel.pauseGame() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "||",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Weapon/Active PowerUp indicators
            val tripleTimeRemaining = player.tripleShotUntil - currentTime
            if (tripleTimeRemaining > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .background(Color(0xFF18181B).copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFFFFF00).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "BOOST",
                        fontSize = 9.sp,
                        color = Color(0xFFFFFF00),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    LinearProgressIndicator(
                        progress = { (tripleTimeRemaining.toFloat() / 10000f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .width(60.dp)
                            .height(4.dp),
                        color = Color(0xFFFFFF00),
                        trackColor = Color(0xFF27272A)
                    )
                }
            }

            // Boss Alert & HP Bar
            if (boss != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ВНИМАНИЕ: МАТЕРИНСКИЙ КОРАБЛЬ БОССА!",
                        color = Color(0xFFFF007F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    // Boss HP progress bar
                    val hpRatio = boss.health.toFloat() / (EnemyType.BOSS.maxHp + (scoreVal / 1000) * 10)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(14.dp)
                            .background(Color(0xFF101030), RoundedCornerShape(4.dp))
                            .border(1.5.dp, Color(0xFFFF007F), RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(hpRatio.coerceIn(0f, 1f))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFF007F), Color.White)
                                    )
                                )
                        )
                        Text(
                            text = "${boss.health} HP",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        // Invulnerable indicator flash text
        if (player.isInvulnerable) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 100.dp)
                    .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "ЩИТ НЕУЯЗВИМОСТИ",
                    color = Color.Red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Pause overlay modal
        AnimatedVisibility(
            visible = isPaused,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101030)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ПАУЗА",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        Button(
                            onClick = { viewModel.resumeGame() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                "ПРОДОЛЖИТЬ",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.exitToMenu() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = Brush.linearGradient(listOf(Color.White, Color.White))
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "В ГЛАВНОЕ МЕНЮ",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameCanvas(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val bulletsList by viewModel.bullets.collectAsStateWithLifecycle()
    val enemiesList by viewModel.enemies.collectAsStateWithLifecycle()
    val powerUpsList by viewModel.powerUps.collectAsStateWithLifecycle()
    val particlesList by viewModel.particles.collectAsStateWithLifecycle()
    val starsList by viewModel.stars.collectAsStateWithLifecycle()

    val currentTime = System.currentTimeMillis()

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }

        // Virtual canonical resolution to keep game adaptive on all devices
        val virtualW = 1080f
        val virtualH = 1920f

        val scaleX = screenW / virtualW
        val scaleY = screenH / virtualH
        val scale = minOf(scaleX, scaleY)

        val gameW = virtualW * scale
        val gameH = virtualH * scale

        val offsetX = (screenW - gameW) / 2f
        val offsetY = (screenH - gameH) / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Pass logical relative delta to viewmodel
                        viewModel.movePlayerRelative(dragAmount.x / scale, dragAmount.y / scale)
                    }
                }
        ) {
            // Draw deep space viewport rect
            drawRect(
                color = Color(0xFF09090B),
                topLeft = Offset(offsetX, offsetY),
                size = androidx.compose.ui.geometry.Size(gameW, gameH)
            )

            // Clip game drawings to viewport edges
            clipRect(
                left = offsetX,
                top = offsetY,
                right = offsetX + gameW,
                bottom = offsetY + gameH
            ) {
                // Draw radial grid pattern inside the viewport
                val stepPx = 24.dp.toPx()
                val dotRadius = 1.2f.dp.toPx()
                val gridColor = Color(0xFF1E1B4B)

                var gx = offsetX
                while (gx < offsetX + gameW) {
                    var gy = offsetY
                    while (gy < offsetY + gameH) {
                        drawCircle(
                            color = gridColor,
                            radius = dotRadius,
                            center = Offset(gx, gy)
                        )
                        gy += stepPx
                    }
                    gx += stepPx
                }

                // Background moving stars
                for (star in starsList) {
                    val sx = offsetX + star.x * scale
                    val sy = offsetY + star.y * scale
                    drawCircle(
                        color = Color.White.copy(alpha = star.alpha),
                        radius = star.size * scale,
                        center = Offset(sx, sy)
                    )
                }

                // Player & Enemy active ammunition bullets
                for (b in bulletsList) {
                    val bx = offsetX + b.x * scale
                    val by = offsetY + b.y * scale
                    val r = b.radius * scale
                    
                    drawCircle(
                        color = b.color,
                        radius = r,
                        center = Offset(bx, by)
                    )
                    // Bloom glow
                    drawCircle(
                        color = b.color.copy(alpha = 0.35f),
                        radius = r * 2.2f,
                        center = Offset(bx, by)
                    )
                }

                // Dropped falling power-ups
                for (pu in powerUpsList) {
                    val px = offsetX + pu.x * scale
                    val py = offsetY + pu.y * scale
                    val r = pu.radius * scale

                    // Spinning outer neon ring
                    drawCircle(
                        color = pu.type.color.copy(alpha = 0.25f),
                        radius = r * 1.5f,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = pu.type.color,
                        radius = r,
                        center = Offset(px, py),
                        style = Stroke(width = 3f * scale)
                    )

                    // Draw custom geometric indicator instead of text
                    drawPowerUpSymbol(px, py, r, pu.type.symbol, pu.type.color, this, scale)
                }

                // User Spaceship fighter
                val px = offsetX + player.x * scale
                val py = offsetY + player.y * scale
                val pr = player.radius * scale

                // Dynamic burning engine tail
                val plumeSize = pr * (0.8f + (currentTime % 6 * 0.08f))
                val plumePath = Path().apply {
                    moveTo(px - pr * 0.22f, py + pr * 0.45f)
                    lineTo(px, py + pr * 0.45f + plumeSize)
                    lineTo(px - pr * 0.22f, py + pr * 0.45f)
                    close()
                }
                drawPath(plumePath, color = Color(0xFFFF6D00))
                drawPath(plumePath, color = Color(0xFFFFEB3B), style = Stroke(width = 1.5f * scale))

                // User body wings
                val shipPath = Path().apply {
                    moveTo(px, py - pr)                      // Nose cone
                    lineTo(px - pr * 0.9f, py + pr)          // Wing left
                    lineTo(px - pr * 0.25f, py + pr * 0.4f)  // Eng split left
                    lineTo(px + pr * 0.25f, py + pr * 0.4f)  // Eng split right
                    lineTo(px + pr * 0.9f, py + pr)          // Wing right
                    close()
                }

                if (!player.isInvulnerable || (currentTime / 80) % 2 == 0L) {
                    // Glowing outer aura shadow for player spaceship
                    drawCircle(
                        color = Color(0xFF6366F1).copy(alpha = 0.35f),
                        radius = pr * 1.5f,
                        center = Offset(px, py)
                    )

                    drawPath(shipPath, color = Color(0xFF6366F1)) // Indigo-500
                    drawPath(shipPath, color = Color(0xFFA5B4FC), style = Stroke(width = 2.5f * scale)) // Indigo-300

                    // Cockpit canopy
                    val canopyPath = Path().apply {
                        moveTo(px, py - pr * 0.55f)
                        lineTo(px - pr * 0.18f, py - pr * 0.1f)
                        lineTo(px + pr * 0.18f, py - pr * 0.1f)
                        close()
                    }
                    drawPath(canopyPath, color = Color(0xCCFFFFFF))

                    // Shield force field
                    if (player.shieldActive) {
                        val shieldAngleOffset = currentTime / 400.0
                        drawCircle(
                            color = Color(0xFF00FFFF).copy(alpha = 0.18f + sin(currentTime / 180.0).toFloat() * 0.05f),
                            radius = pr * 1.5f,
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = Color(0xFF00FFFF),
                            radius = pr * 1.5f,
                            center = Offset(px, py),
                            style = Stroke(width = 3.5f * scale)
                        )
                        // Orbiting visual indicator arcs
                        for (i in 0 until 4) {
                            val angle = i * Math.PI / 2.0 + shieldAngleOffset
                            val ax = (px + cos(angle) * pr * 1.5f).toFloat()
                            val ay = (py + sin(angle) * pr * 1.5f).toFloat()
                            drawCircle(color = Color.White, radius = 6f * scale, center = Offset(ax, ay))
                        }
                    }
                }

                // Active space enemies
                for (enemy in enemiesList) {
                    val ex = offsetX + enemy.x * scale
                    val ey = offsetY + enemy.y * scale
                    val er = enemy.radius * scale

                    val enemyColor = when (enemy.type) {
                        EnemyType.BASIC -> Color(0xFFE11D48) // Rose-600
                        EnemyType.FAST -> Color(0xFFF59E0B)  // Amber-500
                        EnemyType.TANK -> Color(0xFFEA580C)  // Orange-600
                        EnemyType.BOSS -> Color(0xFFFF007F)  // Magenta
                    }
                    val enemyBorderColor = when (enemy.type) {
                        EnemyType.BASIC -> Color(0xFFFB7185) // Rose-400
                        EnemyType.FAST -> Color(0xFFFDE68A)  // Amber-200
                        EnemyType.TANK -> Color(0xFFFDBA74)  // Orange-300
                        EnemyType.BOSS -> Color(0xFFFBCFE8)  // Pink-200
                    }

                    // Outer glowing shadow aura for enemies
                    drawCircle(
                        color = enemyColor.copy(alpha = 0.35f),
                        radius = er * 1.4f,
                        center = Offset(ex, ey)
                    )

                    // Health bar details
                    if (enemy.type == EnemyType.TANK && enemy.health < enemy.type.maxHp) {
                        val barW = er * 1.2f
                        val barH = 5f * scale
                        drawRect(
                            color = Color.Red,
                            topLeft = Offset(ex - barW / 2, ey - er - 14f),
                            size = androidx.compose.ui.geometry.Size(barW, barH)
                        )
                        drawRect(
                            color = Color(0xFF00FF66),
                            topLeft = Offset(ex - barW / 2, ey - er - 14f),
                            size = androidx.compose.ui.geometry.Size(barW * (enemy.health.toFloat() / enemy.type.maxHp), barH)
                        )
                    }

                    when (enemy.type) {
                        EnemyType.BASIC -> {
                            val path = Path().apply {
                                moveTo(ex, ey - er)
                                lineTo(ex + er * 0.9f, ey)
                                lineTo(ex, ey + er * 0.9f)
                                lineTo(ex - er * 0.9f, ey)
                                close()
                            }
                            drawPath(path, color = enemyColor)
                            drawPath(path, color = enemyBorderColor, style = Stroke(width = 2f * scale))
                            drawCircle(color = Color.White, radius = er * 0.2f, center = Offset(ex, ey))
                        }
                        EnemyType.FAST -> {
                            val path = Path().apply {
                                moveTo(ex, ey + er)
                                lineTo(ex - er * 0.8f, ey - er * 0.8f)
                                lineTo(ex, ey - er * 0.25f)
                                lineTo(ex + er * 0.8f, ey - er * 0.8f)
                                close()
                            }
                            drawPath(path, color = enemyColor)
                            drawPath(path, color = enemyBorderColor, style = Stroke(width = 2f * scale))
                        }
                        EnemyType.TANK -> {
                            val path = Path().apply {
                                for (i in 0 until 6) {
                                    val angle = i * Math.PI / 3.0
                                    val hx = (ex + cos(angle) * er).toFloat()
                                    val hy = (ey + sin(angle) * er).toFloat()
                                    if (i == 0) moveTo(hx, hy) else lineTo(hx, hy)
                                }
                                close()
                            }
                            drawPath(path, color = enemyColor)
                            drawPath(path, color = enemyBorderColor, style = Stroke(width = 2.5f * scale))
                            drawCircle(color = Color.Red, radius = er * 0.25f, center = Offset(ex, ey))
                        }
                        EnemyType.BOSS -> {
                            drawCircle(
                                color = enemyColor.copy(alpha = 0.12f),
                                radius = er,
                                center = Offset(ex, ey)
                            )
                            drawCircle(
                                color = enemyColor,
                                radius = er,
                                center = Offset(ex, ey),
                                style = Stroke(width = 3.5f * scale)
                            )
                            // Core
                            val pulseR = er * 0.38f * (0.92f + sin(currentTime / 110.0).toFloat() * 0.08f)
                            drawCircle(
                                color = Color.White,
                                radius = pulseR,
                                center = Offset(ex, ey)
                            )

                            // Outer gear/panels rotating
                            for (i in 0 until 8) {
                                val angle = i * Math.PI / 4.0 + (currentTime / 1800.0)
                                val ax = (ex + cos(angle) * er).toFloat()
                                val ay = (ey + sin(angle) * er).toFloat()
                                drawCircle(
                                    color = Color.White,
                                    radius = 11f * scale,
                                    center = Offset(ax, ay)
                                )
                                drawCircle(
                                    color = enemyColor,
                                    radius = 7f * scale,
                                    center = Offset(ax, ay)
                                )
                            }
                        }
                    }
                }

                // Explosions & hits particles
                for (part in particlesList) {
                    val px = offsetX + part.x * scale
                    val py = offsetY + part.y * scale
                    drawCircle(
                        color = part.color.copy(alpha = part.alpha),
                        radius = part.size * scale,
                        center = Offset(px, py)
                    )
                }
            }

            // Cabinet glowing outer border line
            drawRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(offsetX, offsetY),
                size = androidx.compose.ui.geometry.Size(gameW, gameH),
                style = Stroke(width = 4f * scale)
            )
        }
    }
}

@Composable
fun GameOverScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val finalScore by viewModel.score.collectAsStateWithLifecycle()
    val highscoreVal by viewModel.highScore.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B).copy(alpha = 0.95f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .drawBehind {
                    // Draw soft glowing Rose-600 shadow
                    drawRoundRect(
                        color = Color(0xFFE11D48).copy(alpha = 0.3f),
                        topLeft = Offset(-6f, -6f),
                        size = androidx.compose.ui.geometry.Size(size.width + 12f, size.height + 12f),
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                }
                .border(2.dp, Color(0xFFE11D48), RoundedCornerShape(24.dp))
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ИГРА ОКОНЧЕНА",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE11D48), // Rose-600
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "КОРАБЛЬ РАЗРУШЕН В БОЮ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA1A1AA), // zinc-400
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Scores
                Text(
                    text = "ТВОЙ СЧЁТ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "$finalScore PTS",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (finalScore >= highscoreVal && finalScore > 0) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFFF00).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFFFFF00), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "НОВЫЙ РЕКОРД!",
                            color = Color(0xFFFFFF00),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Text(
                        text = "ЛУЧШИЙ РЕКОРД: $highscoreVal PTS",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }

                // Play Again Button Action (Styled like our Indigo-600 CTA button)
                Button(
                    onClick = { viewModel.startGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .testTag("restart_button")
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(2.dp, Color(0xFF818CF8), RoundedCornerShape(24.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ИГРАТЬ СНОВА",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Exit button styled to match the dark theme
                OutlinedButton(
                    onClick = { viewModel.exitToMenu() },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF18181B),
                        contentColor = Color(0xFFA1A1AA)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = Brush.linearGradient(listOf(Color(0xFF27272A), Color(0xFF27272A)))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "ВЕРНУТЬСЯ В МЕНЮ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

fun drawPowerUpSymbol(
    px: Float,
    py: Float,
    r: Float,
    symbol: String,
    color: Color,
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    scale: Float
) {
    with(drawScope) {
        val size = r * 0.8f
        val strokeW = 4f * scale
        when (symbol) {
            "H" -> {
                // Horizontal bar
                drawLine(
                    color = color,
                    start = Offset(px - size * 0.5f, py),
                    end = Offset(px + size * 0.5f, py),
                    strokeWidth = strokeW * 1.5f
                )
                // Vertical bar
                drawLine(
                    color = color,
                    start = Offset(px, py - size * 0.5f),
                    end = Offset(px, py + size * 0.5f),
                    strokeWidth = strokeW * 1.5f
                )
            }
            "T" -> {
                drawLine(
                    color = color,
                    start = Offset(px - size * 0.5f, py - size * 0.5f),
                    end = Offset(px + size * 0.5f, py - size * 0.5f),
                    strokeWidth = strokeW
                )
                drawLine(
                    color = color,
                    start = Offset(px, py - size * 0.5f),
                    end = Offset(px, py + size * 0.5f),
                    strokeWidth = strokeW
                )
            }
            "S" -> {
                val path = Path().apply {
                    moveTo(px, py - size * 0.6f)
                    lineTo(px + size * 0.5f, py - size * 0.3f)
                    lineTo(px + size * 0.5f, py + size * 0.2f)
                    lineTo(px, py + size * 0.6f)
                    lineTo(px - size * 0.5f, py + size * 0.2f)
                    lineTo(px - size * 0.5f, py - size * 0.3f)
                    close()
                }
                drawPath(path, color = color, style = Stroke(width = strokeW))
            }
            "B" -> {
                drawCircle(color = color, radius = r * 0.4f)
                for (i in 0 until 8) {
                    val angle = i * Math.PI / 4.0
                    val sx = (px + cos(angle) * r * 0.4f).toFloat()
                    val sy = (py + sin(angle) * r * 0.4f).toFloat()
                    val ex = (px + cos(angle) * r * 0.8f).toFloat()
                    val ey = (py + sin(angle) * r * 0.8f).toFloat()
                    drawLine(color = color, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = strokeW * 0.75f)
                }
            }
        }
    }
}
