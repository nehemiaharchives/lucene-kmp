package org.gnit.lucenekmp.queryparser.charstream

import okio.IOException
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.fromCharArray

/**
 * An efficient implementation of JavaCC's CharStream interface.
 *
 *
 * Note that this does not do line-number counting, but instead keeps track of the character
 * position of the token in the input, as required by Lucene's [ ] API.
 */
class FastCharStream(r: Reader) : CharStream {
    var buffer: CharArray? = null

    var bufferLength: Int = 0 // end of valid chars
    var bufferPosition: Int = 0 // next char to read

    var tokenStart: Int = 0 // offset in buffer
    var bufferStart: Int = 0 // position in file of buffer

    var input: Reader = r // source of chars

    @Throws(IOException::class)
    override fun readChar(): Char {
        if (bufferPosition >= bufferLength) refill()
        return buffer!![bufferPosition++]
    }

    @Throws(IOException::class)
    private fun refill() {
        val newPosition = bufferLength - tokenStart

        if (tokenStart == 0) { // token won't fit in buffer
            if (buffer == null) { // first time: alloc buffer
                buffer = CharArray(2048)
            } else if (bufferLength == buffer!!.size) { // grow buffer
                val newBuffer = CharArray(buffer!!.size * 2)
                System.arraycopy(buffer!!, 0, newBuffer, 0, bufferLength)
                buffer = newBuffer
            }
        } else { // shift token to front
            System.arraycopy(buffer!!, tokenStart, buffer!!, 0, newPosition)
        }

        bufferLength = newPosition // update state
        bufferPosition = newPosition
        bufferStart += tokenStart
        tokenStart = 0

        val charsRead: Int =  // fill space in buffer
            input.read(buffer!!, newPosition, buffer!!.size - newPosition)
        if (charsRead == -1) throw READ_PAST_EOF
        else bufferLength += charsRead
    }

    @Throws(IOException::class)
    override fun BeginToken(): Char {
        tokenStart = bufferPosition
        return readChar()
    }

    override fun backup(amount: Int) {
        bufferPosition -= amount
    }

    override fun GetImage(): String {
        return String.fromCharArray(buffer!!, tokenStart, bufferPosition - tokenStart)
    }

    override fun GetSuffix(len: Int): CharArray {
        val value = CharArray(len)
        System.arraycopy(buffer!!, bufferPosition - len, value, 0, len)
        return value
    }

    override fun Done() {
        try {
            input.close()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override val endColumn: Int
        get() = bufferStart + bufferPosition

    override val endLine: Int
        get() = 1

    override val beginColumn: Int
        get() = bufferStart + tokenStart

    override val beginLine: Int
        get() = 1

    companion object {
        // See SOLR-11314
        private val READ_PAST_EOF: IOException = IOException("Read past EOF.")
    }
}
