package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.util.PriorityQueue

/**
 * A [Collector] implementation that collects the top-scoring hits, returning them as a [ ]. This is used by [IndexSearcher] to implement [TopDocs]-based search. Hits
 * are sorted by score descending and then (when the scores are tied) docID ascending. When you
 * create an instance of this collector you should know in advance whether documents are going to be
 * collected in doc Id order or not.
 *
 *
 * **NOTE**: The values [Float.NaN] and [Float.NEGATIVE_INFINITY] are not valid
 * scores. This collector will not properly collect hits with such scores.
 */
class TopScoreDocCollector internal constructor(
    numHits: Int,
    private val after: ScoreDoc?,
    val totalHitsThreshold: Int,
    val minScoreAcc: MaxScoreAccumulator?
) : TopDocsCollector<ScoreDoc>(
    HitQueue(
        numHits,
        true
    ) as PriorityQueue<ScoreDoc>
) {

    override var weight: Weight? = null

    override fun topDocsSize(): Int {
        // Note: this relies on sentinel values having Integer.MAX_VALUE as a doc ID.
        val validTopHitCount = IntArray(1)
        pq.forEach { scoreDoc: ScoreDoc ->
            if (scoreDoc.doc != Int.MAX_VALUE) {
                validTopHitCount[0]++
            }
        }
        return validTopHitCount[0]
    }

    override fun newTopDocs(
        results: Array<ScoreDoc>?,
        start: Int
    ): TopDocs {
        return if (results == null)
            TopDocs(
                TotalHits(totalHits.toLong(), totalHitsRelation),
                kotlin.arrayOfNulls<ScoreDoc>(0) as Array<ScoreDoc>
            )
        else
            TopDocs(
                TotalHits(totalHits.toLong(), totalHitsRelation),
                results
            )
    }

    override fun scoreMode(): ScoreMode {
        return if (totalHitsThreshold == Int.MAX_VALUE) ScoreMode.COMPLETE else ScoreMode.TOP_SCORES
    }

    @Throws(IOException::class)
    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
        val docBase: Int = context.docBase
        val after: ScoreDoc? = this.after
        val afterScore: Float
        val afterDoc: Int
        if (after == null) {
            afterScore = Float.POSITIVE_INFINITY
            afterDoc = DocIdSetIterator.NO_MORE_DOCS
        } else {
            afterScore = after.score
            afterDoc = after.doc - context.docBase
        }

        return object : LeafCollector {
            // HitQueue implements getSentinelObject to return a ScoreDoc, so we know
            // that at this point top() is already initialized.
            private var pqTop: ScoreDoc = pq.top()
            private var minCompetitiveScore = 0f

            override var scorer: Scorable? = null
                set(value) {
                    field = value
                    if (minScoreAcc == null) {
                        updateMinCompetitiveScore(value!!)
                    } else {
                        updateGlobalMinCompetitiveScore(value!!)
                    }
                }

            @Throws(IOException::class)
            override fun collect(doc: Int) {
                val score: Float = scorer!!.score()

                val hitCountSoFar: Int = ++totalHits

                if (minScoreAcc != null && (hitCountSoFar.toLong() and minScoreAcc.modInterval) == 0L) {
                    updateGlobalMinCompetitiveScore(scorer!!)
                }

                if (after != null && (score > afterScore || (score == afterScore && doc <= afterDoc))) {
                    // hit was collected on a previous page
                    if (totalHitsRelation == TotalHits.Relation.EQUAL_TO) {
                        // we just reached totalHitsThreshold, we can start setting the min
                        // competitive score now
                        updateMinCompetitiveScore(scorer!!)
                    }
                    return
                }

                if (score <= pqTop.score) {
                    // Note: for queries that match lots of hits, this is the common case: most hits are not
                    // competitive.
                    if (hitCountSoFar == totalHitsThreshold + 1) {
                        // we just exceeded totalHitsThreshold, we can start setting the min
                        // competitive score now
                        updateMinCompetitiveScore(scorer!!)
                    }

                    // Since docs are returned in-order (i.e., increasing doc Id), a document
                    // with equal score to pqTop.score cannot compete since HitQueue favors
                    // documents with lower doc Ids. Therefore reject those docs too.
                } else {
                    collectCompetitiveHit(doc, score)
                }
            }

            @Throws(IOException::class)
            fun collectCompetitiveHit(doc: Int, score: Float) {
                pqTop.doc = doc + docBase
                pqTop.score = score
                pqTop = pq.updateTop()
                updateMinCompetitiveScore(scorer!!)
            }

            @Throws(IOException::class)
            fun updateGlobalMinCompetitiveScore(scorer: Scorable) {
                checkNotNull(minScoreAcc)
                val maxMinScore: Long = minScoreAcc.raw
                if (maxMinScore != Long.MIN_VALUE) {
                    // since we tie-break on doc id and collect in doc id order we can require
                    // the next float if the global minimum score is set on a document id that is
                    // smaller than the ids in the current leaf
                    var score: Float = MaxScoreAccumulator.toScore(maxMinScore)
                    score =
                        if (docBase >= MaxScoreAccumulator.docId(maxMinScore)) Math.nextUp(score) else score
                    if (score > minCompetitiveScore) {
                        scorer.minCompetitiveScore = score
                        minCompetitiveScore = score
                        totalHitsRelation = TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
                    }
                }
            }

            @Throws(IOException::class)
            fun updateMinCompetitiveScore(scorer: Scorable) {
                if (totalHits > totalHitsThreshold) {
                    // since we tie-break on doc id and collect in doc id order, we can require the next float
                    // pqTop is never null since TopScoreDocCollector fills the priority queue with sentinel
                    // values if the top element is a sentinel value, its score will be -Infty and the below
                    // logic is still valid
                    val localMinScore = Math.nextUp(pqTop.score)
                    if (localMinScore > minCompetitiveScore) {
                        scorer.minCompetitiveScore = localMinScore
                        totalHitsRelation = TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
                        minCompetitiveScore = localMinScore
                        if (minScoreAcc != null) {
                            // we don't use the next float but we register the document id so that other leaves or
                            // leaf partitions can require it if they are after the current maximum
                            minScoreAcc.accumulate(pqTop.doc, pqTop.score)
                        }
                    }
                }
            }
        }
    }
}
