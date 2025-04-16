package org.gnit.lucenekmp.util.bkd

import kotlinx.io.IOException

/**
 * One pass iterator through all points previously written with a [PointWriter], abstracting
 * away whether points are read from (offline) disk or simple arrays in heap.
 *
 * @lucene.internal
 */
interface PointReader : AutoCloseable {
    /** Returns false once iteration is done, else true.  */
    @Throws(IOException::class)
    fun next(): Boolean

    /** Sets the packed value in the provided ByteRef  */
    fun pointValue(): PointValue?
}
