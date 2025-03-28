package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked10 : BulkOperationPacked(10) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 54).toInt()
            values[valuesOffset++] = ((block0 ushr 44) and 1023L).toInt()
            values[valuesOffset++] = ((block0 ushr 34) and 1023L).toInt()
            values[valuesOffset++] = ((block0 ushr 24) and 1023L).toInt()
            values[valuesOffset++] = ((block0 ushr 14) and 1023L).toInt()
            values[valuesOffset++] = ((block0 ushr 4) and 1023L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 15L) shl 6) or (block1 ushr 58)).toInt()
            values[valuesOffset++] = ((block1 ushr 48) and 1023L).toInt()
            values[valuesOffset++] = ((block1 ushr 38) and 1023L).toInt()
            values[valuesOffset++] = ((block1 ushr 28) and 1023L).toInt()
            values[valuesOffset++] = ((block1 ushr 18) and 1023L).toInt()
            values[valuesOffset++] = ((block1 ushr 8) and 1023L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 255L) shl 2) or (block2 ushr 62)).toInt()
            values[valuesOffset++] = ((block2 ushr 52) and 1023L).toInt()
            values[valuesOffset++] = ((block2 ushr 42) and 1023L).toInt()
            values[valuesOffset++] = ((block2 ushr 32) and 1023L).toInt()
            values[valuesOffset++] = ((block2 ushr 22) and 1023L).toInt()
            values[valuesOffset++] = ((block2 ushr 12) and 1023L).toInt()
            values[valuesOffset++] = ((block2 ushr 2) and 1023L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 3L) shl 8) or (block3 ushr 56)).toInt()
            values[valuesOffset++] = ((block3 ushr 46) and 1023L).toInt()
            values[valuesOffset++] = ((block3 ushr 36) and 1023L).toInt()
            values[valuesOffset++] = ((block3 ushr 26) and 1023L).toInt()
            values[valuesOffset++] = ((block3 ushr 16) and 1023L).toInt()
            values[valuesOffset++] = ((block3 ushr 6) and 1023L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 63L) shl 4) or (block4 ushr 60)).toInt()
            values[valuesOffset++] = ((block4 ushr 50) and 1023L).toInt()
            values[valuesOffset++] = ((block4 ushr 40) and 1023L).toInt()
            values[valuesOffset++] = ((block4 ushr 30) and 1023L).toInt()
            values[valuesOffset++] = ((block4 ushr 20) and 1023L).toInt()
            values[valuesOffset++] = ((block4 ushr 10) and 1023L).toInt()
            values[valuesOffset++] = (block4 and 1023L).toInt()
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
            values[valuesOffset++] = (byte0 shl 2) or (byte1 ushr 6)
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte1 and 63) shl 4) or (byte2 ushr 4)
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte2 and 15) shl 6) or (byte3 ushr 2)
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte3 and 3) shl 8) or byte4
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 54
            values[valuesOffset++] = (block0 ushr 44) and 1023L
            values[valuesOffset++] = (block0 ushr 34) and 1023L
            values[valuesOffset++] = (block0 ushr 24) and 1023L
            values[valuesOffset++] = (block0 ushr 14) and 1023L
            values[valuesOffset++] = (block0 ushr 4) and 1023L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 15L) shl 6) or (block1 ushr 58)
            values[valuesOffset++] = (block1 ushr 48) and 1023L
            values[valuesOffset++] = (block1 ushr 38) and 1023L
            values[valuesOffset++] = (block1 ushr 28) and 1023L
            values[valuesOffset++] = (block1 ushr 18) and 1023L
            values[valuesOffset++] = (block1 ushr 8) and 1023L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 255L) shl 2) or (block2 ushr 62)
            values[valuesOffset++] = (block2 ushr 52) and 1023L
            values[valuesOffset++] = (block2 ushr 42) and 1023L
            values[valuesOffset++] = (block2 ushr 32) and 1023L
            values[valuesOffset++] = (block2 ushr 22) and 1023L
            values[valuesOffset++] = (block2 ushr 12) and 1023L
            values[valuesOffset++] = (block2 ushr 2) and 1023L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 3L) shl 8) or (block3 ushr 56)
            values[valuesOffset++] = (block3 ushr 46) and 1023L
            values[valuesOffset++] = (block3 ushr 36) and 1023L
            values[valuesOffset++] = (block3 ushr 26) and 1023L
            values[valuesOffset++] = (block3 ushr 16) and 1023L
            values[valuesOffset++] = (block3 ushr 6) and 1023L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 63L) shl 4) or (block4 ushr 60)
            values[valuesOffset++] = (block4 ushr 50) and 1023L
            values[valuesOffset++] = (block4 ushr 40) and 1023L
            values[valuesOffset++] = (block4 ushr 30) and 1023L
            values[valuesOffset++] = (block4 ushr 20) and 1023L
            values[valuesOffset++] = (block4 ushr 10) and 1023L
            values[valuesOffset++] = block4 and 1023L
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
            values[valuesOffset++] = (byte0 shl 2) or (byte1 ushr 6)
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte1 and 63L) shl 4) or (byte2 ushr 4)
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte2 and 15L) shl 6) or (byte3 ushr 2)
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte3 and 3L) shl 8) or byte4
        }
    }
}
