package com.t2v.tts.engines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiperRussianCatalogTest {
    @Test
    fun `catalog contains four unique Russian Android voices`() {
        val voices = PiperRussianTtsEngine.RUSSIAN_VOICES
        val russianVoices = voices.filter { it.language == "ru-RU" }

        assertEquals(4, russianVoices.size)
        assertEquals(voices.size, voices.map { it.id }.distinct().size)
        assertEquals(
            listOf("irina", "denis", "dmitri", "ruslan"),
            russianVoices.map { it.id },
        )
        assertTrue(voices.any { it.language == "en-US" })
        assertTrue(voices.any { it.language == "en-GB" })
        assertTrue(voices.all { it.archiveUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/") })
        assertTrue(voices.all { it.archiveUrl.endsWith("-medium.tar.bz2") })
        assertTrue(voices.all { it.approximateSizeBytes > 0 })
    }
}
