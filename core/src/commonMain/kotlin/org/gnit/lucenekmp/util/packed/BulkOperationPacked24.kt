package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked24 : BulkOperationPacked(24) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 40).toInt()
            values[valuesOffset++] = ((block0 ushr 16) and 16777215L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 65535L) shl 8) or (block1 ushr 56)).toInt()
            values[valuesOffset++] = ((block1 ushr 32) and 16777215L).toInt()
            values[valuesOffset++] = ((block1 ushr 8) and 16777215L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 255L) shl 16) or (block2 ushr 48)).toInt()
            values[valuesOffset++] = ((block2 ushr 24) and 16777215L).toInt()
            values[valuesOffset++] = (block2 and 16777215L).toInt()
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = blocks[blocksOffset++].toInt() and 0xFF
            val byte1 = blocks[blocksOffset++].toInt() and 0xFF
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = (byte0 shl 16) or (byte1 shl 8) or byte2
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 40
            values[valuesOffset++] = (block0 ushr 16) and 16777215L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 65535L) shl 8) or (block1 ushr 56)
            values[valuesOffset++] = (block1 ushr 32) and 16777215L
            values[valuesOffset++] = (block1 ushr 8) and 16777215L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 255L) shl 16) or (block2 ushr 48)
            values[valuesOffset++] = (block2 ushr 24) and 16777215L
            values[valuesOffset++] = block2 and 16777215L
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte1 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = (byte0 shl 16) or (byte1 shl 8) or byte2
        }
    }
}
