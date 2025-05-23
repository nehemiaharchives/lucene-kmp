package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.StrictMath


/**
 * 2D Geometry object that supports spatial relationships with bounding boxes, triangles and points.
 *
 * @lucene.internal
 */
interface Component2D {
    /** min X value for the component *  */
    val minX: Double

    /** max X value for the component *  */
    val maxX: Double

    /** min Y value for the component *  */
    val minY: Double

    /** max Y value for the component *  */
    val maxY: Double

    /** relates this component2D with a point *  */
    fun contains(x: Double, y: Double): Boolean

    /** relates this component2D with a bounding box *  */
    fun relate(minX: Double, maxX: Double, minY: Double, maxY: Double): PointValues.Relation

    /** return true if this component2D intersects the provided line *  */
    fun intersectsLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double
    ): Boolean

    /** return true if this component2D intersects the provided triangle *  */
    fun intersectsTriangle(
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
    ): Boolean

    /** return true if this component2D contains the provided line *  */
    fun containsLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double
    ): Boolean

    /** return true if this component2D contains the provided triangle *  */
    fun containsTriangle(
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
    ): Boolean

    /**
     * Used by withinTriangle to check the within relationship between a triangle and the query shape
     * (e.g. if the query shape is within the triangle).
     */
    enum class WithinRelation {
        /**
         * If the shape is a candidate for within. Typically this is return if the query shape is fully
         * inside the triangle or if the query shape intersects only edges that do not belong to the
         * original shape.
         */
        CANDIDATE,

        /**
         * The query shape intersects an edge that does belong to the original shape or any point of the
         * triangle is inside the shape.
         */
        NOTWITHIN,

        /** The query shape is disjoint with the triangle.  */
        DISJOINT
    }

    /** Compute the within relation of this component2D with a point *  */
    fun withinPoint(x: Double, y: Double): WithinRelation

    /** Compute the within relation of this component2D with a line *  */
    fun withinLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        ab: Boolean,
        bX: Double,
        bY: Double
    ): WithinRelation

    /** Compute the within relation of this component2D with a triangle *  */
    fun withinTriangle(
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
    ): WithinRelation

    /** return true if this component2D intersects the provided line *  */
    fun intersectsLine(aX: Double, aY: Double, bX: Double, bY: Double): Boolean {
        val minY: Double = StrictMath.min(aY, bY)
        val minX: Double = StrictMath.min(aX, bX)
        val maxY: Double = StrictMath.max(aY, bY)
        val maxX: Double = StrictMath.max(aX, bX)
        return intersectsLine(minX, maxX, minY, maxY, aX, aY, bX, bY)
    }

    /** return true if this component2D intersects the provided triangle *  */
    fun intersectsTriangle(
        aX: Double, aY: Double, bX: Double, bY: Double, cX: Double, cY: Double
    ): Boolean {
        val minY: Double = StrictMath.min(StrictMath.min(aY, bY), cY)
        val minX: Double = StrictMath.min(StrictMath.min(aX, bX), cX)
        val maxY: Double = StrictMath.max(StrictMath.max(aY, bY), cY)
        val maxX: Double = StrictMath.max(StrictMath.max(aX, bX), cX)
        return intersectsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY)
    }

    /** return true if this component2D contains the provided line *  */
    fun containsLine(aX: Double, aY: Double, bX: Double, bY: Double): Boolean {
        val minY: Double = StrictMath.min(aY, bY)
        val minX: Double = StrictMath.min(aX, bX)
        val maxY: Double = StrictMath.max(aY, bY)
        val maxX: Double = StrictMath.max(aX, bX)
        return containsLine(minX, maxX, minY, maxY, aX, aY, bX, bY)
    }

    /** return true if this component2D contains the provided triangle *  */
    fun containsTriangle(
        aX: Double, aY: Double, bX: Double, bY: Double, cX: Double, cY: Double
    ): Boolean {
        val minY: Double = StrictMath.min(StrictMath.min(aY, bY), cY)
        val minX: Double = StrictMath.min(StrictMath.min(aX, bX), cX)
        val maxY: Double = StrictMath.max(StrictMath.max(aY, bY), cY)
        val maxX: Double = StrictMath.max(StrictMath.max(aX, bX), cX)
        return containsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY)
    }

    /** Compute the within relation of this component2D with a triangle *  */
    fun withinLine(aX: Double, aY: Double, ab: Boolean, bX: Double, bY: Double): WithinRelation {
        val minY: Double = StrictMath.min(aY, bY)
        val minX: Double = StrictMath.min(aX, bX)
        val maxY: Double = StrictMath.max(aY, bY)
        val maxX: Double = StrictMath.max(aX, bX)
        return withinLine(minX, maxX, minY, maxY, aX, aY, ab, bX, bY)
    }

    /** Compute the within relation of this component2D with a triangle *  */
    fun withinTriangle(
        aX: Double,
        aY: Double,
        ab: Boolean,
        bX: Double,
        bY: Double,
        bc: Boolean,
        cX: Double,
        cY: Double,
        ca: Boolean
    ): WithinRelation {
        val minY: Double = StrictMath.min(StrictMath.min(aY, bY), cY)
        val minX: Double = StrictMath.min(StrictMath.min(aX, bX), cX)
        val maxY: Double = StrictMath.max(StrictMath.max(aY, bY), cY)
        val maxX: Double = StrictMath.max(StrictMath.max(aX, bX), cX)
        return withinTriangle(minX, maxX, minY, maxY, aX, aY, ab, bX, bY, bc, cX, cY, ca)
    }

    companion object {
        /** Compute whether the bounding boxes are disjoint *  */
        fun disjoint(
            minX1: Double,
            maxX1: Double,
            minY1: Double,
            maxY1: Double,
            minX2: Double,
            maxX2: Double,
            minY2: Double,
            maxY2: Double
        ): Boolean {
            return (maxY1 < minY2 || minY1 > maxY2 || maxX1 < minX2 || minX1 > maxX2)
        }

        /** Compute whether the first bounding box 1 is within the second bounding box *  */
        fun within(
            minX1: Double,
            maxX1: Double,
            minY1: Double,
            maxY1: Double,
            minX2: Double,
            maxX2: Double,
            minY2: Double,
            maxY2: Double
        ): Boolean {
            return (minY2 <= minY1 && maxY2 >= maxY1 && minX2 <= minX1 && maxX2 >= maxX1)
        }

        /** returns true if rectangle (defined by minX, maxX, minY, maxY) contains the X Y point  */
        fun containsPoint(
            x: Double,
            y: Double,
            minX: Double,
            maxX: Double,
            minY: Double,
            maxY: Double
        ): Boolean {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
        }

        /** Compute whether the given x, y point is in a triangle; uses the winding order method  */
        fun pointInTriangle(
            minX: Double,
            maxX: Double,
            minY: Double,
            maxY: Double,
            x: Double,
            y: Double,
            aX: Double,
            aY: Double,
            bX: Double,
            bY: Double,
            cX: Double,
            cY: Double
        ): Boolean {
            // check the bounding box because if the triangle is degenerated, e.g points and lines, we need
            // to filter out
            // coplanar points that are not part of the triangle.
            if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                val a: Int = GeoUtils.orient(x, y, aX, aY, bX, bY)
                val b: Int = GeoUtils.orient(x, y, bX, bY, cX, cY)
                if (a == 0 || b == 0 || a < 0 == b < 0) {
                    val c: Int = GeoUtils.orient(x, y, cX, cY, aX, aY)
                    return c == 0 || (c < 0 == (b < 0 || a < 0))
                }
                return false
            } else {
                return false
            }
        }
    }
}
