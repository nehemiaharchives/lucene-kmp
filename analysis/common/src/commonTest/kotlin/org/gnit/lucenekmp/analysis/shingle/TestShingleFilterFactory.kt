package org.gnit.lucenekmp.analysis.shingle

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests to ensure the Shingle filter factory works. */
class TestShingleFilterFactory : BaseTokenStreamFactoryTestCase() {
    /** Test the defaults */
    @Test
    @Throws(Exception::class)
    fun testDefaults() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Shingle").create(stream)
        assertTokenStreamContents(stream, arrayOf("this", "this is", "is", "is a", "a", "a test", "test"))
    }

    /** Test with unigrams disabled */
    @Test
    @Throws(Exception::class)
    fun testNoUnigrams() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Shingle", "outputUnigrams", "false").create(stream)
        assertTokenStreamContents(stream, arrayOf("this is", "is a", "a test"))
    }

    /** Test with a higher max shingle size */
    @Test
    @Throws(Exception::class)
    fun testMaxShingleSize() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Shingle", "maxShingleSize", "3").create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf("this", "this is", "this is a", "is", "is a", "is a test", "a", "a test", "test")
        )
    }

    /** Test with higher min (and max) shingle size */
    @Test
    @Throws(Exception::class)
    fun testMinShingleSize() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Shingle", "minShingleSize", "3", "maxShingleSize", "4").create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf("this", "this is a", "this is a test", "is", "is a test", "a", "test")
        )
    }

    /** Test with higher min (and max) shingle size and with unigrams disabled */
    @Test
    @Throws(Exception::class)
    fun testMinShingleSizeNoUnigrams() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory(
            "Shingle", "minShingleSize", "3", "maxShingleSize", "4", "outputUnigrams", "false"
        ).create(stream)
        assertTokenStreamContents(stream, arrayOf("this is a", "this is a test", "is a test"))
    }

    /** Test with higher same min and max shingle size */
    @Test
    @Throws(Exception::class)
    fun testEqualMinAndMaxShingleSize() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Shingle", "minShingleSize", "3", "maxShingleSize", "3").create(stream)
        assertTokenStreamContents(stream, arrayOf("this", "this is a", "is", "is a test", "a", "test"))
    }

    /** Test with higher same min and max shingle size and with unigrams disabled */
    @Test
    @Throws(Exception::class)
    fun testEqualMinAndMaxShingleSizeNoUnigrams() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory(
            "Shingle", "minShingleSize", "3", "maxShingleSize", "3", "outputUnigrams", "false"
        ).create(stream)
        assertTokenStreamContents(stream, arrayOf("this is a", "is a test"))
    }

    /** Test with a non-default token separator */
    @Test
    @Throws(Exception::class)
    fun testTokenSeparator() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Shingle", "tokenSeparator", "=BLAH=").create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf("this", "this=BLAH=is", "is", "is=BLAH=a", "a", "a=BLAH=test", "test")
        )
    }

    /** Test with a non-default token separator and with unigrams disabled */
    @Test
    @Throws(Exception::class)
    fun testTokenSeparatorNoUnigrams() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Shingle", "tokenSeparator", "=BLAH=", "outputUnigrams", "false").create(stream)
        assertTokenStreamContents(stream, arrayOf("this=BLAH=is", "is=BLAH=a", "a=BLAH=test"))
    }

    /** Test with an empty token separator */
    @Test
    @Throws(Exception::class)
    fun testEmptyTokenSeparator() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Shingle", "tokenSeparator", "").create(stream)
        assertTokenStreamContents(stream, arrayOf("this", "thisis", "is", "isa", "a", "atest", "test"))
    }

    /** Test with higher min (and max) shingle size and with a non-default token separator */
    @Test
    @Throws(Exception::class)
    fun testMinShingleSizeAndTokenSeparator() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory(
            "Shingle", "minShingleSize", "3", "maxShingleSize", "4", "tokenSeparator", "=BLAH="
        ).create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf(
                "this",
                "this=BLAH=is=BLAH=a",
                "this=BLAH=is=BLAH=a=BLAH=test",
                "is",
                "is=BLAH=a=BLAH=test",
                "a",
                "test"
            )
        )
    }

    /**
     * Test with higher min (and max) shingle size and with a non-default token separator and with
     * unigrams disabled
     */
    @Test
    @Throws(Exception::class)
    fun testMinShingleSizeAndTokenSeparatorNoUnigrams() {
        val reader: Reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory(
            "Shingle",
            "minShingleSize", "3",
            "maxShingleSize", "4",
            "tokenSeparator", "=BLAH=",
            "outputUnigrams", "false"
        ).create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf(
                "this=BLAH=is=BLAH=a", "this=BLAH=is=BLAH=a=BLAH=test", "is=BLAH=a=BLAH=test"
            )
        )
    }

    /**
     * Test with unigrams disabled except when there are no shingles, with a single input token. Using
     * default min/max shingle sizes: 2/2. No shingles will be created, since there are fewer input
     * tokens than min shingle size. However, because outputUnigramsIfNoShingles is set to true, even
     * though outputUnigrams is set to false, one unigram should be output.
     */
    @Test
    @Throws(Exception::class)
    fun testOutputUnigramsIfNoShingles() {
        val reader: Reader = StringReader("test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory(
            "Shingle", "outputUnigrams", "false", "outputUnigramsIfNoShingles", "true"
        ).create(stream)
        assertTokenStreamContents(stream, arrayOf("test"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("Shingle", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
