package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.StrictMath
import org.gnit.lucenekmp.util.SloppyMath

/** 2D circle implementation containing spatial logic.  */
internal class Circle2D private constructor(private val calculator: DistanceCalculator) :
    Component2D {
    override val minX: Double
        get() = calculator.minX

    override val maxX: Double
        get() = calculator.maxX

    override val minY: Double
        get() = calculator.minY

    override val maxY: Double
        get() = calculator.maxY

    override fun contains(x: Double, y: Double): Boolean {
        return calculator.contains(x, y)
    }

    override fun relate(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double
    ): PointValues.Relation {
        if (calculator.disjoint(minX, maxX, minY, maxY)) {
            return PointValues.Relation.CELL_OUTSIDE_QUERY
        }
        if (calculator.within(minX, maxX, minY, maxY)) {
            return PointValues.Relation.CELL_CROSSES_QUERY
        }
        return calculator.relate(minX, maxX, minY, maxY)
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
        if (calculator.disjoint(minX, maxX, minY, maxY)) {
            return false
        }
        return contains(aX, aY) || contains(bX, bY) || calculator.intersectsLine(aX, aY, bX, bY)
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
        if (calculator.disjoint(minX, maxX, minY, maxY)) {
            return false
        }
        return contains(aX, aY)
                || contains(bX, bY)
                || contains(cX, cY)
                || Component2D.pointInTriangle(
            minX, maxX, minY, maxY, calculator.x, calculator.y, aX, aY, bX, bY, cX, cY
        )
                || calculator.intersectsLine(aX, aY, bX, bY)
                || calculator.intersectsLine(bX, bY, cX, cY)
                || calculator.intersectsLine(cX, cY, aX, aY)
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
        if (calculator.disjoint(minX, maxX, minY, maxY)) {
            return false
        }
        return contains(aX, aY) && contains(bX, bY)
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
        if (calculator.disjoint(minX, maxX, minY, maxY)) {
            return false
        }
        return contains(aX, aY) && contains(bX, bY) && contains(cX, cY)
    }

    override fun withinPoint(x: Double, y: Double): Component2D.WithinRelation {
        return if (contains(
                x,
                y
            )
        ) Component2D.WithinRelation.NOTWITHIN else Component2D.WithinRelation.DISJOINT
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
        if (calculator.disjoint(minX, maxX, minY, maxY)) {
            return Component2D.WithinRelation.DISJOINT
        }
        if (contains(aX, aY) || contains(bX, bY)) {
            return Component2D.WithinRelation.NOTWITHIN
        }
        if (ab == true && calculator.intersectsLine(aX, aY, bX, bY)) {
            return Component2D.WithinRelation.NOTWITHIN
        }
        return Component2D.WithinRelation.DISJOINT
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
        if (calculator.disjoint(minX, maxX, minY, maxY)) {
            return Component2D.WithinRelation.DISJOINT
        }

        // if any of the points is inside the circle then we cannot be within this
        // indexed shape
        if (contains(aX, aY) || contains(bX, bY) || contains(cX, cY)) {
            return Component2D.WithinRelation.NOTWITHIN
        }

        // we only check edges that belong to the original polygon. If we intersect any of them, then
        // we are not within.
        if (ab == true && calculator.intersectsLine(aX, aY, bX, bY)) {
            return Component2D.WithinRelation.NOTWITHIN
        }
        if (bc == true && calculator.intersectsLine(bX, bY, cX, cY)) {
            return Component2D.WithinRelation.NOTWITHIN
        }
        if (ca == true && calculator.intersectsLine(cX, cY, aX, aY)) {
            return Component2D.WithinRelation.NOTWITHIN
        }

        // check if center is within the triangle. This is the only check that returns this circle as a
        // candidate but that is ol
        // is fine as the center must be inside to be one of the triangles.
        if (Component2D.pointInTriangle(
                minX, maxX, minY, maxY, calculator.x, calculator.y, aX, aY, bX, bY, cX, cY
            )
            == true
        ) {
            return Component2D.WithinRelation.CANDIDATE
        }
        return Component2D.WithinRelation.DISJOINT
    }

    private interface DistanceCalculator {
        /** check if the point is within a distance  */
        fun contains(x: Double, y: Double): Boolean

        /** check if the line is within a distance  */
        fun intersectsLine(aX: Double, aY: Double, bX: Double, bY: Double): Boolean

        /** Relates this calculator to the provided bounding box  */
        fun relate(
            minX: Double,
            maxX: Double,
            minY: Double,
            maxY: Double
        ): PointValues.Relation

        /** check if the bounding box is disjoint with this calculator bounding box  */
        fun disjoint(minX: Double, maxX: Double, minY: Double, maxY: Double): Boolean

        /** check if the bounding box is contains this calculator bounding box  */
        fun within(minX: Double, maxX: Double, minY: Double, maxY: Double): Boolean

        /** get min X of this calculator  */
        val minX: Double

        /** get max X of this calculator  */
        val maxX: Double

        /** get min Y of this calculator  */
        val minY: Double

        /** get max Y of this calculator  */
        val maxY: Double

        /** get center X  */
        val x: Double

        /** get center Y  */
        val y: Double
    }

    private class CartesianDistance(centerX: Float, centerY: Float, radius: Float) : DistanceCalculator {
        private val centerX: Double = centerX.toDouble()
        private val centerY: Double = centerY.toDouble()
        // product performed with doubles
        private val radiusSquared: Double = radius.toDouble() * radius
        private val rectangle: XYRectangle = XYRectangle.fromPointDistance(centerX, centerY, radius)

        override fun relate(
            minX: Double,
            maxX: Double,
            minY: Double,
            maxY: Double
        ): PointValues.Relation {
            if (Component2D.containsPoint(centerX, centerY, minX, maxX, minY, maxY)) {
                if (contains(minX, minY)
                    && contains(maxX, minY)
                    && contains(maxX, maxY)
                    && contains(minX, maxY)
                ) {
                    // we are fully enclosed, collect everything within this subtree
                    return PointValues.Relation.CELL_INSIDE_QUERY
                }
            } else {
                // circle not fully inside, compute closest distance
                var sumOfSquaredDiffs = 0.0
                if (centerX < minX) {
                    val diff = minX - centerX
                    sumOfSquaredDiffs += diff * diff
                } else if (centerX > maxX) {
                    val diff = maxX - centerX
                    sumOfSquaredDiffs += diff * diff
                }
                if (centerY < minY) {
                    val diff = minY - centerY
                    sumOfSquaredDiffs += diff * diff
                } else if (centerY > maxY) {
                    val diff = maxY - centerY
                    sumOfSquaredDiffs += diff * diff
                }
                if (sumOfSquaredDiffs > radiusSquared) {
                    // disjoint
                    return PointValues.Relation.CELL_OUTSIDE_QUERY
                }
            }
            return PointValues.Relation.CELL_CROSSES_QUERY
        }

        override fun contains(x: Double, y: Double): Boolean {
            if (Component2D.containsPoint(
                    x,
                    y,
                    rectangle.minX.toDouble(),
                    rectangle.maxX.toDouble(),
                    rectangle.minY.toDouble(),
                    rectangle.maxY.toDouble()
                )
            ) {
                val diffX = x - this.centerX
                val diffY = y - this.centerY
                return diffX * diffX + diffY * diffY <= radiusSquared
            }
            return false
        }

        override fun intersectsLine(aX: Double, aY: Double, bX: Double, bY: Double): Boolean {
            return intersectsLine(centerX, centerY, aX, aY, bX, bY, this)
        }

        override fun disjoint(minX: Double, maxX: Double, minY: Double, maxY: Double): Boolean {
            return Component2D.disjoint(
                rectangle.minX.toDouble(),
                rectangle.maxX.toDouble(),
                rectangle.minY.toDouble(),
                rectangle.maxY.toDouble(),
                minX,
                maxX,
                minY,
                maxY
            )
        }

        override fun within(minX: Double, maxX: Double, minY: Double, maxY: Double): Boolean {
            return Component2D.within(
                rectangle.minX.toDouble(),
                rectangle.maxX.toDouble(),
                rectangle.minY.toDouble(),
                rectangle.maxY.toDouble(),
                minX,
                maxX,
                minY,
                maxY
            )
        }

        override val minX: Double get() = rectangle.minX.toDouble()
        override val maxX: Double get() = rectangle.maxX.toDouble()
        override val minY: Double get() = rectangle.minY.toDouble()
        override val maxY: Double get() = rectangle.maxY.toDouble()
        override val x: Double get() = centerX
        override val y: Double get() = centerY
    }

    private class HaversinDistance(val centerLon: Double, val centerLat: Double, radius: Double) : DistanceCalculator {
        val sortKey: Double = GeoUtils.distanceQuerySortKey(radius)
        val axisLat: Double = Rectangle.axisLat(centerLat, radius)
        val rectangle: Rectangle = Rectangle.fromPointDistance(centerLat, centerLon, radius)
        val crossesDateline: Boolean = rectangle.minLon > rectangle.maxLon

        override fun relate(
            minX: Double,
            maxX: Double,
            minY: Double,
            maxY: Double
        ): PointValues.Relation {
            return GeoUtils.relate(minY, maxY, minX, maxX, centerLat, centerLon, sortKey, axisLat)
        }

        override fun contains(x: Double, y: Double): Boolean {
            if (crossesDateline) {
                if (Component2D.containsPoint(
                        x,
                        y,
                        rectangle.minLon,
                        GeoUtils.MAX_LON_INCL,
                        rectangle.minLat,
                        rectangle.maxLat
                    )
                    || Component2D.containsPoint(
                        x,
                        y,
                        GeoUtils.MIN_LON_INCL,
                        rectangle.maxLon,
                        rectangle.minLat,
                        rectangle.maxLat
                    )
                ) {
                    return SloppyMath.haversinSortKey(
                        y,
                        x,
                        this.centerLat,
                        this.centerLon
                    ) <= sortKey
                }
            } else {
                if (Component2D.containsPoint(
                        x, y, rectangle.minLon, rectangle.maxLon, rectangle.minLat, rectangle.maxLat
                    )
                ) {
                    return SloppyMath.haversinSortKey(
                        y,
                        x,
                        this.centerLat,
                        this.centerLon
                    ) <= sortKey
                }
            }
            return false
        }

        override fun intersectsLine(aX: Double, aY: Double, bX: Double, bY: Double): Boolean {
            if (intersectsLine(centerLon, centerLat, aX, aY, bX, bY, this)) {
                return true
            }
            if (crossesDateline) {
                val newCenterLon = if (centerLon > 0) centerLon - 360 else centerLon + 360
                return intersectsLine(newCenterLon, centerLat, aX, aY, bX, bY, this)
            }
            return false
        }

        override fun disjoint(minX: Double, maxX: Double, minY: Double, maxY: Double): Boolean {
            if (crossesDateline) {
                return Component2D.disjoint(
                    rectangle.minLon,
                    GeoUtils.MAX_LON_INCL,
                    rectangle.minLat,
                    rectangle.maxLat,
                    minX,
                    maxX,
                    minY,
                    maxY
                )
                        && Component2D.disjoint(
                    GeoUtils.MIN_LON_INCL,
                    rectangle.maxLon,
                    rectangle.minLat,
                    rectangle.maxLat,
                    minX,
                    maxX,
                    minY,
                    maxY
                )
            } else {
                return Component2D.disjoint(
                    rectangle.minLon,
                    rectangle.maxLon,
                    rectangle.minLat,
                    rectangle.maxLat,
                    minX,
                    maxX,
                    minY,
                    maxY
                )
            }
        }

        override fun within(minX: Double, maxX: Double, minY: Double, maxY: Double): Boolean {
            if (crossesDateline) {
                return Component2D.within(
                    rectangle.minLon,
                    GeoUtils.MAX_LON_INCL,
                    rectangle.minLat,
                    rectangle.maxLat,
                    minX,
                    maxX,
                    minY,
                    maxY
                )
                        || Component2D.within(
                    GeoUtils.MIN_LON_INCL,
                    rectangle.maxLon,
                    rectangle.minLat,
                    rectangle.maxLat,
                    minX,
                    maxX,
                    minY,
                    maxY
                )
            } else {
                return Component2D.within(
                    rectangle.minLon,
                    rectangle.maxLon,
                    rectangle.minLat,
                    rectangle.maxLat,
                    minX,
                    maxX,
                    minY,
                    maxY
                )
            }
        }

        override val minX: Double
            get() = if (crossesDateline) {
                // Component2D does not support boxes that crosses the dateline
                GeoUtils.MIN_LON_INCL
            } else {
                rectangle.minLon
            }

        override val maxX: Double
            get() = if (crossesDateline) {
                // Component2D does not support boxes that crosses the dateline
                GeoUtils.MAX_LON_INCL
            } else {
                rectangle.maxLon
            }

        override val minY: Double
            get() = rectangle.minLat

        override val maxY: Double
            get() = rectangle.maxLat

        override val x: Double
            get() = centerLon

        override val y: Double
            get() = centerLat
    }

    companion object {
        private fun intersectsLine(
            centerX: Double,
            centerY: Double,
            aX: Double,
            aY: Double,
            bX: Double,
            bY: Double,
            calculator: DistanceCalculator
        ): Boolean {
            // Algorithm based on this thread :
            // https://stackoverflow.com/questions/3120357/get-closest-point-to-a-line
            val vectorAPX = centerX - aX
            val vectorAPY = centerY - aY

            val vectorABX = bX - aX
            val vectorABY = bY - aY

            val magnitudeAB = vectorABX * vectorABX + vectorABY * vectorABY
            val dotProduct = vectorAPX * vectorABX + vectorAPY * vectorABY

            val distance = dotProduct / magnitudeAB

            if (distance < 0 || distance > 1) {
                return false
            }

            val pX = aX + vectorABX * distance
            val pY = aY + vectorABY * distance

            val minX: Double = StrictMath.min(aX, bX)
            val minY: Double = StrictMath.min(aY, bY)
            val maxX: Double = StrictMath.max(aX, bX)
            val maxY: Double = StrictMath.max(aY, bY)

            if (pX >= minX && pX <= maxX && pY >= minY && pY <= maxY) {
                return calculator.contains(pX, pY)
            }
            return false
        }

        /**
         * Builds a XYCircle2D from XYCircle. Distance calculations are performed using cartesian
         * distance.
         */
        fun create(circle: XYCircle): Component2D {
            val calculator: DistanceCalculator =
                CartesianDistance(circle.x, circle.y, circle.radius)
            return Circle2D(calculator)
        }

        /** Builds a Circle2D from Circle. Distance calculations are performed using haversin distance.  */
        fun create(circle: Circle): Component2D {
            val calculator: DistanceCalculator =
                HaversinDistance(circle.lon, circle.lat, circle.radius)
            return Circle2D(calculator)
        }
    }
}
