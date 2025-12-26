package org.gnit.lucenekmp.analysis.util

import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRollingCharBuffer : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun test() {
        val ITERS = atLeast(100)

        val buffer = RollingCharBuffer()

        val random = random()
        for (iter in 0 until ITERS) {
            val stringLen = if (random.nextBoolean()) random.nextInt(50) else random.nextInt(20000)
            val s = if (stringLen == 0) "" else TestUtil.randomUnicodeString(random, stringLen)
            if (VERBOSE) {
                println("\nTEST: iter=$iter s.length()=${s.length}")
            }
            buffer.reset(StringReader(s))
            var nextRead = 0
            var availCount = 0
            while (nextRead < s.length) {
                if (VERBOSE) {
                    println("  cycle nextRead=$nextRead avail=$availCount")
                }
                if (availCount == 0 || random.nextBoolean()) {
                    // Read next char
                    if (VERBOSE) {
                        println("    new char")
                    }
                    assertEquals(s[nextRead], buffer.get(nextRead).toChar())
                    nextRead++
                    availCount++
                } else if (random.nextBoolean()) {
                    // Read previous char
                    val pos = TestUtil.nextInt(random, nextRead - availCount, nextRead - 1)
                    if (VERBOSE) {
                        println("    old char pos=$pos")
                    }
                    assertEquals(s[pos], buffer.get(pos).toChar())
                } else {
                    // Read slice
                    val length = if (availCount == 1) 1 else TestUtil.nextInt(random, 1, availCount)
                    val start = if (length == availCount) {
                        nextRead - availCount
                    } else {
                        nextRead - availCount + random.nextInt(availCount - length)
                    }
                    if (VERBOSE) {
                        println("    slice start=$start length=$length")
                    }
                    assertEquals(s.substring(start, start + length), buffer.get(start, length).concatToString())
                }

                if (availCount > 0 && random.nextInt(20) == 17) {
                    val toFree = random.nextInt(availCount)
                    if (VERBOSE) {
                        println("    free $toFree (avail=${availCount - toFree})")
                    }
                    buffer.freeBefore(nextRead - (availCount - toFree))
                    availCount -= toFree
                }
            }
        }
    }
}
