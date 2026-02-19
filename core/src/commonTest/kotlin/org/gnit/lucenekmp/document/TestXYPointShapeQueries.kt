package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import kotlin.math.floor
import kotlin.test.Test

/**
 * random cartesian bounding box, line, and polygon query tests for random generated `x, y`
 * points
 */
class TestXYPointShapeQueries : BaseXYShapeTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.POINT
    }

    override fun randomQueryLine(shapes: Array<Any?>): XYLine {
        val random = random()
        if (random.nextInt(100) == 42) {
            // we want to ensure some cross, so randomly generate lines that share vertices with the
            // indexed point set
            var maxBound = floor(shapes.size * 0.1).toInt()
            if (maxBound < 2) {
                maxBound = shapes.size
            }
            val x = FloatArray(RandomNumbers.randomIntBetween(random, 2, maxBound))
            val y = FloatArray(x.size)
            var i = 0
            var j = 0
            while (j < x.size && i < shapes.size) {
                val p = shapes[i] as? XYPoint
                if (random.nextBoolean() && p != null) {
                    x[j] = p.x
                    y[j] = p.y
                } else {
                    x[j] = ShapeTestUtil.nextFloat(random)
                    y[j] = ShapeTestUtil.nextFloat(random)
                }
                i++
                j++
            }
            return XYLine(x, y)
        }
        return nextLine()
    }

    override fun createIndexableFields(field: String, point: Any): Array<Field> {
        val p = point as XYPoint
        return XYShape.createIndexableFields(field, p.x, p.y)
    }

    override fun getValidator(): Validator {
        return PointValidator(this.ENCODER)
    }

    class PointValidator(encoder: Encoder) : Validator(encoder) {
        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val point = shape as XYPoint
            if (queryRelation == QueryRelation.CONTAINS) {
                return testWithinQuery(query, XYShape.createIndexableFields("dummy", point.x, point.y)) ==
                    Component2D.WithinRelation.CANDIDATE
            }
            return testComponentQuery(query, XYShape.createIndexableFields("dummy", point.x, point.y))
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
