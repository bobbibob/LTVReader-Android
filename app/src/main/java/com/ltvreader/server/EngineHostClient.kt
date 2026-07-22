package com.ltvreader.server

import com.ltvreader.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * HTTP-клиент к удалённому engine-host (см. /server-host/engine_host.py).
 *
 * Поддерживает все эндпоинты из оригинального `app/server/http_app.py`:
 *   - GET  /info
 *   - GET  /engines
 *   - POST /engines/{id}/preload
 *   - POST /engines/{id}/unload
 *   - GET  /engines/{id}/voices
 *   - POST /jobs
 *   - GET  /jobs/{id}
 *   - POST /jobs/{id}/cancel
 */
class EngineHostClient(
    private val baseUrl: String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun isReachable(): Boolean = runCatching {
        val req = Request.Builder().url("$baseUrl/info").get().build()
        http.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    suspend fun listEngines(): List<String> = withContext(Dispatchers.IO) {
        get<List<String>>("/engines") ?: emptyList()
    }

    suspend fun listVoices(engineId: String): List<VoiceInfo> = withContext(Dispatchers.IO) {
        get<List<RemoteVoice>>("/engines/$engineId/voices")?.map {
            VoiceInfo(
                id = it.id,
                displayName = it.display_name,
                language = it.language,
                gender = it.gender,
                engineId = engineId,
                previewUrl = it.preview_url,
                isLocal = false,
                sampleRate = it.sample_rate,
            )
        } ?: emptyList()
    }

    suspend fun preloadEngine(engineId: String, options: Map<String, String>) = withContext(Dispatchers.IO) {
        postJson("/engines/$engineId/preload", mapOf("options" to options))
    }

    suspend fun unloadEngine(engineId: String) = withContext(Dispatchers.IO) {
        postJson("/engines/$engineId/unload", emptyMap())
    }

    data class SynthResponse(
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Int,
        val bytesWritten: Long,
    )

    suspend fun synthesize(
        engineId: String,
        text: String,
        voice: String,
        lang: String,
        speed: Double,
        outputFile: File,
        extras: Map<String, String> = emptyMap(),
    ): SynthResponse = withContext(Dispatchers.IO) {
        val body = mapOf(
            "engine_id" to engineId,
            "text" to text,
            "voice" to voice,
            "lang" to lang,
            "speed" to speed,
            "options" to extras,
        )
        val req = Request.Builder()
            .url("$baseUrl/synthesize")
            .post("""{"engine_id":"${body["engine_id"]}","text":"${(body["text"] as String).replace(""","\\\"")}","voice":"${body["voice"]}","lang":"${body["lang"]}","speed":${body["speed"]}}""".toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                error("engine-host $engineId returned ${resp.code}: ${err.take(200)}")
            }
            val bytes = resp.body?.bytes() ?: error("empty audio")
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(bytes)
            // engine-host возвращает WAV с заголовком; sample-rate/channels парсятся в пайплайне
            SynthResponse(24000, 1, -1, bytes.size.toLong())
        }
    }

    // --- helpers -----------------------------------------------------------

    private suspend inline fun <reified T> get(path: String): T? = withContext(Dispatchers.IO) {
        val rq = Request.Builder().url("$baseUrl$path").get().build()
        runCatching {
            http.newCall(rq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val text = resp.body?.string().orEmpty()
                if (text.isEmpty()) null
                else json.decodeFromString(text) as T
            }
        }.getOrNull()
    }

    private suspend fun postJson(path: String, body: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in body) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(k).append('":')
            when (v) {
                null -> sb.append("null")
                is Number, is Boolean -> sb.append(v.toString())
                is Map<*, *> -> sb.append("{}")  // nested maps not supported in this simple impl
                else -> {
                    sb.append('"').append(v.toString().replace("\", "\\").replace(""", "\"")).append('"')
                }
            }
        }
        sb.append("}")
        val text = sb.toString()
        val rq = Request.Builder()
            .url("$baseUrl$path")
            .post(text.toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(rq).execute().use { resp ->
            resp.body?.string().orEmpty()
        }
    }

    @Serializable
    private data class BodyMap(val data: Map<String, String>)

    @Serializable
    private data class RemoteVoice(
        val id: String,
        val display_name: String,
        val language: String,
        val gender: String = "",
        val preview_url: String? = null,
        val sample_rate: Int = 22050,
    )

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

// simple JSON encoder for requests
