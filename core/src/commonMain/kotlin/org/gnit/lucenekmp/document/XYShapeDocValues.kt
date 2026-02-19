package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.util.BytesRef

/**
 * A concrete implementation of [ShapeDocValues] for storing binary doc value representation
 * of [XYShape] geometries in a [XYShapeDocValuesField]
 *
 * Note: This class cannot be instantiated directly. See [XYShape] for factory API based on
 * different geometries.
 */
class XYShapeDocValues : ShapeDocValues {
    /** protected ctor for instantiating a cartesian doc value based on a tessellation */
    internal constructor(tessellation: List<ShapeField.DecodedTriangle>) : super(tessellation)

    /**
     * protected ctor for instantiating a cartesian doc value based on an already retrieved binary
     * format
     */
    internal constructor(binaryValue: BytesRef) : super(binaryValue)


    override fun computeCentroid(): XYPoint {
        val encoder = getEncoder()
        return XYPoint(
            encoder.decodeX(getEncodedCentroidX()).toFloat(),
            encoder.decodeY(getEncodedCentroidY()).toFloat()
        )
    }

    override fun computeBoundingBox(): XYRectangle {
        val encoder = getEncoder()
        return XYRectangle(
            encoder.decodeX(getEncodedMinX()).toFloat(),
            encoder.decodeX(getEncodedMaxX()).toFloat(),
            encoder.decodeY(getEncodedMinY()).toFloat(),
            encoder.decodeY(getEncodedMaxY()).toFloat()
        )
    }

    override fun getEncoder(): Encoder {
        return object : Encoder {
            override fun encodeX(x: Double): Int {
                return XYEncodingUtils.encode(x.toFloat())
            }

            override fun encodeY(y: Double): Int {
                return XYEncodingUtils.encode(y.toFloat())
            }

            override fun decodeX(encoded: Int): Double {
                return XYEncodingUtils.decode(encoded).toDouble()
            }

            override fun decodeY(encoded: Int): Double {
                return XYEncodingUtils.decode(encoded).toDouble()
            }
        }
    }
}
