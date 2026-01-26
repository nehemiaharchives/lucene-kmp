package org.gnit.lucenekmp.store

import okio.IOException

/**
 * IndexOutput implementation that delegates calls to another directory. This class can be used to
 * add limitations on top of an existing [IndexOutput] implementation such as [ ] or to add additional sanity checks for tests. However, if you plan to
 * write your own [IndexOutput] implementation, you should consider extending directly [ ] or [DataOutput] rather than try to reuse functionality of existing [ ]s by extending this class.
 *
 * @lucene.internal
 */
open class FilterIndexOutput protected constructor(
    resourceDescription: String,
    name: String?,
    out: IndexOutput?
) : IndexOutput(resourceDescription, name) {
    protected val out: IndexOutput?

    /**
     * Creates a FilterIndexOutput with a resource description, name, and wrapped delegate IndexOutput
     */
    init {
        this.out = out
    }

    val delegate: IndexOutput?
        /** Gets the delegate that was passed in on creation  */
        get() = out

    override fun close() {
        out!!.close()
    }

    override val filePointer: Long
        get() = out!!.filePointer

    override fun getChecksum() = out!!.getChecksum()

    @Throws(IOException::class)
    override fun writeByte(b: Byte) {
        out!!.writeByte(b)
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
        out!!.writeBytes(b, offset, length)
    }

    companion object {
        /**
         * Unwraps all FilterIndexOutputs until the first non-FilterIndexOutput IndexOutput instance and
         * returns it
         */
        fun unwrap(out: IndexOutput): IndexOutput? {
            var out: IndexOutput? = out
            while (out is FilterIndexOutput) {
                out = out.out
            }
            return out
        }
    }
}
