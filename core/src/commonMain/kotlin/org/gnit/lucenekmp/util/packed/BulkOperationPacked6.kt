package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked6 : BulkOperationPacked(6) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 58).toInt()
            values[valuesOffset++] = ((block0 ushr 52) and 63L).toInt()
            values[valuesOffset++] = ((block0 ushr 46) and 63L).toInt()
            values[valuesOffset++] = ((block0 ushr 40) and 63L).toInt()
            values[valuesOffset++] = ((block0 ushr 34) and 63L).toInt()
            values[valuesOffset++] = ((block0 ushr 28) and 63L).toInt()
            values[valuesOffset++] = ((block0 ushr 22) and 63L).toInt()
            values[valuesOffset++] = ((block0 ushr 16) and 63L).toInt()
            values[valuesOffset++] = ((block0 ushr 10) and 63L).toInt()
            values[valuesOffset++] = ((block0 ushr 4) and 63L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 15L) shl 2) or (block1 ushr 62)).toInt()
            values[valuesOffset++] = ((block1 ushr 56) and 63L).toInt()
            values[valuesOffset++] = ((block1 ushr 50) and 63L).toInt()
            values[valuesOffset++] = ((block1 ushr 44) and 63L).toInt()
            values[valuesOffset++] = ((block1 ushr 38) and 63L).toInt()
            values[valuesOffset++] = ((block1 ushr 32) and 63L).toInt()
            values[valuesOffset++] = ((block1 ushr 26) and 63L).toInt()
            values[valuesOffset++] = ((block1 ushr 20) and 63L).toInt()
            values[valuesOffset++] = ((block1 ushr 14) and 63L).toInt()
            values[valuesOffset++] = ((block1 ushr 8) and 63L).toInt()
            values[valuesOffset++] = ((block1 ushr 2) and 63L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 3L) shl 4) or (block2 ushr 60)).toInt()
            values[valuesOffset++] = ((block2 ushr 54) and 63L).toInt()
            values[valuesOffset++] = ((block2 ushr 48) and 63L).toInt()
            values[valuesOffset++] = ((block2 ushr 42) and 63L).toInt()
            values[valuesOffset++] = ((block2 ushr 36) and 63L).toInt()
            values[valuesOffset++] = ((block2 ushr 30) and 63L).toInt()
            values[valuesOffset++] = ((block2 ushr 24) and 63L).toInt()
            values[valuesOffset++] = ((block2 ushr 18) and 63L).toInt()
            values[valuesOffset++] = ((block2 ushr 12) and 63L).toInt()
            values[valuesOffset++] = ((block2 ushr 6) and 63L).toInt()
            values[valuesOffset++] = (block2 and 63L).toInt()
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = byte0 ushr 2
            val byte1 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte0 and 3) shl 4) or (byte1 ushr 4)
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte1 and 15) shl 2) or (byte2 ushr 6)
            values[valuesOffset++] = byte2 and 63
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 58
            values[valuesOffset++] = (block0 ushr 52) and 63L
            values[valuesOffset++] = (block0 ushr 46) and 63L
            values[valuesOffset++] = (block0 ushr 40) and 63L
            values[valuesOffset++] = (block0 ushr 34) and 63L
            values[valuesOffset++] = (block0 ushr 28) and 63L
            values[valuesOffset++] = (block0 ushr 22) and 63L
            values[valuesOffset++] = (block0 ushr 16) and 63L
            values[valuesOffset++] = (block0 ushr 10) and 63L
            values[valuesOffset++] = (block0 ushr 4) and 63L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 15L) shl 2) or (block1 ushr 62)
            values[valuesOffset++] = (block1 ushr 56) and 63L
            values[valuesOffset++] = (block1 ushr 50) and 63L
            values[valuesOffset++] = (block1 ushr 44) and 63L
            values[valuesOffset++] = (block1 ushr 38) and 63L
            values[valuesOffset++] = (block1 ushr 32) and 63L
            values[valuesOffset++] = (block1 ushr 26) and 63L
            values[valuesOffset++] = (block1 ushr 20) and 63L
            values[valuesOffset++] = (block1 ushr 14) and 63L
            values[valuesOffset++] = (block1 ushr 8) and 63L
            values[valuesOffset++] = (block1 ushr 2) and 63L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 3L) shl 4) or (block2 ushr 60)
            values[valuesOffset++] = (block2 ushr 54) and 63L
            values[valuesOffset++] = (block2 ushr 48) and 63L
            values[valuesOffset++] = (block2 ushr 42) and 63L
            values[valuesOffset++] = (block2 ushr 36) and 63L
            values[valuesOffset++] = (block2 ushr 30) and 63L
            values[valuesOffset++] = (block2 ushr 24) and 63L
            values[valuesOffset++] = (block2 ushr 18) and 63L
            values[valuesOffset++] = (block2 ushr 12) and 63L
            values[valuesOffset++] = (block2 ushr 6) and 63L
            values[valuesOffset++] = block2 and 63L
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = byte0 ushr 2
            val byte1 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte0 and 3L) shl 4) or (byte1 ushr 4)
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte1 and 15L) shl 2) or (byte2 ushr 6)
            values[valuesOffset++] = byte2 and 63L
        }
    }
}
