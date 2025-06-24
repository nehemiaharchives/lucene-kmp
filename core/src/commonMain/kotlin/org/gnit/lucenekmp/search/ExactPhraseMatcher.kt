package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.ImpactsSource
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.PriorityQueue
import okio.IOException
import org.gnit.lucenekmp.jdkport.compareUnsigned
import kotlin.math.min

/**
 * Expert: Find exact phrases
 *
 * @lucene.internal
 */
class ExactPhraseMatcher(
    postings: Array<PhraseQuery.PostingsAndFreq>,
    scoreMode: ScoreMode,
    scorer: SimScorer,
    matchCost: Float
) : PhraseMatcher(matchCost) {
    private class PostingsAndPosition(val postings: PostingsEnum, val offset: Int) {
        var freq = 0
        var upTo = 0
        var pos = 0
    }

    private val postings: Array<PostingsAndPosition>
    private val approximation: DocIdSetIterator
    private val impactsApproximation: ImpactsDISI

    /** Expert: Creates ExactPhraseMatcher instance  */
    init {
        val approximation: DocIdSetIterator =
            ConjunctionUtils.intersectIterators(postings.map { p -> p.postings }.toMutableList())
        val impactsSource: ImpactsSource = mergeImpacts(postings.map { p -> p.impacts }.toTypedArray())

        this.impactsApproximation =
            ImpactsDISI(
                approximation,
                MaxScoreCache(impactsSource, scorer)
            )
        if (scoreMode == ScoreMode.TOP_SCORES) {
            // TODO: only do this when this is the top-level scoring clause
            // (ScorerSupplier#setTopLevelScoringClause) to save the overhead of wrapping with ImpactsDISI
            // when it would not help
            this.approximation = impactsApproximation
        } else {
            this.approximation = approximation
        }

        val postingsAndPositions: MutableList<PostingsAndPosition> = ArrayList()
        for (posting in postings) {
            postingsAndPositions.add(PostingsAndPosition(posting.postings, posting.position))
        }
        this.postings =
            postingsAndPositions.toTypedArray<PostingsAndPosition>()
    }

    override fun approximation(): DocIdSetIterator {
        return approximation
    }

    override fun impactsApproximation(): ImpactsDISI {
        return impactsApproximation
    }

    override fun maxFreq(): Float {
        var minFreq = postings[0].freq
        for (i in 1..<postings.size) {
            minFreq = min(minFreq, postings[i].freq)
        }
        return minFreq.toFloat()
    }

    @Throws(IOException::class)
    override fun reset() {
        for (posting in postings) {
            posting.freq = posting.postings.freq()
            posting.pos = -1
            posting.upTo = 0
        }
    }

    @Throws(IOException::class)
    override fun nextMatch(): Boolean {
        val lead = postings[0]
        if (lead.upTo < lead.freq) {
            lead.pos = lead.postings.nextPosition()
            lead.upTo += 1
        } else {
            return false
        }
        advanceHead@ while (true) {
            val phrasePos = lead.pos - lead.offset
            for (j in 1..<postings.size) {
                val posting = postings[j]
                val expectedPos = phrasePos + posting.offset

                // advance up to the same position as the lead
                if (!advancePosition(posting, expectedPos)) {
                    break@advanceHead
                }

                if (posting.pos != expectedPos) { // we advanced too far
                    if (advancePosition(lead, posting.pos - posting.offset + lead.offset)) {
                        continue@advanceHead
                    } else {
                        break@advanceHead
                    }
                }
            }
            return true
        }
        return false
    }

    override fun sloppyWeight(): Float {
        return 1f
    }

    override fun startPosition(): Int {
        return postings[0].pos
    }

    override fun endPosition(): Int {
        return postings[postings.size - 1].pos
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        return postings[0].postings.startOffset()
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        return postings[postings.size - 1].postings.endOffset()
    }

    companion object {
        /**
         * Advance the given pos enum to the first position on or after `target`. Return `false` if the enum was exhausted before reaching `target` and `true` otherwise.
         */
        @Throws(IOException::class)
        private fun advancePosition(posting: PostingsAndPosition, target: Int): Boolean {
            while (posting.pos < target) {
                if (posting.upTo == posting.freq) {
                    return false
                } else {
                    posting.pos = posting.postings.nextPosition()
                    posting.upTo += 1
                }
            }
            return true
        }

        /** Merge impacts for multiple terms of an exact phrase.  */
        fun mergeImpacts(impactsEnums: Array<ImpactsEnum>): ImpactsSource {
            // Iteration of block boundaries uses the impacts enum with the lower cost.
            // This is consistent with BlockMaxConjunctionScorer.
            var tmpLeadIndex = -1
            for (i in impactsEnums.indices) {
                if (tmpLeadIndex == -1 || impactsEnums[i].cost() < impactsEnums[tmpLeadIndex].cost()) {
                    tmpLeadIndex = i
                }
            }
            val leadIndex = tmpLeadIndex

            return object : ImpactsSource {
                inner class SubIterator(impacts: MutableList<Impact>) {
                    val iterator: MutableIterator<Impact> = impacts.iterator()
                    var current: Impact?

                    init {
                        this.current = iterator.next()
                    }

                    fun next(): Boolean {
                        if (!iterator.hasNext()) {
                            current = null
                            return false
                        } else {
                            current = iterator.next()
                            return true
                        }
                    }
                }

                override val impacts: Impacts
                    get() {
                        val impacts: Array<Impacts> = Array(impactsEnums.size) { i ->
                            impactsEnums[i].impacts
                        }
                        val lead: Impacts = impacts[leadIndex]
                        return object : Impacts() {
                            override fun numLevels(): Int {
                                // Delegate to the lead
                                return lead.numLevels()
                            }

                            override fun getDocIdUpTo(level: Int): Int {
                                // Delegate to the lead
                                return lead.getDocIdUpTo(level)
                            }

                            /**
                             * Return the minimum level whose impacts are valid up to `docIdUpTo`, or `-1`
                             * if there is no such level.
                             */
                            fun getLevel(impacts: Impacts, docIdUpTo: Int): Int {
                                var level = 0
                                val numLevels: Int = impacts.numLevels()
                                while (level < numLevels) {
                                    if (impacts.getDocIdUpTo(level) >= docIdUpTo) {
                                        return level
                                    }
                                    ++level
                                }
                                return -1
                            }

                            override fun getImpacts(level: Int): MutableList<Impact> {
                                val docIdUpTo = getDocIdUpTo(level)

                                val pq: PriorityQueue<SubIterator> =
                                    object : PriorityQueue<SubIterator>(impacts.size) {
                                        override fun lessThan(a: SubIterator, b: SubIterator): Boolean {
                                            return a.current!!.freq < b.current!!.freq
                                        }
                                    }

                                var hasImpacts = false
                                var onlyImpactList: MutableList<Impact>? = null
                                val subIterators: MutableList<SubIterator> =
                                    ArrayList(impacts.size)
                                for (i in impacts.indices) {
                                    val impactsLevel = getLevel(impacts[i], docIdUpTo)
                                    if (impactsLevel == -1) {
                                        // This instance doesn't have useful impacts, ignore it: this is safe.
                                        continue
                                    }

                                    val impactList: MutableList<Impact> =
                                        impacts[i].getImpacts(impactsLevel)
                                    val firstImpact: Impact = impactList[0]
                                    if (firstImpact.freq == Int.Companion.MAX_VALUE && firstImpact.norm == 1L) {
                                        // Dummy impacts, ignore it too.
                                        continue
                                    }

                                    val subIterator = SubIterator(impactList)
                                    subIterators.add(subIterator)
                                    if (!hasImpacts) {
                                        hasImpacts = true
                                        onlyImpactList = impactList
                                    } else {
                                        onlyImpactList = null // there are multiple impacts
                                    }
                                }

                                if (!hasImpacts) {
                                    return mutableListOf(
                                        Impact(
                                            Int.Companion.MAX_VALUE, 1L
                                        )
                                    )
                                } else if (onlyImpactList != null) {
                                    return onlyImpactList
                                }

                                // Idea: merge impacts by freq. The tricky thing is that we need to
                                // consider freq values that are not in the impacts too. For
                                // instance if the list of impacts is [{freq=2,norm=10}, {freq=4,norm=12}],
                                // there might well be a document that has a freq of 2 and a length of 11,
                                // which was just not added to the list of impacts because {freq=2,norm=10}
                                // is more competitive.
                                // We walk impacts in parallel through a PQ ordered by freq. At any time,
                                // the competitive impact consists of the lowest freq among all entries of
                                // the PQ (the top) and the highest norm (tracked separately).
                                pq.addAll(subIterators)
                                val mergedImpacts: MutableList<Impact> = ArrayList()
                                var top: SubIterator = pq.top()
                                var currentFreq: Int = top.current!!.freq
                                var currentNorm: Long = 0
                                for (it in pq) {
                                    if (Long.compareUnsigned(it.current!!.norm, currentNorm) > 0) {
                                        currentNorm = it.current!!.norm
                                    }
                                }

                                outer@ while (true) {
                                    if (mergedImpacts.isNotEmpty()
                                        && mergedImpacts[mergedImpacts.size - 1].norm == currentNorm
                                    ) {
                                        mergedImpacts[mergedImpacts.size - 1].freq = currentFreq
                                    } else {
                                        mergedImpacts.add(Impact(currentFreq, currentNorm))
                                    }

                                    do {
                                        if (!top.next()) {
                                            // At least one clause doesn't have any more documents below the current norm,
                                            // so we can safely ignore further clauses. The only reason why they have more
                                            // impacts is because they cover more documents that we are not interested in.
                                            break@outer
                                        }
                                        if (Long.compareUnsigned(top.current!!.norm, currentNorm) > 0) {
                                            currentNorm = top.current!!.norm
                                        }
                                        top = pq.updateTop()
                                    } while (top.current!!.freq == currentFreq)

                                    currentFreq = top.current!!.freq
                                }

                                return mergedImpacts
                            }
                        }
                    }

                @Throws(IOException::class)
                override fun advanceShallow(target: Int) {
                    for (impactsEnum in impactsEnums) {
                        impactsEnum.advanceShallow(target)
                    }
                }
            }
        }
    }
}
