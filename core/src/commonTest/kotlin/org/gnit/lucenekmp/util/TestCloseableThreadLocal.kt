package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestCloseableThreadLocal : LuceneTestCase() {
    @Test
    fun testInitValue() {
        val tl = InitValueThreadLocal()
        val str = tl.get() as String
        assertEquals(TEST_VALUE, str)
    }

    @Test
    fun testNullValue() {
        // Tests that null can be set as a valid value (LUCENE-1805). This
        // previously failed in get().
        val ctl = CloseableThreadLocal<Any>()
        ctl.set(null)
        assertNull(ctl.get())
    }

    @Test
    fun testDefaultValueWithoutSetting() {
        // LUCENE-1805: make sure default get returns null,
        // twice in a row
        val ctl = CloseableThreadLocal<Any>()
        assertNull(ctl.get())
    }

    class InitValueThreadLocal : CloseableThreadLocal<Any>() {
        override fun initialValue(): Any {
            return TEST_VALUE
        }
    }

    companion object {
        const val TEST_VALUE: String = "initvaluetest"
    }
}
