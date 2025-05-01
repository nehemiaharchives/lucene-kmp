package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertContentEquals
import kotlinx.io.ByteArrayInputStream
import kotlinx.io.ByteArrayOutputStream
import kotlinx.io.IOException

class BufferedInputStreamTest {

    @Test
    fun testRead() {
        val data = "Hello, World!".toByteArray()
        val inputStream = BufferedInputStream(ByteArrayInputStream(data))

        val result = ByteArray(data.size)
        inputStream.read(result)

        assertContentEquals(data, result)
    }

    @Test
    fun testSkip() {
        val data = "Hello, World!".toByteArray()
        val inputStream = BufferedInputStream(ByteArrayInputStream(data))

        inputStream.skip(7)
        val result = ByteArray(6)
        inputStream.read(result)

        assertContentEquals("World!".toByteArray(), result)
    }

    @Test
    fun testAvailable() {
        val data = "Hello, World!".toByteArray()
        val inputStream = BufferedInputStream(ByteArrayInputStream(data))

        assertEquals(data.size, inputStream.available())
        inputStream.read()
        assertEquals(data.size - 1, inputStream.available())
    }

    @Test
    fun testMarkAndReset() {
        val data = "Hello, World!".toByteArray()
        val inputStream = BufferedInputStream(ByteArrayInputStream(data))

        inputStream.mark(0)
        val result1 = ByteArray(5)
        inputStream.read(result1)
        assertContentEquals("Hello".toByteArray(), result1)

        inputStream.reset()
        val result2 = ByteArray(5)
        inputStream.read(result2)
        assertContentEquals("Hello".toByteArray(), result2)
    }

    @Test
    fun testMarkSupported() {
        val data = "Hello, World!".toByteArray()
        val inputStream = BufferedInputStream(ByteArrayInputStream(data))

        assertTrue(inputStream.markSupported())
    }

    @Test
    fun testClose() {
        val data = "Hello, World!".toByteArray()
        val inputStream = BufferedInputStream(ByteArrayInputStream(data))

        inputStream.close()
        assertFailsWith<IOException> { inputStream.read() }
    }

    @Test
    fun testTransferTo() {
        val data = "Hello, World!".toByteArray()
        val inputStream = BufferedInputStream(ByteArrayInputStream(data))
        val outputStream = ByteArrayOutputStream()

        inputStream.transferTo(outputStream)
        assertContentEquals(data, outputStream.toByteArray())
    }
}
