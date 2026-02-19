package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * random bounding box, line, and polygon query tests for random indexed arrays of `latitude,
 * longitude` points
 */
class TestLatLonMultiPointPointQueries : BaseLatLonPointTestCase() {

    override fun getShapeType(): Any {
        return ShapeType.POINT
    }

    override fun nextShape(): Any {
        val n = random().nextInt(4) + 1
        val points = Array(n) { Point(0.0, 0.0) }
        for (i in 0..<n) {
            points[i] = ShapeType.POINT.nextShape() as Point
        }
        return points
    }

    override fun createIndexableFields(name: String, o: Any): Array<Field> {
        val points = o as Array<Point>
        val fields = Array<Field>(points.size) { LatLonPoint(FIELD_NAME, 0.0, 0.0) }
        for (i in points.indices) {
            fields[i] = LatLonPoint(FIELD_NAME, points[i].lat, points[i].lon)
        }
        return fields
    }

    override fun getValidator(): Validator {
        return MultiPointValidator(ENCODER)
    }

    protected inner class MultiPointValidator(encoder: Encoder) : Validator(encoder) {
        var POINTVALIDATOR: TestLatLonPointShapeQueries.PointValidator

        init {
            POINTVALIDATOR = TestLatLonPointShapeQueries.PointValidator(encoder)
        }

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

    // tests inherited from BaseLatLonPointTestCase

    @Test
    override fun testBoundingBoxQueriesEquivalence() = super.testBoundingBoxQueriesEquivalence()

    @Test
    override fun testQueryEqualsAndHashcode() = super.testQueryEqualsAndHashcode()

    // tests inherited from BaseSpatialTestCase

    @LuceneTestCase.Companion.Nightly
    @Test
    override fun testRandomBig() {
        doTestRandom(100) // TODO reduced from 10000 to 100 for dev speed
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
