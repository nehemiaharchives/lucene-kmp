package org.gnit.lucenekmp.util.packed


/** Non-specialized [BulkOperation] for [PackedInts.Format.PACKED].  */
internal open class BulkOperationPacked(private val bitsPerValue: Int) : BulkOperation() {
    private val longBlockCount: Int
    private val longValueCount: Int
    private val byteBlockCount: Int
    private val byteValueCount: Int
    private var mask: Long = 0
    private val intMask: Int

    init {
        require(bitsPerValue > 0 && bitsPerValue <= 64)
        var blocks = bitsPerValue
        while ((blocks and 1) == 0) {
            blocks = blocks ushr 1
        }
        this.longBlockCount = blocks
        this.longValueCount = 64 * longBlockCount / bitsPerValue
        var byteBlockCount = 8 * longBlockCount
        var byteValueCount = longValueCount
        while ((byteBlockCount and 1) == 0 && (byteValueCount and 1) == 0) {
            byteBlockCount = byteBlockCount ushr 1
            byteValueCount = byteValueCount ushr 1
        }
        this.byteBlockCount = byteBlockCount
        this.byteValueCount = byteValueCount
        if (bitsPerValue == 64) {
            this.mask = 0L.inv()
        } else {
            this.mask = (1L shl bitsPerValue) - 1
        }
        this.intMask = mask.toInt()
        require(longValueCount * bitsPerValue == 64 * longBlockCount)
    }

    override fun longBlockCount(): Int {
        return longBlockCount
    }

    override fun longValueCount(): Int {
        return longValueCount
    }

    override fun byteBlockCount(): Int {
        return byteBlockCount
    }

    override fun byteValueCount(): Int {
        return byteValueCount
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        var bitsLeft = 64
        for (i in 0..<longValueCount * iterations) {
            bitsLeft -= bitsPerValue
            if (bitsLeft < 0) {
                values[valuesOffset++] =
                    (((blocks[blocksOffset++] and ((1L shl (bitsPerValue + bitsLeft)) - 1)) shl -bitsLeft)
                            or (blocks[blocksOffset] ushr (64 + bitsLeft)))
                bitsLeft += 64
            } else {
                values[valuesOffset++] = (blocks[blocksOffset] ushr bitsLeft) and mask
            }
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        var nextValue = 0L
        var bitsLeft = bitsPerValue
        for (i in 0..<iterations * byteBlockCount) {
            val bytes = blocks[blocksOffset++].toLong() and 0xFFL
            if (bitsLeft > 8) {
                // just buffer
                bitsLeft -= 8
                nextValue = nextValue or (bytes shl bitsLeft)
            } else {
                // flush
                var bits = 8 - bitsLeft
                values[valuesOffset++] = nextValue or (bytes ushr bits)
                while (bits >= bitsPerValue) {
                    bits -= bitsPerValue
                    values[valuesOffset++] = (bytes ushr bits) and mask
                }
                // then buffer
                bitsLeft = bitsPerValue - bits
                nextValue = (bytes and ((1L shl bits) - 1)) shl bitsLeft
            }
        }
        require(bitsLeft == bitsPerValue)
    }

    override fun decode(
        blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        if (bitsPerValue > 32) {
            throw UnsupportedOperationException(
                "Cannot decode $bitsPerValue-bits values into an int[]"
            )
        }
        var bitsLeft = 64
        for (i in 0..<longValueCount * iterations) {
            bitsLeft -= bitsPerValue
            if (bitsLeft < 0) {
                values[valuesOffset++] =
                    (((blocks[blocksOffset++] and ((1L shl (bitsPerValue + bitsLeft)) - 1)) shl -bitsLeft)
                            or (blocks[blocksOffset] ushr (64 + bitsLeft))).toInt()
                bitsLeft += 64
            } else {
                values[valuesOffset++] = ((blocks[blocksOffset] ushr bitsLeft) and mask).toInt()
            }
        }
    }

    override fun decode(
        blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int
    ) {
        var blocksOffset = blocksOffset
        var valuesOffset = valuesOffset
        var nextValue = 0
        var bitsLeft = bitsPerValue
        for (i in 0..<iterations * byteBlockCount) {
            val bytes = blocks[blocksOffset++].toInt() and 0xFF
            if (bitsLeft > 8) {
                // just buffer
                bitsLeft -= 8
                nextValue = nextValue or (bytes shl bitsLeft)
            } else {
                // flush
                var bits = 8 - bitsLeft
                values[valuesOffset++] = nextValue or (bytes ushr bits)
                while (bits >= bitsPerValue) {
                    bits -= bitsPerValue
                    values[valuesOffset++] = (bytes ushr bits) and intMask
                }
                // then buffer
                bitsLeft = bitsPerValue - bits
                nextValue = (bytes and ((1 shl bits) - 1)) shl bitsLeft
            }
        }
        require(bitsLeft == bitsPerValue)
    }

    override fun encode(
        values: LongArray, valuesOffset: Int, blocks: LongArray, blocksOffset: Int, iterations: Int
    ) {
        var valuesOffset = valuesOffset
        var blocksOffset = blocksOffset
        var nextBlock: Long = 0
        var bitsLeft = 64
        for (i in 0..<longValueCount * iterations) {
            bitsLeft -= bitsPerValue
            if (bitsLeft > 0) {
                nextBlock = nextBlock or (values[valuesOffset++] shl bitsLeft)
            } else if (bitsLeft == 0) {
                nextBlock = nextBlock or values[valuesOffset++]
                blocks[blocksOffset++] = nextBlock
                nextBlock = 0
                bitsLeft = 64
            } else { // bitsLeft < 0
                nextBlock = nextBlock or (values[valuesOffset] ushr -bitsLeft)
                blocks[blocksOffset++] = nextBlock
                nextBlock = (values[valuesOffset++] and ((1L shl -bitsLeft) - 1)) shl (64 + bitsLeft)
                bitsLeft += 64
            }
        }
    }

    override fun encode(
        values: IntArray, valuesOffset: Int, blocks: LongArray, blocksOffset: Int, iterations: Int
    ) {
        var valuesOffset = valuesOffset
        var blocksOffset = blocksOffset
        var nextBlock: Long = 0
        var bitsLeft = 64
        for (i in 0..<longValueCount * iterations) {
            bitsLeft -= bitsPerValue
            if (bitsLeft > 0) {
                nextBlock = nextBlock or ((values[valuesOffset++].toLong() and 0xFFFFFFFFL) shl bitsLeft)
            } else if (bitsLeft == 0) {
                nextBlock = nextBlock or (values[valuesOffset++].toLong() and 0xFFFFFFFFL)
                blocks[blocksOffset++] = nextBlock
                nextBlock = 0
                bitsLeft = 64
            } else { // bitsLeft < 0
                nextBlock = nextBlock or ((values[valuesOffset].toLong() and 0xFFFFFFFFL) ushr -bitsLeft)
                blocks[blocksOffset++] = nextBlock
                nextBlock = (values[valuesOffset++].toLong() and ((1L shl -bitsLeft) - 1)) shl (64 + bitsLeft)
                bitsLeft += 64
            }
        }
    }

    override fun encode(
        values: LongArray, valuesOffset: Int, blocks: ByteArray, blocksOffset: Int, iterations: Int
    ) {
        var valuesOffset = valuesOffset
        var blocksOffset = blocksOffset
        var nextBlock = 0
        var bitsLeft = 8
        for (i in 0..<byteValueCount * iterations) {
            val v = values[valuesOffset++]
            require(PackedInts.unsignedBitsRequired(v) <= bitsPerValue)
            if (bitsPerValue < bitsLeft) {
                // just buffer
                nextBlock = (nextBlock.toLong() or (v shl (bitsLeft - bitsPerValue))).toInt()
                bitsLeft -= bitsPerValue
            } else {
                // flush as many blocks as possible
                var bits = bitsPerValue - bitsLeft
                blocks[blocksOffset++] = (nextBlock.toLong() or (v ushr bits)).toByte()
                while (bits >= 8) {
                    bits -= 8
                    blocks[blocksOffset++] = (v ushr bits).toByte()
                }
                // then buffer
                bitsLeft = 8 - bits
                nextBlock = ((v and ((1L shl bits) - 1)) shl bitsLeft).toInt()
            }
        }
        require(bitsLeft == 8)
    }

    override fun encode(
        values: IntArray, valuesOffset: Int, blocks: ByteArray, blocksOffset: Int, iterations: Int
    ) {
        var valuesOffset = valuesOffset
        var blocksOffset = blocksOffset
        var nextBlock = 0
        var bitsLeft = 8
        for (i in 0..<byteValueCount * iterations) {
            val v = values[valuesOffset++]
            require(PackedInts.bitsRequired(v.toLong() and 0xFFFFFFFFL) <= bitsPerValue)
            if (bitsPerValue < bitsLeft) {
                // just buffer
                nextBlock = nextBlock or (v shl (bitsLeft - bitsPerValue))
                bitsLeft -= bitsPerValue
            } else {
                // flush as many blocks as possible
                var bits = bitsPerValue - bitsLeft
                blocks[blocksOffset++] = (nextBlock or (v ushr bits)).toByte()
                while (bits >= 8) {
                    bits -= 8
                    blocks[blocksOffset++] = (v ushr bits).toByte()
                }
                // then buffer
                bitsLeft = 8 - bits
                nextBlock = (v and ((1 shl bits) - 1)) shl bitsLeft
            }
        }
        require(bitsLeft == 8)
    }
}
