package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.toUnsignedLong
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.util.SloppyMath
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.nextDown

/**
 * reusable geopoint encoding methods
 *
 * @lucene.experimental
 */
object GeoEncodingUtils {
    /** number of bits used for quantizing latitude and longitude values  */
    const val BITS: Short = 32

    private const val LAT_SCALE = (0x1L shl BITS.toInt()) / 180.0
    private const val LAT_DECODE = 1 / LAT_SCALE
    private const val LON_SCALE = (0x1L shl BITS.toInt()) / 360.0
    private const val LON_DECODE = 1 / LON_SCALE

    val MIN_LON_ENCODED: Int = encodeLongitude(GeoUtils.MIN_LON_INCL)
    val MAX_LON_ENCODED: Int = encodeLongitude(GeoUtils.MAX_LON_INCL)

    /**
     * Quantizes double (64 bit) latitude into 32 bits (rounding down: in the direction of -90)
     *
     * @param latitude latitude value: must be within standard +/-90 coordinate bounds.
     * @return encoded value as a 32-bit `int`
     * @throws IllegalArgumentException if latitude is out of bounds
     */
    fun encodeLatitude(latitude: Double): Int {
        var latitude = latitude
        GeoUtils.checkLatitude(latitude)
        // the maximum possible value cannot be encoded without overflow
        if (latitude == 90.0) {
            latitude = latitude.nextDown()
        }
        return floor(latitude / LAT_DECODE).toInt()
    }

    /**
     * Quantizes double (64 bit) latitude into 32 bits (rounding up: in the direction of +90)
     *
     * @param latitude latitude value: must be within standard +/-90 coordinate bounds.
     * @return encoded value as a 32-bit `int`
     * @throws IllegalArgumentException if latitude is out of bounds
     */
    fun encodeLatitudeCeil(latitude: Double): Int {
        var latitude = latitude
        GeoUtils.checkLatitude(latitude)
        // the maximum possible value cannot be encoded without overflow
        if (latitude == 90.0) {
            latitude = latitude.nextDown()
        }
        return ceil(latitude / LAT_DECODE).toInt()
    }

    /**
     * Quantizes double (64 bit) longitude into 32 bits (rounding down: in the direction of -180)
     *
     * @param longitude longitude value: must be within standard +/-180 coordinate bounds.
     * @return encoded value as a 32-bit `int`
     * @throws IllegalArgumentException if longitude is out of bounds
     */
    fun encodeLongitude(longitude: Double): Int {
        var longitude = longitude
        GeoUtils.checkLongitude(longitude)
        // the maximum possible value cannot be encoded without overflow
        if (longitude == 180.0) {
            longitude = longitude.nextDown()
        }
        return floor(longitude / LON_DECODE).toInt()
    }

    /**
     * Quantizes double (64 bit) longitude into 32 bits (rounding up: in the direction of +180)
     *
     * @param longitude longitude value: must be within standard +/-180 coordinate bounds.
     * @return encoded value as a 32-bit `int`
     * @throws IllegalArgumentException if longitude is out of bounds
     */
    fun encodeLongitudeCeil(longitude: Double): Int {
        var longitude = longitude
        GeoUtils.checkLongitude(longitude)
        // the maximum possible value cannot be encoded without overflow
        if (longitude == 180.0) {
            longitude = longitude.nextDown()
        }
        return ceil(longitude / LON_DECODE).toInt()
    }

    /**
     * Turns quantized value from [.encodeLatitude] back into a double.
     *
     * @param encoded encoded value: 32-bit quantized value.
     * @return decoded latitude value.
     */
    fun decodeLatitude(encoded: Int): Double {
        val result = encoded * LAT_DECODE
        require(result >= GeoUtils.MIN_LAT_INCL && result < GeoUtils.MAX_LAT_INCL)
        return result
    }

    /**
     * Turns quantized value from byte array back into a double.
     *
     * @param src byte array containing 4 bytes to decode at `offset`
     * @param offset offset into `src` to decode from.
     * @return decoded latitude value.
     */
    fun decodeLatitude(src: ByteArray, offset: Int): Double {
        return decodeLatitude(NumericUtils.sortableBytesToInt(src, offset))
    }

    /**
     * Turns quantized value from [.encodeLongitude] back into a double.
     *
     * @param encoded encoded value: 32-bit quantized value.
     * @return decoded longitude value.
     */
    fun decodeLongitude(encoded: Int): Double {
        val result = encoded * LON_DECODE
        require(result >= GeoUtils.MIN_LON_INCL && result < GeoUtils.MAX_LON_INCL)
        return result
    }

    /**
     * Turns quantized value from byte array back into a double.
     *
     * @param src byte array containing 4 bytes to decode at `offset`
     * @param offset offset into `src` to decode from.
     * @return decoded longitude value.
     */
    fun decodeLongitude(src: ByteArray, offset: Int): Double {
        return decodeLongitude(NumericUtils.sortableBytesToInt(src, offset))
    }

    /**
     * Create a predicate that checks whether points are within a distance of a given point. It works
     * by computing the bounding box around the circle that is defined by the given points/distance
     * and splitting it into between 1024 and 4096 smaller boxes (4096*0.75^2=2304 on average). Then
     * for each sub box, it computes the relation between this box and the distance query. Finally at
     * search time, it first computes the sub box that the point belongs to, most of the time, no
     * distance computation will need to be performed since all points from the sub box will either be
     * in or out of the circle.
     *
     * @lucene.internal
     */
    fun createDistancePredicate(
        lat: Double, lon: Double, radiusMeters: Double
    ): DistancePredicate {
        val boundingBox: Rectangle =
            Rectangle.fromPointDistance(lat, lon, radiusMeters)
        val axisLat: Double = Rectangle.axisLat(lat, radiusMeters)
        val distanceSortKey: Double = GeoUtils.distanceQuerySortKey(radiusMeters)
        val boxToRelation =
            { box: Rectangle ->
                GeoUtils.relate(
                    box.minLat, box.maxLat, box.minLon, box.maxLon, lat, lon, distanceSortKey, axisLat
                )
            }
        val subBoxes =
            createSubBoxes(
                boundingBox.minLat,
                boundingBox.maxLat,
                boundingBox.minLon,
                boundingBox.maxLon,
                boxToRelation
            )

        return DistancePredicate(
            subBoxes.latShift,
            subBoxes.lonShift,
            subBoxes.latBase,
            subBoxes.lonBase,
            subBoxes.maxLatDelta,
            subBoxes.maxLonDelta,
            subBoxes.relations,
            lat,
            lon,
            distanceSortKey
        )
    }

    /**
     * Create a predicate that checks whether points are within a geometry. It works the same way as
     * [.createDistancePredicate].
     *
     * @lucene.internal
     */
    fun createComponentPredicate(tree: Component2D): Component2DPredicate {
        val boxToRelation =
            { box: Rectangle ->
                tree.relate(
                    box.minLon,
                    box.maxLon,
                    box.minLat,
                    box.maxLat
                )
            }
        val subBoxes =
            createSubBoxes(
                tree.minY, tree.maxY, tree.minX, tree.maxX, boxToRelation
            )

        return Component2DPredicate(
            subBoxes.latShift,
            subBoxes.lonShift,
            subBoxes.latBase,
            subBoxes.lonBase,
            subBoxes.maxLatDelta,
            subBoxes.maxLonDelta,
            subBoxes.relations,
            tree
        )
    }

    private fun createSubBoxes(
        shapeMinLat: Double,
        shapeMaxLat: Double,
        shapeMinLon: Double,
        shapeMaxLon: Double,
        boxToRelation: (Rectangle) -> PointValues.Relation /*java.util.function.Function<Rectangle, PointValues.Relation>*/
    ): Grid {
        val minLat = encodeLatitudeCeil(shapeMinLat)
        val maxLat = encodeLatitude(shapeMaxLat)
        val minLon = encodeLongitudeCeil(shapeMinLon)
        val maxLon = encodeLongitude(shapeMaxLon)

        if (maxLat < minLat || (shapeMaxLon >= shapeMinLon && maxLon < minLon)) {
            // the box cannot match any quantized point
            return Grid(1, 1, 0, 0, 0, 0, ByteArray(0))
        }

        val latShift: Int
        val lonShift: Int
        val latBase: Int
        val lonBase: Int
        val maxLatDelta: Int
        val maxLonDelta: Int
        run {
            val minLat2 = minLat.toLong() - Int.Companion.MIN_VALUE
            val maxLat2 = maxLat.toLong() - Int.Companion.MIN_VALUE
            latShift = GeoEncodingUtils.computeShift(minLat2, maxLat2)
            latBase = (minLat2 ushr latShift).toInt()
            maxLatDelta = (maxLat2 ushr latShift).toInt() - latBase + 1
            require(maxLatDelta > 0)
        }
        run {
            val minLon2 = minLon.toLong() - Int.Companion.MIN_VALUE
            var maxLon2 = maxLon.toLong() - Int.Companion.MIN_VALUE
            if (shapeMaxLon < shapeMinLon) { // crosses dateline
                maxLon2 += 1L shl 32 // wrap
            }
            lonShift = GeoEncodingUtils.computeShift(minLon2, maxLon2)
            lonBase = (minLon2 ushr lonShift).toInt()
            maxLonDelta = (maxLon2 ushr lonShift).toInt() - lonBase + 1
            require(maxLonDelta > 0)
        }

        val relations = ByteArray(maxLatDelta * maxLonDelta)
        for (i in 0..<maxLatDelta) {
            for (j in 0..<maxLonDelta) {
                val boxMinLat = ((latBase + i) shl latShift) + Int.Companion.MIN_VALUE
                val boxMinLon = ((lonBase + j) shl lonShift) + Int.Companion.MIN_VALUE
                val boxMaxLat = boxMinLat + (1 shl latShift) - 1
                val boxMaxLon = boxMinLon + (1 shl lonShift) - 1

                relations[i * maxLonDelta + j] = boxToRelation(
                    Rectangle(
                        decodeLatitude(boxMinLat), decodeLatitude(boxMaxLat),
                        decodeLongitude(boxMinLon), decodeLongitude(boxMaxLon)
                    )
                ).ordinal.toByte()
            }
        }

        return Grid(latShift, lonShift, latBase, lonBase, maxLatDelta, maxLonDelta, relations)
    }

    /**
     * Compute the minimum shift value so that `(b>>>shift)-(a>>>shift)` is less that `ARITY`.
     */
    private fun computeShift(a: Long, b: Long): Int {
        require(a <= b)
        // We enforce a shift of at least 1 so that when we work with unsigned ints
        // by doing (lat - MIN_VALUE), the result of the shift (lat - MIN_VALUE) >>> shift
        // can be used for comparisons without particular care: the sign bit has
        // been cleared so comparisons work the same for signed and unsigned ints
        var shift = 1
        while (true) {
            val delta = (b ushr shift) - (a ushr shift)
            if (delta >= 0 && delta < Grid.Companion.ARITY) {
                return shift
            }
            ++shift
        }
    }

    open class Grid(
        latShift: Int,
        lonShift: Int,
        latBase: Int,
        lonBase: Int,
        maxLatDelta: Int,
        maxLonDelta: Int,
        relations: ByteArray
    ) {
        val latShift: Int
        val lonShift: Int
        val latBase: Int
        val lonBase: Int
        val maxLatDelta: Int
        val maxLonDelta: Int
        val relations: ByteArray

        init {
            require(!(latShift < 1 || latShift > 31))
            require(!(lonShift < 1 || lonShift > 31))
            this.latShift = latShift
            this.lonShift = lonShift
            this.latBase = latBase
            this.lonBase = lonBase
            this.maxLatDelta = maxLatDelta
            this.maxLonDelta = maxLonDelta
            this.relations = relations
        }

        companion object {
            const val ARITY: Int = 64
        }
    }

    /** A predicate that checks whether a given point is within a distance of another point.  */
    class DistancePredicate(
        latShift: Int,
        lonShift: Int,
        latBase: Int,
        lonBase: Int,
        maxLatDelta: Int,
        maxLonDelta: Int,
        relations: ByteArray,
        private val lat: Double,
        private val lon: Double,
        private val distanceKey: Double
    ) : Grid(latShift, lonShift, latBase, lonBase, maxLatDelta, maxLonDelta, relations) {
        /**
         * Check whether the given point is within a distance of another point. NOTE: this operates
         * directly on the encoded representation of points.
         */
        fun test(lat: Int, lon: Int): Boolean {
            val lat2 = ((lat - Int.Companion.MIN_VALUE) ushr latShift)
            if (lat2 < latBase || lat2 - latBase >= maxLatDelta) {
                return false
            }
            var lon2 = ((lon - Int.Companion.MIN_VALUE) ushr lonShift)
            if (lon2 < lonBase) { // wrap
                lon2 += 1 shl (32 - lonShift)
            }
            require(Int.toUnsignedLong(lon2) >= lonBase)
            require(lon2 - lonBase >= 0)
            if (lon2 - lonBase >= maxLonDelta) {
                return false
            }

            val relation = relations[(lat2 - latBase) * maxLonDelta + (lon2 - lonBase)].toInt()
            return if (relation == PointValues.Relation.CELL_CROSSES_QUERY.ordinal) {
                (SloppyMath.haversinSortKey(
                    decodeLatitude(lat), decodeLongitude(lon), this.lat, this.lon
                )
                        <= distanceKey)
            } else {
                relation == PointValues.Relation.CELL_INSIDE_QUERY.ordinal
            }
        }
    }

    /** A predicate that checks whether a given point is within a component2D geometry.  */
    class Component2DPredicate(
        latShift: Int,
        lonShift: Int,
        latBase: Int,
        lonBase: Int,
        maxLatDelta: Int,
        maxLonDelta: Int,
        relations: ByteArray,
        val tree: Component2D
    ) : Grid(latShift, lonShift, latBase, lonBase, maxLatDelta, maxLonDelta, relations) {

        /**
         * Check whether the given point is within the considered polygon. NOTE: this operates directly
         * on the encoded representation of points.
         */
        fun test(lat: Int, lon: Int): Boolean {
            val lat2 = ((lat - Int.Companion.MIN_VALUE) ushr latShift)
            if (lat2 < latBase || lat2 - latBase >= maxLatDelta) {
                return false
            }
            var lon2 = ((lon - Int.Companion.MIN_VALUE) ushr lonShift)
            if (lon2 < lonBase) { // wrap
                lon2 += 1 shl (32 - lonShift)
            }
            require(Int.toUnsignedLong(lon2) >= lonBase)
            require(lon2 - lonBase >= 0)
            if (lon2 - lonBase >= maxLonDelta) {
                return false
            }

            val relation = relations[(lat2 - latBase) * maxLonDelta + (lon2 - lonBase)].toInt()
            return if (relation == PointValues.Relation.CELL_CROSSES_QUERY.ordinal) {
                tree.contains(decodeLongitude(lon), decodeLatitude(lat))
            } else {
                relation == PointValues.Relation.CELL_INSIDE_QUERY.ordinal
            }
        }
    }
}
