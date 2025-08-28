package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.BytesRef


/** Default implementation of [PayloadAttribute].  */
class PayloadAttributeImpl : AttributeImpl,
    PayloadAttribute {
    override var payload: BytesRef? = null
        set(value) {
            field = value
        }

    /** Initialize this attribute with no payload.  */
    constructor()

    /** Initialize this attribute with the given payload.  */
    constructor(payload: BytesRef?) {
        this.payload = payload
    }

    override fun clear() {
        payload = null
    }

    override fun clone(): PayloadAttributeImpl {
        val clone = super.clone() as PayloadAttributeImpl
        if (payload != null) {
            clone.payload = BytesRef.deepCopyOf(payload!!)
        }
        return clone
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other is PayloadAttributeImpl) {
            if (other.payload == null || payload == null) {
                return other.payload == null && payload == null
            }

            return other.payload == payload
        }

        return false
    }

    override fun hashCode(): Int {
        return if (payload == null) 0 else payload.hashCode()
    }

    override fun copyTo(target: AttributeImpl) {
        val t: PayloadAttribute =
            target as PayloadAttribute
        t.payload = (if (payload == null) null else BytesRef.deepCopyOf(payload!!))
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(PayloadAttribute::class, "payload", payload!!)
    }

    override fun newInstance(): AttributeImpl {
        throw UnsupportedOperationException(
            "AutomatonAttributeImpl cannot be instantiated directly, use init() instead"
        )
    }
}
