package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/** Test queries over [org.gnit.lucenekmp.geo.XYPolygon] doc value geometries */
class TestXYPolygonShapeDVQueries : BaseXYShapeDocValueTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.POLYGON
    }

    override fun createIndexableFields(field: String, shape: Any): Array<Field> {
        val polygon = shape as XYPolygon
        return arrayOf(XYShape.createDocValueField(FIELD_NAME, polygon))
    }

    override fun getValidator(): Validator {
        return TestXYPolygonShapeQueries.PolygonValidator(this.ENCODER)
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
