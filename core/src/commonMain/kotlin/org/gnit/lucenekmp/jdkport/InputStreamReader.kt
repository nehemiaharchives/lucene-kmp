package org.gnit.lucenekmp.jdkport

import kotlinx.io.IOException


/**
 * port of InputStreamReader
 *
 * An InputStreamReader is a bridge from byte streams to character streams: It
 * reads bytes and decodes them into characters using a specified [ ].  The charset that it uses
 * may be specified by name or may be given explicitly, or the
 * [default charset][Charset.defaultCharset] may be used.
 *
 *
 *  Each invocation of one of an InputStreamReader's read() methods may
 * cause one or more bytes to be read from the underlying byte-input stream.
 * To enable the efficient conversion of bytes to characters, more bytes may
 * be read ahead from the underlying stream than are necessary to satisfy the
 * current read operation.
 *
 *
 *  For top efficiency, consider wrapping an InputStreamReader within a
 * BufferedReader.  For example:
 *
 * {@snippet lang=java :
 * *     BufferedReader in = new BufferedReader(new InputStreamReader(anInputStream));
 * * }
 *
 * @see BufferedReader
 *
 * @see InputStream
 *
 * @see Charset
 *
 *
 * @author      Mark Reinhold
 * @since       1.1
 */
open class InputStreamReader : Reader {
    private val sd: StreamDecoder

    /**
     * Creates an InputStreamReader that uses the
     * [default charset][Charset.defaultCharset].
     *
     * @param  in   An InputStream
     *
     * @see Charset.defaultCharset
     */
    constructor(`in`: InputStream) : super(/*`in`*/) {
        val cs: Charset = Charset.defaultCharset()
        sd = StreamDecoder.forInputStreamReader(`in`, /*lockFor(this)*/ this , cs)
    }

    /**
     * Creates an InputStreamReader that uses the named charset.
     *
     * @param  in
     * An InputStream
     *
     * @param  charsetName
     * The name of a supported [charset][Charset]
     *
     * @throws     UnsupportedEncodingException
     * If the named charset is not supported
     */
    /*constructor(`in`: InputStream, charsetName: String) : super(*//*`in`*//*) {
        //if (charsetName == null) throw java.lang.NullPointerException("charsetName")
        sd = StreamDecoder.forInputStreamReader(`in`, *//*lockFor(this)*//* this , charsetName)
    }*/

    /**
     * Creates an InputStreamReader that uses the given charset.
     *
     * @param  in       An InputStream
     * @param  cs       A charset
     *
     * @since 1.4
     */
    constructor(`in`: InputStream, cs: Charset) : super(/*`in`*/) {
        //if (cs == null) throw java.lang.NullPointerException("charset")
        sd = StreamDecoder.forInputStreamReader(`in`, /*lockFor(this)*/ this , cs)
    }

    /**
     * Creates an InputStreamReader that uses the given charset decoder.
     *
     * @param  in       An InputStream
     * @param  dec      A charset decoder
     *
     * @since 1.4
     */
    constructor(`in`: InputStream, dec: CharsetDecoder) : super(/*`in`*/) {
        //if (dec == null) throw java.lang.NullPointerException("charset decoder")
        sd = StreamDecoder.forInputStreamReader(`in`, /*lockFor(this)*/ this , dec)
    }

    val encoding: String?
        /**
         * Returns the name of the character encoding being used by this stream.
         *
         *
         *  If the encoding has an historical name then that name is returned;
         * otherwise the encoding's canonical name is returned.
         *
         *
         *  If this instance was created with the [ ][.InputStreamReader] constructor then the returned
         * name, being unique for the encoding, may differ from the name passed to
         * the constructor. This method will return `null` if the
         * stream has been closed.
         *
         * @return The historical name of this encoding, or
         * `null` if the stream has been closed
         *
         * @see Charset
         */
        get() = sd.encoding

    override fun read(target: CharBuffer): Int {
        return sd.read(target)
    }

    /**
     * Reads a single character.
     *
     * @return The character read, or -1 if the end of the stream has been
     * reached
     *
     * @throws     IOException  If an I/O error occurs
     */
    override fun read(): Int {
        return sd.read()
    }

    /**
     * {@inheritDoc}
     * @throws     IndexOutOfBoundsException  {@inheritDoc}
     */
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        return sd.read(cbuf, off, len)
    }

    /**
     * Tells whether this stream is ready to be read.  An InputStreamReader is
     * ready if its input buffer is not empty, or if bytes are available to be
     * read from the underlying byte stream.
     *
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun ready(): Boolean {
        return sd.ready()
    }

    override fun close() {
        sd.close()
    }

    /*companion object {
        *//**
         * Return the lock object for the given reader's stream decoder.
         * If the reader type is trusted then an internal lock can be used. If the
         * reader type is not trusted then the reader object is the lock.
         *//*
        private fun lockFor(reader: InputStreamReader): Any {
            val clazz: java.lang.Class<*> = reader.javaClass
            if (clazz == InputStreamReader::class.java || clazz == java.io.FileReader::class.java) {
                return InternalLock.newLockOr(reader)
            } else {
                return reader
            }
        }
    }*/
}
