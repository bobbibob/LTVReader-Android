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
 * Поддерживает эндпоинты:
 *   GET  /info
 *   GET  /engines
 *   POST /engines/{id}/preload
 *   POST /engines/{id}/unload
 *   GET  /engines/{id}/voices
 *   POST /synthesize  (упрощённый, как в server-host)
 */
class EngineHostClient(
    private val baseUrl: String,
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun isReachable(): Boolean = runCatching {
        val req = Request.Builder().url("$baseUrl/info").get().build()
        http.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    suspend fun listEngines(): List<String> = withContext(Dispatchers.IO) {
        getJson<List<String>>("/engines") ?: emptyList()
    }

    suspend fun listVoices(engineId: String): List<VoiceInfo> = withContext(Dispatchers.IO) {
        val arr = getJsonArray("/engines/$engineId/voices")
        arr.mapNotNull { el ->
            val v = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val id = (v["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrEmpty() ?: return@mapNotNull null
            VoiceInfo(
                id = id,
                displayName = (v["display_name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrEmpty(),
                language = (v["language"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrEmpty() ?: "en",
                gender = (v["gender"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrEmpty(),
                engineId = engineId,
                previewUrl = (v["preview_url"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrEmpty(),
                isLocal = false,
                sampleRate = (v["sample_rate"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrZero() ?: 22050,
            )
        }
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
        val text_json = encodeJson(body)
        val req = Request.Builder()
            .url("$baseUrl/synthesize")
            .post(text_json.toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                error("engine-host returned ${resp.code}: ${err.take(200)}")
            }
            val bytes = resp.body?.bytes() ?: error("empty audio body")
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(bytes)
            SynthResponse(24000, 1, -1, bytes.size.toLong())
        }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrEmpty(): String? =
        if (isString) content else null

    private fun kotlinx.serialization.json.JsonPrimitive.intOrZero(): Int =
        content.toIntOrNull() ?: 0

    private suspend inline fun <reified T> getJson(path: String): T? = withContext(Dispatchers.IO) {
        val rq = Request.Builder().url("$baseUrl$path").get().build()
        runCatching {
            http.newCall(rq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) null
                else json.decodeFromString(kotlinx.serialization.serializer(), text) as T
            }
        }.getOrNull()
    }

    private suspend fun getJsonArray(path: String): List<kotlinx.serialization.json.JsonElement> = withContext(Dispatchers.IO) {
        val rq = Request.Builder().url("$baseUrl$path").get().build()
        runCatching {
            http.newCall(rq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) emptyList()
                else json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonArray ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun postJson(path: String, body: Map<String, Any?>): String {
        val text = encodeJson(body)
        val rq = Request.Builder()
            .url("$baseUrl$path")
            .post(text.toRequestBody(JSON_MEDIA))
            .build()
        return runCatching {
            http.newCall(rq).execute().use { resp -> resp.body?.string().orEmpty() }
        }.getOrDefault("")
    }

    private fun encodeJson(obj: Any?): String = when (obj) {
        null -> "null"
        is Number, is Boolean -> obj.toString()
        is String -> "\"${obj.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        is Map<*, *> -> obj.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"${k}\":${encodeJson(v)}"
        }
        is List<*> -> obj.joinToString(prefix = "[", postfix = "]") { encodeJson(it) }
        else -> "\"${obj.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
