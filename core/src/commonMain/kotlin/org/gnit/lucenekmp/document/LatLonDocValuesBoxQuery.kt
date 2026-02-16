package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.search.ConstantScoreScorer
import org.gnit.lucenekmp.search.ConstantScoreWeight
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.search.Weight

/** Distance query for [LatLonDocValuesField].  */
internal class LatLonDocValuesBoxQuery(
    field: String?,
    minLatitude: Double,
    maxLatitude: Double,
    minLongitude: Double,
    maxLongitude: Double
) : Query() {
    private val field: String?
    private val minLatitude: Int
    private val maxLatitude: Int
    private val minLongitude: Int
    private val maxLongitude: Int
    private val crossesDateline: Boolean

    init {
        GeoUtils.checkLatitude(minLatitude)
        GeoUtils.checkLatitude(maxLatitude)
        GeoUtils.checkLongitude(minLongitude)
        GeoUtils.checkLongitude(maxLongitude)
        requireNotNull(field) { "field must not be null" }
        this.field = field
        this.crossesDateline = minLongitude > maxLongitude // make sure to compute this before rounding
        this.minLatitude = GeoEncodingUtils.encodeLatitudeCeil(minLatitude)
        this.maxLatitude = GeoEncodingUtils.encodeLatitude(maxLatitude)
        this.minLongitude = GeoEncodingUtils.encodeLongitudeCeil(minLongitude)
        this.maxLongitude = GeoEncodingUtils.encodeLongitude(maxLongitude)
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        if (this.field != field) {
            sb.append(this.field)
            sb.append(':')
        }
        sb.append("box(minLat=").append(GeoEncodingUtils.decodeLatitude(minLatitude))
        sb.append(", maxLat=").append(GeoEncodingUtils.decodeLatitude(maxLatitude))
        sb.append(", minLon=").append(GeoEncodingUtils.decodeLongitude(minLongitude))
        sb.append(", maxLon=").append(GeoEncodingUtils.decodeLongitude(maxLongitude))
        return sb.append(")").toString()
    }

    override fun equals(obj: Any?): Boolean {
        if (sameClassAs(obj) == false) {
            return false
        }
        val other = obj as LatLonDocValuesBoxQuery
        return field == other.field
                && crossesDateline == other.crossesDateline && minLatitude == other.minLatitude && maxLatitude == other.maxLatitude && minLongitude == other.minLongitude && maxLongitude == other.maxLongitude
    }

    override fun hashCode(): Int {
        var h: Int = classHash()
        h = 31 * h + field.hashCode()
        h = 31 * h + crossesDateline.hashCode()
        h = 31 * h + minLatitude.hashCode()
        h = 31 * h + maxLatitude.hashCode()
        h = 31 * h + minLongitude.hashCode()
        h = 31 * h + maxLongitude.hashCode()
        return h
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : ConstantScoreWeight(this, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val values: SortedNumericDocValues? = if(field != null) context.reader().getSortedNumericDocValues(field) else null
                if (values == null) {
                    return null
                }

                val iterator: TwoPhaseIterator =
                    object : TwoPhaseIterator(values) {
                        @Throws(IOException::class)
                        override fun matches(): Boolean {
                            var i = 0
                            val count: Int = values.docValueCount()
                            while (i < count) {
                                val value: Long = values.nextValue()
                                val lat = (value ushr 32).toInt()
                                if (lat !in minLatitude..maxLatitude) {
                                    // not within latitude range
                                    ++i
                                    continue
                                }

                                val lon = (value and 0xFFFFFFFFL).toInt()
                                if (crossesDateline) {
                                    if (lon in (maxLongitude + 1)..<minLongitude) {
                                        // not within longitude range
                                        ++i
                                        continue
                                    }
                                } else {
                                    if (lon !in minLongitude..maxLongitude) {
                                        // not within longitude range
                                        ++i
                                        continue
                                    }
                                }

                                ++i
                                return true
                                /*++i*/ // unreachable code warning, so moving to above line
                            }
                            return false
                        }

                        override fun matchCost(): Float {
                            return 5f // 5 comparisons
                        }
                    }
                val scorer = ConstantScoreScorer(boost, scoreMode, iterator)
                return DefaultScorerSupplier(scorer)
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return DocValues.isCacheable(ctx, field!!)
            }
        }
    }
}
