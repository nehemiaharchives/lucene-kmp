package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestFixedLengthBytesRefArray : LuceneTestCase() {

    @Test
    fun testBasic() {
        val a = FixedLengthBytesRefArray(Int.SIZE_BYTES)
        val numValues = 100
        for (i in 0 until numValues) {
            val bytes = byteArrayOf(0, 0, 0, (10 - i).toByte())
            a.append(BytesRef(bytes))
        }

        val iterator = a.iterator(Comparator<BytesRef> { a, b -> a.compareTo(b) })

        var last: BytesRef? = null

        var count = 0
        while (true) {
            val bytes = iterator.next() ?: break
            if (last != null) {
                assertTrue(last!!.compareTo(bytes) < 0, "count=$count last=$last bytes=$bytes")
            }
            last = BytesRef.deepCopyOf(bytes)
            count++
        }

        assertEquals(numValues, count)
    }

    @Test
    fun testRandom() {
        val length = TestUtil.nextInt(random(), 4, 10)
        val count = atLeast(10000)
        val values = Array(count) { BytesRef() }

        val a = FixedLengthBytesRefArray(length)
        for (i in 0 until count) {
            val value = BytesRef(ByteArray(length))
            random().nextBytes(value.bytes)
            values[i] = value
            a.append(value)
        }

        values.sort()
        val iterator = a.iterator(Comparator<BytesRef> { a, b -> a.compareTo(b) })
        for (i in 0 until count) {
            val next = iterator.next()
            assertNotNull(next)
            assertEquals(values[i], next)
        }
    }
}
