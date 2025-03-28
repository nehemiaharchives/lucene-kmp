package org.gnit.lucenekmp.util


import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.util.ByteBlockPool.DirectAllocator
import kotlin.jvm.JvmOverloads


/**
 * [BytesRefHash] is a special purpose hash-map like data-structure optimized for [ ] instances. BytesRefHash maintains mappings of byte arrays to ids
 * (Map&lt;BytesRef,int&gt;) storing the hashed bytes efficiently in continuous storage. The mapping
 * to the id is encapsulated inside [BytesRefHash] and is guaranteed to be increased for each
 * added [BytesRef].
 *
 *
 * Note: The maximum capacity [BytesRef] instance passed to [.add] must not
 * be longer than [ByteBlockPool.BYTE_BLOCK_SIZE]-2. The internal storage is limited to 2GB
 * total byte storage.
 *
 * @lucene.internal
 */
class BytesRefHash(pool: ByteBlockPool, capacity: Int, bytesStartArray: BytesStartArray) : Accountable {
    // the following fields are needed by comparator,
    // so package private to prevent access$-methods:
    val pool: BytesRefBlockPool
    var bytesStart: IntArray?

    private var hashSize: Int
    private var hashHalfSize: Int
    private var hashMask: Int
    private var count = 0
    private var lastCount = -1
    private var ids: IntArray?
    private val bytesStartArray: BytesStartArray
    private val bytesUsed: Counter

    /** Creates a new [BytesRefHash]  */
    /**
     * Creates a new [BytesRefHash] with a [ByteBlockPool] using a [ ].
     */
    @JvmOverloads
    constructor(pool: ByteBlockPool = ByteBlockPool(DirectAllocator())) : this(
        pool, DEFAULT_CAPACITY, DirectBytesStartArray(
            DEFAULT_CAPACITY
        )
    )

    /** Creates a new [BytesRefHash]  */
    init {
        hashSize = capacity
        hashHalfSize = hashSize shr 1
        hashMask = hashSize - 1
        this.pool = BytesRefBlockPool(pool)
        ids = IntArray(hashSize)
        Arrays.fill(ids!!, -1)
        this.bytesStartArray = bytesStartArray
        bytesStart = bytesStartArray.init()
        val bytesUsed = bytesStartArray.bytesUsed()
        this.bytesUsed = if (bytesUsed == null) Counter.newCounter() else bytesUsed
        bytesUsed!!.addAndGet(hashSize * Int.SIZE_BYTES.toLong())
    }

    /**
     * Returns the number of [BytesRef] values in this [BytesRefHash].
     *
     * @return the number of [BytesRef] values in this [BytesRefHash].
     */
    fun size(): Int {
        return count
    }

    /**
     * Populates and returns a [BytesRef] with the bytes for the given bytesID.
     *
     *
     * Note: the given bytesID must be a positive integer less than the current size ([ ][.size])
     *
     * @param bytesID the id
     * @param ref the [BytesRef] to populate
     * @return the given BytesRef instance populated with the bytes for the given bytesID
     */
    fun get(bytesID: Int, ref: BytesRef): BytesRef {
        checkNotNull(bytesStart) { "bytesStart is null - not initialized" }
        require(bytesID < bytesStart!!.size) { "bytesID exceeds byteStart len: " + bytesStart!!.size }
        pool.fillBytesRef(ref, bytesStart!![bytesID])
        return ref
    }

    /**
     * Returns the ids array in arbitrary order. Valid ids start at offset of 0 and end at a limit of
     * [.size] - 1
     *
     *
     * Note: This is a destructive operation. [.clear] must be called in order to reuse
     * this [BytesRefHash] instance.
     *
     * @lucene.internal
     */
    fun compact(): IntArray {
        checkNotNull(bytesStart) { "bytesStart is null - not initialized" }
        var upto = 0
        for (i in 0..<hashSize) {
            if (ids!![i] != -1) {
                if (upto < i) {
                    ids!![upto] = ids!![i]
                    ids!![i] = -1
                }
                upto++
            }
        }

        require(upto == count)
        lastCount = count
        return ids!!
    }

    /**
     * Returns the values array sorted by the referenced byte values.
     *
     *
     * Note: This is a destructive operation. [.clear] must be called in order to reuse
     * this [BytesRefHash] instance.
     */
    fun sort(): IntArray {
        val compact = compact()
        require(count * 2 <= compact.size) { "We need load factor <= 0.5f to speed up this sort" }
        val tmpOffset = count
        object : StringSorter(BytesRefComparator.NATURAL) {
            override fun radixSorter(cmp: BytesRefComparator): Sorter {
                return object : MSBStringRadixSorter(cmp) {
                    private var k = 0

                    override fun buildHistogram(
                        prefixCommonBucket: Int,
                        prefixCommonLen: Int,
                        from: Int,
                        to: Int,
                        k: Int,
                        histogram: IntArray
                    ) {
                        this.k = k
                        histogram[prefixCommonBucket] = prefixCommonLen
                        Arrays.fill(
                            compact, tmpOffset + from - prefixCommonLen, tmpOffset + from, prefixCommonBucket
                        )
                        for (i in from..<to) {
                            val b: Int = getBucket(i, k)
                            compact[tmpOffset + i] = b
                            histogram[b]++
                        }
                    }

                    override fun shouldFallback(from: Int, to: Int, l: Int): Boolean {
                        // We lower the fallback threshold because the bucket cache speeds up the reorder
                        return to - from <= LENGTH_THRESHOLD / 2 || l >= LEVEL_THRESHOLD
                    }

                    fun swapBucketCache(i: Int, j: Int) {
                        swap(i, j)
                        val tmp = compact[tmpOffset + i]
                        compact[tmpOffset + i] = compact[tmpOffset + j]
                        compact[tmpOffset + j] = tmp
                    }

                    override fun reorder(
                        from: Int,
                        to: Int,
                        startOffsets: IntArray,
                        endOffsets: IntArray,
                        k: Int
                    ) {
                        require(this.k == k)
                        for (i in 0..<HISTOGRAM_SIZE) {
                            val limit = endOffsets[i]
                            var h1 = startOffsets[i]
                            while (h1 < limit) {
                                val b = compact[tmpOffset + from + h1]
                                val h2: Int = startOffsets[b]++
                                swapBucketCache(from + h1, from + h2)
                                h1 = startOffsets[i]
                            }
                        }
                    }
                }
            }

            override fun swap(i: Int, j: Int) {
                val tmp = compact[i]
                compact[i] = compact[j]
                compact[j] = tmp
            }

            override fun get(builder: BytesRefBuilder, result: BytesRef, i: Int) {
                pool.fillBytesRef(result, bytesStart!![compact[i]])
            }
        }.sort(0, count)
        Arrays.fill(compact, tmpOffset, compact.size, -1)
        return compact
    }

    private fun shrink(targetSize: Int): Boolean {
        // Cannot use ArrayUtil.shrink because we require power
        // of 2:
        var newSize = hashSize
        while (newSize >= 8 && newSize / 4 > targetSize) {
            newSize /= 2
        }
        if (newSize != hashSize) {
            bytesUsed.addAndGet(Int.SIZE_BYTES * -(hashSize - newSize).toLong())
            hashSize = newSize
            ids = IntArray(hashSize)
            Arrays.fill(ids!!, -1)
            hashHalfSize = newSize / 2
            hashMask = newSize - 1
            return true
        } else {
            return false
        }
    }

    /** Clears the [BytesRef] which maps to the given [BytesRef]  */
    @JvmOverloads
    fun clear(resetPool: Boolean = true) {
        lastCount = count
        count = 0
        if (resetPool) {
            pool.reset()
        }
        bytesStart = bytesStartArray.clear()
        if (lastCount != -1 && shrink(lastCount)) {
            // shrink clears the hash entries
            return
        }
        Arrays.fill(ids!!, -1)
    }

    /** Closes the BytesRefHash and releases all internally used memory  */
    fun close() {
        clear(true)
        ids = null
        bytesUsed.addAndGet(Int.SIZE_BYTES * -hashSize.toLong())
    }

    /**
     * Adds a new [BytesRef]
     *
     * @param bytes the bytes to hash
     * @return the id the given bytes are hashed if there was no mapping for the given bytes,
     * otherwise `(-(id)-1)`. This guarantees that the return value will always be
     * &gt;= 0 if the given bytes haven't been hashed before.
     * @throws MaxBytesLengthExceededException if the given bytes are `> 2 +` [     ][ByteBlockPool.BYTE_BLOCK_SIZE]
     */
    fun add(bytes: BytesRef): Int {
        checkNotNull(bytesStart) { "Bytesstart is null - not initialized" }
        // final position
        val hashPos = findHash(bytes)
        var e = ids!![hashPos]

        if (e == -1) {
            // new entry
            if (count >= bytesStart!!.size) {
                bytesStart = bytesStartArray.grow()
                require(count < bytesStart!!.size + 1) { "count: " + count + " len: " + bytesStart!!.size }
            }
            bytesStart!![count] = pool.addBytesRef(bytes)
            e = count++
            require(ids!![hashPos] == -1)
            ids!![hashPos] = e

            if (count == hashHalfSize) {
                rehash(2 * hashSize, true)
            }
            return e
        }
        return -(e + 1)
    }

    /**
     * Returns the id of the given [BytesRef].
     *
     * @param bytes the bytes to look for
     * @return the id of the given bytes, or `-1` if there is no mapping for the given bytes.
     */
    fun find(bytes: BytesRef): Int {
        return ids!![findHash(bytes)]
    }

    private fun findHash(bytes: BytesRef): Int {
        checkNotNull(bytesStart) { "bytesStart is null - not initialized" }

        var code = doHash(bytes.bytes, bytes.offset, bytes.length)

        // final position
        var hashPos = code and hashMask
        var e = ids!![hashPos]
        if (e != -1 && pool.equals(bytesStart!![e], bytes) === false) {
            // Conflict; use linear probe to find an open slot
            // (see LUCENE-5604):
            do {
                code++
                hashPos = code and hashMask
                e = ids!![hashPos]
            } while (e != -1 && pool.equals(bytesStart!![e], bytes) === false)
        }

        return hashPos
    }

    /**
     * Adds a "arbitrary" int offset instead of a BytesRef term. This is used in the indexer to hold
     * the hash for term vectors, because they do not redundantly store the byte[] term directly and
     * instead reference the byte[] term already stored by the postings BytesRefHash. See add(int
     * textStart) in TermsHashPerField.
     */
    fun addByPoolOffset(offset: Int): Int {
        checkNotNull(bytesStart) { "Bytesstart is null - not initialized" }
        // final position
        var code = offset
        var hashPos = offset and hashMask
        var e = ids!![hashPos]
        if (e != -1 && bytesStart!![e] != offset) {
            // Conflict; use linear probe to find an open slot
            // (see LUCENE-5604):
            do {
                code++
                hashPos = code and hashMask
                e = ids!![hashPos]
            } while (e != -1 && bytesStart!![e] != offset)
        }
        if (e == -1) {
            // new entry
            if (count >= bytesStart!!.size) {
                bytesStart = bytesStartArray.grow()
                require(count < bytesStart!!.size + 1) { "count: " + count + " len: " + bytesStart!!.size }
            }
            e = count++
            bytesStart!![e] = offset
            require(ids!![hashPos] == -1)
            ids!![hashPos] = e

            if (count == hashHalfSize) {
                rehash(2 * hashSize, false)
            }
            return e
        }
        return -(e + 1)
    }

    /**
     * Called when hash is too small (`> 50%` occupied) or too large (`< 20%` occupied).
     */
    private fun rehash(newSize: Int, hashOnData: Boolean) {
        val newMask = newSize - 1
        bytesUsed.addAndGet(Int.SIZE_BYTES * newSize.toLong())
        val newHash = IntArray(newSize)
        Arrays.fill(newHash, -1)
        for (i in 0..<hashSize) {
            val e0 = ids!![i]
            if (e0 != -1) {
                var code: Int
                if (hashOnData) {
                    code = pool.hash(bytesStart!![e0])
                } else {
                    code = bytesStart!![e0]
                }

                var hashPos = code and newMask
                require(hashPos >= 0)
                if (newHash[hashPos] != -1) {
                    // Conflict; use linear probe to find an open slot
                    // (see LUCENE-5604):
                    do {
                        code++
                        hashPos = code and newMask
                    } while (newHash[hashPos] != -1)
                }
                newHash[hashPos] = e0
            }
        }

        hashMask = newMask
        bytesUsed.addAndGet(Int.SIZE_BYTES * -ids!!.size.toLong())
        ids = newHash
        hashSize = newSize
        hashHalfSize = newSize / 2
    }

    /**
     * reinitializes the [BytesRefHash] after a previous [.clear] call. If [ ][.clear] has not been called previously this method has no effect.
     */
    fun reinit() {
        if (bytesStart == null) {
            bytesStart = bytesStartArray.init()
        }

        if (ids == null) {
            ids = IntArray(hashSize)
            bytesUsed.addAndGet(Int.SIZE_BYTES * hashSize.toLong())
        }
    }

    /**
     * Returns the bytesStart offset into the internally used [ByteBlockPool] for the given
     * bytesID
     *
     * @param bytesID the id to look up
     * @return the bytesStart offset into the internally used [ByteBlockPool] for the given id
     */
    fun byteStart(bytesID: Int): Int {
        checkNotNull(bytesStart) { "bytesStart is null - not initialized" }
        require(bytesID >= 0 && bytesID < count) { bytesID }
        return bytesStart!![bytesID]
    }

    public override fun ramBytesUsed(): Long {
        val size =
            (BASE_RAM_BYTES
                    + RamUsageEstimator.sizeOfObject(bytesStart)
                    + RamUsageEstimator.sizeOfObject(ids)
                    + RamUsageEstimator.sizeOfObject(pool))
        return size
    }

    /**
     * Thrown if a [BytesRef] exceeds the [BytesRefHash] limit of [ ][ByteBlockPool.BYTE_BLOCK_SIZE]-2.
     */
    class MaxBytesLengthExceededException internal constructor(message: String?) : RuntimeException(message)

    /** Manages allocation of the per-term addresses.  */
    abstract class BytesStartArray {
        /**
         * Initializes the BytesStartArray. This call will allocate memory
         *
         * @return the initialized bytes start array
         */
        abstract fun init(): IntArray?

        /**
         * Grows the [BytesStartArray]
         *
         * @return the grown array
         */
        abstract fun grow(): IntArray?

        /**
         * clears the [BytesStartArray] and returns the cleared instance.
         *
         * @return the cleared instance, this might be `null`
         */
        abstract fun clear(): IntArray?

        /**
         * A [Counter] reference holding the number of bytes used by this [BytesStartArray].
         * The [BytesRefHash] uses this reference to track it memory usage
         *
         * @return a [AtomicLong] reference holding the number of bytes used by this [     ].
         */
        abstract fun bytesUsed(): Counter
    }

    /**
     * A simple [BytesStartArray] that tracks memory allocation using a private [Counter]
     * instance.
     */
    open class DirectBytesStartArray @JvmOverloads constructor(// TODO: can't we just merge this w/
        // TrackingDirectBytesStartArray...?  Just add a ctor
        // that makes a private bytesUsed?
        protected val initSize: Int, private val bytesUsed: Counter = Counter.newCounter()
    ) : BytesStartArray() {
        private var bytesStart: IntArray? = IntArray(0)

        override fun clear(): IntArray? {
            return null.also { bytesStart = it }
        }

        override fun grow(): IntArray {
            checkNotNull(bytesStart)
            return ArrayUtil.grow(bytesStart!!, bytesStart!!.size + 1).also { bytesStart = it }
        }

        override fun init(): IntArray {
            return IntArray(ArrayUtil.oversize(initSize, Int.SIZE_BYTES)).also { bytesStart = it }
        }

        override fun bytesUsed(): Counter {
            return bytesUsed
        }
    }

    companion object {
        private val BASE_RAM_BYTES = (RamUsageEstimator.shallowSizeOfInstance(BytesRefHash::class)
                +  // size of Counter
                RamUsageEstimator.primitiveSizes[Long::class]!!)

        const val DEFAULT_CAPACITY: Int = 16

        // TODO: maybe use long?  But our keys are typically short...
        fun doHash(bytes: ByteArray?, offset: Int, length: Int): Int {
            return StringHelper.murmurhash3_x86_32(bytes!!, offset, length, StringHelper.GOOD_FAST_HASH_SEED)
        }
    }
}
