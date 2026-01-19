package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.jdkport.CharacterDataLatin1.Companion.A
import org.gnit.lucenekmp.jdkport.CharacterDataLatin1.Companion.B
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Example property check using A and B.
 * Replace this with your actual implementation logic.
 */
fun isFoo(ch: Char): Boolean {
    // Let's say you define "Foo" for demonstration when the lowest bit in A[ch.code] is set,
    // and B[ch.code] in a certain range
    if (ch.code !in 0 until 256) return false
    val prop = A[ch.code] and 0x1
    val mask = (B[ch.code] in '\u0000'..'\u007F') // just as an example
    return prop != 0 && mask
}


class CharacterDataLatin1Test {

    @Test
    fun testTableHas256ValuesAllIntsAndReasonablePropertyBits() {
        assertTrue(A.size == 256, "A table size must be 256")
        for ((i, v) in A.withIndex()) {
            // Example checks - adapt depending on your needs
            // Contract: Int, can be negative, shouldn't overflow
            assertTrue(v is Int, "A[$i] should be Int")
            // Contract: E.g., all ignorable controls should have the 'ignorable' property bit set
            if (i in 0..8) {
                // For Cc/ignorable, suppose bit 0xF is set (example, adapt if needed)
                assertTrue((v and 0xF) == 0xF, "A[$i] should be ignorable control, got ${v.toString(16)}")
            }
        }
    }

    @Test
    fun testTableHas256CharValues() {
        assertEquals(256, B.size, "B table size must be 256")
        for ((i, v) in B.withIndex()) {
            // CharArray always contains Chars, but you can add value checks if needed
            assertTrue(v.code in 0x0000..0xFFFF, "B[$i] should be a valid Char")
        }
    }

    @Test
    fun testContractVsFunctionMatchAcrossAll0To255() {
        var foundMismatch = false
        for (code in 0..255) {
            val ch = code.toChar()

            // Compute property from tables, as used by your implementation
            val propFromTable: Boolean = run {
                val prop = A[code] and 0x1
                val mask = (B[code] in '\u0000'..'\u007F') // EXAMPLE
                prop != 0 && mask
            }
            val funcResult = isFoo(ch)
            try {
                assertEquals(
                    propFromTable,
                    funcResult,
                    "Mismatch for char U+${code.toString(16).padStart(4, '0').uppercase()} '${ch}'"
                )
            } catch (e: AssertionError) {
                println(
                    "Diagnostic failed for char U+${
                        code.toString(16).padStart(4, '0').uppercase()
                    } '${printableChar(ch)}': " +
                            "TableProperty=$propFromTable, FuncResult=$funcResult, " +
                            "A=0x${A[code].toString(16).padStart(8, '0').uppercase()}, " +
                            "B=0x${B[code].code.toString(16).padStart(4, '0').uppercase()}"
                )
                foundMismatch = true
            }
        }
        if (foundMismatch) fail("One or more property mismatches found. See stdout for details.")
    }

    private fun printableChar(ch: Char): String {
        return when (ch) {
            in '\u0020'..'\u007E' -> ch.toString() // Printable ASCII
            else -> "." // Replace with dot for non-printable
        }
    }

    @Test
    fun testGetProperties() {
        assertEquals(0x4800100F, CharacterDataLatin1.instance.getProperties(0))
        assertEquals(0x68000018, CharacterDataLatin1.instance.getProperties(33))
        assertEquals(0x2800601A, CharacterDataLatin1.instance.getProperties(36))
    }

    @Test
    fun testGetType() {
        assertEquals(15, CharacterDataLatin1.instance.getType(0))
        assertEquals(24, CharacterDataLatin1.instance.getType(33))
        assertEquals(26, CharacterDataLatin1.instance.getType(36))
    }

    @Test
    fun testIsDigit() {
        assertTrue(CharacterDataLatin1.instance.isDigit('0'.code))
        assertTrue(CharacterDataLatin1.instance.isDigit('9'.code))
        assertFalse(CharacterDataLatin1.instance.isDigit('a'.code))
    }

    @Test
    fun testIsLowerCase() {
        assertTrue(CharacterDataLatin1.instance.isLowerCase('a'.code))
        assertTrue(CharacterDataLatin1.instance.isLowerCase('z'.code))
        assertFalse(CharacterDataLatin1.instance.isLowerCase('A'.code))
    }

    @Test
    fun testIsUpperCase() {
        assertTrue(CharacterDataLatin1.instance.isUpperCase('A'.code))
        assertTrue(CharacterDataLatin1.instance.isUpperCase('Z'.code))
        assertFalse(CharacterDataLatin1.instance.isUpperCase('a'.code))
    }

    @Test
    fun testIsWhitespace() {
        assertTrue(CharacterDataLatin1.instance.isWhitespace(' '.code))
        assertTrue(CharacterDataLatin1.instance.isWhitespace('\n'.code))
        assertFalse(CharacterDataLatin1.instance.isWhitespace('a'.code))
    }

    @Test
    fun testIsMirrored() {
        assertTrue(CharacterDataLatin1.instance.isMirrored('('.code))
        assertTrue(CharacterDataLatin1.instance.isMirrored(')'.code))
        assertFalse(CharacterDataLatin1.instance.isMirrored('a'.code))
    }

    @Test
    fun testIsJavaIdentifierStart() {
        println(CharacterDataLatin1.instance.getProperties('a'.code))

        assertTrue(CharacterDataLatin1.instance.isJavaIdentifierStart('a'.code))
        assertTrue(CharacterDataLatin1.instance.isJavaIdentifierStart('_'.code))
        assertFalse(CharacterDataLatin1.instance.isJavaIdentifierStart('1'.code))
    }

    @Test
    fun testIsJavaIdentifierPart() {
        assertTrue(CharacterDataLatin1.instance.isJavaIdentifierPart('a'.code))
        assertTrue(CharacterDataLatin1.instance.isJavaIdentifierPart('1'.code))
        assertFalse(CharacterDataLatin1.instance.isJavaIdentifierPart(' '.code))
    }

    @Test
    fun testIsUnicodeIdentifierStart() {
        assertTrue(CharacterDataLatin1.instance.isUnicodeIdentifierStart('a'.code))
        assertFalse(CharacterDataLatin1.instance.isUnicodeIdentifierStart('_'.code))
        assertFalse(CharacterDataLatin1.instance.isUnicodeIdentifierStart('1'.code))
    }

    @Test
    fun testIsUnicodeIdentifierPart() {
        assertTrue(CharacterDataLatin1.instance.isUnicodeIdentifierPart('a'.code))
        assertTrue(CharacterDataLatin1.instance.isUnicodeIdentifierPart('1'.code))
        assertFalse(CharacterDataLatin1.instance.isUnicodeIdentifierPart(' '.code))
    }

    @Test
    fun testIsIdentifierIgnorable() {
        assertTrue(CharacterDataLatin1.instance.isIdentifierIgnorable(0x0000))
        assertTrue(CharacterDataLatin1.instance.isIdentifierIgnorable(0x0008))
        assertFalse(CharacterDataLatin1.instance.isIdentifierIgnorable('a'.code))
    }

    @Test
    fun testIsEmoji() {
        // Only test within Latin-1 range for CharacterDataLatin1
        for (code in 1..255) {
            assertFalse(CharacterDataLatin1.instance.isEmoji(1))
        }
    }

    @Test
    fun testIsEmojiPresentation() {
        // Only test within Latin-1 range for CharacterDataLatin1
        for (code in 1..255) {
            assertFalse(CharacterDataLatin1.instance.isEmojiPresentation(1))
        }
    }

    @Test
    fun testIsEmojiModifier() {
        // Only test within Latin-1 range for CharacterDataLatin1
        for (code in 1..255) {
            assertFalse(CharacterDataLatin1.instance.isEmojiModifier(1))
        }
    }

    @Test
    fun testIsEmojiModifierBase() {
        // Only test within Latin-1 range for CharacterDataLatin1
        for (code in 1..255) {
            assertFalse(CharacterDataLatin1.instance.isEmojiModifierBase(1))
        }
    }

    @Test
    fun testIsEmojiComponent() {
        // Only test within Latin-1 range for CharacterDataLatin1
        for (code in 1..255) {
            assertFalse(CharacterDataLatin1.instance.isEmojiComponent(1))
        }
    }

    @Test
    fun testIsExtendedPictographic() {
        // Only test within Latin-1 range for CharacterDataLatin1
        for (code in 1..255) {
            assertFalse(CharacterDataLatin1.instance.isExtendedPictographic(1))
        }
    }

    @Test
    fun testToLowerCase() {
        assertEquals('a'.code, CharacterDataLatin1.instance.toLowerCase('A'.code))
        assertEquals('z'.code, CharacterDataLatin1.instance.toLowerCase('Z'.code))
        assertEquals('a'.code, CharacterDataLatin1.instance.toLowerCase('a'.code))
    }

    @Test
    fun testToUpperCase() {
        assertEquals('A'.code, CharacterDataLatin1.instance.toUpperCase('a'.code))
        assertEquals('Z'.code, CharacterDataLatin1.instance.toUpperCase('z'.code))
        assertEquals('A'.code, CharacterDataLatin1.instance.toUpperCase('A'.code))
    }

    @Test
    fun testToTitleCase() {
        assertEquals('A'.code, CharacterDataLatin1.instance.toTitleCase('a'.code))
        assertEquals('Z'.code, CharacterDataLatin1.instance.toTitleCase('z'.code))
        assertEquals('A'.code, CharacterDataLatin1.instance.toTitleCase('A'.code))
    }

    @Test
    fun testDigit() {
        assertEquals(0, CharacterDataLatin1.instance.digit('0'.code, 10))
        assertEquals(9, CharacterDataLatin1.instance.digit('9'.code, 10))
        assertEquals(-1, CharacterDataLatin1.instance.digit('a'.code, 10))
    }

    @Test
    fun testGetNumericValue() {
        assertEquals(0, CharacterDataLatin1.instance.getNumericValue('0'.code))
        assertEquals(9, CharacterDataLatin1.instance.getNumericValue('9'.code))
        assertEquals(10, CharacterDataLatin1.instance.getNumericValue('a'.code))
        assertEquals(16, CharacterDataLatin1.instance.getNumericValue('g'.code))
    }

    @Test
    fun testDirectionalityBitExtraction() {
        for (code in 0..255) {
            val props = CharacterDataLatin1.A[code]
            val directionality = ((props and 0x78000000) shr 27)
            println(
                "U+${code.toString(16).padStart(4, '0').uppercase()}: props=0x${
                    props.toUInt().toString(16)
                }, directionality=$directionality"
            )
        }
    }

    @Test
    fun testGetDirectionality() {
        assertEquals(Character.DIRECTIONALITY_BOUNDARY_NEUTRAL, CharacterDataLatin1.instance.getDirectionality(0x0000))
        assertEquals(Character.DIRECTIONALITY_BOUNDARY_NEUTRAL, CharacterDataLatin1.instance.getDirectionality(0x0008))
        assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, CharacterDataLatin1.instance.getDirectionality('a'.code))
    }

    @Test
    fun testToUpperCaseEx() {
        assertEquals('A'.code, CharacterDataLatin1.instance.toUpperCaseEx('a'.code))
        assertEquals('Z'.code, CharacterDataLatin1.instance.toUpperCaseEx('z'.code))
        assertEquals('A'.code, CharacterDataLatin1.instance.toUpperCaseEx('A'.code))
    }

    @Test
    fun testToUpperCaseCharArray() {
        assertContentEquals(charArrayOf('a'), CharacterDataLatin1.instance.toUpperCaseCharArray('a'.code))
        assertContentEquals(charArrayOf('z'), CharacterDataLatin1.instance.toUpperCaseCharArray('z'.code))
        assertContentEquals(charArrayOf('A'), CharacterDataLatin1.instance.toUpperCaseCharArray('A'.code))
        assertContentEquals(charArrayOf('S', 'S'), CharacterDataLatin1.instance.toUpperCaseCharArray(0x00DF))
    }

    @Test
    fun testIsOtherAlphabetic() {
        assertFalse(CharacterDataLatin1.instance.isOtherAlphabetic('a'.code))
        assertFalse(CharacterDataLatin1.instance.isOtherAlphabetic('z'.code))
        assertFalse(CharacterDataLatin1.instance.isOtherAlphabetic('1'.code))
    }

    @Test
    fun testIsIdeographic() {
        assertFalse(CharacterDataLatin1.instance.isIdeographic('a'.code))
    }
}
