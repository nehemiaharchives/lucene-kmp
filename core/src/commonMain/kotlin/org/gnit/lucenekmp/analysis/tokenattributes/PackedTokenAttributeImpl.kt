package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute.Companion.DEFAULT_TYPE
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector


/**
 * Default implementation of the common attributes used by Lucene:
 *
 *
 *  * [CharTermAttribute]
 *  * [TypeAttribute]
 *  * [PositionIncrementAttribute]
 *  * [PositionLengthAttribute]
 *  * [OffsetAttribute]
 *  * [TermFrequencyAttribute]
 *
 */
class PackedTokenAttributeImpl
/** Constructs the attribute implementation.  */
    : CharTermAttributeImpl(), TypeAttribute, PositionIncrementAttribute, PositionLengthAttribute, OffsetAttribute,
    TermFrequencyAttribute {

    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var type: String = DEFAULT_TYPE
    private var positionIncrement: Int = 1
    override var positionLength: Int = 1
        set(positionLength) {
            require(positionLength >= 1) { "Position length must be 1 or greater: got $positionLength" }
            field = positionLength
        }

    override var termFrequency: Int = 1
        set(termFrequency) {
            require(termFrequency >= 1) { "Term frequency must be 1 or greater; got $termFrequency" }
            field = termFrequency

        }

    /**
     * {@inheritDoc}
     *
     * @see PositionIncrementAttribute
     */
    override fun setPositionIncrement(positionIncrement: Int) {
        require(positionIncrement >= 0) { "Increment must be zero or greater: $positionIncrement" }
        /*~~kzcczk~~*/this.positionIncrement = positionIncrement
    }

    /**
     * {@inheritDoc}
     *
     * @see PositionIncrementAttribute
     */
    override fun getPositionIncrement(): Int {
        return positionIncrement
    }

    /**
     * {@inheritDoc}
     *
     * @see OffsetAttribute
     */
    override fun startOffset(): Int {
        return startOffset
    }

    /**
     * {@inheritDoc}
     *
     * @see OffsetAttribute
     */
    override fun endOffset(): Int {
        return endOffset
    }

    /**
     * {@inheritDoc}
     *
     * @see OffsetAttribute
     */
    override fun setOffset(startOffset: Int, endOffset: Int) {
        require(!(startOffset < 0 || endOffset < startOffset)) {
            ("startOffset must be non-negative, and endOffset must be >= startOffset; got "
                    + "startOffset="
                    + startOffset
                    + ",endOffset="
                    + endOffset)
        }
        this.startOffset = startOffset
        this.endOffset = endOffset
    }

    /**
     * {@inheritDoc}
     *
     * @see TypeAttribute
     */
    override fun type(): String {
        return type
    }

    /**
     * {@inheritDoc}
     *
     * @see TypeAttribute
     */
    override fun setType(type: String) {
        this.type = type
    }

    /** Resets the attributes  */
    override fun clear() {
        super.clear()
        positionLength = 1
        positionIncrement = positionLength
        termFrequency = 1
        endOffset = 0
        startOffset = endOffset
        type = DEFAULT_TYPE
    }

    /** Resets the attributes at end  */
    override fun end() {
        super.end()
        // super.end already calls this.clear, so we only set values that are different from clear:
        positionIncrement = 0
    }

    override fun clone(): PackedTokenAttributeImpl {
        return super.clone() as PackedTokenAttributeImpl
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === /*~~wrdocy~~*/this) return true

        return if (obj is PackedTokenAttributeImpl) {
            (startOffset == obj.startOffset && endOffset == obj.endOffset && positionIncrement == obj.positionIncrement && positionLength == obj.positionLength && type == obj.type
                    && termFrequency == obj.termFrequency && super.equals(obj))
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        var code: Int = super.hashCode()
        code = code * 31 + startOffset
        code = code * 31 + endOffset
        code = code * 31 + positionIncrement
        code = code * 31 + positionLength
        if (type != null) code = code * 31 + type.hashCode()
        code = code * 31 + termFrequency
        return code
    }

    override fun copyTo(target: AttributeImpl) {
        if (target is PackedTokenAttributeImpl) {
            target.copyBuffer(buffer(), 0, length)
            target.positionIncrement = positionIncrement
            target.positionLength = positionLength
            target.startOffset = startOffset
            target.endOffset = endOffset
            target.type = type
            target.termFrequency = termFrequency
        } else {
            super.copyTo(target)
            (target as OffsetAttribute).setOffset(startOffset, endOffset)
            (target as PositionIncrementAttribute).setPositionIncrement(positionIncrement)
            (target as PositionLengthAttribute).positionLength = positionLength
            (target as TypeAttribute).setType(type)
            (target as TermFrequencyAttribute).termFrequency = termFrequency
        }
    }

    override fun reflectWith(reflector: AttributeReflector) {
        super.reflectWith(reflector)
        reflector.reflect(OffsetAttribute::class, "startOffset", startOffset)
        reflector.reflect(OffsetAttribute::class, "endOffset", endOffset)
        reflector.reflect(PositionIncrementAttribute::class, "positionIncrement", positionIncrement)
        reflector.reflect(PositionLengthAttribute::class, "positionLength", positionLength)
        reflector.reflect(TypeAttribute::class, "type", type)
        reflector.reflect(TermFrequencyAttribute::class, "termFrequency", termFrequency)
    }
}
