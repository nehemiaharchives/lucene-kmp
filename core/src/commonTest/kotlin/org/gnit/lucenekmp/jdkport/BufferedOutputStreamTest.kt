package org.gnit.lucenekmp.jdkport

import kotlinx.io.ByteArrayOutputStream
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BufferedOutputStreamTest {

    @Test
    fun testWrite() {
        val data = "Hello, World!".toByteArray()
        val outputStream = ByteArrayOutputStream()
        val bufferedOutputStream = BufferedOutputStream(outputStream)

        bufferedOutputStream.write(data)
        bufferedOutputStream.flush()

        assertContentEquals(data, outputStream.toByteArray())
    }

    @Test
    fun testFlush() {
        val data = "Hello, World!".toByteArray()
        val outputStream = ByteArrayOutputStream()
        val bufferedOutputStream = BufferedOutputStream(outputStream)

        bufferedOutputStream.write(data)
        bufferedOutputStream.flush()

        assertContentEquals(data, outputStream.toByteArray())
    }

    @Test
    fun testClose() {
        val data = "Hello, World!".toByteArray()
        val outputStream = ByteArrayOutputStream()
        val bufferedOutputStream = BufferedOutputStream(outputStream)

        bufferedOutputStream.write(data)
        bufferedOutputStream.close()

        assertFailsWith<IOException> { bufferedOutputStream.write(data) }
    }
}
