package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.index.PrefixCodedTerms
import org.gnit.lucenekmp.index.PrefixCodedTerms.TermIterator
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.DocIdSetBuilder.BulkAdder
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.reflect.cast

/**
 * Abstract query class to find all documents whose single or multi-dimensional point values,
 * previously indexed with e.g. [IntPoint], is contained in the specified set.
 *
 *
 * This is for subclasses and works on the underlying binary encoding: to create range queries
 * for lucene's standard `Point` types, refer to factory methods on those classes, e.g. [ ][IntPoint.newSetQuery] for fields indexed with [IntPoint].
 *
 * @see PointValues
 *
 * @lucene.experimental
 */
abstract class PointInSetQuery protected constructor(
    val field: String,
    numDims: Int,
    bytesPerDim: Int,
    packedPoints: Stream
) : Query(), Accountable {
    // A little bit overkill for us, since all of our "terms" are always in the same field:
    val sortedPackedPoints: PrefixCodedTerms
    val sortedPackedPointsHashCode: Int
    val numDims: Int
    val bytesPerDim: Int
    val ramBytesUsed: Long // cache

    /** Iterator of encoded point values.  */ // TODO: if we want to stream, maybe we should use jdk stream class
    abstract class Stream : BytesRefIterator {
        abstract override fun next(): BytesRef?
    }

    /** The `packedPoints` iterator must be in sorted order.  */
    init {
        require(!(bytesPerDim < 1 || bytesPerDim > PointValues.MAX_NUM_BYTES)) { "bytesPerDim must be > 0 and <= " + PointValues.MAX_NUM_BYTES + "; got " + bytesPerDim }
        this.bytesPerDim = bytesPerDim
        require(!(numDims < 1 || numDims > PointValues.MAX_INDEX_DIMENSIONS)) { "numDims must be > 0 and <= " + PointValues.MAX_INDEX_DIMENSIONS + "; got " + numDims }

        this.numDims = numDims

        // In the 1D case this works well (the more points, the more common prefixes they share,
        // typically), but in
        // the > 1 D case, where we are only looking at the first dimension's prefix bytes, it can at
        // worst not hurt:
        val builder: PrefixCodedTerms.Builder = PrefixCodedTerms.Builder()
        var previous: BytesRefBuilder? = null
        var current: BytesRef
        while ((packedPoints.next().also { current = it!! }) != null) {
            require(current.length == numDims * bytesPerDim) {
                ("packed point length should be "
                        + (numDims * bytesPerDim)
                        + " but got "
                        + current.length
                        + "; field=\""
                        + field
                        + "\" numDims="
                        + numDims
                        + " bytesPerDim="
                        + bytesPerDim)
            }
            if (previous == null) {
                previous = BytesRefBuilder()
            } else {
                val cmp = previous.get().compareTo(current)
                if (cmp == 0) {
                    continue  // deduplicate
                } else require(cmp <= 0) { "values are out of order: saw $previous before $current" }
            }
            builder.add(field, current)
            previous.copyBytes(current)
        }
        sortedPackedPoints = builder.finish()
        sortedPackedPointsHashCode = sortedPackedPoints.hashCode()
        ramBytesUsed =
            (BASE_RAM_BYTES
                    + RamUsageEstimator.sizeOfObject(field)
                    + RamUsageEstimator.sizeOfObject(sortedPackedPoints))
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        // We don't use RandomAccessWeight here: it's no good to approximate with "match all docs".
        // This is an inverted structure and should be used in the first pass:

        return object : ConstantScoreWeight(this, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val reader: LeafReader = context.reader()

                val values: PointValues? = reader.getPointValues(field)
                if (values == null) {
                    // No docs in this segment/field indexed any points
                    return null
                }

                require(values.numIndexDimensions == numDims) {
                    ("field=\""
                            + field
                            + "\" was indexed with numIndexDims="
                            + values.numIndexDimensions
                            + " but this query has numIndexDims="
                            + numDims)
                }
                require(values.bytesPerDimension == bytesPerDim) {
                    ("field=\""
                            + field
                            + "\" was indexed with bytesPerDim="
                            + values.bytesPerDimension
                            + " but this query has bytesPerDim="
                            + bytesPerDim)
                }

                if (numDims == 1) {
                    // We optimize this common case, effectively doing a merge sort of the indexed values vs
                    // the queried set:
                    return object : ScorerSupplier() {
                        var cost: Long = -1 // calculate lazily, only once

                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            val result = DocIdSetBuilder(reader.maxDoc(), values)
                            values.intersect(this@PointInSetQuery.MergePointVisitor(sortedPackedPoints.iterator(), result))
                            val iterator: DocIdSetIterator = result.build().iterator()
                            return ConstantScoreScorer(score(), scoreMode, iterator!!)
                        }

                        override fun cost(): Long {
                            try {
                                if (cost == -1L) {
                                    // Computing the cost may be expensive, so only do it if necessary
                                    val result: DocIdSetBuilder = DocIdSetBuilder(reader.maxDoc(), values)
                                    cost =
                                        values.estimateDocCount(
                                            this@PointInSetQuery.MergePointVisitor(sortedPackedPoints.iterator(), result)
                                        )
                                    require(cost >= 0)
                                }
                                return cost
                            } catch (e: IOException) {
                                throw UncheckedIOException(e)
                            }
                        }
                    }
                } else {
                    // NOTE: this is naive implementation, where for each point we re-walk the KD tree to
                    // intersect.  We could instead do a similar
                    // optimization as the 1D case, but I think it'd mean building a query-time KD tree so we
                    // could efficiently intersect against the
                    // index, which is probably tricky!

                    return object : ScorerSupplier() {
                        var cost: Long = -1 // calculate lazily, only once

                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            val result = DocIdSetBuilder(reader.maxDoc(), values)
                            val visitor: SinglePointVisitor = this@PointInSetQuery.SinglePointVisitor(result)
                            val iterator: TermIterator = sortedPackedPoints.iterator()
                            var point: BytesRef? = iterator.next()
                            while (point != null) {
                                visitor.setPoint(point)
                                values.intersect(visitor)
                                point = iterator.next()
                            }
                            return ConstantScoreScorer(score(), scoreMode, result.build().iterator())
                        }

                        override fun cost(): Long {
                            try {
                                if (cost == -1L) {
                                    val result = DocIdSetBuilder(reader.maxDoc(), values)
                                    val visitor: SinglePointVisitor = this@PointInSetQuery.SinglePointVisitor(result)
                                    val iterator: TermIterator = sortedPackedPoints.iterator()
                                    cost = 0
                                    var point: BytesRef? = iterator.next()
                                    while (point != null) {
                                        visitor.setPoint(point)
                                        cost += values.estimateDocCount(visitor)
                                        point = iterator.next()
                                    }
                                    require(cost >= 0)
                                }
                                return cost
                            } catch (e: IOException) {
                                throw UncheckedIOException(e)
                            }
                        }
                    }
                }
            }



            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }
        }
    }

    /**
     * Essentially does a merge sort, only collecting hits when the indexed point and query point are
     * the same. This is an optimization, used in the 1D case.
     */
    private inner class MergePointVisitor(private val iterator: TermIterator, private val result: DocIdSetBuilder) : IntersectVisitor {
        private var nextQueryPoint: BytesRef? = iterator.next()
        private val comparator: ByteArrayComparator = ArrayUtil.getUnsignedComparator(bytesPerDim)
        private var adder: BulkAdder? = null

        override fun grow(count: Int) {
            adder = result.grow(count)
        }

        override fun visit(docID: Int) {
            adder!!.add(docID)
        }

        @Throws(IOException::class)
        override fun visit(iterator: DocIdSetIterator) {
            adder!!.add(iterator)
        }

        override fun visit(docID: Int, packedValue: ByteArray) {
            if (matches(packedValue)) {
                visit(docID)
            }
        }

        @Throws(IOException::class)
        override fun visit(iterator: DocIdSetIterator, packedValue: ByteArray) {
            if (matches(packedValue)) {
                adder!!.add(iterator)
            }
        }

        fun matches(packedValue: ByteArray): Boolean {
            while (nextQueryPoint != null) {
                val cmp: Int = comparator.compare(nextQueryPoint!!.bytes, nextQueryPoint!!.offset, packedValue, 0)
                if (cmp == 0) {
                    return true
                } else if (cmp < 0) {
                    // Query point is before index point, so we move to next query point
                    nextQueryPoint = iterator.next()
                } else {
                    // Query point is after index point, so we don't collect and we return:
                    break
                }
            }
            return false
        }

        override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
            while (nextQueryPoint != null) {
                val cmpMin: Int =
                    comparator.compare(nextQueryPoint!!.bytes, nextQueryPoint!!.offset, minPackedValue, 0)
                if (cmpMin < 0) {
                    // query point is before the start of this cell
                    nextQueryPoint = iterator.next()
                    continue
                }
                val cmpMax: Int =
                    comparator.compare(nextQueryPoint!!.bytes, nextQueryPoint!!.offset, maxPackedValue, 0)
                if (cmpMax > 0) {
                    // query point is after the end of this cell
                    return Relation.CELL_OUTSIDE_QUERY
                }

                if (cmpMin == 0 && cmpMax == 0) {
                    // NOTE: we only hit this if we are on a cell whose min and max values are exactly equal
                    // to our point,
                    // which can easily happen if many (> 512) docs share this one value
                    return Relation.CELL_INSIDE_QUERY
                } else {
                    return Relation.CELL_CROSSES_QUERY
                }
            }

            // We exhausted all points in the query:
            return Relation.CELL_OUTSIDE_QUERY
        }
    }

    /**
     * IntersectVisitor that queries against a highly degenerate shape: a single point. This is used
     * in the > 1D case.
     */
    inner class SinglePointVisitor(private val result: DocIdSetBuilder) : IntersectVisitor {
        private val comparator: ByteArrayComparator = ArrayUtil.getUnsignedComparator(bytesPerDim)
        private val pointBytes: ByteArray = ByteArray(bytesPerDim * numDims)
        private var adder: BulkAdder? = null

        fun setPoint(point: BytesRef) {
            // we verified this up front in query's ctor:
            require(point.length == pointBytes.size)
            /*java.lang.System.arraycopy(point.bytes, point.offset, pointBytes, 0, pointBytes.size)*/
            point.bytes.copyInto(
                destination = pointBytes,
                destinationOffset = 0,
                startIndex = point.offset,
                endIndex = point.offset + point.length
            )
        }

        override fun grow(count: Int) {
            adder = result.grow(count)
        }

        override fun visit(docID: Int) {
            adder!!.add(docID)
        }

        @Throws(IOException::class)
        override fun visit(iterator: DocIdSetIterator) {
            adder!!.add(iterator)
        }

        override fun visit(docID: Int, packedValue: ByteArray) {
            require(packedValue.size == pointBytes.size)
            if (packedValue.contentEquals(pointBytes)) {
                // The point for this doc matches the point we are querying on
                visit(docID)
            }
        }

        @Throws(IOException::class)
        override fun visit(iterator: DocIdSetIterator, packedValue: ByteArray) {
            require(packedValue.size == pointBytes.size)
            if (packedValue.contentEquals(pointBytes)) {
                // The point for this set of docs matches the point we are querying on
                adder!!.add(iterator)
            }
        }

        override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
            var crosses = false

            for (dim in 0..<numDims) {
                val offset = dim * bytesPerDim

                val cmpMin: Int = comparator.compare(minPackedValue, offset, pointBytes, offset)
                if (cmpMin > 0) {
                    return Relation.CELL_OUTSIDE_QUERY
                }

                val cmpMax: Int = comparator.compare(maxPackedValue, offset, pointBytes, offset)
                if (cmpMax < 0) {
                    return Relation.CELL_OUTSIDE_QUERY
                }

                if (cmpMin != 0 || cmpMax != 0) {
                    crosses = true
                }
            }

            if (crosses) {
                return Relation.CELL_CROSSES_QUERY
            } else {
                // NOTE: we only hit this if we are on a cell whose min and max values are exactly equal to
                // our point,
                // which can easily happen if many docs share this one value
                return Relation.CELL_INSIDE_QUERY
            }
        }
    }

    val packedPoints: MutableCollection<ByteArray>
        get() = object : AbstractMutableCollection<ByteArray>() {
            override fun iterator(): MutableIterator<ByteArray> {
                val size = sortedPackedPoints.size().toInt()
                val iterator: PrefixCodedTerms.TermIterator = sortedPackedPoints.iterator()
                return object : MutableIterator<ByteArray> {
                    var upto: Int = 0

                    override fun hasNext(): Boolean {
                        return upto < size
                    }

                    override fun next(): ByteArray {
                        if (upto == size) {
                            throw NoSuchElementException()
                        }

                        upto++
                        val next: BytesRef = iterator.next() ?: throw NoSuchElementException()
                        return BytesRef.deepCopyOf(next).bytes
                    }

                    override fun remove() {
                        throw UnsupportedOperationException("Removal not supported")
                    }
                }
            }

            override fun add(element: ByteArray): Boolean {
                throw UnsupportedOperationException("Adding not supported")
            }

            override val size: Int
                get() {
                    return sortedPackedPoints.size().toInt()
                }
        }

    override fun hashCode(): Int {
        var hash = classHash()
        hash = 31 * hash + field.hashCode()
        hash = 31 * hash + sortedPackedPointsHashCode
        hash = 31 * hash + numDims
        hash = 31 * hash + bytesPerDim
        return hash
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(this::class.cast(other))
    }

    private fun equalsTo(other: PointInSetQuery): Boolean {
        return other.field == field
                && other.numDims == numDims && other.bytesPerDim == bytesPerDim && other.sortedPackedPointsHashCode == sortedPackedPointsHashCode && other.sortedPackedPoints.equals(
            sortedPackedPoints
        )
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        if (this.field == field == false) {
            sb.append(this.field)
            sb.append(':')
        }

        sb.append("{")

        val iterator: TermIterator = sortedPackedPoints.iterator()
        val pointBytes = ByteArray(numDims * bytesPerDim)
        var first = true
        var point: BytesRef? = iterator.next()
        while (point != null) {
            if (first == false) {
                sb.append(" ")
            }
            first = false
            /*java.lang.System.arraycopy(point.bytes, point.offset, pointBytes, 0, pointBytes.size)*/
            point.bytes.copyInto(
                destination = pointBytes,
                destinationOffset = 0,
                startIndex = point.offset,
                endIndex = point.offset + point.length
            )

            sb.append(toString(pointBytes))
            point = iterator.next()
        }
        sb.append("}")
        return sb.toString()
    }

    /**
     * Returns a string of a single value in a human-readable format for debugging. This is used by
     * [.toString].
     *
     *
     * The default implementation encodes the individual byte values.
     *
     * @param value single value, never null
     * @return human readable value for debugging
     */
    protected abstract fun toString(value: ByteArray): String

    override fun ramBytesUsed(): Long {
        return ramBytesUsed
    }

    companion object {
        protected val BASE_RAM_BYTES: Long = RamUsageEstimator.shallowSizeOfInstance(PointInSetQuery::class)
    }
}
