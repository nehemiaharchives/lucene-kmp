package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.util.BytesRef

/**
 * Concrete implementation of a [ShapeDocValuesField] for geographic geometries.
 *
 * This field should be instantiated through [LatLonShape.createDocValueField] with [Line].
 *
 * - [LatLonShape.createDocValueField] with [Polygon] for indexing a geographic polygon doc value field.
 * - [LatLonShape.createDocValueField] with [Line] for indexing a geographic linestring doc value.
 * - [LatLonShape.createDocValueField] with lat/lon for indexing a geographic point doc value.
 * - [LatLonShape.createDocValueField] with tessellation list for indexing from a precomputed tessellation.
 * - [LatLonShape.createDocValueField] with [BytesRef] for indexing from existing encoding.
 *
 * WARNING: Like [LatLonShape], vertex values are indexed with some loss of precision
 * from the original `double` values.
 *
 * @see PointValues
 * @see LatLonDocValuesField
 */
class LatLonShapeDocValuesField private constructor(name: String, shapeDocValues: LatLonShapeDocValues) :
    ShapeDocValuesField(name, shapeDocValues) {
    /** constructs a `LatLonShapeDocValueField` from a pre-tessellated geometry */
    internal constructor(name: String, tessellation: List<ShapeField.DecodedTriangle>) :
        this(name, LatLonShapeDocValues(tessellation))

    /** Creates a `LatLonShapeDocValueField` from a given serialized value */
    internal constructor(name: String, binaryValue: BytesRef) :
        this(name, LatLonShapeDocValues(binaryValue))

    /** retrieves the centroid location for the geometry */
    override fun getCentroid(): Point {
        return shapeDocValues.centroid as Point
    }

    /** retrieves the bounding box for the geometry */
    override fun getBoundingBox(): Rectangle {
        return shapeDocValues.boundingBox as Rectangle
    }

    override fun decodeX(encoded: Int): Double {
        return GeoEncodingUtils.decodeLongitude(encoded)
    }

    override fun decodeY(encoded: Int): Double {
        return GeoEncodingUtils.decodeLatitude(encoded)
    }
}
