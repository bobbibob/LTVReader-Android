package com.ltvreader.core.markup

import org.junit.Test

class PauseDebugTest {
    @Test
    fun `debug 0_7s`() {
        val parser = LTVMarkupParser()
        val parsed = parser.parse("{{pause 0.7s}}")
        println("Commands: ${parsed.commands}")
        println("Pause: ${parsed.commands.filterIsInstance<MarkupCommand.Pause>()}")
    }
}
