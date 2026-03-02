package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.TimeSource

class ObjectsTest {

    @Test
    fun testEquals() {
        assertEquals(true, Objects.equals("test", "test"))
        assertEquals(false, Objects.equals("test", "TEST"))
        assertEquals(false, Objects.equals("test", null))
        assertEquals(false, Objects.equals(null, "test"))
        assertEquals(true, Objects.equals(null, null))
    }

    @Test
    fun testHash() {
        assertEquals(0, Objects.hash())
        assertEquals(1, Objects.hash(1))
        assertEquals(Objects.hash(1, 2, 3), Objects.hash(1, 2, 3))
        assertNotEquals(Objects.hash(1, 2, 3), Objects.hash(3, 2, 1))
    }

    @Test
    fun testToString() {
        assertEquals("test", Objects.toString("test"))
        assertEquals("null", Objects.toString(null))
    }

    @Test
    fun testStringBuilderGetCharsPerformanceRepro() {
        val dst = CharArray(20000)
        val sb = StringBuilder("a")
        val totalMark = TimeSource.Monotonic.markNow()
        var copyNanos = 0L

        for (i in 0..<20000) {
            val copyMark = TimeSource.Monotonic.markNow()
            sb.getChars(0, sb.length, dst, 0)
            copyNanos += copyMark.elapsedNow().inWholeNanoseconds
            sb.append('a')
        }

        // basic correctness guard so the benchmarked path actually copied expected data
        assertEquals('a', dst[0])
        println(
            "ObjectsTest.stringBuilderGetChars perf elapsedMs=${totalMark.elapsedNow().inWholeMilliseconds} " +
                "copyNs=$copyNanos finalLength=${sb.length}"
        )
    }
}
