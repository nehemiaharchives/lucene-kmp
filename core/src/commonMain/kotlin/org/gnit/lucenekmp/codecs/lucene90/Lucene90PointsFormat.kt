package org.gnit.lucenekmp.codecs.lucene90


import kotlinx.io.IOException
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

/**
 * Lucene 9.0 point format, which encodes dimensional values in a block KD-tree structure for fast
 * 1D range and N dimensional shape intersection filtering. See [this paper](https://www.cs.duke.edu/~pankaj/publications/papers/bkd-sstd.pdf) for
 * details.
 *
 *
 * Data is stored across three files
 *
 *
 *  * A .kdm file that records metadata about the fields, such as numbers of dimensions or
 * numbers of bytes per dimension.
 *  * A .kdi file that stores inner nodes of the tree.
 *  * A .kdd file that stores leaf nodes, where most of the data lives.
 *
 *
 * See [this
 * wiki](https://cwiki.apache.org/confluence/pages/viewpage.actionpageId=173081898) for detailed data structures of the three files.
 *
 * @lucene.experimental
 */
class Lucene90PointsFormat
/** Sole constructor  */
    : PointsFormat() {
    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): PointsWriter {
        return Lucene90PointsWriter(state)
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): PointsReader {
        return Lucene90PointsReader(state)
    }

    companion object {
        const val DATA_CODEC_NAME: String = "Lucene90PointsFormatData"
        const val INDEX_CODEC_NAME: String = "Lucene90PointsFormatIndex"
        const val META_CODEC_NAME: String = "Lucene90PointsFormatMeta"

        /** Filename extension for the leaf blocks  */
        const val DATA_EXTENSION: String = "kdd"

        /** Filename extension for the index per field  */
        const val INDEX_EXTENSION: String = "kdi"

        /** Filename extension for the meta per field  */
        const val META_EXTENSION: String = "kdm"

        const val VERSION_START: Int = 0
        const val VERSION_CURRENT: Int = VERSION_START
    }
}
