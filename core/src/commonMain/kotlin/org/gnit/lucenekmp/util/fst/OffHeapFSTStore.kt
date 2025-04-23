package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.fst.FST.BytesReader
import org.gnit.lucenekmp.util.fst.FST.FSTMetadata
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlinx.io.IOException

/**
 * Provides off heap storage of finite state machine (FST), using underlying index input instead of
 * byte store on heap
 *
 * @lucene.experimental
 */
class OffHeapFSTStore(private val `in`: IndexInput, private val offset: Long, metadata: FSTMetadata<*>) : FSTReader {
    private val numBytes: Long = metadata.numBytes

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED
    }

    fun size(): Long {
        return numBytes
    }

    override fun getReverseBytesReader(): BytesReader {
        try {
            return ReverseRandomAccessReader(`in`.randomAccessSlice(offset, numBytes))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    override fun writeTo(out: DataOutput) {
        throw UnsupportedOperationException(
            "writeToOutput operation is not supported for OffHeapFSTStore"
        )
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(OffHeapFSTStore::class)
    }
}
