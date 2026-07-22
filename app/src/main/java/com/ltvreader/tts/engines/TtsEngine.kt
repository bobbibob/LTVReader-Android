package com.ltvreader.tts.engines

import com.ltvreader.tts.EngineInfo
import com.ltvreader.tts.TtsRequest
import com.ltvreader.tts.TtsResult
import com.ltvreader.tts.VoiceInfo
import kotlinx.coroutines.flow.Flow

/**
 * Контракт TTS-движка. 1:1 порт `BaseTTSEngine` из оригинала.
 *
 * Все движки (локальные, облачные, удалённые) реализуют этот интерфейс.
 * UI и пайплайн работают только через него.
 */
interface TtsEngine {
    /** Метаданные движка. */
    val info: EngineInfo

    /** Установлен ли движок и доступен ли для использования. */
    fun isAvailable(): Boolean

    /** Список доступных голосов (может быть сетевым). */
    suspend fun listVoices(): List<VoiceInfo>

    /** Прогрев движка (загрузка модели, инициализация). */
    suspend fun preload()

    /** Освобождение ресурсов. */
    suspend fun close()

    /** Синтез одного фрагмента. */
    suspend fun synthesize(request: TtsRequest): TtsResult

    /** Прервать текущую генерацию (если поддерживается). */
    suspend fun cancel()
}
