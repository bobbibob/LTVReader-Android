package com.ltvreader.core.audio

/**
 * Утилиты кодирования/декодирования WAV.
 *
 * 16-битный PCM, поддержка mono/stereo. Прямой порт упрощённой логики
 * из `app/utils/ffmpeg_utils.py` — но без ffmpeg, чтобы работать на
 * Android, где ffmpeg подключается отдельно (см. ffmpeg-kit).
 */
object AudioEncoder {

    /** Записать [chunk] в WAV-файл [out]. Возвращает количество байт данных. */
    fun writeWav(out: java.io.File, chunk: AudioChunk): Long {
        val dataSize = chunk.samples.size * 2
        val byteRate = chunk.sampleRate * chunk.channels * 2
        val totalSize = 36 + dataSize

        java.io.DataOutputStream(java.io.BufferedOutputStream(out.outputStream())).use { os ->
            // RIFF header
            os.writeBytes("RIFF")
            os.writeIntLe(totalSize)
            os.writeBytes("WAVE")
            // fmt subchunk
            os.writeBytes("fmt ")
            os.writeIntLe(16)              // subchunk size
            os.writeShortLe(1)              // PCM
            os.writeShortLe(chunk.channels.toShort().toInt())
            os.writeIntLe(chunk.sampleRate)
            os.writeIntLe(byteRate)
            os.writeShortLe((chunk.channels * 2).toShort().toInt()) // block align
            os.writeShortLe(16)             // bits per sample
            // data subchunk
            os.writeBytes("data")
            os.writeIntLe(dataSize)
            for (s in chunk.samples) os.writeShortLe(s.toInt())
        }
        return dataSize.toLong()
    }

    /**
     * Прочитать PCM из WAV-файла. Поддерживает 16-битный PCM,
     * остальные форматы конвертируются через ffmpeg-kit заранее.
     */
    fun readWav(file: java.io.File): Pair<WavInfo, AudioChunk> {
        java.io.DataInputStream(java.io.BufferedInputStream(file.inputStream())).use { ins ->
            // RIFF header
            val riff = ByteArray(4).also { ins.readFully(it) }
            require(riff.toString(Charsets.US_ASCII) == "RIFF") { "Not a RIFF file" }
            val riffSize = ins.readIntLe()
            val wave = ByteArray(4).also { ins.readFully(it) }
            require(wave.toString(Charsets.US_ASCII) == "WAVE") { "Not a WAVE file" }
            // iterate subchunks
            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            var dataOffset = 0
            var dataSize = 0
            while (ins.available() > 0) {
                val id = ByteArray(4).also { ins.readFully(it) }.toString(Charsets.US_ASCII)
                val size = ins.readIntLe()
                when (id) {
                    "fmt " -> {
                        val audioFormat = ins.readShortLe().toInt() and 0xFFFF
                        require(audioFormat == 1) { "Only PCM is supported (got format $audioFormat)" }
                        channels = ins.readShortLe().toInt() and 0xFFFF
                        sampleRate = ins.readIntLe()
                        val _byteRate = ins.readIntLe()
                        val _blockAlign = ins.readShortLe().toInt() and 0xFFFF
                        bitsPerSample = ins.readShortLe().toInt() and 0xFFFF
                        require(bitsPerSample == 16) { "Only 16-bit PCM is supported" }
                        // Skip extra fmt bytes if any
                        val extraFmt = size - 16
                        if (extraFmt > 0) ins.skipBytes(extraFmt)
                    }
                    "data" -> {
                        dataOffset = (ins.sizeIfKnown() ?: file.length().toInt()) - ins.available()
                        dataSize = size
                        val samples = ShortArray(dataSize / 2)
                        for (i in samples.indices) {
                            samples[i] = ins.readShortLe()
                        }
                        val info = WavInfo(sampleRate, channels, bitsPerSample, dataOffset, dataSize)
                        return info to AudioChunk(samples, sampleRate, channels)
                    }
                    else -> ins.skipBytes(size)
                }
            }
            error("WAV file has no 'data' subchunk")
        }
    }

    /** Сгенерировать WAV-файл с тишиной нужной длительности. */
    fun writeSilence(out: java.io.File, durationMs: Int, sampleRate: Int = 22050, channels: Int = 1): Long {
        val n = (sampleRate * channels * durationMs) / 1000
        val silence = ShortArray(n) // already zero
        return writeWav(out, AudioChunk(silence, sampleRate, channels))
    }
}

// DataInput/DataOutput helpers для little-endian
private fun java.io.DataInputStream.readIntLe(): Int =
    readUnsignedShort() or (readUnsignedShort() shl 16)

private fun java.io.DataInputStream.readShortLe(): Short {
    val b0 = read()
    val b1 = read()
    return ((b1 shl 8) or b0).toShort()
}

private fun java.io.DataOutputStream.writeIntLe(v: Int) {
    write(v and 0xFF)
    write((v shr 8) and 0xFF)
    write((v shr 16) and 0xFF)
    write((v shr 24) and 0xFF)
}

private fun java.io.DataOutputStream.writeShortLe(v: Int) {
    write(v and 0xFF)
    write((v shr 8) and 0xFF)
}
