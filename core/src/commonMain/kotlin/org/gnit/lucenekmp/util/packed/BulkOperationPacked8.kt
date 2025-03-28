package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked8 : BulkOperationPacked(8) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 56
            while (shift >= 0) {
                values[valuesOffset++] = ((block ushr shift) and 255L).toInt()
                shift -= 8
            }
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (j in 0..<iterations) {
            values[valuesOffset++] = blocks[blocksOffset++].toInt() and 0xFF
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 56
            while (shift >= 0) {
                values[valuesOffset++] = (block ushr shift) and 255L
                shift -= 8
            }
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (j in 0..<iterations) {
            values[valuesOffset++] = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
        }
    }
}
