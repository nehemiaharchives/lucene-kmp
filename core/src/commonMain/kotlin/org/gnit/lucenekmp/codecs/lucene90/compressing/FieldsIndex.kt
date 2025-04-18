package org.gnit.lucenekmp.codecs.lucene90.compressing

import org.gnit.lucenekmp.jdkport.Cloneable
import kotlinx.io.IOException


internal abstract class FieldsIndex : Cloneable<FieldsIndex>, AutoCloseable {
    /** Get the ID of the block that contains the given docID.  */
    abstract fun getBlockID(docID: Int): Long

    /** Get the start pointer of the block with the given ID.  */
    abstract fun getBlockStartPointer(blockID: Long): Long

    /** Get the number of bytes of the block with the given ID.  */
    abstract fun getBlockLength(blockID: Long): Long

    /** Get the start pointer of the block that contains the given docID.  */
    fun getStartPointer(docID: Int): Long {
        return getBlockStartPointer(getBlockID(docID))
    }

    /** Check the integrity of the index.  */
    @Throws(IOException::class)
    abstract fun checkIntegrity()

    abstract override fun clone(): FieldsIndex
}
