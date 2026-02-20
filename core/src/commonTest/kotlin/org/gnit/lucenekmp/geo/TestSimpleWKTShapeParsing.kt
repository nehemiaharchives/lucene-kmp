package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.geo.SimpleWKTShapeParser.ShapeType
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** simple WKT parsing tests */
class TestSimpleWKTShapeParsing : LuceneTestCase() {
    /** test simple Point */
    @Test
    @Throws(Exception::class)
    fun testPoint() {
        val b = StringBuilder()
        b.append(ShapeType.POINT.toString() + "(101.0 10.0)")
        val shape = SimpleWKTShapeParser.parse(b.toString())

        assertTrue(shape is DoubleArray)
        val point = shape as DoubleArray
        assertEquals(101.0, point[0], 0.0) // lon
        assertEquals(10.0, point[1], 1.0) // lat
    }

    /** test POINT EMPTY returns null */
    @Test
    @Throws(Exception::class)
    fun testEmptyPoint() {
        val b = StringBuilder()
        b.append(ShapeType.POINT.toString() + SimpleWKTShapeParser.SPACE + SimpleWKTShapeParser.EMPTY)
        val shape = SimpleWKTShapeParser.parse(b.toString())
        assertNull(shape)
    }

    /** test simple MULTIPOINT */
    @Test
    @Throws(Exception::class)
    fun testMultiPoint() {
        val b = StringBuilder()
        b.append(ShapeType.MULTIPOINT.toString() + "(101.0 10.0, 180.0 90.0, -180.0 -90.0)")
        val shape = SimpleWKTShapeParser.parse(b.toString())

        assertTrue(shape is Array<*>)
        val pts = shape as Array<DoubleArray>
        assertEquals(3, pts.size)
        assertEquals(101.0, pts[0][0], 0.0)
        assertEquals(10.0, pts[0][1], 0.0)
        assertEquals(180.0, pts[1][0], 0.0)
        assertEquals(90.0, pts[1][1], 0.0)
        assertEquals(-180.0, pts[2][0], 0.0)
        assertEquals(-90.0, pts[2][1], 0.0)
    }

    /** test MULTIPOINT EMPTY returns null */
    @Test
    @Throws(Exception::class)
    fun testEmptyMultiPoint() {
        val b = StringBuilder()
        b.append(ShapeType.MULTIPOINT.toString() + SimpleWKTShapeParser.SPACE + SimpleWKTShapeParser.EMPTY)
        val shape = SimpleWKTShapeParser.parse(b.toString())
        assertNull(shape)
    }

    /** test simple LINESTRING */
    @Test
    @Throws(Exception::class)
    fun testLine() {
        val b = StringBuilder()
        b.append(ShapeType.LINESTRING.toString() + "(101.0 10.0, 180.0 90.0, -180.0 -90.0)")
        val shape = SimpleWKTShapeParser.parse(b.toString())

        assertTrue(shape is Line)
        val line = shape as Line
        assertEquals(3, line.numPoints())
        assertEquals(101.0, line.getLon(0), 0.0)
        assertEquals(10.0, line.getLat(0), 0.0)
        assertEquals(180.0, line.getLon(1), 0.0)
        assertEquals(90.0, line.getLat(1), 0.0)
        assertEquals(-180.0, line.getLon(2), 0.0)
        assertEquals(-90.0, line.getLat(2), 0.0)
    }

    /** test empty LINESTRING */
    @Test
    @Throws(Exception::class)
    fun testEmptyLine() {
        val b = StringBuilder()
        b.append(ShapeType.LINESTRING.toString() + SimpleWKTShapeParser.SPACE + SimpleWKTShapeParser.EMPTY)
        val shape = SimpleWKTShapeParser.parse(b.toString())
        assertNull(shape)
    }

    /** test simple MULTILINESTRING */
    @Test
    @Throws(Exception::class)
    fun testMultiLine() {
        val b = StringBuilder()
        b.append(ShapeType.MULTILINESTRING.toString() + "((100.0 0.0, 101.0 0.0, 101.0 1.0, 100.0 1.0, 100.0 0.0),")
        b.append("(10.0 2.0, 11.0 2.0, 11.0 3.0, 10.0 3.0, 10.0 2.0))")
        val shape = SimpleWKTShapeParser.parse(b.toString())

        assertTrue(shape is Array<*>)
        val lines = shape as Array<Line>
        assertEquals(2, lines.size)
    }

    /** test empty MULTILINESTRING */
    @Test
    @Throws(Exception::class)
    fun testEmptyMultiLine() {
        val b = StringBuilder()
        b.append(ShapeType.MULTILINESTRING.toString() + SimpleWKTShapeParser.SPACE + SimpleWKTShapeParser.EMPTY)
        val shape = SimpleWKTShapeParser.parse(b.toString())
        assertNull(shape)
    }

    /** test simple polygon: POLYGON((100.0 0.0, 101.0 0.0, 101.0 1.0, 100.0 1.0, 100.0 0.0)) */
    @Test
    @Throws(Exception::class)
    fun testPolygon() {
        val b = StringBuilder()
        b.append(ShapeType.POLYGON.toString() + "((100.0 0.0, 101.0 0.0, 101.0 1.0, 100.0 1.0, 100.0 0.0))\n")
        val shape = SimpleWKTShapeParser.parse(b.toString())

        assertTrue(shape is Polygon)
        val polygon = shape as Polygon
        assertEquals(
            Polygon(
                doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0),
                doubleArrayOf(100.0, 101.0, 101.0, 100.0, 100.0)
            ),
            polygon
        )
    }

    /** test polygon with hole */
    @Test
    @Throws(Exception::class)
    fun testPolygonWithHole() {
        val b = StringBuilder()
        b.append(ShapeType.POLYGON.toString() + "((100.0 0.0, 101.0 0.0, 101.0 1.0, 100.0 1.0, 100.0 0.0), ")
        b.append("(100.5 0.5, 100.5 0.75, 100.75 0.75, 100.75 0.5, 100.5 0.5))")
        val shape = SimpleWKTShapeParser.parse(b.toString())

        assertTrue(shape is Polygon)
        val hole =
            Polygon(
                doubleArrayOf(0.5, 0.75, 0.75, 0.5, 0.5),
                doubleArrayOf(100.5, 100.5, 100.75, 100.75, 100.5)
            )
        val expected =
            Polygon(
                doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0),
                doubleArrayOf(100.0, 101.0, 101.0, 100.0, 100.0),
                hole
            )
        val polygon = shape as Polygon

        assertEquals(expected, polygon)
    }

    /** test MultiPolygon returns Polygon array */
    @Test
    @Throws(Exception::class)
    fun testMultiPolygon() {
        val b = StringBuilder()
        b.append(ShapeType.MULTIPOLYGON.toString() + "(((100.0 0.0, 101.0 0.0, 101.0 1.0, 100.0 1.0, 100.0 0.0)),")
        b.append("((10.0 2.0, 11.0 2.0, 11.0 3.0, 10.0 3.0, 10.0 2.0)))")
        val shape = SimpleWKTShapeParser.parse(b.toString())

        assertTrue(shape is Array<*>)
        val polygons = shape as Array<Polygon>
        assertEquals(2, polygons.size)
        assertEquals(
            Polygon(
                doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0),
                doubleArrayOf(100.0, 101.0, 101.0, 100.0, 100.0)
            ),
            polygons[0]
        )
        assertEquals(
            Polygon(
                doubleArrayOf(2.0, 2.0, 3.0, 3.0, 2.0),
                doubleArrayOf(10.0, 11.0, 11.0, 10.0, 10.0)
            ),
            polygons[1]
        )
    }

    /** polygon must be closed */
    @Test
    fun testPolygonNotClosed() {
        val b = StringBuilder()
        b.append(ShapeType.POLYGON.toString() + "((100.0 0.0, 101.0 0.0, 101.0 1.0, 100.0 1.0))\n")

        val expected =
            expectThrows(
                IllegalArgumentException::class,
                {
                    SimpleWKTShapeParser.parse(b.toString())
                }
            )
        assertTrue(
            expected.message!!.contains(
                "first and last points of the polygon must be the same (it must close itself)"
            ),
            expected.message
        )
    }

    /** test simple ENVELOPE (minLon, maxLon, maxLat, minLat) */
    @Test
    @Throws(Exception::class)
    fun testEnvelope() {
        val b = StringBuilder()
        b.append(ShapeType.ENVELOPE.toString() + "(-180.0, 180.0, 90.0, -90.0)")
        val shape = SimpleWKTShapeParser.parse(b.toString())

        assertTrue(shape is Rectangle)
        val bbox = shape as Rectangle
        assertEquals(-180.0, bbox.minLon, 0.0)
        assertEquals(180.0, bbox.maxLon, 0.0)
        assertEquals(-90.0, bbox.minLat, 0.0)
        assertEquals(90.0, bbox.maxLat, 0.0)
    }

    /** test simple geometry collection */
    @Test
    @Throws(Exception::class)
    fun testGeometryCollection() {
        val b = StringBuilder()
        b.append(ShapeType.GEOMETRYCOLLECTION.toString() + "(")
        b.append(
            ShapeType.MULTIPOLYGON.toString() +
                "(((100.0 0.0, 101.0 0.0, 101.0 1.0, 100.0 1.0, 100.0 0.0)),"
        )
        b.append("((10.0 2.0, 11.0 2.0, 11.0 3.0, 10.0 3.0, 10.0 2.0))),")
        b.append(ShapeType.POINT.toString() + "(101.0 10.0),")
        b.append(ShapeType.LINESTRING.toString() + "(101.0 10.0, 180.0 90.0, -180.0 -90.0),")
        b.append(ShapeType.ENVELOPE.toString() + "(-180.0, 180.0, 90.0, -90.0)")
        b.append(")")
        val shape = SimpleWKTShapeParser.parse(b.toString())

        assertTrue(shape is Array<*>)
        val shapes = shape as Array<Any>
        assertEquals(4, shapes.size)
        assertTrue(shapes[0] is Array<*>)
        assertTrue(shapes[1] is DoubleArray)
        assertTrue(shapes[2] is Line)
        assertTrue(shapes[3] is Rectangle)
    }
}
