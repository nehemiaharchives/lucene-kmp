package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.BytesRef

/**
 * Implementation class for [BytesTermAttribute].
 *
 * @lucene.internal
 */
open class BytesTermAttributeImpl : AttributeImpl(), BytesTermAttribute, TermToBytesRefAttribute /* required */ {
    private var bytesNullable: BytesRef? = null

    /** Initialize this attribute with no bytes. */

    override val bytesRef: BytesRef
        get() = bytesNullable ?: BytesRef()

    override fun setBytesRef(bytes: BytesRef?) {
        this.bytesNullable = bytes
    }

    override fun clear() {
        this.bytesNullable = null
    }

    override fun copyTo(target: AttributeImpl) {
        val other = target as BytesTermAttributeImpl
        other.bytesNullable = if (bytesNullable == null) null else BytesRef.deepCopyOf(bytesNullable!!)
    }

    override fun clone(): AttributeImpl {
        val c = super.clone() as BytesTermAttributeImpl
        copyTo(c)
        return c
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(TermToBytesRefAttribute::class, "bytes", bytesNullable)
    }

    override fun newInstance(): AttributeImpl {
        return BytesTermAttributeImpl()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BytesTermAttributeImpl) return false
        return Objects.equals(bytesNullable, other.bytesNullable)
    }

    override fun hashCode(): Int {
        return Objects.hash(bytesNullable)
    }
}
