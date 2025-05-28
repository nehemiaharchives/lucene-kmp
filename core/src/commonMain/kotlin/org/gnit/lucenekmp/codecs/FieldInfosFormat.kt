package org.gnit.lucenekmp.codecs


import okio.IOException
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

/**
 * Encodes/decodes [FieldInfos]
 *
 * @lucene.experimental
 */
abstract class FieldInfosFormat
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() {
    /** Read the [FieldInfos] previously written with [.write].  */
    @Throws(IOException::class)
    abstract fun read(
        directory: Directory, segmentInfo: SegmentInfo, segmentSuffix: String, iocontext: IOContext
    ): FieldInfos

    /** Writes the provided [FieldInfos] to the directory.  */
    @Throws(IOException::class)
    abstract fun write(
        directory: Directory,
        segmentInfo: SegmentInfo,
        segmentSuffix: String,
        infos: FieldInfos,
        context: IOContext
    )
}
