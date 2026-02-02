package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import kotlin.concurrent.Volatile


class StreamDecoder : Reader {
    private val logger = KotlinLogging.logger {}

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

    override fun read(): Int {
        //logger.debug { "StreamDecoder.read() called" }
        // Return the leftover char, if there is one
        if (haveLeftoverChar) {
            //logger.debug { "StreamDecoder.read() returning leftover char: ${leftoverChar.code}" }
            haveLeftoverChar = false
            return leftoverChar.code
        }

        // Convert more bytes
        val cb = CharArray(2)
        //logger.debug { "StreamDecoder.read() calling read(cb, 0, 2)" }
        val n = read(cb, 0, 2)
        //logger.debug { "StreamDecoder.read() returning n: $n" }
        when (n) {
            -1 -> {
                //logger.debug { "StreamDecoder.read() returning -1 (EOF)" }
                return -1
            }

            2 -> {
                //logger.debug { "StreamDecoder.read() returning ${cb[0].code}, saving leftover char: ${cb[1].code}" }
                leftoverChar = cb[1]
                haveLeftoverChar = true
                return cb[0].code
            }

            1 -> {
                //logger.debug { "StreamDecoder.read() returning ${cb[0].code}" }
                return cb[0].code
            }

            0 -> {
                // If no characters were read but we're not at EOF, try again
                // This helps handle cases where decoder.decode() didn't fully decode the bytes
                //logger.debug { "StreamDecoder.read() got zero chars, trying again" }
                return read()
            }

            else -> {
                require(false) { n }
                return -1
            }
        }
    }

    override fun read(cbuf: CharArray, offset: Int, length: Int): Int {
        //logger.debug { "StreamDecoder.read(${cbuf.toCodeString()}, $offset, $length) called" }
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
            //logger.debug { "StreamDecoder.read(cbuf, offset, length) using leftover char: ${leftoverChar.code}" }
            cbuf[off] = leftoverChar
            off++
            len--
            haveLeftoverChar = false
            n = 1
            if ((len == 0) || !implReady()) {
                //logger.debug { "StreamDecoder.read(cbuf, offset, length) returning $n (only leftover char)" }
                return n
            }
        }

        // Always use implRead for remaining chars (len >= 1)
        //logger.debug { "StreamDecoder.read(cbuf, offset, length) calling implRead(${cbuf.toCodeString()}, $off, ${off + len})" }
        val nr = implRead(cbuf, off, off + len)
        //logger.debug { "StreamDecoder.read(cbuf, offset, length) implRead returned $nr" }

        // If leftover char was used and implRead returns -1 (EOF), return n (do not lose leftover char)
        val result = if (nr < 0) (if (n == 1) 1 else -1) else (n + nr)
        //logger.debug { "StreamDecoder.read(cbuf, offset, length) returning $result" }
        return result
    }

    @Throws(IOException::class)
    override fun ready(): Boolean {
        ensureOpen()
        return haveLeftoverChar || implReady()
    }

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
    ){
        //logger.debug { "StreamDecoder constructor called with Charset: ${cs.name()}"  }
    }

    internal constructor(`in`: InputStream?, lock: Any, dec: CharsetDecoder) : super(/*lock*/) {
        //logger.debug { "StreamDecoder constructor called with CharsetDecoder from Charset: ${dec.charset().name()}" }

        this.cs = dec.charset()
        this.decoder = dec
        this.`in` = `in`
        //this.ch = null
        this.bb = ByteBuffer.allocate(DEFAULT_BYTE_BUFFER_SIZE).apply {
            position(0)
            limit(0)
        }

        logger.debug { "ByteBuffer created: capacity=${bb.capacity}, position=${bb.position}, limit=${bb.limit}" }
        // Don't flip the buffer initially, so it can be filled with bytes
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
        //logger.debug { "readBytes() called" }
        //logger.debug { "Before compact: bb.position=${bb.position}, bb.limit=${bb.limit}, bb.capacity=${bb.capacity}" }
        bb.compact()
        //logger.debug { "Before readBytes: bb.position=${bb.position}, bb.limit=${bb.limit}, bb.capacity=${bb.capacity}" }
        try {
            // Read from the input stream, and then update the buffer
            val lim: Int = bb.limit
            val pos: Int = bb.position
            require(pos <= lim)
            val rem = (if (pos <= lim) lim - pos else 0)
            //logger.debug { "Before read: lim=$lim, pos=$pos, rem=$rem, arrayOffset=${bb.arrayOffset()}" }

            //logger.debug { "ByteBuffer array content before read: ${bb.array().joinToString(", ") { it.toString() }}" }

            // This is the critical line where reading happens
            val bytesRead = if (`in` != null) {
                // Log the input stream details
                //logger.debug { "Reading from input stream: ${`in`!!::class.simpleName}" }

                // Attempt to read directly from input stream
                val tempBytes = ByteArray(rem)
                val read = `in`!!.read(tempBytes, 0, rem)
                //logger.debug { "Raw input stream read result: $read bytes" }

                if (read > 0) {
                    //logger.debug { "Bytes read from input stream: ${tempBytes.slice(0 until read).joinToString(", ") { it.toString() }}" }

                    // Copy to our buffer
                    //System.arraycopy(tempBytes, 0, bb.array(), bb.arrayOffset() + pos, read)
                    for(i in 0 until read){
                        bb.put(pos + i, tempBytes[i])
                    }
                    //logger.debug { "Copied bytes to ByteBuffer at position $pos" }

                    read
                } else {
                    read
                }
            } else -1

            //logger.debug { "Bytes read: $bytesRead" }

            // Add debug after read to verify the buffer was updated
            //logger.debug { "ByteBuffer array after read: ${bb.array().joinToString(", ") { it.toString() }}" }

            if (bytesRead < 0) return bytesRead
            if (bytesRead == 0) throw IOException("Underlying input stream returned zero bytes")
            require(bytesRead <= rem) { "bytesRead = $bytesRead, rem = $rem" }

            bb.position(pos + bytesRead)
            //logger.debug { "After setting position: bb.position=${bb.position}, bb.limit=${bb.limit}" }

        } finally {
            // Flip even when an IOException is thrown,
            // otherwise the stream will stutter
            //logger.debug { "Before flip: bb.position=${bb.position}, bb.limit=${bb.limit}" }
            bb.flip()
            //logger.debug { "After flip: bb.position=${bb.position}, bb.limit=${bb.limit}" }
        }

        val rem: Int = bb.remaining()
        //logger.debug { "Final ByteBuffer remaining: $rem" }
        //logger.debug { "Final ByteBuffer content: ${bb.array().slice(bb.position until bb.limit).joinToString(", ") { it.toString() }}" }

        require(rem != 0) { rem }
        return rem
    }

    @Throws(IOException::class)
    fun implRead(cbuf: CharArray, off: Int, end: Int): Int {
        //logger.debug { "StreamDecoder.implRead(${cbuf.toCodeString()}, $off, $end) called" }

        // In order to handle surrogate pairs, this method requires that
        // the invoker attempt to read at least two characters.  Saving the
        // extra character, if any, at a higher level is easier than trying
        // to deal with it here.

        require(end - off > 1)

        var cb: CharBuffer = CharBuffer.wrap(cbuf, off, end - off)
        //logger.debug { "CharBuffer contents before implRead: ${cb.array().slice(cb.position until cb.limit).toCharArray().toCodeString()}" }
        if (cb.position() != 0) {
            // Ensure that cb[0] == cbuf[off]
            cb = cb.slice()
        }

        var eof = false
        while (true) {
            //logger.debug { "ByteBuffer contents before decode: " + bb.array().slice(bb.position until bb.limit).joinToString { it.toUByte().toString() } }
            val cr: CoderResult = decoder.decode(bb, cb, eof)
            //logger.debug { "CharBuffer contents after decode: " + CharArray(cb.remaining()) { i -> cb.get(cb.position() + i) }.joinToString { "${it.code}" } }
            //logger.debug { "CoderResult cr:$cr isUnderflow: ${cr.isUnderflow}, cb.position=${cb.position()}, cb.remaining=${cb.remaining()}, eof=$eof" }
            if (cr.isUnderflow) {
                if (eof) break
                if (!cb.hasRemaining()) break
                if ((cb.position() > 0) && !inReady()) break // Block at most once

                //logger.debug { "implRead: calling readBytes()" }
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
