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
    //abstract fun newDecoder(): CharsetDecoder

    /**
     * Constructs a new encoder for this charset.
     */
    //abstract fun newEncoder(): CharsetEncoder

    /**
     * Tells whether or not this charset supports encoding.
     */
    //open fun canEncode(): Boolean = true

    /**
     * Convenience method that decodes a byte array into a String.
     * Dummy implementation using UTF-8 decoding.
     */
    open fun decode(bytes: ByteArray): String = bytes.decodeToString()

    /**
     * Convenience method that encodes a String into a byte array.
     * Dummy implementation using UTF-8 encoding.
     */
    open fun encode(str: String): ByteArray = str.encodeToByteArray()

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
        val UTF_8: Charset = object : Charset("UTF-8", setOf("UTF8")) {
            override fun contains(cs: Charset): Boolean {
                // UTF-8 contains only itself.
                return cs === this
            }
        }

        val LATIN1: Charset = object : Charset("ISO-8859-1") {
            override fun contains(cs: Charset): Boolean {
                // LATIN1 contains only itself.
                return cs === this
            }

            override fun decode(bytes: ByteArray): String {
                val chars = CharArray(bytes.size) { index ->
                    (bytes[index].toInt() and 0xFF).toChar()
                }

                return chars.concatToString()
            }

            override fun encode(str: String): ByteArray {
                val bytes = ByteArray(str.length) { index ->
                    str[index].code.toByte()
                }

                return bytes
            }
        }

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
        //fun defaultCharset(): Charset = UTF_8
    }
}

/**
 * A minimal dummy exception indicating that the requested charset is not supported.
 */
class UnsupportedCharsetException(charsetName: String) :
    IllegalArgumentException("Unsupported charset: $charsetName")

/**
 * A dummy port of java.nio.charset.CharsetDecoder.
 * This version only supports decoding bytes to String using UTF-8.
 */
abstract class CharsetDecoder {

    /**
     * Decodes the given byte array into a String.
     */
    abstract fun decode(bytes: ByteArray): String


}

/**
 * A dummy port of java.nio.charset.CharsetEncoder.
 * This version only supports encoding String to bytes using UTF-8.
 */
abstract class CharsetEncoder {
    /**
     * Encodes the given String into a byte array.
     */
    abstract fun encode(str: String): ByteArray
}

/**
 * A dummy implementation of CharsetDecoder for UTF-8.
 */
object UTF8Decoder : CharsetDecoder() {
    override fun decode(bytes: ByteArray): String = bytes.decodeToString()
}

/**
 * A dummy implementation of CharsetEncoder for UTF-8.
 */
object UTF8Encoder : CharsetEncoder() {
    override fun encode(str: String): ByteArray = str.encodeToByteArray()
}
