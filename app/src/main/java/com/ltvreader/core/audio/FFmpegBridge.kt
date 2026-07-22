package com.ltvreader.core.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Тонкая обёртка вокруг FFmpeg для Android.
 *
 * Используется для финального кодирования PCM→MP3/OGG/M4A,
 * склейки сегментов, наложения музыки с ducking через sidechaincompress,
 * обрезки тишины в начале/конце и экспорта.
 *
 * Реализация: нативный бинарник FFmpeg, поставляемый в assets/ffmpeg/
 * или скачиваемый при первом запуске.
 *
 * Заменяет `com.arthenica.ffmpegkit` (проект Arthenica был удалён с Maven Central)
 * на нативный бинарник FFmpeg, который мы запускаем через Runtime.
 */
object FFmpegBridge {

    /** Возвращает путь к FFmpeg-бинарю, при необходимости распаковывая его из assets. */
    @Volatile private var ffmpegPath: String? = null

    suspend fun ensure(context: Context): String = withContext(Dispatchers.IO) {
        ffmpegPath?.let { return@withContext it }
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val exeName = if (abi.startsWith("arm")) "ffmpeg-$abi" else "ffmpeg-x86_64"
        val cached = File(context.filesDir, "ffmpeg/$exeName")
        if (!cached.exists()) {
            cached.parentFile?.mkdirs()
            val assetPath = "ffmpeg/$abi/ffmpeg"
            runCatching {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(cached).use { output -> input.copyTo(output) }
                }
            }.onFailure {
                throw RuntimeException("FFmpeg binary not found for $abi. " +
                    "Place ffmpeg in app/src/main/assets/ffmpeg/$abi/ffmpeg")
            }
        }
        cached.setExecutable(true)
        ffmpegPath = cached.absolutePath
        cached.absolutePath
    }

    /**
     * Закодировать WAV → [format] (mp3/ogg/m4a/wav).
     */
    suspend fun encode(
        context: Context,
        wav: File,
        output: File,
        format: String,
        bitrate: String = "192k",
    ): File = withContext(Dispatchers.IO) {
        val codec = when (format.lowercase()) {
            "mp3" -> "libmp3lame"
            "ogg" -> "libvorbis"
            "m4a" -> "aac"
            else -> "pcm_s16le"
        }
        val ext = if (format.equals("wav", true)) "wav" else format
        val target = if (output.extension.isEmpty()) {
            File(output.parentFile, "${output.nameWithoutExtension}.$ext")
        } else output
        val cmd = listOf(
            "-y", "-i", wav.absolutePath,
            "-c:a", codec,
            "-b:a", bitrate,
            target.absolutePath,
        )
        run(context, cmd)
        target
    }

    /**
     * Склеить несколько WAV в один.
     */
    suspend fun concat(
        context: Context,
        wavs: List<File>,
        output: File,
        format: String = "mp3",
        bitrate: String = "192k",
    ): File = withContext(Dispatchers.IO) {
        val listFile = File(output.parentFile, "concat_${System.currentTimeMillis()}.txt")
        listFile.bufferedWriter().use { w ->
            for (f in wavs) {
                val safe = f.absolutePath.replace("'", "'\\''")
                w.write("file '$safe'\n")
            }
        }
        val target = File(output.parentFile, "${output.nameWithoutExtension}.${format}")
        val cmd = listOf(
            "-y", "-f", "concat", "-safe", "0",
            "-i", listFile.absolutePath,
            "-c:a", if (format == "mp3") "libmp3lame" else "pcm_s16le",
            "-b:a", bitrate,
            target.absolutePath,
        )
        try {
            run(context, cmd)
        } finally {
            listFile.delete()
        }
        target
    }

    /**
     * Наложить музыку с sidechain ducking.
     */
    suspend fun applyMusicDucking(
        context: Context,
        voice: File,
        music: File,
        output: File,
        musicVolumeDb: Double = -12.0,
        duckingDb: Double = -6.0,
        format: String = "mp3",
        bitrate: String = "192k",
    ): File = withContext(Dispatchers.IO) {
        val target = File(output.parentFile, "${output.nameWithoutExtension}.${format}")
        val filter = "[1:a]volume=${"%.2f".format(musicVolumeDb)}dB[music];" +
            "[0:a][music]sidechaincompress=threshold=0.05:ratio=8:attack=20:release=1000:" +
            "makeup=${"%.2f".format(-duckingDb)}[ducked];" +
            "[ducked]aresample=44100[out]"
        val cmd = listOf(
            "-y",
            "-i", voice.absolutePath,
            "-i", music.absolutePath,
            "-filter_complex", filter,
            "-map", "[out]",
            "-c:a", if (format == "mp3") "libmp3lame" else "pcm_s16le",
            "-b:a", bitrate,
            target.absolutePath,
        )
        run(context, cmd)
        target
    }

    /**
     * Обрезать тишину в начале и в конце.
     */
    suspend fun trimSilence(
        context: Context,
        input: File,
        output: File,
        thresholdDb: Double = -40.0,
    ): File = withContext(Dispatchers.IO) {
        val filter = "silenceremove=start_periods=1:start_silence=0:" +
            "start_threshold=${thresholdDb}dB:stop_periods=-1:stop_silence=0:" +
            "stop_threshold=${thresholdDb}dB"
        run(context, listOf("-y", "-i", input.absolutePath, "-af", filter, output.absolutePath))
        output
    }

    private suspend fun run(context: Context, args: List<String>) = withContext(Dispatchers.IO) {
        val exe = ensure(context)
        val full = listOf(exe) + args
        val process = ProcessBuilder(full)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val rc = process.waitFor()
        if (rc != 0) {
            throw RuntimeException("ffmpeg exit=$rc:\n$output")
        }
    }
}
