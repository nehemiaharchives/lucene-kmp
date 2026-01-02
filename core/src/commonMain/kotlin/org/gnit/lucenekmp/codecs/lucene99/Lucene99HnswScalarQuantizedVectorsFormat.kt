package org.gnit.lucenekmp.codecs.lucene99

import okio.IOException
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsFormat
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.search.TaskExecutor
import org.gnit.lucenekmp.util.hnsw.HnswGraphBuilder
import org.gnit.lucenekmp.jdkport.ExecutorService
import kotlin.jvm.JvmOverloads

/**
 * Lucene 9.9 vector format using scalar-quantized vectors with HNSW.
 *
 * @lucene.experimental
 */
class Lucene99HnswScalarQuantizedVectorsFormat @JvmOverloads constructor(
    maxConn: Int = Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN,
    beamWidth: Int = Lucene99HnswVectorsFormat.DEFAULT_BEAM_WIDTH,
    numMergeWorkers: Int = Lucene99HnswVectorsFormat.DEFAULT_NUM_MERGE_WORKER,
    bits: Int = 7,
    compress: Boolean = false,
    confidenceInterval: Float? = null,
    mergeExec: ExecutorService? = null
) : KnnVectorsFormat(NAME) {

    private val maxConn: Int
    private val beamWidth: Int
    private val flatVectorsFormat: FlatVectorsFormat
    private val numMergeWorkers: Int
    private val mergeExec: TaskExecutor?

    init {
        require(!(maxConn <= 0 || maxConn > Lucene99HnswVectorsFormat.MAXIMUM_MAX_CONN)) {
            "maxConn must be positive and less than or equal to " +
                "${Lucene99HnswVectorsFormat.MAXIMUM_MAX_CONN}; maxConn=$maxConn"
        }
        require(!(beamWidth <= 0 || beamWidth > Lucene99HnswVectorsFormat.MAXIMUM_BEAM_WIDTH)) {
            "beamWidth must be positive and less than or equal to " +
                "${Lucene99HnswVectorsFormat.MAXIMUM_BEAM_WIDTH}; beamWidth=$beamWidth"
        }
        require(!(numMergeWorkers == 1 && mergeExec != null)) {
            "No executor service is needed as we'll use single thread to merge"
        }
        this.maxConn = maxConn
        this.beamWidth = beamWidth
        this.numMergeWorkers = numMergeWorkers
        this.mergeExec = mergeExec?.let { TaskExecutor(it) }
        this.flatVectorsFormat = Lucene99ScalarQuantizedVectorsFormat(confidenceInterval, bits, compress)
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
        return "Lucene99HnswScalarQuantizedVectorsFormat(" +
            "name=Lucene99HnswScalarQuantizedVectorsFormat, maxConn=$maxConn, " +
            "beamWidth=$beamWidth, flatVectorFormat=$flatVectorsFormat)"
    }

    companion object {
        const val NAME: String = "Lucene99HnswScalarQuantizedVectorsFormat"
    }
}
