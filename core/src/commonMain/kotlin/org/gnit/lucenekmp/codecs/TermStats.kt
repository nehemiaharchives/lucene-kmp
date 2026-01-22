package org.gnit.lucenekmp.codecs

/**
 * Holder for per-term statistics.
 *
 * @param docFreq How many documents have at least one occurrence of this term.
 * @param totalTermFreq Total number of times this term occurs across all documents in the field.
 */
data class TermStats(val docFreq: Int, val totalTermFreq: Long)
