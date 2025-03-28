package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked23 : BulkOperationPacked(23) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 41).toInt()
            values[valuesOffset++] = ((block0 ushr 18) and 8388607L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 262143L) shl 5) or (block1 ushr 59)).toInt()
            values[valuesOffset++] = ((block1 ushr 36) and 8388607L).toInt()
            values[valuesOffset++] = ((block1 ushr 13) and 8388607L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 8191L) shl 10) or (block2 ushr 54)).toInt()
            values[valuesOffset++] = ((block2 ushr 31) and 8388607L).toInt()
            values[valuesOffset++] = ((block2 ushr 8) and 8388607L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 255L) shl 15) or (block3 ushr 49)).toInt()
            values[valuesOffset++] = ((block3 ushr 26) and 8388607L).toInt()
            values[valuesOffset++] = ((block3 ushr 3) and 8388607L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 7L) shl 20) or (block4 ushr 44)).toInt()
            values[valuesOffset++] = ((block4 ushr 21) and 8388607L).toInt()
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block4 and 2097151L) shl 2) or (block5 ushr 62)).toInt()
            values[valuesOffset++] = ((block5 ushr 39) and 8388607L).toInt()
            values[valuesOffset++] = ((block5 ushr 16) and 8388607L).toInt()
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block5 and 65535L) shl 7) or (block6 ushr 57)).toInt()
            values[valuesOffset++] = ((block6 ushr 34) and 8388607L).toInt()
            values[valuesOffset++] = ((block6 ushr 11) and 8388607L).toInt()
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block6 and 2047L) shl 12) or (block7 ushr 52)).toInt()
            values[valuesOffset++] = ((block7 ushr 29) and 8388607L).toInt()
            values[valuesOffset++] = ((block7 ushr 6) and 8388607L).toInt()
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block7 and 63L) shl 17) or (block8 ushr 47)).toInt()
            values[valuesOffset++] = ((block8 ushr 24) and 8388607L).toInt()
            values[valuesOffset++] = ((block8 ushr 1) and 8388607L).toInt()
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block8 and 1L) shl 22) or (block9 ushr 42)).toInt()
            values[valuesOffset++] = ((block9 ushr 19) and 8388607L).toInt()
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block9 and 524287L) shl 4) or (block10 ushr 60)).toInt()
            values[valuesOffset++] = ((block10 ushr 37) and 8388607L).toInt()
            values[valuesOffset++] = ((block10 ushr 14) and 8388607L).toInt()
            val block11 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block10 and 16383L) shl 9) or (block11 ushr 55)).toInt()
            values[valuesOffset++] = ((block11 ushr 32) and 8388607L).toInt()
            values[valuesOffset++] = ((block11 ushr 9) and 8388607L).toInt()
            val block12 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block11 and 511L) shl 14) or (block12 ushr 50)).toInt()
            values[valuesOffset++] = ((block12 ushr 27) and 8388607L).toInt()
            values[valuesOffset++] = ((block12 ushr 4) and 8388607L).toInt()
            val block13 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block12 and 15L) shl 19) or (block13 ushr 45)).toInt()
            values[valuesOffset++] = ((block13 ushr 22) and 8388607L).toInt()
            val block14 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block13 and 4194303L) shl 1) or (block14 ushr 63)).toInt()
            values[valuesOffset++] = ((block14 ushr 40) and 8388607L).toInt()
            values[valuesOffset++] = ((block14 ushr 17) and 8388607L).toInt()
            val block15 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block14 and 131071L) shl 6) or (block15 ushr 58)).toInt()
            values[valuesOffset++] = ((block15 ushr 35) and 8388607L).toInt()
            values[valuesOffset++] = ((block15 ushr 12) and 8388607L).toInt()
            val block16 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block15 and 4095L) shl 11) or (block16 ushr 53)).toInt()
            values[valuesOffset++] = ((block16 ushr 30) and 8388607L).toInt()
            values[valuesOffset++] = ((block16 ushr 7) and 8388607L).toInt()
            val block17 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block16 and 127L) shl 16) or (block17 ushr 48)).toInt()
            values[valuesOffset++] = ((block17 ushr 25) and 8388607L).toInt()
            values[valuesOffset++] = ((block17 ushr 2) and 8388607L).toInt()
            val block18 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block17 and 3L) shl 21) or (block18 ushr 43)).toInt()
            values[valuesOffset++] = ((block18 ushr 20) and 8388607L).toInt()
            val block19 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block18 and 1048575L) shl 3) or (block19 ushr 61)).toInt()
            values[valuesOffset++] = ((block19 ushr 38) and 8388607L).toInt()
            values[valuesOffset++] = ((block19 ushr 15) and 8388607L).toInt()
            val block20 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block19 and 32767L) shl 8) or (block20 ushr 56)).toInt()
            values[valuesOffset++] = ((block20 ushr 33) and 8388607L).toInt()
            values[valuesOffset++] = ((block20 ushr 10) and 8388607L).toInt()
            val block21 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block20 and 1023L) shl 13) or (block21 ushr 51)).toInt()
            values[valuesOffset++] = ((block21 ushr 28) and 8388607L).toInt()
            values[valuesOffset++] = ((block21 ushr 5) and 8388607L).toInt()
            val block22 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block21 and 31L) shl 18) or (block22 ushr 46)).toInt()
            values[valuesOffset++] = ((block22 ushr 23) and 8388607L).toInt()
            values[valuesOffset++] = (block22 and 8388607L).toInt()
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
            values[valuesOffset++] = (byte0 shl 15) or (byte1 shl 7) or (byte2 ushr 1)
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            val byte5 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte2 and 1) shl 22) or (byte3 shl 14) or (byte4 shl 6) or (byte5 ushr 2)
            val byte6 = blocks[blocksOffset++].toInt() and 0xFF
            val byte7 = blocks[blocksOffset++].toInt() and 0xFF
            val byte8 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte5 and 3) shl 21) or (byte6 shl 13) or (byte7 shl 5) or (byte8 ushr 3)
            val byte9 = blocks[blocksOffset++].toInt() and 0xFF
            val byte10 = blocks[blocksOffset++].toInt() and 0xFF
            val byte11 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte8 and 7) shl 20) or (byte9 shl 12) or (byte10 shl 4) or (byte11 ushr 4)
            val byte12 = blocks[blocksOffset++].toInt() and 0xFF
            val byte13 = blocks[blocksOffset++].toInt() and 0xFF
            val byte14 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] =
                ((byte11 and 15) shl 19) or (byte12 shl 11) or (byte13 shl 3) or (byte14 ushr 5)
            val byte15 = blocks[blocksOffset++].toInt() and 0xFF
            val byte16 = blocks[blocksOffset++].toInt() and 0xFF
            val byte17 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] =
                ((byte14 and 31) shl 18) or (byte15 shl 10) or (byte16 shl 2) or (byte17 ushr 6)
            val byte18 = blocks[blocksOffset++].toInt() and 0xFF
            val byte19 = blocks[blocksOffset++].toInt() and 0xFF
            val byte20 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] =
                ((byte17 and 63) shl 17) or (byte18 shl 9) or (byte19 shl 1) or (byte20 ushr 7)
            val byte21 = blocks[blocksOffset++].toInt() and 0xFF
            val byte22 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte20 and 127) shl 16) or (byte21 shl 8) or byte22
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 41
            values[valuesOffset++] = (block0 ushr 18) and 8388607L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 262143L) shl 5) or (block1 ushr 59)
            values[valuesOffset++] = (block1 ushr 36) and 8388607L
            values[valuesOffset++] = (block1 ushr 13) and 8388607L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 8191L) shl 10) or (block2 ushr 54)
            values[valuesOffset++] = (block2 ushr 31) and 8388607L
            values[valuesOffset++] = (block2 ushr 8) and 8388607L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 255L) shl 15) or (block3 ushr 49)
            values[valuesOffset++] = (block3 ushr 26) and 8388607L
            values[valuesOffset++] = (block3 ushr 3) and 8388607L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 7L) shl 20) or (block4 ushr 44)
            values[valuesOffset++] = (block4 ushr 21) and 8388607L
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block4 and 2097151L) shl 2) or (block5 ushr 62)
            values[valuesOffset++] = (block5 ushr 39) and 8388607L
            values[valuesOffset++] = (block5 ushr 16) and 8388607L
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block5 and 65535L) shl 7) or (block6 ushr 57)
            values[valuesOffset++] = (block6 ushr 34) and 8388607L
            values[valuesOffset++] = (block6 ushr 11) and 8388607L
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block6 and 2047L) shl 12) or (block7 ushr 52)
            values[valuesOffset++] = (block7 ushr 29) and 8388607L
            values[valuesOffset++] = (block7 ushr 6) and 8388607L
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block7 and 63L) shl 17) or (block8 ushr 47)
            values[valuesOffset++] = (block8 ushr 24) and 8388607L
            values[valuesOffset++] = (block8 ushr 1) and 8388607L
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block8 and 1L) shl 22) or (block9 ushr 42)
            values[valuesOffset++] = (block9 ushr 19) and 8388607L
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block9 and 524287L) shl 4) or (block10 ushr 60)
            values[valuesOffset++] = (block10 ushr 37) and 8388607L
            values[valuesOffset++] = (block10 ushr 14) and 8388607L
            val block11 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block10 and 16383L) shl 9) or (block11 ushr 55)
            values[valuesOffset++] = (block11 ushr 32) and 8388607L
            values[valuesOffset++] = (block11 ushr 9) and 8388607L
            val block12 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block11 and 511L) shl 14) or (block12 ushr 50)
            values[valuesOffset++] = (block12 ushr 27) and 8388607L
            values[valuesOffset++] = (block12 ushr 4) and 8388607L
            val block13 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block12 and 15L) shl 19) or (block13 ushr 45)
            values[valuesOffset++] = (block13 ushr 22) and 8388607L
            val block14 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block13 and 4194303L) shl 1) or (block14 ushr 63)
            values[valuesOffset++] = (block14 ushr 40) and 8388607L
            values[valuesOffset++] = (block14 ushr 17) and 8388607L
            val block15 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block14 and 131071L) shl 6) or (block15 ushr 58)
            values[valuesOffset++] = (block15 ushr 35) and 8388607L
            values[valuesOffset++] = (block15 ushr 12) and 8388607L
            val block16 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block15 and 4095L) shl 11) or (block16 ushr 53)
            values[valuesOffset++] = (block16 ushr 30) and 8388607L
            values[valuesOffset++] = (block16 ushr 7) and 8388607L
            val block17 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block16 and 127L) shl 16) or (block17 ushr 48)
            values[valuesOffset++] = (block17 ushr 25) and 8388607L
            values[valuesOffset++] = (block17 ushr 2) and 8388607L
            val block18 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block17 and 3L) shl 21) or (block18 ushr 43)
            values[valuesOffset++] = (block18 ushr 20) and 8388607L
            val block19 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block18 and 1048575L) shl 3) or (block19 ushr 61)
            values[valuesOffset++] = (block19 ushr 38) and 8388607L
            values[valuesOffset++] = (block19 ushr 15) and 8388607L
            val block20 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block19 and 32767L) shl 8) or (block20 ushr 56)
            values[valuesOffset++] = (block20 ushr 33) and 8388607L
            values[valuesOffset++] = (block20 ushr 10) and 8388607L
            val block21 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block20 and 1023L) shl 13) or (block21 ushr 51)
            values[valuesOffset++] = (block21 ushr 28) and 8388607L
            values[valuesOffset++] = (block21 ushr 5) and 8388607L
            val block22 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block21 and 31L) shl 18) or (block22 ushr 46)
            values[valuesOffset++] = (block22 ushr 23) and 8388607L
            values[valuesOffset++] = block22 and 8388607L
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
            values[valuesOffset++] = (byte0 shl 15) or (byte1 shl 7) or (byte2 ushr 1)
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte5 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte2 and 1L) shl 22) or (byte3 shl 14) or (byte4 shl 6) or (byte5 ushr 2)
            val byte6 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte7 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte8 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte5 and 3L) shl 21) or (byte6 shl 13) or (byte7 shl 5) or (byte8 ushr 3)
            val byte9 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte10 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte11 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte8 and 7L) shl 20) or (byte9 shl 12) or (byte10 shl 4) or (byte11 ushr 4)
            val byte12 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte13 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte14 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] =
                ((byte11 and 15L) shl 19) or (byte12 shl 11) or (byte13 shl 3) or (byte14 ushr 5)
            val byte15 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte16 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte17 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] =
                ((byte14 and 31L) shl 18) or (byte15 shl 10) or (byte16 shl 2) or (byte17 ushr 6)
            val byte18 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte19 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte20 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] =
                ((byte17 and 63L) shl 17) or (byte18 shl 9) or (byte19 shl 1) or (byte20 ushr 7)
            val byte21 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte22 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte20 and 127L) shl 16) or (byte21 shl 8) or byte22
        }
    }
}
