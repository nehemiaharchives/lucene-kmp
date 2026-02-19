package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * base shape encoding class for testing encoding of tessellated [org.gnit.lucenekmp.document.XYShape] and [LatLonShape]
 */
abstract class BaseShapeEncodingTestCase : LuceneTestCase() {

    protected abstract fun encodeX(x: Double): Int

    protected abstract fun decodeX(x: Int): Double

    protected abstract fun encodeY(y: Double): Int

    protected abstract fun decodeY(y: Int): Double

    protected abstract fun nextX(): Double

    protected abstract fun nextY(): Double

    protected abstract fun nextPolygon(): Any

    protected abstract fun createPolygon2D(polygon: Any): Component2D

    // One shared point with MBR -> MinY, MinX
    open fun testPolygonEncodingMinLatMinLon() {
        val ay = 0.0
        val ax = 0.0
        val by = 1.0
        val blon = 2.0
        val cy = 2.0
        val cx = 1.0
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(by)
        val bxEnc = encodeX(blon)
        val cyEnc = encodeY(cy)
        val cxEnc = encodeX(cx)
        verifyEncodingPermutations(ayEnc, axEnc, byEnc, bxEnc, cyEnc, cxEnc)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // One shared point with MBR -> MinLat, MaxLon
    open fun testPolygonEncodingMinLatMaxLon() {
        val ay = 1.0
        val ax = 0.0
        val by = 0.0
        val blon = 2.0
        val cy = 2.0
        val cx = 1.0
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(by)
        val bxEnc = encodeX(blon)
        val cyEnc = encodeY(cy)
        val cxEnc = encodeX(cx)
        verifyEncodingPermutations(ayEnc, axEnc, byEnc, bxEnc, cyEnc, cxEnc)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // One shared point with MBR -> MaxLat, MaxLon
    open fun testPolygonEncodingMaxLatMaxLon() {
        val ay = 1.0
        val ax = 0.0
        val by = 2.0
        val blon = 2.0
        val cy = 0.0
        val cx = 1.0
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(cy)
        val bxEnc = encodeX(cx)
        val cyEnc = encodeY(by)
        val cxEnc = encodeX(blon)
        verifyEncodingPermutations(ayEnc, axEnc, byEnc, bxEnc, cyEnc, cxEnc)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // One shared point with MBR -> MaxLat, MinLon
    open fun testPolygonEncodingMaxLatMinLon() {
        val ay = 2.0
        val ax = 0.0
        val by = 1.0
        val blon = 2.0
        val cy = 0.0
        val cx = 1.0
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(cy)
        val bxEnc = encodeX(cx)
        val cyEnc = encodeY(by)
        val cxEnc = encodeX(blon)
        verifyEncodingPermutations(ayEnc, axEnc, byEnc, bxEnc, cyEnc, cxEnc)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // Two shared point with MBR -> [MinLat, MinLon], [MaxLat, MaxLon], third point below
    open fun testPolygonEncodingMinLatMinLonMaxLatMaxLonBelow() {
        val ay = 0.0
        val ax = 0.0
        val by = 0.25
        val blon = 0.75
        val cy = 2.0
        val cx = 2.0
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(by)
        val bxEnc = encodeX(blon)
        val cyEnc = encodeY(cy)
        val cxEnc = encodeX(cx)
        verifyEncodingPermutations(ayEnc, axEnc, byEnc, bxEnc, cyEnc, cxEnc)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // Two shared point with MBR -> [MinLat, MinLon], [MaxLat, MaxLon], third point above
    open fun testPolygonEncodingMinLatMinLonMaxLatMaxLonAbove() {
        val ay = 0.0
        val ax = 0.0
        val by = 2.0
        val bx = 2.0
        val cy = 1.75
        val cx = 1.25
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(by)
        val bxEnc = encodeX(bx)
        val cyEnc = encodeY(cy)
        val cxEnc = encodeX(cx)
        verifyEncodingPermutations(ayEnc, axEnc, byEnc, bxEnc, cyEnc, cxEnc)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // Two shared point with MBR -> [MinLat, MaxLon], [MaxLat, MinLon], third point below
    open fun testPolygonEncodingMinLatMaxLonMaxLatMinLonBelow() {
        val ay = 8.0
        val ax = 6.0
        val by = 6.25
        val bx = 6.75
        val cy = 6.0
        val cx = 8.0
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(by)
        val bxEnc = encodeX(bx)
        val cyEnc = encodeY(cy)
        val cxEnc = encodeX(cx)
        verifyEncodingPermutations(ayEnc, axEnc, byEnc, bxEnc, cyEnc, cxEnc)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // Two shared point with MBR -> [MinLat, MaxLon], [MaxLat, MinLon], third point above
    open fun testPolygonEncodingMinLatMaxLonMaxLatMinLonAbove() {
        val ay = 2.0
        val ax = 0.0
        val by = 0.0
        val bx = 2.0
        val cy = 1.75
        val cx = 1.25
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(by)
        val bxEnc = encodeX(bx)
        val cyEnc = encodeY(cy)
        val cxEnc = encodeX(cx)
        verifyEncodingPermutations(ayEnc, axEnc, byEnc, bxEnc, cyEnc, cxEnc)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // all points shared with MBR
    open fun testPolygonEncodingAllSharedAbove() {
        val ay = 0.0
        val ax = 0.0
        val by = 0.0
        val bx = 2.0
        val cy = 2.0
        val cx = 2.0
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(by)
        val bxEnc = encodeX(bx)
        val cyEnc = encodeY(cy)
        val cxEnc = encodeX(cx)
        verifyEncodingPermutations(ayEnc, axEnc, byEnc, bxEnc, cyEnc, cxEnc)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // all points shared with MBR
    open fun testPolygonEncodingAllSharedBelow() {
        val ay = 2.0
        val ax = 0.0
        val by = 0.0
        val bx = 0.0
        val cy = 2.0
        val cx = 2.0
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(by)
        val bxEnc = encodeX(bx)
        val cyEnc = encodeY(cy)
        val cxEnc = encodeX(cx)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, cyEnc)
        assertEquals(encoded.cX, cxEnc)
    }

    // [a,b,c] == [c,a,b] == [b,c,a] == [c,b,a] == [b,a,c] == [a,c,b]
    fun verifyEncodingPermutations(ayEnc: Int, axEnc: Int, byEnc: Int, bxEnc: Int, cyEnc: Int, cxEnc: Int) {
        // this is only valid when points are not co-planar
        assertTrue(
            GeoUtils.orient(
                ayEnc.toDouble(),
                axEnc.toDouble(),
                byEnc.toDouble(),
                bxEnc.toDouble(),
                cyEnc.toDouble(),
                cxEnc.toDouble()
            ) != 0
        )
        val b = ByteArray(7 * ShapeField.BYTES)
        // [a,b,c]
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, false)
        val encodedABC = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encodedABC)
        // [c,a,b]
        ShapeField.encodeTriangle(b, cyEnc, cxEnc, false, ayEnc, axEnc, true, byEnc, bxEnc, true)
        val encodedCAB = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encodedCAB)
        assertEquals(encodedABC, encodedCAB)
        // [b,c,a]
        ShapeField.encodeTriangle(b, byEnc, bxEnc, true, cyEnc, cxEnc, false, ayEnc, axEnc, true)
        val encodedBCA = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encodedBCA)
        assertEquals(encodedABC, encodedBCA)
        // [c,b,a]
        ShapeField.encodeTriangle(b, cyEnc, cxEnc, true, byEnc, bxEnc, true, ayEnc, axEnc, false)
        val encodedCBA = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encodedCBA)
        assertEquals(encodedABC, encodedCBA)
        // [b,a,c]
        ShapeField.encodeTriangle(b, byEnc, bxEnc, true, ayEnc, axEnc, false, cyEnc, cxEnc, true)
        val encodedBAC = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encodedBAC)
        assertEquals(encodedABC, encodedBAC)
        // [a,c,b]
        ShapeField.encodeTriangle(b, ayEnc, axEnc, false, cyEnc, cxEnc, true, byEnc, bxEnc, true)
        val encodedACB = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encodedACB)
        assertEquals(encodedABC, encodedACB)
    }

    open fun testPointEncoding() {
        val lat = 45.0
        val lon = 45.0
        val latEnc = encodeY(lat)
        val lonEnc = encodeX(lon)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, latEnc, lonEnc, true, latEnc, lonEnc, true, latEnc, lonEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, latEnc)
        assertEquals(encoded.aX, lonEnc)
        assertEquals(encoded.bY, latEnc)
        assertEquals(encoded.bX, lonEnc)
        assertEquals(encoded.cY, latEnc)
        assertEquals(encoded.cX, lonEnc)
    }

    open fun testLineEncodingSameLat() {
        val lat = 2.0
        val ax = 0.0
        val bx = 2.0
        val latEnc = encodeY(lat)
        val axEnc = encodeX(ax)
        val bxEnc = encodeX(bx)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, latEnc, axEnc, true, latEnc, bxEnc, true, latEnc, axEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, latEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, latEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, latEnc)
        assertEquals(encoded.cX, axEnc)
        ShapeField.encodeTriangle(b, latEnc, axEnc, true, latEnc, axEnc, true, latEnc, bxEnc, true)
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, latEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, latEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, latEnc)
        assertEquals(encoded.cX, axEnc)
        ShapeField.encodeTriangle(b, latEnc, bxEnc, true, latEnc, axEnc, true, latEnc, axEnc, true)
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, latEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, latEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, latEnc)
        assertEquals(encoded.cX, axEnc)
    }

    open fun testLineEncodingSameLon() {
        val ay = 0.0
        val by = 2.0
        val lon = 2.0
        val ayEnc = encodeY(ay)
        val byEnc = encodeY(by)
        val lonEnc = encodeX(lon)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, lonEnc, true, byEnc, lonEnc, true, ayEnc, lonEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, lonEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, lonEnc)
        assertEquals(encoded.cY, ayEnc)
        assertEquals(encoded.cX, lonEnc)
        ShapeField.encodeTriangle(b, ayEnc, lonEnc, true, ayEnc, lonEnc, true, byEnc, lonEnc, true)
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, lonEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, lonEnc)
        assertEquals(encoded.cY, ayEnc)
        assertEquals(encoded.cX, lonEnc)
        ShapeField.encodeTriangle(b, byEnc, lonEnc, true, ayEnc, lonEnc, true, ayEnc, lonEnc, true)
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, lonEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, lonEnc)
        assertEquals(encoded.cY, ayEnc)
        assertEquals(encoded.cX, lonEnc)
    }

    open fun testLineEncoding() {
        val ay = 0.0
        val by = 2.0
        val ax = 0.0
        val bx = 2.0
        val ayEnc = encodeY(ay)
        val byEnc = encodeY(by)
        val axEnc = encodeX(ax)
        val bxEnc = encodeX(bx)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, ayEnc, axEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, ayEnc)
        assertEquals(encoded.cX, axEnc)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, ayEnc, axEnc, true, byEnc, bxEnc, true)
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, ayEnc)
        assertEquals(encoded.cX, axEnc)
        ShapeField.encodeTriangle(b, byEnc, bxEnc, true, ayEnc, axEnc, true, ayEnc, axEnc, true)
        ShapeField.decodeTriangle(b, encoded)
        assertEquals(encoded.aY, ayEnc)
        assertEquals(encoded.aX, axEnc)
        assertEquals(encoded.bY, byEnc)
        assertEquals(encoded.bX, bxEnc)
        assertEquals(encoded.cY, ayEnc)
        assertEquals(encoded.cX, axEnc)
    }

    open fun testRandomPointEncoding() {
        val ay = nextY()
        val ax = nextX()
        verifyEncoding(ay, ax, ay, ax, ay, ax)
    }

    open fun testRandomLineEncoding() {
        val ay = nextY()
        val ax = nextX()
        val by = nextY()
        val bx = nextX()
        verifyEncoding(ay, ax, by, bx, ay, ax)
    }

    open fun testRandomPolygonEncoding() {
        val ay = nextY()
        val ax = nextX()
        val by = nextY()
        val bx = nextX()
        val cy = nextY()
        val cx = nextX()
        verifyEncoding(ay, ax, by, bx, cy, cx)
    }

    protected fun verifyEncoding(ay: Double, ax: Double, by: Double, bx: Double, cy: Double, cx: Double) {
        val original = intArrayOf(encodeX(ax), encodeY(ay), encodeX(bx), encodeY(by), encodeX(cx), encodeY(cy))
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(
            b,
            original[1],
            original[0],
            true,
            original[3],
            original[2],
            true,
            original[5],
            original[4],
            true
        )
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        val encodedQuantize =
            doubleArrayOf(
                decodeX(encoded.aX),
                decodeY(encoded.aY),
                decodeX(encoded.bX),
                decodeY(encoded.bY),
                decodeX(encoded.cX),
                decodeY(encoded.cY)
            )
        val originalQuantize = orderTriangle(original[0], original[1], original[2], original[3], original[4], original[5])

        for (i in 0..<100) {
            val polygon2D = createPolygon2D(nextPolygon())
            var originalIntersects = false
            var encodedIntersects = false
            var originalContains = false
            var encodedContains = false
            when (encoded.type) {
                ShapeField.DecodedTriangle.TYPE.POINT -> {
                    originalIntersects = polygon2D.contains(originalQuantize[0], originalQuantize[1])
                    encodedIntersects = polygon2D.contains(encodedQuantize[0], encodedQuantize[1])
                    originalContains = polygon2D.contains(originalQuantize[0], originalQuantize[1])
                    encodedContains = polygon2D.contains(encodedQuantize[0], encodedQuantize[1])
                }

                ShapeField.DecodedTriangle.TYPE.LINE -> {
                    originalIntersects =
                        polygon2D.intersectsLine(
                            originalQuantize[0],
                            originalQuantize[1],
                            originalQuantize[2],
                            originalQuantize[3]
                        )
                    encodedIntersects =
                        polygon2D.intersectsLine(
                            encodedQuantize[0],
                            encodedQuantize[1],
                            encodedQuantize[2],
                            encodedQuantize[3]
                        )
                    originalContains =
                        polygon2D.containsLine(
                            originalQuantize[0],
                            originalQuantize[1],
                            originalQuantize[2],
                            originalQuantize[3]
                        )
                    encodedContains =
                        polygon2D.containsLine(
                            encodedQuantize[0],
                            encodedQuantize[1],
                            encodedQuantize[2],
                            encodedQuantize[3]
                        )
                }

                ShapeField.DecodedTriangle.TYPE.TRIANGLE -> {
                    originalIntersects =
                        polygon2D.intersectsTriangle(
                            originalQuantize[0],
                            originalQuantize[1],
                            originalQuantize[2],
                            originalQuantize[3],
                            originalQuantize[4],
                            originalQuantize[5]
                        )
                    encodedIntersects =
                        polygon2D.intersectsTriangle(
                            originalQuantize[0],
                            originalQuantize[1],
                            originalQuantize[2],
                            originalQuantize[3],
                            originalQuantize[4],
                            originalQuantize[5]
                        )
                    originalContains =
                        polygon2D.containsTriangle(
                            originalQuantize[0],
                            originalQuantize[1],
                            originalQuantize[2],
                            originalQuantize[3],
                            originalQuantize[4],
                            originalQuantize[5]
                        )
                    encodedContains =
                        polygon2D.containsTriangle(
                            originalQuantize[0],
                            originalQuantize[1],
                            originalQuantize[2],
                            originalQuantize[3],
                            originalQuantize[4],
                            originalQuantize[5]
                        )
                }

                else -> {}
            }
            assertTrue(originalIntersects == encodedIntersects)
            assertTrue(originalContains == encodedContains)
        }
    }

    private fun orderTriangle(aX: Int, aY: Int, bX: Int, bY: Int, cX: Int, cY: Int): DoubleArray {
        val orientation =
            GeoUtils.orient(
                aX.toDouble(),
                aY.toDouble(),
                bX.toDouble(),
                bY.toDouble(),
                cX.toDouble(),
                cY.toDouble()
            )

        if (orientation == -1) {
            return doubleArrayOf(decodeX(cX), decodeY(cY), decodeX(bX), decodeY(bY), decodeX(aX), decodeY(aY))
        } else if (aX == bX && aY == bY) {
            if (aX != cX || aY != cY) {
                if (aX < cX) {
                    return doubleArrayOf(decodeX(aX), decodeY(aY), decodeX(cX), decodeY(cY), decodeX(aX), decodeY(aY))
                }
                return doubleArrayOf(decodeX(cX), decodeY(cY), decodeX(aX), decodeY(aY), decodeX(cX), decodeY(cY))
            }
        } else if ((aX == cX && aY == cY) || (bX == cX && bY == cY)) {
            if (aX < bX) {
                return doubleArrayOf(decodeX(aX), decodeY(aY), decodeX(bX), decodeY(bY), decodeX(aX), decodeY(aY))
            }
            return doubleArrayOf(decodeX(bX), decodeY(bY), decodeX(aX), decodeY(aY), decodeX(bX), decodeY(bY))
        }
        return doubleArrayOf(decodeX(aX), decodeY(aY), decodeX(bX), decodeY(bY), decodeX(cX), decodeY(cY))
    }

    open fun testDegeneratedTriangle() {
        val ay = 1e-26
        val ax = 0.0
        val by = -1.0
        val bx = 0.0
        val cy = 1.0
        val cx = 0.0
        val ayEnc = encodeY(ay)
        val axEnc = encodeX(ax)
        val byEnc = encodeY(by)
        val bxEnc = encodeX(bx)
        val cyEnc = encodeY(cy)
        val cxEnc = encodeX(cx)
        val b = ByteArray(7 * ShapeField.BYTES)
        ShapeField.encodeTriangle(b, ayEnc, axEnc, true, byEnc, bxEnc, true, cyEnc, cxEnc, true)
        val encoded = ShapeField.DecodedTriangle()
        ShapeField.decodeTriangle(b, encoded)
        assertTrue(encoded.aY == byEnc)
        assertTrue(encoded.aX == bxEnc)
        assertTrue(encoded.bY == cyEnc)
        assertTrue(encoded.bX == cxEnc)
        assertTrue(encoded.cY == ayEnc)
        assertTrue(encoded.cX == axEnc)
    }
}
