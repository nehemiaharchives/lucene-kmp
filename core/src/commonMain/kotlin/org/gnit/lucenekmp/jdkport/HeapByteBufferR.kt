package org.gnit.lucenekmp.jdkport


/**
 *
 *
 *
 * A read-only HeapByteBuffer.  This class extends the corresponding
 * read/write class, overriding the mutation methods to throw a [ ] and overriding the view-buffer methods to return an
 * instance of this class rather than of the superclass.
 *
 */
class HeapByteBufferR

    : HeapByteBuffer {
    constructor(cap: Int, lim: Int, /*segment: java.lang.foreign.MemorySegment*/) : super(
        cap,
        lim,
        /*segment*/
    ) {            // package-private


        this.isReadOnly = true
    }

    constructor(buf: ByteArray, off: Int, len: Int, /*segment: java.lang.foreign.MemorySegment*/) : super(
        buf,
        off,
        len,
        /*segment*/
    ) { // package-private


        this.isReadOnly = true
    }

    constructor(
        buf: ByteArray,
        mark: Int, pos: Int, lim: Int, cap: Int,
        off: Int, /*segment: java.lang.foreign.MemorySegment*/
    ) : super(buf, mark, pos, lim, cap, off, /*segment*/) {
        this.isReadOnly = true
    }

    override fun slice(): ByteBuffer {
        val pos: Int = this.position()
        val lim: Int = this.limit()
        val rem = (if (pos <= lim) lim - pos else 0)
        return HeapByteBufferR(
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
        return HeapByteBufferR(
            hb,
            -1,
            0,
            length,
            length,
            index + offset, /*segment*/
        )
    }

    override fun duplicate(): ByteBuffer {
        return HeapByteBufferR(
            hb,
            this.markValue(),
            this.position(),
            this.limit(),
            this.capacity(),
            offset, /*segment*/
        )
    }

    override fun asReadOnlyBuffer(): ByteBuffer {
        return duplicate()
    }


    override var isReadOnly: Boolean
        get() = true

    override fun put(x: Byte): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun put(i: Int, x: Byte): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun put(src: ByteBuffer): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun put(index: Int, src: ByteBuffer, offset: Int, length: Int): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun put(index: Int, src: ByteArray, offset: Int, length: Int): ByteBuffer {
        throw ReadOnlyBufferException()
    }


    override fun compact(): ByteBuffer {
        throw ReadOnlyBufferException()
    }


    override fun _get(i: Int): Byte {                          // package-private
        return hb[i]
    }

    override fun _put(i: Int, b: Byte) {                  // package-private


        throw ReadOnlyBufferException()
    }


    // char
    override fun putChar(x: Char): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun putChar(i: Int, x: Char): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun asCharBuffer(): CharBuffer {
        /*val pos: Int = position()
        val size: Int = (limit() - pos) shr 1
        *//*val addr: Long = address + pos*//*
        return (if (bigEndian)
            (ByteBufferAsCharBufferRB(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as CharBuffer
        else
            (ByteBufferAsCharBufferRL(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as CharBuffer)*/

        throw UnsupportedOperationException("asCharBuffer is not supported in HeapByteBufferR")
    }


    // short
    override fun putShort(x: Short): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun putShort(i: Int, x: Short): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    /*override fun asShortBuffer(): ShortBuffer {
        val pos: Int = position()
        val size: Int = (limit() - pos) shr 1
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsShortBufferRB(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as ShortBuffer
        else
            (ByteBufferAsShortBufferRL(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as ShortBuffer)
    }*/


    // int
    override fun putInt(x: Int): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun putInt(i: Int, x: Int): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun asIntBuffer(): IntBuffer {
        /*val pos: Int = position()
        val size: Int = (limit() - pos) shr 2
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsIntBufferRB(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as IntBuffer
        else
            (ByteBufferAsIntBufferRL(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as IntBuffer)*/
        throw UnsupportedOperationException("asIntBuffer is not supported in HeapByteBufferR")
    }


    // long
    override fun putLong(x: Long): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun putLong(i: Int, x: Long): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun asLongBuffer(): LongBuffer {
        /*val pos: Int = position()
        val size: Int = (limit() - pos) shr 3
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsLongBufferRB(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as LongBuffer
        else
            (ByteBufferAsLongBufferRL(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as LongBuffer)*/
        throw UnsupportedOperationException("asLongBuffer is not supported in HeapByteBufferR")
    }


    // float
    override fun putFloat(x: Float): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun putFloat(i: Int, x: Float): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun asFloatBuffer(): FloatBuffer {
        /*val pos: Int = position()
        val size: Int = (limit() - pos) shr 2
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsFloatBufferRB(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as FloatBuffer
        else
            (ByteBufferAsFloatBufferRL(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as FloatBuffer)*/
        throw UnsupportedOperationException("asFloatBuffer is not supported in HeapByteBufferR")
    }


    // double
    override fun putDouble(x: Double): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    override fun putDouble(i: Int, x: Double): ByteBuffer {
        throw ReadOnlyBufferException()
    }

    /*override fun asDoubleBuffer(): DoubleBuffer {
        val pos: Int = position()
        val size: Int = (limit() - pos) shr 3
        val addr: Long = address + pos
        return (if (bigEndian)
            (ByteBufferAsDoubleBufferRB(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
            )) as DoubleBuffer
        else
            (ByteBufferAsDoubleBufferRL(
                this,
                -1,
                0,
                size,
                size,
                addr, *//*segment*//*
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
}
