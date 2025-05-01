package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterDataUndefinedTest {

    @Test
    fun testGetProperties() {
        assertEquals(0, CharacterDataUndefined.instance.getProperties(0))
        assertEquals(0, CharacterDataUndefined.instance.getProperties(33))
        assertEquals(0, CharacterDataUndefined.instance.getProperties(36))
    }

    @Test
    fun testGetType() {
        assertEquals(Character.UNASSIGNED.toInt(), CharacterDataUndefined.instance.getType(0))
        assertEquals(Character.UNASSIGNED.toInt(), CharacterDataUndefined.instance.getType(33))
        assertEquals(Character.UNASSIGNED.toInt(), CharacterDataUndefined.instance.getType(36))
    }

    @Test
    fun testIsDigit() {
        assertFalse(CharacterDataUndefined.instance.isDigit('0'.code))
        assertFalse(CharacterDataUndefined.instance.isDigit('9'.code))
        assertFalse(CharacterDataUndefined.instance.isDigit('a'.code))
    }

    @Test
    fun testIsLowerCase() {
        assertFalse(CharacterDataUndefined.instance.isLowerCase('a'.code))
        assertFalse(CharacterDataUndefined.instance.isLowerCase('z'.code))
        assertFalse(CharacterDataUndefined.instance.isLowerCase('A'.code))
    }

    @Test
    fun testIsUpperCase() {
        assertFalse(CharacterDataUndefined.instance.isUpperCase('A'.code))
        assertFalse(CharacterDataUndefined.instance.isUpperCase('Z'.code))
        assertFalse(CharacterDataUndefined.instance.isUpperCase('a'.code))
    }

    @Test
    fun testIsWhitespace() {
        assertFalse(CharacterDataUndefined.instance.isWhitespace(' '.code))
        assertFalse(CharacterDataUndefined.instance.isWhitespace('\n'.code))
        assertFalse(CharacterDataUndefined.instance.isWhitespace('a'.code))
    }

    @Test
    fun testIsMirrored() {
        assertFalse(CharacterDataUndefined.instance.isMirrored('('.code))
        assertFalse(CharacterDataUndefined.instance.isMirrored(')'.code))
        assertFalse(CharacterDataUndefined.instance.isMirrored('a'.code))
    }

    @Test
    fun testIsJavaIdentifierStart() {
        assertFalse(CharacterDataUndefined.instance.isJavaIdentifierStart('a'.code))
        assertFalse(CharacterDataUndefined.instance.isJavaIdentifierStart('_'.code))
        assertFalse(CharacterDataUndefined.instance.isJavaIdentifierStart('1'.code))
    }

    @Test
    fun testIsJavaIdentifierPart() {
        assertFalse(CharacterDataUndefined.instance.isJavaIdentifierPart('a'.code))
        assertFalse(CharacterDataUndefined.instance.isJavaIdentifierPart('1'.code))
        assertFalse(CharacterDataUndefined.instance.isJavaIdentifierPart(' '.code))
    }

    @Test
    fun testIsUnicodeIdentifierStart() {
        assertFalse(CharacterDataUndefined.instance.isUnicodeIdentifierStart('a'.code))
        assertFalse(CharacterDataUndefined.instance.isUnicodeIdentifierStart('_'.code))
        assertFalse(CharacterDataUndefined.instance.isUnicodeIdentifierStart('1'.code))
    }

    @Test
    fun testIsUnicodeIdentifierPart() {
        assertFalse(CharacterDataUndefined.instance.isUnicodeIdentifierPart('a'.code))
        assertFalse(CharacterDataUndefined.instance.isUnicodeIdentifierPart('1'.code))
        assertFalse(CharacterDataUndefined.instance.isUnicodeIdentifierPart(' '.code))
    }

    @Test
    fun testIsIdentifierIgnorable() {
        assertFalse(CharacterDataUndefined.instance.isIdentifierIgnorable(0x0000))
        assertFalse(CharacterDataUndefined.instance.isIdentifierIgnorable(0x0008))
        assertFalse(CharacterDataUndefined.instance.isIdentifierIgnorable('a'.code))
    }

    @Test
    fun testIsEmoji() {
        assertFalse(CharacterDataUndefined.instance.isEmoji(0x1F600))
        assertFalse(CharacterDataUndefined.instance.isEmoji(0x1F64F))
        assertFalse(CharacterDataUndefined.instance.isEmoji('a'.code))
    }

    @Test
    fun testIsEmojiPresentation() {
        assertFalse(CharacterDataUndefined.instance.isEmojiPresentation(0x1F600))
        assertFalse(CharacterDataUndefined.instance.isEmojiPresentation(0x1F64F))
        assertFalse(CharacterDataUndefined.instance.isEmojiPresentation('a'.code))
    }

    @Test
    fun testIsEmojiModifier() {
        assertFalse(CharacterDataUndefined.instance.isEmojiModifier(0x1F3FB))
        assertFalse(CharacterDataUndefined.instance.isEmojiModifier(0x1F3FF))
        assertFalse(CharacterDataUndefined.instance.isEmojiModifier('a'.code))
    }

    @Test
    fun testIsEmojiModifierBase() {
        assertFalse(CharacterDataUndefined.instance.isEmojiModifierBase(0x1F466))
        assertFalse(CharacterDataUndefined.instance.isEmojiModifierBase(0x1F469))
        assertFalse(CharacterDataUndefined.instance.isEmojiModifierBase('a'.code))
    }

    @Test
    fun testIsEmojiComponent() {
        assertFalse(CharacterDataUndefined.instance.isEmojiComponent(0x1F3FB))
        assertFalse(CharacterDataUndefined.instance.isEmojiComponent(0x1F3FF))
        assertFalse(CharacterDataUndefined.instance.isEmojiComponent('a'.code))
    }

    @Test
    fun testIsExtendedPictographic() {
        assertFalse(CharacterDataUndefined.instance.isExtendedPictographic(0x1F600))
        assertFalse(CharacterDataUndefined.instance.isExtendedPictographic(0x1F64F))
        assertFalse(CharacterDataUndefined.instance.isExtendedPictographic('a'.code))
    }

    @Test
    fun testToLowerCase() {
        assertEquals('A'.code, CharacterDataUndefined.instance.toLowerCase('A'.code))
        assertEquals('Z'.code, CharacterDataUndefined.instance.toLowerCase('Z'.code))
        assertEquals('a'.code, CharacterDataUndefined.instance.toLowerCase('a'.code))
    }

    @Test
    fun testToUpperCase() {
        assertEquals('A'.code, CharacterDataUndefined.instance.toUpperCase('A'.code))
        assertEquals('Z'.code, CharacterDataUndefined.instance.toUpperCase('Z'.code))
        assertEquals('a'.code, CharacterDataUndefined.instance.toUpperCase('a'.code))
    }

    @Test
    fun testToTitleCase() {
        assertEquals('A'.code, CharacterDataUndefined.instance.toTitleCase('A'.code))
        assertEquals('Z'.code, CharacterDataUndefined.instance.toTitleCase('Z'.code))
        assertEquals('a'.code, CharacterDataUndefined.instance.toTitleCase('a'.code))
    }

    @Test
    fun testDigit() {
        assertEquals(-1, CharacterDataUndefined.instance.digit('0'.code, 10))
        assertEquals(-1, CharacterDataUndefined.instance.digit('9'.code, 10))
        assertEquals(-1, CharacterDataUndefined.instance.digit('a'.code, 10))
    }

    @Test
    fun testGetNumericValue() {
        assertEquals(-1, CharacterDataUndefined.instance.getNumericValue('0'.code))
        assertEquals(-1, CharacterDataUndefined.instance.getNumericValue('9'.code))
        assertEquals(-1, CharacterDataUndefined.instance.getNumericValue('a'.code))
    }

    @Test
    fun testGetDirectionality() {
        assertEquals(Character.DIRECTIONALITY_UNDEFINED, CharacterDataUndefined.instance.getDirectionality(0x0000))
        assertEquals(Character.DIRECTIONALITY_UNDEFINED, CharacterDataUndefined.instance.getDirectionality(0x0008))
        assertEquals(Character.DIRECTIONALITY_UNDEFINED, CharacterDataUndefined.instance.getDirectionality('a'.code))
    }

    @Test
    fun testToUpperCaseEx() {
        assertEquals('A'.code, CharacterDataUndefined.instance.toUpperCaseEx('A'.code))
        assertEquals('Z'.code, CharacterDataUndefined.instance.toUpperCaseEx('Z'.code))
        assertEquals('a'.code, CharacterDataUndefined.instance.toUpperCaseEx('a'.code))
    }

    @Test
    fun testToUpperCaseCharArray() {
        assertEquals(null, CharacterDataUndefined.instance.toUpperCaseCharArray('A'.code))
        assertEquals(null, CharacterDataUndefined.instance.toUpperCaseCharArray('Z'.code))
        assertEquals(null, CharacterDataUndefined.instance.toUpperCaseCharArray('a'.code))
    }

    @Test
    fun testIsOtherAlphabetic() {
        assertFalse(CharacterDataUndefined.instance.isOtherAlphabetic('a'.code))
        assertFalse(CharacterDataUndefined.instance.isOtherAlphabetic('z'.code))
        assertFalse(CharacterDataUndefined.instance.isOtherAlphabetic('1'.code))
    }

    @Test
    fun testIsIdeographic() {
        assertFalse(CharacterDataUndefined.instance.isIdeographic(0x4E00))
        assertFalse(CharacterDataUndefined.instance.isIdeographic(0x9FFF))
        assertFalse(CharacterDataUndefined.instance.isIdeographic('a'.code))
    }
}
