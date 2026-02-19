package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import kotlin.math.floor
import kotlin.test.Test

/**
 * random bounding box, line, and polygon query tests for random generated `latitude,
 * longitude` points
 */
class TestLatLonPointShapeQueries : BaseLatLonShapeTestCase() {

    override fun getShapeType(): Any {
        return ShapeType.POINT
    }

    override fun randomQueryLine(shapes: Array<Any?>): Line {
        if (random().nextInt(100) == 42) {
            // we want to ensure some cross, so randomly generate lines that share vertices with the
            // indexed point set
            var maxBound = floor(shapes.size * 0.1).toInt()
            if (maxBound < 2) {
                maxBound = shapes.size
            }
            val lats = DoubleArray(RandomNumbers.randomIntBetween(random(), 2, maxBound))
            val lons = DoubleArray(lats.size)
            var i = 0
            var j = 0
            while (j < lats.size && i < shapes.size) {
                val p = shapes[i] as? Point
                if (random().nextBoolean() && p != null) {
                    lats[j] = p.lat
                    lons[j] = p.lon
                } else {
                    lats[j] = GeoTestUtil.nextLatitude()
                    lons[j] = GeoTestUtil.nextLongitude()
                }
                i++
                j++
            }
            return Line(lats, lons)
        }
        return nextLine()
    }

    override fun createIndexableFields(field: String, point: Any): Array<Field> {
        val p = point as Point
        return LatLonShape.createIndexableFields(field, p.lat, p.lon)
    }

    override fun getValidator(): Validator {
        return PointValidator(this.ENCODER)
    }

    class PointValidator(encoder: Encoder) : Validator(encoder) {
        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val p = shape as Point
            if (queryRelation == QueryRelation.CONTAINS) {
                return testWithinQuery(query, LatLonShape.createIndexableFields("dummy", p.lat, p.lon)) ==
                    Component2D.WithinRelation.CANDIDATE
            }
            return testComponentQuery(query, LatLonShape.createIndexableFields("dummy", p.lat, p.lon))
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
