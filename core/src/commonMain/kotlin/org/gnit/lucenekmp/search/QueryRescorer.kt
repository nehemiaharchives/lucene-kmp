package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * A [Rescorer] that uses a provided Query to assign scores to the first-pass hits.
 *
 * @lucene.experimental
 */
abstract class QueryRescorer(
    private val query: Query,
) : Rescorer() {
    /**
     * Implement this in a subclass to combine the first pass and second pass scores. If
     * secondPassMatches is false then the second pass query failed to match a hit from the first pass
     * query, and you should ignore the secondPassScore.
     */
    protected abstract fun combine(
        firstPassScore: Float,
        secondPassMatches: Boolean,
        secondPassScore: Float,
    ): Float

    @Throws(IOException::class)
    override fun rescore(searcher: IndexSearcher, firstPassTopDocs: TopDocs, topN: Int): TopDocs {
        var hits = firstPassTopDocs.scoreDocs.copyOf()

        hits.sortBy { it.doc }

        val leaves: MutableList<LeafReaderContext> = searcher.indexReader.leaves()

        val rewritten = searcher.rewrite(query)
        val weight = searcher.createWeight(rewritten, ScoreMode.COMPLETE, 1f)

        // Now merge sort docIDs from hits, with reader's leaves:
        var hitUpto = 0
        var readerUpto = -1
        var endDoc = 0
        var docBase = 0
        var scorer: Scorer? = null

        while (hitUpto < hits.size) {
            val hit = hits[hitUpto]
            val docID = hit.doc
            var readerContext: LeafReaderContext? = null
            while (docID >= endDoc) {
                readerUpto++
                readerContext = leaves[readerUpto]
                endDoc = readerContext.docBase + readerContext.reader().maxDoc()
            }

            if (readerContext != null) {
                // We advanced to another segment:
                docBase = readerContext.docBase
                scorer = weight.scorer(readerContext)
            }

            if (scorer != null) {
                val targetDoc = docID - docBase
                var actualDoc = scorer.docID()
                if (actualDoc < targetDoc) {
                    actualDoc = scorer.iterator().advance(targetDoc)
                }

                if (actualDoc == targetDoc) {
                    // Query did match this doc:
                    hit.score = combine(hit.score, true, scorer.score())
                } else {
                    // Query did not match this doc:
                    hit.score = combine(hit.score, false, 0.0f)
                }
            } else {
                // Query did not match this doc:
                hit.score = combine(hit.score, false, 0.0f)
            }

            hitUpto++
        }

        val sortDocComparator =
            compareByDescending<ScoreDoc> { it.score }
                .thenBy { it.doc }

        if (topN < hits.size) {
            hits = hits.sortedWith(sortDocComparator).take(topN).toTypedArray()
        } else {
            hits.sortWith(sortDocComparator)
        }

        return TopDocs(firstPassTopDocs.totalHits, hits)
    }

    @Throws(IOException::class)
    override fun explain(searcher: IndexSearcher, firstPassExplanation: Explanation, docID: Int): Explanation {
        val secondPassExplanation = searcher.explain(query, docID)

        val secondPassScore =
            if (secondPassExplanation.isMatch) secondPassExplanation.value else null

        val score =
            if (secondPassScore == null) {
                combine(firstPassExplanation.value.toFloat(), false, 0.0f)
            } else {
                combine(firstPassExplanation.value.toFloat(), true, secondPassScore.toFloat())
            }

        val first =
            Explanation.match(
                firstPassExplanation.value,
                "first pass score",
                firstPassExplanation,
            )

        val second =
            if (secondPassScore == null) {
                Explanation.noMatch("no second pass score")
            } else {
                Explanation.match(secondPassScore, "second pass score", secondPassExplanation)
            }

        return Explanation.match(
            score,
            "combined first and second pass score using $this",
            first,
            second,
        )
    }

    companion object {
        /**
         * Sugar API, calling {#rescore} using a simple linear combination of firstPassScore + weight *
         * secondPassScore
         */
        @Throws(IOException::class)
        fun rescore(
            searcher: IndexSearcher,
            topDocs: TopDocs,
            query: Query,
            weight: Double,
            topN: Int,
        ): TopDocs {
            return object : QueryRescorer(query) {
                override fun combine(
                    firstPassScore: Float,
                    secondPassMatches: Boolean,
                    secondPassScore: Float,
                ): Float {
                    var score = firstPassScore
                    if (secondPassMatches) {
                        score += (weight * secondPassScore).toFloat()
                    }
                    return score
                }
            }.rescore(searcher, topDocs, topN)
        }
    }
}
