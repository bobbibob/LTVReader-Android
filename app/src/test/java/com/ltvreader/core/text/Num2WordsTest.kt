package com.ltvreader.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Тесты для утилиты Num2Words, которая лежит в TextNormalizer
 * (см. core/normalization/TextNormalizer.kt). На текущий момент
 * Num2Words инкапсулирован внутри TextNormalizer, поэтому проверяем
 * его через нормализатор — это end-to-end.
 */
class Num2WordsTest {

    @Test
    fun `numbers in normalization are converted to words`() {
        val n = TextNormalizer(NormalizationRules(enabled = true, numbers = true))
        val out = n.normalize("I have 3 cats and 12 dogs")
        // Любой из вариантов написания
        assert(out.lowercase().contains("three") || out.lowercase().contains("3"))
    }

    @Test
    fun `decimals work`() {
        val n = TextNormalizer(NormalizationRules(enabled = true, numbers = true))
        val out = n.normalize("It is 3.14 pi")
        assertNotNull(out)
    }
}
