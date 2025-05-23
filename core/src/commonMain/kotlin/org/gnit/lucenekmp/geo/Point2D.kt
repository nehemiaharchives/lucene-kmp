package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues

/** 2D point implementation containing geo spatial logic.  */
internal class Point2D private constructor(val x: Double, val y: Double) : Component2D {
    override val minX: Double get() = x
    override val maxX: Double get() = x
    override val minY: Double get() = y
    override val maxY: Double get() = y

    override fun contains(x: Double, y: Double): Boolean {
        return x == this.x && y == this.y
    }

    override fun relate(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double
    ): PointValues.Relation {
        if (Component2D.containsPoint(this.x, this.y, minX, maxX, minY, maxY)) {
            return PointValues.Relation.CELL_CROSSES_QUERY
        }
        return PointValues.Relation.CELL_OUTSIDE_QUERY
    }

    override fun intersectsLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double
    ): Boolean {
        return Component2D.containsPoint(this.x, this.y, minX, maxX, minY, maxY)
                && GeoUtils.orient(aX, aY, bX, bY, this.x, this.y) == 0
    }

    override fun intersectsTriangle(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double,
        cX: Double,
        cY: Double
    ): Boolean {
        return Component2D.pointInTriangle(
            minX, maxX, minY, maxY,
            this.x,
            this.y, aX, aY, bX, bY, cX, cY
        )
    }

    override fun containsLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double
    ): Boolean {
        return false
    }

    override fun containsTriangle(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double,
        cX: Double,
        cY: Double
    ): Boolean {
        return false
    }

    override fun withinPoint(x: Double, y: Double): Component2D.WithinRelation {
        return if (contains(
                x,
                y
            )
        ) Component2D.WithinRelation.CANDIDATE else Component2D.WithinRelation.DISJOINT
    }

    override fun withinLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        ab: Boolean,
        bX: Double,
        bY: Double
    ): Component2D.WithinRelation {
        // can be improved?
        return if (intersectsLine(minX, maxX, minY, maxY, aX, aY, bX, bY))
            Component2D.WithinRelation.CANDIDATE
        else
            Component2D.WithinRelation.DISJOINT
    }

    override fun withinTriangle(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        ab: Boolean,
        bX: Double,
        bY: Double,
        bc: Boolean,
        cX: Double,
        cY: Double,
        ca: Boolean
    ): Component2D.WithinRelation {
        if (Component2D.pointInTriangle(
                minX, maxX, minY, maxY,
                this.x,
                this.y, aX, aY, bX, bY, cX, cY
            )
        ) {
            return Component2D.WithinRelation.CANDIDATE
        }
        return Component2D.WithinRelation.DISJOINT
    }

    companion object {
        /** create a Point2D component tree from a LatLon point  */
        fun create(point: Point): Component2D {
            // Points behave as rectangles
            val qLat: Double =
                if (point.lat == GeoUtils.MAX_LAT_INCL)
                    point.lat
                else
                    GeoEncodingUtils.decodeLatitude(
                        GeoEncodingUtils.encodeLatitudeCeil(
                            point.lat
                        )
                    )
            val qLon: Double =
                if (point.lon == GeoUtils.MAX_LON_INCL)
                    point.lon
                else
                    GeoEncodingUtils.decodeLongitude(
                        GeoEncodingUtils.encodeLongitudeCeil(point.lon)
                    )
            return Point2D(qLon, qLat)
        }

        /** create a Point2D component tree from a XY point  */
        fun create(xyPoint: XYPoint): Component2D {
            return Point2D(xyPoint.x.toDouble(), xyPoint.y.toDouble())
        }
    }
}
