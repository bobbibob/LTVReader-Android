package com.t2v.generators

import androidx.test.core.app.ApplicationProvider
import com.t2v.generators.impl.BundledMusicGenerator
import com.t2v.generators.impl.BundledSoundGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Integration test for the bundled music/sound generators.
 *
 * Verifies that the generators can read WAV assets, copy them to the output
 * file, and return correct metadata (sample rate, channels, duration) by
 * parsing the WAV header.
 *
 * Uses Robolectric so that [android.content.Context.assets] is available
 * in the JVM test environment.
 */
@RunWith(RobolectricTestRunner::class)
class BundledAssetGeneratorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val outputDir = File(context.filesDir, "test-gen-output").apply { mkdirs() }

    @Test
    fun `bundled music copies ambient pad and returns correct metadata`() = runBlocking {
        val gen = BundledMusicGenerator(context)
        assertTrue("Bundled music should be available", gen.isAvailable())
        assertEquals(GeneratorCategory.Music, gen.category)

        val output = File(outputDir, "music-ambient.wav")
        val result = gen.generate(
            GeneratorRequest(
                prompt = "ambient background",
                outputFile = output,
                category = GeneratorCategory.Music,
            ),
        )

        assertTrue("Output file should exist", output.isFile)
        assertTrue("Output should be a WAV > 1KB", output.length() > 1024)
        assertEquals(22050, result.sampleRate)
        assertEquals(1, result.channels)
        assertTrue("Duration should be > 0", result.durationMs > 0)
        assertTrue("BytesWritten should match file size", result.bytesWritten == output.length())
    }

    @Test
    fun `bundled sound copies whoosh and returns correct metadata`() = runBlocking {
        val gen = BundledSoundGenerator(context)
        assertTrue("Bundled sound should be available", gen.isAvailable())
        assertEquals(GeneratorCategory.Sound, gen.category)

        val output = File(outputDir, "sound-whoosh.wav")
        val result = gen.generate(
            GeneratorRequest(
                prompt = "whoosh transition",
                outputFile = output,
                category = GeneratorCategory.Sound,
            ),
        )

        assertTrue("Output file should exist", output.isFile)
        assertTrue("Output should be a WAV > 1KB", output.length() > 1024)
        assertEquals(22050, result.sampleRate)
        assertEquals(1, result.channels)
        assertTrue("Duration should be > 0", result.durationMs > 0)
    }

    @Test
    fun `bundled music falls back to ambient for unknown prompt`() = runBlocking {
        val gen = BundledMusicGenerator(context)
        val output = File(outputDir, "music-fallback.wav")
        val result = gen.generate(
            GeneratorRequest(
                prompt = "something completely unknown",
                outputFile = output,
                category = GeneratorCategory.Music,
            ),
        )

        assertTrue("Fallback should still produce a file", output.isFile)
        assertTrue("Fallback duration should be > 0", result.durationMs > 0)
    }

    @Test
    fun `bundled sound matches door close`() = runBlocking {
        val gen = BundledSoundGenerator(context)
        val output = File(outputDir, "sound-door.wav")
        val result = gen.generate(
            GeneratorRequest(
                prompt = "door close slam",
                outputFile = output,
                category = GeneratorCategory.Sound,
            ),
        )

        assertTrue("Door close should produce a file", output.isFile)
        assertTrue("Door close duration should be > 0", result.durationMs > 0)
    }
}
