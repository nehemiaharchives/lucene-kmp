package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests to ensure this factory is working */
class TestRemoveDuplicatesTokenFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

        fun tok(pos: Int, t: String, start: Int, end: Int): Token {
        val tok = Token(t, start, end)
        tok.setPositionIncrement(pos)
        return tok
    }

    fun testDups(expected: String, vararg tokens: Token) {
        var stream: TokenStream = CannedTokenStream(*tokens)
        stream = tokenFilterFactory("RemoveDuplicates").create(stream)
        assertTokenStreamContents(stream, expected.split("\\s".toRegex()).toTypedArray())
    }

    @Test
    fun testSimpleDups() {
        testDups(
            "A B C D E",
            tok(1, "A", 0, 4),
            tok(1, "B", 5, 10),
            tok(0, "B", 11, 15),
            tok(1, "C", 16, 20),
            tok(0, "D", 16, 20),
            tok(1, "E", 21, 25)
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("RemoveDuplicates", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
