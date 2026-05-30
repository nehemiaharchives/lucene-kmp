package org.gnit.lucenekmp.analysis.charfilter

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure this factory is working */
class TestHTMLStripCharFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNothingChanged() {
        //                             11111111112
        //                   012345678901234567890
        val text = "this is only a test."
        val cs: Reader =
            charFilterFactory("HTMLStrip", "escapedTags", "a, Title").create(StringReader(text))
        val ts: TokenStream = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(
            ts,
            arrayOf("this", "is", "only", "a", "test."),
            intArrayOf(0, 5, 8, 13, 15),
            intArrayOf(4, 7, 12, 14, 20)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNoEscapedTags() {
        //                             11111111112222222222333333333344
        //                   012345678901234567890123456789012345678901
        val text = "<u>this</u> is <b>only</b> a <I>test</I>."
        val cs: Reader = charFilterFactory("HTMLStrip").create(StringReader(text))
        val ts: TokenStream = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(
            ts,
            arrayOf("this", "is", "only", "a", "test."),
            intArrayOf(3, 12, 18, 27, 32),
            intArrayOf(11, 14, 26, 28, 41)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testEscapedTags() {
        //                             11111111112222222222333333333344
        //                   012345678901234567890123456789012345678901
        val text = "<u>this</u> is <b>only</b> a <I>test</I>."
        val cs: Reader = charFilterFactory("HTMLStrip", "escapedTags", "U i").create(StringReader(text))
        val ts: TokenStream = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(
            ts,
            arrayOf("<u>this</u>", "is", "only", "a", "<I>test</I>."),
            intArrayOf(0, 12, 18, 27, 29),
            intArrayOf(11, 14, 26, 28, 41)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testSeparatorOnlyEscapedTags() {
        //                             11111111112222222222333333333344
        //                   012345678901234567890123456789012345678901
        val text = "<u>this</u> is <b>only</b> a <I>test</I>."
        val cs: Reader =
            charFilterFactory("HTMLStrip", "escapedTags", ",, , ").create(StringReader(text))
        val ts: TokenStream = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(
            ts,
            arrayOf("this", "is", "only", "a", "test."),
            intArrayOf(3, 12, 18, 27, 32),
            intArrayOf(11, 14, 26, 28, 41)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyEscapedTags() {
        //                             11111111112222222222333333333344
        //                   012345678901234567890123456789012345678901
        val text = "<u>this</u> is <b>only</b> a <I>test</I>."
        val cs: Reader =
            charFilterFactory("HTMLStrip", "escapedTags", "").create(StringReader(text))
        val ts: TokenStream = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(
            ts,
            arrayOf("this", "is", "only", "a", "test."),
            intArrayOf(3, 12, 18, 27, 32),
            intArrayOf(11, 14, 26, 28, 41)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testSingleEscapedTag() {
        //                             11111111112222222222333333333344
        //                   012345678901234567890123456789012345678901
        val text = "<u>this</u> is <b>only</b> a <I>test</I>."
        val cs: Reader =
            charFilterFactory("HTMLStrip", "escapedTags", ", B\r\n\t").create(StringReader(text))
        val ts: TokenStream = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(
            ts,
            arrayOf("this", "is", "<b>only</b>", "a", "test."),
            intArrayOf(3, 12, 15, 27, 32),
            intArrayOf(11, 14, 26, 28, 41)
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            charFilterFactory("HTMLStrip", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
