package org.gnit.lucenekmp.jdkport

import kotlin.math.min

/**
 * A byte buffer.
 *
 *
 *  This class defines six categories of operations upon
 * byte buffers:
 *
 *
 *
 *  *
 *
 * Absolute and relative [&lt;i&gt;get&lt;/i&gt;][.get] and
 * [&lt;i&gt;put&lt;/i&gt;][.put] methods that read and write
 * single bytes;
 *
 *  *
 *
 * Absolute and relative [&lt;i&gt;bulk get&lt;/i&gt;][.get]
 * methods that transfer contiguous sequences of bytes from this buffer
 * into an array;
 *
 *  *
 *
 * Absolute and relative [&lt;i&gt;bulk put&lt;/i&gt;][.put]
 * methods that transfer contiguous sequences of bytes from a
 * byte array or some other byte
 * buffer into this buffer;
 *
 *
 *
 *  *
 *
 * Absolute and relative [&lt;i&gt;get&lt;/i&gt;][.getChar]
 * and [&lt;i&gt;put&lt;/i&gt;][.putChar] methods that read and
 * write values of other primitive types, translating them to and from
 * sequences of bytes in a particular byte order;
 *
 *  *
 *
 * Methods for creating *[view buffers](#views)*,
 * which allow a byte buffer to be viewed as a buffer containing values of
 * some other primitive type; and
 *
 *
 *
 *  *
 *
 * A method for [compacting][.compact]
 * a byte buffer.
 *
 *
 *
 *
 *  Byte buffers can be created either by [ &lt;i&gt;allocation&lt;/i&gt;][.allocate], which allocates space for the buffer's
 *
 *
 *
 * content, or by [&lt;i&gt;wrapping&lt;/i&gt;][.wrap] an
 * existing byte array into a buffer.
 *
 *
 *
 * <a id="direct"></a>
 * <h2> Direct *vs.* non-direct buffers </h2>
 *
 *
 *  A byte buffer is either *direct* or *non-direct*.  Given a
 * direct byte buffer, the Java virtual machine will make a best effort to
 * perform native I/O operations directly upon it.  That is, it will attempt to
 * avoid copying the buffer's content to (or from) an intermediate buffer
 * before (or after) each invocation of one of the underlying operating
 * system's native I/O operations.
 *
 *
 *  A direct byte buffer may be created by invoking the [ ][.allocateDirect] factory method of this class.  The
 * buffers returned by this method typically have somewhat higher allocation
 * and deallocation costs than non-direct buffers.  The contents of direct
 * buffers may reside outside of the normal garbage-collected heap, and so
 * their impact upon the memory footprint of an application might not be
 * obvious.  It is therefore recommended that direct buffers be allocated
 * primarily for large, long-lived buffers that are subject to the underlying
 * system's native I/O operations.  In general it is best to allocate direct
 * buffers only when they yield a measurable gain in program performance.
 *
 *
 *  A direct byte buffer may also be created by [ ][java.nio.channels.FileChannel.map] a region of a file
 * directly into memory.  An implementation of the Java platform may optionally
 * support the creation of direct byte buffers from native code via JNI.  If an
 * instance of one of these kinds of buffers refers to an inaccessible region
 * of memory then an attempt to access that region will not change the buffer's
 * content and will cause an unspecified exception to be thrown either at the
 * time of the access or at some later time.
 *
 *
 *  Whether a byte buffer is direct or non-direct may be determined by
 * invoking its [isDirect][.isDirect] method.  This method is provided so
 * that explicit buffer management can be done in performance-critical code.
 *
 *
 * <a id="bin"></a>
 * <h2> Access to binary data </h2>
 *
 *
 *  This class defines methods for reading and writing values of all other
 * primitive types, except `boolean`.  Primitive values are translated
 * to (or from) sequences of bytes according to the buffer's current byte
 * order, which may be retrieved and modified via the [order][.order]
 * methods.  Specific byte orders are represented by instances of the [ ] class.  The initial order of a byte buffer is always [ ][ByteOrder.BIG_ENDIAN].
 *
 *
 *  For access to heterogeneous binary data, that is, sequences of values of
 * different types, this class defines a family of absolute and relative
 * *get* and *put* methods for each type.  For 32-bit floating-point
 * values, for example, this class defines:
 *
 * {@snippet lang=java :
 * *     // @link substring="getFloat()" target="#getFloat" :
 * *     float      getFloat()
 * *     // @link substring="getFloat(int index)" target="#getFloat(int)" :
 * *     float      getFloat(int index)
 * *     // @link substring="putFloat(float f)" target="#putFloat(float)" :
 * *     ByteBuffer putFloat(float f)
 * *     // @link substring="putFloat(int index, float f)" target="#putFloat(int,float)" :
 * *     ByteBuffer putFloat(int index, float f)
 * * }
 *
 *
 *  Corresponding methods are defined for the types `char,
 * short, int, long`, and `double`.  The index
 * parameters of the absolute *get* and *put* methods are in terms of
 * bytes rather than of the type being read or written.
 *
 * <a id="views"></a>
 *
 *
 *  For access to homogeneous binary data, that is, sequences of values of
 * the same type, this class defines methods that can create *views* of a
 * given byte buffer.  A *view buffer* is simply another buffer whose
 * content is backed by the byte buffer.  Changes to the byte buffer's content
 * will be visible in the view buffer, and vice versa; the two buffers'
 * position, limit, and mark values are independent.  The [ ][.asFloatBuffer] method, for example, creates an instance of
 * the [FloatBuffer] class that is backed by the byte buffer upon which
 * the method is invoked.  Corresponding view-creation methods are defined for
 * the types `char, short, int, long`, and `double`.
 *
 *
 *  View buffers have three important advantages over the families of
 * type-specific *get* and *put* methods described above:
 *
 *
 *
 *  *
 *
 * A view buffer is indexed not in terms of bytes but rather in terms
 * of the type-specific size of its values;
 *
 *  *
 *
 * A view buffer provides relative bulk *get* and *put*
 * methods that can transfer contiguous sequences of values between a buffer
 * and an array or some other buffer of the same type; and
 *
 *  *
 *
 * A view buffer is potentially much more efficient because it will
 * be direct if, and only if, its backing byte buffer is direct.
 *
 *
 *
 *
 *  The byte order of a view buffer is fixed to be that of its byte buffer
 * at the time that the view is created.
 *
 *
 * <h2> Invocation chaining </h2>
 *
 *
 *
 *  Methods in this class that do not otherwise have a value to return are
 * specified to return the buffer upon which they are invoked.  This allows
 * method invocations to be chained.
 *
 *
 *
 * The sequence of statements
 *
 * {@snippet lang=java :
 * *     bb.putInt(0xCAFEBABE);
 * *     bb.putShort(3);
 * *     bb.putShort(45);
 * * }
 *
 * can, for example, be replaced by the single statement
 *
 * {@snippet lang=java :
 * *     bb.putInt(0xCAFEBABE).putShort(3).putShort(45);
 * * }
 *
 *
 *
 * <h2> Optional operations </h2>
 * Methods specified as
 * *[-only-buffers-heading optional][Buffer]* throw a [ReadOnlyBufferException] when invoked
 * on a [read-only][isReadOnly] ByteBuffer. The
 * methods [array][.array] and [arrayOffset][.arrayOffset]
 * throw an [UnsupportedOperationException] if the ByteBuffer is
 * not backed by an [accessible byte array][Buffer.hasArray]
 * (irrespective of whether the ByteBuffer is read-only).
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 *
 * @sealedGraph
 *
 * @since 1.4
 */
@Ported(from = "java.nio.ByteBuffer")
abstract class ByteBuffer

    : Buffer, Comparable<ByteBuffer> {
    // These fields are declared here rather than in Heap-X-Buffer in order to
    // reduce the number of virtual method invocations needed to access these
    // values, which is especially costly when coding small buffers.
    //
    val hb: ByteArray // Non-null only for heap buffers
    val offset: Int
    override var isReadOnly: Boolean = false

    // Creates a new buffer with the given mark, position, limit, capacity,
    // backing array, and array offset
    //
    internal constructor(
        mark: Int, pos: Int, lim: Int, cap: Int,  // package-private
        hb: ByteArray, offset: Int, /*segment: java.lang.foreign.MemorySegment*/
    ) : super(mark, pos, lim, cap, /*segment*/) {
        this.hb = hb
        this.offset = offset
    }

    // Creates a new buffer with the given mark, position, limit, and capacity
    //
    /*internal constructor(
        mark: Int,
        pos: Int,
        lim: Int,
        cap: Int,
        *//*segment: java.lang.foreign.MemorySegment*//*
    ) : this(mark, pos, lim, cap, null, 0, *//*segment*//*)*/

    // Creates a new buffer with given base, address and capacity
    //
    internal constructor(hb: ByteArray, addr: Long, cap: Int, /*segment: java.lang.foreign.MemorySegment*/) : super(
        /*addr,*/
        cap,
        /*segment*/
    ) { // package-private
        this.hb = hb
        this.offset = 0
    }

    override fun base(): Any {
        return hb
    }


    /**
     * Creates a new byte buffer whose content is a shared subsequence of
     * this buffer's content.
     *
     *
     *  The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer, its mark will be
     * undefined, and its byte order will be
     *
     * [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
     *
     *
     *
     * The new buffer will be direct if, and only if, this buffer is direct, and
     * it will be read-only if, and only if, this buffer is read-only.
     *
     * @return  The new byte buffer
     *
     *
     * @see .alignedSlice
     */
    abstract override fun slice(): ByteBuffer

    /**
     * Creates a new byte buffer whose content is a shared subsequence of
     * this buffer's content.
     *
     *
     *  The content of the new buffer will start at position `index`
     * in this buffer, and will contain `length` elements. Changes to
     * this buffer's content will be visible in the new buffer, and vice versa;
     * the two buffers' position, limit, and mark values will be independent.
     *
     *
     *  The new buffer's position will be zero, its capacity and its limit
     * will be `length`, its mark will be undefined, and its byte order
     * will be
     *
     * [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
     *
     *
     *
     * The new buffer will be direct if, and only if, this buffer is direct,
     * and it will be read-only if, and only if, this buffer is read-only.
     *
     * @param   index
     * The position in this buffer at which the content of the new
     * buffer will start; must be non-negative and no larger than
     * [limit()][.limit]
     *
     * @param   length
     * The number of elements the new buffer will contain; must be
     * non-negative and no larger than `limit() - index`
     *
     * @return  The new buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative or greater than `limit()`,
     * `length` is negative, or `length > limit() - index`
     *
     * @since 13
     */
    abstract override fun slice(index: Int, length: Int): ByteBuffer

    /**
     * Creates a new byte buffer that shares this buffer's content.
     *
     *
     *  The content of the new buffer will be that of this buffer.  Changes
     * to this buffer's content will be visible in the new buffer, and vice
     * versa; the two buffers' position, limit, and mark values will be
     * independent.
     *
     *
     *  The new buffer's capacity, limit, position,
     *
     * and mark values will be identical to those of this buffer, and its byte
     * order will be [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
     *
     *
     *
     * The new buffer will be direct if, and only if, this buffer is direct, and
     * it will be read-only if, and only if, this buffer is read-only.
     *
     * @return  The new byte buffer
     */
    abstract override fun duplicate(): ByteBuffer

    /**
     * Creates a new, read-only byte buffer that shares this buffer's
     * content.
     *
     *
     *  The content of the new buffer will be that of this buffer.  Changes
     * to this buffer's content will be visible in the new buffer; the new
     * buffer itself, however, will be read-only and will not allow the shared
     * content to be modified.  The two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's capacity, limit, position,
     *
     * and mark values will be identical to those of this buffer, and its byte
     * order will be [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
     *
     *
     *
     *
     *
     *  If this buffer is itself read-only then this method behaves in
     * exactly the same way as the [duplicate][.duplicate] method.
     *
     * @return  The new, read-only byte buffer
     */
    abstract fun asReadOnlyBuffer(): ByteBuffer


    // -- Singleton get/put methods --
    /**
     * Relative *get* method.  Reads the byte at this buffer's
     * current position, and then increments the position.
     *
     * @return  The byte at the buffer's current position
     *
     * @throws  BufferUnderflowException
     * If the buffer's current position is not smaller than its limit
     */
    abstract fun get(): Byte

    /**
     * Relative *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes the given byte into this buffer at the current
     * position, and then increments the position.
     *
     * @param  b
     * The byte to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If this buffer's current position is not smaller than its limit
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun put(b: Byte): ByteBuffer

    /**
     * Absolute *get* method.  Reads the byte at the given
     * index.
     *
     * @param  index
     * The index from which the byte will be read
     *
     * @return  The byte at the given index
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit
     */
    abstract fun get(index: Int): Byte


    /**
     * Absolute *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes the given byte into this buffer at the given
     * index.
     *
     * @param  index
     * The index at which the byte will be written
     *
     * @param  b
     * The byte value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun put(index: Int, b: Byte): ByteBuffer


    // -- Bulk get operations --
    /**
     * Relative bulk *get* method.
     *
     *
     *  This method transfers bytes from this buffer into the given
     * destination array.  If there are fewer bytes remaining in the
     * buffer than are required to satisfy the request, that is, if
     * `length`&nbsp;`>`&nbsp;`remaining()`, then no
     * bytes are transferred and a [BufferUnderflowException] is
     * thrown.
     *
     *
     *  Otherwise, this method copies `length` bytes from this
     * buffer into the given array, starting at the current position of this
     * buffer and at the given offset in the array.  The position of this
     * buffer is then incremented by `length`.
     *
     *
     *  In other words, an invocation of this method of the form
     * `src.get(dst,&nbsp;off,&nbsp;len)` has exactly the same effect as
     * the loop
     *
     * {@snippet lang=java :
     * *     for (int i = off; i < off + len; i++)
     * *         dst[i] = src.get();
     * * }
     *
     * except that it first checks that there are sufficient bytes in
     * this buffer and it is potentially much more efficient.
     *
     * @param  dst
     * The array into which bytes are to be written
     *
     * @param  offset
     * The offset within the array of the first byte to be
     * written; must be non-negative and no larger than
     * `dst.length`
     *
     * @param  length
     * The maximum number of bytes to be written to the given
     * array; must be non-negative and no larger than
     * `dst.length - offset`
     *
     * @return  This buffer
     *
     * @throws  BufferUnderflowException
     * If there are fewer than `length` bytes
     * remaining in this buffer
     *
     * @throws  IndexOutOfBoundsException
     * If the preconditions on the `offset` and `length`
     * parameters do not hold
     */
    open fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        Objects.checkFromIndexSize(offset, length, dst.size)
        val pos: Int = position()
        if (length > limit() - pos) throw BufferUnderflowException()

        getArray(pos, dst, offset, length)

        position(pos + length)
        return this
    }

    /**
     * Relative bulk *get* method.
     *
     *
     *  This method transfers bytes from this buffer into the given
     * destination array.  An invocation of this method of the form
     * `src.get(a)` behaves in exactly the same way as the invocation
     *
     * {@snippet lang=java :
     * *     src.get(a, 0, a.length)
     * * }
     *
     * @param   dst
     * The destination array
     *
     * @return  This buffer
     *
     * @throws  BufferUnderflowException
     * If there are fewer than `length` bytes
     * remaining in this buffer
     */
    fun get(dst: ByteArray): ByteBuffer {
        return get(dst, 0, dst.size)
    }

    /**
     * Absolute bulk *get* method.
     *
     *
     *  This method transfers `length` bytes from this
     * buffer into the given array, starting at the given index in this
     * buffer and at the given offset in the array.  The position of this
     * buffer is unchanged.
     *
     *
     *  An invocation of this method of the form
     * `src.get(index,&nbsp;dst,&nbsp;offset,&nbsp;length)`
     * has exactly the same effect as the following loop except that it first
     * checks the consistency of the supplied parameters and it is potentially
     * much more efficient:
     *
     * {@snippet lang=java :
     * *     for (int i = offset, j = index; i < offset + length; i++, j++)
     * *         dst[i] = src.get(j);
     * * }
     *
     * @param  index
     * The index in this buffer from which the first byte will be
     * read; must be non-negative and less than `limit()`
     *
     * @param  dst
     * The destination array
     *
     * @param  offset
     * The offset within the array of the first byte to be
     * written; must be non-negative and less than
     * `dst.length`
     *
     * @param  length
     * The number of bytes to be written to the given array;
     * must be non-negative and no larger than the smaller of
     * `limit() - index` and `dst.length - offset`
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If the preconditions on the `index`, `offset`, and
     * `length` parameters do not hold
     *
     * @since 13
     */
    open fun get(index: Int, dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        Objects.checkFromIndexSize(index, length, limit())
        Objects.checkFromIndexSize(offset, length, dst.size)

        getArray(index, dst, offset, length)

        return this
    }

    /**
     * Absolute bulk *get* method.
     *
     *
     *  This method transfers bytes from this buffer into the given
     * destination array.  The position of this buffer is unchanged.  An
     * invocation of this method of the form
     * `src.get(index,&nbsp;dst)` behaves in exactly the same
     * way as the invocation:
     *
     * {@snippet lang=java :
     * *     src.get(index, dst, 0, dst.length)
     * * }
     *
     * @param  index
     * The index in this buffer from which the first byte will be
     * read; must be non-negative and less than `limit()`
     *
     * @param  dst
     * The destination array
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative, not smaller than `limit()`,
     * or `limit() - index < dst.length`
     *
     * @since 13
     */
    fun get(index: Int, dst: ByteArray): ByteBuffer {
        return get(index, dst, 0, dst.size)
    }

    private fun getArray(index: Int, dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        /*if ((length.toLong() shl 0) > Bits.JNI_COPY_TO_ARRAY_THRESHOLD) {
            val bufAddr: Long = address + (index.toLong() shl 0)
            val dstOffset =
                ARRAY_BASE_OFFSET + (offset.toLong() shl 0)
            val len = length.toLong() shl 0

            try {
                SCOPED_MEMORY_ACCESS.copyMemory(
                    session(), null, base(), bufAddr,
                    dst, dstOffset, len
                )
            } finally {
                java.lang.ref.Reference.reachabilityFence(this)
            }
        } else {*/
            val end = offset + length
            var i = offset
            var j = index
            while (i < end) {
                dst[i] = get(j)
                i++
                j++
            }
        //}
        return this
    }

    // -- Bulk put operations --
    /**
     * Relative bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method transfers the bytes remaining in the given source
     * buffer into this buffer.  If there are more bytes remaining in the
     * source buffer than in this buffer, that is, if
     * `src.remaining()`&nbsp;`>`&nbsp;`remaining()`,
     * then no bytes are transferred and a [ ] is thrown.
     *
     *
     *  Otherwise, this method copies
     * *n*&nbsp;=&nbsp;`src.remaining()` bytes from the given
     * buffer into this buffer, starting at each buffer's current position.
     * The positions of both buffers are then incremented by *n*.
     *
     *
     *  In other words, an invocation of this method of the form
     * `dst.put(src)` has exactly the same effect as the loop
     *
     * {@snippet lang=java :
     * *     while (src.hasRemaining())
     * *         dst.put(src.get());
     * * }
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient.  If this buffer and
     * the source buffer share the same backing array or memory, then the
     * result will be as if the source elements were first copied to an
     * intermediate location before being written into this buffer.
     *
     * @param  src
     * The source buffer from which bytes are to be read;
     * must not be this buffer
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there is insufficient space in this buffer
     * for the remaining bytes in the source buffer
     *
     * @throws  IllegalArgumentException
     * If the source buffer is this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    open fun put(src: ByteBuffer): ByteBuffer {
        if (src === this) throw createSameBufferException()
        if (isReadOnly) throw ReadOnlyBufferException()

        val srcPos: Int = src.position()
        val srcLim: Int = src.limit()
        val srcRem = (if (srcPos <= srcLim) srcLim - srcPos else 0)
        val pos: Int = position()
        val lim: Int = limit()
        val rem = (if (pos <= lim) lim - pos else 0)

        if (srcRem > rem) throw BufferOverflowException()

        putBuffer(pos, src, srcPos, srcRem)

        position(pos + srcRem)
        src.position(srcPos + srcRem)

        return this
    }

    /**
     * Absolute bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method transfers `length` bytes into this buffer from
     * the given source buffer, starting at the given `offset` in the
     * source buffer and the given `index` in this buffer. The positions
     * of both buffers are unchanged.
     *
     *
     *  In other words, an invocation of this method of the form
     * `dst.put(index,&nbsp;src,&nbsp;offset,&nbsp;length)`
     * has exactly the same effect as the loop
     *
     * {@snippet lang=java :
     * *     for (int i = offset, j = index; i < offset + length; i++, j++)
     * *         dst.put(j, src.get(i));
     * * }
     *
     * except that it first checks the consistency of the supplied parameters
     * and it is potentially much more efficient.  If this buffer and
     * the source buffer share the same backing array or memory, then the
     * result will be as if the source elements were first copied to an
     * intermediate location before being written into this buffer.
     *
     * @param index
     * The index in this buffer at which the first byte will be
     * written; must be non-negative and less than `limit()`
     *
     * @param src
     * The buffer from which bytes are to be read
     *
     * @param offset
     * The index within the source buffer of the first byte to be
     * read; must be non-negative and less than `src.limit()`
     *
     * @param length
     * The number of bytes to be read from the given buffer;
     * must be non-negative and no larger than the smaller of
     * `limit() - index` and `src.limit() - offset`
     *
     * @return This buffer
     *
     * @throws IndexOutOfBoundsException
     * If the preconditions on the `index`, `offset`, and
     * `length` parameters do not hold
     *
     * @throws ReadOnlyBufferException
     * If this buffer is read-only
     *
     * @since 16
     */
    open fun put(index: Int, src: ByteBuffer, offset: Int, length: Int): ByteBuffer {
        Objects.checkFromIndexSize(index, length, limit())
        Objects.checkFromIndexSize(offset, length, src.limit())
        if (isReadOnly) throw ReadOnlyBufferException()

        putBuffer(index, src, offset, length)

        return this
    }

    fun putBuffer(pos: Int, src: ByteBuffer, srcPos: Int, n: Int) {
        // Copy bytes one by one using get/put operations
        // This is platform-agnostic and works with both heap and direct buffers
        for (i in 0 until n) {
            val srcByte = src.get(srcPos + i)
            this.put(pos + i, srcByte)
        }

        // Original JDK implementation (commented out for platform-agnostic porting):
        /*
        val srcBase = src.base()
        assert(srcBase != null || src.isDirect)
        val base = base()
        assert(base != null || this.isDirect)
        val srcAddr: Long = src.address + (srcPos.toLong() shl 0)
        val addr: Long = address + (pos.toLong() shl 0)
        val len = n.toLong() shl 0

        try {
            SCOPED_MEMORY_ACCESS.copyMemory(
                src.session(), session(), srcBase, srcAddr,
                base, addr, len
            )
        } finally {
            java.lang.ref.Reference.reachabilityFence(src)
            java.lang.ref.Reference.reachabilityFence(this)
        }
        */
    }

    /**
     * Relative bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method transfers bytes into this buffer from the given
     * source array.  If there are more bytes to be copied from the array
     * than remain in this buffer, that is, if
     * `length`&nbsp;`>`&nbsp;`remaining()`, then no
     * bytes are transferred and a [BufferOverflowException] is
     * thrown.
     *
     *
     *  Otherwise, this method copies `length` bytes from the
     * given array into this buffer, starting at the given offset in the array
     * and at the current position of this buffer.  The position of this buffer
     * is then incremented by `length`.
     *
     *
     *  In other words, an invocation of this method of the form
     * `dst.put(src,&nbsp;off,&nbsp;len)` has exactly the same effect as
     * the loop
     *
     * {@snippet lang=java :
     * *     for (int i = off; i < off + len; i++)
     * *         dst.put(src[i]);
     * * }
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient.
     *
     * @param  src
     * The array from which bytes are to be read
     *
     * @param  offset
     * The offset within the array of the first byte to be read;
     * must be non-negative and no larger than `src.length`
     *
     * @param  length
     * The number of bytes to be read from the given array;
     * must be non-negative and no larger than
     * `src.length - offset`
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there is insufficient space in this buffer
     *
     * @throws  IndexOutOfBoundsException
     * If the preconditions on the `offset` and `length`
     * parameters do not hold
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    open fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (isReadOnly) throw ReadOnlyBufferException()
        Objects.checkFromIndexSize(offset, length, src.size)
        val pos: Int = position()
        if (length > limit() - pos) throw BufferOverflowException()

        putArray(pos, src, offset, length)

        position(pos + length)
        return this
    }

    /**
     * Relative bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method transfers the entire content of the given source
     * byte array into this buffer.  An invocation of this method of the
     * form `dst.put(a)` behaves in exactly the same way as the
     * invocation
     *
     * {@snippet lang=java :
     * *     dst.put(a, 0, a.length)
     * * }
     *
     * @param   src
     * The source array
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there is insufficient space in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    fun put(src: ByteArray): ByteBuffer {
        return put(src, 0, src.size)
    }

    /**
     * Absolute bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method transfers `length` bytes from the given
     * array, starting at the given offset in the array and at the given index
     * in this buffer.  The position of this buffer is unchanged.
     *
     *
     *  An invocation of this method of the form
     * `dst.put(index,&nbsp;src,&nbsp;offset,&nbsp;length)`
     * has exactly the same effect as the following loop except that it first
     * checks the consistency of the supplied parameters and it is potentially
     * much more efficient:
     *
     * {@snippet lang=java :
     * *     for (int i = offset, j = index; i < offset + length; i++, j++)
     * *         dst.put(j, src[i]);
     * * }
     *
     * @param  index
     * The index in this buffer at which the first byte will be
     * written; must be non-negative and less than `limit()`
     *
     * @param  src
     * The array from which bytes are to be read
     *
     * @param  offset
     * The offset within the array of the first byte to be read;
     * must be non-negative and less than `src.length`
     *
     * @param  length
     * The number of bytes to be read from the given array;
     * must be non-negative and no larger than the smaller of
     * `limit() - index` and `src.length - offset`
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If the preconditions on the `index`, `offset`, and
     * `length` parameters do not hold
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     *
     * @since 13
     */
    open fun put(index: Int, src: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (isReadOnly) throw ReadOnlyBufferException()
        Objects.checkFromIndexSize(index, length, limit())
        Objects.checkFromIndexSize(offset, length, src.size)

        putArray(index, src, offset, length)

        return this
    }

    /**
     * Absolute bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method copies bytes into this buffer from the given source
     * array.  The position of this buffer is unchanged.  An invocation of this
     * method of the form `dst.put(index,&nbsp;src)`
     * behaves in exactly the same way as the invocation:
     *
     * {@snippet lang=java :
     * *     dst.put(index, src, 0, src.length);
     * * }
     *
     * @param  index
     * The index in this buffer at which the first byte will be
     * written; must be non-negative and less than `limit()`
     *
     * @param  src
     * The array from which bytes are to be read
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative, not smaller than `limit()`,
     * or `limit() - index < src.length`
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     *
     * @since 13
     */
    fun put(index: Int, src: ByteArray): ByteBuffer {
        return put(index, src, 0, src.size)
    }

    fun putArray(index: Int, src: ByteArray, offset: Int, length: Int): ByteBuffer {
        /*if ((length.toLong() shl 0) > Bits.JNI_COPY_FROM_ARRAY_THRESHOLD) {
            val bufAddr: Long = address + (index.toLong() shl 0)
            val srcOffset =
                ARRAY_BASE_OFFSET + (offset.toLong() shl 0)
            val len = length.toLong() shl 0

            try {
                SCOPED_MEMORY_ACCESS.copyMemory(
                    null, session(), src, srcOffset,
                    base(), bufAddr, len
                )
            } finally {
                java.lang.ref.Reference.reachabilityFence(this)
            }
        } else {*/
            val end = offset + length
            var i = offset
            var j = index
            while (i < end) {
                this.put(j, src[i])
                i++
                j++
            }
        //}
        return this
    }


    // -- Other stuff --
    /**
     * Tells whether or not this buffer is backed by an accessible byte
     * array.
     *
     *
     *  If this method returns `true` then the [array][.array]
     * and [arrayOffset][.arrayOffset] methods may safely be invoked.
     *
     *
     * @return  `true` if, and only if, this buffer
     * is backed by an array and is not read-only
     */
    override fun hasArray(): Boolean {
        return (hb != null) && !isReadOnly
    }

    /**
     * Returns the byte array that backs this
     * buffer&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Modifications to this buffer's content will cause the returned
     * array's content to be modified, and vice versa.
     *
     *
     *  Invoke the [hasArray][.hasArray] method before invoking this
     * method in order to ensure that this buffer has an accessible backing
     * array.
     *
     * @return  The array that backs this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is backed by an array but is read-only
     *
     * @throws  UnsupportedOperationException
     * If this buffer is not backed by an accessible array
     */
    override fun array(): ByteArray {
        if (hb == null) throw UnsupportedOperationException()
        if (isReadOnly) throw ReadOnlyBufferException()
        return hb
    }

    /**
     * Returns the offset within this buffer's backing array of the first
     * element of the buffer&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  If this buffer is backed by an array then buffer position *p*
     * corresponds to array index *p*&nbsp;+&nbsp;`arrayOffset()`.
     *
     *
     *  Invoke the [hasArray][.hasArray] method before invoking this
     * method in order to ensure that this buffer has an accessible backing
     * array.
     *
     * @return  The offset within this buffer's array
     * of the first element of the buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is backed by an array but is read-only
     *
     * @throws  UnsupportedOperationException
     * If this buffer is not backed by an accessible array
     */
    override fun arrayOffset(): Int {
        if (hb == null) throw UnsupportedOperationException()
        if (isReadOnly) throw ReadOnlyBufferException()
        return offset
    }

    // -- Covariant return type overrides
    /**
     * {@inheritDoc}
     * @since 9
     */
    override fun position(newPosition: Int): ByteBuffer {
        super.position(newPosition)
        return this
    }

    /**
     * {@inheritDoc}
     * @since 9
     */
    override fun limit(newLimit: Int): ByteBuffer {
        super.limit(newLimit)
        return this
    }

    /**
     * {@inheritDoc}
     * @since 9
     */
    override fun mark(): ByteBuffer {
        super.mark()
        return this
    }

    /**
     * {@inheritDoc}
     * @since 9
     */
    override fun reset(): ByteBuffer {
        super.reset()
        return this
    }

    /**
     * {@inheritDoc}
     * @since 9
     */
    override fun clear(): ByteBuffer {
        super.clear()
        return this
    }

    /**
     * {@inheritDoc}
     * @since 9
     */
    override fun flip(): ByteBuffer {
        super.flip()
        return this
    }

    /**
     * {@inheritDoc}
     * @since 9
     */
    override fun rewind(): ByteBuffer {
        super.rewind()
        return this
    }

    /**
     * Compacts this buffer&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  The bytes between the buffer's current position and its limit,
     * if any, are copied to the beginning of the buffer.  That is, the
     * byte at index *p*&nbsp;=&nbsp;`position()` is copied
     * to index zero, the byte at index *p*&nbsp;+&nbsp;1 is copied
     * to index one, and so forth until the byte at index
     * `limit()`&nbsp;-&nbsp;1 is copied to index
     * *n*&nbsp;=&nbsp;`limit()`&nbsp;-&nbsp;`1`&nbsp;-&nbsp;*p*.
     * The buffer's position is then set to *n+1* and its limit is set to
     * its capacity.  The mark, if defined, is discarded.
     *
     *
     *  The buffer's position is set to the number of bytes copied,
     * rather than to zero, so that an invocation of this method can be
     * followed immediately by an invocation of another relative *put*
     * method.
     *
     *
     *
     *
     *  Invoke this method after writing data from a buffer in case the
     * write was incomplete.  The following loop, for example, copies bytes
     * from one channel to another via the buffer `buf`:
     *
     * {@snippet lang=java :
     * *     buf.clear();          // Prepare buffer for use
     * *     while (in.read(buf) >= 0 || buf.position != 0) {
     * *         buf.flip();
     * *         out.write(buf);
     * *         buf.compact();    // In case of partial write
     * *     }
     * * }
     *
     *
     *
     * @return  This buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun compact(): ByteBuffer

    /**
     * Tells whether or not this byte buffer is direct.
     *
     * @return  `true` if, and only if, this buffer is direct
     */
    abstract override val isDirect: Boolean


    /**
     * Returns a string summarizing the state of this buffer.
     *
     * @return  A summary string
     */
    override fun toString(): String {
        return (this::class.qualifiedName
                + "[pos=" + position()
                + " lim=" + limit()
                + " cap=" + capacity()
                + "]")
    }


    /**
     * Returns the current hash code of this buffer.
     *
     *
     *  The hash code of a byte buffer depends only upon its remaining
     * elements; that is, upon the elements from `position()` up to, and
     * including, the element at `limit()`&nbsp;-&nbsp;`1`.
     *
     *
     *  Because buffer hash codes are content-dependent, it is inadvisable
     * to use buffers as keys in hash maps or similar data structures unless it
     * is known that their contents will not change.
     *
     * @return  The current hash code of this buffer
     */
    override fun hashCode(): Int {
        var h = 1
        val p: Int = position()
        for (i in limit() - 1 downTo p) h = 31 * h + get(i).toInt()

        return h
    }

    /**
     * Tells whether or not this buffer is equal to another object.
     *
     *
     *  Two byte buffers are equal if, and only if,
     *
     *
     *
     *  1.
     *
     * They have the same element type,
     *
     *  1.
     *
     * They have the same number of remaining elements, and
     *
     *
     *  1.
     *
     * The two sequences of remaining elements, considered
     * independently of their starting positions, are pointwise equal.
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *  A byte buffer is not equal to any other type of object.
     *
     * @param  other  The object to which this buffer is to be compared
     *
     * @return  `true` if, and only if, this buffer is equal to the
     * given object
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteBuffer) return false
        val that = other
        val thisPos: Int = this.position()
        val thisRem: Int = this.limit() - thisPos
        val thatPos: Int = that.position()
        val thatRem: Int = that.limit() - thatPos
        if (thisRem < 0 || thisRem != thatRem) return false
        return BufferMismatch.mismatch(
            this, thisPos,
            that, thatPos,
            thisRem
        ) < 0
    }

    /**
     * Compares this buffer to another.
     *
     *
     *  Two byte buffers are compared by comparing their sequences of
     * remaining elements lexicographically, without regard to the starting
     * position of each sequence within its corresponding buffer.
     *
     *
     *
     *
     *
     *
     *
     *
     * Pairs of `byte` elements are compared as if by invoking
     * [Byte.compare].
     *
     *
     *
     *  A byte buffer is not comparable to any other type of object.
     *
     * @return  A negative integer, zero, or a positive integer as this buffer
     * is less than, equal to, or greater than the given buffer
     */
    override fun compareTo(that: ByteBuffer): Int {
        val thisPos: Int = this.position()
        val thisRem: Int = this.limit() - thisPos
        val thatPos: Int = that.position()
        val thatRem: Int = that.limit() - thatPos
        val length = min(thisRem, thatRem)
        if (length < 0) return -1
        val i: Int = BufferMismatch.mismatch(
            this, thisPos,
            that, thatPos,
            length
        )
        if (i >= 0) {
            return compare(this.get(thisPos + i), that.get(thatPos + i))
        }
        return thisRem - thatRem
    }

    /**
     * Finds and returns the relative index of the first mismatch between this
     * buffer and a given buffer.  The index is relative to the
     * [position][.position] of each buffer and will be in the range of
     * 0 (inclusive) up to the smaller of the [remaining][.remaining]
     * elements in each buffer (exclusive).
     *
     *
     *  If the two buffers share a common prefix then the returned index is
     * the length of the common prefix and it follows that there is a mismatch
     * between the two buffers at that index within the respective buffers.
     * If one buffer is a proper prefix of the other then the returned index is
     * the smaller of the remaining elements in each buffer, and it follows that
     * the index is only valid for the buffer with the larger number of
     * remaining elements.
     * Otherwise, there is no mismatch.
     *
     * @param  that
     * The byte buffer to be tested for a mismatch with this buffer
     *
     * @return  The relative index of the first mismatch between this and the
     * given buffer, otherwise -1 if no mismatch.
     *
     * @since 11
     */
    fun mismatch(that: ByteBuffer): Int {
        val thisPos: Int = this.position()
        val thisRem: Int = this.limit() - thisPos
        val thatPos: Int = that.position()
        val thatRem: Int = that.limit() - thatPos
        val length = min(thisRem, thatRem)
        if (length < 0) return -1
        val r: Int = BufferMismatch.mismatch(
            this, thisPos,
            that, thatPos,
            length
        )
        return if (r == -1 && thisRem != thatRem) length else r
    }


    // -- Other char stuff --
    // -- Other byte stuff: Access to binary data --
    var bigEndian // package-private
            : Boolean = true
    var nativeByteOrder // package-private
            : Boolean = (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)

    /**
     * Retrieves this buffer's byte order.
     *
     *
     *  The byte order is used when reading or writing multibyte values, and
     * when creating buffers that are views of this byte buffer.  The order of
     * a newly-created byte buffer is always [ BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
     *
     * @return  This buffer's byte order
     */
    fun order(): ByteOrder {
        return if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    }

    /**
     * Modifies this buffer's byte order.
     *
     * @param  bo
     * The new byte order,
     * either [BIG_ENDIAN][ByteOrder.BIG_ENDIAN]
     * or [LITTLE_ENDIAN][ByteOrder.LITTLE_ENDIAN]
     *
     * @return  This buffer
     */
    fun order(bo: ByteOrder): ByteBuffer {
        bigEndian = (bo == ByteOrder.BIG_ENDIAN)
        nativeByteOrder =
            (bigEndian == (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN))
        return this
    }

    /**
     * Returns the memory address, pointing to the byte at the given index,
     * modulo the given unit size.
     *
     *
     *  The return value is non-negative in the range of `0`
     * (inclusive) up to `unitSize` (exclusive), with zero indicating
     * that the address of the byte at the index is aligned for the unit size,
     * and a positive value that the address is misaligned for the unit size.
     * If the address of the byte at the index is misaligned, the return value
     * represents how much the index should be adjusted to locate a byte at an
     * aligned address.  Specifically, the index should either be decremented by
     * the return value if the latter is not greater than `index`, or be
     * incremented by the unit size minus the return value.  Therefore given
     * {@snippet lang=java :
     * *     int value = alignmentOffset(index, unitSize)
     * * }
     * then the identities
     * {@snippet lang=java :
     * *     alignmentOffset(index - value, unitSize) == 0, value <= index
     * * }
     * and
     * {@snippet lang=java :
     * *     alignmentOffset(index + (unitSize - value), unitSize) == 0
     * * }
     * must hold.
     *
     * @apiNote
     * This method may be utilized to determine if unit size bytes from an
     * index can be accessed atomically, if supported by the native platform.
     *
     * @param  index
     * The index to query for alignment offset, must be non-negative, no
     * upper bounds check is performed
     *
     * @param  unitSize
     * The unit size in bytes, must be a power of `2`
     *
     * @return  The indexed byte's memory address modulo the unit size
     *
     * @throws IllegalArgumentException
     * If the index is negative or the unit size is not a power of
     * `2`
     *
     * @throws UnsupportedOperationException
     * If the buffer is non-direct, and `unitSize > 1`
     *
     * @see .alignedSlice
     * @since 9
     */

    // not implemented because kmp does not support jdk memory access

    /*fun alignmentOffset(index: Int, unitSize: Int): Int {
        require(index >= 0) { "Index less than zero: $index" }
        require(!(unitSize < 1 || (unitSize and (unitSize - 1)) != 0)) { "Unit size not a power of two: $unitSize" }
        if (unitSize > 1 && !this.isDirect) throw UnsupportedOperationException("Unit size unsupported for non-direct buffers: $unitSize")

        return ((address + index) and (unitSize - 1).toLong()).toInt()
    }*/

    /**
     * Creates a new byte buffer whose content is a shared and aligned
     * subsequence of this buffer's content.
     *
     *
     *  The content of the new buffer will start at this buffer's current
     * position rounded up to the index of the nearest aligned byte for the
     * given unit size, and end at this buffer's limit rounded down to the index
     * of the nearest aligned byte for the given unit size.
     * If rounding results in out-of-bound values then the new buffer's capacity
     * and limit will be zero.  If rounding is within bounds the following
     * expressions will be true for a new buffer `nb` and unit size
     * `unitSize`:
     * {@snippet lang=java :
     * *     nb.alignmentOffset(0, unitSize) == 0
     * *     nb.alignmentOffset(nb.limit(), unitSize) == 0
     * * }
     *
     *
     *  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer or fewer subject to
     * alignment, its mark will be undefined, and its byte order will be
     * [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
     *
     * The new buffer will be direct if, and only if, this buffer is direct, and
     * it will be read-only if, and only if, this buffer is read-only.
     *
     * @apiNote
     * This method may be utilized to create a new buffer where unit size bytes
     * from index, that is a multiple of the unit size, may be accessed
     * atomically, if supported by the native platform.
     *
     * @param  unitSize
     * The unit size in bytes, must be a power of `2`
     *
     * @return  The new byte buffer
     *
     * @throws IllegalArgumentException
     * If the unit size not a power of `2`
     *
     * @throws UnsupportedOperationException
     * If the buffer is non-direct, and `unitSize > 1`
     *
     * @see .alignmentOffset
     * @see .slice
     * @since 9
     */

    // not implemented because kmp does not support jdk memory access

    /*fun alignedSlice(unitSize: Int): ByteBuffer {
        val pos: Int = position()
        val lim: Int = limit()

        val pos_mod = alignmentOffset(pos, unitSize)
        val lim_mod = alignmentOffset(lim, unitSize)

        // Round up the position to align with unit size
        var aligned_pos = if (pos_mod > 0)
            pos + (unitSize - pos_mod)
        else
            pos

        // Round down the limit to align with unit size
        var aligned_lim = lim - lim_mod

        if (aligned_pos > lim || aligned_lim < pos) {
            aligned_lim = pos
            aligned_pos = aligned_lim
        }

        return slice(aligned_pos, aligned_lim - aligned_pos)
    }*/


    /**
     * Relative *get* method for reading a char value.
     *
     *
     *  Reads the next two bytes at this buffer's current position,
     * composing them into a char value according to the current byte order,
     * and then increments the position by two.
     *
     * @return  The char value at the buffer's current position
     *
     * @throws  BufferUnderflowException
     * If there are fewer than two bytes
     * remaining in this buffer
     */
    abstract fun getChar(): Char

    /**
     * Relative *put* method for writing a char
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes two bytes containing the given char value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by two.
     *
     * @param  value
     * The char value to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there are fewer than two bytes
     * remaining in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putChar(value: Char): ByteBuffer

    /**
     * Absolute *get* method for reading a char value.
     *
     *
     *  Reads two bytes at the given index, composing them into a
     * char value according to the current byte order.
     *
     * @param  index
     * The index from which the bytes will be read
     *
     * @return  The char value at the given index
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus one
     */
    abstract fun getChar(index: Int): Char

    /**
     * Absolute *put* method for writing a char
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes two bytes containing the given char value, in the
     * current byte order, into this buffer at the given index.
     *
     * @param  index
     * The index at which the bytes will be written
     *
     * @param  value
     * The char value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus one
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putChar(index: Int, value: Char): ByteBuffer

    /**
     * Creates a view of this byte buffer as a char buffer.
     *
     *
     *  The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer divided by
     * two, its mark will be undefined, and its byte order will be that
     * of the byte buffer at the moment the view is created.  The new buffer
     * will be direct if, and only if, this buffer is direct, and it will be
     * read-only if, and only if, this buffer is read-only.
     *
     * @return  A new char buffer
     */
    abstract fun asCharBuffer(): CharBuffer


    /**
     * Relative *get* method for reading a short value.
     *
     *
     *  Reads the next two bytes at this buffer's current position,
     * composing them into a short value according to the current byte order,
     * and then increments the position by two.
     *
     * @return  The short value at the buffer's current position
     *
     * @throws  BufferUnderflowException
     * If there are fewer than two bytes
     * remaining in this buffer
     */
    abstract fun getShort(): Short

    /**
     * Relative *put* method for writing a short
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes two bytes containing the given short value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by two.
     *
     * @param  value
     * The short value to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there are fewer than two bytes
     * remaining in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putShort(value: Short): ByteBuffer

    /**
     * Absolute *get* method for reading a short value.
     *
     *
     *  Reads two bytes at the given index, composing them into a
     * short value according to the current byte order.
     *
     * @param  index
     * The index from which the bytes will be read
     *
     * @return  The short value at the given index
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus one
     */
    abstract fun getShort(index: Int): Short

    /**
     * Absolute *put* method for writing a short
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes two bytes containing the given short value, in the
     * current byte order, into this buffer at the given index.
     *
     * @param  index
     * The index at which the bytes will be written
     *
     * @param  value
     * The short value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus one
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putShort(index: Int, value: Short): ByteBuffer

    /**
     * Creates a view of this byte buffer as a short buffer.
     *
     *
     *  The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer divided by
     * two, its mark will be undefined, and its byte order will be that
     * of the byte buffer at the moment the view is created.  The new buffer
     * will be direct if, and only if, this buffer is direct, and it will be
     * read-only if, and only if, this buffer is read-only.
     *
     * @return  A new short buffer
     */
    // TODO not ported because it is not used in lucene at this point of porting process. port if it is needed later
    //abstract fun asShortBuffer(): ShortBuffer


    /**
     * Relative *get* method for reading an int value.
     *
     *
     *  Reads the next four bytes at this buffer's current position,
     * composing them into an int value according to the current byte order,
     * and then increments the position by four.
     *
     * @return  The int value at the buffer's current position
     *
     * @throws  BufferUnderflowException
     * If there are fewer than four bytes
     * remaining in this buffer
     */
    abstract fun getInt(): Int

    /**
     * Relative *put* method for writing an int
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes four bytes containing the given int value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by four.
     *
     * @param  value
     * The int value to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there are fewer than four bytes
     * remaining in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putInt(value: Int): ByteBuffer

    /**
     * Absolute *get* method for reading an int value.
     *
     *
     *  Reads four bytes at the given index, composing them into a
     * int value according to the current byte order.
     *
     * @param  index
     * The index from which the bytes will be read
     *
     * @return  The int value at the given index
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus three
     */
    abstract fun getInt(index: Int): Int

    /**
     * Absolute *put* method for writing an int
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes four bytes containing the given int value, in the
     * current byte order, into this buffer at the given index.
     *
     * @param  index
     * The index at which the bytes will be written
     *
     * @param  value
     * The int value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus three
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putInt(index: Int, value: Int): ByteBuffer

    /**
     * Creates a view of this byte buffer as an int buffer.
     *
     *
     *  The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer divided by
     * four, its mark will be undefined, and its byte order will be that
     * of the byte buffer at the moment the view is created.  The new buffer
     * will be direct if, and only if, this buffer is direct, and it will be
     * read-only if, and only if, this buffer is read-only.
     *
     * @return  A new int buffer
     */
    abstract fun asIntBuffer(): IntBuffer


    /**
     * Relative *get* method for reading a long value.
     *
     *
     *  Reads the next eight bytes at this buffer's current position,
     * composing them into a long value according to the current byte order,
     * and then increments the position by eight.
     *
     * @return  The long value at the buffer's current position
     *
     * @throws  BufferUnderflowException
     * If there are fewer than eight bytes
     * remaining in this buffer
     */
    abstract fun getLong(): Long

    /**
     * Relative *put* method for writing a long
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes eight bytes containing the given long value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by eight.
     *
     * @param  value
     * The long value to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there are fewer than eight bytes
     * remaining in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putLong(value: Long): ByteBuffer

    /**
     * Absolute *get* method for reading a long value.
     *
     *
     *  Reads eight bytes at the given index, composing them into a
     * long value according to the current byte order.
     *
     * @param  index
     * The index from which the bytes will be read
     *
     * @return  The long value at the given index
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus seven
     */
    abstract fun getLong(index: Int): Long

    /**
     * Absolute *put* method for writing a long
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes eight bytes containing the given long value, in the
     * current byte order, into this buffer at the given index.
     *
     * @param  index
     * The index at which the bytes will be written
     *
     * @param  value
     * The long value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus seven
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putLong(index: Int, value: Long): ByteBuffer

    /**
     * Creates a view of this byte buffer as a long buffer.
     *
     *
     *  The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer divided by
     * eight, its mark will be undefined, and its byte order will be that
     * of the byte buffer at the moment the view is created.  The new buffer
     * will be direct if, and only if, this buffer is direct, and it will be
     * read-only if, and only if, this buffer is read-only.
     *
     * @return  A new long buffer
     */
    abstract fun asLongBuffer(): LongBuffer


    /**
     * Relative *get* method for reading a float value.
     *
     *
     *  Reads the next four bytes at this buffer's current position,
     * composing them into a float value according to the current byte order,
     * and then increments the position by four.
     *
     * @return  The float value at the buffer's current position
     *
     * @throws  BufferUnderflowException
     * If there are fewer than four bytes
     * remaining in this buffer
     */
    abstract fun getFloat(): Float

    /**
     * Relative *put* method for writing a float
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes four bytes containing the given float value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by four.
     *
     * @param  value
     * The float value to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there are fewer than four bytes
     * remaining in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putFloat(value: Float): ByteBuffer

    /**
     * Absolute *get* method for reading a float value.
     *
     *
     *  Reads four bytes at the given index, composing them into a
     * float value according to the current byte order.
     *
     * @param  index
     * The index from which the bytes will be read
     *
     * @return  The float value at the given index
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus three
     */
    abstract fun getFloat(index: Int): Float

    /**
     * Absolute *put* method for writing a float
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes four bytes containing the given float value, in the
     * current byte order, into this buffer at the given index.
     *
     * @param  index
     * The index at which the bytes will be written
     *
     * @param  value
     * The float value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus three
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putFloat(index: Int, value: Float): ByteBuffer

    /**
     * Creates a view of this byte buffer as a float buffer.
     *
     *
     *  The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer divided by
     * four, its mark will be undefined, and its byte order will be that
     * of the byte buffer at the moment the view is created.  The new buffer
     * will be direct if, and only if, this buffer is direct, and it will be
     * read-only if, and only if, this buffer is read-only.
     *
     * @return  A new float buffer
     */
    abstract fun asFloatBuffer(): FloatBuffer


    /**
     * Relative *get* method for reading a double value.
     *
     *
     *  Reads the next eight bytes at this buffer's current position,
     * composing them into a double value according to the current byte order,
     * and then increments the position by eight.
     *
     * @return  The double value at the buffer's current position
     *
     * @throws  BufferUnderflowException
     * If there are fewer than eight bytes
     * remaining in this buffer
     */
    abstract fun getDouble(): Double

    /**
     * Relative *put* method for writing a double
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes eight bytes containing the given double value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by eight.
     *
     * @param  value
     * The double value to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there are fewer than eight bytes
     * remaining in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putDouble(value: Double): ByteBuffer

    /**
     * Absolute *get* method for reading a double value.
     *
     *
     *  Reads eight bytes at the given index, composing them into a
     * double value according to the current byte order.
     *
     * @param  index
     * The index from which the bytes will be read
     *
     * @return  The double value at the given index
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus seven
     */
    abstract fun getDouble(index: Int): Double

    /**
     * Absolute *put* method for writing a double
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes eight bytes containing the given double value, in the
     * current byte order, into this buffer at the given index.
     *
     * @param  index
     * The index at which the bytes will be written
     *
     * @param  value
     * The double value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus seven
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    abstract fun putDouble(index: Int, value: Double): ByteBuffer

    /**
     * Creates a view of this byte buffer as a double buffer.
     *
     *
     *  The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer divided by
     * eight, its mark will be undefined, and its byte order will be that
     * of the byte buffer at the moment the view is created.  The new buffer
     * will be direct if, and only if, this buffer is direct, and it will be
     * read-only if, and only if, this buffer is read-only.
     *
     * @return  A new double buffer
     */
    // TODO not ported because it is not used in lucene at this point of porting process. port if it is needed later
    //abstract fun asDoubleBuffer(): DoubleBuffer

    companion object {
        // Cached array base offset
        //private val ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(ByteArray::class.java).toLong()

        /**
         * Allocates a new direct byte buffer.
         *
         *
         *  The new buffer's position will be zero, its limit will be its
         * capacity, its mark will be undefined, each of its elements will be
         * initialized to zero, and its byte order will be
         * [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].  Whether or not it has a
         * [backing array][.hasArray] is unspecified.
         *
         * @param  capacity
         * The new buffer's capacity, in bytes
         *
         * @return  The new byte buffer
         *
         * @throws  IllegalArgumentException
         * If the `capacity` is a negative integer
         */
        // TODO not ported because it is not used in lucene at this point of porting process. port if it is needed later
        /*fun allocateDirect(capacity: Int): ByteBuffer {
            return DirectByteBuffer(capacity)
        }*/


        /**
         * Allocates a new byte buffer.
         *
         *
         *  The new buffer's position will be zero, its limit will be its
         * capacity, its mark will be undefined, each of its elements will be
         * initialized to zero, and its byte order will be
         *
         * [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
         *
         *
         *
         *
         * It will have a [backing array][.array], and its
         * [array offset][.arrayOffset] will be zero.
         *
         * @param  capacity
         * The new buffer's capacity, in bytes
         *
         * @return  The new byte buffer
         *
         * @throws  IllegalArgumentException
         * If the `capacity` is a negative integer
         */
        fun allocate(capacity: Int): ByteBuffer {
            if (capacity < 0) throw createCapacityException(capacity)
            return HeapByteBuffer(capacity, capacity/*, null*/)
        }

        /**
         * Wraps a byte array into a buffer.
         *
         *
         *  The new buffer will be backed by the given byte array;
         * that is, modifications to the buffer will cause the array to be modified
         * and vice versa.  The new buffer's capacity will be
         * `array.length`, its position will be `offset`, its limit
         * will be `offset + length`, its mark will be undefined, and its
         * byte order will be
         *
         * [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
         *
         *
         *
         *
         * Its [backing array][.array] will be the given array, and
         * its [array offset][.arrayOffset] will be zero.
         *
         * @param  array
         * The array that will back the new buffer
         *
         * @param  offset
         * The offset of the subarray to be used; must be non-negative and
         * no larger than `array.length`.  The new buffer's position
         * will be set to this value.
         *
         * @param  length
         * The length of the subarray to be used;
         * must be non-negative and no larger than
         * `array.length - offset`.
         * The new buffer's limit will be set to `offset + length`.
         *
         * @return  The new byte buffer
         *
         * @throws  IndexOutOfBoundsException
         * If the preconditions on the `offset` and `length`
         * parameters do not hold
         */
        /**
         * Wraps a byte array into a buffer.
         *
         *
         *  The new buffer will be backed by the given byte array;
         * that is, modifications to the buffer will cause the array to be modified
         * and vice versa.  The new buffer's capacity and limit will be
         * `array.length`, its position will be zero, its mark will be
         * undefined, and its byte order will be
         *
         * [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
         *
         *
         *
         *
         * Its [backing array][.array] will be the given array, and its
         * [array offset][.arrayOffset] will be zero.
         *
         * @param  array
         * The array that will back this buffer
         *
         * @return  The new byte buffer
         */
        fun wrap(
            array: ByteArray,
            offset: Int = 0, length: Int = array.size
        ): ByteBuffer {
            try {
                return HeapByteBuffer(array, offset, length/*, null*/)
            } catch (x: IllegalArgumentException) {
                throw IndexOutOfBoundsException()
            }
        }

        private fun compare(x: Byte, y: Byte): Int {
            return Byte.compare(x, y)
        }
    }
}
