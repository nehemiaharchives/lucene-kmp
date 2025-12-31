package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that normalizes common katakana spelling variations ending in a long sound
 * character by removing this character (U+30FC).
 */
class JapaneseKatakanaStemFilter : TokenFilter {
    companion object {
        const val DEFAULT_MINIMUM_LENGTH: Int = 4
        private const val HIRAGANA_KATAKANA_PROLONGED_SOUND_MARK: Char = '\u30fc'
    }

    private val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val minimumKatakanaLength: Int

    constructor(input: TokenStream, minimumLength: Int) : super(input) {
        require(minimumLength >= 1) { "minimumLength must be >=1" }
        minimumKatakanaLength = minimumLength
    }

    constructor(input: TokenStream) : this(input, DEFAULT_MINIMUM_LENGTH)

    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttr.isKeyword) {
                termAttr.setLength(stem(termAttr.buffer(), termAttr.length))
            }
            return true
        }
        return false
    }

    private fun stem(term: CharArray, length: Int): Int {
        if (length < minimumKatakanaLength) {
            return length
        }
        if (!isKatakana(term, length)) {
            return length
        }
        if (term[length - 1] == HIRAGANA_KATAKANA_PROLONGED_SOUND_MARK) {
            return length - 1
        }
        return length
    }

    private fun isKatakana(term: CharArray, length: Int): Boolean {
        for (i in 0 until length) {
            val ch = term[i]
            if (ch !in '\u30a0'..'\u30ff') {
                return false
            }
        }
        return true
    }
}
