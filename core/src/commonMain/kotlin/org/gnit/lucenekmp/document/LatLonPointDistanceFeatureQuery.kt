package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.util.SloppyMath
import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import kotlin.math.min
import kotlin.reflect.cast

internal class LatLonPointDistanceFeatureQuery(
    private val field: String,
    originLat: Double,
    originLon: Double,
    pivotDistance: Double
) : Query() {
    private val originLat: Double
    private val originLon: Double
    private val pivotDistance: Double

    init {
        GeoUtils.checkLatitude(originLat)
        GeoUtils.checkLongitude(originLon)
        this.originLon = originLon
        this.originLat = originLat
        require(!(pivotDistance <= 0)) { "pivotDistance must be > 0, got $pivotDistance" }
        this.pivotDistance = pivotDistance
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun equals(o: Any?): Boolean {
        return sameClassAs(o) && equalsTo(this::class.cast(o))
    }

    private fun equalsTo(other: LatLonPointDistanceFeatureQuery): Boolean {
        return field == other.field
                && originLon == other.originLon && originLat == other.originLat && pivotDistance == other.pivotDistance
    }

    override fun hashCode(): Int {
        var h: Int = classHash()
        h = 31 * h + field.hashCode()
        h = 31 * h + originLat.hashCode()
        h = 31 * h + originLon.hashCode()
        h = 31 * h + pivotDistance.hashCode()
        return h
    }

    override fun toString(field: String?): String {
        return (this::class.simpleName
                + "(field="
                + field
                + ",originLat="
                + originLat
                + ",originLon="
                + originLon
                + ",pivotDistance="
                + pivotDistance
                + ")")
    }

    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        return object : Weight(this) {
            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return false
            }

            @Throws(IOException::class)
            override fun explain(
                context: LeafReaderContext,
                doc: Int
            ): Explanation {
                val multiDocValues: SortedNumericDocValues =
                    DocValues.getSortedNumeric(context.reader(), field)
                if (multiDocValues.advanceExact(doc) == false) {
                    return Explanation.noMatch(
                        "Document $doc doesn't have a value for field $field"
                    )
                }
                val encoded = selectValue(multiDocValues)
                val latitudeBits = (encoded shr 32).toInt()
                val longitudeBits = (encoded and 0xFFFFFFFFL).toInt()
                val lat: Double = GeoEncodingUtils.decodeLatitude(latitudeBits)
                val lon: Double = GeoEncodingUtils.decodeLongitude(longitudeBits)
                val distance: Double = SloppyMath.haversinMeters(originLat, originLon, lat, lon)
                val score = (boost * (pivotDistance / (pivotDistance + distance))).toFloat()
                return Explanation.match(
                    score,
                    "Distance score, computed as weight * pivotDistance / (pivotDistance + abs(distance)) from:",
                    Explanation.match(boost, "weight"),
                    Explanation.match(pivotDistance, "pivotDistance"),
                    Explanation.match(originLat, "originLat"),
                    Explanation.match(originLon, "originLon"),
                    Explanation.match(lat, "current lat"),
                    Explanation.match(lon, "current lon"),
                    Explanation.match(distance, "distance")
                )
            }

            @Throws(IOException::class)
            fun selectValue(multiDocValues: SortedNumericDocValues): Long {
                val count: Int = multiDocValues.docValueCount()
                var value: Long = multiDocValues.nextValue()
                if (count == 1) {
                    return value
                }
                // compute exact sort key: avoid any asin() computations
                var distance = getDistanceKeyFromEncoded(value)
                for (i in 1..<count) {
                    val nextValue: Long = multiDocValues.nextValue()
                    val nextDistance = getDistanceKeyFromEncoded(nextValue)
                    if (nextDistance < distance) {
                        distance = nextDistance
                        value = nextValue
                    }
                }
                return value
            }

            fun selectValues(multiDocValues: SortedNumericDocValues): NumericDocValues {
                val singleton: NumericDocValues? =
                    DocValues.unwrapSingleton(multiDocValues)
                if (singleton != null) {
                    return singleton
                }
                return object : NumericDocValues() {
                    var value: Long = 0

                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return value
                    }

                    @Throws(IOException::class)
                    override fun advanceExact(target: Int): Boolean {
                        if (multiDocValues.advanceExact(target)) {
                            value = selectValue(multiDocValues)
                            return true
                        } else {
                            return false
                        }
                    }

                    override fun docID(): Int {
                        return multiDocValues.docID()
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        return multiDocValues.nextDoc()
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        return multiDocValues.advance(target)
                    }

                    override fun cost(): Long {
                        return multiDocValues.cost()
                    }
                }
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val pointValues: PointValues? = context.reader().getPointValues(field)
                if (pointValues == null) {
                    // No data on this segment
                    return null
                }
                val multiDocValues: SortedNumericDocValues =
                    DocValues.getSortedNumeric(context.reader(), field)
                val docValues: NumericDocValues = selectValues(multiDocValues)
                return object : ScorerSupplier() {
                    @Throws(IOException::class)
                    override fun get(leadCost: Long): Scorer {
                        return this@LatLonPointDistanceFeatureQuery.DistanceScorer(
                            context.reader().maxDoc(), leadCost, boost, pointValues, docValues
                        )
                    }

                    override fun cost(): Long {
                        return docValues.cost()
                    }
                }
            }
        }
    }

    private fun getDistanceFromEncoded(encoded: Long): Double {
        return SloppyMath.haversinMeters(getDistanceKeyFromEncoded(encoded))
    }

    private fun getDistanceKeyFromEncoded(encoded: Long): Double {
        val latitudeBits = (encoded shr 32).toInt()
        val longitudeBits = (encoded and 0xFFFFFFFFL).toInt()
        val lat: Double = GeoEncodingUtils.decodeLatitude(latitudeBits)
        val lon: Double = GeoEncodingUtils.decodeLongitude(longitudeBits)
        return SloppyMath.haversinSortKey(originLat, originLon, lat, lon)
    }

    private inner class DistanceScorer(
        private val maxDoc: Int,
        private val leadCost: Long,
        private val boost: Float,
        private val pointValues: PointValues,
        private val docValues: NumericDocValues
    ) : Scorer() {
        private var it: DocIdSetIterator
        private var doc = -1
        private var maxDistance: Double = GeoUtils.EARTH_MEAN_RADIUS_METERS * Math.PI

        override fun docID(): Int {
            return doc
        }

        fun score(distance: Double): Float {
            return (boost * (pivotDistance / (pivotDistance + distance))).toFloat()
        }

        /**
         * Inverting the score computation is very hard due to all potential rounding errors, so we
         * binary search the maximum distance. The limit is set to 1 meter.
         */
        fun computeMaxDistance(minScore: Float, previousMaxDistance: Double): Double {
            require(score(0.0) >= minScore)
            if (score(previousMaxDistance) >= minScore) {
                // minScore did not decrease enough to require an update to the max distance
                return previousMaxDistance
            }
            require(score(previousMaxDistance) < minScore)
            var min = 0.0
            var max = previousMaxDistance
            // invariant: score(min) >= minScore && score(max) < minScore
            while (max - min > 1) {
                val mid = (min + max) / 2
                val score = score(mid)
                if (score >= minScore) {
                    min = mid
                } else {
                    max = mid
                }
            }
            require(score(min) >= minScore)
            require(min == Double.Companion.MAX_VALUE || score(min + 1) < minScore)
            return min
        }

        @Throws(IOException::class)
        override fun score(): Float {
            if (docValues.advanceExact(docID()) == false) {
                return 0f
            }
            return score(getDistanceFromEncoded(docValues.longValue()))
        }

        override fun iterator(): DocIdSetIterator {
            // add indirection so that if 'it' is updated then it will
            // be taken into account
            return object : DocIdSetIterator() {
                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    return it.nextDoc().also { doc = it }
                }

                override fun docID(): Int {
                    return doc
                }

                override fun cost(): Long {
                    return it.cost()
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    return it.advance(target).also { doc = it }
                }
            }
        }

        override fun getMaxScore(upTo: Int): Float {
            return boost
        }

        private var setMinCompetitiveScoreCounter = 0

        init {
            // initially use doc values in order to iterate all documents that have
            // a value for this field
            this.it = docValues
        }

        override var minCompetitiveScore: Float
            get() {
                TODO()
            }
            set(minScore) {
            if (minScore > boost) {
                it = DocIdSetIterator.empty()
                return
            }

            setMinCompetitiveScoreCounter++
            // We sample the calls to this method as it is expensive to recalculate the iterator.
            if (setMinCompetitiveScoreCounter > 256 && (setMinCompetitiveScoreCounter and 0x1f) != 0x1f) {
                return
            }

            val previousMaxDistance = maxDistance
            maxDistance = computeMaxDistance(minScore, maxDistance)
            if (maxDistance == previousMaxDistance) {
                // nothing to update
                return
            }

            // Ideally we would be doing a distance query but that is too expensive so we approximate
            // with a box query which performs better.
            val box: Rectangle =
                Rectangle.fromPointDistance(originLat, originLon, maxDistance)
            val minLat: Int = GeoEncodingUtils.encodeLatitude(box.minLat)
            val maxLat: Int = GeoEncodingUtils.encodeLatitude(box.maxLat)
            val minLon: Int = GeoEncodingUtils.encodeLongitude(box.minLon)
            val maxLon: Int = GeoEncodingUtils.encodeLongitude(box.maxLon)
            val crossDateLine: Boolean = box.crossesDateline()

            val result = DocIdSetBuilder(maxDoc)
            val doc = docID()
            val visitor: PointValues.IntersectVisitor =
                object : PointValues.IntersectVisitor {
                    var adder: DocIdSetBuilder.BulkAdder? = null

                    override fun grow(count: Int) {
                        adder = result.grow(count)
                    }

                    override fun visit(docID: Int) {
                        if (docID <= doc) {
                            // Already visited or skipped
                            return
                        }
                        adder!!.add(docID)
                    }

                    override fun visit(docID: Int, packedValue: ByteArray) {
                        if (docID <= doc) {
                            // Already visited or skipped
                            return
                        }
                        val lat: Int = NumericUtils.sortableBytesToInt(packedValue, 0)
                        if (lat > maxLat || lat < minLat) {
                            // Latitude out of range
                            return
                        }
                        val lon: Int = NumericUtils.sortableBytesToInt(
                            packedValue,
                            LatLonPoint.BYTES
                        )
                        if (crossDateLine) {
                            if (lon < minLon && lon > maxLon) {
                                // Longitude out of range
                                return
                            }
                        } else {
                            if (lon > maxLon || lon < minLon) {
                                // Longitude out of range
                                return
                            }
                        }
                        adder!!.add(docID)
                    }

                    override fun compare(
                        minPackedValue: ByteArray,
                        maxPackedValue: ByteArray
                    ): Relation {
                        val latLowerBound: Int =
                            NumericUtils.sortableBytesToInt(minPackedValue, 0)
                        val latUpperBound: Int =
                            NumericUtils.sortableBytesToInt(maxPackedValue, 0)
                        if (latLowerBound > maxLat || latUpperBound < minLat) {
                            return Relation.CELL_OUTSIDE_QUERY
                        }
                        var crosses = latLowerBound < minLat || latUpperBound > maxLat
                        val lonLowerBound: Int =
                            NumericUtils.sortableBytesToInt(
                                minPackedValue,
                                LatLonPoint.BYTES
                            )
                        val lonUpperBound: Int =
                            NumericUtils.sortableBytesToInt(
                                maxPackedValue,
                                LatLonPoint.BYTES
                            )
                        if (crossDateLine) {
                            if (lonLowerBound > maxLon && lonUpperBound < minLon) {
                                return Relation.CELL_OUTSIDE_QUERY
                            }
                            crosses = crosses or (lonLowerBound < maxLon || lonUpperBound > minLon)
                        } else {
                            if (lonLowerBound > maxLon || lonUpperBound < minLon) {
                                return Relation.CELL_OUTSIDE_QUERY
                            }
                            crosses = crosses or (lonLowerBound < minLon || lonUpperBound > maxLon)
                        }
                        return if (crosses) {
                            Relation.CELL_CROSSES_QUERY
                        } else {
                            Relation.CELL_INSIDE_QUERY
                        }
                    }
                }

            val currentQueryCost = min(leadCost, it.cost())
            // TODO: what is the right factor compared to the current disi Is 8 optimal
            val threshold = currentQueryCost ushr 3
            if (PointValues.isEstimatedPointCountGreaterThanOrEqualTo(
                    visitor, pointValues.pointTree, threshold
                )
            ) {
                // the new range is not selective enough to be worth materializing
                return
            }
            pointValues.intersect(visitor)
            it = result.build().iterator()
        }
    }
}
