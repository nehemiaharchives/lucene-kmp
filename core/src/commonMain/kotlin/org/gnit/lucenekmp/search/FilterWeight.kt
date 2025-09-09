package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * A [FilterWeight] contains another [Weight] and implements all abstract methods by
 * delegating to the wrapped weight.
 *
 * Note that [FilterWeight] does not override the non-abstract [Weight.bulkScorer] method
 * and subclasses must provide their own implementation if required.
 *
 * @lucene.internal
 */
abstract class FilterWeight protected constructor(
    query: Query,
    protected val `in`: Weight
) : Weight(query) {

    protected constructor(weight: Weight) : this(weight.query, weight)

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
        return `in`.isCacheable(ctx)
    }

    @Throws(IOException::class)
    override fun explain(context: LeafReaderContext, doc: Int): Explanation {
        return `in`.explain(context, doc)
    }

    @Throws(IOException::class)
    override fun matches(context: LeafReaderContext, doc: Int): Matches? {
        return `in`.matches(context, doc)
    }

    @Throws(IOException::class)
    override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
        return `in`.scorerSupplier(context)
    }
}

