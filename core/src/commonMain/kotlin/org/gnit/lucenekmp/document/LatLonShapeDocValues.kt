package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.util.BytesRef

/**
 * A concrete implementation of [ShapeDocValues] for storing binary doc value representation
 * of [LatLonShape] geometries in a [LatLonShapeDocValuesField].
 *
 * Note: This class cannot be instantiated directly. See [LatLonShape] for factory API
 * based on different geometries.
 */
class LatLonShapeDocValues : ShapeDocValues {
    fun getBinaryValueRef(): BytesRef {
        return binaryValue()
    }

    /** protected ctor for instantiating a lat lon doc value based on a tessellation */
    internal constructor(tessellation: List<ShapeField.DecodedTriangle>) : super(tessellation)

    /**
     * protected ctor for instantiating a lat lon doc value based on an already retrieved binary
     * format
     */
    internal constructor(binaryValue: BytesRef) : super(binaryValue)

    override fun computeCentroid(): Point {
        val encoder = getEncoder()
        return Point(
            encoder.decodeY(getEncodedCentroidY()),
            encoder.decodeX(getEncodedCentroidX())
        )
    }

    override fun computeBoundingBox(): Rectangle {
        val encoder = getEncoder()
        return Rectangle(
            encoder.decodeY(getEncodedMinY()),
            encoder.decodeY(getEncodedMaxY()),
            encoder.decodeX(getEncodedMinX()),
            encoder.decodeX(getEncodedMaxX())
        )
    }

    override fun getEncoder(): Encoder {
        return object : Encoder {
            override fun encodeX(x: Double): Int {
                return GeoEncodingUtils.encodeLongitude(x)
            }

            override fun encodeY(y: Double): Int {
                return GeoEncodingUtils.encodeLatitude(y)
            }

            override fun decodeX(encoded: Int): Double {
                return GeoEncodingUtils.decodeLongitude(encoded)
            }

            override fun decodeY(encoded: Int): Double {
                return GeoEncodingUtils.decodeLatitude(encoded)
            }
        }
    }
}
