package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
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
import org.gnit.lucenekmp.util.NumericUtils
import okio.IOException
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.jdkport.isFinite
import kotlin.math.max
import kotlin.reflect.cast

/** Distance query for [LatLonPoint].  */
internal class LatLonPointDistanceQuery(field: String, latitude: Double, longitude: Double, radiusMeters: Double) :
    Query() {
    val field: String
    val latitude: Double
    val longitude: Double
    val radiusMeters: Double

    init {
        requireNotNull(field) { "field must not be null" }
        require(!(Double.isFinite(radiusMeters) == false || radiusMeters < 0)) { "radiusMeters: '$radiusMeters' is invalid" }
        GeoUtils.checkLatitude(latitude)
        GeoUtils.checkLongitude(longitude)
        this.field = field
        this.latitude = latitude
        this.longitude = longitude
        this.radiusMeters = radiusMeters
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        val box: Rectangle =
            Rectangle.fromPointDistance(latitude, longitude, radiusMeters)
        // create bounding box(es) for the distance range
        // these are pre-encoded with LatLonPoint's encoding
        val minLat: Int = GeoEncodingUtils.encodeLatitude(box.minLat)
        val maxLat: Int = GeoEncodingUtils.encodeLatitude(box.maxLat)
        val minLon: Int
        val maxLon: Int
        // second set of longitude ranges to check (for cross-dateline case)
        val minLon2: Int

        // crosses dateline: split
        if (box.crossesDateline()) {
            // box1
            minLon = Int.Companion.MIN_VALUE
            maxLon = GeoEncodingUtils.encodeLongitude(box.maxLon)
            // box2
            minLon2 = GeoEncodingUtils.encodeLongitude(box.minLon)
        } else {
            minLon = GeoEncodingUtils.encodeLongitude(box.minLon)
            maxLon = GeoEncodingUtils.encodeLongitude(box.maxLon)
            // disable box2
            minLon2 = Int.Companion.MAX_VALUE
        }

        // compute exact sort key: avoid any asin() computations
        val sortKey: Double = GeoUtils.distanceQuerySortKey(radiusMeters)

        val axisLat: Double = Rectangle.axisLat(latitude, radiusMeters)

        return object : ConstantScoreWeight(this, boost) {
            val distancePredicate: GeoEncodingUtils.DistancePredicate =
                GeoEncodingUtils.createDistancePredicate(latitude, longitude, radiusMeters)

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val reader: LeafReader = context.reader()
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
                LatLonPoint.checkCompatible(fieldInfo)

                // matching docids
                val result =                    DocIdSetBuilder(reader.maxDoc(), values)
                val visitor: IntersectVisitor = getIntersectVisitor(result)

                return object : ScorerSupplier() {
                    var cost: Long = -1

                    @Throws(IOException::class)
                    override fun get(leadCost: Long): Scorer {
                        if (values.docCount == reader.maxDoc() && values.docCount
                                .toLong() == values.size() && cost() > reader.maxDoc() / 2
                        ) {
                            // If all docs have exactly one value and the cost is greater
                            // than half the leaf size then maybe we can make things faster
                            // by computing the set of documents that do NOT match the range
                            val result: FixedBitSet =
                                FixedBitSet(reader.maxDoc())
                            result.set(0, reader.maxDoc())
                            val cost = longArrayOf(reader.maxDoc().toLong())
                            values.intersect(getInverseIntersectVisitor(result, cost))
                            val iterator: DocIdSetIterator =
                                BitSetIterator(result, cost[0])
                            return ConstantScoreScorer(score(), scoreMode, iterator)
                        }
                        values.intersect(visitor)
                        return ConstantScoreScorer(
                            score(),
                            scoreMode,
                            result.build().iterator()
                        )
                    }

                    override fun cost(): Long {
                        if (cost == -1L) {
                            cost = values.estimateDocCount(visitor)
                        }
                        require(cost >= 0)
                        return cost
                    }
                }
            }

            fun matches(packedValue: ByteArray): Boolean {
                val lat: Int = NumericUtils.sortableBytesToInt(packedValue, 0)
                // bounding box check
                if (lat > maxLat || lat < minLat) {
                    // latitude out of bounding box range
                    return false
                }
                val lon: Int =
                    NumericUtils.sortableBytesToInt(packedValue, Int.SIZE_BYTES)
                if ((lon > maxLon || lon < minLon) && lon < minLon2) {
                    // longitude out of bounding box range
                    return false
                }
                return distancePredicate.test(lat, lon)
            }

            // algorithm: we create a bounding box (two bounding boxes if we cross the dateline).
            // 1. check our bounding box(es) first. if the subtree is entirely outside of those, bail.
            // 2. check if the subtree is disjoint. it may cross the bounding box but not intersect with
            // circle
            // 3. see if the subtree is fully contained. if the subtree is enormous along the x axis,
            // wrapping half way around the world, etc: then this can't work, just go to step 4.
            // 4. recurse naively (subtrees crossing over circle edge)
            fun relate(
                minPackedValue: ByteArray,
                maxPackedValue: ByteArray
            ): Relation {
                val latLowerBound: Int = NumericUtils.sortableBytesToInt(minPackedValue, 0)
                val latUpperBound: Int = NumericUtils.sortableBytesToInt(maxPackedValue, 0)
                if (latLowerBound > maxLat || latUpperBound < minLat) {
                    // latitude out of bounding box range
                    return Relation.CELL_OUTSIDE_QUERY
                }

                val lonLowerBound: Int = NumericUtils.sortableBytesToInt(
                    minPackedValue,
                    LatLonPoint.BYTES
                )
                val lonUpperBound: Int = NumericUtils.sortableBytesToInt(
                    maxPackedValue,
                    LatLonPoint.BYTES
                )
                if ((lonLowerBound > maxLon || lonUpperBound < minLon) && lonUpperBound < minLon2) {
                    // longitude out of bounding box range
                    return Relation.CELL_OUTSIDE_QUERY
                }

                val latMin: Double = GeoEncodingUtils.decodeLatitude(latLowerBound)
                val lonMin: Double = GeoEncodingUtils.decodeLongitude(lonLowerBound)
                val latMax: Double = GeoEncodingUtils.decodeLatitude(latUpperBound)
                val lonMax: Double = GeoEncodingUtils.decodeLongitude(lonUpperBound)

                return GeoUtils.relate(
                    latMin, latMax, lonMin, lonMax, latitude, longitude, sortKey, axisLat
                )
            }

            /** Create a visitor that collects documents matching the range.  */
            fun getIntersectVisitor(result: DocIdSetBuilder): IntersectVisitor {
                return object : IntersectVisitor {
                    var adder: DocIdSetBuilder.BulkAdder? = null

                    override fun grow(count: Int) {
                        adder = result.grow(count)
                    }

                    override fun visit(docID: Int) {
                        adder!!.add(docID)
                    }

                    override fun visit(ref: IntsRef) {
                        adder!!.add(ref)
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

                    override fun compare(
                        minPackedValue: ByteArray,
                        maxPackedValue: ByteArray
                    ): Relation {
                        return relate(minPackedValue, maxPackedValue)
                    }
                }
            }

            /** Create a visitor that clears documents that do NOT match the range.  */
            fun getInverseIntersectVisitor(
                result: FixedBitSet,
                cost: LongArray
            ): IntersectVisitor {
                return object : IntersectVisitor {
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

                    override fun visit(docID: Int, packedValue: ByteArray) {
                        if (matches(packedValue) == false) {
                            visit(docID)
                        }
                    }

                    @Throws(IOException::class)
                    override fun visit(iterator: DocIdSetIterator, packedValue: ByteArray) {
                        if (matches(packedValue) == false) {
                            visit(iterator)
                        }
                    }

                    override fun compare(
                        minPackedValue: ByteArray,
                        maxPackedValue: ByteArray
                    ): Relation {
                        val relation: Relation =
                            relate(minPackedValue, maxPackedValue)
                        when (relation) {
                            Relation.CELL_INSIDE_QUERY ->                 // all points match, skip this subtree
                                return Relation.CELL_OUTSIDE_QUERY

                            Relation.CELL_OUTSIDE_QUERY ->                 // none of the points match, clear all documents
                                return Relation.CELL_INSIDE_QUERY

                            Relation.CELL_CROSSES_QUERY -> return relation
                            else -> return relation
                        }
                    }
                }
            }
        }
    }

    override fun hashCode(): Int {
        val prime = 31
        var result: Int = classHash()
        result = prime * result + field.hashCode()
        var temp: Long = Double.doubleToLongBits(latitude)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = Double.doubleToLongBits(longitude)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = Double.doubleToLongBits(radiusMeters)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(this::class.cast(other))
    }

    private fun equalsTo(other: LatLonPointDistanceQuery): Boolean {
        return field == other.field
                && Double.doubleToLongBits(latitude) == Double.doubleToLongBits(other.latitude) && Double.doubleToLongBits(
            longitude
        ) == Double.doubleToLongBits(other.longitude) && Double.doubleToLongBits(radiusMeters) == Double.doubleToLongBits(
            other.radiusMeters
        )
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        if (this.field != field) {
            sb.append(this.field)
            sb.append(':')
        }
        sb.append(latitude)
        sb.append(",")
        sb.append(longitude)
        sb.append(" +/- ")
        sb.append(radiusMeters)
        sb.append(" meters")
        return sb.toString()
    }
}
