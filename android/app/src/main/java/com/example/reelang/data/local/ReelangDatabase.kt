package com.example.reelang.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.reelang.data.local.dao.*
import com.example.reelang.data.local.entities.*

@Database(
    entities = [
        WordEntity::class,
        ReelEntity::class,
        CaptionEntity::class,
        UserProfileEntity::class,
        PracticeSessionEntity::class,
        EventOutboxEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ReelangDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun reelDao(): ReelDao
    abstract fun captionDao(): CaptionDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun eventOutboxDao(): EventOutboxDao

    companion object {
        @Volatile
        private var INSTANCE: ReelangDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS event_outbox (
                        eventId TEXT NOT NULL PRIMARY KEY,
                        eventType TEXT NOT NULL,
                        reelId TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        clientTimestamp TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): ReelangDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ReelangDatabase::class.java,
                    "reelang_database"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
