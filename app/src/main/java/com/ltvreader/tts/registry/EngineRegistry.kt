package com.ltvreader.tts.registry

import android.content.Context
import com.ltvreader.tts.EngineInfo
import com.ltvreader.tts.TtsEngineException
import com.ltvreader.tts.VoiceConfig
import com.ltvreader.tts.VoiceInfo
import com.ltvreader.tts.engines.AzureTtsEngine
import com.ltvreader.tts.engines.CustomHttpTtsEngine
import com.ltvreader.tts.engines.ElevenLabsTtsEngine
import com.ltvreader.tts.engines.GeminiTtsEngine
import com.ltvreader.tts.engines.KokoroTtsEngine
import com.ltvreader.tts.engines.OpenAiTtsEngine
import com.ltvreader.tts.engines.RemoteHostTtsEngine
import com.ltvreader.tts.engines.TtsEngine
import com.ltvreader.server.EngineHostClient
import java.io.File

/**
 * Реестр TTS-движков. Прямой порт `app/tts/engine_registry.py`.
 *
 * Хранит лениво создаваемые экземпляры движков + их конфигурацию.
 * Используется пайплайном и UI.
 */
class EngineRegistry(
    private val appContext: Context,
    private val settings: EngineSettings,
    private val hostClient: EngineHostClient? = null,
) {

    /** Настройки конкретного движка (API-ключи и т.п.). */
    data class EngineSettings(
        val engines: Map<String, Map<String, String>> = emptyMap(),
    )

    private val instances = mutableMapOf<String, TtsEngine>()

    /** Список всех известных движков (включая удалённые, даже если host недоступен). */
    fun allEngineInfos(): List<EngineInfo> = buildList {
        add(KokoroTtsEngine.ENGINE_INFO)
        add(OpenAiTtsEngine.ENGINE_INFO)
        add(ElevenLabsTtsEngine.ENGINE_INFO)
        add(GeminiTtsEngine.ENGINE_INFO)
        add(AzureTtsEngine.ENGINE_INFO)
        add(CustomHttpTtsEngine.ENGINE_INFO)
        if (hostClient != null) {
            add(EngineInfo("remote:piper", "Piper (via remote host)", EngineInfo.EngineKind.Remote))
            add(EngineInfo("remote:chatterbox", "Chatterbox (via remote host)", EngineInfo.EngineKind.Remote))
            add(EngineInfo("remote:qwen", "Qwen3 TTS (via remote host)", EngineInfo.EngineKind.Remote))
            add(EngineInfo("remote:omnivoice", "OmniVoice (via remote host)", EngineInfo.EngineKind.Remote))
        }
    }

    /** Получить или создать экземпляр движка по id. */
    @Synchronized
    fun get(engineId: String): TtsEngine {
        instances[engineId]?.let { return it }
        val created = createEngine(engineId)
            ?: throw TtsEngineException.Generic("Unknown engine: $engineId")
        instances[engineId] = created
        return created
    }

    /** Освободить все движки. */
    suspend fun closeAll() {
        for (e in instances.values) e.close()
        instances.clear()
    }

    private fun createEngine(id: String): TtsEngine? {
        val cfg = settings.engines[id] ?: emptyMap()
        return when (id) {
            "kokoro" -> {
                val root = File(appContext.filesDir, "voices/kokoro")
                KokoroTtsEngine(
                    modelFile = File(root, "kokoro.onnx"),
                    voicesFile = File(root, "voices.bin"),
                )
            }
            "openai" -> OpenAiTtsEngine(
                apiKey = cfg["apiKey"] ?: return null,
                baseUrl = cfg["baseUrl"] ?: "https://api.openai.com/v1",
                defaultModel = cfg["model"] ?: "gpt-4o-mini-tts",
            )
            "elevenlabs" -> ElevenLabsTtsEngine(
                apiKey = cfg["apiKey"] ?: return null,
                baseUrl = cfg["baseUrl"] ?: "https://api.elevenlabs.io/v1",
                defaultVoiceId = cfg["voiceId"] ?: "21m00Tcm4TlvDq8ikWAM",
                modelId = cfg["modelId"] ?: "eleven_multilingual_v2",
            )
            "gemini" -> GeminiTtsEngine(
                apiKey = cfg["apiKey"] ?: return null,
                model = cfg["model"] ?: "gemini-2.5-flash-preview-tts",
                voiceName = cfg["voiceName"] ?: "Kore",
            )
            "azure" -> AzureTtsEngine(
                subscriptionKey = cfg["subscriptionKey"] ?: return null,
                region = cfg["region"] ?: return null,
                defaultVoice = cfg["voice"] ?: "en-US-JennyNeural",
            )
            "custom_http" -> CustomHttpTtsEngine(
                CustomHttpTtsEngine.Config(
                    url = cfg["url"] ?: return null,
                    headers = parseHeaders(cfg["headers"] ?: ""),
                    bodyTemplate = cfg["bodyTemplate"]
                        ?: """{"text": "{{text}}", "voice": "{{voice}}", "lang": "{{lang}}"}""",
                    responseAudioField = cfg["responseAudioField"]?.ifEmpty { "audio" },
                    responseUrlField = cfg["responseUrlField"]?.ifEmpty { null },
                ),
            )
            "remote:piper" -> hostClient?.let { RemoteHostTtsEngine(it, "piper", "Piper (via remote host)") }
            "remote:chatterbox" -> hostClient?.let { RemoteHostTtsEngine(it, "chatterbox", "Chatterbox (via remote host)") }
            "remote:qwen" -> hostClient?.let { RemoteHostTtsEngine(it, "qwen", "Qwen3 TTS (via remote host)") }
            "remote:omnivoice" -> hostClient?.let { RemoteHostTtsEngine(it, "omnivoice", "OmniVoice (via remote host)") }
            else -> null
        }
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (line in raw.lines()) {
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (key.isNotEmpty()) out[key] = value
            }
        }
        return out
    }
}
