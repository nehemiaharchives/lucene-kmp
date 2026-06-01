package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests to ensure the keyword marker filter factory is working. */
class TestKeywordMarkerFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testKeywords() {
        val reader = StringReader("dogs cats")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "KeywordMarker",
                Version.LATEST,
                StringMockResourceLoader("cats"),
                "protected",
                "protwords.txt"
            ).create(stream)
        stream = tokenFilterFactory("PorterStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("dog", "cats"))
    }

    @Test
    fun testKeywords2() {
        val reader = StringReader("dogs cats")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("KeywordMarker", "pattern", "cats|Dogs").create(stream)
        stream = tokenFilterFactory("PorterStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("dog", "cats"))
    }

    @Test
    fun testKeywordsMixed() {
        val reader = StringReader("dogs cats birds")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "KeywordMarker",
                Version.LATEST,
                StringMockResourceLoader("cats"),
                "protected",
                "protwords.txt",
                "pattern",
                "birds|Dogs"
            ).create(stream)
        stream = tokenFilterFactory("PorterStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("dog", "cats", "birds"))
    }

    @Test
    fun testKeywordsCaseInsensitive() {
        val reader = StringReader("dogs cats Cats")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "KeywordMarker",
                Version.LATEST,
                StringMockResourceLoader("cats"),
                "protected",
                "protwords.txt",
                "ignoreCase",
                "true"
            ).create(stream)
        stream = tokenFilterFactory("PorterStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("dog", "cats", "Cats"))
    }

    @Test
    fun testKeywordsCaseInsensitive2() {
        val reader = StringReader("dogs cats Cats")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("KeywordMarker", "pattern", "Cats", "ignoreCase", "true").create(stream)
        stream = tokenFilterFactory("PorterStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("dog", "cats", "Cats"))
    }

    @Test
    fun testKeywordsCaseInsensitiveMixed() {
        val reader = StringReader("dogs cats Cats Birds birds")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "KeywordMarker",
                Version.LATEST,
                StringMockResourceLoader("cats"),
                "protected",
                "protwords.txt",
                "pattern",
                "birds",
                "ignoreCase",
                "true"
            ).create(stream)
        stream = tokenFilterFactory("PorterStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("dog", "cats", "Cats", "Birds", "birds"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("KeywordMarker", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
