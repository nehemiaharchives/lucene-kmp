package org.gnit.lucenekmp.search

import okio.IOException


/**
 * A [Scorer] which wraps another scorer and caches the score of the current document.
 * Successive calls to [.score] will return the same result and will not invoke the wrapped
 * Scorer's score() method, unless the current document has changed.<br></br>
 * This class might be useful due to the changes done to the [Collector] interface, in which
 * the score is not computed for a document by default, only if the collector requests it. Some
 * collectors may need to use the score in several places, however all they have in hand is a [ ] object, and might end up computing the score of a document more than once.
 */
class ScoreCachingWrappingScorer private constructor(scorer: Scorable) :
    Scorable() {
    private var scoreIsCached = false
    private var curScore = 0f
    private val `in`: Scorable = scorer

    private class ScoreCachingWrappingLeafCollector(`in`: LeafCollector) :
        FilterLeafCollector(`in`) {
        private var scorer: ScoreCachingWrappingScorer? = null

        @Throws(IOException::class)
        override fun setScorer(scorer: Scorable) {
            this.scorer = ScoreCachingWrappingScorer(scorer)
            super.setScorer(this.scorer!!)
        }

        @Throws(IOException::class)
        override fun collect(doc: Int) {
            if (scorer != null) {
                // Invalidate cache when collecting a new doc
                scorer!!.scoreIsCached = false
            }
            super.collect(doc)
        }

        @Throws(IOException::class)
        override fun competitiveIterator(): DocIdSetIterator {
            return `in`.competitiveIterator()!!
        }
    }

    @Throws(IOException::class)
    override fun score(): Float {
        if (!scoreIsCached) {
            curScore = `in`.score()
            scoreIsCached = true
        }

        return curScore
    }

    override var minCompetitiveScore: Float
        set(minScore) {
            `in`.minCompetitiveScore = minScore
        }
        get() = `in`.minCompetitiveScore

    override val children: MutableCollection<ChildScorable>
        get() = mutableSetOf(ChildScorable(`in`, "CACHED"))

    companion object {
        /**
         * Wrap the provided [LeafCollector] so that scores are computed lazily and cached if
         * accessed multiple times.
         */
        fun wrap(collector: LeafCollector): LeafCollector {
            if (collector is ScoreCachingWrappingLeafCollector) {
                return collector
            }
            return ScoreCachingWrappingLeafCollector(collector)
        }
    }
}
