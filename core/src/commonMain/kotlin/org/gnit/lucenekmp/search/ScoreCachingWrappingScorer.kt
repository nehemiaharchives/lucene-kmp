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
class ScoreCachingWrappingScorer(scorer: Scorable) :
    Scorable() {
    var scoreIsCached = false
    private var curScore = 0f
    private val `in`: Scorable = scorer

    class ScoreCachingWrappingLeafCollector(`in`: LeafCollector) :
        FilterLeafCollector(`in`) {
        override var scorer: Scorable? = null
            set(scorer) {
                field = ScoreCachingWrappingScorer(scorer!!)
                super.scorer = this.scorer!!
            }

        @Throws(IOException::class)
        override fun collect(doc: Int) {
            if (scorer != null && scorer is ScoreCachingWrappingScorer) {
                // Invalidate cache when collecting a new doc
                (scorer as ScoreCachingWrappingScorer).scoreIsCached = false
            }
            super.collect(doc)
        }

        @Throws(IOException::class)
        override fun competitiveIterator(): DocIdSetIterator? {
            return `in`.competitiveIterator()
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
