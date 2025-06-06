package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.ByteBlockPool

/**
 * Class that Posting and PostingVector use to write interleaved byte streams into shared fixed-size
 * byte[] arrays. The idea is to allocate slices of increasing lengths. For example, the first slice
 * is 5 bytes, the next slice is 14, etc. We start by writing our bytes into the first 5 bytes. When
 * we hit the end of the slice, we allocate the next slice and then write the address of the new
 * slice into the last 4 bytes of the previous slice (the "forwarding address").
 *
 *
 * Each slice is filled with 0's initially, and we mark the end with a non-zero byte. This way
 * the methods that are writing into the slice don't need to record its length and instead allocate
 * a new slice once they hit a non-zero byte.
 *
 * @lucene.internal
 */
internal class ByteSlicePool(
    /**
     * The underlying structure consists of fixed-size blocks. We overlay variable-length slices on
     * top. Each slice is contiguous in memory, i.e. it does not straddle multiple blocks.
     */
    val pool: ByteBlockPool
) {

    /**
     * Allocates a new slice with the given size and level 0.
     *
     * @return the position where the slice starts
     */
    fun newSlice(size: Int): Int {
        require(size <= ByteBlockPool.BYTE_BLOCK_SIZE) {
            ("Slice size "
                    + size
                    + " should be less than the block size "
                    + ByteBlockPool.BYTE_BLOCK_SIZE)
        }

        if (pool.byteUpto > ByteBlockPool.BYTE_BLOCK_SIZE - size) {
            pool.nextBuffer()
        }
        val upto: Int = pool.byteUpto
        pool.byteUpto += size
        pool.buffer!![pool.byteUpto - 1] = 16 // This codifies level 0.
        return upto
    }

    /**
     * Creates a new byte slice in continuation of the provided slice and return its offset into the
     * pool.
     *
     * @param slice the current slice
     * @param upto the offset into the current slice, which is expected to point to the last byte of
     * the slice
     * @return the new slice's offset in the pool
     */
    fun allocSlice(slice: ByteArray, upto: Int): Int {
        return allocKnownSizeSlice(slice, upto) shr 8
    }

    /**
     * Create a new byte slice in continuation of the provided slice and return its length and offset
     * into the pool.
     *
     * @param slice the current slice
     * @param upto the offset into the current slice, which is expected to point to the last byte of
     * the slice
     * @return the new slice's length on the lower 8 bits and the offset into the pool on the other 24
     * bits
     */
    fun allocKnownSizeSlice(slice: ByteArray, upto: Int): Int {
        val level = slice[upto].toInt() and 15 // The last 4 bits codify the level.
        val newLevel = NEXT_LEVEL_ARRAY[level]
        val newSize = LEVEL_SIZE_ARRAY[newLevel]

        // Maybe allocate another block
        if (pool.byteUpto > ByteBlockPool.BYTE_BLOCK_SIZE - newSize) {
            pool.nextBuffer()
        }

        val newUpto: Int = pool.byteUpto
        val offset: Int = newUpto + pool.byteOffset
        pool.byteUpto += newSize

        // Copy forward the past 3 bytes (which we are about to overwrite with the forwarding address).
        // We actually copy 4 bytes at once since VarHandles make it cheap.
        val past3Bytes = BitUtil.VH_LE_INT.get(slice, upto - 3) and 0xFFFFFF
        // Ensure we're not changing the content of `buffer` by setting 4 bytes instead of 3. This
        // should never happen since the next `newSize` bytes must be equal to 0.
        assert(pool.buffer!![newUpto + 3].toInt() == 0)
        BitUtil.VH_LE_INT.set(pool.buffer!!, newUpto, past3Bytes)

        // Write forwarding address at end of last slice:
        BitUtil.VH_LE_INT.set(slice, upto - 3, offset)

        // Write new level:
        pool.buffer!![pool.byteUpto - 1] = (16 or newLevel).toByte()

        return ((newUpto + 3) shl 8) or (newSize - 3)
    }

    companion object {
        /**
         * An array holding the level sizes for byte slices. The first slice is 5 bytes, the second is 14,
         * and so on.
         */
        val LEVEL_SIZE_ARRAY: IntArray = intArrayOf(5, 14, 20, 30, 40, 40, 80, 80, 120, 200)

        /**
         * An array holding indexes for the [.LEVEL_SIZE_ARRAY], to quickly navigate to the next
         * slice level. These are encoded on 4 bits in the slice, so the values in this array should be
         * less than 16.
         *
         *
         * `NEXT_LEVEL_ARRAY[x] == x + 1`, except for the last element, where `NEXT_LEVEL_ARRAY[x] == x`, pointing at the maximum slice size.
         */
        val NEXT_LEVEL_ARRAY: IntArray = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 9)

        /** The first level size for new slices.  */
        val FIRST_LEVEL_SIZE: Int = LEVEL_SIZE_ARRAY[0]
    }
}
