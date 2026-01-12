package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.index.Terms


/**
 * Stores all statistics commonly used ranking methods.
 *
 * @lucene.experimental
 */
open class BasicStats
/** Constructor.  */(
    val field: String?,
    /** A query boost. Should be applied as a multiplicative factor to the score.  */
    val boost: Double
) {
    /** Returns the number of documents.  */
    /** Sets the number of documents.  */
    /** The number of documents.  */
    var numberOfDocuments: Long = 0

    /**
     * Returns the total number of tokens in the field.
     *
     * @see Terms.getSumTotalTermFreq
     */
    /**
     * Sets the total number of tokens in the field.
     *
     * @see Terms.getSumTotalTermFreq
     */
    /** The total number of tokens in the field.  */
    var numberOfFieldTokens: Long = 0

    /** Returns the average field length.  */
    /** Sets the average field length.  */
    /** The average field length.  */
    var avgFieldLength: Double = 0.0

    /** Returns the document frequency.  */
    /** Sets the document frequency.  */
    /** The document frequency.  */
    var docFreq: Long = 0

    /** Returns the total number of occurrences of this term across all documents.  */
    /** Sets the total number of occurrences of this term across all documents.  */
    /** The total number of occurrences of this term across all documents.  */
    var totalTermFreq: Long = 0

    // -------------------------- Boost-related stuff --------------------------
    /** Returns the total boost.  */

    // ------------------------- Getter/setter methods -------------------------
}
