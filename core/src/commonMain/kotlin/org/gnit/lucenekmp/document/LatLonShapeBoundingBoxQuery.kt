package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoEncodingUtils.MAX_LON_ENCODED
import org.gnit.lucenekmp.geo.GeoEncodingUtils.MIN_LON_ENCODED
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLatitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLatitudeCeil
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLongitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLongitudeCeil
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.NumericUtils

/** Finds all previously indexed geo shapes that intersect the specified bounding box. */
internal class LatLonShapeBoundingBoxQuery(field: String, queryRelation: QueryRelation, private val rectangle: Rectangle) :
    SpatialQuery(field, queryRelation, rectangle) {

    override fun createComponent2D(vararg geometries: Geometry): Component2D {
        return LatLonGeometry.create(geometries[0] as Rectangle)
    }

    override val spatialVisitor: SpatialVisitor
        get() {
            val encodedRectangle =
                EncodedLatLonRectangle(rectangle.minLat, rectangle.maxLat, rectangle.minLon, rectangle.maxLon)
            return object : SpatialVisitor() {
                override fun relate(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                    return if (queryRelation == QueryRelation.INTERSECTS || queryRelation == QueryRelation.DISJOINT) {
                        encodedRectangle.intersectRangeBBox(
                            ShapeField.BYTES,
                            0,
                            minPackedValue,
                            3 * ShapeField.BYTES,
                            2 * ShapeField.BYTES,
                            maxPackedValue
                        )
                    } else {
                        encodedRectangle.relateRangeBBox(
                            ShapeField.BYTES,
                            0,
                            minPackedValue,
                            3 * ShapeField.BYTES,
                            2 * ShapeField.BYTES,
                            maxPackedValue
                        )
                    }
                }

                override fun intersects(): (ByteArray) -> Boolean {
                    val scratchTriangle = ShapeField.DecodedTriangle()
                    return { triangle ->
                        ShapeField.decodeTriangle(triangle, scratchTriangle)
                        when (scratchTriangle.type) {
                            ShapeField.DecodedTriangle.TYPE.POINT -> encodedRectangle.contains(
                                scratchTriangle.aX,
                                scratchTriangle.aY
                            )

                            ShapeField.DecodedTriangle.TYPE.LINE -> encodedRectangle.intersectsLine(
                                scratchTriangle.aX,
                                scratchTriangle.aY,
                                scratchTriangle.bX,
                                scratchTriangle.bY
                            )

                            ShapeField.DecodedTriangle.TYPE.TRIANGLE -> encodedRectangle.intersectsTriangle(
                                scratchTriangle.aX,
                                scratchTriangle.aY,
                                scratchTriangle.bX,
                                scratchTriangle.bY,
                                scratchTriangle.cX,
                                scratchTriangle.cY
                            )
                        }
                    }
                }

                override fun within(): (ByteArray) -> Boolean {
                    val scratchTriangle = ShapeField.DecodedTriangle()
                    return { triangle ->
                        ShapeField.decodeTriangle(triangle, scratchTriangle)
                        when (scratchTriangle.type) {
                            ShapeField.DecodedTriangle.TYPE.POINT -> encodedRectangle.contains(
                                scratchTriangle.aX,
                                scratchTriangle.aY
                            )

                            ShapeField.DecodedTriangle.TYPE.LINE -> encodedRectangle.containsLine(
                                scratchTriangle.aX,
                                scratchTriangle.aY,
                                scratchTriangle.bX,
                                scratchTriangle.bY
                            )

                            ShapeField.DecodedTriangle.TYPE.TRIANGLE -> encodedRectangle.containsTriangle(
                                scratchTriangle.aX,
                                scratchTriangle.aY,
                                scratchTriangle.bX,
                                scratchTriangle.bY,
                                scratchTriangle.cX,
                                scratchTriangle.cY
                            )
                        }
                    }
                }

                override fun contains(): (ByteArray) -> Component2D.WithinRelation {
                    require(!encodedRectangle.crossesDateline()) {
                        "withinTriangle is not supported for rectangles crossing the date line"
                    }
                    val scratchTriangle = ShapeField.DecodedTriangle()
                    return { triangle ->
                        ShapeField.decodeTriangle(triangle, scratchTriangle)
                        when (scratchTriangle.type) {
                            ShapeField.DecodedTriangle.TYPE.POINT -> {
                                if (encodedRectangle.contains(scratchTriangle.aX, scratchTriangle.aY)) {
                                    Component2D.WithinRelation.NOTWITHIN
                                } else {
                                    Component2D.WithinRelation.DISJOINT
                                }
                            }

                            ShapeField.DecodedTriangle.TYPE.LINE -> encodedRectangle.withinLine(
                                scratchTriangle.aX,
                                scratchTriangle.aY,
                                scratchTriangle.ab,
                                scratchTriangle.bX,
                                scratchTriangle.bY
                            )

                            ShapeField.DecodedTriangle.TYPE.TRIANGLE -> encodedRectangle.withinTriangle(
                                scratchTriangle.aX,
                                scratchTriangle.aY,
                                scratchTriangle.ab,
                                scratchTriangle.bX,
                                scratchTriangle.bY,
                                scratchTriangle.bc,
                                scratchTriangle.cX,
                                scratchTriangle.cY,
                                scratchTriangle.ca
                            )
                        }
                    }
                }
            }
        }

    override fun equalsTo(o: Any?): Boolean {
        return super.equalsTo(o) && rectangle == (o as LatLonShapeBoundingBoxQuery).rectangle
    }

    override fun hashCode(): Int {
        var hash = super.hashCode()
        hash = 31 * hash + rectangle.hashCode()
        return hash
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        sb.append(this::class.simpleName)
        sb.append(':')
        if (this.field != field) {
            sb.append(" field=")
            sb.append(this.field)
            sb.append(':')
        }
        sb.append(rectangle.toString())
        return sb.toString()
    }

    private class EncodedLatLonRectangle(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) :
        EncodedRectangle(
            encodeLongitudeCeil(validateMinLon(minLon, maxLon)),
            encodeLongitude(maxLon),
            encodeLatitudeCeil(minLat),
            encodeLatitude(maxLat),
            validateMinLon(minLon, maxLon) > maxLon
        ) {

        private val bbox: ByteArray = ByteArray(4 * Int.SIZE_BYTES)
        private val west: ByteArray?

        init {
            if (wrapsCoordinateSystem) {
                west = ByteArray(4 * Int.SIZE_BYTES)
                encode(MIN_LON_ENCODED, maxX, minY, maxY, west)
                encode(minX, MAX_LON_ENCODED, minY, maxY, bbox)
            } else {
                west = null
                encode(minX, maxX, minY, maxY, bbox)
            }
        }

        fun crossesDateline(): Boolean {
            return wrapsCoordinateSystem
        }

        fun relateRangeBBox(
            minXOffset: Int,
            minYOffset: Int,
            minTriangle: ByteArray,
            maxXOffset: Int,
            maxYOffset: Int,
            maxTriangle: ByteArray
        ): Relation {
            val eastRelation =
                compareBBoxToRangeBBox(bbox, minXOffset, minYOffset, minTriangle, maxXOffset, maxYOffset, maxTriangle)
            if (crossesDateline() && eastRelation == Relation.CELL_OUTSIDE_QUERY) {
                return compareBBoxToRangeBBox(
                    west!!,
                    minXOffset,
                    minYOffset,
                    minTriangle,
                    maxXOffset,
                    maxYOffset,
                    maxTriangle
                )
            }
            return eastRelation
        }

        fun intersectRangeBBox(
            minXOffset: Int,
            minYOffset: Int,
            minTriangle: ByteArray,
            maxXOffset: Int,
            maxYOffset: Int,
            maxTriangle: ByteArray
        ): Relation {
            val eastRelation =
                intersectBBoxWithRangeBBox(
                    bbox,
                    minXOffset,
                    minYOffset,
                    minTriangle,
                    maxXOffset,
                    maxYOffset,
                    maxTriangle
                )
            if (crossesDateline() && eastRelation == Relation.CELL_OUTSIDE_QUERY) {
                return intersectBBoxWithRangeBBox(
                    west!!,
                    minXOffset,
                    minYOffset,
                    minTriangle,
                    maxXOffset,
                    maxYOffset,
                    maxTriangle
                )
            }
            return eastRelation
        }

        private fun compareBBoxToRangeBBox(
            bbox: ByteArray,
            minXOffset: Int,
            minYOffset: Int,
            minTriangle: ByteArray,
            maxXOffset: Int,
            maxYOffset: Int,
            maxTriangle: ByteArray
        ): Relation {
            if (disjoint(bbox, minXOffset, minYOffset, minTriangle, maxXOffset, maxYOffset, maxTriangle)) {
                return Relation.CELL_OUTSIDE_QUERY
            }

            if (ArrayUtil.compareUnsigned4(minTriangle, minXOffset, bbox, Int.SIZE_BYTES) >= 0 &&
                ArrayUtil.compareUnsigned4(maxTriangle, maxXOffset, bbox, 3 * Int.SIZE_BYTES) <= 0 &&
                ArrayUtil.compareUnsigned4(minTriangle, minYOffset, bbox, 0) >= 0 &&
                ArrayUtil.compareUnsigned4(maxTriangle, maxYOffset, bbox, 2 * Int.SIZE_BYTES) <= 0
            ) {
                return Relation.CELL_INSIDE_QUERY
            }

            return Relation.CELL_CROSSES_QUERY
        }

        private fun intersectBBoxWithRangeBBox(
            bbox: ByteArray,
            minXOffset: Int,
            minYOffset: Int,
            minTriangle: ByteArray,
            maxXOffset: Int,
            maxYOffset: Int,
            maxTriangle: ByteArray
        ): Relation {
            if (disjoint(bbox, minXOffset, minYOffset, minTriangle, maxXOffset, maxYOffset, maxTriangle)) {
                return Relation.CELL_OUTSIDE_QUERY
            }

            if (ArrayUtil.compareUnsigned4(minTriangle, minXOffset, bbox, Int.SIZE_BYTES) >= 0 &&
                ArrayUtil.compareUnsigned4(minTriangle, minYOffset, bbox, 0) >= 0
            ) {
                if (ArrayUtil.compareUnsigned4(maxTriangle, minXOffset, bbox, 3 * Int.SIZE_BYTES) <= 0 &&
                    ArrayUtil.compareUnsigned4(maxTriangle, maxYOffset, bbox, 2 * Int.SIZE_BYTES) <= 0
                ) {
                    return Relation.CELL_INSIDE_QUERY
                }
                if (ArrayUtil.compareUnsigned4(maxTriangle, maxXOffset, bbox, 3 * Int.SIZE_BYTES) <= 0 &&
                    ArrayUtil.compareUnsigned4(maxTriangle, minYOffset, bbox, 2 * Int.SIZE_BYTES) <= 0
                ) {
                    return Relation.CELL_INSIDE_QUERY
                }
            }

            if (ArrayUtil.compareUnsigned4(maxTriangle, maxXOffset, bbox, 3 * Int.SIZE_BYTES) <= 0 &&
                ArrayUtil.compareUnsigned4(maxTriangle, maxYOffset, bbox, 2 * Int.SIZE_BYTES) <= 0
            ) {
                if (ArrayUtil.compareUnsigned4(minTriangle, minXOffset, bbox, Int.SIZE_BYTES) >= 0 &&
                    ArrayUtil.compareUnsigned4(minTriangle, maxYOffset, bbox, 0) >= 0
                ) {
                    return Relation.CELL_INSIDE_QUERY
                }
                if (ArrayUtil.compareUnsigned4(minTriangle, maxXOffset, bbox, Int.SIZE_BYTES) >= 0 &&
                    ArrayUtil.compareUnsigned4(minTriangle, minYOffset, bbox, 0) >= 0
                ) {
                    return Relation.CELL_INSIDE_QUERY
                }
            }

            return Relation.CELL_CROSSES_QUERY
        }

        private fun disjoint(
            bbox: ByteArray,
            minXOffset: Int,
            minYOffset: Int,
            minTriangle: ByteArray,
            maxXOffset: Int,
            maxYOffset: Int,
            maxTriangle: ByteArray
        ): Boolean {
            return ArrayUtil.compareUnsigned4(minTriangle, minXOffset, bbox, 3 * Int.SIZE_BYTES) > 0 ||
                    ArrayUtil.compareUnsigned4(maxTriangle, maxXOffset, bbox, Int.SIZE_BYTES) < 0 ||
                    ArrayUtil.compareUnsigned4(minTriangle, minYOffset, bbox, 2 * Int.SIZE_BYTES) > 0 ||
                    ArrayUtil.compareUnsigned4(maxTriangle, maxYOffset, bbox, 0) < 0
        }

        companion object {
            private fun validateMinLon(minLon: Double, maxLon: Double): Double {
                if (minLon == 180.0 && minLon > maxLon) {
                    return -180.0
                }
                return minLon
            }

            private fun encode(minX: Int, maxX: Int, minY: Int, maxY: Int, b: ByteArray) {
                NumericUtils.intToSortableBytes(minY, b, 0)
                NumericUtils.intToSortableBytes(minX, b, Int.SIZE_BYTES)
                NumericUtils.intToSortableBytes(maxY, b, 2 * Int.SIZE_BYTES)
                NumericUtils.intToSortableBytes(maxX, b, 3 * Int.SIZE_BYTES)
            }
        }
    }
}
