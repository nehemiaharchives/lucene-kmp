package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.FieldComparator
import org.gnit.lucenekmp.search.LeafFieldComparator
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.SloppyMath
import kotlin.math.max
import kotlin.math.min

/**
 * Compares documents by distance from an origin point
 *
 *
 * When the least competitive item on the priority queue changes (setBottom), we recompute a
 * bounding box representing competitive distance to the top-N. Then in compareBottom, we can
 * quickly reject hits based on bounding box alone without computing distance for every element.
 */
internal class LatLonPointDistanceComparator(val field: String, val latitude: Double, val longitude: Double, numHits: Int) : FieldComparator<Double>(), LeafFieldComparator {
    val values: DoubleArray = DoubleArray(numHits)
    var bottom: Double = 0.0
    private var topValue: Double = 0.0
    lateinit var currentDocs: SortedNumericDocValues /*= null*/

    // current bounding box(es) for the bottom distance on the PQ.
    // these are pre-encoded with LatLonPoint's encoding and
    // used to exclude uncompetitive hits faster.
    var minLon: Int = Int.MIN_VALUE
    var maxLon: Int = Int.MAX_VALUE
    var minLat: Int = Int.MIN_VALUE
    var maxLat: Int = Int.MAX_VALUE

    // second set of longitude ranges to check (for cross-dateline case)
    var minLon2: Int = Int.MAX_VALUE

    // the number of times setBottom has been called (adversary protection)
    var setBottomCounter: Int = 0

    private var currentValues = LongArray(4)
    private var valuesDocID = -1

    override fun setScorer(scorer: Scorable) {}

    override fun compare(slot1: Int, slot2: Int): Int {
        return Double.compare(values[slot1], values[slot2])
    }

    override fun setBottom(slot: Int) {
        bottom = values[slot]
        // make bounding box(es) to exclude non-competitive hits, but start
        // sampling if we get called way too much: don't make gobs of bounding
        // boxes if comparator hits a worst case order (e.g. backwards distance order)
        if (setBottomCounter < 1024 || (setBottomCounter and 0x3F) == 0x3F) {
            val box: Rectangle = Rectangle.fromPointDistance(latitude, longitude, haversin2(bottom))
            // pre-encode our box to our integer encoding, so we don't have to decode
            // to double values for uncompetitive hits. This has some cost!
            minLat = GeoEncodingUtils.encodeLatitude(box.minLat)
            maxLat = GeoEncodingUtils.encodeLatitude(box.maxLat)
            if (box.crossesDateline()) {
                // box1
                minLon = Int.MIN_VALUE
                maxLon = GeoEncodingUtils.encodeLongitude(box.maxLon)
                // box2
                minLon2 = GeoEncodingUtils.encodeLongitude(box.minLon)
            } else {
                minLon = GeoEncodingUtils.encodeLongitude(box.minLon)
                maxLon = GeoEncodingUtils.encodeLongitude(box.maxLon)
                // disable box2
                minLon2 = Int.MAX_VALUE
            }
        }
        setBottomCounter++
    }

    override fun setTopValue(value: Double) {
        topValue = value
    }

    @Throws(IOException::class)
    private fun setValues() {
        if (valuesDocID != currentDocs.docID()) {
            assert(
                valuesDocID < currentDocs.docID()
            ) { " valuesDocID=" + valuesDocID + " vs " + currentDocs.docID() }
            valuesDocID = currentDocs.docID()
            val count: Int = currentDocs.docValueCount()
            if (count > currentValues.size) {
                currentValues = LongArray(ArrayUtil.oversize(count, Long.SIZE_BYTES))
            }
            for (i in 0..<count) {
                currentValues[i] = currentDocs.nextValue()
            }
        }
    }

    @Throws(IOException::class)
    override fun compareBottom(doc: Int): Int {
        if (doc > currentDocs.docID()) {
            currentDocs.advance(doc)
        }
        if (doc < currentDocs.docID()) {
            return Double.compare(bottom, Double.POSITIVE_INFINITY)
        }

        setValues()

        val numValues: Int = currentDocs.docValueCount()

        var cmp = -1
        for (i in 0..<numValues) {
            val encoded = currentValues[i]

            // test bounding box
            val latitudeBits = (encoded shr 32).toInt()
            if (latitudeBits !in minLat..maxLat) {
                continue
            }
            val longitudeBits = (encoded and 0xFFFFFFFFL).toInt()
            if ((longitudeBits !in minLon..maxLon) && (longitudeBits < minLon2)) {
                continue
            }

            // only compute actual distance if its inside "competitive bounding box"
            val docLatitude: Double = GeoEncodingUtils.decodeLatitude(latitudeBits)
            val docLongitude: Double = GeoEncodingUtils.decodeLongitude(longitudeBits)
            cmp = max(
                cmp,
                Double.compare(
                    bottom,
                    SloppyMath.haversinSortKey(latitude, longitude, docLatitude, docLongitude)
                )
            )
            // once we compete in the PQ, no need to continue.
            if (cmp > 0) {
                return cmp
            }
        }
        return cmp
    }

    @Throws(IOException::class)
    override fun copy(slot: Int, doc: Int) {
        values[slot] = sortKey(doc)
    }

    @Throws(IOException::class)
    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
        val reader: LeafReader = context.reader()
        val info: FieldInfo? = reader.fieldInfos.fieldInfo(field)
        if (info != null) {
            LatLonDocValuesField.checkCompatible(info)
        }
        currentDocs = DocValues.getSortedNumeric(reader, field)
        valuesDocID = -1
        return this
    }

    override fun value(slot: Int): Double {
        return haversin2(values[slot])
    }

    @Throws(IOException::class)
    override fun compareTop(doc: Int): Int {
        return Double.compare(topValue, haversin2(sortKey(doc)))
    }

    // TODO: optimize for single-valued case
    // TODO: do all kinds of other optimizations!
    @Throws(IOException::class)
    fun sortKey(doc: Int): Double {
        if (doc > currentDocs.docID()) {
            currentDocs.advance(doc)
        }
        var minValue = Double.POSITIVE_INFINITY
        if (doc == currentDocs.docID()) {
            setValues()
            val numValues: Int = currentDocs.docValueCount()
            for (i in 0..<numValues) {
                val encoded = currentValues[i]
                val docLatitude: Double = GeoEncodingUtils.decodeLatitude((encoded shr 32).toInt())
                val docLongitude: Double = GeoEncodingUtils.decodeLongitude((encoded and 0xFFFFFFFFL).toInt())
                minValue = min(
                    minValue,
                    SloppyMath.haversinSortKey(latitude, longitude, docLatitude, docLongitude)
                )
            }
        }
        return minValue
    }

    companion object {
        // second half of the haversin calculation, used to convert results from haversin1 (used
        // internally
        // for sorting) for display purposes.
        fun haversin2(partial: Double): Double {
            if (partial.isInfinite()) {
                return partial
            }
            return SloppyMath.haversinMeters(partial)
        }
    }
}
