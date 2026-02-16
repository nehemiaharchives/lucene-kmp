package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYGeometry
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

/** XYGeometry query for [XYDocValuesField].  */
class XYDocValuesPointInGeometryQuery internal constructor(field: String?, vararg geometries: XYGeometry) : Query() {
    private val field: String?
    private val geometries: Array<XYGeometry>

    init {
        requireNotNull(field) { "field must not be null" }
        requireNotNull(geometries) { "geometries must not be null" }
        require(geometries.isNotEmpty()) { "geometries must not be empty" }
        for (i in geometries.indices) {
            requireNotNull(geometries[i]) { "geometries[$i] must not be null" }
        }
        this.field = field
        this.geometries = geometries.copyOf() as Array<XYGeometry>
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        if (this.field != field) {
            sb.append(this.field)
            sb.append(':')
        }
        sb.append("geometries(").append(geometries.contentToString())
        return sb.append(")").toString()
    }

    override fun equals(obj: Any?): Boolean {
        if (sameClassAs(obj) == false) {
            return false
        }
        val other = obj as XYDocValuesPointInGeometryQuery
        return field == other.field && geometries.contentEquals(other.geometries)
    }

    override fun hashCode(): Int {
        var h: Int = classHash()
        h = 31 * h + field.hashCode()
        h = 31 * h + geometries.contentHashCode()
        return h
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : ConstantScoreWeight(this, boost) {
            val component2D: Component2D = XYGeometry.create(*geometries)

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
                                val x = XYEncodingUtils.decode((value ushr 32).toInt()).toDouble()
                                val y = XYEncodingUtils.decode((value and 0xFFFFFFFFL).toInt()).toDouble()
                                if (component2D.contains(x, y)) {
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
                val scorer = ConstantScoreScorer(boost, scoreMode, iterator)
                return DefaultScorerSupplier(scorer)
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return DocValues.isCacheable(ctx, field!!)
            }
        }
    }
}
