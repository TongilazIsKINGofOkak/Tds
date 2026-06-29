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
    val score: Int,
    val playerName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface HighScoreDao {
    @Query("SELECT * FROM high_scores ORDER BY score DESC LIMIT 10")
    fun getTopScores(): Flow<List<HighScore>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: HighScore)

    @Query("SELECT MAX(score) FROM high_scores")
    suspend fun getMaxScore(): Int?
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
                    "game_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class HighScoreRepository(private val highScoreDao: HighScoreDao) {
    val topScores: Flow<List<HighScore>> = highScoreDao.getTopScores()

    suspend fun insertScore(score: HighScore) {
        highScoreDao.insertScore(score)
    }

    suspend fun getMaxScore(): Int {
        return highScoreDao.getMaxScore() ?: 0
    }
}
