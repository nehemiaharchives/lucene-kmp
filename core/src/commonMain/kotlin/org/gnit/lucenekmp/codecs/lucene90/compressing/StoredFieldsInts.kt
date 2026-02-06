package org.gnit.lucenekmp.codecs.lucene90.compressing


import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexInput
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.jdkport.toUnsignedLong

internal object StoredFieldsInts {
    private const val BLOCK_SIZE = 128
    private const val BLOCK_SIZE_MINUS_ONE = BLOCK_SIZE - 1

    @Throws(IOException::class)
    fun writeInts(values: IntArray, start: Int, count: Int, out: DataOutput) {
        var allEqual = true
        for (i in 1..<count) {
            if (values[start + i] != values[start]) {
                allEqual = false
                break
            }
        }
        if (allEqual) {
            out.writeByte(0.toByte())
            out.writeVInt(values[0])
        } else {
            var max: Long = 0
            for (i in 0..<count) {
                max = max or Int.toUnsignedLong(values[start + i])
            }
            if (max <= 0xff) {
                out.writeByte(8.toByte())
                writeInts8(out, count, values, start)
            } else if (max <= 0xffff) {
                out.writeByte(16.toByte())
                writeInts16(out, count, values, start)
            } else {
                out.writeByte(32.toByte())
                writeInts32(out, count, values, start)
            }
        }
    }

    @Throws(IOException::class)
    private fun writeInts8(out: DataOutput, count: Int, values: IntArray, offset: Int) {
        var k = 0
        while (k < count - BLOCK_SIZE_MINUS_ONE) {
            val step = offset + k
            for (i in 0..15) {
                val l =
                    ((values[step + i].toLong() shl 56)
                            or (values[step + 16 + i].toLong() shl 48)
                            or (values[step + 32 + i].toLong() shl 40)
                            or (values[step + 48 + i].toLong() shl 32)
                            or (values[step + 64 + i].toLong() shl 24)
                            or (values[step + 80 + i].toLong() shl 16)
                            or (values[step + 96 + i].toLong() shl 8)
                            or values[step + 112 + i].toLong())
                out.writeLong(l)
            }
            k += BLOCK_SIZE
        }
        while (k < count) {
            out.writeByte(values[offset + k].toByte())
            k++
        }
    }

    @Throws(IOException::class)
    private fun writeInts16(out: DataOutput, count: Int, values: IntArray, offset: Int) {
        var k = 0
        while (k < count - BLOCK_SIZE_MINUS_ONE) {
            val step = offset + k
            for (i in 0..31) {
                val l =
                    ((values[step + i].toLong() shl 48)
                            or (values[step + 32 + i].toLong() shl 32)
                            or (values[step + 64 + i].toLong() shl 16)
                            or values[step + 96 + i].toLong())
                out.writeLong(l)
            }
            k += BLOCK_SIZE
        }
        while (k < count) {
            out.writeShort(values[offset + k].toShort())
            k++
        }
    }

    @Throws(IOException::class)
    private fun writeInts32(out: DataOutput, count: Int, values: IntArray, offset: Int) {
        var k = 0
        while (k < count - BLOCK_SIZE_MINUS_ONE) {
            val step = offset + k
            for (i in 0..63) {
                val l = (values[step + i].toLong() shl 32) or (values[step + 64 + i].toLong() and 0xFFFFFFFFL)
                out.writeLong(l)
            }
            k += BLOCK_SIZE
        }
        while (k < count) {
            out.writeInt(values[offset + k])
            k++
        }
    }

    /** Read `count` integers into `values`.  */
    @Throws(IOException::class)
    fun readInts(`in`: IndexInput, count: Int, values: LongArray, offset: Int) {
        val bpv: Int = `in`.readByte().toInt()
        when (bpv) {
            0 -> Arrays.fill(values, offset, offset + count, `in`.readVInt().toLong())
            8 -> readInts8(`in`, count, values, offset)
            16 -> readInts16(`in`, count, values, offset)
            32 -> readInts32(`in`, count, values, offset)
            else -> throw IOException("Unsupported number of bits per value: $bpv")
        }
    }

    @Throws(IOException::class)
    private fun readInts8(`in`: IndexInput, count: Int, values: LongArray, offset: Int) {
        var k = 0
        while (k < count - BLOCK_SIZE_MINUS_ONE) {
            val step = offset + k
            `in`.readLongs(values, step, 16)
            for (i in 0..15) {
                val l = values[step + i]
                values[step + i] = (l ushr 56) and 0xFFL
                values[step + 16 + i] = (l ushr 48) and 0xFFL
                values[step + 32 + i] = (l ushr 40) and 0xFFL
                values[step + 48 + i] = (l ushr 32) and 0xFFL
                values[step + 64 + i] = (l ushr 24) and 0xFFL
                values[step + 80 + i] = (l ushr 16) and 0xFFL
                values[step + 96 + i] = (l ushr 8) and 0xFFL
                values[step + 112 + i] = l and 0xFFL
            }
            k += BLOCK_SIZE
        }
        while (k < count) {
            values[offset + k] = Byte.toUnsignedInt(`in`.readByte()).toLong()
            k++
        }
    }

    @Throws(IOException::class)
    private fun readInts16(`in`: IndexInput, count: Int, values: LongArray, offset: Int) {
        var k = 0
        while (k < count - BLOCK_SIZE_MINUS_ONE) {
            val step = offset + k
            `in`.readLongs(values, step, 32)
            for (i in 0..31) {
                val l = values[step + i]
                values[step + i] = (l ushr 48) and 0xFFFFL
                values[step + 32 + i] = (l ushr 32) and 0xFFFFL
                values[step + 64 + i] = (l ushr 16) and 0xFFFFL
                values[step + 96 + i] = l and 0xFFFFL
            }
            k += BLOCK_SIZE
        }
        while (k < count) {
            values[offset + k] = Short.toUnsignedInt(`in`.readShort()).toLong()
            k++
        }
    }

    @Throws(IOException::class)
    private fun readInts32(`in`: IndexInput, count: Int, values: LongArray, offset: Int) {
        var k = 0
        while (k < count - BLOCK_SIZE_MINUS_ONE) {
            val step = offset + k
            `in`.readLongs(values, step, 64)
            for (i in 0..63) {
                val l = values[step + i]
                values[step + i] = l ushr 32
                values[step + 64 + i] = l and 0xFFFFFFFFL
            }
            k += BLOCK_SIZE
        }
        while (k < count) {
            values[offset + k] = `in`.readInt().toLong()
            k++
        }
    }
}
