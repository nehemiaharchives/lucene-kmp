package org.gnit.lucenekmp.analysis.ar

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Arabic filter Factories are working. */
class TestArabicFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Test ArabicNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testNormalizer() {
        val reader: Reader = StringReader("الذين مَلكت أيمانكم")
        val tokenizer = whitespaceMockTokenizer(reader)
        var stream: TokenStream = tokenizer
        stream = tokenFilterFactory("ArabicNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("الذين", "ملكت", "ايمانكم"))
    }

    /** Test ArabicStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("الذين مَلكت أيمانكم")
        val tokenizer = whitespaceMockTokenizer(reader)
        var stream: TokenStream = tokenizer
        stream = tokenFilterFactory("ArabicNormalization").create(stream)
        stream = tokenFilterFactory("ArabicStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("ذين", "ملكت", "ايمانكم"))
    }

    /** Test PersianCharFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testPersianCharFilter() {
        val reader: Reader = charFilterFactory("Persian").create(StringReader("می‌خورد"))
        val tokenizer = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(tokenizer, arrayOf("می", "خورد"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedNormalization = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("ArabicNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedNormalization.message?.contains("Unknown parameters") == true)

        val expectedStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("Arabicstem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedStem.message?.contains("Unknown parameters") == true)

        val expectedPersian = assertFailsWith<IllegalArgumentException> {
            charFilterFactory("Persian", "bogusArg", "bogusValue")
        }
        assertTrue(expectedPersian.message?.contains("Unknown parameters") == true)

    }
}


