package org.gnit.lucenekmp.util.packed


/** Efficient sequential read/write of packed integers.  */
internal class BulkOperationPacked20 : BulkOperationPacked(20) {
    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = (block0 ushr 44).toInt()
            values[valuesOffset++] = ((block0 ushr 24) and 1048575L).toInt()
            values[valuesOffset++] = ((block0 ushr 4) and 1048575L).toInt()
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block0 and 15L) shl 16) or (block1 ushr 48)).toInt()
            values[valuesOffset++] = ((block1 ushr 28) and 1048575L).toInt()
            values[valuesOffset++] = ((block1 ushr 8) and 1048575L).toInt()
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block1 and 255L) shl 12) or (block2 ushr 52)).toInt()
            values[valuesOffset++] = ((block2 ushr 32) and 1048575L).toInt()
            values[valuesOffset++] = ((block2 ushr 12) and 1048575L).toInt()
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block2 and 4095L) shl 8) or (block3 ushr 56)).toInt()
            values[valuesOffset++] = ((block3 ushr 36) and 1048575L).toInt()
            values[valuesOffset++] = ((block3 ushr 16) and 1048575L).toInt()
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = (((block3 and 65535L) shl 4) or (block4 ushr 60)).toInt()
            values[valuesOffset++] = ((block4 ushr 40) and 1048575L).toInt()
            values[valuesOffset++] = ((block4 ushr 20) and 1048575L).toInt()
            values[valuesOffset++] = (block4 and 1048575L).toInt()
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
            values[valuesOffset++] = (byte0 shl 12) or (byte1 shl 4) or (byte2 ushr 4)
            val byte3 = blocks[blocksOffset++].toInt() and 0xFF
            val byte4 = blocks[blocksOffset++].toInt() and 0xFF
            values[valuesOffset++] = ((byte2 and 15) shl 16) or (byte3 shl 8) or byte4
        }
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        for (i in 0..<iterations) {
            val block0 = blocks[blocksOffset++]
            values[valuesOffset++] = block0 ushr 44
            values[valuesOffset++] = (block0 ushr 24) and 1048575L
            values[valuesOffset++] = (block0 ushr 4) and 1048575L
            val block1 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block0 and 15L) shl 16) or (block1 ushr 48)
            values[valuesOffset++] = (block1 ushr 28) and 1048575L
            values[valuesOffset++] = (block1 ushr 8) and 1048575L
            val block2 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block1 and 255L) shl 12) or (block2 ushr 52)
            values[valuesOffset++] = (block2 ushr 32) and 1048575L
            values[valuesOffset++] = (block2 ushr 12) and 1048575L
            val block3 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block2 and 4095L) shl 8) or (block3 ushr 56)
            values[valuesOffset++] = (block3 ushr 36) and 1048575L
            values[valuesOffset++] = (block3 ushr 16) and 1048575L
            val block4 = blocks[blocksOffset++]
            values[valuesOffset++] = ((block3 and 65535L) shl 4) or (block4 ushr 60)
            values[valuesOffset++] = (block4 ushr 40) and 1048575L
            values[valuesOffset++] = (block4 ushr 20) and 1048575L
            values[valuesOffset++] = block4 and 1048575L
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
            values[valuesOffset++] = (byte0 shl 12) or (byte1 shl 4) or (byte2 ushr 4)
            val byte3 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            val byte4 = (blocks[blocksOffset++].toInt() and 0xFF).toLong()
            values[valuesOffset++] = ((byte2 and 15L) shl 16) or (byte3 shl 8) or byte4
        }
    }
}
