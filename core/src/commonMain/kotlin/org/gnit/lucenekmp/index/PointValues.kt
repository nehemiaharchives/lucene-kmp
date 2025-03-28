package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.bkd.BKDConfig
import kotlin.math.pow


/**
 * Access to indexed numeric values.
 *
 *
 * Points represent numeric values and are indexed differently than ordinary text. Instead of an
 * inverted index, points are indexed with datastructures such as [KD-trees](https://en.wikipedia.org/wiki/K-d_tree). These structures are optimized for
 * operations such as *range*, *distance*, *nearest-neighbor*, and
 * *point-in-polygon* queries.
 *
 * <h2>Basic Point Types</h2>
 *
 * <table>
 * <caption>Basic point types in Java and Lucene</caption>
 * <tr><th>Java type</th><th>Lucene class</th></tr>
 * <tr><td>`int`</td><td>[IntPoint]</td></tr>
 * <tr><td>`long`</td><td>[LongPoint]</td></tr>
 * <tr><td>`float`</td><td>[FloatPoint]</td></tr>
 * <tr><td>`double`</td><td>[DoublePoint]</td></tr>
 * <tr><td>`byte[]`</td><td>[BinaryPoint]</td></tr>
 * <tr><td>[InetAddress]</td><td>[InetAddressPoint]</td></tr>
 * <tr><td>[BigInteger]</td><td>[BigIntegerPoint]({@docRoot}/../sandbox/org/apache/lucene/sandbox/document/BigIntegerPoint.html)*</td></tr>
</table> *
 *
 * * in the *lucene-sandbox* jar<br></br>
 *
 *
 * Basic Lucene point types behave like their java peers: for example [IntPoint] represents
 * a signed 32-bit [Integer], supporting values ranging from [Integer.MIN_VALUE] to
 * [Integer.MAX_VALUE], ordered consistent with [Integer.compareTo]. In
 * addition to indexing support, point classes also contain static methods (such as [ ][IntPoint.newRangeQuery]) for creating common queries. For example:
 *
 * <pre class="prettyprint">
 * // add year 1970 to document
 * document.add(new IntPoint("year", 1970));
 * // index document
 * writer.addDocument(document);
 * ...
 * // issue range query of 1960-1980
 * Query query = IntPoint.newRangeQuery("year", 1960, 1980);
 * TopDocs docs = searcher.search(query, ...);
</pre> *
 *
 * <h2>Geospatial Point Types</h2>
 *
 * Although basic point types such as [DoublePoint] support points in multi-dimensional space
 * too, Lucene has specialized classes for location data. These classes are optimized for location
 * data: they are more space-efficient and support special operations such as *distance* and
 * *polygon* queries. There are currently two implementations: <br></br>
 *
 *
 *  1. [LatLonPoint]: indexes `(latitude,longitude)` as `(x,y)` in
 * two-dimensional space.
 *  1. [Geo3DPoint]({@docRoot}/../spatial3d/org/apache/lucene/spatial3d/Geo3DPoint.html)*
 * in *lucene-spatial3d*: indexes `(latitude,longitude)` as `(x,y,z)` in
 * three-dimensional space.
 *
 *
 * * does **not** support altitude, 3D here means "uses three dimensions under-the-hood"<br></br>
 *
 * <h2>Advanced usage</h2>
 *
 * Custom structures can be created on top of single- or multi- dimensional basic types, on top of
 * [BinaryPoint] for more flexibility, or via custom [Field] subclasses.
 *
 * @lucene.experimental
 */
abstract class PointValues
/** Default constructor  */
protected constructor() {
    /** Used by [.intersect] to check how each recursive cell corresponds to the query.  */
    enum class Relation {
        /** Return this if the cell is fully contained by the query  */
        CELL_INSIDE_QUERY,

        /** Return this if the cell and query do not overlap  */
        CELL_OUTSIDE_QUERY,

        /** Return this if the cell partially overlaps the query  */
        CELL_CROSSES_QUERY
    }

    @get:Throws(IOException::class)
    abstract val pointTree: PointValues.PointTree

    /**
     * Basic operations to read the KD-tree.
     *
     * @lucene.experimental
     */
    interface PointTree : Cloneable {
        /** Clone, the current node becomes the root of the new tree.  */
        public override fun clone(): PointTree

        /**
         * Move to the first child node and return `true` upon success. Returns `false` for
         * leaf nodes and `true` otherwise.
         */
        @Throws(IOException::class)
        fun moveToChild(): Boolean

        /**
         * Move to the next sibling node and return `true` upon success. Returns `false` if
         * the current node has no more siblings.
         */
        @Throws(IOException::class)
        fun moveToSibling(): Boolean

        /**
         * Move to the parent node and return `true` upon success. Returns `false` for the
         * root node and `true` otherwise.
         */
        @Throws(IOException::class)
        fun moveToParent(): Boolean

        /** Return the minimum packed value of the current node.  */
        val minPackedValue: ByteArray

        /** Return the maximum packed value of the current node.  */
        val maxPackedValue: ByteArray

        /** Return the number of points below the current node.  */
        fun size(): Long

        /** Visit all the docs below the current node.  */
        @Throws(IOException::class)
        fun visitDocIDs(visitor: IntersectVisitor)

        /** Visit all the docs and values below the current node.  */
        @Throws(IOException::class)
        fun visitDocValues(visitor: IntersectVisitor)
    }

    /**
     * We recurse the [PointTree], using a provided instance of this to guide the recursion.
     *
     * @lucene.experimental
     */
    interface IntersectVisitor {
        /**
         * Called for all documents in a leaf cell that's fully contained by the query. The consumer
         * should blindly accept the docID.
         */
        @Throws(IOException::class)
        fun visit(docID: Int)

        /**
         * Similar to [IntersectVisitor.visit], but a bulk visit and implementations may have
         * their optimizations.
         */
        @Throws(IOException::class)
        fun visit(iterator: DocIdSetIterator) {
            var docID: Int
            while ((iterator.nextDoc().also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                visit(docID)
            }
        }

        /**
         * Similar to [IntersectVisitor.visit], but a bulk visit and implements may have
         * their optimizations. Even if the implementation does the same thing this method, this may be
         * a speed improvement due to fewer virtual calls.
         */
        @Throws(IOException::class)
        fun visit(ref: IntsRef) {
            for (i in ref.offset..<ref.length + ref.offset) {
                visit(ref.ints[i])
            }
        }

        /**
         * Called for all documents in a leaf cell that crosses the query. The consumer should
         * scrutinize the packedValue to decide whether to accept it. In the 1D case, values are visited
         * in increasing order, and in the case of ties, in increasing docID order.
         */
        @Throws(IOException::class)
        fun visit(docID: Int, packedValue: ByteArray)

        /**
         * Similar to [IntersectVisitor.visit] but in this case the packedValue can
         * have more than one docID associated to it. The provided iterator should not escape the scope
         * of this method so that implementations of PointValues are free to reuse it,
         */
        @Throws(IOException::class)
        fun visit(iterator: DocIdSetIterator, packedValue: ByteArray) {
            var docID: Int
            while ((iterator.nextDoc().also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                visit(docID, packedValue)
            }
        }

        /**
         * Called for non-leaf cells to test how the cell relates to the query, to determine how to
         * further recurse down the tree.
         */
        fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation

        /** Notifies the caller that this many documents are about to be visited  */
        fun grow(count: Int) {}
    }

    /**
     * Finds all documents and points matching the provided visitor. This method does not enforce live
     * documents, so it's up to the caller to test whether each document is deleted, if necessary.
     */
    @Throws(IOException::class)
    fun intersect(visitor: IntersectVisitor) {
        val pointTree = this.pointTree
        intersect(visitor, pointTree)
        require(pointTree.moveToParent() == false)
    }

    @Throws(IOException::class)
    private fun intersect(visitor: IntersectVisitor, pointTree: PointTree) {
        val r = visitor.compare(pointTree.minPackedValue, pointTree.maxPackedValue)
        when (r) {
            Relation.CELL_OUTSIDE_QUERY -> {}
            Relation.CELL_INSIDE_QUERY ->         // This cell is fully inside the query shape: recursively add all points in this cell
                // without filtering
                pointTree.visitDocIDs(visitor)

            Relation.CELL_CROSSES_QUERY ->         // The cell crosses the shape boundary, or the cell fully contains the query, so we fall
                // through and do full filtering:
                if (pointTree.moveToChild()) {
                    do {
                        intersect(visitor, pointTree)
                    } while (pointTree.moveToSibling())
                    pointTree.moveToParent()
                } else {
                    // TODO: we can assert that the first value here in fact matches what the pointTree
                    // claimed
                    // Leaf node; scan and filter all points in this block:
                    pointTree.visitDocValues(visitor)
                }

            else -> throw IllegalArgumentException("Unreachable code")
        }
    }

    /**
     * Estimate the number of points that would be visited by [.intersect] with the given [ ]. This should run many times faster than [.intersect].
     */
    fun estimatePointCount(visitor: IntersectVisitor): Long {
        try {
            val pointTree = this.pointTree
            val count = estimatePointCount(visitor, pointTree, Long.Companion.MAX_VALUE)
            require(pointTree.moveToParent() == false)
            return count
        } catch (ioe: IOException) {
            throw IOException(ioe)
        }
    }

    /**
     * Estimate the number of documents that would be matched by [.intersect] with the given
     * [IntersectVisitor]. This should run many times faster than [ ][.intersect].
     *
     * @see DocIdSetIterator.cost
     */
    fun estimateDocCount(visitor: IntersectVisitor): Long {
        val estimatedPointCount = estimatePointCount(visitor)
        val docCount = this.docCount
        val size = size().toDouble()
        if (estimatedPointCount >= size) {
            // math all docs
            return docCount.toLong()
        } else if (size == docCount.toDouble() || estimatedPointCount == 0L) {
            // if the point count estimate is 0 or we have only single values
            // return this estimate
            return estimatedPointCount
        } else {
            // in case of multi values estimate the number of docs using the solution provided in
            // https://math.stackexchange.com/questions/1175295/urn-problem-probability-of-drawing-balls-of-k-unique-colors
            // then approximate the solution for points per doc << size() which results in the expression
            // D * (1 - ((N - n) / N)^(N/D))
            // where D is the total number of docs, N the total number of points and n the estimated point
            // count
            val docEstimate = (docCount * (1.0 - ((size - estimatedPointCount) / size).pow(size / docCount))).toLong()
            return if (docEstimate == 0L) 1L else docEstimate
        }
    }

    @get:Throws(IOException::class)
    abstract val minPackedValue: ByteArray

    @get:Throws(IOException::class)
    abstract val maxPackedValue: ByteArray

    @get:Throws(IOException::class)
    abstract val numDimensions: Int

    @get:Throws(IOException::class)
    abstract val numIndexDimensions: Int

    @get:Throws(IOException::class)
    abstract val bytesPerDimension: Int

    /** Returns the total number of indexed points across all documents.  */
    abstract fun size(): Long

    /** Returns the total number of documents that have indexed at least one point.  */
    abstract val docCount: Int

    companion object {
        /** Maximum number of bytes for each dimension  */
        const val MAX_NUM_BYTES: Int = 16

        /** Maximum number of dimensions  */
        val MAX_DIMENSIONS: Int = BKDConfig.MAX_DIMS

        /** Maximum number of index dimensions  */
        val MAX_INDEX_DIMENSIONS: Int = BKDConfig.MAX_INDEX_DIMS

        /**
         * Return the cumulated number of points across all leaves of the given [IndexReader].
         * Leaves that do not have points for the given field are ignored.
         *
         * @see PointValues.size
         */
        @Throws(IOException::class)
        fun size(reader: IndexReader, field: String): Long {
            var size: Long = 0
            for (ctx in reader.leaves()) {
                val values = ctx.reader().getPointValues(field)
                if (values != null) {
                    size += values.size()
                }
            }
            return size
        }

        /**
         * Return the cumulated number of docs that have points across all leaves of the given [ ]. Leaves that do not have points for the given field are ignored.
         *
         * @see PointValues.getDocCount
         */
        @Throws(IOException::class)
        fun getDocCount(reader: IndexReader, field: String): Int {
            var count = 0
            for (ctx in reader.leaves()) {
                val values = ctx!!.reader().getPointValues(field)
                if (values != null) {
                    count += values.docCount
                }
            }
            return count
        }

        /**
         * Return the minimum packed values across all leaves of the given [IndexReader]. Leaves
         * that do not have points for the given field are ignored.
         *
         * @see PointValues.getMinPackedValue
         */
        @Throws(IOException::class)
        fun getMinPackedValue(reader: IndexReader, field: String): ByteArray? {
            var minValue: ByteArray? = null
            for (ctx in reader.leaves()) {
                val values = ctx.reader().getPointValues(field)
                if (values == null) {
                    continue
                }
                val leafMinValue = values.minPackedValue
                if (leafMinValue == null) {
                    continue
                }
                if (minValue == null) {
                    minValue = leafMinValue.clone()
                } else {
                    val numDimensions = values.numIndexDimensions
                    val numBytesPerDimension = values.bytesPerDimension
                    val comparator: ByteArrayComparator =
                        ArrayUtil.getUnsignedComparator(numBytesPerDimension)
                    for (i in 0..<numDimensions) {
                        val offset = i * numBytesPerDimension
                        if (comparator.compare(leafMinValue, offset, minValue, offset) < 0) {
                            /*java.lang.System.arraycopy(leafMinValue, offset, minValue, offset, numBytesPerDimension)*/
                            leafMinValue.copyInto(
                                destination = minValue,
                                destinationOffset = offset,
                                startIndex = offset,
                                endIndex = offset + numBytesPerDimension
                            )
                        }
                    }
                }
            }
            return minValue
        }

        /**
         * Return the maximum packed values across all leaves of the given [IndexReader]. Leaves
         * that do not have points for the given field are ignored.
         *
         * @see PointValues.getMaxPackedValue
         */
        @Throws(IOException::class)
        fun getMaxPackedValue(reader: IndexReader, field: String): ByteArray? {
            var maxValue: ByteArray? = null
            for (ctx in reader.leaves()) {
                val values = ctx.reader().getPointValues(field)
                if (values == null) {
                    continue
                }
                val leafMaxValue = values.maxPackedValue
                if (leafMaxValue == null) {
                    continue
                }
                if (maxValue == null) {
                    maxValue = leafMaxValue.clone()
                } else {
                    val numDimensions = values.numIndexDimensions
                    val numBytesPerDimension = values.bytesPerDimension
                    val comparator: ByteArrayComparator =
                        ArrayUtil.getUnsignedComparator(numBytesPerDimension)
                    for (i in 0..<numDimensions) {
                        val offset = i * numBytesPerDimension
                        if (comparator.compare(leafMaxValue, offset, maxValue, offset) > 0) {
                            /*java.lang.System.arraycopy(leafMaxValue, offset, maxValue, offset, numBytesPerDimension)*/
                            leafMaxValue.copyInto(
                                destination = maxValue,
                                destinationOffset = offset,
                                startIndex = offset,
                                endIndex = offset + numBytesPerDimension
                            )
                        }
                    }
                }
            }
            return maxValue
        }

        /**
         * Estimate if the point count that would be matched by [.intersect] with the given [ ] is greater than or equal to the upperBound.
         *
         * @lucene.internal
         */
        @Throws(IOException::class)
        fun isEstimatedPointCountGreaterThanOrEqualTo(
            visitor: IntersectVisitor, pointTree: PointTree, upperBound: Long
        ): Boolean {
            return estimatePointCount(visitor, pointTree, upperBound) >= upperBound
        }

        /**
         * Estimate the number of documents that would be matched by [.intersect] with the given
         * [IntersectVisitor]. The estimation will terminate when the point count gets greater than
         * or equal to the upper bound.
         *
         *
         * TODO: will broad-first help estimation terminate earlier
         */
        @Throws(IOException::class)
        private fun estimatePointCount(
            visitor: IntersectVisitor, pointTree: PointTree, upperBound: Long
        ): Long {
            val r = visitor.compare(pointTree.minPackedValue, pointTree.maxPackedValue)
            when (r) {
                Relation.CELL_OUTSIDE_QUERY ->         // This cell is fully outside the query shape: no points added
                    return 0L

                Relation.CELL_INSIDE_QUERY ->         // This cell is fully inside the query shape: add all points
                    return pointTree.size()

                Relation.CELL_CROSSES_QUERY ->         // The cell crosses the shape boundary: keep recursing
                    if (pointTree.moveToChild()) {
                        var cost: Long = 0
                        do {
                            cost += estimatePointCount(visitor, pointTree, upperBound - cost)
                        } while (cost < upperBound && pointTree.moveToSibling())
                        pointTree.moveToParent()
                        return cost
                    } else {
                        // Assume half the points matched
                        return (pointTree.size() + 1) / 2
                    }

                else -> throw IllegalArgumentException("Unreachable code")
            }
        }
    }
}
