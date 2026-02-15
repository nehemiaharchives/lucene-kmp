package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.XYCircle
import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.StrictMath
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.tests.util.BiasedNumbers
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** generates random cartesian geometry; heavy reuse of [GeoTestUtil]  */
object ShapeTestUtil {
    /** returns next pseudorandom polygon  */
    fun nextPolygon(): XYPolygon {
        val random: Random = random()
        if (random.nextBoolean()) {
            return surpriseMePolygon(random)
        } else if (LuceneTestCase.TEST_NIGHTLY && random.nextInt(10) == 1) {
            // this poly is slow to create ... only do it 10% of the time:
            while (true) {
                val gons: Int = TestUtil.nextInt(random, 4, 500)
                // So the poly can cover at most 50% of the earth's surface:
                val radius: Double = random.nextDouble() * 0.5 * Float.MAX_VALUE + 1.0
                try {
                    return createRegularPolygon(nextFloat(random).toDouble(), nextFloat(random).toDouble(), radius, gons)
                } catch (iae: IllegalArgumentException) {
                    // something went wrong, try again
                }
            }
        }

        val box: XYRectangle = nextBox(random)
        if (random.nextBoolean()) {
            // box
            return boxPolygon(box)
        } else {
            // triangle
            return trianglePolygon(box)
        }
    }

    fun nextXYPoint(): XYPoint {
        val random: Random = random()
        val x = nextFloat(random)
        val y = nextFloat(random)
        return XYPoint(x, y)
    }

    fun nextLine(): XYLine {
        val poly: XYPolygon = nextPolygon()
        val x = FloatArray(poly.numPoints() - 1)
        val y = FloatArray(x.size)
        for (i in x.indices) {
            x[i] = poly.getPolyX(i)
            y[i] = poly.getPolyY(i)
        }
        return XYLine(x, y)
    }

    fun nextCircle(): XYCircle {
        val random: Random = random()
        val x = nextFloat(random)
        val y = nextFloat(random)
        var radius = 0f
        while (radius == 0f) {
            radius = random().nextFloat() * Float.MAX_VALUE / 2
        }
        assert(radius != 0f)
        return XYCircle(x, y, radius)
    }

    private fun trianglePolygon(box: XYRectangle): XYPolygon {
        val polyX = FloatArray(4)
        val polyY = FloatArray(4)
        polyX[0] = box.minX
        polyY[0] = box.minY
        polyX[1] = box.maxX
        polyY[1] = box.minY
        polyX[2] = box.maxX
        polyY[2] = box.maxY
        polyX[3] = box.minX
        polyY[3] = box.minY
        return XYPolygon(polyX, polyY)
    }

    fun nextBox(random: Random): XYRectangle {
        // prevent lines instead of boxes
        var x0 = nextFloat(random)
        var x1 = nextFloat(random)
        while (x0 == x1) {
            x1 = nextFloat(random)
        }
        // prevent lines instead of boxes
        var y0 = nextFloat(random)
        var y1 = nextFloat(random)
        while (y0 == y1) {
            y1 = nextFloat(random)
        }

        if (x1 < x0) {
            val x = x0
            x0 = x1
            x1 = x
        }

        if (y1 < y0) {
            val y = y0
            y0 = y1
            y1 = y
        }

        return XYRectangle(x0, x1, y0, y1)
    }

    private fun boxPolygon(box: XYRectangle): XYPolygon {
        val polyX = FloatArray(5)
        val polyY = FloatArray(5)
        polyX[0] = box.minX
        polyY[0] = box.minY
        polyX[1] = box.maxX
        polyY[1] = box.minY
        polyX[2] = box.maxX
        polyY[2] = box.maxY
        polyX[3] = box.minX
        polyY[3] = box.maxY
        polyX[4] = box.minX
        polyY[4] = box.minY
        return XYPolygon(polyX, polyY)
    }

    private fun surpriseMePolygon(random: Random): XYPolygon {
        while (true) {
            val centerX = nextFloat(random)
            val centerY = nextFloat(random)
            val radius: Double = 0.1 + 20 * random.nextDouble()
            val radiusDelta: Double = random.nextDouble()

            val xList: ArrayList<Float> = ArrayList<Float>()
            val yList: ArrayList<Float> = ArrayList<Float>()
            var angle = 0.0
            while (true) {
                angle += random.nextDouble() * 40.0
                if (angle > 360) {
                    break
                }
                var len: Double = radius * (1.0 - radiusDelta + radiusDelta * random.nextDouble())
                val maxX: Float =
                    StrictMath.min(
                        StrictMath.abs(Float.MAX_VALUE - centerX),
                        StrictMath.abs(-Float.MAX_VALUE - centerX)
                    )
                val maxY: Float =
                    StrictMath.min(
                        StrictMath.abs(Float.MAX_VALUE - centerY),
                        StrictMath.abs(-Float.MAX_VALUE - centerY)
                    )

                len = StrictMath.min(len, StrictMath.min(maxX, maxY).toDouble())

                val x = (centerX + len * cos(Math.toRadians(angle))).toFloat()
                val y = (centerY + len * sin(Math.toRadians(angle))).toFloat()

                xList.add(x)
                yList.add(y)
            }

            // close it
            xList.add(xList.get(0))
            yList.add(yList.get(0))

            val xArray = FloatArray(xList.size)
            val yArray = FloatArray(yList.size)
            for (i in xList.indices) {
                xArray[i] = xList.get(i)
                yArray[i] = yList.get(i)
            }
            return XYPolygon(xArray, yArray)
        }
    }

    /**
     * Makes an n-gon, centered at the provided x/y, and each vertex approximately distanceMeters away
     * from the center.
     *
     *
     * Do not invoke me across the dateline or a pole!!
     */
    fun createRegularPolygon(
        centerX: Double, centerY: Double, radius: Double, gons: Int
    ): XYPolygon {
        var radius = radius
        val maxX: Double =
            StrictMath.min(
                StrictMath.abs(Float.MAX_VALUE - centerX), StrictMath.abs(-Float.MAX_VALUE - centerX)
            )
        val maxY: Double =
            StrictMath.min(
                StrictMath.abs(Float.MAX_VALUE - centerY), StrictMath.abs(-Float.MAX_VALUE - centerY)
            )

        radius = StrictMath.min(radius, StrictMath.min(maxX, maxY))

        val result = arrayOfNulls<FloatArray>(2)
        result[0] = FloatArray(gons + 1)
        result[1] = FloatArray(gons + 1)
        // System.out.println("make gon=" + gons);
        for (i in 0..<gons) {
            val angle = 360.0 - i * (360.0 / gons)
            // System.out.println("  angle " + angle);
            val x = cos(StrictMath.toRadians(angle))
            val y = sin(StrictMath.toRadians(angle))
            result[0]!![i] = (centerY + y * radius).toFloat()
            result[1]!![i] = (centerX + x * radius).toFloat()
        }

        // close poly
        result[0]!![gons] = result[0]!![0]
        result[1]!![gons] = result[1]!![0]

        return XYPolygon(result[0]!!, result[1]!!)
    }

    fun nextFloat(random: Random): Float {
        return BiasedNumbers.randomFloatBetween(random, -Float.MAX_VALUE, Float.MAX_VALUE)
    }

    /** Keep it simple, we don't need to take arbitrary Random for geo tests  */
    private fun random(): Random {
        return Random
    }

    /** Simple slow point in polygon check (for testing)  */ // direct port of PNPOLY C code (https://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html)
    // this allows us to improve the code yet still ensure we have its properties
    // it is under the BSD license
    // (https://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html#License%20to%20Use)
    //
    // Copyright (c) 1970-2003, Wm. Randolph Franklin
    //
    // Permission is hereby granted, free of charge, to any person obtaining a copy of this software
    // and associated
    // documentation files (the "Software"), to deal in the Software without restriction, including
    // without limitation
    // the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
    // the Software, and
    // to permit persons to whom the Software is furnished to do so, subject to the following
    // conditions:
    //
    // 1. Redistributions of source code must retain the above copyright
    //    notice, this list of conditions and the following disclaimers.
    // 2. Redistributions in binary form must reproduce the above copyright
    //    notice in the documentation and/or other materials provided with
    //    the distribution.
    // 3. The name of W. Randolph Franklin may not be used to endorse or
    //    promote products derived from this Software without specific
    //    prior written permission.
    //
    // THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
    // BUT NOT LIMITED
    // TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
    // NO EVENT SHALL
    // THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
    // IN AN ACTION OF
    // CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
    // OR OTHER DEALINGS
    // IN THE SOFTWARE.
    fun containsSlowly(polygon: XYPolygon, x: Double, y: Double): Boolean {
        if (polygon.getHoles().size > 0) {
            throw UnsupportedOperationException("this testing method does not support holes")
        }
        val polyXs: DoubleArray = XYEncodingUtils.floatArrayToDoubleArray(polygon.polyX)
        val polyYs: DoubleArray = XYEncodingUtils.floatArrayToDoubleArray( polygon.polyY)
        // bounding box check required due to rounding errors (we don't solve that problem)
        if (x < polygon.minX || x > polygon.maxX || y < polygon.minY || y > polygon.maxY) {
            return false
        }

        var c = false
        var i: Int
        var j: Int
        val nvert = polyYs.size
        val verty = polyYs
        val vertx = polyXs
        val testy = y
        val testx = x
        i = 0
        j = 1
        while (j < nvert) {
            if (testy == verty[j] && testy == verty[i]
                || ((testy <= verty[j] && testy >= verty[i])
                        != (testy >= verty[j] && testy <= verty[i]))
            ) {
                if ((testx == vertx[j] && testx == vertx[i])
                    || ((testx <= vertx[j] && testx >= vertx[i]) != (testx >= vertx[j] && testx <= vertx[i])
                            && GeoUtils.orient(vertx[i], verty[i], vertx[j], verty[j], testx, testy) == 0)
                ) {
                    // return true if point is on boundary
                    return true
                } else if (((verty[i] > testy) != (verty[j] > testy))
                    && (testx
                            < (vertx[j] - vertx[i]) * (testy - verty[i]) / (verty[j] - verty[i]) + vertx[i])
                ) {
                    c = !c
                }
            }
            ++i
            ++j
        }
        return c
    }
}
