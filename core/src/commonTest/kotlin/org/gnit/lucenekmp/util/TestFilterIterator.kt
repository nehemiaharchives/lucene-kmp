package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestFilterIterator : LuceneTestCase() {

    private fun assertNoMore(it: MutableIterator<*>) {
        assertFalse(it.hasNext())
        expectThrows(NoSuchElementException::class) {
            it.next()
        }
        assertFalse(it.hasNext())
    }

    @Test
    fun testEmpty() {
        val it = object : FilterIterator<String, String>(set.iterator()) {
            override fun predicateFunction(`object`: String): Boolean {
                return false
            }
        }
        assertNoMore(it)
    }

    @Test
    fun testA1() {
        val it = object : FilterIterator<String, String>(set.iterator()) {
            override fun predicateFunction(`object`: String): Boolean {
                return "a" == `object`
            }
        }
        assertTrue(it.hasNext())
        assertEquals("a", it.next())
        assertNoMore(it)
    }

    @Test
    fun testA2() {
        val it = object : FilterIterator<String, String>(set.iterator()) {
            override fun predicateFunction(`object`: String): Boolean {
                return "a" == `object`
            }
        }
        // this time without check: assertTrue(it.hasNext());
        assertEquals("a", it.next())
        assertNoMore(it)
    }

    @Test
    fun testB1() {
        val it = object : FilterIterator<String, String>(set.iterator()) {
            override fun predicateFunction(`object`: String): Boolean {
                return "b" == `object`
            }
        }
        assertTrue(it.hasNext())
        assertEquals("b", it.next())
        assertNoMore(it)
    }

    @Test
    fun testB2() {
        val it = object : FilterIterator<String, String>(set.iterator()) {
            override fun predicateFunction(`object`: String): Boolean {
                return "b" == `object`
            }
        }
        // this time without check: assertTrue(it.hasNext());
        assertEquals("b", it.next())
        assertNoMore(it)
    }

    @Test
    fun testAll1() {
        val it = object : FilterIterator<String, String>(set.iterator()) {
            override fun predicateFunction(`object`: String): Boolean {
                return true
            }
        }
        assertTrue(it.hasNext())
        assertEquals("a", it.next())
        assertTrue(it.hasNext())
        assertEquals("b", it.next())
        assertTrue(it.hasNext())
        assertEquals("c", it.next())
        assertNoMore(it)
    }

    @Test
    fun testAll2() {
        val it = object : FilterIterator<String, String>(set.iterator()) {
            override fun predicateFunction(`object`: String): Boolean {
                return true
            }
        }
        assertEquals("a", it.next())
        assertEquals("b", it.next())
        assertEquals("c", it.next())
        assertNoMore(it)
    }

    @Test
    fun testUnmodifiable() {
        val it = object : FilterIterator<String, String>(set.iterator()) {
            override fun predicateFunction(`object`: String): Boolean {
                return true
            }
        }
        assertEquals("a", it.next())
        expectThrows(UnsupportedOperationException::class) {
            it.remove()
        }
    }

    companion object {
        private val set: MutableSet<String> = mutableSetOf("a", "b", "c")
    }
}
