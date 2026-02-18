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
class TestLatLonDocValuesMultiPointPointQueries : BaseLatLonDocValueTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.POINT
    }

    override fun nextShape(): Any {
        val n = LuceneTestCase.random().nextInt(4) + 1
        val points = Array(n) { Point(0.0, 0.0) }
        for (i in 0..<n) {
            points[i] = ShapeType.POINT.nextShape() as Point
        }
        return points
    }

    override fun createIndexableFields(name: String, o: Any): Array<Field> {
        val points = o as Array<Point>
        val fields = Array<Field>(points.size) { LatLonDocValuesField(FIELD_NAME, 0.0, 0.0) }
        for (i in points.indices) {
            fields[i] = LatLonDocValuesField(FIELD_NAME, points[i].lat, points[i].lon)
        }
        return fields
    }

    override fun getValidator(): Validator {
        return MultiPointValidator(ENCODER)
    }

    protected inner class MultiPointValidator(encoder: Encoder) : Validator(encoder) {
        var POINTVALIDATOR: PointValidator = PointValidator(encoder)

        override fun setRelation(relation: QueryRelation): Validator {
            super.setRelation(relation)
            POINTVALIDATOR.setRelation(relation)
            return this
        }

        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val points = shape as Array<Point>
            for (p in points) {
                val b = POINTVALIDATOR.testComponentQuery(query, p)
                if (b && queryRelation == QueryRelation.INTERSECTS) {
                    return true
                } else if (b && queryRelation == QueryRelation.CONTAINS) {
                    return true
                } else if (!b && queryRelation == QueryRelation.DISJOINT) {
                    return false
                } else if (!b && queryRelation == QueryRelation.WITHIN) {
                    return false
                }
            }
            return queryRelation != QueryRelation.INTERSECTS && queryRelation != QueryRelation.CONTAINS
        }
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
        doTestRandom(10) // TODO reduced from 10000 to 10 for dev speed
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
