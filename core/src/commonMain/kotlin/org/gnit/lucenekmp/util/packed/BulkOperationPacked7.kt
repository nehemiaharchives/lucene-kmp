package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked7 : BulkOperationPacked(7) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 57).toInt()
            values[valuesOffset++] = ((block0 ushr 50) and 127L).toInt()
            values[valuesOffset++] = ((block0 ushr 43) and 127L).toInt()
            values[valuesOffset++] = ((block0 ushr 36) and 127L).toInt()
            values[valuesOffset++] = ((block0 ushr 29) and 127L).toInt()
            values[valuesOffset++] = ((block0 ushr 22) and 127L).toInt()
            values[valuesOffset++] = ((block0 ushr 15) and 127L).toInt()
            values[valuesOffset++] = ((block0 ushr 8) and 127L).toInt()
            values[valuesOffset++] = ((block0 ushr 1) and 127L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 1L) shl 6) or (block1 ushr 58)).toInt()
            values[valuesOffset++] = ((block1 ushr 51) and 127L).toInt()
            values[valuesOffset++] = ((block1 ushr 44) and 127L).toInt()
            values[valuesOffset++] = ((block1 ushr 37) and 127L).toInt()
            values[valuesOffset++] = ((block1 ushr 30) and 127L).toInt()
            values[valuesOffset++] = ((block1 ushr 23) and 127L).toInt()
            values[valuesOffset++] = ((block1 ushr 16) and 127L).toInt()
            values[valuesOffset++] = ((block1 ushr 9) and 127L).toInt()
            values[valuesOffset++] = ((block1 ushr 2) and 127L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 3L) shl 5) or (block2 ushr 59)).toInt()
            values[valuesOffset++] = ((block2 ushr 52) and 127L).toInt()
            values[valuesOffset++] = ((block2 ushr 45) and 127L).toInt()
            values[valuesOffset++] = ((block2 ushr 38) and 127L).toInt()
            values[valuesOffset++] = ((block2 ushr 31) and 127L).toInt()
            values[valuesOffset++] = ((block2 ushr 24) and 127L).toInt()
            values[valuesOffset++] = ((block2 ushr 17) and 127L).toInt()
            values[valuesOffset++] = ((block2 ushr 10) and 127L).toInt()
            values[valuesOffset++] = ((block2 ushr 3) and 127L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 7L) shl 4) or (block3 ushr 60)).toInt()
            values[valuesOffset++] = ((block3 ushr 53) and 127L).toInt()
            values[valuesOffset++] = ((block3 ushr 46) and 127L).toInt()
            values[valuesOffset++] = ((block3 ushr 39) and 127L).toInt()
            values[valuesOffset++] = ((block3 ushr 32) and 127L).toInt()
            values[valuesOffset++] = ((block3 ushr 25) and 127L).toInt()
            values[valuesOffset++] = ((block3 ushr 18) and 127L).toInt()
            values[valuesOffset++] = ((block3 ushr 11) and 127L).toInt()
            values[valuesOffset++] = ((block3 ushr 4) and 127L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 15L) shl 3) or (block4 ushr 61)).toInt()
            values[valuesOffset++] = ((block4 ushr 54) and 127L).toInt()
            values[valuesOffset++] = ((block4 ushr 47) and 127L).toInt()
            values[valuesOffset++] = ((block4 ushr 40) and 127L).toInt()
            values[valuesOffset++] = ((block4 ushr 33) and 127L).toInt()
            values[valuesOffset++] = ((block4 ushr 26) and 127L).toInt()
            values[valuesOffset++] = ((block4 ushr 19) and 127L).toInt()
            values[valuesOffset++] = ((block4 ushr 12) and 127L).toInt()
            values[valuesOffset++] = ((block4 ushr 5) and 127L).toInt()
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block4 and 31L) shl 2) or (block5 ushr 62)).toInt()
            values[valuesOffset++] = ((block5 ushr 55) and 127L).toInt()
            values[valuesOffset++] = ((block5 ushr 48) and 127L).toInt()
            values[valuesOffset++] = ((block5 ushr 41) and 127L).toInt()
            values[valuesOffset++] = ((block5 ushr 34) and 127L).toInt()
            values[valuesOffset++] = ((block5 ushr 27) and 127L).toInt()
            values[valuesOffset++] = ((block5 ushr 20) and 127L).toInt()
            values[valuesOffset++] = ((block5 ushr 13) and 127L).toInt()
            values[valuesOffset++] = ((block5 ushr 6) and 127L).toInt()
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block5 and 63L) shl 1) or (block6 ushr 63)).toInt()
            values[valuesOffset++] = ((block6 ushr 56) and 127L).toInt()
            values[valuesOffset++] = ((block6 ushr 49) and 127L).toInt()
            values[valuesOffset++] = ((block6 ushr 42) and 127L).toInt()
            values[valuesOffset++] = ((block6 ushr 35) and 127L).toInt()
            values[valuesOffset++] = ((block6 ushr 28) and 127L).toInt()
            values[valuesOffset++] = ((block6 ushr 21) and 127L).toInt()
            values[valuesOffset++] = ((block6 ushr 14) and 127L).toInt()
            values[valuesOffset++] = ((block6 ushr 7) and 127L).toInt()
            values[valuesOffset++] = (block6 and 127L).toInt()
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = byte0 ushr 1
            val byte1 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte0 and 1) shl 6) or (byte1 ushr 2)
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte1 and 3) shl 5) or (byte2 ushr 3)
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte2 and 7) shl 4) or (byte3 ushr 4)
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte3 and 15) shl 3) or (byte4 ushr 5)
            val byte5 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte4 and 31) shl 2) or (byte5 ushr 6)
            val byte6 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte5 and 63) shl 1) or (byte6 ushr 7)
            values[valuesOffset++] = byte6 and 127
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 57
            values[valuesOffset++] = (block0 ushr 50) and 127L
            values[valuesOffset++] = (block0 ushr 43) and 127L
            values[valuesOffset++] = (block0 ushr 36) and 127L
            values[valuesOffset++] = (block0 ushr 29) and 127L
            values[valuesOffset++] = (block0 ushr 22) and 127L
            values[valuesOffset++] = (block0 ushr 15) and 127L
            values[valuesOffset++] = (block0 ushr 8) and 127L
            values[valuesOffset++] = (block0 ushr 1) and 127L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 1L) shl 6) or (block1 ushr 58)
            values[valuesOffset++] = (block1 ushr 51) and 127L
            values[valuesOffset++] = (block1 ushr 44) and 127L
            values[valuesOffset++] = (block1 ushr 37) and 127L
            values[valuesOffset++] = (block1 ushr 30) and 127L
            values[valuesOffset++] = (block1 ushr 23) and 127L
            values[valuesOffset++] = (block1 ushr 16) and 127L
            values[valuesOffset++] = (block1 ushr 9) and 127L
            values[valuesOffset++] = (block1 ushr 2) and 127L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 3L) shl 5) or (block2 ushr 59)
            values[valuesOffset++] = (block2 ushr 52) and 127L
            values[valuesOffset++] = (block2 ushr 45) and 127L
            values[valuesOffset++] = (block2 ushr 38) and 127L
            values[valuesOffset++] = (block2 ushr 31) and 127L
            values[valuesOffset++] = (block2 ushr 24) and 127L
            values[valuesOffset++] = (block2 ushr 17) and 127L
            values[valuesOffset++] = (block2 ushr 10) and 127L
            values[valuesOffset++] = (block2 ushr 3) and 127L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 7L) shl 4) or (block3 ushr 60)
            values[valuesOffset++] = (block3 ushr 53) and 127L
            values[valuesOffset++] = (block3 ushr 46) and 127L
            values[valuesOffset++] = (block3 ushr 39) and 127L
            values[valuesOffset++] = (block3 ushr 32) and 127L
            values[valuesOffset++] = (block3 ushr 25) and 127L
            values[valuesOffset++] = (block3 ushr 18) and 127L
            values[valuesOffset++] = (block3 ushr 11) and 127L
            values[valuesOffset++] = (block3 ushr 4) and 127L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 15L) shl 3) or (block4 ushr 61)
            values[valuesOffset++] = (block4 ushr 54) and 127L
            values[valuesOffset++] = (block4 ushr 47) and 127L
            values[valuesOffset++] = (block4 ushr 40) and 127L
            values[valuesOffset++] = (block4 ushr 33) and 127L
            values[valuesOffset++] = (block4 ushr 26) and 127L
            values[valuesOffset++] = (block4 ushr 19) and 127L
            values[valuesOffset++] = (block4 ushr 12) and 127L
            values[valuesOffset++] = (block4 ushr 5) and 127L
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block4 and 31L) shl 2) or (block5 ushr 62)
            values[valuesOffset++] = (block5 ushr 55) and 127L
            values[valuesOffset++] = (block5 ushr 48) and 127L
            values[valuesOffset++] = (block5 ushr 41) and 127L
            values[valuesOffset++] = (block5 ushr 34) and 127L
            values[valuesOffset++] = (block5 ushr 27) and 127L
            values[valuesOffset++] = (block5 ushr 20) and 127L
            values[valuesOffset++] = (block5 ushr 13) and 127L
            values[valuesOffset++] = (block5 ushr 6) and 127L
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block5 and 63L) shl 1) or (block6 ushr 63)
            values[valuesOffset++] = (block6 ushr 56) and 127L
            values[valuesOffset++] = (block6 ushr 49) and 127L
            values[valuesOffset++] = (block6 ushr 42) and 127L
            values[valuesOffset++] = (block6 ushr 35) and 127L
            values[valuesOffset++] = (block6 ushr 28) and 127L
            values[valuesOffset++] = (block6 ushr 21) and 127L
            values[valuesOffset++] = (block6 ushr 14) and 127L
            values[valuesOffset++] = (block6 ushr 7) and 127L
            values[valuesOffset++] = block6 and 127L
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val byte0 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = byte0 ushr 1
            val byte1 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte0 and 1L) shl 6) or (byte1 ushr 2)
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte1 and 3L) shl 5) or (byte2 ushr 3)
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte2 and 7L) shl 4) or (byte3 ushr 4)
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte3 and 15L) shl 3) or (byte4 ushr 5)
            val byte5 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte4 and 31L) shl 2) or (byte5 ushr 6)
            val byte6 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte5 and 63L) shl 1) or (byte6 ushr 7)
            values[valuesOffset++] = byte6 and 127L
        }
    }
}
