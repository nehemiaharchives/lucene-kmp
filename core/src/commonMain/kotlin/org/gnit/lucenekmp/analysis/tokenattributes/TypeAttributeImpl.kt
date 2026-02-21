package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector

/** Default implementation of [TypeAttribute]. */
class TypeAttributeImpl() : AttributeImpl(), TypeAttribute {
    private var type: String = TypeAttribute.DEFAULT_TYPE

    /** Initialize this attribute with `type` */
    constructor(type: String) : this() {
        this.type = type
    }

    override fun type(): String {
        return type
    }

    override fun setType(type: String) {
        this.type = type
    }

    override fun clear() {
        type = TypeAttribute.DEFAULT_TYPE
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other is TypeAttributeImpl) {
            return this.type == other.type
        }

        return false
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    override fun copyTo(target: AttributeImpl) {
        val t: TypeAttribute = target as TypeAttribute
        t.setType(type)
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(TypeAttribute::class, "type", type)
    }

    override fun newInstance(): AttributeImpl {
        return TypeAttributeImpl()
    }
}
