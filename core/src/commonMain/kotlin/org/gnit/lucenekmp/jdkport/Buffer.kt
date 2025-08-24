package org.gnit.lucenekmp.jdkport

/**
 * A container for data of a specific primitive type.
 *
 *
 *  A buffer is a linear, finite sequence of elements of a specific
 * primitive type.  Aside from its content, the essential properties of a
 * buffer are its capacity, limit, and position:
 *
 * <blockquote>
 *
 *
 *  A buffer's *capacity* is the number of elements it contains.  The
 * capacity of a buffer is never negative and never changes.
 *
 *
 *  A buffer's *limit* is the index of the first element that should
 * not be read or written.  A buffer's limit is never negative and is never
 * greater than its capacity.
 *
 *
 *  A buffer's *position* is the index of the next element to be
 * read or written.  A buffer's position is never negative and is never
 * greater than its limit.
 *
</blockquote> *
 *
 *
 *  There is one subclass of this class for each non-boolean primitive type.
 *
 *
 * <h2> Transferring data </h2>
 *
 *
 *  Each subclass of this class defines two categories of *get* and
 * *put* operations:
 *
 * <blockquote>
 *
 *
 *  *Relative* operations read or write one or more elements starting
 * at the current position and then increment the position by the number of
 * elements transferred.  If the requested transfer exceeds the limit then a
 * relative *get* operation throws a [BufferUnderflowException]
 * and a relative *put* operation throws a [   ]; in either case, no data is transferred.
 *
 *
 *  *Absolute* operations take an explicit element index and do not
 * affect the position.  Absolute *get* and *put* operations throw
 * an [IndexOutOfBoundsException] if the index argument exceeds the
 * limit.
 *
</blockquote> *
 *
 *
 *  Data may also, of course, be transferred in to or out of a buffer by the
 * I/O operations of an appropriate channel, which are always relative to the
 * current position.
 *
 *
 * <h2> Marking and resetting </h2>
 *
 *
 *  A buffer's *mark* is the index to which its position will be reset
 * when the [reset][.reset] method is invoked.  The mark is not always
 * defined, but when it is defined it is never negative and is never greater
 * than the position.  If the mark is defined then it is discarded when the
 * position or the limit is adjusted to a value smaller than the mark.  If the
 * mark is not defined then invoking the [reset][.reset] method causes an
 * [InvalidMarkException] to be thrown.
 *
 *
 * <h2> Invariants </h2>
 *
 *
 *  The following invariant holds for the mark, position, limit, and
 * capacity values:
 *
 * <blockquote>
 * `0` `<=`
 * *mark* `<=`
 * *position* `<=`
 * *limit* `<=`
 * *capacity*
</blockquote> *
 *
 *
 *  A newly-created buffer always has a position of zero and a mark that is
 * undefined.  The initial limit may be zero, or it may be some other value
 * that depends upon the type of the buffer and the manner in which it is
 * constructed.  Each element of a newly-allocated buffer is initialized
 * to zero.
 *
 *
 * <h2> Additional operations </h2>
 *
 *
 *  In addition to methods for accessing the position, limit, and capacity
 * values and for marking and resetting, this class also defines the following
 * operations upon buffers:
 *
 *
 *
 *  *
 *
 * [.clear] makes a buffer ready for a new sequence of
 * channel-read or relative *put* operations: It sets the limit to the
 * capacity and the position to zero.
 *
 *  *
 *
 * [.flip] makes a buffer ready for a new sequence of
 * channel-write or relative *get* operations: It sets the limit to the
 * current position and then sets the position to zero.
 *
 *  *
 *
 * [.rewind] makes a buffer ready for re-reading the data that
 * it already contains: It leaves the limit unchanged and sets the position
 * to zero.
 *
 *  *
 *
 * The [.slice] and [slice(index,length)][.slice]
 * methods create a subsequence of a buffer: They leave the limit and the
 * position unchanged.
 *
 *  *
 *
 * [.duplicate] creates a shallow copy of a buffer: It leaves
 * the limit and the position unchanged.
 *
 *
 *
 *
 * <h2> Read-only buffers </h2>
 *
 *
 *  Every buffer is readable, but not every buffer is writable.  The
 * mutation methods of each buffer class are specified as *optional
 * operations* that will throw a [ReadOnlyBufferException] when
 * invoked upon a read-only buffer.  A read-only buffer does not allow its
 * content to be changed, but its mark, position, and limit values are mutable.
 * Whether or not a buffer is read-only may be determined by invoking its
 * [isReadOnly][.isReadOnly] method.
 *
 *
 * <h2> Thread safety </h2>
 *
 *
 *  Buffers are not safe for use by multiple concurrent threads.  If a
 * buffer is to be used by more than one thread then access to the buffer
 * should be controlled by appropriate synchronization.
 *
 *
 * <h2> Invocation chaining </h2>
 *
 *
 *  Methods in this class that do not otherwise have a value to return are
 * specified to return the buffer upon which they are invoked.  This allows
 * method invocations to be chained; for example, the sequence of statements
 *
 * {@snippet lang=java :
 * *     b.flip();
 * *     b.position(23);
 * *     b.limit(42);
 * * }
 *
 * can be replaced by the single, more compact statement
 *
 * {@snippet lang=java :
 * *     b.flip().position(23).limit(42);
 * * }
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 * @sealedGraph
 */
@Ported(from = "java.nio.Buffer")
abstract class Buffer {
    // Invariants: mark <= position <= limit <= capacity
    private var mark = -1
    var position = 0
    var limit = 0
    val capacity: Int

    // Used by heap byte buffers or direct buffers with Unsafe access
    // For heap byte buffers this field will be the address relative to the
    // array base address and offset into that array. The address might
    // not align on a word boundary for slices, nor align at a long word
    // (8 byte) boundary for byte[] allocations on 32-bit systems.
    // For direct buffers it is the start address of the memory region. The
    // address might not align on a word boundary for slices, nor when created
    // using JNI, see NewDirectByteBuffer(void*, long).
    // Should ideally be declared final
    // NOTE: hoisted here for speed in JNI GetDirectBufferAddress
    // var address: Long = 0

    // Used by buffers generated by the memory access API (JEP-370)
    //val segment: java.lang.foreign.MemorySegment


    // Creates a new buffer with given address and capacity.
    //
    internal constructor(/*addr: Long,*/ cap: Int, /*segment: java.lang.foreign.MemorySegment*/) {
        /*this.address = addr*/
        this.capacity = cap
        //this.segment = segment
    }

    // Creates a new buffer with the given mark, position, limit, and capacity,
    // after checking invariants.
    //
    internal constructor(
        mark: Int,
        pos: Int,
        lim: Int,
        cap: Int,
        //segment: java.lang.foreign.MemorySegment
    ) {       // package-private
        if (cap < 0) throw createCapacityException(cap)
        this.capacity = cap
        //this.segment = segment
        limit(lim)
        position(pos)
        if (mark >= 0) {
            require(mark <= pos) {
                ("mark > position: ("
                        + mark + " > " + pos + ")")
            }
            this.mark = mark
        }
    }

    /**
     * Returns this buffer's capacity.
     *
     * @return  The capacity of this buffer
     */
    fun capacity(): Int {
        return capacity
    }

    /**
     * Returns this buffer's position.
     *
     * @return  The position of this buffer
     */
    fun position(): Int {
        return position
    }

    /**
     * Sets this buffer's position.  If the mark is defined and larger than the
     * new position then it is discarded.
     *
     * @param  newPosition
     * The new position value; must be non-negative
     * and no larger than the current limit
     *
     * @return  This buffer
     *
     * @throws  IllegalArgumentException
     * If the preconditions on `newPosition` do not hold
     */
    open fun position(newPosition: Int): Buffer {
        if ((newPosition > limit) or (newPosition < 0)) throw createPositionException(newPosition)
        if (mark > newPosition) mark = -1
        position = newPosition
        return this
    }

    /**
     * Verify that `0 < newPosition <= limit`
     *
     * @param newPosition
     * The new position value
     *
     * @throws IllegalArgumentException
     * If the specified position is out of bounds.
     */
    private fun createPositionException(newPosition: Int): IllegalArgumentException {
        var msg: String

        if (newPosition > limit) {
            msg = "newPosition > limit: ($newPosition > $limit)"
        } else { // assume negative
            assert(newPosition < 0) { "newPosition expected to be negative" }
            msg = "newPosition < 0: ($newPosition < 0)"
        }

        return IllegalArgumentException(msg)
    }

    /**
     * Returns this buffer's limit.
     *
     * @return  The limit of this buffer
     */
    fun limit(): Int {
        return limit
    }

    /**
     * Sets this buffer's limit.  If the position is larger than the new limit
     * then it is set to the new limit.  If the mark is defined and larger than
     * the new limit then it is discarded.
     *
     * @param  newLimit
     * The new limit value; must be non-negative
     * and no larger than this buffer's capacity
     *
     * @return  This buffer
     *
     * @throws  IllegalArgumentException
     * If the preconditions on `newLimit` do not hold
     */
    open fun limit(newLimit: Int): Buffer {
        if ((newLimit > capacity) or (newLimit < 0)) throw createLimitException(newLimit)
        limit = newLimit
        if (position > newLimit) position = newLimit
        if (mark > newLimit) mark = -1
        return this
    }

    /**
     * Verify that `0 < newLimit <= capacity`
     *
     * @param newLimit
     * The new limit value
     *
     * @throws IllegalArgumentException
     * If the specified limit is out of bounds.
     */
    private fun createLimitException(newLimit: Int): IllegalArgumentException {
        var msg: String

        if (newLimit > capacity) {
            msg = "newLimit > capacity: ($newLimit > $capacity)"
        } else { // assume negative
            assert(newLimit < 0) { "newLimit expected to be negative" }
            msg = "newLimit < 0: ($newLimit < 0)"
        }

        return IllegalArgumentException(msg)
    }

    /**
     * Sets this buffer's mark at its position.
     *
     * @return  This buffer
     */
    open fun mark(): Buffer {
        mark = position
        return this
    }

    /**
     * Resets this buffer's position to the previously-marked position.
     *
     *
     *  Invoking this method neither changes nor discards the mark's
     * value.
     *
     * @return  This buffer
     *
     * @throws  InvalidMarkException
     * If the mark has not been set
     */
    open fun reset(): Buffer {
        val m = mark
        if (m < 0) throw InvalidMarkException()
        position = m
        return this
    }

    /**
     * Clears this buffer.  The position is set to zero, the limit is set to
     * the capacity, and the mark is discarded.
     *
     *
     *  Invoke this method before using a sequence of channel-read or
     * *put* operations to fill this buffer.  For example:
     *
     * {@snippet lang=java :
     * *     buf.clear();     // Prepare buffer for reading
     * *     in.read(buf);    // Read data
     * * }
     *
     *
     *  This method does not actually erase the data in the buffer, but it
     * is named as if it did because it will most often be used in situations
     * in which that might as well be the case.
     *
     * @return  This buffer
     */
    open fun clear(): Buffer {
        position = 0
        limit = capacity
        mark = -1
        return this
    }

    /**
     * Flips this buffer.  The limit is set to the current position and then
     * the position is set to zero.  If the mark is defined then it is
     * discarded.
     *
     *
     *  After a sequence of channel-read or *put* operations, invoke
     * this method to prepare for a sequence of channel-write or relative
     * *get* operations.  For example:
     *
     * {@snippet lang=java :
     * *     buf.put(magic);    // Prepend header
     * *     in.read(buf);      // Read data into rest of buffer
     * *     buf.flip();        // Flip buffer
     * *     out.write(buf);    // Write header + data to channel
     * * }
     *
     *
     *  This method is often used in conjunction with the [ ][java.nio.ByteBuffer.compact] method when transferring data from
     * one place to another.
     *
     * @return  This buffer
     */
    open fun flip(): Buffer {
        limit = position
        position = 0
        mark = -1
        return this
    }

    /**
     * Rewinds this buffer.  The position is set to zero and the mark is
     * discarded.
     *
     *
     *  Invoke this method before a sequence of channel-write or *get*
     * operations, assuming that the limit has already been set
     * appropriately.  For example:
     *
     * {@snippet lang=java :
     * *     out.write(buf);    // Write remaining data
     * *     buf.rewind();      // Rewind buffer
     * *     buf.get(array);    // Copy data into array
     * * }
     *
     * @return  This buffer
     */
    open fun rewind(): Buffer {
        position = 0
        mark = -1
        return this
    }

    /**
     * Returns the number of elements between the current position and the
     * limit.
     *
     * @return  The number of elements remaining in this buffer
     */
    fun remaining(): Int {
        val rem = limit - position
        return if (rem > 0) rem else 0
    }

    /**
     * Tells whether there are any elements between the current position and
     * the limit.
     *
     * @return  `true` if, and only if, there is at least one element
     * remaining in this buffer
     */
    fun hasRemaining(): Boolean {
        return position < limit
    }

    /**
     * Tells whether or not this buffer is read-only.
     *
     * @return  `true` if, and only if, this buffer is read-only
     */
    abstract var isReadOnly: Boolean

    /**
     * Tells whether or not this buffer is backed by an accessible
     * array.
     *
     *
     *  If this method returns `true` then the [array][.array]
     * and [arrayOffset][.arrayOffset] methods may safely be invoked.
     *
     *
     * @return  `true` if, and only if, this buffer
     * is backed by an array and is not read-only
     *
     * @since 1.6
     */
    abstract fun hasArray(): Boolean

    /**
     * Returns the array that backs this
     * buffer&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method is intended to allow array-backed buffers to be
     * passed to native code more efficiently. Concrete subclasses
     * provide more strongly-typed return values for this method.
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
     *
     * @since 1.6
     */
    abstract fun array(): Any

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
     *
     * @since 1.6
     */
    abstract fun arrayOffset(): Int

    /**
     * Tells whether or not this buffer is
     * [*direct*](ByteBuffer.html#direct).
     *
     * @return  `true` if, and only if, this buffer is direct
     *
     * @since 1.6
     */
    abstract val isDirect: Boolean

    /**
     * Creates a new buffer whose content is a shared subsequence of
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
     * will be the number of elements remaining in this buffer, its mark will be
     * undefined. The new buffer will be direct if, and only if, this buffer is
     * direct, and it will be read-only if, and only if, this buffer is
     * read-only.
     *
     * @return  The new buffer
     *
     * @since 9
     */
    abstract fun slice(): Buffer

    /**
     * Creates a new buffer whose content is a shared subsequence of
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
     * will be `length`, its mark will be undefined. The new buffer will
     * be direct if, and only if, this buffer is direct, and it will be
     * read-only if, and only if, this buffer is read-only.
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
    abstract fun slice(index: Int, length: Int): Buffer

    /**
     * Creates a new buffer that shares this buffer's content.
     *
     *
     *  The content of the new buffer will be that of this buffer.  Changes
     * to this buffer's content will be visible in the new buffer, and vice
     * versa; the two buffers' position, limit, and mark values will be
     * independent.
     *
     *
     *  The new buffer's capacity, limit, position and mark values will be
     * identical to those of this buffer. The new buffer will be direct if, and
     * only if, this buffer is direct, and it will be read-only if, and only if,
     * this buffer is read-only.
     *
     * @return  The new buffer
     *
     * @since 9
     */
    abstract fun duplicate(): Buffer


    // -- Package-private methods for bounds checking, etc. --
    /**
     *
     * @return the base reference, paired with the address
     * field, which in combination can be used for unsafe access into a heap
     * buffer or direct byte buffer (and views of).
     */
    abstract fun base(): Any

    /**
     * Checks the current position against the limit, throwing a [ ] if it is not smaller than the limit, and then
     * increments the position.
     *
     * @return  The current position value, before it is incremented
     */
    fun nextGetIndex(): Int {                          // package-private
        val p = position
        if (p >= limit) throw BufferUnderflowException()
        position = p + 1
        return p
    }

    fun nextGetIndex(nb: Int): Int {                    // package-private
        val p = position
        if (limit - p < nb) throw BufferUnderflowException()
        position = p + nb
        return p
    }

    /**
     * Checks the current position against the limit, throwing a [ ] if it is not smaller than the limit, and then
     * increments the position.
     *
     * @return  The current position value, before it is incremented
     */
    fun nextPutIndex(): Int {                          // package-private
        val p = position
        if (p >= limit) throw BufferOverflowException()
        position = p + 1
        return p
    }

    fun nextPutIndex(nb: Int): Int {                    // package-private
        val p = position
        if (limit - p < nb) throw BufferOverflowException()
        position = p + nb
        return p
    }

    /**
     * Checks the given index against the limit, throwing an [ ] if it is greater than the limit
     * or is negative.
     */
    /*@ForceInline*/
    fun checkIndex(i: Int): Int {                       // package-private
        return Preconditions.checkIndex<IndexOutOfBoundsException>(
            i,
            limit,
            IOOBE_FORMATTER
        )
    }

    /**
     * Checks the given index and number of bytes against the range
     * `[0, limit]`, throwing an [ ] if the index is negative or the index
     * plus the number of bytes is greater than the limit.
     */
    /*@ForceInline*/
    fun checkIndex(i: Int, nb: Int): Int {               // package-private
        return Preconditions.checkIndex<IndexOutOfBoundsException>(
            i,
            limit - nb + 1,
            IOOBE_FORMATTER
        )
    }

    /**
     * {@return the scale shifts for this Buffer}
     *
     *
     * The scale shifts are:
     * ByteBuffer:               0
     * ShortBuffer, CharBuffer:  1
     * IntBuffer, FloatBuffer:   2
     * LongBuffer, DoubleBuffer: 3
     */
    abstract fun scaleShifts(): Int

    /*abstract fun heapSegment(
        base: Any,
        offset: Long,
        length: Long,
        readOnly: Boolean,
        bufferScope: MemorySessionImpl
    ): AbstractMemorySegmentImpl*/

    fun markValue(): Int {                             // package-private
        return mark
    }

    fun discardMark() {                          // package-private
        mark = -1
    }

    /*@ForceInline*/
    /*fun session(): MemorySessionImpl {
        if (segment != null) {
            return (segment as AbstractMemorySegmentImpl).sessionImpl()
        } else {
            return null
        }
    }*/

    fun checkSession() {
        /*val session: MemorySessionImpl = session()
        if (session != null) {
            session.checkValidState()
        }*/
    }

    companion object {
        // Cached unsafe-access object
        //val UNSAFE: jdk.internal.misc.Unsafe = jdk.internal.misc.Unsafe.getUnsafe()

        //val SCOPED_MEMORY_ACCESS: ScopedMemoryAccess = ScopedMemoryAccess.getScopedMemoryAccess()

        /**
         * The characteristics of Spliterators that traverse and split elements
         * maintained in Buffers.
         */
        /*val SPLITERATOR_CHARACTERISTICS: Int =
            java.util.Spliterator.SIZED or java.util.Spliterator.SUBSIZED or java.util.Spliterator.ORDERED*/

        /**
         * Returns an `IllegalArgumentException` indicating that the source
         * and target are the same `Buffer`.  Intended for use in
         * `put(src)` when the parameter is the `Buffer` on which the
         * method is being invoked.
         *
         * @return  IllegalArgumentException
         * With a message indicating equal source and target buffers
         */
        fun createSameBufferException(): IllegalArgumentException {
            return IllegalArgumentException("The source buffer is this buffer")
        }

        /**
         * Verify that the capacity is nonnegative.
         *
         * @param  capacity
         * The new buffer's capacity, in $type$s
         *
         * @throws IllegalArgumentException
         * If the `capacity` is a negative integer
         */
        fun createCapacityException(capacity: Int): IllegalArgumentException {
            assert(capacity < 0) { "capacity expected to be negative" }
            return IllegalArgumentException(
                ("capacity < 0: ("
                        + capacity + " < 0)")
            )
        }

        /**
         * Exception formatter that returns an [IndexOutOfBoundsException]
         * with no detail message.
         */
        private val IOOBE_FORMATTER: (String, MutableList<Number>) -> IndexOutOfBoundsException /*BiFunction<String, MutableList<Number>, IndexOutOfBoundsException>*/ =
            Preconditions.outOfBoundsExceptionFormatter<IndexOutOfBoundsException> { s: String ->
                IndexOutOfBoundsException()
            }

        /*init {
            // setup access to this package in SharedSecrets

            SharedSecrets.setJavaNioAccess(
                object : JavaNioAccess() {
                    val directBufferPool: BufferPool
                        get() = java.nio.Bits.BUFFER_POOL

                    override fun newDirectByteBuffer(
                        addr: Long,
                        cap: Int,
                        obj: Any,
                        segment: java.lang.foreign.MemorySegment
                    ): java.nio.ByteBuffer {
                        return DirectByteBuffer(addr, cap, obj, segment)
                    }

                    override fun newMappedByteBuffer(
                        unmapperProxy: UnmapperProxy,
                        address: Long,
                        cap: Int,
                        obj: Any,
                        segment: java.lang.foreign.MemorySegment
                    ): java.nio.ByteBuffer {
                        return if (unmapperProxy == null)
                            DirectByteBuffer(address, cap, obj, segment)
                        else
                            DirectByteBuffer(
                                address,
                                cap,
                                obj,
                                unmapperProxy.fileDescriptor(),
                                unmapperProxy.isSync(),
                                segment
                            )
                    }

                    override fun newHeapByteBuffer(
                        hb: ByteArray,
                        offset: Int,
                        capacity: Int,
                        segment: java.lang.foreign.MemorySegment
                    ): java.nio.ByteBuffer {
                        return HeapByteBuffer(hb, -1, 0, capacity, capacity, offset, segment)
                    }

                    override fun newDirectByteBuffer(addr: Long, cap: Int): java.nio.ByteBuffer {
                        return DirectByteBuffer(addr, cap.toLong())
                    }

                    *//*@ForceInline*//*
                    public override fun getBufferBase(buffer: Buffer): Any {
                        return buffer.base()
                    }

                    public override fun getBufferAddress(buffer: Buffer): Long {
                        return buffer.address
                    }

                    public override fun unmapper(buffer: Buffer): UnmapperProxy {
                        if (buffer is MappedByteBuffer) {
                            return buffer.unmapper()
                        } else {
                            return null
                        }
                    }

                    public override fun bufferSegment(buffer: Buffer): java.lang.foreign.MemorySegment {
                        return buffer.segment
                    }

                    public override fun acquireSession(buffer: Buffer) {
                        val scope: MemorySessionImpl = buffer.session()
                        if (scope != null) {
                            scope.acquire0()
                        }
                    }

                    public override fun releaseSession(buffer: Buffer) {
                        try {
                            val scope: MemorySessionImpl = buffer.session()
                            if (scope != null) {
                                scope.release0()
                            }
                        } finally {
                            java.lang.ref.Reference.reachabilityFence(buffer)
                        }
                    }

                    public override fun isThreadConfined(buffer: Buffer): Boolean {
                        val scope: MemorySessionImpl = buffer.session()
                        return scope != null && scope.ownerThread() != null
                    }

                    public override fun hasSession(buffer: Buffer): Boolean {
                        return buffer.session() != null
                    }

                    override fun mappedMemoryUtils(): MappedMemoryUtilsProxy {
                        return MappedMemoryUtils.PROXY
                    }

                    override fun reserveMemory(size: Long, cap: Long) {
                        java.nio.Bits.reserveMemory(size, cap)
                    }

                    override fun unreserveMemory(size: Long, cap: Long) {
                        java.nio.Bits.unreserveMemory(size, cap)
                    }

                    override fun pageSize(): Int {
                        return java.nio.Bits.pageSize()
                    }

                    *//*@ForceInline*//*
                    public override fun scaleShifts(buffer: Buffer): Int {
                        return buffer.scaleShifts()
                    }

                    *//*@ForceInline*//*
                    public override fun heapSegment(
                        buffer: Buffer,
                        base: Any,
                        offset: Long,
                        length: Long,
                        readOnly: Boolean,
                        bufferScope: MemorySessionImpl
                    ): AbstractMemorySegmentImpl {
                        return buffer.heapSegment(base, offset, length, readOnly, bufferScope)
                    }
                })
        }*/
    }
}
