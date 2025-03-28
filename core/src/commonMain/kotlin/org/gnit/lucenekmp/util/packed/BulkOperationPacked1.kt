package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked1 : BulkOperationPacked(1) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 63
            while (shift >= 0) {
                values[valuesOffset++] = ((block ushr shift) and 1L).toInt()
                shift -= 1
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
            values[valuesOffset++] = (block.toInt() ushr 7) and 1
            values[valuesOffset++] = (block.toInt() ushr 6) and 1
            values[valuesOffset++] = (block.toInt() ushr 5) and 1
            values[valuesOffset++] = (block.toInt() ushr 4) and 1
            values[valuesOffset++] = (block.toInt() ushr 3) and 1
            values[valuesOffset++] = (block.toInt() ushr 2) and 1
            values[valuesOffset++] = (block.toInt() ushr 1) and 1
            values[valuesOffset++] = block.toInt() and 1
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 63
            while (shift >= 0) {
                values[valuesOffset++] = (block ushr shift) and 1L
                shift -= 1
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
            values[valuesOffset++] = ((block.toInt() ushr 7) and 1).toLong()
            values[valuesOffset++] = ((block.toInt() ushr 6) and 1).toLong()
            values[valuesOffset++] = ((block.toInt() ushr 5) and 1).toLong()
            values[valuesOffset++] = ((block.toInt() ushr 4) and 1).toLong()
            values[valuesOffset++] = ((block.toInt() ushr 3) and 1).toLong()
            values[valuesOffset++] = ((block.toInt() ushr 2) and 1).toLong()
            values[valuesOffset++] = ((block.toInt() ushr 1) and 1).toLong()
            values[valuesOffset++] = (block.toInt() and 1).toLong()
        }
    }
}
