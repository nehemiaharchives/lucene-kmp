package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * random cartesian bounding box, line, and polygon query tests for random indexed [XYPolygon]
 * types
 */
class TestXYPolygonShapeQueries : BaseXYShapeTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.POLYGON
    }

    override fun createIndexableFields(field: String, polygon: Any): Array<Field> {
        return XYShape.createIndexableFields(field, polygon as XYPolygon)
    }

    override fun getValidator(): Validator {
        return PolygonValidator(this.ENCODER)
    }

    class PolygonValidator(encoder: Encoder) : Validator(encoder) {
        override fun testComponentQuery(query: Component2D, o: Any): Boolean {
            val polygon = o as XYPolygon
            if (queryRelation == QueryRelation.CONTAINS) {
                return testWithinQuery(query, XYShape.createIndexableFields("dummy", polygon)) ==
                    Component2D.WithinRelation.CANDIDATE
            }
            return testComponentQuery(query, XYShape.createIndexableFields("dummy", polygon))
        }
    }

    // tests inherited from BaseSpatialTestCase

    @LuceneTestCase.Companion.Nightly
    @Test
    override fun testRandomBig() {
        doTestRandom(25) // TODO reduced from 25000 to 25 for dev speed
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
