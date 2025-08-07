package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.index.DocValuesUpdate.BinaryDocValuesUpdate
import org.gnit.lucenekmp.index.DocValuesUpdate.NumericDocValuesUpdate
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.Bits.MatchAllBits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefArray
import org.gnit.lucenekmp.util.BytesRefComparator
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.math.max
import kotlin.math.min


/**
 * This class efficiently buffers numeric and binary field updates and stores terms, values and
 * metadata in a memory efficient way without creating large amounts of objects. Update terms are
 * stored without de-duplicating the update term. In general we try to optimize for several
 * use-cases. For instance we try to use constant space for update terms field since the common case
 * always updates on the same field. Also for docUpTo we try to optimize for the case when updates
 * should be applied to all docs ie. docUpTo=Integer.MAX_VALUE. In other cases each update will
 * likely have a different docUpTo. Along the same lines this impl optimizes the case when all
 * updates have a value. Lastly, if all updates share the same value for a numeric field we only
 * store the value once.
 */
class FieldUpdatesBuffer private constructor(
    private val bytesUsed: Counter,
    initialValue: DocValuesUpdate,
    docUpTo: Int,
    isNumeric: Boolean
) {
    private var numUpdates = 1

    // we use a very simple approach and store the update term values without de-duplication
    // which is also not a common case to keep updating the same value more than once...
    // we might pay a higher price in terms of memory in certain cases but will gain
    // on CPU for those. We also use a stable sort to sort in order to apply the terms in order
    // since by definition we store them in order.
    private val termValues: BytesRefArray
    private var termSortState: BytesRefArray.SortState? = null
    private val byteValues: BytesRefArray? // this will be null if we are buffering numerics
    private var docsUpTo: IntArray
    private var numericValues: LongArray? = null // this will be null if we are buffering binaries
    private var hasValues: FixedBitSet? = null
    private var maxNumeric = Long.Companion.MIN_VALUE
    private var minNumeric = Long.Companion.MAX_VALUE
    private var fields: Array<String?>
    private val isNumeric: Boolean
    private var finished = false

    init {
        this.bytesUsed.addAndGet(SELF_SHALLOW_SIZE)
        termValues = BytesRefArray(bytesUsed)
        termValues.append(initialValue.term!!.bytes)
        fields = arrayOf<String?>(initialValue.term.field)
        bytesUsed.addAndGet(sizeOfString(initialValue.term.field))
        docsUpTo = intArrayOf(docUpTo)
        if (!initialValue.hasValue) {
            hasValues = FixedBitSet(1)
            bytesUsed.addAndGet(hasValues!!.ramBytesUsed())
        }
        this.isNumeric = isNumeric
        byteValues = if (isNumeric) null else BytesRefArray(bytesUsed)
    }

    constructor(bytesUsed: Counter, initialValue: NumericDocValuesUpdate, docUpTo: Int) : this(
        bytesUsed,
        initialValue,
        docUpTo,
        true
    ) {
        if (initialValue.hasValue()) {
            numericValues = longArrayOf(initialValue.getValue())
            minNumeric = initialValue.getValue()
            maxNumeric = minNumeric
        } else {
            numericValues = longArrayOf(0)
        }
        bytesUsed.addAndGet(Long.SIZE_BYTES.toLong())
    }

    constructor(bytesUsed: Counter, initialValue: BinaryDocValuesUpdate, docUpTo: Int) : this(
        bytesUsed,
        initialValue,
        docUpTo,
        false
    ) {
        if (initialValue.hasValue()) {
            byteValues!!.append(initialValue.getValue()!!)
        }
    }

    fun getMaxNumeric(): Long {
        require(isNumeric)
        if (minNumeric == Long.Companion.MAX_VALUE && maxNumeric == Long.Companion.MIN_VALUE) {
            return 0 // we don't have any value;
        }
        return maxNumeric
    }

    fun getMinNumeric(): Long {
        require(isNumeric)
        if (minNumeric == Long.Companion.MAX_VALUE && maxNumeric == Long.Companion.MIN_VALUE) {
            return 0 // we don't have any value
        }
        return minNumeric
    }

    fun add(field: String, docUpTo: Int, ord: Int, hasValue: Boolean) {
        require(!finished) { "buffer was finished already" }
        if (fields[0] != field || fields.size != 1) {
            if (fields.size <= ord) {
                val array: Array<String?> = ArrayUtil.grow(fields, ord + 1)
                if (fields.size == 1) {
                    Arrays.fill(array, 1, ord, fields[0])
                }
                bytesUsed.addAndGet(
                    (array.size - fields.size) * RamUsageEstimator.NUM_BYTES_OBJECT_REF.toLong()
                )
                fields = array
            }
            if (field !== fields[0]) { // that's an easy win of not accounting if there is an outlier
                bytesUsed.addAndGet(sizeOfString(field))
            }
            fields[ord] = field
        }

        if (docsUpTo[0] != docUpTo || docsUpTo.size != 1) {
            if (docsUpTo.size <= ord) {
                val array: IntArray = ArrayUtil.grow(docsUpTo, ord + 1)
                if (docsUpTo.size == 1) {
                    Arrays.fill(array, 1, ord, docsUpTo[0])
                }
                bytesUsed.addAndGet((array.size - docsUpTo.size) * Int.SIZE_BYTES.toLong())
                docsUpTo = array
            }
            docsUpTo[ord] = docUpTo
        }

        if (!hasValue || hasValues != null) {
            if (hasValues == null) {
                hasValues = FixedBitSet(ord + 1)
                hasValues!!.set(0, ord)
                bytesUsed.addAndGet(hasValues!!.ramBytesUsed())
            } else if (hasValues!!.length() <= ord) {
                val fixedBitSet: FixedBitSet =
                    FixedBitSet.ensureCapacity(hasValues!!, ArrayUtil.oversize(ord + 1, 1))
                bytesUsed.addAndGet(fixedBitSet.ramBytesUsed() - hasValues!!.ramBytesUsed())
                hasValues = fixedBitSet
            }
            if (hasValue) {
                hasValues!!.set(ord)
            }
        }
    }

    fun addUpdate(term: Term, value: Long, docUpTo: Int) {
        require(isNumeric)
        val ord = append(term)
        val field = term.field
        add(field, docUpTo, ord, true)
        minNumeric = min(minNumeric, value)
        maxNumeric = max(maxNumeric, value)
        if (numericValues!![0] != value || numericValues!!.size != 1) {
            if (numericValues!!.size <= ord) {
                val array: LongArray = ArrayUtil.grow(numericValues!!, ord + 1)
                if (numericValues!!.size == 1) {
                    Arrays.fill(array, 1, ord, numericValues!![0])
                }
                bytesUsed.addAndGet((array.size - numericValues!!.size) * Long.SIZE_BYTES.toLong())
                numericValues = array
            }
            numericValues!![ord] = value
        }
    }

    fun addNoValue(term: Term, docUpTo: Int) {
        val ord = append(term)
        add(term.field, docUpTo, ord, false)
    }

    fun addUpdate(term: Term, value: BytesRef?, docUpTo: Int) {
        require(!isNumeric)
        val ord = append(term)
        byteValues!!.append(value!!)
        add(term.field, docUpTo, ord, true)
    }

    private fun append(term: Term): Int {
        termValues.append(term.bytes)
        return numUpdates++
    }

    fun finish() {
        check(!finished) { "buffer was finished already" }
        finished = true
        val sortedTerms = hasSingleValue() && hasValues == null && fields.size == 1
        if (sortedTerms) {
            termSortState = termValues.sort(BytesRefComparator.NATURAL, true)
            require(assertTermAndDocInOrder())
            bytesUsed.addAndGet(termSortState!!.ramBytesUsed())
        }
    }

    private fun assertTermAndDocInOrder(): Boolean {
        try {
            val iterator: BytesRefArray.IndexedBytesRefIterator = termValues.iterator(termSortState)
            var last: BytesRef? = null
            var lastOrd = -1
            var current: BytesRef?
            while ((iterator.next().also { current = it }) != null) {
                if (last != null) {
                    val cmp = current!!.compareTo(last)
                    require(cmp >= 0) { "term in reverse order" }
                    require(
                        cmp != 0
                                || (docsUpTo[getArrayIndex(docsUpTo.size, lastOrd)]
                                <= docsUpTo[getArrayIndex(docsUpTo.size, iterator.ord())])
                    ) { "doc id in reverse order" }
                }
                last = BytesRef.deepCopyOf(current!!)
                lastOrd = iterator.ord()
            }
        } catch (e: IOException) {
            require(false) { e.message!! }
        }
        return true
    }

    fun iterator(): BufferedUpdateIterator {
        check(finished) { "buffer is not finished yet" }
        return BufferedUpdateIterator()
    }

    fun isNumeric(): Boolean {
        require(isNumeric || byteValues != null)
        return isNumeric
    }

    fun hasSingleValue(): Boolean {
        // we only do this optimization for numerics so far.
        return isNumeric && numericValues!!.size == 1
    }

    fun getNumericValue(idx: Int): Long {
        if (hasValues != null && !hasValues!!.get(idx)) {
            return 0
        }
        return numericValues!![getArrayIndex(numericValues!!.size, idx)]
    }

    /** Struct like class that is used to iterate over all updates in this buffer  */
    class BufferedUpdate internal constructor() {
        /** the max document ID this update should be applied to  */
        var docUpTo: Int = 0

        /** a numeric value or 0 if this buffer holds binary updates  */
        var numericValue: Long = 0

        /** a binary value or null if this buffer holds numeric updates  */
        var binaryValue: BytesRef? = null

        /** `true` if this update has a value  */
        var hasValue: Boolean = false

        /** The update terms field. This will never be null.  */
        var termField: String? = null

        /** The update terms value. This will never be null.  */
        var termValue: BytesRef? = null

        override fun hashCode(): Int {
            throw UnsupportedOperationException(
                "this struct should not be use in map or other data-structures that use hashCode / equals"
            )
        }

        override fun equals(obj: Any?): Boolean {
            throw UnsupportedOperationException(
                "this struct should not be use in map or other data-structures that use hashCode / equals"
            )
        }
    }

    /** An iterator that iterates over all updates in insertion order  */
    inner class BufferedUpdateIterator {
        private val termValuesIterator: BytesRefArray.IndexedBytesRefIterator = termValues.iterator(termSortState)
        private val lookAheadTermIterator: BytesRefArray.IndexedBytesRefIterator? = if (termSortState != null) termValues.iterator(termSortState) else null
        private val byteValuesIterator: BytesRefIterator? = if (isNumeric) null else byteValues!!.iterator()
        private val bufferedUpdate = BufferedUpdate()
        private val updatesWithValue: Bits? = if (hasValues == null) MatchAllBits(numUpdates) else hasValues

        val isSortedTerms: Boolean
            /**
             * If all updates update a single field to the same value, then we can apply these updates in
             * the term order instead of the request order as both will yield the same result. This
             * optimization allows us to iterate the term dictionary faster and de-duplicate updates.
             */
            get() = termSortState != null

        /**
         * Moves to the next BufferedUpdate or return null if all updates are consumed. The returned
         * instance is a shared instance and must be fully consumed before the next call to this method.
         */
        @Throws(IOException::class)
        fun next(): BufferedUpdate? {
            val next: BytesRef? = nextTerm()
            if (next != null) {
                val idx: Int = termValuesIterator.ord()
                bufferedUpdate.termValue = next
                bufferedUpdate.hasValue = updatesWithValue!!.get(idx)
                bufferedUpdate.termField = fields[getArrayIndex(fields.size, idx)]
                bufferedUpdate.docUpTo = docsUpTo[getArrayIndex(docsUpTo.size, idx)]
                if (bufferedUpdate.hasValue) {
                    if (isNumeric) {
                        bufferedUpdate.numericValue = numericValues!![getArrayIndex(numericValues!!.size, idx)]
                        bufferedUpdate.binaryValue = null
                    } else {
                        bufferedUpdate.binaryValue = byteValuesIterator!!.next()
                    }
                } else {
                    bufferedUpdate.binaryValue = null
                    bufferedUpdate.numericValue = 0
                }
                return bufferedUpdate
            } else {
                return null
            }
        }

        @Throws(IOException::class)
        private fun nextTerm(): BytesRef? {
            if (lookAheadTermIterator != null) {
                if (bufferedUpdate.termValue == null) {
                    lookAheadTermIterator.next()
                }
                var lastTerm: BytesRef?
                var aheadTerm: BytesRef?
                do {
                    aheadTerm = lookAheadTermIterator.next()
                    lastTerm = termValuesIterator.next()
                } while (aheadTerm != null // Shortcut to avoid equals, we did a stable sort before, so aheadTerm can only equal
                    // lastTerm when aheadTerm has a lager ord.
                    && lookAheadTermIterator.ord() > termValuesIterator.ord() && aheadTerm == lastTerm
                )
                return lastTerm
            } else {
                return termValuesIterator.next()
            }
        }
    }

    companion object {
        private val SELF_SHALLOW_SIZE: Long = RamUsageEstimator.shallowSizeOfInstance(FieldUpdatesBuffer::class)
        private val STRING_SHALLOW_SIZE: Long = RamUsageEstimator.shallowSizeOfInstance(String::class)
        private fun sizeOfString(string: String): Long {
            return STRING_SHALLOW_SIZE + (string.length * Character.BYTES.toLong())
        }

        private fun getArrayIndex(arrayLength: Int, index: Int): Int {
            require(
                arrayLength == 1 || arrayLength > index
            ) { "illegal array index length: $arrayLength index: $index" }
            return min(arrayLength - 1, index)
        }
    }
}
