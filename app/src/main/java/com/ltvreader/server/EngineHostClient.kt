package com.ltvreader.server

import com.ltvreader.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * HTTP-клиент к удалённому engine-host (см. /server-host/engine_host.py).
 *
 * Эндпоинты:
 *   GET  /info
 *   GET  /engines
 *   GET  /engines/{id}/voices
 *   POST /engines/{id}/preload
 *   POST /engines/{id}/unload
 *   POST /synthesize
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
        getJsonArray("/engines").mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }

    suspend fun listVoices(engineId: String): List<VoiceInfo> = withContext(Dispatchers.IO) {
        val arr = getJsonArray("/engines/$engineId/voices")
        arr.mapNotNull { el ->
            val v = el as? JsonObject ?: return@mapNotNull null
            val idPrim = v["id"] as? kotlinx.serialization.json.JsonPrimitive
            val id = idPrim?.contentOrEmpty() ?: return@mapNotNull null
            VoiceInfo(
                id = id,
                displayName = stringField(v, "display_name") ?: id,
                language = stringField(v, "language") ?: "en",
                gender = stringField(v, "gender").orEmpty(),
                engineId = "remote:$engineId",
                previewUrl = stringField(v, "preview_url"),
                isLocal = false,
                sampleRate = intField(v, "sample_rate") ?: 22050,
                downloadModelId = stringField(v, "download_model_id"),
                downloadSizeBytes = longField(v, "download_size_bytes") ?: -1,
                isCloned = booleanField(v, "is_cloned") ?: false,
            )
        }
    }

    suspend fun downloadVoiceModel(modelId: String): Unit = withContext(Dispatchers.IO) {
        val body = """{"files":[]}""".toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$baseUrl/models/$modelId/download")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Voice model download failed: HTTP ${response.code}")
            }
        }
    }

    suspend fun createVoiceClone(
        name: String,
        transcript: String,
        language: String,
        modelId: String,
        fileName: String,
        audio: ByteArray,
    ): Unit = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", name)
            .addFormDataPart("transcript", transcript)
            .addFormDataPart("language", language)
            .addFormDataPart("model_id", modelId)
            .addFormDataPart(
                "audio",
                fileName,
                audio.toRequestBody("application/octet-stream".toMediaType()),
            )
            .build()
        val request = Request.Builder().url("$baseUrl/voice-clones").post(body).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Voice cloning failed: HTTP ${response.code} ${response.body?.string().orEmpty().take(200)}")
            }
        }
    }

    suspend fun deleteVoiceClone(cloneId: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/voice-clones/$cloneId").delete().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Delete voice clone failed: HTTP ${response.code}")
        }
    }

    suspend fun generateMusic(
        modelId: String,
        prompt: String,
        seconds: Int,
        outputFile: File,
    ): Unit = withContext(Dispatchers.IO) {
        val body = mapOf(
            "model_id" to modelId,
            "prompt" to prompt,
            "seconds" to seconds,
        )
        val request = Request.Builder()
            .url("$baseUrl/music/generate")
            .post(encodeJson(body).toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Music generation failed: HTTP ${response.code} ${response.body?.string().orEmpty().take(200)}")
            }
            val responseBody = response.body ?: error("Music service returned no audio")
            outputFile.parentFile?.mkdirs()
            responseBody.byteStream().use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
            }
        }
    }

    suspend fun preloadEngine(engineId: String, options: Map<String, String>) = withContext(Dispatchers.IO) {
        val body = mapOf("options" to options)
        postJsonText("/engines/$engineId/preload", body)
    }

    suspend fun unloadEngine(engineId: String) = withContext(Dispatchers.IO) {
        postJsonText("/engines/$engineId/unload", emptyMap())
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

    // --- helpers -----------------------------------------------------------

    private suspend fun getJsonArray(path: String): List<JsonElement> = withContext(Dispatchers.IO) {
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

    private fun postJsonText(path: String, body: Map<String, Any?>): String {
        val text = encodeJson(body)
        val rq = Request.Builder()
            .url("$baseUrl$path")
            .post(text.toRequestBody(JSON_MEDIA))
            .build()
        return runCatching {
            http.newCall(rq).execute().use { resp -> resp.body?.string().orEmpty() }
        }.getOrDefault("")
    }

    private fun stringField(obj: JsonObject, key: String): String? {
        val prim = obj[key] as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return if (prim.isString) prim.content else null
    }

    private fun intField(obj: JsonObject, key: String): Int? {
        val prim = obj[key] as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return prim.content.toIntOrNull()
    }

    private fun longField(obj: JsonObject, key: String): Long? {
        val prim = obj[key] as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return prim.content.toLongOrNull()
    }

    private fun booleanField(obj: JsonObject, key: String): Boolean? {
        val prim = obj[key] as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return prim.content.toBooleanStrictOrNull()
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrEmpty(): String? =
        if (isString) content else null

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
