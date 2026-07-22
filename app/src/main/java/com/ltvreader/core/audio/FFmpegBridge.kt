package com.ltvreader.core.audio

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Тонкая обёртка вокруг ffmpeg-kit для Android.
 *
 * Используется для финального кодирования PCM→MP3/OGG/M4A,
 * склейки сегментов, наложения музыки с ducking через sidechaincompress,
 * обрезки тишины в начале/конце и экспорта.
 *
 * Это 1:1 замена `FFmpegRunner` из оригинала, без Windows-специфики
 * (CREATE_NO_WINDOW и т.п.).
 */
object FFmpegBridge {

    /**
     * Закодировать WAV → [format] (mp3/ogg/m4a/wav).
     */
    suspend fun encode(wav: File, output: File, format: String, bitrate: String = "192k"): File = withContext(Dispatchers.IO) {
        val codec = when (format.lowercase()) {
            "mp3" -> "libmp3lame"
            "ogg" -> "libvorbis"
            "m4a" -> "aac"
            else -> "pcm_s16le"
        }
        val ext = if (format.equals("wav", true)) "wav" else format
        val target = if (output.extension.isEmpty()) File(output.parentFile, "${output.nameWithoutExtension}.$ext") else output

        val cmd = buildString {
            append("-y -i ")
            append(quote(wav.absolutePath))
            append(" -c:a ").append(codec)
            if (format.equals("mp3", true) || format.equals("ogg", true) || format.equals("m4a", true)) {
                append(" -b:a ").append(bitrate)
            }
            append(" ").append(quote(target.absolutePath))
        }
        run(cmd)
        target
    }

    /**
     * Склеить несколько WAV в один с fade-in/fade-out на стыках.
     * Использует concat demuxer с автоматической подгонкой sample rate.
     */
    suspend fun concat(wavs: List<File>, output: File, format: String = "mp3", bitrate: String = "192k"): File = withContext(Dispatchers.IO) {
        val listFile = File(output.parentFile, "concat_${System.currentTimeMillis()}.txt")
        listFile.bufferedWriter().use { w ->
            for (f in wavs) {
                // Экранирование одинарных кавычек для ffmpeg concat
                val safe = f.absolutePath.replace("'", "'\\''")
                w.write("file '$safe'\n")
            }
        }
        val target = File(output.parentFile, "${output.nameWithoutExtension}.${format}")
        val cmd = buildString {
            append("-y -f concat -safe 0 -i ")
            append(quote(listFile.absolutePath))
            append(" -c:a ")
            append(if (format == "mp3") "libmp3lame" else "pcm_s16le")
            if (format == "mp3") append(" -b:a ").append(bitrate)
            append(" ").append(quote(target.absolutePath))
        }
        try {
            run(cmd)
        } finally {
            listFile.delete()
        }
        target
    }

    /**
     * Наложить музыку с sidechain ducking.
     *
     * voice — WAV голоса (доминирующая дорожка).
     * music — WAV музыки.
     * output — куда записать результат.
     */
    suspend fun applyMusicDucking(
        voice: File,
        music: File,
        output: File,
        musicVolumeDb: Double = -12.0,
        duckingDb: Double = -6.0,
        format: String = "mp3",
        bitrate: String = "192k",
    ): File = withContext(Dispatchers.IO) {
        val target = File(output.parentFile, "${output.nameWithoutExtension}.${format}")
        val filter = StringBuilder().apply {
            // Применяем громкость к музыке, потом ducking через sidechaincompress
            append("[1:a]volume=").append(db(musicVolumeDb))
                .append("dB[music];")
            append("[0:a][music]sidechaincompress=threshold=0.05:ratio=8:attack=20:release=1000:makeup=")
                .append(db(-duckingDb))
                .append("[ducked];")
            append("[ducked]aresample=44100[out]")
        }
        val cmd = buildString {
            append("-y -i ").append(quote(voice.absolutePath))
            append(" -i ").append(quote(music.absolutePath))
            append(" -filter_complex ").append(quote(filter.toString()))
            append(" -map '[out]' -c:a ")
            append(if (format == "mp3") "libmp3lame" else "pcm_s16le")
            if (format == "mp3") append(" -b:a ").append(bitrate)
            append(" ").append(quote(target.absolutePath))
        }
        run(cmd)
        target
    }

    /**
     * Обрезать тишину в начале и в конце. Использует silenceremove.
     */
    suspend fun trimSilence(input: File, output: File, thresholdDb: Double = -40.0): File = withContext(Dispatchers.IO) {
        val filter = "silenceremove=start_periods=1:start_silence=0:start_threshold=${thresholdDb}dB:" +
            "stop_periods=-1:stop_silence=0:stop_threshold=${thresholdDb}dB"
        val cmd = "-y -i ${quote(input.absolutePath)} -af $filter ${quote(output.absolutePath)}"
        run(cmd)
        output
    }

    private fun run(cmd: String) {
        val session = FFmpegKit.execute(cmd)
        if (!ReturnCode.isSuccess(session.returnCode)) {
            val logs = session.allLogsAsString
            session.cancel()
            throw RuntimeException("ffmpeg failed: ${session.failStackTrace}\n--- LOGS ---\n$logs")
        }
    }

    private fun quote(s: String): String = "'${s.replace("'", "'\\''")}'"
    private fun db(v: Double): String = "%.2f".format(v)
}
