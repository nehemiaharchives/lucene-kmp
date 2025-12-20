package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.BytesRef

/**
 * Implementation class for [MaxNonCompetitiveBoostAttribute].
 *
 * @lucene.internal
 */
class MaxNonCompetitiveBoostAttributeImpl : AttributeImpl(), MaxNonCompetitiveBoostAttribute {
    override var maxNonCompetitiveBoost: Float = Float.NEGATIVE_INFINITY
        set(value) {
            field = value
        }

    private var competitiveTermValue: BytesRef? = null

    override fun setCompetitiveTerm(competitiveTerm: BytesRef?) {
        competitiveTermValue = competitiveTerm
    }

    override val competitiveTerm: BytesRef?
        get() = competitiveTermValue

    override fun clear() {
        maxNonCompetitiveBoost = Float.NEGATIVE_INFINITY
        competitiveTermValue = null
    }

    override fun copyTo(target: AttributeImpl) {
        val t = target as MaxNonCompetitiveBoostAttribute
        t.maxNonCompetitiveBoost = maxNonCompetitiveBoost
        t.setCompetitiveTerm(competitiveTermValue)
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(
            MaxNonCompetitiveBoostAttribute::class,
            "maxNonCompetitiveBoost",
            maxNonCompetitiveBoost
        )
        reflector.reflect(MaxNonCompetitiveBoostAttribute::class, "competitiveTerm", competitiveTermValue)
    }

    override fun newInstance(): AttributeImpl = MaxNonCompetitiveBoostAttributeImpl()
}
