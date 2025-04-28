package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import kotlin.jvm.JvmRecord


/** Allows access to the score of a Query  */
abstract class Scorable {
    /** Returns the score of the current document matching the query.  */
    @Throws(IOException::class)
    abstract fun score(): Float

    /**
     * Returns the smoothing score of the current document matching the query. This score is used when
     * the query/term does not appear in the document, and behaves like an idf. The smoothing score is
     * particularly important when the Scorer returns a product of probabilities so that the document
     * score does not go to zero when one probability is zero. This can return 0 or a smoothing score.
     *
     *
     * Smoothing scores are described in many papers, including: Metzler, D. and Croft, W. B. ,
     * "Combining the Language Model and Inference Network Approaches to Retrieval," Information
     * Processing and Management Special Issue on Bayesian Networks and Information Retrieval, 40(5),
     * pp.735-750.
     */
    @Throws(IOException::class)
    open fun smoothingScore(docId: Int): Float {
        return 0f
    }

    /**
     * Optional method: Tell the scorer that its iterator may safely ignore all documents whose score
     * is less than the given `minScore`. This is a no-op by default.
     *
     *
     * This method may only be called from collectors that use [ScoreMode.TOP_SCORES], and
     * successive calls may only set increasing values of `minScore`.
     */
    open var minCompetitiveScore: Float = 0F

    @get:Throws(IOException::class)
    open val children: MutableCollection<ChildScorable>
        /**
         * Returns child sub-scorers positioned on the current document
         *
         * @lucene.experimental
         */
        get() = mutableListOf<ChildScorable>()

    /**
     * A child Scorer and its relationship to its parent. The meaning of the relationship depends upon
     * the parent query.
     *
     *
     * The relationship can be any string that makes sense to the parent Scorer.
     *
     * @param child Child Scorer. (note this is typically a direct child, and may itself also have
     * children).
     * @param relationship An arbitrary string relating this scorer to the parent.
     * @lucene.experimental
     */
    @JvmRecord
    data class ChildScorable(val child: Scorable, val relationship: String)
}
