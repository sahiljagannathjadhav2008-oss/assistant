package com.example.sjassistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        NoteEntity::class,
        TodoEntity::class,
        ReminderEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SjDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun noteDao(): NoteDao
    abstract fun todoDao(): TodoDao
    abstract fun reminderDao(): ReminderDao
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: SjDatabase? = null

        fun getInstance(context: Context): SjDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SjDatabase::class.java,
                    "sj_assistant_offline.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
