package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked19 : BulkOperationPacked(19) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 45).toInt()
            values[valuesOffset++] = ((block0 ushr 26) and 524287L).toInt()
            values[valuesOffset++] = ((block0 ushr 7) and 524287L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 127L) shl 12) or (block1 ushr 52)).toInt()
            values[valuesOffset++] = ((block1 ushr 33) and 524287L).toInt()
            values[valuesOffset++] = ((block1 ushr 14) and 524287L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 16383L) shl 5) or (block2 ushr 59)).toInt()
            values[valuesOffset++] = ((block2 ushr 40) and 524287L).toInt()
            values[valuesOffset++] = ((block2 ushr 21) and 524287L).toInt()
            values[valuesOffset++] = ((block2 ushr 2) and 524287L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 3L) shl 17) or (block3 ushr 47)).toInt()
            values[valuesOffset++] = ((block3 ushr 28) and 524287L).toInt()
            values[valuesOffset++] = ((block3 ushr 9) and 524287L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 511L) shl 10) or (block4 ushr 54)).toInt()
            values[valuesOffset++] = ((block4 ushr 35) and 524287L).toInt()
            values[valuesOffset++] = ((block4 ushr 16) and 524287L).toInt()
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block4 and 65535L) shl 3) or (block5 ushr 61)).toInt()
            values[valuesOffset++] = ((block5 ushr 42) and 524287L).toInt()
            values[valuesOffset++] = ((block5 ushr 23) and 524287L).toInt()
            values[valuesOffset++] = ((block5 ushr 4) and 524287L).toInt()
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block5 and 15L) shl 15) or (block6 ushr 49)).toInt()
            values[valuesOffset++] = ((block6 ushr 30) and 524287L).toInt()
            values[valuesOffset++] = ((block6 ushr 11) and 524287L).toInt()
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block6 and 2047L) shl 8) or (block7 ushr 56)).toInt()
            values[valuesOffset++] = ((block7 ushr 37) and 524287L).toInt()
            values[valuesOffset++] = ((block7 ushr 18) and 524287L).toInt()
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block7 and 262143L) shl 1) or (block8 ushr 63)).toInt()
            values[valuesOffset++] = ((block8 ushr 44) and 524287L).toInt()
            values[valuesOffset++] = ((block8 ushr 25) and 524287L).toInt()
            values[valuesOffset++] = ((block8 ushr 6) and 524287L).toInt()
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block8 and 63L) shl 13) or (block9 ushr 51)).toInt()
            values[valuesOffset++] = ((block9 ushr 32) and 524287L).toInt()
            values[valuesOffset++] = ((block9 ushr 13) and 524287L).toInt()
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block9 and 8191L) shl 6) or (block10 ushr 58)).toInt()
            values[valuesOffset++] = ((block10 ushr 39) and 524287L).toInt()
            values[valuesOffset++] = ((block10 ushr 20) and 524287L).toInt()
            values[valuesOffset++] = ((block10 ushr 1) and 524287L).toInt()
            val block11 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block10 and 1L) shl 18) or (block11 ushr 46)).toInt()
            values[valuesOffset++] = ((block11 ushr 27) and 524287L).toInt()
            values[valuesOffset++] = ((block11 ushr 8) and 524287L).toInt()
            val block12 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block11 and 255L) shl 11) or (block12 ushr 53)).toInt()
            values[valuesOffset++] = ((block12 ushr 34) and 524287L).toInt()
            values[valuesOffset++] = ((block12 ushr 15) and 524287L).toInt()
            val block13 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block12 and 32767L) shl 4) or (block13 ushr 60)).toInt()
            values[valuesOffset++] = ((block13 ushr 41) and 524287L).toInt()
            values[valuesOffset++] = ((block13 ushr 22) and 524287L).toInt()
            values[valuesOffset++] = ((block13 ushr 3) and 524287L).toInt()
            val block14 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block13 and 7L) shl 16) or (block14 ushr 48)).toInt()
            values[valuesOffset++] = ((block14 ushr 29) and 524287L).toInt()
            values[valuesOffset++] = ((block14 ushr 10) and 524287L).toInt()
            val block15 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block14 and 1023L) shl 9) or (block15 ushr 55)).toInt()
            values[valuesOffset++] = ((block15 ushr 36) and 524287L).toInt()
            values[valuesOffset++] = ((block15 ushr 17) and 524287L).toInt()
            val block16 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block15 and 131071L) shl 2) or (block16 ushr 62)).toInt()
            values[valuesOffset++] = ((block16 ushr 43) and 524287L).toInt()
            values[valuesOffset++] = ((block16 ushr 24) and 524287L).toInt()
            values[valuesOffset++] = ((block16 ushr 5) and 524287L).toInt()
            val block17 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block16 and 31L) shl 14) or (block17 ushr 50)).toInt()
            values[valuesOffset++] = ((block17 ushr 31) and 524287L).toInt()
            values[valuesOffset++] = ((block17 ushr 12) and 524287L).toInt()
            val block18 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block17 and 4095L) shl 7) or (block18 ushr 57)).toInt()
            values[valuesOffset++] = ((block18 ushr 38) and 524287L).toInt()
            values[valuesOffset++] = ((block18 ushr 19) and 524287L).toInt()
            values[valuesOffset++] = (block18 and 524287L).toInt()
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
            values[valuesOffset++] = (byte0 shl 11) or (byte1 shl 3) or (byte2 ushr 5)
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte2 and 31) shl 14) or (byte3 shl 6) or (byte4 ushr 2)
            val byte5 = blocks[blocksOffset++].toInt() and 0xFF
            val byte6 = blocks[blocksOffset++].toInt() and 0xFF
            val byte7 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte4 and 3) shl 17) or (byte5 shl 9) or (byte6 shl 1) or (byte7 ushr 7)
            val byte8 = blocks[blocksOffset++].toInt() and 0xFF
            val byte9 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte7 and 127) shl 12) or (byte8 shl 4) or (byte9 ushr 4)
            val byte10 = blocks[blocksOffset++].toInt() and 0xFF
            val byte11 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte9 and 15) shl 15) or (byte10 shl 7) or (byte11 ushr 1)
            val byte12 = blocks[blocksOffset++].toInt() and 0xFF
            val byte13 = blocks[blocksOffset++].toInt() and 0xFF
            val byte14 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] =
                ((byte11 and 1) shl 18) or (byte12 shl 10) or (byte13 shl 2) or (byte14 ushr 6)
            val byte15 = blocks[blocksOffset++].toInt() and 0xFF
            val byte16 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte14 and 63) shl 13) or (byte15 shl 5) or (byte16 ushr 3)
            val byte17 = blocks[blocksOffset++].toInt() and 0xFF
            val byte18 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte16 and 7) shl 16) or (byte17 shl 8) or byte18
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 45
            values[valuesOffset++] = (block0 ushr 26) and 524287L
            values[valuesOffset++] = (block0 ushr 7) and 524287L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 127L) shl 12) or (block1 ushr 52)
            values[valuesOffset++] = (block1 ushr 33) and 524287L
            values[valuesOffset++] = (block1 ushr 14) and 524287L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 16383L) shl 5) or (block2 ushr 59)
            values[valuesOffset++] = (block2 ushr 40) and 524287L
            values[valuesOffset++] = (block2 ushr 21) and 524287L
            values[valuesOffset++] = (block2 ushr 2) and 524287L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 3L) shl 17) or (block3 ushr 47)
            values[valuesOffset++] = (block3 ushr 28) and 524287L
            values[valuesOffset++] = (block3 ushr 9) and 524287L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 511L) shl 10) or (block4 ushr 54)
            values[valuesOffset++] = (block4 ushr 35) and 524287L
            values[valuesOffset++] = (block4 ushr 16) and 524287L
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block4 and 65535L) shl 3) or (block5 ushr 61)
            values[valuesOffset++] = (block5 ushr 42) and 524287L
            values[valuesOffset++] = (block5 ushr 23) and 524287L
            values[valuesOffset++] = (block5 ushr 4) and 524287L
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block5 and 15L) shl 15) or (block6 ushr 49)
            values[valuesOffset++] = (block6 ushr 30) and 524287L
            values[valuesOffset++] = (block6 ushr 11) and 524287L
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block6 and 2047L) shl 8) or (block7 ushr 56)
            values[valuesOffset++] = (block7 ushr 37) and 524287L
            values[valuesOffset++] = (block7 ushr 18) and 524287L
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block7 and 262143L) shl 1) or (block8 ushr 63)
            values[valuesOffset++] = (block8 ushr 44) and 524287L
            values[valuesOffset++] = (block8 ushr 25) and 524287L
            values[valuesOffset++] = (block8 ushr 6) and 524287L
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block8 and 63L) shl 13) or (block9 ushr 51)
            values[valuesOffset++] = (block9 ushr 32) and 524287L
            values[valuesOffset++] = (block9 ushr 13) and 524287L
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block9 and 8191L) shl 6) or (block10 ushr 58)
            values[valuesOffset++] = (block10 ushr 39) and 524287L
            values[valuesOffset++] = (block10 ushr 20) and 524287L
            values[valuesOffset++] = (block10 ushr 1) and 524287L
            val block11 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block10 and 1L) shl 18) or (block11 ushr 46)
            values[valuesOffset++] = (block11 ushr 27) and 524287L
            values[valuesOffset++] = (block11 ushr 8) and 524287L
            val block12 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block11 and 255L) shl 11) or (block12 ushr 53)
            values[valuesOffset++] = (block12 ushr 34) and 524287L
            values[valuesOffset++] = (block12 ushr 15) and 524287L
            val block13 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block12 and 32767L) shl 4) or (block13 ushr 60)
            values[valuesOffset++] = (block13 ushr 41) and 524287L
            values[valuesOffset++] = (block13 ushr 22) and 524287L
            values[valuesOffset++] = (block13 ushr 3) and 524287L
            val block14 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block13 and 7L) shl 16) or (block14 ushr 48)
            values[valuesOffset++] = (block14 ushr 29) and 524287L
            values[valuesOffset++] = (block14 ushr 10) and 524287L
            val block15 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block14 and 1023L) shl 9) or (block15 ushr 55)
            values[valuesOffset++] = (block15 ushr 36) and 524287L
            values[valuesOffset++] = (block15 ushr 17) and 524287L
            val block16 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block15 and 131071L) shl 2) or (block16 ushr 62)
            values[valuesOffset++] = (block16 ushr 43) and 524287L
            values[valuesOffset++] = (block16 ushr 24) and 524287L
            values[valuesOffset++] = (block16 ushr 5) and 524287L
            val block17 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block16 and 31L) shl 14) or (block17 ushr 50)
            values[valuesOffset++] = (block17 ushr 31) and 524287L
            values[valuesOffset++] = (block17 ushr 12) and 524287L
            val block18 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block17 and 4095L) shl 7) or (block18 ushr 57)
            values[valuesOffset++] = (block18 ushr 38) and 524287L
            values[valuesOffset++] = (block18 ushr 19) and 524287L
            values[valuesOffset++] = block18 and 524287L
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
            values[valuesOffset++] = (byte0 shl 11) or (byte1 shl 3) or (byte2 ushr 5)
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte2 and 31L) shl 14) or (byte3 shl 6) or (byte4 ushr 2)
            val byte5 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte6 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte7 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte4 and 3L) shl 17) or (byte5 shl 9) or (byte6 shl 1) or (byte7 ushr 7)
            val byte8 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte9 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte7 and 127L) shl 12) or (byte8 shl 4) or (byte9 ushr 4)
            val byte10 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte11 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte9 and 15L) shl 15) or (byte10 shl 7) or (byte11 ushr 1)
            val byte12 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte13 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte14 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] =
                ((byte11 and 1L) shl 18) or (byte12 shl 10) or (byte13 shl 2) or (byte14 ushr 6)
            val byte15 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte16 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte14 and 63L) shl 13) or (byte15 shl 5) or (byte16 ushr 3)
            val byte17 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte18 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte16 and 7L) shl 16) or (byte17 shl 8) or byte18
        }
    }
}
