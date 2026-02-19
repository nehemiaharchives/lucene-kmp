package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.util.BytesRef

/**
 * Concrete implementation of a [ShapeDocValuesField] for cartesian geometries.
 *
 * This field should be instantiated through [XYShape.createDocValueField] with [XYLine]
 *
 * - [XYShape.createDocValueField] with [XYPolygon] for indexing a cartesian polygon doc value field.
 * - [XYShape.createDocValueField] with [XYLine] for indexing a cartesian linestring doc value.
 * - [XYShape.createDocValueField] with x/y for indexing a x, y cartesian point doc value.
 * - [XYShape.createDocValueField] with tessellation list for indexing from a precomputed tessellation.
 * - [XYShape.createDocValueField] with [BytesRef] for indexing from existing encoding.
 */
class XYShapeDocValuesField private constructor(name: String, shapeDocValues: XYShapeDocValues) :
    ShapeDocValuesField(name, shapeDocValues) {

    /** constructs a `XYShapeDocValueField` from a pre-tessellated geometry */
    internal constructor(name: String, tessellation: List<ShapeField.DecodedTriangle>) :
        this(name, XYShapeDocValues(tessellation))

    /** Creates a `XYShapeDocValueField` from a given serialized value */
    internal constructor(name: String, binaryValue: BytesRef) :
        this(name, XYShapeDocValues(binaryValue))

    /** retrieves the centroid location for the geometry */
    override fun getCentroid(): XYPoint {
        return shapeDocValues.centroid as XYPoint
    }

    override fun getBoundingBox(): XYRectangle {
        return shapeDocValues.boundingBox as XYRectangle
    }

    override fun decodeX(encoded: Int): Double {
        return XYEncodingUtils.decode(encoded).toDouble()
    }

    override fun decodeY(encoded: Int): Double {
        return XYEncodingUtils.decode(encoded).toDouble()
    }
}
