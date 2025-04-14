package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Bits

/**
 * Format for live/deleted documents
 *
 * @lucene.experimental
 */
abstract class LiveDocsFormat
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() {
    /** Read live docs bits.  */
    @Throws(IOException::class)
    abstract fun readLiveDocs(dir: Directory, info: SegmentCommitInfo, context: IOContext): Bits

    /**
     * Persist live docs bits. Use [SegmentCommitInfo.getNextDelGen] to determine the generation
     * of the deletes file you should write to.
     */
    @Throws(IOException::class)
    abstract fun writeLiveDocs(
        bits: Bits, dir: Directory, info: SegmentCommitInfo, newDelCount: Int, context: IOContext
    )

    /** Records all files in use by this [SegmentCommitInfo] into the files argument.  */
    @Throws(IOException::class)
    abstract fun files(info: SegmentCommitInfo, files: MutableCollection<String>)
}
