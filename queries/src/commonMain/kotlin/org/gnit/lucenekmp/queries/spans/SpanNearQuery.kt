package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScorerSupplier

/**
 * Matches spans which are near one another. One can specify slop as well as whether matches are
 * required to be in-order.
 */
class SpanNearQuery(
    clausesIn: Array<SpanQuery>,
    private val slop: Int,
    private val inOrder: Boolean,
) : SpanQuery() {
    private var clauses: MutableList<SpanQuery> = ArrayList(clausesIn.size)
    private var field: String? = null

    init {
        for (clause in clausesIn) {
            if (field == null) {
                field = clause.getField()
            } else if (clause.getField() != null && clause.getField() != field) {
                throw IllegalArgumentException("Clauses must have same field.")
            }
            clauses.add(clause)
        }
    }

    /** Return the clauses whose spans are matched. */
    fun getClauses(): Array<SpanQuery> {
        return clauses.toTypedArray()
    }

    /** Return the maximum number of intervening unmatched positions permitted. */
    fun getSlop(): Int {
        return slop
    }

    /** Return true if matches are required to be in-order. */
    fun isInOrder(): Boolean {
        return inOrder
    }

    override fun getField(): String? {
        return field
    }

    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        buffer.append("spanNear([")
        val iterator = clauses.iterator()
        while (iterator.hasNext()) {
            val clause = iterator.next()
            buffer.append(clause.toString(field))
            if (iterator.hasNext()) {
                buffer.append(", ")
            }
        }
        buffer.append("], ")
        buffer.append(slop)
        buffer.append(", ")
        buffer.append(inOrder)
        buffer.append(")")
        return buffer.toString()
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        val subWeights = ArrayList<SpanWeight>()
        for (q in clauses) {
            subWeights.add(q.createWeight(searcher, scoreMode, boost))
        }
        return SpanNearWeight(subWeights, searcher, if (scoreMode.needsScores()) getTermStates(subWeights) else null, boost)
    }

    inner class SpanNearWeight(
        private val subWeights: List<SpanWeight>,
        searcher: IndexSearcher,
        terms: Map<Term, TermStates>?,
        boost: Float,
    ) : SpanWeight(this@SpanNearQuery, searcher, terms, boost) {
        override fun extractTermStates(contexts: MutableMap<Term, TermStates>) {
            for (w in subWeights) {
                w.extractTermStates(contexts)
            }
        }

        @Throws(IOException::class)
        override fun getSpans(context: LeafReaderContext, requiredPostings: Postings): Spans? {
            val terms = context.reader().terms(field)
            if (terms == null) {
                return null
            }

            val subSpans = ArrayList<Spans>(clauses.size)
            for (w in subWeights) {
                val subSpan = w.getSpans(context, requiredPostings)
                if (subSpan != null) {
                    subSpans.add(subSpan)
                } else {
                    return null
                }
            }

            return if (!inOrder) {
                NearSpansUnordered(slop, subSpans)
            } else {
                NearSpansOrdered(slop, subSpans)
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            for (w in subWeights) {
                if (!w.isCacheable(ctx)) return false
            }
            return true
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            val spans = getSpans(context, Postings.POSITIONS) ?: return null
            val scorer = SpanScorer(spans, getSimScorer(), context.reader().getNormValues(requireNotNull(field)))
            return object : ScorerSupplier() {
                override fun get(leadCost: Long) = scorer
                override fun cost(): Long = scorer.iterator().cost()
            }
        }
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        var actuallyRewritten = false
        val rewrittenClauses = ArrayList<SpanQuery>()
        for (c in clauses) {
            val query = c.rewrite(indexSearcher) as SpanQuery
            actuallyRewritten = actuallyRewritten || query !== c
            rewrittenClauses.add(query)
        }
        if (actuallyRewritten) {
            return SpanNearQuery(rewrittenClauses.toTypedArray(), slop, inOrder)
        }
        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        if (!visitor.acceptField(getField())) {
            return
        }
        val v = visitor.getSubVisitor(BooleanClause.Occur.MUST, this)
        for (clause in clauses) {
            clause.visit(v)
        }
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && inOrder == (other as SpanNearQuery).inOrder && slop == other.slop && clauses == other.clauses
    }

    override fun hashCode(): Int {
        var result = classHash()
        result = result xor clauses.hashCode()
        result += slop
        val fac = 1 + if (inOrder) 8 else 4
        return fac * result
    }

    /** A builder for SpanNearQueries */
    class Builder(
        private val field: String,
        private val ordered: Boolean,
    ) {
        private val clauses: MutableList<SpanQuery> = ArrayList()
        private var slop: Int = 0

        /** Add a new clause */
        fun addClause(clause: SpanQuery): Builder {
            if (clause.getField() != field) {
                throw IllegalArgumentException("Cannot add clause $clause to SpanNearQuery for field $field")
            }
            clauses.add(clause)
            return this
        }

        /** Add a gap after the previous clause of a defined width */
        fun addGap(width: Int): Builder {
            if (!ordered) {
                throw IllegalArgumentException("Gaps can only be added to ordered near queries")
            }
            clauses.add(SpanGapQuery(field, width))
            return this
        }

        /** Set the slop for this query */
        fun setSlop(slop: Int): Builder {
            this.slop = slop
            return this
        }

        /** Build the query */
        fun build(): SpanNearQuery {
            return SpanNearQuery(clauses.toTypedArray(), slop, ordered)
        }
    }

    private class SpanGapQuery(
        private val fieldInternal: String,
        private val widthInternal: Int,
    ) : SpanQuery() {
        override fun getField(): String {
            return fieldInternal
        }

        override fun visit(visitor: QueryVisitor) {
            visitor.visitLeaf(this)
        }

        override fun toString(field: String?): String {
            return "SpanGap($fieldInternal:$widthInternal)"
        }

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
            return SpanGapWeight(searcher, boost)
        }

        private inner class SpanGapWeight(
            searcher: IndexSearcher,
            boost: Float,
        ) : SpanWeight(this@SpanGapQuery, searcher, null, boost) {
            override fun extractTermStates(contexts: MutableMap<Term, TermStates>) {}

            @Throws(IOException::class)
            override fun getSpans(ctx: LeafReaderContext, requiredPostings: Postings): Spans {
                return GapSpans(widthInternal)
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && widthInternal == (other as SpanGapQuery).widthInternal && fieldInternal == other.fieldInternal
        }

        override fun hashCode(): Int {
            var result = classHash()
            result -= 7 * widthInternal
            return result * 15 - fieldInternal.hashCode()
        }
    }

    class GapSpans(private val width: Int) : Spans() {
        private var doc = -1
        private var pos = -1

        @Throws(IOException::class)
        override fun nextStartPosition(): Int {
            pos += 1
            return pos
        }

        @Throws(IOException::class)
        fun skipToPosition(position: Int): Int {
            pos = position
            return pos
        }

        override fun startPosition(): Int = pos

        override fun endPosition(): Int = pos + width

        override fun width(): Int = width

        @Throws(IOException::class)
        override fun collect(collector: SpanCollector) {}

        override fun docID(): Int = doc

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            pos = -1
            doc += 1
            return doc
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            pos = -1
            doc = target
            return doc
        }

        override fun cost(): Long = 0

        override fun positionsCost(): Float = 0f
    }

    companion object {
        /** Returns a [Builder] for an ordered query on a particular field */
        fun newOrderedNearQuery(field: String): Builder {
            return Builder(field, true)
        }

        /** Returns a [Builder] for an unordered query on a particular field */
        fun newUnorderedNearQuery(field: String): Builder {
            return Builder(field, false)
        }
    }
}
