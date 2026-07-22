package com.ltvreader.tts.engines

import com.ltvreader.tts.EngineInfo
import com.ltvreader.tts.EngineInfo.EngineKind
import com.ltvreader.tts.TtsEngineException
import com.ltvreader.tts.TtsRequest
import com.ltvreader.tts.TtsResult
import com.ltvreader.tts.VoiceInfo
import com.ltvreader.server.EngineHostClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Универсальный клиент к удалённому `engine_host.py` (см. /server-host).
 *
 * Этот класс покрывает движки, которые **не могут** работать локально
 * на Android-устройстве:
 *   - Piper (нужен нативный бинарник под aarch64-android)
 *   - Chatterbox (PyTorch 2 ГБ + модель)
 *   - Qwen3 TTS (огромные модели + CUDA)
 *   - OmniVoice (то же)
 *
 * Клиент обращается к локальному engine-host по HTTP (Wi-Fi).
 *
 * Прямой порт `app/server/engine_host_client.py`.
 */
class RemoteHostTtsEngine(
    private val hostClient: EngineHostClient,
    private val engineId: String,
    displayName: String,
) : TtsEngine {

    override val info: EngineInfo = EngineInfo(
        id = "remote:$engineId",
        displayName = displayName,
        kind = EngineKind.Remote,
    )

    override fun isAvailable(): Boolean = hostClient.isReachable()

    suspend fun listVoicesRemote(): List<VoiceInfo> = withContext(Dispatchers.IO) {
        hostClient.listVoices(engineId)
    }

    suspend fun preloadRemote() = withContext(Dispatchers.IO) {
        hostClient.preloadEngine(engineId, emptyMap())
    }

    suspend fun closeRemote() = withContext(Dispatchers.IO) {
        hostClient.unloadEngine(engineId)
    }

    override suspend fun cancel() {
        // Реализовано через job_id на стороне engine-host.
    }

    override suspend fun synthesize(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val response = hostClient.synthesize(
            engineId = engineId,
            text = request.text,
            voice = request.voice.voice,
            lang = request.voice.lang,
            speed = request.voice.speed,
            outputFile = request.outputFile,
            extras = request.voice.extras + request.voice.referenceAudioPath?.let { "reference_audio_path" to it }.let { if (it != null) mapOf(it) else emptyMap() },
        )
        TtsResult(
            outputFile = request.outputFile,
            sampleRate = response.sampleRate,
            channels = response.channels,
            durationMs = response.durationMs,
            bytesWritten = response.bytesWritten,
        )
    }
}
