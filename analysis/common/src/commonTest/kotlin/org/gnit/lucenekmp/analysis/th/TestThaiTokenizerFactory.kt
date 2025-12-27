package org.gnit.lucenekmp.analysis.th

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Thai tokenizer factory is working. */
class TestThaiTokenizerFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Ensure the tokenizer actually decomposes text. */
    @Test
    @Throws(Exception::class)
    fun testWordBreak() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val tokenizer: Tokenizer = tokenizerFactory("Thai").create(newAttributeFactory())
        tokenizer.setReader(StringReader("การที่ได้ต้องแสดงว่างานดี"))
        assertTokenStreamContents(
            tokenizer,
            arrayOf("การ", "ที่", "ได้", "ต้อง", "แสดง", "ว่า", "งาน", "ดี")
        )
    }

    /** Test that bogus arguments result in exception. */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenizerFactory("Thai", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
