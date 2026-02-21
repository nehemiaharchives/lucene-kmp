package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCharsRefBuilder : LuceneTestCase() {

    @Test
    fun testAppend() {
        val s = TestUtil.randomUnicodeString(random(), 100)
        val builder = CharsRefBuilder()
        while (builder.length() < s.length) {
            if (random().nextBoolean()) {
                builder.append(s[builder.length()])
            } else {
                val start = builder.length()
                val end = TestUtil.nextInt(random(), start, s.length)
                if (random().nextBoolean()) {
                    builder.append(s.substring(start, end))
                } else {
                    builder.append(s, start, end)
                }
            }
        }
        assertEquals(s, builder.toString())
    }

    @Test
    fun testAppendNull() {
        val builder = CharsRefBuilder()
        builder.append(null as CharSequence?)
        builder.append(null as CharSequence?, 1, 3)
        assertEquals("nullnull", builder.toString())
    }
}
