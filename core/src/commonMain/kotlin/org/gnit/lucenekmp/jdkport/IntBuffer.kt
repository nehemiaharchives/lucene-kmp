package org.gnit.lucenekmp.jdkport

import okio.Buffer
import kotlin.math.min

/**
 * A platform-agnostic IntBuffer built on top of a kotlin‑io Buffer.
 *
 * The [capacity] is in numbers of ints (each int is 4 bytes). The
 * [position] and [limit] are expressed in ints. When reading/writing,
 * This version tracks an extra [baseOffset] (in bytes) so that view buffers (via slice())
 * share the same underlying Buffer while translating int indices appropriately.
 *
 * All indices (position, limit, capacity) are in units of ints (4 bytes).
 * we convert indices by multiplying by 4.
 */
class IntBuffer(private val buffer: Buffer, val capacity: Int, private val baseOffset: Long = 0L) : Comparable<IntBuffer> {

    var position: Int = 0
    var limit: Int = capacity
    var order: ByteOrder = ByteOrder.nativeOrder()

    companion object {
        /**
         * Allocates a new IntBuffer with the given capacity (in ints).
         * Internally, it creates a Buffer large enough to hold (capacity * 4) bytes.
         */
        fun allocate(capacity: Int): IntBuffer {
            require(capacity >= 0) { "Capacity must be non-negative" }
            val byteArray = ByteArray(capacity * 4)
            val buf = Buffer().apply { write(byteArray, 0, byteArray.size) }
            return IntBuffer(buf, capacity).clear()
        }

        /**
         * Wraps an existing IntArray into an IntBuffer.
         * The resulting buffer’s capacity and limit are set to array.size.
         */
        fun wrap(array: IntArray): IntBuffer {
            val byteArray = ByteArray(array.size * 4)
            val nativeOrder = ByteOrder.nativeOrder()
            for (i in array.indices) {
                val offset = i * 4
                val value = array[i]
                when (nativeOrder) {
                    ByteOrder.BIG_ENDIAN -> {
                        byteArray[offset]     = (value shr 24).toByte()
                        byteArray[offset + 1] = (value shr 16).toByte()
                        byteArray[offset + 2] = (value shr 8).toByte()
                        byteArray[offset + 3] = value.toByte()
                    }
                    ByteOrder.LITTLE_ENDIAN -> {
                        byteArray[offset]     = value.toByte()
                        byteArray[offset + 1] = (value shr 8).toByte()
                        byteArray[offset + 2] = (value shr 16).toByte()
                        byteArray[offset + 3] = (value shr 24).toByte()
                    }
                }
            }
            val buf = Buffer().apply { write(byteArray, 0, byteArray.size) }
            return IntBuffer(buf, array.size).clear()
        }
    }

    fun remaining(): Int = limit - position

    fun hasRemaining(): Boolean = position < limit

    fun clear(): IntBuffer {
        position = 0
        limit = capacity
        return this
    }

    fun flip(): IntBuffer {
        limit = position
        position = 0
        return this
    }

    fun duplicate(): IntBuffer {
        val newBuffer = buffer.copy()
        val dup = IntBuffer(newBuffer, capacity)
        dup.position = position
        dup.limit = limit
        dup.order = order
        return dup
    }

    fun get(): Int {
        if (position >= limit) throw BufferUnderflowException("No more ints to read")
        val result = get(position)
        position++
        return result
    }

    fun get(index: Int): Int {
        if (index < 0 || index >= limit)
            throw IndexOutOfBoundsException("Index $index out of bounds (limit: $limit)")
        val byteOffset = baseOffset + (index.toLong() * 4L)
        var result = 0
        when (order) {
            ByteOrder.BIG_ENDIAN -> {
                for (i in 0 until 4) {
                    result = (result shl 8) or (buffer.get(byteOffset + i).toInt() and 0xFF)
                }
            }
            ByteOrder.LITTLE_ENDIAN -> {
                for (i in 0 until 4) {
                    result = result or ((buffer.get(byteOffset + i).toInt() and 0xFF) shl (i * 8))
                }
            }
        }
        return result
    }

    fun position(newPosition: Int): IntBuffer {
        this.position = newPosition
        return this
    }

    fun limit(newLimit: Int): IntBuffer {
        this.limit = newLimit
        return this
    }

    fun put(value: Int): IntBuffer {
        if (position >= limit) throw BufferOverflowException("No space to write")
        put(position, value)
        position++
        return this
    }

    fun get(dst: IntArray, offset: Int, length: Int): IntBuffer {
        if (offset < 0 || length < 0 || offset + length > dst.size)
            throw IndexOutOfBoundsException("offset: $offset, length: $length, array size: ${dst.size}")

        val pos = this.position
        if (length > this.limit - pos)
            throw BufferUnderflowException("Not enough ints remaining: requested $length, available ${this.limit - pos}")

        for (i in 0 until length) {
            dst[offset + i] = this.get(pos + i)
        }
        this.position = pos + length
        return this
    }

    fun get(dst: IntArray): IntBuffer = get(dst, 0, dst.size)

    fun put(index: Int, value: Int): IntBuffer {
        if (index < 0 || index >= limit)
            throw IndexOutOfBoundsException("Index $index out of bounds (limit: $limit)")
        val byteOffset = baseOffset + (index.toLong() * 4L)
        when (order) {
            ByteOrder.BIG_ENDIAN -> {
                buffer.setByteAt(byteOffset,     (value shr 24).toByte())
                buffer.setByteAt(byteOffset + 1, (value shr 16).toByte())
                buffer.setByteAt(byteOffset + 2, (value shr 8).toByte())
                buffer.setByteAt(byteOffset + 3, value.toByte())
            }
            ByteOrder.LITTLE_ENDIAN -> {
                buffer.setByteAt(byteOffset,     value.toByte())
                buffer.setByteAt(byteOffset + 1, (value shr 8).toByte())
                buffer.setByteAt(byteOffset + 2, (value shr 16).toByte())
                buffer.setByteAt(byteOffset + 3, (value shr 24).toByte())
            }
        }
        return this
    }

    fun slice(): IntBuffer {
        val remainingInts = remaining()
        val newBaseOffset = baseOffset + (position.toLong() * 4L)
        return IntBuffer(buffer, remainingInts, newBaseOffset).clear().also {
            it.order = this.order
        }
    }

    override fun compareTo(other: IntBuffer): Int {
        val n = min(remaining(), other.remaining())
        for (i in 0 until n) {
            val cmp = get(this.position + i).compareTo(other.get(other.position + i))
            if (cmp != 0) return cmp
        }
        return remaining() - other.remaining()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntBuffer) return false

        if (this.remaining() != other.remaining()) return false

        val thisPos = this.position
        val otherPos = other.position
        for (i in 0 until remaining()) {
            if (this.get(thisPos + i) != other.get(otherPos + i)) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        val pos = this.position
        for (i in 0 until remaining()) {
            result = 31 * result + get(pos + i).hashCode()
        }
        return result
    }

    override fun toString(): String {
        return "IntBuffer(pos=$position, lim=$limit, cap=$capacity, order=$order)"
    }
}
