package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import okio.IOException

/** Expert: Weight class for phrase matching  */
abstract class PhraseWeight protected constructor(
    query: Query,
    val field: String,
    searcher: IndexSearcher,
    val scoreMode: ScoreMode
) : Weight(query) {
    val stats: SimScorer
    val similarity: Similarity = searcher.getSimilarity()

    /**
     * Expert: Creates PhraseWeight instance
     *
     * @lucene.internal
     */
    init {
        var stats: SimScorer = getStats(searcher)
        if (stats == null) { // Means no terms or scores are not needed
            stats =
                object : SimScorer() {
                    override fun score(freq: Float, norm: Long): Float {
                        return 1f
                    }
                }
        }
        this.stats = stats
    }

    @Throws(IOException::class)
    protected abstract fun getStats(searcher: IndexSearcher): SimScorer

    @Throws(IOException::class)
    protected abstract fun getPhraseMatcher(
        context: LeafReaderContext,
        scorer: SimScorer,
        exposeOffsets: Boolean
    ): PhraseMatcher

    @Throws(IOException::class)
    override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
        val matcher: PhraseMatcher = getPhraseMatcher(context, stats, false)
        if (matcher == null) return null
        val norms: NumericDocValues? =
            if (scoreMode.needsScores()) context.reader().getNormValues(field) else null
        val scorer = PhraseScorer(matcher, scoreMode, stats, norms)
        return DefaultScorerSupplier(scorer)
    }

    @Throws(IOException::class)
    override fun explain(
        context: LeafReaderContext,
        doc: Int
    ): Explanation {
        val matcher: PhraseMatcher = getPhraseMatcher(context, stats, false)
        if (matcher == null || matcher.approximation().advance(doc) != doc) {
            return Explanation.noMatch("no matching terms")
        }
        matcher.reset()
        if (!matcher.nextMatch()) {
            return Explanation.noMatch("no matching phrase")
        }
        var freq: Float = matcher.sloppyWeight()
        while (matcher.nextMatch()) {
            freq += matcher.sloppyWeight()
        }
        val freqExplanation: Explanation =
            Explanation.match(freq, "phraseFreq=$freq")
        val norms: NumericDocValues? =
            if (scoreMode.needsScores()) context.reader().getNormValues(field) else null
        var norm = 1L
        if (norms != null && norms.advanceExact(doc)) {
            norm = norms.longValue()
        }
        val scoreExplanation: Explanation = stats.explain(freqExplanation, norm)
        return Explanation.match(
            scoreExplanation.value,
            ("weight("
                    + query
                    + " in "
                    + doc
                    + ") ["
                    + similarity::class.simpleName
                    + "], result of:"),
            scoreExplanation
        )
    }

    @Throws(IOException::class)
    override fun matches(
        context: LeafReaderContext,
        doc: Int
    ): Matches? {
        return MatchesUtils.forField(
            field
        ) {
            val matcher: PhraseMatcher = getPhraseMatcher(context, stats, true)
            if (matcher == null || matcher.approximation().advance(doc) != doc) {
                null
            }
            matcher.reset()
            if (!matcher.nextMatch()) {
                null
            }
            object : MatchesIterator {
                var started: Boolean = false

                @Throws(IOException::class)
                override fun next(): Boolean {
                    if (!started) {
                        return true.also { started = it }
                    }
                    return matcher.nextMatch()
                }

                override fun startPosition(): Int {
                    return matcher.startPosition()
                }

                override fun endPosition(): Int {
                    return matcher.endPosition()
                }

                @Throws(IOException::class)
                override fun startOffset(): Int {
                    return matcher.startOffset()
                }

                @Throws(IOException::class)
                override fun endOffset(): Int {
                    return matcher.endOffset()
                }

                override val subMatches: MatchesIterator?
                    get() {
                        return null // phrases are treated as leaves
                    }

                override val query: Query
                    get() {
                        return this@PhraseWeight.query
                    }
            }
        }
    }

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
        return true
    }
}
