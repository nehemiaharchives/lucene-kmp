package org.gnit.lucenekmp.search.comparators

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.PointTree
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.*
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.math.max
import kotlin.math.min

/**
 * Abstract numeric comparator for comparing numeric values. This comparator provides a skipping
 * functionality – an iterator that can skip over non-competitive documents.
 *
 *
 * Parameter `field` provided in the constructor is used as a field name in the default
 * implementations of the methods `getNumericDocValues` and `getPointValues` to retrieve
 * doc values and points. You can pass a dummy value for a field name (e.g. when sorting by script),
 * but in this case you must override both of these methods.
 */
abstract class NumericComparator<T : Number> protected constructor(
    protected val field: String,
    protected val missingValue: T,
    reverse: Boolean,
    pruning: Pruning,
    bytesCount: Int
) : FieldComparator<T>() {
    private val missingValueAsLong: Long
    protected val reverse: Boolean
    private val bytesCount: Int // how many bytes are used to encode this number

    protected var topValueSet: Boolean = false
    protected var singleSort: Boolean = false // singleSort is true, if sort is based on a single sort field.
    protected var hitsThresholdReached: Boolean = false
    protected var queueFull: Boolean = false
    protected var pruning: Pruning

    init {
        this.missingValueAsLong = missingValueAsComparableLong()
        this.reverse = reverse
        this.pruning = pruning
        this.bytesCount = bytesCount
    }

    override fun setTopValue(value: T) {
        topValueSet = true
    }

    override fun setSingleSort() {
        singleSort = true
    }

    override fun disableSkipping() {
        pruning = Pruning.NONE
    }

    protected abstract fun missingValueAsComparableLong(): Long

    /**
     * Decode sortable bytes to long. It should be consistent with the codec that [PointValues]
     * of this field is using.
     */
    protected abstract fun sortableBytesToLong(bytes: ByteArray): Long

    /** Leaf comparator for [NumericComparator] that provides skipping functionality  */
    abstract inner class NumericLeafComparator(context: LeafReaderContext) : LeafFieldComparator {
        private val context: LeafReaderContext
        protected val docValues: NumericDocValues
        private val pointValues: PointValues?

        private var pointTree: PointTree?

        // if skipping functionality should be enabled on this segment
        private var enableSkipping = false
        private var maxDoc = 0

        /** According to [FieldComparator.setTopValue], topValueSet is final in leafComparator  */
        private val leafTopSet = topValueSet

        private var minValueAsLong = Long.Companion.MIN_VALUE
        private var maxValueAsLong = Long.Companion.MAX_VALUE

        private lateinit var competitiveIterator: DocIdSetIterator
        private var iteratorCost: Long = -1
        private var maxDocVisited = -1
        private var updateCounter = 0
        private var currentSkipInterval = MIN_SKIP_INTERVAL

        // helps to be conservative about increasing the sampling interval
        private var tryUpdateFailCount = 0

        init {
            this.context = context
            this.docValues = getNumericDocValues(context, field)
            this.pointValues = if (pruning !== Pruning.NONE) context.reader().getPointValues(field) else null
            this.pointTree = pointValues?.pointTree
            if (pointValues != null) {
                val info: FieldInfo? = context.reader().fieldInfos.fieldInfo(field)
                check(!(info == null || info.pointDimensionCount == 0)) {
                    ("Field "
                            + field
                            + " doesn't index points according to FieldInfos yet returns non-null PointValues")
                }
                require(!(info.pointDimensionCount > 1)) { "Field $field is indexed with multiple dimensions, sorting is not supported" }
                require(info.pointNumBytes == bytesCount) {
                    ("Field "
                            + field
                            + " is indexed with "
                            + info.pointNumBytes
                            + " bytes per dimension, but "
                            + this@NumericComparator
                            + " expected "
                            + bytesCount)
                }
                this.enableSkipping = true // skipping is enabled when points are available
                this.maxDoc = context.reader().maxDoc()
                this.competitiveIterator = DocIdSetIterator.all(maxDoc)
                if (leafTopSet) {
                    encodeTop()
                }
            } else {
                this.enableSkipping = false
                this.maxDoc = 0
            }
        }

        /**
         * Retrieves the NumericDocValues for the field in this segment
         *
         *
         * If you override this method, you should probably always disable skipping as the comparator
         * uses values from the points index to build its competitive iterators, and assumes that the
         * values in doc values and points are the same.
         *
         * @param context – reader context
         * @param field - field name
         * @return numeric doc values for the field in this segment.
         * @throws IOException If there is a low-level I/O error
         */
        @Throws(IOException::class)
        protected fun getNumericDocValues(context: LeafReaderContext, field: String): NumericDocValues {
            return DocValues.getNumeric(context.reader(), field)
        }

        @Throws(IOException::class)
        override fun setBottom(slot: Int) {
            queueFull = true // if we are setting bottom, it means that we have collected enough hits
            updateCompetitiveIterator() // update an iterator if we set a new bottom
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            maxDocVisited = doc
        }

        @Throws(IOException::class)
        override fun setScorer(scorer: Scorable) {
            if (iteratorCost == -1L) {
                if (scorer is Scorer) {
                    iteratorCost =
                        (scorer as Scorer).iterator().cost() // starting iterator cost is the scorer's cost
                } else {
                    iteratorCost = maxDoc.toLong()
                }
                updateCompetitiveIterator() // update an iterator when we have a new segment
            }
        }

        @Throws(IOException::class)
        override fun setHitsThresholdReached() {
            hitsThresholdReached = true
            updateCompetitiveIterator()
        }

        // update its iterator to include possibly only docs that are "stronger" than the current bottom
        // entry
        @Throws(IOException::class)
        private fun updateCompetitiveIterator() {
            if (enableSkipping == false || hitsThresholdReached == false || (leafTopSet == false && queueFull == false)) return
            // if some documents have missing points, check that missing values prohibits optimization
            if ((pointValues!!.docCount < maxDoc) && this.isMissingValueCompetitive) {
                return  // we can't filter out documents, as documents with missing values are competitive
            }

            updateCounter++
            // Start sampling if we get called too much
            if (updateCounter > 256
                && (updateCounter and (currentSkipInterval - 1)) != currentSkipInterval - 1
            ) {
                return
            }

            if (queueFull) {
                encodeBottom()
            }

            val result: DocIdSetBuilder = DocIdSetBuilder(maxDoc)
            val visitor: IntersectVisitor =
                object : IntersectVisitor {
                    var adder: DocIdSetBuilder.BulkAdder? = null

                    override fun grow(count: Int) {
                        adder = result.grow(count)
                    }

                    override fun visit(docID: Int) {
                        if (docID <= maxDocVisited) {
                            return  // Already visited or skipped
                        }
                        adder!!.add(docID)
                    }

                    override fun visit(docID: Int, packedValue: ByteArray) {
                        if (docID <= maxDocVisited) {
                            return  // already visited or skipped
                        }
                        val l = sortableBytesToLong(packedValue)
                        if (l >= minValueAsLong && l <= maxValueAsLong) {
                            adder!!.add(docID) // doc is competitive
                        }
                    }

                    override fun compare(
                        minPackedValue: ByteArray,
                        maxPackedValue: ByteArray
                    ): PointValues.Relation {
                        val min = sortableBytesToLong(minPackedValue)
                        val max = sortableBytesToLong(maxPackedValue)

                        if (min > maxValueAsLong || max < minValueAsLong) {
                            // 1. cmp ==0 and pruning==Pruning.GREATER_THAN_OR_EQUAL_TO : if the sort is
                            // ascending then maxValueAsLong is bottom's next less value, so it is competitive
                            // 2. cmp ==0 and pruning==Pruning.GREATER_THAN: maxValueAsLong equals to
                            // bottom, but there are multiple comparators, so it could be competitive
                            return PointValues.Relation.CELL_OUTSIDE_QUERY
                        }

                        if (min < minValueAsLong || max > maxValueAsLong) {
                            return PointValues.Relation.CELL_CROSSES_QUERY
                        }
                        return PointValues.Relation.CELL_INSIDE_QUERY
                    }
                }

            val threshold = iteratorCost ushr 3

            if (PointValues.isEstimatedPointCountGreaterThanOrEqualTo(
                    visitor, this.pointTree!!, threshold
                )
            ) {
                // the new range is not selective enough to be worth materializing, it doesn't reduce number
                // of docs at least 8x
                updateSkipInterval(false)
                if (pointValues.docCount < iteratorCost) {
                    // Use the set of doc with values to help drive iteration
                    competitiveIterator = getNumericDocValues(context, field)
                    iteratorCost = pointValues.docCount.toLong()
                }
                return
            }
            pointValues.intersect(visitor)
            competitiveIterator = result.build().iterator()
            iteratorCost = competitiveIterator.cost()
            updateSkipInterval(true)
        }

        private fun updateSkipInterval(success: Boolean) {
            if (updateCounter > 256) {
                if (success) {
                    currentSkipInterval = max(currentSkipInterval / 2, MIN_SKIP_INTERVAL)
                    tryUpdateFailCount = 0
                } else {
                    if (tryUpdateFailCount >= 3) {
                        currentSkipInterval = min(currentSkipInterval * 2, MAX_SKIP_INTERVAL)
                        tryUpdateFailCount = 0
                    } else {
                        tryUpdateFailCount++
                    }
                }
            }
        }

        /**
         * If [NumericComparator.pruning] equals [Pruning.GREATER_THAN_OR_EQUAL_TO], we
         * could better tune the [NumericLeafComparator.maxValueAsLong]/[ ][NumericLeafComparator.minValueAsLong]. For instance, if the sort is ascending and bottom
         * value is 5, we will use a range on [MIN_VALUE, 4].
         */
        private fun encodeBottom() {
            if (reverse == false) {
                maxValueAsLong = bottomAsComparableLong()
                if (pruning === Pruning.GREATER_THAN_OR_EQUAL_TO && maxValueAsLong != Long.Companion.MIN_VALUE) {
                    maxValueAsLong--
                }
            } else {
                minValueAsLong = bottomAsComparableLong()
                if (pruning === Pruning.GREATER_THAN_OR_EQUAL_TO && minValueAsLong != Long.Companion.MAX_VALUE) {
                    minValueAsLong++
                }
            }
        }

        /**
         * If [NumericComparator.pruning] equals [Pruning.GREATER_THAN_OR_EQUAL_TO], we
         * could better tune the [NumericLeafComparator.minValueAsLong]/[ ][NumericLeafComparator.minValueAsLong]. For instance, if the sort is ascending and top value
         * is 3, we will use a range on [4, MAX_VALUE].
         */
        private fun encodeTop() {
            if (reverse == false) {
                minValueAsLong = topAsComparableLong()
                if (singleSort
                    && pruning === Pruning.GREATER_THAN_OR_EQUAL_TO && queueFull
                    && minValueAsLong != Long.Companion.MAX_VALUE
                ) {
                    minValueAsLong++
                }
            } else {
                maxValueAsLong = topAsComparableLong()
                if (singleSort
                    && pruning === Pruning.GREATER_THAN_OR_EQUAL_TO && queueFull
                    && maxValueAsLong != Long.Companion.MIN_VALUE
                ) {
                    maxValueAsLong--
                }
            }
        }

        private val isMissingValueCompetitive: Boolean
            get() {
                // if queue is full, compare with bottom first,
                // if competitive, then check if we can compare with topValue
                if (queueFull) {
                    val result: Int = Long.compare(missingValueAsLong, bottomAsComparableLong())
                    // in reverse (desc) sort missingValue is competitive when it's greater or equal to bottom,
                    // in asc sort missingValue is competitive when it's smaller or equal to bottom
                    val competitive =
                        if (reverse)
                            (if (pruning === Pruning.GREATER_THAN_OR_EQUAL_TO) result > 0 else result >= 0)
                        else
                            (if (pruning === Pruning.GREATER_THAN_OR_EQUAL_TO) result < 0 else result <= 0)
                    if (competitive == false) {
                        return false
                    }
                }

                if (leafTopSet) {
                    val result: Int = Long.compare(missingValueAsLong, topAsComparableLong())
                    // in reverse (desc) sort missingValue is competitive when it's smaller or equal to
                    // topValue,
                    // in asc sort missingValue is competitive when it's greater or equal to topValue
                    return if (reverse) (result <= 0) else (result >= 0)
                }

                // by default competitive
                return true
            }

        override fun competitiveIterator(): DocIdSetIterator? {
            if (enableSkipping == false) return null
            return object : DocIdSetIterator() {
                private var docID: Int = competitiveIterator.docID()

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    return advance(docID + 1)
                }

                override fun docID(): Int {
                    return docID
                }

                override fun cost(): Long {
                    return competitiveIterator.cost()
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    return competitiveIterator.advance(target).also { docID = it }
                }

                @Throws(IOException::class)
                override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
                    // The competitive iterator is usually a BitSetIterator, which has an optimized
                    // implementation of #intoBitSet.
                    if (competitiveIterator.docID() < docID) {
                        competitiveIterator.advance(docID)
                    }
                    competitiveIterator.intoBitSet(upTo, bitSet, offset)
                    docID = competitiveIterator.docID()
                }
            }
        }

        protected abstract fun bottomAsComparableLong(): Long

        protected abstract fun topAsComparableLong(): Long
    }

    companion object {
        // MIN_SKIP_INTERVAL and MAX_SKIP_INTERVAL both should be powers of 2
        private const val MIN_SKIP_INTERVAL = 32
        private const val MAX_SKIP_INTERVAL = 8192
    }
}
