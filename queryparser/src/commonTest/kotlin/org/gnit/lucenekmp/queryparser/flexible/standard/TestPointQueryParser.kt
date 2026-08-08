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
package org.gnit.lucenekmp.queryparser.flexible.standard

import org.gnit.lucenekmp.document.DoublePoint
import org.gnit.lucenekmp.document.FloatPoint
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.jdkport.NumberFormat
import org.gnit.lucenekmp.queryparser.flexible.standard.config.PointsConfig
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

/** Simple test for point field integration into the flexible QP */
class TestPointQueryParser : LuceneTestCase() {

    @Test
    fun testIntegers() {
        val parser = StandardQueryParser()
        val pointsConfig: MutableMap<String, PointsConfig> = mutableMapOf()
        pointsConfig["intField"] = PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Int::class)
        parser.pointsConfigMap = pointsConfig

        assertEquals(
            IntPoint.newRangeQuery("intField", 1, 3), parser.parse("intField:[1 TO 3]", "body")
        )
        assertEquals(IntPoint.newRangeQuery("intField", 1, 1), parser.parse("intField:1", "body"))
    }

    @Test
    fun testLongs() {
        val parser = StandardQueryParser()
        val pointsConfig: MutableMap<String, PointsConfig> = mutableMapOf()
        pointsConfig["longField"] = PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Long::class)
        parser.pointsConfigMap = pointsConfig

        assertEquals(
            LongPoint.newRangeQuery("longField", 1, 3), parser.parse("longField:[1 TO 3]", "body")
        )
        assertEquals(LongPoint.newRangeQuery("longField", 1, 1), parser.parse("longField:1", "body"))
    }

    @Test
    fun testFloats() {
        val parser = StandardQueryParser()
        val pointsConfig: MutableMap<String, PointsConfig> = mutableMapOf()
        pointsConfig["floatField"] = PointsConfig(NumberFormat.getNumberInstance(Locale.ROOT), Float::class)
        parser.pointsConfigMap = pointsConfig

        assertEquals(
            FloatPoint.newRangeQuery("floatField", 1.5F, 3.6F),
            parser.parse("floatField:[1.5 TO 3.6]", "body"),
        )
        assertEquals(
            FloatPoint.newRangeQuery("floatField", 1.5F, 1.5F), parser.parse("floatField:1.5", "body")
        )
    }

    @Test
    fun testDoubles() {
        val parser = StandardQueryParser()
        val pointsConfig: MutableMap<String, PointsConfig> = mutableMapOf()
        pointsConfig["doubleField"] = PointsConfig(NumberFormat.getNumberInstance(Locale.ROOT), Double::class)
        parser.pointsConfigMap = pointsConfig

        assertEquals(
            DoublePoint.newRangeQuery("doubleField", 1.5, 3.6),
            parser.parse("doubleField:[1.5 TO 3.6]", "body"),
        )
        assertEquals(
            DoublePoint.newRangeQuery("doubleField", 1.5, 1.5),
            parser.parse("doubleField:1.5", "body"),
        )
    }
}
