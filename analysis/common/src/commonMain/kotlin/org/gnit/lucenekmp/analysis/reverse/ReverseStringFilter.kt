package org.gnit.lucenekmp.analysis.reverse

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.fromCharArray

/**
 * Reverse token string, for example "country" => "yrtnuoc".
 *
 * <p>If `marker` is supplied, then tokens will be also prepended by that character. For example,
 * with a marker of `\u0001`, "country" => "\u0001yrtnuoc". This is useful when implementing
 * efficient leading wildcards search.
 */
class ReverseStringFilter : TokenFilter {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val marker: Char

    /** Create a new ReverseStringFilter that reverses all tokens in the supplied [TokenStream]. */
    constructor(input: TokenStream) : this(input, NOMARKER)

    /**
     * Create a new ReverseStringFilter that reverses and marks all tokens in the supplied
     * [TokenStream].
     */
    constructor(input: TokenStream, marker: Char) : super(input) {
        this.marker = marker
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            var len = termAtt.length
            if (marker != NOMARKER) {
                len++
                termAtt.resizeBuffer(len)
                termAtt.buffer()[len - 1] = marker
            }
            reverse(termAtt.buffer(), 0, len)
            termAtt.setLength(len)
            return true
        }
        return false
    }

    companion object {
        private const val NOMARKER: Char = '\uFFFF'

        /** Example marker character: U+0001 (START OF HEADING) */
        const val START_OF_HEADING_MARKER: Char = '\u0001'

        /** Example marker character: U+001F (INFORMATION SEPARATOR ONE) */
        const val INFORMATION_SEPARATOR_MARKER: Char = '\u001F'

        /** Example marker character: U+EC00 (PRIVATE USE AREA: EC00) */
        const val PUA_EC00_MARKER: Char = '\uEC00'

        /** Example marker character: U+200F (RIGHT-TO-LEFT MARK) */
        const val RTL_DIRECTION_MARKER: Char = '\u200F'

        fun reverse(input: String): String {
            val charInput = input.toCharArray()
            reverse(charInput, 0, charInput.size)
            return String.fromCharArray(charInput)
        }

        fun reverse(buffer: CharArray) {
            reverse(buffer, 0, buffer.size)
        }

        fun reverse(buffer: CharArray, len: Int) {
            reverse(buffer, 0, len)
        }

        fun reverse(buffer: CharArray, start: Int, len: Int) {
            if (len < 2) return
            var end = (start + len) - 1
            var frontHigh = buffer[start]
            var endLow = buffer[end]
            var allowFrontSur = true
            var allowEndSur = true
            val mid = start + (len shr 1)
            var i = start
            while (i < mid) {
                val frontLow = buffer[i + 1]
                val endHigh = buffer[end - 1]
                val surAtFront =
                    allowFrontSur &&
                        Character.isHighSurrogate(frontHigh) &&
                        Character.isLowSurrogate(frontLow)
                if (surAtFront && len < 3) {
                    return
                }
                val surAtEnd =
                    allowEndSur &&
                        Character.isHighSurrogate(endHigh) &&
                        Character.isLowSurrogate(endLow)
                allowFrontSur = true
                allowEndSur = true
                if (surAtFront == surAtEnd) {
                    if (surAtFront) {
                        buffer[end] = frontLow
                        buffer[--end] = frontHigh
                        buffer[i] = endHigh
                        buffer[++i] = endLow
                        frontHigh = buffer[i + 1]
                        endLow = buffer[end - 1]
                    } else {
                        buffer[end] = frontHigh
                        buffer[i] = endLow
                        frontHigh = frontLow
                        endLow = endHigh
                    }
                } else if (surAtFront) {
                    buffer[end] = frontLow
                    buffer[i] = endLow
                    endLow = endHigh
                    allowFrontSur = false
                } else {
                    buffer[end] = frontHigh
                    buffer[i] = endHigh
                    frontHigh = frontLow
                    allowEndSur = false
                }
                i++
                end--
            }
            if ((len and 0x01) == 1 && !(allowFrontSur && allowEndSur)) {
                buffer[end] = if (allowFrontSur) endLow else frontHigh
            }
        }
    }
}
