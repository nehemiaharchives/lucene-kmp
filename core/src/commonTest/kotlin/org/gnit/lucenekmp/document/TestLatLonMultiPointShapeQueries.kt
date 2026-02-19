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
class TestLatLonMultiPointShapeQueries : BaseLatLonShapeTestCase() {

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
        val allFields = mutableListOf<Field>()
        for (point in points) {
            allFields.addAll(LatLonShape.createIndexableFields(name, point.lat, point.lon))
        }
        return allFields.toTypedArray()
    }

    override fun getValidator(): Validator {
        return MultiPointValidator(ENCODER)
    }

    protected class MultiPointValidator(encoder: Encoder) : Validator(encoder) {
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

    // tests inherited from BaseLatLonShapeTestCase

    @Test
    override fun testBoundingBoxQueriesEquivalence() = super.testBoundingBoxQueriesEquivalence()

    @Test
    override fun testBoxQueryEqualsAndHashcode() = super.testBoxQueryEqualsAndHashcode()

    @Test
    override fun testLineQueryEqualsAndHashcode() = super.testLineQueryEqualsAndHashcode()

    @Test
    override fun testPolygonQueryEqualsAndHashcode() = super.testPolygonQueryEqualsAndHashcode()

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
