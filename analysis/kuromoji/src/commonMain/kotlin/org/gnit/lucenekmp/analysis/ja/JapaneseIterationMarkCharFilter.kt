package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.CharFilter
import org.gnit.lucenekmp.analysis.util.RollingCharBuffer
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Normalizes Japanese horizontal iteration marks (odoriji) to their expanded form.
 */
class JapaneseIterationMarkCharFilter : CharFilter {
    companion object {
        /** Normalize kanji iteration marks by default */
        const val NORMALIZE_KANJI_DEFAULT: Boolean = true

        /** Normalize kana iteration marks by default */
        const val NORMALIZE_KANA_DEFAULT: Boolean = true

        private const val KANJI_ITERATION_MARK: Char = '\u3005'
        private const val HIRAGANA_ITERATION_MARK: Char = '\u309d'
        private const val HIRAGANA_VOICED_ITERATION_MARK: Char = '\u309e'
        private const val KATAKANA_ITERATION_MARK: Char = '\u30fd'
        private const val KATAKANA_VOICED_ITERATION_MARK: Char = '\u30fe'
        private const val FULL_STOP_PUNCTUATION: Char = '\u3002'

        // Hiragana to dakuten map (lookup using code point - 0x30ab（か)
        private val h2d: CharArray = CharArray(50)

        // Katakana to dakuten map (lookup using code point - 0x30ab（カ)
        private val k2d: CharArray = CharArray(50)

        init {
            h2d[0] = '\u304c'
            h2d[1] = '\u304c'
            h2d[2] = '\u304e'
            h2d[3] = '\u304e'
            h2d[4] = '\u3050'
            h2d[5] = '\u3050'
            h2d[6] = '\u3052'
            h2d[7] = '\u3052'
            h2d[8] = '\u3054'
            h2d[9] = '\u3054'
            h2d[10] = '\u3056'
            h2d[11] = '\u3056'
            h2d[12] = '\u3058'
            h2d[13] = '\u3058'
            h2d[14] = '\u305a'
            h2d[15] = '\u305a'
            h2d[16] = '\u305c'
            h2d[17] = '\u305c'
            h2d[18] = '\u305e'
            h2d[19] = '\u305e'
            h2d[20] = '\u3060'
            h2d[21] = '\u3060'
            h2d[22] = '\u3062'
            h2d[23] = '\u3062'
            h2d[24] = '\u3063'
            h2d[25] = '\u3065'
            h2d[26] = '\u3065'
            h2d[27] = '\u3067'
            h2d[28] = '\u3067'
            h2d[29] = '\u3069'
            h2d[30] = '\u3069'
            h2d[31] = '\u306a'
            h2d[32] = '\u306b'
            h2d[33] = '\u306c'
            h2d[34] = '\u306d'
            h2d[35] = '\u306e'
            h2d[36] = '\u3070'
            h2d[37] = '\u3070'
            h2d[38] = '\u3071'
            h2d[39] = '\u3073'
            h2d[40] = '\u3073'
            h2d[41] = '\u3074'
            h2d[42] = '\u3076'
            h2d[43] = '\u3076'
            h2d[44] = '\u3077'
            h2d[45] = '\u3079'
            h2d[46] = '\u3079'
            h2d[47] = '\u307a'
            h2d[48] = '\u307c'
            h2d[49] = '\u307c'

            val codePointDifference = '\u30ab'.code - '\u304b'.code
            for (i in k2d.indices) {
                k2d[i] = (h2d[i].code + codePointDifference).toChar()
            }
        }
    }

    private val buffer = RollingCharBuffer()
    private var bufferPosition = 0
    private var iterationMarksSpanSize = 0
    private var iterationMarkSpanEndPosition = 0
    private var normalizeKanji: Boolean
    private var normalizeKana: Boolean

    constructor(input: Reader) : this(input, NORMALIZE_KANJI_DEFAULT, NORMALIZE_KANA_DEFAULT)

    constructor(input: Reader, normalizeKanji: Boolean, normalizeKana: Boolean) : super(input) {
        this.normalizeKanji = normalizeKanji
        this.normalizeKana = normalizeKana
        buffer.reset(input)
    }

    @Throws(IOException::class)
    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        var read = 0
        var i = offset
        while (i < offset + length) {
            val c = read()
            if (c == -1) {
                break
            }
            buffer[i] = c.toChar()
            read++
            i++
        }
        return if (read == 0) -1 else read
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val ic = buffer.get(bufferPosition)
        if (ic == -1) {
            buffer.freeBefore(bufferPosition)
            return ic
        }

        var c = ic.toChar()

        if (Character.isHighSurrogate(c) || c.isLowSurrogate()) {
            iterationMarkSpanEndPosition = bufferPosition + 1
        }

        if (c == FULL_STOP_PUNCTUATION) {
            buffer.freeBefore(bufferPosition)
            iterationMarkSpanEndPosition = bufferPosition + 1
        }

        if (isIterationMark(c)) {
            c = normalizeIterationMark(c)
        }

        bufferPosition++
        return c.code
    }

    @Throws(IOException::class)
    private fun normalizeIterationMark(c: Char): Char {
        if (bufferPosition < iterationMarkSpanEndPosition) {
            return normalize(sourceCharacter(bufferPosition, iterationMarksSpanSize), c)
        }

        if (bufferPosition == iterationMarkSpanEndPosition) {
            iterationMarkSpanEndPosition++
            return c
        }

        iterationMarksSpanSize = nextIterationMarkSpanSize()
        iterationMarkSpanEndPosition = bufferPosition + iterationMarksSpanSize
        return normalize(sourceCharacter(bufferPosition, iterationMarksSpanSize), c)
    }

    @Throws(IOException::class)
    private fun nextIterationMarkSpanSize(): Int {
        var spanSize = 0
        var i = bufferPosition
        while (buffer.get(i) != -1 && isIterationMark(buffer.get(i).toChar())) {
            spanSize++
            i++
        }
        if (bufferPosition - spanSize < iterationMarkSpanEndPosition) {
            spanSize = bufferPosition - iterationMarkSpanEndPosition
        }
        return spanSize
    }

    @Throws(IOException::class)
    private fun sourceCharacter(position: Int, spanSize: Int): Char {
        return buffer.get(position - spanSize).toChar()
    }

    private fun normalize(c: Char, m: Char): Char {
        if (isHiraganaIterationMark(m)) {
            return normalizedHiragana(c, m)
        }
        if (isKatakanaIterationMark(m)) {
            return normalizedKatakana(c, m)
        }
        return c
    }

    private fun normalizedHiragana(c: Char, m: Char): Char {
        return when (m) {
            HIRAGANA_ITERATION_MARK -> if (isHiraganaDakuten(c)) (c.code - 1).toChar() else c
            HIRAGANA_VOICED_ITERATION_MARK -> lookupHiraganaDakuten(c)
            else -> c
        }
    }

    private fun normalizedKatakana(c: Char, m: Char): Char {
        return when (m) {
            KATAKANA_ITERATION_MARK -> if (isKatakanaDakuten(c)) (c.code - 1).toChar() else c
            KATAKANA_VOICED_ITERATION_MARK -> lookupKatakanaDakuten(c)
            else -> c
        }
    }

    private fun isIterationMark(c: Char): Boolean {
        return isKanjiIterationMark(c) || isHiraganaIterationMark(c) || isKatakanaIterationMark(c)
    }

    private fun isHiraganaIterationMark(c: Char): Boolean {
        return if (normalizeKana) {
            c == HIRAGANA_ITERATION_MARK || c == HIRAGANA_VOICED_ITERATION_MARK
        } else {
            false
        }
    }

    private fun isKatakanaIterationMark(c: Char): Boolean {
        return if (normalizeKana) {
            c == KATAKANA_ITERATION_MARK || c == KATAKANA_VOICED_ITERATION_MARK
        } else {
            false
        }
    }

    private fun isKanjiIterationMark(c: Char): Boolean {
        return if (normalizeKanji) {
            c == KANJI_ITERATION_MARK
        } else {
            false
        }
    }

    private fun lookupHiraganaDakuten(c: Char): Char = lookup(c, h2d, '\u304b')

    private fun lookupKatakanaDakuten(c: Char): Char = lookup(c, k2d, '\u30ab')

    private fun isHiraganaDakuten(c: Char): Boolean {
        return inside(c, h2d, '\u304b') && c == lookupHiraganaDakuten(c)
    }

    private fun isKatakanaDakuten(c: Char): Boolean {
        return inside(c, k2d, '\u30ab') && c == lookupKatakanaDakuten(c)
    }

    private fun lookup(c: Char, map: CharArray, offset: Char): Char {
        return if (!inside(c, map, offset)) c else map[c.code - offset.code]
    }

    private fun inside(c: Char, map: CharArray, offset: Char): Boolean {
        return c >= offset && c.code < offset.code + map.size
    }

    override fun correct(currentOff: Int): Int = currentOff
}
