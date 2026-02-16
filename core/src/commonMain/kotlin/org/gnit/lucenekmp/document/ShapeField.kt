package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.Tessellator
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils.sortableBytesToInt
import org.gnit.lucenekmp.util.NumericUtils.intToSortableBytes
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.StrictMath

/**
 * A base shape utility class used for both LatLon (spherical) and XY (cartesian) shape fields.
 *
 *
 * [Polygon]'s and [Line]'s are decomposed into a triangular mesh using the [ ] utility class. Each [Triangle] is encoded by this base class and indexed as a
 * seven dimension multi-value field.
 *
 *
 * Finding all shapes that intersect a range (e.g., bounding box), or target shape, at search
 * time is efficient.
 *
 *
 * This class defines the static methods for encoding the three vertices of a tessellated
 * triangles as a seven dimension point. The coordinates are converted from double precision values
 * into 32 bit integers so they are sortable at index time.
 */
object ShapeField {
    /** vertex coordinates are encoded as 4 byte integers  */
    const val BYTES: Int = Int.SIZE_BYTES

    /**
     * tessellated triangles are seven dimensions; the first four are the bounding box index
     * dimensions
     */
    internal val TYPE: FieldType = FieldType()

    init {
        TYPE.setDimensions(7, 4, BYTES)
        TYPE.freeze()
    }

    private const val MINY_MINX_MAXY_MAXX_Y_X = 0
    private const val MINY_MINX_Y_X_MAXY_MAXX = 1
    private const val MAXY_MINX_Y_X_MINY_MAXX = 2
    private const val MAXY_MINX_MINY_MAXX_Y_X = 3
    private const val Y_MINX_MINY_X_MAXY_MAXX = 4
    private const val Y_MINX_MINY_MAXX_MAXY_X = 5
    private const val MAXY_MINX_MINY_X_Y_MAXX = 6
    private const val MINY_MINX_Y_MAXX_MAXY_X = 7

    /**
     * A triangle is encoded using 6 points and an extra point with encoded information in three bits
     * of how to reconstruct it. Triangles are encoded with CCW orientation and might be rotated to
     * limit the number of possible reconstructions to 2^3. Reconstruction always happens from west to
     * east.
     */
    fun encodeTriangle(
        bytes: ByteArray,
        aY: Int,
        aX: Int,
        ab: Boolean,
        bY: Int,
        bX: Int,
        bc: Boolean,
        cY: Int,
        cX: Int,
        ca: Boolean
    ) {
        var aY = aY
        var aX = aX
        var ab = ab
        var bY = bY
        var bX = bX
        var bc = bc
        var cY = cY
        var cX = cX
        var ca = ca
        require(bytes.size == 7 * BYTES)
        // rotate edges and place minX at the beginning
        if (bX < aX || cX < aX) {
            val tempX = aX
            val tempY = aY
            val tempBool = ab
            if (bX < cX) {
                aX = bX
                aY = bY
                ab = bc
                bX = cX
                bY = cY
                bc = ca
                cX = tempX
                cY = tempY
                ca = tempBool
            } else {
                aX = cX
                aY = cY
                ab = ca
                cX = bX
                cY = bY
                ca = bc
                bX = tempX
                bY = tempY
                bc = tempBool
            }
        } else if (aX == bX && aX == cX) {
            // degenerated case, all points with same longitude
            // we need to prevent that aX is in the middle (not part of the MBS)
            if (bY < aY || cY < aY) {
                val tempX = aX
                val tempY = aY
                val tempBool = ab
                if (bY < cY) {
                    aX = bX
                    aY = bY
                    ab = bc
                    bX = cX
                    bY = cY
                    bc = ca
                    cX = tempX
                    cY = tempY
                    ca = tempBool
                } else {
                    aX = cX
                    aY = cY
                    ab = ca
                    cX = bX
                    cY = bY
                    ca = bc
                    bX = tempX
                    bY = tempY
                    bc = tempBool
                }
            }
        }

        // change orientation if CW
        if (GeoUtils.orient(
                aX.toDouble(),
                aY.toDouble(),
                bX.toDouble(),
                bY.toDouble(),
                cX.toDouble(),
                cY.toDouble()
            ) == -1
        ) {
            // swap b with c
            val tempX = bX
            val tempY = bY
            val tempBool = ab
            // aX and aY do not change, ab becomes bc
            ab = bc
            bX = cX
            bY = cY
            // bc does not change, ca becomes ab
            cX = tempX
            cY = tempY
            ca = tempBool
        }

        val minX = aX
        val minY: Int = StrictMath.min(aY, StrictMath.min(bY, cY))
        val maxX: Int = StrictMath.max(aX, StrictMath.max(bX, cX))
        val maxY: Int = StrictMath.max(aY, StrictMath.max(bY, cY))

        var bits: Int
        val x: Int
        val y: Int
        if (minY == aY) {
            if (maxY == bY && maxX == bX) {
                y = cY
                x = cX
                bits = MINY_MINX_MAXY_MAXX_Y_X
            } else if (maxY == cY && maxX == cX) {
                y = bY
                x = bX
                bits = MINY_MINX_Y_X_MAXY_MAXX
            } else {
                y = bY
                x = cX
                bits = MINY_MINX_Y_MAXX_MAXY_X
            }
        } else if (maxY == aY) {
            if (minY == bY && maxX == bX) {
                y = cY
                x = cX
                bits = MAXY_MINX_MINY_MAXX_Y_X
            } else if (minY == cY && maxX == cX) {
                y = bY
                x = bX
                bits = MAXY_MINX_Y_X_MINY_MAXX
            } else {
                y = cY
                x = bX
                bits = MAXY_MINX_MINY_X_Y_MAXX
            }
        } else if (maxX == bX && minY == bY) {
            y = aY
            x = cX
            bits = Y_MINX_MINY_MAXX_MAXY_X
        } else if (maxX == cX && maxY == cY) {
            y = aY
            x = bX
            bits = Y_MINX_MINY_X_MAXY_MAXX
        } else {
            throw IllegalArgumentException("Could not encode the provided triangle")
        }
        bits = bits or if (ab) (1 shl 3) else 0
        bits = bits or if (bc) (1 shl 4) else 0
        bits = bits or if (ca) (1 shl 5) else 0
        intToSortableBytes(minY, bytes, 0)
        intToSortableBytes(minX, bytes, BYTES)
        intToSortableBytes(maxY, bytes, 2 * BYTES)
        intToSortableBytes(maxX, bytes, 3 * BYTES)
        intToSortableBytes(y, bytes, 4 * BYTES)
        intToSortableBytes(x, bytes, 5 * BYTES)
        intToSortableBytes(bits, bytes, 6 * BYTES)
    }

    /**
     * Decode a triangle encoded by [ShapeField.encodeTriangle].
     */
    fun decodeTriangle(t: ByteArray, triangle: DecodedTriangle) {
        val aX: Int
        val aY: Int
        val bX: Int
        val bY: Int
        val cX: Int
        val cY: Int
        val ab: Boolean
        val bc: Boolean
        val ca: Boolean
        val bits: Int = sortableBytesToInt(t, 6 * BYTES)
        // extract the first three bits
        val tCode = (((1 shl 3) - 1) and (bits shr 0))
        when (tCode) {
            MINY_MINX_MAXY_MAXX_Y_X -> {
                aY = sortableBytesToInt(t, 0 * BYTES)
                aX = sortableBytesToInt(t, 1 * BYTES)
                bY = sortableBytesToInt(t, 2 * BYTES)
                bX = sortableBytesToInt(t, 3 * BYTES)
                cY = sortableBytesToInt(t, 4 * BYTES)
                cX = sortableBytesToInt(t, 5 * BYTES)
            }

            MINY_MINX_Y_X_MAXY_MAXX -> {
                aY = sortableBytesToInt(t, 0 * BYTES)
                aX = sortableBytesToInt(t, 1 * BYTES)
                bY = sortableBytesToInt(t, 4 * BYTES)
                bX = sortableBytesToInt(t, 5 * BYTES)
                cY = sortableBytesToInt(t, 2 * BYTES)
                cX = sortableBytesToInt(t, 3 * BYTES)
            }

            MAXY_MINX_Y_X_MINY_MAXX -> {
                aY = sortableBytesToInt(t, 2 * BYTES)
                aX = sortableBytesToInt(t, 1 * BYTES)
                bY = sortableBytesToInt(t, 4 * BYTES)
                bX = sortableBytesToInt(t, 5 * BYTES)
                cY = sortableBytesToInt(t, 0 * BYTES)
                cX = sortableBytesToInt(t, 3 * BYTES)
            }

            MAXY_MINX_MINY_MAXX_Y_X -> {
                aY = sortableBytesToInt(t, 2 * BYTES)
                aX = sortableBytesToInt(t, 1 * BYTES)
                bY = sortableBytesToInt(t, 0 * BYTES)
                bX = sortableBytesToInt(t, 3 * BYTES)
                cY = sortableBytesToInt(t, 4 * BYTES)
                cX = sortableBytesToInt(t, 5 * BYTES)
            }

            Y_MINX_MINY_X_MAXY_MAXX -> {
                aY = sortableBytesToInt(t, 4 * BYTES)
                aX = sortableBytesToInt(t, 1 * BYTES)
                bY = sortableBytesToInt(t, 0 * BYTES)
                bX = sortableBytesToInt(t, 5 * BYTES)
                cY = sortableBytesToInt(t, 2 * BYTES)
                cX = sortableBytesToInt(t, 3 * BYTES)
            }

            Y_MINX_MINY_MAXX_MAXY_X -> {
                aY = sortableBytesToInt(t, 4 * BYTES)
                aX = sortableBytesToInt(t, 1 * BYTES)
                bY = sortableBytesToInt(t, 0 * BYTES)
                bX = sortableBytesToInt(t, 3 * BYTES)
                cY = sortableBytesToInt(t, 2 * BYTES)
                cX = sortableBytesToInt(t, 5 * BYTES)
            }

            MAXY_MINX_MINY_X_Y_MAXX -> {
                aY = sortableBytesToInt(t, 2 * BYTES)
                aX = sortableBytesToInt(t, 1 * BYTES)
                bY = sortableBytesToInt(t, 0 * BYTES)
                bX = sortableBytesToInt(t, 5 * BYTES)
                cY = sortableBytesToInt(t, 4 * BYTES)
                cX = sortableBytesToInt(t, 3 * BYTES)
            }

            MINY_MINX_Y_MAXX_MAXY_X -> {
                aY = sortableBytesToInt(t, 0 * BYTES)
                aX = sortableBytesToInt(t, 1 * BYTES)
                bY = sortableBytesToInt(t, 4 * BYTES)
                bX = sortableBytesToInt(t, 3 * BYTES)
                cY = sortableBytesToInt(t, 2 * BYTES)
                cX = sortableBytesToInt(t, 5 * BYTES)
            }

            else -> throw IllegalArgumentException("Could not decode the provided triangle")
        }
        // Points of the decoded triangle must be co-planar or CCW oriented
        require(
            GeoUtils.orient(
                aX.toDouble(),
                aY.toDouble(),
                bX.toDouble(),
                bY.toDouble(),
                cX.toDouble(),
                cY.toDouble()
            ) >= 0
        )
        ab = (bits and (1 shl 3)) == 1 shl 3
        bc = (bits and (1 shl 4)) == 1 shl 4
        ca = (bits and (1 shl 5)) == 1 shl 5
        triangle.setValues(aX, aY, ab, bX, bY, bc, cX, cY, ca)
        resolveTriangleType(triangle)
    }

    fun resolveTriangleType(triangle: DecodedTriangle) {
        if (triangle.aX == triangle.bX && triangle.aY == triangle.bY) {
            if (triangle.aX == triangle.cX && triangle.aY == triangle.cY) {
                triangle.type = DecodedTriangle.TYPE.POINT
            } else {
                // a and b are identical, remove ab, and merge bc and ca
                triangle.ab = triangle.bc or triangle.ca
                triangle.bX = triangle.cX
                triangle.bY = triangle.cY
                triangle.cX = triangle.aX
                triangle.cY = triangle.aY
                triangle.type = DecodedTriangle.TYPE.LINE
            }
        } else if (triangle.aX == triangle.cX && triangle.aY == triangle.cY) {
            // a and c are identical, remove ac, and merge ab and bc
            triangle.ab = triangle.ab or triangle.bc
            triangle.type = DecodedTriangle.TYPE.LINE
        } else if (triangle.bX == triangle.cX && triangle.bY == triangle.cY) {
            // b and c are identical, remove bc, and merge ab and ca
            triangle.ab = triangle.ab or triangle.ca
            triangle.cX = triangle.aX
            triangle.cY = triangle.aY
            triangle.type = DecodedTriangle.TYPE.LINE
        } else {
            triangle.type = DecodedTriangle.TYPE.TRIANGLE
        }
    }

    /**
     * polygons are decomposed into tessellated triangles using [ ] these triangles are encoded and inserted as separate indexed
     * POINT fields
     */
    open class Triangle : Field {
        /** constructor for points and lines  */
        internal constructor(
            name: String,
            aXencoded: Int,
            aYencoded: Int,
            bXencoded: Int,
            bYencoded: Int,
            cXencoded: Int,
            cYencoded: Int
        ) : super(name, TYPE) {
            setTriangleValue(
                aXencoded, aYencoded, true, bXencoded, bYencoded, true, cXencoded, cYencoded, true
            )
        }

        /** xtor from a given Tessellated Triangle object  */
        internal constructor(name: String, t: Tessellator.Triangle) : super(name, TYPE) {
            setTriangleValue(
                t.getEncodedX(0),
                t.getEncodedY(0),
                t.isEdgefromPolygon(0),
                t.getEncodedX(1),
                t.getEncodedY(1),
                t.isEdgefromPolygon(1),
                t.getEncodedX(2),
                t.getEncodedY(2),
                t.isEdgefromPolygon(2)
            )
        }

        /** sets the vertices of the triangle as integer encoded values  */
        protected fun setTriangleValue(
            aX: Int,
            aY: Int,
            abFromShape: Boolean,
            bX: Int,
            bY: Int,
            bcFromShape: Boolean,
            cX: Int,
            cY: Int,
            caFromShape: Boolean
        ) {
            val bytes: ByteArray

            if (!isFieldsDataInitialized()) {
                bytes = ByteArray(7 * BYTES)
                fieldsData = BytesRef(bytes)
            } else {
                bytes = (fieldsData as BytesRef).bytes
            }
            encodeTriangle(bytes, aY, aX, abFromShape, bY, bX, bcFromShape, cY, cX, caFromShape)
        }
    }

    /** Query Relation Types *  */
    enum class QueryRelation {
        /** used for INTERSECT Queries  */
        INTERSECTS,

        /** used for WITHIN Queries  */
        WITHIN,

        /** used for DISJOINT Queries  */
        DISJOINT,

        /** used for CONTAINS Queries  */
        CONTAINS
    }

    /**
     * Represents a encoded triangle using [ShapeField.decodeTriangle].
     */
    class DecodedTriangle
    /** default xtor  */
    {
        /** type of triangle  */
        enum class TYPE {
            /** all coordinates are equal  */
            POINT,

            /** first and third coordinates are equal  */
            LINE,

            /** all coordinates are different  */
            TRIANGLE
        }

        /** x coordinate, vertex one  */
        var aX: Int = 0

        /** y coordinate, vertex one  */
        var aY: Int = 0

        /** x coordinate, vertex two  */
        var bX: Int = 0

        /** y coordinate, vertex two  */
        var bY: Int = 0

        /** x coordinate, vertex three  */
        var cX: Int = 0

        /** y coordinate, vertex three  */
        var cY: Int = 0

        /** represent if edge ab belongs to original shape  */
        var ab: Boolean = false

        /** represent if edge bc belongs to original shape  */
        var bc: Boolean = false

        /** represent if edge ca belongs to original shape  */
        var ca: Boolean = false

        /** triangle type  */
        lateinit var type: TYPE

        /** Sets the values of the DecodedTriangle  */
        fun setValues(
            aX: Int, aY: Int, ab: Boolean, bX: Int, bY: Int, bc: Boolean, cX: Int, cY: Int, ca: Boolean
        ) {
            this.aX = aX
            this.aY = aY
            this.ab = ab
            this.bX = bX
            this.bY = bY
            this.bc = bc
            this.cX = cX
            this.cY = cY
            this.ca = ca
        }

        override fun hashCode(): Int {
            return Objects.hash(aX, aY, bX, bY, cX, cY, ab, bc, ca)
        }

        override fun equals(o: Any?): Boolean {
            val other = o as DecodedTriangle
            return (aX == other.aX && bX == other.bX && cX == other.cX)
                    && (aY == other.aY && bY == other.bY && cY == other.cY)
                    && (ab == other.ab && bc == other.bc && ca == other.ca)
        }

        /** pretty print the triangle vertices  */
        override fun toString(): String {
            val result =
                (("$aX, $aY")
                        + " "
                        + ("$bX, $bY")
                        + " "
                        + ("$cX, $cY")
                        + " "
                        + ("[$ab,$bc,$ca]"))
            return result
        }
    }
}
