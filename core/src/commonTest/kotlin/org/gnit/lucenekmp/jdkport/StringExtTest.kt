package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals

class StringExtTest {
    val logger = KotlinLogging.logger {}

    @Test
    fun testCodePointsBasicMultiLingualPlane() {
        val s = "Hello\u00A9" // '©' is 0xA9
        val expected: Sequence<Int> = sequenceOf(0x48, 0x65, 0x6C, 0x6C, 0x6F, 0xA9)
        val result: Sequence<Int> = s.codePointSequence()
        assertContentEquals(expected, result)

        assertContentEquals(sequenceOf(0x03A9), "Ω".codePointSequence())
        assertContentEquals(sequenceOf(0x8A9E), "語".codePointSequence())
    }

    @Test
    fun testCodePointsSurrogatePair() {
        val s = "\uD83D\uDE00" // '😀' is a surrogate pair
        val expected: Sequence<Int> = sequenceOf(0x1F600)
        val result: Sequence<Int> = s.codePointSequence()
        assertContentEquals(expected, result)

        //Unpaired high surrogate – “\uD83E” → [0xD83E]
        assertContentEquals(
            sequenceOf(0xD83E),
            "\uD83E".codePointSequence()
        )

        //High surrogate + wrong follower – “\uD83E\u0061” → [0xD83E, 97]
        assertContentEquals(
            sequenceOf(0xD83E, 97),
            "\uD83E\u0061".codePointSequence()
        )
    }

    @Test
    fun testToByteArray(){
        val s = "Hello, 世界"
        val utf8Bytes = s.toByteArray(Charset.UTF_8)
        val latin1Bytes = s.toByteArray(Charset.ISO_8859_1)

        val expectedUtf8 = byteArrayOf(
            0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x2C, 0x20,
            0xE4.toByte(), 0xB8.toByte(), 0x96.toByte(),
            0xE7.toByte(), 0x95.toByte(), 0x8C.toByte()
        )
        val expectedLatin1 = byteArrayOf(
            0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x2C, 0x20,
            0x3F, 0x3F // '世' and '界' cannot be represented in Latin-1
        )

        assertContentEquals(expectedUtf8, utf8Bytes)
        assertContentEquals(expectedLatin1, latin1Bytes)
    }

    @Test
    fun testFromCharArrayLatin1() {
        val arr = charArrayOf('a', 'b', 'c', 'd', 'e')
        val str = String.fromCharArray(arr, 1, 3)
        // Should be "bcd"
        assertEquals("bcd", str)
    }

    /**
     * Α	GREEK CAPITAL LETTER ALPHA (U+0391)	feff0391
     * Β	GREEK CAPITAL LETTER BETA (U+0392)	feff0392
     * Γ	GREEK CAPITAL LETTER GAMMA (U+0393)	feff0393
     * Δ	GREEK CAPITAL LETTER DELTA (U+0394)	feff0394
     * Ε	GREEK CAPITAL LETTER EPSILON (U+0395)	feff0395
     */
    @Test
    fun testFromCharArrayUTF16() {
        val arr = charArrayOf(
            '\u0391', // 'Α'
            '\u0392', // 'Β'
            '\u0393', // 'Γ'
            '\u0394', // 'Δ'
            '\u0395'  // 'Ε'
        )
        val str = String.fromCharArray(arr, 1, 3)
        val codePoints = str.codePointSequence().joinToString(",")
        logger.debug { "codePoints: $codePoints" }
        assertEquals("ΒΓΔ", str)
    }

    @Test
    fun testFromCharArrayInvalidRange() {
        val arr = charArrayOf('a', 'b', 'c')
        assertFailsWith<Exception> {
            String.fromCharArray(arr, -1, 2)
        }
        assertFailsWith<Exception> {
            String.fromCharArray(arr, 2, 5)
        }
    }

    @Test
    fun testFromByteArrayLatin1() {
        val bytes = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F) // "Hello"
        val str = String.fromByteArray(bytes, Charset.ISO_8859_1)
        assertEquals("Hello", str)
    }

    @Test
    fun testFromByteArrayUtf8() {
        val bytes = byteArrayOf(
            0xCE.toByte(), 0x91.toByte(), // 'Α' (U+0391)
            0xCE.toByte(), 0x92.toByte(), // 'Β' (U+0392)
            0xCE.toByte(), 0x93.toByte(), // 'Γ' (U+0393)
            0xCE.toByte(), 0x94.toByte(), // 'Δ' (U+0394)
            0xCE.toByte(), 0x95.toByte()  // 'Ε' (U+0395)
        )
        val str = String.fromByteArray(bytes, Charset.UTF_8)
        assertEquals("ΑΒΓΔΕ", str)
    }

    @Test
    fun testFromByteArrayUtf8TwoByteBoundaryLatin1Range() {
        val bytes = byteArrayOf(
            0xC2.toByte(), 0x80.toByte(), // U+0080
            0xC2.toByte(), 0xA9.toByte(), // U+00A9
            0xC3.toByte(), 0xBF.toByte()  // U+00FF
        )

        val str = String.fromByteArray(bytes, Charset.UTF_8)
        assertEquals("\u0080\u00A9\u00FF", str)
    }

    @Test
    fun testFromByteArrayLatin1UnsignedUpperHalf() {
        val bytes = byteArrayOf(
            0x7F,
            0x80.toByte(),
            0xFF.toByte()
        )

        val str = String.fromByteArray(bytes, Charset.ISO_8859_1)
        assertEquals("\u007F\u0080\u00FF", str)
    }

    @Test
    fun testFromByteArrayUtf8SupplementaryCodePoint() {
        val bytes = byteArrayOf(
            0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte() // U+1F600
        )

        val str = String.fromByteArray(bytes, Charset.UTF_8)
        assertEquals("\uD83D\uDE00", str)
    }

    @Test
    fun testFromByteArrayWithOffsetAndLength() {
        val prefix = byteArrayOf(0x00, 0x00)
        val bytes = byteArrayOf(
            0xCE.toByte(), 0x91.toByte(), // 'Α'
            0xCE.toByte(), 0x92.toByte(), // 'Β'
            0xCE.toByte(), 0x93.toByte()  // 'Γ'
        )
        val suffix = byteArrayOf(0x00)
        val all = prefix + bytes + suffix

        val str = String.fromByteArray(all, 2, bytes.size, Charset.UTF_8)
        assertEquals("ΑΒΓ", str)
    }
}
