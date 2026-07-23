package com.ltvreader.core.markup

import org.junit.Test
import kotlin.test.assertEquals

class PauseDebugTest {
    @Test
    fun `0_7s should be 700ms`() {
        val parser = LTVMarkupParser()
        val parsed = parser.parse("{{pause 0.7s}}")
        val pauses = parsed.commands.filterIsInstance<MarkupCommand.Pause>()
        println("Pauses: ${pauses.map { it.durationMs }}")
        assertEquals(700, pauses.first().durationMs)
    }
}
