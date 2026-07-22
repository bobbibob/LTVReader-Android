package com.ltvreader.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioMixerTest {

    private val mixer = AudioMixer()

    @Test
    fun `dbToGain converts correctly`() {
        assertEquals(1.0, mixer.dbToGain(0.0), 0.0001)
        assertEquals(10.0, mixer.dbToGain(20.0), 0.0001)
        assertEquals(0.1, mixer.dbToGain(-20.0), 0.0001)
    }

    @Test
    fun `applyGain scales samples`() {
        val chunk = AudioChunk(shortArrayOf(1000, -1000, 2000, -2000), 22050, 1)
        val out = mixer.applyGain(chunk, 0.5)
        assertEquals(500, out.samples[0].toInt())
        assertEquals(-500, out.samples[1].toInt())
    }

    @Test
    fun `mix with no music applies gain and returns voice`() {
        val voice = AudioChunk(shortArrayOf(1000, 2000, 3000), 22050, 1)
        val out = mixer.mix(voice, null, AudioMixSettings(voicePath = "x", voiceVolumeDb = 0.0))
        assertEquals(3, out.samples.size)
    }

    @Test
    fun `mixWithDucking combines samples`() {
        val voice = AudioChunk(ShortArray(1000) { (it * 10).toShort() }, 22050, 1)
        val music = AudioChunk(ShortArray(1000) { 500 }, 22050, 1)
        val out = mixer.mixWithDucking(voice, music, voiceGain = 1.0, musicGain = 1.0, duckingDb = -6.0)
        assertEquals(1000, out.samples.size)
    }

    @Test
    fun `fades produce shorter signal at edges`() {
        val chunk = AudioChunk(ShortArray(1000) { Short.MAX_VALUE }, 22050, 1)
        val out = mixer.applyFades(chunk, fadeInMs = 100, fadeOutMs = 100)
        // В начале должно быть меньше, чем в середине
        assertTrue(out.samples[0] < out.samples[500])
        assertTrue(out.samples[999] < out.samples[500])
    }

    @Test
    fun `normalize keeps peak under Short_MAX`() {
        val chunk = AudioChunk(ShortArray(100) { (it * 100).toShort() }, 22050, 1)
        val out = mixer.normalize(chunk, targetLufs = -16.0)
        for (s in out.samples) {
            assertTrue(abs(s.toInt()) <= Short.MAX_VALUE.toInt())
        }
    }

    @Test
    fun `writeSilence produces correct duration`() {
        val tmp = kotlin.io.path.createTempFile(suffix = ".wav").toFile()
        val bytes = AudioEncoder.writeSilence(tmp, durationMs = 500, sampleRate = 22050)
        assertTrue(bytes > 0)
        val (_, chunk) = AudioEncoder.readWav(tmp)
        // 22050 * 0.5 = 11025 ± допуск
        assertTrue(kotlin.math.abs(chunk.samples.size - 11025) < 50)
    }
}
