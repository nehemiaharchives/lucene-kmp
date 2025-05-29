package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import kotlin.jvm.JvmOverloads

/**
 * A pool for int blocks similar to [ByteBlockPool]
 *
 * @lucene.internal
 */
class IntBlockPool
/**
 * Creates a new [IntBlockPool] with a default [Allocator].
 *
 * @see IntBlockPool.nextBuffer
 */ @JvmOverloads constructor(private val allocator: Allocator = DirectAllocator()) {
    /** Abstract class for allocating and freeing int blocks.  */
    abstract class Allocator protected constructor(protected val blockSize: Int) {
        abstract fun recycleIntBlocks(blocks: Array<IntArray>, start: Int, end: Int)

        val intBlock: IntArray
            get() = IntArray(blockSize)
    }

    /** A simple [Allocator] that never recycles.  */
    class DirectAllocator
    /** Creates a new [DirectAllocator] with a default block size  */
        : Allocator(INT_BLOCK_SIZE) {
        override fun recycleIntBlocks(blocks: Array<IntArray>, start: Int, end: Int) {}
    }

    private val EMPTY = IntArray(0)

    /**
     * array of buffers currently used in the pool. Buffers are allocated if needed don't modify this
     * outside of this class
     */
    var buffers: Array<IntArray> = Array<IntArray>(10){ EMPTY }

    /** index into the buffers array pointing to the current buffer used as the head  */
    private var bufferUpto = -1

    /** Pointer to the current position in head buffer  */
    var intUpto: Int = INT_BLOCK_SIZE

    /** Current head buffer  */
    var buffer: IntArray = EMPTY

    /** Current head offset  */
    var intOffset: Int = -INT_BLOCK_SIZE

    /**
     * Creates a new [IntBlockPool] with the given [Allocator].
     *
     * @see IntBlockPool.nextBuffer
     */

    /**
     * Expert: Resets the pool to its initial state, while optionally reusing the first buffer.
     * Buffers that are not reused are reclaimed by [ ][ByteBlockPool.Allocator.recycleByteBlocks]. Buffers can be filled with
     * zeros before recycling them. This is useful if a slice pool works on top of this int pool and
     * relies on the buffers being filled with zeros to find the non-zero end of slices.
     *
     * @param zeroFillBuffers if `true` the buffers are filled with `0`.
     * @param reuseFirst if `true` the first buffer will be reused and calling [     ][IntBlockPool.nextBuffer] is not needed after reset iff the block pool was used before ie.
     * [IntBlockPool.nextBuffer] was called before.
     */
    fun reset(zeroFillBuffers: Boolean, reuseFirst: Boolean) {
        if (bufferUpto != -1) {
            // We allocated at least one buffer

            if (zeroFillBuffers) {
                for (i in 0..<bufferUpto) {
                    // Fully zero fill buffers that we fully used
                    Arrays.fill(buffers[i], 0)
                }
                // Partial zero fill the final buffer
                Arrays.fill(buffers[bufferUpto], 0, intUpto, 0)
            }

            if (bufferUpto > 0 || !reuseFirst) {
                val offset = if (reuseFirst) 1 else 0
                // Recycle all but the first buffer
                allocator.recycleIntBlocks(buffers, offset, 1 + bufferUpto)
                Arrays.fill(buffers, offset, bufferUpto + 1, EMPTY)
            }
            if (reuseFirst) {
                // Re-use the first buffer
                bufferUpto = 0
                intUpto = 0
                intOffset = 0
                buffer = buffers[0]
            } else {
                bufferUpto = -1
                intUpto = INT_BLOCK_SIZE
                intOffset = -INT_BLOCK_SIZE
                buffer = EMPTY
            }
        }
    }

    /**
     * Advances the pool to its next buffer. This method should be called once after the constructor
     * to initialize the pool. In contrast to the constructor a [IntBlockPool.reset] call will advance the pool to its first buffer immediately.
     */
    fun nextBuffer() {
        if (1 + bufferUpto == buffers.size) {
            val newBuffers = Array((buffers.size * 1.5).toInt()){ EMPTY }
            System.arraycopy(buffers, 0, newBuffers, 0, buffers.size)
            buffers = newBuffers
        }
        buffers[1 + bufferUpto] = allocator.intBlock
        buffer = buffers[1 + bufferUpto]
        bufferUpto++

        intUpto = 0
        intOffset = Math.addExact(intOffset, INT_BLOCK_SIZE)
    }

    companion object {
        const val INT_BLOCK_SHIFT: Int = 13
        val INT_BLOCK_SIZE: Int = 1 shl INT_BLOCK_SHIFT
        val INT_BLOCK_MASK: Int = INT_BLOCK_SIZE - 1
    }
}
