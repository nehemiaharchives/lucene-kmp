package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext


/**
 * A Weight that has a constant score equal to the boost of the wrapped query. This is typically
 * useful when building queries which do not produce meaningful scores and are mostly useful for
 * filtering.
 *
 * @lucene.internal
 */
abstract class ConstantScoreWeight protected constructor(query: Query, private val score: Float) : Weight(query) {
    /** Return the score produced by this [Weight].  */
    protected fun score(): Float {
        return score
    }

    @Throws(IOException::class)
    public override fun explain(context: LeafReaderContext, doc: Int): Explanation {
        val s = scorer(context)
        val exists: Boolean
        if (s == null) {
            exists = false
        } else {
            val twoPhase = s.twoPhaseIterator()
            if (twoPhase == null) {
                exists = s.iterator().advance(doc) == doc
            } else {
                exists = twoPhase.approximation().advance(doc) == doc && twoPhase.matches()
            }
        }

        if (exists) {
            return Explanation.match(score, query.toString() + (if (score == 1f) "" else "^$score"))
        } else {
            return Explanation.noMatch("$query doesn't match id $doc")
        }
    }
}
