package org.gnit.lucenekmp.util.compress

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.BytesRef
import kotlinx.io.IOException
import kotlin.experimental.and
import kotlin.experimental.or


/**
 * Utility class that can efficiently compress arrays that mostly contain characters in the
 * [0x1F,0x3F) or [0x5F,0x7F) ranges, which notably include all digits, lowercase characters, '.',
 * '-' and '_'.
 */
object LowercaseAsciiCompression {
    private fun isCompressible(b: Int): Boolean {
        val high3Bits = (b + 1) and 0x1F.inv()
        return high3Bits == 0x20 || high3Bits == 0x60
    }

    /**
     * Compress `in[0:len]` into `out`. This returns `false` if the content cannot
     * be compressed. The number of bytes written is guaranteed to be less than `len` otherwise.
     */
    @Throws(IOException::class)
    fun compress(`in`: ByteArray, len: Int, tmp: ByteArray, out: DataOutput): Boolean {
        if (len < 8) {
            return false
        }

        // 1. Count exceptions and fail compression if there are too many of them.
        val maxExceptions = len ushr 5
        var previousExceptionIndex = 0
        var numExceptions = 0
        for (i in 0..<len) {
            val b = `in`[i].toInt() and 0xFF
            if (!isCompressible(b)) {
                while (i - previousExceptionIndex > 0xFF) {
                    ++numExceptions
                    previousExceptionIndex += 0xFF
                }
                if (++numExceptions > maxExceptions) {
                    return false
                }
                previousExceptionIndex = i
            }
        }
        require(numExceptions <= maxExceptions)

        // 2. Now move all bytes to the [0,0x40) range (6 bits). This loop gets auto-vectorized on
        // JDK13+.
        val compressedLen = len - (len ushr 2) // ignores exceptions
        require(compressedLen < len)
        for (i in 0..<len) {
            val b = (`in`[i].toInt() and 0xFF) + 1
            tmp[i] = ((b and 0x1F) or ((b and 0x40) ushr 1)).toByte()
        }

        // 3. Now pack the bytes so that we record 4 ASCII chars in 3 bytes
        var o = 0
        for (i in compressedLen..<len) {
            tmp[o++] = tmp[o++] or ((tmp[i] and 0x30).toInt() shl 2).toByte() // bits 4-5
        }
        for (i in compressedLen..<len) {
            tmp[o++] = tmp[o++] or ((tmp[i] and 0x0C).toInt() shl 4).toByte() // bits 2-3
        }
        for (i in compressedLen..<len) {
            tmp[o++] = tmp[o++] or ((tmp[i] and 0x03).toInt() shl 6).toByte() // bits 0-1
        }
        require(o <= compressedLen)

        out.writeBytes(tmp, 0, compressedLen)

        // 4. Finally record exceptions
        out.writeVInt(numExceptions)
        if (numExceptions > 0) {
            previousExceptionIndex = 0
            var numExceptions2 = 0
            for (i in 0..<len) {
                val b = `in`[i].toInt() and 0xFF
                if (!isCompressible(b)) {
                    while (i - previousExceptionIndex > 0xFF) {
                        // We record deltas between exceptions as bytes, so we need to create
                        // "artificial" exceptions if the delta between two of them is greater
                        // than the maximum unsigned byte value.
                        out.writeByte(0xFF.toByte())
                        previousExceptionIndex += 0xFF
                        out.writeByte(`in`[previousExceptionIndex])
                        numExceptions2++
                    }
                    out.writeByte((i - previousExceptionIndex).toByte())
                    previousExceptionIndex = i
                    out.writeByte(b.toByte())
                    numExceptions2++
                }
            }

            // TODO: shouldn't this really be an assert instead?  but then this real "if" triggered
            // LUCENE-10551 so maybe it should remain a real "if":
            check(numExceptions == numExceptions2) {
                "$numExceptions <> $numExceptions2 " + BytesRef(
                    `in`,
                    0,
                    len
                )
            }
        }

        return true
    }

    /**
     * Decompress data that has been compressed with [.compress]. `len` must be the original length, not the compressed length.
     */
    @Throws(IOException::class)
    fun decompress(`in`: DataInput, out: ByteArray, len: Int) {
        val saved = len ushr 2
        val compressedLen = len - saved

        // 1. Copy the packed bytes
        `in`.readBytes(out, 0, compressedLen)

        // 2. Restore the leading 2 bits of each packed byte into whole bytes
        for (i in 0..<saved) {
            out[compressedLen + i] = ((((out[i].toInt() and 0xC0) ushr 2)
                    or ((out[saved + i].toInt() and 0xC0) ushr 4)
                    or ((out[(saved shl 1) + i].toInt() and 0xC0) ushr 6))).toByte()
        }

        // 3. Move back to the original range. This loop gets auto-vectorized on JDK13+.
        for (i in 0..<len) {
            val b = out[i]
            out[i] = (((b.toInt() and 0x1F) or 0x20 or ((b.toInt() and 0x20) shl 1)) - 1).toByte()
        }

        // 4. Restore exceptions
        val numExceptions: Int = `in`.readVInt()
        var i = 0
        for (exception in 0..<numExceptions) {
            i += `in`.readByte() and 0xFF.toByte()
            out[i] = `in`.readByte()
        }
    }
}
