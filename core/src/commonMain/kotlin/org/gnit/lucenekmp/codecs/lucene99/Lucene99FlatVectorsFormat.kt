package org.gnit.lucenekmp.codecs.lucene99


import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsFormat
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsWriter
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import okio.IOException

/**
 * Lucene 9.9 flat vector format, which encodes numeric vector values
 *
 * <h2>.vec (vector data) file</h2>
 *
 *
 * For each field:
 *
 *
 *  * Vector data ordered by field, document ordinal, and vector dimension. When the
 * vectorEncoding is BYTE, each sample is stored as a single byte. When it is FLOAT32, each
 * sample is stored as an IEEE float in little-endian byte order.
 *  * DocIds encoded by [IndexedDISI.writeBitSet],
 * note that only in sparse case
 *  * OrdToDoc was encoded by [org.apache.lucene.util.packed.DirectMonotonicWriter], note
 * that only in sparse case
 *
 *
 * <h2>.vemf (vector metadata) file</h2>
 *
 *
 * For each field:
 *
 *
 *  * **[int32]** field number
 *  * **[int32]** vector similarity function ordinal
 *  * **[vlong]** offset to this field's vectors in the .vec file
 *  * **[vlong]** length of this field's vectors, in bytes
 *  * **[vint]** dimension of this field's vectors
 *  * **[int]** the number of documents having values for this field
 *  * **[int8]** if equals to -2, empty - no vector values. If equals to -1, dense – all
 * documents have values for a field. If equals to 0, sparse – some documents missing values.
 *  * DocIds were encoded by [IndexedDISI.writeBitSet]
 *  * OrdToDoc was encoded by [org.apache.lucene.util.packed.DirectMonotonicWriter], note
 * that only in sparse case
 *
 *
 * @lucene.experimental
 */
class Lucene99FlatVectorsFormat(private val vectorsScorer: FlatVectorsScorer) : FlatVectorsFormat(NAME) {
    private val logger = KotlinLogging.logger {}

    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): FlatVectorsWriter {
        return Lucene99FlatVectorsWriter(state, vectorsScorer)
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): FlatVectorsReader {
        //logger.debug { "Lucene99FlatVectorsFormat: fieldsReader start seg=${state.segmentInfo.name} suffix=${state.segmentSuffix}" }
        return Lucene99FlatVectorsReader(state, vectorsScorer)
    }

    override fun toString(): String {
        return "Lucene99FlatVectorsFormat(vectorsScorer=$vectorsScorer)"
    }

    companion object {
        const val NAME: String = "Lucene99FlatVectorsFormat"
        const val META_CODEC_NAME: String = "Lucene99FlatVectorsFormatMeta"
        const val VECTOR_DATA_CODEC_NAME: String = "Lucene99FlatVectorsFormatData"
        const val META_EXTENSION: String = "vemf"
        const val VECTOR_DATA_EXTENSION: String = "vec"

        const val VERSION_START: Int = 0
        const val VERSION_CURRENT: Int = VERSION_START

        const val DIRECT_MONOTONIC_BLOCK_SHIFT: Int = 16
    }
}
