package org.gnit.lucenekmp.analysis.vi

class VietnameseToken(
    private val text: String,
    private val type: Type,
    private var segType: SegType? = null,
    private var splittedByDot: Boolean = false,
    private val startPos: Int,
    private val endPos: Int
) {
    companion object Companion {
        val FULL_STOP = VietnameseToken(".", Type.PUNCT, SegType.END_SEG_TYPE, false, -1, -1)
        val COMMA = VietnameseToken(",", Type.PUNCT, SegType.END_SEG_TYPE, false, -1, -1)
        val SPACE = VietnameseToken(" ", Type.SPACE, null, false, -1, -1)
    }

    enum class Type {
        WORD,
        NUMBER,
        SPACE,
        PUNCT,
        WHOLE_URL,
        SITE_URL;

        companion object Companion {
            private val VALUES = entries.toTypedArray()

            fun fromInt(i: Int): Type = VALUES[i]
        }
    }

    enum class SegType {
        OTHER_SEG_TYPE,
        SKIP_SEG_TYPE,
        URL_SEG_TYPE,
        END_URL_TYPE,
        END_SEG_TYPE;

        companion object Companion {
            private val VALUES = entries.toTypedArray()

            fun fromInt(i: Int): SegType = VALUES[i]
        }
    }

    constructor(text: String, start: Int, end: Int) : this(text, Type.WORD, null, false, start, end)

    constructor(text: String, type: Type, start: Int, end: Int) : this(text, type, null, false, start, end)

    constructor(text: String, type: Type, segType: SegType?, start: Int, end: Int) : this(
        text,
        type,
        segType,
        false,
        start,
        end
    )

    fun cloneWithNewText(newText: String, newEnd: Int): VietnameseToken {
        return VietnameseToken(newText, type, segType, splittedByDot, startPos, endPos)
    }

    fun getText(): String = text

    fun getType(): Type = type

    fun getPos(): Int = startPos

    fun getEndPos(): Int = if (endPos > 0) endPos else startPos + text.length

    fun getSegType(): SegType? = segType

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(type).append(" `").append(text).append('`')
        sb.append(
            when (segType) {
                SegType.END_SEG_TYPE -> " END"
                SegType.URL_SEG_TYPE -> " URL"
                SegType.SKIP_SEG_TYPE -> " SKIP"
                SegType.END_URL_TYPE -> " END_URL"
                else -> " OTHER"
            }
        )
        sb.append(' ').append(startPos).append('-').append(getEndPos())
        return sb.toString()
    }

    fun cloneToken(): VietnameseToken {
        return VietnameseToken(text, type, segType, splittedByDot, startPos, endPos)
    }

    override fun hashCode(): Int {
        return text.hashCode() xor type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VietnameseToken) return false
        return text == other.text && type == other.type
    }

    fun isWord(): Boolean = type == Type.WORD

    fun isPunct(): Boolean = type == Type.PUNCT

    fun isNumber(): Boolean = type == Type.NUMBER

    fun isWholeUrl(): Boolean = type == Type.WHOLE_URL

    fun isSiteUrl(): Boolean = type == Type.SITE_URL

    fun isSpace(): Boolean = type == Type.SPACE

    fun isEndSeg(): Boolean = segType == SegType.END_SEG_TYPE

    fun isSplittedByDot(): Boolean = splittedByDot

    fun setEndSeg() {
        segType = SegType.END_SEG_TYPE
    }

    fun setOtherSeg() {
        segType = SegType.OTHER_SEG_TYPE
    }

    fun setEndUrlSeg() {
        segType = SegType.END_URL_TYPE
    }

    fun setUrlSeg() {
        segType = SegType.URL_SEG_TYPE
    }

    fun setSkipSeg() {
        segType = SegType.SKIP_SEG_TYPE
    }

    fun isUrlSeg(): Boolean = segType == SegType.URL_SEG_TYPE

    fun isEndUrlSeg(): Boolean = segType == SegType.END_URL_TYPE

    fun isSkipSeg(): Boolean = segType == SegType.SKIP_SEG_TYPE

    fun isOtherSeg(): Boolean = segType == SegType.OTHER_SEG_TYPE

    fun isWordOrNumber(): Boolean = isWord() || isNumber() || isSiteUrl()

    fun isEndSegOrSkip(): Boolean = isEndSeg() || isSkipSeg()

    fun isUrlSegOrEndUrl(): Boolean = isUrlSeg() || isEndUrlSeg()

    fun isWordOrNumberOrUrl(): Boolean = isWordOrNumber() || isWholeUrl() || isSiteUrl()
}