package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.ReaderUtil
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.FieldValueHitQueue.Entry
import org.gnit.lucenekmp.search.TotalHits.Relation
import kotlin.math.max

/**
 * A [Collector] that sorts by [SortField] using [FieldComparator]s.
 *
 *
 * See the constructor of [TopFieldCollectorManager] for instantiating a
 * TopFieldCollectorManager with support for concurrency in IndexSearcher.
 *
 * @lucene.experimental
 */
abstract class TopFieldCollector private constructor(
    pq: FieldValueHitQueue<Entry>,
    val numHits: Int,
    totalHitsThreshold: Int,
    val needsScores: Boolean,
    minScoreAcc: MaxScoreAccumulator?
) : TopDocsCollector<Entry>(pq) {
    // TODO: one optimization we could do is to pre-fill
    // the queue with sentinel value that guaranteed to
    // always compare lower than a real hit; this would
    // save having to check queueFull on each insert
    private abstract inner class TopFieldLeafCollector(
        queue: FieldValueHitQueue<Entry>,
        sort: Sort,
        context: LeafReaderContext
    ) : LeafCollector {
        val comparator: LeafFieldComparator
        val reverseMul: Int
        override var scorer: Scorable? = null
            set(scorer) {
                field = scorer
                comparator.setScorer(scorer!!)
                if (minScoreAcc == null) {
                    updateMinCompetitiveScore(scorer)
                } else {
                    updateGlobalMinCompetitiveScore(scorer)
                }
            }

        var collectedAllCompetitiveHits: Boolean = false

        init {
            // as all segments are sorted in the same way, enough to check only the 1st segment for
            // indexSort
            if (searchSortPartOfIndexSort == null) {
                val indexSort: Sort = context.reader().metaData.sort!!
                searchSortPartOfIndexSort = canEarlyTerminate(sort, indexSort)
                if (searchSortPartOfIndexSort!!) {
                    firstComparator.disableSkipping()
                }
            }
            val comparators: Array<LeafFieldComparator> = queue.getComparators(context)
            val reverseMuls: IntArray = queue.reverseMul
            if (comparators.size == 1) {
                this.reverseMul = reverseMuls[0]
                this.comparator = comparators[0]
            } else {
                this.reverseMul = 1
                this.comparator = MultiLeafFieldComparator(comparators, reverseMuls)
            }
        }

        @Throws(IOException::class)
        fun countHit(doc: Int) {
            val hitCountSoFar: Int = ++totalHits

            if (minScoreAcc != null && (hitCountSoFar.toLong() and minScoreAcc.modInterval) == 0L) {
                updateGlobalMinCompetitiveScore(scorer!!)
            }
            if (!scoreMode.isExhaustive() && totalHitsRelation == Relation.EQUAL_TO && totalHits > totalHitsThreshold) {
                // for the first time hitsThreshold is reached, notify comparator about this
                comparator.setHitsThresholdReached()
                totalHitsRelation = Relation.GREATER_THAN_OR_EQUAL_TO
            }
        }

        @Throws(IOException::class)
        fun thresholdCheck(doc: Int): Boolean {
            if (collectedAllCompetitiveHits || reverseMul * comparator.compareBottom(doc) <= 0) {
                // since docs are visited in doc Id order, if compare is 0, it means
                // this document is larger than anything else in the queue, and
                // therefore not competitive.
                if (searchSortPartOfIndexSort!!) {
                    if (totalHits > totalHitsThreshold) {
                        totalHitsRelation = Relation.GREATER_THAN_OR_EQUAL_TO
                        throw CollectionTerminatedException()
                    } else {
                        collectedAllCompetitiveHits = true
                    }
                } else if (totalHitsRelation == Relation.EQUAL_TO) {
                    // we can start setting the min competitive score if the
                    // threshold is reached for the first time here.
                    updateMinCompetitiveScore(scorer!!)
                }
                return true
            }
            return false
        }

        @Throws(IOException::class)
        fun collectCompetitiveHit(doc: Int) {
            // This hit is competitive - replace bottom element in queue & adjustTop
            comparator.copy(bottom!!.slot, doc)
            updateBottom(doc)
            comparator.setBottom(bottom!!.slot)
            updateMinCompetitiveScore(scorer!!)
        }

        @Throws(IOException::class)
        fun collectAnyHit(doc: Int, hitsCollected: Int) {
            // Startup transient: queue hasn't gathered numHits yet
            val slot = hitsCollected - 1
            // Copy hit into queue
            comparator.copy(slot, doc)
            add(slot, doc)
            if (queueFull) {
                comparator.setBottom(bottom!!.slot)
                updateMinCompetitiveScore(scorer!!)
            }
        }

        @Throws(IOException::class)
        override fun competitiveIterator(): DocIdSetIterator {
            return comparator.competitiveIterator()!!
        }
    }

    /*
   * Implements a TopFieldCollector over one SortField criteria, with tracking
   * document scores and maxScore.
   */
    internal class SimpleFieldCollector(
        val sort: Sort,
        val queue: FieldValueHitQueue<Entry>,
        numHits: Int,
        totalHitsThreshold: Int,
        minScoreAcc: MaxScoreAccumulator?
    ) : TopFieldCollector(queue, numHits, totalHitsThreshold, sort.needsScores(), minScoreAcc) {

        override var weight: Weight? = null

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            // reset the minimum competitive score
            minCompetitiveScore = 0f
            docBase = context.docBase

            var collector: LeafCollector =
                object : TopFieldLeafCollector(queue, sort, context) {
                    @Throws(IOException::class)
                    override fun collect(doc: Int) {
                        countHit(doc)
                        if (queueFull) {
                            if (thresholdCheck(doc)) {
                                return
                            }
                            collectCompetitiveHit(doc)
                        } else {
                            collectAnyHit(doc, totalHits)
                        }
                    }
                }

            if (needsScores) {
                // score-based comparators may need to call score() multiple times, e.g. once for the
                // comparison, and once to copy the score into the priority queue
                collector = ScoreCachingWrappingScorer.wrap(collector)
            }

            return collector
        }
    }

    /*
   * Implements a TopFieldCollector when after != null.
   */
    internal class PagingFieldCollector(
        val sort: Sort,
        val queue: FieldValueHitQueue<Entry>,
        val after: FieldDoc,
        numHits: Int,
        totalHitsThreshold: Int,
        minScoreAcc: MaxScoreAccumulator?
    ) : TopFieldCollector(queue, numHits, totalHitsThreshold, sort.needsScores(), minScoreAcc) {

        override var weight: Weight? = null

        var collectedHits: Int = 0

        init {

            val comparators: Array<FieldComparator<Any>> = queue.comparators as Array<FieldComparator<Any>>
            // Tell all comparators their top value:
            for (i in comparators.indices) {
                val comparator: FieldComparator<Any> = comparators[i]
                comparator.setTopValue(after.fields!![i]!!)
            }
        }

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            // reset the minimum competitive score
            minCompetitiveScore = 0f
            docBase = context.docBase
            val afterDoc: Int = after.doc - docBase

            var collector: LeafCollector =
                object : TopFieldLeafCollector(queue, sort, context) {
                    @Throws(IOException::class)
                    override fun collect(doc: Int) {
                        countHit(doc)
                        if (queueFull) {
                            if (thresholdCheck(doc)) {
                                return
                            }
                        }
                        val topCmp: Int = reverseMul * comparator.compareTop(doc)
                        if (topCmp > 0 || (topCmp == 0 && doc <= afterDoc)) {
                            // Already collected on a previous page
                            if (totalHitsRelation == Relation.EQUAL_TO) {
                                // check if totalHitsThreshold is reached and we can update competitive score
                                // necessary to account for possible update to global min competitive score
                                updateMinCompetitiveScore(scorer!!)
                            }
                            return
                        }
                        if (queueFull) {
                            collectCompetitiveHit(doc)
                        } else {
                            collectedHits++
                            collectAnyHit(doc, collectedHits)
                        }
                    }
                }

            if (needsScores) {
                // score-based comparators may need to call score() multiple times, e.g. once for the
                // comparison, and once to copy the score into the priority queue
                collector = ScoreCachingWrappingScorer.wrap(collector)
            }

            return collector
        }
    }

    val totalHitsThreshold: Int = max(totalHitsThreshold, numHits)
    val firstComparator: FieldComparator<*>
    val canSetMinScore: Boolean

    var searchSortPartOfIndexSort: Boolean? = null // shows if Search Sort if a part of the Index Sort

    // an accumulator that maintains the maximum of the segment's minimum competitive scores
    val minScoreAcc: MaxScoreAccumulator?

    // the current local minimum competitive score already propagated to the underlying scorer
    var minCompetitiveScore: Float = 0f

    val numComparators: Int
    var bottom: Entry? = null
    var queueFull: Boolean = false
    var docBase: Int = 0
    val scoreMode: ScoreMode

    // Declaring the constructor private prevents extending this class by anyone
    // else. Note that the class cannot be final since it's extended by the
    // internal versions. If someone will define a constructor with any other
    // visibility, then anyone will be able to extend the class, which is not what
    // we want.
    init {
        this.numComparators = pq.comparators.size
        this.firstComparator = pq.comparators[0]
        val reverseMul: Int = pq.reverseMul[0]

        if (firstComparator::class == FieldComparator.RelevanceComparator::class
            && reverseMul == 1 // if the natural sort is preserved (sort by descending relevance)
            && totalHitsThreshold != Int.MAX_VALUE
        ) {
            scoreMode = ScoreMode.TOP_SCORES
            canSetMinScore = true
        } else {
            canSetMinScore = false
            scoreMode = if (totalHitsThreshold != Int.MAX_VALUE) {
                if (needsScores) ScoreMode.TOP_DOCS_WITH_SCORES else ScoreMode.TOP_DOCS
            } else {
                if (needsScores) ScoreMode.COMPLETE else ScoreMode.COMPLETE_NO_SCORES
            }
        }
        this.minScoreAcc = minScoreAcc
    }

    override fun scoreMode(): ScoreMode {
        return scoreMode
    }

    @Throws(IOException::class)
    protected fun updateGlobalMinCompetitiveScore(scorer: Scorable) {
        checkNotNull(minScoreAcc)
        if (canSetMinScore) {
            // we can start checking the global maximum score even if the local queue is not full or if
            // the threshold is not reached on the local competitor: the fact that there is a shared min
            // competitive score implies that one of the collectors hit its totalHitsThreshold already
            val maxMinScore: Long = minScoreAcc.raw
            val score: Float = MaxScoreAccumulator.toScore(maxMinScore)

            if (maxMinScore != Long.MIN_VALUE && score > minCompetitiveScore) {
                scorer.minCompetitiveScore = score
                minCompetitiveScore = score
                totalHitsRelation = Relation.GREATER_THAN_OR_EQUAL_TO
            }
        }
    }

    @Throws(IOException::class)
    protected fun updateMinCompetitiveScore(scorer: Scorable) {
        if (canSetMinScore && queueFull && totalHits > totalHitsThreshold) {
            checkNotNull(bottom)
            val minScore = firstComparator.value(bottom!!.slot) as Float
            if (minScore > minCompetitiveScore) {
                scorer.minCompetitiveScore = minScore
                minCompetitiveScore = minScore
                totalHitsRelation = Relation.GREATER_THAN_OR_EQUAL_TO
                if (minScoreAcc != null) {
                    minScoreAcc.accumulate(docBase, minScore)
                }
            }
        }
    }

    fun add(slot: Int, doc: Int) {
        bottom = pq.add(FieldValueHitQueue.Entry(slot, docBase + doc))
        // The queue is full either when totalHits == numHits (in SimpleFieldCollector), in which case
        // slot = totalHits - 1, or when hitsCollected == numHits (in PagingFieldCollector this is hits
        // on the current page) and slot = hitsCollected - 1.
        assert(slot < numHits)
        queueFull = slot == numHits - 1
    }

    fun updateBottom(doc: Int) {
        // bottom.score is already set to Float.NaN in add().
        bottom!!.doc = docBase + doc
        bottom = pq.updateTop()
    }

    /*
   * Only the following callback methods need to be overridden since
   * topDocs(int, int) calls them to return the results.
   */
    override fun populateResults(results: Array<ScoreDoc>, howMany: Int) {
        // avoid casting if unnecessary.
        val queue: FieldValueHitQueue<Entry> =
            pq as FieldValueHitQueue<Entry>
        for (i in howMany - 1 downTo 0) {
            results[i] = queue.fillFields(queue.pop()!!)
        }
    }

    override fun newTopDocs(
        results: Array<ScoreDoc>?,
        start: Int
    ): TopDocs {
        var results: Array<ScoreDoc>? = results
        if (results == null) {
            results = EMPTY_SCOREDOCS
        }

        // If this is a maxScoring tracking collector and there were no results,
        return TopFieldDocs(
            TotalHits(totalHits.toLong(), totalHitsRelation),
            results,
            (pq as FieldValueHitQueue<Entry>).fields
        )
    }

    override fun topDocs(): TopFieldDocs {
        return super.topDocs() as TopFieldDocs
    }

    val isEarlyTerminated: Boolean
        /** Return whether collection terminated early.  */
        get() = totalHitsRelation == TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO

    companion object {
        fun canEarlyTerminate(
            searchSort: Sort,
            indexSort: Sort
        ): Boolean {
            return canEarlyTerminateOnDocId(searchSort) || canEarlyTerminateOnPrefix(searchSort, indexSort)
        }

        private fun canEarlyTerminateOnDocId(searchSort: Sort): Boolean {
            val fields1: Array<SortField> = searchSort.sort
            return SortField.FIELD_DOC == fields1[0]
        }

        private fun canEarlyTerminateOnPrefix(
            searchSort: Sort,
            indexSort: Sort
        ): Boolean {
            if (indexSort != null) {
                val fields1: Array<SortField> = searchSort.sort
                val fields2: Array<SortField> = indexSort.sort
                // early termination is possible if fields1 is a prefix of fields2
                if (fields1.size > fields2.size) {
                    return false
                }
                return fields1.toList() == fields2.toList().subList(0, fields1.size)
            } else {
                return false
            }
        }

        private val EMPTY_SCOREDOCS: Array<ScoreDoc> =
            kotlin.arrayOfNulls<ScoreDoc>(0) as Array<ScoreDoc>

        /**
         * Populate [scores][ScoreDoc.score] of the given `topDocs`.
         *
         * @param topDocs the top docs to populate
         * @param searcher the index searcher that has been used to compute `topDocs`
         * @param query the query that has been used to compute `topDocs`
         * @throws IllegalArgumentException if there is evidence that `topDocs` have been computed
         * against a different searcher or a different query.
         * @lucene.experimental
         */
        @Throws(IOException::class)
        fun populateScores(
            topDocs: Array<ScoreDoc>,
            searcher: IndexSearcher,
            query: Query
        ) {
            // Get the score docs sorted in doc id order
            var topDocs: Array<ScoreDoc> = topDocs
            topDocs = topDocs.copyOf()
            topDocs.sortWith(compareBy { it.doc })

            val weight: Weight =
                searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f)
            val contexts: MutableList<LeafReaderContext> = searcher.getIndexReader().leaves()
            var currentContext: LeafReaderContext? = null
            var currentScorer: Scorer? = null
            for (scoreDoc in topDocs) {
                if (currentContext == null
                    || scoreDoc.doc >= currentContext.docBase + currentContext.reader().maxDoc()
                ) {
                    Objects.checkIndex(scoreDoc.doc, searcher.getIndexReader().maxDoc())
                    val newContextIndex: Int = ReaderUtil.subIndex(scoreDoc.doc, contexts)
                    currentContext = contexts[newContextIndex]
                    val scorerSupplier: ScorerSupplier? = weight.scorerSupplier(currentContext)
                    requireNotNull(scorerSupplier) { "Doc id " + scoreDoc.doc + " doesn't match the query" }
                    currentScorer = scorerSupplier.get(1) // random-access
                }
                val leafDoc: Int = scoreDoc.doc - currentContext.docBase
                assert(leafDoc >= 0)
                val advanced: Int = currentScorer!!.iterator().advance(leafDoc)
                require(leafDoc == advanced) { "Doc id " + scoreDoc.doc + " doesn't match the query" }
                scoreDoc.score = currentScorer.score()
            }
        }
    }
}
