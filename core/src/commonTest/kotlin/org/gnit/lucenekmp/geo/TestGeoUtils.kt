/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.geo.EarthDebugger
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.SloppyMath
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestGeoUtils : LuceneTestCase() {
    @Test
    fun testRandomCircleToBBox() {
        val iters = atLeast(100)
        repeat(iters) {
            val centerLat = GeoTestUtil.nextLatitude()
            val centerLon = GeoTestUtil.nextLongitude()
            val radiusMeters = if (random().nextBoolean()) {
                random().nextDouble() * 444000
            } else {
                random().nextDouble() * 50_000_000
            }
            val bbox = Rectangle.fromPointDistance(centerLat, centerLon, radiusMeters)
            repeat(1000) {
                val point = GeoTestUtil.nextPointNear(bbox)
                val lat = point[0]
                val lon = point[1]
                val distanceMeters = SloppyMath.haversinMeters(centerLat, centerLon, lat, lon)
                val haversinSays = distanceMeters <= radiusMeters
                val bboxSays = if (bbox.crossesDateline()) {
                    if (lat >= bbox.minLat && lat <= bbox.maxLat) {
                        lon <= bbox.maxLon || lon >= bbox.minLon
                    } else {
                        false
                    }
                } else {
                    lat >= bbox.minLat && lat <= bbox.maxLat && lon >= bbox.minLon && lon <= bbox.maxLon
                }
                if (haversinSays) {
                    assertTrue(bboxSays, "point was within the distance according to haversin, but the bbox doesn't contain it")
                }
            }
        }
    }

    @Test
    fun testBoundingBoxOpto() {
        val iters = atLeast(100)
        repeat(iters) {
            val lat = GeoTestUtil.nextLatitude()
            val lon = GeoTestUtil.nextLongitude()
            val radius = 50_000_000 * random().nextDouble()
            val box = Rectangle.fromPointDistance(lat, lon, radius)
            val box1: Rectangle
            val box2: Rectangle?
            if (box.crossesDateline()) {
                box1 = Rectangle(box.minLat, box.maxLat, -180.0, box.maxLon)
                box2 = Rectangle(box.minLat, box.maxLat, box.minLon, 180.0)
            } else {
                box1 = box
                box2 = null
            }
            repeat(1000) {
                val point = GeoTestUtil.nextPointNear(box)
                val lat2 = point[0]
                val lon2 = point[1]
                if (SloppyMath.haversinMeters(lat, lon, lat2, lon2) <= radius) {
                    assertTrue(lat2 >= box.minLat && lat2 <= box.maxLat)
                    assertTrue(lon2 >= box1.minLon && lon2 <= box1.maxLon || (box2 != null && lon2 >= box2.minLon && lon2 <= box2.maxLon))
                }
            }
        }
    }

    @Test
    fun testHaversinOpto() {
        val iters = atLeast(100)
        repeat(iters) {
            val lat = GeoTestUtil.nextLatitude()
            val lon = GeoTestUtil.nextLongitude()
            val radius = 50_000_000 * random().nextDouble()
            val box = Rectangle.fromPointDistance(lat, lon, radius)
            if (box.maxLon - lon < 90 && lon - box.minLon < 90) {
                val minPartialDistance = max(
                    SloppyMath.haversinSortKey(lat, lon, lat, box.maxLon),
                    SloppyMath.haversinSortKey(lat, lon, box.maxLat, lon)
                )
                repeat(10000) {
                    val p = GeoTestUtil.nextPointNear(box)
                    val lat2 = p[0]
                    val lon2 = p[1]
                    if (SloppyMath.haversinMeters(lat, lon, lat2, lon2) <= radius) {
                        assertTrue(SloppyMath.haversinSortKey(lat, lon, lat2, lon2) <= minPartialDistance)
                    }
                }
            }
        }
    }

    @Test
    fun testInfiniteRect() {
        repeat(1000) {
            val centerLat = GeoTestUtil.nextLatitude()
            val centerLon = GeoTestUtil.nextLongitude()
            val rect = Rectangle.fromPointDistance(centerLat, centerLon, Double.POSITIVE_INFINITY)
            assertEquals(-180.0, rect.minLon, 0.0)
            assertEquals(180.0, rect.maxLon, 0.0)
            assertEquals(-90.0, rect.minLat, 0.0)
            assertEquals(90.0, rect.maxLat, 0.0)
            assertFalse(rect.crossesDateline())
        }
    }

    @Test
    fun testAxisLat() {
        val earthCircumference = 2.0 * Math.PI * GeoUtils.EARTH_MEAN_RADIUS_METERS
        assertEquals(90.0, Rectangle.axisLat(0.0, earthCircumference / 4), 0.0)
        repeat(100) {
            val reallyBig = random().nextInt(10) == 0
            val maxRadius = if (reallyBig) 1.1 * earthCircumference else earthCircumference / 8
            val radius = maxRadius * random().nextDouble()
            var prevAxisLat = Rectangle.axisLat(0.0, radius)
            var lat = 0.1
            while (lat < 90) {
                val nextAxisLat = Rectangle.axisLat(lat, radius)
                val bbox = Rectangle.fromPointDistance(lat, 180.0, radius)
                val dist = SloppyMath.haversinMeters(lat, 180.0, nextAxisLat, bbox.maxLon)
                if (nextAxisLat < GeoUtils.MAX_LAT_INCL) {
                    assertEquals(dist, radius, 0.1, "lat = $lat")
                }
                assertTrue(prevAxisLat <= nextAxisLat, "lat = $lat")
                prevAxisLat = nextAxisLat
                lat += 0.1
            }
            prevAxisLat = Rectangle.axisLat(-0.0, radius)
            lat = -0.1
            while (lat > -90) {
                val nextAxisLat = Rectangle.axisLat(lat, radius)
                val bbox = Rectangle.fromPointDistance(lat, 180.0, radius)
                val dist = SloppyMath.haversinMeters(lat, 180.0, nextAxisLat, bbox.maxLon)
                if (nextAxisLat > GeoUtils.MIN_LAT_INCL) {
                    assertEquals(dist, radius, 0.1, "lat = $lat")
                }
                assertTrue(prevAxisLat >= nextAxisLat, "lat = $lat")
                prevAxisLat = nextAxisLat
                lat -= 0.1
            }
        }
    }

    @Test
    fun testCircleOpto() {
        val r = random()
        var i = 0
        val iters = atLeast(r, 3)
        while (i < iters) {
            val centerLat = -90 + 180.0 * r.nextDouble()
            val centerLon = -180 + 360.0 * r.nextDouble()
            val radius = 50_000_000.0 * r.nextDouble()
            val box = Rectangle.fromPointDistance(centerLat, centerLon, radius)
            if (box.crossesDateline()) {
                continue
            }
            val axisLat = Rectangle.axisLat(centerLat, radius)
            val innerIters = atLeast(100)
            repeat(innerIters) {
                val latBounds = doubleArrayOf(-90.0, box.minLat, axisLat, box.maxLat, 90.0)
                val lonBounds = doubleArrayOf(-180.0, box.minLon, centerLon, box.maxLon, 180.0)
                val maxLatRow = r.nextInt(4)
                val latMax = randomInRange(r, latBounds[maxLatRow], latBounds[maxLatRow + 1])
                val minLonCol = r.nextInt(4)
                val lonMin = randomInRange(r, lonBounds[minLonCol], lonBounds[minLonCol + 1])
                val minLatMaxRow = if (maxLatRow == 3) 3 else maxLatRow + 1
                val minLatRow = r.nextInt(minLatMaxRow)
                val latMin = randomInRange(r, latBounds[minLatRow], kotlin.math.min(latBounds[minLatRow + 1], latMax))
                val maxLonMinCol = max(minLonCol, 1)
                val maxLonCol = maxLonMinCol + r.nextInt(4 - maxLonMinCol)
                val lonMax = randomInRange(r, kotlin.math.max(lonBounds[maxLonCol], lonMin), lonBounds[maxLonCol + 1])
                require(latMax >= latMin)
                require(lonMax >= lonMin)
                if (isDisjoint(centerLat, centerLon, radius, axisLat, latMin, latMax, lonMin, lonMax)) {
                    repeat(200) {
                        var lat = latMin + (latMax - latMin) * r.nextDouble()
                        var lon = lonMin + (lonMax - lonMin) * r.nextDouble()
                        if (r.nextBoolean()) {
                            when (r.nextInt(4)) {
                                0 -> lat = latMin
                                1 -> lat = latMax
                                2 -> lon = lonMin
                                3 -> lon = lonMax
                            }
                        }
                        val distance = SloppyMath.haversinMeters(centerLat, centerLon, lat, lon)
                        try {
                            val msg = """
                                isDisjoint(
                                centerLat=$centerLat
                                centerLon=$centerLon
                                radius=$radius
                                latMin=$latMin
                                latMax=$latMax
                                lonMin=$lonMin
                                lonMax=$lonMax) == false BUT
                                haversin($centerLat, $centerLon, $lat, $lon) = $distance
                                bbox=${Rectangle.fromPointDistance(centerLat, centerLon, radius)}
                            """.trimIndent()
                            assertTrue(distance > radius, msg)
                        } catch (e: AssertionError) {
                            val ed = EarthDebugger()
                            ed.addRect(latMin, latMax, lonMin, lonMax)
                            ed.addCircle(centerLat, centerLon, radius, true)
                            println(ed.finish())
                            throw e
                        }
                    }
                }
            }
            i++
        }
    }

    private fun randomInRange(random: kotlin.random.Random, min: Double, max: Double): Double {
        return min + (max - min) * random.nextDouble()
    }

    private fun isDisjoint(
        centerLat: Double,
        centerLon: Double,
        radius: Double,
        axisLat: Double,
        latMin: Double,
        latMax: Double,
        lonMin: Double,
        lonMax: Double
    ): Boolean {
        if ((centerLon < lonMin || centerLon > lonMax) &&
            (axisLat + Rectangle.AXISLAT_ERROR < latMin || axisLat - Rectangle.AXISLAT_ERROR > latMax)
        ) {
            if (SloppyMath.haversinMeters(centerLat, centerLon, latMin, lonMin) > radius &&
                SloppyMath.haversinMeters(centerLat, centerLon, latMin, lonMax) > radius &&
                SloppyMath.haversinMeters(centerLat, centerLon, latMax, lonMin) > radius &&
                SloppyMath.haversinMeters(centerLat, centerLon, latMax, lonMax) > radius
            ) {
                return true
            }
        }
        return false
    }

    @Test
    fun testWithin90LonDegrees() {
        assertTrue(GeoUtils.within90LonDegrees(0.0, -80.0, 80.0))
        assertFalse(GeoUtils.within90LonDegrees(0.0, -100.0, 80.0))
        assertFalse(GeoUtils.within90LonDegrees(0.0, -80.0, 100.0))
        assertTrue(GeoUtils.within90LonDegrees(-150.0, 140.0, 170.0))
        assertFalse(GeoUtils.within90LonDegrees(-150.0, 120.0, 150.0))
        assertTrue(GeoUtils.within90LonDegrees(150.0, -170.0, -140.0))
        assertFalse(GeoUtils.within90LonDegrees(150.0, -150.0, -120.0))
    }
}
