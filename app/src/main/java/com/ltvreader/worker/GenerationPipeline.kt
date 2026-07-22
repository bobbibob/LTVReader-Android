package com.ltvreader.worker

import android.content.Context
import com.ltvreader.core.audio.AudioEncoder
import com.ltvreader.core.audio.FFmpegBridge
import com.ltvreader.core.text.TextProcessor
import com.ltvreader.data.AudiobookEntity
import com.ltvreader.data.SegmentEntity
import com.ltvreader.tts.TtsEngineException
import com.ltvreader.tts.TtsRequest
import com.ltvreader.tts.VoiceConfig
import com.ltvreader.tts.engines.TtsEngine
import com.ltvreader.tts.registry.EngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Пайплайн генерации. Прямой порт `app/core/audio_pipeline.py:AudioGenerationPipeline`
 * (2513 строк оригинала).
 *
 * Стратегия:
 *   1. text → chunks (TextProcessor)
 *   2. chunks → segments (с pause_before_ms / pause_after_ms)
 *   3. для каждого сегмента — TTS-вызов
 *   4. WAV-склейка + ffmpeg-кодирование в MP3
 *
 * Поддерживает отмену, прогресс, ретраи.
 */
class GenerationPipeline(
    private val context: Context,
    private val engineRegistry: EngineRegistry,
    private val textProcessor: TextProcessor,
) {

    data class Progress(
        val total: Int = 0,
        val done: Int = 0,
        val currentSegment: String? = null,
        val phase: Phase = Phase.Idle,
        val error: String? = null,
    ) {
        enum class Phase { Idle, Processing, Synthesizing, Encoding, Completed, Failed, Cancelled }
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var cancelled = false

    suspend fun cancel() {
        cancelled = true
    }

    suspend fun generate(
        projectId: Long,
        audiobookId: Long,
        rawText: String,
        voice: VoiceConfig,
        engineId: String,
        outputDir: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        cancelled = false
        runCatching {
            outputDir.mkdirs()
            val (sections, chunks) = textProcessor.process(rawText)
            val segmentWavs = mutableListOf<File>()
            val total = chunks.size
            _progress.value = Progress(total = total, done = 0, phase = Progress.Phase.Processing)

            val engine = engineRegistry.get(engineId)

            for ((idx, chunk) in chunks.withIndex()) {
                if (cancelled) {
                    _progress.update { it.copy(phase = Progress.Phase.Cancelled) }
                    throw TtsEngineException.Cancelled()
                }
                _progress.update {
                    it.copy(
                        currentSegment = chunk.text.take(80),
                        phase = Progress.Phase.Synthesizing,
                    )
                }
                val wav = File(outputDir, "seg_%05d.wav".format(idx))
                val req = TtsRequest(
                    text = chunk.text,
                    outputFile = wav,
                    voice = voice.copy(
                        speed = chunk.markupState.speed ?: voice.speed,
                        volume = chunk.markupState.volume ?: voice.volume,
                        pitch = chunk.markupState.pitch ?: voice.pitch,
                        lang = chunk.markupState.language ?: voice.lang,
                        voice = chunk.markupState.voice ?: voice.voice,
                    ),
                )
                val result = withRetry(maxAttempts = 2) { engine.synthesize(req) }

                // Между чанками — тишина
                if (chunk.markupPauseBeforeMs > 0) {
                    val pre = File(outputDir, "pause_pre_%05d.wav".format(idx))
                    AudioEncoder.writeSilence(pre, chunk.markupPauseBeforeMs)
                    segmentWavs += pre
                }
                segmentWavs += result.outputFile
                if (chunk.markupPauseAfterMs != null && chunk.markupPauseAfterMs > 0) {
                    val post = File(outputDir, "pause_post_%05d.wav".format(idx))
                    AudioEncoder.writeSilence(post, chunk.markupPauseAfterMs)
                    segmentWavs += post
                }
                _progress.update { it.copy(done = idx + 1) }
            }

            // Кодируем
            _progress.update { it.copy(phase = Progress.Phase.Encoding) }
            val finalMp3 = File(outputDir, "audiobook.mp3")
            if (segmentWavs.size == 1) {
                FFmpegBridge.encode(context, segmentWavs.first(), finalMp3, "mp3")
            } else {
                FFmpegBridge.concat(context, segmentWavs, finalMp3, "mp3")
            }
            _progress.update { it.copy(phase = Progress.Phase.Completed) }
            finalMp3
        }.onFailure { e ->
            _progress.update {
                it.copy(phase = if (cancelled) Progress.Phase.Cancelled else Progress.Phase.Failed, error = e.message)
            }
        }
    }

    private suspend fun <T> withRetry(maxAttempts: Int, block: suspend () -> T): T {
        var last: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: TtsEngineException.Cancelled) {
                throw e
            } catch (t: Throwable) {
                last = t
                if (attempt == maxAttempts - 1) throw t
            }
        }
        throw last ?: RuntimeException("retry failed")
    }
}
