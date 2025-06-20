package org.gnit.lucenekmp.jdkport


/** The CharacterData class encapsulates the large tables found in
 * Java.lang.Character.  */
internal class CharacterDataPrivateUse private constructor() : CharacterData() {
    override fun getProperties(ch: Int): Int {
        return 0
    }

    override fun getType(ch: Int): Int {
        return (if ((ch and 0xFFFE) == 0xFFFE)
            Character.UNASSIGNED
        else
            Character.PRIVATE_USE).toInt()
    }

    override fun isJavaIdentifierStart(ch: Int): Boolean {
        return false
    }

    override fun isJavaIdentifierPart(ch: Int): Boolean {
        return false
    }

    override fun isUnicodeIdentifierStart(ch: Int): Boolean {
        return false
    }

    override fun isUnicodeIdentifierPart(ch: Int): Boolean {
        return false
    }

    override fun isIdentifierIgnorable(ch: Int): Boolean {
        return false
    }

    override fun isEmoji(ch: Int): Boolean {
        return false
    }

    override fun isEmojiPresentation(ch: Int): Boolean {
        return false
    }

    override fun isEmojiModifier(ch: Int): Boolean {
        return false
    }

    override fun isEmojiModifierBase(ch: Int): Boolean {
        return false
    }

    override fun isEmojiComponent(ch: Int): Boolean {
        return false
    }

    override fun isExtendedPictographic(ch: Int): Boolean {
        return false
    }

    override fun toLowerCase(ch: Int): Int {
        return ch
    }

    override fun toUpperCase(ch: Int): Int {
        return ch
    }

    override fun toTitleCase(ch: Int): Int {
        return ch
    }

    override fun digit(ch: Int, radix: Int): Int {
        return -1
    }

    override fun getNumericValue(ch: Int): Int {
        return -1
    }

    override fun isDigit(ch: Int): Boolean {
        return false
    }

    override fun isLowerCase(ch: Int): Boolean {
        return false
    }

    override fun isUpperCase(ch: Int): Boolean {
        return false
    }

    override fun isWhitespace(ch: Int): Boolean {
        return false
    }

    override fun getDirectionality(ch: Int): Byte {
        return if ((ch and 0xFFFE) == 0xFFFE)
            Character.DIRECTIONALITY_UNDEFINED
        else
            Character.DIRECTIONALITY_LEFT_TO_RIGHT
    }

    override fun isMirrored(ch: Int): Boolean {
        return false
    }

    companion object {
        val instance: CharacterData = CharacterDataPrivateUse()
    }
}
