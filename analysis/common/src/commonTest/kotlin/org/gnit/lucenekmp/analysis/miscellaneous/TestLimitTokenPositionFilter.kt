package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.analysis.synonym.SynonymFilter
import org.gnit.lucenekmp.analysis.synonym.SynonymMap
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.CharsRefBuilder
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TestLimitTokenPositionFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testMaxPosition2() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val a: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    // if we are consuming all tokens, we can use the checks, otherwise we can't
                    tokenizer.enableChecks = consumeAll
                    return TokenStreamComponents(
                        tokenizer,
                        LimitTokenPositionFilter(tokenizer, 2, consumeAll)
                    )
                }
            }

            // don't use assertAnalyzesTo here, as the end offset is not the end of the string (unless
            // consumeAll is true, in which case it's correct)!
            assertTokenStreamContents(
                a.tokenStream("dummy", "1  2     3  4  5"),
                arrayOf("1", "2"),
                intArrayOf(0, 3),
                intArrayOf(1, 4),
                finalOffset = if (consumeAll) 16 else null
            )
            assertTokenStreamContents(
                a.tokenStream("dummy", StringReader("1 2 3 4 5")),
                arrayOf("1", "2"),
                intArrayOf(0, 2),
                intArrayOf(1, 3),
                finalOffset = if (consumeAll) 9 else null
            )

            // less than the limit, ensure we behave correctly
            assertTokenStreamContents(
                a.tokenStream("dummy", "1  "),
                arrayOf("1"),
                intArrayOf(0),
                intArrayOf(1),
                finalOffset = if (consumeAll) 3 else null
            )

            // equal to limit
            assertTokenStreamContents(
                a.tokenStream("dummy", "1  2  "),
                arrayOf("1", "2"),
                intArrayOf(0, 3),
                intArrayOf(1, 4),
                finalOffset = if (consumeAll) 6 else null
            )
            a.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testMaxPosition3WithSynomyms() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val tokenizer = whitespaceMockTokenizer("one two three four five")
            // if we are consuming all tokens, we can use the checks, otherwise we can't
            tokenizer.enableChecks = consumeAll

            val builder = SynonymMap.Builder(true)
            builder.add(CharsRef("one"), CharsRef("first"), true)
            builder.add(CharsRef("one"), CharsRef("alpha"), true)
            builder.add(CharsRef("one"), CharsRef("beguine"), true)
            val multiWordCharsRef = CharsRefBuilder()
            SynonymMap.Builder.join(arrayOf("and", "indubitably", "single", "only"), multiWordCharsRef)
            builder.add(CharsRef("one"), multiWordCharsRef.get(), true)
            SynonymMap.Builder.join(arrayOf("dopple", "ganger"), multiWordCharsRef)
            builder.add(CharsRef("two"), multiWordCharsRef.get(), true)
            val synonymMap = builder.build()
            var stream: TokenStream = SynonymFilter(tokenizer, synonymMap, true)
            stream = LimitTokenPositionFilter(stream, 3, consumeAll)

            // "only", the 4th word of multi-word synonym "and indubitably single only" is not emitted,
            // since its position is greater than 3.
            assertTokenStreamContents(
                stream,
                arrayOf(
                    "one",
                    "first",
                    "alpha",
                    "beguine",
                    "and",
                    "two",
                    "indubitably",
                    "dopple",
                    "three",
                    "single",
                    "ganger"
                ),
                intArrayOf(1, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0)
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIllegalArguments() {
        assertFailsWith<IllegalArgumentException> {
            LimitTokenPositionFilter(whitespaceMockTokenizer("one two three four five"), 0)
        }
    }
}
