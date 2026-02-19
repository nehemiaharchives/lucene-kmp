package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Finds all previously indexed cartesian shapes that comply the given [QueryRelation] with
 * the specified array of [XYGeometry].
 *
 * The field must be indexed using [XYShape.createIndexableFields] added per document.
 */
internal class XYShapeQuery(field: String, queryRelation: QueryRelation, vararg geometries: XYGeometry) :
    SpatialQuery(field, queryRelation, *geometries) {

    override fun createComponent2D(vararg geometries: Geometry): Component2D {
        @Suppress("UNCHECKED_CAST")
        return XYGeometry.create(*(geometries as Array<XYGeometry>))
    }

    override val spatialVisitor: SpatialVisitor
        get() = getSpatialVisitor(queryComponent2D)

    companion object {
        private fun getSpatialVisitor(component2D: Component2D): SpatialVisitor {
            return object : SpatialVisitor() {
                override fun relate(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    val minY = XYEncodingUtils.decode(NumericUtils.sortableBytesToInt(minPackedValue, 0)).toDouble()
                    val minX = XYEncodingUtils.decode(NumericUtils.sortableBytesToInt(minPackedValue, ShapeField.BYTES)).toDouble()
                    val maxY = XYEncodingUtils.decode(NumericUtils.sortableBytesToInt(maxPackedValue, 2 * ShapeField.BYTES)).toDouble()
                    val maxX = XYEncodingUtils.decode(NumericUtils.sortableBytesToInt(maxPackedValue, 3 * ShapeField.BYTES)).toDouble()
                    return component2D.relate(minX, maxX, minY, maxY)
                }

                override fun intersects(): (ByteArray) -> Boolean {
                    val scratchTriangle = ShapeField.DecodedTriangle()
                    return { triangle ->
                        ShapeField.decodeTriangle(triangle, scratchTriangle)
                        when (scratchTriangle.type) {
                            ShapeField.DecodedTriangle.TYPE.POINT -> {
                                val y = XYEncodingUtils.decode(scratchTriangle.aY).toDouble()
                                val x = XYEncodingUtils.decode(scratchTriangle.aX).toDouble()
                                component2D.contains(x, y)
                            }

                            ShapeField.DecodedTriangle.TYPE.LINE -> {
                                val aY = XYEncodingUtils.decode(scratchTriangle.aY).toDouble()
                                val aX = XYEncodingUtils.decode(scratchTriangle.aX).toDouble()
                                val bY = XYEncodingUtils.decode(scratchTriangle.bY).toDouble()
                                val bX = XYEncodingUtils.decode(scratchTriangle.bX).toDouble()
                                component2D.intersectsLine(aX, aY, bX, bY)
                            }

                            ShapeField.DecodedTriangle.TYPE.TRIANGLE -> {
                                val aY = XYEncodingUtils.decode(scratchTriangle.aY).toDouble()
                                val aX = XYEncodingUtils.decode(scratchTriangle.aX).toDouble()
                                val bY = XYEncodingUtils.decode(scratchTriangle.bY).toDouble()
                                val bX = XYEncodingUtils.decode(scratchTriangle.bX).toDouble()
                                val cY = XYEncodingUtils.decode(scratchTriangle.cY).toDouble()
                                val cX = XYEncodingUtils.decode(scratchTriangle.cX).toDouble()
                                component2D.intersectsTriangle(aX, aY, bX, bY, cX, cY)
                            }
                        }
                    }
                }

                override fun within(): (ByteArray) -> Boolean {
                    val scratchTriangle = ShapeField.DecodedTriangle()
                    return { triangle ->
                        ShapeField.decodeTriangle(triangle, scratchTriangle)
                        when (scratchTriangle.type) {
                            ShapeField.DecodedTriangle.TYPE.POINT -> {
                                val y = XYEncodingUtils.decode(scratchTriangle.aY).toDouble()
                                val x = XYEncodingUtils.decode(scratchTriangle.aX).toDouble()
                                component2D.contains(x, y)
                            }

                            ShapeField.DecodedTriangle.TYPE.LINE -> {
                                val aY = XYEncodingUtils.decode(scratchTriangle.aY).toDouble()
                                val aX = XYEncodingUtils.decode(scratchTriangle.aX).toDouble()
                                val bY = XYEncodingUtils.decode(scratchTriangle.bY).toDouble()
                                val bX = XYEncodingUtils.decode(scratchTriangle.bX).toDouble()
                                component2D.containsLine(aX, aY, bX, bY)
                            }

                            ShapeField.DecodedTriangle.TYPE.TRIANGLE -> {
                                val aY = XYEncodingUtils.decode(scratchTriangle.aY).toDouble()
                                val aX = XYEncodingUtils.decode(scratchTriangle.aX).toDouble()
                                val bY = XYEncodingUtils.decode(scratchTriangle.bY).toDouble()
                                val bX = XYEncodingUtils.decode(scratchTriangle.bX).toDouble()
                                val cY = XYEncodingUtils.decode(scratchTriangle.cY).toDouble()
                                val cX = XYEncodingUtils.decode(scratchTriangle.cX).toDouble()
                                component2D.containsTriangle(aX, aY, bX, bY, cX, cY)
                            }
                        }
                    }
                }

                override fun contains(): (ByteArray) -> Component2D.WithinRelation {
                    val scratchTriangle = ShapeField.DecodedTriangle()
                    return { triangle ->
                        ShapeField.decodeTriangle(triangle, scratchTriangle)
                        when (scratchTriangle.type) {
                            ShapeField.DecodedTriangle.TYPE.POINT -> {
                                val y = XYEncodingUtils.decode(scratchTriangle.aY).toDouble()
                                val x = XYEncodingUtils.decode(scratchTriangle.aX).toDouble()
                                component2D.withinPoint(x, y)
                            }

                            ShapeField.DecodedTriangle.TYPE.LINE -> {
                                val aY = XYEncodingUtils.decode(scratchTriangle.aY).toDouble()
                                val aX = XYEncodingUtils.decode(scratchTriangle.aX).toDouble()
                                val bY = XYEncodingUtils.decode(scratchTriangle.bY).toDouble()
                                val bX = XYEncodingUtils.decode(scratchTriangle.bX).toDouble()
                                component2D.withinLine(aX, aY, scratchTriangle.ab, bX, bY)
                            }

                            ShapeField.DecodedTriangle.TYPE.TRIANGLE -> {
                                val aY = XYEncodingUtils.decode(scratchTriangle.aY).toDouble()
                                val aX = XYEncodingUtils.decode(scratchTriangle.aX).toDouble()
                                val bY = XYEncodingUtils.decode(scratchTriangle.bY).toDouble()
                                val bX = XYEncodingUtils.decode(scratchTriangle.bX).toDouble()
                                val cY = XYEncodingUtils.decode(scratchTriangle.cY).toDouble()
                                val cX = XYEncodingUtils.decode(scratchTriangle.cX).toDouble()
                                component2D.withinTriangle(
                                    aX,
                                    aY,
                                    scratchTriangle.ab,
                                    bX,
                                    bY,
                                    scratchTriangle.bc,
                                    cX,
                                    cY,
                                    scratchTriangle.ca
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
