package com.ltvreader.server

import com.ltvreader.tts.EngineInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Репозиторий моделей TTS. Работает через engine-host (Python-сервер).
 *
 * Сервер проксирует HuggingFace: делает `huggingface_hub.hf_hub_download()`
 * и сохраняет файлы на диск. Android скачивает готовые файлы через GET.
 */
class ModelRepository(
    private val baseUrl: String,
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(3600, TimeUnit.SECONDS) // долгие скачивания
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class ModelInfo(
        val id: String,
        val name: String,
        val engine: String,
        val size_mb: Int,
        val languages: List<String> = emptyList(),
        val description: String = "",
        val tags: List<String> = emptyList(),
        val files: List<String> = emptyList(),
    )

    @Serializable
    data class LocalModel(
        val id: String,
        val path: String,
        val total_size_mb: Double,
        val files_count: Int,
    )

    @Serializable
    data class ModelsResponse(
        val models: List<ModelInfo>,
        val installed: List<String> = emptyList(),
    )

    @Serializable
    data class LocalModelsResponse(
        val models: List<LocalModel>,
        val dir: String,
    )

    fun isReachable(): Boolean = runCatching {
        val rq = Request.Builder().url("$baseUrl/info").get().build()
        http.newCall(rq).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    suspend fun listModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val rq = Request.Builder().url("$baseUrl/models").get().build()
        runCatching {
            http.newCall(rq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val text = resp.body?.string().orEmpty()
                parseModels(text)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun listLocalModels(): List<LocalModel> = withContext(Dispatchers.IO) {
        val rq = Request.Builder().url("$baseUrl/local-models").get().build()
        runCatching {
            http.newCall(rq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val text = resp.body?.string().orEmpty()
                parseLocalModels(text)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Скачать файл модели на устройство. Возвращает путь к скачанному файлу.
     *
     * @param modelId — id репо (например, "onnx-community/Kokoro-82M")
     * @param fileName — имя файла в репо (например, "kokoro-v0_19.onnx")
     * @param outputDir — куда сохранить (по умолчанию — context.filesDir/models)
     */
    suspend fun downloadFile(
        modelId: String,
        fileName: String,
        outputFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val url = "$baseUrl/models/${modelId}/file/$fileName"
        val rq = Request.Builder().url(url).get().build()
        http.newCall(rq).execute().use { resp ->
            if (!resp.isSuccessful) error("download failed: HTTP ${resp.code}")
            val body = resp.body ?: error("empty body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
        }
        outputFile
    }

    /**
     * Запросить сервер скачать файл к себе (на диск сервера).
     * Полезно, если хочется использовать модель локально на engine-host,
     * а на Android получать только результат /synthesize.
     */
    suspend fun requestServerDownload(modelId: String, files: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        val body = "{\"files\": ${if (files.isEmpty()) "[]" else files.joinToString(",") { "\"$it\"" }}}"
        val rq = Request.Builder()
            .url("$baseUrl/models/$modelId/download")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(rq).execute().use { resp ->
            resp.body?.string() ?: ""
        }
    }

    /**
     * Удалить локально скачанную модель (на сервере).
     */
    suspend fun deleteLocal(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val rq = Request.Builder()
            .url("$baseUrl/local-models/$modelId")
            .delete()
            .build()
        runCatching {
            http.newCall(rq).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun parseModels(text: String): List<ModelInfo> {
        // Простой парсер: регулярка для { "id": "...", "name": "...", ... }
        val result = mutableListOf<ModelInfo>()
        val objRegex = Regex("""\{[^{}]*"id"[^{}]*\}""")
        for (obj in objRegex.findAll(text)) {
            val m = obj.value
            val id = extract(m, "id") ?: continue
            val name = extract(m, "name") ?: id
            val engine = extract(m, "engine") ?: "unknown"
            val size = extract(m, "size_mb")?.toIntOrNull() ?: 0
            val desc = extract(m, "description") ?: ""
            result += ModelInfo(
                id = id,
                name = name,
                engine = engine,
                size_mb = size,
                languages = extractArray(m, "languages"),
                description = desc,
                tags = extractArray(m, "tags"),
                files = extractArray(m, "files"),
            )
        }
        return result
    }

    private fun parseLocalModels(text: String): List<LocalModel> {
        val result = mutableListOf<LocalModel>()
        val objRegex = Regex("""\{[^{}]*"path"[^{}]*\}""")
        for (obj in objRegex.findAll(text)) {
            val m = obj.value
            val id = extract(m, "id") ?: continue
            val path = extract(m, "path") ?: ""
            val size = extract(m, "total_size_mb")?.toDoubleOrNull() ?: 0.0
            val count = extract(m, "files_count")?.toIntOrNull() ?: 0
            result += LocalModel(id, path, size, count)
        }
        return result
    }

    private fun extract(obj: String, key: String): String? {
        val r = Regex(""""$key"\s*:\s*"([^"\\]*)"""")
        return r.find(obj)?.groupValues?.get(1)
    }

    private fun extractArray(obj: String, key: String): List<String> {
        val r = Regex(""""$key"\s*:\s*\[(.*?)\]""")
        val m = r.find(obj) ?: return emptyList()
        return m.groupValues[1].split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
