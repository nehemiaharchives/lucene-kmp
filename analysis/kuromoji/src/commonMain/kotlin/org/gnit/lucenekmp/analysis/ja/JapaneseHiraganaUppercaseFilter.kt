package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.internal.hppc.CharObjectHashMap

/**
 * A [TokenFilter] that normalizes small letters (捨て仮名) in hiragana into normal letters. For
 * instance, "ちょっとまって" will be translated to "ちよつとまつて".
 */
class JapaneseHiraganaUppercaseFilter(input: TokenStream) : TokenFilter(input) {
    companion object {
        private val LETTER_MAPPINGS: CharObjectHashMap<Char> = JapaneseFilterUtil.createCharMap(
            'ぁ' to 'あ',
            'ぃ' to 'い',
            'ぅ' to 'う',
            'ぇ' to 'え',
            'ぉ' to 'お',
            'っ' to 'つ',
            'ゃ' to 'や',
            'ゅ' to 'ゆ',
            'ょ' to 'よ',
            'ゎ' to 'わ',
            'ゕ' to 'か',
            'ゖ' to 'け'
        )
    }

    private val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class)

    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }
        val termBuffer = termAttr.buffer()
        val length = termAttr.length
        for (i in 0 until length) {
            val c = LETTER_MAPPINGS.get(termBuffer[i])
            if (c != null) {
                termBuffer[i] = c
            }
        }
        return true
    }
}
