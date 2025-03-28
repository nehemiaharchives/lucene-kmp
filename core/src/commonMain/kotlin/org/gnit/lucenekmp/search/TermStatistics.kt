package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.BytesRef


/**
 * Contains statistics for a specific term
 *
 *
 * This class holds statistics for this term across all documents for scoring purposes:
 *
 *
 *  * [.docFreq]: number of documents this term occurs in.
 *  * [.totalTermFreq]: number of tokens for this term.
 *
 *
 *
 * The following conditions are always true:
 *
 *
 *  * All statistics are positive integers: never zero or negative.
 *  * `docFreq` &lt;= `totalTermFreq`
 *  * `docFreq` &lt;= `sumDocFreq` of the collection
 *  * `totalTermFreq` &lt;= `sumTotalTermFreq` of the collection
 *
 *
 *
 * Values may include statistics on deleted documents that have not yet been merged away.
 *
 *
 * Be careful when performing calculations on these values because they are represented as 64-bit
 * integer values, you may need to cast to `double` for your use.
 *
 * @param term Term bytes.
 *
 * This value is never `null`.
 * @param docFreq number of documents containing the term in the collection, in the range [1 ..
 * [.totalTermFreq]].
 *
 * This is the document-frequency for the term: the count of documents where the term appears
 * at least one time.
 *
 * This value is always a positive number, and never exceeds [.totalTermFreq]. It also
 * cannot exceed [CollectionStatistics.sumDocFreq]. @see TermsEnum#docFreq()
 * @param totalTermFreq number of occurrences of the term in the collection, in the range [[     ][.docFreq] .. [CollectionStatistics.sumTotalTermFreq]].
 *
 * This is the token count for the term: the number of times it appears in the field across
 * all documents.
 *
 * This value is always a positive number, always at least [.docFreq], and never
 * exceeds [CollectionStatistics.sumTotalTermFreq]. @see TermsEnum#totalTermFreq()
 * @lucene.experimental
 */
// TODO: actually add missing cross-checks to guarantee TermStatistics is in bounds of
// CollectionStatistics,
// otherwise many similarity functions will implode.
class TermStatistics(term: BytesRef, val docFreq: Long, val totalTermFreq: Long) {
    val term: BytesRef

    /**
     * Creates statistics instance for a term.
     *
     * @throws NullPointerException if `term` is `null`.
     * @throws IllegalArgumentException if `docFreq` is negative or zero.
     * @throws IllegalArgumentException if `totalTermFreq` is less than `docFreq`.
     */
    init {
        this.term = term
        requireNotNull<Any>(term)
        require(docFreq > 0) { "docFreq must be positive, docFreq: $docFreq" }
        require(totalTermFreq > 0) { "totalTermFreq must be positive, totalTermFreq: $totalTermFreq" }
        require(totalTermFreq >= docFreq) {
            ("totalTermFreq must be at least docFreq, totalTermFreq: "
                    + totalTermFreq
                    + ", docFreq: "
                    + docFreq)
        }
    }
}
