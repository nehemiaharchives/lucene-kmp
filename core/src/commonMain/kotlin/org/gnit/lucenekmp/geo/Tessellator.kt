package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.geo.GeoUtils.WindingOrder
import org.gnit.lucenekmp.jdkport.StrictMath
import org.gnit.lucenekmp.jdkport.compareUnsigned
import org.gnit.lucenekmp.jdkport.sort
import org.gnit.lucenekmp.util.BitUtil
import kotlin.jvm.JvmOverloads
import kotlin.math.*

/**
 * Computes a triangular mesh tessellation for a given polygon.
 *
 *
 * This is inspired by mapbox's earcut algorithm (https://github.com/mapbox/earcut) which is a
 * modification to FIST (https://www.cosy.sbg.ac.at/~held/projects/triang/triang.html) written by
 * Martin Held, and ear clipping
 * (https://www.geometrictools.com/Documentation/TriangulationByEarClipping.pdf) written by David
 * Eberly.
 *
 *
 * Notes:
 *
 *
 *  * Requires valid polygons:
 *
 *  * No self intersections
 *  * Holes may only touch at one vertex
 *  * Polygon must have an area (e.g., no "line" boxes)
 *  * sensitive to overflow (e.g, subatomic values such as E-200 can cause unexpected
 * behavior)
 *
 *
 *
 *
 * The code is a modified version of the javascript implementation provided by MapBox under the
 * following license:
 *
 *
 * ISC License
 *
 *
 * Copyright (c) 2016, Mapbox
 *
 *
 * Permission to use, copy, modify, and/or distribute this software for any purpose with or
 * without fee is hereby granted, provided that the above copyright notice and this permission
 * notice appear in all copies.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH' REGARD TO THIS
 * SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE
 * AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
 * OF THIS SOFTWARE.
 *
 * @lucene.internal
 */
object Tessellator {
    // this is a dumb heuristic to control whether we cut over to sorted morton values
    private const val VERTEX_THRESHOLD = 80

    @JvmOverloads
    fun tessellate(
        polygon: Polygon, checkSelfIntersections: Boolean, monitor: Monitor? = null
    ): MutableList<Triangle> {
        // Attempt to establish a doubly-linked list of the provided shell points (should be CCW, but
        // this will correct);
        // then filter instances of intersections.
        var outerNode =
            createDoublyLinkedList(
                polygon.getPolyLons(),
                polygon.getPolyLats(),
                polygon.getWindingOrder(),
                true,
                0,
                WindingOrder.CW
            )
        // If an outer node hasn't been detected, the shape is malformed. (must comply with OGC SFA
        // specification)
        requireNotNull(outerNode) { "Malformed shape detected in Tessellator!" }
        require(!(outerNode === outerNode.next || outerNode === outerNode.next!!.next)) { "at least three non-collinear points required" }

        // Determine if the specified list of points contains holes
        if (polygon.numHoles() > 0) {
            // Eliminate the hole triangulation.
            outerNode = eliminateHoles(polygon, outerNode)
        }

        // If the shape crosses VERTEX_THRESHOLD, use z-order curve hashing:
        val mortonOptimized: Boolean
        run {
            var threshold: Int = Tessellator.VERTEX_THRESHOLD - polygon.numPoints()
            var i = 0
            while (threshold >= 0 && i < polygon.numHoles()) {
                threshold -= polygon.getHole(i).numPoints()
                ++i
            }

            // Link polygon nodes in Z-Order
            mortonOptimized = threshold < 0
            if (mortonOptimized == true) {
                Tessellator.sortByMorton(outerNode)
            }
        }
        if (checkSelfIntersections) {
            checkIntersection(outerNode, mortonOptimized)
        }
        // Calculate the tessellation using the doubly LinkedList.
        val result =
            earcutLinkedList(
                polygon, outerNode, ArrayList<Triangle>(), State.INIT, mortonOptimized, monitor, 0
            )
        if (result.isEmpty()) {
            notifyMonitor(Monitor.Companion.FAILED, monitor, null, result)
            throw IllegalArgumentException(
                "Unable to Tessellate shape. Possible malformed shape detected."
            )
        }
        notifyMonitor(Monitor.Companion.COMPLETED, monitor, null, result)

        return result
    }

    @JvmOverloads
    fun tessellate(
        polygon: XYPolygon, checkSelfIntersections: Boolean, monitor: Monitor? = null
    ): MutableList<Triangle> {
        // Attempt to establish a doubly-linked list of the provided shell points (should be CCW, but
        // this will correct);
        // then filter instances of intersections.0
        var outerNode =
            createDoublyLinkedList(
                XYEncodingUtils.floatArrayToDoubleArray(polygon.polyX),
                XYEncodingUtils.floatArrayToDoubleArray(polygon.polyY),
                polygon.getWindingOrder(),
                false,
                0,
                WindingOrder.CW
            )
        // If an outer node hasn't been detected, the shape is malformed. (must comply with OGC SFA
        // specification)
        requireNotNull(outerNode) { "Malformed shape detected in Tessellator!" }
        require(!(outerNode === outerNode.next || outerNode === outerNode.next!!.next)) { "at least three non-collinear points required" }

        // Determine if the specified list of points contains holes
        if (polygon.numHoles() > 0) {
            // Eliminate the hole triangulation.
            outerNode = eliminateHoles(polygon, outerNode)
        }

        // If the shape crosses VERTEX_THRESHOLD, use z-order curve hashing:
        val mortonOptimized: Boolean
        run {
            var threshold: Int = Tessellator.VERTEX_THRESHOLD - polygon.numPoints()
            var i = 0
            while (threshold >= 0 && i < polygon.numHoles()) {
                threshold -= polygon.getHole(i).numPoints()
                ++i
            }

            // Link polygon nodes in Z-Order
            mortonOptimized = threshold < 0
            if (mortonOptimized == true) {
                Tessellator.sortByMorton(outerNode)
            }
        }
        if (checkSelfIntersections == true) {
            checkIntersection(outerNode, mortonOptimized)
        }
        // Calculate the tessellation using the doubly LinkedList.
        val result =
            earcutLinkedList(
                polygon, outerNode, ArrayList<Triangle>(), State.INIT, mortonOptimized, monitor, 0
            )
        if (result.isEmpty()) {
            notifyMonitor(Monitor.Companion.FAILED, monitor, null, result)
            throw IllegalArgumentException(
                "Unable to Tessellate shape. Possible malformed shape detected."
            )
        }
        notifyMonitor(Monitor.Companion.COMPLETED, monitor, null, result)

        return result
    }

    /**
     * Creates a circular doubly linked list using polygon points. The order is governed by the
     * specified winding order
     */
    private fun createDoublyLinkedList(
        x: DoubleArray,
        y: DoubleArray,
        polyWindingOrder: WindingOrder,
        isGeo: Boolean,
        startIndex: Int,
        windingOrder: WindingOrder
    ): Node? {
        var startIndex = startIndex
        var lastNode: Node? = null
        // Link points into the circular doubly-linked list in the specified winding order
        if (windingOrder == polyWindingOrder) {
            for (i in x.indices) {
                lastNode = insertNode(x, y, startIndex++, i, lastNode, isGeo)
            }
        } else {
            for (i in x.indices.reversed()) {
                lastNode = insertNode(x, y, startIndex++, i, lastNode, isGeo)
            }
        }
        // if first and last node are the same then remove the end node and set lastNode to the start
        if (lastNode != null && isVertexEquals(lastNode, lastNode.next!!)) {
            removeNode(lastNode, true)
            lastNode = lastNode.next
        }

        // Return the last node in the Doubly-Linked List
        return filterPoints(lastNode, null)
    }

    private fun eliminateHoles(polygon: XYPolygon, outerNode: Node): Node? {
        // Define a list to hole a reference to each filtered hole list.
        val holeList: MutableList<Node> = ArrayList()
        // keep a reference to the hole
        val holeListPolygons: MutableMap<Node, XYPolygon> =
            HashMap()
        // Iterate through each array of hole vertices.
        val holes: Array<XYPolygon> = polygon.getHoles()
        var nodeIndex: Int = polygon.numPoints()
        for (i in 0..<polygon.numHoles()) {
            // create the doubly-linked hole list
            val list: Node? =
                createDoublyLinkedList(
                    XYEncodingUtils.floatArrayToDoubleArray(holes[i].polyX),
                    XYEncodingUtils.floatArrayToDoubleArray(holes[i].polyY),
                    holes[i].getWindingOrder(),
                    false,
                    nodeIndex,
                    WindingOrder.CCW
                )
            // Determine if the resulting hole polygon was successful.
            if (list != null) {
                // Add the leftmost vertex of the hole.
                val leftMost = fetchLeftmost(list)
                holeList.add(leftMost)
                holeListPolygons.put(leftMost, holes[i])
            }
            nodeIndex += holes[i].numPoints()
        }
        return eliminateHoles(holeList, holeListPolygons, outerNode)
    }

    /** Links every hole into the outer loop, producing a single-ring polygon without holes.  */
    private fun eliminateHoles(polygon: Polygon, outerNode: Node): Node? {
        // Define a list to hole a reference to each filtered hole list.
        val holeList: MutableList<Node> = ArrayList()
        // keep a reference to the hole
        val holeListPolygons: MutableMap<Node, Polygon> =
            HashMap()
        // Iterate through each array of hole vertices.
        val holes: Array<Polygon> = polygon.getHoles()
        var nodeIndex: Int = polygon.numPoints()
        for (i in 0..<polygon.numHoles()) {
            // create the doubly-linked hole list
            val list =
                createDoublyLinkedList(
                    holes[i].getPolyLons(),
                    holes[i].getPolyLats(),
                    holes[i].getWindingOrder(),
                    true,
                    nodeIndex,
                    WindingOrder.CCW
                )
            require(list !== list?.next) { "Points are all coplanar in hole: " + holes[i] }
            // Add the leftmost vertex of the hole.
            val leftMost = fetchLeftmost(list)
            holeList.add(leftMost)
            holeListPolygons.put(leftMost, holes[i])
            nodeIndex += holes[i].numPoints()
        }
        return eliminateHoles(holeList, holeListPolygons, outerNode)
    }

    private fun eliminateHoles(
        holeList: MutableList<Node>, holeListPolygons: MutableMap<Node, *>, outerNode: Node?
    ): Node? {
        // Sort the hole vertices by x coordinate
        var outerNode = outerNode
        holeList.sort { pNodeA: Node, pNodeB: Node ->
            var diff = pNodeA.getX() - pNodeB.getX()
            if (diff == 0.0) {
                diff = pNodeA.getY() - pNodeB.getY()
                if (diff == 0.0) {
                    // same hole node
                    val a = min(pNodeA.previous!!.getY(), pNodeA.next!!.getY())
                    val b = min(pNodeB.previous!!.getY(), pNodeB.next!!.getY())
                    diff = a - b
                }
            }
            if (diff < 0) -1 else if (diff > 0) 1 else 0
        }

        // Process holes from left to right.
        for (i in holeList.indices) {
            // Eliminate hole triangles from the result set
            val holeNode = holeList[i]
            val holeMinX: Double
            val holeMaxX: Double
            val holeMinY: Double
            val holeMaxY: Double
            val h: Any = holeListPolygons[holeNode] as Any
            if (h is Polygon) {
                holeMinX = h.minLon
                holeMaxX = h.maxLon
                holeMinY = h.minLat
                holeMaxY = h.maxLat
            } else {
                val holePoly: XYPolygon = h as XYPolygon
                holeMinX = holePoly.minX.toDouble()
                holeMaxX = holePoly.maxX.toDouble()
                holeMinY = holePoly.minY.toDouble()
                holeMaxY = holePoly.maxY.toDouble()
            }
            eliminateHole(holeNode, outerNode, holeMinX, holeMaxX, holeMinY, holeMaxY)
            // Filter the new polygon.
            outerNode = filterPoints(outerNode, outerNode!!.next)
        }
        // Return a pointer to the list.
        return outerNode
    }

    /** Finds a bridge between vertices that connects a hole with an outer ring, and links it  */
    private fun eliminateHole(
        holeNode: Node,
        outerNode: Node?,
        holeMinX: Double,
        holeMaxX: Double,
        holeMinY: Double,
        holeMaxY: Double
    ) {
        // Attempt to merge the hole using a common point between if it exists.

        var outerNode: Node? = outerNode
        if (maybeMergeHoleWithSharedVertices(
                holeNode, outerNode!!, holeMinX, holeMaxX, holeMinY, holeMaxY
            )
        ) {
            return
        }
        // Attempt to find a logical bridge between the HoleNode and OuterNode.
        outerNode = fetchHoleBridge(holeNode, outerNode)

        // Determine whether a hole bridge could be fetched.
        if (outerNode != null) {
            // compute if the bridge overlaps with a polygon edge.
            val fromPolygon =
                isPointInLine(outerNode, outerNode.next!!, holeNode)
                        || isPointInLine(holeNode, holeNode.next!!, outerNode)
            // Split the resulting polygon.
            val node = splitPolygon(outerNode, holeNode, fromPolygon)
            // Filter the split nodes.
            filterPoints(node, node.next)
        }
    }

    /**
     * Choose a common vertex between the polygon and the hole if it exists and return true, otherwise
     * return false
     */
    private fun maybeMergeHoleWithSharedVertices(
        holeNode: Node,
        outerNode: Node,
        holeMinX: Double,
        holeMaxX: Double,
        holeMinY: Double,
        holeMaxY: Double
    ): Boolean {
        // Attempt to find a common point between the HoleNode and OuterNode.
        var sharedVertex: Node? = null
        var sharedVertexConnection: Node? = null
        var next: Node? = outerNode
        do {
            if (Rectangle.containsPoint(
                    next!!.getY(), next.getX(), holeMinY, holeMaxY, holeMinX, holeMaxX
                )
            ) {
                val newSharedVertex = getSharedVertex(holeNode, next)
                if (newSharedVertex != null) {
                    if (sharedVertex == null) {
                        sharedVertex = newSharedVertex
                        sharedVertexConnection = next
                    } else if (newSharedVertex == sharedVertex) {
                        // This can only happen if this vertex has been already used for a bridge. We need to
                        // choose the right one.
                        sharedVertexConnection =
                            getSharedInsideVertex(sharedVertex, sharedVertexConnection!!, next)
                    }
                }
            }
            next = next.next
        } while (next !== outerNode)
        if (sharedVertex != null) {
            // Split the resulting polygon.
            val node = splitPolygon(sharedVertexConnection!!, sharedVertex, true)
            // Filter the split nodes.
            filterPoints(node, node.next)
            return true
        }
        return false
    }

    /** Check if the provided vertex is in the polygon and return it  */
    private fun getSharedVertex(polygon: Node, vertex: Node): Node? {
        var next: Node? = polygon
        do {
            if (isVertexEquals(next!!, vertex)) {
                return next
            }
            next = next.next
        } while (next !== polygon)
        return null
    }

    /** Choose the vertex that has a smaller angle with the hole vertex  */
    fun getSharedInsideVertex(holeVertex: Node, candidateA: Node, candidateB: Node): Node {
        require(isVertexEquals(holeVertex, candidateA) && isVertexEquals(holeVertex, candidateB))
        // we are joining candidate.prevNode -> holeVertex.node -> holeVertex.nextNode.
        // A negative area means a convex angle. if both are convex/reflex choose the point of
        // minimum angle
        val a1 =
            area(
                candidateA.previous!!.getX(),
                candidateA.previous!!.getY(),
                holeVertex.getX(),
                holeVertex.getY(),
                holeVertex.next!!.getX(),
                holeVertex.next!!.getY()
            )
        val a2 =
            area(
                candidateB.previous!!.getX(),
                candidateB.previous!!.getY(),
                holeVertex.getX(),
                holeVertex.getY(),
                holeVertex.next!!.getX(),
                holeVertex.next!!.getY()
            )

        if (a1 < 0 != a2 < 0) {
            // one is convex, the other reflex, get the convex one
            return if (a1 < a2) candidateA else candidateB
        } else {
            // both are convex / reflex, choose the smallest angle
            val angle1 = angle(candidateA.previous!!, candidateA, holeVertex.next!!)
            val angle2 = angle(candidateB.previous!!, candidateB, holeVertex.next!!)
            return if (angle1 < angle2) candidateA else candidateB
        }
    }

    private fun angle(a: Node, b: Node, c: Node): Double {
        val ax = a.getX() - b.getX()
        val ay = a.getY() - b.getY()
        val cx = c.getX() - b.getX()
        val cy = c.getY() - b.getY()
        val dotProduct = ax * cx + ay * cy
        val aLength = sqrt(ax * ax + ay * ay)
        val bLength = sqrt(cx * cx + cy * cy)
        return acos(dotProduct / (aLength * bLength))
    }

    /**
     * David Eberly's algorithm for finding a bridge between a hole and outer polygon
     *
     *
     * see: http://www.geometrictools.com/Documentation/TriangulationByEarClipping.pdf
     */
    private fun fetchHoleBridge(holeNode: Node, outerNode: Node): Node? {
        var p: Node? = outerNode
        var qx = Double.Companion.NEGATIVE_INFINITY
        val hx = holeNode.getX()
        val hy = holeNode.getY()
        var connection: Node? = null
        // 1. find a segment intersected by a ray from the hole's leftmost point to the left;
        // segment's endpoint with lesser x will be potential connection point
        run {
            do {
                if (hy <= p!!.getY() && hy >= p.next!!.getY() && p.next!!.getY() != p.getY()) {
                    val x =
                        p.getX() + (hy - p.getY()) * (p.next!!.getX() - p.getX()) / (p.next!!.getY() - p.getY())
                    if (x <= hx && x > qx) {
                        qx = x
                        if (x == hx) {
                            if (hy == p.getY()) return p
                            if (hy == p.next!!.getY()) return p.next
                        }
                        connection = if (p.getX() < p.next!!.getX()) p else p.next
                    }
                }
                p = p.next
            } while (p !== outerNode)
        }

        if (connection == null) {
            return null
        } else if (hx == qx) {
            return connection.previous
        }

        // 2. look for points inside the triangle of hole point, segment intersection, and endpoint
        // its a valid connection iff there are no points found;
        // otherwise choose the point of the minimum angle with the ray as the connection point
        val stop: Node = connection
        val mx = connection.getX()
        val my = connection.getY()
        var tanMin = Double.Companion.POSITIVE_INFINITY
        var tan: Double
        p = connection
        do {
            if (hx >= p!!.getX() && p.getX() >= mx && hx != p.getX() && pointInEar(
                    p.getX(),
                    p.getY(),
                    if (hy < my) hx else qx,
                    hy,
                    mx,
                    my,
                    if (hy < my) qx else hx,
                    hy
                )
            ) {
                tan = abs(hy - p.getY()) / (hx - p.getX()) // tangential
                if ((tan < tanMin || (tan == tanMin && p.getX() > connection!!.getX()))
                    && isLocallyInside(p, holeNode)
                ) {
                    connection = p
                    tanMin = tan
                }
            }
            p = p.next
        } while (p !== stop)
        return connection
    }

    /** Finds the left-most hole of a polygon ring. *  */
    private fun fetchLeftmost(start: Node?): Node {
        var node: Node? = start
        var leftMost: Node? = start
        do {
            // Determine if the current node possesses a lesser X position.
            if (node!!.getX() < leftMost!!.getX()
                || (node.getX() == leftMost.getX() && node.getY() < leftMost.getY())
            ) {
                // Maintain a reference to this Node.
                leftMost = node
            }
            // Progress the search to the next node in the doubly-linked list.
            node = node.next
        } while (node !== start)

        // Return the node with the smallest X value.
        return leftMost
    }

    /**
     * Main ear slicing loop which triangulates the vertices of a polygon, provided as a doubly-linked
     * list. *
     */
    private fun earcutLinkedList(
        polygon: Any,
        currEar: Node?,
        tessellation: MutableList<Triangle>,
        state: State,
        mortonOptimized: Boolean,
        monitor: Monitor?,
        depth: Int
    ): MutableList<Triangle> {
        var currEar = currEar
        var state = state
        earcut@ do {
            if (currEar == null || currEar.previous === currEar.next) {
                return tessellation
            }

            var stop: Node? = currEar
            var prevNode: Node?
            var nextNode: Node?

            // Iteratively slice ears
            do {
                notifyMonitor(state, depth, monitor, currEar, tessellation)
                prevNode = currEar!!.previous
                nextNode = currEar.next
                // Determine whether the current triangle must be cut off.
                val isReflex =
                    (area(
                        prevNode!!.getX(),
                        prevNode.getY(),
                        currEar.getX(),
                        currEar.getY(),
                        nextNode!!.getX(),
                        nextNode.getY()
                    )
                            >= 0)
                if (isReflex == false && isEar(currEar, mortonOptimized) == true) {
                    // Compute if edges belong to the polygon
                    val abFromPolygon = prevNode.isNextEdgeFromPolygon
                    val bcFromPolygon = currEar.isNextEdgeFromPolygon
                    val caFromPolygon = isEdgeFromPolygon(prevNode, nextNode, mortonOptimized)
                    // Return the triangulated data
                    tessellation.add(
                        Triangle(
                            prevNode, abFromPolygon, currEar, bcFromPolygon, nextNode, caFromPolygon
                        )
                    )
                    // Remove the ear node.
                    removeNode(currEar, caFromPolygon)

                    // Skipping to the next node leaves fewer slither triangles.
                    currEar = nextNode.next
                    stop = nextNode.next
                    continue
                }
                currEar = nextNode
                // If the whole polygon has been iterated over and no more ears can be found.
                if (currEar === stop) {
                    when (state) {
                        State.INIT -> {
                            // try filtering points and slicing again
                            currEar = filterPoints(currEar, null)
                            state = State.CURE
                            continue@earcut
                        }

                        State.CURE -> {
                            // if this didn't work, try curing all small self-intersections locally
                            currEar = cureLocalIntersections(currEar, tessellation, mortonOptimized)
                            state = State.SPLIT
                            continue@earcut
                        }

                        State.SPLIT ->               // as a last resort, try splitting the remaining polygon into two
                            if (splitEarcut(
                                    polygon,
                                    currEar,
                                    tessellation,
                                    mortonOptimized,
                                    monitor,
                                    depth + 1
                                )
                                == false
                            ) {
                                // we could not process all points. Tessellation failed
                                notifyMonitor(state.name + "[FAILED]", monitor, currEar, tessellation)
                                throw IllegalArgumentException(
                                    "Unable to Tessellate shape. Possible malformed shape detected."
                                )
                            }
                    }
                    break
                }
            } while (currEar!!.previous !== currEar.next)
            break
        } while (true)
        // Return the calculated tessellation
        return tessellation
    }

    /** Determines whether a polygon node forms a valid ear with adjacent nodes. *  */
    private fun isEar(ear: Node, mortonOptimized: Boolean): Boolean {
        if (mortonOptimized == true) {
            return mortonIsEar(ear)
        }

        // make sure there aren't other points inside the potential ear
        var node = ear.next!!.next
        while (node !== ear.previous) {
            if (pointInEar(
                    node!!.getX(),
                    node.getY(),
                    ear.previous!!.getX(),
                    ear.previous!!.getY(),
                    ear.getX(),
                    ear.getY(),
                    ear.next!!.getX(),
                    ear.next!!.getY()
                )
                && (area(
                    node.previous!!.getX(),
                    node.previous!!.getY(),
                    node.getX(),
                    node.getY(),
                    node.next!!.getX(),
                    node.next!!.getY()
                )
                        >= 0)
            ) {
                return false
            }
            node = node.next
        }
        return true
    }

    /**
     * Uses morton code for speed to determine whether or a polygon node forms a valid ear w/ adjacent
     * nodes
     */
    private fun mortonIsEar(ear: Node): Boolean {
        // triangle bbox (flip the bits so negative encoded values are < positive encoded values)
        val minTX: Int =
            StrictMath.min(StrictMath.min(ear.previous!!.x, ear.x), ear.next!!.x) xor -0x80000000
        val minTY: Int =
            StrictMath.min(StrictMath.min(ear.previous!!.y, ear.y), ear.next!!.y) xor -0x80000000
        val maxTX: Int =
            StrictMath.max(StrictMath.max(ear.previous!!.x, ear.x), ear.next!!.x) xor -0x80000000
        val maxTY: Int =
            StrictMath.max(StrictMath.max(ear.previous!!.y, ear.y), ear.next!!.y) xor -0x80000000

        // z-order range for the current triangle bbox;
        val minZ: Long = BitUtil.interleave(minTX, minTY)
        val maxZ: Long = BitUtil.interleave(maxTX, maxTY)

        // now make sure we don't have other points inside the potential ear;

        // look for points inside the triangle in both directions
        var p = ear.previousZ
        var n = ear.nextZ
        while (p != null && Long.compareUnsigned(
                p.morton,
                minZ
            ) >= 0 && n != null && Long.compareUnsigned(n.morton, maxZ) <= 0
        ) {
            if (p.idx != ear.previous!!.idx && p.idx != ear.next!!.idx && pointInEar(
                    p.getX(),
                    p.getY(),
                    ear.previous!!.getX(),
                    ear.previous!!.getY(),
                    ear.getX(),
                    ear.getY(),
                    ear.next!!.getX(),
                    ear.next!!.getY()
                )
                && (area(
                    p.previous!!.getX(),
                    p.previous!!.getY(),
                    p.getX(),
                    p.getY(),
                    p.next!!.getX(),
                    p.next!!.getY()
                )
                        >= 0)
            ) return false
            p = p.previousZ

            if (n.idx != ear.previous!!.idx && n.idx != ear.next!!.idx && pointInEar(
                    n.getX(),
                    n.getY(),
                    ear.previous!!.getX(),
                    ear.previous!!.getY(),
                    ear.getX(),
                    ear.getY(),
                    ear.next!!.getX(),
                    ear.next!!.getY()
                )
                && (area(
                    n.previous!!.getX(),
                    n.previous!!.getY(),
                    n.getX(),
                    n.getY(),
                    n.next!!.getX(),
                    n.next!!.getY()
                )
                        >= 0)
            ) return false
            n = n.nextZ
        }

        // first look for points inside the triangle in decreasing z-order
        while (p != null && Long.compareUnsigned(p.morton, minZ) >= 0) {
            if (p.idx != ear.previous!!.idx && p.idx != ear.next!!.idx && pointInEar(
                    p.getX(),
                    p.getY(),
                    ear.previous!!.getX(),
                    ear.previous!!.getY(),
                    ear.getX(),
                    ear.getY(),
                    ear.next!!.getX(),
                    ear.next!!.getY()
                )
                && (area(
                    p.previous!!.getX(),
                    p.previous!!.getY(),
                    p.getX(),
                    p.getY(),
                    p.next!!.getX(),
                    p.next!!.getY()
                )
                        >= 0)
            ) {
                return false
            }
            p = p.previousZ
        }
        // then look for points in increasing z-order
        while (n != null && Long.compareUnsigned(n.morton, maxZ) <= 0) {
            if (n.idx != ear.previous!!.idx && n.idx != ear.next!!.idx && pointInEar(
                    n.getX(),
                    n.getY(),
                    ear.previous!!.getX(),
                    ear.previous!!.getY(),
                    ear.getX(),
                    ear.getY(),
                    ear.next!!.getX(),
                    ear.next!!.getY()
                )
                && (area(
                    n.previous!!.getX(),
                    n.previous!!.getY(),
                    n.getX(),
                    n.getY(),
                    n.next!!.getX(),
                    n.next!!.getY()
                )
                        >= 0)
            ) {
                return false
            }
            n = n.nextZ
        }
        return true
    }

    /** Iterate through all polygon nodes and remove small local self-intersections *  */
    private fun cureLocalIntersections(
        startNode: Node, tessellation: MutableList<Triangle>, mortonOptimized: Boolean
    ): Node {
        var startNode = startNode
        var node: Node? = startNode
        var nextNode: Node?
        do {
            nextNode = node!!.next
            val a = node.previous
            val b = nextNode!!.next

            // a self-intersection where edge (v[i-1],v[i]) intersects (v[i+1],v[i+2])
            if (isVertexEquals(a!!, b!!) == false && linesIntersect(
                    a.getX(),
                    a.getY(),
                    node.getX(),
                    node.getY(),
                    nextNode.getX(),
                    nextNode.getY(),
                    b.getX(),
                    b.getY()
                )
                && isLocallyInside(a, b)
                && isLocallyInside(b, a) // this call is expensive so do it last
                && isIntersectingPolygon(a, a.getX(), a.getY(), b.getX(), b.getY()) == false
            ) {
                // compute edges from polygon
                val abFromPolygon =
                    if (a.next === node)
                        a.isNextEdgeFromPolygon
                    else
                        isEdgeFromPolygon(a, node, mortonOptimized)
                val bcFromPolygon =
                    if (node.next === b)
                        node.isNextEdgeFromPolygon
                    else
                        isEdgeFromPolygon(node, b, mortonOptimized)
                val caFromPolygon =
                    if (b.next === a) b.isNextEdgeFromPolygon else isEdgeFromPolygon(
                        a,
                        b,
                        mortonOptimized
                    )
                tessellation.add(Triangle(a, abFromPolygon, node, bcFromPolygon, b, caFromPolygon))
                // Return the triangulated vertices to the tessellation
                tessellation.add(Triangle(a, abFromPolygon, node, bcFromPolygon, b, caFromPolygon))

                // remove two nodes involved
                removeNode(node, caFromPolygon)
                removeNode(node.next!!, caFromPolygon)
                startNode = b
                node = startNode
            }
            node = node.next
        } while (node !== startNode)

        return node
    }

    /**
     * Attempt to split a polygon and independently triangulate each side. Return true if the polygon
     * was splitted *
     */
    private fun splitEarcut(
        polygon: Any,
        start: Node,
        tessellation: MutableList<Triangle>,
        mortonOptimized: Boolean,
        monitor: Monitor?,
        depth: Int
    ): Boolean {
        // Search for a valid diagonal that divides the polygon into two.
        var searchNode: Node? = start
        do {
            val nextNode = searchNode!!.next
            var diagonal = nextNode!!.next
            while (diagonal !== searchNode!!.previous) {
                if (searchNode.idx != diagonal!!.idx && isValidDiagonal(searchNode, diagonal)) {
                    // Split the polygon into two at the point of the diagonal
                    var splitNode: Node? =
                        splitPolygon(
                            searchNode,
                            diagonal,
                            isEdgeFromPolygon(searchNode, diagonal, mortonOptimized)
                        )
                    // Filter the resulting polygon.
                    searchNode = filterPoints(searchNode, searchNode.next)
                    splitNode = filterPoints(splitNode, splitNode!!.next)
                    // Attempt to earcut both of the resulting polygons
                    if (mortonOptimized) {
                        sortByMortonWithReset(searchNode)
                        sortByMortonWithReset(splitNode)
                    }
                    notifyMonitorSplit(depth, monitor, searchNode, splitNode)
                    earcutLinkedList(
                        polygon, searchNode, tessellation, State.INIT, mortonOptimized, monitor, depth
                    )
                    earcutLinkedList(
                        polygon, splitNode, tessellation, State.INIT, mortonOptimized, monitor, depth
                    )
                    notifyMonitorSplitEnd(depth, monitor)
                    // Finish the iterative search
                    return true
                }
                diagonal = diagonal.next
            }
            searchNode = searchNode.next
        } while (searchNode !== start)
        // if there is some area left, we failed
        return signedArea(start, start) == 0.0
    }

    /** Computes if edge defined by a and b overlaps with a polygon edge *  */
    private fun checkIntersection(a: Node?, isMorton: Boolean) {
        var next = a!!.next
        do {
            var innerNext = next!!.next
            if (isMorton) {
                mortonCheckIntersection(next, innerNext!!)
            } else {
                do {
                    checkIntersectionPoint(next, innerNext!!)
                    innerNext = innerNext.next
                } while (innerNext !== next.previous)
            }
            next = next.next
        } while (next !== a.previous)
    }

    /**
     * Uses morton code for speed to determine whether or not and edge defined by a and b overlaps
     * with a polygon edge
     */
    private fun mortonCheckIntersection(a: Node, b: Node) {
        // edge bbox (flip the bits so negative encoded values are < positive encoded values)
        val minTX: Int = StrictMath.min(a.x, a.next!!.x) xor -0x80000000
        val minTY: Int = StrictMath.min(a.y, a.next!!.y) xor -0x80000000
        val maxTX: Int = StrictMath.max(a.x, a.next!!.x) xor -0x80000000
        val maxTY: Int = StrictMath.max(a.y, a.next!!.y) xor -0x80000000

        // z-order range for the current edge;
        val minZ: Long = BitUtil.interleave(minTX, minTY)
        val maxZ: Long = BitUtil.interleave(maxTX, maxTY)

        // now make sure we don't have other points inside the potential ear;

        // look for points inside edge in both directions
        var p = b.previousZ
        var n = b.nextZ
        while (p != null && Long.compareUnsigned(
                p.morton,
                minZ
            ) >= 0 && n != null && Long.compareUnsigned(n.morton, maxZ) <= 0
        ) {
            checkIntersectionPoint(p, a)
            p = p.previousZ
            checkIntersectionPoint(n, a)
            n = n.nextZ
        }

        // first look for points inside the edge in decreasing z-order
        while (p != null && Long.compareUnsigned(p.morton, minZ) >= 0) {
            checkIntersectionPoint(p, a)
            p = p.previousZ
        }
        // then look for points in increasing z-order
        while (n != null && Long.compareUnsigned(n.morton, maxZ) <= 0) {
            checkIntersectionPoint(n, a)
            n = n.nextZ
        }
    }

    private fun checkIntersectionPoint(a: Node, b: Node) {
        if (a === b) {
            return
        }

        if (max(a.getY(), a.next!!.getY()) <= min(b.getY(), b.next!!.getY()) || min(
                a.getY(),
                a.next!!.getY()
            ) >= max(b.getY(), b.next!!.getY()) || max(a.getX(), a.next!!.getX()) <= min(
                b.getX(),
                b.next!!.getX()
            ) || min(a.getX(), a.next!!.getX()) >= max(b.getX(), b.next!!.getX())
        ) {
            return
        }

        if (GeoUtils.lineCrossesLine(
                a.getX(),
                a.getY(),
                a.next!!.getX(),
                a.next!!.getY(),
                b.getX(),
                b.getY(),
                b.next!!.getX(),
                b.next!!.getY()
            )
        ) {
            // Line AB represented as a1x + b1y = c1
            val a1 = a.next!!.getY() - a.getY()
            val b1 = a.getX() - a.next!!.getX()
            val c1 = a1 * (a.getX()) + b1 * (a.getY())

            // Line CD represented as a2x + b2y = c2
            val a2 = b.next!!.getY() - b.getY()
            val b2 = b.getX() - b.next!!.getX()
            val c2 = a2 * (b.getX()) + b2 * (b.getY())

            val determinant = a1 * b2 - a2 * b1

            require(determinant != 0.0)

            val x = (b2 * c1 - b1 * c2) / determinant
            val y = (a1 * c2 - a2 * c1) / determinant

            throw IllegalArgumentException("Polygon self-intersection at lat=$y lon=$x")
        }
        require(
            !(a.isNextEdgeFromPolygon
                    && b.isNextEdgeFromPolygon
                    && GeoUtils.lineOverlapLine(
                a.getX(),
                a.getY(),
                a.next!!.getX(),
                a.next!!.getY(),
                b.getX(),
                b.getY(),
                b.next!!.getX(),
                b.next!!.getY()
            ))
        ) { "Polygon ring self-intersection at lat=" + a.getY() + " lon=" + a.getX() }
    }

    /** Computes if edge defined by a and b overlaps with a polygon edge *  */
    private fun isEdgeFromPolygon(a: Node, b: Node, isMorton: Boolean): Boolean {
        if (isMorton) {
            return isMortonEdgeFromPolygon(a, b)
        }
        var next: Node? = a
        do {
            if (isPointInLine(next!!, next.next!!, a) && isPointInLine(
                    next,
                    next.next!!,
                    b
                )
            ) {
                return next.isNextEdgeFromPolygon
            }
            if (isPointInLine(next, next.previous!!, a) && isPointInLine(
                    next,
                    next.previous!!,
                    b
                )
            ) {
                return next.previous!!.isNextEdgeFromPolygon
            }
            next = next.next
        } while (next !== a)
        return false
    }

    /**
     * Uses morton code for speed to determine whether or not and edge defined by a and b overlaps
     * with a polygon edge
     */
    private fun isMortonEdgeFromPolygon(a: Node, b: Node): Boolean {
        // edge bbox (flip the bits so negative encoded values are < positive encoded values)
        val minTX: Int = StrictMath.min(a.x, b.x) xor -0x80000000
        val minTY: Int = StrictMath.min(a.y, b.y) xor -0x80000000
        val maxTX: Int = StrictMath.max(a.x, b.x) xor -0x80000000
        val maxTY: Int = StrictMath.max(a.y, b.y) xor -0x80000000

        // z-order range for the current edge;
        val minZ: Long = BitUtil.interleave(minTX, minTY)
        val maxZ: Long = BitUtil.interleave(maxTX, maxTY)

        // now make sure we don't have other points inside the potential ear;

        // look for points inside edge in both directions
        var p = a.previousZ
        var n = a.nextZ
        while (p != null && Long.compareUnsigned(
                p.morton,
                minZ
            ) >= 0 && n != null && Long.compareUnsigned(n.morton, maxZ) <= 0
        ) {
            if (isPointInLine(p, p.next!!, a) && isPointInLine(p, p.next!!, b)) {
                return p.isNextEdgeFromPolygon
            }
            if (isPointInLine(p, p.previous!!, a) && isPointInLine(p, p.previous!!, b)) {
                return p.previous!!.isNextEdgeFromPolygon
            }

            p = p.previousZ

            if (isPointInLine(n, n.next!!, a) && isPointInLine(n, n.next!!, b)) {
                return n.isNextEdgeFromPolygon
            }
            if (isPointInLine(n, n.previous!!, a) && isPointInLine(n, n.previous!!, b)) {
                return n.previous!!.isNextEdgeFromPolygon
            }

            n = n.nextZ
        }

        // first look for points inside the edge in decreasing z-order
        while (p != null && Long.compareUnsigned(p.morton, minZ) >= 0) {
            if (isPointInLine(p, p.next!!, a) && isPointInLine(p, p.next!!, b)) {
                return p.isNextEdgeFromPolygon
            }
            if (isPointInLine(p, p.previous!!, a) && isPointInLine(p, p.previous!!, b)) {
                return p.previous!!.isNextEdgeFromPolygon
            }
            p = p.previousZ
        }
        // then look for points in increasing z-order
        while (n != null && Long.compareUnsigned(n.morton, maxZ) <= 0) {
            if (isPointInLine(n, n.next!!, a) && isPointInLine(n, n.next!!, b)) {
                return n.isNextEdgeFromPolygon
            }
            if (isPointInLine(n, n.previous!!, a) && isPointInLine(n, n.previous!!, b)) {
                return n.previous!!.isNextEdgeFromPolygon
            }
            n = n.nextZ
        }
        return false
    }

    private fun isPointInLine(a: Node, b: Node, point: Node): Boolean {
        return isPointInLine(a, b, point.getX(), point.getY())
    }

    /** returns true if the lon, lat point is colinear w/ the provided a and b point  */
    private fun isPointInLine(
        a: Node, b: Node, lon: Double, lat: Double
    ): Boolean {
        val dxc = lon - a.getX()
        val dyc = lat - a.getY()

        val dxl = b.getX() - a.getX()
        val dyl = b.getY() - a.getY()

        if (dxc * dyl - dyc * dxl == 0.0) {
            return if (abs(dxl) >= abs(dyl)) {
                if (dxl > 0) a.getX() <= lon && lon <= b.getX() else b.getX() <= lon && lon <= a.getX()
            } else {
                if (dyl > 0) a.getY() <= lat && lat <= b.getY() else b.getY() <= lat && lat <= a.getY()
            }
        }
        return false
    }

    /** Links two polygon vertices using a bridge. *  */
    private fun splitPolygon(a: Node, b: Node, edgeFromPolygon: Boolean): Node {
        val a2 = Node(a)
        val b2 = Node(b)
        val an = a.next
        val bp = b.previous

        a.next = b
        a.isNextEdgeFromPolygon = edgeFromPolygon
        a.nextZ = b
        b.previous = a
        b.previousZ = a
        a2.next = an
        a2.nextZ = an
        an!!.previous = a2
        an.previousZ = a2
        b2.next = a2
        b2.isNextEdgeFromPolygon = edgeFromPolygon
        b2.nextZ = a2
        a2.previous = b2
        a2.previousZ = b2
        bp!!.next = b2
        bp.nextZ = b2

        return b2
    }

    /**
     * Determines whether a diagonal between two polygon nodes lies within a polygon interior. (This
     * determines the validity of the ray.) *
     */
    private fun isValidDiagonal(a: Node, b: Node): Boolean {
        if (a.next!!.idx == b.idx || a.previous!!.idx == b.idx // check next edges are locally visible
            || isLocallyInside(a.previous!!, b) == false || isLocallyInside(
                b.next!!,
                a
            ) == false // check polygons are CCW in both sides
            || isCWPolygon(a, b) == false || isCWPolygon(b, a) == false
        ) {
            return false
        }
        if (isVertexEquals(a, b)) {
            return true
        }
        return isLocallyInside(a, b)
                && isLocallyInside(b, a)
                && middleInsert(
            a,
            a.getX(),
            a.getY(),
            b.getX(),
            b.getY()
        ) // make sure we don't introduce collinear lines
                && area(
            a.previous!!.getX(),
            a.previous!!.getY(),
            a.getX(),
            a.getY(),
            b.getX(),
            b.getY()
        ) != 0.0 && area(a.getX(), a.getY(), b.getX(), b.getY(), b.next!!.getX(), b.next!!.getY()) != 0.0 && area(
            a.next!!.getX(), a.next!!.getY(), a.getX(), a.getY(), b.getX(), b.getY()
        ) != 0.0 && area(
            a.getX(),
            a.getY(),
            b.getX(),
            b.getY(),
            b.previous!!.getX(),
            b.previous!!.getY()
        ) != 0.0 // this call is expensive so do it last
                && isIntersectingPolygon(a, a.getX(), a.getY(), b.getX(), b.getY()) == false
    }

    /** Determine whether the polygon defined between node start and node end is CW  */
    private fun isCWPolygon(start: Node, end: Node): Boolean {
        // The polygon must be CW
        return signedArea(start, end) < 0
    }

    /** Determine the signed area between node start and node end  */
    private fun signedArea(start: Node, end: Node): Double {
        var next: Node? = start
        var windingSum = 0.0
        do {
            // compute signed area
            windingSum +=
                area(
                    next!!.getX(), next.getY(), next.next!!.getX(), next.next!!.getY(), end.getX(), end.getY()
                )
            next = next.next
        } while (next!!.next !== end)
        return windingSum
    }

    private fun isLocallyInside(a: Node, b: Node): Boolean {
        val area =
            area(
                a.previous!!.getX(), a.previous!!.getY(), a.getX(), a.getY(), a.next!!.getX(), a.next!!.getY()
            )
        return if (area == 0.0) {
            // parallel
            false
        } else if (area < 0) {
            // if a is cw
            (area(a.getX(), a.getY(), b.getX(), b.getY(), a.next!!.getX(), a.next!!.getY()) >= 0
                    && (area(a.getX(), a.getY(), a.previous!!.getX(), a.previous!!.getY(), b.getX(), b.getY())
                    >= 0))
        } else {
            // ccw
            (area(a.getX(), a.getY(), b.getX(), b.getY(), a.previous!!.getX(), a.previous!!.getY()) < 0
                    || area(a.getX(), a.getY(), a.next!!.getX(), a.next!!.getY(), b.getX(), b.getY()) < 0)
        }
    }

    /** Determine whether the middle point of a polygon diagonal is contained within the polygon  */
    private fun middleInsert(
        start: Node, x0: Double, y0: Double, x1: Double, y1: Double
    ): Boolean {
        var node: Node? = start
        var nextNode: Node?
        var lIsInside = false
        val lDx = (x0 + x1) / 2.0f
        val lDy = (y0 + y1) / 2.0f
        do {
            nextNode = node!!.next
            if (node.getY() > lDy != nextNode!!.getY() > lDy
                && (lDx
                        < (nextNode.getX() - node.getX())
                        * (lDy - node.getY())
                        / (nextNode.getY() - node.getY())
                        + node.getX())
            ) {
                lIsInside = !lIsInside
            }
            node = node.next
        } while (node !== start)
        return lIsInside
    }

    /** Determines if the diagonal of a polygon is intersecting with any polygon elements. *  */
    private fun isIntersectingPolygon(
        start: Node, x0: Double, y0: Double, x1: Double, y1: Double
    ): Boolean {
        var node: Node? = start
        var nextNode: Node?
        do {
            nextNode = node!!.next
            if (isVertexEquals(node, x0, y0) == false && isVertexEquals(
                    node,
                    x1,
                    y1
                ) == false
            ) {
                if (linesIntersect(
                        node.getX(), node.getY(), nextNode!!.getX(), nextNode.getY(), x0, y0, x1, y1
                    )
                ) {
                    return true
                }
            }
            node = nextNode
        } while (node !== start)

        return false
    }

    /** Determines whether two line segments intersect. *  */
    fun linesIntersect(
        aX0: Double,
        aY0: Double,
        aX1: Double,
        aY1: Double,
        bX0: Double,
        bY0: Double,
        bX1: Double,
        bY1: Double
    ): Boolean {
        return (area(aX0, aY0, aX1, aY1, bX0, bY0) > 0) != (area(aX0, aY0, aX1, aY1, bX1, bY1) > 0)
                && (area(bX0, bY0, bX1, bY1, aX0, aY0) > 0) != (area(bX0, bY0, bX1, bY1, aX1, aY1) > 0)
    }

    /** Interlinks polygon nodes in Z-Order. It reset the values on the z values*  */
    private fun sortByMortonWithReset(start: Node?) {
        var next: Node? = start
        do {
            next!!.previousZ = next.previous
            next.nextZ = next.next
            next = next.next
        } while (next !== start)
        sortByMorton(start)
    }

    /** Interlinks polygon nodes in Z-Order. *  */
    private fun sortByMorton(start: Node?) {
        start!!.previousZ!!.nextZ = null
        start.previousZ = null
        // Sort the generated ring using Z ordering.
        tathamSort(start)
    }

    /**
     * Simon Tatham's doubly-linked list O(n log n) mergesort see:
     * http://www.chiark.greenend.org.uk/~sgtatham/algorithms/listsort.html
     */
    private fun tathamSort(list: Node?) {
        var list = list
        var p: Node?
        var q: Node?
        var e: Node?
        var tail: Node?
        var i: Int
        var numMerges: Int
        var pSize: Int
        var qSize: Int
        var inSize = 1

        if (list == null) {
            return
        }

        do {
            p = list
            list = null
            tail = null
            // count number of merges in this pass
            numMerges = 0

            while (p != null) {
                ++numMerges
                // step 'insize' places along from p
                q = p
                i = 0
                pSize = 0
                while (i < inSize && q != null) {
                    ++i
                    ++pSize
                    q = q.nextZ
                }
                // if q hasn't fallen off end, we have two lists to merge
                qSize = inSize

                // now we have two lists; merge
                while (pSize > 0 || (qSize > 0 && q != null)) {
                    if (pSize != 0
                        && (qSize == 0 || q == null || Long.compareUnsigned(p!!.morton, q.morton) <= 0)
                    ) {
                        e = p
                        p = p!!.nextZ
                        --pSize
                    } else {
                        e = q
                        q = q!!.nextZ
                        --qSize
                    }

                    if (tail != null) {
                        tail.nextZ = e
                    } else {
                        list = e
                    }
                    // maintain reverse pointers
                    e.previousZ = tail
                    tail = e
                }
                // now p has stepped 'insize' places along, and q has too
                p = q
            }

            tail!!.nextZ = null
            inSize *= 2
        } while (numMerges > 1)
    }

    /** Eliminate colinear/duplicate points from the doubly linked list  */
    private fun filterPoints(start: Node?, end: Node?): Node? {
        var end = end
        if (start == null) {
            return start
        }

        if (end == null) {
            end = start
        }

        var node: Node? = start
        var nextNode: Node?
        var prevNode: Node?
        var continueIteration: Boolean

        do {
            continueIteration = false
            nextNode = node!!.next
            prevNode = node.previous
            // we can filter points when:
            // 1. they are the same
            // 2.- each one starts and ends in each other
            // 3.- they are collinear and both edges have the same value in .isNextEdgeFromPolygon
            // 4.-  they are collinear and second edge returns over the first edge
            if (isVertexEquals(node, nextNode!!)
                || isVertexEquals(prevNode!!, nextNode)
                || ((prevNode.isNextEdgeFromPolygon == node.isNextEdgeFromPolygon
                        || isPointInLine(prevNode, node, nextNode.getX(), nextNode.getY()))
                        && (area(
                    prevNode.getX(),
                    prevNode.getY(),
                    node.getX(),
                    node.getY(),
                    nextNode.getX(),
                    nextNode.getY()
                )
                        == 0.0))
            ) {
                // Remove the node
                removeNode(node, prevNode!!.isNextEdgeFromPolygon)
                end = prevNode
                node = end

                if (node === nextNode) {
                    break
                }
                continueIteration = true
            } else {
                node = nextNode
            }
        } while (continueIteration || node !== end)
        return end!!
    }

    /**
     * Creates a node and optionally links it with a previous node in a circular doubly-linked list
     */
    private fun insertNode(
        x: DoubleArray,
        y: DoubleArray,
        index: Int,
        vertexIndex: Int,
        lastNode: Node?,
        isGeo: Boolean
    ): Node {
        val node = Node(x, y, index, vertexIndex, isGeo)
        if (lastNode == null) {
            node.previous = node
            node.previousZ = node
            node.next = node
            node.nextZ = node
        } else {
            node.next = lastNode.next
            node.nextZ = lastNode.next
            node.previous = lastNode
            node.previousZ = lastNode
            lastNode.next!!.previous = node
            lastNode.nextZ!!.previousZ = node
            lastNode.next = node
            lastNode.nextZ = node
        }
        return node
    }

    /** Removes a node from the doubly linked list  */
    private fun removeNode(node: Node, edgeFromPolygon: Boolean) {
        node.next!!.previous = node.previous
        node.previous!!.next = node.next
        node.previous!!.isNextEdgeFromPolygon = edgeFromPolygon

        if (node.previousZ != null) {
            node.previousZ!!.nextZ = node.nextZ
        }
        if (node.nextZ != null) {
            node.nextZ!!.previousZ = node.previousZ
        }
    }

    /** Determines if two point vertices are equal. *  */
    private fun isVertexEquals(a: Node, b: Node): Boolean {
        return isVertexEquals(a, b.getX(), b.getY())
    }

    /** Determines if two point vertices are equal. *  */
    private fun isVertexEquals(a: Node, x: Double, y: Double): Boolean {
        return a.getX() == x && a.getY() == y
    }

    /** Compute signed area of triangle, negative means convex angle and positive reflex angle.  */
    private fun area(
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double,
        cX: Double,
        cY: Double
    ): Double {
        return (bY - aY) * (cX - bX) - (bX - aX) * (cY - bY)
    }

    /** Compute whether point is in a candidate ear  */
    private fun pointInEar(
        x: Double,
        y: Double,
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        cx: Double,
        cy: Double
    ): Boolean {
        return (cx - x) * (ay - y) - (ax - x) * (cy - y) >= 0 && (ax - x) * (by - y) - (bx - x) * (ay - y) >= 0 && (bx - x) * (cy - y) - (cx - x) * (by - y) >= 0
    }

    private fun getPoints(start: Node): MutableList<Point> {
        var node: Node? = start
        val points: ArrayList<Point> =
            ArrayList()
        do {
            points.add(Point(node!!.getY(), node.getX()))
            node = node.next
        } while (node !== start)
        return points
    }

    private fun notifyMonitorSplit(
        depth: Int, monitor: Monitor?, searchNode: Node?, diagonalNode: Node?
    ) {
        if (monitor != null) {
            check(!(searchNode == null || diagonalNode == null)) { "Invalid split provided to monitor" }
            monitor.startSplit("SPLIT[$depth]", getPoints(searchNode), getPoints(diagonalNode))
        }
    }

    private fun notifyMonitorSplitEnd(depth: Int, monitor: Monitor?) {
        if (monitor != null) {
            monitor.endSplit("SPLIT[$depth]")
        }
    }

    private fun notifyMonitor(
        state: State, depth: Int, monitor: Monitor?, start: Node?, tessellation: MutableList<Triangle>
    ) {
        if (monitor != null) {
            notifyMonitor(
                state.name + (if (depth == 0) "" else "[$depth]"), monitor, start, tessellation
            )
        }
    }

    private fun notifyMonitor(
        status: String, monitor: Monitor?, start: Node?, tessellation: MutableList<Triangle>
    ) {
        if (monitor != null) {
            if (start == null) {
                monitor.currentState(status, null, tessellation)
            } else {
                monitor.currentState(status, getPoints(start), tessellation)
            }
        }
    }

    /** state of the tessellated split - avoids recursion  */
    private enum class State {
        INIT,
        CURE,
        SPLIT
    }

    /**
     * Implementation of this interface will receive calls with internal data at each step of the
     * triangulation algorithm. This is of use for debugging complex cases, as well as gaining insight
     * into the way the algorithm works. Data provided includes a status string containing the current
     * mode, list of points representing the current linked-list of internal nodes used for
     * triangulation, and a list of triangles so far created by the algorithm.
     */
    interface Monitor {
        /** Each loop of the main earclip algorithm will call this with the current state  */
        fun currentState(
            status: String,
            points: MutableList<Point>?,
            tessellation: MutableList<Triangle>
        )

        /** When a new polygon split is entered for mode=SPLIT, this is called.  */
        fun startSplit(
            status: String,
            leftPolygon: MutableList<Point>,
            rightPolygon: MutableList<Point>
        )

        /** When a polygon split is completed, this is called.  */
        fun endSplit(status: String)

        companion object {
            const val FAILED: String = "FAILED"
            const val COMPLETED: String = "COMPLETED"
        }
    }

    /** Circular Doubly-linked list used for polygon coordinates  */
    class Node {
        // node index in the linked list
        val idx: Int

        // vertex index in the polygon
        private val vrtxIdx: Int

        // reference to the polygon for lat/lon values;
        private val polyX: DoubleArray
        private val polyY: DoubleArray

        // encoded x value
        val x: Int

        // encoded y value
        val y: Int

        // morton code for sorting
        val morton: Long

        // previous node
        var previous: Node?

        // next node
        var next: Node?

        // previous z node
        var previousZ: Node?

        // next z node
        var nextZ: Node?

        // if the edge from this node to the next node is part of the polygon edges
        var isNextEdgeFromPolygon: Boolean

        constructor(
            x: DoubleArray,
            y: DoubleArray,
            index: Int,
            vertexIndex: Int,
            isGeo: Boolean
        ) {
            this.idx = index
            this.vrtxIdx = vertexIndex
            this.polyX = x
            this.polyY = y
            // casting to float is safe as original values for non-geo are represented as floats
            this.y =
                if (isGeo) GeoEncodingUtils.encodeLatitude(polyY[vrtxIdx]) else XYEncodingUtils.encode(
                    polyY[vrtxIdx].toFloat()
                )
            this.x =
                if (isGeo) GeoEncodingUtils.encodeLongitude(polyX[vrtxIdx]) else XYEncodingUtils.encode(
                    polyX[vrtxIdx].toFloat()
                )
            this.morton = BitUtil.interleave(this.x xor -0x80000000, this.y xor -0x80000000)
            this.previous = null
            this.next = null
            this.previousZ = null
            this.nextZ = null
            this.isNextEdgeFromPolygon = true
        }

        /** simple deep copy constructor  */
        constructor(other: Node) {
            this.idx = other.idx
            this.vrtxIdx = other.vrtxIdx
            this.polyX = other.polyX
            this.polyY = other.polyY
            this.morton = other.morton
            this.x = other.x
            this.y = other.y
            this.previous = other.previous
            this.next = other.next
            this.previousZ = other.previousZ
            this.nextZ = other.nextZ
            this.isNextEdgeFromPolygon = other.isNextEdgeFromPolygon
        }

        /** get the x value  */
        fun getX(): Double {
            return polyX[vrtxIdx]
        }

        /** get the y value  */
        fun getY(): Double {
            return polyY[vrtxIdx]
        }

        override fun toString(): String {
            val builder = StringBuilder()
            if (this.previous == null) builder.append("||-")
            else builder.append(this.previous!!.idx).append(" <- ")
            builder.append(this.idx)
            if (this.next == null) builder.append(" -||")
            else builder.append(" -> ").append(this.next!!.idx)
            return builder.toString()
        }
    }

    /** Triangle in the tessellated mesh  */
    class Triangle(
        a: Node,
        isABfromPolygon: Boolean,
        b: Node,
        isBCfromPolygon: Boolean,
        c: Node,
        isCAfromPolygon: Boolean
    ) {
        var vertex: Array<Node> = arrayOf(a, b, c)
        var edgeFromPolygon: BooleanArray = booleanArrayOf(isABfromPolygon, isBCfromPolygon, isCAfromPolygon)

        /** get quantized x value for the given vertex  */
        fun getEncodedX(vertex: Int): Int {
            return this.vertex[vertex].x
        }

        /** get quantized y value for the given vertex  */
        fun getEncodedY(vertex: Int): Int {
            return this.vertex[vertex].y
        }

        /** get y value for the given vertex  */
        fun getY(vertex: Int): Double {
            return this.vertex[vertex].getY()
        }

        /** get x value for the given vertex  */
        fun getX(vertex: Int): Double {
            return this.vertex[vertex].getX()
        }

        /** get if edge is shared with the polygon for the given edge  */
        fun isEdgefromPolygon(startVertex: Int): Boolean {
            return edgeFromPolygon[startVertex]
        }

        /** pretty print the triangle vertices  */
        override fun toString(): String {
            val result =
                (vertex[0].x
                    .toString() + ", "
                        + vertex[0].y
                        + " ["
                        + edgeFromPolygon[0]
                        + "] "
                        + vertex[1].x
                        + ", "
                        + vertex[1].y
                        + " ["
                        + edgeFromPolygon[1]
                        + "] "
                        + vertex[2].x
                        + ", "
                        + vertex[2].y
                        + " ["
                        + edgeFromPolygon[2]
                        + "]")
            return result
        }
    }
}
