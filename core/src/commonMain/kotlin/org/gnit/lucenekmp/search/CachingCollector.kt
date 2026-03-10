package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.ArrayUtil

/**
 * Caches all docs, and optionally also scores, coming from a search, and is then able to replay
 * them to another collector. You specify the max RAM this class may use. Once the collection is
 * done, call [isCached]. If this returns true, you can use [replay]
 * against a new collector. If it returns false, this means too much RAM was required and you must
 * instead re-run the original search.
 *
 * <p><b>NOTE</b>: this class consumes 4 (or 8 bytes, if scoring is cached) per collected document.
 * If the result set is large this can easily be a very substantial amount of RAM!
 *
 * <p>See the Lucene `modules/grouping` module for more details including a full code example.
 *
 * @lucene.experimental
 */
abstract class CachingCollector : FilterCollector {
    private class CachedScorable : Scorable() {
        // NOTE: these members are package-private b/c that way accessing them from
        // the outer class does not incur access check by the JVM. The same
        // situation would be if they were defined in the outer class as private
        // members.
        var score: Float = 0f

        override fun score(): Float {
            return score
        }
    }

    private open class NoScoreCachingCollector(`in`: Collector, var maxDocsToCache: Int) : CachingCollector(`in`) {
        var contexts: MutableList<LeafReaderContext>? = mutableListOf()
        var docs: MutableList<IntArray>? = mutableListOf()

        protected open fun wrap(`in`: LeafCollector, maxDocsToCache: Int): NoScoreCachingLeafCollector {
            return NoScoreCachingLeafCollector(`in`, maxDocsToCache, this)
        }

        // note: do *not* override needScore to say false. Just because we aren't caching the score
        // doesn't mean the
        //   wrapped collector doesn't need it to do its job.

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            val `in`: LeafCollector = this.`in`.getLeafCollector(context)
            return if (maxDocsToCache >= 0) {
                if (contexts != null) {
                    contexts!!.add(context)
                }
                wrap(`in`, maxDocsToCache)
            } else {
                `in`
            }
        }

        open fun invalidate() {
            maxDocsToCache = -1
            contexts = null
            docs = null
        }

        @Throws(IOException::class)
        protected open fun collect(collector: LeafCollector, i: Int) {
            val docs: IntArray = this.docs!![i]
            for (doc in docs) {
                collector.collect(doc)
            }
            collector.finish()
        }

        @Throws(IOException::class)
        override fun replay(other: Collector) {
            check(isCached()) {
                "cannot replay: cache was cleared because too much RAM was required"
            }
            assert(docs!!.size == contexts!!.size)
            for (i in 0..<contexts!!.size) {
                val context: LeafReaderContext = contexts!![i]
                val collector: LeafCollector = other.getLeafCollector(context)
                collect(collector, i)
            }
        }
    }

    private class ScoreCachingCollector(`in`: Collector, maxDocsToCache: Int) : NoScoreCachingCollector(`in`, maxDocsToCache) {
        val scores: MutableList<FloatArray> = mutableListOf()

        override fun wrap(`in`: LeafCollector, maxDocsToCache: Int): NoScoreCachingLeafCollector {
            return ScoreCachingLeafCollector(`in`, maxDocsToCache, this)
        }

        /**
         * Ensure the scores are collected so they can be replayed, even if the wrapped collector
         * doesn't need them.
         */
        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }

        @Throws(IOException::class)
        override fun collect(collector: LeafCollector, i: Int) {
            val docs: IntArray = this.docs!![i]
            val scores: FloatArray = this.scores[i]
            assert(docs.size == scores.size)
            val scorer = CachedScorable()
            collector.scorer = scorer
            for (j in docs.indices) {
                scorer.score = scores[j]
                collector.collect(docs[j])
            }
        }
    }

    private open inner class NoScoreCachingLeafCollector(`in`: LeafCollector, val maxDocsToCache: Int, val collector: NoScoreCachingCollector) : FilterLeafCollector(`in`) {
        var docs: IntArray? = IntArray(kotlin.math.min(maxDocsToCache, INITIAL_ARRAY_SIZE))
        var docCount: Int = 0

        protected open fun grow(newLen: Int) {
            docs = ArrayUtil.growExact(docs!!, newLen)
        }

        protected open fun invalidate() {
            docs = null
            docCount = -1
            cached = false
        }

        @Throws(IOException::class)
        protected open fun buffer(doc: Int) {
            docs!![docCount] = doc
        }

        @Throws(IOException::class)
        override fun collect(doc: Int) {
            if (docs != null) {
                if (docCount >= docs!!.size) {
                    if (docCount >= maxDocsToCache) {
                        invalidate()
                    } else {
                        val newLen = minOf(ArrayUtil.oversize(docCount + 1, Int.SIZE_BYTES), maxDocsToCache)
                        grow(newLen)
                    }
                }
                if (docs != null) {
                    buffer(doc)
                    ++docCount
                }
            }
            super.collect(doc)
        }

        protected open fun postCollect() {
            val docs: IntArray = cachedDocs()!!
            collector.maxDocsToCache -= docs.size
            collector.docs!!.add(docs)
        }

        @Throws(IOException::class)
        override fun finish() {
            if (!hasCache()) {
                collector.invalidate()
            } else {
                postCollect()
            }
        }

        fun hasCache(): Boolean {
            return docs != null
        }

        fun cachedDocs(): IntArray? {
            return if (docs == null) null else ArrayUtil.copyOfSubArray(docs!!, 0, docCount)
        }
    }

    private inner class ScoreCachingLeafCollector(`in`: LeafCollector, maxDocsToCache: Int, collector: ScoreCachingCollector) : NoScoreCachingLeafCollector(`in`, maxDocsToCache, collector) {
        override var scorer: Scorable? = null
            set(scorer) {
                field = scorer
                super.scorer = scorer
            }
        var scores: FloatArray = FloatArray(docs!!.size)

        override fun grow(newLen: Int) {
            super.grow(newLen)
            scores = ArrayUtil.growExact(scores, newLen)
        }

        override fun invalidate() {
            super.invalidate()
            scores = FloatArray(0)
        }

        @Throws(IOException::class)
        override fun buffer(doc: Int) {
            super.buffer(doc)
            scores[docCount] = scorer!!.score()
        }

        fun cachedScores(): FloatArray? {
            return if (docs == null) null else ArrayUtil.copyOfSubArray(scores, 0, docCount)
        }

        override fun postCollect() {
            super.postCollect()
            (collector as ScoreCachingCollector).scores.add(cachedScores()!!)
        }
    }

    private var cached: Boolean = true

    private constructor(`in`: Collector) : super(`in`)

    /** Return true is this collector is able to replay collection. */
    fun isCached(): Boolean {
        return cached
    }

    @Throws(IOException::class)
    abstract fun replay(other: Collector)

    companion object {
        private const val INITIAL_ARRAY_SIZE = 128

        /**
         * Creates a [CachingCollector] which does not wrap another collector. The cached documents
         * and scores can later be [replay]ed.
         */
        fun create(cacheScores: Boolean, maxRAMMB: Double): CachingCollector {
            val other: Collector = object : SimpleCollector() {
                override var weight: Weight? = null

                @Throws(IOException::class)
                override fun collect(doc: Int) {
                }

                override fun scoreMode(): ScoreMode {
                    return ScoreMode.COMPLETE
                }
            }
            return create(other, cacheScores, maxRAMMB)
        }

        /**
         * Create a new [CachingCollector] that wraps the given collector and caches documents and
         * scores up to the specified RAM threshold.
         *
         * @param other the Collector to wrap and delegate calls to.
         * @param cacheScores whether to cache scores in addition to document IDs. Note that this
         * increases the RAM consumed per doc
         * @param maxRAMMB the maximum RAM in MB to consume for caching the documents and scores. If the
         * collector exceeds the threshold, no documents and scores are cached.
         */
        fun create(other: Collector, cacheScores: Boolean, maxRAMMB: Double): CachingCollector {
            var bytesPerDoc = Int.SIZE_BYTES
            if (cacheScores) {
                bytesPerDoc += Float.SIZE_BYTES
            }
            val maxDocsToCache = ((maxRAMMB * 1024 * 1024) / bytesPerDoc).toInt()
            return create(other, cacheScores, maxDocsToCache)
        }

        /**
         * Create a new [CachingCollector] that wraps the given collector and caches documents and
         * scores up to the specified max docs threshold.
         *
         * @param other the Collector to wrap and delegate calls to.
         * @param cacheScores whether to cache scores in addition to document IDs. Note that this
         * increases the RAM consumed per doc
         * @param maxDocsToCache the maximum number of documents for caching the documents and possible
         * the scores. If the collector exceeds the threshold, no documents and scores are cached.
         */
        fun create(other: Collector, cacheScores: Boolean, maxDocsToCache: Int): CachingCollector {
            return if (cacheScores) {
                ScoreCachingCollector(other, maxDocsToCache)
            } else {
                NoScoreCachingCollector(other, maxDocsToCache)
            }
        }
    }
}
