package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.jdkport.UTF_8.Companion.INSTANCE


/**
 * ported from java.nio.StandardCharsets
 *
 * Constant definitions for the standard [charsets][Charset]. These
 * charsets are guaranteed to be available on every implementation of the Java
 * platform.
 *
 * @see [Standard Charsets](Charset.html.standard)
 *
 * @since 1.7
 */
class StandardCharsets private constructor() {
    // To avoid accidental eager initialization of often unused Charsets
    // from happening while the VM is booting up, which may delay
    // initialization of VM components, we should generally avoid depending
    // on this class from elsewhere in java.base.
    init {
        throw AssertionError("No java.nio.charset.StandardCharsets instances for you!")
    }

    companion object {
        /**
         * Seven-bit ASCII, also known as ISO646-US, also known as the
         * Basic Latin block of the Unicode character set.
         */
        //val US_ASCII: Charset = sun.nio.cs.US_ASCII.INSTANCE

        /**
         * ISO Latin Alphabet No. 1, also known as ISO-LATIN-1.
         */
        //val ISO_8859_1: Charset = sun.nio.cs.ISO_8859_1.INSTANCE

        /**
         * Eight-bit UCS Transformation Format.
         */
        val UTF_8: Charset = INSTANCE

        /**
         * Sixteen-bit UCS Transformation Format, big-endian byte order.
         */
        //val UTF_16BE: Charset = sun.nio.cs.UTF_16BE()

        /**
         * Sixteen-bit UCS Transformation Format, little-endian byte order.
         */
        //val UTF_16LE: Charset = sun.nio.cs.UTF_16LE()

        /**
         * Sixteen-bit UCS Transformation Format, byte order identified by an
         * optional byte-order mark.
         */
        //val UTF_16: Charset = sun.nio.cs.UTF_16()

        /**
         * Thirty-two-bit UCS Transformation Format, big-endian byte order.
         * @since 22
         */
        //val UTF_32BE: Charset = sun.nio.cs.UTF_32BE()

        /**
         * Thirty-two-bit UCS Transformation Format, little-endian byte order.
         * @since 22
         */
        //val UTF_32LE: Charset = sun.nio.cs.UTF_32LE()

        /**
         * Thirty-two-bit UCS Transformation Format, byte order identified by an
         * optional byte-order mark.
         * @since 22
         */
        //val UTF_32: Charset = sun.nio.cs.UTF_32()

        fun aliases_UTF_8(): Set<String> {
            return setOf(
                "UTF8",
                "unicode-1-1-utf-8",
            )
        }
    }
}
