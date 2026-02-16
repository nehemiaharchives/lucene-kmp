package org.gnit.lucenekmp.tests.geo

import okio.IOException
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.util.SloppyMath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.withSign
import kotlin.random.Random

/** static methods for testing geo  */
object GeoTestUtil {
    /** returns next pseudorandom latitude (anywhere)  */
    fun nextLatitude(): Double {
        return nextDoubleInternal(GeoUtils.MIN_LAT_INCL, GeoUtils.MAX_LAT_INCL)
    }

    /** returns next pseudorandom longitude (anywhere)  */
    fun nextLongitude(): Double {
        return nextDoubleInternal(GeoUtils.MIN_LON_INCL, GeoUtils.MAX_LON_INCL)
    }

    /**
     * Returns next double within range.
     *
     *
     * Don't pass huge numbers or infinity or anything like that yet. may have bugs!
     */
    // the goal is to adjust random number generation to test edges, create more duplicates, create
    // "one-offs" in floating point space, etc.
    // we do this by first picking a good "base value" (explicitly targeting edges, zero if allowed,
    // or "discrete values"). but it also
    // ensures we pick any double in the range and generally still produces randomish looking numbers.
    // then we sometimes perturb that by one ulp.
    private fun nextDoubleInternal(low: Double, high: Double): Double {
        assert(low >= Int.MIN_VALUE)
        assert(high <= Int.MAX_VALUE)
        assert(Double.isFinite(low))
        assert(Double.isFinite(high))
        assert(high >= low) { "low=$low high=$high" }

        // if they are equal, not much we can do
        if (low == high) {
            return low
        }

        // first pick a base value.
        val baseValue: Double
        val surpriseMe: Int = random().nextInt(17)
        if (surpriseMe == 0) {
            // random bits
            val lowBits: Long = NumericUtils.doubleToSortableLong(low)
            val highBits: Long = NumericUtils.doubleToSortableLong(high)
            baseValue = NumericUtils.sortableLongToDouble(TestUtil.nextLong(random(), lowBits, highBits))
        } else if (surpriseMe == 1) {
            // edge case
            baseValue = low
        } else if (surpriseMe == 2) {
            // edge case
            baseValue = high
        } else if (surpriseMe == 3 && low <= 0 && high >= 0) {
            // may trigger divide by 0
            baseValue = 0.0
        } else if (surpriseMe == 4) {
            // divide up space into block of 360
            val delta = (high - low) / 360
            val block: Int = random().nextInt(360)
            baseValue = low + delta * block
        } else {
            // distributed ~ evenly
            baseValue = low + (high - low) * random().nextDouble()
        }

        assert(baseValue >= low)
        assert(baseValue <= high)

        // either return the base value or adjust it by 1 ulp in a random direction (if possible)
        val adjustMe: Int = random().nextInt(17)
        if (adjustMe == 0) {
            return Math.nextAfter(adjustMe.toFloat(), high).toDouble()
        } else if (adjustMe == 1) {
            return Math.nextAfter(adjustMe.toFloat(), low).toDouble()
        } else {
            return baseValue
        }
    }

    /** returns next pseudorandom latitude, kinda close to `otherLatitude`  */
    private fun nextLatitudeNear(otherLatitude: Double, delta: Double): Double {
        var delta = delta
        delta = abs(delta)
        GeoUtils.checkLatitude(otherLatitude)
        val surpriseMe: Int = random().nextInt(97)
        if (surpriseMe == 0) {
            // purely random
            return nextLatitude()
        } else if (surpriseMe < 49) {
            // upper half of region (the exact point or 1 ulp difference is still likely)
            return nextDoubleInternal(otherLatitude, min(90.0, otherLatitude + delta))
        } else {
            // lower half of region (the exact point or 1 ulp difference is still likely)
            return nextDoubleInternal(max(-90.0, otherLatitude - delta), otherLatitude)
        }
    }

    /** returns next pseudorandom longitude, kinda close to `otherLongitude`  */
    private fun nextLongitudeNear(otherLongitude: Double, delta: Double): Double {
        var delta = delta
        delta = abs(delta)
        GeoUtils.checkLongitude(otherLongitude)
        val surpriseMe: Int = random().nextInt(97)
        if (surpriseMe == 0) {
            // purely random
            return nextLongitude()
        } else if (surpriseMe < 49) {
            // upper half of region (the exact point or 1 ulp difference is still likely)
            return nextDoubleInternal(otherLongitude, min(180.0, otherLongitude + delta))
        } else {
            // lower half of region (the exact point or 1 ulp difference is still likely)
            return nextDoubleInternal(max(-180.0, otherLongitude - delta), otherLongitude)
        }
    }

    /**
     * returns next pseudorandom latitude, kinda close to `minLatitude/maxLatitude`
     * **NOTE:**minLatitude/maxLatitude are merely guidelines. the returned value is sometimes
     * outside of that range! this is to facilitate edge testing of lines
     */
    private fun nextLatitudeBetween(minLatitude: Double, maxLatitude: Double): Double {
        assert(maxLatitude >= minLatitude)
        GeoUtils.checkLatitude(minLatitude)
        GeoUtils.checkLatitude(maxLatitude)
        if (random().nextInt(47) == 0) {
            // purely random
            return nextLatitude()
        } else {
            // extend the range by 1%
            val difference = (maxLatitude - minLatitude) / 100
            val lower = max(-90.0, minLatitude - difference)
            val upper = min(90.0, maxLatitude + difference)
            return nextDoubleInternal(lower, upper)
        }
    }

    /**
     * returns next pseudorandom longitude, kinda close to `minLongitude/maxLongitude`
     * **NOTE:**minLongitude/maxLongitude are merely guidelines. the returned value is sometimes
     * outside of that range! this is to facilitate edge testing of lines
     */
    private fun nextLongitudeBetween(minLongitude: Double, maxLongitude: Double): Double {
        assert(maxLongitude >= minLongitude)
        GeoUtils.checkLongitude(minLongitude)
        GeoUtils.checkLongitude(maxLongitude)
        if (random().nextInt(47) == 0) {
            // purely random
            return nextLongitude()
        } else {
            // extend the range by 1%
            val difference = (maxLongitude - minLongitude) / 100
            val lower = max(-180.0, minLongitude - difference)
            val upper = min(180.0, maxLongitude + difference)
            return nextDoubleInternal(lower, upper)
        }
    }

    /** Returns the next point around a line (more or less)  */
    private fun nextPointAroundLine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): DoubleArray {
        val x1 = lon1
        val x2 = lon2
        val y1 = lat1
        val y2 = lat2
        val minX = min(x1, x2)
        val maxX = max(x1, x2)
        val minY = min(y1, y2)
        val maxY = max(y1, y2)
        if (minX == maxX) {
            return doubleArrayOf(
                nextLatitudeBetween(minY, maxY), nextLongitudeNear(minX, 0.01 * (maxY - minY))
            )
        } else if (minY == maxY) {
            return doubleArrayOf(
                nextLatitudeNear(minY, 0.01 * (maxX - minX)), nextLongitudeBetween(minX, maxX)
            )
        } else {
            val x = nextLongitudeBetween(minX, maxX)
            var y = (y1 - y2) / (x1 - x2) * (x - x1) + y1
            if (Double.isFinite(y) == false) {
                // this can happen due to underflow when delta between x values is wonderfully tiny!
                y = 90.0.withSign(x1)
            }
            val delta = (maxY - minY) * 0.01
            // our formula may put the targeted Y out of bounds
            y = min(90.0, y)
            y = max(-90.0, y)
            return doubleArrayOf(nextLatitudeNear(y, delta), x)
        }
    }

    /** Returns next point (lat/lon) for testing near a Box. It may cross the dateline  */
    fun nextPointNear(rectangle: Rectangle): DoubleArray {
        if (rectangle.crossesDateline()) {
            // pick a "side" of the two boxes we really are
            if (random().nextBoolean()) {
                return nextPointNear(
                    Rectangle(rectangle.minLat, rectangle.maxLat, -180.0, rectangle.maxLon)
                )
            } else {
                return nextPointNear(
                    Rectangle(rectangle.minLat, rectangle.maxLat, rectangle.minLon, 180.0)
                )
            }
        } else {
            return nextPointNear(boxPolygon(rectangle))
        }
    }

    /** Returns next point (lat/lon) for testing near a Polygon  */ // see http://www-ma2.upc.es/geoc/Schirra-pointPolygon.pdf for more info on some of these
    // strategies
    fun nextPointNear(polygon: Polygon): DoubleArray {
        val polyLats: DoubleArray = polygon.getPolyLats()
        val polyLons: DoubleArray = polygon.getPolyLons()
        val holes: Array<Polygon> = polygon.getHoles()

        // if there are any holes, target them aggressively
        if (holes.isNotEmpty() && random().nextInt(3) == 0) {
            return nextPointNear(holes[random().nextInt(holes.size)])
        }

        val surpriseMe: Int = random().nextInt(97)
        if (surpriseMe == 0) {
            // purely random
            return doubleArrayOf(nextLatitude(), nextLongitude())
        } else if (surpriseMe < 5) {
            // purely random within bounding box
            return doubleArrayOf(
                nextLatitudeBetween(polygon.minLat, polygon.maxLat),
                nextLongitudeBetween(polygon.minLon, polygon.maxLon)
            )
        } else if (surpriseMe < 20) {
            // target a vertex
            val vertex: Int = random().nextInt(polyLats.size - 1)
            return doubleArrayOf(
                nextLatitudeNear(polyLats[vertex], polyLats[vertex + 1] - polyLats[vertex]),
                nextLongitudeNear(polyLons[vertex], polyLons[vertex + 1] - polyLons[vertex])
            )
        } else if (surpriseMe < 30) {
            // target points around the bounding box edges
            val container: Polygon =
                boxPolygon(Rectangle(polygon.minLat, polygon.maxLat, polygon.minLon, polygon.maxLon))
            val containerLats: DoubleArray = container.getPolyLats()
            val containerLons: DoubleArray = container.getPolyLons()
            val startVertex: Int = random().nextInt(containerLats.size - 1)
            return nextPointAroundLine(
                containerLats[startVertex], containerLons[startVertex],
                containerLats[startVertex + 1], containerLons[startVertex + 1]
            )
        } else {
            // target points around diagonals between vertices
            val startVertex: Int = random().nextInt(polyLats.size - 1)
            // but favor edges heavily
            val endVertex =
                if (random().nextBoolean()) startVertex + 1 else random().nextInt(polyLats.size - 1)
            return nextPointAroundLine(
                polyLats[startVertex], polyLons[startVertex],
                polyLats[endVertex], polyLons[endVertex]
            )
        }
    }

    /** Returns next box for testing near a Polygon  */
    fun nextBoxNear(polygon: Polygon): Rectangle {
        val point1: DoubleArray
        val point2: DoubleArray

        // if there are any holes, target them aggressively
        val holes: Array<Polygon> = polygon.getHoles()
        if (holes.isNotEmpty() && random().nextInt(3) == 0) {
            return nextBoxNear(holes[random().nextInt(holes.size)])
        }

        val surpriseMe: Int = random().nextInt(97)
        if (surpriseMe == 0) {
            // formed from two interesting points
            point1 = nextPointNear(polygon)
            point2 = nextPointNear(polygon)
        } else {
            // formed from one interesting point: then random within delta.
            point1 = nextPointNear(polygon)
            point2 = DoubleArray(2)
            // now figure out a good delta: we use a rough heuristic, up to the length of an edge
            val polyLats: DoubleArray = polygon.getPolyLats()
            val polyLons: DoubleArray = polygon.getPolyLons()
            val vertex: Int = random().nextInt(polyLats.size - 1)
            val deltaX = polyLons[vertex + 1] - polyLons[vertex]
            val deltaY = polyLats[vertex + 1] - polyLats[vertex]
            val edgeLength = sqrt(deltaX * deltaX + deltaY * deltaY)
            point2[0] = nextLatitudeNear(point1[0], edgeLength)
            point2[1] = nextLongitudeNear(point1[1], edgeLength)
        }

        // form a box from the two points
        val minLat = min(point1[0], point2[0])
        val maxLat = max(point1[0], point2[0])
        val minLon = min(point1[1], point2[1])
        val maxLon = max(point1[1], point2[1])
        return Rectangle(minLat, maxLat, minLon, maxLon)
    }

    /** returns next pseudorandom box: can cross the 180th meridian  */
    fun nextBox(): Rectangle {
        return nextBoxInternal(true)
    }

    /** returns next pseudorandom box: does not cross the 180th meridian  */
    fun nextBoxNotCrossingDateline(): Rectangle {
        return nextBoxInternal(false)
    }

    /**
     * Makes an n-gon, centered at the provided lat/lon, and each vertex approximately distanceMeters
     * away from the center.
     *
     *
     * Do not invoke me across the dateline or a pole!!
     */
    fun createRegularPolygon(
        centerLat: Double, centerLon: Double, radiusMeters: Double, gons: Int
    ): Polygon {
        // System.out.println("MAKE POLY: centerLat=" + centerLat + " centerLon=" + centerLon + "
        // radiusMeters=" + radiusMeters + " gons=" + gons);

        val result = arrayOfNulls<DoubleArray>(2)
        result[0] = DoubleArray(gons + 1)
        result[1] = DoubleArray(gons + 1)
        // System.out.println("make gon=" + gons);
        for (i in 0..<gons) {
            val angle = 360.0 - i * (360.0 / gons)
            // System.out.println("  angle " + angle);
            val x = cos(Math.toRadians(angle))
            val y = sin(Math.toRadians(angle))
            var factor = 2.0
            var step = 1.0
            var last = 0

            // System.out.println("angle " + angle + " slope=" + slope);
            // Iterate out along one spoke until we hone in on the point that's nearly exactly
            // radiusMeters from the center:
            while (true) {
                // TODO: we could in fact cross a pole  Just do what surpriseMePolygon does

                val lat = centerLat + y * factor
                GeoUtils.checkLatitude(lat)
                val lon = centerLon + x * factor
                GeoUtils.checkLongitude(lon)
                val distanceMeters: Double = SloppyMath.haversinMeters(centerLat, centerLon, lat, lon)

                // System.out.println("  iter lat=" + lat + " lon=" + lon + " distance=" + distanceMeters +
                // " vs " + radiusMeters);
                if (abs(distanceMeters - radiusMeters) < 0.1) {
                    // Within 10 cm: close enough!
                    result[0]!![i] = lat
                    result[1]!![i] = lon
                    break
                }

                if (distanceMeters > radiusMeters) {
                    // too big
                    // System.out.println("    smaller");
                    factor -= step
                    if (last == 1) {
                        // System.out.println("      half-step");
                        step /= 2.0
                    }
                    last = -1
                } else if (distanceMeters < radiusMeters) {
                    // too small
                    // System.out.println("    bigger");
                    factor += step
                    if (last == -1) {
                        // System.out.println("      half-step");
                        step /= 2.0
                    }
                    last = 1
                }
            }
        }

        // close poly
        result[0]!![gons] = result[0]!![0]
        result[1]!![gons] = result[1]!![0]

        // System.out.println("  polyLats=" + Arrays.toString(result[0]));
        // System.out.println("  polyLons=" + Arrays.toString(result[1]));
        return Polygon(result[0]!!, result[1]!!)
    }

    fun nextPoint(): Point {
        val lat = nextLatitude()
        val lon = nextLongitude()
        return Point(lat, lon)
    }

    fun nextLine(): Line {
        val p: Polygon = nextPolygon()
        val lats = DoubleArray(p.numPoints() - 1)
        val lons = DoubleArray(lats.size)
        for (i in lats.indices) {
            lats[i] = p.getPolyLat(i)
            lons[i] = p.getPolyLon(i)
        }
        return Line(lats, lons)
    }

    fun nextCircle(): Circle {
        val lat = nextLatitude()
        val lon = nextLongitude()
        val radiusMeters: Double =
            random().nextDouble() * GeoUtils.EARTH_MEAN_RADIUS_METERS * Math.PI / 2.0 + 1.0
        return Circle(lat, lon, radiusMeters)
    }

    /** returns next pseudorandom polygon  */
    fun nextPolygon(): Polygon {
        if (random().nextBoolean()) {
            return surpriseMePolygon()
        } else if (random().nextInt(10) == 1) {
            // this poly is slow to create ... only do it 10% of the time:
            while (true) {
                val gons: Int = TestUtil.nextInt(random(), 4, 500)
                // So the poly can cover at most 50% of the earth's surface:
                val radiusMeters: Double =
                    random().nextDouble() * GeoUtils.EARTH_MEAN_RADIUS_METERS * Math.PI / 2.0 + 1.0
                try {
                    return createRegularPolygon(nextLatitude(), nextLongitude(), radiusMeters, gons)
                } catch (iae: IllegalArgumentException) {
                    // we tried to cross dateline or pole ... try again
                }
            }
        }

        val box: Rectangle = nextBoxInternal(false)
        if (random().nextBoolean()) {
            // box
            return boxPolygon(box)
        } else {
            // triangle
            return trianglePolygon(box)
        }
    }

    private fun nextBoxInternal(canCrossDateLine: Boolean): Rectangle {
        // prevent lines instead of boxes
        var lat0 = nextLatitude()
        var lat1 = nextLatitude()
        while (lat0 == lat1) {
            lat1 = nextLatitude()
        }
        // prevent lines instead of boxes
        var lon0 = nextLongitude()
        var lon1 = nextLongitude()
        while (lon0 == lon1) {
            lon1 = nextLongitude()
        }

        if (lat1 < lat0) {
            val x = lat0
            lat0 = lat1
            lat1 = x
        }

        if (canCrossDateLine == false && lon1 < lon0) {
            val x = lon0
            lon0 = lon1
            lon1 = x
        }

        return Rectangle(lat0, lat1, lon0, lon1)
    }

    private fun boxPolygon(box: Rectangle): Polygon {
        assert(box.crossesDateline() == false)
        val polyLats = DoubleArray(5)
        val polyLons = DoubleArray(5)
        polyLats[0] = box.minLat
        polyLons[0] = box.minLon
        polyLats[1] = box.maxLat
        polyLons[1] = box.minLon
        polyLats[2] = box.maxLat
        polyLons[2] = box.maxLon
        polyLats[3] = box.minLat
        polyLons[3] = box.maxLon
        polyLats[4] = box.minLat
        polyLons[4] = box.minLon
        return Polygon(polyLats, polyLons)
    }

    private fun trianglePolygon(box: Rectangle): Polygon {
        assert(box.crossesDateline() == false)
        val polyLats = DoubleArray(4)
        val polyLons = DoubleArray(4)
        polyLats[0] = box.minLat
        polyLons[0] = box.minLon
        polyLats[1] = box.maxLat
        polyLons[1] = box.minLon
        polyLats[2] = box.maxLat
        polyLons[2] = box.maxLon
        polyLats[3] = box.minLat
        polyLons[3] = box.minLon
        return Polygon(polyLats, polyLons)
    }

    private fun surpriseMePolygon(): Polygon {
        // repeat until we get a poly that doesn't cross dateline:
        newPoly@ while (true) {
            // System.out.println("\nPOLY ITER");
            val centerLat = nextLatitude()
            val centerLon = nextLongitude()
            val radius: Double = 0.1 + 20 * random().nextDouble()
            val radiusDelta: Double = random().nextDouble()

            val lats: ArrayList<Double> = ArrayList()
            val lons: ArrayList<Double> = ArrayList()
            var angle = 0.0
            while (true) {
                angle += random().nextDouble() * 40.0
                // System.out.println("  angle " + angle);
                if (angle > 360) {
                    break
                }
                val len: Double = radius * (1.0 - radiusDelta + radiusDelta * random().nextDouble())
                // System.out.println("    len=" + len);
                val lat = centerLat + len * cos(Math.toRadians(angle))
                val lon = centerLon + len * sin(Math.toRadians(angle))
                if (lon <= GeoUtils.MIN_LON_INCL || lon >= GeoUtils.MAX_LON_INCL || lat > 90 || lat < -90) {
                    // cannot cross dateline or pole: try again!
                    continue@newPoly
                }
                lats.add(lat)
                lons.add(lon)

                // System.out.println("    lat=" + lats.get(lats.size()-1) + " lon=" +
                // lons.get(lons.size()-1));
            }

            // close it
            lats.add(lats[0])
            lons.add(lons[0])

            val latsArray = DoubleArray(lats.size)
            val lonsArray = DoubleArray(lons.size)
            for (i in lats.indices) {
                latsArray[i] = lats[i]
                lonsArray[i] = lons[i]
            }
            return Polygon(latsArray, lonsArray)
        }
    }

    /** Keep it simple, we don't need to take arbitrary Random for geo tests  */
    private fun random(): Random {
        return Random
    }

    /**
     * Returns svg of polygon for debugging.
     *
     *
     * You can pass any number of objects: Polygon: polygon with optional holes Polygon[]: arrays
     * of polygons for convenience Rectangle: for a box double[2]: as latitude,longitude for a point
     *
     *
     * At least one object must be a polygon. The viewBox is formed around all polygons found in
     * the arguments.
     */
    fun toSVG(vararg objects: Any): String {
        val flattened: MutableList<Any> = ArrayList()
        for (o in objects) {
            if (o is Array<*>) {
                flattened.addAll(o as Array<Polygon>)
            } else {
                flattened.add(o)
            }
        }
        // first compute bounding area of all the objects
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (o in flattened) {
            val r: Rectangle
            if (o is Polygon) {
                r = Rectangle.fromPolygon(arrayOf(o))
                minLat = min(minLat, r.minLat)
                maxLat = max(maxLat, r.maxLat)
                minLon = min(minLon, r.minLon)
                maxLon = max(maxLon, r.maxLon)
            }
        }
        require(!(Double.isFinite(minLat) == false || Double.isFinite(maxLat) == false || Double.isFinite(minLon) == false || Double.isFinite(maxLon) == false)) { "you must pass at least one polygon" }

        // add some additional padding so we can really see what happens on the edges too
        val xpadding = (maxLon - minLon) / 64
        val ypadding = (maxLat - minLat) / 64
        // expand points to be this large
        val pointX = xpadding * 0.1
        val pointY = ypadding * 0.1
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" height=\"640\" width=\"480\" viewBox=\"")
        sb.append(minLon - xpadding)
            .append(" ")
            .append(90 - maxLat - ypadding)
            .append(" ")
            .append(maxLon - minLon + (2 * xpadding))
            .append(" ")
            .append(maxLat - minLat + (2 * ypadding))
        sb.append("\">\n")

        // encode each object
        for (o in flattened) {
            // tostring
            if (o is DoubleArray) {
                sb.append("<!-- point: ")
                sb.append(o[0]).append(',').append(o[1])
                sb.append(" -->\n")
            } else {
                sb.append("<!-- ").append(o::class.simpleName).append(": \n")
                sb.append(o.toString())
                sb.append("\n-->\n")
            }
            val gon: Polygon
            val style: String
            val opacity: String
            if (o is Rectangle) {
                gon = boxPolygon(o)
                style = "fill:lightskyblue;stroke:black;stroke-width:0.2%;stroke-dasharray:0.5%,1%;"
                opacity = "0.3"
            } else if (o is DoubleArray) {
                gon =
                    boxPolygon(
                        Rectangle(
                            max(-90.0, o[0] - pointY),
                            min(90.0, o[0] + pointY),
                            max(-180.0, o[1] - pointX),
                            min(180.0, o[1] + pointX)
                        )
                    )
                style = "fill:red;stroke:red;stroke-width:0.1%;"
                opacity = "0.7"
            } else {
                gon = o as Polygon
                style = "fill:lawngreen;stroke:black;stroke-width:0.3%;"
                opacity = "0.5"
            }
            // polygon
            val polyLats: DoubleArray = gon.getPolyLats()
            val polyLons: DoubleArray = gon.getPolyLons()
            sb.append("<polygon fill-opacity=\"").append(opacity).append("\" points=\"")
            for (i in polyLats.indices) {
                if (i > 0) {
                    sb.append(" ")
                }
                sb.append(polyLons[i]).append(",").append(90 - polyLats[i])
            }
            sb.append("\" style=\"").append(style).append("\"/>\n")
            for (hole in gon.getHoles()) {
                val holeLats: DoubleArray = hole.getPolyLats()
                val holeLons: DoubleArray = hole.getPolyLons()
                sb.append("<polygon points=\"")
                for (i in holeLats.indices) {
                    if (i > 0) {
                        sb.append(" ")
                    }
                    sb.append(holeLons[i]).append(",").append(90 - holeLats[i])
                }
                sb.append("\" style=\"fill:lightgray\"/>\n")
            }
        }
        sb.append("</svg>\n")
        return sb.toString()
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
    fun containsSlowly(polygon: Polygon, latitude: Double, longitude: Double): Boolean {
        if (polygon.getHoles().isNotEmpty()) {
            throw UnsupportedOperationException("this testing method does not support holes")
        }
        val polyLats: DoubleArray = polygon.getPolyLats()
        val polyLons: DoubleArray = polygon.getPolyLons()
        // bounding box check required due to rounding errors (we don't solve that problem)
        if (latitude < polygon.minLat || latitude > polygon.maxLat || longitude < polygon.minLon || longitude > polygon.maxLon) {
            return false
        }

        var c = false
        var i: Int
        var j: Int
        val nvert = polyLats.size
        val verty = polyLats
        val vertx = polyLons
        val testy = latitude
        val testx = longitude
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

    /** reads a shape from file  */
    @Throws(IOException::class)
    fun readShape(name: String): String {
        return Loader.LOADER.readShape(name)
    }

    private class Loader {
        @Throws(IOException::class)
        fun readShape(name: String): String {
            /*var `is`: java.io.InputStream = javaClass.getResourceAsStream(name)
            if (`is` == null) {
                throw java.io.FileNotFoundException("classpath resource not found: " + name)
            }
            if (name.endsWith(".gz")) {
                `is` = java.util.zip.GZIPInputStream(`is`)
            }
            val reader: BufferedReader = BufferedReader(InputStreamReader(`is`, StandardCharsets.UTF_8))
            val builder: StringBuilder = StringBuilder()
            reader.lines().forEach { s: String -> builder.append(s) }
            return builder.toString()*/
            TODO("need to implement this")
        }

        companion object {
            val LOADER: Loader = Loader()
        }
    }
}
