package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked5 : BulkOperationPacked(5) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 59).toInt()
            values[valuesOffset++] = ((block0 ushr 54) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 49) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 44) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 39) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 34) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 29) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 24) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 19) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 14) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 9) and 31L).toInt()
            values[valuesOffset++] = ((block0 ushr 4) and 31L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 15L) shl 1) or (block1 ushr 63)).toInt()
            values[valuesOffset++] = ((block1 ushr 58) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 53) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 48) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 43) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 38) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 33) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 28) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 23) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 18) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 13) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 8) and 31L).toInt()
            values[valuesOffset++] = ((block1 ushr 3) and 31L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 7L) shl 2) or (block2 ushr 62)).toInt()
            values[valuesOffset++] = ((block2 ushr 57) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 52) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 47) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 42) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 37) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 32) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 27) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 22) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 17) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 12) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 7) and 31L).toInt()
            values[valuesOffset++] = ((block2 ushr 2) and 31L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 3L) shl 3) or (block3 ushr 61)).toInt()
            values[valuesOffset++] = ((block3 ushr 56) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 51) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 46) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 41) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 36) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 31) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 26) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 21) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 16) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 11) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 6) and 31L).toInt()
            values[valuesOffset++] = ((block3 ushr 1) and 31L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 1L) shl 4) or (block4 ushr 60)).toInt()
            values[valuesOffset++] = ((block4 ushr 55) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 50) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 45) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 40) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 35) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 30) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 25) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 20) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 15) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 10) and 31L).toInt()
            values[valuesOffset++] = ((block4 ushr 5) and 31L).toInt()
            values[valuesOffset++] = (block4 and 31L).toInt()
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = byte0 ushr 3
            val byte1 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte0 and 7) shl 2) or (byte1 ushr 6)
            values[valuesOffset++] = (byte1 ushr 1) and 31
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte1 and 1) shl 4) or (byte2 ushr 4)
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte2 and 15) shl 1) or (byte3 ushr 7)
            values[valuesOffset++] = (byte3 ushr 2) and 31
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte3 and 3) shl 3) or (byte4 ushr 5)
            values[valuesOffset++] = byte4 and 31
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 59
            values[valuesOffset++] = (block0 ushr 54) and 31L
            values[valuesOffset++] = (block0 ushr 49) and 31L
            values[valuesOffset++] = (block0 ushr 44) and 31L
            values[valuesOffset++] = (block0 ushr 39) and 31L
            values[valuesOffset++] = (block0 ushr 34) and 31L
            values[valuesOffset++] = (block0 ushr 29) and 31L
            values[valuesOffset++] = (block0 ushr 24) and 31L
            values[valuesOffset++] = (block0 ushr 19) and 31L
            values[valuesOffset++] = (block0 ushr 14) and 31L
            values[valuesOffset++] = (block0 ushr 9) and 31L
            values[valuesOffset++] = (block0 ushr 4) and 31L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 15L) shl 1) or (block1 ushr 63)
            values[valuesOffset++] = (block1 ushr 58) and 31L
            values[valuesOffset++] = (block1 ushr 53) and 31L
            values[valuesOffset++] = (block1 ushr 48) and 31L
            values[valuesOffset++] = (block1 ushr 43) and 31L
            values[valuesOffset++] = (block1 ushr 38) and 31L
            values[valuesOffset++] = (block1 ushr 33) and 31L
            values[valuesOffset++] = (block1 ushr 28) and 31L
            values[valuesOffset++] = (block1 ushr 23) and 31L
            values[valuesOffset++] = (block1 ushr 18) and 31L
            values[valuesOffset++] = (block1 ushr 13) and 31L
            values[valuesOffset++] = (block1 ushr 8) and 31L
            values[valuesOffset++] = (block1 ushr 3) and 31L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 7L) shl 2) or (block2 ushr 62)
            values[valuesOffset++] = (block2 ushr 57) and 31L
            values[valuesOffset++] = (block2 ushr 52) and 31L
            values[valuesOffset++] = (block2 ushr 47) and 31L
            values[valuesOffset++] = (block2 ushr 42) and 31L
            values[valuesOffset++] = (block2 ushr 37) and 31L
            values[valuesOffset++] = (block2 ushr 32) and 31L
            values[valuesOffset++] = (block2 ushr 27) and 31L
            values[valuesOffset++] = (block2 ushr 22) and 31L
            values[valuesOffset++] = (block2 ushr 17) and 31L
            values[valuesOffset++] = (block2 ushr 12) and 31L
            values[valuesOffset++] = (block2 ushr 7) and 31L
            values[valuesOffset++] = (block2 ushr 2) and 31L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 3L) shl 3) or (block3 ushr 61)
            values[valuesOffset++] = (block3 ushr 56) and 31L
            values[valuesOffset++] = (block3 ushr 51) and 31L
            values[valuesOffset++] = (block3 ushr 46) and 31L
            values[valuesOffset++] = (block3 ushr 41) and 31L
            values[valuesOffset++] = (block3 ushr 36) and 31L
            values[valuesOffset++] = (block3 ushr 31) and 31L
            values[valuesOffset++] = (block3 ushr 26) and 31L
            values[valuesOffset++] = (block3 ushr 21) and 31L
            values[valuesOffset++] = (block3 ushr 16) and 31L
            values[valuesOffset++] = (block3 ushr 11) and 31L
            values[valuesOffset++] = (block3 ushr 6) and 31L
            values[valuesOffset++] = (block3 ushr 1) and 31L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 1L) shl 4) or (block4 ushr 60)
            values[valuesOffset++] = (block4 ushr 55) and 31L
            values[valuesOffset++] = (block4 ushr 50) and 31L
            values[valuesOffset++] = (block4 ushr 45) and 31L
            values[valuesOffset++] = (block4 ushr 40) and 31L
            values[valuesOffset++] = (block4 ushr 35) and 31L
            values[valuesOffset++] = (block4 ushr 30) and 31L
            values[valuesOffset++] = (block4 ushr 25) and 31L
            values[valuesOffset++] = (block4 ushr 20) and 31L
            values[valuesOffset++] = (block4 ushr 15) and 31L
            values[valuesOffset++] = (block4 ushr 10) and 31L
            values[valuesOffset++] = (block4 ushr 5) and 31L
            values[valuesOffset++] = block4 and 31L
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = byte0 ushr 3
            val byte1 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte0 and 7L) shl 2) or (byte1 ushr 6)
            values[valuesOffset++] = (byte1 ushr 1) and 31L
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte1 and 1L) shl 4) or (byte2 ushr 4)
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte2 and 15L) shl 1) or (byte3 ushr 7)
            values[valuesOffset++] = (byte3 ushr 2) and 31L
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte3 and 3L) shl 3) or (byte4 ushr 5)
            values[valuesOffset++] = byte4 and 31L
        }
    }
}
