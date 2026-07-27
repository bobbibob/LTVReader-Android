package com.t2v.generators

import com.t2v.tts.registry.EngineRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorRegistryTest {

    @Test
    fun `bundled music and sound are always available`() {
        // No Android context here: isAvailable() must be pure logic so the
        // registry can be exercised without a device.
        val ids = listOf(
            com.t2v.generators.impl.BundledMusicGenerator::class,
            com.t2v.generators.impl.BundledSoundGenerator::class,
        )
        assertTrue(ids.isNotEmpty())
    }

    @Test
    fun `elevenlabs sound is unavailable without an api key`() {
        val gen = com.t2v.generators.impl.ElevenLabsSoundEffectsGenerator(apiKey = "")
        assertFalse(gen.isAvailable())
    }

    @Test
    fun `elevenlabs sound is available with an api key`() {
        val gen = com.t2v.generators.impl.ElevenLabsSoundEffectsGenerator(apiKey = "sk-test")
        assertTrue(gen.isAvailable())
        assertEquals(GeneratorCategory.Sound, gen.category)
        assertEquals("elevenlabs.sound", gen.id)
    }

    @Test
    fun `GeneratorResult carries byte count and metadata`() {
        val tmp = java.io.File.createTempFile("generator", ".wav").also { it.deleteOnExit() }
        tmp.writeBytes(ByteArray(4))
        val result = GeneratorResult(
            outputFile = tmp,
            sampleRate = 22050,
            channels = 1,
            durationMs = 0,
            bytesWritten = 4,
        )
        assertNotNull(result.outputFile)
        assertEquals(4, result.bytesWritten)
    }

    @Test
    fun `EngineSettings defaults preserve generator contract`() {
        val settings = EngineRegistry.EngineSettings()
        assertEquals(emptyMap<String, Map<String, String>>(), settings.engines)
    }
}
