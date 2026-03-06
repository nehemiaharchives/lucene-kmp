package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ByteArrayInputStreamTest {

    @Test
    fun testReadSkipMarkAndReset() {
        val input = ByteArrayInputStream(byteArrayOf(10, 20, 30, 40), offset = 1, length = 2)

        assertEquals(2, input.available())
        assertEquals(20, input.read())
        input.mark(0)
        assertEquals(1, input.available())
        assertEquals(1L, input.skip(1))
        assertEquals(0, input.available())
        input.reset()
        assertEquals(30, input.read())
        assertEquals(-1, input.read())
        assertEquals(0L, input.skip(-1))
    }

    @Test
    fun testBulkReadAndZeroLengthRead() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val destination = byteArrayOf(9, 9, 9, 9)

        assertEquals(0, input.read(destination, 1, 0))
        assertEquals(3, input.read(destination, 1, 3))
        assertContentEquals(byteArrayOf(9, 1, 2, 3), destination)
        assertEquals(-1, input.read(destination, 0, 1))
        assertEquals(true, input.markSupported())
    }
}
