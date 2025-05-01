package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterTest {

    @Test
    fun testIsDigit() {
        assertTrue(Character.isDigit('0'.code))
        assertTrue(Character.isDigit('9'.code))
        assertFalse(Character.isDigit('a'.code))
    }

    @Test
    fun testIsLowerCase() {
        assertTrue(Character.isLowerCase('a'.code))
        assertTrue(Character.isLowerCase('z'.code))
        assertFalse(Character.isLowerCase('A'.code))
    }

    @Test
    fun testIsUpperCase() {
        assertTrue(Character.isUpperCase('A'.code))
        assertTrue(Character.isUpperCase('Z'.code))
        assertFalse(Character.isUpperCase('a'.code))
    }

    @Test
    fun testIsWhitespace() {
        assertTrue(Character.isWhitespace(' '.code))
        assertTrue(Character.isWhitespace('\n'.code))
        assertFalse(Character.isWhitespace('a'.code))
    }

    @Test
    fun testIsMirrored() {
        assertTrue(Character.isMirrored('('.code))
        assertTrue(Character.isMirrored(')'.code))
        assertFalse(Character.isMirrored('a'.code))
    }

    @Test
    fun testIsJavaIdentifierStart() {
        assertTrue(Character.isJavaIdentifierStart('a'.code))
        assertTrue(Character.isJavaIdentifierStart('_'.code))
        assertFalse(Character.isJavaIdentifierStart('1'.code))
    }

    @Test
    fun testIsJavaIdentifierPart() {
        assertTrue(Character.isJavaIdentifierPart('a'.code))
        assertTrue(Character.isJavaIdentifierPart('1'.code))
        assertFalse(Character.isJavaIdentifierPart(' '.code))
    }

    @Test
    fun testIsUnicodeIdentifierStart() {
        assertTrue(Character.isUnicodeIdentifierStart('a'.code))
        assertTrue(Character.isUnicodeIdentifierStart('_'.code))
        assertFalse(Character.isUnicodeIdentifierStart('1'.code))
    }

    @Test
    fun testIsUnicodeIdentifierPart() {
        assertTrue(Character.isUnicodeIdentifierPart('a'.code))
        assertTrue(Character.isUnicodeIdentifierPart('1'.code))
        assertFalse(Character.isUnicodeIdentifierPart(' '.code))
    }

    @Test
    fun testIsIdentifierIgnorable() {
        assertTrue(Character.isIdentifierIgnorable(0x0000))
        assertTrue(Character.isIdentifierIgnorable(0x0008))
        assertFalse(Character.isIdentifierIgnorable('a'.code))
    }

    @Test
    fun testIsEmoji() {
        assertTrue(Character.isEmoji(0x1F600))
        assertTrue(Character.isEmoji(0x1F64F))
        assertFalse(Character.isEmoji('a'.code))
    }

    @Test
    fun testIsEmojiPresentation() {
        assertTrue(Character.isEmojiPresentation(0x1F600))
        assertTrue(Character.isEmojiPresentation(0x1F64F))
        assertFalse(Character.isEmojiPresentation('a'.code))
    }

    @Test
    fun testIsEmojiModifier() {
        assertTrue(Character.isEmojiModifier(0x1F3FB))
        assertTrue(Character.isEmojiModifier(0x1F3FF))
        assertFalse(Character.isEmojiModifier('a'.code))
    }

    @Test
    fun testIsEmojiModifierBase() {
        assertTrue(Character.isEmojiModifierBase(0x1F466))
        assertTrue(Character.isEmojiModifierBase(0x1F469))
        assertFalse(Character.isEmojiModifierBase('a'.code))
    }

    @Test
    fun testIsEmojiComponent() {
        assertTrue(Character.isEmojiComponent(0x1F3FB))
        assertTrue(Character.isEmojiComponent(0x1F3FF))
        assertFalse(Character.isEmojiComponent('a'.code))
    }

    @Test
    fun testIsExtendedPictographic() {
        assertTrue(Character.isExtendedPictographic(0x1F600))
        assertTrue(Character.isExtendedPictographic(0x1F64F))
        assertFalse(Character.isExtendedPictographic('a'.code))
    }

    @Test
    fun testToLowerCase() {
        assertEquals('a'.code, Character.toLowerCase('A'.code))
        assertEquals('z'.code, Character.toLowerCase('Z'.code))
        assertEquals('a'.code, Character.toLowerCase('a'.code))
    }

    @Test
    fun testToUpperCase() {
        assertEquals('A'.code, Character.toUpperCase('a'.code))
        assertEquals('Z'.code, Character.toUpperCase('z'.code))
        assertEquals('A'.code, Character.toUpperCase('A'.code))
    }

    @Test
    fun testToTitleCase() {
        assertEquals('A'.code, Character.toTitleCase('a'.code))
        assertEquals('Z'.code, Character.toTitleCase('z'.code))
        assertEquals('A'.code, Character.toTitleCase('A'.code))
    }

    @Test
    fun testDigit() {
        assertEquals(0, Character.digit('0'.code, 10))
        assertEquals(9, Character.digit('9'.code, 10))
        assertEquals(-1, Character.digit('a'.code, 10))
    }

    @Test
    fun testGetNumericValue() {
        assertEquals(0, Character.getNumericValue('0'.code))
        assertEquals(9, Character.getNumericValue('9'.code))
        assertEquals(-1, Character.getNumericValue('a'.code))
    }

    @Test
    fun testGetDirectionality() {
        assertEquals(Character.DIRECTIONALITY_UNDEFINED, Character.getDirectionality(0x0000))
        assertEquals(Character.DIRECTIONALITY_UNDEFINED, Character.getDirectionality(0x0008))
        assertEquals(Character.DIRECTIONALITY_UNDEFINED, Character.getDirectionality('a'.code))
    }

    @Test
    fun testToUpperCaseEx() {
        assertEquals('A'.code, Character.toUpperCaseEx('a'.code))
        assertEquals('Z'.code, Character.toUpperCaseEx('z'.code))
        assertEquals('A'.code, Character.toUpperCaseEx('A'.code))
    }

    @Test
    fun testToUpperCaseCharArray() {
        assertEquals(charArrayOf('A'), Character.toUpperCaseCharArray('a'.code))
        assertEquals(charArrayOf('Z'), Character.toUpperCaseCharArray('z'.code))
        assertEquals(charArrayOf('A'), Character.toUpperCaseCharArray('A'.code))
    }

    @Test
    fun testIsOtherAlphabetic() {
        assertTrue(Character.isOtherAlphabetic('a'.code))
        assertTrue(Character.isOtherAlphabetic('z'.code))
        assertFalse(Character.isOtherAlphabetic('1'.code))
    }

    @Test
    fun testIsIdeographic() {
        assertTrue(Character.isIdeographic(0x4E00))
        assertTrue(Character.isIdeographic(0x9FFF))
        assertFalse(Character.isIdeographic('a'.code))
    }
}
