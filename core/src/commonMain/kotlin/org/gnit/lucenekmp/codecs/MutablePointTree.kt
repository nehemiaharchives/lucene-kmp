package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.PointTree
import org.gnit.lucenekmp.util.BytesRef

/**
 * One leaf [PointTree] whose order of points can be changed. This class is useful for codecs
 * to optimize flush.
 *
 * @lucene.internal
 */
abstract class MutablePointTree  /** Sole constructor.  */
protected constructor() : PointTree {

    /** Set `packedValue` with a reference to the packed bytes of the i-th value.  */
    abstract fun getValue(i: Int, packedValue: BytesRef)

    /** Get the k-th byte of the i-th value.  */
    abstract fun getByteAt(i: Int, k: Int): Byte

    /** Return the doc ID of the i-th value.  */
    abstract fun getDocID(i: Int): Int

    /** Swap the i-th and j-th values.  */
    abstract fun swap(i: Int, j: Int)

    /** Save the i-th value into the j-th position in temporary storage.  */
    abstract fun save(i: Int, j: Int)

    /** Restore values between i-th and j-th(excluding) in temporary storage into original storage.  */
    abstract fun restore(i: Int, j: Int)

    override fun clone(): PointTree {
        throw UnsupportedOperationException()
    }

    override fun moveToChild(): Boolean {
        return false
    }

    override fun moveToSibling(): Boolean {
        return false
    }

    override fun moveToParent(): Boolean {
        return false
    }

    override val minPackedValue: ByteArray
        get() {
            throw UnsupportedOperationException()
        }

    override val maxPackedValue: ByteArray
        get() {
            throw UnsupportedOperationException()
        }

    override fun visitDocIDs(visitor: IntersectVisitor) {
        throw UnsupportedOperationException()
    }
}
