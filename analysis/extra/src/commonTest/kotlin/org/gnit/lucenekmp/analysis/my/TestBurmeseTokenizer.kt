package org.gnit.lucenekmp.analysis.my

import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the Burmese tokenizer. */
class TestBurmeseTokenizer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testSyllableAndParticleSegmentation() {
        val tokenizer = BurmeseTokenizer()
        tokenizer.setReader(StringReader("မြန်မာစာကိုဖတ်သည်"))
        assertTokenStreamContents(
            tokenizer,
            arrayOf("မြန်", "မာ", "စာ", "ကို", "ဖတ်", "သည်")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testMixedText() {
        val tokenizer = BurmeseTokenizer()
        tokenizer.setReader(StringReader("Lucene ၁၂ မြန်မာ"))
        assertTokenStreamContents(tokenizer, arrayOf("lucene", "၁၂", "မြန်", "မာ"))
    }
}
