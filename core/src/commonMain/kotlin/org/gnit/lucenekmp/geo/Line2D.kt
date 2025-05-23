package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues

/**
 * 2D geo line implementation represented as a balanced interval tree of edges.
 *
 *
 * Line `Line2D` Construction takes `O(n log n)` time for sorting and tree
 * construction. [relate()][.relate] are `O(n)`, but for most practical lines are much
 * faster than brute force.
 */
internal class Line2D : Component2D {
    /** minimum Y of this geometry's bounding box area  */
    override val minY: Double

    /** maximum Y of this geometry's bounding box area  */
    override val maxY: Double

    /** minimum X of this geometry's bounding box area  */
    override val minX: Double

    /** maximum X of this geometry's bounding box area  */
    override val maxX: Double

    /** lines represented as a 2-d interval tree.  */
    private val tree: EdgeTree

    private constructor(line: Line) {
        this.minY = line.minLat
        this.maxY = line.maxLat
        this.minX = line.minLon
        this.maxX = line.maxLon
        this.tree = EdgeTree.createTree(line.getLons(), line.getLats())
    }

    private constructor(line: XYLine) {
        this.minY = line.minY.toDouble()
        this.maxY = line.maxY.toDouble()
        this.minX = line.minX.toDouble()
        this.maxX = line.maxX.toDouble()
        this.tree =
            EdgeTree.createTree(
                XYEncodingUtils.floatArrayToDoubleArray(line.getX()),
                XYEncodingUtils.floatArrayToDoubleArray(line.getY())
            )
    }

    override fun contains(x: Double, y: Double): Boolean {
        if (Component2D.containsPoint(x, y, this.minX, this.maxX, this.minY, this.maxY)) {
            return tree.isPointOnLine(x, y)
        }
        return false
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
            return PointValues.Relation.CELL_CROSSES_QUERY
        }
        if (tree.crossesBox(minX, maxX, minY, maxY, true)) {
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
        return tree.crossesLine(minX, maxX, minY, maxY, aX, aY, bX, bY, true)
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
        return Component2D.pointInTriangle(
            minX, maxX, minY, maxY, tree.x1, tree.y1, aX, aY, bX, bY, cX, cY
        )
                || tree.crossesTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY, true)
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
        // can be improved?
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
        if (ab && intersectsLine(minX, maxX, minY, maxY, aX, aY, bX, bY)) {
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

        var relation: Component2D.WithinRelation =
            Component2D.WithinRelation.DISJOINT
        // if any of the edges intersects an the edge belongs to the shape then it cannot be within.
        // if it only intersects edges that do not belong to the shape, then it is a candidate
        // we skip edges at the dateline to support shapes crossing it
        if (tree.crossesLine(minX, maxX, minY, maxY, aX, aY, bX, bY, true)) {
            if (ab == true) {
                return Component2D.WithinRelation.NOTWITHIN
            } else {
                relation = Component2D.WithinRelation.CANDIDATE
            }
        }

        if (tree.crossesLine(minX, maxX, minY, maxY, bX, bY, cX, cY, true)) {
            if (bc == true) {
                return Component2D.WithinRelation.NOTWITHIN
            } else {
                relation = Component2D.WithinRelation.CANDIDATE
            }
        }
        if (tree.crossesLine(minX, maxX, minY, maxY, cX, cY, aX, aY, true)) {
            if (ca == true) {
                return Component2D.WithinRelation.NOTWITHIN
            } else {
                relation = Component2D.WithinRelation.CANDIDATE
            }
        }
        // if any of the edges crosses and edge that does not belong to the shape
        // then it is a candidate for within
        if (relation == Component2D.WithinRelation.CANDIDATE) {
            return Component2D.WithinRelation.CANDIDATE
        }

        // Check if shape is within the triangle
        if (Component2D.pointInTriangle(
                minX, maxX, minY, maxY, tree.x1, tree.y1, aX, aY, bX, bY, cX, cY
            )
            == true
        ) {
            return Component2D.WithinRelation.CANDIDATE
        }
        return relation
    }

    companion object {
        /** create a Line2D from the provided LatLon Linestring  */
        fun create(line: Line): Component2D {
            return Line2D(line)
        }

        /** create a Line2D from the provided XY Linestring  */
        fun create(line: XYLine): Component2D {
            return Line2D(line)
        }
    }
}
