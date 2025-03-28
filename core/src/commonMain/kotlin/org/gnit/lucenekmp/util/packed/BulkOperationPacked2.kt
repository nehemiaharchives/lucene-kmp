package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked2 : BulkOperationPacked(2) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 62
            while (shift >= 0) {
                values[valuesOffset++] = ((block ushr shift) and 3L).toInt()
                shift -= 2
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
            values[valuesOffset++] = (block.toInt() ushr 6) and 3
            values[valuesOffset++] = (block.toInt() ushr 4) and 3
            values[valuesOffset++] = (block.toInt() ushr 2) and 3
            values[valuesOffset++] = block.toInt() and 3
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 62
            while (shift >= 0) {
                values[valuesOffset++] = (block ushr shift) and 3L
                shift -= 2
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
            values[valuesOffset++] = ((block.toInt() ushr 6) and 3).toLong()
            values[valuesOffset++] = ((block.toInt() ushr 4) and 3).toLong()
            values[valuesOffset++] = ((block.toInt() ushr 2) and 3).toLong()
            values[valuesOffset++] = (block.toInt() and 3).toLong()
        }
    }
}
