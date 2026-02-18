package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Ignore
import kotlin.test.Test

/** Tests queries over geographic point shape doc values */
@Ignore
class TestLatLonPointShapeDVQueries : BaseLatLonShapeDocValueTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.POINT
    }

    override fun createIndexableFields(field: String, shape: Any): Array<Field> {
        val point = shape as Point
        throw UnsupportedOperationException("LatLonShape.createDocValueField is not ported yet for point=$point")
    }

    override fun getValidator(): Validator {
        throw UnsupportedOperationException("TestLatLonPointShapeQueries.PointValidator is not ported yet")
    }

    /** test random line queries */
    override fun verifyRandomLineQueries(reader: IndexReader, vararg shapes: Any?) {
        // NOT IMPLEMENTED YET
    }

    /** test random polygon queries */
    override fun verifyRandomPolygonQueries(reader: IndexReader, vararg shapes: Any?) {
        // NOT IMPLEMENTED YET
    }

    /** test random point queries */
    override fun verifyRandomPointQueries(reader: IndexReader, vararg shapes: Any?) {
        // NOT IMPLEMENTED YET
    }

    /** test random distance queries */
    override fun verifyRandomDistanceQueries(reader: IndexReader, vararg shapes: Any?) {
        // NOT IMPLEMENTED YET
    }

    // tests inherited from BaseSpatialTestCase

    @LuceneTestCase.Companion.Nightly
    @Test
    override fun testRandomBig() {
        doTestRandom(10000)
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
