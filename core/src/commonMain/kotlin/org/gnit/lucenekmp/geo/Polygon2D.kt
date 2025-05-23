package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues

/**
 * 2D polygon implementation represented as a balanced interval tree of edges.
 *
 *
 * Loosely based on the algorithm described in [
 * http://www-ma2.upc.es/geoc/Schirra-pointPolygon.pdf](http://www-ma2.upc.es/geoc/Schirra-pointPolygon.pdf).
 */
internal class Polygon2D private constructor(
    /** minimum X of this geometry's bounding box area  */
    override val minX: Double,
    /** maximum X of this geometry's bounding box area  */
    override val maxX: Double,
    /** minimum Y of this geometry's bounding box area  */
    override val minY: Double,
    /** maximum Y of this geometry's bounding box area  */
    override val maxY: Double,
    x: DoubleArray,
    y: DoubleArray,
    holes: Component2D?
) : Component2D {
    /** tree of holes, or null  */
    protected val holes: Component2D?

    /** Edges of the polygon represented as a 2-d interval tree.  */
    val tree: EdgeTree

    init {
        this.holes = holes
        this.tree = EdgeTree.createTree(x, y)
    }

    private constructor(polygon: XYPolygon, holes: Component2D?) : this(
        polygon.minX.toDouble(),
        polygon.maxX.toDouble(),
        polygon.minY.toDouble(),
        polygon.maxY.toDouble(),
        XYEncodingUtils.floatArrayToDoubleArray(polygon.polyX),
        XYEncodingUtils.floatArrayToDoubleArray(polygon.polyY),
        holes
    )

    private constructor(polygon: Polygon, holes: Component2D?) : this(
        polygon.minLon,
        polygon.maxLon,
        polygon.minLat,
        polygon.maxLat,
        polygon.getPolyLons(),
        polygon.getPolyLats(),
        holes
    )

    /**
     * Returns true if the point is contained within this polygon.
     *
     *
     * See [https://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html](https://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html) for more information.
     */
    override fun contains(x: Double, y: Double): Boolean {
        if (Component2D.containsPoint(x, y, minX, maxX, minY, maxY) && tree.contains(x, y)) {
            return holes == null || holes.contains(x, y) == false
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
        // check any holes
        if (holes != null) {
            val holeRelation: PointValues.Relation = holes.relate(minX, maxX, minY, maxY)
            if (holeRelation == PointValues.Relation.CELL_CROSSES_QUERY) {
                return PointValues.Relation.CELL_CROSSES_QUERY
            } else if (holeRelation == PointValues.Relation.CELL_INSIDE_QUERY) {
                return PointValues.Relation.CELL_OUTSIDE_QUERY
            }
        }
        // check each corner: if < 4 && > 0 are present, its cheaper than crossesSlowly
        val numCorners = numberOfCorners(minX, maxX, minY, maxY)
        if (numCorners == 4) {
            if (tree.crossesBox(minX, maxX, minY, maxY, true)) {
                return PointValues.Relation.CELL_CROSSES_QUERY
            }
            return PointValues.Relation.CELL_INSIDE_QUERY
        } else if (numCorners == 0) {
            if (Component2D.containsPoint(tree.x1, tree.y1, minX, maxX, minY, maxY)) {
                return PointValues.Relation.CELL_CROSSES_QUERY
            }
            if (tree.crossesBox(minX, maxX, minY, maxY, true)) {
                return PointValues.Relation.CELL_CROSSES_QUERY
            }
            return PointValues.Relation.CELL_OUTSIDE_QUERY
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
        if (contains(aX, aY)
            || contains(bX, bY)
            || tree.crossesLine(minX, maxX, minY, maxY, aX, aY, bX, bY, true)
        ) {
            return holes == null || holes.containsLine(minX, maxX, minY, maxY, aX, aY, bX, bY) == false
        }
        return false
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
        if (contains(aX, aY)
            || contains(bX, bY)
            || contains(cX, cY)
            || Component2D.pointInTriangle(
                minX, maxX, minY, maxY, tree.x1, tree.y1, aX, aY, bX, bY, cX, cY
            )
            || tree.crossesTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY, true)
        ) {
            return holes == null
                    || holes.containsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY) == false
        }
        return false
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
        if (contains(aX, aY)
            && contains(bX, bY)
            && tree.crossesLine(minX, maxX, minY, maxY, aX, aY, bX, bY, false) == false
        ) {
            return holes == null || holes.intersectsLine(minX, maxX, minY, maxY, aX, aY, bX, bY) == false
        }
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
        if (contains(aX, aY)
            && contains(bX, bY)
            && contains(cX, cY)
            && tree.crossesTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY, false) == false
        ) {
            return holes == null
                    || holes.intersectsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY) == false
        }
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
        if (ab == true && tree.crossesLine(minX, maxX, minY, maxY, aX, aY, bX, bY, true)) {
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

        // if any of the points is inside the polygon, the polygon cannot be within this indexed
        // shape because points belong to the original indexed shape.
        if (contains(aX, aY) || contains(bX, bY) || contains(cX, cY)) {
            return Component2D.WithinRelation.NOTWITHIN
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

    // returns 0, 4, or something in between
    private fun numberOfCorners(minX: Double, maxX: Double, minY: Double, maxY: Double): Int {
        var containsCount = 0
        if (contains(minX, minY)) {
            containsCount++
        }
        if (contains(maxX, minY)) {
            containsCount++
        }
        if (containsCount == 1) {
            return containsCount
        }
        if (contains(maxX, maxY)) {
            containsCount++
        }
        if (containsCount == 2) {
            return containsCount
        }
        if (contains(minX, maxY)) {
            containsCount++
        }
        return containsCount
    }

    companion object {
        /** Builds a Polygon2D from LatLon polygon  */
        fun create(polygon: Polygon): Component2D {
            val gonHoles: Array<Polygon> = polygon.getHoles()
            var holes: Component2D? = null
            if (gonHoles.isNotEmpty()) {
                holes = LatLonGeometry.create(*gonHoles)
            }
            return Polygon2D(polygon, holes)
        }

        /** Builds a Polygon2D from XY polygon  */
        fun create(polygon: XYPolygon): Component2D {
            val gonHoles: Array<XYPolygon> = polygon.getHoles()
            var holes: Component2D? = null
            if (gonHoles.isNotEmpty()) {
                holes = XYGeometry.create(*gonHoles)
            }
            return Polygon2D(polygon, holes)
        }
    }
}
