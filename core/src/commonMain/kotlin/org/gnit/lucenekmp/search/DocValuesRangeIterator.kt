package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.DocValuesSkipper


/**
 * Wrapper around a [TwoPhaseIterator] for a doc-values range query that speeds things up by
 * taking advantage of a [DocValuesSkipper].
 *
 * @lucene.experimental
 */
class DocValuesRangeIterator(
    private val innerTwoPhase: TwoPhaseIterator,
    skipper: DocValuesSkipper,
    lowerValue: Long,
    upperValue: Long,
    queryRangeHasGaps: Boolean
) : TwoPhaseIterator(
    if (queryRangeHasGaps)
        RangeWithGapsApproximation(
            innerTwoPhase.approximation(), skipper, lowerValue, upperValue
        )
    else
        RangeNoGapsApproximation(
            innerTwoPhase.approximation(), skipper, lowerValue, upperValue
        )
) {
    enum class Match {
        /** None of the documents in the range match  */
        NO,

        /** Document values need to be checked to verify matches  */
        MAYBE,

        /** All documents in the range that have a value match  */
        IF_DOC_HAS_VALUE,

        /** All docs in the range match  */
        YES
    }

    override val approximation: Approximation

    init {
        this.approximation = approximation() as Approximation
    }

    abstract class Approximation(
        private val innerApproximation: DocIdSetIterator,
        skipper: DocValuesSkipper,
        lowerValue: Long,
        upperValue: Long
    ) : DocIdSetIterator() {
        protected val skipper: DocValuesSkipper
        protected val lowerValue: Long
        protected val upperValue: Long

        private var doc = -1

        // Track a decision for all doc IDs between the current doc ID and upTo inclusive.
        var match: Match = Match.MAYBE
        var upTo: Int = -1

        init {
            this.skipper = skipper
            this.lowerValue = lowerValue
            this.upperValue = upperValue
        }

        override fun docID(): Int {
            return doc
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return advance(docID() + 1)
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            var target = target
            while (true) {
                if (target > upTo) {
                    skipper.advance(target)
                    // If target doesn't have a value and is between two blocks, it is possible that advance()
                    // moved to a block that doesn't contain `target`.
                    target = kotlin.math.max(target, skipper.minDocID(0))
                    if (target == NO_MORE_DOCS) {
                        return NO_MORE_DOCS.also { doc = it }
                    }
                    upTo = skipper.maxDocID(0)
                    match = match(0)

                    // If we have a YES or NO decision, see if we still have the same decision on a higher
                    // level (= on a wider range of doc IDs)
                    var nextLevel = 1
                    while (match != Match.MAYBE && nextLevel < skipper.numLevels() && match == match(nextLevel)) {
                        upTo = skipper.maxDocID(nextLevel)
                        nextLevel++
                    }
                }
                when (match) {
                    Match.YES -> return target.also { doc = it }
                    Match.MAYBE, Match.IF_DOC_HAS_VALUE -> {
                        if (target > innerApproximation.docID()) {
                            target = innerApproximation.advance(target)
                        }
                        if (target <= upTo) {
                            return target.also { doc = it }
                        }
                    }

                    Match.NO -> {
                        if (upTo == NO_MORE_DOCS) {
                            return NO_MORE_DOCS.also { doc = it }
                        }
                        target = upTo + 1
                    }

                    /*else -> throw AssertionError("Unknown enum constant: " + match)*/
                }
            }
        }

        override fun cost(): Long {
            return innerApproximation.cost()
        }

        protected abstract fun match(level: Int): Match
    }

    private class RangeNoGapsApproximation(
        innerApproximation: DocIdSetIterator,
        skipper: DocValuesSkipper,
        lowerValue: Long,
        upperValue: Long
    ) : Approximation(innerApproximation, skipper, lowerValue, upperValue) {
        override fun match(level: Int): Match {
            val minValue: Long = skipper.minValue(level)
            val maxValue: Long = skipper.maxValue(level)
            if (minValue > upperValue || maxValue < lowerValue) {
                return Match.NO
            } else if (minValue >= lowerValue && maxValue <= upperValue) {
                if (skipper.docCount(level) == skipper.maxDocID(level) - skipper.minDocID(level) + 1) {
                    return Match.YES
                } else {
                    return Match.IF_DOC_HAS_VALUE
                }
            } else {
                return Match.MAYBE
            }
        }
    }

    private class RangeWithGapsApproximation(
        innerApproximation: DocIdSetIterator,
        skipper: DocValuesSkipper,
        lowerValue: Long,
        upperValue: Long
    ) : Approximation(innerApproximation, skipper, lowerValue, upperValue) {
        override fun match(level: Int): Match {
            val minValue: Long = skipper.minValue(level)
            val maxValue: Long = skipper.maxValue(level)
            if (minValue > upperValue || maxValue < lowerValue) {
                return Match.NO
            } else {
                return Match.MAYBE
            }
        }
    }

    @Throws(IOException::class)
    override fun matches(): Boolean {
        return when (approximation.match) {
            Match.YES -> true
            Match.IF_DOC_HAS_VALUE -> true
            Match.MAYBE -> innerTwoPhase.matches()
            Match.NO -> throw IllegalStateException("Unpositioned approximation")
        }
    }

    override fun matchCost(): Float {
        return innerTwoPhase.matchCost()
    }
}
