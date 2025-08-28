package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.jdkport.Math.addExact
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.math.min


/** Represents hits returned by [IndexSearcher.search].  */
open class TopDocs(
    /** The total number of hits for the query.  */
    var totalHits: TotalHits,
    /** The top hits for the query.  */
    var scoreDocs: Array<ScoreDoc>
) {

    // Refers to one hit:
    class ShardRef(// Which shard (index into shardHits[]):
        val shardIndex: Int
    ) {
        // Which hit within the shard:
        var hitIndex: Int = 0

        override fun toString(): String {
            return "ShardRef(shardIndex=$shardIndex hitIndex=$hitIndex)"
        }
    }

    // Specialized MergeSortQueue that just merges by
    // relevance score, descending:
    private class ScoreMergeSortQueue(
        shardHits: Array<TopDocs>,
        tieBreakerComparator: Comparator<ScoreDoc>
    ) : PriorityQueue<ShardRef>(shardHits.size) {
        val shardHits: Array<Array<ScoreDoc>?> = kotlin.arrayOfNulls(shardHits.size)
        val tieBreakerComparator: Comparator<ScoreDoc>

        init {
            for (shardIDX in shardHits.indices) {
                this.shardHits[shardIDX] = shardHits[shardIDX].scoreDocs
            }
            this.tieBreakerComparator = tieBreakerComparator
        }

        // Returns true if first is < second
        override fun lessThan(first: ShardRef, second: ShardRef): Boolean {
            require(first != second)
            val firstScoreDoc: ScoreDoc = shardHits[first.shardIndex]!![first.hitIndex]
            val secondScoreDoc: ScoreDoc = shardHits[second.shardIndex]!![second.hitIndex]
            if (firstScoreDoc.score < secondScoreDoc.score) {
                return false
            } else if (firstScoreDoc.score > secondScoreDoc.score) {
                return true
            } else {
                return tieBreakLessThan(first, firstScoreDoc, second, secondScoreDoc, tieBreakerComparator)
            }
        }
    }

    private class MergeSortQueue(sort: Sort, shardHits: Array<TopDocs>, val tieBreaker: Comparator<ScoreDoc>) :
        PriorityQueue<ShardRef>(shardHits.size) {
        // These are really FieldDoc instances:
        val shardHits: Array<Array<ScoreDoc>?> = kotlin.arrayOfNulls(shardHits.size)
        val comparators: Array<FieldComparator<*>?>
        val reverseMul: IntArray

        init {
            for (shardIDX in shardHits.indices) {
                val shard: Array<ScoreDoc> = shardHits[shardIDX].scoreDocs
                this.shardHits[shardIDX] = shard
                // Fail gracefully if API is misused:
                for (hitIDX in shard.indices) {
                    val sd: ScoreDoc = shard[hitIDX]
                    require(sd is FieldDoc) {
                        ("shard "
                                + shardIDX
                                + " was not sorted by the provided Sort (expected FieldDoc but got ScoreDoc)")
                    }
                    val fd: FieldDoc = sd
                    requireNotNull(fd.fields) { "shard $shardIDX did not set sort field values (FieldDoc.fields is null)" }
                }
            }

            val sortFields: Array<SortField> = sort.sort
            comparators = kotlin.arrayOfNulls<FieldComparator<*>>(sortFields.size)
            reverseMul = IntArray(sortFields.size)
            for (compIDX in sortFields.indices) {
                val sortField: SortField = sortFields[compIDX]
                comparators[compIDX] = sortField.getComparator(1, Pruning.NONE)
                reverseMul[compIDX] = if (sortField.reverse) -1 else 1
            }
        }

        // Returns true if first is < second
        override fun lessThan(first: ShardRef, second: ShardRef): Boolean {
            require(first != second)
            val firstFD: FieldDoc = shardHits[first.shardIndex]!![first.hitIndex] as FieldDoc
            val secondFD: FieldDoc = shardHits[second.shardIndex]!![second.hitIndex] as FieldDoc

            for (compIDX in comparators.indices) {
                val comp: FieldComparator<Any?> = comparators[compIDX]!! as FieldComparator<Any?>
                val cmp: Int =
                    (reverseMul[compIDX]
                            * comp.compareValues(
                        firstFD.fields!![compIDX] as FieldComparator<Any?>,
                        secondFD.fields!![compIDX] as FieldComparator<Any?>
                    ))

                if (cmp != 0) {
                    return cmp < 0
                }
            }
            return tieBreakLessThan(first, firstFD, second, secondFD, tieBreaker)
        }
    }

    private data class ShardIndexAndDoc(val shardIndex: Int, val doc: Int)

    companion object {
        /** Internal comparator with shardIndex  */
        private val SHARD_INDEX_TIE_BREAKER: Comparator<ScoreDoc> =
            Comparator { a, b -> a.shardIndex.compareTo(b.shardIndex) }

        /** Internal comparator with docID  */
        private val DOC_ID_TIE_BREAKER: Comparator<ScoreDoc> =
            Comparator { a, b -> a.doc.compareTo(b.doc) }

        /** Default comparator  */
        private val DEFAULT_TIE_BREAKER: Comparator<ScoreDoc> =
            Comparator<ScoreDoc> { a, b ->
                val result = SHARD_INDEX_TIE_BREAKER.compare(a, b)
                if (result != 0) result else DOC_ID_TIE_BREAKER.compare(a, b)
            }

        /**
         * Use the tie breaker if provided. If tie breaker returns 0 signifying equal values, we use hit
         * indices to tie break intra shard ties
         */
        fun tieBreakLessThan(
            first: ShardRef,
            firstDoc: ScoreDoc,
            second: ShardRef,
            secondDoc: ScoreDoc,
            tieBreaker: Comparator<ScoreDoc>
        ): Boolean {
            checkNotNull(tieBreaker)
            val value: Int = tieBreaker.compare(firstDoc, secondDoc)

            if (value == 0) {
                // Equal Values
                // Tie break in same shard: resolve however the
                // shard had resolved it:
                require(first.hitIndex != second.hitIndex)
                return first.hitIndex < second.hitIndex
            }

            return value < 0
        }

        /**
         * Returns a new TopDocs, containing topN results across the provided TopDocs, sorting by score.
         * Each [TopDocs] instance must be sorted.
         *
         * @see .merge
         * @lucene.experimental
         */
        fun merge(topN: Int, shardHits: Array<TopDocs>): TopDocs {
            return merge(0, topN, shardHits)
        }

        /**
         * Same as [.merge] but also ignores the top `start` top docs. This is
         * typically useful for pagination.
         *
         *
         * docIDs are expected to be in consistent pattern i.e. either all ScoreDocs have their
         * shardIndex set, or all have them as -1 (signifying that all hits belong to same searcher)
         *
         * @lucene.experimental
         */
        fun merge(start: Int, topN: Int, shardHits: Array<TopDocs>): TopDocs {
            return mergeAux(null, start, topN, shardHits, DEFAULT_TIE_BREAKER)
        }

        /**
         * Same as above, but accepts the passed in tie breaker
         *
         *
         * docIDs are expected to be in consistent pattern i.e. either all ScoreDocs have their
         * shardIndex set, or all have them as -1 (signifying that all hits belong to same searcher)
         *
         * @lucene.experimental
         */
        fun merge(
            start: Int, topN: Int, shardHits: Array<TopDocs>, tieBreaker: Comparator<ScoreDoc>
        ): TopDocs {
            return mergeAux(null, start, topN, shardHits, tieBreaker)
        }

        /**
         * Returns a new TopFieldDocs, containing topN results across the provided TopFieldDocs, sorting
         * by the specified [Sort]. Each of the TopDocs must have been sorted by the same Sort, and
         * sort field values must have been filled.
         *
         * @see .merge
         * @lucene.experimental
         */
        fun merge(sort: Sort, topN: Int, shardHits: Array<TopFieldDocs>): TopFieldDocs {
            return merge(sort, 0, topN, shardHits)
        }

        /**
         * Same as [.merge] but also ignores the top `start` top
         * docs. This is typically useful for pagination.
         *
         *
         * docIDs are expected to be in consistent pattern i.e. either all ScoreDocs have their
         * shardIndex set, or all have them as -1 (signifying that all hits belong to same searcher)
         *
         * @lucene.experimental
         */
        fun merge(sort: Sort, start: Int, topN: Int, shardHits: Array<TopFieldDocs>): TopFieldDocs {
            requireNotNull(sort) { "sort must be non-null when merging field-docs" }
            return mergeAux(
                sort,
                start,
                topN,
                shardHits.map { it as TopDocs }.toTypedArray(),
                DEFAULT_TIE_BREAKER
            ) as TopFieldDocs
        }

        /**
         * Pass in a custom tie breaker for ordering results
         *
         * @lucene.experimental
         */
        fun merge(
            sort: Sort,
            start: Int,
            topN: Int,
            shardHits: Array<TopFieldDocs>,
            tieBreaker: Comparator<ScoreDoc>
        ): TopFieldDocs {
            requireNotNull(sort) { "sort must be non-null when merging field-docs" }
            return mergeAux(
                sort,
                start,
                topN,
                shardHits.map { it as TopDocs }.toTypedArray(),
                tieBreaker
            ) as TopFieldDocs
        }

        /**
         * Auxiliary method used by the [.merge] impls. A sort value of null is used to indicate
         * that docs should be sorted by score.
         */
        private fun mergeAux(
            sort: Sort?, start: Int, size: Int, shardHits: Array<TopDocs>, tieBreaker: Comparator<ScoreDoc>
        ): TopDocs {
            val queue: PriorityQueue<ShardRef> = if (sort == null) {
                ScoreMergeSortQueue(shardHits, tieBreaker)
            } else {
                MergeSortQueue(sort, shardHits, tieBreaker)
            }

            var totalHitCount: Long = 0
            var totalHitsRelation: TotalHits.Relation = TotalHits.Relation.EQUAL_TO
            var availHitCount = 0
            for (shardIDX in shardHits.indices) {
                val shard = shardHits[shardIDX]
                totalHitCount += shard.totalHits.value
                if (shard.totalHits.relation == TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO) {
                    totalHitsRelation = TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
                }
                if (shard.scoreDocs.isNotEmpty()) {
                    availHitCount += shard.scoreDocs.size
                    queue.add(ShardRef(shardIDX))
                }
            }

            val hits: Array<ScoreDoc?>
            var unsetShardIndex = false
            if (availHitCount <= start) {
                hits = kotlin.arrayOfNulls(0)
            } else {
                hits = kotlin.arrayOfNulls(min(size, availHitCount - start))
                val requestedResultWindow = start + size
                val numIterOnHits = min(availHitCount, requestedResultWindow)
                var hitUpto = 0
                while (hitUpto < numIterOnHits) {
                    require(queue.size() > 0)
                    val ref: ShardRef = queue.top()
                    val hit: ScoreDoc = shardHits[ref.shardIndex].scoreDocs[ref.hitIndex++]

                    if (hitUpto > 0) {
                        require(unsetShardIndex == (hit.shardIndex == -1)) { "Inconsistent order of shard indices" }
                    }

                    unsetShardIndex = unsetShardIndex or (hit.shardIndex == -1)

                    if (hitUpto >= start) {
                        hits[hitUpto - start] = hit
                    }

                    hitUpto++

                    if (ref.hitIndex < shardHits[ref.shardIndex].scoreDocs.size) {
                        queue.updateTop()
                    } else {
                        queue.pop()
                    }
                }
            }

            val totalHits = TotalHits(totalHitCount, totalHitsRelation)
            return if (sort == null) {
                TopDocs(totalHits, hits as Array<ScoreDoc>)
            } else {
                TopFieldDocs(totalHits, hits as Array<ScoreDoc>, sort.sort)
            }
        }

        /**
         * Reciprocal Rank Fusion method.
         *
         *
         * This method combines different search results into a single ranked list by combining their
         * ranks. This is especially well suited when combining hits computed via different methods, whose
         * score distributions are hardly comparable.
         *
         * @param topN the top N results to be returned
         * @param k a constant determines how much influence documents in individual rankings have on the
         * final result. A higher value gives lower rank documents more influence. k should be greater
         * than or equal to 1.
         * @param hits a list of TopDocs to apply RRF on
         * @return a TopDocs contains the top N ranked results.
         */
        fun rrf(topN: Int, k: Int, hits: Array<TopDocs>): TopDocs {
            require(topN >= 1) { "topN must be >= 1, got $topN" }
            require(k >= 1) { "k must be >= 1, got $k" }

            var shardIndexSet: Boolean? = null
            for (topDocs in hits) {
                for (scoreDoc in topDocs.scoreDocs) {
                    val thisShardIndexSet = scoreDoc.shardIndex != -1
                    if (shardIndexSet == null) {
                        shardIndexSet = thisShardIndexSet
                    } else require(shardIndexSet == thisShardIndexSet) { "All hits must either have their ScoreDoc#shardIndex set, or unset (-1), not a mix of both." }
                }
            }

            // Compute the rrf score as a double to reduce accuracy loss due to floating-point arithmetic.
            val rrfScore: MutableMap<ShardIndexAndDoc, Double> = mutableMapOf()
            var totalHitCount: Long = 0
            for (topDoc in hits) {
                // A document is a hit globally if it is a hit for any of the top docs, so we compute the
                // total hit count as the max total hit count.
                totalHitCount = kotlin.math.max(totalHitCount, topDoc.totalHits.value)
                for (i in topDoc.scoreDocs.indices) {
                    val scoreDoc: ScoreDoc = topDoc.scoreDocs[i]
                    val rank: Int = i + 1
                    val rrfScoreContribution: Double = 1.0 / addExact(k, rank)
                    val key = ShardIndexAndDoc(scoreDoc.shardIndex, scoreDoc.doc)
                    val oldValue = rrfScore[key] ?: 0.0
                    rrfScore[key] = oldValue + rrfScoreContribution
                }
            }

            val rrfScoreRank: MutableList<MutableMap.MutableEntry<ShardIndexAndDoc, Double>> =
                rrfScore.entries.toMutableList()
            rrfScoreRank.sortWith(compareByDescending<MutableMap.MutableEntry<ShardIndexAndDoc, Double>> { it.value }
                .thenBy { it.key.doc }
                .thenBy { it.key.shardIndex }
            )

            val rrfScoreDocs: Array<ScoreDoc?> = kotlin.arrayOfNulls(min(topN, rrfScoreRank.size))
            for (i in rrfScoreDocs.indices) {
                val entry = rrfScoreRank[i]
                val doc = entry.key.doc
                val shardIndex = entry.key.shardIndex
                val score = entry.value.toFloat()
                rrfScoreDocs[i] = ScoreDoc(doc, score, shardIndex)
            }

            val totalHits = TotalHits(totalHitCount, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO)
            return TopDocs(totalHits, rrfScoreDocs as Array<ScoreDoc>)
        }
    }
}
