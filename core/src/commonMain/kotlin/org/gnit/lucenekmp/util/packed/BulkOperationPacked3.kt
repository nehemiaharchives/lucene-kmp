package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked3 : BulkOperationPacked(3) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 61).toInt()
            values[valuesOffset++] = ((block0 ushr 58) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 55) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 52) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 49) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 46) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 43) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 40) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 37) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 34) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 31) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 28) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 25) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 22) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 19) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 16) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 13) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 10) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 7) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 4) and 7L).toInt()
            values[valuesOffset++] = ((block0 ushr 1) and 7L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 1L) shl 2) or (block1 ushr 62)).toInt()
            values[valuesOffset++] = ((block1 ushr 59) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 56) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 53) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 50) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 47) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 44) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 41) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 38) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 35) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 32) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 29) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 26) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 23) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 20) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 17) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 14) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 11) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 8) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 5) and 7L).toInt()
            values[valuesOffset++] = ((block1 ushr 2) and 7L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 3L) shl 1) or (block2 ushr 63)).toInt()
            values[valuesOffset++] = ((block2 ushr 60) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 57) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 54) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 51) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 48) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 45) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 42) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 39) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 36) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 33) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 30) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 27) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 24) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 21) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 18) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 15) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 12) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 9) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 6) and 7L).toInt()
            values[valuesOffset++] = ((block2 ushr 3) and 7L).toInt()
            values[valuesOffset++] = (block2 and 7L).toInt()
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = byte0 ushr 5
            values[valuesOffset++] = (byte0 ushr 2) and 7
            val byte1 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte0 and 3) shl 1) or (byte1 ushr 7)
            values[valuesOffset++] = (byte1 ushr 4) and 7
            values[valuesOffset++] = (byte1 ushr 1) and 7
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte1 and 1) shl 2) or (byte2 ushr 6)
            values[valuesOffset++] = (byte2 ushr 3) and 7
            values[valuesOffset++] = byte2 and 7
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 61
            values[valuesOffset++] = (block0 ushr 58) and 7L
            values[valuesOffset++] = (block0 ushr 55) and 7L
            values[valuesOffset++] = (block0 ushr 52) and 7L
            values[valuesOffset++] = (block0 ushr 49) and 7L
            values[valuesOffset++] = (block0 ushr 46) and 7L
            values[valuesOffset++] = (block0 ushr 43) and 7L
            values[valuesOffset++] = (block0 ushr 40) and 7L
            values[valuesOffset++] = (block0 ushr 37) and 7L
            values[valuesOffset++] = (block0 ushr 34) and 7L
            values[valuesOffset++] = (block0 ushr 31) and 7L
            values[valuesOffset++] = (block0 ushr 28) and 7L
            values[valuesOffset++] = (block0 ushr 25) and 7L
            values[valuesOffset++] = (block0 ushr 22) and 7L
            values[valuesOffset++] = (block0 ushr 19) and 7L
            values[valuesOffset++] = (block0 ushr 16) and 7L
            values[valuesOffset++] = (block0 ushr 13) and 7L
            values[valuesOffset++] = (block0 ushr 10) and 7L
            values[valuesOffset++] = (block0 ushr 7) and 7L
            values[valuesOffset++] = (block0 ushr 4) and 7L
            values[valuesOffset++] = (block0 ushr 1) and 7L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 1L) shl 2) or (block1 ushr 62)
            values[valuesOffset++] = (block1 ushr 59) and 7L
            values[valuesOffset++] = (block1 ushr 56) and 7L
            values[valuesOffset++] = (block1 ushr 53) and 7L
            values[valuesOffset++] = (block1 ushr 50) and 7L
            values[valuesOffset++] = (block1 ushr 47) and 7L
            values[valuesOffset++] = (block1 ushr 44) and 7L
            values[valuesOffset++] = (block1 ushr 41) and 7L
            values[valuesOffset++] = (block1 ushr 38) and 7L
            values[valuesOffset++] = (block1 ushr 35) and 7L
            values[valuesOffset++] = (block1 ushr 32) and 7L
            values[valuesOffset++] = (block1 ushr 29) and 7L
            values[valuesOffset++] = (block1 ushr 26) and 7L
            values[valuesOffset++] = (block1 ushr 23) and 7L
            values[valuesOffset++] = (block1 ushr 20) and 7L
            values[valuesOffset++] = (block1 ushr 17) and 7L
            values[valuesOffset++] = (block1 ushr 14) and 7L
            values[valuesOffset++] = (block1 ushr 11) and 7L
            values[valuesOffset++] = (block1 ushr 8) and 7L
            values[valuesOffset++] = (block1 ushr 5) and 7L
            values[valuesOffset++] = (block1 ushr 2) and 7L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 3L) shl 1) or (block2 ushr 63)
            values[valuesOffset++] = (block2 ushr 60) and 7L
            values[valuesOffset++] = (block2 ushr 57) and 7L
            values[valuesOffset++] = (block2 ushr 54) and 7L
            values[valuesOffset++] = (block2 ushr 51) and 7L
            values[valuesOffset++] = (block2 ushr 48) and 7L
            values[valuesOffset++] = (block2 ushr 45) and 7L
            values[valuesOffset++] = (block2 ushr 42) and 7L
            values[valuesOffset++] = (block2 ushr 39) and 7L
            values[valuesOffset++] = (block2 ushr 36) and 7L
            values[valuesOffset++] = (block2 ushr 33) and 7L
            values[valuesOffset++] = (block2 ushr 30) and 7L
            values[valuesOffset++] = (block2 ushr 27) and 7L
            values[valuesOffset++] = (block2 ushr 24) and 7L
            values[valuesOffset++] = (block2 ushr 21) and 7L
            values[valuesOffset++] = (block2 ushr 18) and 7L
            values[valuesOffset++] = (block2 ushr 15) and 7L
            values[valuesOffset++] = (block2 ushr 12) and 7L
            values[valuesOffset++] = (block2 ushr 9) and 7L
            values[valuesOffset++] = (block2 ushr 6) and 7L
            values[valuesOffset++] = (block2 ushr 3) and 7L
            values[valuesOffset++] = block2 and 7L
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = byte0 ushr 5
            values[valuesOffset++] = (byte0 ushr 2) and 7L
            val byte1 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte0 and 3L) shl 1) or (byte1 ushr 7)
            values[valuesOffset++] = (byte1 ushr 4) and 7L
            values[valuesOffset++] = (byte1 ushr 1) and 7L
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte1 and 1L) shl 2) or (byte2 ushr 6)
            values[valuesOffset++] = (byte2 ushr 3) and 7L
            values[valuesOffset++] = byte2 and 7L
        }
    }
}
