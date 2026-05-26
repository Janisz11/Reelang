package com.example.reelang.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.reelang.data.local.dao.*
import com.example.reelang.data.local.entities.*

@Database(
    entities = [
        WordEntity::class,
        ReelEntity::class,
        CaptionEntity::class,
        UserProfileEntity::class,
        PracticeSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ReelangDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun reelDao(): ReelDao
    abstract fun captionDao(): CaptionDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun practiceSessionDao(): PracticeSessionDao

    companion object {
        @Volatile
        private var INSTANCE: ReelangDatabase? = null

        fun getInstance(context: Context): ReelangDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ReelangDatabase::class.java,
                    "reelang_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
