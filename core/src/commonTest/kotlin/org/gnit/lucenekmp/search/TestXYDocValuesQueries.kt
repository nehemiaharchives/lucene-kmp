package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.XYDocValuesField
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.tests.geo.BaseXYPointTestCase
import kotlin.test.Test

class TestXYDocValuesQueries : BaseXYPointTestCase() {
    override fun addPointToDoc(field: String, doc: Document, x: Float, y: Float) {
        doc.add(XYDocValuesField(field, x, y))
    }

    override fun newRectQuery(field: String?, minX: Float, maxX: Float, minY: Float, maxY: Float): Query {
        return XYDocValuesField.newSlowBoxQuery(field, minX, maxX, minY, maxY)
    }

    override fun newDistanceQuery(field: String?, centerX: Float, centerY: Float, radius: Float): Query {
        return XYDocValuesField.newSlowDistanceQuery(field, centerX, centerY, radius)
    }

    override fun newPolygonQuery(field: String?, vararg polygons: XYPolygon): Query {
        return XYDocValuesField.newSlowPolygonQuery(field, *polygons)
    }

    override fun newGeometryQuery(field: String, vararg geometries: XYGeometry): Query {
        return XYDocValuesField.newSlowGeometryQuery(field, *geometries)
    }

    // tests inherited from BaseXYPointTestCase

    @Test
    override fun testIndexExtremeValues() = super.testIndexExtremeValues()

    @Test
    override fun testIndexNaNValues() = super.testIndexNaNValues()

    @Test
    override fun testIndexInfValues() = super.testIndexInfValues()

    @Test
    override fun testBoxBasics() = super.testBoxBasics()

    @Test
    override fun testBoxNull() = super.testBoxNull()

    @Test
    override fun testBoxInvalidCoordinates() = super.testBoxInvalidCoordinates()

    @Test
    override fun testDistanceBasics() = super.testDistanceBasics()

    @Test
    override fun testDistanceNull() = super.testDistanceNull()

    @Test
    override fun testDistanceIllegal() = super.testDistanceIllegal()

    @Test
    override fun testDistanceNegative() = super.testDistanceNegative()

    @Test
    override fun testDistanceNaN() = super.testDistanceNaN()

    @Test
    override fun testDistanceInf() = super.testDistanceInf()

    @Test
    override fun testPolygonBasics() = super.testPolygonBasics()

    @Test
    override fun testPolygonHole() = super.testPolygonHole()

    @Test
    override fun testPolygonHoleExcludes() = super.testPolygonHoleExcludes()

    @Test
    override fun testMultiPolygonBasics() = super.testMultiPolygonBasics()

    @Test
    override fun testPolygonNullField() = super.testPolygonNullField()

    @Test
    override fun testSamePointManyTimes() = super.testSamePointManyTimes()

    @Test
    override fun testLowCardinality() = super.testLowCardinality()

    @Test
    override fun testAllYEqual() = super.testAllYEqual()

    @Test
    override fun testAllXEqual() = super.testAllXEqual()

    @Test
    override fun testMultiValued() = super.testMultiValued()

    @Test
    override fun testRandomTiny() = super.testRandomTiny()

    @Test
    override fun testRandomMedium() = super.testRandomMedium()

    @Test
    override fun testRandomBig() = super.testRandomBig()

    @Test
    override fun testRectBoundariesAreInclusive() = super.testRectBoundariesAreInclusive()

    @Test
    override fun testRandomDistance() = super.testRandomDistance()

    @Test
    override fun testRandomDistanceHuge() = super.testRandomDistanceHuge()

    @Test
    override fun testEquals() = super.testEquals()

    @Test
    override fun testSmallSetRect() = super.testSmallSetRect()

    @Test
    override fun testSmallSetRect2() = super.testSmallSetRect2()

    @Test
    override fun testSmallSetMultiValued() = super.testSmallSetMultiValued()

    @Test
    override fun testSmallSetWholeSpace() = super.testSmallSetWholeSpace()

    @Test
    override fun testSmallSetPoly() = super.testSmallSetPoly()

    @Test
    override fun testSmallSetPolyWholeSpace() = super.testSmallSetPolyWholeSpace()

    @Test
    override fun testSmallSetDistance() = super.testSmallSetDistance()

    @Test
    override fun testSmallSetTinyDistance() = super.testSmallSetTinyDistance()

    @Test
    override fun testSmallSetHugeDistance() = super.testSmallSetHugeDistance()
}
