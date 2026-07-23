package com.ltvreader.tts.engines

import com.ltvreader.tts.EngineInfo
import com.ltvreader.tts.EngineInfo.EngineKind
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
 * Покрывает движки, которые **не могут** работать локально на Android-устройстве:
 * Piper, Chatterbox, Qwen3 TTS, OmniVoice.
 *
 * Клиент обращается к engine-host по HTTP (LAN).
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

    override suspend fun listVoices(): List<VoiceInfo> = withContext(Dispatchers.IO) {
        hostClient.listVoices(engineId)
    }

    override suspend fun preload(): Unit = withContext(Dispatchers.IO) {
        hostClient.preloadEngine(engineId, emptyMap())
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        hostClient.unloadEngine(engineId)
    }

    override suspend fun cancel() {
        // Реализовано через job_id на стороне engine-host.
    }

    override suspend fun synthesize(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val extras = mutableMapOf<String, String>()
        extras.putAll(request.voice.extras)
        request.voice.referenceAudioPath?.let { extras["reference_audio_path"] = it }
        val response = hostClient.synthesize(
            engineId = engineId,
            text = request.text,
            voice = request.voice.voice,
            lang = request.voice.lang,
            speed = request.voice.speed,
            outputFile = request.outputFile,
            extras = extras,
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
