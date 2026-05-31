package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.AutomatonToTokenStream
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.TokenStreamToAutomaton
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.synonym.SynonymGraphFilter
import org.gnit.lucenekmp.analysis.synonym.SynonymMap
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.Transition
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestFlattenGraphFilter : BaseTokenStreamTestCase() {
    private fun token(term: String, posInc: Int, posLength: Int, startOffset: Int, endOffset: Int): Token {
        val t = Token(term, startOffset, endOffset)
        t.setPositionIncrement(posInc)
        t.positionLength = posLength
        return t
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleMock() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.SIMPLE, true)
                val ts: TokenStream = FlattenGraphFilter(tokenizer)
                return TokenStreamComponents(tokenizer, ts)
            }
        }

        assertAnalyzesTo(
            a,
            "wtf happened",
            arrayOf("wtf", "happened"),
            intArrayOf(0, 4),
            intArrayOf(3, 12),
            arrayOf("word", "word"),
            intArrayOf(1, 1)
        )
    }

    @Test
    fun testAlreadyFlatten() {
        val input = CannedTokenStream(
            0,
            12,
            token("wtf", 1, 1, 0, 3),
            token("what", 0, 1, 0, 3),
            token("wow", 0, 1, 0, 3),
            token("the", 1, 1, 0, 3),
            token("that's", 0, 1, 0, 3),
            token("fudge", 1, 1, 0, 3),
            token("funny", 0, 1, 0, 3),
            token("happened", 1, 1, 4, 12)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("wtf", "what", "wow", "the", "that's", "fudge", "funny", "happened"),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 4),
            intArrayOf(3, 3, 3, 3, 3, 3, 3, 12),
            intArrayOf(1, 0, 0, 1, 0, 1, 0, 1),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1),
            12
        )
    }

    @Test
    fun testWTF1() {
        val input = CannedTokenStream(
            0,
            12,
            token("wtf", 1, 5, 0, 3),
            token("what", 0, 1, 0, 3),
            token("wow", 0, 3, 0, 3),
            token("the", 1, 1, 0, 3),
            token("fudge", 1, 3, 0, 3),
            token("that's", 1, 1, 0, 3),
            token("funny", 1, 1, 0, 3),
            token("happened", 1, 1, 4, 12)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("wtf", "what", "wow", "the", "that's", "fudge", "funny", "happened"),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 4),
            intArrayOf(3, 3, 3, 3, 3, 3, 3, 12),
            intArrayOf(1, 0, 0, 1, 0, 1, 0, 1),
            intArrayOf(3, 1, 1, 1, 1, 1, 1, 1),
            12
        )
    }

    @Test
    fun testWTF2() {
        val input = CannedTokenStream(
            0,
            12,
            token("what", 1, 1, 0, 3),
            token("wow", 0, 3, 0, 3),
            token("wtf", 0, 5, 0, 3),
            token("the", 1, 1, 0, 3),
            token("fudge", 1, 3, 0, 3),
            token("that's", 1, 1, 0, 3),
            token("funny", 1, 1, 0, 3),
            token("happened", 1, 1, 4, 12)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("what", "wow", "wtf", "the", "that's", "fudge", "funny", "happened"),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 4),
            intArrayOf(3, 3, 3, 3, 3, 3, 3, 12),
            intArrayOf(1, 0, 0, 1, 0, 1, 0, 1),
            intArrayOf(1, 1, 3, 1, 1, 1, 1, 1),
            12
        )
    }

    @Test
    fun testNonGreedySynonyms() {
        val input = CannedTokenStream(
            0,
            20,
            token("wizard", 1, 1, 0, 6),
            token("wizard_of_oz", 0, 3, 0, 12),
            token("of", 1, 1, 7, 9),
            token("oz", 1, 1, 10, 12),
            token("oz_screams", 0, 2, 10, 20),
            token("screams", 1, 1, 13, 20)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("wizard", "wizard_of_oz", "of", "oz", "oz_screams", "screams"),
            intArrayOf(0, 0, 7, 10, 10, 13),
            intArrayOf(6, 12, 9, 12, 20, 20),
            intArrayOf(1, 0, 1, 1, 0, 1),
            intArrayOf(1, 3, 1, 1, 2, 1),
            20
        )
    }

    @Test
    fun testNonGraph() {
        val input = CannedTokenStream(
            0,
            22,
            token("hello", 1, 1, 0, 5),
            token("pseudo", 1, 1, 6, 12),
            token("world", 1, 1, 13, 18),
            token("fun", 1, 1, 19, 22)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("hello", "pseudo", "world", "fun"),
            intArrayOf(0, 6, 13, 19),
            intArrayOf(5, 12, 18, 22),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 1, 1, 1),
            22
        )
    }

    @Test
    fun testSimpleHole() {
        val input = CannedTokenStream(
            0,
            13,
            token("hello", 1, 1, 0, 5),
            token("hole", 2, 1, 6, 10),
            token("fun", 1, 1, 11, 13)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("hello", "hole", "fun"),
            intArrayOf(0, 6, 11),
            intArrayOf(5, 10, 13),
            intArrayOf(1, 2, 1),
            intArrayOf(1, 1, 1),
            13
        )
    }

    @Test
    fun testHoleUnderSyn() {
        val input = CannedTokenStream(
            0,
            12,
            token("wizard", 1, 1, 0, 6),
            token("woz", 0, 3, 0, 12),
            token("oz", 2, 1, 10, 12)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("wizard", "woz", "oz"),
            intArrayOf(0, 0, 10),
            intArrayOf(6, 12, 12),
            intArrayOf(1, 0, 2),
            intArrayOf(1, 3, 1),
            12
        )
    }

    @Test
    fun testStrangelyNumberedNodes() {
        val input = CannedTokenStream(
            0,
            27,
            token("dog", 1, 3, 0, 5),
            token("puppy", 0, 3, 0, 5),
            token("flies", 3, 1, 6, 11)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("dog", "puppy", "flies"),
            intArrayOf(0, 0, 6),
            intArrayOf(5, 5, 11),
            intArrayOf(1, 0, 1),
            intArrayOf(1, 1, 1),
            27
        )
    }

    @Test
    fun testTwoLongParallelPaths() {
        val input = CannedTokenStream(
            0,
            11,
            token("a", 1, 1, 0, 1),
            token("b", 0, 2, 0, 1),
            token("a", 1, 2, 2, 3),
            token("b", 1, 2, 2, 3),
            token("a", 1, 2, 4, 5),
            token("b", 1, 2, 4, 5),
            token("a", 1, 2, 6, 7),
            token("b", 1, 2, 6, 7),
            token("a", 1, 2, 8, 9),
            token("b", 1, 2, 8, 9),
            token("a", 1, 2, 10, 11),
            token("b", 1, 2, 10, 11)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("a", "b", "a", "b", "a", "b", "a", "b", "a", "b", "a", "b"),
            intArrayOf(0, 0, 2, 2, 4, 4, 6, 6, 8, 8, 10, 10),
            intArrayOf(1, 1, 3, 3, 5, 5, 7, 7, 9, 9, 11, 11),
            intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
            11
        )
    }

    @Test
    fun testAltPathFirstStepHole() {
        val input = CannedTokenStream(0, 3, token("abc", 1, 3, 0, 3), token("b", 1, 1, 1, 2), token("c", 1, 1, 2, 3))
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("abc", "b", "c"),
            intArrayOf(0, 1, 2),
            intArrayOf(3, 2, 3),
            intArrayOf(1, 1, 1),
            intArrayOf(3, 1, 1),
            3
        )
    }

    @Test
    fun testAltPathLastStepHole() {
        val input = CannedTokenStream(
            0,
            4,
            token("abc", 1, 3, 0, 3),
            token("a", 0, 1, 0, 1),
            token("b", 1, 1, 1, 2),
            token("d", 2, 1, 3, 4)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("abc", "a", "b", "d"),
            intArrayOf(0, 0, 1, 3),
            intArrayOf(1, 1, 2, 4),
            intArrayOf(1, 0, 1, 2),
            intArrayOf(3, 1, 1, 1),
            4
        )
    }

    @Test
    fun testLongHole() {
        val input = CannedTokenStream(
            0,
            28,
            token("hello", 1, 1, 0, 5),
            token("hole", 5, 1, 20, 24),
            token("fun", 1, 1, 25, 28)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("hello", "hole", "fun"),
            intArrayOf(0, 20, 25),
            intArrayOf(5, 24, 28),
            intArrayOf(1, 2, 1),
            intArrayOf(1, 1, 1),
            28
        )
    }

    @Test
    fun testAltPathLastStepLongHole() {
        val input = CannedTokenStream(0, 4, token("abc", 1, 3, 0, 3), token("a", 0, 1, 0, 1), token("d", 3, 1, 3, 4))
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("abc", "a", "d"),
            intArrayOf(0, 0, 3),
            intArrayOf(1, 1, 4),
            intArrayOf(1, 0, 2),
            intArrayOf(2, 1, 1),
            4
        )
    }

    @Test
    fun testAltPathLastStepHoleWithoutEndToken() {
        val input = CannedTokenStream(0, 2, token("abc", 1, 3, 0, 3), token("a", 0, 1, 0, 1), token("b", 1, 1, 1, 2))
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("abc", "a", "b"),
            intArrayOf(0, 0, 1),
            intArrayOf(1, 1, 2),
            intArrayOf(1, 0, 1),
            intArrayOf(1, 1, 1),
            2
        )
    }

    @Test
    fun testAltPathLastStepHoleFollowedByHole() {
        val input = CannedTokenStream(0, 5, token("abc", 1, 3, 0, 3), token("b", 1, 1, 1, 2), token("e", 3, 1, 4, 5))
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("abc", "b", "e"),
            intArrayOf(0, 1, 4),
            intArrayOf(3, 2, 5),
            intArrayOf(1, 1, 2),
            intArrayOf(1, 1, 1),
            5
        )
    }

    @Test
    fun testShingledGap() {
        val input = CannedTokenStream(
            0,
            5,
            token("abc", 1, 3, 0, 3),
            token("a", 0, 1, 0, 1),
            token("b", 1, 1, 1, 2),
            token("cde", 1, 3, 2, 5),
            token("d", 1, 1, 3, 4),
            token("e", 1, 1, 4, 5)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("abc", "a", "d", "b", "cde", "e"),
            intArrayOf(0, 0, 3, 3, 4, 4),
            intArrayOf(1, 1, 3, 3, 5, 5),
            intArrayOf(1, 0, 1, 0, 1, 0),
            intArrayOf(1, 1, 1, 1, 1, 1),
            5
        )
    }

    @Test
    fun testShingledGapWithHoles() {
        val input = CannedTokenStream(
            0,
            5,
            token("abc", 1, 3, 0, 3),
            token("b", 1, 1, 1, 2),
            token("cde", 1, 3, 2, 5),
            token("d", 1, 1, 3, 4),
            token("e", 1, 1, 4, 5)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("abc", "d", "b", "cde", "e"),
            intArrayOf(0, 3, 3, 4, 4),
            intArrayOf(3, 3, 3, 5, 5),
            intArrayOf(1, 1, 0, 1, 0),
            intArrayOf(1, 1, 1, 1, 1),
            5
        )
    }

    @Test
    fun testFirstTokenHole() {
        val input = CannedTokenStream(0, 9, token("start", 2, 1, 0, 5))
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(out, arrayOf("start"), intArrayOf(0), intArrayOf(5), intArrayOf(2), intArrayOf(1), 9)
    }

    @Test
    fun testShingleFromGap() {
        val input = CannedTokenStream(
            0,
            9,
            token("a", 1, 1, 4, 8),
            token("abc", 0, 3, 4, 7),
            token("cd", 2, 2, 6, 8),
            token("d", 1, 1, 7, 8),
            token("e", 1, 1, 8, 9)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("a", "abc", "d", "cd", "e"),
            intArrayOf(4, 4, 7, 7, 8),
            intArrayOf(7, 7, 8, 8, 9),
            intArrayOf(1, 0, 1, 1, 1),
            intArrayOf(1, 1, 2, 1, 1),
            9
        )
    }

    @Test
    fun testShingledGapAltPath() {
        val input = CannedTokenStream(0, 4, token("abc", 1, 3, 0, 3), token("abcd", 0, 4, 0, 4), token("cd", 2, 2, 2, 4))
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("abc", "abcd", "cd"),
            intArrayOf(0, 0, 2),
            intArrayOf(3, 4, 4),
            intArrayOf(1, 0, 1),
            intArrayOf(1, 2, 1),
            4
        )
    }

    @Test
    fun testHeavilyConnectedGraphWithGap() {
        val input = CannedTokenStream(
            0,
            7,
            token("a", 1, 1, 0, 1),
            token("ab", 0, 2, 0, 2),
            token("abcdef", 0, 6, 0, 6),
            token("abcd", 0, 4, 0, 4),
            token("bcdef", 1, 5, 1, 7),
            token("def", 2, 3, 4, 7),
            token("e", 1, 1, 5, 6),
            token("f", 1, 1, 6, 7)
        )
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("a", "ab", "abcdef", "abcd", "bcdef", "e", "def", "f"),
            intArrayOf(0, 0, 0, 0, 5, 5, 6, 6),
            intArrayOf(1, 1, 7, 1, 7, 6, 7, 7),
            intArrayOf(1, 0, 0, 0, 1, 0, 1, 0),
            intArrayOf(1, 1, 3, 1, 2, 1, 1, 1),
            7
        )
    }

    @Test
    fun testShingleWithLargeLeadingGap() {
        val input = CannedTokenStream(0, 6, token("abcde", 1, 5, 0, 5), token("ef", 4, 2, 4, 6), token("f", 1, 1, 5, 6))
        val out = FlattenGraphFilter(input)
        assertTokenStreamContents(
            out,
            arrayOf("abcde", "f", "ef"),
            intArrayOf(0, 5, 5),
            intArrayOf(5, 6, 6),
            intArrayOf(1, 1, 0),
            intArrayOf(1, 1, 1),
            6
        )
    }

    private fun buildMultiTokenCharsRef(tokens: Array<String>, charsRefBuilder: CharsRefBuilder, random: Random): CharsRef {
        val srcLen = random.nextInt(2) + 2
        val srcTokens = Array(srcLen) { "" }
        for (pos in 0 until srcLen) {
            srcTokens[pos] = tokens[random().nextInt(tokens.size)]
        }
        SynonymMap.Builder.join(srcTokens, charsRefBuilder)
        return charsRefBuilder.toCharsRef()
    }

    @Test
    fun testRandomGraphs() {
        val baseTokens = arrayOf("t1", "t2", "t3", "t4")
        val synTokens = arrayOf("s1", "s2", "s3", "s4")
        val mapBuilder = SynonymMap.Builder()
        val charRefBuilder = CharsRefBuilder()
        val random = random()

        val synCount = random.nextInt(10) + 10
        for (i in 0 until synCount) {
            val type = random.nextInt(4)
            val src: CharsRef
            val dest: CharsRef
            when (type) {
                0 -> {
                    src = charRefBuilder.append(baseTokens[random.nextInt(baseTokens.size)]).toCharsRef()
                    charRefBuilder.clear()
                    dest = charRefBuilder.append(synTokens[random.nextInt(synTokens.size)]).toCharsRef()
                    charRefBuilder.clear()
                }
                1 -> {
                    src = buildMultiTokenCharsRef(baseTokens, charRefBuilder, random)
                    charRefBuilder.clear()
                    dest = charRefBuilder.append(synTokens[random.nextInt(synTokens.size)]).toCharsRef()
                    charRefBuilder.clear()
                }
                2 -> {
                    src = charRefBuilder.append(baseTokens[random.nextInt(baseTokens.size)]).toCharsRef()
                    charRefBuilder.clear()
                    dest = buildMultiTokenCharsRef(synTokens, charRefBuilder, random)
                    charRefBuilder.clear()
                }
                else -> {
                    src = buildMultiTokenCharsRef(baseTokens, charRefBuilder, random)
                    charRefBuilder.clear()
                    dest = buildMultiTokenCharsRef(synTokens, charRefBuilder, random)
                    charRefBuilder.clear()
                }
            }
            mapBuilder.add(src, dest, true)
        }

        val synMap = mapBuilder.build()
        val stopWordCount = random.nextInt(4) + 1
        val stopWords = CharArraySet(stopWordCount, true)
        while (stopWords.size < stopWordCount) {
            var index = random.nextInt(baseTokens.size + synTokens.size)
            var tokenArray = baseTokens
            if (index >= baseTokens.size) {
                index -= baseTokens.size
                tokenArray = synTokens
            }
            stopWords.add(tokenArray[index])
        }

        val withFlattenGraph = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val `in`: Tokenizer = WhitespaceTokenizer()
                var result: TokenStream = SynonymGraphFilter(`in`, synMap, true)
                result = StopFilter(result, stopWords)
                result = FlattenGraphFilter(result)
                return TokenStreamComponents(`in`, result)
            }
        }

        val tokenCount = random.nextInt(20) + 20
        val stringTokens = mutableListOf<String>()
        while (stringTokens.size < tokenCount) {
            stringTokens.add(baseTokens[random.nextInt(baseTokens.size)])
        }

        val text = stringTokens.joinToString(" ")
        val tsta = TokenStreamToAutomaton()
        val flattenedTokenStream = withFlattenGraph.tokenStream("field", text)
        assertFalse(Operations.hasDeadStates(tsta.toAutomaton(flattenedTokenStream)))
        flattenedTokenStream.close()
    }

    @Test
    fun testPathsNotLost() {
        val wordCount = random().nextInt(5) + 5
        val acceptStrings = mutableListOf<BytesRef>()
        for (i in 0 until wordCount) {
            val wordLen = random().nextInt(5) + 5
            val ref = BytesRef(wordLen)
            ref.length = wordLen
            ref.offset = 0
            for (j in 0 until wordLen) {
                ref.bytes[j] = (random().nextInt(5) + 65).toByte()
            }
            acceptStrings.add(ref)
        }
        acceptStrings.sort()
        val nonFlattenedAutomaton = Automata.makeStringUnion(acceptStrings)
        val ts = AutomatonToTokenStream.toTokenStream(nonFlattenedAutomaton)
        val flattenedTokenStream = FlattenGraphFilter(ts)
        val tsta = TokenStreamToAutomaton()
        val flattenedAutomaton = tsta.toAutomaton(flattenedTokenStream)
        val acceptStringsWithPosSep = createAcceptStringsWithPosSep(acceptStrings)
        for (acceptString in acceptStringsWithPosSep) {
            assertTrue(recursivelyValidate(acceptString, 0, 0, flattenedAutomaton), "string not accepted ${acceptString.utf8ToString()}")
        }
    }

    private fun createAcceptStringsWithPosSep(acceptStrings: List<BytesRef>): List<BytesRef> {
        val acceptStringsWithPosSep = mutableListOf<BytesRef>()
        for (acceptString in acceptStrings) {
            val withPosSep = BytesRef(acceptString.length * 2 - 1)
            withPosSep.length = acceptString.length * 2 - 1
            withPosSep.offset = 0
            for (i in 0 until acceptString.length) {
                withPosSep.bytes[i * 2] = acceptString.bytes[i]
                if (i * 2 + 1 < withPosSep.length) {
                    withPosSep.bytes[i * 2 + 1] = TokenStreamToAutomaton.POS_SEP.toByte()
                }
            }
            acceptStringsWithPosSep.add(withPosSep)
        }
        return acceptStringsWithPosSep
    }

    fun recursivelyValidate(acceptString: BytesRef, acceptStringIndex: Int, state: Int, automaton: Automaton): Boolean {
        if (acceptStringIndex == acceptString.length) {
            return automaton.isAccept(state)
        }
        val transition = Transition()
        automaton.initTransition(state, transition)
        val numTransitions = automaton.getNumTransitions(state)
        var accept = false
        for (i in 0 until numTransitions) {
            automaton.getTransition(state, i, transition)
            if (transition.min <= acceptString.bytes[acceptStringIndex] && transition.max >= acceptString.bytes[acceptStringIndex].toInt()) {
                accept = recursivelyValidate(acceptString, acceptStringIndex + 1, transition.dest, automaton)
            }
            if (accept) break
        }
        return accept
    }
}

