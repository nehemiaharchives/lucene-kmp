package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.geo.SimpleWKTShapeParser
import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Simple tests for [org.gnit.lucenekmp.document.ShapeDocValuesField] */
class TestShapeDocValues : LuceneTestCase() {
    companion object {
        private const val TOLERANCE = 1E-7

        private const val FIELD_NAME = "field"

        /** Compute signed area of rectangle */
        private fun area(p: Polygon): Double {
            return (p.maxLon - p.minLon) * (p.maxLat - p.minLat)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleDocValue() {
        val dv: ShapeDocValues = LatLonShapeDocValues(getTessellation(getTestPolygonWithHole()))
        // tests geometry inside a hole and crossing
        assertEquals(
            dv.relate(LatLonGeometry.create(Rectangle(-0.25, -0.24, -3.8, -3.7))),
            PointValues.Relation.CELL_OUTSIDE_QUERY
        )
        assertNotEquals(
            dv.relate(LatLonGeometry.create(Rectangle(-1.2, 1.2, -1.5, 1.7))),
            PointValues.Relation.CELL_CROSSES_QUERY
        )
    }

    @Test
    fun testLatLonPolygonBBox() {
        val p = GeoTestUtil.nextPolygon()
        if (area(p) != 0.0) {
            val expected = computeBoundingBox(p) as Rectangle
            val dv = LatLonShape.createDocValueField(FIELD_NAME, p)
            assertEquals(expected.minLat, dv.getBoundingBox().minLat, TOLERANCE)
            assertEquals(expected.maxLat, dv.getBoundingBox().maxLat, TOLERANCE)
            assertEquals(expected.minLon, dv.getBoundingBox().minLon, TOLERANCE)
            assertEquals(expected.maxLon, dv.getBoundingBox().maxLon, TOLERANCE)
        }
    }

    @Test
    fun testXYPolygonBBox() {
        val p = nextTessellatableXYPolygon()
        val expected = computeBoundingBox(p) as XYRectangle
        val dv = XYShape.createDocValueField(FIELD_NAME, p)
        assertEquals(expected.minX, dv.getBoundingBox().minX, TOLERANCE.toFloat())
        assertEquals(expected.maxX, dv.getBoundingBox().maxX, TOLERANCE.toFloat())
        assertEquals(expected.minY, dv.getBoundingBox().minY, TOLERANCE.toFloat())
        assertEquals(expected.maxY, dv.getBoundingBox().maxY, TOLERANCE.toFloat())
    }

    @Test
    fun testLatLonPolygonCentroid() {
        val p = GeoTestUtil.nextPolygon()
        val expected = computeCentroid(p) as Point
        val tess = getTessellation(p)
        val dvField = LatLonShape.createDocValueField(FIELD_NAME, p)
        assertEquals(tess.size.toLong(), dvField.numberOfTerms().toLong())
        assertEquals(expected.lat, dvField.getCentroid().lat, TOLERANCE)
        assertEquals(expected.lon, dvField.getCentroid().lon, TOLERANCE)
        assertEquals(ShapeField.DecodedTriangle.TYPE.TRIANGLE, dvField.getHighestDimensionType())
    }

    @Test
    fun testXYPolygonCentroid() {
        val p = nextTessellatableXYPolygon()
        val expected = computeCentroid(p) as XYPoint
        val dvField = XYShape.createDocValueField(FIELD_NAME, getTessellation(p))
        assertEquals(expected.x, dvField.getCentroid().x, TOLERANCE.toFloat())
        assertEquals(expected.y, dvField.getCentroid().y, TOLERANCE.toFloat())
        assertEquals(ShapeField.DecodedTriangle.TYPE.TRIANGLE, dvField.getHighestDimensionType())
    }

    private fun computeCentroid(p: Geometry): Geometry {
        val tess = getTessellation(p)
        var totalSignedArea = 0.0
        var numXPly = 0.0
        var numYPly = 0.0
        val decodeX: (Int) -> Double =
            if (p is Polygon) {
                { x -> GeoEncodingUtils.decodeLongitude(x) }
            } else {
                { x -> XYEncodingUtils.decode(x).toDouble() }
            }
        val decodeY: (Int) -> Double =
            if (p is Polygon) {
                { y -> GeoEncodingUtils.decodeLatitude(y) }
            } else {
                { y -> XYEncodingUtils.decode(y).toDouble() }
            }
        val createPoint: (Double, Double) -> Geometry =
            if (p is Polygon) {
                { x, y -> Point(y, x) }
            } else {
                { x, y -> XYPoint(x.toFloat(), y.toFloat()) }
            }

        for (t in tess) {
            val ax = decodeX(t.aX)
            val ay = decodeY(t.aY)
            val bx = decodeX(t.bX)
            val by = decodeY(t.bY)
            val cx = decodeX(t.cX)
            val cy = decodeY(t.cY)

            val signedArea = abs(0.5 * ((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)))
            // accumulate midPoints and signed area
            numXPly += (((ax + bx + cx) / 3.0) * signedArea)
            numYPly += (((ay + by + cy) / 3.0) * signedArea)
            totalSignedArea += signedArea
        }
        totalSignedArea = if (totalSignedArea == 0.0) 1.0 else totalSignedArea
        return createPoint(numXPly / totalSignedArea, numYPly / totalSignedArea)
    }

    /**
     * compute the bounding box from the tessellation; test utils may create self crossing polygons
     * cleaned by the tessellator
     */
    private fun computeBoundingBox(p: Geometry): Geometry {
        val tess = getTessellation(p)
        val decodeX: (Int) -> Double =
            if (p is Polygon) {
                { x -> GeoEncodingUtils.decodeLongitude(x) }
            } else {
                { x -> XYEncodingUtils.decode(x).toDouble() }
            }
        val decodeY: (Int) -> Double =
            if (p is Polygon) {
                { y -> GeoEncodingUtils.decodeLatitude(y) }
            } else {
                { y -> XYEncodingUtils.decode(y).toDouble() }
            }
        val createRectangle: (Array<Double>, Array<Double>) -> Geometry =
            if (p is Polygon) {
                { minV, maxV -> Rectangle(minV[1], maxV[1], minV[0], maxV[0]) }
            } else {
                { minV, maxV -> XYRectangle(minV[0].toFloat(), maxV[0].toFloat(), minV[1].toFloat(), maxV[1].toFloat()) }
            }

        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (t in tess) {
            val ax = decodeX(t.aX)
            val ay = decodeY(t.aY)
            val bx = decodeX(t.bX)
            val by = decodeY(t.bY)
            val cx = decodeX(t.cX)
            val cy = decodeY(t.cY)
            minX = min(minX, min(ax, min(bx, cx)))
            maxX = max(maxX, max(ax, max(bx, cx)))
            minY = min(minY, min(ay, min(by, cy)))
            maxY = max(maxY, max(ay, max(by, cy)))
        }
        return createRectangle(arrayOf(minX, minY), arrayOf(maxX, maxY))
    }

    @Test
    @Throws(Exception::class)
    fun testExplicitLatLonPolygonCentroid() {
        val mp = "POLYGON((-80 -10, -40 -10, -40 10, -80 10, -80 -10))"
        val p = SimpleWKTShapeParser.parse(mp) as Polygon
        val tess = getTessellation(p)
        val dvField = LatLonShape.createDocValueField(FIELD_NAME, tess)
        assertEquals(0.0, dvField.getCentroid().lat, 1E-7)
        assertEquals(-60.0, dvField.getCentroid().lon, 1E-7)
        assertEquals(ShapeField.DecodedTriangle.TYPE.TRIANGLE, dvField.getHighestDimensionType())
    }

    /**
     * ensures consistency between [ByteBuffersDataOutput.writeVInt] and [ShapeDocValues.vIntSize]
     * and [ByteBuffersDataOutput.writeVLong] and [ShapeDocValues.vLongSize] so the serialization is valid.
     */
    @Test
    @Throws(Exception::class)
    fun testVariableValueSizes() {
        // scratch buffer
        val out = ByteBuffersDataOutput()

        for (i in 0..<random().nextInt(100, 500)) {
            // test variable int sizes
            val testInt = random().nextInt(Int.MAX_VALUE)
            val pB = out.size()
            out.writeVInt(testInt)
            val pA = out.size()
            assertEquals(ShapeDocValues.vIntSize(testInt).toLong(), pA - pB)

            // test variable long sizes
            val testLong = random().nextLong(Long.MAX_VALUE)
            out.writeVLong(testLong)
            assertEquals(ShapeDocValues.vLongSize(testLong).toLong(), out.size() - pA)
        }
    }

    private fun getTestPolygonWithHole(): Polygon {
        val poly = GeoTestUtil.createRegularPolygon(0.0, 0.0, 100000.0, 7)
        val inner =
            Polygon(
                doubleArrayOf(-1.0, -1.0, 0.5, 1.0, 1.0, 0.5, -1.0),
                doubleArrayOf(1.0, -1.0, -0.5, -1.0, 1.0, 0.5, 1.0)
            )
        val inner2 =
            Polygon(
                doubleArrayOf(-1.0, -1.0, 0.5, 1.0, 1.0, 0.5, -1.0),
                doubleArrayOf(-2.0, -4.0, -3.5, -4.0, -2.0, -2.5, -2.0)
            )

        return Polygon(poly.getPolyLats(), poly.getPolyLons(), inner, inner2)
    }

    private fun getTessellation(p: Geometry): List<ShapeField.DecodedTriangle> {
        if (p is Polygon) {
            return getTessellation(p)
        } else if (p is XYPolygon) {
            return getTessellation(p)
        }
        throw IllegalArgumentException("invalid geometry type: ${p::class}")
    }

    private fun getTessellation(p: Polygon): List<ShapeField.DecodedTriangle> {
        val fields = LatLonShape.createIndexableFields(FIELD_NAME, p)
        val tess = ArrayList<ShapeField.DecodedTriangle>(fields.size)
        for (f in fields) {
            val d = ShapeField.DecodedTriangle()
            ShapeField.decodeTriangle(f.binaryValue()!!.bytes, d)
            tess.add(d)
        }
        return tess
    }

    private fun getTessellation(p: XYPolygon): List<ShapeField.DecodedTriangle> {
        val fields = XYShape.createIndexableFields(FIELD_NAME, p, true)
        val tess = ArrayList<ShapeField.DecodedTriangle>(fields.size)
        for (f in fields) {
            val d = ShapeField.DecodedTriangle()
            ShapeField.decodeTriangle(f.binaryValue()!!.bytes, d)
            tess.add(d)
        }
        return tess
    }

    private fun nextTessellatableXYPolygon(): XYPolygon {
        repeat(100) {
            val polygon = ShapeTestUtil.nextPolygon()
            try {
                XYShape.createIndexableFields(FIELD_NAME, polygon, true)
                return polygon
            } catch (_: IllegalArgumentException) {
                // Parity with Java BaseXYShapeTestCase.ShapeType.POLYGON.nextShape():
                // random polygon generation may yield malformed polygons; retry until tessellation succeeds.
            }
        }
        throw AssertionError("Unable to generate a tessellatable XY polygon after 100 attempts")
    }

}
