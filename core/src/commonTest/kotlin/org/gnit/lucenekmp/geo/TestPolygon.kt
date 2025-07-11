package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.jdkport.ParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestPolygon : LuceneTestCase() {

    fun testPolygonNullPolyLats(){
        // this test does not need to be implemented because of kotlin null safety
    }

    fun testPolygonNullPolyLons(){
        // this test does not need to be implemented because of kotlin null safety
    }

    @Test
    fun testPolygonLine() {
        val e = expectThrows(IllegalArgumentException::class) {
            Polygon(doubleArrayOf(18.0, 18.0, 18.0), doubleArrayOf(-66.0, -65.0, -66.0))
        }
        assertTrue(e!!.message!!.contains("at least 4 polygon points required"))
    }

    @Test
    fun testPolygonBogus() {
        val e = expectThrows(IllegalArgumentException::class) {
            Polygon(doubleArrayOf(18.0, 18.0, 19.0, 19.0), doubleArrayOf(-66.0, -65.0, -65.0, -66.0, -66.0))
        }
        assertTrue(e!!.message!!.contains("must be equal length"))
    }

    @Test
    fun testPolygonNotClosed() {
        val e = expectThrows(IllegalArgumentException::class) {
            Polygon(doubleArrayOf(18.0, 18.0, 19.0, 19.0, 19.0), doubleArrayOf(-66.0, -65.0, -65.0, -66.0, -67.0))
        }
        assertTrue(e!!.message!!.contains("it must close itself"))
    }

    @Test
    fun testGeoJSONPolygon() {
        val json = """
            {
              "type": "Polygon",
              "coordinates": [
                [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                  [100.0, 1.0], [100.0, 0.0] ]
              ]
            }
        """.trimIndent()
        val polygons = Polygon.fromGeoJSON(json)!!
        assertEquals(1, polygons.size)
        assertEquals(
            Polygon(
                doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0),
                doubleArrayOf(100.0, 101.0, 101.0, 100.0, 100.0)
            ),
            polygons[0]
        )
    }

    @Test
    fun testGeoJSONPolygonWithHole() {
        val json = """
            {
              "type": "Polygon",
              "coordinates": [
                [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                  [100.0, 1.0], [100.0, 0.0] ],
                [ [100.5, 0.5], [100.5, 0.75], [100.75, 0.75], [100.75, 0.5], [100.5, 0.5] ]
              ]
            }
        """.trimIndent()
        val hole = Polygon(
            doubleArrayOf(0.5, 0.75, 0.75, 0.5, 0.5),
            doubleArrayOf(100.5, 100.5, 100.75, 100.75, 100.5)
        )
        val expected = Polygon(
            doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0),
            doubleArrayOf(100.0, 101.0, 101.0, 100.0, 100.0),
            hole
        )
        val polygons = Polygon.fromGeoJSON(json)!!
        assertEquals(1, polygons.size)
        assertEquals(expected, polygons[0])
    }

    @Test
    fun testGeoJSONMultiPolygon() {
        val json = """
            {
              "type": "MultiPolygon",
              "coordinates": [
                [
                  [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                    [100.0, 1.0], [100.0, 0.0] ]
                ],
                [
                  [ [10.0, 2.0], [11.0, 2.0], [11.0, 3.0],
                    [10.0, 3.0], [10.0, 2.0] ]
                ]
              ],
            }
        """.trimIndent()
        val polygons = Polygon.fromGeoJSON(json)!!
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

    @Test
    fun testGeoJSONTypeComesLast() {
        val json = """
            {
              "coordinates": [
                [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                  [100.0, 1.0], [100.0, 0.0] ]
              ],
              "type": "Polygon",
            }
        """.trimIndent()
        val polygons = Polygon.fromGeoJSON(json)!!
        assertEquals(1, polygons.size)
        assertEquals(
            Polygon(
                doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0),
                doubleArrayOf(100.0, 101.0, 101.0, 100.0, 100.0)
            ),
            polygons[0]
        )
    }

    @Test
    fun testGeoJSONPolygonFeature() {
        val json = """
            { "type": "Feature",
              "geometry": {
                "type": "Polygon",
                "coordinates": [
                  [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                    [100.0, 1.0], [100.0, 0.0] ]
                  ]
              },
              "properties": {
                "prop0": "value0",
                "prop1": {"this": "that"}
              }
            }
        """.trimIndent()
        val polygons = Polygon.fromGeoJSON(json)!!
        assertEquals(1, polygons.size)
        assertEquals(
            Polygon(
                doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0),
                doubleArrayOf(100.0, 101.0, 101.0, 100.0, 100.0)
            ),
            polygons[0]
        )
    }

    @Test
    fun testGeoJSONMultiPolygonFeature() {
        val json = """
            { "type": "Feature",
              "geometry": {
                  "type": "MultiPolygon",
                  "coordinates": [
                    [
                      [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                        [100.0, 1.0], [100.0, 0.0] ]
                    ],
                    [
                      [ [10.0, 2.0], [11.0, 2.0], [11.0, 3.0],
                        [10.0, 3.0], [10.0, 2.0] ]
                    ]
                  ]
              },
              "properties": {
                "prop0": "value0",
                "prop1": {"this": "that"}
              }
            }
        """.trimIndent()
        val polygons = Polygon.fromGeoJSON(json)!!
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

    @Test
    fun testGeoJSONFeatureCollectionWithSinglePolygon() {
        val b = StringBuilder()
        b.append("{ \"type\": \"FeatureCollection\",\n")
        b.append("  \"features\": [\n")
        b.append("    { \"type\": \"Feature\",\n")
        b.append("      \"geometry\": {\n")
        b.append("        \"type\": \"Polygon\",\n")
        b.append("        \"coordinates\": [\n")
        b.append("          [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],\n")
        b.append("            [100.0, 1.0], [100.0, 0.0] ]\n")
        b.append("          ]\n")
        b.append("      },\n")
        b.append("      \"properties\": {\n")
        b.append("        \"prop0\": \"value0\",\n")
        b.append("        \"prop1\": {\"this\": \"that\"}\n")
        b.append("      }\n")
        b.append("    }\n")
        b.append("  ]\n")
        b.append("}    \n")
        val json = b.toString()
        val expected = Polygon(
            doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0),
            doubleArrayOf(100.0, 101.0, 101.0, 100.0, 100.0)
        )
        val actual = Polygon.fromGeoJSON(json)!!
        assertEquals(1, actual.size)
        assertEquals(expected, actual[0])
    }

    @Test
    fun testIllegalGeoJSONExtraCrapAtEnd() {
        val json = """
            {
              "type": "Polygon",
              "coordinates": [
                [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                  [100.0, 1.0], [100.0, 0.0] ]
              ]
            }
            foo
        """.trimIndent()
        val e = expectThrows(ParseException::class) {
            Polygon.fromGeoJSON(json)
        }
        assertTrue(e!!.message!!.contains("unexpected character 'f' after end of GeoJSON object"))
    }

    @Test
    fun testIllegalGeoJSONLinkedCRS() {
        val json = """
            {
              "type": "Polygon",
              "coordinates": [
                [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                  [100.0, 1.0], [100.0, 0.0] ]
              ],
              "crs": {
                "type": "link",
                "properties": {
                  "href": "http://example.com/crs/42",
                  "type": "proj4"
                }
              }    
            }
        """.trimIndent()
        val e = expectThrows(ParseException::class) {
            Polygon.fromGeoJSON(json)
        }
        assertTrue(e!!.message!!.contains("cannot handle linked crs"))
    }

    @Test
    fun testIllegalGeoJSONMultipleFeatures() {
        val json = """
            { "type": "FeatureCollection",
              "features": [
                { "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [102.0, 0.5]},
                  "properties": {"prop0": "value0"}
                },
                { "type": "Feature",
                  "geometry": {
                  "type": "LineString",
                  "coordinates": [
                    [102.0, 0.0], [103.0, 1.0], [104.0, 0.0], [105.0, 1.0]
                    ]
                  },
                  "properties": {
                    "prop0": "value0",
                    "prop1": 0.0
                  }
                },
                { "type": "Feature",
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [
                      [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                        [100.0, 1.0], [100.0, 0.0] ]
                      ]
                  },
                  "properties": {
                    "prop0": "value0",
                    "prop1": {"this": "that"}
                  }
                }
              ]
            }    
        """.trimIndent()
        val e = expectThrows(ParseException::class) {
            Polygon.fromGeoJSON(json)
        }
        assertTrue(
            e!!.message!!.contains(
                "can only handle type FeatureCollection (if it has a single polygon geometry), Feature, Polygon or MultiPolygon, but got Point"
            )
        )
    }

    @Test
    fun testPolygonPropertiesCanBeStringArrays() {
        val json = """
            {
              "type": "Polygon",
              "coordinates": [
                [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                  [100.0, 1.0], [100.0, 0.0] ]
              ],
              "properties": {
                "array": [ "value" ]
              }
            }
        """.trimIndent()
        val polygons = Polygon.fromGeoJSON(json)!!
        assertEquals(1, polygons.size)
    }
}

