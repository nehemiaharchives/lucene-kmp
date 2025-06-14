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

import org.gnit.lucenekmp.geo.GeoEncodingUtils.decodeLatitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.decodeLongitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLatitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLatitudeCeil
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLongitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLongitudeCeil
import org.gnit.lucenekmp.geo.GeoUtils.MAX_LAT_INCL
import org.gnit.lucenekmp.geo.GeoUtils.MAX_LON_INCL
import org.gnit.lucenekmp.geo.GeoUtils.MIN_LAT_INCL
import org.gnit.lucenekmp.geo.GeoUtils.MIN_LON_INCL
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests methods in [GeoEncodingUtils] */
class TestGeoEncodingUtils : LuceneTestCase() {

    /**
     * step through some integers, ensuring they decode to their expected double values. double values
     * start at -90 and increase by LATITUDE_DECODE for each integer. check edge cases within the
     * double range and random doubles within the range too.
     */
    @Test
    fun testLatitudeQuantization() {
        val LATITUDE_DECODE = 180.0 / (1L shl 32)
        val random = random()
        for (i in 0 until 10000) {
            val encoded = random.nextInt()
            val min = MIN_LAT_INCL + (encoded - Int.MIN_VALUE.toLong()) * LATITUDE_DECODE
            val decoded = decodeLatitude(encoded)
            // should exactly equal expected value
            assertEquals(min, decoded, 0.0)
            // should round-trip
            assertEquals(encoded, encodeLatitude(decoded))
            assertEquals(encoded, encodeLatitudeCeil(decoded))
            // test within the range
            if (encoded != Int.MAX_VALUE) {
                // this is the next representable value
                // all double values between [min .. max) should encode to the current integer
                // all double values between (min .. max] should encodeCeil to the next integer.
                val max = min + LATITUDE_DECODE
                assertEquals(max, decodeLatitude(encoded + 1), 0.0)
                assertEquals(encoded + 1, encodeLatitude(max))
                assertEquals(encoded + 1, encodeLatitudeCeil(max))

                // first and last doubles in range that will be quantized
                val minEdge = min.nextUp()
                val maxEdge = max.nextDown()
                assertEquals(encoded, encodeLatitude(minEdge))
                assertEquals(encoded + 1, encodeLatitudeCeil(minEdge))
                assertEquals(encoded, encodeLatitude(maxEdge))
                assertEquals(encoded + 1, encodeLatitudeCeil(maxEdge))

                // check random values within the double range
                val minBits = NumericUtils.doubleToSortableLong(minEdge)
                val maxBits = NumericUtils.doubleToSortableLong(maxEdge)
                for (j in 0 until 100) {
                    val valueBits = minBits + random.nextLong(0, (maxBits - minBits) + 1)
                    val value = NumericUtils.sortableLongToDouble(valueBits)
                    // round down
                    assertEquals(encoded, encodeLatitude(value))
                    // round up
                    assertEquals(encoded + 1, encodeLatitudeCeil(value))
                }
            }
        }
    }

    /**
     * step through some integers, ensuring they decode to their expected double values. double values
     * start at -180 and increase by LONGITUDE_DECODE for each integer. check edge cases within the
     * double range and a random doubles within the range too.
     */
    @Test
    fun testLongitudeQuantization() {
        val LONGITUDE_DECODE = 360.0 / (1L shl 32)
        val random = random()
        for (i in 0 until 10000) {
            val encoded = random.nextInt()
            val min = MIN_LON_INCL + (encoded - Int.MIN_VALUE.toLong()) * LONGITUDE_DECODE
            val decoded = decodeLongitude(encoded)
            // should exactly equal expected value
            assertEquals(min, decoded, 0.0)
            // should round-trip
            assertEquals(encoded, encodeLongitude(decoded))
            assertEquals(encoded, encodeLongitudeCeil(decoded))
            // test within the range
            if (encoded != Int.MAX_VALUE) {
                // this is the next representable value
                // all double values between [min .. max) should encode to the current integer
                // all double values between (min .. max] should encodeCeil to the next integer.
                val max = min + LONGITUDE_DECODE
                assertEquals(max, decodeLongitude(encoded + 1), 0.0)
                assertEquals(encoded + 1, encodeLongitude(max))
                assertEquals(encoded + 1, encodeLongitudeCeil(max))

                // first and last doubles in range that will be quantized
                val minEdge = min.nextUp()
                val maxEdge = max.nextDown()
                assertEquals(encoded, encodeLongitude(minEdge))
                assertEquals(encoded + 1, encodeLongitudeCeil(minEdge))
                assertEquals(encoded, encodeLongitude(maxEdge))
                assertEquals(encoded + 1, encodeLongitudeCeil(maxEdge))

                // check random values within the double range
                val minBits = NumericUtils.doubleToSortableLong(minEdge)
                val maxBits = NumericUtils.doubleToSortableLong(maxEdge)
                for (j in 0 until 100) {
                    val valueBits = minBits + random.nextLong(0, (maxBits - minBits) + 1)
                    val value = NumericUtils.sortableLongToDouble(valueBits)
                    // round down
                    assertEquals(encoded, encodeLongitude(value))
                    // round up
                    assertEquals(encoded + 1, encodeLongitudeCeil(value))
                }
            }
        }
    }

    // check edge/interesting cases explicitly
    @Test
    fun testEncodeEdgeCases() {
        assertEquals(Int.MIN_VALUE, encodeLatitude(MIN_LAT_INCL))
        assertEquals(Int.MIN_VALUE, encodeLatitudeCeil(MIN_LAT_INCL))
        assertEquals(Int.MAX_VALUE, encodeLatitude(MAX_LAT_INCL))
        assertEquals(Int.MAX_VALUE, encodeLatitudeCeil(MAX_LAT_INCL))

        assertEquals(Int.MIN_VALUE, encodeLongitude(MIN_LON_INCL))
        assertEquals(Int.MIN_VALUE, encodeLongitudeCeil(MIN_LON_INCL))
        assertEquals(Int.MAX_VALUE, encodeLongitude(MAX_LON_INCL))
        assertEquals(Int.MAX_VALUE, encodeLongitudeCeil(MAX_LON_INCL))
    }
}

