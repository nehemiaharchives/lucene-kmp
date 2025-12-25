package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.math.min
import kotlin.random.Random


/** Wraps a Reader, and can throw random or fixed exceptions, and spoon feed read chars.  */
class MockReaderWrapper(
    private val random: Random,
    private val `in`: Reader) : Reader() {

    private var excAtChar = -1
    private var readSoFar = 0
    private var throwExcNext = false

    /** Throw an exception after reading this many chars.  */
    fun throwExcAfterChar(charUpto: Int) {
        excAtChar = charUpto
        // You should only call this on init!:
        assert(readSoFar == 0)
    }

    fun throwExcNext() {
        throwExcNext = true
    }

    @Throws(IOException::class)
    override fun close() {
        `in`.close()
    }

    @Throws(IOException::class)
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (throwExcNext || (excAtChar != -1 && readSoFar >= excAtChar)) {
            throw RuntimeException("fake exception now!")
        }
        val read: Int
        val realLen: Int
        if (len == 1) {
            realLen = 1
        } else {
            // Spoon-feed: intentionally maybe return less than
            // the consumer asked for
            realLen = TestUtil.nextInt(random, 1, len)
        }
        if (excAtChar != -1) {
            val left = excAtChar - readSoFar
            assert(left != 0)
            read = `in`.read(cbuf, off, min(realLen, left))
            assert(read != -1)
            readSoFar += read
        } else {
            read = `in`.read(cbuf, off, realLen)
        }
        return read
    }

    fun markSupported(): Boolean {
        return false
    }

    override fun ready(): Boolean {
        return false
    }

    companion object {
        fun isMyEvilException(t: Throwable): Boolean {
            return (t is RuntimeException) && "fake exception now!" == t.message
        }
    }
}
