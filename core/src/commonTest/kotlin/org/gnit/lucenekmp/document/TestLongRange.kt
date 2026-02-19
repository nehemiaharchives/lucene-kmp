package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLongRange : LuceneTestCase() {
    @Test
    fun testToString() {
        val range =
            LongRange(
                "foo",
                longArrayOf(1, 11, 21, 31),
                longArrayOf(2, 12, 22, 32)
            )
        assertEquals("LongRange <foo: [1 : 2] [11 : 12] [21 : 22] [31 : 32]>", range.toString())
    }
}
