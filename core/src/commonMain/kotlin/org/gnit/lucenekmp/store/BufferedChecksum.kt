package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.util.Checksum
import org.gnit.lucenekmp.util.putIntLE
import org.gnit.lucenekmp.util.putLongLE
import org.gnit.lucenekmp.util.putShortLE
import kotlin.jvm.JvmOverloads
import kotlin.math.min


/** Wraps another [Checksum] with an internal buffer to speed up checksum calculations.  */
class BufferedChecksum @JvmOverloads constructor(`in`: Checksum, bufferSize: Int = DEFAULT_BUFFERSIZE) :
    Checksum {
    private val `in`: Checksum
    private val buffer: ByteArray
    private var upto = 0

    /** Create a new BufferedChecksum with the specified bufferSize  */
    /** Create a new BufferedChecksum with [.DEFAULT_BUFFERSIZE]  */
    init {
        this.`in` = `in`
        this.buffer = ByteArray(bufferSize)
    }

    override fun update(b: Int) {
        if (upto == buffer.size) {
            flush()
        }
        buffer[upto++] = b.toByte()
    }

    override fun update(b: ByteArray, off: Int, len: Int) {
        if (len >= buffer.size) {
            flush()
            `in`.update(b, off, len)
        } else {
            if (upto + len > buffer.size) {
                flush()
            }
            /*java.lang.System.arraycopy(b, off, buffer, upto, len)*/
            buffer.copyInto(
                destination = buffer,
                destinationOffset = upto,
                startIndex = off,
                endIndex = off + len
            )
            upto += len
        }
    }

    /**
     * Writes [value] as a little-endian short into [buffer] at the current offset.
     *
     * If there isn’t enough room, [flush] is called.
     *
     * @param value the short value to write
     */
    fun updateShort(value: Short) {
        if (upto + 2 > buffer.size) flush()
        /*  BitUtil.VH_LE_SHORT.set(buffer, upto, `val`)*/
        buffer.putShortLE(upto, value)
        upto += Short.SIZE_BYTES
    }


    /**
     * Writes [value] as a little-endian int into [buffer] at the current offset.
     *
     * If there isn’t enough room, [flush] is called.
     *
     * @return the new offset (currentOffset + 4)
     */
    fun updateInt(value: Int) {
        if (upto + Int.SIZE_BYTES > buffer.size) flush()
        /*   BitUtil.VH_LE_INT.set(buffer, upto, `val`)     */
        buffer.putIntLE(upto, value)
        upto += Int.SIZE_BYTES
    }

    /**
     * Writes [value] as a little‑endian long into the internal buffer.
     * Flushes if there isn’t enough room.
     */
    fun updateLong(value: Long) {
        if (upto + Long.SIZE_BYTES > buffer.size) flush()
        /*BitUtil.VH_LE_LONG.set(buffer, upto, val);*/
        buffer.putLongLE(upto, value)
        upto += Long.SIZE_BYTES
    }

    /*fun updateLongsBeforePort(vals: LongArray, offset: Int, len: Int) {
        var offset = offset
        var len = len
        if (upto > 0) {
            val remainingCapacityInLong = min((buffer.size - upto) / Long.SIZE_BYTES, len)
            var i = 0
            while (i < remainingCapacityInLong) {
                updateLong(vals[offset])
                i++
                offset++
                len--
            }
            if (0 == len) return
        }

        val b: java.nio.LongBuffer =
            java.nio.ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        val capacityInLong: Int = buffer.size / Long.SIZE_BYTES
        while (len > 0) {
            flush()
            val l = min(capacityInLong, len)
            b.put(0, vals, offset, l)
            upto += l * Long.SIZE_BYTES
            offset += l
            len -= l
        }
    }
    */
    /**
     * Updates the checksum by writing multiple long values from [vals] starting at [offset]
     * for [len] entries. It writes as many longs as possible into the internal [buffer] (which is
     * backed by a ByteArray) in little‑endian order. When there isn’t enough space,
     * [flush] is called.
     */
    fun updateLongs(vals: LongArray, offset: Int, len: Int) {
        var off = offset
        var remaining = len

        // First, if there's data already in the buffer, try to fill the remaining capacity.
        if (upto > 0) {
            val availableCapacityInLong = min((buffer.size - upto) / Long.SIZE_BYTES, remaining)
            repeat(availableCapacityInLong) {
                updateLong(vals[off])
                off++
                remaining--
            }
            if (remaining == 0) return
        }

        // Now, write remaining longs in bulk.
        val capacityInLong = buffer.size / Long.SIZE_BYTES
        while (remaining > 0) {
            flush() // flush the buffer so that upto becomes 0
            val l = min(capacityInLong, remaining)
            // "Bulk" write l longs into the buffer at once.
            for (i in 0 until l) {
                buffer.putLongLE(i * Long.SIZE_BYTES, vals[off + i])
            }
            upto = l * Long.SIZE_BYTES
            off += l
            remaining -= l
        }
    }

    override fun getValue(): Long {
        flush()
        return `in`.getValue()
    }


    override fun reset() {
        upto = 0
        `in`.reset()
    }

    private fun flush() {
        if (upto > 0) {
            `in`.update(buffer, 0, upto)
        }
        upto = 0
    }

    companion object {
        /** Default buffer size: 1024  */
        const val DEFAULT_BUFFERSIZE: Int = 1024
    }
}
