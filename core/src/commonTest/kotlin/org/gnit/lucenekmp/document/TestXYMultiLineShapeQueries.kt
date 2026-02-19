package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * random cartesian bounding box, line, and polygon query tests for random indexed arrays of
 * cartesian [XYLine] types
 */
class TestXYMultiLineShapeQueries : BaseXYShapeTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.LINE
    }

    override fun nextShape(): Any {
        val n = random().nextInt(4) + 1
        val lines = arrayOfNulls<XYLine>(n)
        for (i in 0..<n) {
            lines[i] = nextLine()
        }
        @Suppress("UNCHECKED_CAST")
        return lines as Array<XYLine>
    }

    override fun createIndexableFields(name: String, o: Any): Array<Field> {
        val lines = o as Array<XYLine>
        val allFields = mutableListOf<Field>()
        for (line in lines) {
            allFields.addAll(XYShape.createIndexableFields(name, line))
        }
        return allFields.toTypedArray()
    }

    override fun getValidator(): Validator {
        return MultiLineValidator(ENCODER)
    }

    protected class MultiLineValidator(encoder: Encoder) : Validator(encoder) {
        private var LINEVALIDATOR: TestXYLineShapeQueries.LineValidator =
            TestXYLineShapeQueries.LineValidator(encoder)

        override fun setRelation(relation: QueryRelation): Validator {
            super.setRelation(relation)
            LINEVALIDATOR.setRelation(relation)
            return this
        }

        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val lines = shape as Array<XYLine>
            for (l in lines) {
                val b = LINEVALIDATOR.testComponentQuery(query, l)
                if (b && queryRelation == ShapeField.QueryRelation.INTERSECTS) {
                    return true
                } else if (b && queryRelation == QueryRelation.CONTAINS) {
                    return true
                } else if (!b && queryRelation == ShapeField.QueryRelation.DISJOINT) {
                    return false
                } else if (!b && queryRelation == ShapeField.QueryRelation.WITHIN) {
                    return false
                }
            }
            return queryRelation != ShapeField.QueryRelation.INTERSECTS && queryRelation != QueryRelation.CONTAINS
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
