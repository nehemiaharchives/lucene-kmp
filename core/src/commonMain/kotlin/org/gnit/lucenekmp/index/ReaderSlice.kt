package org.gnit.lucenekmp.index

import kotlin.jvm.JvmRecord

/**
 * Subreader slice from a parent composite reader.
 *
 * @param start Document ID this slice starts from.
 * @param length Number of documents in this slice.
 * @param readerIndex Sub-reader index for this slice.
 * @lucene.internal
 */
@JvmRecord
data class ReaderSlice(val start: Int, val length: Int, val readerIndex: Int) {
    companion object {
        /** Zero-length `ReaderSlice` array.  */
        val EMPTY_ARRAY: Array<ReaderSlice> = emptyArray()
    }
}
