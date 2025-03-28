package org.gnit.lucenekmp.util


/**
 * An object whose RAM usage can be computed.
 *
 * @lucene.internal
 */
interface Accountable {


    /** Return the memory usage of this object in bytes. Negative values are illegal.  */
    fun ramBytesUsed(): Long

    /**
     * Returns nested resources of this class. The result should be a point-in-time snapshot (to avoid
     * race conditions).
     *
     * @see Accountables
     */
    fun getChildResources(): MutableCollection<Accountable> {
        return mutableListOf()
    }

    companion object {
        /** An accountable that always returns 0 */
        val NULL_ACCOUNTABLE: Accountable = object : Accountable {
            override fun ramBytesUsed(): Long = 0
        }
    }

}
