package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.search.CollectionTerminatedException
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
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IntsRef
import okio.IOException
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.jdkport.StrictMath
import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min

/**
 * Base query class for all spatial geometries: [LatLonShape], [LatLonPoint] and [ ]. In order to create a query, use the factory methods on those classes.
 *
 * @lucene.internal
 */
internal abstract class SpatialQuery protected constructor(
    field: String,
    queryRelation: QueryRelation,
    vararg geometries: Geometry
) : Query() {
    /** returns the field name  */
    /** field name  */
    val field: String

    /**
     * query relation disjoint: [QueryRelation.DISJOINT], intersects: [ ][QueryRelation.INTERSECTS], within: [QueryRelation.DISJOINT], contains: [ ][QueryRelation.CONTAINS]
     */
    @get:JvmName("queryRelationGetter")
    val queryRelation: QueryRelation

    val geometries: Array<Geometry>
    val queryComponent2D: Component2D

    init {
        requireNotNull(field) { "field must not be null" }
        requireNotNull(queryRelation) { "queryRelation must not be null" }
        this.field = field
        this.queryRelation = queryRelation
        this.geometries = (geometries as Array<Geometry>).copyOf()
        this.queryComponent2D = createComponent2D(*geometries)
    }

    protected abstract fun createComponent2D(vararg geometries: Geometry): Component2D

    /**
     * returns the spatial visitor to be used for this query. Called before generating the query
     * [Weight]
     */
    protected abstract val spatialVisitor: SpatialVisitor

    /** Visitor used for walking the BKD tree.  */
    protected abstract class SpatialVisitor {
        /** relates a range of points (internal node) to the query  */
        protected abstract fun relate(
            minPackedValue: ByteArray,
            maxPackedValue: ByteArray
        ): PointValues.Relation

        /** Gets a intersects predicate. Called when constructing a [Scorer]  */
        protected abstract fun intersects(): (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/

        /** Gets a within predicate. Called when constructing a [Scorer]  */
        protected abstract fun within(): (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/

        /** Gets a contains function. Called when constructing a [Scorer]  */
        abstract fun contains(): (ByteArray) -> Component2D.WithinRelation /*java.util.function.Function<ByteArray, Component2D.WithinRelation>*/

        private fun containsPredicate(): (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/ {
            val contains: (ByteArray) -> Component2D.WithinRelation /*java.util.function.Function<ByteArray, Component2D.WithinRelation>*/ =
                contains()
            return { bytes: ByteArray -> contains(bytes) == Component2D.WithinRelation.CANDIDATE }
        }

        fun getInnerFunction(
            queryRelation: QueryRelation
        ): (ByteArray, ByteArray) -> PointValues.Relation /*BiFunction<ByteArray, ByteArray, PointValues.Relation>*/ {
            if (queryRelation == QueryRelation.DISJOINT) {
                return { minPackedValue: ByteArray, maxPackedValue: ByteArray ->
                    transposeRelation(
                        relate(
                            minPackedValue,
                            maxPackedValue
                        )
                    )
                }
            }
            return { minPackedValue: ByteArray, maxPackedValue: ByteArray ->
                relate(
                    minPackedValue,
                    maxPackedValue
                )
            }
        }

        fun getLeafPredicate(queryRelation: QueryRelation): (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/ {
            when (queryRelation) {
                QueryRelation.INTERSECTS -> return intersects()
                QueryRelation.WITHIN -> return within()
                QueryRelation.DISJOINT -> return { bytes: ByteArray -> !intersects()(bytes) } /*intersects().negate()*/
                QueryRelation.CONTAINS -> return containsPredicate()
                else -> throw IllegalArgumentException("Unsupported query type :[$queryRelation]")
            }
        }
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    protected fun queryIsCacheable(ctx: LeafReaderContext): Boolean {
        return true
    }

    @Throws(IOException::class)
    protected fun getScorerSupplier(
        reader: LeafReader,
        spatialVisitor: SpatialVisitor,
        scoreMode: ScoreMode,
        boost: Float,
        score: Float
    ): ScorerSupplier? {
        val values: PointValues? = reader.getPointValues(field)
        if (values == null) {
            // No docs in this segment had any points fields
            return null
        }
        val fieldInfo: FieldInfo? = reader.fieldInfos.fieldInfo(field)
        if (fieldInfo == null) {
            // No docs in this segment indexed this field at all
            return null
        }

        val rel: PointValues.Relation =
            spatialVisitor
                .getInnerFunction(queryRelation)(values.minPackedValue, values.maxPackedValue)
        if (rel == PointValues.Relation.CELL_OUTSIDE_QUERY
            || (rel == PointValues.Relation.CELL_INSIDE_QUERY && queryRelation == QueryRelation.CONTAINS)
        ) {
            // no documents match the query
            return null
        } else if (values.docCount == reader.maxDoc() && rel == PointValues.Relation.CELL_INSIDE_QUERY) {
            // all documents match the query
            return object : ScorerSupplier() {
                override fun get(leadCost: Long): Scorer {
                    return ConstantScoreScorer(
                        score,
                        scoreMode,
                        DocIdSetIterator.all(reader.maxDoc())
                    )
                }

                override fun cost(): Long {
                    return reader.maxDoc().toLong()
                }
            }
        } else {
            if (queryRelation != QueryRelation.INTERSECTS && queryRelation != QueryRelation.CONTAINS && values.docCount
                    .toLong() != values.size() && hasAnyHits(spatialVisitor, queryRelation, values) == false
            ) {
                // First we check if we have any hits so we are fast in the adversarial case where
                // the shape does not match any documents and we are in the dense case
                return null
            }
            // walk the tree to get matching documents
            return object : RelationScorerSupplier(values, spatialVisitor, queryRelation) {
                @Throws(IOException::class)
                override fun get(leadCost: Long): Scorer {
                    return getScorer(reader, score, scoreMode)
                }
            }
        }
    }

    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        val query = this
        val spatialVisitor = this.spatialVisitor
        return object : ConstantScoreWeight(query, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val reader: LeafReader = context.reader()
                return getScorerSupplier(reader, spatialVisitor, scoreMode, boost, score())
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return queryIsCacheable(ctx)
            }
        }
    }

    /** returns the query relation  */
    fun getQueryRelation(): QueryRelation {
        return queryRelation
    }

    override fun hashCode(): Int {
        var hash: Int = classHash()
        hash = 31 * hash + field.hashCode()
        hash = 31 * hash + queryRelation.hashCode()
        hash = 31 * hash + geometries.contentHashCode()
        return hash
    }

    override fun equals(o: Any?): Boolean {
        return sameClassAs(o) && equalsTo(o!!)
    }

    /** class specific equals check  */
    protected open fun equalsTo(o: Any?): Boolean {
        return field == (o as SpatialQuery).field
                && this.queryRelation == o.queryRelation && geometries.contentEquals(o.geometries)
    }

    /**
     * utility class for implementing constant score logic specific to INTERSECT, WITHIN, and DISJOINT
     */
    private abstract class RelationScorerSupplier(
        private val values: PointValues,
        private val spatialVisitor: SpatialVisitor,
        private val queryRelation: QueryRelation
    ) : ScorerSupplier() {
        private var cost: Long = -1

        @Throws(IOException::class)
        protected fun getScorer(
            reader: LeafReader, boost: Float, scoreMode: ScoreMode
        ): Scorer {
            when (queryRelation) {
                QueryRelation.INTERSECTS -> return getSparseScorer(
                    reader,
                    boost,
                    scoreMode
                )

                QueryRelation.CONTAINS -> return getContainsDenseScorer(
                    reader,
                    boost,
                    scoreMode
                )

                QueryRelation.WITHIN, QueryRelation.DISJOINT -> return if (values.docCount
                        .toLong() == values.size()
                )
                    getSparseScorer(reader, boost, scoreMode)
                else
                    getDenseScorer(reader, boost, scoreMode)

                else -> throw IllegalArgumentException("Unsupported query type :[$queryRelation]")
            }
        }

        /** Scorer used for INTERSECTS and single value points  */
        @Throws(IOException::class)
        fun getSparseScorer(
            reader: LeafReader, boost: Float, scoreMode: ScoreMode
        ): Scorer {
            if (queryRelation == QueryRelation.DISJOINT && values.docCount == reader.maxDoc() && values.docCount
                    .toLong() == values.size() && cost() > reader.maxDoc() / 2
            ) {
                // If all docs have exactly one value and the cost is greater
                // than half the leaf size then maybe we can make things faster
                // by computing the set of documents that do NOT match the query
                val result = FixedBitSet(reader.maxDoc())
                result.set(0, reader.maxDoc())
                val cost = longArrayOf(reader.maxDoc().toLong())
                values.intersect(getInverseDenseVisitor(spatialVisitor, queryRelation, result, cost))
                val iterator: DocIdSetIterator =
                    BitSetIterator(result, cost[0])
                return ConstantScoreScorer(boost, scoreMode, iterator)
            } else if (values.docCount < (values.size() ushr 2)) {
                // we use a dense structure so we can skip already visited documents
                val result = FixedBitSet(reader.maxDoc())
                val cost = longArrayOf(0)
                values.intersect(getIntersectsDenseVisitor(spatialVisitor, queryRelation, result, cost))
                require(cost[0] > 0 || result.cardinality() == 0)
                val iterator: DocIdSetIterator =
                    if (cost[0] == 0L) DocIdSetIterator.empty() else BitSetIterator(
                        result,
                        cost[0]
                    )
                return ConstantScoreScorer(boost, scoreMode, iterator)
            } else {
                val docIdSetBuilder = DocIdSetBuilder(reader.maxDoc(), values)
                values.intersect(getSparseVisitor(spatialVisitor, queryRelation, docIdSetBuilder))
                val iterator: DocIdSetIterator = docIdSetBuilder.build().iterator()
                return ConstantScoreScorer(boost, scoreMode, iterator)
            }
        }

        /** Scorer used for WITHIN and DISJOINT  */
        @Throws(IOException::class)
        fun getDenseScorer(
            reader: LeafReader,
            boost: Float,
            scoreMode: ScoreMode
        ): Scorer {
            val result = FixedBitSet(reader.maxDoc())
            val cost: LongArray
            if (values.docCount == reader.maxDoc()) {
                cost = longArrayOf(values.size())
                // In this case we can spare one visit to the tree, all documents
                // are potential matches
                result.set(0, reader.maxDoc())
                // Remove false positives
                values.intersect(getInverseDenseVisitor(spatialVisitor, queryRelation, result, cost))
            } else {
                cost = longArrayOf(0)
                // Get potential  documents.
                val excluded = FixedBitSet(reader.maxDoc())
                values.intersect(getDenseVisitor(spatialVisitor, queryRelation, result, excluded, cost))
                result.andNot(excluded)
                // Remove false positives, we only care about the inner nodes as intersecting
                // leaf nodes have been already taken into account. Unfortunately this
                // process still reads the leaf nodes.
                values.intersect(getShallowInverseDenseVisitor(spatialVisitor, queryRelation, result))
            }
            require(cost[0] > 0 || result.cardinality() == 0)
            val iterator: DocIdSetIterator =
                if (cost[0] == 0L) DocIdSetIterator.empty() else BitSetIterator(
                    result,
                    cost[0]
                )
            return ConstantScoreScorer(boost, scoreMode, iterator)
        }

        @Throws(IOException::class)
        fun getContainsDenseScorer(
            reader: LeafReader,
            boost: Float,
            scoreMode: ScoreMode
        ): Scorer {
            val result = FixedBitSet(reader.maxDoc())
            val cost = longArrayOf(0)
            // Get potential  documents.
            val excluded = FixedBitSet(reader.maxDoc())
            values.intersect(
                getContainsDenseVisitor(spatialVisitor, queryRelation, result, excluded, cost)
            )
            result.andNot(excluded)
            require(cost[0] > 0 || result.cardinality() == 0)
            val iterator: DocIdSetIterator =
                if (cost[0] == 0L) DocIdSetIterator.empty() else BitSetIterator(
                    result,
                    cost[0]
                )
            return ConstantScoreScorer(boost, scoreMode, iterator)
        }

        override fun cost(): Long {
            if (cost == -1L) {
                // Computing the cost may be expensive, so only do it if necessary
                cost = values.estimateDocCount(getEstimateVisitor(spatialVisitor, queryRelation))
                require(cost >= 0)
            }
            return cost
        }
    }

    /** Holds spatial logic for a bounding box that works in the encoded space  */
    open class EncodedRectangle protected constructor(
        protected var minX: Int,
        protected var maxX: Int,
        protected var minY: Int,
        protected var maxY: Int,
        protected var wrapsCoordinateSystem: Boolean
    ) {
        protected fun wrapsCoordinateSystem(): Boolean {
            return wrapsCoordinateSystem
        }

        /** Checks if the rectangle contains the provided point  */
        fun contains(x: Int, y: Int): Boolean {
            if (y < minY || y > maxY) {
                return false
            }
            return if (wrapsCoordinateSystem()) {
                (x > maxX && x < minX) == false
            } else {
                (x > maxX || x < minX) == false
            }
        }

        /** Checks if the rectangle intersects the provided LINE  */
        fun intersectsLine(aX: Int, aY: Int, bX: Int, bY: Int): Boolean {
            if (contains(aX, aY) || contains(bX, bY)) {
                return true
            }
            // check bounding boxes are disjoint
            if (StrictMath.max(aY, bY) < minY || StrictMath.min(aY, bY) > maxY) {
                return false
            }
            if (wrapsCoordinateSystem) { // crosses dateline
                if (StrictMath.min(aX, bX) > maxX && StrictMath.max(aX, bX) < minX) {
                    return false
                }
            } else {
                if (StrictMath.min(aX, bX) > maxX || StrictMath.max(aX, bX) < minX) {
                    return false
                }
            }
            // expensive part
            return edgeIntersectsQuery(aX, aY, bX, bY)
        }

        /** Checks if the rectangle intersects the provided triangle  */
        fun intersectsTriangle(aX: Int, aY: Int, bX: Int, bY: Int, cX: Int, cY: Int): Boolean {
            // query contains any triangle points
            if (contains(aX, aY) || contains(bX, bY) || contains(cX, cY)) {
                return true
            }
            // check bounding box of triangle
            val tMinY: Int = StrictMath.min(StrictMath.min(aY, bY), cY)
            val tMaxY: Int = StrictMath.max(StrictMath.max(aY, bY), cY)
            // check bounding boxes are disjoint
            if (tMaxY < minY || tMinY > maxY) {
                return false
            }
            val tMinX: Int = StrictMath.min(StrictMath.min(aX, bX), cX)
            val tMaxX: Int = StrictMath.max(StrictMath.max(aX, bX), cX)
            if (wrapsCoordinateSystem) { // wraps coordinate system
                if (tMinX > maxX && tMaxX < minX) {
                    return false
                }
            } else {
                if (tMinX > maxX || tMaxX < minX) {
                    return false
                }
            }
            // expensive part
            return Component2D.pointInTriangle(
                tMinX.toDouble(),
                tMaxX.toDouble(),
                tMinY.toDouble(),
                tMaxY.toDouble(),
                minX.toDouble(),
                minY.toDouble(),
                aX.toDouble(),
                aY.toDouble(),
                bX.toDouble(),
                bY.toDouble(),
                cX.toDouble(),
                cY.toDouble()
            )
                    || edgeIntersectsQuery(aX, aY, bX, bY)
                    || edgeIntersectsQuery(bX, bY, cX, cY)
                    || edgeIntersectsQuery(cX, cY, aX, aY)
        }

        fun intersectsRectangle(minX: Int, maxX: Int, minY: Int, maxY: Int): Boolean {
            // simple Y check
            if (this.minY > maxY || this.maxY < minY) {
                return false
            }

            if (this.minX <= maxX) {
                // if the triangle's minX is less than the query maxX
                if (wrapsCoordinateSystem || this.maxX >= minX) {
                    // intersects if the query box is wrapping (western box) or
                    // the triangle maxX is greater than the query minX
                    return true
                }
            }

            return wrapsCoordinateSystem
        }

        fun containsRectangle(minX: Int, maxX: Int, minY: Int, maxY: Int): Boolean {
            return this.minX <= minX && this.maxX >= maxX && this.minY <= minY && this.maxY >= maxY
        }

        /** Checks if the rectangle contains the provided LINE  */
        fun containsLine(aX: Int, aY: Int, bX: Int, bY: Int): Boolean {
            if (aY < minY || bY < minY || aY > maxY || bY > maxY) {
                return false
            }
            return if (wrapsCoordinateSystem) { // wraps coordinate system
                (aX >= minX && bX >= minX) || (aX <= maxX && bX <= maxX)
            } else {
                aX >= minX && bX >= minX && aX <= maxX && bX <= maxX
            }
        }

        /** Checks if the rectangle contains the provided triangle  */
        fun containsTriangle(aX: Int, aY: Int, bX: Int, bY: Int, cX: Int, cY: Int): Boolean {
            if (aY < minY || bY < minY || cY < minY || aY > maxY || bY > maxY || cY > maxY) {
                return false
            }
            return if (wrapsCoordinateSystem) { // wraps coordinate system
                (aX >= minX && bX >= minX && cX >= minX) || (aX <= maxX && bX <= maxX && cX <= maxX)
            } else {
                aX >= minX && bX >= minX && cX >= minX && aX <= maxX && bX <= maxX && cX <= maxX
            }
        }

        /** Returns the Within relation to the provided triangle  */
        fun withinLine(
            aX: Int,
            aY: Int,
            ab: Boolean,
            bX: Int,
            bY: Int
        ): Component2D.WithinRelation {
            if (contains(aX, aY) || contains(bX, bY)) {
                return Component2D.WithinRelation.NOTWITHIN
            }
            if (ab == true && edgeIntersectsBox(aX, aY, bX, bY, minX, maxX, minY, maxY) == true) {
                return Component2D.WithinRelation.NOTWITHIN
            }
            return Component2D.WithinRelation.DISJOINT
        }

        /** Returns the Within relation to the provided triangle  */
        fun withinTriangle(
            aX: Int, aY: Int, ab: Boolean, bX: Int, bY: Int, bc: Boolean, cX: Int, cY: Int, ca: Boolean
        ): Component2D.WithinRelation {
            // Points belong to the shape so if points are inside the rectangle then it cannot be within.
            if (contains(aX, aY) || contains(bX, bY) || contains(cX, cY)) {
                return Component2D.WithinRelation.NOTWITHIN
            }

            // Bounding boxes disjoint
            val tMinY: Int = StrictMath.min(StrictMath.min(aY, bY), cY)
            val tMaxY: Int = StrictMath.max(StrictMath.max(aY, bY), cY)
            // check bounding boxes are disjoint
            if (tMaxY < minY || tMinY > maxY) {
                return Component2D.WithinRelation.DISJOINT
            }
            val tMinX: Int = StrictMath.min(StrictMath.min(aX, bX), cX)
            val tMaxX: Int = StrictMath.max(StrictMath.max(aX, bX), cX)
            if (wrapsCoordinateSystem) { // wraps coordinate system
                if (tMinX > maxX && tMaxX < minX) {
                    return Component2D.WithinRelation.DISJOINT
                }
            } else {
                if (tMinX > maxX || tMaxX < minX) {
                    return Component2D.WithinRelation.DISJOINT
                }
            }
            // If any of the edges intersects an edge belonging to the shape then it cannot be within.
            var relation: Component2D.WithinRelation =
                Component2D.WithinRelation.DISJOINT
            if (edgeIntersectsBox(aX, aY, bX, bY, minX, maxX, minY, maxY) == true) {
                if (ab == true) {
                    return Component2D.WithinRelation.NOTWITHIN
                } else {
                    relation = Component2D.WithinRelation.CANDIDATE
                }
            }
            if (edgeIntersectsBox(bX, bY, cX, cY, minX, maxX, minY, maxY) == true) {
                if (bc == true) {
                    return Component2D.WithinRelation.NOTWITHIN
                } else {
                    relation = Component2D.WithinRelation.CANDIDATE
                }
            }

            if (edgeIntersectsBox(cX, cY, aX, aY, minX, maxX, minY, maxY) == true) {
                if (ca == true) {
                    return Component2D.WithinRelation.NOTWITHIN
                } else {
                    relation = Component2D.WithinRelation.CANDIDATE
                }
            }
            // Check if shape is within the triangle
            if (relation == Component2D.WithinRelation.CANDIDATE
                || Component2D.pointInTriangle(
                    tMinX.toDouble(),
                    tMaxX.toDouble(),
                    tMinY.toDouble(),
                    tMaxY.toDouble(),
                    minX.toDouble(),
                    minY.toDouble(),
                    aX.toDouble(),
                    aY.toDouble(),
                    bX.toDouble(),
                    bY.toDouble(),
                    cX.toDouble(),
                    cY.toDouble()
                )
            ) {
                return Component2D.WithinRelation.CANDIDATE
            }
            return relation
        }

        /** returns true if the edge (defined by (aX, aY) (bX, bY)) intersects the query  */
        private fun edgeIntersectsQuery(aX: Int, aY: Int, bX: Int, bY: Int): Boolean {
            if (wrapsCoordinateSystem) {
                return edgeIntersectsBox(
                    aX,
                    aY,
                    bX,
                    bY,
                    GeoEncodingUtils.MIN_LON_ENCODED,
                    this.maxX,
                    this.minY,
                    this.maxY
                )
                        || edgeIntersectsBox(
                    aX,
                    aY,
                    bX,
                    bY,
                    this.minX,
                    GeoEncodingUtils.MAX_LON_ENCODED,
                    this.minY,
                    this.maxY
                )
            }
            return edgeIntersectsBox(aX, aY, bX, bY, this.minX, this.maxX, this.minY, this.maxY)
        }

        companion object {
            /** returns true if the edge (defined by (aX, aY) (bX, bY)) intersects the box  */
            private fun edgeIntersectsBox(
                aX: Int, aY: Int, bX: Int, bY: Int, minX: Int, maxX: Int, minY: Int, maxY: Int
            ): Boolean {
                if (max(aX, bX) < minX || min(aX, bX) > maxX || min(aY, bY) > maxY || max(aY, bY) < minY) {
                    return false
                }
                return GeoUtils.lineCrossesLineWithBoundary(
                    aX.toDouble(),
                    aY.toDouble(),
                    bX.toDouble(),
                    bY.toDouble(),
                    minX.toDouble(),
                    maxY.toDouble(),
                    maxX.toDouble(),
                    maxY.toDouble()
                )
                        ||  // top
                        GeoUtils.lineCrossesLineWithBoundary(
                            aX.toDouble(),
                            aY.toDouble(),
                            bX.toDouble(),
                            bY.toDouble(),
                            maxX.toDouble(),
                            maxY.toDouble(),
                            maxX.toDouble(),
                            minY.toDouble()
                        )
                        ||  // bottom
                        GeoUtils.lineCrossesLineWithBoundary(
                            aX.toDouble(),
                            aY.toDouble(),
                            bX.toDouble(),
                            bY.toDouble(),
                            maxX.toDouble(),
                            minY.toDouble(),
                            minX.toDouble(),
                            minY.toDouble()
                        )
                        ||  // left
                        GeoUtils.lineCrossesLineWithBoundary(
                            aX.toDouble(),
                            aY.toDouble(),
                            bX.toDouble(),
                            bY.toDouble(),
                            minX.toDouble(),
                            minY.toDouble(),
                            minX.toDouble(),
                            maxY.toDouble()
                        ) // right
            }
        }
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        sb.append(this::class.simpleName)
        sb.append(':')
        if (this.field == field == false) {
            sb.append(" field=")
            sb.append(this.field)
            sb.append(':')
        }
        sb.append("[")
        for (i in geometries.indices) {
            sb.append(geometries[i].toString())
            sb.append(',')
        }
        sb.append(']')
        return sb.toString()
    }

    companion object {
        /**
         * transpose the relation; INSIDE becomes OUTSIDE, OUTSIDE becomes INSIDE, CROSSES remains
         * unchanged
         */
        protected fun transposeRelation(r: PointValues.Relation): PointValues.Relation {
            if (r == PointValues.Relation.CELL_INSIDE_QUERY) {
                return PointValues.Relation.CELL_OUTSIDE_QUERY
            } else if (r == PointValues.Relation.CELL_OUTSIDE_QUERY) {
                return PointValues.Relation.CELL_INSIDE_QUERY
            }
            return PointValues.Relation.CELL_CROSSES_QUERY
        }

        /** create a visitor for calculating point count estimates for the provided relation  */
        private fun getEstimateVisitor(
            spatialVisitor: SpatialVisitor, queryRelation: QueryRelation
        ): PointValues.IntersectVisitor {
            val innerFunction: (ByteArray, ByteArray) -> PointValues.Relation /*BiFunction<ByteArray, ByteArray, PointValues.Relation>*/ =
                spatialVisitor.getInnerFunction(queryRelation)
            return object : PointValues.IntersectVisitor {
                override fun visit(docID: Int) {
                    throw UnsupportedOperationException()
                }

                override fun visit(docID: Int, t: ByteArray) {
                    throw UnsupportedOperationException()
                }

                override fun compare(
                    minTriangle: ByteArray,
                    maxTriangle: ByteArray
                ): PointValues.Relation {
                    return innerFunction(minTriangle, maxTriangle)
                }
            }
        }

        /**
         * create a visitor that adds documents that match the query using a sparse bitset. (Used by
         * INTERSECT when the number of docs <= 4 * number of points )
         */
        private fun getSparseVisitor(
            spatialVisitor: SpatialVisitor,
            queryRelation: QueryRelation,
            result: DocIdSetBuilder
        ): PointValues.IntersectVisitor {
            val innerFunction: (ByteArray, ByteArray) -> PointValues.Relation /*BiFunction<ByteArray, ByteArray, PointValues.Relation>*/ =
                spatialVisitor.getInnerFunction(queryRelation)
            val leafPredicate: (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/ =
                spatialVisitor.getLeafPredicate(queryRelation)
            return object : PointValues.IntersectVisitor {
                var adder: DocIdSetBuilder.BulkAdder? = null

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

                override fun visit(docID: Int, t: ByteArray) {
                    if (leafPredicate(t)) {
                        visit(docID)
                    }
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator, t: ByteArray) {
                    if (leafPredicate(t)) {
                        adder!!.add(iterator)
                    }
                }

                override fun compare(
                    minTriangle: ByteArray,
                    maxTriangle: ByteArray
                ): PointValues.Relation {
                    return innerFunction(minTriangle, maxTriangle)
                }
            }
        }

        /** Scorer used for INTERSECTS when the number of points > 4 * number of docs  */
        private fun getIntersectsDenseVisitor(
            spatialVisitor: SpatialVisitor,
            queryRelation: QueryRelation,
            result: FixedBitSet,
            cost: LongArray
        ): PointValues.IntersectVisitor {
            val innerFunction: (ByteArray, ByteArray) -> PointValues.Relation /*BiFunction<ByteArray, ByteArray, PointValues.Relation>*/ =
                spatialVisitor.getInnerFunction(queryRelation)
            val leafPredicate: (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/ =
                spatialVisitor.getLeafPredicate(queryRelation)
            return object : PointValues.IntersectVisitor {
                override fun visit(docID: Int) {
                    result.set(docID)
                    cost[0]++
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator) {
                    result.or(iterator)
                    cost[0] += iterator.cost()
                }

                override fun visit(ref: IntsRef) {
                    for (i in 0..<ref.length) {
                        result.set(ref.ints[ref.offset + i])
                    }
                    cost[0] += ref.length.toLong()
                }

                override fun visit(docID: Int, t: ByteArray) {
                    if (result.get(docID) == false) {
                        if (leafPredicate(t)) {
                            visit(docID)
                        }
                    }
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator, t: ByteArray) {
                    if (leafPredicate(t)) {
                        visit(iterator)
                    }
                }

                override fun compare(
                    minTriangle: ByteArray,
                    maxTriangle: ByteArray
                ): PointValues.Relation {
                    return innerFunction(minTriangle, maxTriangle)
                }
            }
        }

        /**
         * create a visitor that adds documents that match the query using a dense bitset; used with
         * WITHIN & DISJOINT
         */
        private fun getDenseVisitor(
            spatialVisitor: SpatialVisitor,
            queryRelation: QueryRelation,
            result: FixedBitSet,
            excluded: FixedBitSet,
            cost: LongArray
        ): PointValues.IntersectVisitor {
            val innerFunction: (ByteArray, ByteArray) -> PointValues.Relation /*BiFunction<ByteArray, ByteArray, PointValues.Relation>*/ =
                spatialVisitor.getInnerFunction(queryRelation)
            val leafPredicate: (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/ =
                spatialVisitor.getLeafPredicate(queryRelation)
            return object : PointValues.IntersectVisitor {
                override fun visit(docID: Int) {
                    result.set(docID)
                    cost[0]++
                }

                override fun visit(ref: IntsRef) {
                    for (i in 0..<ref.length) {
                        result.set(ref.ints[ref.offset + i])
                    }
                    cost[0] += ref.length.toLong()
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator) {
                    result.or(iterator)
                    cost[0] += iterator.cost()
                }

                override fun visit(docID: Int, t: ByteArray) {
                    if (excluded.get(docID) == false) {
                        if (leafPredicate(t)) {
                            visit(docID)
                        } else {
                            excluded.set(docID)
                        }
                    }
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator, t: ByteArray) {
                    if (leafPredicate(t)) {
                        visit(iterator)
                    } else {
                        excluded.or(iterator)
                    }
                }

                override fun compare(
                    minTriangle: ByteArray,
                    maxTriangle: ByteArray
                ): PointValues.Relation {
                    return innerFunction(minTriangle, maxTriangle)
                }
            }
        }

        /**
         * create a visitor that adds documents that match the query using a dense bitset; used with
         * CONTAINS
         */
        private fun getContainsDenseVisitor(
            spatialVisitor: SpatialVisitor,
            queryRelation: QueryRelation,
            result: FixedBitSet,
            excluded: FixedBitSet,
            cost: LongArray
        ): PointValues.IntersectVisitor {
            val innerFunction: (ByteArray, ByteArray) -> PointValues.Relation /*BiFunction<ByteArray, ByteArray, PointValues.Relation>*/ =
                spatialVisitor.getInnerFunction(queryRelation)
            val leafFunction: (ByteArray) -> Component2D.WithinRelation /*java.util.function.Function<ByteArray, Component2D.WithinRelation>*/ =
                spatialVisitor.contains()
            return object : PointValues.IntersectVisitor {
                override fun visit(docID: Int) {
                    excluded.set(docID)
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator) {
                    excluded.or(iterator)
                }

                override fun visit(ref: IntsRef) {
                    for (i in 0..<ref.length) {
                        visit(ref.ints[ref.offset + i])
                    }
                }

                override fun visit(docID: Int, t: ByteArray) {
                    if (excluded.get(docID) == false) {
                        val within: Component2D.WithinRelation = leafFunction(t)
                        if (within == Component2D.WithinRelation.CANDIDATE) {
                            cost[0]++
                            result.set(docID)
                        } else if (within == Component2D.WithinRelation.NOTWITHIN) {
                            excluded.set(docID)
                        }
                    }
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator, t: ByteArray) {
                    val within: Component2D.WithinRelation = leafFunction(t)
                    var docID: Int
                    while ((iterator.nextDoc()
                            .also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS
                    ) {
                        if (within == Component2D.WithinRelation.CANDIDATE) {
                            cost[0]++
                            result.set(docID)
                        } else if (within == Component2D.WithinRelation.NOTWITHIN) {
                            excluded.set(docID)
                        }
                    }
                }

                override fun compare(
                    minTriangle: ByteArray,
                    maxTriangle: ByteArray
                ): PointValues.Relation {
                    return innerFunction(minTriangle, maxTriangle)
                }
            }
        }

        /**
         * create a visitor that clears documents that do not match the polygon query using a dense
         * bitset; used with WITHIN & DISJOINT
         */
        private fun getInverseDenseVisitor(
            spatialVisitor: SpatialVisitor,
            queryRelation: QueryRelation,
            result: FixedBitSet,
            cost: LongArray
        ): PointValues.IntersectVisitor {
            val innerFunction: (ByteArray, ByteArray) -> PointValues.Relation /*BiFunction<ByteArray, ByteArray, PointValues.Relation>*/ =
                spatialVisitor.getInnerFunction(queryRelation)
            val leafPredicate: (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/ =
                spatialVisitor.getLeafPredicate(queryRelation)
            return object : PointValues.IntersectVisitor {
                override fun visit(docID: Int) {
                    result.clear(docID)
                    cost[0]--
                }

                override fun visit(ref: IntsRef) {
                    for (i in 0..<ref.length) {
                        result.clear(ref.ints[ref.offset + i])
                    }
                    cost[0] = max(0, cost[0] - ref.length)
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator) {
                    result.andNot(iterator)
                    cost[0] = max(0, cost[0] - iterator.cost())
                }

                override fun visit(docID: Int, packedTriangle: ByteArray) {
                    if (result.get(docID)) {
                        if (leafPredicate(packedTriangle) == false) {
                            visit(docID)
                        }
                    }
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator, t: ByteArray) {
                    if (leafPredicate(t) == false) {
                        visit(iterator)
                    }
                }

                override fun compare(
                    minPackedValue: ByteArray,
                    maxPackedValue: ByteArray
                ): PointValues.Relation {
                    return transposeRelation(innerFunction(minPackedValue, maxPackedValue))
                }
            }
        }

        /**
         * create a visitor that clears documents that do not match the polygon query using a dense
         * bitset; used with WITHIN & DISJOINT. This visitor only takes into account inner nodes
         */
        private fun getShallowInverseDenseVisitor(
            spatialVisitor: SpatialVisitor,
            queryRelation: QueryRelation,
            result: FixedBitSet
        ): PointValues.IntersectVisitor {
            val innerFunction: (ByteArray, ByteArray) -> PointValues.Relation /*BiFunction<ByteArray, ByteArray, PointValues.Relation>*/ =
                spatialVisitor.getInnerFunction(queryRelation)

            return object : PointValues.IntersectVisitor {
                override fun visit(docID: Int) {
                    result.clear(docID)
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator) {
                    result.andNot(iterator)
                }

                override fun visit(ref: IntsRef) {
                    for (i in 0..<ref.length) {
                        visit(ref.ints[ref.offset + i])
                    }
                }

                override fun visit(docID: Int, packedTriangle: ByteArray) {
                    // NO-OP
                }

                override fun visit(iterator: DocIdSetIterator, t: ByteArray) {
                    // NO-OP
                }

                override fun compare(
                    minPackedValue: ByteArray,
                    maxPackedValue: ByteArray
                ): PointValues.Relation {
                    return transposeRelation(innerFunction(minPackedValue, maxPackedValue))
                }
            }
        }

        /**
         * Return true if the query matches at least one document. It creates a visitor that terminates as
         * soon as one or more docs are matched.
         */
        @Throws(IOException::class)
        private fun hasAnyHits(
            spatialVisitor: SpatialVisitor,
            queryRelation: QueryRelation,
            values: PointValues
        ): Boolean {
            try {
                val innerFunction: (ByteArray, ByteArray) -> PointValues.Relation /*BiFunction<ByteArray, ByteArray, PointValues.Relation>*/ =
                    spatialVisitor.getInnerFunction(queryRelation)
                val leafPredicate: (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/ =
                    spatialVisitor.getLeafPredicate(queryRelation)
                values.intersect(
                    object : PointValues.IntersectVisitor {
                        override fun visit(docID: Int) {
                            throw CollectionTerminatedException()
                        }

                        override fun visit(docID: Int, t: ByteArray) {
                            if (leafPredicate(t)) {
                                throw CollectionTerminatedException()
                            }
                        }

                        override fun visit(iterator: DocIdSetIterator, t: ByteArray) {
                            if (leafPredicate(t)) {
                                throw CollectionTerminatedException()
                            }
                        }

                        override fun compare(
                            minPackedValue: ByteArray,
                            maxPackedValue: ByteArray
                        ): PointValues.Relation {
                            val rel: PointValues.Relation =
                                innerFunction(minPackedValue, maxPackedValue)
                            if (rel == PointValues.Relation.CELL_INSIDE_QUERY) {
                                throw CollectionTerminatedException()
                            }
                            return rel
                        }
                    })
            } catch (e: CollectionTerminatedException) {
                return true
            }
            return false
        }
    }
}
