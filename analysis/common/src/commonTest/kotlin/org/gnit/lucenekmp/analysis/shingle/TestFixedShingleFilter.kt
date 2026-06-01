package org.gnit.lucenekmp.analysis.shingle

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.util.graph.GraphTokenStreamFiniteStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestFixedShingleFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testBiGramFilter() {
        val ts: TokenStream =
            CannedTokenStream(
                Token("please", 0, 6),
                Token("divide", 7, 13),
                Token("this", 14, 18),
                Token("sentence", 19, 27),
                Token("into", 28, 32),
                Token("shingles", 33, 41)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 2),
            arrayOf("please divide", "divide this", "this sentence", "sentence into", "into shingles"),
            intArrayOf(0, 7, 14, 19, 28),
            intArrayOf(13, 18, 27, 32, 41),
            arrayOf("shingle", "shingle", "shingle", "shingle", "shingle"),
            intArrayOf(1, 1, 1, 1, 1),
            intArrayOf(1, 1, 1, 1, 1),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithAltSeparator() {
        val ts: TokenStream =
            CannedTokenStream(
                Token("please", 0, 6),
                Token("divide", 7, 13),
                Token("this", 14, 18),
                Token("sentence", 19, 27),
                Token("into", 28, 32),
                Token("shingles", 33, 41)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 2, "<SEP>", "_"),
            arrayOf(
                "please<SEP>divide",
                "divide<SEP>this",
                "this<SEP>sentence",
                "sentence<SEP>into",
                "into<SEP>shingles"
            ),
            intArrayOf(0, 7, 14, 19, 28),
            intArrayOf(13, 18, 27, 32, 41),
            arrayOf("shingle", "shingle", "shingle", "shingle", "shingle"),
            intArrayOf(1, 1, 1, 1, 1),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilter() {
        val ts: TokenStream =
            CannedTokenStream(
                Token("please", 0, 6),
                Token("divide", 7, 13),
                Token("this", 14, 18),
                Token("sentence", 19, 27),
                Token("into", 28, 32),
                Token("shingles", 33, 41)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 3),
            arrayOf(
                "please divide this",
                "divide this sentence",
                "this sentence into",
                "sentence into shingles"
            )
        )
    }

    @Test
    @Throws(IOException::class)
    fun testShingleSizeGreaterThanTokenstreamLength() {
        val ts: TokenStream =
            FixedShingleFilter(
                CannedTokenStream(Token("please", 0, 6), Token("divide", 7, 13)),
                3
            )

        ts.reset()
        assertFalse(ts.incrementToken())
    }

    @Test
    @Throws(IOException::class)
    fun testWithStopwords() {
        val ts: TokenStream =
            CannedTokenStream(
                Token("please", 0, 6),
                Token("divide", 7, 13),
                Token("sentence", 2, 19, 27),
                Token("shingles", 2, 33, 41)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 3),
            arrayOf("please divide _", "divide _ sentence", "sentence _ shingles"),
            intArrayOf(0, 7, 19),
            intArrayOf(13, 27, 41),
            arrayOf("shingle", "shingle", "shingle"),
            intArrayOf(1, 1, 2),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testConsecutiveStopwords() {
        val ts: TokenStream =
            CannedTokenStream(
                Token("b", 2, 2, 3),
                Token("c", 4, 5),
                Token("d", 6, 7),
                Token("b", 3, 12, 13),
                Token("c", 14, 15)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 4),
            arrayOf("b c d _", "c d _ _", "d _ _ b"),
            intArrayOf(2, 4, 6),
            intArrayOf(7, 7, 13),
            intArrayOf(2, 1, 1),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTrailingStopwords() {
        val ts: TokenStream =
            CannedTokenStream(
                1, 7,
                Token("b", 0, 1),
                Token("c", 2, 3),
                Token("d", 4, 5)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 3),
            arrayOf("b c d", "c d _"),
            intArrayOf(0, 2),
            intArrayOf(5, 5),
            intArrayOf(1, 1),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testMultipleTrailingStopwords() {
        val ts: TokenStream =
            CannedTokenStream(
                2, 9,
                Token("b", 0, 1),
                Token("c", 2, 3),
                Token("d", 4, 5)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 3),
            arrayOf("b c d", "c d _", "d _ _"),
            intArrayOf(0, 2, 4),
            intArrayOf(5, 5, 5),
            intArrayOf(1, 1, 1),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testIncomingGraphs() {
        val ts: TokenStream =
            CannedTokenStream(
                Token("b", 0, 1),
                Token("a", 0, 0, 1),
                Token("c", 2, 3),
                Token("b", 4, 5),
                Token("a", 0, 4, 5),
                Token("d", 6, 7)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 2),
            arrayOf("b c", "a c", "c b", "c a", "b d", "a d"),
            intArrayOf(0, 0, 2, 2, 4, 4),
            intArrayOf(3, 3, 5, 5, 7, 7),
            intArrayOf(1, 0, 1, 0, 1, 0),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testShinglesSpanningGraphs() {
        val ts: TokenStream =
            CannedTokenStream(
                Token("b", 0, 1),
                Token("a", 0, 0, 1),
                Token("c", 2, 3),
                Token("b", 4, 5),
                Token("a", 0, 4, 5),
                Token("d", 6, 7)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 3),
            arrayOf("b c b", "b c a", "a c b", "a c a", "c b d", "c a d"),
            intArrayOf(0, 0, 0, 0, 2, 2),
            intArrayOf(5, 5, 5, 5, 7, 7),
            intArrayOf(1, 0, 0, 0, 1, 0),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTrailingGraphsOfDifferingLengths() {
        val ts: TokenStream =
            CannedTokenStream(
                Token("a", 0, 1),
                Token("b", 1, 2, 3, 3),
                Token("c", 0, 2, 3),
                Token("d", 2, 3),
                Token("e", 2, 3),
                Token("f", 4, 5)
            )

        assertTokenStreamContents(
            FixedShingleFilter(ts, 3),
            arrayOf("a b f", "a c d", "c d e", "d e f")
        )
    }

    @Test
    fun testParameterLimits() {
        val e =
            expectThrows(IllegalArgumentException::class) {
                FixedShingleFilter(CannedTokenStream(), 1)
            }
        assertEquals("Shingle size must be between 2 and 4, got 1", e.message)
        val e2 =
            expectThrows(IllegalArgumentException::class) {
                FixedShingleFilter(CannedTokenStream(), 5)
            }
        assertEquals("Shingle size must be between 2 and 4, got 5", e2.message)
    }

    @Test
    @Throws(IOException::class)
    fun testWithGraphInput() {
        val ts: TokenStream =
            CannedTokenStream(
                Token("fuz", 0, 3),
                Token("foo", 1, 4, 6, 2),
                Token("bar", 0, 4, 6),
                Token("baz", 1, 4, 6)
            )
        val graph = GraphTokenStreamFiniteStrings(ts)
        val it = graph.finiteStrings.iterator()
        assertTokenStreamContents(FixedShingleFilter(it.next(), 2), arrayOf("fuz foo"))
        assertTokenStreamContents(FixedShingleFilter(it.next(), 2), arrayOf("fuz bar", "bar baz"))
    }
}
