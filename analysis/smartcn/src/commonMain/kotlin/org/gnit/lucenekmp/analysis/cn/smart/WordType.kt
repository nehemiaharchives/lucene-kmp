package org.gnit.lucenekmp.analysis.cn.smart

/**
 * Internal SmartChineseAnalyzer token type constants
 *
 * @lucene.experimental
 */
object WordType {
    /** Start of a Sentence */
    const val SENTENCE_BEGIN: Int = 0

    /** End of a Sentence */
    const val SENTENCE_END: Int = 1

    /** Chinese Word */
    const val CHINESE_WORD: Int = 2

    /** ASCII String */
    const val STRING: Int = 3

    /** ASCII Alphanumeric */
    const val NUMBER: Int = 4

    /** Punctuation Symbol */
    const val DELIMITER: Int = 5

    /** Full-Width String */
    const val FULLWIDTH_STRING: Int = 6

    /** Full-Width Alphanumeric */
    const val FULLWIDTH_NUMBER: Int = 7
}
