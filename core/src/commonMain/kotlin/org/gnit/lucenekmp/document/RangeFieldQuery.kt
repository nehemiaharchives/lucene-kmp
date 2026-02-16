package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.ConstantScoreScorer
import org.gnit.lucenekmp.search.ConstantScoreWeight
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.IntsRef
import kotlin.reflect.cast


/**
 * Query class for searching `RangeField` types by a defined [Relation].
 *
 * @lucene.internal
 */
abstract class RangeFieldQuery protected constructor(field: String, ranges: ByteArray, numDims: Int, queryType: QueryType) : Query() {
    /** field name  */
    val field: String

    /**
     * query relation intersects: `CELL_CROSSES_QUERY`, contains: `CELL_CONTAINS_QUERY`,
     * within: `CELL_WITHIN_QUERY`
     */
    val queryType: QueryType

    /** number of dimensions - max 4  */
    val numDims: Int

    /** ranges encoded as a sortable byte array  */
    val ranges: ByteArray

    /** number of bytes per dimension  */
    val bytesPerDim: Int

    /** ByteArrayComparator selected by bytesPerDim  */
    val comparator: ByteArrayComparator

    /**
     * Used by `RangeFieldQuery` to check how each internal or leaf node relates to the query.
     */
    enum class QueryType {
        /** Use this for intersects queries.  */
        INTERSECTS {
            override fun compare(
                queryPackedValue: ByteArray,
                minPackedValue: ByteArray,
                maxPackedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                dim: Int,
                comparator: ByteArrayComparator
            ): Relation {
                val minOffset = dim * bytesPerDim
                val maxOffset = minOffset + bytesPerDim * numDims

                if (comparator.compare(queryPackedValue, maxOffset, minPackedValue, minOffset) < 0
                    || comparator.compare(queryPackedValue, minOffset, maxPackedValue, maxOffset) > 0
                ) {
                    // disjoint
                    return Relation.CELL_OUTSIDE_QUERY
                }

                if (comparator.compare(queryPackedValue, maxOffset, maxPackedValue, minOffset) >= 0
                    && comparator.compare(queryPackedValue, minOffset, minPackedValue, maxOffset) <= 0
                ) {
                    return Relation.CELL_INSIDE_QUERY
                }

                return Relation.CELL_CROSSES_QUERY
            }

            override fun matches(
                queryPackedValue: ByteArray,
                packedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                dim: Int,
                comparator: ByteArrayComparator
            ): Boolean {
                val minOffset = dim * bytesPerDim
                val maxOffset = minOffset + bytesPerDim * numDims
                return comparator.compare(queryPackedValue, maxOffset, packedValue, minOffset) >= 0
                        && comparator.compare(queryPackedValue, minOffset, packedValue, maxOffset) <= 0
            }
        },

        /** Use this for within queries.  */
        WITHIN {
            override fun compare(
                queryPackedValue: ByteArray,
                minPackedValue: ByteArray,
                maxPackedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                dim: Int,
                comparator: ByteArrayComparator
            ): Relation {
                val minOffset = dim * bytesPerDim
                val maxOffset = minOffset + bytesPerDim * numDims

                if (comparator.compare(queryPackedValue, maxOffset, minPackedValue, maxOffset) < 0
                    || comparator.compare(queryPackedValue, minOffset, maxPackedValue, minOffset) > 0
                ) {
                    // all ranges have at least one point outside of the query
                    return Relation.CELL_OUTSIDE_QUERY
                }

                if (comparator.compare(queryPackedValue, maxOffset, maxPackedValue, maxOffset) >= 0
                    && comparator.compare(queryPackedValue, minOffset, minPackedValue, minOffset) <= 0
                ) {
                    return Relation.CELL_INSIDE_QUERY
                }

                return Relation.CELL_CROSSES_QUERY
            }

            override fun matches(
                queryPackedValue: ByteArray,
                packedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                dim: Int,
                comparator: ByteArrayComparator
            ): Boolean {
                val minOffset = dim * bytesPerDim
                val maxOffset = minOffset + bytesPerDim * numDims
                return comparator.compare(queryPackedValue, minOffset, packedValue, minOffset) <= 0
                        && comparator.compare(queryPackedValue, maxOffset, packedValue, maxOffset) >= 0
            }
        },

        /** Use this for contains  */
        CONTAINS {
            override fun compare(
                queryPackedValue: ByteArray,
                minPackedValue: ByteArray,
                maxPackedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                dim: Int,
                comparator: ByteArrayComparator
            ): Relation {
                val minOffset = dim * bytesPerDim
                val maxOffset = minOffset + bytesPerDim * numDims

                if (comparator.compare(queryPackedValue, maxOffset, maxPackedValue, maxOffset) > 0
                    || comparator.compare(queryPackedValue, minOffset, minPackedValue, minOffset) < 0
                ) {
                    // all ranges are either less than the query max or greater than the query min
                    return Relation.CELL_OUTSIDE_QUERY
                }

                if (comparator.compare(queryPackedValue, maxOffset, minPackedValue, maxOffset) <= 0
                    && comparator.compare(queryPackedValue, minOffset, maxPackedValue, minOffset) >= 0
                ) {
                    return Relation.CELL_INSIDE_QUERY
                }

                return Relation.CELL_CROSSES_QUERY
            }

            override fun matches(
                queryPackedValue: ByteArray,
                packedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                dim: Int,
                comparator: ByteArrayComparator
            ): Boolean {
                val minOffset = dim * bytesPerDim
                val maxOffset = minOffset + bytesPerDim * numDims
                return comparator.compare(queryPackedValue, minOffset, packedValue, minOffset) >= 0
                        && comparator.compare(queryPackedValue, maxOffset, packedValue, maxOffset) <= 0
            }
        },

        /** Use this for crosses queries  */
        CROSSES {
            override fun compare(
                queryPackedValue: ByteArray,
                minPackedValue: ByteArray,
                maxPackedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                dim: Int,
                comparator: ByteArrayComparator
            ): Relation {
                throw UnsupportedOperationException()
            }

            override fun matches(
                queryPackedValue: ByteArray,
                packedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                dim: Int,
                comparator: ByteArrayComparator
            ): Boolean {
                throw UnsupportedOperationException()
            }

            override fun compare(
                queryPackedValue: ByteArray,
                minPackedValue: ByteArray,
                maxPackedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                comparator: ByteArrayComparator
            ): Relation {
                val intersectRelation: Relation =
                    INTERSECTS.compare(
                        queryPackedValue, minPackedValue, maxPackedValue, numDims, bytesPerDim, comparator
                    )
                if (intersectRelation == Relation.CELL_OUTSIDE_QUERY) {
                    return Relation.CELL_OUTSIDE_QUERY
                }

                val withinRelation: Relation =
                    WITHIN.compare(
                        queryPackedValue, minPackedValue, maxPackedValue, numDims, bytesPerDim, comparator
                    )
                if (withinRelation == Relation.CELL_INSIDE_QUERY) {
                    return Relation.CELL_OUTSIDE_QUERY
                }

                if (intersectRelation == Relation.CELL_INSIDE_QUERY
                    && withinRelation == Relation.CELL_OUTSIDE_QUERY
                ) {
                    return Relation.CELL_INSIDE_QUERY
                }

                return Relation.CELL_CROSSES_QUERY
            }

            override fun matches(
                queryPackedValue: ByteArray,
                packedValue: ByteArray,
                numDims: Int,
                bytesPerDim: Int,
                comparator: ByteArrayComparator
            ): Boolean {
                return INTERSECTS.matches(queryPackedValue, packedValue, numDims, bytesPerDim, comparator)
                        && (WITHIN.matches(queryPackedValue, packedValue, numDims, bytesPerDim, comparator)
                        == false)
            }
        };

        abstract fun compare(
            queryPackedValue: ByteArray,
            minPackedValue: ByteArray,
            maxPackedValue: ByteArray,
            numDims: Int,
            bytesPerDim: Int,
            dim: Int,
            comparator: ByteArrayComparator
        ): Relation

        open fun compare(
            queryPackedValue: ByteArray,
            minPackedValue: ByteArray,
            maxPackedValue: ByteArray,
            numDims: Int,
            bytesPerDim: Int,
            comparator: ByteArrayComparator
        ): Relation {
            var inside = true
            for (dim in 0..<numDims) {
                val relation: Relation =
                    compare(
                        queryPackedValue,
                        minPackedValue,
                        maxPackedValue,
                        numDims,
                        bytesPerDim,
                        dim,
                        comparator
                    )
                if (relation == Relation.CELL_OUTSIDE_QUERY) {
                    return Relation.CELL_OUTSIDE_QUERY
                } else if (relation != Relation.CELL_INSIDE_QUERY) {
                    inside = false
                }
            }
            return if (inside) Relation.CELL_INSIDE_QUERY else Relation.CELL_CROSSES_QUERY
        }

        abstract fun matches(
            queryPackedValue: ByteArray,
            packedValue: ByteArray,
            numDims: Int,
            bytesPerDim: Int,
            dim: Int,
            comparator: ByteArrayComparator
        ): Boolean

        /**
         * Compares every dim for 2 encoded ranges and returns true if all dims match. Matching
         * implementation is based on the
         */
        open fun matches(
            queryPackedValue: ByteArray,
            packedValue: ByteArray,
            numDims: Int,
            bytesPerDim: Int,
            comparator: ByteArrayComparator
        ): Boolean {
            for (dim in 0..<numDims) {
                if (matches(queryPackedValue, packedValue, numDims, bytesPerDim, dim, comparator)
                    == false
                ) {
                    return false
                }
            }
            return true
        }
    }

    /**
     * Create a query for searching indexed ranges that match the provided relation.
     *
     * @param field field name. must not be null.
     * @param ranges encoded range values; this is done by the `RangeField` implementation
     * @param queryType the query relation
     */
    init {
        checkArgs(field, ranges, numDims)
        requireNotNull(queryType) { "Query type cannot be null" }
        this.field = field
        this.queryType = queryType
        this.numDims = numDims
        this.ranges = ranges
        this.bytesPerDim = ranges.size / (2 * numDims)
        this.comparator = ArrayUtil.getUnsignedComparator(bytesPerDim)
    }

    /** Check indexed field info against the provided query data.  */
    private fun checkFieldInfo(fieldInfo: FieldInfo) {
        require(fieldInfo.pointDimensionCount / 2 == numDims) {
            ("field=\""
                    + field
                    + "\" was indexed with numDims="
                    + fieldInfo.pointDimensionCount / 2 + " but this query has numDims="
                    + numDims)
        }
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    @Throws(IOException::class)
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : ConstantScoreWeight(this, boost) {
            fun getIntersectVisitor(result: DocIdSetBuilder): IntersectVisitor {
                return object : IntersectVisitor {
                    var adder: DocIdSetBuilder.BulkAdder? = null

                    override fun grow(count: Int) {
                        adder = result.grow(count)
                    }

                    override fun visit(ref: IntsRef) {
                        adder!!.add(ref)
                    }

                    override fun visit(docID: Int) {
                        adder!!.add(docID)
                    }

                    @Throws(IOException::class)
                    override fun visit(iterator: DocIdSetIterator) {
                        adder!!.add(iterator)
                    }

                    override fun visit(docID: Int, leaf: ByteArray) {
                        if (queryType.matches(ranges, leaf, numDims, bytesPerDim, comparator)) {
                            visit(docID)
                        }
                    }

                    @Throws(IOException::class)
                    override fun visit(iterator: DocIdSetIterator, leaf: ByteArray) {
                        if (queryType.matches(ranges, leaf, numDims, bytesPerDim, comparator)) {
                            adder!!.add(iterator)
                        }
                    }

                    override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                        return queryType.compare(
                            ranges, minPackedValue, maxPackedValue, numDims, bytesPerDim, comparator
                        )
                    }
                }
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val reader: LeafReader = context.reader()
                val values: PointValues? = reader.getPointValues(field)
                if (values == null) {
                    // no docs in this segment indexed any ranges
                    return null
                }
                val fieldInfo: FieldInfo? = reader.fieldInfos.fieldInfo(field)
                if (fieldInfo == null) {
                    // no docs in this segment indexed this field
                    return null
                }
                checkFieldInfo(fieldInfo)
                var allDocsMatch = false
                if (values.docCount == reader.maxDoc()
                    && (queryType.compare(
                        ranges,
                        values.minPackedValue,
                        values.maxPackedValue,
                        numDims,
                        bytesPerDim,
                        comparator
                    )
                            == Relation.CELL_INSIDE_QUERY)
                ) {
                    allDocsMatch = true
                }

                if (allDocsMatch) {
                    return object : ScorerSupplier() {
                        override fun get(leadCost: Long): Scorer {
                            return ConstantScoreScorer(
                                score(), scoreMode, DocIdSetIterator.all(reader.maxDoc())
                            )
                        }

                        override fun cost(): Long {
                            return reader.maxDoc().toLong()
                        }
                    }
                } else {
                    return object : ScorerSupplier() {
                        val result: DocIdSetBuilder = DocIdSetBuilder(reader.maxDoc(), values)
                        val visitor: IntersectVisitor = getIntersectVisitor(result)
                        var cost: Long = -1

                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            values.intersect(visitor)
                            val iterator: DocIdSetIterator = result.build().iterator()
                            return ConstantScoreScorer(score(), scoreMode, iterator)
                        }

                        override fun cost(): Long {
                            if (cost == -1L) {
                                // Computing the cost may be expensive, so only do it if necessary
                                cost = values.estimateDocCount(visitor)
                                assert(cost >= 0)
                            }
                            return cost
                        }
                    }
                }
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }
        }
    }

    override fun hashCode(): Int {
        var hash: Int = classHash()
        hash = 31 * hash + field.hashCode()
        hash = 31 * hash + numDims
        hash = 31 * hash + queryType.hashCode()
        hash = 31 * hash + ranges.contentHashCode()

        return hash
    }

    override fun equals(o: Any?): Boolean {
        return sameClassAs(o) && equalsTo(this::class.cast(o))
    }

    /** Check equality of two RangeFieldQuery objects  */
    protected fun equalsTo(other: RangeFieldQuery): Boolean {
        return field == other.field
                && numDims == other.numDims && ranges.contentEquals(other.ranges) && other.queryType === queryType
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        if (this.field == field == false) {
            sb.append(this.field)
            sb.append(':')
        }
        sb.append("<ranges:")
        sb.append(toString(ranges, 0))
        for (d in 1..<numDims) {
            sb.append(' ')
            sb.append(toString(ranges, d))
        }
        sb.append('>')

        return sb.toString()
    }

    /**
     * Returns a string of a single value in a human-readable format for debugging. This is used by
     * [.toString].
     *
     * @param dimension dimension of the particular value
     * @param ranges encoded ranges, never null
     * @return human readable value for debugging
     */
    protected abstract fun toString(ranges: ByteArray, dimension: Int): String

    companion object {
        /** check input arguments  */
        private fun checkArgs(field: String, ranges: ByteArray, numDims: Int) {
            requireNotNull(field) { "field must not be null" }
            require(numDims <= 4) { "dimension size cannot be greater than 4" }
            require(!(ranges == null || ranges.isEmpty())) { "encoded ranges cannot be null or empty" }
        }
    }
}
