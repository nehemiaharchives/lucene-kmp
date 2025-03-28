package org.gnit.lucenekmp.search

import kotlinx.io.IOException

/**
 * Computes the similarity score between a given query vector and different document vectors. This
 * is used for exact searching and scoring
 *
 * @lucene.experimental
 */
interface VectorScorer {
    /**
     * Compute the score for the current document ID.
     *
     * @return the score for the current document ID
     * @throws IOException if an exception occurs during score computation
     */
    @Throws(IOException::class)
    fun score(): Float

    /**
     * @return a [DocIdSetIterator] over the documents.
     */
    fun iterator(): DocIdSetIterator
}
