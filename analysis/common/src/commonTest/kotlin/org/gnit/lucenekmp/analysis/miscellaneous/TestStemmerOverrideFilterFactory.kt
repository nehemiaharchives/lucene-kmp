package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests to ensure the stemmer override filter factory is working. */
class TestStemmerOverrideFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testKeywords() {
        val reader = StringReader("testing dogs")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "StemmerOverride",
                Version.LATEST,
                StringMockResourceLoader("dogs\tcat"),
                "dictionary",
                "stemdict.txt"
            ).create(stream)
        stream = tokenFilterFactory("PorterStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("test", "cat"))
    }

    @Test
    fun testKeywordsCaseInsensitive() {
        val reader = StringReader("testing DoGs")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "StemmerOverride",
                Version.LATEST,
                StringMockResourceLoader("dogs\tcat"),
                "dictionary",
                "stemdict.txt",
                "ignoreCase",
                "true"
            ).create(stream)
        stream = tokenFilterFactory("PorterStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("test", "cat"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("StemmerOverride", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
