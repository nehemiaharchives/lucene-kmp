package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * random bounding box, line, and polygon query tests for random indexed arrays of [Line] types
 */
class TestLatLonMultiLineShapeQueries : BaseLatLonShapeTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.LINE
    }

    override fun nextShape(): Any {
        val n = random().nextInt(4) + 1
        val lines = arrayOfNulls<Line>(n)
        for (i in 0..<n) {
            lines[i] = nextLine()
        }
        @Suppress("UNCHECKED_CAST")
        return lines as Array<Line>
    }

    override fun createIndexableFields(name: String, o: Any): Array<Field> {
        val lines = o as Array<Line>
        val allFields = mutableListOf<Field>()
        for (line in lines) {
            allFields.addAll(LatLonShape.createIndexableFields(name, line))
        }
        return allFields.toTypedArray()
    }

    override fun getValidator(): Validator {
        return MultiLineValidator(ENCODER)
    }

    protected class MultiLineValidator(encoder: Encoder) : Validator(encoder) {
        private var LINEVALIDATOR: LineValidator = LineValidator(encoder)

        override fun setRelation(relation: QueryRelation): Validator {
            super.setRelation(relation)
            LINEVALIDATOR.setRelation(relation)
            return this
        }

        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val lines = shape as Array<Line>
            for (l in lines) {
                val b = LINEVALIDATOR.testComponentQuery(query, l)
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

        protected class LineValidator(encoder: Encoder) : Validator(encoder) {
            override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
                val line = shape as Line
                if (queryRelation == QueryRelation.CONTAINS) {
                    return testWithinQuery(query, LatLonShape.createIndexableFields("dummy", line)) ==
                        Component2D.WithinRelation.CANDIDATE
                }
                return testComponentQuery(query, LatLonShape.createIndexableFields("dummy", line))
            }
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
