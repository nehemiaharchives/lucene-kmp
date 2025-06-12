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
package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.StrictMath
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSloppyMath : LuceneTestCase() {
    private val COS_DELTA = 1e-15
    private val ASIN_DELTA = 1e-7
    private val HAVERSIN_DELTA = 38e-2
    private val REASONABLE_HAVERSIN_DELTA = 1e-5

    @Test
    fun testCos() {
        assertTrue(SloppyMath.cos(Double.NaN).isNaN())
        assertTrue(SloppyMath.cos(Double.NEGATIVE_INFINITY).isNaN())
        assertTrue(SloppyMath.cos(Double.POSITIVE_INFINITY).isNaN())
        assertEquals(StrictMath.cos(1.0), SloppyMath.cos(1.0), COS_DELTA)
        assertEquals(StrictMath.cos(0.0), SloppyMath.cos(0.0), COS_DELTA)
        assertEquals(StrictMath.cos(Math.PI / 2), SloppyMath.cos(Math.PI / 2), COS_DELTA)
        assertEquals(StrictMath.cos(-Math.PI / 2), SloppyMath.cos(-Math.PI / 2), COS_DELTA)
        assertEquals(StrictMath.cos(Math.PI / 4), SloppyMath.cos(Math.PI / 4), COS_DELTA)
        assertEquals(StrictMath.cos(-Math.PI / 4), SloppyMath.cos(-Math.PI / 4), COS_DELTA)
        assertEquals(StrictMath.cos(Math.PI * 2 / 3), SloppyMath.cos(Math.PI * 2 / 3), COS_DELTA)
        assertEquals(StrictMath.cos(-Math.PI * 2 / 3), SloppyMath.cos(-Math.PI * 2 / 3), COS_DELTA)
        assertEquals(StrictMath.cos(Math.PI / 6), SloppyMath.cos(Math.PI / 6), COS_DELTA)
        assertEquals(StrictMath.cos(-Math.PI / 6), SloppyMath.cos(-Math.PI / 6), COS_DELTA)
        for (i in 0 until 10000) {
            var d = random().nextDouble() * SloppyMath.SIN_COS_MAX_VALUE_FOR_INT_MODULO
            if (random().nextBoolean()) {
                d = -d
            }
            assertEquals(StrictMath.cos(d), SloppyMath.cos(d), COS_DELTA)
        }
    }

    @Test
    fun testAsin() {
        assertTrue(SloppyMath.asin(Double.NaN).isNaN())
        assertTrue(SloppyMath.asin(2.0).isNaN())
        assertTrue(SloppyMath.asin(-2.0).isNaN())
        assertEquals(-Math.PI / 2, SloppyMath.asin(-1.0), ASIN_DELTA)
        assertEquals(-Math.PI / 3, SloppyMath.asin(-0.8660254), ASIN_DELTA)
        assertEquals(-Math.PI / 4, SloppyMath.asin(-0.7071068), ASIN_DELTA)
        assertEquals(-Math.PI / 6, SloppyMath.asin(-0.5), ASIN_DELTA)
        assertEquals(0.0, SloppyMath.asin(0.0), ASIN_DELTA)
        assertEquals(Math.PI / 6, SloppyMath.asin(0.5), ASIN_DELTA)
        assertEquals(Math.PI / 4, SloppyMath.asin(0.7071068), ASIN_DELTA)
        assertEquals(Math.PI / 3, SloppyMath.asin(0.8660254), ASIN_DELTA)
        assertEquals(Math.PI / 2, SloppyMath.asin(1.0), ASIN_DELTA)
        for (i in 0 until 10000) {
            var d = random().nextDouble()
            if (random().nextBoolean()) {
                d = -d
            }
            assertEquals(StrictMath.asin(d), SloppyMath.asin(d), ASIN_DELTA)
            assertTrue(SloppyMath.asin(d) >= -Math.PI / 2)
            assertTrue(SloppyMath.asin(d) <= Math.PI / 2)
        }
    }

    @Test
    fun testHaversin() {
        assertTrue(SloppyMath.haversinMeters(1.0, 1.0, 1.0, Double.NaN).isNaN())
        assertTrue(SloppyMath.haversinMeters(1.0, 1.0, Double.NaN, 1.0).isNaN())
        assertTrue(SloppyMath.haversinMeters(1.0, Double.NaN, 1.0, 1.0).isNaN())
        assertTrue(SloppyMath.haversinMeters(Double.NaN, 1.0, 1.0, 1.0).isNaN())

        assertEquals(0.0, SloppyMath.haversinMeters(0.0, 0.0, 0.0, 0.0), 0.0)
        assertEquals(0.0, SloppyMath.haversinMeters(0.0, -180.0, 0.0, -180.0), 0.0)
        assertEquals(0.0, SloppyMath.haversinMeters(0.0, -180.0, 0.0, 180.0), 0.0)
        assertEquals(0.0, SloppyMath.haversinMeters(0.0, 180.0, 0.0, 180.0), 0.0)
        assertEquals(0.0, SloppyMath.haversinMeters(90.0, 0.0, 90.0, 0.0), 0.0)
        assertEquals(0.0, SloppyMath.haversinMeters(90.0, -180.0, 90.0, -180.0), 0.0)
        assertEquals(0.0, SloppyMath.haversinMeters(90.0, -180.0, 90.0, 180.0), 0.0)
        assertEquals(0.0, SloppyMath.haversinMeters(90.0, 180.0, 90.0, 180.0), 0.0)

        val earthRadiusMs = 6_371_008.7714
        val halfCircle = earthRadiusMs * Math.PI
        assertEquals(halfCircle, SloppyMath.haversinMeters(0.0, 0.0, 0.0, 180.0), 0.0)

        val r = random()
        val randomLat1 = 40.7143528 + (r.nextInt(10) - 5) * 360.0
        val randomLon1 = -74.0059731 + (r.nextInt(10) - 5) * 360.0
        val randomLat2 = 40.65 + (r.nextInt(10) - 5) * 360.0
        val randomLon2 = -73.95 + (r.nextInt(10) - 5) * 360.0
        assertEquals(8_572.1137, SloppyMath.haversinMeters(randomLat1, randomLon1, randomLat2, randomLon2), 0.01)

        assertEquals(0.0, SloppyMath.haversinMeters(40.7143528, -74.0059731, 40.7143528, -74.0059731), 0.0)
        assertEquals(5_285.89, SloppyMath.haversinMeters(40.7143528, -74.0059731, 40.759011, -73.9844722), 0.01)
        assertEquals(462.10, SloppyMath.haversinMeters(40.7143528, -74.0059731, 40.718266, -74.007819), 0.01)
        assertEquals(1_054.98, SloppyMath.haversinMeters(40.7143528, -74.0059731, 40.7051157, -74.0088305), 0.01)
        assertEquals(1_258.12, SloppyMath.haversinMeters(40.7143528, -74.0059731, 40.7247222, -74.0), 0.01)
        assertEquals(2_028.52, SloppyMath.haversinMeters(40.7143528, -74.0059731, 40.731033, -73.9962255), 0.01)
        assertEquals(8_572.11, SloppyMath.haversinMeters(40.7143528, -74.0059731, 40.65, -73.95), 0.01)
    }

    @Test
    fun testHaversinSortKey() {
        val iters = atLeast(10000)
        for (i in 0 until iters) {
            val centerLat = GeoTestUtil.nextLatitude()
            val centerLon = GeoTestUtil.nextLongitude()
            val lat1 = GeoTestUtil.nextLatitude()
            val lon1 = GeoTestUtil.nextLongitude()
            val lat2 = GeoTestUtil.nextLatitude()
            val lon2 = GeoTestUtil.nextLongitude()

            val expected = compareValues(
                SloppyMath.haversinMeters(centerLat, centerLon, lat1, lon1),
                SloppyMath.haversinMeters(centerLat, centerLon, lat2, lon2)
            )
            val actual = compareValues(
                SloppyMath.haversinSortKey(centerLat, centerLon, lat1, lon1),
                SloppyMath.haversinSortKey(centerLat, centerLon, lat2, lon2)
            )
            val expectedSign = if (expected < 0) -1 else if (expected > 0) 1 else 0
            val actualSign = if (actual < 0) -1 else if (actual > 0) 1 else 0
            assertEquals(expectedSign, actualSign)
            assertEquals(
                SloppyMath.haversinMeters(centerLat, centerLon, lat1, lon1),
                SloppyMath.haversinMeters(SloppyMath.haversinSortKey(centerLat, centerLon, lat1, lon1)),
                0.0
            )
            assertEquals(
                SloppyMath.haversinMeters(centerLat, centerLon, lat2, lon2),
                SloppyMath.haversinMeters(SloppyMath.haversinSortKey(centerLat, centerLon, lat2, lon2)),
                0.0
            )
        }
    }

    @Test
    fun testHaversinFromSortKey() {
        assertEquals(0.0, SloppyMath.haversinMeters(0.0), 0.0)
    }

    @Test
    fun testAgainstSlowVersion() {
        for (i in 0 until 100_000) {
            val lat1 = GeoTestUtil.nextLatitude()
            val lon1 = GeoTestUtil.nextLongitude()
            val lat2 = GeoTestUtil.nextLatitude()
            val lon2 = GeoTestUtil.nextLongitude()

            val expected = slowHaversin(lat1, lon1, lat2, lon2)
            val actual = SloppyMath.haversinMeters(lat1, lon1, lat2, lon2)
            assertEquals(expected, actual, HAVERSIN_DELTA)
        }
    }

    @Test
    fun testAcrossWholeWorldSteps() {
        var lat1 = -90
        while (lat1 <= 90) {
            var lon1 = -180
            while (lon1 <= 180) {
                var lat2 = -90
                while (lat2 <= 90) {
                    var lon2 = -180
                    while (lon2 <= 180) {
                        val expected = slowHaversin(lat1.toDouble(), lon1.toDouble(), lat2.toDouble(), lon2.toDouble())
                        val actual = SloppyMath.haversinMeters(lat1.toDouble(), lon1.toDouble(), lat2.toDouble(), lon2.toDouble())
                        assertEquals(expected, actual, HAVERSIN_DELTA)
                        lon2 += 10
                    }
                    lat2 += 10
                }
                lon1 += 10
            }
            lat1 += 10
        }
    }

    @Test
    fun testAgainstSlowVersionReasonable() {
        for (i in 0 until 100_000) {
            val lat1 = GeoTestUtil.nextLatitude()
            val lon1 = GeoTestUtil.nextLongitude()
            val lat2 = GeoTestUtil.nextLatitude()
            val lon2 = GeoTestUtil.nextLongitude()

            val expected = SloppyMath.haversinMeters(lat1, lon1, lat2, lon2)
            if (expected < 1_000_000) {
                val actual = slowHaversin(lat1, lon1, lat2, lon2)
                assertEquals(expected, actual, REASONABLE_HAVERSIN_DELTA)
            }
        }
    }

    private fun slowHaversin(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val h1 = (1 - StrictMath.cos(Math.toRadians(lat2) - Math.toRadians(lat1))) / 2
        val h2 = (1 - StrictMath.cos(Math.toRadians(lon2) - Math.toRadians(lon1))) / 2
        val h = h1 + StrictMath.cos(Math.toRadians(lat1)) * StrictMath.cos(Math.toRadians(lat2)) * h2
        return 2 * 6_371_008.7714 * StrictMath.asin(min(1.0, sqrt(h)))
    }
}
