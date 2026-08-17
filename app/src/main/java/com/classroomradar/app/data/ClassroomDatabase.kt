package com.classroomradar.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val title: String = "课堂记录",
    val audioRelativePath: String? = null,
)

@Entity(
    tableName = "transcript_lines",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index(value = ["sessionId", "timestampMillis"])],
)
data class TranscriptLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val text: String,
)

@Entity(
    tableName = "question_events",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index(value = ["sessionId", "timestampMillis"])],
)
data class QuestionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val matchedTrigger: String?,
    val question: String,
    val sourceLine: String,
)

data class SessionSummary(
    val id: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val title: String,
    val audioRelativePath: String?,
    val questionCount: Int,
)

@Dao
interface ClassroomDao {
    @Insert
    fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTranscript(line: TranscriptLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertQuestion(event: QuestionEventEntity): Long

    @Query("UPDATE sessions SET endedAtMillis = :endedAtMillis WHERE id = :sessionId")
    fun finishSession(sessionId: Long, endedAtMillis: Long)

    @Query("UPDATE sessions SET audioRelativePath = :relativePath WHERE id = :sessionId")
    fun updateAudioPath(sessionId: Long, relativePath: String?)

    @Query("UPDATE sessions SET title = :title WHERE id = :sessionId")
    fun renameSession(sessionId: Long, title: String)

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSession(sessionId: Long): SessionEntity?

    @Query("SELECT * FROM transcript_lines WHERE sessionId = :sessionId ORDER BY timestampMillis ASC, id ASC")
    fun observeTranscript(sessionId: Long): Flow<List<TranscriptLineEntity>>

    @Query("SELECT * FROM transcript_lines WHERE sessionId = :sessionId ORDER BY timestampMillis ASC, id ASC")
    fun getTranscript(sessionId: Long): List<TranscriptLineEntity>

    @Query("SELECT * FROM question_events WHERE sessionId = :sessionId ORDER BY timestampMillis DESC, id DESC")
    fun observeQuestions(sessionId: Long): Flow<List<QuestionEventEntity>>

    @Query("SELECT * FROM question_events WHERE sessionId = :sessionId ORDER BY timestampMillis ASC, id ASC")
    fun getQuestions(sessionId: Long): List<QuestionEventEntity>

    @Query(
        """
        SELECT sessions.id, sessions.startedAtMillis, sessions.endedAtMillis, sessions.title,
               sessions.audioRelativePath, COUNT(question_events.id) AS questionCount
        FROM sessions
        LEFT JOIN question_events ON question_events.sessionId = sessions.id
        GROUP BY sessions.id
        ORDER BY sessions.startedAtMillis DESC
        """,
    )
    fun observeSessionSummaries(): Flow<List<SessionSummary>>

    @Query("SELECT audioRelativePath FROM sessions WHERE audioRelativePath IS NOT NULL")
    fun getAllAudioPaths(): List<String>

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    fun deleteSession(sessionId: Long)

    @Query("DELETE FROM sessions")
    fun deleteAllSessions()

    @Transaction
    fun removeSession(sessionId: Long): String? {
        val audioPath = getSession(sessionId)?.audioRelativePath
        deleteSession(sessionId)
        return audioPath
    }

    @Transaction
    fun removeAllSessions(): List<String> {
        val audioPaths = getAllAudioPaths()
        deleteAllSessions()
        return audioPaths
    }
}

@Database(
    entities = [SessionEntity::class, TranscriptLineEntity::class, QuestionEventEntity::class],
    // Earlier preview builds changed the schema while keeping version 1, which makes
    // Room reject their on-device database during app startup. Version 2 lets the
    // fallback below recreate only those legacy databases instead of crashing.
    version = 2,
    exportSchema = true,
)
abstract class ClassroomDatabase : RoomDatabase() {
    abstract fun classroomDao(): ClassroomDao

    companion object {
        private const val DATABASE_NAME = "water_class_radar.db"
        @Volatile private var instance: ClassroomDatabase? = null

        fun get(context: Context): ClassroomDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ClassroomDatabase::class.java,
                DATABASE_NAME,
            )
                // This protects upgrades from a database schema-version mismatch.
                // No migration applies to the current version, so existing same-version
                // records remain untouched.
                .fallbackToDestructiveMigration(true)
                .build()
                .also { instance = it }
        }
    }
}
