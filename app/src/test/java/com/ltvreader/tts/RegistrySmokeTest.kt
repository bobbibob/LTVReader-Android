package com.ltvreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrySmokeTest {

    @Test
    fun `kokoro engine info is local`() {
        val info = com.ltvreader.tts.engines.KokoroTtsEngine.ENGINE_INFO
        assertNotNull(info)
        assertEquals("kokoro", info.id)
        assertTrue(info.supportsLocal)
    }

    @Test
    fun `openai engine requires api key`() {
        val info = com.ltvreader.tts.engines.OpenAiTtsEngine.ENGINE_INFO
        assertTrue(info.requiresApiKey)
    }

    @Test
    fun `azure engine is cloud`() {
        val info = com.ltvreader.tts.engines.AzureTtsEngine.ENGINE_INFO
        assertEquals(EngineInfo.EngineKind.Cloud, info.kind)
    }

    @Test
    fun `all cloud engines have a config schema`() {
        val engines = listOf(
            com.ltvreader.tts.engines.OpenAiTtsEngine.ENGINE_INFO,
            com.ltvreader.tts.engines.ElevenLabsTtsEngine.ENGINE_INFO,
            com.ltvreader.tts.engines.GeminiTtsEngine.ENGINE_INFO,
            com.ltvreader.tts.engines.AzureTtsEngine.ENGINE_INFO,
        )
        for (e in engines) {
            assertTrue("Engine ${e.id} has empty config schema", e.configSchema.isNotEmpty())
        }
    }
}
