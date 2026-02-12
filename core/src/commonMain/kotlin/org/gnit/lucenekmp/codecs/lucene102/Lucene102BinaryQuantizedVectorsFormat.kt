package org.gnit.lucenekmp.codecs.lucene102

import okio.IOException
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorScorerUtil
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsFormat
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsWriter
import org.gnit.lucenekmp.codecs.lucene99.Lucene99FlatVectorsFormat
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState


/**
 * The binary quantization format used here is a per-vector optimized scalar quantization. These
 * ideas are evolutions of LVQ proposed in [Similarity
 * search in the blink of an eye with compressed indices](https://arxiv.org/abs/2304.04759) by Cecilia Aguerrebere et al., the
 * previous work on globally optimized scalar quantization in Apache Lucene, and [Accelerating Large-Scale Inference with Anisotropic
 * Vector Quantization ](https://arxiv.org/abs/1908.10396) by Ruiqi Guo et. al. Also see [ ]. Some of key features are:
 *
 *
 *  * Estimating the distance between two vectors using their centroid centered distance. This
 * requires some additional corrective factors, but allows for centroid centering to occur.
 *  * Optimized scalar quantization to single bit level of centroid centered vectors.
 *  * Asymmetric quantization of vectors, where query vectors are quantized to half-byte (4 bits)
 * precision (normalized to the centroid) and then compared directly against the single bit
 * quantized vectors in the index.
 *  * Transforming the half-byte quantized query vectors in such a way that the comparison with
 * single bit vectors can be done with bit arithmetic.
 *
 *
 * A previous work related to improvements over regular LVQ is [Practical and Asymptotically Optimal Quantization of
 * High-Dimensional Vectors in Euclidean Space for Approximate Nearest Neighbor Search ](https://arxiv.org/abs/2409.09913) by
 * Jianyang Gao, et. al.
 *
 *
 * The format is stored within two files:
 *
 * <h2>.veb (vector data) file</h2>
 *
 *
 * Stores the binary quantized vectors in a flat format. Additionally, it stores each vector's
 * corrective factors. At the end of the file, additional information is stored for vector ordinal
 * to centroid ordinal mapping and sparse vector information.
 *
 *
 *  * For each vector:
 *
 *  * **[byte]** the binary quantized values, each byte holds 8 bits.
 *  * **[float]** the optimized quantiles and an additional similarity dependent
 * corrective factor.
 *  * **short** the sum of the quantized components
 *
 *  * After the vectors, sparse vector information keeping track of monotonic blocks.
 *
 *
 * <h2>.vemb (vector metadata) file</h2>
 *
 *
 * Stores the metadata for the vectors. This includes the number of vectors, the number of
 * dimensions, and file offset information.
 *
 *
 *  * **int** the field number
 *  * **int** the vector encoding ordinal
 *  * **int** the vector similarity ordinal
 *  * **vint** the vector dimensions
 *  * **vlong** the offset to the vector data in the .veb file
 *  * **vlong** the length of the vector data in the .veb file
 *  * **vint** the number of vectors
 *  * **[float]** the centroid
 *  * **float** the centroid square magnitude
 *  * The sparse vector information, if required, mapping vector ordinal to doc ID
 *
 */
class Lucene102BinaryQuantizedVectorsFormat
/** Creates a new instance with the default number of vectors per cluster.  */
    : FlatVectorsFormat(NAME) {
    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): FlatVectorsWriter {
        return Lucene102BinaryQuantizedVectorsWriter(
            scorer, rawVectorFormat.fieldsWriter(state), state
        )
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): FlatVectorsReader {
        return Lucene102BinaryQuantizedVectorsReader(
            state, rawVectorFormat.fieldsReader(state), scorer
        )
    }

    override fun getMaxDimensions(fieldName: String): Int {
        return 1024
    }

    override fun toString(): String {
        return "Lucene102BinaryQuantizedVectorsFormat(name=$NAME, flatVectorScorer=$scorer, rawVectorFormat=$rawVectorFormat)"
    }

    companion object {
        const val QUERY_BITS: Byte = 4
        const val INDEX_BITS: Byte = 1

        const val BINARIZED_VECTOR_COMPONENT: String = "BVEC"
        const val NAME: String = "Lucene102BinaryQuantizedVectorsFormat"

        const val VERSION_START: Int = 0
        const val VERSION_CURRENT: Int = VERSION_START
        const val META_CODEC_NAME: String = "Lucene102BinaryQuantizedVectorsFormatMeta"
        const val VECTOR_DATA_CODEC_NAME: String = "Lucene102BinaryQuantizedVectorsFormatData"
        const val META_EXTENSION: String = "vemb"
        const val VECTOR_DATA_EXTENSION: String = "veb"
        const val DIRECT_MONOTONIC_BLOCK_SHIFT: Int = 16

        private val rawVectorFormat: FlatVectorsFormat = Lucene99FlatVectorsFormat(FlatVectorScorerUtil.getLucene99FlatVectorsScorer())

        private val scorer: Lucene102BinaryFlatVectorsScorer = Lucene102BinaryFlatVectorsScorer(FlatVectorScorerUtil.getLucene99FlatVectorsScorer())
    }
}
