package com.ltvreader.tts.engines

import com.ltvreader.tts.EngineInfo
import com.ltvreader.tts.EngineInfo.EngineKind
import com.ltvreader.tts.TtsEngineException
import com.ltvreader.tts.TtsRequest
import com.ltvreader.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * ElevenLabs Text-to-Speech.
 *
 * Прямой порт `app/tts/api_engines.py:ElevenLabsTTSEngine`.
 */
class ElevenLabsTtsEngine(
    private val apiKey: String,
    private val baseUrl: String = "https://api.elevenlabs.io/v1",
    private val defaultVoiceId: String = "21m00Tcm4TlvDq8ikWAM", // "Rachel"
    private val modelId: String = "eleven_multilingual_v2",
) : AbstractHttpEngine(ENGINE_INFO) {

    override fun endpoint(): String = "$baseUrl/text-to-speech/$defaultVoiceId?output_format=mp3_44100_128"

    override fun headers(): Map<String, String> = mapOf(
        "xi-api-key" to apiKey,
        "Accept" to "audio/mpeg",
        "Content-Type" to "application/json",
    )

    override fun buildBody(request: TtsRequest): JsonObject = buildJsonObject {
        put("text", request.text)
        put("model_id", modelId)
        put(
            "voice_settings",
            buildJsonObject {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
                put("style", 0.0)
                put("use_speaker_boost", true)
            },
        )
        request.voice.lang.takeIf { it.isNotBlank() }?.let { put("language_code", it) }
    }

    override suspend fun listVoices(): List<VoiceInfo> = withContext(Dispatchers.IO) {
        val rq = Request.Builder()
            .url("$baseUrl/voices")
            .header("xi-api-key", apiKey)
            .get()
            .build()
        runCatching {
            httpClient.newCall(rq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val text = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(text).let { it as? JsonObject } ?: return@runCatching emptyList()
                val arr = obj["voices"] as? JsonArray ?: return@runCatching emptyList()
                arr.mapNotNull { el ->
                    val v = el as? JsonObject ?: return@mapNotNull null
                    VoiceInfo(
                        id = v["voice_id"]?.toString()?.trim('"') ?: return@mapNotNull null,
                        displayName = v["name"]?.toString()?.trim('"').orEmpty(),
                        language = "multi",
                        engineId = ENGINE_INFO.id,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        val ENGINE_INFO = EngineInfo(
            id = "elevenlabs",
            displayName = "ElevenLabs",
            kind = EngineKind.Cloud,
            requiresApiKey = true,
            supportsCloning = true,
            configSchema = listOf(
                EngineInfo.ConfigField("apiKey", "API Key", EngineInfo.ConfigField.FieldType.Password, secret = true),
                EngineInfo.ConfigField("voiceId", "Voice ID", EngineInfo.ConfigField.FieldType.String, default = "21m00Tcm4TlvDq8ikWAM"),
                EngineInfo.ConfigField("modelId", "Model", EngineInfo.ConfigField.FieldType.Select, default = "eleven_multilingual_v2", options = listOf("eleven_multilingual_v2", "eleven_turbo_v2_5", "eleven_flash_v2_5")),
            ),
        )
    }
}
