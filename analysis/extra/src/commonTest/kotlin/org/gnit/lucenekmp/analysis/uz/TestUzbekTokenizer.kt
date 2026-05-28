package org.gnit.lucenekmp.analysis.uz

import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the Uzbek tokenizer. */
class TestUzbekTokenizer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testApostropheLetters() {
        val tokenizer = UzbekTokenizer()
        tokenizer.setReader(StringReader("Oʻzbek g‘isht o'quvchi"))
        assertTokenStreamContents(tokenizer, arrayOf("Oʻzbek", "g‘isht", "o'quvchi"))
    }
}
