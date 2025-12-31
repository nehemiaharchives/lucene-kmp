package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.internal.hppc.CharObjectHashMap

/**
 * A [TokenFilter] that normalizes small letters (捨て仮名) in katakana into normal letters.
 * For instance, "ストップウォッチ" will be translated to "ストツプウオツチ".
 */
class JapaneseKatakanaUppercaseFilter(input: TokenStream) : TokenFilter(input) {
    companion object {
        private val LETTER_MAPPINGS: CharObjectHashMap<Char> = JapaneseFilterUtil.createCharMap(
            'ァ' to 'ア',
            'ィ' to 'イ',
            'ゥ' to 'ウ',
            'ェ' to 'エ',
            'ォ' to 'オ',
            'ヵ' to 'カ',
            'ㇰ' to 'ク',
            'ヶ' to 'ケ',
            'ㇱ' to 'シ',
            'ㇲ' to 'ス',
            'ッ' to 'ツ',
            'ㇳ' to 'ト',
            'ㇴ' to 'ヌ',
            'ㇵ' to 'ハ',
            'ㇶ' to 'ヒ',
            'ㇷ' to 'フ',
            'ㇸ' to 'ヘ',
            'ㇹ' to 'ホ',
            'ㇺ' to 'ム',
            'ャ' to 'ヤ',
            'ュ' to 'ユ',
            'ョ' to 'ヨ',
            'ㇻ' to 'ラ',
            'ㇼ' to 'リ',
            'ㇽ' to 'ル',
            'ㇾ' to 'レ',
            'ㇿ' to 'ロ',
            'ヮ' to 'ワ'
        )
    }

    private val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class)

    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }
        val termBuffer = termAttr.buffer()
        var newLength = termAttr.length
        var from = 0
        var to = 0
        while (from < newLength) {
            val c = termBuffer[from]
            if (c == 'ㇷ' && from + 1 < newLength && termBuffer[from + 1] == '゚') {
                termBuffer[to] = 'プ'
                from++
                newLength--
            } else {
                val mappedChar = LETTER_MAPPINGS.get(c)
                termBuffer[to] = mappedChar ?: c
            }
            from++
            to++
        }
        termAttr.setLength(newLength)
        return true
    }
}
