package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector

/** Simple implementation of [OffsetAttribute] */
class OffsetAttributeImpl : AttributeImpl(), OffsetAttribute {
    private var start = 0
    private var end = 0

    override fun startOffset(): Int = start

    override fun endOffset(): Int = end

    override fun setOffset(startOffset: Int, endOffset: Int) {
        require(startOffset <= endOffset) { "startOffset must be <= endOffset" }
        require(startOffset >= 0 && endOffset >= 0) { "offsets must be non-negative" }
        start = startOffset
        end = endOffset
    }

    override fun clear() {
        start = 0
        end = 0
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other is OffsetAttributeImpl) {
            return other.start == start && other.end == end
        }
        return false
    }

    override fun hashCode(): Int {
        var code = start
        code = code * 31 + end
        return code
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(OffsetAttribute::class, "startOffset", start)
        reflector.reflect(OffsetAttribute::class, "endOffset", end)
    }

    override fun copyTo(target: AttributeImpl) {
        val t = target as OffsetAttribute
        t.setOffset(start, end)
    }

    override fun newInstance(): AttributeImpl = OffsetAttributeImpl()
}
