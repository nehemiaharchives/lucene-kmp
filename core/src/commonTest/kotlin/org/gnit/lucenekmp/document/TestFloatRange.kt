package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFloatRange : LuceneTestCase() {

    @Test
    fun testToString() {
        val range = FloatRange("foo", floatArrayOf(0.1f, 1.1f, 2.1f, 3.1f), floatArrayOf(.2f, 1.2f, 2.2f, 3.2f))
        assertEquals(
            "FloatRange <foo: [0.1 : 0.2] [1.1 : 1.2] [2.1 : 2.2] [3.1 : 3.2]>", range.toString()
        )
    }
}
