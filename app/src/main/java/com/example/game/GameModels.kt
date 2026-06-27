package com.example.game

enum class Direction {
    UP, DOWN, LEFT, RIGHT;

    fun isOpposite(other: Direction): Boolean {
        return when (this) {
            UP -> other == DOWN
            DOWN -> other == UP
            LEFT -> other == RIGHT
            RIGHT -> other == LEFT
        }
    }
}

enum class GameStatus {
    NOT_STARTED,
    RUNNING,
    PAUSED,
    GAME_OVER
}

enum class Difficulty(val label: String, val baseSpeedMs: Long) {
    EASY("Easy", 220L),
    MEDIUM("Medium", 160L),
    HARD("Hard", 110L),
    INSANE("Insane", 75L)
}

data class Point(val x: Int, val y: Int)
