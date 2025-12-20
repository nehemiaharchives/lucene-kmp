package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector

/**
 * Implementation class for [BoostAttribute].
 *
 * @lucene.internal
 */
class BoostAttributeImpl : AttributeImpl(), BoostAttribute {
    override var boost: Float = BoostAttribute.DEFAULT_BOOST

    override fun clear() {
        boost = BoostAttribute.DEFAULT_BOOST
    }

    override fun copyTo(target: AttributeImpl) {
        val t = target as BoostAttribute
        t.boost = boost
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(BoostAttribute::class, "boost", boost)
    }

    override fun newInstance(): AttributeImpl = BoostAttributeImpl()
}
