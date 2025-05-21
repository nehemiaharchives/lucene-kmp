package org.gnit.lucenekmp.jdkport

import kotlin.test.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class SystemTest {
    @Test
    fun testGetPropertyDefault() {
        val key = "NON_EXISTENT_PROPERTY"
        val defaultValue = "default"
        val value = System.getProperty(key, defaultValue)
        assertEquals(defaultValue, value)
        logger.debug { "testGetPropertyDefault passed" }
    }

    @Test
    fun testArraycopyNullable() {
        val src = arrayOf<String?>(null, "a", "b", null, "c")
        val dest = arrayOfNulls<String>(5)
        System.arraycopy(src, 1, dest, 0, 3)
        assertContentEquals(arrayOf("a", "b", null, null, null), dest)
    }

    @Test
    fun testArraycopyNotNull() {
        val src = arrayOf("x", "y", "z", "w")
        val dest = arrayOf("a", "b", "c", "d")
        System.arraycopy(src, 1, dest, 0, 2)
        assertContentEquals(arrayOf("y", "z", "c", "d"), dest)
    }

    @Test
    fun testArraycopyBoolean() {
        val src = booleanArrayOf(true, false, true, false)
        val dest = booleanArrayOf(false, false, false, false)
        System.arraycopy(src, 1, dest, 0, 3)
        assertContentEquals(booleanArrayOf(false, true, false, false), dest)
    }

    @Test
    fun testArraycopyByte() {
        val src = byteArrayOf(1, 2, 3, 4)
        val dest = byteArrayOf(0, 0, 0, 0)
        System.arraycopy(src, 2, dest, 1, 2)
        assertContentEquals(byteArrayOf(0, 3, 4, 0), dest)
    }

    @Test
    fun testArraycopyInt() {
        val src = intArrayOf(10, 20, 30, 40)
        val dest = intArrayOf(0, 0, 0, 0)
        System.arraycopy(src, 0, dest, 2, 2)
        assertContentEquals(intArrayOf(0, 0, 10, 20), dest)
    }

    @Test
    fun testArraycopyLong() {
        val src = longArrayOf(100L, 200L, 300L, 400L)
        val dest = longArrayOf(0L, 0L, 0L, 0L)
        System.arraycopy(src, 1, dest, 0, 3)
        assertContentEquals(longArrayOf(200L, 300L, 400L, 0L), dest)
    }

    @Test
    fun testArraycopyFloat() {
        val src = floatArrayOf(1.1f, 2.2f, 3.3f, 4.4f)
        val dest = floatArrayOf(0f, 0f, 0f, 0f)
        System.arraycopy(src, 0, dest, 1, 3)
        assertContentEquals(floatArrayOf(0f, 1.1f, 2.2f, 3.3f), dest)
    }

    @Test
    fun testArraycopyChar() {
        val src = charArrayOf('a', 'b', 'c', 'd')
        val dest = charArrayOf('x', 'x', 'x', 'x')
        System.arraycopy(src, 2, dest, 1, 2)
        assertContentEquals(charArrayOf('x', 'c', 'd', 'x'), dest)
    }
}
