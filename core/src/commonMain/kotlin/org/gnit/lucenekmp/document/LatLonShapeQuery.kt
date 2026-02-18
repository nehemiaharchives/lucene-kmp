package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Finds all previously indexed geo shapes that comply the given [QueryRelation] with the
 * specified array of [LatLonGeometry].
 */
internal class LatLonShapeQuery(field: String, queryRelation: QueryRelation, vararg geometries: LatLonGeometry) :
    SpatialQuery(field, queryRelation, *validateGeometries(queryRelation, *geometries)) {

    override fun createComponent2D(vararg geometries: Geometry): Component2D {
        @Suppress("UNCHECKED_CAST")
        return LatLonGeometry.create(*(geometries as Array<LatLonGeometry>))
    }

    override val spatialVisitor: SpatialVisitor
        get() = getSpatialVisitor(queryComponent2D)

    companion object {
        private fun validateGeometries(
            queryRelation: QueryRelation,
            vararg geometries: LatLonGeometry
        ): Array<LatLonGeometry> {
            if (queryRelation == QueryRelation.WITHIN) {
                for (geometry in geometries) {
                    if (geometry is Line) {
                        throw IllegalArgumentException(
                            "LatLonShapeQuery does not support ${QueryRelation.WITHIN} queries with line geometries"
                        )
                    }
                }
            }
            return geometries.map { it }.toTypedArray()
        }

        private fun getSpatialVisitor(component2D: Component2D): SpatialVisitor {
            return object : SpatialVisitor() {
                override fun relate(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                    val minLat = GeoEncodingUtils.decodeLatitude(NumericUtils.sortableBytesToInt(minPackedValue, 0))
                    val minLon =
                        GeoEncodingUtils.decodeLongitude(NumericUtils.sortableBytesToInt(minPackedValue, ShapeField.BYTES))
                    val maxLat =
                        GeoEncodingUtils.decodeLatitude(
                            NumericUtils.sortableBytesToInt(maxPackedValue, 2 * ShapeField.BYTES)
                        )
                    val maxLon =
                        GeoEncodingUtils.decodeLongitude(
                            NumericUtils.sortableBytesToInt(maxPackedValue, 3 * ShapeField.BYTES)
                        )
                    return component2D.relate(minLon, maxLon, minLat, maxLat)
                }

                override fun intersects(): (ByteArray) -> Boolean {
                    val scratchTriangle = ShapeField.DecodedTriangle()
                    return { triangle ->
                        ShapeField.decodeTriangle(triangle, scratchTriangle)
                        when (scratchTriangle.type) {
                            ShapeField.DecodedTriangle.TYPE.POINT -> {
                                val alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY)
                                val alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX)
                                component2D.contains(alon, alat)
                            }

                            ShapeField.DecodedTriangle.TYPE.LINE -> {
                                val alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY)
                                val alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX)
                                val blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY)
                                val blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX)
                                component2D.intersectsLine(alon, alat, blon, blat)
                            }

                            ShapeField.DecodedTriangle.TYPE.TRIANGLE -> {
                                val alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY)
                                val alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX)
                                val blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY)
                                val blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX)
                                val clat = GeoEncodingUtils.decodeLatitude(scratchTriangle.cY)
                                val clon = GeoEncodingUtils.decodeLongitude(scratchTriangle.cX)
                                component2D.intersectsTriangle(alon, alat, blon, blat, clon, clat)
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
                                val alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY)
                                val alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX)
                                component2D.contains(alon, alat)
                            }

                            ShapeField.DecodedTriangle.TYPE.LINE -> {
                                val alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY)
                                val alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX)
                                val blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY)
                                val blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX)
                                component2D.containsLine(alon, alat, blon, blat)
                            }

                            ShapeField.DecodedTriangle.TYPE.TRIANGLE -> {
                                val alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY)
                                val alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX)
                                val blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY)
                                val blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX)
                                val clat = GeoEncodingUtils.decodeLatitude(scratchTriangle.cY)
                                val clon = GeoEncodingUtils.decodeLongitude(scratchTriangle.cX)
                                component2D.containsTriangle(alon, alat, blon, blat, clon, clat)
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
                                val alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY)
                                val alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX)
                                component2D.withinPoint(alon, alat)
                            }

                            ShapeField.DecodedTriangle.TYPE.LINE -> {
                                val alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY)
                                val alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX)
                                val blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY)
                                val blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX)
                                component2D.withinLine(alon, alat, scratchTriangle.ab, blon, blat)
                            }

                            ShapeField.DecodedTriangle.TYPE.TRIANGLE -> {
                                val alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY)
                                val alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX)
                                val blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY)
                                val blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX)
                                val clat = GeoEncodingUtils.decodeLatitude(scratchTriangle.cY)
                                val clon = GeoEncodingUtils.decodeLongitude(scratchTriangle.cX)
                                component2D.withinTriangle(
                                    alon,
                                    alat,
                                    scratchTriangle.ab,
                                    blon,
                                    blat,
                                    scratchTriangle.bc,
                                    clon,
                                    clat,
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
