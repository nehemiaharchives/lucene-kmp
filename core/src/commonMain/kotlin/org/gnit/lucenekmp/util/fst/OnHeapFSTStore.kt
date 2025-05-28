package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.fst.FST.BytesReader
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.fst.FSTCompiler.Companion.getOnHeapReaderWriter
import okio.IOException

/**
 * Provides storage of finite state machine (FST), using byte array or byte store allocated on heap.
 *
 * @lucene.experimental
 */
class OnHeapFSTStore(maxBlockBits: Int, `in`: DataInput, numBytes: Long) : FSTReader {
    /**
     * A [ReadWriteDataOutput], used during reading when the FST is very large (more than 1 GB).
     * If the FST is less than 1 GB then bytesArray is set instead.
     */
    private var dataOutput: ReadWriteDataOutput? = null

    /** Used at read time when the FST fits into a single byte[].  */
    private val bytesArray: ByteArray?

    init {
        require(!(maxBlockBits < 1 || maxBlockBits > 30)) { "maxBlockBits should be 1 .. 30; got $maxBlockBits" }

        if (numBytes > 1 shl maxBlockBits) {
            // FST is big: we need multiple pages
            dataOutput = getOnHeapReaderWriter(maxBlockBits) as ReadWriteDataOutput
            dataOutput!!.copyBytes(`in`, numBytes)
            dataOutput!!.freeze()
            bytesArray = null
        } else {
            // FST fits into a single block: use ByteArrayBytesStoreReader for less overhead
            bytesArray = ByteArray(numBytes.toInt())
            `in`.readBytes(bytesArray, 0, bytesArray.size)
        }
    }

    override fun ramBytesUsed(): Long {
        var size = BASE_RAM_BYTES_USED
        size += bytesArray?.size?.toLong() ?: dataOutput!!.ramBytesUsed()
        return size
    }

    override fun getReverseBytesReader(): BytesReader {
        return if (bytesArray != null) {
            ReverseBytesReader(bytesArray)
        } else {
            dataOutput!!.getReverseBytesReader()
        }
    }

    @Throws(IOException::class)
    override fun writeTo(out: DataOutput) {
        if (dataOutput != null) {
            dataOutput!!.writeTo(out)
        } else {
            checkNotNull(bytesArray)
            out.writeBytes(bytesArray, 0, bytesArray.size)
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(OnHeapFSTStore::class)
    }
}
