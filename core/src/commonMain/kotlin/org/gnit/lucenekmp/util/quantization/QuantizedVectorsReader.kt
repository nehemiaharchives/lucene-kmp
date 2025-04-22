package org.gnit.lucenekmp.util.quantization

import org.gnit.lucenekmp.util.Accountable
import kotlinx.io.IOException

/**
 * Quantized vector reader
 *
 * @lucene.experimental
 */
interface QuantizedVectorsReader : AutoCloseable, Accountable {
    @Throws(IOException::class)
    fun getQuantizedVectorValues(fieldName: String): QuantizedByteVectorValues?

    fun getQuantizationState(fieldName: String): ScalarQuantizer?
}
