package org.gnit.lucenekmp.analysis.vi

/**
 * Light Stemmer for Vietnamese.
 *
 * Currently applies only very conservative normalization and does not remove morphemes.
 */
internal class VietnameseStemmer {
    fun stem(buffer: CharArray, len: Int): Int {
        return len
    }
}
