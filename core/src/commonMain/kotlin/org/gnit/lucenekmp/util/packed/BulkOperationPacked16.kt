package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked16 : BulkOperationPacked(16) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 48
            while (shift >= 0) {
                values[valuesOffset++] = ((block ushr shift) and 65535L).toInt()
                shift -= 16
            }
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (j in 0..<iterations) {
            values[valuesOffset++] =
                ((blocks[blocksOffset++].toInt() and 0xFF) shl 8) or (blocks[blocksOffset++].toInt() and 0xFF)
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block = blocks[blocksOffset++]
            var shift = 48
            while (shift >= 0) {
                values[valuesOffset++] = (block ushr shift) and 65535L
                shift -= 16
            }
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (j in 0..<iterations) {
            values[valuesOffset++] =
                ((blocks[blocksOffset++].toLong() and 0xFFL) shl 8) or (blocks[blocksOffset++].toLong() and 0xFFL)
        }
    }
}
