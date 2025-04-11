package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.PostingsFormat // javadocs
import org.gnit.lucenekmp.codecs.perfield.PerFieldPostingsFormat // javadocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

/**
 * Holder class for common parameters used during read.
 *
 * @lucene.experimental
 */
class SegmentReadState {
    /** [Directory] where this segment is read from.  */
    val directory: Directory

    /** [SegmentInfo] describing this segment.  */
    val segmentInfo: SegmentInfo

    /** [FieldInfos] describing all fields in this segment.  */
    val fieldInfos: FieldInfos

    /** [IOContext] to pass to [Directory.openInput].  */
    val context: IOContext

    /**
     * Unique suffix for any postings files read for this segment. [PerFieldPostingsFormat] sets
     * this for each of the postings formats it wraps. If you create a new [PostingsFormat] then
     * any files you write/read must be derived using this suffix (use [ ][IndexFileNames.segmentFileName]).
     */
    val segmentSuffix: String

    /** Create a `SegmentReadState`.  */
    constructor(dir: Directory, info: SegmentInfo, fieldInfos: FieldInfos, context: IOContext) : this(
        dir,
        info,
        fieldInfos,
        context,
        ""
    )

    /** Create a `SegmentReadState`.  */
    constructor(
        dir: Directory,
        info: SegmentInfo,
        fieldInfos: FieldInfos,
        context: IOContext,
        segmentSuffix: String
    ) {
        this.directory = dir
        this.segmentInfo = info
        this.fieldInfos = fieldInfos
        this.context = context
        this.segmentSuffix = segmentSuffix
    }

    /** Create a `SegmentReadState`.  */
    constructor(other: SegmentReadState, newSegmentSuffix: String) {
        this.directory = other.directory
        this.segmentInfo = other.segmentInfo
        this.fieldInfos = other.fieldInfos
        this.context = other.context
        this.segmentSuffix = newSegmentSuffix
    }
}
