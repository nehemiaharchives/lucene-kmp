package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

/** Controls the format of term vectors  */
abstract class TermVectorsFormat
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() {
    /** Returns a [TermVectorsReader] to read term vectors.  */
    @Throws(IOException::class)
    abstract fun vectorsReader(
        directory: Directory, segmentInfo: SegmentInfo, fieldInfos: FieldInfos, context: IOContext
    ): TermVectorsReader

    /** Returns a [TermVectorsWriter] to write term vectors.  */
    @Throws(IOException::class)
    abstract fun vectorsWriter(
        directory: Directory, segmentInfo: SegmentInfo, context: IOContext
    ): TermVectorsWriter
}
