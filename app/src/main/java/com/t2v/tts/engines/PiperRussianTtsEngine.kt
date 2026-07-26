package com.t2v.tts.engines

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.t2v.core.audio.AudioChunk
import com.t2v.core.audio.AudioEncoder
import com.t2v.tts.EngineInfo
import com.t2v.tts.TtsEngineException
import com.t2v.tts.TtsRequest
import com.t2v.tts.TtsResult
import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Downloadable Piper/VITS models converted and published by sherpa-onnx. */
class PiperRussianTtsEngine(
    private val modelsDir: File,
) : TtsEngine {
    override val info: EngineInfo = ENGINE_INFO
    private var activeVoiceId: String? = null
    private var tts: OfflineTts? = null

    override fun isAvailable(): Boolean = installedVoices().isNotEmpty()

    override suspend fun listVoices(): List<VoiceInfo> = installedVoices().map { voice ->
        VoiceInfo(
            id = voice.id,
            displayName = voice.displayName,
            language = voice.language,
            gender = voice.gender,
            engineId = info.id,
            isLocal = true,
            sampleRate = 22050,
        )
    }

    override suspend fun preload(): Unit = Unit

    override suspend fun synthesize(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val installed = installedVoices()
        val voice = installed.firstOrNull { it.id == request.voice.voice }
            ?: installed.firstOrNull()
            ?: throw TtsEngineException.NotInstalled(info.id)
        val runtime = ensureTts(voice)
        val audio = runtime.generate(
            text = request.text,
            sid = 0,
            speed = request.voice.speed.toFloat().coerceIn(0.5f, 2.0f),
        )
        if (audio.samples.isEmpty()) {
            throw TtsEngineException.Generic("${voice.displayName} returned empty audio")
        }
        val volume = request.voice.volume.coerceIn(0.0, 4.0).toFloat()
        val pcm = ShortArray(audio.samples.size) { index ->
            (audio.samples[index] * volume * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        request.outputFile.parentFile?.mkdirs()
        AudioEncoder.writeWav(
            request.outputFile,
            AudioChunk(samples = pcm, sampleRate = audio.sampleRate, channels = 1),
        )
        TtsResult(
            outputFile = request.outputFile,
            sampleRate = audio.sampleRate,
            channels = 1,
            durationMs = ((pcm.size * 1000L) / audio.sampleRate).toInt(),
            bytesWritten = request.outputFile.length(),
        )
    }

    override suspend fun cancel(): Unit = Unit

    override suspend fun close(): Unit {
        tts?.release()
        tts = null
        activeVoiceId = null
    }

    private fun ensureTts(voice: RussianVoice): OfflineTts {
        if (activeVoiceId == voice.id) tts?.let { return it }
        tts?.release()
        val root = findModelRoot(File(modelsDir, voice.id))
            ?: throw TtsEngineException.NotInstalled(voice.id)
        val model = root.walkTopDown().firstOrNull { it.isFile && it.extension == "onnx" }
            ?: throw TtsEngineException.NotInstalled(voice.id)
        val tokens = root.walkTopDown().firstOrNull { it.isFile && it.name == "tokens.txt" }
            ?: throw TtsEngineException.NotInstalled(voice.id)
        val dataDir = root.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }
            ?: throw TtsEngineException.NotInstalled(voice.id)
        val vits = OfflineTtsVitsModelConfig(
            model = model.absolutePath,
            tokens = tokens.absolutePath,
            dataDir = dataDir.absolutePath,
        )
        return OfflineTts(
            OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = vits,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            ),
        ).also {
            tts = it
            activeVoiceId = voice.id
        }
    }

    private fun installedVoices(): List<RussianVoice> =
        RUSSIAN_VOICES.filter { findModelRoot(File(modelsDir, it.id)) != null }

    private fun findModelRoot(directory: File): File? {
        if (!directory.isDirectory) return null
        val hasModel = directory.walkTopDown().any { it.isFile && it.extension == "onnx" }
        val hasTokens = directory.walkTopDown().any { it.isFile && it.name == "tokens.txt" }
        val hasEspeak = directory.walkTopDown().any { it.isDirectory && it.name == "espeak-ng-data" }
        return directory.takeIf { hasModel && hasTokens && hasEspeak }
    }

    data class RussianVoice(
        val id: String,
        val displayName: String,
        val gender: String,
        val language: String,
        val archiveUrl: String,
        val approximateSizeBytes: Long = 65_000_000L,
    )

    companion object {
        val RUSSIAN_VOICES = listOf(
            russianVoice("irina", "Ирина", "female"),
            russianVoice("denis", "Денис", "male"),
            russianVoice("dmitri", "Дмитрий", "male"),
            russianVoice("ruslan", "Руслан", "male"),
            piperVoice(
                id = "en-us-amy",
                displayName = "Amy",
                gender = "female",
                language = "en-US",
                archiveName = "vits-piper-en_US-amy-medium.tar.bz2",
            ),
            piperVoice(
                id = "en-gb-cori",
                displayName = "Cori",
                gender = "female",
                language = "en-GB",
                archiveName = "vits-piper-en_GB-cori-medium.tar.bz2",
            ),
        )

        val ENGINE_INFO = EngineInfo(
            id = "piper_ru",
            displayName = "Piper/VITS (на устройстве)",
            kind = EngineInfo.EngineKind.Local,
            supportsLocal = true,
        )

        private fun russianVoice(id: String, name: String, gender: String): RussianVoice {
            val archiveName = "vits-piper-ru_RU-$id-medium.tar.bz2"
            return RussianVoice(
                id = id,
                displayName = name,
                gender = gender,
                language = "ru-RU",
                archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName",
            )
        }

        private fun piperVoice(
            id: String,
            displayName: String,
            gender: String,
            language: String,
            archiveName: String,
        ) = RussianVoice(
            id = id,
            displayName = displayName,
            gender = gender,
            language = language,
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName",
        )
    }
}
