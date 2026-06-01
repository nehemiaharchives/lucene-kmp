package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test

/** Test that this filter removes tokens that match a particular set of flags. */
class TestDropIfFlaggedFilter : BaseTokenStreamTestCase() {
    /** Test the straight forward cases. When all flags match the token should be dropped */
    @Test
    @Throws(Exception::class)
    fun testDropped() {
        val token = Token("foo", 0, 2)
        val token2 = Token("bar", 4, 6)
        val token3 = Token("baz", 8, 10)
        val token4 = Token("bam", 12, 14)

        token.flags = 0 // 000 no flags match
        token2.flags = 1 // 001 one flag matches
        token3.flags = 2 // 010 no flags match
        token4.flags = 7 // 111 both flags match (drop)

        var ts: TokenStream = CannedTokenStream(token, token2, token3, token4)
        ts = DropIfFlaggedFilter(ts, 5) // 101

        assertTokenStreamContents(
            ts,
            arrayOf("foo", "bar", "baz"),
            intArrayOf(0, 4, 8),
            intArrayOf(2, 6, 10),
            posIncrements = intArrayOf(1, 1, 1)
        )
    }

    /** Test where the first and last token are dropped. */
    @Test
    @Throws(Exception::class)
    fun testDroppedFirst() {
        val token = Token("foo", 0, 2)
        val token2 = Token("bar", 4, 6)
        val token3 = Token("baz", 8, 10)
        val token4 = Token("bam", 12, 14)

        token.flags = 4 // 100 flag matches (drop)
        token2.flags = 1 // 001 no flags match
        token3.flags = 2 // 010 no flags match
        token4.flags = 7 // 111 flag matches (drop)

        var ts: TokenStream = CannedTokenStream(token, token2, token3, token4)
        ts = DropIfFlaggedFilter(ts, 4)

        assertTokenStreamContents(
            ts,
            arrayOf("bar", "baz"),
            intArrayOf(4, 8),
            intArrayOf(6, 10),
            posIncrements = intArrayOf(2, 1)
        )
    }
}
