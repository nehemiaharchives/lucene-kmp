package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.DecodedTriangle.TYPE
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef

/**
 * A binary doc values format representation for [LatLonShape] and [XYShape].
 */
abstract class ShapeDocValues {
    /** the binary doc value */
    private val data: BytesRef

    /** the geometry comparator used to check relations */
    protected val shapeComparator: ShapeComparator

    /** the centroid of the shape docvalue */
    open lateinit var centroid: Geometry

    /** the bounding box of the shape docvalue */
    val boundingBox: Geometry

    /**
     * Creates a ShapeDocValues instance from a shape tessellation.
     *
     * @param tessellation The tessellation (must not be null)
     */
    protected constructor(tessellation: List<ShapeField.DecodedTriangle>) {
        this.data = computeBinaryValue(tessellation)
        this.shapeComparator = ShapeComparator(this.data)
        this.centroid = computeCentroid()
        this.boundingBox = computeBoundingBox()
    }

    /** Creates a ShapeDocValues instance from a given serialized value. */
    protected constructor(binaryValue: BytesRef) {
        this.data = BytesRef.deepCopyOf(binaryValue)
        this.shapeComparator = ShapeComparator(this.data)
        this.centroid = computeCentroid()
        this.boundingBox = computeBoundingBox()
    }

    /** returns the encoded doc values field as a [BytesRef] */
    internal fun binaryValue(): BytesRef {
        return data
    }

    /** Returns the number of terms (tessellated triangles) for this shape */
    fun numberOfTerms(): Int {
        return shapeComparator.numberOfTerms()
    }

    /** returns the min x value for the shape's bounding box */
    fun getEncodedMinX(): Int {
        return shapeComparator.getMinX()
    }

    /** returns the min y value for the shape's bounding box */
    fun getEncodedMinY(): Int {
        return shapeComparator.getMinY()
    }

    /** returns the max x value for the shape's bounding box */
    fun getEncodedMaxX(): Int {
        return shapeComparator.getMaxX()
    }

    /** returns the max y value for the shape's bounding box */
    fun getEncodedMaxY(): Int {
        return shapeComparator.getMaxY()
    }

    /** Retrieves the encoded x centroid location for the geometry(s) */
    protected fun getEncodedCentroidX(): Int {
        return shapeComparator.getCentroidX()
    }

    /** Retrieves the encoded y centroid location for the geometry(s) */
    protected fun getEncodedCentroidY(): Int {
        return shapeComparator.getCentroidY()
    }

    /** Retrieves the highest dimensional type for computing centroid. */
    fun getHighestDimension(): TYPE {
        return shapeComparator.getHighestDimension()
    }

    private fun computeBinaryValue(tessellation: List<ShapeField.DecodedTriangle>): BytesRef {
        try {
            // dfs order serialization
            val dfsSerialized = ArrayList<TreeNode>(tessellation.size)
            buildTree(tessellation, dfsSerialized)
            val w = Writer(dfsSerialized)
            return w.getBytesRef()
        } catch (e: Exception) {
            throw RuntimeException("Internal error building LatLonShapeDocValues. Got ", e)
        }
    }

    fun relate(component: Component2D): Relation {
        return shapeComparator.relate(component)
    }

    protected interface Encoder {
        fun encodeX(x: Double): Int
        fun encodeY(y: Double): Int
        fun decodeX(encoded: Int): Double
        fun decodeY(encoded: Int): Double
    }

    protected abstract fun getEncoder(): Encoder
    protected abstract fun computeCentroid(): Geometry
    protected abstract fun computeBoundingBox(): Geometry
    /*abstract fun getCentroid(): Geometry*/
    /*abstract fun getBoundingBox(): Geometry*/

    /** main entry point to build the tessellation tree */
    private fun buildTree(
        tessellation: List<ShapeField.DecodedTriangle>,
        dfsSerialized: MutableList<TreeNode>
    ): TreeNode? {
        if (tessellation.size == 1) {
            val t = tessellation[0]
            val node = TreeNode(t)
            if (t.type == TYPE.LINE) {
                if (node.length != 0.0) {
                    node.midX /= node.length
                    node.midY /= node.length
                }
            } else if (t.type == TYPE.TRIANGLE) {
                if (node.signedArea != 0.0) {
                    node.midX /= node.signedArea
                    node.midY /= node.signedArea
                }
            }
            node.highestType = t.type
            dfsSerialized.add(node)
            return node
        }

        val triangles = Array<TreeNode>(tessellation.size) { TreeNode(tessellation[it]) }
        var i = 0
        var minY = Int.MAX_VALUE
        var minX = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        var maxX = Int.MIN_VALUE

        // running stats for computing centroid
        var totalSignedArea = 0.0
        var totalLength = 0.0
        var numXPnt = 0.0
        var numYPnt = 0.0
        var numXLin = 0.0
        var numYLin = 0.0
        var numXPly = 0.0
        var numYPly = 0.0
        var highestType = TYPE.POINT

        for (t in tessellation) {
            val node = triangles[i++]
            minY = kotlin.math.min(minY, node.minY)
            minX = kotlin.math.min(minX, node.minX)
            maxY = kotlin.math.max(maxY, node.maxY)
            maxX = kotlin.math.max(maxX, node.maxX)

            totalSignedArea += node.signedArea
            totalLength += node.length
            if (t.type == TYPE.POINT) {
                numXPnt += node.midX
                numYPnt += node.midY
            } else if (t.type == TYPE.LINE) {
                if (highestType == TYPE.POINT) {
                    highestType = TYPE.LINE
                }
                numXLin += node.midX
                numYLin += node.midY
            } else {
                if (highestType != TYPE.TRIANGLE) {
                    highestType = TYPE.TRIANGLE
                }
                numXPly += node.midX
                numYPly += node.midY
            }
        }

        val root = createTree(triangles, 0, triangles.size - 1, false, null, dfsSerialized)

        // pull up min values for the root node so the bbox is consistent
        root!!.minY = minY
        root.minX = minX

        // set the highest dimensional type
        root.highestType = highestType

        // compute centroid values for the root node so the centroid is consistent
        if (highestType == TYPE.POINT) {
            root.midX = numXPnt / i
            root.midY = numYPnt / i
        } else if (highestType == TYPE.LINE) {
            root.midX = numXLin
            root.midY = numYLin
            if (totalLength != 0.0) {
                root.midX /= totalLength
                root.midY /= totalLength
            }
        } else {
            root.midX = numXPly
            root.midY = numYPly
            if (totalSignedArea != 0.0) {
                root.midX /= totalSignedArea
                root.midY /= totalSignedArea
            }
        }

        return root
    }

    /** creates the tree */
    private fun createTree(
        triangles: Array<TreeNode>,
        low: Int,
        high: Int,
        splitX: Boolean,
        parent: TreeNode?,
        dfsSerialized: MutableList<TreeNode>
    ): TreeNode? {
        if (low > high) {
            return null
        }
        val mid = (low + high) ushr 1
        if (low < high) {
            val comparator =
                if (splitX) {
                    Comparator<TreeNode> { left, right ->
                        val c = left.minX.compareTo(right.minX)
                        if (c != 0) c else left.maxX.compareTo(right.maxX)
                    }
                } else {
                    Comparator<TreeNode> { left, right ->
                        val c = left.minY.compareTo(right.minY)
                        if (c != 0) c else left.maxY.compareTo(right.maxY)
                    }
                }
            ArrayUtil.select(triangles, low, high + 1, mid, comparator)
        }
        val newNode = triangles[mid]
        dfsSerialized.add(newNode)
        newNode.parent = parent

        newNode.left = createTree(triangles, low, mid - 1, !splitX, newNode, dfsSerialized)
        newNode.right = createTree(triangles, mid + 1, high, !splitX, newNode, dfsSerialized)

        if (newNode.left != null) {
            newNode.minX = kotlin.math.min(newNode.minX, newNode.left!!.minX)
            newNode.minY = kotlin.math.min(newNode.minY, newNode.left!!.minY)
            newNode.maxX = kotlin.math.max(newNode.maxX, newNode.left!!.maxX)
            newNode.maxY = kotlin.math.max(newNode.maxY, newNode.left!!.maxY)
        }
        if (newNode.right != null) {
            newNode.minX = kotlin.math.min(newNode.minX, newNode.right!!.minX)
            newNode.minY = kotlin.math.min(newNode.minY, newNode.right!!.minY)
            newNode.maxX = kotlin.math.max(newNode.maxX, newNode.right!!.maxX)
            newNode.maxY = kotlin.math.max(newNode.maxY, newNode.right!!.maxY)
        }

        if (newNode.left != null) {
            newNode.left!!.byteSize += vLongSize(newNode.maxX.toLong() - newNode.left!!.minX)
            newNode.left!!.byteSize += vLongSize(newNode.maxY.toLong() - newNode.left!!.minY)
            newNode.left!!.byteSize += vLongSize(newNode.maxX.toLong() - newNode.left!!.maxX)
            newNode.left!!.byteSize += vLongSize(newNode.maxY.toLong() - newNode.left!!.maxY)
            newNode.left!!.byteSize += computeComponentSize(newNode.left!!, newNode.maxX, newNode.maxY)
            newNode.byteSize += vIntSize(newNode.left!!.byteSize) + newNode.left!!.byteSize
        }
        if (newNode.right != null) {
            newNode.right!!.byteSize += vLongSize(newNode.maxX.toLong() - newNode.right!!.minX)
            newNode.right!!.byteSize += vLongSize(newNode.maxY.toLong() - newNode.right!!.minY)
            newNode.right!!.byteSize += vLongSize(newNode.maxX.toLong() - newNode.right!!.maxX)
            newNode.right!!.byteSize += vLongSize(newNode.maxY.toLong() - newNode.right!!.maxY)
            newNode.right!!.byteSize += computeComponentSize(newNode.right!!, newNode.maxX, newNode.maxY)
            newNode.byteSize += vIntSize(newNode.right!!.byteSize) + newNode.right!!.byteSize
        }
        return newNode
    }

    private fun computeComponentSize(node: TreeNode, maxX: Int, maxY: Int): Int {
        var size = 0
        val t = node.triangle
        size += vLongSize(maxX.toLong() - t.aX)
        size += vLongSize(maxY.toLong() - t.aY)
        if (t.type == TYPE.LINE || t.type == TYPE.TRIANGLE) {
            size += vLongSize(maxX.toLong() - t.bX)
            size += vLongSize(maxY.toLong() - t.bY)
        }
        if (t.type == TYPE.TRIANGLE) {
            size += vLongSize(maxX.toLong() - t.cX)
            size += vLongSize(maxY.toLong() - t.cY)
        }
        return size
    }

    /**
     * Builds an in-memory binary tree of tessellated triangles.
     *
     * This class is ported for side-by-side parity with Java ShapeDocValues.
     */
    private inner class TreeNode internal constructor(t: ShapeField.DecodedTriangle) {
        /** the triangle for this tree node */
        internal val triangle: ShapeField.DecodedTriangle = t

        /** centroid running stats (in encoded space) for this tree node */
        internal var midX: Double
        internal var midY: Double

        /** Units are encoded space. This is only used to compute centroid in encoded space. */
        internal val signedArea: Double

        /** Units are encoded space. This is only used to compute centroid in encoded space. */
        internal val length: Double
        internal var highestType: TYPE? = null

        /** the bounding box for the tree */
        internal var minX: Int
        internal var maxX: Int
        internal var minY: Int
        internal var maxY: Int

        internal var left: TreeNode? = null
        internal var right: TreeNode? = null
        internal var parent: TreeNode? = null

        internal var byteSize = 1

        init {
            minX = minOf(t.aX, t.bX, t.cX)
            minY = minOf(t.aY, t.bY, t.cY)
            maxX = maxOf(t.aX, t.bX, t.cX)
            maxY = maxOf(t.aY, t.bY, t.cY)

            val encoder = getEncoder()
            val ax = encoder.decodeX(t.aX)
            val ay = encoder.decodeY(t.aY)
            if (t.type == TYPE.POINT) {
                midX = ax
                midY = ay
                signedArea = 0.0
                length = 0.0
            } else if (t.type == TYPE.LINE || t.type == TYPE.TRIANGLE) {
                val bx = encoder.decodeX(t.bX)
                val by = encoder.decodeY(t.bY)
                if (t.type == TYPE.LINE) {
                    length = kotlin.math.hypot(ax - bx, ay - by)
                    midX = (0.5 * (ax + bx)) * length
                    midY = (0.5 * (ay + by)) * length
                    signedArea = 0.0
                } else {
                    val cx = encoder.decodeX(t.cX)
                    val cy = encoder.decodeY(t.cY)
                    signedArea = kotlin.math.abs(0.5 * ((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)))
                    midX = ((ax + bx + cx) / 3.0) * signedArea
                    midY = ((ay + by + cy) / 3.0) * signedArea
                    length = 0.0
                }
            } else {
                throw IllegalArgumentException("invalid type [${t.type}] found")
            }
        }
    }

    /** Writes data from a ShapeDocValues field to a data output array */
    private inner class Writer internal constructor(dfsSerialized: MutableList<TreeNode>) {
        private val output: ByteBuffersDataOutput
        private var bytesRef: BytesRef

        init {
            output = ByteBuffersDataOutput()
            writeTree(dfsSerialized)
            val outSize = output.size()
            require(outSize <= Int.MAX_VALUE.toLong()) { "ShapeDocValues writer output too large: $outSize" }
            bytesRef = BytesRef(output.toArrayCopy(), 0, outSize.toInt())
        }

        fun getBytesRef(): BytesRef {
            return bytesRef
        }

        private fun writeTree(dfsSerialized: MutableList<TreeNode>) {
            // write encoding version
            output.writeByte(VERSION)
            // write number of terms (triangles)
            output.writeVInt(dfsSerialized.size)
            // write root
            val root = dfsSerialized.removeAt(0)
            // write bounding box; convert to variable long by translating
            val encoder = getEncoder()
            output.writeVLong(root.minX.toLong() - Int.MIN_VALUE)
            output.writeVLong(root.maxX.toLong() - Int.MIN_VALUE)
            output.writeVLong(root.minY.toLong() - Int.MIN_VALUE)
            output.writeVLong(root.maxY.toLong() - Int.MIN_VALUE)

            // write centroid
            output.writeVLong(encoder.encodeX(root.midX).toLong() - Int.MIN_VALUE)
            output.writeVLong(encoder.encodeY(root.midY).toLong() - Int.MIN_VALUE)
            // write highest dimensional type
            output.writeVInt(root.highestType!!.ordinal)
            // write header
            writeHeader(root)
            // write component
            writeComponent(root, root.maxX, root.maxY)

            for (t in dfsSerialized) {
                writeNode(t)
            }
        }

        /** Serializes a node in the most compact way possible */
        private fun writeNode(node: TreeNode) {
            // write subtree total size
            output.writeVInt(node.byteSize) // variable
            // write max bounds
            writeBounds(node) // variable
            writeHeader(node) // 1 byte
            writeComponent(node, node.parent!!.maxX, node.parent!!.maxY) // variable
        }

        /** Serializes a component (POINT, LINE, or TRIANGLE) in the most compact way possible */
        private fun writeComponent(node: TreeNode, pMaxX: Int, pMaxY: Int) {
            val t = node.triangle
            output.writeVLong(pMaxX.toLong() - t.aX)
            output.writeVLong(pMaxY.toLong() - t.aY)
            if (t.type == TYPE.LINE || t.type == TYPE.TRIANGLE) {
                output.writeVLong(pMaxX.toLong() - t.bX)
                output.writeVLong(pMaxY.toLong() - t.bY)
            }
            if (t.type == TYPE.TRIANGLE) {
                output.writeVLong(pMaxX.toLong() - t.cX)
                output.writeVLong(pMaxY.toLong() - t.cY)
            }
        }

        /** Writes the header metadata in the most compact way possible */
        private fun writeHeader(node: TreeNode) {
            var header = 0x00
            if (node.right != null) {
                header = header or 0x01
            }
            if (node.left != null) {
                header = header or 0x02
            }
            if (node.triangle.type == TYPE.POINT) {
                header = header or 0x04
            } else if (node.triangle.type == TYPE.LINE) {
                header = header or 0x08
            }
            if (node.triangle.ab) {
                header = header or 0x10
            }
            if (node.triangle.bc) {
                header = header or 0x20
            }
            if (node.triangle.ca) {
                header = header or 0x40
            }
            output.writeVInt(header)
        }

        private fun writeBounds(node: TreeNode) {
            output.writeVLong(node.parent!!.maxX.toLong() - node.minX)
            output.writeVLong(node.parent!!.maxY.toLong() - node.minY)
            output.writeVLong(node.parent!!.maxX.toLong() - node.maxX)
            output.writeVLong(node.parent!!.maxY.toLong() - node.maxY)
        }
    }

    /** Reads values from a ShapeDocValues Field */
    private inner class Reader(binaryValue: BytesRef) : DataInput() {
        /** data input array to read the docvalue data */
        private val data: ByteArrayDataInput = ByteArrayDataInput(binaryValue.bytes, binaryValue.offset, binaryValue.length)

        // scratch classes
        private val bbox: BBox = BBox(Int.MAX_VALUE, -Int.MAX_VALUE, Int.MAX_VALUE, -Int.MAX_VALUE)

        override fun clone(): Reader {
            return Reader(this@ShapeDocValues.data)
        }

        /** rewinds the buffer to the beginning */
        internal fun rewind() {
            data.rewind()
        }

        /** reads the component bounding box */
        internal fun readBBox(): BBox {
            return bbox.reset(
                (data.readVLong() + Int.MIN_VALUE).toInt(),
                (data.readVLong() + Int.MIN_VALUE).toInt(),
                (data.readVLong() + Int.MIN_VALUE).toInt(),
                (data.readVLong() + Int.MIN_VALUE).toInt()
            )
        }

        /** resets the scratch bounding box */
        internal fun resetBBox(minX: Int, maxX: Int, minY: Int, maxY: Int): BBox {
            return bbox.reset(minX, maxX, minY, maxY)
        }

        internal fun getPosition(): Int {
            return data.position
        }

        override fun readByte(): Byte {
            return data.readByte()
        }

        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            data.readBytes(b, offset, len)
        }

        override fun skipBytes(numBytes: Long) {
            data.skipBytes(numBytes)
        }

        private val header: Header = Header()

        internal fun readType(bits: Int): TYPE = header.readType(bits)
        internal fun readHasLeftSubtree(bits: Int): Boolean = header.readHasLeftSubtree(bits)
        internal fun readHasRightSubtree(bits: Int): Boolean = header.readHasRightSubtree(bits)

        private inner class Header {
            /** reads the component type (POINT, LINE, TRIANGLE) */
            fun readType(bits: Int): TYPE {
                if ((bits and 0x04) == 0x04) {
                    return TYPE.POINT
                }
                if ((bits and 0x08) == 0x08) {
                    return TYPE.LINE
                }
                return TYPE.TRIANGLE
            }

            /** reads if the left subtree is null */
            fun readHasLeftSubtree(bits: Int): Boolean {
                return (bits and 0x02) == 0x02
            }

            /** reads if the right subtree is null */
            fun readHasRightSubtree(bits: Int): Boolean {
                return (bits and 0x01) == 0x01
            }
        }

        internal inner class BBox(minX: Int, maxX: Int, minY: Int, maxY: Int) :
            SpatialQuery.EncodedRectangle(minX, maxX, minY, maxY, false) {
            /** resets bounding box values */
            fun reset(minX: Int, maxX: Int, minY: Int, maxY: Int): BBox {
                this.minX = minX
                this.maxX = maxX
                this.minY = minY
                this.maxY = maxY
                this.wrapsCoordinateSystem = false
                return this
            }
        }
    }

    /** Shape Comparator class provides tree traversal relation methods */
    protected inner class ShapeComparator(binaryValue: BytesRef) {
        private var dvReader: Reader = Reader(binaryValue)
        private val encoder: Encoder = getEncoder()
        private val version: Byte = dvReader.readByte()
        private val numberOfTerms: Int = dvReader.readVInt()
        private val boundingBox: Reader.BBox = dvReader.readBBox()
        private val centroidX: Int = (dvReader.readVLong() + Int.MIN_VALUE).toInt()
        private val centroidY: Int = (dvReader.readVLong() + Int.MIN_VALUE).toInt()
        private val highestDimension: TYPE = TYPE.entries[dvReader.readVInt()]

        init {
            dvReader.rewind()
        }

        fun numberOfTerms(): Int {
            return numberOfTerms
        }

        fun getMinX(): Int {
            return boundingBox.minX
        }

        fun getMinY(): Int {
            return boundingBox.minY
        }

        fun getMaxX(): Int {
            return boundingBox.maxX
        }

        fun getMaxY(): Int {
            return boundingBox.maxY
        }

        fun getHighestDimension(): TYPE {
            return highestDimension
        }

        fun getCentroidX(): Int {
            return centroidX
        }

        fun getCentroidY(): Int {
            return centroidY
        }

        private fun skipCentroid() {
            dvReader.readVLong()
            dvReader.readVLong()
        }

        private fun skipHighestDimension() {
            dvReader.readVInt()
        }

        fun relate(query: Component2D): Relation {
            try {
                dvReader.readByte()
                dvReader.readVInt()
                val bbox = dvReader.readBBox()
                val tMinX = bbox.minX
                val tMaxX = bbox.maxX
                val tMaxY = bbox.maxY

                var r = query.relate(
                    encoder.decodeX(bbox.minX),
                    encoder.decodeX(bbox.maxX),
                    encoder.decodeY(bbox.minY),
                    encoder.decodeY(bbox.maxY)
                )
                if (r != Relation.CELL_CROSSES_QUERY) {
                    return r
                }

                skipCentroid()
                skipHighestDimension()
                val headerBits = dvReader.readVInt()
                val x = (tMaxX - dvReader.readVLong()).toInt()
                if (relateComponent(dvReader.readType(headerBits), bbox, tMaxX, tMaxY, encoder.decodeX(x), query) == Relation.CELL_CROSSES_QUERY) {
                    return Relation.CELL_CROSSES_QUERY
                }
                r = Relation.CELL_OUTSIDE_QUERY

                if (dvReader.readHasLeftSubtree(headerBits)) {
                    if ((relate(query, false, tMaxX, tMaxY, dvReader.readVInt())) == Relation.CELL_CROSSES_QUERY) {
                        return Relation.CELL_CROSSES_QUERY
                    }
                }
                if (dvReader.readHasRightSubtree(headerBits)) {
                    if (query.maxX >= encoder.decodeX(tMinX)) {
                        if ((relate(query, false, tMaxX, tMaxY, dvReader.readVInt())) == Relation.CELL_CROSSES_QUERY) {
                            return Relation.CELL_CROSSES_QUERY
                        }
                    }
                }
                return r
            } finally {
                dvReader.rewind()
            }
        }

        private fun relate(
            queryComponent2D: Component2D,
            splitX: Boolean,
            pMaxX: Int,
            pMaxY: Int,
            nodeSize: Int
        ): Relation {
            val prePos = dvReader.getPosition()
            val tMinX = (pMaxX - dvReader.readVLong()).toInt()
            val tMinY = (pMaxY - dvReader.readVLong()).toInt()
            val tMaxX = (pMaxX - dvReader.readVLong()).toInt()
            val tMaxY = (pMaxY - dvReader.readVLong()).toInt()
            val headerBits = dvReader.readVInt()
            var remainingNodeSize = nodeSize - (dvReader.getPosition() - prePos)

            if (queryComponent2D.minX > encoder.decodeX(tMaxX)
                || queryComponent2D.minY > encoder.decodeY(tMaxY)
            ) {
                dvReader.skipBytes(remainingNodeSize.toLong())
                return Relation.CELL_OUTSIDE_QUERY
            }

            val x = (pMaxX - dvReader.readVLong()).toInt()
            val bbox = dvReader.resetBBox(tMinX, tMaxX, tMinY, tMaxY)
            if (relateComponent(
                    dvReader.readType(headerBits),
                    bbox,
                    pMaxX,
                    pMaxY,
                    encoder.decodeX(x),
                    queryComponent2D
                ) == Relation.CELL_CROSSES_QUERY
            ) {
                return Relation.CELL_CROSSES_QUERY
            }

            if (dvReader.readHasLeftSubtree(headerBits)) {
                if (relate(queryComponent2D, !splitX, tMaxX, tMaxY, dvReader.readVInt()) == Relation.CELL_CROSSES_QUERY) {
                    return Relation.CELL_CROSSES_QUERY
                }
            }

            if (dvReader.readHasRightSubtree(headerBits)) {
                val size = dvReader.readVInt()
                if ((!splitX && queryComponent2D.maxY >= encoder.decodeY(tMinY))
                    || (splitX && queryComponent2D.maxX >= encoder.decodeX(tMinX))
                ) {
                    if (relate(queryComponent2D, !splitX, tMaxX, tMaxY, size) == Relation.CELL_CROSSES_QUERY) {
                        return Relation.CELL_CROSSES_QUERY
                    }
                } else {
                    dvReader.skipBytes(size.toLong())
                }
            }
            return Relation.CELL_OUTSIDE_QUERY
        }

        private fun relateComponent(
            type: TYPE,
            bbox: SpatialQuery.EncodedRectangle,
            pMaxX: Int,
            pMaxY: Int,
            x: Double,
            queryComponent2D: Component2D
        ): Relation {
            val r = when (type) {
                TYPE.POINT -> relatePoint(bbox, pMaxY, x, queryComponent2D)
                TYPE.LINE -> relateLine(bbox, pMaxX, pMaxY, x, queryComponent2D)
                TYPE.TRIANGLE -> relateTriangle(bbox, pMaxX, pMaxY, x, queryComponent2D)
            }
            return if (r == Relation.CELL_CROSSES_QUERY) Relation.CELL_CROSSES_QUERY else Relation.CELL_OUTSIDE_QUERY
        }

        private fun relatePoint(
            bbox: SpatialQuery.EncodedRectangle,
            pMaxY: Int,
            ax: Double,
            query: Component2D
        ): Relation {
            val y = (pMaxY - dvReader.readVLong()).toInt()
            return if (query.contains(ax, encoder.decodeY(y))) Relation.CELL_CROSSES_QUERY else Relation.CELL_OUTSIDE_QUERY
        }

        private fun relateLine(
            bbox: SpatialQuery.EncodedRectangle,
            pMaxX: Int,
            pMaxY: Int,
            ax: Double,
            query: Component2D
        ): Relation {
            val ay = (pMaxY - dvReader.readVLong()).toInt()
            val bx = encoder.decodeX((pMaxX - dvReader.readVLong()).toInt())
            val by = (pMaxY - dvReader.readVLong()).toInt()
            return if (query.intersectsLine(ax, encoder.decodeY(ay), bx, encoder.decodeY(by))) {
                Relation.CELL_CROSSES_QUERY
            } else {
                Relation.CELL_OUTSIDE_QUERY
            }
        }

        private fun relateTriangle(
            bbox: SpatialQuery.EncodedRectangle,
            pMaxX: Int,
            pMaxY: Int,
            ax: Double,
            queryComponent2D: Component2D
        ): Relation {
            val ay = (pMaxY - dvReader.readVLong()).toInt()
            val bx = encoder.decodeX((pMaxX - dvReader.readVLong()).toInt())
            val by = (pMaxY - dvReader.readVLong()).toInt()
            val cx = encoder.decodeX((pMaxX - dvReader.readVLong()).toInt())
            val cy = (pMaxY - dvReader.readVLong()).toInt()
            return if (queryComponent2D.intersectsTriangle(
                    ax,
                    encoder.decodeY(ay),
                    bx,
                    encoder.decodeY(by),
                    cx,
                    encoder.decodeY(cy)
                )
            ) {
                Relation.CELL_CROSSES_QUERY
            } else {
                Relation.CELL_OUTSIDE_QUERY
            }
        }
    }

    companion object {
        /** doc value format version; used to support bwc for any encoding changes */
        const val VERSION: Byte = 0

        /** Creates a geometry query for shape docvalues */
        fun newGeometryQuery(
            field: String,
            relation: ShapeField.QueryRelation,
            vararg geometries: Any
        ): Query {
            throw UnsupportedOperationException(
                "ShapeDocValues.newGeometryQuery is not ported yet for field=$field relation=$relation geometries=${geometries.size}"
            )
        }

        private fun decodeTriangles(binaryValue: BytesRef): List<ShapeField.DecodedTriangle> {
            val triangles = ArrayList<ShapeField.DecodedTriangle>()
            var offset = binaryValue.offset
            val end = binaryValue.offset + binaryValue.length
            while (offset + (7 * ShapeField.BYTES) <= end) {
                val bytes = ByteArray(7 * ShapeField.BYTES)
                binaryValue.bytes.copyInto(bytes, 0, offset, offset + bytes.size)
                val t = ShapeField.DecodedTriangle()
                ShapeField.decodeTriangle(bytes, t)
                triangles.add(t)
                offset += bytes.size
            }
            return triangles
        }

        fun vIntSize(i: Int): Int {
            return when {
                (i and (-1 shl 7)) == 0 -> 1
                (i and (-1 shl 14)) == 0 -> 2
                (i and (-1 shl 21)) == 0 -> 3
                (i and (-1 shl 28)) == 0 -> 4
                else -> 5
            }
        }

        fun vLongSize(i: Long): Int {
            var value = i
            var bytes = 1
            while ((value and -128L) != 0L) {
                value = value ushr 7
                bytes++
            }
            return bytes
        }
    }
}
