package org.gnit.lucenekmp.store

import okio.Buffer
import okio.EOFException
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestInputStreamDataInput : LuceneTestCase() {
    private lateinit var randomData: ByteArray
    private lateinit var input: InputStreamDataInput

    private fun byteArrayInputStream(data: ByteArray): InputStream {
        val source = Buffer().apply { write(data) }
        return OkioSourceInputStream(source)
    }

    @BeforeTest
    fun setUp() {
        randomData = ByteArray(atLeast(100))
        random().nextBytes(randomData)
        input = NoReadInputStreamDataInput(byteArrayInputStream(randomData))
    }

    @AfterTest
    fun tearDown() {
        input.close()
    }

    @Test
    fun testSkipBytes() {
        val random = random()
        val `in` = InputStreamDataInput(byteArrayInputStream(randomData))
        val maxSkipTo = randomData.size - 1
        var curr = 0
        while (curr < maxSkipTo) {
            val skipTo = TestUtil.nextInt(random, curr, maxSkipTo)
            val step = skipTo - curr
            `in`.skipBytes(step.toLong())
            assertEquals(randomData[skipTo], `in`.readByte())
            curr = skipTo + 1
        }
        `in`.close()
    }

    @Test
    fun testNoReadWhenSkipping() {
        val random = random()
        val maxSkipTo = randomData.size - 1
        var curr = 0
        while (curr < maxSkipTo) {
            val step = TestUtil.nextInt(random, 0, maxSkipTo - curr)
            input.skipBytes(step.toLong())
            curr += step
        }
    }

    @Test
    fun testFullSkip() {
        input.skipBytes(randomData.size.toLong())
    }

    @Test
    fun testSkipOffEnd() {
        assertFailsWith<EOFException> {
            input.skipBytes(randomData.size.toLong() + 1)
        }
    }

    private class NoReadInputStreamDataInput(stream: InputStream) : InputStreamDataInput(stream) {
        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            throw UnsupportedOperationException()
        }

        override fun readByte(): Byte {
            throw UnsupportedOperationException()
        }
    }
}

