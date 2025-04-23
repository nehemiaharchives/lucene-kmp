package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.System

// Storing a single contiguous byte[] for the current node of the FST we are writing. The byte[]
// will only grow, never shrink.
// Note: This is only safe for usage that is bounded in the number of bytes written. Do not make
// this public! Public users should instead use ByteBuffersDataOutput
class GrowableByteArrayDataOutput : DataOutput(), Accountable {
    // holds an initial size of 256 bytes. this byte array will only grow, but not shrink
    var bytes: ByteArray = ByteArray(INITIAL_SIZE)
        private set

    fun getBytes(): ByteArray {
        return bytes
    }

    fun getPosition(): Int {
        return position
    }

    private var nextWrite = 0

    override fun writeByte(b: Byte) {
        ensureCapacity(1)
        bytes[nextWrite++] = b
    }

    override fun writeBytes(b: ByteArray, offset: Int, len: Int) {
        if (len == 0) {
            return
        }
        ensureCapacity(len)
        System.arraycopy(b, offset, bytes, nextWrite, len)
        nextWrite += len
    }

    var position: Int
        get() = nextWrite
        /** Set the position of the byte[], increasing the capacity if needed  */
        set(newLen) {
            require(newLen >= 0)
            if (newLen > nextWrite) {
                ensureCapacity(newLen - nextWrite)
            }
            nextWrite = newLen
        }

    /**
     * Ensure we can write additional capacityToWrite bytes.
     *
     * @param capacityToWrite the additional bytes to write
     */
    private fun ensureCapacity(capacityToWrite: Int) {
        require(capacityToWrite > 0)
        bytes = ArrayUtil.grow(bytes, nextWrite + capacityToWrite)
    }

    /** Writes all of our bytes to the target [DataOutput].  */
    @Throws(IOException::class)
    fun writeTo(out: DataOutput) {
        out.writeBytes(bytes, 0, nextWrite)
    }

    /** Copies bytes from this store to a target byte array.  */
    fun writeTo(srcOffset: Int, dest: ByteArray, destOffset: Int, len: Int) {
        require(srcOffset + len <= nextWrite)
        System.arraycopy(bytes, srcOffset, dest, destOffset, len)
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(bytes)
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(GrowableByteArrayDataOutput::class)

        private const val INITIAL_SIZE = 1 shl 8
    }
}
