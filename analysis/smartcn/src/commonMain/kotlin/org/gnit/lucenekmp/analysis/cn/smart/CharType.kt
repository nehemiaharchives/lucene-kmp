package org.gnit.lucenekmp.analysis.cn.smart

/**
 * Internal SmartChineseAnalyzer character type constants.
 *
 * @lucene.experimental
 */
object CharType {
    /** Punctuation Characters */
    const val DELIMITER: Int = 0

    /** Letters */
    const val LETTER: Int = 1

    /** Numeric Digits */
    const val DIGIT: Int = 2

    /** Han Ideographs */
    const val HANZI: Int = 3

    /** Characters that act as a space */
    const val SPACE_LIKE: Int = 4

    /** Full-Width letters */
    const val FULLWIDTH_LETTER: Int = 5

    /** Full-Width alphanumeric characters */
    const val FULLWIDTH_DIGIT: Int = 6

    /** Other (not fitting any of the other categories) */
    const val OTHER: Int = 7

    /** Surrogate character */
    const val SURROGATE: Int = 8
}
