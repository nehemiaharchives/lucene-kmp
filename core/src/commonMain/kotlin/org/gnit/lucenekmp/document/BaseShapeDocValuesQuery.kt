package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.search.ConstantScoreScorer
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.util.BytesRef

/**
 * Base query class for ShapeDocValues queries. Concrete implementations include:
 * [LatLonShapeDocValuesQuery] and [XYShapeDocValuesQuery]
 */
internal abstract class BaseShapeDocValuesQuery(
    field: String,
    queryRelation: QueryRelation,
    vararg geometries: Geometry
) : SpatialQuery(field, validateRelation(queryRelation), *geometries) {

    protected abstract fun getShapeDocValues(binaryValue: BytesRef): ShapeDocValues

    @Throws(IOException::class)
    override fun getScorerSupplier(
        reader: LeafReader,
        spatialVisitor: SpatialVisitor,
        scoreMode: ScoreMode,
        boost: Float,
        score: Float
    ): ScorerSupplier? {
        val values: BinaryDocValues = reader.getBinaryDocValues(field) ?: return null
        reader.fieldInfos.fieldInfo(field) ?: return null

        val iterator = object : TwoPhaseIterator(values) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                return match(getShapeDocValues(requireNotNull(values.binaryValue())))
            }

            override fun matchCost(): Float {
                return this@BaseShapeDocValuesQuery.matchCost()
            }
        }
        return object : ScorerSupplier() {
            override fun get(leadCost: Long): Scorer {
                return ConstantScoreScorer(boost, scoreMode, iterator)
            }

            override fun cost(): Long {
                return reader.maxDoc().toLong()
            }
        }
    }

    /** matches the doc value to the query; overridable to provide custom query logic */
    @Throws(IOException::class)
    protected open fun match(shapeDocValues: ShapeDocValues): Boolean {
        val result = matchesComponent(shapeDocValues, queryRelation, queryComponent2D)
        if (queryRelation == QueryRelation.DISJOINT) {
            return result == false
        }
        return result
    }

    /** compute the cost of the query; overridable */
    protected open fun matchCost(): Float {
        // multiply comparisons (estimated 60) by number of terms (averaged at 100)
        // todo: revisit
        return 60f * 100f
    }

    protected fun matchesComponent(
        dv: ShapeDocValues,
        queryRelation: QueryRelation,
        component: Component2D
    ): Boolean {
        val r = dv.relate(component)
        if (r != PointValues.Relation.CELL_OUTSIDE_QUERY) {
            if (queryRelation == QueryRelation.WITHIN) {
                return r == PointValues.Relation.CELL_INSIDE_QUERY
            }
            return true
        }
        return false
    }

    companion object {
        private fun validateRelation(queryRelation: QueryRelation): QueryRelation {
            if (queryRelation == QueryRelation.CONTAINS) {
                throw IllegalArgumentException(
                    "ShapeDocValuesBoundingBoxQuery does not yet support CONTAINS queries"
                )
            }
            return queryRelation
        }
    }
}
