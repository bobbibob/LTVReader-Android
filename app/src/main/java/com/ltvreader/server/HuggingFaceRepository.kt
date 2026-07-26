package com.ltvreader.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Direct client for public and token-protected Hugging Face repositories.
 *
 * Model files are downloaded into app-private storage. A small manifest preserves
 * the original repository id because filesystem-safe directory names are hashes.
 */
class HuggingFaceRepository(
    private val modelsRoot: File,
    private val token: String = "",
) {
    data class ModelFile(
        val path: String,
        val sizeBytes: Long = -1,
    ) {
        val isTtsArtifact: Boolean
            get() = path.substringAfterLast('.').lowercase() in SUPPORTED_EXTENSIONS
    }

    data class Model(
        val id: String,
        val name: String,
        val downloads: Long,
        val tags: List<String>,
        val files: List<ModelFile>,
    ) {
        val totalSizeBytes: Long
            get() = files.map { it.sizeBytes }.filter { it > 0 }.sum()

        val compatibleFiles: List<ModelFile>
            get() = files.filter { it.isTtsArtifact }
    }

    data class InstalledModel(
        val id: String,
        val directory: File,
        val filesCount: Int,
        val totalSizeBytes: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3600, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 30): List<Model> = withContext(Dispatchers.IO) {
        val urlBuilder = "https://huggingface.co/api/models".toHttpUrl().newBuilder()
            .addQueryParameter("filter", "text-to-speech")
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .addQueryParameter("full", "true")
        query.trim().takeIf { it.isNotEmpty() }?.let {
            urlBuilder.addQueryParameter("search", it)
        }
        val url = urlBuilder.build()
        val request = authorized(Request.Builder().url(url)).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Hugging Face search failed: HTTP ${response.code}")
            }
            parseModels(response.body?.string().orEmpty())
        }
    }

    suspend fun model(repoId: String): Model = withContext(Dispatchers.IO) {
        val url = "https://huggingface.co/api/models".toHttpUrl().newBuilder()
            .addPathSegments(repoId.trim('/'))
            .addQueryParameter("files_metadata", "true")
            .build()
        val request = authorized(Request.Builder().url(url)).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Hugging Face repository failed: HTTP ${response.code}")
            }
            parseModel(json.parseToJsonElement(response.body?.string().orEmpty()) as JsonObject)
                ?: error("Invalid Hugging Face model response")
        }
    }

    suspend fun install(
        model: Model,
        files: List<ModelFile> = model.compatibleFiles,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): InstalledModel = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()) { "No supported TTS model files found in ${model.id}" }
        val directory = directoryFor(model.id)
        directory.mkdirs()
        val knownTotal = files.map { it.sizeBytes }.filter { it > 0 }.sum()
        var completedBytes = 0L

        try {
            for (file in files) {
                val output = safeTarget(directory, file.path)
                val partial = File(output.parentFile, "${output.name}.part")
                output.parentFile?.mkdirs()
                val url = "https://huggingface.co".toHttpUrl().newBuilder()
                    .addPathSegments(model.id)
                    .addPathSegments("resolve/main")
                    .addPathSegments(file.path)
                    .build()
                val request = authorized(Request.Builder().url(url)).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Download ${file.path} failed: HTTP ${response.code}")
                    }
                    val body = response.body ?: error("Empty response for ${file.path}")
                    val currentSize = body.contentLength().takeIf { it > 0 } ?: file.sizeBytes
                    body.byteStream().use { input ->
                        partial.outputStream().use { outputStream ->
                            val buffer = ByteArray(128 * 1024)
                            var fileBytes = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                outputStream.write(buffer, 0, read)
                                fileBytes += read
                                val total = if (knownTotal > 0) knownTotal else currentSize
                                onProgress(completedBytes + fileBytes, total)
                            }
                        }
                    }
                    if (output.exists() && !output.delete()) {
                        error("Cannot replace ${output.name}")
                    }
                    if (!partial.renameTo(output)) {
                        error("Cannot finish ${output.name}")
                    }
                    completedBytes += output.length()
                }
            }
            writeManifest(directory, model.id)
            installedModel(directory) ?: error("Cannot read installed model")
        } catch (error: Throwable) {
            directory.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".part") }
                .forEach { it.delete() }
            throw error
        }
    }

    fun installed(): List<InstalledModel> {
        if (!modelsRoot.isDirectory) return emptyList()
        return modelsRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull(::installedModel)
            .sortedBy { it.id.lowercase() }
    }

    fun delete(modelId: String): Boolean {
        val directory = directoryFor(modelId)
        return directory.exists() && directory.deleteRecursively()
    }

    private fun parseModels(text: String): List<Model> {
        val array = json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonObject)?.let(::parseModel) }
    }

    private fun parseModel(obj: JsonObject): Model? {
        val id = obj.string("id") ?: obj.string("modelId") ?: return null
        val tags = (obj["tags"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            .orEmpty()
        val files = (obj["siblings"] as? JsonArray)
            ?.mapNotNull { sibling ->
                val item = sibling as? JsonObject ?: return@mapNotNull null
                val path = item.string("rfilename") ?: return@mapNotNull null
                val size = item.long("size")
                    ?: (item["lfs"] as? JsonObject)?.long("size")
                    ?: -1L
                ModelFile(path, size)
            }
            .orEmpty()
        return Model(
            id = id,
            name = id.substringAfter('/'),
            downloads = obj.long("downloads") ?: 0,
            tags = tags,
            files = files,
        )
    }

    private fun installedModel(directory: File): InstalledModel? {
        val manifest = File(directory, MANIFEST)
        if (!manifest.isFile) return null
        val obj = runCatching {
            json.parseToJsonElement(manifest.readText()) as JsonObject
        }.getOrNull() ?: return null
        val id = obj.string("id") ?: return null
        val files = directory.walkTopDown().filter { it.isFile && it.name != MANIFEST }.toList()
        return InstalledModel(id, directory, files.size, files.sumOf { it.length() })
    }

    private fun writeManifest(directory: File, id: String) {
        File(directory, MANIFEST).writeText(
            buildJsonObject {
                put("id", id)
                put("installedAt", System.currentTimeMillis())
            }.toString(),
        )
    }

    private fun directoryFor(modelId: String): File =
        File(modelsRoot, sha256(modelId).take(24))

    private fun safeTarget(root: File, relativePath: String): File {
        val target = File(root, relativePath).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator)) {
            "Unsafe model file path: $relativePath"
        }
        return target
    }

    private fun authorized(builder: Request.Builder): Request.Builder =
        if (token.isBlank()) builder else builder.header("Authorization", "Bearer $token")

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MANIFEST = ".ltv-model.json"
        private val SUPPORTED_EXTENSIONS = setOf(
            "onnx", "bin", "json", "txt", "model", "safetensors", "pt", "pth",
            "yaml", "yml", "tokens", "vocab", "config",
        )
    }
}
