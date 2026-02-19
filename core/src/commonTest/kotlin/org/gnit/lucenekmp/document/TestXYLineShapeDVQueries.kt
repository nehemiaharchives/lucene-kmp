package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/** Tests queries over cartesian line shape doc values */
class TestXYLineShapeDVQueries : BaseXYShapeDocValueTestCase() {
    override fun getShapeType(): Any {
        return ShapeType.LINE
    }

    override fun createIndexableFields(field: String, shape: Any): Array<Field> {
        val line = shape as XYLine
        return arrayOf(XYShape.createDocValueField(FIELD_NAME, line))
    }

    override fun getValidator(): Validator {
        return TestXYLineShapeQueries.LineValidator(this.ENCODER)
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
