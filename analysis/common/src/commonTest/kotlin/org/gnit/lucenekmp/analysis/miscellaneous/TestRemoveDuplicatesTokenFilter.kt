package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.synonym.SynonymGraphFilter
import org.gnit.lucenekmp.analysis.synonym.SynonymMap
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.CharsRef
import kotlin.test.Test

class TestRemoveDuplicatesTokenFilter : BaseTokenStreamTestCase() {
    companion object {
        fun tok(pos: Int, t: String, start: Int, end: Int): Token {
            return Token(t, start, end).apply {
                setPositionIncrement(pos)
            }
        }

        fun tok(pos: Int, t: String): Token {
            return tok(pos, t, 0, 0)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDups() {
        testDups(
            "A B B C D E",
            tok(1, "A", 0, 4),
            tok(1, "B", 5, 10),
            tok(1, "B", 11, 15),
            tok(1, "C", 16, 20),
            tok(0, "D", 16, 20),
            tok(1, "E", 21, 25)
        )
        testDups(
            "A B C D E",
            tok(1, "A", 0, 4),
            tok(1, "B", 5, 10),
            tok(0, "B", 11, 15),
            tok(1, "C", 16, 20),
            tok(0, "D", 16, 20),
            tok(1, "E", 21, 25)
        )
        testDups(
            "A B C D E F G H I J K",
            tok(1, "A"),
            tok(1, "B"),
            tok(0, "B"),
            tok(1, "C"),
            tok(1, "D"),
            tok(0, "D"),
            tok(0, "D"),
            tok(1, "E"),
            tok(1, "F"),
            tok(0, "F"),
            tok(1, "G"),
            tok(0, "H"),
            tok(0, "H"),
            tok(1, "I"),
            tok(1, "J"),
            tok(0, "K"),
            tok(0, "J")
        )
    }

    @Throws(Exception::class)
    private fun testDups(expected: String, vararg tokens: Token) {
        val toks = tokens.iterator()
        val ts: TokenStream = RemoveDuplicatesTokenFilter(
            object : TokenStream() {
                val termAtt = addAttribute(CharTermAttribute::class)
                val offsetAtt = addAttribute(OffsetAttribute::class)
                val posIncAtt = addAttribute(PositionIncrementAttribute::class)

                override fun incrementToken(): Boolean {
                    return if (toks.hasNext()) {
                        clearAttributes()
                        val tok = toks.next()
                        termAtt.setEmpty()!!.append(tok)
                        offsetAtt.setOffset(tok.startOffset(), tok.endOffset())
                        posIncAtt.setPositionIncrement(tok.getPositionIncrement())
                        true
                    } else {
                        false
                    }
                }
            }
        )

        assertTokenStreamContents(ts, expected.split("\\s".toRegex()).toTypedArray())
    }

    // some helper methods for the below test with synonyms
    private fun randomNonEmptyString(): String {
        while (true) {
            val s = TestUtil.randomUnicodeString(random()).trim()
            if (s.isNotEmpty() && s.indexOf('\u0000') == -1) {
                return s
            }
        }
    }

    private fun add(b: SynonymMap.Builder, input: String, output: String, keepOrig: Boolean) {
        b.add(
            CharsRef(input.replace(" +".toRegex(), "\u0000")),
            CharsRef(output.replace(" +".toRegex(), "\u0000")),
            keepOrig
        )
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val numIters = atLeast(3)
        repeat(numIters) {
            val b = SynonymMap.Builder(random().nextBoolean())
            val numEntries = atLeast(10)
            repeat(numEntries) {
                add(b, randomNonEmptyString(), randomNonEmptyString(), random().nextBoolean())
            }
            val map = b.build()
            val ignoreCase = random().nextBoolean()

            val analyzer: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.SIMPLE, true)
                    val stream: TokenStream = SynonymGraphFilter(tokenizer, map, ignoreCase)
                    return TokenStreamComponents(tokenizer, RemoveDuplicatesTokenFilter(stream))
                }
            }

            checkRandomData(random(), analyzer, 200)
            analyzer.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, RemoveDuplicatesTokenFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
