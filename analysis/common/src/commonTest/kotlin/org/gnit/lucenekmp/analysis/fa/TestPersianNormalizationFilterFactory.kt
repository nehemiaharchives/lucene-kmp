package org.gnit.lucenekmp.analysis.fa

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Persian normalization factory is working. */
class TestPersianNormalizationFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Ensure the filter actually normalizes persian text. */
    @Test
    @Throws(Exception::class)
    fun testNormalization() {
        val reader: Reader = StringReader("های")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("PersianNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("هاي"))
    }

    /** Test PersianStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("کتابها بهترین دوستان")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("PersianNormalization").create(stream)
        stream = tokenFilterFactory("PersianStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("كتاب", "به", "دوست"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("PersianNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedNorm.message?.contains("Unknown parameters") == true)

        val expectedStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("PersianStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedStem.message?.contains("Unknown parameters") == true)
    }
}

