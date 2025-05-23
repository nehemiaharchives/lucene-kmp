package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.compare
import kotlin.experimental.xor
import kotlin.math.max
import kotlin.math.min


/**
 * Internal tree node: represents geometry edge from [x1, y1] to [x2, y2]. The sort value is `low`, which is the minimum y of the edge. `max` stores the maximum y of this edge or any
 * children.
 *
 *
 * Construction takes `O(n log n)` time for sorting and tree construction. Methods are
 * `O(n)`, but for most practical lines and polygons are much faster than brute force.
 */
internal open class EdgeTree private constructor(
    val x1: Double, // X-Y pair (in original order) of the two vertices
    val y1: Double, val x2: Double, val y2: Double,
    /** min Y of this edge  */
    val low: Double,
    /** max Y of this edge or any children  */
    var max: Double
) {
    /** left child edge, or null  */
    var left: EdgeTree? = null

    /** right child edge, or null  */
    var right: EdgeTree? = null

    /** Returns true if the point is on an edge or crosses the edge subtree an odd number of times.  */
    fun contains(x: Double, y: Double): Boolean {
        return containsPnPoly(x, y) > FALSE
    }

    /**
     * Returns byte 0x00 if the point crosses this edge subtree an even number of times. Returns byte
     * 0x01 if the point crosses this edge subtree an odd number of times. Returns byte 0x02 if the
     * point is on one of the edges.
     *
     *
     * See [
 * https://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html](https://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html) for more information.
     */
    // ported to java from https://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html
    // original code under the BSD license
    // (https://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html#License%20to%20Use)
    //
    // Copyright (c) 1970-2003, Wm. Randolph Franklin
    //
    // Permission is hereby granted, free of charge, to any person obtaining a copy of this software
    // and associated
    // documentation files (the "Software"), to deal in the Software without restriction, including
    // without limitation
    // the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
    // the Software, and
    // to permit persons to whom the Software is furnished to do so, subject to the following
    // conditions:
    //
    // 1. Redistributions of source code must retain the above copyright
    //    notice, this list of conditions and the following disclaimers.
    // 2. Redistributions in binary form must reproduce the above copyright
    //    notice in the documentation and/or other materials provided with
    //    the distribution.
    // 3. The name of W. Randolph Franklin may not be used to endorse or
    //    promote products derived from this Software without specific
    //    prior written permission.
    //
    // THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
    // BUT NOT LIMITED
    // TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
    // NO EVENT SHALL
    // THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
    // IN AN ACTION OF
    // CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
    // OR OTHER DEALINGS
    // IN THE SOFTWARE.
    private fun containsPnPoly(x: Double, y: Double): Byte {
        var res = FALSE
        if (y <= this.max) {
            if (y == this.y1 && y == this.y2
                || (y <= this.y1 && y >= this.y2) != (y >= this.y1 && y <= this.y2)
            ) {
                if ((x == this.x1 && x == this.x2)
                    || ((x <= this.x1 && x >= this.x2) != (x >= this.x1 && x <= this.x2)
                            && GeoUtils.orient(this.x1, this.y1, this.x2, this.y2, x, y) == 0)
                ) {
                    return ON_EDGE
                } else if (this.y1 > y != this.y2 > y) {
                    res =
                        if (x < (this.x2 - this.x1) * (y - this.y1) / (this.y2 - this.y1) + this.x1)
                            TRUE
                        else
                            FALSE
                }
            }
            if (this.left != null) {
                res = res xor left!!.containsPnPoly(x, y)
                if ((res.toInt() and 0x02) == 0x02) {
                    return ON_EDGE
                }
            }

            if (this.right != null && y >= this.low) {
                res = res xor right!!.containsPnPoly(x, y)
                if ((res.toInt() and 0x02) == 0x02) {
                    return ON_EDGE
                }
            }
        }
        require(res >= FALSE && res <= ON_EDGE)
        return res
    }

    /** returns true if the provided x, y point lies on the line  */
    fun isPointOnLine(x: Double, y: Double): Boolean {
        if (y <= max) {
            val a1x = x1
            val a1y = y1
            val b1x = x2
            val b1y = y2
            val outside =
                (a1y < y && b1y < y)
                        || (a1y > y && b1y > y)
                        || (a1x < x && b1x < x)
                        || (a1x > x && b1x > x)
            if (outside == false && GeoUtils.orient(a1x, a1y, b1x, b1y, x, y) == 0) {
                return true
            }
            if (left != null && left!!.isPointOnLine(x, y)) {
                return true
            }
            if (right != null && y >= this.low && right!!.isPointOnLine(x, y)) {
                return true
            }
        }
        return false
    }

    /** Returns true if the triangle crosses any edge in this edge subtree  */
    fun crossesTriangle(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        cx: Double,
        cy: Double,
        includeBoundary: Boolean
    ): Boolean {
        if (minY <= max) {
            val dy = y1
            val ey = y2
            val dx = x1
            val ex = x2

            // optimization: see if the rectangle is outside of the "bounding box" of the polyline at all
            // if not, don't waste our time trying more complicated stuff
            val outside =
                (dy < minY && ey < minY)
                        || (dy > maxY && ey > maxY)
                        || (dx < minX && ex < minX)
                        || (dx > maxX && ex > maxX)

            if (outside == false) {
                if (includeBoundary == true) {
                    if (GeoUtils.lineCrossesLineWithBoundary(dx, dy, ex, ey, ax, ay, bx, by)
                        || GeoUtils.lineCrossesLineWithBoundary(dx, dy, ex, ey, bx, by, cx, cy)
                        || GeoUtils.lineCrossesLineWithBoundary(dx, dy, ex, ey, cx, cy, ax, ay)
                    ) {
                        return true
                    }
                } else {
                    if (GeoUtils.lineCrossesLine(dx, dy, ex, ey, ax, ay, bx, by)
                        || GeoUtils.lineCrossesLine(dx, dy, ex, ey, bx, by, cx, cy)
                        || GeoUtils.lineCrossesLine(dx, dy, ex, ey, cx, cy, ax, ay)
                    ) {
                        return true
                    }
                }
            }

            if (left != null
                && left!!.crossesTriangle(
                    minX, maxX, minY, maxY, ax, ay, bx, by, cx, cy, includeBoundary
                )
            ) {
                return true
            }

            if (right != null && maxY >= low && right!!.crossesTriangle(
                    minX, maxX, minY, maxY, ax, ay, bx, by, cx, cy, includeBoundary
                )
            ) {
                return true
            }
        }
        return false
    }

    /** Returns true if the box crosses any edge in this edge subtree  */
    fun crossesBox(minX: Double, maxX: Double, minY: Double, maxY: Double, includeBoundary: Boolean): Boolean {
        // we just have to cross one edge to answer the question, so we descend the tree and return when
        // we do.
        if (minY <= max) {
            // we compute line intersections of every polygon edge with every box line.
            // if we find one, return true.
            // for each box line (AB):
            //   for each poly line (CD):
            //     intersects = orient(C,D,A) * orient(C,D,B) <= 0 && orient(A,B,C) * orient(A,B,D) <= 0
            val cy = y1
            val dy = y2
            val cx = x1
            val dx = x2

            // optimization: see if either end of the line segment is contained by the rectangle
            if (Rectangle.containsPoint(cy, cx, minY, maxY, minX, maxX)
                || Rectangle.containsPoint(dy, dx, minY, maxY, minX, maxX)
            ) {
                return true
            }

            // optimization: see if the rectangle is outside of the "bounding box" of the polyline at all
            // if not, don't waste our time trying more complicated stuff
            val outside =
                (cy < minY && dy < minY)
                        || (cy > maxY && dy > maxY)
                        || (cx < minX && dx < minX)
                        || (cx > maxX && dx > maxX)

            if (outside == false) {
                if (includeBoundary == true) {
                    if (GeoUtils.lineCrossesLineWithBoundary(
                            cx,
                            cy,
                            dx,
                            dy,
                            minX,
                            minY,
                            maxX,
                            minY
                        )
                        || GeoUtils.lineCrossesLineWithBoundary(
                            cx,
                            cy,
                            dx,
                            dy,
                            maxX,
                            minY,
                            maxX,
                            maxY
                        )
                        || GeoUtils.lineCrossesLineWithBoundary(
                            cx,
                            cy,
                            dx,
                            dy,
                            maxX,
                            maxY,
                            minX,
                            maxY
                        )
                        || GeoUtils.lineCrossesLineWithBoundary(
                            cx,
                            cy,
                            dx,
                            dy,
                            minX,
                            maxY,
                            minX,
                            minY
                        )
                    ) {
                        // include boundaries: ensures box edges that terminate on the polygon are included
                        return true
                    }
                } else {
                    if (GeoUtils.lineCrossesLine(cx, cy, dx, dy, minX, minY, maxX, minY)
                        || GeoUtils.lineCrossesLine(cx, cy, dx, dy, maxX, minY, maxX, maxY)
                        || GeoUtils.lineCrossesLine(cx, cy, dx, dy, maxX, maxY, minX, maxY)
                        || GeoUtils.lineCrossesLine(cx, cy, dx, dy, minX, maxY, minX, minY)
                    ) {
                        return true
                    }
                }
            }

            if (left != null && left!!.crossesBox(minX, maxX, minY, maxY, includeBoundary)) {
                return true
            }

            if (right != null && maxY >= low && right!!.crossesBox(minX, maxX, minY, maxY, includeBoundary)) {
                return true
            }
        }
        return false
    }

    /** Returns true if the line crosses any edge in this edge subtree  */
    fun crossesLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        a2x: Double,
        a2y: Double,
        b2x: Double,
        b2y: Double,
        includeBoundary: Boolean
    ): Boolean {
        if (minY <= max) {
            val a1x = x1
            val a1y = y1
            val b1x = x2
            val b1y = y2

            val outside =
                (a1y < minY && b1y < minY)
                        || (a1y > maxY && b1y > maxY)
                        || (a1x < minX && b1x < minX)
                        || (a1x > maxX && b1x > maxX)
            if (outside == false) {
                if (includeBoundary) {
                    if (GeoUtils.lineCrossesLineWithBoundary(
                            a1x,
                            a1y,
                            b1x,
                            b1y,
                            a2x,
                            a2y,
                            b2x,
                            b2y
                        )
                    ) {
                        return true
                    }
                } else {
                    if (GeoUtils.lineCrossesLine(a1x, a1y, b1x, b1y, a2x, a2y, b2x, b2y)) {
                        return true
                    }
                }
            }
            if (left != null
                && left!!.crossesLine(minX, maxX, minY, maxY, a2x, a2y, b2x, b2y, includeBoundary)
            ) {
                return true
            }
            if (right != null && maxY >= low && right!!.crossesLine(
                    minX,
                    maxX,
                    minY,
                    maxY,
                    a2x,
                    a2y,
                    b2x,
                    b2y,
                    includeBoundary
                )
            ) {
                return true
            }
        }
        return false
    }

    companion object {
        /** helper bytes to signal if a point is on an edge, it is within the edge tree or disjoint  */
        private const val FALSE: Byte = 0x00

        private const val TRUE: Byte = 0x01
        private const val ON_EDGE: Byte = 0x02

        /**
         * Creates an edge interval tree from a set of geometry vertices.
         *
         * @return root node of the tree.
         */
        fun createTree(x: DoubleArray, y: DoubleArray): EdgeTree {
            val edges = Array(x.size - 1) { i ->
                EdgeTree(
                    x[i], y[i],
                    x[i + 1], y[i + 1],
                    min(y[i], y[i + 1]),
                    max(y[i], y[i + 1])
                )
            }

            // sort the edges then build a balanced tree from them
            Arrays.sort(
                edges,
                Comparator { left: EdgeTree, right: EdgeTree ->
                    var ret: Int = Double.compare(left.low, right.low)
                    if (ret == 0) {
                        ret = Double.compare(left.max, right.max)
                    }
                    ret
                })
            return createTree(edges, 0, edges.size - 1)
        }

        /** Creates tree from sorted edges (with range low and high inclusive)  */
        private fun createTree(edges: Array<EdgeTree>, low: Int, high: Int): EdgeTree {
            // java lucene returns null but lucene-kmp will throw an exception
            /*if (low > high) {
                return null
            }*/
            require(low <= high) { "low: $low, high: $high" }

            // add midpoint
            val mid = (low + high) ushr 1
            val newNode = edges[mid]
            // add children
            newNode.left = createTree(edges, low, mid - 1)
            newNode.right = createTree(edges, mid + 1, high)
            // pull up max values to this node
            if (newNode.left != null) {
                newNode.max = max(newNode.max, newNode.left!!.max)
            }
            if (newNode.right != null) {
                newNode.max = max(newNode.max, newNode.right!!.max)
            }
            return newNode
        }
    }
}
