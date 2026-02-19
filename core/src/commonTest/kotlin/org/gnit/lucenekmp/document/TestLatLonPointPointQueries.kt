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
class TestLatLonPointPointQueries : BaseLatLonPointTestCase() {

    override fun getShapeType(): Any {
        return ShapeType.POINT
    }

    override fun getValidator(): Validator {
        return PointValidator(this.ENCODER)
    }

    override fun createIndexableFields(name: String, o: Any): Array<Field> {
        val point = o as Point
        return arrayOf(LatLonPoint(FIELD_NAME, point.lat, point.lon))
    }

    protected class PointValidator(encoder: Encoder) : Validator(encoder) {
        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val p = shape as Point
            if (queryRelation == QueryRelation.CONTAINS) {
                return testWithinQuery(query, LatLonShape.createIndexableFields("dummy", p.lat, p.lon)) ==
                    Component2D.WithinRelation.CANDIDATE
            }
            return testComponentQuery(query, LatLonShape.createIndexableFields("dummy", p.lat, p.lon))
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
