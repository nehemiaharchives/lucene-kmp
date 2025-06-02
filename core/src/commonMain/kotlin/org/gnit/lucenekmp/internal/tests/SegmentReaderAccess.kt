package org.gnit.lucenekmp.internal.tests

import org.gnit.lucenekmp.index.SegmentReader

/**
 * Access to [org.apache.lucene.index.SegmentReader] internals exposed to the test framework.
 *
 * @lucene.internal
 */
interface SegmentReaderAccess {
    /**
     * @return Returns the package-private `SegmentCoreReaders` associated with the segment
     * reader. We don't use the actual type anywhere, so just return it as an object, without
     * type.
     */
    fun getCore(segmentReader: SegmentReader): Any
}
