package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked18 : BulkOperationPacked(18) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 46).toInt()
            values[valuesOffset++] = ((block0 ushr 28) and 262143L).toInt()
            values[valuesOffset++] = ((block0 ushr 10) and 262143L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 1023L) shl 8) or (block1 ushr 56)).toInt()
            values[valuesOffset++] = ((block1 ushr 38) and 262143L).toInt()
            values[valuesOffset++] = ((block1 ushr 20) and 262143L).toInt()
            values[valuesOffset++] = ((block1 ushr 2) and 262143L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 3L) shl 16) or (block2 ushr 48)).toInt()
            values[valuesOffset++] = ((block2 ushr 30) and 262143L).toInt()
            values[valuesOffset++] = ((block2 ushr 12) and 262143L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 4095L) shl 6) or (block3 ushr 58)).toInt()
            values[valuesOffset++] = ((block3 ushr 40) and 262143L).toInt()
            values[valuesOffset++] = ((block3 ushr 22) and 262143L).toInt()
            values[valuesOffset++] = ((block3 ushr 4) and 262143L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 15L) shl 14) or (block4 ushr 50)).toInt()
            values[valuesOffset++] = ((block4 ushr 32) and 262143L).toInt()
            values[valuesOffset++] = ((block4 ushr 14) and 262143L).toInt()
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block4 and 16383L) shl 4) or (block5 ushr 60)).toInt()
            values[valuesOffset++] = ((block5 ushr 42) and 262143L).toInt()
            values[valuesOffset++] = ((block5 ushr 24) and 262143L).toInt()
            values[valuesOffset++] = ((block5 ushr 6) and 262143L).toInt()
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block5 and 63L) shl 12) or (block6 ushr 52)).toInt()
            values[valuesOffset++] = ((block6 ushr 34) and 262143L).toInt()
            values[valuesOffset++] = ((block6 ushr 16) and 262143L).toInt()
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block6 and 65535L) shl 2) or (block7 ushr 62)).toInt()
            values[valuesOffset++] = ((block7 ushr 44) and 262143L).toInt()
            values[valuesOffset++] = ((block7 ushr 26) and 262143L).toInt()
            values[valuesOffset++] = ((block7 ushr 8) and 262143L).toInt()
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block7 and 255L) shl 10) or (block8 ushr 54)).toInt()
            values[valuesOffset++] = ((block8 ushr 36) and 262143L).toInt()
            values[valuesOffset++] = ((block8 ushr 18) and 262143L).toInt()
            values[valuesOffset++] = (block8 and 262143L).toInt()
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
            values[valuesOffset++] = (byte0 shl 10) or (byte1 shl 2) or (byte2 ushr 6)
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte2 and 63) shl 12) or (byte3 shl 4) or (byte4 ushr 4)
            val byte5 = blocks[blocksOffset++].toInt() and 0xFF
            val byte6 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte4 and 15) shl 14) or (byte5 shl 6) or (byte6 ushr 2)
            val byte7 = blocks[blocksOffset++].toInt() and 0xFF
            val byte8 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte6 and 3) shl 16) or (byte7 shl 8) or byte8
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 46
            values[valuesOffset++] = (block0 ushr 28) and 262143L
            values[valuesOffset++] = (block0 ushr 10) and 262143L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 1023L) shl 8) or (block1 ushr 56)
            values[valuesOffset++] = (block1 ushr 38) and 262143L
            values[valuesOffset++] = (block1 ushr 20) and 262143L
            values[valuesOffset++] = (block1 ushr 2) and 262143L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 3L) shl 16) or (block2 ushr 48)
            values[valuesOffset++] = (block2 ushr 30) and 262143L
            values[valuesOffset++] = (block2 ushr 12) and 262143L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 4095L) shl 6) or (block3 ushr 58)
            values[valuesOffset++] = (block3 ushr 40) and 262143L
            values[valuesOffset++] = (block3 ushr 22) and 262143L
            values[valuesOffset++] = (block3 ushr 4) and 262143L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 15L) shl 14) or (block4 ushr 50)
            values[valuesOffset++] = (block4 ushr 32) and 262143L
            values[valuesOffset++] = (block4 ushr 14) and 262143L
            val block5 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block4 and 16383L) shl 4) or (block5 ushr 60)
            values[valuesOffset++] = (block5 ushr 42) and 262143L
            values[valuesOffset++] = (block5 ushr 24) and 262143L
            values[valuesOffset++] = (block5 ushr 6) and 262143L
            val block6 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block5 and 63L) shl 12) or (block6 ushr 52)
            values[valuesOffset++] = (block6 ushr 34) and 262143L
            values[valuesOffset++] = (block6 ushr 16) and 262143L
            val block7 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block6 and 65535L) shl 2) or (block7 ushr 62)
            values[valuesOffset++] = (block7 ushr 44) and 262143L
            values[valuesOffset++] = (block7 ushr 26) and 262143L
            values[valuesOffset++] = (block7 ushr 8) and 262143L
            val block8 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block7 and 255L) shl 10) or (block8 ushr 54)
            values[valuesOffset++] = (block8 ushr 36) and 262143L
            values[valuesOffset++] = (block8 ushr 18) and 262143L
            values[valuesOffset++] = block8 and 262143L
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
            values[valuesOffset++] = (byte0 shl 10) or (byte1 shl 2) or (byte2 ushr 6)
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte2 and 63L) shl 12) or (byte3 shl 4) or (byte4 ushr 4)
            val byte5 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte6 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte4 and 15L) shl 14) or (byte5 shl 6) or (byte6 ushr 2)
            val byte7 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte8 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte6 and 3L) shl 16) or (byte7 shl 8) or byte8
        }
    }
}
