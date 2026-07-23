package com.ltvreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * База данных Room. Один файл `ltvreader.db`.
 *
 * Схема 1:1 повторяет SQLite из оригинала (audiobook_store.py):
 *   - projects
 *   - audiobooks
 *   - segments
 *   - voices (локальные, загруженные)
 *   - custom_dictionary
 *   - normalization_rules
 *   - voice_gallery_cache
 */
@Database(
    entities = [
        ProjectEntity::class,
        AudiobookEntity::class,
        SegmentEntity::class,
        VoiceEntity::class,
        DictionaryEntry::class,
        NormalizationRuleEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun audiobooks(): AudiobookDao
    abstract fun segments(): SegmentDao
    abstract fun voices(): VoiceDao
    abstract fun dictionary(): DictionaryDao
    abstract fun normalization(): NormalizationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ltvreader.db",
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}

class StringListConverter {
    @TypeConverter fun fromList(value: List<String>?): String =
        value?.joinToString("\u001F") ?: ""
    @TypeConverter fun toList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split("\u001F")
}
