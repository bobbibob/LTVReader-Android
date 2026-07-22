package com.ltvreader.core.markup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LTVMarkupParserTest {

    private val parser = LTVMarkupParser(random = kotlin.random.Random(7))

    @Test
    fun `parses voice and chapter commands`() {
        val src = """{{chapter "Lesson 1"}}
            {{voice "Serena - Spanish"}}Bienvenido a esta lección.
            {{pause 900ms}}
            {{voice "Sohee - English"}}Now listen.
        """.trimIndent()
        val parsed = parser.parse(src)
        // chapter, voice x2, pause, voice
        assertTrue(parsed.commands.any { it is MarkupCommand.Chapter && it.title == "Lesson 1" })
        assertTrue(parsed.commands.count { it is MarkupCommand.Voice } == 2)
        assertTrue(parsed.commands.any { it is MarkupCommand.Pause && it.durationMs == 900 })
    }

    @Test
    fun `pause supports ms s and long short keywords`() {
        val parsed = parser.parse("""A {{pause 0.7s}} B {{pause.long}} C""")
        val pauses = parsed.commands.filterIsInstance<MarkupCommand.Pause>()
        assertEquals(700, pauses[0].durationMs)
        assertEquals(1500, pauses[1].durationMs)
    }

    @Test
    fun `pause random uses range`() {
        val parsed = parser.parse("{{pause random 500 500}}")
        val p = parsed.commands.filterIsInstance<MarkupCommand.Pause>().first()
        assertEquals(500, p.durationMs)
    }

    @Test
    fun `volume accepts percent and number`() {
        val p1 = parser.parse("{{volume 80%}}")
        val p2 = parser.parse("{{volume 0.5}}")
        assertTrue(p1.commands.first() is MarkupCommand.Volume)
        assertTrue(p2.commands.first() is MarkupCommand.Volume)
    }

    @Test
    fun `final state merges last voice and lang`() {
        val parsed = parser.parse("""{{voice "Alice"}}{{lang es}}{{speed 1.2}}Hello""")
        assertEquals("Alice", parsed.finalState.voice)
        assertEquals("es", parsed.finalState.language)
        assertEquals(1.2, parsed.finalState.speed!!, 0.0001)
    }

    @Test
    fun `placeholder removed from plain text`() {
        val parsed = parser.parse("""A {{pause 500ms}} B""")
        assertTrue(!parsed.plainText.contains("{{"))
        assertTrue(parsed.plainText.contains("A"))
        assertTrue(parsed.plainText.contains("B"))
    }

    @Test
    fun `cmd command parses key equals value`() {
        val parsed = parser.parse("""{{cmd style="narrator" tone="calm"}}""")
        val custom = parsed.commands.filterIsInstance<MarkupCommand.Custom>().first()
        assertEquals("narrator", custom.values["style"])
        assertEquals("calm", custom.values["tone"])
    }
}
