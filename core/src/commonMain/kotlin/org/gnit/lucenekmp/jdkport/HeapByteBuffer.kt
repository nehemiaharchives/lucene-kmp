package org.gnit.lucenekmp.jdkport

open class HeapByteBuffer

    : ByteBuffer {
    // For speed these fields are actually declared in X-Buffer;
    // these declarations are here as documentation
    /*
    protected final byte[] hb;
    protected final int offset;
    */
    constructor(cap: Int, lim: Int /*segment: java.lang.foreign.MemorySegment*/) : super(
        -1,
        0,
        lim,
        cap,
        ByteArray(cap),
        0,
        /*segment*/
    ) {            // package-private

        /*
        hb = new byte[cap];
        offset = 0;
        */
        //this.address = ARRAY_BASE_OFFSET
    }

    constructor(buf: ByteArray, off: Int, len: Int /*segment: java.lang.foreign.MemorySegment*/) : super(
        -1,
        off,
        off + len,
        buf.size,
        buf,
        0,
        /*segment*/
    ) { // package-private

        /*
        hb = buf;
        offset = 0;
        */
        //this.address = ARRAY_BASE_OFFSET
    }

    protected constructor(
        buf: ByteArray,
        mark: Int, pos: Int, lim: Int, cap: Int,
        off: Int, /*segment: java.lang.foreign.MemorySegment*/
    ) : super(mark, pos, lim, cap, buf, off /*segment*/) {
        /*
               hb = buf;
               offset = off;
               */
        //this.address = ARRAY_BASE_OFFSET + off * ARRAY_INDEX_SCALE
    }

    override fun slice(): ByteBuffer {
        val pos: Int = this.position()
        val lim: Int = this.limit()
        val rem = (if (pos <= lim) lim - pos else 0)
        return HeapByteBuffer(
            hb,
            -1,
            0,
            rem,
            rem,
            pos + offset, /*segment*/
        )
    }

    override fun slice(index: Int, length: Int): ByteBuffer {
        Objects.checkFromIndexSize(index, length, limit())
        return HeapByteBuffer(
            hb,
            -1,
            0,
            length,
            length,
            index + offset, /*segment*/
        )
    }

    override fun duplicate(): ByteBuffer {
        return HeapByteBuffer(
            hb,
            this.markValue(),
            this.position(),
            this.limit(),
            this.capacity(),
            offset, /*segment*/
        )
    }

    // TODO implement HeapByteBufferR
    override fun asReadOnlyBuffer(): ByteBuffer {
        return HeapByteBufferR(
            hb,
            this.markValue(),
            this.position(),
            this.limit(),
            this.capacity(),
            offset, /*segment*/
        )
    }


    protected fun ix(i: Int): Int {
        return i + offset
    }


    /*private fun byteOffset(i: Long): Long {
        return address + i
    }*/


    override fun get(): Byte {
        return hb[ix(nextGetIndex())]
    }

    override fun get(i: Int): Byte {
        return hb[ix(checkIndex(i))]
    }


    override fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        checkSession()
        Objects.checkFromIndexSize(offset, length, dst.size)
        val pos: Int = position()
        if (length > limit() - pos) throw BufferUnderflowException()
        System.arraycopy(hb, ix(pos), dst, offset, length)
        position(pos + length)
        return this
    }

    override fun get(index: Int, dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        checkSession()
        Objects.checkFromIndexSize(index, length, limit())
        Objects.checkFromIndexSize(offset, length, dst.size)
        System.arraycopy(hb, ix(index), dst, offset, length)
        return this
    }

    override val isDirect: Boolean
        get() = false


    override var isReadOnly: Boolean = false
        get() = false

    override fun put(x: Byte): ByteBuffer {
        hb[ix(nextPutIndex())] = x
        return this
    }

    override fun put(i: Int, x: Byte): ByteBuffer {
        hb[ix(checkIndex(i))] = x
        return this
    }

    override fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        checkSession()
        Objects.checkFromIndexSize(offset, length, src.size)
        val pos: Int = position()
        if (length > limit() - pos) throw BufferOverflowException()
        System.arraycopy(src, offset, hb, ix(pos), length)
        position(pos + length)
        return this
    }

    override fun put(src: ByteBuffer): ByteBuffer {
        checkSession()
        super.put(src)
        return this
    }

    override fun put(index: Int, src: ByteBuffer, offset: Int, length: Int): ByteBuffer {
        checkSession()
        super.put(index, src, offset, length)
        return this
    }

    override fun put(index: Int, src: ByteArray, offset: Int, length: Int): ByteBuffer {
        checkSession()
        Objects.checkFromIndexSize(index, length, limit())
        Objects.checkFromIndexSize(offset, length, src.size)
        System.arraycopy(src, offset, hb, ix(index), length)
        return this
    }


    override fun compact(): ByteBuffer {
        val pos: Int = position()
        val lim: Int = limit()
        assert(pos <= lim)
        val rem = (if (pos <= lim) lim - pos else 0)
        System.arraycopy(hb, ix(pos), hb, ix(0), rem)
        position(rem)
        limit(capacity())
        discardMark()
        return this
    }


    open fun _get(i: Int): Byte {                          // package-private
        return hb[i]
    }

    open fun _put(i: Int, b: Byte) {                  // package-private

        hb[i] = b
    }

    /** throws if this is read-only */
    private fun checkWritable() {
        if (isReadOnly) throw ReadOnlyBufferException("buffer is read-only")
    }


    /*override val char: Char
        // char
        get() = Buffer.SCOPED_MEMORY_ACCESS.getCharUnaligned(
            session(),
            hb,
            byteOffset(nextGetIndex(2).toLong()),
            bigEndian
        )*/

    override fun getChar(): Char {
        if (remaining() < 2)
            throw BufferUnderflowException("Not enough bytes remaining to read a char (need 2, have ${remaining()})")
        val value = getChar(position)
        position += 2
        return value
    }

    override fun getChar(i: Int): Char {
        /*  return Buffer.SCOPED_MEMORY_ACCESS.getCharUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(i, 2).toLong()),
            bigEndian)
        */

        if (i < 0 || limit - i < 2) {
            throw IndexOutOfBoundsException("Index $i out of bounds: need 2 bytes from index (limit: $limit)")
        }
        return if (order() == ByteOrder.BIG_ENDIAN) {
            val hi = hb[ix(i)].toInt() and 0xFF
            val lo = hb[ix(i + 1)].toInt() and 0xFF
            ((hi shl 8) or lo).toChar()
        } else {
            val lo = hb[ix(i)].toInt() and 0xFF
            val hi = hb[ix(i + 1)].toInt() and 0xFF
            ((hi shl 8) or lo).toChar()
        }
    }

    override fun putChar(value: Char): ByteBuffer {
        /*
        SCOPED_MEMORY_ACCESS.putCharUnaligned(session(), hb, byteOffset(nextPutIndex(2)), x, bigEndian);
        return this;
        */
        checkWritable()
        if (remaining() < 2) {
            throw BufferOverflowException("Not enough space to write 2 bytes at position $position with limit $limit")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[position] = (value.code shr 8).toByte()
            hb[position + 1] = value.code.toByte()
        } else {
            hb[position] = value.code.toByte()
            hb[position + 1] = (value.code shr 8).toByte()
        }
        position += 2
        return this
    }

    override fun putChar(index: Int, value: Char): ByteBuffer {

        /*SCOPED_MEMORY_ACCESS.putCharUnaligned(session(), hb, byteOffset(checkIndex(i, 2)), x, bigEndian);
        return this;*/

        checkWritable()
        if (index < 0 || limit - index < 2) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 2 bytes from index (limit: $limit)")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[ix(index)] = (value.code shr 8).toByte()
            hb[ix(index + 1)] = value.code.toByte()
        } else {
            hb[ix(index)] = value.code.toByte()
            hb[ix(index + 1)] = (value.code shr 8).toByte()
        }
        return this
    }

    override fun asCharBuffer(): CharBuffer {
        /*
        val pos: Int = position()
        val size: Int = (limit() - pos) shr 1
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsCharBufferB(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as CharBuffer
        else
            (ByteBufferAsCharBufferL(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as CharBuffer)
        */
        throw UnsupportedOperationException("asCharBuffer is not yet implemented")
    }


    /*override val short: Short
        // short
        get() = Buffer.SCOPED_MEMORY_ACCESS.getShortUnaligned(
            session(),
            hb,
            byteOffset(nextGetIndex(2).toLong()),
            bigEndian
        )*/
    override fun getShort(): Short {
        if (remaining() < 2)
            throw BufferUnderflowException("Not enough bytes remaining to read a short (need 2, have ${remaining()})")
        val value = if (bigEndian) {
            val hi = hb[position].toInt() and 0xFF
            val lo = hb[position + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        } else {
            val lo = hb[position].toInt() and 0xFF
            val hi = hb[position + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        }
        position += 2
        return value
    }

    override fun getShort(index: Int): Short {
        /*return Buffer.SCOPED_MEMORY_ACCESS.getShortUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(index, 2).toLong()),
            bigEndian
        )*/
        if (index < 0 || limit - index < 2) {
            throw IndexOutOfBoundsException(
                "Index $index out of bounds: need 2 bytes from index (limit: $limit)"
            )
        }
        return if (bigEndian) {
            val hi = hb[index].toInt() and 0xFF
            val lo = hb[index + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        } else {
            val lo = hb[index].toInt() and 0xFF
            val hi = hb[index + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        }
    }


    override fun putShort(value: Short): ByteBuffer {
        /*Buffer.SCOPED_MEMORY_ACCESS.putShortUnaligned(
            session(),
            hb,
            byteOffset(nextPutIndex(2).toLong()),
            x,
            bigEndian
        )
        return this*/

        checkWritable()
        if (remaining() < 2) {
            throw BufferOverflowException("Not enough space to write 2 bytes at position $position with limit $limit")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[position] = (value.toInt() shr 8).toByte()
            hb[position + 1] = value.toByte()
        } else {
            hb[position] = value.toByte()
            hb[position + 1] = (value.toInt() shr 8).toByte()
        }
        position += 2
        return this
    }

    override fun putShort(index: Int, value: Short): ByteBuffer {
        checkWritable()
        if (index < 0 || limit - index < 2) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 2 bytes from index (limit: $limit)")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[ix(index)] = (value.toInt() shr 8).toByte()
            hb[ix(index + 1)] = value.toByte()
        } else {
            hb[ix(index)] = value.toByte()
            hb[ix(index + 1)] = (value.toInt() shr 8).toByte()
        }
        return this
    }

    /*override fun asShortBuffer(): ShortBuffer {
        val pos: Int = position()
        val size: Int = (limit() - pos) shr 1
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsShortBufferB(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as ShortBuffer
        else
            (ByteBufferAsShortBufferL(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as ShortBuffer)
    }*/

    override fun getInt(): Int {
        /*Buffer.SCOPED_MEMORY_ACCESS.getIntUnaligned(
            session(),
            hb,
            byteOffset(nextGetIndex(4).toLong()),
            bigEndian
        )*/

        if (remaining() < 4)
            throw BufferUnderflowException("Not enough bytes remaining to read an int (need 4, have ${remaining()})")
        val value = getInt(position)
        position += 4
        return value
    }

    override fun getInt(index: Int): Int {
        /*return Buffer.SCOPED_MEMORY_ACCESS.getIntUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(i, 4).toLong()),
            bigEndian
        )*/
        if (index < 0 || limit - index < 4) {
            throw IndexOutOfBoundsException(
                "Index $index out of bounds: need 4 bytes from index (limit: $limit)"
            )
        }
        return if (bigEndian) {
            ((hb[index].toInt() and 0xFF) shl 24) or
                    ((hb[index + 1].toInt() and 0xFF) shl 16) or
                    ((hb[index + 2].toInt() and 0xFF) shl 8) or
                    (hb[index + 3].toInt() and 0xFF)
        } else {
            (hb[index].toInt() and 0xFF) or
                    ((hb[index + 1].toInt() and 0xFF) shl 8) or
                    ((hb[index + 2].toInt() and 0xFF) shl 16) or
                    ((hb[index + 3].toInt() and 0xFF) shl 24)
        }
    }


    override fun putInt(value: Int): ByteBuffer {
        /*Buffer.SCOPED_MEMORY_ACCESS.putIntUnaligned(
            session(),
            hb,
            byteOffset(nextPutIndex(4).toLong()),
            y,
            bigEndian
        )
        return this*/

        checkWritable()

        if (remaining() < 4) {
            throw BufferOverflowException("Not enough space to write 4 bytes at position $position with limit $limit")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[position] = (value shr 24).toByte()
            hb[position + 1] = (value shr 16).toByte()
            hb[position + 2] = (value shr 8).toByte()
            hb[position + 3] = value.toByte()
        } else {
            hb[position] = value.toByte()
            hb[position + 1] = (value shr 8).toByte()
            hb[position + 2] = (value shr 16).toByte()
            hb[position + 3] = (value shr 24).toByte()
        }
        position += 4
        return this
    }

    override fun putInt(index: Int, value: Int): ByteBuffer {
        /*
        Buffer.SCOPED_MEMORY_ACCESS.putIntUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(i, 4).toLong()),
            x,
            bigEndian
        )
        return this
        */

        checkWritable()
        if (index < 0 || limit - index < 4) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 4 bytes from index (limit: $limit)")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[ix(index)] = (value shr 24).toByte()
            hb[ix(index + 1)] = (value shr 16).toByte()
            hb[ix(index + 2)] = (value shr 8).toByte()
            hb[ix(index + 3)] = value.toByte()
        } else {
            hb[ix(index)] = value.toByte()
            hb[ix(index + 1)] = (value shr 8).toByte()
            hb[ix(index + 2)] = (value shr 16).toByte()
            hb[ix(index + 3)] = (value shr 24).toByte()
        }
        return this
    }

    override fun asIntBuffer(): IntBuffer {
        /*val pos: Int = position()
        val size: Int = (limit() - pos) shr 2
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsIntBufferB(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as IntBuffer
        else
            (ByteBufferAsIntBufferL(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as IntBuffer)*/

        val intCapacity = remaining() / 4
        if (intCapacity <= 0) {
            return IntBuffer.allocate(0).apply { order = this@HeapByteBuffer.order() }
        }
        val slice = duplicate()
        slice.position(position)
        slice.limit(position + (intCapacity * 4))
        val intBuffer = IntBuffer.allocate(intCapacity)
        intBuffer.clear()
        intBuffer.order = this.order()
        for (i in 0 until intCapacity) {
            val value = slice.getInt()
            intBuffer.put(value)
        }
        intBuffer.flip()
        return intBuffer
    }


    /*override val long: Long
        // long
        get() = Buffer.SCOPED_MEMORY_ACCESS.getLongUnaligned(
            session(),
            hb,
            byteOffset(nextGetIndex(8).toLong()),
            bigEndian
        )*/

    override fun getLong(): Long {
        if (remaining() < 8)
            throw BufferUnderflowException("Not enough bytes remaining to read a long (need 8, have ${remaining()})")
        val value = getLong(position)
        position += 8
        return value
    }

    override fun getLong(index: Int): Long {
        /*return Buffer.SCOPED_MEMORY_ACCESS.getLongUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(index, 8).toLong()),
            bigEndian
        )*/
        if (index < 0 || limit - index < 8) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 8 bytes from index (limit: $limit)")
        }
        // suspected bug code:
        return if (order() == ByteOrder.BIG_ENDIAN) {
            ((hb[index].toLong() and 0xFF) shl 56) or
                    ((hb[index + 1].toLong() and 0xFF) shl 48) or
                    ((hb[index + 2].toLong() and 0xFF) shl 40) or
                    ((hb[index + 3].toLong() and 0xFF) shl 32) or
                    ((hb[index + 4].toLong() and 0xFF) shl 24) or
                    ((hb[index + 5].toLong() and 0xFF) shl 16) or
                    ((hb[index + 6].toLong() and 0xFF) shl 8) or
                    (hb[index + 7].toLong() and 0xFF)
        } else {
            println("little endian detected. getLong: $index, ${hb[index]}, ${hb[index + 1]}, ${hb[index + 2]}, ${hb[index + 3]}, ${hb[index + 4]}, ${hb[index + 5]}, ${hb[index + 6]}, ${hb[index + 7]}")
            (hb[index].toLong() and 0xFF) or
                    ((hb[index + 1].toLong() and 0xFF) shl 8) or
                    ((hb[index + 2].toLong() and 0xFF) shl 16) or
                    ((hb[index + 3].toLong() and 0xFF) shl 24) or
                    ((hb[index + 4].toLong() and 0xFF) shl 32) or
                    ((hb[index + 5].toLong() and 0xFF) shl 40) or
                    ((hb[index + 6].toLong() and 0xFF) shl 48) or
                    ((hb[index + 7].toLong() and 0xFF) shl 56)
        }
    }


    override fun putLong(value: Long): ByteBuffer {
        checkWritable()
        if (remaining() < 8) {
            throw BufferOverflowException("Not enough space to write 8 bytes at position $position with limit $limit")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[position] = (value shr 56).toByte()
            hb[position + 1] = (value shr 48).toByte()
            hb[position + 2] = (value shr 40).toByte()
            hb[position + 3] = (value shr 32).toByte()
            hb[position + 4] = (value shr 24).toByte()
            hb[position + 5] = (value shr 16).toByte()
            hb[position + 6] = (value shr 8).toByte()
            hb[position + 7] = value.toByte()
        } else {
            hb[position] = value.toByte()
            hb[position + 1] = (value shr 8).toByte()
            hb[position + 2] = (value shr 16).toByte()
            hb[position + 3] = (value shr 24).toByte()
            hb[position + 4] = (value shr 32).toByte()
            hb[position + 5] = (value shr 40).toByte()
            hb[position + 6] = (value shr 48).toByte()
            hb[position + 7] = (value shr 56).toByte()
        }
        position += 8
        return this
    }

    /*override fun putLong(i: Int, x: Long): ByteBuffer {
        Buffer.SCOPED_MEMORY_ACCESS.putLongUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(i, 8).toLong()),
            x,
            bigEndian
        )
        return this
    }*/

    override fun putLong(index: Int, value: Long): ByteBuffer {
        /*
        // JDK reference:
        Buffer.SCOPED_MEMORY_ACCESS.putLongUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(i, 8).toLong()),
            x,
            bigEndian
        )
        return this
        */
        checkWritable()
        if (index < 0 || limit - index < 8) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 8 bytes from index (limit: $limit)")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[ix(index)] = (value shr 56).toByte()
            hb[ix(index + 1)] = (value shr 48).toByte()
            hb[ix(index + 2)] = (value shr 40).toByte()
            hb[ix(index + 3)] = (value shr 32).toByte()
            hb[ix(index + 4)] = (value shr 24).toByte()
            hb[ix(index + 5)] = (value shr 16).toByte()
            hb[ix(index + 6)] = (value shr 8).toByte()
            hb[ix(index + 7)] = value.toByte()
        } else {
            hb[ix(index)] = value.toByte()
            hb[ix(index + 1)] = (value shr 8).toByte()
            hb[ix(index + 2)] = (value shr 16).toByte()
            hb[ix(index + 3)] = (value shr 24).toByte()
            hb[ix(index + 4)] = (value shr 32).toByte()
            hb[ix(index + 5)] = (value shr 40).toByte()
            hb[ix(index + 6)] = (value shr 48).toByte()
            hb[ix(index + 7)] = (value shr 56).toByte()
        }
        return this
    }

    override fun asLongBuffer(): LongBuffer {
        /*val pos: Int = position()
        val size: Int = (limit() - pos) shr 3
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsLongBufferB(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as LongBuffer
        else
            (ByteBufferAsLongBufferL(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as LongBuffer)*/

        val longCapacity = remaining() / 8
        if (longCapacity <= 0) {
            return LongBuffer.allocate(0).apply { order = this@HeapByteBuffer.order() }
        }
        val slice = duplicate()
        slice.position(position)
        slice.limit(position + (longCapacity * 8))
        val longBuffer = LongBuffer.allocate(longCapacity)
        longBuffer.clear()
        longBuffer.order = this.order()
        println("order of longBuffer: ${longBuffer.order}")
        println("longBuffer before fill: $longBuffer, get(0): ${longBuffer.get(0)}")
        for (i in 0 until longCapacity) {
            val value = slice.getLong()
            longBuffer.put(value)
        }
        println("longBuffer after fill: $longBuffer, get(0): ${longBuffer.get(0)}")
        longBuffer.flip()
        println("longBuffer after flip: $longBuffer, get(0): ${longBuffer.get(0)}")
        return longBuffer
    }

    /*override val float: Float
        // float
        get() {
            val x: Int = Buffer.SCOPED_MEMORY_ACCESS.getIntUnaligned(
                session(),
                hb,
                byteOffset(nextGetIndex(4).toLong()),
                bigEndian
            )
            return Float.intBitsToFloat(x)
        }*/

    override fun getFloat(): Float {
        if (remaining() < 4)
            throw BufferUnderflowException("Not enough bytes remaining to read a float (need 4, have ${remaining()})")
        val value = getInt(position)
        position += 4
        return Float.fromBits(value)
    }

    /*override fun getFloat(i: Int): Float {
        val x: Int = Buffer.SCOPED_MEMORY_ACCESS.getIntUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(i, 4).toLong()),
            bigEndian
        )
        return Float.intBitsToFloat(x)
    }*/
    override fun getFloat(index: Int): Float {
        /*
        // JDK reference:
        public float getFloat(int i) {
            int x = Buffer.SCOPED_MEMORY_ACCESS.getIntUnaligned(
                session(),
                hb,
                byteOffset(checkIndex(i, 4).toLong()),
                bigEndian
            );
            return Float.intBitsToFloat(x);
        }
        */
        if (index < 0 || limit - index < 4) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 4 bytes from index (limit: $limit)")
        }
        val intBits = getInt(index)
        return Float.fromBits(intBits)
    }

    override fun putFloat(value: Float): ByteBuffer {
        /*
        val y = Float.floatToRawIntBits(x)
        Buffer.SCOPED_MEMORY_ACCESS.putIntUnaligned(
            session(),
            hb,
            byteOffset(nextPutIndex(4).toLong()),
            y,
            bigEndian
        )
        return this
        */
        val intBits = value.toRawBits()
        putInt(intBits)
        return this
    }

    override fun putFloat(index: Int, value: Float): ByteBuffer {
        /*
        val y = Float.floatToRawIntBits(x)
        Buffer.SCOPED_MEMORY_ACCESS.putIntUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(i, 4).toLong()),
            y,
            bigEndian
        )
        return this
        */
        checkWritable()
        if (index < 0 || limit - index < 4) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 4 bytes from index (limit: $limit)")
        }
        val intBits = value.toRawBits()
        putInt(index, intBits)
        return this
    }

    override fun asFloatBuffer(): FloatBuffer {
        /*val pos: Int = position()
        val size: Int = (limit() - pos) shr 2
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsFloatBufferB(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as FloatBuffer
        else
            (ByteBufferAsFloatBufferL(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as FloatBuffer)*/

        val floatCapacity = remaining() / 4
        if (floatCapacity <= 0) {
            return FloatBuffer.allocate(0).apply { order = this@HeapByteBuffer.order() }
        }
        val slice = duplicate()
        slice.position(position)
        slice.limit(position + (floatCapacity * 4))
        val floatBuffer = FloatBuffer.allocate(floatCapacity)
        floatBuffer.clear()
        floatBuffer.order = this.order()
        val floatArray = FloatArray(floatCapacity)
        for (i in 0 until floatCapacity) {
            val bits = when (order()) {
                ByteOrder.BIG_ENDIAN -> {
                    ((slice.get().toInt() and 0xFF) shl 24) or
                            ((slice.get().toInt() and 0xFF) shl 16) or
                            ((slice.get().toInt() and 0xFF) shl 8) or
                            (slice.get().toInt() and 0xFF)
                }

                else -> {
                    (slice.get().toInt() and 0xFF) or
                            ((slice.get().toInt() and 0xFF) shl 8) or
                            ((slice.get().toInt() and 0xFF) shl 16) or
                            ((slice.get().toInt() and 0xFF) shl 24)
                }
            }
            floatArray[i] = Float.fromBits(bits)
        }
        floatBuffer.put(floatArray)
        floatBuffer.flip()
        return floatBuffer
    }

    /*override val double: Double
        // double
        get() {
            val x: Long = Buffer.SCOPED_MEMORY_ACCESS.getLongUnaligned(
                session(),
                hb,
                byteOffset(nextGetIndex(8).toLong()),
                bigEndian
            )
            return Double.longBitsToDouble(x)
        }*/

    override fun getDouble(): Double {
        if (remaining() < 8)
            throw BufferUnderflowException("Not enough bytes remaining to read a double (need 8, have ${remaining()})")
        val value = getLong(position)
        position += 8
        return Double.fromBits(value)
    }

    override fun getDouble(index: Int): Double {
        /*
        val x: Long = Buffer.SCOPED_MEMORY_ACCESS.getLongUnaligned(
            session(),
            hb,
            byteOffset(checkIndex(i, 8).toLong()),
            bigEndian
        )
        return Double.longBitsToDouble(x)
        */
        if (index < 0 || limit - index < 8) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 8 bytes from index (limit: $limit)")
        }
        val longBits = getLong(index)
        return Double.fromBits(longBits)
    }

    override fun putDouble(value: Double): ByteBuffer {
        /*  val y = Double.doubleToRawLongBits(x)
            Buffer.SCOPED_MEMORY_ACCESS.putLongUnaligned(
                session(),
                hb,
                byteOffset(nextPutIndex(8).toLong()),
                y,
                bigEndian
            )
            return this
        */

        checkWritable()
        if (remaining() < 8) {
            throw BufferOverflowException("Not enough space to write 8 bytes at position $position with limit $limit")
        }
        val longBits = value.toRawBits()
        putLong(longBits)
        return this
    }

    override fun putDouble(i: Int, x: Double): ByteBuffer {
        /*  val y = Double.doubleToRawLongBits(x)
            Buffer.SCOPED_MEMORY_ACCESS.putLongUnaligned(
                session(),
                hb,
                byteOffset(checkIndex(i, 8).toLong()),
                y,
                bigEndian
            )
            return this
        */
        checkWritable()
        if (i < 0 || limit - i < 8) {
            throw IndexOutOfBoundsException("Index $i out of bounds: need 8 bytes from index (limit: $limit)")
        }
        val longBits = x.toRawBits()
        putLong(i, longBits)
        return this
    }

    /*override fun asDoubleBuffer(): DoubleBuffer {
        val pos: Int = position()
        val size: Int = (limit() - pos) shr 3
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsDoubleBufferB(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as DoubleBuffer
        else
            (ByteBufferAsDoubleBufferL(
                this,
                -1,
                0,
                size,
                size,
                addr, segment
            )) as DoubleBuffer)
    }*/


    /*@ForceInline*/
    override fun scaleShifts(): Int {
        return 0
    }

    /*@ForceInline*/
    /*override fun heapSegment(
        base: Any,
        offset: Long,
        length: Long,
        readOnly: Boolean,
        bufferScope: MemorySessionImpl
    ): AbstractMemorySegmentImpl {
        return SegmentFactories.arrayOfByteSegment(base, offset, length, readOnly, bufferScope)
    }*/


    companion object {
        // Cached array base offset
        //private val ARRAY_BASE_OFFSET = Buffer.UNSAFE.arrayBaseOffset(ByteArray::class.java).toLong()

        // Cached array index scale
        //private val ARRAY_INDEX_SCALE = Buffer.UNSAFE.arrayIndexScale(ByteArray::class.java).toLong()
    }
}
