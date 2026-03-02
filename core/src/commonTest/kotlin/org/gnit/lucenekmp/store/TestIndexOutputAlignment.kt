package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestIndexOutputAlignment : LuceneTestCase() {

    @Test
    fun testAlignmentCalculation() {
        assertEquals(0L, IndexOutput.alignOffset(0L, Long.SIZE_BYTES))
        assertEquals(0L, IndexOutput.alignOffset(0L, Int.SIZE_BYTES))
        assertEquals(0L, IndexOutput.alignOffset(0L, Short.SIZE_BYTES))
        assertEquals(0L, IndexOutput.alignOffset(0L, Byte.SIZE_BYTES))

        assertEquals(8L, IndexOutput.alignOffset(1L, Long.SIZE_BYTES))
        assertEquals(4L, IndexOutput.alignOffset(1L, Int.SIZE_BYTES))
        assertEquals(2L, IndexOutput.alignOffset(1L, Short.SIZE_BYTES))
        assertEquals(1L, IndexOutput.alignOffset(1L, Byte.SIZE_BYTES))

        assertEquals(32L, IndexOutput.alignOffset(25L, Long.SIZE_BYTES))
        assertEquals(28L, IndexOutput.alignOffset(25L, Int.SIZE_BYTES))
        assertEquals(26L, IndexOutput.alignOffset(25L, Short.SIZE_BYTES))
        assertEquals(25L, IndexOutput.alignOffset(25L, Byte.SIZE_BYTES))

        val value = 1L shl 48
        assertEquals(value, IndexOutput.alignOffset(value - 1, Long.SIZE_BYTES))
        assertEquals(value, IndexOutput.alignOffset(value - 1, Int.SIZE_BYTES))
        assertEquals(value, IndexOutput.alignOffset(value - 1, Short.SIZE_BYTES))
        // byte alignment never changes anything:
        assertEquals(value - 1, IndexOutput.alignOffset(value - 1, Byte.SIZE_BYTES))

        assertEquals(Long.MAX_VALUE, IndexOutput.alignOffset(Long.MAX_VALUE, Byte.SIZE_BYTES))
    }

    @Test
    fun testInvalidAlignments() {
        assertInvalidAligment(0)
        assertInvalidAligment(-1)
        assertInvalidAligment(-2)
        assertInvalidAligment(6)
        assertInvalidAligment(43)
        assertInvalidAligment(Int.MIN_VALUE)

        expectThrows(IllegalArgumentException::class) { IndexOutput.alignOffset(-1L, 1) }
        expectThrows(ArithmeticException::class) { IndexOutput.alignOffset(Long.MAX_VALUE, 2) }
    }

    private fun assertInvalidAligment(size: Int) {
        expectThrows(IllegalArgumentException::class) { IndexOutput.alignOffset(1L, size) }
    }

    @Test
    fun testOutputAlignment() {
        intArrayOf(Long.SIZE_BYTES, Int.SIZE_BYTES, Short.SIZE_BYTES, Byte.SIZE_BYTES)
            .forEach { runTestOutputAlignment(it) }
    }

    private fun runTestOutputAlignment(alignment: Int) {
        OutputStreamIndexOutput(
            "test output",
            "test",
            ByteArrayOutputStream(),
            8192
        ).use { out ->
            for (i in 0 until 10 * RANDOM_MULTIPLIER) {
                // write some bytes
                val length = random().nextInt(32)
                out.writeBytes(ByteArray(length), length)
                val origPos = out.filePointer
                // align to next boundary
                val newPos = out.alignFilePointer(alignment)
                assertEquals(out.filePointer, newPos)
                assertTrue(newPos % alignment.toLong() == 0L, "not aligned")
                assertTrue(newPos >= origPos, "newPos >=")
                assertTrue(newPos - origPos < alignment.toLong(), "too much added")
            }
        }
    }
}
