package org.gnit.lucenekmp.jdkport

import kotlinx.io.IOException
import kotlin.concurrent.Volatile


class StreamDecoder : Reader {
    @Volatile
    private var closed = false

    @Throws(IOException::class)
    private fun ensureOpen() {
        if (closed) throw IOException("Stream closed")
    }

    // In order to handle surrogates properly we must never try to produce
    // fewer than two characters at a time.  If we're only asked to return one
    // character then the other is saved here to be returned later.
    //
    private var haveLeftoverChar = false
    private var leftoverChar = 0.toChar()


    val encoding: String?
        // -- Public methods corresponding to those in InputStreamReader --
        get() {
            if (this.isOpen) return encodingName()
            return null
        }

    fun getEncoding() = encoding

    @Throws(IOException::class)
    override fun read(): Int {
        // Return the leftover char, if there is one
        if (haveLeftoverChar) {
            haveLeftoverChar = false
            return leftoverChar.code
        }

        // Convert more bytes
        val cb = CharArray(2)
        val n = read(cb, 0, 2)
        when (n) {
            -1 -> return -1
            2 -> {
                leftoverChar = cb[1]
                haveLeftoverChar = true
                return cb[0].code
            }

            1 -> return cb[0].code
            else -> {
                require(false) { n }
                return -1
            }
        }
    }

    @Throws(IOException::class)
    override fun read(cbuf: CharArray, offset: Int, length: Int): Int {
        var off = offset
        var len = length

        ensureOpen()
        if ((off < 0) || (off > cbuf.size) || (len < 0) ||
            ((off + len) > cbuf.size) || ((off + len) < 0)
        ) {
            throw IndexOutOfBoundsException()
        }
        if (len == 0) return 0

        var n = 0

        if (haveLeftoverChar) {
            // Copy the leftover char into the buffer
            cbuf[off] = leftoverChar
            off++
            len--
            haveLeftoverChar = false
            n = 1
            if ((len == 0) || !implReady())  // Return now if this is all we can produce w/o blocking
                return n
        }

        if (len == 1) {
            // Treat single-character array reads just like read()
            val c = read()
            if (c == -1) return if (n == 0) -1 else n
            cbuf[off] = c.toChar()
            return n + 1
        }

        // Read remaining characters
        val nr = implRead(cbuf, off, off + len)

        // At this point, n is either 1 if a leftover character was read,
        // or 0 if no leftover character was read. If n is 1 and nr is -1,
        // indicating EOF, then we don't return their sum as this loses data.
        return if (nr < 0) (if (n == 1) 1 else nr) else (n + nr)
    }

    @Throws(IOException::class)
    override fun ready(): Boolean {
        ensureOpen()
        return haveLeftoverChar || implReady()
    }

    @Throws(IOException::class)
    override fun close() {
        if (closed) return
        try {
            implClose()
        } finally {
            closed = true
        }
    }

    private val isOpen: Boolean
        get() = !closed

    @Throws(IOException::class)
    fun fillZeroToPosition() {
        Arrays.fill(bb.array(), bb.arrayOffset(), bb.arrayOffset() + bb.position, 0.toByte())
    }

    // -- Charset-based stream decoder impl --
    private val cs: Charset
    private val decoder: CharsetDecoder
    private val bb: ByteBuffer

    // Exactly one of these is non-null
    private val `in`: InputStream?
    //private val ch: ReadableByteChannel?

    internal constructor(`in`: InputStream?, lock: Any, cs: Charset) : this(
        `in`, lock,
        cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
    )

    internal constructor(`in`: InputStream?, lock: Any, dec: CharsetDecoder) : super(/*lock*/) {
        this.cs = dec.charset()
        this.decoder = dec
        this.`in` = `in`
        //this.ch = null
        this.bb = ByteBuffer.allocate(DEFAULT_BYTE_BUFFER_SIZE)
        bb.flip() // So that bb is initially empty
    }

    /*internal constructor(ch: ReadableByteChannel?, dec: CharsetDecoder, mbc: Int) {
        this.`in` = null
        this.ch = ch
        this.decoder = dec
        this.cs = dec.charset()
        this.bb = ByteBuffer.allocate(
            if (mbc < 0)
                DEFAULT_BYTE_BUFFER_SIZE
            else
                (if (mbc < MIN_BYTE_BUFFER_SIZE)
                    MIN_BYTE_BUFFER_SIZE
                else
                    mbc)
        )
        bb.flip()
    }*/

    @Throws(IOException::class)
    private fun readBytes(): Int {
        bb.compact()
        try {
            if (/*ch != null*/false) {
                // Read from the channel

                // unreachable code because the lucene kmp port don't use channels so far, implement if needed

                /*val n: Int = ch.read(bb)
                if (n < 0) return n*/

            } else {
                // Read from the input stream, and then update the buffer
                val lim: Int = bb.limit
                val pos: Int = bb.position
                require(pos <= lim)
                val rem = (if (pos <= lim) lim - pos else 0)
                val n: Int = `in`!!.read(bb.array(), bb.arrayOffset() + pos, rem)
                if (n < 0) return n
                if (n == 0) throw IOException("Underlying input stream returned zero bytes")
                require(n <= rem) { "n = $n, rem = $rem" }
                bb.position(pos + n)
            }
        } finally {
            // Flip even when an IOException is thrown,
            // otherwise the stream will stutter
            bb.flip()
        }

        val rem: Int = bb.remaining()
        require(rem != 0) { rem }
        return rem
    }

    @Throws(IOException::class)
    fun implRead(cbuf: CharArray, off: Int, end: Int): Int {
        // In order to handle surrogate pairs, this method requires that
        // the invoker attempt to read at least two characters.  Saving the
        // extra character, if any, at a higher level is easier than trying
        // to deal with it here.

        require(end - off > 1)

        var cb: CharBuffer = CharBuffer.wrap(cbuf, off, end - off)
        if (cb.position() != 0) {
            // Ensure that cb[0] == cbuf[off]
            cb = cb.slice()
        }

        var eof = false
        while (true) {
            val cr: CoderResult = decoder.decode(bb, cb, eof)
            if (cr.isUnderflow) {
                if (eof) break
                if (!cb.hasRemaining()) break
                if ((cb.position() > 0) && !inReady()) break // Block at most once

                val n = readBytes()
                if (n < 0) {
                    eof = true
                    if ((cb.position() == 0) && (!bb.hasRemaining())) break
                }
                continue
            }
            if (cr.isOverflow) {
                require(cb.position() > 0)
                break
            }
            cr.throwException()
        }

        if (eof) {
            // ## Need to flush decoder
            decoder.reset()
        }

        if (cb.position() == 0) {
            if (eof) {
                return -1
            }
            require(false)
        }
        return cb.position()
    }

    fun encodingName(): String? {
        /*return (if (cs is HistoricallyNamedCharset)
            (cs as HistoricallyNamedCharset).historicalName()
        else
            cs.name())*/

        return cs.name()
    }

    private fun inReady(): Boolean {
        try {
            return (((`in` != null) && (`in`.available() > 0))
                    /*|| (ch is FileChannel)*/) // ## RBC.available()?
        } catch (x: IOException) {
            return false
        }
    }

    fun implReady(): Boolean {
        return bb.hasRemaining() || inReady()
    }

    @Throws(IOException::class)
    fun implClose() {
        if (/*ch != null*/false) {
            //ch.close()
        } else {
            `in`!!.close()
        }
    }

    companion object {
        private const val MIN_BYTE_BUFFER_SIZE = 32
        private const val DEFAULT_BYTE_BUFFER_SIZE = 8192

        // Factories for InputStreamReader
        /*@Throws(UnsupportedEncodingException::class)
        fun forInputStreamReader(
            `in`: InputStream?,
            lock: Any,
            charsetName: String
        ): StreamDecoder {
            try {
                return StreamDecoder(`in`, lock, Charset.forName(charsetName))
            } catch (x: IllegalCharsetNameException) {
                throw UnsupportedEncodingException(charsetName)
            } catch (x: java.nio.charset.UnsupportedCharsetException) {
                throw UnsupportedEncodingException(charsetName)
            }
        }*/

        fun forInputStreamReader(
            `in`: InputStream?,
            lock: Any,
            cs: Charset
        ): StreamDecoder {
            return StreamDecoder(`in`, lock, cs)
        }

        fun forInputStreamReader(
            `in`: InputStream?,
            lock: Any,
            dec: CharsetDecoder
        ): StreamDecoder {
            return StreamDecoder(`in`, lock, dec)
        }


        // Factory for java.nio.channels.Channels.newReader
        /*fun forDecoder(
            ch: ReadableByteChannel?,
            dec: CharsetDecoder,
            minBufferCap: Int
        ): StreamDecoder {
            return StreamDecoder(ch, dec, minBufferCap)
        }*/
    }
}
