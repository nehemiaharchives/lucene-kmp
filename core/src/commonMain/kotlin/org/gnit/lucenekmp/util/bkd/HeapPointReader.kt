package org.gnit.lucenekmp.util.bkd

/**
 * Utility class to read buffered points from in-heap arrays.
 *
 * @lucene.internal
 */
class HeapPointReader internal constructor(
    private val points: (Int) -> PointValue?,
    start: Int,
    private val end: Int
) : PointReader {
    private var curRead: Int = start - 1

    override fun next(): Boolean {
        curRead++
        return curRead < end
    }

    override fun pointValue(): PointValue? {
        return points(curRead)
    }

    override fun close() {}
}
