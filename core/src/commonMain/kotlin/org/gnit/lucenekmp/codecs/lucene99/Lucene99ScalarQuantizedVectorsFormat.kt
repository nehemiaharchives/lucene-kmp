package org.gnit.lucenekmp.codecs.lucene99

import okio.IOException
import org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorScorerUtil
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsFormat
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsWriter
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import kotlin.math.max

/**
 * Format supporting vector quantization, storage, and retrieval.
 *
 * @lucene.experimental
 */
class Lucene99ScalarQuantizedVectorsFormat(
    val confidenceInterval: Float? = null,
    val bits: Int = 7,
    val compress: Boolean = false
) : FlatVectorsFormat(NAME) {

    private val flatVectorScorer = Lucene99ScalarQuantizedVectorScorer(DefaultFlatVectorScorer())

    init {
        if (confidenceInterval != null &&
            confidenceInterval != DYNAMIC_CONFIDENCE_INTERVAL &&
            (confidenceInterval < MINIMUM_CONFIDENCE_INTERVAL || confidenceInterval > MAXIMUM_CONFIDENCE_INTERVAL)
        ) {
            throw IllegalArgumentException(
                "confidenceInterval must be between $MINIMUM_CONFIDENCE_INTERVAL and " +
                    "$MAXIMUM_CONFIDENCE_INTERVAL or 0; confidenceInterval=$confidenceInterval"
            )
        }
        if (bits < 1 || bits > 8 || (ALLOWED_BITS and (1 shl bits)) == 0) {
            throw IllegalArgumentException("bits must be one of: 4, 7; bits=$bits")
        }
        if (bits > 4 && compress) {
            throw IllegalArgumentException("compress=true only applies when bits=4")
        }
    }

    override fun toString(): String {
        return NAME +
            "(name=$NAME, confidenceInterval=$confidenceInterval, bits=$bits, compress=$compress, " +
            "flatVectorScorer=$flatVectorScorer, rawVectorFormat=$rawVectorFormat)"
    }

    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): FlatVectorsWriter {
        return Lucene99ScalarQuantizedVectorsWriter(
            state,
            confidenceInterval,
            bits.toByte(),
            compress,
            rawVectorFormat.fieldsWriter(state),
            flatVectorScorer
        )
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): FlatVectorsReader {
        return Lucene99ScalarQuantizedVectorsReader(
            state,
            rawVectorFormat.fieldsReader(state),
            flatVectorScorer
        )
    }

    companion object {
        private const val ALLOWED_BITS: Int = (1 shl 7) or (1 shl 4)
        const val QUANTIZED_VECTOR_COMPONENT: String = "QVEC"

        const val NAME: String = "Lucene99ScalarQuantizedVectorsFormat"

        const val VERSION_START: Int = 0
        const val VERSION_ADD_BITS: Int = 1
        const val VERSION_CURRENT: Int = VERSION_ADD_BITS
        const val META_CODEC_NAME: String = "Lucene99ScalarQuantizedVectorsFormatMeta"
        const val VECTOR_DATA_CODEC_NAME: String = "Lucene99ScalarQuantizedVectorsFormatData"
        const val META_EXTENSION: String = "vemq"
        const val VECTOR_DATA_EXTENSION: String = "veq"

        private val rawVectorFormat: FlatVectorsFormat =
            Lucene99FlatVectorsFormat(FlatVectorScorerUtil.getLucene99FlatVectorsScorer())

        private const val MINIMUM_CONFIDENCE_INTERVAL: Float = 0.9f
        private const val MAXIMUM_CONFIDENCE_INTERVAL: Float = 1f
        const val DYNAMIC_CONFIDENCE_INTERVAL: Float = 0f

        fun calculateDefaultConfidenceInterval(vectorDimension: Int): Float {
            return max(MINIMUM_CONFIDENCE_INTERVAL, 1f - (1f / (vectorDimension + 1)))
        }
    }
}
