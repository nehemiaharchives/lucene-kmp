package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestPolygon2D : LuceneTestCase() {
    @Test
    fun testMultiPolygon() {
        val hole = Polygon(doubleArrayOf(-10.0, -10.0, 10.0, 10.0, -10.0), doubleArrayOf(-10.0, 10.0, 10.0, -10.0, -10.0))
        val outer = Polygon(doubleArrayOf(-50.0, -50.0, 50.0, 50.0, -50.0), doubleArrayOf(-50.0, 50.0, 50.0, -50.0, -50.0), hole)
        val island = Polygon(doubleArrayOf(-5.0, -5.0, 5.0, 5.0, -5.0), doubleArrayOf(-5.0, 5.0, 5.0, -5.0, -5.0))
        val polygon = LatLonGeometry.create(outer, island)
        assertTrue(polygon.contains(-2.0, 2.0))
        assertFalse(polygon.contains(-6.0, 6.0))
        assertTrue(polygon.contains(-25.0, 25.0))
        assertFalse(polygon.contains(-51.0, 51.0))
        assertEquals(Relation.CELL_INSIDE_QUERY, polygon.relate(-2.0, 2.0, -2.0, 2.0))
        assertEquals(Relation.CELL_OUTSIDE_QUERY, polygon.relate(6.0, 7.0, 6.0, 7.0))
        assertEquals(Relation.CELL_INSIDE_QUERY, polygon.relate(24.0, 25.0, 24.0, 25.0))
        assertEquals(Relation.CELL_OUTSIDE_QUERY, polygon.relate(51.0, 52.0, 51.0, 52.0))
        assertEquals(Relation.CELL_CROSSES_QUERY, polygon.relate(-60.0, 60.0, -60.0, 60.0))
        assertEquals(Relation.CELL_CROSSES_QUERY, polygon.relate(49.0, 51.0, 49.0, 51.0))
        assertEquals(Relation.CELL_CROSSES_QUERY, polygon.relate(9.0, 11.0, 9.0, 11.0))
        assertEquals(Relation.CELL_CROSSES_QUERY, polygon.relate(5.0, 6.0, 5.0, 6.0))
    }

    @Test
    fun testPacMan() {
        val px = doubleArrayOf(0.0, 10.0, 10.0, 0.0, -8.0, -10.0, -8.0, 0.0, 10.0, 10.0, 0.0)
        val py = doubleArrayOf(0.0, 5.0, 9.0, 10.0, 9.0, 0.0, -9.0, -10.0, -9.0, -5.0, 0.0)
        val xMin = 2.0
        val xMax = 11.0
        val yMin = -1.0
        val yMax = 1.0
        val polygon = Polygon2D.create(Polygon(py, px))
        assertEquals(Relation.CELL_CROSSES_QUERY, polygon.relate(yMin, yMax, xMin, xMax))
    }

    @Test
    fun testBoundingBox() {
        for (i in 0 until 100) {
            val polygon = Polygon2D.create(GeoTestUtil.nextPolygon())
            for (j in 0 until 100) {
                val latitude = GeoTestUtil.nextLatitude()
                val longitude = GeoTestUtil.nextLongitude()
                if (polygon.contains(longitude, latitude)) {
                    assertTrue(latitude >= polygon.minY && latitude <= polygon.maxY)
                    assertTrue(longitude >= polygon.minX && longitude <= polygon.maxX)
                }
            }
        }
    }

    @Test
    fun testBoundingBoxEdgeCases() {
        for (i in 0 until 100) {
            val polygon = GeoTestUtil.nextPolygon()
            val impl = Polygon2D.create(polygon)
            for (j in 0 until 100) {
                val point = GeoTestUtil.nextPointNear(polygon)
                val latitude = point[0]
                val longitude = point[1]
                if (impl.contains(longitude, latitude)) {
                    assertTrue(latitude >= polygon.minLat && latitude <= polygon.maxLat)
                    assertTrue(longitude >= polygon.minLon && longitude <= polygon.maxLon)
                }
            }
        }
    }

    @Test
    fun testContainsRandom() {
        val iters = atLeast(50)
        for (i in 0 until iters) {
            val polygon = GeoTestUtil.nextPolygon()
            val impl = Polygon2D.create(polygon)
            for (j in 0 until 100) {
                val rectangle = GeoTestUtil.nextBoxNear(polygon)
                if (impl.relate(rectangle.minLon, rectangle.maxLon, rectangle.minLat, rectangle.maxLat) == Relation.CELL_INSIDE_QUERY) {
                    for (k in 0 until 500) {
                        val point = GeoTestUtil.nextPointNear(rectangle)
                        val latitude = point[0]
                        val longitude = point[1]
                        if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                            assertTrue(impl.contains(longitude, latitude))
                        }
                    }
                    for (k in 0 until 100) {
                        val point = GeoTestUtil.nextPointNear(polygon)
                        val latitude = point[0]
                        val longitude = point[1]
                        if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                            assertTrue(impl.contains(longitude, latitude))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testContainsEdgeCases() {
        for (i in 0 until 1000) {
            val polygon = GeoTestUtil.nextPolygon()
            val impl = Polygon2D.create(polygon)
            for (j in 0 until 10) {
                val rectangle = GeoTestUtil.nextBoxNear(polygon)
                if (impl.relate(rectangle.minLon, rectangle.maxLon, rectangle.minLat, rectangle.maxLat) == Relation.CELL_INSIDE_QUERY) {
                    for (k in 0 until 100) {
                        val point = GeoTestUtil.nextPointNear(rectangle)
                        val latitude = point[0]
                        val longitude = point[1]
                        if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                            assertTrue(impl.contains(longitude, latitude))
                        }
                    }
                    for (k in 0 until 20) {
                        val point = GeoTestUtil.nextPointNear(polygon)
                        val latitude = point[0]
                        val longitude = point[1]
                        if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                            assertTrue(impl.contains(longitude, latitude))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testIntersectRandom() {
        val iters = atLeast(10)
        for (i in 0 until iters) {
            val polygon = GeoTestUtil.nextPolygon()
            val impl = Polygon2D.create(polygon)
            val innerIters = atLeast(10)
            for (j in 0 until innerIters) {
                val rectangle = GeoTestUtil.nextBoxNear(polygon)
                if (impl.relate(rectangle.minLon, rectangle.maxLon, rectangle.minLat, rectangle.maxLat) == Relation.CELL_OUTSIDE_QUERY) {
                    for (k in 0 until 1000) {
                        val point = GeoTestUtil.nextPointNear(rectangle)
                        val latitude = point[0]
                        val longitude = point[1]
                        if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                            assertFalse(impl.contains(longitude, latitude))
                        }
                    }
                    for (k in 0 until 100) {
                        val point = GeoTestUtil.nextPointNear(polygon)
                        val latitude = point[0]
                        val longitude = point[1]
                        if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                            assertFalse(impl.contains(longitude, latitude))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testIntersectEdgeCases() {
        for (i in 0 until 100) {
            val polygon = GeoTestUtil.nextPolygon()
            val impl = Polygon2D.create(polygon)
            for (j in 0 until 10) {
                val rectangle = GeoTestUtil.nextBoxNear(polygon)
                if (impl.relate(rectangle.minLon, rectangle.maxLon, rectangle.minLat, rectangle.maxLat) == Relation.CELL_OUTSIDE_QUERY) {
                    for (k in 0 until 100) {
                        val point = GeoTestUtil.nextPointNear(rectangle)
                        val latitude = point[0]
                        val longitude = point[1]
                        if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                            assertFalse(impl.contains(longitude, latitude))
                        }
                    }
                    for (k in 0 until 50) {
                        val point = GeoTestUtil.nextPointNear(polygon)
                        val latitude = point[0]
                        val longitude = point[1]
                        if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                            assertFalse(impl.contains(longitude, latitude))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testEdgeInsideness() {
        val poly = Polygon2D.create(Polygon(doubleArrayOf(-2.0, -2.0, 2.0, 2.0, -2.0), doubleArrayOf(-2.0, 2.0, 2.0, -2.0, -2.0)))
        assertTrue(poly.contains(-2.0, -2.0))
        assertTrue(poly.contains(2.0, -2.0))
        assertTrue(poly.contains(-2.0, 2.0))
        assertTrue(poly.contains(2.0, 2.0))
        assertTrue(poly.contains(-1.0, -2.0))
        assertTrue(poly.contains(0.0, -2.0))
        assertTrue(poly.contains(1.0, -2.0))
        assertTrue(poly.contains(-1.0, 2.0))
        assertTrue(poly.contains(0.0, 2.0))
        assertTrue(poly.contains(1.0, 2.0))
        assertTrue(poly.contains(2.0, -1.0))
        assertTrue(poly.contains(2.0, 0.0))
        assertTrue(poly.contains(2.0, 1.0))
        assertTrue(poly.contains(-2.0, -1.0))
        assertTrue(poly.contains(-2.0, 0.0))
        assertTrue(poly.contains(-2.0, 1.0))
    }

    @Test
    fun testIntersectsSameEdge() {
        val poly = Polygon2D.create(Polygon(doubleArrayOf(-2.0, -2.0, 2.0, 2.0, -2.0), doubleArrayOf(-2.0, 2.0, 2.0, -2.0, -2.0)))
        assertTrue(poly.containsTriangle(-1.0, -1.0, 1.0, 1.0, -1.0, -1.0))
        assertTrue(poly.containsTriangle(-2.0, -2.0, 2.0, 2.0, -2.0, -2.0))
        assertTrue(poly.intersectsTriangle(-1.0, -1.0, 1.0, 1.0, -1.0, -1.0))
        assertTrue(poly.intersectsTriangle(-2.0, -2.0, 2.0, 2.0, -2.0, -2.0))
        assertFalse(poly.containsTriangle(-4.0, -4.0, 4.0, 4.0, -4.0, -4.0))
        assertFalse(poly.containsTriangle(-2.0, -2.0, 4.0, 4.0, 4.0, 4.0))
        assertTrue(poly.intersectsTriangle(-4.0, -4.0, 4.0, 4.0, -4.0, -4.0))
        assertTrue(poly.intersectsTriangle(-2.0, -2.0, 4.0, 4.0, 4.0, 4.0))
        assertFalse(poly.containsTriangle(-1.0, -1.0, 3.0, 3.0, 1.0, 1.0))
        assertFalse(poly.containsTriangle(-2.0, -2.0, 3.0, 3.0, 2.0, 2.0))
        assertTrue(poly.intersectsTriangle(-1.0, -1.0, 3.0, 3.0, 1.0, 1.0))
        assertTrue(poly.intersectsTriangle(-2.0, -2.0, 3.0, 3.0, 2.0, 2.0))
        assertFalse(poly.containsTriangle(-4.0, -4.0, 7.0, 7.0, 4.0, 4.0))
        assertFalse(poly.containsTriangle(-2.0, -2.0, 7.0, 7.0, 4.0, 4.0))
        assertTrue(poly.intersectsTriangle(-4.0, -4.0, 7.0, 7.0, 4.0, 4.0))
        assertTrue(poly.intersectsTriangle(-2.0, -2.0, 7.0, 7.0, 4.0, 4.0))
    }

    @Test
    fun testContainsAgainstOriginal() {
        val iters = atLeast(100)
        for (i in 0 until iters) {
            var polygon = GeoTestUtil.nextPolygon()
            while (polygon.getHoles().isNotEmpty()) {
                polygon = GeoTestUtil.nextPolygon()
            }
            val impl = Polygon2D.create(polygon)
            for (j in 0 until 1000) {
                val point = GeoTestUtil.nextPointNear(polygon)
                val latitude = point[0]
                val longitude = point[1]
                val expected = GeoTestUtil.containsSlowly(polygon, longitude, latitude)
                assertEquals(expected, impl.contains(longitude, latitude))
            }
        }
    }

    @Test
    fun testRelateTriangle() {
        for (i in 0 until 100) {
            val polygon = GeoTestUtil.nextPolygon()
            val impl = Polygon2D.create(polygon)
            for (j in 0 until 100) {
                val a = GeoTestUtil.nextPointNear(polygon)
                val b = GeoTestUtil.nextPointNear(polygon)
                val c = GeoTestUtil.nextPointNear(polygon)
                if (impl.contains(a[1], a[0]) || impl.contains(b[1], b[0]) || impl.contains(c[1], c[0])) {
                    assertTrue(impl.intersectsTriangle(a[1], a[0], b[1], b[0], c[1], c[0]))
                }
            }
        }
    }

    @Test
    fun testRelateTriangleContainsPolygon() {
        val polygon = Polygon(doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0), doubleArrayOf(0.0, 1.0, 1.0, 0.0, 0.0))
        val impl = Polygon2D.create(polygon)
        assertTrue(impl.intersectsTriangle(-10.0, -1.0, 2.0, -1.0, 10.0, 10.0))
    }

    @Test
    fun testRelateTriangleEdgeCases() {
        for (i in 0 until 100) {
            val randomRadius = RandomNumbers.randomIntBetween(random(), 1000, 100000)
            val numVertices = RandomNumbers.randomIntBetween(random(), 100, 1000)
            val polygon = GeoTestUtil.createRegularPolygon(0.0, 0.0, randomRadius.toDouble(), numVertices)
            val impl = Polygon2D.create(polygon)
            for (j in 1 until numVertices) {
                val a = doubleArrayOf(0.0, 0.0)
                val b = doubleArrayOf(polygon.getPolyLat(j - 1), polygon.getPolyLon(j - 1))
                val c = if (random().nextBoolean()) doubleArrayOf(polygon.getPolyLat(j), polygon.getPolyLon(j)) else doubleArrayOf(a[0], a[1])
                assertTrue(impl.intersectsTriangle(a[0], a[1], b[0], b[1], c[0], c[1]))
            }
        }
    }

    @Test
    fun testLineCrossingPolygonPoints() {
        val p = Polygon(doubleArrayOf(0.0, -1.0, 0.0, 1.0, 0.0), doubleArrayOf(-1.0, 0.0, 1.0, 0.0, -1.0))
        val polygon2D = Polygon2D.create(p)
        val intersects = polygon2D.intersectsTriangle(
            GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(-1.5)),
            GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(0.0)),
            GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(1.5)),
            GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(0.0)),
            GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(-1.5)),
            GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(0.0))
        )
        assertTrue(intersects)
    }

    @Test
    fun testRandomLineCrossingPolygon() {
        val p = GeoTestUtil.createRegularPolygon(0.0, 0.0, 1000.0, TestUtil.nextInt(random(), 100, 10000))
        val polygon2D = Polygon2D.create(p)
        for (i in 0 until 1000) {
            val longitude = GeoTestUtil.nextLongitude()
            val latitude = GeoTestUtil.nextLatitude()
            val intersects = polygon2D.intersectsTriangle(
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(-longitude)),
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(-latitude)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(longitude)),
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(latitude)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(-longitude)),
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(-latitude))
            )
            assertTrue(intersects)
        }
    }
}
