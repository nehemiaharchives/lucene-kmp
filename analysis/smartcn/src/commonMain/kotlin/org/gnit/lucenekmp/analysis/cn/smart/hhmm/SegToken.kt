package org.gnit.lucenekmp.analysis.cn.smart.hhmm

import org.gnit.lucenekmp.analysis.cn.smart.WordType

/**
 * SmartChineseAnalyzer internal token
 *
 * @lucene.experimental
 */
class SegToken(
    /** Character array containing token text */
    var charArray: CharArray,

    /** start offset into original sentence */
    var startOffset: Int,

    /** end offset into original sentence */
    var endOffset: Int,

    /** WordType of the text */
    var wordType: Int,

    /** word frequency */
    var weight: Int,
) {
    /** during segmentation, this is used to store the index of the token in the token list table */
    var index: Int = 0

    override fun hashCode(): Int {
        var result = charArray.contentHashCode()
        result = 31 * result + endOffset
        result = 31 * result + index
        result = 31 * result + startOffset
        result = 31 * result + weight
        result = 31 * result + wordType
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SegToken) return false
        return charArray.contentEquals(other.charArray) &&
            endOffset == other.endOffset &&
            index == other.index &&
            startOffset == other.startOffset &&
            weight == other.weight &&
            wordType == other.wordType
    }
}
