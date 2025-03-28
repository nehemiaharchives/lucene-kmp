package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked9 : BulkOperationPacked(9) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 55).toInt()
            values[valuesOffset++] = ((block0 ushr 46) and 511L).toInt()
            values[valuesOffset++] = ((block0 ushr 37) and 511L).toInt()
            values[valuesOffset++] = ((block0 ushr 28) and 511L).toInt()
            values[valuesOffset++] = ((block0 ushr 19) and 511L).toInt()
            values[valuesOffset++] = ((block0 ushr 10) and 511L).toInt()
            values[valuesOffset++] = ((block0 ushr 1) and 511L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 1L) shl 8) or (block1 ushr 56)).toInt()
            values[valuesOffset++] = ((block1 ushr 47) and 511L).toInt()
            values[valuesOffset++] = ((block1 ushr 38) and 511L).toInt()
            values[valuesOffset++] = ((block1 ushr 29) and 511L).toInt()
            values[valuesOffset++] = ((block1 ushr 20) and 511L).toInt()
            values[valuesOffset++] = ((block1 ushr 11) and 511L).toInt()
            values[valuesOffset++] = ((block1 ushr 2) and 511L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 3L) shl 7) or (block2 ushr 57)).toInt()
            values[valuesOffset++] = ((block2 ushr 48) and 511L).toInt()
            values[valuesOffset++] = ((block2 ushr 39) and 511L).toInt()
            values[valuesOffset++] = ((block2 ushr 30) and 511L).toInt()
            values[valuesOffset++] = ((block2 ushr 21) and 511L).toInt()
            values[valuesOffset++] = ((block2 ushr 12) and 511L).toInt()
            values[valuesOffset++] = ((block2 ushr 3) and 511L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 7L) shl 6) or (block3 ushr 58)).toInt()
            values[valuesOffset++] = ((block3 ushr 49) and 511L).toInt()
            values[valuesOffset++] = ((block3 ushr 40) and 511L).toInt()
            values[valuesOffset++] = ((block3 ushr 31) and 511L).toInt()
            values[valuesOffset++] = ((block3 ushr 22) and 511L).toInt()
            values[valuesOffset++] = ((block3 ushr 13) and 511L).toInt()
            values[valuesOffset++] = ((block3 ushr 4) and 511L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 15L) shl 5) or (block4 ushr 59)).toInt()
            values[valuesOffset++] = ((block4 ushr 50) and 511L).toInt()
            values[valuesOffset++] = ((block4 ushr 41) and 511L).toInt()
            values[valuesOffset++] = ((block4 ushr 32) and 511L).toInt()
            values[valuesOffset++] = ((block4 ushr 23) and 511L).toInt()
            values[valuesOffset++] = ((block4 ushr 14) and 511L).toInt()
            values[valuesOffset++] = ((block4 ushr 5) and 511L).toInt()
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block4 and 31L) shl 4) or (block5 ushr 60)).toInt()
            values[valuesOffset++] = ((block5 ushr 51) and 511L).toInt()
            values[valuesOffset++] = ((block5 ushr 42) and 511L).toInt()
            values[valuesOffset++] = ((block5 ushr 33) and 511L).toInt()
            values[valuesOffset++] = ((block5 ushr 24) and 511L).toInt()
            values[valuesOffset++] = ((block5 ushr 15) and 511L).toInt()
            values[valuesOffset++] = ((block5 ushr 6) and 511L).toInt()
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block5 and 63L) shl 3) or (block6 ushr 61)).toInt()
            values[valuesOffset++] = ((block6 ushr 52) and 511L).toInt()
            values[valuesOffset++] = ((block6 ushr 43) and 511L).toInt()
            values[valuesOffset++] = ((block6 ushr 34) and 511L).toInt()
            values[valuesOffset++] = ((block6 ushr 25) and 511L).toInt()
            values[valuesOffset++] = ((block6 ushr 16) and 511L).toInt()
            values[valuesOffset++] = ((block6 ushr 7) and 511L).toInt()
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block6 and 127L) shl 2) or (block7 ushr 62)).toInt()
            values[valuesOffset++] = ((block7 ushr 53) and 511L).toInt()
            values[valuesOffset++] = ((block7 ushr 44) and 511L).toInt()
            values[valuesOffset++] = ((block7 ushr 35) and 511L).toInt()
            values[valuesOffset++] = ((block7 ushr 26) and 511L).toInt()
            values[valuesOffset++] = ((block7 ushr 17) and 511L).toInt()
            values[valuesOffset++] = ((block7 ushr 8) and 511L).toInt()
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block7 and 255L) shl 1) or (block8 ushr 63)).toInt()
            values[valuesOffset++] = ((block8 ushr 54) and 511L).toInt()
            values[valuesOffset++] = ((block8 ushr 45) and 511L).toInt()
            values[valuesOffset++] = ((block8 ushr 36) and 511L).toInt()
            values[valuesOffset++] = ((block8 ushr 27) and 511L).toInt()
            values[valuesOffset++] = ((block8 ushr 18) and 511L).toInt()
            values[valuesOffset++] = ((block8 ushr 9) and 511L).toInt()
            values[valuesOffset++] = (block8 and 511L).toInt()
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
            values[valuesOffset++] = (byte0 shl 1) or (byte1 ushr 7)
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte1 and 127) shl 2) or (byte2 ushr 6)
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte2 and 63) shl 3) or (byte3 ushr 5)
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte3 and 31) shl 4) or (byte4 ushr 4)
            val byte5 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte4 and 15) shl 5) or (byte5 ushr 3)
            val byte6 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte5 and 7) shl 6) or (byte6 ushr 2)
            val byte7 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte6 and 3) shl 7) or (byte7 ushr 1)
            val byte8 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte7 and 1) shl 8) or byte8
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 55
            values[valuesOffset++] = (block0 ushr 46) and 511L
            values[valuesOffset++] = (block0 ushr 37) and 511L
            values[valuesOffset++] = (block0 ushr 28) and 511L
            values[valuesOffset++] = (block0 ushr 19) and 511L
            values[valuesOffset++] = (block0 ushr 10) and 511L
            values[valuesOffset++] = (block0 ushr 1) and 511L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 1L) shl 8) or (block1 ushr 56)
            values[valuesOffset++] = (block1 ushr 47) and 511L
            values[valuesOffset++] = (block1 ushr 38) and 511L
            values[valuesOffset++] = (block1 ushr 29) and 511L
            values[valuesOffset++] = (block1 ushr 20) and 511L
            values[valuesOffset++] = (block1 ushr 11) and 511L
            values[valuesOffset++] = (block1 ushr 2) and 511L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 3L) shl 7) or (block2 ushr 57)
            values[valuesOffset++] = (block2 ushr 48) and 511L
            values[valuesOffset++] = (block2 ushr 39) and 511L
            values[valuesOffset++] = (block2 ushr 30) and 511L
            values[valuesOffset++] = (block2 ushr 21) and 511L
            values[valuesOffset++] = (block2 ushr 12) and 511L
            values[valuesOffset++] = (block2 ushr 3) and 511L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 7L) shl 6) or (block3 ushr 58)
            values[valuesOffset++] = (block3 ushr 49) and 511L
            values[valuesOffset++] = (block3 ushr 40) and 511L
            values[valuesOffset++] = (block3 ushr 31) and 511L
            values[valuesOffset++] = (block3 ushr 22) and 511L
            values[valuesOffset++] = (block3 ushr 13) and 511L
            values[valuesOffset++] = (block3 ushr 4) and 511L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 15L) shl 5) or (block4 ushr 59)
            values[valuesOffset++] = (block4 ushr 50) and 511L
            values[valuesOffset++] = (block4 ushr 41) and 511L
            values[valuesOffset++] = (block4 ushr 32) and 511L
            values[valuesOffset++] = (block4 ushr 23) and 511L
            values[valuesOffset++] = (block4 ushr 14) and 511L
            values[valuesOffset++] = (block4 ushr 5) and 511L
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block4 and 31L) shl 4) or (block5 ushr 60)
            values[valuesOffset++] = (block5 ushr 51) and 511L
            values[valuesOffset++] = (block5 ushr 42) and 511L
            values[valuesOffset++] = (block5 ushr 33) and 511L
            values[valuesOffset++] = (block5 ushr 24) and 511L
            values[valuesOffset++] = (block5 ushr 15) and 511L
            values[valuesOffset++] = (block5 ushr 6) and 511L
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block5 and 63L) shl 3) or (block6 ushr 61)
            values[valuesOffset++] = (block6 ushr 52) and 511L
            values[valuesOffset++] = (block6 ushr 43) and 511L
            values[valuesOffset++] = (block6 ushr 34) and 511L
            values[valuesOffset++] = (block6 ushr 25) and 511L
            values[valuesOffset++] = (block6 ushr 16) and 511L
            values[valuesOffset++] = (block6 ushr 7) and 511L
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block6 and 127L) shl 2) or (block7 ushr 62)
            values[valuesOffset++] = (block7 ushr 53) and 511L
            values[valuesOffset++] = (block7 ushr 44) and 511L
            values[valuesOffset++] = (block7 ushr 35) and 511L
            values[valuesOffset++] = (block7 ushr 26) and 511L
            values[valuesOffset++] = (block7 ushr 17) and 511L
            values[valuesOffset++] = (block7 ushr 8) and 511L
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block7 and 255L) shl 1) or (block8 ushr 63)
            values[valuesOffset++] = (block8 ushr 54) and 511L
            values[valuesOffset++] = (block8 ushr 45) and 511L
            values[valuesOffset++] = (block8 ushr 36) and 511L
            values[valuesOffset++] = (block8 ushr 27) and 511L
            values[valuesOffset++] = (block8 ushr 18) and 511L
            values[valuesOffset++] = (block8 ushr 9) and 511L
            values[valuesOffset++] = block8 and 511L
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
            values[valuesOffset++] = (byte0 shl 1) or (byte1 ushr 7)
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte1 and 127L) shl 2) or (byte2 ushr 6)
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte2 and 63L) shl 3) or (byte3 ushr 5)
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte3 and 31L) shl 4) or (byte4 ushr 4)
            val byte5 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte4 and 15L) shl 5) or (byte5 ushr 3)
            val byte6 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte5 and 7L) shl 6) or (byte6 ushr 2)
            val byte7 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte6 and 3L) shl 7) or (byte7 ushr 1)
            val byte8 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte7 and 1L) shl 8) or byte8
        }
    }
}
