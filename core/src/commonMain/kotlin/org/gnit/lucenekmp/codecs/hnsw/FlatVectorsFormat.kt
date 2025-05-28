package org.gnit.lucenekmp.codecs.hnsw


import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import okio.IOException

/**
 * Encodes/decodes per-document vectors and provides a scoring interface for the flat stored vectors
 *
 * @lucene.experimental
 */
abstract class FlatVectorsFormat
/** Sole constructor  */
protected constructor(name: String) : KnnVectorsFormat(name) {
    /** Returns a [FlatVectorsWriter] to write the vectors to the index.  */
    @Throws(IOException::class)
    abstract override fun fieldsWriter(state: SegmentWriteState): FlatVectorsWriter

    /** Returns a [KnnVectorsReader] to read the vectors from the index.  */
    @Throws(IOException::class)
    abstract override fun fieldsReader(state: SegmentReadState): FlatVectorsReader

    override fun getMaxDimensions(fieldName: String): Int {
        return 1024
    }
}
