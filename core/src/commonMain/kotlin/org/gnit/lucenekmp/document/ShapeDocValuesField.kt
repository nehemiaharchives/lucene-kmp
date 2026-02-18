package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.document.ShapeField.DecodedTriangle.TYPE
import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.IndexableFieldType
import org.gnit.lucenekmp.search.Query

/**
 * A doc values field for [LatLonShape] and [XYShape] that uses [ShapeDocValues]
 * as the underlying binary doc value format.
 */
abstract class ShapeDocValuesField(name: String, protected val shapeDocValues: ShapeDocValues) :
    Field(name, FIELD_TYPE) {

    init {
        this.fieldsData = shapeDocValues.binaryValue()
    }

    /** The name of the field */
    override fun name(): String {
        return super.name()
    }

    /** Gets the [IndexableFieldType] for this ShapeDocValue field */
    override fun fieldType(): IndexableFieldType {
        return FIELD_TYPE
    }

    /** Currently there is no string representation for the ShapeDocValueField */
    override fun stringValue(): String? {
        return null
    }

    /** TokenStreams are not yet supported */
    override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream? {
        return null
    }

    /** Returns the number of terms (tessellated triangles) for this shape */
    fun numberOfTerms(): Int {
        return shapeDocValues.numberOfTerms()
    }

    /** Creates a geometry query for shape docvalues */
    companion object {
        protected val FIELD_TYPE: FieldType = FieldType()

        init {
            FIELD_TYPE.setDocValuesType(DocValuesType.BINARY)
            FIELD_TYPE.setOmitNorms(true)
            FIELD_TYPE.freeze()
        }

        fun newGeometryQuery(field: String, relation: QueryRelation, vararg geometries: Any): Query {
            throw IllegalStateException(
                "geometry queries not yet supported on shape doc values for field [$field]"
            )
        }
    }

    /** retrieves the centroid location for the geometry */
    abstract fun getCentroid(): Geometry

    /** retrieves the bounding box for the geometry */
    abstract fun getBoundingBox(): Geometry

    /**
     * Retrieves the highest dimensional type (POINT, LINE, TRIANGLE) for computing the geometry(s)
     * centroid.
     */
    fun getHighestDimensionType(): TYPE {
        return shapeDocValues.getHighestDimension()
    }

    /** decodes x coordinates from encoded space */
    protected abstract fun decodeX(encoded: Int): Double

    /** decodes y coordinates from encoded space */
    protected abstract fun decodeY(encoded: Int): Double
}
