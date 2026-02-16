package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDoubleRange : LuceneTestCase() {

    @Test
    fun testToString() {
        val range = DoubleRange("foo", doubleArrayOf(0.1, 1.1, 2.1, 3.1), doubleArrayOf(.2, 1.2, 2.2, 3.2))
        assertEquals(
            "DoubleRange <foo: [0.1 : 0.2] [1.1 : 1.2] [2.1 : 2.2] [3.1 : 3.2]>", range.toString()
        )
    }
}
