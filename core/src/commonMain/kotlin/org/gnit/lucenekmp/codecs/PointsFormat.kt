package org.gnit.lucenekmp.codecs

import okio.IOException
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

/**
 * Encodes/decodes indexed points.
 *
 * @lucene.experimental
 */
abstract class PointsFormat
/** Creates a new point format.  */
protected constructor() {
    /** Writes a new segment  */
    @Throws(IOException::class)
    abstract fun fieldsWriter(state: SegmentWriteState): PointsWriter

    /**
     * Reads a segment. NOTE: by the time this call returns, it must hold open any files it will need
     * to use; else, those files may be deleted. Additionally, required files may be deleted during
     * the execution of this call before there is a chance to open them. Under these circumstances an
     * IOException should be thrown by the implementation. IOExceptions are expected and will
     * automatically cause a retry of the segment opening logic with the newly revised segments.
     */
    @Throws(IOException::class)
    abstract fun fieldsReader(state: SegmentReadState): PointsReader

    companion object {
        /** A `PointsFormat` that has nothing indexed  */
        val EMPTY: PointsFormat = object : PointsFormat() {
            override fun fieldsWriter(state: SegmentWriteState): PointsWriter {
                throw UnsupportedOperationException()
            }

            override fun fieldsReader(state: SegmentReadState): PointsReader {
                return object : PointsReader() {
                    override fun close() {}

                    override fun checkIntegrity() {}

                    override fun getValues(field: String): PointValues {
                        throw IllegalArgumentException(
                            "field=\"$field\" was not indexed with points"
                        )
                    }
                }
            }
        }
    }
}
