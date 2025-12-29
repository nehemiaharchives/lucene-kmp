package org.gnit.lucenekmp.analysis.charfilter

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.UnicodeUtil
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMappingCharFilter : BaseTokenStreamTestCase() {
    private lateinit var normMap: NormalizeCharMap

    @BeforeTest
    fun setUp() {
        val builder = NormalizeCharMap.Builder()

        builder.add("aa", "a")
        builder.add("bbb", "b")
        builder.add("cccc", "cc")

        builder.add("h", "i")
        builder.add("j", "jj")
        builder.add("k", "kkk")
        builder.add("ll", "llll")

        builder.add("empty", "")

        // BMP (surrogate pair)
        builder.add(UnicodeUtil.newString(intArrayOf(0x1D122), 0, 1), "fclef")

        builder.add("\uFF01", "full-width-exclamation")

        normMap = builder.build()
    }

    @Test
    @Throws(IOException::class)
    fun testReaderReset() {
        val cs = MappingCharFilter(normMap, StringReader("x"))
        val buf = CharArray(10)
        var len = cs.read(buf, 0, 10)
        assertEquals(1, len)
        assertEquals('x', buf[0])
        len = cs.read(buf, 0, 10)
        assertEquals(-1, len)

        // rewind
        cs.reset()
        len = cs.read(buf, 0, 10)
        assertEquals(1, len)
        assertEquals('x', buf[0])
    }

    @Test
    @Throws(IOException::class)
    fun testNothingChange() {
        val cs = MappingCharFilter(normMap, StringReader("x"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, arrayOf("x"), intArrayOf(0), intArrayOf(1), 1)
    }

    @Test
    @Throws(IOException::class)
    fun test1to1() {
        val cs = MappingCharFilter(normMap, StringReader("h"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, arrayOf("i"), intArrayOf(0), intArrayOf(1), 1)
    }

    @Test
    @Throws(IOException::class)
    fun test1to2() {
        val cs = MappingCharFilter(normMap, StringReader("j"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, arrayOf("jj"), intArrayOf(0), intArrayOf(1), 1)
    }

    @Test
    @Throws(IOException::class)
    fun test1to3() {
        val cs = MappingCharFilter(normMap, StringReader("k"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, arrayOf("kkk"), intArrayOf(0), intArrayOf(1), 1)
    }

    @Test
    @Throws(IOException::class)
    fun test2to4() {
        val cs = MappingCharFilter(normMap, StringReader("ll"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, arrayOf("llll"), intArrayOf(0), intArrayOf(2), 2)
    }

    @Test
    @Throws(IOException::class)
    fun test2to1() {
        val cs = MappingCharFilter(normMap, StringReader("aa"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, arrayOf("a"), intArrayOf(0), intArrayOf(2), 2)
    }

    @Test
    @Throws(IOException::class)
    fun test3to1() {
        val cs = MappingCharFilter(normMap, StringReader("bbb"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, arrayOf("b"), intArrayOf(0), intArrayOf(3), 3)
    }

    @Test
    @Throws(IOException::class)
    fun test4to2() {
        val cs = MappingCharFilter(normMap, StringReader("cccc"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, arrayOf("cc"), intArrayOf(0), intArrayOf(4), 4)
    }

    @Test
    @Throws(IOException::class)
    fun test5to0() {
        val cs = MappingCharFilter(normMap, StringReader("empty"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, emptyArray(), intArrayOf(), intArrayOf(), 5)
    }

    @Test
    @Throws(IOException::class)
    fun testNonBMPChar() {
        val cs =
            MappingCharFilter(
                normMap,
                StringReader(UnicodeUtil.newString(intArrayOf(0x1D122), 0, 1))
            )
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(ts, arrayOf("fclef"), intArrayOf(0), intArrayOf(2), 2)
    }

    @Test
    @Throws(IOException::class)
    fun testFullWidthChar() {
        val cs = MappingCharFilter(normMap, StringReader("\uFF01"))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(
            ts,
            arrayOf("full-width-exclamation"),
            intArrayOf(0),
            intArrayOf(1),
            1
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTokenStream() {
        val testString = "h i j k ll cccc bbb aa"
        val cs = MappingCharFilter(normMap, StringReader(testString))
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(
            ts,
            arrayOf("i", "i", "jj", "kkk", "llll", "cc", "b", "a"),
            intArrayOf(0, 2, 4, 6, 8, 11, 16, 20),
            intArrayOf(1, 3, 5, 7, 10, 15, 19, 22),
            testString.length
        )
    }

    @Test
    @Throws(IOException::class)
    fun testChained() {
        val testString = "aaaa ll h"
        val cs =
            MappingCharFilter(
                normMap,
                MappingCharFilter(normMap, StringReader(testString))
            )
        val ts = whitespaceMockTokenizer(cs)
        assertTokenStreamContents(
            ts,
            arrayOf("a", "llllllll", "i"),
            intArrayOf(0, 5, 8),
            intArrayOf(4, 7, 9),
            testString.length
        )
    }

    @Test
    @Throws(IOException::class)
    fun testRandom() {
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, tokenizer)
                }

                override fun initReader(fieldName: String, reader: org.gnit.lucenekmp.jdkport.Reader):
                    org.gnit.lucenekmp.jdkport.Reader {
                    return MappingCharFilter(normMap, reader)
                }
            }

        val numRounds = RANDOM_MULTIPLIER * 1000
        checkRandomData(random(), analyzer, numRounds)
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFinalOffsetSpecialCase() {
        val builder = NormalizeCharMap.Builder()
        builder.add("t", "")
        // even though this below rule has no effect, the test passes if you remove it!!
        builder.add("tmakdbl", "c")

        val map = builder.build()

        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, tokenizer)
                }

                override fun initReader(fieldName: String, reader: org.gnit.lucenekmp.jdkport.Reader):
                    org.gnit.lucenekmp.jdkport.Reader {
                    return MappingCharFilter(map, reader)
                }
            }

        val text = "gzw f quaxot"
        checkAnalysisConsistency(random(), analyzer, false, text)
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRandomMaps() {
        val numIterations = atLeast(3)
        repeat(numIterations) {
            val map = randomMap()
            val analyzer =
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                        return TokenStreamComponents(tokenizer, tokenizer)
                    }

                    override fun initReader(
                        fieldName: String,
                        reader: org.gnit.lucenekmp.jdkport.Reader
                    ): org.gnit.lucenekmp.jdkport.Reader {
                        return MappingCharFilter(map, reader)
                    }
                }
            val numRounds = 100
            checkRandomData(random(), analyzer, numRounds)
            analyzer.close()
        }
    }

    private fun randomMap(): NormalizeCharMap {
        val random = random()
        val builder = NormalizeCharMap.Builder()
        val keys = mutableSetOf<String>()
        val num = random.nextInt(5)
        for (i in 0 until num) {
            val key = TestUtil.randomSimpleString(random)
            if (key.isNotEmpty() && !keys.contains(key)) {
                val value = TestUtil.randomSimpleString(random)
                builder.add(key, value)
                keys.add(key)
            }
        }
        return builder.build()
    }

    @Test
    @Throws(IOException::class)
    fun testRandomMaps2() {
        val random = random()
        val numIterations = atLeast(3)
        repeat(numIterations) { iter ->
            if (VERBOSE) {
                println("\nTEST iter=$iter")
            }

            val endLetter = TestUtil.nextInt(random, 'b'.code, 'z'.code).toChar()

            val map = mutableMapOf<String, String>()
            val builder = NormalizeCharMap.Builder()
            val numMappings = atLeast(5)
            if (VERBOSE) {
                println("  mappings:")
            }
            while (map.size < numMappings) {
                val key = TestUtil.randomSimpleStringRange(random, 'a', endLetter, 7)
                if (key.isNotEmpty() && !map.containsKey(key)) {
                    val value = TestUtil.randomSimpleString(random)
                    map[key] = value
                    builder.add(key, value)
                    if (VERBOSE) {
                        println("    $key -> $value")
                    }
                }
            }

            val charMap = builder.build()

            if (VERBOSE) {
                println("  test random documents...")
            }

            repeat(100) {
                val content = TestUtil.randomSimpleStringRange(random, 'a', endLetter, atLeast(1000))

                if (VERBOSE) {
                    println("  content=$content")
                }

                val output = StringBuilder()
                val inputOffsets = mutableListOf<Int>()

                var cumDiff = 0
                var charIdx = 0
                while (charIdx < content.length) {
                    var matchLen = -1
                    var matchRepl: String? = null

                    for ((key, value) in map) {
                        if (charIdx + key.length <= content.length) {
                            val limit = charIdx + key.length
                            var matches = true
                            var charIdx2 = charIdx
                            while (charIdx2 < limit) {
                                if (key[charIdx2 - charIdx] != content[charIdx2]) {
                                    matches = false
                                    break
                                }
                                charIdx2++
                            }

                            if (matches) {
                                if (key.length > matchLen) {
                                    matchLen = key.length
                                    matchRepl = value
                                }
                            }
                        }
                    }

                    if (matchLen != -1 && matchRepl != null) {
                        if (VERBOSE) {
                            println(
                                "    match=" +
                                    content.substring(charIdx, charIdx + matchLen) +
                                    " @ off=" +
                                    charIdx +
                                    " repl=" +
                                    matchRepl
                            )
                        }
                        output.append(matchRepl)
                        val minLen = minOf(matchLen, matchRepl.length)

                        for (outIdx in 0 until minLen) {
                            inputOffsets.add(output.length - matchRepl.length + outIdx + cumDiff)
                        }

                        cumDiff += matchLen - matchRepl.length
                        charIdx += matchLen

                        if (matchRepl.length < matchLen) {
                            // nothing to do
                        } else if (matchRepl.length > matchLen) {
                            for (outIdx in matchLen until matchRepl.length) {
                                inputOffsets.add(output.length + cumDiff - 1)
                            }
                        }
                    } else {
                        inputOffsets.add(output.length + cumDiff)
                        output.append(content[charIdx])
                        charIdx++
                    }
                }

                val expected = output.toString()
                if (VERBOSE) {
                    print("    expected:")
                    for (charIdx2 in expected.indices) {
                        print(" ${expected[charIdx2]}/${inputOffsets[charIdx2]}")
                    }
                    println()
                }

                val mapFilter = MappingCharFilter(charMap, StringReader(content))

                val actualBuilder = StringBuilder()
                val actualInputOffsets = mutableListOf<Int>()

                while (true) {
                    if (random.nextBoolean()) {
                        val ch = mapFilter.read()
                        if (ch == -1) {
                            break
                        }
                        actualBuilder.append(ch.toChar())
                    } else {
                        val buffer = CharArray(TestUtil.nextInt(random, 1, 100))
                        val off = if (buffer.size == 1) 0 else random.nextInt(buffer.size - 1)
                        val count = mapFilter.read(buffer, off, buffer.size - off)
                        if (count == -1) {
                            break
                        } else {
                            actualBuilder.appendRange(buffer, off, off + count)
                        }
                    }

                    if (random.nextInt(10) == 7) {
                        while (actualInputOffsets.size < actualBuilder.length) {
                            actualInputOffsets.add(mapFilter.correctOffset(actualInputOffsets.size))
                        }
                    }
                }

                while (actualInputOffsets.size < actualBuilder.length) {
                    actualInputOffsets.add(mapFilter.correctOffset(actualInputOffsets.size))
                }

                val actual = actualBuilder.toString()

                assertEquals(expected, actual)
                assertEquals(inputOffsets, actualInputOffsets)
            }
        }
    }
}
