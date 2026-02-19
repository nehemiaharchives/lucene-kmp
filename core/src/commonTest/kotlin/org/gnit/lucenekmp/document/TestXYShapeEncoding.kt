package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import kotlin.test.Test

/** tests XYShape encoding */
class TestXYShapeEncoding : BaseShapeEncodingTestCase() {
    override fun encodeX(x: Double): Int {
        return XYEncodingUtils.encode(x.toFloat())
    }

    override fun encodeY(y: Double): Int {
        return XYEncodingUtils.encode(y.toFloat())
    }

    override fun decodeX(xEncoded: Int): Double {
        return XYEncodingUtils.decode(xEncoded).toDouble()
    }

    override fun decodeY(yEncoded: Int): Double {
        return XYEncodingUtils.decode(yEncoded).toDouble()
    }

    override fun nextX(): Double {
        return ShapeTestUtil.nextFloat(random()).toDouble()
    }

    override fun nextY(): Double {
        return ShapeTestUtil.nextFloat(random()).toDouble()
    }

    override fun nextPolygon(): XYPolygon {
        return ShapeTestUtil.nextPolygon()
    }

    override fun createPolygon2D(polygon: Any): Component2D {
        return XYGeometry.create(polygon as XYPolygon)
    }

    @Test
    fun testRotationChangesOrientation() {
        val ay = -3.4028218437925203E38
        val ax = 3.4028220466166163E38
        val by = 3.4028218437925203E38
        val bx = -3.4028218437925203E38
        val cy = 3.4028230607370965E38
        val cx = -3.4028230607370965E38
        verifyEncoding(ay, ax, by, bx, cy, cx)
    }

    // tests inherited from BaseShapeEncodingTestCase

    @Test
    override fun testPolygonEncodingMinLatMinLon() = super.testPolygonEncodingMinLatMinLon()

    @Test
    override fun testPolygonEncodingMinLatMaxLon() = super.testPolygonEncodingMinLatMaxLon()

    @Test
    override fun testPolygonEncodingMaxLatMaxLon() = super.testPolygonEncodingMaxLatMaxLon()

    @Test
    override fun testPolygonEncodingMaxLatMinLon() = super.testPolygonEncodingMaxLatMinLon()

    @Test
    override fun testPolygonEncodingMinLatMinLonMaxLatMaxLonBelow() =
        super.testPolygonEncodingMinLatMinLonMaxLatMaxLonBelow()

    @Test
    override fun testPolygonEncodingMinLatMinLonMaxLatMaxLonAbove() =
        super.testPolygonEncodingMinLatMinLonMaxLatMaxLonAbove()

    @Test
    override fun testPolygonEncodingMinLatMaxLonMaxLatMinLonBelow() =
        super.testPolygonEncodingMinLatMaxLonMaxLatMinLonBelow()

    @Test
    override fun testPolygonEncodingMinLatMaxLonMaxLatMinLonAbove() =
        super.testPolygonEncodingMinLatMaxLonMaxLatMinLonAbove()

    @Test
    override fun testPolygonEncodingAllSharedAbove() = super.testPolygonEncodingAllSharedAbove()

    @Test
    override fun testPolygonEncodingAllSharedBelow() = super.testPolygonEncodingAllSharedBelow()

    @Test
    override fun testPointEncoding() = super.testPointEncoding()

    @Test
    override fun testLineEncodingSameLat() = super.testLineEncodingSameLat()

    @Test
    override fun testLineEncodingSameLon() = super.testLineEncodingSameLon()

    @Test
    override fun testLineEncoding() = super.testLineEncoding()

    @Test
    override fun testRandomPointEncoding() = super.testRandomPointEncoding()

    @Test
    override fun testRandomLineEncoding() = super.testRandomLineEncoding()

    @Test
    override fun testRandomPolygonEncoding() = super.testRandomPolygonEncoding()

    @Test
    override fun testDegeneratedTriangle() = super.testDegeneratedTriangle()
}
