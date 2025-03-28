package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked4 : BulkOperationPacked(4) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 60
            while (shift >= 0) {
                values[valuesOffset++] = ((block ushr shift) and 15L).toInt()
                shift -= 4
            }
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (j in 0..<iterations) {
            val block = blocks[blocksOffset++]
            values[valuesOffset++] = (block.toInt() ushr 4) and 15
            values[valuesOffset++] = block.toInt() and 15
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 60
            while (shift >= 0) {
                values[valuesOffset++] = (block ushr shift) and 15L
                shift -= 4
            }
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (j in 0..<iterations) {
            val block = blocks[blocksOffset++]
            values[valuesOffset++] = ((block.toInt() ushr 4) and 15).toLong()
            values[valuesOffset++] = (block.toInt() and 15).toLong()
        }
    }
}
