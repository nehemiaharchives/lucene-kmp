package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.BytesRef

/**
 * Bounding Box query for [ShapeDocValuesField] representing [XYShape]
 */
internal class XYShapeDocValuesQuery(
    field: String,
    queryRelation: QueryRelation,
    vararg geometries: XYGeometry
) : BaseShapeDocValuesQuery(field, queryRelation, *geometries) {

    override fun createComponent2D(vararg geometries: Geometry): Component2D {
        @Suppress("UNCHECKED_CAST")
        return XYGeometry.create(*(geometries as Array<XYGeometry>))
    }

    override fun getShapeDocValues(binaryValue: BytesRef): ShapeDocValues {
        return XYShapeDocValues(binaryValue)
    }

    override val spatialVisitor: SpatialVisitor
        get() = XYShapeQuery.getSpatialVisitor(queryComponent2D)

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        val query = this
        val spatialVisitor = this.spatialVisitor
        return object : org.gnit.lucenekmp.search.ConstantScoreWeight(query, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): org.gnit.lucenekmp.search.ScorerSupplier? {
                return getScorerSupplier(context.reader(), spatialVisitor, scoreMode, boost, score())
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return queryIsCacheable(ctx)
            }
        }
    }
}
