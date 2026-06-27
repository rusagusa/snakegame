package com.example.game

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HighScore
import com.example.data.HighScoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class SnakeViewModel(
    application: Application,
    private val repository: HighScoreRepository
) : AndroidViewModel(application) {

    // Grid Dimensions
    val gridWidth = 20
    val gridHeight = 24

    // Game States
    private val _snake = MutableStateFlow<List<Point>>(emptyList())
    val snake: StateFlow<List<Point>> = _snake.asStateFlow()

    private val _previousSnake = MutableStateFlow<List<Point>>(emptyList())
    val previousSnake: StateFlow<List<Point>> = _previousSnake.asStateFlow()

    private val _food = MutableStateFlow<Point>(Point(10, 12))
    val food: StateFlow<Point> = _food.asStateFlow()

    private val _goldenFood = MutableStateFlow<Point?>(null)
    val goldenFood: StateFlow<Point?> = _goldenFood.asStateFlow()

    private val _goldenFoodTimer = MutableStateFlow(0)
    val goldenFoodTimer: StateFlow<Int> = _goldenFoodTimer.asStateFlow()

    private val _direction = MutableStateFlow(Direction.RIGHT)
    val direction: StateFlow<Direction> = _direction.asStateFlow()

    private val _gameStatus = MutableStateFlow(GameStatus.NOT_STARTED)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _currentScore = MutableStateFlow(0)
    val currentScore: StateFlow<Int> = _currentScore.asStateFlow()

    private val _highScore = MutableStateFlow(0)
    val highScore: StateFlow<Int> = _highScore.asStateFlow()

    private val _difficulty = MutableStateFlow(Difficulty.MEDIUM)
    val difficulty: StateFlow<Difficulty> = _difficulty.asStateFlow()

    private val _obstacles = MutableStateFlow<Set<Point>>(emptySet())
    val obstacles: StateFlow<Set<Point>> = _obstacles.asStateFlow()

    // Sound & Haptic Preferences
    private val _isSoundEnabled = MutableStateFlow(true)
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    private val _isHapticEnabled = MutableStateFlow(true)
    val isHapticEnabled: StateFlow<Boolean> = _isHapticEnabled.asStateFlow()

    // Time Tracking for Canvas smooth interpolation
    var lastTickTimeMs: Long = 0
        private set
    var tickDurationMs: Long = 160L
        private set

    // Control lock to prevent 180 turn self-collision
    private var lastProcessedDirection = Direction.RIGHT

    // Top Scores observed from repository for currently selected difficulty
    val topScores: StateFlow<List<HighScore>> = _difficulty.combine(repository.topScores) { diff, allScores ->
        allScores.filter { it.difficulty == diff.label }.take(10)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var gameJob: Job? = null
    private var goldenFoodTimerJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        } catch (e: Exception) {
            // Safe fallback if hardware is busy
        }
        // Load high score initially
        updateHighScoreForDifficulty()
    }

    private fun updateHighScoreForDifficulty() {
        viewModelScope.launch {
            _highScore.value = repository.getHighScoreForDifficulty(_difficulty.value.label)
        }
    }

    fun setDifficulty(newDiff: Difficulty) {
        if (_gameStatus.value == GameStatus.NOT_STARTED || _gameStatus.value == GameStatus.GAME_OVER) {
            _difficulty.value = newDiff
            updateHighScoreForDifficulty()
            generateObstacles()
        }
    }

    fun toggleSound() {
        _isSoundEnabled.value = !_isSoundEnabled.value
    }

    fun toggleHaptic() {
        _isHapticEnabled.value = !_isHapticEnabled.value
    }

    private fun generateObstacles() {
        val newObstacles = mutableSetOf<Point>()
        when (_difficulty.value) {
            Difficulty.EASY, Difficulty.MEDIUM -> {
                // No obstacles
            }
            Difficulty.HARD -> {
                // Symmetric individual stone pillars
                newObstacles.add(Point(5, 5))
                newObstacles.add(Point(14, 5))
                newObstacles.add(Point(5, 18))
                newObstacles.add(Point(14, 18))
                newObstacles.add(Point(10, 11))
                newObstacles.add(Point(10, 12))
            }
            Difficulty.INSANE -> {
                // Maze walls
                for (x in 4..7) newObstacles.add(Point(x, 6))
                for (x in 12..15) newObstacles.add(Point(x, 6))
                for (x in 4..7) newObstacles.add(Point(x, 17))
                for (x in 12..15) newObstacles.add(Point(x, 17))
                newObstacles.add(Point(10, 3))
                newObstacles.add(Point(10, 20))
            }
        }
        _obstacles.value = newObstacles
    }

    fun startGame() {
        gameJob?.cancel()
        goldenFoodTimerJob?.cancel()

        // Reset state
        val initialSnake = listOf(
            Point(6, 12),
            Point(5, 12),
            Point(4, 12)
        )
        _snake.value = initialSnake
        _previousSnake.value = initialSnake
        _direction.value = Direction.RIGHT
        lastProcessedDirection = Direction.RIGHT
        _currentScore.value = 0
        _goldenFood.value = null
        _goldenFoodTimer.value = 0
        generateObstacles()
        spawnFood()

        _gameStatus.value = GameStatus.RUNNING
        lastTickTimeMs = System.currentTimeMillis()
        tickDurationMs = getSpeedMs()

        gameJob = viewModelScope.launch {
            while (_gameStatus.value == GameStatus.RUNNING) {
                val speed = getSpeedMs()
                tickDurationMs = speed
                delay(speed)
                tick()
            }
        }
    }

    fun pauseGame() {
        if (_gameStatus.value == GameStatus.RUNNING) {
            _gameStatus.value = GameStatus.PAUSED
            gameJob?.cancel()
            goldenFoodTimerJob?.cancel()
        }
    }

    fun resumeGame() {
        if (_gameStatus.value == GameStatus.PAUSED) {
            _gameStatus.value = GameStatus.RUNNING
            lastTickTimeMs = System.currentTimeMillis()
            tickDurationMs = getSpeedMs()

            gameJob = viewModelScope.launch {
                while (_gameStatus.value == GameStatus.RUNNING) {
                    val speed = getSpeedMs()
                    tickDurationMs = speed
                    delay(speed)
                    tick()
                }
            }

            if (_goldenFood.value != null && _goldenFoodTimer.value > 0) {
                startGoldenFoodTimer()
            }
        }
    }

    fun changeDirection(newDir: Direction) {
        if (_gameStatus.value != GameStatus.RUNNING) return
        // Prevent opposite direction 180 turns
        if (!newDir.isOpposite(lastProcessedDirection)) {
            _direction.value = newDir
            triggerHaptic(5) // Light tap on turn
        }
    }

    private fun getSpeedMs(): Long {
        val base = _difficulty.value.baseSpeedMs
        // Increase speed dynamically as score increases
        val scoreBonus = (_currentScore.value / 3) * 4L
        return maxOf(45L, base - scoreBonus)
    }

    private fun tick() {
        val currentList = _snake.value
        if (currentList.isEmpty()) return

        val head = currentList.first()
        val dir = _direction.value
        lastProcessedDirection = dir

        // Calculate next head position
        val nextHead = when (dir) {
            Direction.UP -> Point(head.x, head.y - 1)
            Direction.DOWN -> Point(head.x, head.y + 1)
            Direction.LEFT -> Point(head.x - 1, head.y)
            Direction.RIGHT -> Point(head.x + 1, head.y)
        }

        // 1. Collision Check: Wall
        if (nextHead.x < 0 || nextHead.x >= gridWidth || nextHead.y < 0 || nextHead.y >= gridHeight) {
            endGame()
            return
        }

        // 2. Collision Check: Obstacles
        if (_obstacles.value.contains(nextHead)) {
            endGame()
            return
        }

        // 3. Collision Check: Self (ignore tail tip because it moves away, unless we grow)
        val isSelfCollision = currentList.dropLast(1).contains(nextHead)
        if (isSelfCollision) {
            endGame()
            return
        }

        // Record previous state for interpolation
        _previousSnake.value = currentList

        // Check if eating food
        val ateNormalFood = nextHead == _food.value
        val ateGoldenFood = nextHead == _goldenFood.value

        val newSnake = mutableListOf<Point>()
        newSnake.add(nextHead)

        if (ateNormalFood) {
            newSnake.addAll(currentList)
            _currentScore.value += 10
            playTone(ToneGenerator.TONE_PROP_ACK, 70)
            triggerHaptic(40) // Crisp buzz for eating

            // Update high score instantly on screen if beaten
            if (_currentScore.value > _highScore.value) {
                _highScore.value = _currentScore.value
            }

            // Golden food spawn chance (15%) if golden food is not active
            if (_goldenFood.value == null && Random.nextFloat() < 0.18f) {
                spawnGoldenFood()
            }
            spawnFood()
        } else if (ateGoldenFood) {
            newSnake.addAll(currentList)
            _currentScore.value += 30
            _goldenFood.value = null
            _goldenFoodTimer.value = 0
            goldenFoodTimerJob?.cancel()

            playTone(ToneGenerator.TONE_PROP_BEEP, 120)
            triggerHaptic(70) // Double buzz for golden food

            if (_currentScore.value > _highScore.value) {
                _highScore.value = _currentScore.value
            }
        } else {
            // standard move: add head, add body, drop tail
            newSnake.addAll(currentList.dropLast(1))
        }

        _snake.value = newSnake
        lastTickTimeMs = System.currentTimeMillis()
    }

    private fun spawnFood() {
        val occupied = (_snake.value + _obstacles.value).toSet()
        var attempts = 0
        var newFood: Point
        do {
            newFood = Point(Random.nextInt(gridWidth), Random.nextInt(gridHeight))
            attempts++
        } while (occupied.contains(newFood) && attempts < 100)
        _food.value = newFood
    }

    private fun spawnGoldenFood() {
        val occupied = (_snake.value + _obstacles.value + _food.value).toSet()
        var attempts = 0
        var newGoldenFood: Point
        do {
            newGoldenFood = Point(Random.nextInt(gridWidth), Random.nextInt(gridHeight))
            attempts++
        } while (occupied.contains(newGoldenFood) && attempts < 100)

        _goldenFood.value = newGoldenFood
        _goldenFoodTimer.value = 6 // 6 seconds to eat
        startGoldenFoodTimer()
    }

    private fun startGoldenFoodTimer() {
        goldenFoodTimerJob?.cancel()
        goldenFoodTimerJob = viewModelScope.launch {
            while (_goldenFoodTimer.value > 0 && _goldenFood.value != null) {
                delay(1000)
                _goldenFoodTimer.value -= 1
            }
            // Expire golden food
            _goldenFood.value = null
        }
    }

    private fun endGame() {
        _gameStatus.value = GameStatus.GAME_OVER
        gameJob?.cancel()
        goldenFoodTimerJob?.cancel()

        playTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        triggerHaptic(180) // Long heavy vibration for game over
    }

    fun submitHighScore(playerName: String) {
        val name = playerName.trim().ifEmpty { "SNAKE" }
        viewModelScope.launch {
            repository.insertScore(
                HighScore(
                    playerName = name,
                    score = _currentScore.value,
                    difficulty = _difficulty.value.label
                )
            )
            updateHighScoreForDifficulty()
        }
    }

    fun clearScores() {
        viewModelScope.launch {
            repository.clearAllScores()
            _highScore.value = 0
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        if (_isSoundEnabled.value) {
            viewModelScope.launch {
                try {
                    toneGenerator?.startTone(toneType, durationMs)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun triggerHaptic(durationMs: Long) {
        if (_isHapticEnabled.value) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            } catch (e: Exception) {
                // Safe ignore if permission or hardware is unavailable
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameJob?.cancel()
        goldenFoodTimerJob?.cancel()
        toneGenerator?.release()
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = AppDatabase.getDatabase(context)
                    val repository = HighScoreRepository(database.highScoreDao())
                    return SnakeViewModel(context.applicationContext as Application, repository) as T
                }
            }
        }
    }
}
