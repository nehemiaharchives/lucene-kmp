package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

/**
 * Encodes/decodes compound files
 *
 * @lucene.experimental
 */
abstract class CompoundFormat
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */ // Explicitly declared so that we have non-empty javadoc
protected constructor() {
    // TODO: this is very minimal. If we need more methods,
    // we can add 'producer' classes.
    /** Returns a Directory view (read-only) for the compound files in this segment  */
    @Throws(IOException::class)
    abstract fun getCompoundReader(dir: Directory, si: SegmentInfo): CompoundDirectory

    /**
     * Packs the provided segment's files into a compound format. All files referenced by the provided
     * [SegmentInfo] must have [CodecUtil.writeIndexHeader] and [ ][CodecUtil.writeFooter].
     */
    @Throws(IOException::class)
    abstract fun write(dir: Directory, si: SegmentInfo, context: IOContext)
}
