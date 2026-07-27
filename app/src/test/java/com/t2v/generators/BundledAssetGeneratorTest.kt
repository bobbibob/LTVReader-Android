package com.t2v.generators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Tests for the bundled music/sound generator assets and WAV header parsing.
 *
 * Reads the WAV files directly from the source assets directory (not through
 * Android's AssetManager) so the test runs in a plain JVM without Robolectric.
 * The WAV header parsing logic mirrors [com.t2v.generators.impl.BundledAssetGenerator].
 */
class BundledAssetGeneratorTest {

    private val assetsDir = File("src/main/assets")

    // Expected keyword → asset path mappings (must match BundledMusicGenerator / BundledSoundGenerator)
    private val musicAssets = mapOf(
        "ambient" to "music/ambient-pad.wav",
        "cinema" to "music/calm-cinema.wav",
        "uplift" to "music/uplift.wav",
    )
    private val soundAssets = mapOf(
        "door" to "sound/door-close.wav",
        "notif" to "sound/notification.wav",
        "whoosh" to "sound/whoosh.wav",
    )
    private val musicFallback = "music/ambient-pad.wav"
    private val soundFallback = "sound/whoosh.wav"

    @Test
    fun `all bundled music assets exist and are valid 22050 Hz mono WAVs`() {
        for ((keyword, path) in musicAssets) {
            val file = File(assetsDir, path)
            assertTrue("Missing music asset for keyword '$keyword': $path", file.isFile)
            assertTrue("$path should be > 10KB", file.length() > 10_000)
            val meta = readWavMeta(file)
            assertTrue("Could not parse WAV header for $path", meta != null)
            assertEquals("Sample rate for $path", 22050, meta!!.sampleRate)
            assertEquals("Channels for $path", 1, meta.channels)
            assertTrue("Duration for $path should be > 0", meta.durationMs > 0)
        }
    }

    @Test
    fun `all bundled sound assets exist and are valid 22050 Hz mono WAVs`() {
        for ((keyword, path) in soundAssets) {
            val file = File(assetsDir, path)
            assertTrue("Missing sound asset for keyword '$keyword': $path", file.isFile)
            assertTrue("$path should be > 5KB", file.length() > 5_000)
            val meta = readWavMeta(file)
            assertTrue("Could not parse WAV header for $path", meta != null)
            assertEquals("Sample rate for $path", 22050, meta!!.sampleRate)
            assertEquals("Channels for $path", 1, meta.channels)
            assertTrue("Duration for $path should be > 0", meta.durationMs > 0)
        }
    }

    @Test
    fun `fallback assets exist`() {
        assertTrue("Music fallback must exist", File(assetsDir, musicFallback).isFile)
        assertTrue("Sound fallback must exist", File(assetsDir, soundFallback).isFile)
    }

    @Test
    fun `WAV header parser reads correct metadata for ambient pad`() {
        val file = File(assetsDir, "music/ambient-pad.wav")
        val meta = readWavMeta(file)
        assertTrue(meta != null)
        assertEquals(22050, meta!!.sampleRate)
        assertEquals(1, meta.channels)
        // ~129KB data = ~66150 samples ≈ 3s at 22050 Hz
        assertTrue("Duration should be ~3s, got ${meta.durationMs}ms", meta.durationMs in 2500..3500)
    }

    @Test
    fun `WAV header parser reads correct metadata for whoosh`() {
        val file = File(assetsDir, "sound/whoosh.wav")
        val meta = readWavMeta(file)
        assertTrue(meta != null)
        assertEquals(22050, meta!!.sampleRate)
        assertEquals(1, meta.channels)
        // ~26KB data = ~13230 samples ≈ 0.6s at 22050 Hz
        assertTrue("Duration should be ~0.6s, got ${meta.durationMs}ms", meta.durationMs in 400..800)
    }

    @Test
    fun `keyword matching picks correct asset`() {
        // Simulates BundledAssetGenerator.pickAsset() logic
        assertEquals("music/ambient-pad.wav", pickAsset(musicAssets, musicFallback, "ambient background"))
        assertEquals("music/calm-cinema.wav", pickAsset(musicAssets, musicFallback, "cinema calm"))
        assertEquals("music/uplift.wav", pickAsset(musicAssets, musicFallback, "uplift beat"))
        assertEquals("music/ambient-pad.wav", pickAsset(musicAssets, musicFallback, "something unknown"))
        assertEquals("sound/door-close.wav", pickAsset(soundAssets, soundFallback, "door close slam"))
        assertEquals("sound/notification.wav", pickAsset(soundAssets, soundFallback, "notif alert"))
        assertEquals("sound/whoosh.wav", pickAsset(soundAssets, soundFallback, "whoosh transition"))
        assertEquals("sound/whoosh.wav", pickAsset(soundAssets, soundFallback, "unknown sound"))
    }

    // --- Helpers ---

    private fun pickAsset(assets: Map<String, String>, fallback: String, prompt: String): String {
        val lower = prompt.lowercase()
        return assets.entries.firstOrNull { (key, _) -> lower.contains(key) }?.value ?: fallback
    }

    private data class WavMeta(val sampleRate: Int, val channels: Int, val durationMs: Int)

    /**
     * Mirrors the WAV header parsing in BundledAssetGenerator.readWavMeta().
     */
    private fun readWavMeta(file: File): WavMeta? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val riff = ByteArray(4).also { raf.readFully(it) }
                if (riff.toString(Charsets.US_ASCII) != "RIFF") return null
                raf.skipBytes(4)
                val wave = ByteArray(4).also { raf.readFully(it) }
                if (wave.toString(Charsets.US_ASCII) != "WAVE") return null
                var sampleRate = 0
                var channels = 0
                var bitsPerSample = 0
                var dataSize = 0
                while (raf.filePointer < raf.length()) {
                    val idBytes = ByteArray(4)
                    if (raf.read(idBytes) < 4) break
                    val id = idBytes.toString(Charsets.US_ASCII)
                    val size = raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)
                    when (id) {
                        "fmt " -> {
                            raf.skipBytes(2)
                            channels = raf.read() or (raf.read() shl 8)
                            sampleRate = raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)
                            raf.skipBytes(4)
                            raf.skipBytes(2)
                            bitsPerSample = raf.read() or (raf.read() shl 8)
                            val extra = size - 16
                            if (extra > 0) raf.skipBytes(extra)
                        }
                        "data" -> { dataSize = size; break }
                        else -> raf.skipBytes(size)
                    }
                }
                val bytesPerSample = bitsPerSample / 8
                val totalSamples = if (bytesPerSample > 0) dataSize / (bytesPerSample * channels) else 0
                val durationMs = if (sampleRate > 0) (totalSamples * 1000L / sampleRate).toInt() else 0
                WavMeta(sampleRate, channels, durationMs)
            }
        } catch (e: Exception) { null }
    }
}
