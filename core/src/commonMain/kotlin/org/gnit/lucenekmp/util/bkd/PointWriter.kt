package org.gnit.lucenekmp.util.bkd

import okio.IOException


/**
 * Appends many points, and then at the end provides a [PointReader] to iterate those points.
 * This abstracts away whether we write to disk, or use simple arrays in heap.
 *
 * @lucene.internal
 */
interface PointWriter : AutoCloseable {
    /** Add a new point from the packed value and docId  */
    @Throws(IOException::class)
    fun append(packedValue: ByteArray, docID: Int)

    /** Add a new point from a [PointValue]  */
    @Throws(IOException::class)
    fun append(pointValue: PointValue)

    /** Returns a [PointReader] iterator to step through all previously added points  */
    @Throws(IOException::class)
    fun getReader(startPoint: Long, length: Long): PointReader

    /** Return the number of points in this writer  */
    fun count(): Long

    /** Removes any temp files behind this writer  */
    @Throws(IOException::class)
    fun destroy()
}
