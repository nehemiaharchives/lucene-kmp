package org.gnit.lucenekmp.analysis.util

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** Simple tests to ensure the French elision filter factory is working. */
class TestElisionFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Ensure the filter actually normalizes text. */
    @Test
    @Throws(Exception::class)
    fun testElision() {
        val reader: Reader = StringReader("l'avion")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("Elision", "articles", "frenchArticles.txt").create(stream)
        assertTokenStreamContents(stream, arrayOf("avion"))
    }

    /** Test creating an elision filter without specifying any articles */
    @Test
    @Throws(Exception::class)
    fun testDefaultArticles() {
        val reader: Reader = StringReader("l'avion")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("Elision").create(stream)
        assertTokenStreamContents(stream, arrayOf("avion"))
    }

    /** Test setting ignoreCase=true */
    @Test
    @Throws(Exception::class)
    fun testCaseInsensitive() {
        val reader: Reader = StringReader("L'avion")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream =
            tokenFilterFactory("Elision", "articles", "frenchArticles.txt", "ignoreCase", "true")
                .create(stream)
        assertTokenStreamContents(stream, arrayOf("avion"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("Elision", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
