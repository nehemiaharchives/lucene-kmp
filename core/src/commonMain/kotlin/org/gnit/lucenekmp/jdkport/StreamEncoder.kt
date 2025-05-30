package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import kotlin.concurrent.Volatile
import kotlin.math.min


class StreamEncoder : Writer {
    private val logger = KotlinLogging.logger {}

    @Volatile
    private var closed = false

    @Throws(IOException::class)
    private fun ensureOpen() {
        if (closed) throw IOException("Stream closed")
    }

    val encoding: String?
        // -- Public methods corresponding to those in OutputStreamWriter --
        get() {
            if (this.isOpen) return encodingName()
            return null
        }

    @Throws(IOException::class)
    fun flushBuffer() {
        //synchronized(lock) {
            if (this.isOpen) implFlushBuffer()
            else throw IOException("Stream closed")
        //}
    }

    @Throws(IOException::class)
    override fun write(c: Int) {
        val cbuf = CharArray(1)
        cbuf[0] = c.toChar()
        write(cbuf, 0, 1)
    }

    @Throws(IOException::class)
    override fun write(cbuf: CharArray, off: Int, len: Int) {
        //synchronized(lock) {
            ensureOpen()
            if ((off < 0) || (off > cbuf.size) || (len < 0) ||
                ((off + len) > cbuf.size) || ((off + len) < 0)
            ) {
                throw IndexOutOfBoundsException()
            } else if (len == 0) {
                return
            }
            implWrite(cbuf, off, len)
        //}
    }

    @Throws(IOException::class)
    override fun write(str: String, off: Int, len: Int) {
        /* Check the len before creating a char buffer */
        if (len < 0) throw IndexOutOfBoundsException()
        val cbuf = CharArray(len)
        str.toCharArray(cbuf, 0, off, off + len)
        write(cbuf, 0, len)
    }

    @Throws(IOException::class)
    fun write(cb: CharBuffer) {
        val position: Int = cb.position
        try {
            //synchronized(lock) {
            ensureOpen()
            implWrite(cb)
            //}
        } finally {
            cb.position = position
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        //synchronized(lock) {
            ensureOpen()
            implFlush()
        //}
    }

    override fun close() {
        //synchronized(lock) {
            if (closed) return
            try {
                implClose()
            } finally {
                closed = true
            }
        //}
    }

    private val isOpen: Boolean
        get() = !closed


    // -- Charset-based stream encoder impl --
    private val cs: Charset
    private val encoder: CharsetEncoder
    private var bb: ByteBuffer
    private val maxBufferCapacity: Int

    private val out: OutputStream

    // Leftover first char in a surrogate pair
    private var haveLeftoverChar = false
    private var leftoverChar = 0.toChar()
    private var lcb: CharBuffer? = null

    private constructor(out: OutputStream, /*lock: Any,*/ cs: Charset) : this(
        out, /*lock,*/
        cs.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
    )

    /*private constructor(out: OutputStream, lock: Any, enc: CharsetEncoder) : super(lock) {
        this.out = out
        this.cs = enc.charset()
        this.encoder = enc

        this.bb = ByteBuffer.allocate(INITIAL_BYTE_BUFFER_CAPACITY)
        this.maxBufferCapacity = MAX_BYTE_BUFFER_CAPACITY
    }*/

    private constructor(out: OutputStream, enc: CharsetEncoder) : super() {
        this.out = out
        this.cs = enc.charset()
        this.encoder = enc

        this.bb = ByteBuffer.allocate(INITIAL_BYTE_BUFFER_CAPACITY)
        this.maxBufferCapacity = MAX_BYTE_BUFFER_CAPACITY
    }

    @Throws(IOException::class)
    private fun writeBytes() {
        logger.debug {"writeBytes() bb.position=${bb.position}, bb[0]=${bb.array()[0]}" }

        bb.flip()
        val lim: Int = bb.limit
        val pos: Int = bb.position
        assert(pos <= lim)
        val rem = (if (pos <= lim) lim - pos else 0)

        if (rem > 0) {
            out.write(bb.array(), bb.arrayOffset() + pos, rem)
        }
        bb.clear()
    }

    @Throws(IOException::class)
    private fun flushLeftoverChar(cb: CharBuffer?, endOfInput: Boolean) {
        if (!haveLeftoverChar && !endOfInput) return
        if (lcb == null) lcb = CharBuffer.allocate(2)
        else lcb!!.clear()
        if (haveLeftoverChar) lcb!!.put(leftoverChar)
        if ((cb != null) && cb.hasRemaining()) lcb!!.put(cb.get())
        lcb!!.flip()
        while (lcb!!.hasRemaining() || endOfInput) {
            val cr: CoderResult = encoder.encode(lcb!!, bb, endOfInput)
            if (cr.isUnderflow) {
                if (lcb!!.hasRemaining()) {
                    leftoverChar = lcb!!.get()
                    if (cb != null && cb.hasRemaining()) {
                        lcb!!.clear()
                        lcb!!.put(leftoverChar).put(cb.get()).flip()
                        continue
                    }
                    return
                }
                break
            }
            if (cr.isOverflow) {
                assert(bb.position > 0)
                writeBytes()
                continue
            }
            cr.throwException()
        }
        haveLeftoverChar = false
    }

    @Throws(IOException::class)
    fun implWrite(cbuf: CharArray, off: Int, len: Int) {
        val cb: CharBuffer = CharBuffer.wrap(cbuf, off, len)
        implWrite(cb)
    }

    @Throws(IOException::class)
    fun implWrite(cb: CharBuffer) {
        if (haveLeftoverChar) {
            flushLeftoverChar(cb, false)
        }

        growByteBufferIfNeeded(cb.remaining())

        while (cb.hasRemaining()) {
            val cr: CoderResult = encoder.encode(cb, bb, false)
            if (cr.isUnderflow) {
                assert(cb.remaining() <= 1) { cb.remaining() }
                if (cb.remaining() == 1) {
                    haveLeftoverChar = true
                    leftoverChar = cb.get()
                }
                break
            }
            if (cr.isOverflow) {
                assert(bb.position > 0)
                writeBytes()
                continue
            }
            cr.throwException()
        }
    }

    /**
     * Grows bb to a capacity to allow len characters be encoded.
     */
    @Throws(IOException::class)
    fun growByteBufferIfNeeded(len: Int) {
        val cap: Int = bb.capacity
        if (cap < maxBufferCapacity) {
            val maxBytes: Int = len * Math.round(encoder.maxBytesPerChar())
            val newCap = min(maxBytes, maxBufferCapacity)
            if (newCap > cap) {
                implFlushBuffer()
                bb = ByteBuffer.allocate(newCap)
            }
        }
    }

    @Throws(IOException::class)
    fun implFlushBuffer() {
        if (bb.position > 0) {
            writeBytes()
        }
    }

    @Throws(IOException::class)
    fun implFlush() {
        implFlushBuffer()
        out.flush()
    }

    @Throws(IOException::class)
    fun implClose() {
        try {
            out.use {
                flushLeftoverChar(null, true)
                while (true) {
                    val cr: CoderResult = encoder.flush(bb)
                    if (cr.isUnderflow) break
                    if (cr.isOverflow) {
                        assert(bb.position > 0)
                        writeBytes()
                        continue
                    }
                    cr.throwException()
                }

                if (bb.position > 0) writeBytes()
                out.flush()
            }
        } catch (x: IOException) {
            encoder.reset()
            throw x
        }
    }

    fun encodingName(): String? {
        /*return (if (cs is sun.nio.cs.HistoricallyNamedCharset)
            (cs as sun.nio.cs.HistoricallyNamedCharset).historicalName()
        else
            cs.name())*/

        return cs.name()
    }

    companion object {
        private const val INITIAL_BYTE_BUFFER_CAPACITY = 512
        private const val MAX_BYTE_BUFFER_CAPACITY = 8192

        // Factories for OutputStreamWriter
        /*@Throws(UnsupportedEncodingException::class)
        fun forOutputStreamWriter(
            out: OutputStream,
            lock: Any,
            charsetName: String
        ): StreamEncoder {
            try {
                return StreamEncoder(out, lock, Charset.forName(charsetName))
            } catch (x: IllegalCharsetNameException) {
                throw UnsupportedEncodingException(charsetName)
            } catch (x: java.nio.charset.UnsupportedCharsetException) {
                throw UnsupportedEncodingException(charsetName)
            }
        }*/

        fun forOutputStreamWriter(
            out: OutputStream,
            /*lock: Any,*/
            cs: Charset
        ): StreamEncoder {
            return StreamEncoder(out, /*lock,*/ cs)
        }

        /*fun forOutputStreamWriter(
            out: OutputStream,
            lock: Any,
            enc: CharsetEncoder
        ): StreamEncoder {
            return StreamEncoder(out, lock, enc)
        }*/

        fun forOutputStreamWriter(
            out: OutputStream,
            enc: CharsetEncoder
        ): StreamEncoder {
            return StreamEncoder(out, enc)
        }
    }
}
