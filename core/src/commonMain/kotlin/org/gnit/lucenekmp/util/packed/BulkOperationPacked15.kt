package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked15 : BulkOperationPacked(15) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 49).toInt()
            values[valuesOffset++] = ((block0 ushr 34) and 32767L).toInt()
            values[valuesOffset++] = ((block0 ushr 19) and 32767L).toInt()
            values[valuesOffset++] = ((block0 ushr 4) and 32767L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 15L) shl 11) or (block1 ushr 53)).toInt()
            values[valuesOffset++] = ((block1 ushr 38) and 32767L).toInt()
            values[valuesOffset++] = ((block1 ushr 23) and 32767L).toInt()
            values[valuesOffset++] = ((block1 ushr 8) and 32767L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 255L) shl 7) or (block2 ushr 57)).toInt()
            values[valuesOffset++] = ((block2 ushr 42) and 32767L).toInt()
            values[valuesOffset++] = ((block2 ushr 27) and 32767L).toInt()
            values[valuesOffset++] = ((block2 ushr 12) and 32767L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 4095L) shl 3) or (block3 ushr 61)).toInt()
            values[valuesOffset++] = ((block3 ushr 46) and 32767L).toInt()
            values[valuesOffset++] = ((block3 ushr 31) and 32767L).toInt()
            values[valuesOffset++] = ((block3 ushr 16) and 32767L).toInt()
            values[valuesOffset++] = ((block3 ushr 1) and 32767L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 1L) shl 14) or (block4 ushr 50)).toInt()
            values[valuesOffset++] = ((block4 ushr 35) and 32767L).toInt()
            values[valuesOffset++] = ((block4 ushr 20) and 32767L).toInt()
            values[valuesOffset++] = ((block4 ushr 5) and 32767L).toInt()
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block4 and 31L) shl 10) or (block5 ushr 54)).toInt()
            values[valuesOffset++] = ((block5 ushr 39) and 32767L).toInt()
            values[valuesOffset++] = ((block5 ushr 24) and 32767L).toInt()
            values[valuesOffset++] = ((block5 ushr 9) and 32767L).toInt()
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block5 and 511L) shl 6) or (block6 ushr 58)).toInt()
            values[valuesOffset++] = ((block6 ushr 43) and 32767L).toInt()
            values[valuesOffset++] = ((block6 ushr 28) and 32767L).toInt()
            values[valuesOffset++] = ((block6 ushr 13) and 32767L).toInt()
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block6 and 8191L) shl 2) or (block7 ushr 62)).toInt()
            values[valuesOffset++] = ((block7 ushr 47) and 32767L).toInt()
            values[valuesOffset++] = ((block7 ushr 32) and 32767L).toInt()
            values[valuesOffset++] = ((block7 ushr 17) and 32767L).toInt()
            values[valuesOffset++] = ((block7 ushr 2) and 32767L).toInt()
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block7 and 3L) shl 13) or (block8 ushr 51)).toInt()
            values[valuesOffset++] = ((block8 ushr 36) and 32767L).toInt()
            values[valuesOffset++] = ((block8 ushr 21) and 32767L).toInt()
            values[valuesOffset++] = ((block8 ushr 6) and 32767L).toInt()
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block8 and 63L) shl 9) or (block9 ushr 55)).toInt()
            values[valuesOffset++] = ((block9 ushr 40) and 32767L).toInt()
            values[valuesOffset++] = ((block9 ushr 25) and 32767L).toInt()
            values[valuesOffset++] = ((block9 ushr 10) and 32767L).toInt()
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block9 and 1023L) shl 5) or (block10 ushr 59)).toInt()
            values[valuesOffset++] = ((block10 ushr 44) and 32767L).toInt()
            values[valuesOffset++] = ((block10 ushr 29) and 32767L).toInt()
            values[valuesOffset++] = ((block10 ushr 14) and 32767L).toInt()
            val block11 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block10 and 16383L) shl 1) or (block11 ushr 63)).toInt()
            values[valuesOffset++] = ((block11 ushr 48) and 32767L).toInt()
            values[valuesOffset++] = ((block11 ushr 33) and 32767L).toInt()
            values[valuesOffset++] = ((block11 ushr 18) and 32767L).toInt()
            values[valuesOffset++] = ((block11 ushr 3) and 32767L).toInt()
            val block12 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block11 and 7L) shl 12) or (block12 ushr 52)).toInt()
            values[valuesOffset++] = ((block12 ushr 37) and 32767L).toInt()
            values[valuesOffset++] = ((block12 ushr 22) and 32767L).toInt()
            values[valuesOffset++] = ((block12 ushr 7) and 32767L).toInt()
            val block13 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block12 and 127L) shl 8) or (block13 ushr 56)).toInt()
            values[valuesOffset++] = ((block13 ushr 41) and 32767L).toInt()
            values[valuesOffset++] = ((block13 ushr 26) and 32767L).toInt()
            values[valuesOffset++] = ((block13 ushr 11) and 32767L).toInt()
            val block14 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block13 and 2047L) shl 4) or (block14 ushr 60)).toInt()
            values[valuesOffset++] = ((block14 ushr 45) and 32767L).toInt()
            values[valuesOffset++] = ((block14 ushr 30) and 32767L).toInt()
            values[valuesOffset++] = ((block14 ushr 15) and 32767L).toInt()
            values[valuesOffset++] = (block14 and 32767L).toInt()
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
            values[valuesOffset++] = (byte0 shl 7) or (byte1 ushr 1)
            val byte2 = blocks[blocksOffset++].toInt() and 0xFF
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte1 and 1) shl 14) or (byte2 shl 6) or (byte3 ushr 2)
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            val byte5 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte3 and 3) shl 13) or (byte4 shl 5) or (byte5 ushr 3)
            val byte6 = blocks[blocksOffset++].toInt() and 0xFF
            val byte7 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte5 and 7) shl 12) or (byte6 shl 4) or (byte7 ushr 4)
            val byte8 = blocks[blocksOffset++].toInt() and 0xFF
            val byte9 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte7 and 15) shl 11) or (byte8 shl 3) or (byte9 ushr 5)
            val byte10 = blocks[blocksOffset++].toInt() and 0xFF
            val byte11 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte9 and 31) shl 10) or (byte10 shl 2) or (byte11 ushr 6)
            val byte12 = blocks[blocksOffset++].toInt() and 0xFF
            val byte13 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte11 and 63) shl 9) or (byte12 shl 1) or (byte13 ushr 7)
            val byte14 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte13 and 127) shl 8) or byte14
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 49
            values[valuesOffset++] = (block0 ushr 34) and 32767L
            values[valuesOffset++] = (block0 ushr 19) and 32767L
            values[valuesOffset++] = (block0 ushr 4) and 32767L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 15L) shl 11) or (block1 ushr 53)
            values[valuesOffset++] = (block1 ushr 38) and 32767L
            values[valuesOffset++] = (block1 ushr 23) and 32767L
            values[valuesOffset++] = (block1 ushr 8) and 32767L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 255L) shl 7) or (block2 ushr 57)
            values[valuesOffset++] = (block2 ushr 42) and 32767L
            values[valuesOffset++] = (block2 ushr 27) and 32767L
            values[valuesOffset++] = (block2 ushr 12) and 32767L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 4095L) shl 3) or (block3 ushr 61)
            values[valuesOffset++] = (block3 ushr 46) and 32767L
            values[valuesOffset++] = (block3 ushr 31) and 32767L
            values[valuesOffset++] = (block3 ushr 16) and 32767L
            values[valuesOffset++] = (block3 ushr 1) and 32767L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 1L) shl 14) or (block4 ushr 50)
            values[valuesOffset++] = (block4 ushr 35) and 32767L
            values[valuesOffset++] = (block4 ushr 20) and 32767L
            values[valuesOffset++] = (block4 ushr 5) and 32767L
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block4 and 31L) shl 10) or (block5 ushr 54)
            values[valuesOffset++] = (block5 ushr 39) and 32767L
            values[valuesOffset++] = (block5 ushr 24) and 32767L
            values[valuesOffset++] = (block5 ushr 9) and 32767L
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block5 and 511L) shl 6) or (block6 ushr 58)
            values[valuesOffset++] = (block6 ushr 43) and 32767L
            values[valuesOffset++] = (block6 ushr 28) and 32767L
            values[valuesOffset++] = (block6 ushr 13) and 32767L
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block6 and 8191L) shl 2) or (block7 ushr 62)
            values[valuesOffset++] = (block7 ushr 47) and 32767L
            values[valuesOffset++] = (block7 ushr 32) and 32767L
            values[valuesOffset++] = (block7 ushr 17) and 32767L
            values[valuesOffset++] = (block7 ushr 2) and 32767L
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block7 and 3L) shl 13) or (block8 ushr 51)
            values[valuesOffset++] = (block8 ushr 36) and 32767L
            values[valuesOffset++] = (block8 ushr 21) and 32767L
            values[valuesOffset++] = (block8 ushr 6) and 32767L
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block8 and 63L) shl 9) or (block9 ushr 55)
            values[valuesOffset++] = (block9 ushr 40) and 32767L
            values[valuesOffset++] = (block9 ushr 25) and 32767L
            values[valuesOffset++] = (block9 ushr 10) and 32767L
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block9 and 1023L) shl 5) or (block10 ushr 59)
            values[valuesOffset++] = (block10 ushr 44) and 32767L
            values[valuesOffset++] = (block10 ushr 29) and 32767L
            values[valuesOffset++] = (block10 ushr 14) and 32767L
            val block11 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block10 and 16383L) shl 1) or (block11 ushr 63)
            values[valuesOffset++] = (block11 ushr 48) and 32767L
            values[valuesOffset++] = (block11 ushr 33) and 32767L
            values[valuesOffset++] = (block11 ushr 18) and 32767L
            values[valuesOffset++] = (block11 ushr 3) and 32767L
            val block12 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block11 and 7L) shl 12) or (block12 ushr 52)
            values[valuesOffset++] = (block12 ushr 37) and 32767L
            values[valuesOffset++] = (block12 ushr 22) and 32767L
            values[valuesOffset++] = (block12 ushr 7) and 32767L
            val block13 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block12 and 127L) shl 8) or (block13 ushr 56)
            values[valuesOffset++] = (block13 ushr 41) and 32767L
            values[valuesOffset++] = (block13 ushr 26) and 32767L
            values[valuesOffset++] = (block13 ushr 11) and 32767L
            val block14 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block13 and 2047L) shl 4) or (block14 ushr 60)
            values[valuesOffset++] = (block14 ushr 45) and 32767L
            values[valuesOffset++] = (block14 ushr 30) and 32767L
            values[valuesOffset++] = (block14 ushr 15) and 32767L
            values[valuesOffset++] = block14 and 32767L
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
            values[valuesOffset++] = (byte0 shl 7) or (byte1 ushr 1)
            val byte2 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte1 and 1L) shl 14) or (byte2 shl 6) or (byte3 ushr 2)
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte5 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte3 and 3L) shl 13) or (byte4 shl 5) or (byte5 ushr 3)
            val byte6 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte7 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte5 and 7L) shl 12) or (byte6 shl 4) or (byte7 ushr 4)
            val byte8 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte9 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte7 and 15L) shl 11) or (byte8 shl 3) or (byte9 ushr 5)
            val byte10 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte11 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte9 and 31L) shl 10) or (byte10 shl 2) or (byte11 ushr 6)
            val byte12 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte13 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte11 and 63L) shl 9) or (byte12 shl 1) or (byte13 ushr 7)
            val byte14 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte13 and 127L) shl 8) or byte14
        }
    }
}
