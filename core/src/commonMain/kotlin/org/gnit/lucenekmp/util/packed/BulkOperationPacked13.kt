package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked13 : BulkOperationPacked(13) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 51).toInt()
            values[valuesOffset++] = ((block0 ushr 38) and 8191L).toInt()
            values[valuesOffset++] = ((block0 ushr 25) and 8191L).toInt()
            values[valuesOffset++] = ((block0 ushr 12) and 8191L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 4095L) shl 1) or (block1 ushr 63)).toInt()
            values[valuesOffset++] = ((block1 ushr 50) and 8191L).toInt()
            values[valuesOffset++] = ((block1 ushr 37) and 8191L).toInt()
            values[valuesOffset++] = ((block1 ushr 24) and 8191L).toInt()
            values[valuesOffset++] = ((block1 ushr 11) and 8191L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 2047L) shl 2) or (block2 ushr 62)).toInt()
            values[valuesOffset++] = ((block2 ushr 49) and 8191L).toInt()
            values[valuesOffset++] = ((block2 ushr 36) and 8191L).toInt()
            values[valuesOffset++] = ((block2 ushr 23) and 8191L).toInt()
            values[valuesOffset++] = ((block2 ushr 10) and 8191L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 1023L) shl 3) or (block3 ushr 61)).toInt()
            values[valuesOffset++] = ((block3 ushr 48) and 8191L).toInt()
            values[valuesOffset++] = ((block3 ushr 35) and 8191L).toInt()
            values[valuesOffset++] = ((block3 ushr 22) and 8191L).toInt()
            values[valuesOffset++] = ((block3 ushr 9) and 8191L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 511L) shl 4) or (block4 ushr 60)).toInt()
            values[valuesOffset++] = ((block4 ushr 47) and 8191L).toInt()
            values[valuesOffset++] = ((block4 ushr 34) and 8191L).toInt()
            values[valuesOffset++] = ((block4 ushr 21) and 8191L).toInt()
            values[valuesOffset++] = ((block4 ushr 8) and 8191L).toInt()
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block4 and 255L) shl 5) or (block5 ushr 59)).toInt()
            values[valuesOffset++] = ((block5 ushr 46) and 8191L).toInt()
            values[valuesOffset++] = ((block5 ushr 33) and 8191L).toInt()
            values[valuesOffset++] = ((block5 ushr 20) and 8191L).toInt()
            values[valuesOffset++] = ((block5 ushr 7) and 8191L).toInt()
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block5 and 127L) shl 6) or (block6 ushr 58)).toInt()
            values[valuesOffset++] = ((block6 ushr 45) and 8191L).toInt()
            values[valuesOffset++] = ((block6 ushr 32) and 8191L).toInt()
            values[valuesOffset++] = ((block6 ushr 19) and 8191L).toInt()
            values[valuesOffset++] = ((block6 ushr 6) and 8191L).toInt()
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block6 and 63L) shl 7) or (block7 ushr 57)).toInt()
            values[valuesOffset++] = ((block7 ushr 44) and 8191L).toInt()
            values[valuesOffset++] = ((block7 ushr 31) and 8191L).toInt()
            values[valuesOffset++] = ((block7 ushr 18) and 8191L).toInt()
            values[valuesOffset++] = ((block7 ushr 5) and 8191L).toInt()
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block7 and 31L) shl 8) or (block8 ushr 56)).toInt()
            values[valuesOffset++] = ((block8 ushr 43) and 8191L).toInt()
            values[valuesOffset++] = ((block8 ushr 30) and 8191L).toInt()
            values[valuesOffset++] = ((block8 ushr 17) and 8191L).toInt()
            values[valuesOffset++] = ((block8 ushr 4) and 8191L).toInt()
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block8 and 15L) shl 9) or (block9 ushr 55)).toInt()
            values[valuesOffset++] = ((block9 ushr 42) and 8191L).toInt()
            values[valuesOffset++] = ((block9 ushr 29) and 8191L).toInt()
            values[valuesOffset++] = ((block9 ushr 16) and 8191L).toInt()
            values[valuesOffset++] = ((block9 ushr 3) and 8191L).toInt()
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block9 and 7L) shl 10) or (block10 ushr 54)).toInt()
            values[valuesOffset++] = ((block10 ushr 41) and 8191L).toInt()
            values[valuesOffset++] = ((block10 ushr 28) and 8191L).toInt()
            values[valuesOffset++] = ((block10 ushr 15) and 8191L).toInt()
            values[valuesOffset++] = ((block10 ushr 2) and 8191L).toInt()
            val block11 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block10 and 3L) shl 11) or (block11 ushr 53)).toInt()
            values[valuesOffset++] = ((block11 ushr 40) and 8191L).toInt()
            values[valuesOffset++] = ((block11 ushr 27) and 8191L).toInt()
            values[valuesOffset++] = ((block11 ushr 14) and 8191L).toInt()
            values[valuesOffset++] = ((block11 ushr 1) and 8191L).toInt()
            val block12 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block11 and 1L) shl 12) or (block12 ushr 52)).toInt()
            values[valuesOffset++] = ((block12 ushr 39) and 8191L).toInt()
            values[valuesOffset++] = ((block12 ushr 26) and 8191L).toInt()
            values[valuesOffset++] = ((block12 ushr 13) and 8191L).toInt()
            values[valuesOffset++] = (block12 and 8191L).toInt()
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
            values[valuesOffset++] = (byte0 shl 5) or (byte1 ushr 3)
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte1 and 7) shl 10) or (byte2 shl 2) or (byte3 ushr 6)
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte3 and 63) shl 7) or (byte4 ushr 1)
            val byte5 = blocks[blocksOffset++].toInt() and 0xFF
            val byte6 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte4 and 1) shl 12) or (byte5 shl 4) or (byte6 ushr 4)
            val byte7 = blocks[blocksOffset++].toInt() and 0xFF
            val byte8 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte6 and 15) shl 9) or (byte7 shl 1) or (byte8 ushr 7)
            val byte9 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte8 and 127) shl 6) or (byte9 ushr 2)
            val byte10 = blocks[blocksOffset++].toInt() and 0xFF
            val byte11 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte9 and 3) shl 11) or (byte10 shl 3) or (byte11 ushr 5)
            val byte12 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte11 and 31) shl 8) or byte12
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 51
            values[valuesOffset++] = (block0 ushr 38) and 8191L
            values[valuesOffset++] = (block0 ushr 25) and 8191L
            values[valuesOffset++] = (block0 ushr 12) and 8191L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 4095L) shl 1) or (block1 ushr 63)
            values[valuesOffset++] = (block1 ushr 50) and 8191L
            values[valuesOffset++] = (block1 ushr 37) and 8191L
            values[valuesOffset++] = (block1 ushr 24) and 8191L
            values[valuesOffset++] = (block1 ushr 11) and 8191L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 2047L) shl 2) or (block2 ushr 62)
            values[valuesOffset++] = (block2 ushr 49) and 8191L
            values[valuesOffset++] = (block2 ushr 36) and 8191L
            values[valuesOffset++] = (block2 ushr 23) and 8191L
            values[valuesOffset++] = (block2 ushr 10) and 8191L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 1023L) shl 3) or (block3 ushr 61)
            values[valuesOffset++] = (block3 ushr 48) and 8191L
            values[valuesOffset++] = (block3 ushr 35) and 8191L
            values[valuesOffset++] = (block3 ushr 22) and 8191L
            values[valuesOffset++] = (block3 ushr 9) and 8191L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 511L) shl 4) or (block4 ushr 60)
            values[valuesOffset++] = (block4 ushr 47) and 8191L
            values[valuesOffset++] = (block4 ushr 34) and 8191L
            values[valuesOffset++] = (block4 ushr 21) and 8191L
            values[valuesOffset++] = (block4 ushr 8) and 8191L
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block4 and 255L) shl 5) or (block5 ushr 59)
            values[valuesOffset++] = (block5 ushr 46) and 8191L
            values[valuesOffset++] = (block5 ushr 33) and 8191L
            values[valuesOffset++] = (block5 ushr 20) and 8191L
            values[valuesOffset++] = (block5 ushr 7) and 8191L
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block5 and 127L) shl 6) or (block6 ushr 58)
            values[valuesOffset++] = (block6 ushr 45) and 8191L
            values[valuesOffset++] = (block6 ushr 32) and 8191L
            values[valuesOffset++] = (block6 ushr 19) and 8191L
            values[valuesOffset++] = (block6 ushr 6) and 8191L
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block6 and 63L) shl 7) or (block7 ushr 57)
            values[valuesOffset++] = (block7 ushr 44) and 8191L
            values[valuesOffset++] = (block7 ushr 31) and 8191L
            values[valuesOffset++] = (block7 ushr 18) and 8191L
            values[valuesOffset++] = (block7 ushr 5) and 8191L
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block7 and 31L) shl 8) or (block8 ushr 56)
            values[valuesOffset++] = (block8 ushr 43) and 8191L
            values[valuesOffset++] = (block8 ushr 30) and 8191L
            values[valuesOffset++] = (block8 ushr 17) and 8191L
            values[valuesOffset++] = (block8 ushr 4) and 8191L
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block8 and 15L) shl 9) or (block9 ushr 55)
            values[valuesOffset++] = (block9 ushr 42) and 8191L
            values[valuesOffset++] = (block9 ushr 29) and 8191L
            values[valuesOffset++] = (block9 ushr 16) and 8191L
            values[valuesOffset++] = (block9 ushr 3) and 8191L
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block9 and 7L) shl 10) or (block10 ushr 54)
            values[valuesOffset++] = (block10 ushr 41) and 8191L
            values[valuesOffset++] = (block10 ushr 28) and 8191L
            values[valuesOffset++] = (block10 ushr 15) and 8191L
            values[valuesOffset++] = (block10 ushr 2) and 8191L
            val block11 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block10 and 3L) shl 11) or (block11 ushr 53)
            values[valuesOffset++] = (block11 ushr 40) and 8191L
            values[valuesOffset++] = (block11 ushr 27) and 8191L
            values[valuesOffset++] = (block11 ushr 14) and 8191L
            values[valuesOffset++] = (block11 ushr 1) and 8191L
            val block12 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block11 and 1L) shl 12) or (block12 ushr 52)
            values[valuesOffset++] = (block12 ushr 39) and 8191L
            values[valuesOffset++] = (block12 ushr 26) and 8191L
            values[valuesOffset++] = (block12 ushr 13) and 8191L
            values[valuesOffset++] = block12 and 8191L
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
            values[valuesOffset++] = (byte0 shl 5) or (byte1 ushr 3)
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte1 and 7L) shl 10) or (byte2 shl 2) or (byte3 ushr 6)
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte3 and 63L) shl 7) or (byte4 ushr 1)
            val byte5 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte6 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte4 and 1L) shl 12) or (byte5 shl 4) or (byte6 ushr 4)
            val byte7 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte8 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte6 and 15L) shl 9) or (byte7 shl 1) or (byte8 ushr 7)
            val byte9 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte8 and 127L) shl 6) or (byte9 ushr 2)
            val byte10 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte11 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte9 and 3L) shl 11) or (byte10 shl 3) or (byte11 ushr 5)
            val byte12 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte11 and 31L) shl 8) or byte12
        }
    }
}
