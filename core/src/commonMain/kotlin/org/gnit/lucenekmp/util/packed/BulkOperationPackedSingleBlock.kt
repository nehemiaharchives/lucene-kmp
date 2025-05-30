package org.gnit.lucenekmp.util.packed

/** Non-specialized [BulkOperation] for [PackedInts.Format.PACKED_SINGLE_BLOCK].  */
internal class BulkOperationPackedSingleBlock(private val bitsPerValue: Int) :
    BulkOperation() {
    private val valueCount: Int = 64 / bitsPerValue
    private val mask: Long = (1L shl bitsPerValue) - 1

    override fun longBlockCount(): Int {
        return BLOCK_COUNT
    }

    override fun byteBlockCount(): Int {
        return BLOCK_COUNT * 8
    }

    override fun longValueCount(): Int {
        return valueCount
    }

    override fun byteValueCount(): Int {
        return valueCount
    }

    private fun decode(block: Long, values: LongArray, valuesOffset: Int): Int {
        var block = block
        var valuesOffset = valuesOffset
        values[valuesOffset++] = block and mask
        for (j in 1..<valueCount) {
            block = block ushr bitsPerValue
            values[valuesOffset++] = block and mask
        }
        return valuesOffset
    }

    private fun decode(block: Long, values: IntArray, valuesOffset: Int): Int {
        var block = block
        var valuesOffset = valuesOffset
        values[valuesOffset++] = (block and mask).toInt()
        for (j in 1..<valueCount) {
            block = block ushr bitsPerValue
            values[valuesOffset++] = (block and mask).toInt()
        }
        return valuesOffset
    }

    private fun encode(values: LongArray, valuesOffset: Int): Long {
        var valuesOffset = valuesOffset
        var block = values[valuesOffset++]
        for (j in 1..<valueCount) {
            block = block or (values[valuesOffset++] shl (j * bitsPerValue))
        }
        return block
    }

    private fun encode(values: IntArray, valuesOffset: Int): Long {
        var valuesOffset = valuesOffset
        var block = values[valuesOffset++].toLong() and 0xFFFFFFFFL
        for (j in 1..<valueCount) {
            block = block or ((values[valuesOffset++].toLong() and 0xFFFFFFFFL) shl (j * bitsPerValue))
        }
        return block
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            valuesOffset = decode(block, values, valuesOffset)
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = readLong(blocks, blocksOffset)
            blocksOffset += 8
            valuesOffset = decode(block, values, valuesOffset)
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        if (bitsPerValue > 32) {
            throw UnsupportedOperationException(
                "Cannot decode $bitsPerValue-bits values into an int[]"
            )
        }
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            valuesOffset = decode(block, values, valuesOffset)
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        if (bitsPerValue > 32) {
            throw UnsupportedOperationException(
                "Cannot decode $bitsPerValue-bits values into an int[]"
            )
        }
        for (i in 0..<iterations) {
            val block = readLong(blocks, blocksOffset)
            blocksOffset += 8
            valuesOffset = decode(block, values, valuesOffset)
        }
    }

    override fun encode(
        values: LongArray, valuesOffset: Int, blocks: LongArray, blocksOffset: Int, iterations: Int
    ) {
        var valuesOffset = valuesOffset
        var blocksOffset = blocksOffset
        for (i in 0..<iterations) {
            blocks[blocksOffset++] = encode(values, valuesOffset)
            valuesOffset += valueCount
        }
    }

    override fun encode(
        values: IntArray, valuesOffset: Int, blocks: LongArray, blocksOffset: Int, iterations: Int
    ) {
        var valuesOffset = valuesOffset
        var blocksOffset = blocksOffset
        for (i in 0..<iterations) {
            blocks[blocksOffset++] = encode(values, valuesOffset)
            valuesOffset += valueCount
        }
    }

    override fun encode(
        values: LongArray, valuesOffset: Int, blocks: ByteArray, blocksOffset: Int, iterations: Int
    ) {
        var valuesOffset = valuesOffset
        var blocksOffset = blocksOffset
        for (i in 0..<iterations) {
            val block = encode(values, valuesOffset)
            valuesOffset += valueCount
            blocksOffset = writeLong(block, blocks, blocksOffset)
        }
    }

    override fun encode(
        values: IntArray, valuesOffset: Int, blocks: ByteArray, blocksOffset: Int, iterations: Int
    ) {
        var valuesOffset = valuesOffset
        var blocksOffset = blocksOffset
        for (i in 0..<iterations) {
            val block = encode(values, valuesOffset)
            valuesOffset += valueCount
            blocksOffset = writeLong(block, blocks, blocksOffset)
        }
    }

    companion object {
        private const val BLOCK_COUNT = 1

        private fun readLong(blocks: ByteArray, blocksOffset: Int): Long {
            var blocksOffset = blocksOffset
            return ((blocks[blocksOffset++].toLong() and 0xFFL) shl 56 or ((blocks[blocksOffset++].toLong() and 0xFFL) shl 48
                    ) or ((blocks[blocksOffset++].toLong() and 0xFFL) shl 40
                    ) or ((blocks[blocksOffset++].toLong() and 0xFFL) shl 32
                    ) or ((blocks[blocksOffset++].toLong() and 0xFFL) shl 24
                    ) or ((blocks[blocksOffset++].toLong() and 0xFFL) shl 16
                    ) or ((blocks[blocksOffset++].toLong() and 0xFFL) shl 8
                    ) or (blocks[blocksOffset++].toLong() and 0xFFL))
        }
    }
}
