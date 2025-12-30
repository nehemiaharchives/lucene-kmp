package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.BufferedInputStream
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.InputStreamDataInput
import org.gnit.lucenekmp.util.IOSupplier

/** n-gram connection cost data */
abstract class ConnectionCosts(
    connectionCostResource: IOSupplier<InputStream>,
    connectionCostsCodecHeader: String,
    dictCodecVersion: Int
) {
    companion object {
        const val FILENAME_SUFFIX: String = ".dat"
    }

    private val buffer: ByteBuffer
    private val forwardSize: Int

    init {
        BufferedInputStream(connectionCostResource.get()).use { input ->
            val `in`: DataInput = InputStreamDataInput(input)
            CodecUtil.checkHeader(`in`, connectionCostsCodecHeader, dictCodecVersion, dictCodecVersion)
            forwardSize = `in`.readVInt()
            val backwardSize = `in`.readVInt()
            val size = forwardSize * backwardSize

            val tmpBuffer = ByteBuffer.allocate(size * 2)
            var accum = 0
            for (j in 0 until backwardSize) {
                for (i in 0 until forwardSize) {
                    accum += `in`.readZInt()
                    tmpBuffer.putShort(accum.toShort())
                }
            }
            buffer = tmpBuffer.asReadOnlyBuffer()
        }
    }

    fun get(forwardId: Int, backwardId: Int): Int {
        val offset = (backwardId * forwardSize + forwardId) * 2
        return buffer.getShort(offset).toInt()
    }
}
