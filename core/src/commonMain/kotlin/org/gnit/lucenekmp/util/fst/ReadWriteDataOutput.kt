package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.fst.FST.BytesReader
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput.Companion.ALLOCATE_BB_ON_HEAP
import org.gnit.lucenekmp.store.ByteBuffersDataOutput.Companion.NO_REUSE
import org.gnit.lucenekmp.store.DataOutput
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.ByteBuffer

/**
 * An adapter class to use [ByteBuffersDataOutput] as a [FSTReader]. It allows the FST
 * to be readable immediately after writing
 */
internal class ReadWriteDataOutput(private val blockBits: Int) : DataOutput(), FSTReader {
    private val dataOutput: ByteBuffersDataOutput =
        ByteBuffersDataOutput(blockBits, blockBits, ALLOCATE_BB_ON_HEAP, NO_REUSE)
    private val blockSize: Int = 1 shl blockBits
    private val blockMask: Int = blockSize - 1
    private var byteBuffers: MutableList<ByteBuffer>? = null

    // whether this DataOutput is already frozen
    private var frozen = false

    override fun writeByte(b: Byte) {
        require(!frozen)
        dataOutput.writeByte(b)
    }

    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
        require(!frozen)
        dataOutput.writeBytes(b, offset, length)
    }

    override fun ramBytesUsed(): Long {
        return dataOutput.ramBytesUsed()
    }

    fun freeze() {
        frozen = true
        // this operation is costly, so we want to compute it once and cache
        this.byteBuffers = dataOutput.toWriteableBufferList()
        // ensure the ByteBuffer internal array is accessible. The call to toWriteableBufferList() above
        // would ensure that it is accessible.
        require(byteBuffers!!.all { obj: ByteBuffer -> obj.hasArray() })
    }

    override fun getReverseBytesReader(): BytesReader {
        checkNotNull(byteBuffers) // freeze() must be called first
        if (byteBuffers!!.size == 1) {
            // use a faster implementation for single-block case
            return ReverseBytesReader(byteBuffers!![0].array())
        }
        return object : BytesReader() {
            private var current: ByteArray = byteBuffers!![0].array()
            private var nextBuffer = -1
            private var nextRead = 0

            override fun readByte(): Byte {
                if (nextRead == -1) {
                    current = byteBuffers!![nextBuffer--].array()
                    nextRead = blockSize - 1
                }
                return current[nextRead--]
            }

            override fun skipBytes(count: Long) {
                position = position - count
            }

            override fun readBytes(b: ByteArray, offset: Int, len: Int) {
                for (i in 0..<len) {
                    b[offset + i] = readByte()
                }
            }

            override var position: Long
                get() = (nextBuffer.toLong() + 1) * blockSize + nextRead
                set(pos: Long) {
                    val bufferIndex = (pos shr blockBits).toInt()
                    if (nextBuffer != bufferIndex - 1) {
                        nextBuffer = bufferIndex - 1
                        current = byteBuffers!![bufferIndex].array()
                    }
                    nextRead = (pos and blockMask.toLong()).toInt()
                    require(position == pos) { "pos=$pos getPos()=${position}" }
                }
        }
    }

    @Throws(IOException::class)
    override fun writeTo(out: DataOutput) {
        dataOutput.copyTo(out)
    }
}
