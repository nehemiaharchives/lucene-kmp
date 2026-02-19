package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import kotlin.test.Test

/** Test case for LatLonShape encoding */
class TestLatLonShapeEncoding : BaseShapeEncodingTestCase() {
    override fun encodeX(x: Double): Int {
        return GeoEncodingUtils.encodeLongitude(x)
    }

    override fun encodeY(y: Double): Int {
        return GeoEncodingUtils.encodeLatitude(y)
    }

    override fun decodeX(xEncoded: Int): Double {
        return GeoEncodingUtils.decodeLongitude(xEncoded)
    }

    override fun decodeY(yEncoded: Int): Double {
        return GeoEncodingUtils.decodeLatitude(yEncoded)
    }

    override fun nextX(): Double {
        return GeoTestUtil.nextLongitude()
    }

    override fun nextY(): Double {
        return GeoTestUtil.nextLatitude()
    }

    override fun nextPolygon(): Polygon {
        return GeoTestUtil.nextPolygon()
    }

    override fun createPolygon2D(polygon: Any): Component2D {
        return LatLonGeometry.create(polygon as Polygon)
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
