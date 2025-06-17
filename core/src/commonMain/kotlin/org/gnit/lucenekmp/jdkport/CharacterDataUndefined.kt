package org.gnit.lucenekmp.jdkport

/** The CharacterData class encapsulates the large tables found in
 * Java.lang.Character.  */
internal class CharacterDataUndefined private constructor() : CharacterData() {
    override fun getProperties(ch: Int): Int {
        return 0
    }

    override fun getType(ch: Int): Int {
        return Character.UNASSIGNED.toInt()
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
        return if (ch in 0x10400..0x10427) ch + 0x28 else ch
    }

    override fun toUpperCase(ch: Int): Int {
        return if (ch in 0x10428..0x1044F) ch - 0x28 else ch
    }

    override fun toTitleCase(ch: Int): Int {
        return toUpperCase(ch)
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
        return Character.DIRECTIONALITY_UNDEFINED
    }

    override fun isMirrored(ch: Int): Boolean {
        return false
    }

    companion object {
        val instance: CharacterData = CharacterDataUndefined()
    }
}
