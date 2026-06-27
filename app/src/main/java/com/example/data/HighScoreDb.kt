package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "high_scores")
data class HighScore(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerName: String,
    val score: Int,
    val difficulty: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface HighScoreDao {
    @Query("SELECT * FROM high_scores ORDER BY score DESC, timestamp DESC LIMIT 10")
    fun getTopScores(): Flow<List<HighScore>>

    @Query("SELECT * FROM high_scores WHERE difficulty = :difficulty ORDER BY score DESC, timestamp DESC LIMIT 10")
    fun getTopScoresForDifficulty(difficulty: String): Flow<List<HighScore>>

    @Query("SELECT MAX(score) FROM high_scores WHERE difficulty = :difficulty")
    suspend fun getHighScoreForDifficulty(difficulty: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(highScore: HighScore)

    @Query("DELETE FROM high_scores")
    suspend fun clearAllScores()
}

@Database(entities = [HighScore::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun highScoreDao(): HighScoreDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "snake_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class HighScoreRepository(private val highScoreDao: HighScoreDao) {
    val topScores: Flow<List<HighScore>> = highScoreDao.getTopScores()

    fun getTopScoresForDifficulty(difficulty: String): Flow<List<HighScore>> {
        return highScoreDao.getTopScoresForDifficulty(difficulty)
    }

    suspend fun getHighScoreForDifficulty(difficulty: String): Int {
        return highScoreDao.getHighScoreForDifficulty(difficulty) ?: 0
    }

    suspend fun insertScore(highScore: HighScore) {
        highScoreDao.insertScore(highScore)
    }

    suspend fun clearAllScores() {
        highScoreDao.clearAllScores()
    }
}
