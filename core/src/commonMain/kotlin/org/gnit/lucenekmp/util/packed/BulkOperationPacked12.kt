package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked12 : BulkOperationPacked(12) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 52).toInt()
            values[valuesOffset++] = ((block0 ushr 40) and 4095L).toInt()
            values[valuesOffset++] = ((block0 ushr 28) and 4095L).toInt()
            values[valuesOffset++] = ((block0 ushr 16) and 4095L).toInt()
            values[valuesOffset++] = ((block0 ushr 4) and 4095L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 15L) shl 8) or (block1 ushr 56)).toInt()
            values[valuesOffset++] = ((block1 ushr 44) and 4095L).toInt()
            values[valuesOffset++] = ((block1 ushr 32) and 4095L).toInt()
            values[valuesOffset++] = ((block1 ushr 20) and 4095L).toInt()
            values[valuesOffset++] = ((block1 ushr 8) and 4095L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 255L) shl 4) or (block2 ushr 60)).toInt()
            values[valuesOffset++] = ((block2 ushr 48) and 4095L).toInt()
            values[valuesOffset++] = ((block2 ushr 36) and 4095L).toInt()
            values[valuesOffset++] = ((block2 ushr 24) and 4095L).toInt()
            values[valuesOffset++] = ((block2 ushr 12) and 4095L).toInt()
            values[valuesOffset++] = (block2 and 4095L).toInt()
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
            values[valuesOffset++] = (byte0 shl 4) or (byte1 ushr 4)
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte1 and 15) shl 8) or byte2
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 52
            values[valuesOffset++] = (block0 ushr 40) and 4095L
            values[valuesOffset++] = (block0 ushr 28) and 4095L
            values[valuesOffset++] = (block0 ushr 16) and 4095L
            values[valuesOffset++] = (block0 ushr 4) and 4095L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 15L) shl 8) or (block1 ushr 56)
            values[valuesOffset++] = (block1 ushr 44) and 4095L
            values[valuesOffset++] = (block1 ushr 32) and 4095L
            values[valuesOffset++] = (block1 ushr 20) and 4095L
            values[valuesOffset++] = (block1 ushr 8) and 4095L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 255L) shl 4) or (block2 ushr 60)
            values[valuesOffset++] = (block2 ushr 48) and 4095L
            values[valuesOffset++] = (block2 ushr 36) and 4095L
            values[valuesOffset++] = (block2 ushr 24) and 4095L
            values[valuesOffset++] = (block2 ushr 12) and 4095L
            values[valuesOffset++] = block2 and 4095L
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
            values[valuesOffset++] = (byte0 shl 4) or (byte1 ushr 4)
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte1 and 15L) shl 8) or byte2
        }
    }
}
