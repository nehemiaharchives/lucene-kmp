package org.gnit.lucenekmp.search

import kotlin.jvm.JvmRecord


/**
 * Contains statistics for a collection (field).
 *
 *
 * This class holds statistics across all documents for scoring purposes:
 *
 *
 *  * [.maxDoc]: number of documents.
 *  * [.docCount]: number of documents that contain this field.
 *  * [.sumDocFreq]: number of postings-list entries.
 *  * [.sumTotalTermFreq]: number of tokens.
 *
 *
 *
 * The following conditions are always true:
 *
 *
 *  * All statistics are positive integers: never zero or negative.
 *  * `docCount` &lt;= `maxDoc`
 *  * `docCount` &lt;= `sumDocFreq` &lt;= `sumTotalTermFreq`
 *
 *
 *
 * Values may include statistics on deleted documents that have not yet been merged away.
 *
 *
 * Be careful when performing calculations on these values because they are represented as 64-bit
 * integer values, you may need to cast to `double` for your use.
 *
 * @param field Field's name.
 *
 * This value is never `null`.
 * @param maxDoc The total number of documents in the range [1 .. [Long.MAX_VALUE]],
 * regardless of whether they all contain values for this field.
 *
 * This value is always a positive number. @see IndexReader#maxDoc()
 * @param docCount The total number of documents that have at least one term for this field , in the
 * range [1 .. [.maxDoc]].
 *
 * This value is always a positive number, and never exceeds [.maxDoc]. @see
 * Terms#getDocCount()
 * @param sumTotalTermFreq The total number of tokens for this field , in the range [[     ][.sumDocFreq] .. [Long.MAX_VALUE]]. This is the "word count" for this field across all
 * documents. It is the sum of [TermStatistics.totalTermFreq] across all terms. It is
 * also the sum of each document's field length across all documents.
 *
 * This value is always a positive number, and always at least [.sumDocFreq]. @see
 * Terms#getSumTotalTermFreq()
 * @param sumDocFreq The total number of posting list entries for this field, in the range [[     ][.docCount] .. [.sumTotalTermFreq]]. This is the sum of term-document pairs: the sum
 * of [TermStatistics.docFreq] across all terms. It is also the sum of each document's
 * unique term count for this field across all documents.
 *
 * This value is always a positive number, always at least [.docCount], and never
 * exceeds [.sumTotalTermFreq]. @see Terms#getSumDocFreq()
 * @lucene.experimental
 */
data class CollectionStatistics(
    val field: String?,
    val maxDoc: Long,
    val docCount: Long,
    val sumTotalTermFreq: Long,
    val sumDocFreq: Long
) {
    /**
     * Creates statistics instance for a collection (field).
     *
     * @throws IllegalArgumentException if `maxDoc` is negative or zero.
     * @throws IllegalArgumentException if `docCount` is negative or zero.
     * @throws IllegalArgumentException if `docCount` is more than `maxDoc`.
     * @throws IllegalArgumentException if `sumDocFreq` is less than `docCount`.
     * @throws IllegalArgumentException if `sumTotalTermFreq` is less than `sumDocFreq`.
     */
    init {
        requireNotNull<String>(field)
        require(maxDoc > 0) { "maxDoc must be positive, maxDoc: $maxDoc" }
        require(docCount > 0) { "docCount must be positive, docCount: $docCount" }
        require(docCount <= maxDoc) { "docCount must not exceed maxDoc, docCount: $docCount, maxDoc: $maxDoc" }
        require(sumDocFreq > 0) { "sumDocFreq must be positive, sumDocFreq: $sumDocFreq" }
        require(sumDocFreq >= docCount) {
            ("sumDocFreq must be at least docCount, sumDocFreq: "
                    + sumDocFreq
                    + ", docCount: "
                    + docCount)
        }
        require(sumTotalTermFreq > 0) { "sumTotalTermFreq must be positive, sumTotalTermFreq: $sumTotalTermFreq" }
        require(sumTotalTermFreq >= sumDocFreq) {
            ("sumTotalTermFreq must be at least sumDocFreq, sumTotalTermFreq: "
                    + sumTotalTermFreq
                    + ", sumDocFreq: "
                    + sumDocFreq)
        }
    }
}
