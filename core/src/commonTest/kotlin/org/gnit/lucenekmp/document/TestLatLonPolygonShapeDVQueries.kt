package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Ignore
import kotlin.test.Test

/** Test queries over LatLon Polygon ShapeDocValues */
@Ignore
class TestLatLonPolygonShapeDVQueries : BaseLatLonShapeDocValueTestCase() {

    override fun getShapeType(): Any {
        return ShapeType.POLYGON
    }

    override fun createIndexableFields(field: String, shape: Any): Array<Field> {
        val polygon = shape as Polygon
        throw UnsupportedOperationException("LatLonShape.createDocValueField is not ported yet for polygon=$polygon")
    }

    override fun getValidator(): Validator {
        throw UnsupportedOperationException("TestLatLonPolygonShapeQueries.PolygonValidator is not ported yet")
    }

    /** test random line queries */
    override fun verifyRandomLineQueries(reader: IndexReader, shapes: Array<Any?>) {
        // NOT IMPLEMENTED YET
    }

    /** test random polygon queries */
    override fun verifyRandomPolygonQueries(reader: IndexReader, shapes: Array<Any?>) {
        // NOT IMPLEMENTED YET
    }

    /** test random point queries */
    override fun verifyRandomPointQueries(reader: IndexReader, shapes: Array<Any?>) {
        // NOT IMPLEMENTED YET
    }

    /** test random distance queries */
    override fun verifyRandomDistanceQueries(reader: IndexReader, shapes: Array<Any?>) {
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
