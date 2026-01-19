package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * An abstract class that provides the vector similarity scores between the query vector and the
 * [org.apache.lucene.document.KnnFloatVectorField] or [ ] for documents.
 */
internal abstract class VectorSimilarityValuesSource(protected val fieldName: String) :
    DoubleValuesSource() {
    @Throws(IOException::class)
    override fun getValues(
        ctx: LeafReaderContext,
        scores: DoubleValues?
    ): DoubleValues {
        val scorer: VectorScorer? = getScorer(ctx)
        if (scorer == null) {
            return DoubleValues.EMPTY
        }
        val iterator: DocIdSetIterator = scorer.iterator()
        return object : DoubleValues() {
            @Throws(IOException::class)
            override fun doubleValue(): Double {
                return scorer.score().toDouble()
            }

            @Throws(IOException::class)
            override fun advanceExact(doc: Int): Boolean {
                return doc >= iterator.docID() && (iterator.docID() == doc || iterator.advance(doc) == doc)
            }
        }
    }

    @Throws(IOException::class)
    protected abstract fun getScorer(ctx: LeafReaderContext): VectorScorer?

    override fun needsScores(): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun rewrite(reader: IndexSearcher): DoubleValuesSource {
        return this
    }

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
        return true
    }
}
