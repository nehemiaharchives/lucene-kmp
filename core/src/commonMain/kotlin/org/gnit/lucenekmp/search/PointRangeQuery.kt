package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.PointTree
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.index.PointValues.Relation.CELL_CROSSES_QUERY
import org.gnit.lucenekmp.index.PointValues.Relation.CELL_INSIDE_QUERY
import org.gnit.lucenekmp.index.PointValues.Relation.CELL_OUTSIDE_QUERY
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.DocIdSetBuilder.BulkAdder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IntsRef
import kotlin.reflect.cast


/**
 * Abstract class for range queries against single or multidimensional points such as [ ].
 *
 *
 * This is for subclasses and works on the underlying binary encoding: to create range queries
 * for lucene's standard `Point` types, refer to factory methods on those classes, e.g. [ ][IntPoint.newRangeQuery] for fields indexed with [IntPoint].
 *
 *
 * For a single-dimensional field this query is a simple range query; in a multi-dimensional
 * field it's a box shape.
 *
 * @see PointValues
 *
 * @lucene.experimental
 */
abstract class PointRangeQuery protected constructor(
    field: String,
    lowerPoint: ByteArray,
    upperPoint: ByteArray,
    numDims: Int
) : Query() {
    val field: String
    val numDims: Int
    val bytesPerDim: Int
    var lowerPoint: ByteArray
        get() = lowerPoint.clone()

    var upperPoint: ByteArray
        get() = upperPoint.clone()

    /**
     * Expert: create a multidimensional range query for point values.
     *
     * @param field field name. must not be `null`.
     * @param lowerPoint lower portion of the range (inclusive).
     * @param upperPoint upper portion of the range (inclusive).
     * @param numDims number of dimensions.
     * @throws IllegalArgumentException if `field` is null, or if `lowerValue.length !=
     * upperValue.length`
     */
    init {
        checkArgs(field, lowerPoint, upperPoint)
        this.field = field
        require(numDims > 0) { "numDims must be positive, got $numDims" }
        require(lowerPoint.isNotEmpty()) { "lowerPoint has length of zero" }
        require(lowerPoint.size % numDims == 0) { "lowerPoint is not a fixed multiple of numDims" }
        require(lowerPoint.size == upperPoint.size) {
            ("lowerPoint has length="
                    + lowerPoint.size
                    + " but upperPoint has different length="
                    + upperPoint.size)
        }
        this.numDims = numDims
        this.bytesPerDim = lowerPoint.size / numDims

        this.lowerPoint = lowerPoint
        this.upperPoint = upperPoint
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    @Throws(IOException::class)
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        // We don't use RandomAccessWeight here: it's no good to approximate with "match all docs".
        // This is an inverted structure and should be used in the first pass:

        return object : ConstantScoreWeight(this, boost) {
            private val comparator: ByteArrayComparator = ArrayUtil.getUnsignedComparator(bytesPerDim)

            fun matches(packedValue: ByteArray): Boolean {
                var offset = 0
                var dim = 0
                while (dim < numDims) {
                    if (comparator.compare(packedValue, offset, lowerPoint, offset) < 0) {
                        // Doc's value is too low, in this dimension
                        return false
                    }
                    if (comparator.compare(packedValue, offset, upperPoint, offset) > 0) {
                        // Doc's value is too high, in this dimension
                        return false
                    }
                    dim++
                    offset += bytesPerDim
                }
                return true
            }

            fun relate(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                var crosses = false
                var offset = 0

                var dim = 0
                while (dim < numDims) {
                    if (comparator.compare(minPackedValue, offset, upperPoint, offset) > 0
                        || comparator.compare(maxPackedValue, offset, lowerPoint, offset) < 0
                    ) {
                        return CELL_OUTSIDE_QUERY
                    }

                    crosses = crosses or (
                            comparator.compare(minPackedValue, offset, lowerPoint, offset) < 0
                                    || comparator.compare(maxPackedValue, offset, upperPoint, offset) > 0)
                    dim++
                    offset += bytesPerDim
                }

                return if (crosses) {
                    CELL_CROSSES_QUERY
                } else {
                    CELL_INSIDE_QUERY
                }
            }

            fun getIntersectVisitor(result: DocIdSetBuilder): IntersectVisitor {
                return object : IntersectVisitor {
                    var adder: BulkAdder? = null

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

                    override fun visit(ref: IntsRef) {
                        adder!!.add(ref)
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

                    override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                        return relate(minPackedValue, maxPackedValue)
                    }
                }
            }

            /** Create a visitor that clears documents that do NOT match the range.  */
            fun getInverseIntersectVisitor(result: FixedBitSet, cost: LongArray): IntersectVisitor {
                return object : IntersectVisitor {
                    override fun visit(docID: Int) {
                        result.clear(docID)
                        cost[0]--
                    }

                    @Throws(IOException::class)
                    override fun visit(iterator: DocIdSetIterator) {
                        result.andNot(iterator)
                        cost[0] = kotlin.math.max(0, cost[0] - iterator.cost())
                    }

                    override fun visit(ref: IntsRef) {
                        for (i in ref.offset..<ref.offset + ref.length) {
                            result.clear(ref.ints[i])
                        }
                        cost[0] = kotlin.math.max(0, cost[0] - ref.length)
                    }

                    override fun visit(docID: Int, packedValue: ByteArray) {
                        if (!matches(packedValue)) {
                            visit(docID)
                        }
                    }

                    @Throws(IOException::class)
                    override fun visit(iterator: DocIdSetIterator, packedValue: ByteArray) {
                        if (!matches(packedValue)) {
                            visit(iterator)
                        }
                    }

                    override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                        val relation: Relation = relate(minPackedValue, maxPackedValue)
                        return when (relation) {
                            CELL_INSIDE_QUERY ->                 // all points match, skip this subtree
                                CELL_OUTSIDE_QUERY

                            CELL_OUTSIDE_QUERY ->                 // none of the points match, clear all documents
                                CELL_INSIDE_QUERY

                            CELL_CROSSES_QUERY -> relation
                        }
                    }
                }
            }

            @Throws(IOException::class)
            fun checkValidPointValues(values: PointValues?): Boolean {
                if (values == null) {
                    // No docs in this segment/field indexed any points
                    return false
                }

                require(values.numIndexDimensions == numDims) {
                    ("field=\""
                            + field
                            + "\" was indexed with numIndexDimensions="
                            + values.numIndexDimensions
                            + " but this query has numDims="
                            + numDims)
                }
                require(bytesPerDim == values.bytesPerDimension) {
                    ("field=\""
                            + field
                            + "\" was indexed with bytesPerDim="
                            + values.bytesPerDimension
                            + " but this query has bytesPerDim="
                            + bytesPerDim)
                }
                return true
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val reader: LeafReader = context.reader()

                val valuesToCheck: PointValues? = reader.getPointValues(field)
                if (!checkValidPointValues(valuesToCheck)) {
                    return null
                }

                val values = valuesToCheck!!

                if (values.docCount == 0) {
                    return null
                } else {
                    val fieldPackedLower: ByteArray = values.minPackedValue
                    val fieldPackedUpper: ByteArray = values.maxPackedValue
                    for (i in 0..<numDims) {
                        val offset = i * bytesPerDim
                        if (comparator.compare(lowerPoint, offset, fieldPackedUpper, offset) > 0
                            || comparator.compare(upperPoint, offset, fieldPackedLower, offset) < 0
                        ) {
                            // If this query is a required clause of a boolean query, then returning null here
                            // will help make sure that we don't call ScorerSupplier#get on other required clauses
                            // of the same boolean query, which is an expensive operation for some queries (e.g.
                            // multi-term queries).
                            return null
                        }
                    }
                }

                var allDocsMatch: Boolean
                if (values.docCount == reader.maxDoc()) {
                    val fieldPackedLower: ByteArray = values.minPackedValue
                    val fieldPackedUpper: ByteArray = values.maxPackedValue
                    allDocsMatch = true
                    for (i in 0..<numDims) {
                        val offset = i * bytesPerDim
                        if (comparator.compare(lowerPoint, offset, fieldPackedLower, offset) > 0
                            || comparator.compare(upperPoint, offset, fieldPackedUpper, offset) < 0
                        ) {
                            allDocsMatch = false
                            break
                        }
                    }
                } else {
                    allDocsMatch = false
                }

                if (allDocsMatch) {
                    // all docs have a value and all points are within bounds, so everything matches
                    return MatchAllScorerSupplier(score(), scoreMode, reader.maxDoc())
                } else {
                    return object : ConstantScoreScorerSupplier(score(), scoreMode, reader.maxDoc()) {
                        val result: DocIdSetBuilder = DocIdSetBuilder(reader.maxDoc(), values)
                        val visitor: IntersectVisitor = getIntersectVisitor(result)
                        var cost: Long = -1

                        @Throws(IOException::class)
                        override fun iterator(leadCost: Long): DocIdSetIterator {
                            if (values.docCount == reader.maxDoc() && values.docCount.toLong() == values.size() && cost() > reader.maxDoc() / 2) {
                                // If all docs have exactly one value and the cost is greater
                                // than half the leaf size then maybe we can make things faster
                                // by computing the set of documents that do NOT match the range
                                val result = FixedBitSet(reader.maxDoc())
                                result.set(0, reader.maxDoc())
                                val cost = longArrayOf(reader.maxDoc().toLong())
                                values.intersect(getInverseIntersectVisitor(result, cost))
                                return BitSetIterator(result, cost[0])
                            }

                            values.intersect(visitor)
                            return result.build().iterator()
                        }

                        override fun cost(): Long {
                            if (cost == -1L) {
                                // Computing the cost may be expensive, so only do it if necessary
                                cost = values.estimateDocCount(visitor)
                                require(cost >= 0)
                            }
                            return cost
                        }
                    }
                }
            }

            @Throws(IOException::class)
            override fun count(context: LeafReaderContext): Int {
                val reader: LeafReader = context.reader()

                val values: PointValues? = reader.getPointValues(field)
                if (!checkValidPointValues(values)) {
                    return 0
                }

                if (values != null && !reader.hasDeletions()) {
                    if (relate(values.minPackedValue, values.maxPackedValue)
                        === CELL_INSIDE_QUERY
                    ) {
                        return values.docCount
                    }
                    // only 1D: we have the guarantee that it will actually run fast since there are at most 2
                    // crossing leaves.
                    // docCount == size : counting according number of points in leaf node, so must be
                    // single-valued.
                    if (numDims == 1 && values.docCount.toLong() == values.size()) {
                        return pointCount(
                            values.pointTree,
                            { minPackedValue: ByteArray, maxPackedValue: ByteArray ->
                                this.relate(minPackedValue, maxPackedValue)
                            },
                            { packedValue: ByteArray -> this.matches(packedValue) }
                        ).toInt()
                    }
                }
                return super.count(context)
            }

            /**
             * Finds the number of points matching the provided range conditions. Using this method is
             * faster than calling [PointValues.intersect] to get the count of
             * intersecting points. This method does not enforce live documents, therefore it should only
             * be used when there are no deleted documents.
             *
             * @param pointTree start node of the count operation
             * @param nodeComparator comparator to be used for checking whether the internal node is
             * inside the range
             * @param leafComparator comparator to be used for checking whether the leaf node is inside
             * the range
             * @return count of points that match the range
             */
            @Throws(IOException::class)
            fun pointCount(
                pointTree: PointTree,
                nodeComparator: (ByteArray, ByteArray) -> Relation,
                leafComparator: (ByteArray) -> Boolean
            ): Long {
                val matchingNodeCount = longArrayOf(0)
                // create a custom IntersectVisitor that records the number of leafNodes that matched
                val visitor: IntersectVisitor =
                    object : IntersectVisitor {
                        override fun visit(docID: Int) {
                            // this branch should be unreachable
                            throw UnsupportedOperationException(
                                ("This IntersectVisitor does not perform any actions on a "
                                        + "docID="
                                        + docID
                                        + " node being visited")
                            )
                        }

                        override fun visit(docID: Int, packedValue: ByteArray) {
                            if (leafComparator(packedValue)) {
                                matchingNodeCount[0]++
                            }
                        }

                        override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                            return nodeComparator(minPackedValue, maxPackedValue)
                        }
                    }
                pointCount(visitor, pointTree, matchingNodeCount)
                return matchingNodeCount[0]
            }

            @Throws(IOException::class)
            fun pointCount(
                visitor: IntersectVisitor, pointTree: PointTree, matchingNodeCount: LongArray
            ) {
                val r: Relation = visitor.compare(pointTree.minPackedValue, pointTree.maxPackedValue)
                when (r) {
                    CELL_OUTSIDE_QUERY ->             // This cell is fully outside the query shape: return 0 as the count of its nodes
                        return

                    CELL_INSIDE_QUERY -> {
                        // This cell is fully inside the query shape: return the size of the entire node as the
                        // count
                        matchingNodeCount[0] += pointTree.size()
                        return
                    }

                    CELL_CROSSES_QUERY -> {
                        /*
                        The cell crosses the shape boundary, or the cell fully contains the query, so we fall
                        through and do full counting.
                        */
                        if (pointTree.moveToChild()) {
                            do {
                                pointCount(visitor, pointTree, matchingNodeCount)
                            } while (pointTree.moveToSibling())
                            pointTree.moveToParent()
                        } else {
                            // we have reached a leaf node here.
                            pointTree.visitDocValues(visitor)
                            // leaf node count is saved in the matchingNodeCount array by the visitor
                        }
                        return
                    }

                    /*else -> throw IllegalArgumentException("Unreachable code")*/
                }
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }
        }
    }

    override fun hashCode(): Int {
        var hash = classHash()
        hash = 31 * hash + field.hashCode()
        hash = 31 * hash + lowerPoint.contentHashCode()
        hash = 31 * hash + upperPoint.contentHashCode()
        hash = 31 * hash + numDims
        hash = 31 * hash + Objects.hashCode(bytesPerDim)
        return hash
    }

    override fun equals(o: Any?): Boolean {
        return sameClassAs(o) && equalsTo(this::class.cast(o))
    }

    private fun equalsTo(other: PointRangeQuery): Boolean {
        return field == other.field
                && numDims == other.numDims && bytesPerDim == other.bytesPerDim && lowerPoint.contentEquals(other.lowerPoint) && upperPoint.contentEquals(
            other.upperPoint
        )
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        if (this.field != field) {
            sb.append(this.field)
            sb.append(':')
        }

        // print ourselves as "range per dimension"
        for (i in 0..<numDims) {
            if (i > 0) {
                sb.append(',')
            }

            val startOffset = bytesPerDim * i

            sb.append('[')
            sb.append(
                toString(
                    i, ArrayUtil.copyOfSubArray(lowerPoint, startOffset, startOffset + bytesPerDim)
                )
            )
            sb.append(" TO ")
            sb.append(
                toString(
                    i, ArrayUtil.copyOfSubArray(upperPoint, startOffset, startOffset + bytesPerDim)
                )
            )
            sb.append(']')
        }

        return sb.toString()
    }

    /**
     * Returns a string of a single value in a human-readable format for debugging. This is used by
     * [.toString].
     *
     * @param dimension dimension of the particular value
     * @param value single value, never null
     * @return human readable value for debugging
     */
    protected abstract fun toString(dimension: Int, value: ByteArray): String

    companion object {
        /**
         * Check preconditions for all factory methods
         *
         * @throws IllegalArgumentException if `field`, `lowerPoint` or `upperPoint` are
         * null.
         */
        fun checkArgs(field: String, lowerPoint: Any, upperPoint: Any) {
            requireNotNull(field) { "field must not be null" }
            requireNotNull(lowerPoint) { "lowerPoint must not be null" }
            requireNotNull(upperPoint) { "upperPoint must not be null" }
        }
    }
}
