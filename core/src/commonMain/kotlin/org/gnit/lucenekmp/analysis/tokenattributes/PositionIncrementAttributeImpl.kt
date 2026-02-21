package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector

/** Default implementation of [PositionIncrementAttribute]. */
class PositionIncrementAttributeImpl : AttributeImpl(), PositionIncrementAttribute {
    private var positionIncrement: Int = 1

    override fun setPositionIncrement(positionIncrement: Int) {
        require(positionIncrement >= 0) {
            "Position increment must be zero or greater; got $positionIncrement"
        }
        this.positionIncrement = positionIncrement
    }

    override fun getPositionIncrement(): Int {
        return positionIncrement
    }

    override fun clear() {
        this.positionIncrement = 1
    }

    override fun end() {
        this.positionIncrement = 0
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other is PositionIncrementAttributeImpl) {
            return positionIncrement == other.positionIncrement
        }

        return false
    }

    override fun hashCode(): Int {
        return positionIncrement
    }

    override fun copyTo(target: AttributeImpl) {
        val t: PositionIncrementAttribute = target as PositionIncrementAttribute
        t.setPositionIncrement(positionIncrement)
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(PositionIncrementAttribute::class, "positionIncrement", positionIncrement)
    }

    override fun newInstance(): AttributeImpl {
        return PositionIncrementAttributeImpl()
    }
}
