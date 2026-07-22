package com.ltvreader.core.text

/**
 * Ссылка на аудио-событие из LTV-разметки. Полное определение —
 * в [com.ltvreader.core.markup.MarkupAudioEvent].
 *
 * Здесь только лёгкий маркер, чтобы TextChunk мог хранить события
 * без жёсткой связи с пакетом markup.
 */
data class AudioEventRef(
    val kind: String,           // "sfx" | "music" | "silence"
    val payload: String,        // путь к файлу / идентификатор
    val offsetMs: Int = 0,      // смещение относительно чанка
    val durationMs: Int? = null,
)
