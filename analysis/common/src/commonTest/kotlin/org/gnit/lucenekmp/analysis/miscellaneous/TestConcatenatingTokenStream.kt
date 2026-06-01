package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.test.Test
import kotlin.test.assertTrue

class TestConcatenatingTokenStream : BaseTokenStreamTestCase() {
    @Test
    fun testBasic() {
        val factory: AttributeFactory = newAttributeFactory()

        val first = MockTokenizer(factory, MockTokenizer.WHITESPACE, false)
        first.setReader(StringReader("first words "))
        val second = MockTokenizer(factory, MockTokenizer.WHITESPACE, false)
        second.setReader(StringReader("second words"))
        val third = MockTokenizer(factory, MockTokenizer.WHITESPACE, false)
        third.setReader(StringReader(" third words"))

        val ts: TokenStream = ConcatenatingTokenStream(first, second, EmptyTokenStream(), third)
        assertTokenStreamContents(
            ts,
            arrayOf("first", "words", "second", "words", "third", "words"),
            intArrayOf(0, 6, 12, 19, 25, 31),
            intArrayOf(5, 11, 18, 24, 30, 36)
        )

        first.setReader(StringReader("first words "))
        second.setReader(StringReader("second words"))
        third.setReader(StringReader(" third words"))
        assertTokenStreamContents(
            ts,
            arrayOf("first", "words", "second", "words", "third", "words"),
            intArrayOf(0, 6, 12, 19, 25, 31),
            intArrayOf(5, 11, 18, 24, 30, 36),
            null,
            intArrayOf(1, 1, 1, 1, 1, 1)
        )
    }

    @Test
    fun testOffsetGaps() {
        val cts1 = CannedTokenStream(2, 10, Token("a", 0, 1), Token("b", 2, 3))
        val cts2 = CannedTokenStream(2, 10, Token("c", 0, 1), Token("d", 2, 3))

        val ts: TokenStream = ConcatenatingTokenStream(cts1, cts2)
        BaseTokenStreamTestCase.assertTokenStreamContents(
            ts = ts,
            output = arrayOf("a", "b", "c", "d"),
            startOffsets = intArrayOf(0, 2, 10, 12),
            endOffsets = intArrayOf(1, 3, 11, 13),
            types = null,
            posIncrements = intArrayOf(1, 1, 3, 1),
            posLengths = null,
            finalOffset = 20,
            finalPosInc = 2,
            keywordAtts = null,
            graphOffsetsAreCorrect = false,
            payloads = null,
            flags = null,
            boost = null
        )
    }

    @Test
    fun testInconsistentAttributes() {
        val factory: AttributeFactory = newAttributeFactory()

        val first = MockTokenizer(factory, MockTokenizer.WHITESPACE, false)
        first.setReader(StringReader("first words "))
        first.addAttribute(PayloadAttribute::class)
        val second = MockTokenizer(factory, MockTokenizer.WHITESPACE, false)
        second.setReader(StringReader("second words"))
        second.addAttribute(FlagsAttribute::class)

        val ts: TokenStream = ConcatenatingTokenStream(first, second)
        assertTrue(ts.hasAttribute(FlagsAttribute::class))
        assertTrue(ts.hasAttribute(PayloadAttribute::class))

        assertTokenStreamContents(
            ts,
            arrayOf("first", "words", "second", "words"),
            intArrayOf(0, 6, 12, 19),
            intArrayOf(5, 11, 18, 24)
        )
    }

    @Test
    fun testInconsistentAttributeFactories() {
        val first = MockTokenizer(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY, MockTokenizer.WHITESPACE, true)
        val second = MockTokenizer(TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY, MockTokenizer.WHITESPACE, true)

        expectThrows(IllegalArgumentException::class) {
            ConcatenatingTokenStream(first, second)
        }
    }
}
