package org.gnit.lucenekmp.jdkport

import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.fail

class BreakIteratorTest {
    private val locale = Locale.US
    private val characterBreak = BreakIterator.getCharacterInstance(locale)
    private val wordBreak = BreakIterator.getWordInstance(locale)
    private val lineBreak = BreakIterator.getLineInstance(locale)
    private val sentenceBreak = BreakIterator.getSentenceInstance(locale)

    private fun generalIteratorTest(bi: BreakIterator, expectedResult: List<String>) {
        val buffer = StringBuilder()
        for (i in expectedResult.indices) {
            buffer.append(expectedResult[i])
        }
        val text = buffer.toString()

        bi.setText(text)

        val nextResults = testFirstAndNext(bi, text)
        val previousResults = testLastAndPrevious(bi, text)

        println("comparing forward and backward...")
        compareFragmentLists("forward iteration", "backward iteration", nextResults, previousResults)
        println("comparing expected and actual...")
        compareFragmentLists("expected result", "actual result", expectedResult, nextResults)

        val boundaries = IntArray(expectedResult.size + 3)
        boundaries[0] = BreakIterator.DONE
        boundaries[1] = 0
        for (i in expectedResult.indices) {
            boundaries[i + 2] = boundaries[i + 1] + expectedResult[i].length
        }
        boundaries[boundaries.size - 1] = BreakIterator.DONE

        testFollowing(bi, text, boundaries)
        testPreceding(bi, text, boundaries)
        testIsBoundary(bi, text, boundaries)

        doMultipleSelectionTest(bi, text)
    }

    private fun testFirstAndNext(bi: BreakIterator, text: String): MutableList<String> {
        var p = bi.first()
        var lastP = p
        val result = mutableListOf<String>()

        if (p != 0) {
            fail("first() returned $p instead of 0")
        }
        while (p != BreakIterator.DONE) {
            p = bi.next()
            if (p != BreakIterator.DONE) {
                if (p <= lastP) {
                    fail("next() failed to move forward: next() on position $lastP yielded $p")
                }
                result.add(text.substring(lastP, p))
            } else {
                if (lastP != text.length) {
                    fail("next() returned DONE prematurely: offset was $lastP instead of ${text.length}")
                }
            }
            lastP = p
        }
        return result
    }

    private fun testLastAndPrevious(bi: BreakIterator, text: String): MutableList<String> {
        var p = bi.last()
        var lastP = p
        val result = mutableListOf<String>()

        if (p != text.length) {
            fail("last() returned $p instead of ${text.length}")
        }
        while (p != BreakIterator.DONE) {
            p = bi.previous()
            if (p != BreakIterator.DONE) {
                if (p >= lastP) {
                    fail("previous() failed to move backward: previous() on position $lastP yielded $p")
                }
                result.add(0, text.substring(p, lastP))
            } else {
                if (lastP != 0) {
                    fail("previous() returned DONE prematurely: offset was $lastP instead of 0")
                }
            }
            lastP = p
        }
        return result
    }

    private fun compareFragmentLists(f1Name: String, f2Name: String, f1: List<String>, f2: List<String>) {
        var p1 = 0
        var p2 = 0
        var t1 = 0
        var t2 = 0

        while (p1 < f1.size && p2 < f2.size) {
            var s1 = f1[p1]
            var s2 = f2[p2]
            t1 += s1.length
            t2 += s2.length

            if (s1 == s2) {
                debugLogln("   >$s1<")
                ++p1
                ++p2
            } else {
                var tempT1 = t1
                var tempT2 = t2
                var tempP1 = p1
                var tempP2 = p2

                while (tempT1 != tempT2 && tempP1 < f1.size && tempP2 < f2.size) {
                    while (tempT1 < tempT2 && tempP1 < f1.size) {
                        tempT1 += f1[tempP1].length
                        ++tempP1
                    }
                    while (tempT2 < tempT1 && tempP2 < f2.size) {
                        tempT2 += f2[tempP2].length
                        ++tempP2
                    }
                }
                println("*** $f1Name has:")
                while (p1 <= tempP1 && p1 < f1.size) {
                    s1 = f1[p1]
                    t1 += s1.length
                    debugLogln(" *** >$s1<")
                    ++p1
                }
                println("***** $f2Name has:")
                while (p2 <= tempP2 && p2 < f2.size) {
                    s2 = f2[p2]
                    t2 += s2.length
                    debugLogln(" ***** >$s2<")
                    ++p2
                }
                fail("Discrepancy between $f1Name and $f2Name\n---\n$f1\n---\n$f2")
            }
        }
    }

    private fun testFollowing(bi: BreakIterator, text: String, boundaries: IntArray) {
        println("testFollowing():")
        var p = 2
        var i = 0
        try {
            for (index in 0..text.length) {
                i = index
                if (i == boundaries[p]) {
                    ++p
                }
                val b = bi.following(i)
                println("bi.following($i) -> $b")
                if (b != boundaries[p]) {
                    fail("Wrong result from following() for $i: expected ${boundaries[p]}, got $b")
                }
            }
        } catch (e: IllegalArgumentException) {
            fail("IllegalArgumentException caught from following() for offset: $i")
        }
    }

    private fun testPreceding(bi: BreakIterator, text: String, boundaries: IntArray) {
        println("testPreceding():")
        var p = 0
        var i = 0
        try {
            for (index in 0..text.length) {
                i = index
                val b = bi.preceding(i)
                println("bi.preceding($i) -> $b")
                if (b != boundaries[p]) {
                    fail("Wrong result from preceding() for $i: expected ${boundaries[p]}, got $b")
                }
                if (i == boundaries[p + 1]) {
                    ++p
                }
            }
        } catch (e: IllegalArgumentException) {
            fail("IllegalArgumentException caught from preceding() for offset: $i")
        }
    }

    private fun testIsBoundary(bi: BreakIterator, text: String, boundaries: IntArray) {
        println("testIsBoundary():")
        var p = 1
        for (i in 0..text.length) {
            val isB = bi.isBoundary(i)
            println("bi.isBoundary($i) -> $isB")

            if (i == boundaries[p]) {
                if (!isB) {
                    fail("Wrong result from isBoundary() for $i: expected true, got false")
                }
                ++p
            } else {
                if (isB) {
                    fail("Wrong result from isBoundary() for $i: expected false, got true")
                }
            }
        }
    }

    private fun doMultipleSelectionTest(iterator: BreakIterator, testText: String) {
        println("Multiple selection test...")
        val testIterator = iterator.clone() as BreakIterator
        var offset = iterator.first()
        var testOffset: Int
        var count = 0

        do {
            testOffset = testIterator.first()
            testOffset = testIterator.next(count)
            println("next($count) -> $testOffset")
            if (offset != testOffset) {
                fail(
                    "next(n) and next() not returning consistent results: for step $count, " +
                        "next(n) returned $testOffset and next() had $offset"
                )
            }

            if (offset != BreakIterator.DONE) {
                count++
                offset = iterator.next()
            }
        } while (offset != BreakIterator.DONE)

        offset = iterator.last()
        count = 0

        do {
            testOffset = testIterator.last()
            testOffset = testIterator.next(count)
            println("next($count) -> $testOffset")
            if (offset != testOffset) {
                fail(
                    "next(n) and next() not returning consistent results: for step $count, " +
                        "next(n) returned $testOffset and next() had $offset"
                )
            }

            if (offset != BreakIterator.DONE) {
                count--
                offset = iterator.previous()
            }
        } while (offset != BreakIterator.DONE)
    }

    private fun doBreakInvariantTest(tb: BreakIterator, testChars: String) {
        val work = StringBuilder("aaa")
        var errorCount = 0

        val breaks = "\n\u2029\u2028"

        for (i in breaks.indices) {
            work.setCharAt(1, breaks[i])
            for (j in testChars.indices) {
                work.setCharAt(0, testChars[j])
                for (k in testChars.indices) {
                    val c = testChars[k]

                    if (work[1] == '\r' && c == '\n') {
                        continue
                    }

                    val type1 = Character.getType(work[1].code).toByte()
                    val type2 = Character.getType(c.code).toByte()
                    if (type1 == Character.CONTROL || type1 == Character.FORMAT ||
                        type2 == Character.CONTROL || type2 == Character.FORMAT
                    ) {
                        continue
                    }

                    work.setCharAt(2, c)
                    tb.setText(work.toString())
                    var seen2 = false
                    var l = tb.first()
                    while (l != BreakIterator.DONE) {
                        if (l == 2) {
                            seen2 = true
                        }
                        l = tb.next()
                    }
                    if (!seen2) {
                        fail(
                            "No break between U+" + work[1].code.toString(16) +
                                " and U+" + work[2].code.toString(16)
                        )
                        errorCount++
                        if (errorCount >= 75) {
                            return
                        }
                    }
                }
            }
        }
    }

    private fun doOtherInvariantTest(tb: BreakIterator, testChars: String) {
        val work = StringBuilder("a\r\na")
        var errorCount = 0

        for (i in testChars.indices) {
            work.setCharAt(0, testChars[i])
            for (j in testChars.indices) {
                work.setCharAt(3, testChars[j])
                tb.setText(work.toString())
                var k = tb.first()
                while (k != BreakIterator.DONE) {
                    if (k == 2) {
                        fail(
                            "Break between CR and LF in string U+" + work[0].code.toString(16) +
                                ", U+d U+a U+" + work[3].code.toString(16)
                        )
                        errorCount++
                        if (errorCount >= 75) {
                            return
                        }
                    }
                    k = tb.next()
                }
            }
        }

        work.setLength(0)
        work.append("aaaa")
        for (i in testChars.indices) {
            var c = testChars[i]
            if (c == '\n' || c == '\r' || c == '\u2029' || c == '\u2028' || c == '\u0003') {
                continue
            }
            work.setCharAt(1, c)
            for (j in testChars.indices) {
                c = testChars[j]
                val cType = Character.getType(c.code).toByte()
                if (cType != Character.NON_SPACING_MARK && cType != Character.ENCLOSING_MARK) {
                    continue
                }
                work.setCharAt(2, c)

                val type1 = Character.getType(work[1].code).toByte()
                val type2 = Character.getType(work[2].code).toByte()
                if (type1 == Character.CONTROL || type1 == Character.FORMAT ||
                    type2 == Character.CONTROL || type2 == Character.FORMAT
                ) {
                    continue
                }

                tb.setText(work.toString())
                var k = tb.first()
                while (k != BreakIterator.DONE) {
                    if (k == 2) {
                        fail(
                            "Break between U+" + work[1].code.toString(16) +
                                " and U+" + work[2].code.toString(16)
                        )
                        errorCount++
                        if (errorCount >= 75) {
                            return
                        }
                    }
                    k = tb.next()
                }
            }
        }
    }

    private fun debugLogln(s: String) {
        val zeros = "0000"
        val out = StringBuilder()
        for (i in s.indices) {
            val c = s[i]
            if (c in ' '..<'\u007f') {
                out.append(c)
            } else {
                out.append("\\u")
                val temp = c.code.toString(16)
                out.append(zeros.substring(0, 4 - temp.length))
                out.append(temp)
            }
        }
        println(out.toString())
    }

    @Test
    fun TestWordBreak() {
        val wordSelectionData = mutableListOf<String>()

        wordSelectionData.add("12,34")

        wordSelectionData.add(" ")
        wordSelectionData.add("\u00A2")
        wordSelectionData.add("\u00A3")
        wordSelectionData.add("\u00A4")
        wordSelectionData.add("\u00A5")
        wordSelectionData.add("alpha-beta-gamma")
        wordSelectionData.add(".")
        wordSelectionData.add(" ")
        wordSelectionData.add("Badges")
        wordSelectionData.add("?")
        wordSelectionData.add(" ")
        wordSelectionData.add("BADGES")
        wordSelectionData.add("!")
        wordSelectionData.add("?")
        wordSelectionData.add("!")
        wordSelectionData.add(" ")
        wordSelectionData.add("We")
        wordSelectionData.add(" ")
        wordSelectionData.add("don't")
        wordSelectionData.add(" ")
        wordSelectionData.add("need")
        wordSelectionData.add(" ")
        wordSelectionData.add("no")
        wordSelectionData.add(" ")
        wordSelectionData.add("STINKING")
        wordSelectionData.add(" ")
        wordSelectionData.add("BADGES")
        wordSelectionData.add("!")
        wordSelectionData.add("!")
        wordSelectionData.add("!")

        wordSelectionData.add("012.566,5")
        wordSelectionData.add(" ")
        wordSelectionData.add("123.3434,900")
        wordSelectionData.add(" ")
        wordSelectionData.add("1000,233,456.000")
        wordSelectionData.add(" ")
        wordSelectionData.add("1,23.322%")
        wordSelectionData.add(" ")
        wordSelectionData.add("123.1222")

        wordSelectionData.add(" ")
        wordSelectionData.add("\u0024123,000.20")

        wordSelectionData.add(" ")
        wordSelectionData.add("179.01\u0025")

        wordSelectionData.add("Hello")
        wordSelectionData.add(",")
        wordSelectionData.add(" ")
        wordSelectionData.add("how")
        wordSelectionData.add(" ")
        wordSelectionData.add("are")
        wordSelectionData.add(" ")
        wordSelectionData.add("you")
        wordSelectionData.add(" ")
        wordSelectionData.add("X")
        wordSelectionData.add(" ")

        wordSelectionData.add("Now")
        wordSelectionData.add("\r")
        wordSelectionData.add("is")
        wordSelectionData.add("\n")
        wordSelectionData.add("the")
        wordSelectionData.add("\r\n")
        wordSelectionData.add("time")
        wordSelectionData.add("\n")
        wordSelectionData.add("\r")
        wordSelectionData.add("for")
        wordSelectionData.add("\r")
        wordSelectionData.add("\r")
        wordSelectionData.add("all")
        wordSelectionData.add(" ")

        generalIteratorTest(wordBreak, wordSelectionData)
    }

    @Ignore
    @Test
    fun TestBug4097779() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4098467Words() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4117554Words() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Test
    fun TestSentenceBreak() {
        val sentenceSelectionData = mutableListOf<String>()

        sentenceSelectionData.add("This is a simple sample sentence. ")
        sentenceSelectionData.add("(This is it.) ")
        sentenceSelectionData.add("This is a simple sample sentence. ")
        sentenceSelectionData.add("\"This isn't it.\" ")
        sentenceSelectionData.add("Hi! ")
        sentenceSelectionData.add("This is a simple sample sentence. ")
        sentenceSelectionData.add("It does not have to make any sense as you can see. ")
        sentenceSelectionData.add("Nel mezzo del cammin di nostra vita, mi ritrovai in una selva oscura. ")
        sentenceSelectionData.add("Che la dritta via aveo smarrita. ")
        sentenceSelectionData.add("He said, that I said, that you said!! ")

        sentenceSelectionData.add("Don't rock the boat.\u2029")

        sentenceSelectionData.add("Because I am the daddy, that is why. ")
        sentenceSelectionData.add("Not on my time (el timo.)! ")

        sentenceSelectionData.add("So what!!\u2029")

        sentenceSelectionData.add("\"But now,\" he said, \"I know!\" ")
        sentenceSelectionData.add("Harris thumbed down several, including \"Away We Go\" (which became the huge success Oklahoma!). ")
        sentenceSelectionData.add("One species, B. anthracis, is highly virulent.\n")
        sentenceSelectionData.add("Wolf said about Sounder:\"Beautifully thought-out and directed.\" ")
        sentenceSelectionData.add("Have you ever said, \"This is where \tI shall live\"? ")
        sentenceSelectionData.add("He answered, \"You may not!\" ")
        sentenceSelectionData.add("Another popular saying is: \"How do you do?\". ")
        sentenceSelectionData.add("Yet another popular saying is: 'I'm fine thanks.' ")
        sentenceSelectionData.add("What is the proper use of the abbreviation pp.? ")
        sentenceSelectionData.add("Yes, I am definatelly 12\" tall!!")

        generalIteratorTest(sentenceBreak, sentenceSelectionData)
    }

    @Ignore
    @Test
    fun TestBug4113835() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4111338() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4117554Sentences() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4158381() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4143071() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4152416() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4152117() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug8264765() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Test
    fun TestLineBreak() {
        val lineSelectionData = mutableListOf<String>()

        lineSelectionData.add("Multi-")
        lineSelectionData.add("Level ")
        lineSelectionData.add("example ")
        lineSelectionData.add("of ")
        lineSelectionData.add("a ")
        lineSelectionData.add("semi-")
        lineSelectionData.add("idiotic ")
        lineSelectionData.add("non-")
        lineSelectionData.add("sensical ")
        lineSelectionData.add("(non-")
        lineSelectionData.add("important) ")
        lineSelectionData.add("sentence. ")

        lineSelectionData.add("Hi  ")
        lineSelectionData.add("Hello ")
        lineSelectionData.add("How\n")
        lineSelectionData.add("are\r")
        lineSelectionData.add("you\u2028")
        lineSelectionData.add("fine.\t")
        lineSelectionData.add("good.  ")

        lineSelectionData.add("Now\r")
        lineSelectionData.add("is\n")
        lineSelectionData.add("the\r\n")
        lineSelectionData.add("time\n")
        lineSelectionData.add("\r")
        lineSelectionData.add("for\r")
        lineSelectionData.add("\r")
        lineSelectionData.add("all")

        generalIteratorTest(lineBreak, lineSelectionData)
    }

    @Ignore
    @Test
    fun TestBug4068133() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4086052() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4097920() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4035266() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4098467Lines() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4117554Lines() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4217703() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    private val graveS = "S\u0300"
    private val acuteBelowI = "i\u0317"
    private val acuteE = "e\u0301"
    private val circumflexA = "a\u0302"
    private val tildeE = "e\u0303"

    @Test
    fun TestCharacterBreak() {
        val characterSelectionData = mutableListOf<String>()

        characterSelectionData.add(graveS)
        characterSelectionData.add(acuteBelowI)
        characterSelectionData.add("m")
        characterSelectionData.add("p")
        characterSelectionData.add("l")
        characterSelectionData.add(acuteE)
        characterSelectionData.add(" ")
        characterSelectionData.add("s")
        characterSelectionData.add(circumflexA)
        characterSelectionData.add("m")
        characterSelectionData.add("p")
        characterSelectionData.add("l")
        characterSelectionData.add(tildeE)
        characterSelectionData.add(".")
        characterSelectionData.add("w")
        characterSelectionData.add(circumflexA)
        characterSelectionData.add("w")
        characterSelectionData.add("a")
        characterSelectionData.add("f")
        characterSelectionData.add("q")
        characterSelectionData.add("\n")
        characterSelectionData.add("\r")
        characterSelectionData.add("\r\n")
        characterSelectionData.add("\n")

        generalIteratorTest(characterBreak, characterSelectionData)
    }

    @Ignore
    @Test
    fun TestBug4098467Characters() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4153072() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4146175Sentences() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4146175Lines() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    @Ignore
    @Test
    fun TestBug4214367() {
        // TODO: port from jdk24u/test/jdk/java/text/BreakIterator/BreakIteratorTest.java
    }

    private val cannedTestChars =
        " !\"#$%&()+-01234<=>ABCDE[]^_`abcde{}|\u00a0\u00a2" +
            "\u00a3\u00a4\u00a5\u00a6\u00a7\u00a8\u00a9\u00ab\u00ad\u00ae\u00af\u00b0\u00b2\u00b3" +
            "\u00b4\u00b9\u00bb\u00bc\u00bd\u02b0\u02b1\u02b2\u02b3\u02b4\u0300\u0301\u0302\u0303" +
            "\u0304\u05d0\u05d1\u05d2\u05d3\u05d4\u0903\u093e\u093f\u0940\u0949\u0f3a\u0f3b\u2000" +
            "\u2001\u2002\u200c\u200d\u200e\u200f\u2010\u2011\u2012\u2028\u2029\u202a\u203e\u203f" +
            "\u2040\u20dd\u20de\u20df\u20e0\u2160\u2161\u2162\u2163\u2164"

    @Test
    fun TestSentenceInvariants() {
        val e = BreakIterator.getSentenceInstance(locale)
        doOtherInvariantTest(e, "$cannedTestChars.,\u3001\u3002\u3041\u3042\u3043\ufeff")
    }

    @Test
    fun TestWordInvariants() {
        val e = BreakIterator.getWordInstance(locale)
        val testChars = cannedTestChars + "',.\u3041\u3042\u3043\u309b\u309c\u30a1\u30a2" +
            "\u30a3\u4e00\u4e01\u4e02"
        doBreakInvariantTest(e, testChars)
        doOtherInvariantTest(e, testChars)
    }

    @Test
    fun TestLineInvariants() {
        val e = BreakIterator.getLineInstance(locale)
        val testChars = cannedTestChars + ".,;:\u3001\u3002\u3041\u3042\u3043\u3044\u3045" +
            "\u30a3\u4e00\u4e01\u4e02"
        doBreakInvariantTest(e, testChars)
        doOtherInvariantTest(e, testChars)

        var errorCount = 0

        val noBreak = "\u00a0\u2007\u2011\ufeff"
        val work = StringBuilder("aaa")
        for (i in testChars.indices) {
            val c = testChars[i]
            if (c == '\r' || c == '\n' || c == '\u2029' || c == '\u2028' || c == '\u0003') {
                continue
            }
            work.setCharAt(0, c)
            for (j in noBreak.indices) {
                work.setCharAt(1, noBreak[j])
                for (k in testChars.indices) {
                    work.setCharAt(2, testChars[k])
                    val type1 = Character.getType(work[1].code).toByte()
                    val type2 = Character.getType(work[2].code).toByte()
                    if (type1 == Character.CONTROL || type1 == Character.FORMAT ||
                        type2 == Character.CONTROL || type2 == Character.FORMAT
                    ) {
                        continue
                    }
                    e.setText(work.toString())
                    var l = e.first()
                    while (l != BreakIterator.DONE) {
                        if (l == 1 || l == 2) {
                            if (work[l - 1] == '\u0020' && (work[l] == '\u00a0' ||
                                    work[l] == '\u0f0c' ||
                                    work[l] == '\u2007' ||
                                    work[l] == '\u2011' ||
                                    work[l] == '\u202f' ||
                                    work[l] == '\ufeff')
                            ) {
                                l = e.next()
                                continue
                            }
                            fail(
                                "Got break between U+" + work[l - 1].code.toString(16) +
                                    " and U+" + work[l].code.toString(16)
                            )
                            errorCount++
                            if (errorCount >= 75) {
                                return
                            }
                        }
                        l = e.next()
                    }
                }
            }
        }
    }

    @Test
    fun TestCharacterInvariants() {
        val e = BreakIterator.getCharacterInstance(locale)
        val testChars = cannedTestChars + "\u1100\u1101\u1102\u1160\u1161\u1162\u11a8\u11a9\u11aa"
        doBreakInvariantTest(e, testChars)
        doOtherInvariantTest(e, testChars)
    }

    @Test
    fun TestEmptyString() {
        val text = ""
        val x = mutableListOf<String>()
        x.add(text)

        generalIteratorTest(lineBreak, x)
    }

    @Test
    fun TestGetAvailableLocales() {
        val locList = BreakIterator.availableLocales
        if (locList.isEmpty()) {
            fail("getAvailableLocales() returned an empty list!")
        }
    }

    @Test
    fun TestJapaneseLineBreak() {
        val testString = StringBuilder("\u4e00x\u4e8c")

        val precedingChars =
            "([{\u201a\u201e\u2045\u207d\u208d\u2329\u3008\u300a\u300c\u300e\u3010\u3014\u3016\u3018\u301a\u301d\ufe35\ufe37\ufe39\ufe3b\ufe3d\ufe3f\ufe41\ufe43\ufe59\ufe5b\ufe5d\uff08\uff3b\uff5b\uff62\u169b" +
                "\u00ab\u2018\u201b\u201c\u201f\u2039" +
                "\u00a5\u00a3\u00a4\u20a0"

        val followingChars =
            ")]}\u2046\u207e\u208e\u232a\u3009\u300b\u300d\u300f\u3011\u3015\u3017\u3019\u301b\u301e\u301f\ufd3e\ufe36\ufe38\ufe3a\ufe3c\ufe3e\ufe40\ufe42\ufe44\ufe5a\ufe5c\ufe5e\uff09\uff3d\uff5d\uff63\u169c" +
                "\u00bb\u2019\u201d\u203a" +
                "!%,.:;\u3001\u3002\u2030\u2031\u2032\u2033\u2034" +
                "\u2103\u2109" +
                "\u00a2" +
                "\u3005\u309d\u309e" +
                "\u3063\u3083\u3085\u3087\u30c3\u30e3\u30e5\u30e7\u30fc\u30fd\u30fe" +
                "\u0300\u0301\u0302" +
                "\u309b\u309c" +
                "\u00b0"

        val iter = BreakIterator.getLineInstance(Locale("ja", "JP"))

        for (i in precedingChars.indices) {
            testString.setCharAt(1, precedingChars[i])
            iter.setText(testString.toString())
            var j = iter.first()
            if (j != 0) {
                fail("ja line break failure: failed to start at 0 and bounced at $j")
            }
            j = iter.next()
            if (j != 1) {
                fail(
                    "ja line break failure: failed to stop before '" + precedingChars[i] +
                        "' (\\u" + precedingChars[i].code.toString(16) +
                        ") at 1 and bounded at $j"
                )
            }
            j = iter.next()
            if (j != 3) {
                fail(
                    "ja line break failure: failed to skip position after '" + precedingChars[i] +
                        "' (\\u" + precedingChars[i].code.toString(16) +
                        ") at 3 and bounded at $j"
                )
            }
        }

        for (i in followingChars.indices) {
            testString.setCharAt(1, followingChars[i])
            iter.setText(testString.toString())
            var j = iter.first()
            if (j != 0) {
                fail("ja line break failure: failed to start at 0 and bounded at $j")
            }
            j = iter.next()
            if (j != 2) {
                fail(
                    "ja line break failure: failed to skip position before '" + followingChars[i] +
                        "' (\\u" + followingChars[i].code.toString(16) +
                        ") at 2 and bounded at $j"
                )
            }
            j = iter.next()
            if (j != 3) {
                fail(
                    "ja line break failure: failed to stop after '" + followingChars[i] +
                        "' (\\u" + followingChars[i].code.toString(16) +
                        ") at 3 and bounded at $j"
                )
            }
        }
    }

    @Test
    fun TestLineBreakBasedOnUnicode3_0_0() {
        var iter: BreakIterator
        var i: Int

        iter = BreakIterator.getWordInstance(Locale.US)
        iter.setText("\u0216\u0217\u0218\u0219\u021A")
        i = iter.first()
        i = iter.next()
        if (i != 5) {
            fail("Word break failure: failed to stop at 5 and bounded at $i")
        }

        iter = BreakIterator.getLineInstance(Locale.US)
        iter.setText("32\u301f1")
        i = iter.first()
        i = iter.next()
        if (i != 3) {
            fail("Line break failure: failed to skip before \\u301F(Pe) at 3 and bounded at $i")
        }

        iter.setText("\u1820\u1806\u1821")
        i = iter.first()
        i = iter.next()
        if (i != 2) {
            fail("Mongolian line break failure: failed to skip position before \\u1806(Pd) at 2 and bounded at $i")
        }

        iter.setText("\u17E0\u17DB\u17E1")
        i = iter.first()
        i = iter.next()
        if (i != 1) {
            fail("Khmer line break failure: failed to stop before \\u17DB(Sc) at 1 and bounded at $i")
        }
        i = iter.next()
        if (i != 3) {
            fail("Khmer line break failure: failed to skip position after \\u17DB(Sc) at 3 and bounded at $i")
        }

        iter.setText("\u1692\u1680\u1696")
        i = iter.first()
        i = iter.next()
        if (i != 2) {
            fail("Ogham line break failure: failed to skip postion before \\u1680(Zs) at 2 and bounded at $i")
        }

        iter = BreakIterator.getLineInstance(Locale("th"))
        iter.setText("\u0E57\u201C\u0E55\u201D\u0E53")
        i = iter.first()
        i = iter.next()
        if (i != 1) {
            fail("Thai line break failure: failed to stop before \\u201C(Pi) at 1 and bounded at $i")
        }
        i = iter.next()
        if (i != 4) {
            fail("Thai line break failure: failed to stop after \\u201D(Pf) at 4 and bounded at $i")
        }
    }

    @Test
    fun TestEndBehavior() {
        val testString = "boo."
        val wb = BreakIterator.getWordInstance(locale)
        wb.setText(testString)

        if (wb.first() != 0) {
            fail("Didn't get break at beginning of string.")
        }
        if (wb.next() != 3) {
            fail("Didn't get break before period in \"boo.\"")
        }
        if (wb.current() != 4 && wb.next() != 4) {
            fail("Didn't get break at end of string.")
        }
    }

    @Test
    fun TestLineBreakContractions() {
        val expected = mutableListOf<String>()

        expected.add("These ")
        expected.add("are ")
        expected.add("'foobles'. ")
        expected.add("Don't ")
        expected.add("you ")
        expected.add("like ")
        expected.add("them?")
        generalIteratorTest(lineBreak, expected)
    }

    @Test
    fun TestGraphemeBreak() {
        val source = openGraphemeBreakTestSource()
        try {
            var line: String? = source.readUtf8Line()
            val codepoint = Regex("([0-9A-F]{4,5})")
            val comment = Regex("#.*")
            val splitRule = Regex("\\s*÷[\\s\\t]*")
            val joinRule = Regex("\\s×\\s")

            while (line != null) {
                val currentLine = line
                val cleaned = comment.replace(currentLine, "").trim()
                if (cleaned.isNotEmpty()) {
                    val parts = splitRule.split(cleaned)
                    val expected = mutableListOf<String>()
                    for (part in parts) {
                        val replaced = codepoint.replace(part) { mr ->
                            codePointToString(mr.value.toInt(16))
                        }
                        val segment = joinRule.replace(replaced, "")
                        if (segment.isNotEmpty()) {
                            expected.add(segment)
                        }
                    }
                    generalIteratorTest(characterBreak, expected)
                }
                line = source.readUtf8Line()
            }
        } finally {
            source.close()
        }
    }

    private fun codePointToString(codePoint: Int): String {
        if (codePoint <= 0xFFFF) {
            return codePoint.toChar().toString()
        }
        val cp = codePoint - 0x10000
        val high = 0xD800 + (cp shr 10)
        val low = 0xDC00 + (cp and 0x3FF)
        return charArrayOf(high.toChar(), low.toChar()).concatToString()
    }

    private fun openGraphemeBreakTestSource(): okio.BufferedSource {
        val fs = FileSystem.SYSTEM
        val path = "src/commonTest/resources/GraphemeBreakTest.txt".toPath()
        if (!fs.exists(path)) {
            throw IOException("GraphemeBreakTest.txt not found at $path")
        }
        return fs.source(path).buffer()
    }

    @Test
    fun TestSetTextIOOBException() {
        BreakIterator.getCharacterInstance(locale).setText(StringCharacterIterator("abcfefg", 1, 5, 3))
    }
}
