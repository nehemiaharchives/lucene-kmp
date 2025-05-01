package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterDataLatin1Test {

    @Test
    fun testGetProperties() {
        assertEquals(0x4800100F, CharacterDataLatin1.instance.getProperties(0))
        assertEquals(0x68000018, CharacterDataLatin1.instance.getProperties(33))
        assertEquals(0x2800601A, CharacterDataLatin1.instance.getProperties(36))
    }

    @Test
    fun testGetType() {
        assertEquals(15, CharacterDataLatin1.instance.getType(0))
        assertEquals(18, CharacterDataLatin1.instance.getType(33))
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
        assertTrue(CharacterDataLatin1.instance.isUnicodeIdentifierStart('_'.code))
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
        assertTrue(CharacterDataLatin1.instance.isEmoji(0x1F600))
        assertTrue(CharacterDataLatin1.instance.isEmoji(0x1F64F))
        assertFalse(CharacterDataLatin1.instance.isEmoji('a'.code))
    }

    @Test
    fun testIsEmojiPresentation() {
        assertTrue(CharacterDataLatin1.instance.isEmojiPresentation(0x1F600))
        assertTrue(CharacterDataLatin1.instance.isEmojiPresentation(0x1F64F))
        assertFalse(CharacterDataLatin1.instance.isEmojiPresentation('a'.code))
    }

    @Test
    fun testIsEmojiModifier() {
        assertTrue(CharacterDataLatin1.instance.isEmojiModifier(0x1F3FB))
        assertTrue(CharacterDataLatin1.instance.isEmojiModifier(0x1F3FF))
        assertFalse(CharacterDataLatin1.instance.isEmojiModifier('a'.code))
    }

    @Test
    fun testIsEmojiModifierBase() {
        assertTrue(CharacterDataLatin1.instance.isEmojiModifierBase(0x1F466))
        assertTrue(CharacterDataLatin1.instance.isEmojiModifierBase(0x1F469))
        assertFalse(CharacterDataLatin1.instance.isEmojiModifierBase('a'.code))
    }

    @Test
    fun testIsEmojiComponent() {
        assertTrue(CharacterDataLatin1.instance.isEmojiComponent(0x1F3FB))
        assertTrue(CharacterDataLatin1.instance.isEmojiComponent(0x1F3FF))
        assertFalse(CharacterDataLatin1.instance.isEmojiComponent('a'.code))
    }

    @Test
    fun testIsExtendedPictographic() {
        assertTrue(CharacterDataLatin1.instance.isExtendedPictographic(0x1F600))
        assertTrue(CharacterDataLatin1.instance.isExtendedPictographic(0x1F64F))
        assertFalse(CharacterDataLatin1.instance.isExtendedPictographic('a'.code))
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
        assertEquals(-1, CharacterDataLatin1.instance.getNumericValue('a'.code))
    }

    @Test
    fun testGetDirectionality() {
        assertEquals(Character.DIRECTIONALITY_UNDEFINED, CharacterDataLatin1.instance.getDirectionality(0x0000))
        assertEquals(Character.DIRECTIONALITY_UNDEFINED, CharacterDataLatin1.instance.getDirectionality(0x0008))
        assertEquals(Character.DIRECTIONALITY_UNDEFINED, CharacterDataLatin1.instance.getDirectionality('a'.code))
    }

    @Test
    fun testToUpperCaseEx() {
        assertEquals('A'.code, CharacterDataLatin1.instance.toUpperCaseEx('a'.code))
        assertEquals('Z'.code, CharacterDataLatin1.instance.toUpperCaseEx('z'.code))
        assertEquals('A'.code, CharacterDataLatin1.instance.toUpperCaseEx('A'.code))
    }

    @Test
    fun testToUpperCaseCharArray() {
        assertEquals(charArrayOf('A'), CharacterDataLatin1.instance.toUpperCaseCharArray('a'.code))
        assertEquals(charArrayOf('Z'), CharacterDataLatin1.instance.toUpperCaseCharArray('z'.code))
        assertEquals(charArrayOf('A'), CharacterDataLatin1.instance.toUpperCaseCharArray('A'.code))
    }

    @Test
    fun testIsOtherAlphabetic() {
        assertTrue(CharacterDataLatin1.instance.isOtherAlphabetic('a'.code))
        assertTrue(CharacterDataLatin1.instance.isOtherAlphabetic('z'.code))
        assertFalse(CharacterDataLatin1.instance.isOtherAlphabetic('1'.code))
    }

    @Test
    fun testIsIdeographic() {
        assertTrue(CharacterDataLatin1.instance.isIdeographic(0x4E00))
        assertTrue(CharacterDataLatin1.instance.isIdeographic(0x9FFF))
        assertFalse(CharacterDataLatin1.instance.isIdeographic('a'.code))
    }
}
