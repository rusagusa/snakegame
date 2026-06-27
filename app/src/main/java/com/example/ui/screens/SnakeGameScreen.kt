package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HighScore
import com.example.game.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Elegant Dark Theme Colors matching our design theme guidelines
object ArcadeColors {
    val DeepObsidian = Color(0xFF111318) // Main canvas slate background
    val MidnightMatte = Color(0xFF1C1B1F) // Container background
    val CyberCyan = Color(0xFFD0BCFF) // Accent Purple/Lavender
    val NeonGreen = Color(0xFFA8E6CF) // Snake body mint color
    val NeonGreenDark = Color(0xFF88C7B0) // Slightly darker mint color
    val HotMagenta = Color(0xFFFF8B94) // Food coral pink color
    val RetroGold = Color(0xFFFFD700) // Gold
    val CharcoalGrey = Color(0xFF2D2F33) // Obstacles
    val ScreenBezel = Color(0xFF2D2F33) // Casing
    val DarkBezel = Color(0xFF111318) // Dark dialog background
    val GlassOverlay = Color(0x1AD0BCFF)
    val SoftGrid = Color(0x08FFFFFF) // Extremely subtle grid pattern
}

@Composable
fun SnakeGameScreen(
    viewModel: SnakeViewModel,
    modifier: Modifier = Modifier
) {
    val snake by viewModel.snake.collectAsStateWithLifecycle()
    val previousSnake by viewModel.previousSnake.collectAsStateWithLifecycle()
    val food by viewModel.food.collectAsStateWithLifecycle()
    val goldenFood by viewModel.goldenFood.collectAsStateWithLifecycle()
    val goldenFoodTimer by viewModel.goldenFoodTimer.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val gameStatus by viewModel.gameStatus.collectAsStateWithLifecycle()
    val currentScore by viewModel.currentScore.collectAsStateWithLifecycle()
    val highScore by viewModel.highScore.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    val obstacles by viewModel.obstacles.collectAsStateWithLifecycle()
    val topScores by viewModel.topScores.collectAsStateWithLifecycle()

    val isSoundEnabled by viewModel.isSoundEnabled.collectAsStateWithLifecycle()
    val isHapticEnabled by viewModel.isHapticEnabled.collectAsStateWithLifecycle()

    // Smooth movement interpolation frame calculations
    var interpolationFraction by remember { mutableStateOf(1f) }

    LaunchedEffect(gameStatus) {
        if (gameStatus == GameStatus.RUNNING) {
            while (true) {
                val now = System.currentTimeMillis()
                val elapsed = now - viewModel.lastTickTimeMs
                interpolationFraction = (elapsed.toFloat() / viewModel.tickDurationMs).coerceIn(0f, 1f)
                delay(16) // ~60fps ticker update
            }
        } else {
            interpolationFraction = 1f
        }
    }

    // Keyboard and focus management
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Modal dialogs state
    var showLeaderboard by remember { mutableStateOf(false) }
    var showNameEntry by remember { mutableStateOf(false) }
    var playerNameInput by remember { mutableStateOf("") }

    // On Game Over, trigger the name entry dialog once
    LaunchedEffect(gameStatus) {
        if (gameStatus == GameStatus.GAME_OVER) {
            playerNameInput = ""
            // Only prompt if they actually played and scored
            if (currentScore > 0) {
                showNameEntry = true
            }
        }
    }

    // Request keyboard focus when running
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ArcadeColors.DeepObsidian),
        containerColor = ArcadeColors.DeepObsidian
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                viewModel.changeDirection(Direction.UP)
                                true
                            }
                            Key.DirectionDown -> {
                                viewModel.changeDirection(Direction.DOWN)
                                true
                            }
                            Key.DirectionLeft -> {
                                viewModel.changeDirection(Direction.LEFT)
                                true
                            }
                            Key.DirectionRight -> {
                                viewModel.changeDirection(Direction.RIGHT)
                                true
                            }
                            Key.Spacebar, Key.Enter -> {
                                when (gameStatus) {
                                    GameStatus.NOT_STARTED, GameStatus.GAME_OVER -> viewModel.startGame()
                                    GameStatus.RUNNING -> viewModel.pauseGame()
                                    GameStatus.PAUSED -> viewModel.resumeGame()
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Arcade Header / Scoreboard Panel
            ScoreboardPanel(
                score = currentScore,
                highScore = highScore,
                difficulty = difficulty,
                goldenTimer = goldenFoodTimer,
                isGoldenActive = goldenFood != null,
                isSoundEnabled = isSoundEnabled,
                isHapticEnabled = isHapticEnabled,
                onToggleSound = { viewModel.toggleSound() },
                onToggleHaptic = { viewModel.toggleHaptic() },
                onShowLeaderboard = { showLeaderboard = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Main CRT Game Screen / Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .aspectRatio(viewModel.gridWidth.toFloat() / viewModel.gridHeight.toFloat(), matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(40.dp))
                    .border(4.dp, Color(0xFF2D2F33), RoundedCornerShape(40.dp))
                    .shadow(12.dp, RoundedCornerShape(40.dp), spotColor = Color(0x1F000000))
                    .background(Color(0xFF1A1C1E)),
                contentAlignment = Alignment.Center
            ) {
                // Game Board Canvas
                GameBoardCanvas(
                    snake = snake,
                    previousSnake = previousSnake,
                    food = food,
                    goldenFood = goldenFood,
                    obstacles = obstacles,
                    gridWidth = viewModel.gridWidth,
                    gridHeight = viewModel.gridHeight,
                    interpolationFraction = interpolationFraction,
                    direction = direction,
                    gameStatus = gameStatus,
                    onSwipe = { dir -> viewModel.changeDirection(dir) }
                )

                // Overlays based on game state
                if (gameStatus == GameStatus.NOT_STARTED) {
                    ArcadeOverlay(
                        title = "RETRO SNAKE",
                        subtitle = "INSERT COIN TO PLAY",
                        buttonText = "START GAME",
                        onButtonClick = {
                            focusRequester.requestFocus()
                            viewModel.startGame()
                        }
                    )
                }

                if (gameStatus == GameStatus.PAUSED) {
                    ArcadeOverlay(
                        title = "GAME PAUSED",
                        subtitle = "READY PLAYER ONE",
                        buttonText = "RESUME",
                        onButtonClick = {
                            focusRequester.requestFocus()
                            viewModel.resumeGame()
                        }
                    )
                }

                if (gameStatus == GameStatus.GAME_OVER) {
                    ArcadeOverlay(
                        title = "GAME OVER",
                        subtitle = "FINAL SCORE: $currentScore",
                        buttonText = "PLAY AGAIN",
                        onButtonClick = {
                            focusRequester.requestFocus()
                            viewModel.startGame()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Arcade Controller Base (D-pad & Speed Selection)
            ControllerPanel(
                gameStatus = gameStatus,
                currentDifficulty = difficulty,
                highScore = highScore,
                onDirectionClick = { dir -> viewModel.changeDirection(dir) },
                onDifficultyClick = { diff -> viewModel.setDifficulty(diff) },
                onCenterClick = {
                    focusRequester.requestFocus()
                    when (gameStatus) {
                        GameStatus.NOT_STARTED, GameStatus.GAME_OVER -> viewModel.startGame()
                        GameStatus.RUNNING -> viewModel.pauseGame()
                        GameStatus.PAUSED -> viewModel.resumeGame()
                    }
                }
            )
        }

        // --- MODAL DIALOGS ---

        // A. Leaderboard Bottom Sheet / Dialog
        if (showLeaderboard) {
            LeaderboardDialog(
                difficulty = difficulty,
                scores = topScores,
                onDismiss = { showLeaderboard = false },
                onClearScores = { viewModel.clearScores() }
            )
        }

        // B. Name Entry Dialog for High Scores
        if (showNameEntry) {
            NameEntryDialog(
                score = currentScore,
                difficulty = difficulty,
                inputName = playerNameInput,
                onNameChange = { playerNameInput = it },
                onSave = {
                    viewModel.submitHighScore(playerNameInput)
                    showNameEntry = false
                    showLeaderboard = true // Show them their entry!
                },
                onDismiss = { showNameEntry = false }
            )
        }
    }
}

@Composable
fun ScoreboardPanel(
    score: Int,
    highScore: Int,
    difficulty: Difficulty,
    goldenTimer: Int,
    isGoldenActive: Boolean,
    isSoundEnabled: Boolean,
    isHapticEnabled: Boolean,
    onToggleSound: () -> Unit,
    onToggleHaptic: () -> Unit,
    onShowLeaderboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Title & Subtitle with active session green dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFD0BCFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "Snake Pro",
                        tint = Color(0xFF381E72),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Snake Pro",
                        color = Color(0xFFE2E2E6),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.5).sp
                      )
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(4.dp)
                      ) {
                          Box(
                              modifier = Modifier
                                  .size(6.dp)
                                  .clip(CircleShape)
                                  .background(Color(0xFF4CAF50))
                          )
                          Text(
                              text = "Active Session",
                              color = Color(0xFF919094),
                              fontSize = 11.sp,
                              fontWeight = FontWeight.Medium
                          )
                      }
                }
            }

            // Right side: Interactive Toggles (styled beautifully as outline circles)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leaderboard
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1B1F))
                        .border(1.dp, Color(0xFF44474E), CircleShape)
                        .clickable { onShowLeaderboard() }
                        .testTag("leaderboard_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = "Leaderboard",
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Sound Toggle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1B1F))
                        .border(1.dp, Color(0xFF44474E), CircleShape)
                        .clickable { onToggleSound() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Sound Toggle",
                        tint = if (isSoundEnabled) Color(0xFFD0BCFF) else Color(0xFF919094),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Haptic Toggle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1B1F))
                        .border(1.dp, Color(0xFF44474E), CircleShape)
                        .clickable { onToggleHaptic() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isHapticEnabled) Icons.Default.Vibration else Icons.Default.Vibration,
                        contentDescription = "Haptic Toggle",
                        tint = if (isHapticEnabled) Color(0xFFD0BCFF) else Color(0xFF919094),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Scoreboard Cards Grid Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Current Score
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1C1B1F), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF44474E), RoundedCornerShape(24.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "CURRENT SCORE",
                        color = Color(0xFF919094),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format("%,d", score),
                        color = Color(0xFFD0BCFF),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // Card 2: Level / Difficulty info
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1C1B1F), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF44474E), RoundedCornerShape(24.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "LEVEL / SPEED",
                        color = Color(0xFF919094),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = difficulty.label.uppercase(),
                        color = Color(0xFFE2E2E6),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        // Golden active indicator overlay (inside/below cards if active)
        if (isGoldenActive) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .background(Color(0x22FFD700), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Golden Booster Active",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GOLDEN POINT: ${goldenTimer}s",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun GameBoardCanvas(
    snake: List<Point>,
    previousSnake: List<Point>,
    food: Point,
    goldenFood: Point?,
    obstacles: Set<Point>,
    gridWidth: Int,
    gridHeight: Int,
    interpolationFraction: Float,
    direction: Direction,
    gameStatus: GameStatus,
    onSwipe: (Direction) -> Unit
) {
    // Swipe gestures state variables
    var totalDragX by remember { mutableStateOf(0f) }
    var totalDragY by remember { mutableStateOf(0f) }

    // Pulses for Food items
    val infiniteTransition = rememberInfiniteTransition(label = "canvas_pulses")
    val normalFoodPulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "normal_pulse"
    )
    val goldenFoodPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(280, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "golden_pulse"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        val threshold = 55f
                        if (abs(totalDragX) > threshold || abs(totalDragY) > threshold) {
                            if (abs(totalDragX) > abs(totalDragY)) {
                                if (totalDragX > 0) onSwipe(Direction.RIGHT) else onSwipe(Direction.LEFT)
                            } else {
                                if (totalDragY > 0) onSwipe(Direction.DOWN) else onSwipe(Direction.UP)
                            }
                            totalDragX = 0f
                            totalDragY = 0f
                        }
                    }
                )
            }
    ) {
        val cellWidth = size.width / gridWidth
        val cellHeight = size.height / gridHeight

        // A. Draw Subtle Retro Grid Lines (Simulating CRT screen grids)
        for (i in 1 until gridWidth) {
            drawLine(
                color = ArcadeColors.SoftGrid,
                start = Offset(i * cellWidth, 0f),
                end = Offset(i * cellWidth, size.height),
                strokeWidth = 1f
            )
        }
        for (j in 1 until gridHeight) {
            drawLine(
                color = ArcadeColors.SoftGrid,
                start = Offset(0f, j * cellHeight),
                end = Offset(size.width, j * cellHeight),
                strokeWidth = 1f
            )
        }

        // B. Draw Static Obstacles (Stone Bricks)
        obstacles.forEach { obstacle ->
            val ox = obstacle.x * cellWidth
            val oy = obstacle.y * cellHeight
            // Draw brick shadow
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(ox + 2f, oy + 2f),
                size = Size(cellWidth - 2f, cellHeight - 2f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            // Draw stone block
            drawRoundRect(
                color = ArcadeColors.CharcoalGrey,
                topLeft = Offset(ox + 1f, oy + 1f),
                size = Size(cellWidth - 2f, cellHeight - 2f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            // Draw an internal metallic stone border
            drawRoundRect(
                color = Color(0x66FFFFFF),
                topLeft = Offset(ox + 3f, oy + 3f),
                size = Size(cellWidth - 6f, cellHeight - 6f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // C. Draw Normal Food Item
        val fx = food.x * cellWidth + cellWidth / 2f
        val fy = food.y * cellHeight + cellHeight / 2f
        val foodRadius = (cellWidth / 2.3f) * normalFoodPulse
        // Glow layer
        drawCircle(
            color = ArcadeColors.HotMagenta.copy(alpha = 0.25f),
            radius = foodRadius + 5.dp.toPx(),
            center = Offset(fx, fy)
        )
        // Core layer
        drawCircle(
            color = ArcadeColors.HotMagenta,
            radius = foodRadius,
            center = Offset(fx, fy)
        )
        // Highlight layer
        drawCircle(
            color = Color.White,
            radius = foodRadius * 0.35f,
            center = Offset(fx - foodRadius * 0.3f, fy - foodRadius * 0.3f)
        )

        // D. Draw Golden Food Item (If available)
        goldenFood?.let { gf ->
            val gfx = gf.x * cellWidth + cellWidth / 2f
            val gfy = gf.y * cellHeight + cellHeight / 2f
            val gfRadius = (cellWidth / 2.0f) * goldenFoodPulse
            // Outer glowing aura
            drawCircle(
                color = ArcadeColors.RetroGold.copy(alpha = 0.3f),
                radius = gfRadius + 8.dp.toPx(),
                center = Offset(gfx, gfy)
            )
            // Sparking Star Cross lines
            drawLine(
                color = ArcadeColors.RetroGold,
                start = Offset(gfx - gfRadius * 1.3f, gfy),
                end = Offset(gfx + gfRadius * 1.3f, gfy),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = ArcadeColors.RetroGold,
                start = Offset(gfx, gfy - gfRadius * 1.3f),
                end = Offset(gfx, gfy + gfRadius * 1.3f),
                strokeWidth = 2.dp.toPx()
            )
            // Golden Food Core
            drawCircle(
                color = ArcadeColors.RetroGold,
                radius = gfRadius,
                center = Offset(gfx, gfy)
            )
            // White spark point
            drawCircle(
                color = Color.White,
                radius = gfRadius * 0.45f,
                center = Offset(gfx, gfy)
            )
        }

        // E. Draw Interpolated Snake Body & Animated Head
        if (snake.isNotEmpty() && gameStatus != GameStatus.NOT_STARTED) {
            val totalSegments = snake.size

            // Let's pre-calculate all interpolated segment pixel offsets to draw standard connector paths
            val pxPositions = List(totalSegments) { index ->
                val curr = snake[index]
                val prev = if (index < previousSnake.size) previousSnake[index] else (previousSnake.lastOrNull() ?: curr)
                val interpX = prev.x + (curr.x - prev.x) * interpolationFraction
                val interpY = prev.y + (curr.y - prev.y) * interpolationFraction
                Offset(interpX * cellWidth + cellWidth / 2f, interpY * cellHeight + cellHeight / 2f)
            }

            // Draw Snake connectors (for beautiful ribbon-like snake body rather than disjoint circles)
            for (i in 0 until totalSegments - 1) {
                drawLine(
                    color = Color.Black,
                    start = pxPositions[i],
                    end = pxPositions[i+1],
                    strokeWidth = cellWidth * 0.75f,
                    cap = StrokeCap.Round
                )
                val bodyColor = lerpColor(ArcadeColors.NeonGreen, ArcadeColors.NeonGreenDark, i.toFloat() / totalSegments)
                drawLine(
                    color = bodyColor,
                    start = pxPositions[i],
                    end = pxPositions[i+1],
                    strokeWidth = cellWidth * 0.62f,
                    cap = StrokeCap.Round
                )
            }

            // Draw individual segments as nice overlay caps to have perfect circle joints
            for (i in 0 until totalSegments) {
                val centerOffset = pxPositions[i]
                val segmentRadius = if (i == 0) cellWidth / 1.8f else cellWidth / 2.3f

                val color = if (i == 0) {
                    ArcadeColors.NeonGreen
                } else {
                    lerpColor(ArcadeColors.NeonGreen, ArcadeColors.NeonGreenDark, i.toFloat() / totalSegments)
                }

                if (i > 0) {
                    // Subtle dark border to distinguish overlapping body segments
                    drawCircle(
                        color = Color(0x33000000),
                        radius = segmentRadius + 1f,
                        center = centerOffset
                    )
                    drawCircle(
                        color = color,
                        radius = segmentRadius,
                        center = centerOffset
                    )
                } else {
                    // It's the head! Draw it larger
                    drawCircle(
                        color = Color.Black,
                        radius = segmentRadius + 1.dp.toPx(),
                        center = centerOffset
                    )
                    drawCircle(
                        color = color,
                        radius = segmentRadius,
                        center = centerOffset
                    )

                    // Draw eyes facing the direction of movement
                    val eyeSize = cellWidth * 0.16f
                    val pupilSize = eyeSize * 0.5f
                    val eyeSpacing = cellWidth * 0.28f

                    val (eyeOffset1, eyeOffset2) = when (direction) {
                        Direction.UP -> Pair(
                            Offset(centerOffset.x - eyeSpacing, centerOffset.y - eyeSpacing),
                            Offset(centerOffset.x + eyeSpacing, centerOffset.y - eyeSpacing)
                        )
                        Direction.DOWN -> Pair(
                            Offset(centerOffset.x - eyeSpacing, centerOffset.y + eyeSpacing),
                            Offset(centerOffset.x + eyeSpacing, centerOffset.y + eyeSpacing)
                        )
                        Direction.LEFT -> Pair(
                            Offset(centerOffset.x - eyeSpacing, centerOffset.y - eyeSpacing),
                            Offset(centerOffset.x - eyeSpacing, centerOffset.y + eyeSpacing)
                        )
                        Direction.RIGHT -> Pair(
                            Offset(centerOffset.x + eyeSpacing, centerOffset.y - eyeSpacing),
                            Offset(centerOffset.x + eyeSpacing, centerOffset.y + eyeSpacing)
                        )
                    }

                    // Sclera
                    drawCircle(color = Color.White, radius = eyeSize, center = eyeOffset1)
                    drawCircle(color = Color.White, radius = eyeSize, center = eyeOffset2)

                    // Pupil
                    drawCircle(color = Color.Black, radius = pupilSize, center = eyeOffset1)
                    drawCircle(color = Color.Black, radius = pupilSize, center = eyeOffset2)
                }
            }
        }
    }
}

// Linear color interpolation helper
fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction
    )
}

@Composable
fun ControllerPanel(
    gameStatus: GameStatus,
    currentDifficulty: Difficulty,
    highScore: Int,
    onDirectionClick: (Direction) -> Unit,
    onDifficultyClick: (Difficulty) -> Unit,
    onCenterClick: () -> Unit
) {
    // Elegant Dark Theme footer wrapper
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A. Speed / Difficulty Selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Difficulty.values().forEach { d ->
                val isSelected = currentDifficulty == d
                val isRunning = gameStatus == GameStatus.RUNNING || gameStatus == GameStatus.PAUSED

                val selectedBg = if (isSelected) Color(0xFF2D2F33) else Color.Transparent
                val borderStrokeColor = if (isSelected) Color(0xFF44474E) else Color.Transparent
                val textColor = if (isSelected) Color(0xFFD0BCFF) else Color(0xFF919094)

                TextButton(
                    onClick = { onDifficultyClick(d) },
                    enabled = !isRunning,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = textColor,
                        disabledContentColor = if (isSelected) textColor.copy(alpha = 0.5f) else Color.DarkGray
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(selectedBg)
                        .border(1.dp, borderStrokeColor, RoundedCornerShape(12.dp))
                        .testTag("difficulty_${d.name.lowercase()}_button")
                ) {
                    Text(
                        text = d.label.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // B. Dynamic Centered D-Pad
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Up Arrow
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1B1F))
                    .border(1.dp, Color(0xFF44474E), RoundedCornerShape(16.dp))
                    .clickable { onDirectionClick(Direction.UP) }
                    .testTag("dpad_up"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "UP",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Middle Row: LEFT, CENTER (Action), RIGHT
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Arrow
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1B1F))
                        .border(1.dp, Color(0xFF44474E), RoundedCornerShape(16.dp))
                        .clickable { onDirectionClick(Direction.LEFT) }
                        .testTag("dpad_left"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "LEFT",
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center Action Button (Start/Pause/Resume, with styled retro center action look)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (gameStatus == GameStatus.RUNNING) Color(0xFF44474E) else Color(0xFFD0BCFF))
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable { onCenterClick() }
                        .testTag("control_center_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (gameStatus) {
                            GameStatus.RUNNING -> Icons.Default.Pause
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = "Action Button",
                        tint = if (gameStatus == GameStatus.RUNNING) Color.White else Color(0xFF381E72),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Right Arrow
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1B1F))
                        .border(1.dp, Color(0xFF44474E), RoundedCornerShape(16.dp))
                        .clickable { onDirectionClick(Direction.RIGHT) }
                        .testTag("dpad_right"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "RIGHT",
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Down Arrow
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1B1F))
                    .border(1.dp, Color(0xFF44474E), RoundedCornerShape(16.dp))
                    .clickable { onDirectionClick(Direction.DOWN) }
                    .testTag("dpad_down"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "DOWN",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // C. Elegant High Score Pill / Status Footer matching the HTML:
        // bg-[#2D2F33] rounded-full text-[#E2E2E6]
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF2D2F33))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "High Score Trophy",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "BEST: ${highScore} PTS",
                    color = Color(0xFFE2E2E6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
fun ArcadeOverlay(
    title: String,
    subtitle: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2111318)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            // Blinking neon visual decoration
            val infiniteTransition = rememberInfiniteTransition(label = "blink")
            val blinkAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "blinkAlpha"
            )

            Text(
                text = title,
                color = Color(0xFFE2E2E6),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                color = Color(0xFF919094),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD0BCFF),
                    contentColor = Color(0xFF381E72)
                ),
                modifier = Modifier
                    .shadow((blinkAlpha * 8).dp, CircleShape, spotColor = Color(0xFFD0BCFF))
                    .height(48.dp)
                    .testTag("arcade_overlay_action_button"),
                shape = RoundedCornerShape(100.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = "Start Icon",
                        tint = Color(0xFF381E72)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buttonText,
                        color = Color(0xFF381E72),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LeaderboardDialog(
    difficulty: Difficulty,
    scores: List<HighScore>,
    onDismiss: () -> Unit,
    onClearScores: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.dp, Color(0xFF44474E), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Trophy
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Trophy",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Leaderboard",
                        color = Color(0xFFE2E2E6),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                }

                Text(
                    text = "DIFFICULTY: ${difficulty.label.uppercase()}",
                    color = Color(0xFFD0BCFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // High score table list
                if (scores.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO HIGH SCORES YET\nBE THE FIRST!",
                            color = Color(0xFF919094),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp)
                    ) {
                        itemsIndexed(scores) { index, record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (index % 2 == 0) Color(0xFF2D2F33) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val medalColor = when (index) {
                                        0 -> Color(0xFFFFD700)
                                        1 -> Color(0xFFC0C0C0) // Silver
                                        2 -> Color(0xFFCD7F32) // Bronze
                                        else -> Color(0xFF919094)
                                    }
                                    Text(
                                        text = "#${index + 1}",
                                        color = medalColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.width(36.dp)
                                    )
                                    Text(
                                        text = record.playerName.uppercase(),
                                        color = Color(0xFFE2E2E6),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }

                                Text(
                                    text = String.format("%,d", record.score),
                                    color = Color(0xFFD0BCFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onClearScores,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF8B94)),
                        modifier = Modifier.testTag("clear_scores_button")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESET DB", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.testTag("dismiss_leaderboard_button")
                    ) {
                        Text("CLOSE", color = Color(0xFF381E72), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NameEntryDialog(
    score: Int,
    difficulty: Difficulty,
    inputName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF44474E), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = "New Record",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "NEW RECORD!",
                    color = Color(0xFFE2E2E6),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                Text(
                    text = "DIFFICULTY: ${difficulty.label.uppercase()}",
                    color = Color(0xFF919094),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                Text(
                    text = String.format("%,d", score),
                    color = Color(0xFFD0BCFF),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = inputName,
                    onValueChange = {
                        // Max 8 characters for retro name arcade style!
                        if (it.length <= 8) {
                            onNameChange(it)
                        }
                    },
                    label = { Text("PLAYER INITIALS", color = Color(0xFF919094)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF44474E),
                        focusedLabelColor = Color(0xFFD0BCFF),
                        cursorColor = Color(0xFFD0BCFF),
                        focusedTextColor = Color(0xFFE2E2E6),
                        unfocusedTextColor = Color(0xFFE2E2E6)
                    ),
                    maxLines = 1,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("name_input_field")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("SKIP", color = Color(0xFF919094), fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = onSave,
                        enabled = inputName.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            disabledContainerColor = Color(0xFF2D2F33)
                        ),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.testTag("save_score_button")
                    ) {
                        Text(
                            text = "SAVE SCORE",
                            color = if (inputName.trim().isNotEmpty()) Color(0xFF381E72) else Color(0xFF919094),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
