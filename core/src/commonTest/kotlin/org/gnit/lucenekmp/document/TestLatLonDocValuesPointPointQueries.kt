package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * random bounding box, line, and polygon query tests for random indexed arrays of `latitude,
 * longitude` points
 */
class TestLatLonDocValuesPointPointQueries : BaseLatLonDocValueTestCase() {

    override fun getShapeType(): Any {
        return ShapeType.POINT
    }

    override fun createIndexableFields(name: String, o: Any): Array<Field> {
        val point = o as Point
        val fields = Array<Field>(1) { LatLonDocValuesField(FIELD_NAME, 0.0, 0.0) }
        fields[0] = LatLonDocValuesField(FIELD_NAME, point.lat, point.lon)
        return fields
    }

    override fun getValidator(): Validator {
        return PointValidator(this.ENCODER)
    }

    protected class PointValidator(encoder: Encoder) : Validator(encoder) {
        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val p = shape as Point
            if (queryRelation == QueryRelation.CONTAINS) {
                return testWithinQuery(query, createIndexableFields(p)) == Component2D.WithinRelation.CANDIDATE
            }
            return testComponentQuery(query, createIndexableFields(p))
        }

        private fun createIndexableFields(p: Point): Array<Field> {
            val encodedLat = GeoEncodingUtils.encodeLatitude(p.lat)
            val encodedLon = GeoEncodingUtils.encodeLongitude(p.lon)
            return arrayOf(
                ShapeField.Triangle(
                    "dummy",
                    encodedLon,
                    encodedLat,
                    encodedLon,
                    encodedLat,
                    encodedLon,
                    encodedLat
                )
            )
        }
    }

    // tests inherited from BaseSpatialTestCase
    @LuceneTestCase.Companion.Nightly
    @Test
    override fun testRandomBig() {
        doTestRandom(10000)
    }

    @Test
    override fun testSameShapeManyTimes() = super.testSameShapeManyTimes()

    @Test
    override fun testLowCardinalityShapeManyTimes() = super.testLowCardinalityShapeManyTimes()

    @Test
    override fun testRandomTiny() = super.testRandomTiny()

    @Test
    override fun testRandomMedium() = super.testRandomMedium()
}
