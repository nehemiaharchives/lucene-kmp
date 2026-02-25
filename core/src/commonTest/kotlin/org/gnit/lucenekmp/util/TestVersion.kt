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

import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
class TestVersion : LuceneTestCase() {

    @Test
    fun testOnOrAfter() {
        val versions =
            listOf(
                Version.LUCENE_10_0_0,
                Version.LUCENE_10_1_0,
                Version.LUCENE_10_2_0,
                Version.LUCENE_11_0_0,
                Version.LATEST,
                Version.LUCENE_CURRENT,
            )
        for (v in versions) {
            assertTrue(Version.LATEST.onOrAfter(v), "LATEST must be always onOrAfter($v)")
        }
        assertTrue(Version.LUCENE_11_0_0.onOrAfter(Version.fromBits(9, 0, 0)))
        assertTrue(Version.LUCENE_11_0_0.onOrAfter(Version.LUCENE_10_0_0))
        assertTrue(Version.LUCENE_11_0_0.onOrAfter(Version.LUCENE_10_1_0))
    }

    @Test
    fun testToString() {
        assertEquals("9.0.0", Version.fromBits(9, 0, 0).toString())
        assertEquals("10.0.0", Version.LUCENE_10_0_0.toString())
        assertEquals("10.1.0", Version.LUCENE_10_1_0.toString())
        assertEquals("11.0.0", Version.LUCENE_11_0_0.toString())
    }

    @Test
    fun testParseLeniently() {
        assertEquals(Version.LUCENE_11_0_0, Version.parseLeniently("11.0"))
        assertEquals(Version.LUCENE_11_0_0, Version.parseLeniently("11.0.0"))
        assertEquals(Version.LUCENE_11_0_0, Version.parseLeniently("LUCENE_11_0"))
        assertEquals(Version.LUCENE_11_0_0, Version.parseLeniently("LUCENE_11_0_0"))
        assertEquals(Version.LUCENE_10_0_0, Version.parseLeniently("10.0"))
        assertEquals(Version.LUCENE_10_0_0, Version.parseLeniently("10.0.0"))
        assertEquals(Version.LUCENE_10_0_0, Version.parseLeniently("LUCENE_10_0"))
        assertEquals(Version.LUCENE_10_0_0, Version.parseLeniently("LUCENE_10_0_0"))

        assertEquals(Version.LATEST, Version.parseLeniently("LATEST"))
        assertEquals(Version.LATEST, Version.parseLeniently("latest"))
        assertEquals(Version.LATEST, Version.parseLeniently("LUCENE_CURRENT"))
        assertEquals(Version.LATEST, Version.parseLeniently("lucene_current"))
    }

    @Test
    fun testParseLenientlyExceptions() {
        var expected =
            expectThrows(ParseException::class) {
                Version.parseLeniently("LUCENE")
            }
        assertTrue(expected.message!!.contains("LUCENE"))

        expected =
            expectThrows(ParseException::class) {
                Version.parseLeniently("LUCENE_610")
            }
        assertTrue(expected.message!!.contains("LUCENE_610"))

        expected =
            expectThrows(ParseException::class) {
                Version.parseLeniently("LUCENE61")
            }
        assertTrue(expected.message!!.contains("LUCENE61"))

        expected =
            expectThrows(ParseException::class) {
                Version.parseLeniently("LUCENE_7.0.0")
            }
        assertTrue(expected.message!!.contains("LUCENE_7.0.0"))
    }

    @Test
    fun testParseLenientlyOnAllConstants() {
        val versionsByName =
            listOf(
                "LUCENE_10_0_0" to Version.LUCENE_10_0_0,
                "LUCENE_10_1_0" to Version.LUCENE_10_1_0,
                "LUCENE_10_2_0" to Version.LUCENE_10_2_0,
                "LUCENE_11_0_0" to Version.LUCENE_11_0_0,
                "LATEST" to Version.LATEST,
                "LUCENE_CURRENT" to Version.LUCENE_CURRENT,
            )
        var atLeastOne = false
        for ((name, v) in versionsByName) {
            atLeastOne = true
            assertEquals(v, Version.parseLeniently(v.toString()))
            assertEquals(v, Version.parseLeniently(name))
            assertEquals(v, Version.parseLeniently(name.lowercase()))
        }
        assertTrue(atLeastOne)
    }

    @Test
    fun testParse() {
        assertEquals(Version.LUCENE_10_0_0, Version.parse("10.0.0"))
        assertEquals(Version.LUCENE_11_0_0, Version.parse("11.0.0"))

        // Version does not pass judgement on the major version:
        assertEquals(1, Version.parse("1.0").major)
        assertEquals(7, Version.parse("7.0.0").major)
    }

    @Test
    fun testForwardsCompatibility() {
        assertTrue(Version.parse("11.10.20").onOrAfter(Version.LUCENE_11_0_0))
        assertTrue(Version.parse("10.10.20").onOrAfter(Version.LUCENE_10_0_0))
        assertTrue(Version.parse("9.10.20").onOrAfter(Version.fromBits(9, 0, 0)))
    }

    @Test
    fun testParseExceptions() {
        var expected =
            expectThrows(ParseException::class) {
                Version.parse("LUCENE_7_0_0")
            }
        assertTrue(expected.message!!.contains("LUCENE_7_0_0"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.256")
            }
        assertTrue(expected.message!!.contains("7.256"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.-1")
            }
        assertTrue(expected.message!!.contains("7.-1"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.1.256")
            }
        assertTrue(expected.message!!.contains("7.1.256"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.1.-1")
            }
        assertTrue(expected.message!!.contains("7.1.-1"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.1.1.3")
            }
        assertTrue(expected.message!!.contains("7.1.1.3"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.1.1.-1")
            }
        assertTrue(expected.message!!.contains("7.1.1.-1"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.1.1.1")
            }
        assertTrue(expected.message!!.contains("7.1.1.1"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.1.1.2")
            }
        assertTrue(expected.message!!.contains("7.1.1.2"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.0.0.0")
            }
        assertTrue(expected.message!!.contains("7.0.0.0"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7.0.0.1.42")
            }
        assertTrue(expected.message!!.contains("7.0.0.1.42"))

        expected =
            expectThrows(ParseException::class) {
                Version.parse("7..0.1")
            }
        assertTrue(expected.message!!.contains("7..0.1"))
    }

    @Test
    fun testDeprecations() {
        // Reflection-based deprecation annotation checks from Java test are not available in KMP common.
        // Keep parity intent by checking that non-latest aliases map to the latest value.
        assertEquals(Version.LATEST, Version.LUCENE_11_0_0)
        assertEquals(Version.LATEST, Version.LUCENE_CURRENT)
    }

    @Test
    fun testNonFloatingPointCompliantVersionNumbers() {
        val version800 = Version.parse("8.0.0")
        assertTrue(Version.parse("8.10.0").onOrAfter(version800))
        assertTrue(Version.parse("8.10.0").onOrAfter(Version.parse("8.9.255")))
        assertTrue(Version.parse("8.128.0").onOrAfter(version800))
        assertTrue(Version.parse("8.255.0").onOrAfter(version800))

        val version400 = Version.parse("4.0.0")
        assertTrue(version800.onOrAfter(version400))
        assertTrue(Version.parse("8.128.0").onOrAfter(version400))
        assertFalse(version400.onOrAfter(version800))
    }

    @Test
    fun testLatestVersionCommonBuild() {
        // common-build.xml sets 'tests.LUCENE_VERSION', if not, we skip this test!
        val commonBuildVersion = System.getProperty("tests.LUCENE_VERSION")
        if (commonBuildVersion == null) {
            return
        }
        assertEquals(
            Version.LATEST.toString(),
            commonBuildVersion,
            "Version.LATEST does not match the one given in tests.LUCENE_VERSION property"
        )
    }

    @Test
    fun testEqualsHashCode() {
        val random = random()
        val version = "" + (4 + random.nextInt(1)) + "." + random.nextInt(10) + "." + random.nextInt(10)
        val v1 = Version.parseLeniently(version)
        val v2 = Version.parseLeniently(version)
        assertEquals(v1.hashCode(), v2.hashCode())
        assertEquals(v1, v2)
        val iters = 10 + random.nextInt(20)
        for (i in 0 until iters) {
            val v = "" + (4 + random.nextInt(1)) + "." + random.nextInt(10) + "." + random.nextInt(10)
            if (v == version) {
                assertEquals(Version.parseLeniently(v).hashCode(), v1.hashCode())
                assertEquals(Version.parseLeniently(v), v1)
            } else {
                assertFalse(Version.parseLeniently(v).equals(v1))
            }
        }
    }
}
