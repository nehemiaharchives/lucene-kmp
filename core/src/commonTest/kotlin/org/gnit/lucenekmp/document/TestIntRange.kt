package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIntRange : LuceneTestCase() {
    @Test
    fun testToString() {
        val range = IntRange("foo", intArrayOf(1, 11, 21, 31), intArrayOf(2, 12, 22, 32))
        assertEquals("IntRange <foo: [1 : 2] [11 : 12] [21 : 22] [31 : 32]>", range.toString())
    }
}
