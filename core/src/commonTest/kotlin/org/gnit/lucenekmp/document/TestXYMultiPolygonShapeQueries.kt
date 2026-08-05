package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * random cartesian bounding box, line, and polygon query tests for random indexed arrays of
 * cartesian [XYPolygon] types
 */
class TestXYMultiPolygonShapeQueries : BaseXYShapeTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.POLYGON
    }

    override fun nextShape(): Any {
        val n = random().nextInt(4) + 1
        val polygons = arrayOfNulls<XYPolygon>(n)
        for (i in 0..<n) {
            polygons[i] = getShapeType().let { (it as ShapeType).nextShape() } as XYPolygon
        }
        @Suppress("UNCHECKED_CAST")
        return polygons as Array<XYPolygon>
    }

    override fun createIndexableFields(name: String, o: Any): Array<Field> {
        val polygons = o as Array<XYPolygon>
        val allFields = mutableListOf<Field>()
        for (polygon in polygons) {
            allFields.addAll(XYShape.createIndexableFields(name, polygon))
        }
        return allFields.toTypedArray()
    }

    override fun getValidator(): Validator {
        return MultiPolygonValidator(ENCODER)
    }

    class MultiPolygonValidator(encoder: Encoder) : Validator(encoder) {
        var POLYGONVALIDATOR: TestXYPolygonShapeQueries.PolygonValidator =
            TestXYPolygonShapeQueries.PolygonValidator(encoder)

        override fun setRelation(relation: QueryRelation): Validator {
            super.setRelation(relation)
            POLYGONVALIDATOR.setRelation(relation)
            return this
        }

        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val polygons = shape as Array<XYPolygon>
            if (queryRelation == QueryRelation.CONTAINS) {
                return testWithinPolygon(query, polygons)
            }
            for (p in polygons) {
                val b = POLYGONVALIDATOR.testComponentQuery(query, p)
                if (b && queryRelation == QueryRelation.INTERSECTS) {
                    return true
                } else if (!b && queryRelation == QueryRelation.DISJOINT) {
                    return false
                } else if (!b && queryRelation == QueryRelation.WITHIN) {
                    return false
                }
            }
            return queryRelation != QueryRelation.INTERSECTS && queryRelation != QueryRelation.CONTAINS
        }

        private fun testWithinPolygon(query: Component2D, polygons: Array<XYPolygon>): Boolean {
            var answer = Component2D.WithinRelation.DISJOINT
            for (p in polygons) {
                val relation = POLYGONVALIDATOR.testWithinQuery(query, XYShape.createIndexableFields("dummy", p))
                if (relation == Component2D.WithinRelation.NOTWITHIN) {
                    return false
                } else if (relation == Component2D.WithinRelation.CANDIDATE) {
                    answer = relation
                }
            }
            return answer == Component2D.WithinRelation.CANDIDATE
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
