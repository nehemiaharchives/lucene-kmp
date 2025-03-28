package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.Attribute


/**
 * Add this [Attribute] to a [TermsEnum] returned by [ ][MultiTermQuery.getTermsEnum] and update the boost on each returned term.
 * This enables to control the boost factor for each matching term in [ ][MultiTermQuery.SCORING_BOOLEAN_REWRITE] or [TopTermsRewrite] mode. [FuzzyQuery] is
 * using this to take the edit distance into account.
 *
 *
 * **Please note:** This attribute is intended to be added only by the TermsEnum to itself in
 * its constructor and consumed by the [MultiTermQuery.RewriteMethod].
 *
 * @lucene.internal
 */
interface BoostAttribute : Attribute {
    /** Retrieves the boost, default is `1.0f`.  */
    /** Sets the boost in this attribute  */
    var boost: Float

    companion object {
        const val DEFAULT_BOOST: Float = 1.0f
    }
}
