package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector

/** Default implementation of [PositionLengthAttribute]. */
class PositionLengthAttributeImpl : AttributeImpl(), PositionLengthAttribute {
    private var positionLengthValue = 1

    /** Initializes this attribute with position length of 1. */
    override var positionLength: Int
        get() = positionLengthValue
        set(positionLength) {
            require(positionLength >= 1) {
                "Position length must be 1 or greater; got $positionLength"
            }
            this.positionLengthValue = positionLength
        }

    override fun clear() {
        this.positionLengthValue = 1
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other is PositionLengthAttributeImpl) {
            return positionLengthValue == other.positionLengthValue
        }

        return false
    }

    override fun hashCode(): Int {
        return positionLengthValue
    }

    override fun copyTo(target: AttributeImpl) {
        val t: PositionLengthAttribute = target as PositionLengthAttribute
        t.positionLength = positionLengthValue
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(PositionLengthAttribute::class, "positionLength", positionLengthValue)
    }

    override fun newInstance(): AttributeImpl {
        return PositionLengthAttributeImpl()
    }
}
