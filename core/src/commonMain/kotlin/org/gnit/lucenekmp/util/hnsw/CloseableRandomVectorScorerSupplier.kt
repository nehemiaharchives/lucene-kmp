package org.gnit.lucenekmp.util.hnsw


/**
 * A supplier that creates [UpdateableRandomVectorScorer] from an ordinal. Caller should be
 * sure to close after use
 *
 *
 * NOTE: the [.copy] returned [RandomVectorScorerSupplier] is not necessarily
 * closeable
 */
interface CloseableRandomVectorScorerSupplier : AutoCloseable, RandomVectorScorerSupplier {
    fun totalVectorCount(): Int
}
