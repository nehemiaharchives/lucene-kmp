package org.gnit.lucenekmp.analysis.cn.smart.hhmm

import org.gnit.lucenekmp.analysis.cn.smart.Utility
import org.gnit.lucenekmp.analysis.cn.smart.WordType

/**
 * Filters a SegToken by converting full-width latin to half-width, then lowercasing latin.
 * Additionally, all punctuation is converted into Utility.COMMON_DELIMITER
 *
 * @lucene.experimental
 */
class SegTokenFilter {
    /**
     * Filter an input SegToken
     */
    fun filter(token: SegToken): SegToken {
        when (token.wordType) {
            WordType.FULLWIDTH_NUMBER, WordType.FULLWIDTH_STRING -> {
                for (i in token.charArray.indices) {
                    if (token.charArray[i] >= 0xFF10.toChar()) {
                        token.charArray[i] = (token.charArray[i].code - 0xFEE0).toChar()
                    }
                    if (token.charArray[i] >= 'A' && token.charArray[i] <= 'Z') {
                        token.charArray[i] = (token.charArray[i].code + 0x0020).toChar()
                    }
                }
            }
            WordType.STRING -> {
                for (i in token.charArray.indices) {
                    if (token.charArray[i] >= 'A' && token.charArray[i] <= 'Z') {
                        token.charArray[i] = (token.charArray[i].code + 0x0020).toChar()
                    }
                }
            }
            WordType.DELIMITER -> {
                token.charArray = Utility.COMMON_DELIMITER
            }
        }
        return token
    }
}
