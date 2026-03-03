package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.jdkport.PrintWriter
import org.gnit.lucenekmp.jdkport.StringWriter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockGraphTokenFilter
import org.gnit.lucenekmp.tests.analysis.MockHoleInjectingTokenFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.analysis.TokenStreamToDot
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.fst.Util
import org.gnit.lucenekmp.jdkport.assert
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class TestGraphTokenizers : BaseTokenStreamTestCase() {

    // Makes a graph TokenStream from the string; separate
    // positions with single space, multiple tokens at the same
    // position with /, and add optional position length with
    // :.  EG "a b c" is a simple chain, "a/x b c" adds 'x'
    // over 'a' at position 0 with posLen=1, "a/x:3 b c" adds
    // 'x' over a with posLen=3.  Tokens are in normal-form!
    // So, offsets are computed based on the first token at a
    // given position.  NOTE: each token must be a single
    // character!  We assume this when computing offsets...

    // NOTE: all input tokens must be length 1!!!  This means
    // you cannot turn on MockCharFilter when random
    // testing...

    class GraphTokenizer : Tokenizer() {
        private var tokens: MutableList<Token>? = null
        private var upto: Int = 0
        private var inputLength: Int = 0

        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
        private val posIncrAtt: PositionIncrementAttribute =
            addAttribute(PositionIncrementAttribute::class)
        private val posLengthAtt: PositionLengthAttribute =
            addAttribute(PositionLengthAttribute::class)

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            tokens = null
            upto = 0
        }

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (tokens == null) {
                fillTokens()
            }
            // System.out.println("graphTokenizer: incr upto=" + upto + " vs " + tokens.size());
            if (upto == tokens!!.size) {
                // System.out.println("  END @ " + tokens.size());
                return false
            }
            val t = tokens!![upto++]
            // System.out.println("  return token=" + t);
            clearAttributes()
            termAtt.append(t.toString())
            offsetAtt.setOffset(t.startOffset(), t.endOffset())
            posIncrAtt.setPositionIncrement(t.getPositionIncrement())
            posLengthAtt.positionLength = t.positionLength
            return true
        }

        @Throws(IOException::class)
        override fun end() {
            super.end()
            // NOTE: somewhat... hackish, but we need this to
            // satisfy BTSTC:
            val lastOffset: Int
            if (tokens != null && tokens!!.isNotEmpty()) {
                lastOffset = tokens!![tokens!!.size - 1].endOffset()
            } else {
                lastOffset = 0
            }
            offsetAtt.setOffset(correctOffset(lastOffset), correctOffset(inputLength))
        }

        @Throws(IOException::class)
        private fun fillTokens() {
            val sb = StringBuilder()
            val buffer = CharArray(256)
            while (true) {
                val count = input.read(buffer, 0, buffer.size)
                if (count == -1) {
                    break
                }
                sb.append(buffer.concatToString(0, count))
                // System.out.println("got count=" + count);
            }
            // System.out.println("fillTokens: " + sb);

            inputLength = sb.length

            val parts = sb.toString().split(" ")

            tokens = mutableListOf()
            var pos = 0
            var maxPos = -1
            var offset = 0
            // System.out.println("again");
            for (part in parts) {
                val overlapped = part.split("/")
                var firstAtPos = true
                var minPosLength = Int.MAX_VALUE
                for (part2 in overlapped) {
                    val colonIndex = part2.indexOf(':')
                    val token: String
                    val posLength: Int
                    if (colonIndex != -1) {
                        token = part2.substring(0, colonIndex)
                        posLength = part2.substring(1 + colonIndex).toInt()
                    } else {
                        token = part2
                        posLength = 1
                    }
                    maxPos = maxOf(maxPos, pos + posLength)
                    minPosLength = minOf(minPosLength, posLength)
                    val t = Token(token, offset, offset + 2 * posLength - 1)
                    t.positionLength = posLength
                    t.setPositionIncrement(if (firstAtPos) 1 else 0)
                    firstAtPos = false
                    // System.out.println("  add token=" + t + " startOff=" + t.startOffset() + " endOff=" +
                    // t.endOffset());
                    tokens!!.add(t)
                }
                pos += minPosLength
                offset = 2 * pos
            }
            assert(maxPos <= pos) { "input string mal-formed: posLength>1 tokens hang over the end" }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMockGraphTokenFilterBasic() {

        for (iter in 0 until 10 * RANDOM_MULTIPLIER) {

            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }

            // Make new analyzer each time, because MGTF has fixed
            // seed:
            val a: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val t2: TokenStream = MockGraphTokenFilter(random(), t)
                    return TokenStreamComponents(t, t2)
                }
            }

            checkAnalysisConsistency(random(), a, false, "a b c d e f g h i j k")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMockGraphTokenFilterOnGraphInput() {
        for (iter in 0 until 100 * RANDOM_MULTIPLIER) {

            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }

            // Make new analyzer each time, because MGTF has fixed
            // seed:
            val a: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = GraphTokenizer()
                    val t2: TokenStream = MockGraphTokenFilter(random(), t)
                    return TokenStreamComponents(t, t2)
                }
            }

            checkAnalysisConsistency(random(), a, false, "a/x:3 c/y:2 d e f/z:4 g h i j k")
        }
    }

    // Just deletes (leaving hole) token 'a':
    private class RemoveATokens(`in`: TokenStream) : TokenFilter(`in`) {
        private var pendingPosInc = 0

        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val posIncAtt: PositionIncrementAttribute =
            addAttribute(PositionIncrementAttribute::class)

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            pendingPosInc = 0
        }

        @Throws(IOException::class)
        override fun end() {
            super.end()
            posIncAtt.setPositionIncrement(pendingPosInc + posIncAtt.getPositionIncrement())
        }

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            while (true) {
                val gotOne = input.incrementToken()
                if (!gotOne) {
                    return false
                } else if (termAtt.toString() == "a") {
                    pendingPosInc += posIncAtt.getPositionIncrement()
                } else {
                    posIncAtt.setPositionIncrement(pendingPosInc + posIncAtt.getPositionIncrement())
                    pendingPosInc = 0
                    return true
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMockGraphTokenFilterBeforeHoles() {
        for (iter in 0 until 100 * RANDOM_MULTIPLIER) {

            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }

            // Make new analyzer each time, because MGTF has fixed
            // seed:
            val a: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val t2: TokenStream = MockGraphTokenFilter(random(), t)
                    val t3: TokenStream = RemoveATokens(t2)
                    return TokenStreamComponents(t, t3)
                }
            }

            val random: Random = random()
            checkAnalysisConsistency(random, a, false, "a b c d e f g h i j k")
            checkAnalysisConsistency(random, a, false, "x y a b c d e f g h i j k")
            checkAnalysisConsistency(random, a, false, "a b c d e f g h i j k a")
            checkAnalysisConsistency(random, a, false, "a b c d e f g h i j k a x y")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMockGraphTokenFilterAfterHoles() {
        for (iter in 0 until 100 * RANDOM_MULTIPLIER) {

            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }

            // Make new analyzer each time, because MGTF has fixed
            // seed:
            val a: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val t2: TokenStream = RemoveATokens(t)
                    val t3: TokenStream = MockGraphTokenFilter(random(), t2)
                    return TokenStreamComponents(t, t3)
                }
            }

            val random: Random = random()
            checkAnalysisConsistency(random, a, false, "a b c d e f g h i j k")
            checkAnalysisConsistency(random, a, false, "x y a b c d e f g h i j k")
            checkAnalysisConsistency(random, a, false, "a b c d e f g h i j k a")
            checkAnalysisConsistency(random, a, false, "a b c d e f g h i j k a x y")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMockGraphTokenFilterRandom() {
        for (iter in 0 until 3 * RANDOM_MULTIPLIER) {

            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }

            // Make new analyzer each time, because MGTF has fixed
            // seed:
            val a: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val t2: TokenStream = MockGraphTokenFilter(random(), t)
                    return TokenStreamComponents(t, t2)
                }
            }

            val random: Random = random()
            checkRandomData(random, a, 5, atLeast(100))
        }
    }

    // Two MockGraphTokenFilters
    @Test
    @Throws(Exception::class)
    fun testDoubleMockGraphTokenFilterRandom() {
        for (iter in 0 until 3 * RANDOM_MULTIPLIER) {

            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }

            // Make new analyzer each time, because MGTF has fixed
            // seed:
            val a: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val t1: TokenStream = MockGraphTokenFilter(random(), t)
                    val t2: TokenStream = MockGraphTokenFilter(random(), t1)
                    return TokenStreamComponents(t, t2)
                }
            }

            val random: Random = random()
            checkRandomData(random, a, 5, atLeast(100))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMockGraphTokenFilterBeforeHolesRandom() {
        for (iter in 0 until 3 * RANDOM_MULTIPLIER) {

            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }

            // Make new analyzer each time, because MGTF has fixed
            // seed:
            val a: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val t1: TokenStream = MockGraphTokenFilter(random(), t)
                    val t2: TokenStream = MockHoleInjectingTokenFilter(random(), t1)
                    return TokenStreamComponents(t, t2)
                }
            }

            val random: Random = random()
            checkRandomData(random, a, 5, atLeast(100))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMockGraphTokenFilterAfterHolesRandom() {
        for (iter in 0 until 3 * RANDOM_MULTIPLIER) {

            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }

            // Make new analyzer each time, because MGTF has fixed
            // seed:
            val a: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val t1: TokenStream = MockHoleInjectingTokenFilter(random(), t)
                    val t2: TokenStream = MockGraphTokenFilter(random(), t1)
                    return TokenStreamComponents(t, t2)
                }
            }

            val random: Random = random()
            checkRandomData(random, a, 5, atLeast(100))
        }
    }

    companion object {
        private fun token(term: String, posInc: Int, posLength: Int): Token {
            val t = Token(term, 0, 0)
            t.setPositionIncrement(posInc)
            t.positionLength = posLength
            return t
        }

        private fun token(
            term: String,
            posInc: Int,
            posLength: Int,
            startOffset: Int,
            endOffset: Int
        ): Token {
            val t = Token(term, startOffset, endOffset)
            t.setPositionIncrement(posInc)
            t.positionLength = posLength
            return t
        }

        private val SEP_A: Automaton = Automata.makeChar(TokenStreamToAutomaton.POS_SEP)
        private val HOLE_A: Automaton = Automata.makeChar(TokenStreamToAutomaton.HOLE)
    }

    private fun join(vararg strings: String): Automaton {
        val asList: MutableList<Automaton> = mutableListOf()
        for (s in strings) {
            asList.add(s2a(s))
            asList.add(SEP_A)
        }
        asList.removeAt(asList.size - 1)
        return Operations.concatenate(asList)
    }

    private fun join(vararg asAutomaton: Automaton): Automaton {
        return Operations.concatenate(asAutomaton.toMutableList())
    }

    private fun s2a(s: String): Automaton {
        return Automata.makeString(s)
    }

    @Test
    @Throws(Exception::class)
    fun testSingleToken() {
        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("abc", 1, 1)
                )
            )
        assertSameLanguage(s2a("abc"), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleHoles() {
        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("a", 1, 1), token("b", 3, 1)
                )
            )
        assertSameLanguage(join(s2a("a"), SEP_A, HOLE_A, SEP_A, HOLE_A, SEP_A, s2a("b")), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testSynOverMultipleHoles() {
        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("a", 1, 1), token("x", 0, 3), token("b", 3, 1)
                )
            )
        val a1 = join(s2a("a"), SEP_A, HOLE_A, SEP_A, HOLE_A, SEP_A, s2a("b"))
        val a2 = join(s2a("x"), SEP_A, s2a("b"))
        assertSameLanguage(Operations.union(listOf(a1, a2)), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testTwoTokens() {
        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("abc", 1, 1), token("def", 1, 1)
                )
            )
        assertSameLanguage(join("abc", "def"), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testHole() {

        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("abc", 1, 1), token("def", 2, 1)
                )
            )
        assertSameLanguage(join(s2a("abc"), SEP_A, HOLE_A, SEP_A, s2a("def")), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testOverlappedTokensSausage() {

        // Two tokens on top of each other (sausage):
        val ts: TokenStream =
            CannedTokenStream(*arrayOf(token("abc", 1, 1), token("xyz", 0, 1)))
        val a1 = s2a("abc")
        val a2 = s2a("xyz")
        assertSameLanguage(Operations.union(listOf(a1, a2)), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testOverlappedTokensLattice() {

        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("abc", 1, 1), token("xyz", 0, 2), token("def", 1, 1)
                )
            )
        val a1 = s2a("xyz")
        val a2 = join("abc", "def")
        assertSameLanguage(Operations.union(listOf(a1, a2)), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testSynOverHole() {

        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("a", 1, 1), token("X", 0, 2), token("b", 2, 1)
                )
            )
        val a1 = Operations.union(listOf(join(s2a("a"), SEP_A, HOLE_A), s2a("X")))
        val expected = Operations.concatenate(mutableListOf(a1, join(SEP_A, s2a("b"))))
        assertSameLanguage(expected, ts)
    }

    @Test
    @Throws(Exception::class)
    fun testSynOverHole2() {

        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("xyz", 1, 1), token("abc", 0, 3), token("def", 2, 1)
                )
            )
        val expected =
            Operations.union(listOf(join(s2a("xyz"), SEP_A, HOLE_A, SEP_A, s2a("def")), s2a("abc")))
        assertSameLanguage(expected, ts)
    }

    @Test
    @Throws(Exception::class)
    fun testOverlappedTokensLattice2() {

        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("abc", 1, 1), token("xyz", 0, 3), token("def", 1, 1), token("ghi", 1, 1)
                )
            )
        val a1 = s2a("xyz")
        val a2 = join("abc", "def", "ghi")
        assertSameLanguage(Operations.union(listOf(a1, a2)), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testToDot() {
        val ts: TokenStream = CannedTokenStream(*arrayOf(token("abc", 1, 1, 0, 4)))
        val w = StringWriter()
        TokenStreamToDot("abcd", ts, PrintWriter(w)).toDot()
        assertTrue(w.toString().indexOf("abc / abcd") != -1)
    }

    @Test
    @Throws(Exception::class)
    fun testStartsWithHole() {
        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("abc", 2, 1)
                )
            )
        assertSameLanguage(join(HOLE_A, SEP_A, s2a("abc")), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testEndsWithHole() {
        val ts: TokenStream =
            CannedTokenStream(
                1,
                0,
                *arrayOf(
                    token("abc", 2, 1)
                )
            )
        assertSameLanguage(join(HOLE_A, SEP_A, s2a("abc"), SEP_A, HOLE_A), ts)
    }

    @Test
    @Throws(Exception::class)
    fun testSynHangingOverEnd() {
        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("a", 1, 1), token("X", 0, 10)
                )
            )
        assertSameLanguage(Operations.union(listOf(s2a("a"), s2a("X"))), ts)
    }

    /** Returns all paths */
    private fun toPathStrings(a: Automaton): MutableSet<String> {
        val scratchBytesRefBuilder = BytesRefBuilder()
        val paths: MutableSet<String> = mutableSetOf()
        for (ir: IntsRef in AutomatonTestUtil.getFiniteStringsRecursive(a, -1)) {
            paths.add(
                Util.toBytesRef(ir, scratchBytesRefBuilder)
                    .utf8ToString()
                    .replace(TokenStreamToAutomaton.POS_SEP.toChar(), ' ')
            )
        }
        return paths
    }

    @Throws(IOException::class)
    private fun assertSameLanguage(expected: Automaton, ts: TokenStream) {
        assertSameLanguage(expected, TokenStreamToAutomaton().toAutomaton(ts))
    }

    private fun assertSameLanguage(expected: Automaton, actual: Automaton) {
        val expectedDet =
            Operations.determinize(
                Operations.removeDeadStates(expected), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val actualDet =
            Operations.determinize(
                Operations.removeDeadStates(actual), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        if (AutomatonTestUtil.sameLanguage(expectedDet, actualDet) == false) {
            val expectedPaths = toPathStrings(expectedDet)
            val actualPaths = toPathStrings(actualDet)
            val b = StringBuilder()
            b.append("expected:\n")
            for (path in expectedPaths) {
                b.append("  ")
                b.append(path)
                if (!actualPaths.contains(path)) {
                    b.append(" [missing!]")
                }
                b.append('\n')
            }
            b.append("actual:\n")
            for (path in actualPaths) {
                b.append("  ")
                b.append(path)
                if (!expectedPaths.contains(path)) {
                    b.append(" [unexpected!]")
                }
                b.append('\n')
            }
            fail("accepted language is different:\n\n$b")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testTokenStreamGraphWithHoles() {
        val ts: TokenStream =
            CannedTokenStream(
                *arrayOf(
                    token("abc", 1, 1), token("xyz", 1, 8), token("def", 1, 1), token("ghi", 1, 1)
                )
            )
        assertSameLanguage(
            Operations.union(
                listOf(
                    join(s2a("abc"), SEP_A, s2a("xyz")),
                    join(s2a("abc"), SEP_A, HOLE_A, SEP_A, s2a("def"), SEP_A, s2a("ghi"))
                )
            ),
            ts
        )
    }
}
