package com.ltvreader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Проект — импортированный документ, который пользователь редактирует. */
@Entity(
    tableName = "projects",
    indices = [Index(value = ["updatedAt"])],
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourcePath: String? = null,
    val rawText: String = "",
    val normalizedText: String? = null,
    val markupText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val ttsEngine: String = "kokoro",
    val voiceConfigJson: String = "{}",
    val splitMode: String = "safe_chunks",
    val exportMode: String = "single",
    val outputPath: String? = null,
)

/** Аудиокнига — результат генерации одного проекта. */
@Entity(
    tableName = "audiobooks",
    indices = [Index("projectId"), Index("status")],
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AudiobookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val status: String = "pending",  // pending|running|completed|failed|cancelled
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val outputPath: String? = null,
    val durationMs: Int = 0,
    val segmentsTotal: Int = 0,
    val segmentsDone: Int = 0,
    val errorMessage: String? = null,
)

/** Сегмент — один чанк текста + сгенерированный аудиофайл. */
@Entity(
    tableName = "segments",
    indices = [Index("audiobookId"), Index("orderIndex")],
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audiobookId: Long,
    val orderIndex: Int,
    val text: String,
    val audioPath: String? = null,
    val durationMs: Int = 0,
    val status: String = "pending",  // pending|running|completed|failed
    val engineConfigJson: String = "{}",
    val pauseBeforeMs: Int = 0,
    val pauseAfterMs: Int = 0,
    val errorMessage: String? = null,
    val similarity: Double? = null,
    val tailCutMs: Int = 0,
)

/** Локально загруженный голос (из voice gallery или движка). */
@Entity(
    tableName = "voices",
    indices = [Index(value = ["engineId", "voiceId"], unique = true)],
)
data class VoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engineId: String,
    val voiceId: String,
    val displayName: String,
    val language: String = "en",
    val gender: String = "",
    val sampleRate: Int = 22050,
    val localPath: String? = null,
    val installedAt: Long = System.currentTimeMillis(),
    val tags: String = "",  // CSV
)

/** Запись пользовательского словаря замен. */
@Entity(
    tableName = "dictionary",
    indices = [Index("language")],
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val language: String = "all",
    val pattern: String,
    val replacement: String,
    val enabled: Boolean = true,
)

/** Правило нормализации (числа/даты/валюты/...). */
@Entity(tableName = "normalization_rules")
data class NormalizationRuleEntity(
    @PrimaryKey val ruleKey: String,
    val enabled: Boolean = true,
    val configJson: String = "{}",
)
