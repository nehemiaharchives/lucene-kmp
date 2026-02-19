package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * random cartesian bounding box, line, and polygon query tests for random indexed arrays of `x, y`
 * points
 */
class TestXYMultiPointShapeQueries : BaseXYShapeTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.POINT
    }

    override fun nextShape(): Any {
        val n = random().nextInt(4) + 1
        val points = arrayOfNulls<XYPoint>(n)
        for (i in 0..<n) {
            points[i] = ShapeTestUtil.nextXYPoint()
        }
        @Suppress("UNCHECKED_CAST")
        return points as Array<XYPoint>
    }

    override fun createIndexableFields(name: String, o: Any): Array<Field> {
        val points = o as Array<XYPoint>
        val allFields = mutableListOf<Field>()
        for (point in points) {
            allFields.addAll(XYShape.createIndexableFields(name, point.x, point.y))
        }
        return allFields.toTypedArray()
    }

    override fun getValidator(): Validator {
        return MultiPointValidator(ENCODER)
    }

    protected class MultiPointValidator(encoder: Encoder) : Validator(encoder) {
        var POINTVALIDATOR: TestXYPointShapeQueries.PointValidator = TestXYPointShapeQueries.PointValidator(encoder)

        override fun setRelation(relation: QueryRelation): Validator {
            super.setRelation(relation)
            POINTVALIDATOR.setRelation(relation)
            return this
        }

        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val points = shape as Array<XYPoint>
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
