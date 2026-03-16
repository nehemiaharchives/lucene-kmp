package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.TwoPhaseIterator

/** Matches the union of its clauses. */
class SpanOrQuery : SpanQuery {
    private val clauses: MutableList<SpanQuery>
    private var field: String? = null

    /** Construct a SpanOrQuery merging the provided clauses. All clauses must have the same field. */
    constructor(vararg clauses: SpanQuery) {
        this.clauses = ArrayList(clauses.size)
        for (seq in clauses) {
            addClause(seq)
        }
    }

    /** Adds a clause to this query */
    private fun addClause(clause: SpanQuery) {
        if (field == null) {
            field = clause.getField()
        } else if (clause.getField() != null && clause.getField() != field) {
            throw IllegalArgumentException("Clauses must have same field.")
        }
        this.clauses.add(clause)
    }

    /** Return the clauses whose spans are matched. */
    fun getClauses(): Array<SpanQuery> {
        return clauses.toTypedArray()
    }

    override fun getField(): String? {
        return field
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewritten = SpanOrQuery()
        var actuallyRewritten = false
        for (i in clauses.indices) {
            val c = clauses[i]
            val query = c.rewrite(indexSearcher) as SpanQuery
            actuallyRewritten = actuallyRewritten || query !== c
            rewritten.addClause(query)
        }
        if (actuallyRewritten) {
            return rewritten
        }
        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(getField()) == false) {
            return
        }
        val v = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this)
        for (q in clauses) {
            q.visit(v)
        }
    }

    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        buffer.append("spanOr([")
        val i = clauses.iterator()
        while (i.hasNext()) {
            val clause = i.next()
            buffer.append(clause.toString(field))
            if (i.hasNext()) {
                buffer.append(", ")
            }
        }
        buffer.append("])")
        return buffer.toString()
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && clauses == (other as SpanOrQuery).clauses
    }

    override fun hashCode(): Int {
        return classHash() xor clauses.hashCode()
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        val subWeights = ArrayList<SpanWeight>(clauses.size)
        for (q in clauses) {
            subWeights.add(q.createWeight(searcher, scoreMode, boost))
        }
        return SpanOrWeight(
            searcher,
            if (scoreMode.needsScores()) getTermStates(subWeights) else null,
            subWeights,
            boost,
        )
    }

    /**
     * Creates SpanOrQuery scorer instances
     *
     * @lucene.internal
     */
    inner class SpanOrWeight(
        searcher: IndexSearcher,
        terms: Map<Term, TermStates>?,
        val subWeights: List<SpanWeight>,
        boost: Float,
    ) : SpanWeight(this@SpanOrQuery, searcher, terms, boost) {
        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            for (w in subWeights) {
                if (w.isCacheable(ctx) == false) return false
            }
            return true
        }

        override fun extractTermStates(contexts: MutableMap<Term, TermStates>) {
            for (w in subWeights) {
                w.extractTermStates(contexts)
            }
        }

        @Throws(IOException::class)
        override fun getSpans(context: LeafReaderContext, requiredPostings: Postings): Spans? {
            val subSpans = ArrayList<Spans>(clauses.size)

            for (w in subWeights) {
                val spans = w.getSpans(context, requiredPostings)
                if (spans != null) {
                    subSpans.add(spans)
                }
            }

            if (subSpans.size == 0) {
                return null
            } else if (subSpans.size == 1) {
                return subSpans[0]
            }

            val byDocQueue = SpanDisiPriorityQueue(subSpans.size)
            for (spans in subSpans) {
                byDocQueue.add(SpanDisiWrapper(spans))
            }

            val byPositionQueue =
                SpanPositionQueue(subSpans.size) // when empty use -1

            return object : Spans() {
                var topPositionSpans: Spans? = null

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    topPositionSpans = null
                    var topDocSpans = byDocQueue.top()
                    val currentDoc = topDocSpans.doc
                    do {
                        topDocSpans.doc = topDocSpans.iterator.nextDoc()
                        topDocSpans = byDocQueue.updateTop()
                    } while (topDocSpans.doc == currentDoc)
                    return topDocSpans.doc
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    topPositionSpans = null
                    var topDocSpans = byDocQueue.top()
                    do {
                        topDocSpans.doc = topDocSpans.iterator.advance(target)
                        topDocSpans = byDocQueue.updateTop()
                    } while (topDocSpans.doc < target)
                    return topDocSpans.doc
                }

                override fun docID(): Int {
                    val topDocSpans = byDocQueue.top()
                    return topDocSpans.doc
                }

                override fun asTwoPhaseIterator(): TwoPhaseIterator? {
                    var sumMatchCost = 0f // See also DisjunctionScorer.asTwoPhaseIterator()
                    var sumApproxCost = 0L

                    for (w in byDocQueue) {
                        if (w.twoPhaseView != null) {
                            val costWeight = if (w.cost <= 1L) 1L else w.cost
                            sumMatchCost += w.twoPhaseView.matchCost() * costWeight
                            sumApproxCost += costWeight
                        }
                    }

                    if (sumApproxCost == 0L) { // no sub spans supports approximations
                        computePositionsCost()
                        return null
                    }

                    val matchCost = sumMatchCost / sumApproxCost

                    return object : TwoPhaseIterator(SpanDisjunctionDISIApproximation(byDocQueue)) {
                        @Throws(IOException::class)
                        override fun matches(): Boolean {
                            return twoPhaseCurrentDocMatches()
                        }

                        override fun matchCost(): Float {
                            return matchCost
                        }
                    }
                }

                var positionsCost = -1f

                fun computePositionsCost() {
                    var sumPositionsCost = 0f
                    var sumCost = 0L
                    for (w in byDocQueue) {
                        val costWeight = if (w.cost <= 1L) 1L else w.cost
                        sumPositionsCost += w.spans.positionsCost() * costWeight
                        sumCost += costWeight
                    }
                    positionsCost = sumPositionsCost / sumCost
                }

                override fun positionsCost(): Float {
                    // This may be called when asTwoPhaseIterator returned null,
                    // which happens when none of the sub spans supports approximations.
                    assert(positionsCost > 0)
                    return positionsCost
                }

                var lastDocTwoPhaseMatched = -1

                @Throws(IOException::class)
                fun twoPhaseCurrentDocMatches(): Boolean {
                    var listAtCurrentDoc = byDocQueue.topList()
                    // remove the head of the list as long as it does not match
                    val currentDoc = listAtCurrentDoc.doc
                    while (listAtCurrentDoc.twoPhaseView != null) {
                        if (listAtCurrentDoc.twoPhaseView.matches()) {
                            // use this spans for positions at current doc:
                            listAtCurrentDoc.lastApproxMatchDoc = currentDoc
                            break
                        }
                        // do not use this spans for positions at current doc:
                        listAtCurrentDoc.lastApproxNonMatchDoc = currentDoc
                        listAtCurrentDoc = listAtCurrentDoc.next ?: return false
                    }
                    lastDocTwoPhaseMatched = currentDoc
                    topPositionSpans = null
                    return true
                }

                @Throws(IOException::class)
                fun fillPositionQueue() { // called at first nextStartPosition
                    assert(byPositionQueue.size() == 0)
                    // add all matching Spans at current doc to byPositionQueue
                    var listAtCurrentDoc: SpanDisiWrapper? = byDocQueue.topList()
                    while (listAtCurrentDoc != null) {
                        var spansAtDoc: Spans? = listAtCurrentDoc.spans
                        if (lastDocTwoPhaseMatched == listAtCurrentDoc.doc) {
                            // matched by DisjunctionDisiApproximation
                            if (listAtCurrentDoc.twoPhaseView != null) {
                                // matched by approximation
                                if (listAtCurrentDoc.lastApproxNonMatchDoc == listAtCurrentDoc.doc) {
                                    // matches() returned false
                                    spansAtDoc = null
                                } else if (listAtCurrentDoc.lastApproxMatchDoc != listAtCurrentDoc.doc) {
                                    if (!listAtCurrentDoc.twoPhaseView.matches()) {
                                        spansAtDoc = null
                                    }
                                }
                            }
                        }

                        if (spansAtDoc != null) {
                            assert(spansAtDoc.docID() == listAtCurrentDoc.doc)
                            assert(spansAtDoc.startPosition() == -1)
                            spansAtDoc.nextStartPosition()
                            assert(spansAtDoc.startPosition() != NO_MORE_POSITIONS)
                            byPositionQueue.add(spansAtDoc)
                        }
                        listAtCurrentDoc = listAtCurrentDoc.next
                    }
                    assert(byPositionQueue.size() > 0)
                }

                @Throws(IOException::class)
                override fun nextStartPosition(): Int {
                    if (topPositionSpans == null) {
                        byPositionQueue.clear()
                        fillPositionQueue() // fills byPositionQueue at first position
                        topPositionSpans = byPositionQueue.top()
                    } else {
                        topPositionSpans!!.nextStartPosition()
                        topPositionSpans = byPositionQueue.updateTop()
                    }
                    return topPositionSpans!!.startPosition()
                }

                override fun startPosition(): Int {
                    return if (topPositionSpans == null) -1 else topPositionSpans!!.startPosition()
                }

                override fun endPosition(): Int {
                    return if (topPositionSpans == null) -1 else topPositionSpans!!.endPosition()
                }

                override fun width(): Int {
                    return topPositionSpans!!.width()
                }

                @Throws(IOException::class)
                override fun collect(collector: SpanCollector) {
                    if (topPositionSpans != null) topPositionSpans!!.collect(collector)
                }

                override fun toString(): String {
                    return "spanOr(${this@SpanOrQuery.toString(null)})@${docID()}: ${startPosition()} - ${endPosition()}"
                }

                var cost = -1L

                override fun cost(): Long {
                    if (cost == -1L) {
                        cost = 0
                        for (spans in subSpans) {
                            cost += spans.cost()
                        }
                    }
                    return cost
                }
            }
        }
    }
}
