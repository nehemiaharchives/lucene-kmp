package org.gnit.lucenekmp.jdkport

/**
 * A minimal multiplatform dummy port of java.nio.charset.Charset.
 * This implementation supports only UTF-8.
 */
abstract class Charset protected constructor(
    private val canonicalName: String,
    private val aliases: Set<String> = emptySet()
) : Comparable<Charset> {

    /**
     * Returns this charset's canonical name.
     */
    fun name(): String = canonicalName

    /**
     * Returns an immutable set containing this charset's aliases.
     */
    //fun aliases(): Set<String> = aliases

    /**
     * Returns this charset's human-readable name for the default locale.
     */
    //fun displayName(): String = canonicalName

    /**
     * Returns this charset's human-readable name for the given locale.
     * The locale parameter is ignored in this dummy implementation.
     */
    //fun displayName(locale: Any): String = canonicalName

    /**
     * Tells whether or not this charset contains the given charset.
     * Since only UTF-8 is supported, it returns true if and only if [cs] is this.
     */
    abstract fun contains(cs: Charset): Boolean

    /**
     * Constructs a new decoder for this charset.
     */
    abstract fun newDecoder(): CharsetDecoder

    /**
     * Constructs a new encoder for this charset.]
     */
    abstract fun newEncoder(): CharsetEncoder

    /**
     * Tells whether or not this charset supports encoding.
     */
    //open fun canEncode(): Boolean = true

    /**
     * Convenience method that decodes bytes in this charset into Unicode
     * characters.
     *
     *
     *  An invocation of this method upon a charset `cs` returns the
     * same result as the expression
     *
     * {@snippet lang=java :
     * *     cs.newDecoder()
     * *       .onMalformedInput(CodingErrorAction.REPLACE)
     * *       .onUnmappableCharacter(CodingErrorAction.REPLACE)
     * *       .decode(bb);
     * * }
     *
     * except that it is potentially more efficient because it can cache
     * decoders between successive invocations.
     *
     *
     *  This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement byte array.  In order
     * to detect such sequences, use the [ ][CharsetDecoder.decode] method directly.
     *
     * @param  bb  The byte buffer to be decoded
     *
     * @return  A char buffer containing the decoded characters
     */
    fun decode(bb: ByteBuffer): CharBuffer {
        return newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(bb)
    }

    /**
     * Convenience method that encodes Unicode characters into bytes in this
     * charset.
     *
     *
     *  An invocation of this method upon a charset `cs` returns the
     * same result as the expression
     *
     * {@snippet lang=java :
     * *     cs.newEncoder()
     * *       .onMalformedInput(CodingErrorAction.REPLACE)
     * *       .onUnmappableCharacter(CodingErrorAction.REPLACE)
     * *       .encode(bb);
     * * }
     *
     * except that it is potentially more efficient because it can cache
     * encoders between successive invocations.
     *
     *
     *  This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  In order to
     * detect such sequences, use the [ ][CharsetEncoder.encode] method directly.
     *
     * @param  cb  The char buffer to be encoded
     *
     * @return  A byte buffer containing the encoded characters
     */
    fun encode(cb: CharBuffer): ByteBuffer{
        return newEncoder().onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .encode(cb)
    }

    /**
     * Compares this charset to another by comparing their canonical names (ignoring case).
     */
    override fun compareTo(other: Charset): Int =
        canonicalName.compareTo(other.canonicalName, ignoreCase = true)

    override fun equals(other: Any?): Boolean {
        return this === other || (other is Charset &&
                this.canonicalName.equals(other.canonicalName, ignoreCase = true))
    }

    override fun hashCode(): Int = canonicalName.lowercase().hashCode()

    override fun toString(): String = canonicalName

    companion object {
        /**
         * The only supported charset instance: UTF-8.
         */
        val UTF_8: Charset = org.gnit.lucenekmp.jdkport.UTF_8()

        val ISO_8859_1: Charset = org.gnit.lucenekmp.jdkport.ISO_8859_1()

        /**
         * Returns a charset object for the named charset.
         * Only "UTF-8" (case-insensitive) is supported.
         *
         * @throws UnsupportedCharsetException if the name is not "UTF-8" or "UTF8".
         */
        /*fun forName(charsetName: String): Charset {
            if (charsetName.equals("UTF-8", ignoreCase = true) ||
                charsetName.equals("UTF8", ignoreCase = true)
            ) {
                return UTF_8
            }
            throw UnsupportedCharsetException(charsetName)
        }*/

        /**
         * Tells whether the named charset is supported.
         */
        /*fun isSupported(charsetName: String): Boolean {
            return charsetName.equals("UTF-8", ignoreCase = true) ||
                    charsetName.equals("UTF8", ignoreCase = true)
        }*/

        /**
         * Returns the default charset. Always UTF-8.
         */
        fun defaultCharset(): Charset = UTF_8
    }
}

/**
 * A minimal dummy exception indicating that the requested charset is not supported.
 */
class UnsupportedCharsetException(charsetName: String) :
    IllegalArgumentException("Unsupported charset: $charsetName")

