package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
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

/**
 * Finds all previously indexed geo points that comply the given [ShapeField.QueryRelation]
 * with the specified array of [LatLonGeometry].
 *
 *
 * The field must be indexed using [LatLonDocValuesField] added per document.
 */
internal class LatLonDocValuesQuery(field: String?, queryRelation: ShapeField.QueryRelation, vararg geometries: LatLonGeometry) : Query() {
    private val field: String?
    private val geometries: Array<LatLonGeometry>
    private val queryRelation: ShapeField.QueryRelation
    private val component2D: Component2D

    init {
        requireNotNull(field) { "field must not be null" }
        requireNotNull(queryRelation) { "queryRelation must not be null" }
        if (queryRelation == ShapeField.QueryRelation.WITHIN) {
            for (geometry in geometries) {
                require(geometry !is Line) {
                    ("LatLonDocValuesPointQuery does not support "
                            + ShapeField.QueryRelation.WITHIN
                            + " queries with line geometries")
                }
            }
        }
        if (queryRelation == ShapeField.QueryRelation.CONTAINS) {
            for (geometry in geometries) {
                require((geometry is Point) != false) {
                    ("LatLonDocValuesPointQuery does not support "
                            + ShapeField.QueryRelation.CONTAINS
                            + " queries with non-points geometries")
                }
            }
        }
        this.field = field
        this.geometries = geometries as Array<LatLonGeometry>
        this.queryRelation = queryRelation
        this.component2D = LatLonGeometry.create(*geometries)
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        if (this.field != field) {
            sb.append(this.field)
            sb.append(':')
        }
        sb.append(queryRelation).append(':')
        sb.append("geometries(").append(geometries.contentToString())
        return sb.append(")").toString()
    }

    override fun equals(obj: Any?): Boolean {
        if (sameClassAs(obj) == false) {
            return false
        }
        val other = obj as LatLonDocValuesQuery
        return field == other.field
                && queryRelation == other.queryRelation && geometries.contentEquals(other.geometries)
    }

    override fun hashCode(): Int {
        var h: Int = classHash()
        h = 31 * h + field.hashCode()
        h = 31 * h + queryRelation.hashCode()
        h = 31 * h + geometries.contentHashCode()
        return h
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        val component2DPredicate: GeoEncodingUtils.Component2DPredicate? =
            if (queryRelation == ShapeField.QueryRelation.CONTAINS)
                null
            else
                GeoEncodingUtils.createComponentPredicate(component2D)
        return object : ConstantScoreWeight(this, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val values: SortedNumericDocValues? = if(field != null) context.reader().getSortedNumericDocValues(field) else null
                if (values == null) {
                    return null
                }
                val iterator: TwoPhaseIterator
                when (queryRelation) {
                    ShapeField.QueryRelation.INTERSECTS -> iterator = intersects(values, component2DPredicate!!)
                    ShapeField.QueryRelation.WITHIN -> iterator = within(values, component2DPredicate!!)
                    ShapeField.QueryRelation.DISJOINT -> iterator = disjoint(values, component2DPredicate!!)
                    ShapeField.QueryRelation.CONTAINS -> iterator = contains(values, geometries)
                    else -> throw IllegalArgumentException(
                        "Invalid query relationship:[$queryRelation]"
                    )
                }
                val scorer = ConstantScoreScorer(boost, scoreMode, iterator)
                return DefaultScorerSupplier(scorer)
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return DocValues.isCacheable(ctx, field!!)
            }
        }
    }

    private fun intersects(
        values: SortedNumericDocValues, component2DPredicate: GeoEncodingUtils.Component2DPredicate
    ): TwoPhaseIterator {
        return object : TwoPhaseIterator(values) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                var i = 0
                val count: Int = values.docValueCount()
                while (i < count) {
                    val value: Long = values.nextValue()
                    val lat = (value ushr 32).toInt()
                    val lon = (value and 0xFFFFFFFFL).toInt()
                    if (component2DPredicate.test(lat, lon)) {
                        return true
                    }
                    ++i
                }
                return false
            }

            override fun matchCost(): Float {
                return 1000f // TODO: what should it be
            }
        }
    }

    private fun within(
        values: SortedNumericDocValues, component2DPredicate: GeoEncodingUtils.Component2DPredicate
    ): TwoPhaseIterator {
        return object : TwoPhaseIterator(values) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                var i = 0
                val count: Int = values.docValueCount()
                while (i < count) {
                    val value: Long = values.nextValue()
                    val lat = (value ushr 32).toInt()
                    val lon = (value and 0xFFFFFFFFL).toInt()
                    if (component2DPredicate.test(lat, lon) == false) {
                        return false
                    }
                    ++i
                }
                return true
            }

            override fun matchCost(): Float {
                return 1000f // TODO: what should it be
            }
        }
    }

    private fun disjoint(
        values: SortedNumericDocValues, component2DPredicate: GeoEncodingUtils.Component2DPredicate
    ): TwoPhaseIterator {
        return object : TwoPhaseIterator(values) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                var i = 0
                val count: Int = values.docValueCount()
                while (i < count) {
                    val value: Long = values.nextValue()
                    val lat = (value ushr 32).toInt()
                    val lon = (value and 0xFFFFFFFFL).toInt()
                    if (component2DPredicate.test(lat, lon)) {
                        return false
                    }
                    ++i
                }
                return true
            }

            override fun matchCost(): Float {
                return 1000f // TODO: what should it be
            }
        }
    }

    private fun contains(values: SortedNumericDocValues, geometries: Array<LatLonGeometry>): TwoPhaseIterator {
        val component2Ds: MutableList<Component2D> = ArrayList(geometries.size)
        for (i in geometries.indices) {
            component2Ds.add(LatLonGeometry.create(geometries[i]))
        }
        return object : TwoPhaseIterator(values) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                var answer: Component2D.WithinRelation = Component2D.WithinRelation.DISJOINT
                var i = 0
                val count: Int = values.docValueCount()
                while (i < count) {
                    val value: Long = values.nextValue()
                    val lat: Double = GeoEncodingUtils.decodeLatitude((value ushr 32).toInt())
                    val lon: Double = GeoEncodingUtils.decodeLongitude((value and 0xFFFFFFFFL).toInt())
                    for (component2D in component2Ds) {
                        val relation: Component2D.WithinRelation = component2D.withinPoint(lon, lat)
                        if (relation == Component2D.WithinRelation.NOTWITHIN) {
                            return false
                        } else if (relation != Component2D.WithinRelation.DISJOINT) {
                            answer = relation
                        }
                    }
                    ++i
                }
                return answer == Component2D.WithinRelation.CANDIDATE
            }

            override fun matchCost(): Float {
                return 1000f // TODO: what should it be
            }
        }
    }
}
