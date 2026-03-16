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

/**
 * Removes matches which overlap with another SpanQuery or which are within x tokens before or y
 * tokens after another SpanQuery.
 */
class SpanNotQuery : SpanQuery {
    private val include: SpanQuery
    private val exclude: SpanQuery
    private val pre: Int
    private val post: Int

    /**
     * Construct a SpanNotQuery matching spans from `include` which have no overlap with
     * spans from `exclude`.
     */
    constructor(include: SpanQuery, exclude: SpanQuery) : this(include, exclude, 0, 0)

    /**
     * Construct a SpanNotQuery matching spans from `include` which have no overlap with
     * spans from `exclude` within `dist` tokens of `include`.
     * Inversely, a negative `dist` value may be used to specify a certain amount of
     * allowable overlap.
     */
    constructor(include: SpanQuery, exclude: SpanQuery, dist: Int) : this(include, exclude, dist, dist)

    /**
     * Construct a SpanNotQuery matching spans from `include` which have no overlap with
     * spans from `exclude` within `pre` tokens before or `post`
     * tokens of `include`. Inversely, negative values for `pre` and/or
     * `post` allow a certain amount of overlap to occur.
     */
    constructor(include: SpanQuery, exclude: SpanQuery, pre: Int, post: Int) {
        this.include = include
        this.exclude = exclude
        this.pre = pre
        this.post = post

        if (include.getField() != null && exclude.getField() != null && include.getField() != exclude.getField()) {
            throw IllegalArgumentException("Clauses must have same field.")
        }
    }

    /** Return the SpanQuery whose matches are filtered. */
    fun getInclude(): SpanQuery {
        return include
    }

    /** Return the SpanQuery whose matches must not overlap those returned. */
    fun getExclude(): SpanQuery {
        return exclude
    }

    override fun getField(): String? {
        return include.getField()
    }

    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        buffer.append("spanNot(")
        buffer.append(include.toString(field))
        buffer.append(", ")
        buffer.append(exclude.toString(field))
        buffer.append(", ")
        buffer.append(pre.toString())
        buffer.append(", ")
        buffer.append(post.toString())
        buffer.append(")")
        return buffer.toString()
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        val includeWeight = include.createWeight(searcher, scoreMode, boost)
        val excludeWeight = exclude.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, boost)
        return SpanNotWeight(
            searcher,
            if (scoreMode.needsScores()) getTermStates(includeWeight) else null,
            includeWeight,
            excludeWeight,
            boost,
        )
    }

    /**
     * Creates SpanNotQuery scorer instances
     *
     * @lucene.internal
     */
    inner class SpanNotWeight(
        searcher: IndexSearcher,
        terms: Map<Term, TermStates>?,
        val includeWeight: SpanWeight,
        val excludeWeight: SpanWeight,
        boost: Float,
    ) : SpanWeight(this@SpanNotQuery, searcher, terms, boost) {
        override fun extractTermStates(contexts: MutableMap<Term, TermStates>) {
            includeWeight.extractTermStates(contexts)
        }

        @Throws(IOException::class)
        override fun getSpans(context: LeafReaderContext, requiredPostings: Postings): Spans? {
            val includeSpans = includeWeight.getSpans(context, requiredPostings)
            if (includeSpans == null) {
                return null
            }

            val excludeSpans = excludeWeight.getSpans(context, requiredPostings)
            if (excludeSpans == null) {
                return includeSpans
            }

            val excludeTwoPhase = excludeSpans.asTwoPhaseIterator()
            val excludeApproximation =
                if (excludeTwoPhase == null) null else excludeTwoPhase.approximation()

            return object : FilterSpans(includeSpans) {
                // last document we have checked matches() against for the exclusion, and failed
                // when using approximations, so we don't call it again, and pass thru all inclusions.
                var lastApproxDoc = -1
                var lastApproxResult = false

                @Throws(IOException::class)
                override fun accept(candidate: Spans): AcceptStatus {
                    // TODO: this logic is ugly and sneaky, can we clean it up?
                    val doc = candidate.docID()
                    if (doc > excludeSpans.docID()) {
                        // catch up 'exclude' to the current doc
                        if (excludeTwoPhase != null) {
                            if (excludeApproximation!!.advance(doc) == doc) {
                                lastApproxDoc = doc
                                lastApproxResult = excludeTwoPhase.matches()
                            }
                        } else {
                            excludeSpans.advance(doc)
                        }
                    } else if (excludeTwoPhase != null && doc == excludeSpans.docID() && doc != lastApproxDoc) {
                        // excludeSpans already sitting on our candidate doc, but matches not called yet.
                        lastApproxDoc = doc
                        lastApproxResult = excludeTwoPhase.matches()
                    }

                    if (doc != excludeSpans.docID() || (doc == lastApproxDoc && lastApproxResult == false)) {
                        return AcceptStatus.YES
                    }

                    if (excludeSpans.startPosition() == -1) {
                        // init exclude start position if needed
                        excludeSpans.nextStartPosition()
                    }

                    while (excludeSpans.endPosition() <= candidate.startPosition() - pre) {
                        // exclude end position is before a possible exclusion
                        if (excludeSpans.nextStartPosition() == NO_MORE_POSITIONS) {
                            return AcceptStatus.YES // no more exclude at current doc.
                        }
                    }

                    // exclude end position far enough in current doc, check start position:
                    return if (excludeSpans.startPosition() - post >= candidate.endPosition()) {
                        AcceptStatus.YES
                    } else {
                        AcceptStatus.NO
                    }
                }
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return includeWeight.isCacheable(ctx) && excludeWeight.isCacheable(ctx)
        }
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewrittenInclude = include.rewrite(indexSearcher) as SpanQuery
        val rewrittenExclude = exclude.rewrite(indexSearcher) as SpanQuery
        if (rewrittenInclude !== include || rewrittenExclude !== exclude) {
            return SpanNotQuery(rewrittenInclude, rewrittenExclude, pre, post)
        }
        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(getField())) {
            include.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this))
            exclude.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST_NOT, this))
        }
    }

    /** Returns true iff `o` is equal to this. */
    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(other as SpanNotQuery)
    }

    private fun equalsTo(other: SpanNotQuery): Boolean {
        return include == other.include
            && exclude == other.exclude
            && pre == other.pre
            && post == other.post
    }

    override fun hashCode(): Int {
        var h = classHash()
        h = h.rotateLeft(1)
        h = h xor include.hashCode()
        h = h.rotateLeft(1)
        h = h xor exclude.hashCode()
        h = h.rotateLeft(1)
        h = h xor pre
        h = h.rotateLeft(1)
        h = h xor post
        return h
    }

    private fun Int.rotateLeft(bitCount: Int): Int {
        return (this shl bitCount) or (this ushr (32 - bitCount))
    }
}
