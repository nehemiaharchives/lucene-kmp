package org.gnit.lucenekmp.jdkport

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
        val US_ASCII: Charset = org.gnit.lucenekmp.jdkport.US_ASCII()

        /**
         * ISO Latin Alphabet No. 1, also known as ISO-LATIN-1.
         */
        val ISO_8859_1: Charset = org.gnit.lucenekmp.jdkport.ISO_8859_1()

        /**
         * Eight-bit UCS Transformation Format.
         */
        val UTF_8: Charset = org.gnit.lucenekmp.jdkport.UTF_8()

        /**
         * Sixteen-bit UCS Transformation Format, big-endian byte order.
         */
        val UTF_16BE: Charset = org.gnit.lucenekmp.jdkport.UTF_16BE()

        /**
         * Windows-1251 (CP1251).
         */
        val WINDOWS_1251: Charset = org.gnit.lucenekmp.jdkport.CP1251()

        /**
         * GB2312 (EUC-CN repertoire).
         */
        val GB2312: Charset = org.gnit.lucenekmp.jdkport.GB2312()

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

        fun aliases_ISO_8859_1(): Set<String> {
            return setOf(
                "iso-ir-100",
                "ISO_8859-1",
                "latin1",
                "l1",
                "IBM819",
                "cp819",
                "csISOLatin1",
                "819",
                "IBM-819",
                "ISO8859_1",
                "ISO_8859-1:1987",
                "ISO_8859_1",
                "8859_1",
                "ISO8859-1",
            )
        }

        fun aliases_UTF_8(): Set<String> {
            return setOf(
                "UTF8",
                "unicode-1-1-utf-8",
            )
        }

        fun aliases_UTF_16BE(): Set<String> {
            return setOf(
                "UTF_16BE",
                "ISO-10646-UCS-2",
                "X-UTF-16BE",
                "UnicodeBigUnmarked",
            )
        }

        fun aliases_CP1251(): Set<String> {
            return setOf(
                "windows-1251",
                "cp1251",
                "CP1251",
                "Cp1251",
                "MS1251",
                "ansi-1251",
                "WINDOWS-1251"
            )
        }

        fun aliases_GB2312(): Set<String> {
            return setOf(
                "GB2312",
                "gb2312",
                "EUC_CN",
                "EUC-CN",
                "csGB2312",
            )
        }

        fun aliases_US_ASCII(): Set<String> {
            return setOf(
                "us-ascii",
                "ASCII",
                "646",
                "iso646-us",
                "us",
                "ANSI_X3.4-1968",
                "ANSI_X3.4-1986",
                "ISO_646.irv:1991",
                "ISO646-US",
                "IBM367",
                "cp367",
                "csASCII"
            )
        }
    }
}
