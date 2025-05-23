package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.compare
import kotlin.math.max
import kotlin.math.min

/** 2D rectangle implementation containing cartesian spatial logic.  */
internal class Rectangle2D private constructor(
    override val minX: Double,
    override val maxX: Double,
    override val minY: Double,
    override val maxY: Double
) : Component2D {

    override fun contains(x: Double, y: Double): Boolean {
        return Component2D.containsPoint(x, y, this.minX, this.maxX, this.minY, this.maxY)
    }

    override fun relate(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double
    ): PointValues.Relation {
        if (Component2D.disjoint(
                this.minX,
                this.maxX,
                this.minY,
                this.maxY,
                minX,
                maxX,
                minY,
                maxY
            )
        ) {
            return PointValues.Relation.CELL_OUTSIDE_QUERY
        }
        if (Component2D.within(
                minX,
                maxX,
                minY,
                maxY,
                this.minX,
                this.maxX,
                this.minY,
                this.maxY
            )
        ) {
            return PointValues.Relation.CELL_INSIDE_QUERY
        }
        return PointValues.Relation.CELL_CROSSES_QUERY
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
        if (Component2D.disjoint(
                this.minX,
                this.maxX,
                this.minY,
                this.maxY,
                minX,
                maxX,
                minY,
                maxY
            )
        ) {
            return false
        }
        return contains(aX, aY) || contains(bX, bY) || edgesIntersect(aX, aY, bX, bY)
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
        if (Component2D.disjoint(
                this.minX,
                this.maxX,
                this.minY,
                this.maxY,
                minX,
                maxX,
                minY,
                maxY
            )
        ) {
            return false
        }
        return contains(aX, aY)
                || contains(bX, bY)
                || contains(cX, cY)
                || Component2D.pointInTriangle(
            minX, maxX, minY, maxY, this.minX, this.minY, aX, aY, bX, bY, cX, cY
        )
                || edgesIntersect(aX, aY, bX, bY)
                || edgesIntersect(bX, bY, cX, cY)
                || edgesIntersect(cX, cY, aX, aY)
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
        return Component2D.within(
            minX,
            maxX,
            minY,
            maxY,
            this.minX,
            this.maxX,
            this.minY,
            this.maxY
        )
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
        return Component2D.within(
            minX,
            maxX,
            minY,
            maxY,
            this.minX,
            this.maxX,
            this.minY,
            this.maxY
        )
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
        if (Component2D.disjoint(
                this.minX,
                this.maxX,
                this.minY,
                this.maxY,
                minX,
                maxX,
                minY,
                maxY
            )
        ) {
            return Component2D.WithinRelation.DISJOINT
        }
        if (contains(aX, aY) || contains(bX, bY)) {
            return Component2D.WithinRelation.NOTWITHIN
        }
        if (ab == true && edgesIntersect(aX, aY, bX, bY)) {
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
        // Bounding boxes disjoint
        if (Component2D.disjoint(
                this.minX,
                this.maxX,
                this.minY,
                this.maxY,
                minX,
                maxX,
                minY,
                maxY
            )
        ) {
            return Component2D.WithinRelation.DISJOINT
        }

        // Points belong to the shape so if points are inside the rectangle then it cannot be within.
        if (contains(aX, aY) || contains(bX, bY) || contains(cX, cY)) {
            return Component2D.WithinRelation.NOTWITHIN
        }
        // If any of the edges intersects an edge belonging to the shape then it cannot be within.
        var relation: Component2D.WithinRelation =
            Component2D.WithinRelation.DISJOINT
        if (edgesIntersect(aX, aY, bX, bY) == true) {
            if (ab == true) {
                return Component2D.WithinRelation.NOTWITHIN
            } else {
                relation = Component2D.WithinRelation.CANDIDATE
            }
        }
        if (edgesIntersect(bX, bY, cX, cY) == true) {
            if (bc == true) {
                return Component2D.WithinRelation.NOTWITHIN
            } else {
                relation = Component2D.WithinRelation.CANDIDATE
            }
        }

        if (edgesIntersect(cX, cY, aX, aY) == true) {
            if (ca == true) {
                return Component2D.WithinRelation.NOTWITHIN
            } else {
                relation = Component2D.WithinRelation.CANDIDATE
            }
        }
        // If any of the rectangle edges crosses a triangle edge that does not belong to the shape
        // then it is a candidate for within
        if (relation == Component2D.WithinRelation.CANDIDATE) {
            return Component2D.WithinRelation.CANDIDATE
        }
        // Check if shape is within the triangle
        if (Component2D.pointInTriangle(
                minX, maxX, minY, maxY, this.minX, this.minY, aX, aY, bX, bY, cX, cY
            )
        ) {
            return Component2D.WithinRelation.CANDIDATE
        }
        return relation
    }

    private fun edgesIntersect(aX: Double, aY: Double, bX: Double, bY: Double): Boolean {
        // shortcut: check bboxes of edges are disjoint
        if (max(aX, bX) < minX || min(aX, bX) > maxX || min(aY, bY) > maxY || max(aY, bY) < minY) {
            return false
        }
        return GeoUtils.lineCrossesLineWithBoundary(aX, aY, bX, bY, minX, maxY, maxX, maxY)
                ||  // top
                GeoUtils.lineCrossesLineWithBoundary(aX, aY, bX, bY, maxX, maxY, maxX, minY)
                ||  // bottom
                GeoUtils.lineCrossesLineWithBoundary(aX, aY, bX, bY, maxX, minY, minX, minY)
                ||  // left
                GeoUtils.lineCrossesLineWithBoundary(
                    aX,
                    aY,
                    bX,
                    bY,
                    minX,
                    minY,
                    minX,
                    maxY
                ) // right
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Rectangle2D) return false
        val that = o
        return Double.compare(minX, that.minX) == 0 && Double.compare(
            maxX,
            that.maxX
        ) == 0 && Double.compare(minY, that.minY) == 0 && Double.compare(maxY, that.maxY) == 0
    }

    override fun hashCode(): Int {
        val result: Int = Objects.hash(minX, maxX, minY, maxY)
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Rectangle2D(x=")
        sb.append(minX)
        sb.append(" TO ")
        sb.append(maxX)
        sb.append(" y=")
        sb.append(minY)
        sb.append(" TO ")
        sb.append(maxY)
        sb.append(")")
        return sb.toString()
    }

    companion object {
        /** create a component2D from the provided XY rectangle  */
        fun create(rectangle: XYRectangle): Component2D {
            return Rectangle2D(
                rectangle.minX.toDouble(),
                rectangle.maxX.toDouble(),
                rectangle.minY.toDouble(),
                rectangle.maxY.toDouble()
            )
        }

        private val MIN_LON_INCL_QUANTIZE: Double = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.MIN_LON_ENCODED)
        private val MAX_LON_INCL_QUANTIZE: Double = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.MAX_LON_ENCODED)

        /** create a component2D from the provided LatLon rectangle  */
        fun create(rectangle: Rectangle): Component2D {
            // behavior of LatLonPoint.newBoxQuery()
            var minLongitude: Double = rectangle.minLon
            var crossesDateline = rectangle.minLon > rectangle.maxLon
            if (minLongitude == 180.0 && crossesDateline) {
                minLongitude = -180.0
                crossesDateline = false
            }
            // need to quantize!
            val qMinLat: Double = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitudeCeil(rectangle.minLat))
            val qMaxLat: Double = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(rectangle.maxLat))
            val qMinLon: Double = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitudeCeil(minLongitude))
            val qMaxLon: Double = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(rectangle.maxLon))
            if (crossesDateline) {
                // for rectangles that cross the dateline we need to create two components
                val components: Array<Component2D> = arrayOf(
                    Rectangle2D(MIN_LON_INCL_QUANTIZE, qMaxLon, qMinLat, qMaxLat),
                    Rectangle2D(qMinLon, MAX_LON_INCL_QUANTIZE, qMinLat, qMaxLat)
                )
                return ComponentTree.create(components)
            } else {
                return Rectangle2D(qMinLon, qMaxLon, qMinLat, qMaxLat)
            }
        }
    }
}
