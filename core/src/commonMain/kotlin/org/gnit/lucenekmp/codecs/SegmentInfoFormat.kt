package org.gnit.lucenekmp.codecs

import okio.IOException
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

/**
 * Expert: Controls the format of the [SegmentInfo] (segment metadata file).
 *
 * @see SegmentInfo
 *
 * @lucene.experimental
 */
abstract class SegmentInfoFormat
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() {
    /**
     * Read [SegmentInfo] data from a directory.
     *
     * @param directory directory to read from
     * @param segmentName name of the segment to read
     * @param segmentID expected identifier for the segment
     * @return infos instance to be populated with data
     * @throws IOException If an I/O error occurs
     */
    @Throws(IOException::class)
    abstract fun read(
        directory: Directory, segmentName: String, segmentID: ByteArray, context: IOContext
    ): SegmentInfo

    /**
     * Write [SegmentInfo] data. The codec must add its SegmentInfo filename(s) to `info`
     * before doing i/o.
     *
     * @throws IOException If an I/O error occurs
     */
    @Throws(IOException::class)
    abstract fun write(dir: Directory, info: SegmentInfo, ioContext: IOContext)
}
