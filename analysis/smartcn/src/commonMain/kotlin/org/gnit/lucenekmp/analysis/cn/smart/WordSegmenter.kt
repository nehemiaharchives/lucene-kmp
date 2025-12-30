package org.gnit.lucenekmp.analysis.cn.smart

import org.gnit.lucenekmp.analysis.cn.smart.hhmm.HHMMSegmenter
import org.gnit.lucenekmp.analysis.cn.smart.hhmm.SegToken
import org.gnit.lucenekmp.analysis.cn.smart.hhmm.SegTokenFilter

/**
 * Segment a sentence of Chinese text into words.
 *
 * @lucene.experimental
 */
class WordSegmenter {
    private val hhmmSegmenter = HHMMSegmenter()
    private val tokenFilter = SegTokenFilter()

    fun segmentSentence(sentence: String, startOffset: Int): List<SegToken> {
        val segTokenList = hhmmSegmenter.process(sentence)
        var result: List<SegToken> = emptyList()
        if (segTokenList.size > 2) {
            result = segTokenList.subList(1, segTokenList.size - 1)
        }
        for (st in result) {
            convertSegToken(st, sentence, startOffset)
        }
        return result
    }

    fun convertSegToken(st: SegToken, sentence: String, sentenceStartOffset: Int): SegToken {
        when (st.wordType) {
            WordType.STRING,
            WordType.NUMBER,
            WordType.FULLWIDTH_NUMBER,
            WordType.FULLWIDTH_STRING -> {
                st.charArray = sentence.substring(st.startOffset, st.endOffset).toCharArray()
            }
        }
        val filtered = tokenFilter.filter(st)
        filtered.startOffset += sentenceStartOffset
        filtered.endOffset += sentenceStartOffset
        return filtered
    }
}
