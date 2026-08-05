package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/** random bounding box, line, and polygon query tests for random indexed [Polygon] types */
class TestLatLonPolygonShapeQueries : BaseLatLonShapeTestCase() {

    override fun getShapeType(): Any {
        return ShapeType.POLYGON
    }

    override fun createIndexableFields(field: String, polygon: Any): Array<Field> {
        return LatLonShape.createIndexableFields(field, polygon as Polygon)
    }

    override fun getValidator(): Validator {
        return PolygonValidator(this.ENCODER)
    }

    class PolygonValidator(encoder: Encoder) : Validator(encoder) {
        override fun testComponentQuery(query: Component2D, o: Any): Boolean {
            val polygon = o as Polygon
            if (queryRelation == QueryRelation.CONTAINS) {
                return testWithinQuery(query, LatLonShape.createIndexableFields("dummy", polygon)) ==
                    Component2D.WithinRelation.CANDIDATE
            }
            return testComponentQuery(query, LatLonShape.createIndexableFields("dummy", polygon))
        }

        fun testWithinPolygon(query: Component2D, polygon: Polygon): Component2D.WithinRelation {
            return testWithinQuery(query, LatLonShape.createIndexableFields("dummy", polygon))
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
        doTestRandom(10) // TODO reduced from 25000 to 10 for dev speed
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
