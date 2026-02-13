package org.gnit.lucenekmp.codecs.lucene102

import okio.IOException
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsFormat
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsFormat
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsReader
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsWriter
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.search.TaskExecutor

/**
 * A vectors format that uses HNSW graph to store and search for vectors. But vectors are binary
 * quantized using [Lucene102BinaryQuantizedVectorsFormat] before being stored in the graph.
 */
class Lucene102HnswBinaryQuantizedVectorsFormat(
    private val maxConn: Int = Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN,
    private val beamWidth: Int = Lucene99HnswVectorsFormat.DEFAULT_BEAM_WIDTH,
    private val numMergeWorkers: Int = Lucene99HnswVectorsFormat.DEFAULT_NUM_MERGE_WORKER,
    mergeExec: ExecutorService? = null
) : KnnVectorsFormat(NAME) {

    private val mergeExec: TaskExecutor?

    init {
        require(maxConn > 0 && maxConn <= Lucene99HnswVectorsFormat.MAXIMUM_MAX_CONN) {
            "maxConn must be positive and less than or equal to " +
                Lucene99HnswVectorsFormat.MAXIMUM_MAX_CONN +
                "; maxConn=" + maxConn
        }
        require(beamWidth > 0 && beamWidth <= Lucene99HnswVectorsFormat.MAXIMUM_BEAM_WIDTH) {
            "beamWidth must be positive and less than or equal to " +
                Lucene99HnswVectorsFormat.MAXIMUM_BEAM_WIDTH +
                "; beamWidth=" + beamWidth
        }
        require(!(numMergeWorkers == 1 && mergeExec != null)) {
            "No executor service is needed as we'll use single thread to merge"
        }
        this.mergeExec = if (mergeExec != null) TaskExecutor(mergeExec) else null
    }

    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): KnnVectorsWriter {
        return Lucene99HnswVectorsWriter(
            state,
            maxConn,
            beamWidth,
            flatVectorsFormat.fieldsWriter(state),
            numMergeWorkers,
            mergeExec
        )
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): KnnVectorsReader {
        return Lucene99HnswVectorsReader(state, flatVectorsFormat.fieldsReader(state))
    }

    override fun getMaxDimensions(fieldName: String): Int {
        return 1024
    }

    override fun toString(): String {
        return "Lucene102HnswBinaryQuantizedVectorsFormat(name=Lucene102HnswBinaryQuantizedVectorsFormat, " +
            "maxConn=$maxConn, beamWidth=$beamWidth, flatVectorFormat=$flatVectorsFormat)"
    }

    companion object {
        const val NAME: String = "Lucene102HnswBinaryQuantizedVectorsFormat"

        /** The format for storing, reading, merging vectors on disk. */
        private val flatVectorsFormat: FlatVectorsFormat = Lucene102BinaryQuantizedVectorsFormat()
    }
}
