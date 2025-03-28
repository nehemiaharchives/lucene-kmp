package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked22 : BulkOperationPacked(22) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 42).toInt()
            values[valuesOffset++] = ((block0 ushr 20) and 4194303L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 1048575L) shl 2) or (block1 ushr 62)).toInt()
            values[valuesOffset++] = ((block1 ushr 40) and 4194303L).toInt()
            values[valuesOffset++] = ((block1 ushr 18) and 4194303L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 262143L) shl 4) or (block2 ushr 60)).toInt()
            values[valuesOffset++] = ((block2 ushr 38) and 4194303L).toInt()
            values[valuesOffset++] = ((block2 ushr 16) and 4194303L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 65535L) shl 6) or (block3 ushr 58)).toInt()
            values[valuesOffset++] = ((block3 ushr 36) and 4194303L).toInt()
            values[valuesOffset++] = ((block3 ushr 14) and 4194303L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 16383L) shl 8) or (block4 ushr 56)).toInt()
            values[valuesOffset++] = ((block4 ushr 34) and 4194303L).toInt()
            values[valuesOffset++] = ((block4 ushr 12) and 4194303L).toInt()
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block4 and 4095L) shl 10) or (block5 ushr 54)).toInt()
            values[valuesOffset++] = ((block5 ushr 32) and 4194303L).toInt()
            values[valuesOffset++] = ((block5 ushr 10) and 4194303L).toInt()
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block5 and 1023L) shl 12) or (block6 ushr 52)).toInt()
            values[valuesOffset++] = ((block6 ushr 30) and 4194303L).toInt()
            values[valuesOffset++] = ((block6 ushr 8) and 4194303L).toInt()
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block6 and 255L) shl 14) or (block7 ushr 50)).toInt()
            values[valuesOffset++] = ((block7 ushr 28) and 4194303L).toInt()
            values[valuesOffset++] = ((block7 ushr 6) and 4194303L).toInt()
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block7 and 63L) shl 16) or (block8 ushr 48)).toInt()
            values[valuesOffset++] = ((block8 ushr 26) and 4194303L).toInt()
            values[valuesOffset++] = ((block8 ushr 4) and 4194303L).toInt()
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block8 and 15L) shl 18) or (block9 ushr 46)).toInt()
            values[valuesOffset++] = ((block9 ushr 24) and 4194303L).toInt()
            values[valuesOffset++] = ((block9 ushr 2) and 4194303L).toInt()
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block9 and 3L) shl 20) or (block10 ushr 44)).toInt()
            values[valuesOffset++] = ((block10 ushr 22) and 4194303L).toInt()
            values[valuesOffset++] = (block10 and 4194303L).toInt()
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
            values[valuesOffset++] = (byte0 shl 14) or (byte1 shl 6) or (byte2 ushr 2)
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            val byte5 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte2 and 3) shl 20) or (byte3 shl 12) or (byte4 shl 4) or (byte5 ushr 4)
            val byte6 = blocks[blocksOffset++].toInt() and 0xFF
            val byte7 = blocks[blocksOffset++].toInt() and 0xFF
            val byte8 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte5 and 15) shl 18) or (byte6 shl 10) or (byte7 shl 2) or (byte8 ushr 6)
            val byte9 = blocks[blocksOffset++].toInt() and 0xFF
            val byte10 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte8 and 63) shl 16) or (byte9 shl 8) or byte10
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 42
            values[valuesOffset++] = (block0 ushr 20) and 4194303L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 1048575L) shl 2) or (block1 ushr 62)
            values[valuesOffset++] = (block1 ushr 40) and 4194303L
            values[valuesOffset++] = (block1 ushr 18) and 4194303L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 262143L) shl 4) or (block2 ushr 60)
            values[valuesOffset++] = (block2 ushr 38) and 4194303L
            values[valuesOffset++] = (block2 ushr 16) and 4194303L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 65535L) shl 6) or (block3 ushr 58)
            values[valuesOffset++] = (block3 ushr 36) and 4194303L
            values[valuesOffset++] = (block3 ushr 14) and 4194303L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 16383L) shl 8) or (block4 ushr 56)
            values[valuesOffset++] = (block4 ushr 34) and 4194303L
            values[valuesOffset++] = (block4 ushr 12) and 4194303L
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block4 and 4095L) shl 10) or (block5 ushr 54)
            values[valuesOffset++] = (block5 ushr 32) and 4194303L
            values[valuesOffset++] = (block5 ushr 10) and 4194303L
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block5 and 1023L) shl 12) or (block6 ushr 52)
            values[valuesOffset++] = (block6 ushr 30) and 4194303L
            values[valuesOffset++] = (block6 ushr 8) and 4194303L
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block6 and 255L) shl 14) or (block7 ushr 50)
            values[valuesOffset++] = (block7 ushr 28) and 4194303L
            values[valuesOffset++] = (block7 ushr 6) and 4194303L
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block7 and 63L) shl 16) or (block8 ushr 48)
            values[valuesOffset++] = (block8 ushr 26) and 4194303L
            values[valuesOffset++] = (block8 ushr 4) and 4194303L
            val block9 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block8 and 15L) shl 18) or (block9 ushr 46)
            values[valuesOffset++] = (block9 ushr 24) and 4194303L
            values[valuesOffset++] = (block9 ushr 2) and 4194303L
            val block10 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block9 and 3L) shl 20) or (block10 ushr 44)
            values[valuesOffset++] = (block10 ushr 22) and 4194303L
            values[valuesOffset++] = block10 and 4194303L
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
            values[valuesOffset++] = (byte0 shl 14) or (byte1 shl 6) or (byte2 ushr 2)
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte5 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte2 and 3L) shl 20) or (byte3 shl 12) or (byte4 shl 4) or (byte5 ushr 4)
            val byte6 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte7 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte8 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte5 and 15L) shl 18) or (byte6 shl 10) or (byte7 shl 2) or (byte8 ushr 6)
            val byte9 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte10 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte8 and 63L) shl 16) or (byte9 shl 8) or byte10
        }
    }
}
