package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.BytesRef


/**
 * Add this [Attribute] to a fresh [AttributeSource] before calling [ ][MultiTermQuery.getTermsEnum]. [FuzzyQuery] is using this to control
 * its internal behaviour to only return competitive terms.
 *
 *
 * **Please note:** This attribute is intended to be added by the [ ] to an empty [AttributeSource] that is shared for all segments
 * during query rewrite. This attribute source is passed to all segment enums on [ ][MultiTermQuery.getTermsEnum]. [TopTermsRewrite] uses this attribute
 * to inform all enums about the current boost, that is not competitive.
 *
 * @lucene.internal
 */
interface MaxNonCompetitiveBoostAttribute : Attribute {
    /**
     * This is the maximum boost that would not be competitive. Default is negative infinity, which
     * means every term is competitive.
     */
    /** This is the maximum boost that would not be competitive.  */
    var maxNonCompetitiveBoost: Float

    /** This is the term or `null` of the term that triggered the boost change.  */
    fun setCompetitiveTerm(competitiveTerm: BytesRef?)

    /**
     * This is the term or `null` of the term that triggered the boost change. Default is
     * `null`, which means every term is competitoive.
     */
    val competitiveTerm: BytesRef?
}
