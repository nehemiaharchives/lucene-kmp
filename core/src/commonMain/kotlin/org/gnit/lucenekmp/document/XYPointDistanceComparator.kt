package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYRectangle
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


/**
 * Compares documents by distance from an origin point
 *
 *
 * When the least competitive item on the priority queue changes (setBottom), we recompute a
 * bounding box representing competitive distance to the top-N. Then in compareBottom, we can
 * quickly reject hits based on bounding box alone without computing distance for every element.
 */
internal class XYPointDistanceComparator(val field: String, x: Float, y: Float, numHits: Int) : FieldComparator<Double>(), LeafFieldComparator {
    val x: Double = x.toDouble()
    val y: Double = y.toDouble()

    // distances needs to be calculated with square root to
    // avoid numerical issues (square distances are different but
    // actual distances are equal)
    val values: DoubleArray = DoubleArray(numHits)
    var bottom: Double = 0.0
    private var topValue: Double = 0.0
    lateinit var currentDocs: SortedNumericDocValues /*= null*/

    // current bounding box(es) for the bottom distance on the PQ.
    // these are pre-encoded with XYPoint's encoding and
    // used to exclude uncompetitive hits faster.
    var minX: Int = Int.MIN_VALUE
    var maxX: Int = Int.MAX_VALUE
    var minY: Int = Int.MIN_VALUE
    var maxY: Int = Int.MAX_VALUE

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
        if (bottom < Float.MAX_VALUE
            && (setBottomCounter < 1024 || (setBottomCounter and 0x3F) == 0x3F)
        ) {
            val rectangle: XYRectangle = XYRectangle.fromPointDistance(x.toFloat(), y.toFloat(), bottom.toFloat())
            // pre-encode our box to our integer encoding, so we don't have to decode
            // to double values for uncompetitive hits. This has some cost!
            this.minX = XYEncodingUtils.encode(rectangle.minX)
            this.maxX = XYEncodingUtils.encode(rectangle.maxX)
            this.minY = XYEncodingUtils.encode(rectangle.minY)
            this.maxY = XYEncodingUtils.encode(rectangle.maxY)
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
            val xBits = (encoded shr 32).toInt()
            if (xBits !in minX..maxX) {
                continue
            }
            val yBits = (encoded and 0xFFFFFFFFL).toInt()
            if (yBits !in minY..maxY) {
                continue
            }

            // only compute actual distance if its inside "competitive bounding box"
            val docX = XYEncodingUtils.decode(xBits).toDouble()
            val docY = XYEncodingUtils.decode(yBits).toDouble()
            val diffX = x - docX
            val diffY = y - docY
            val distance = sqrt(diffX * diffX + diffY * diffY)
            cmp = max(cmp, Double.compare(bottom, distance))
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
            XYDocValuesField.checkCompatible(info)
        }
        currentDocs = DocValues.getSortedNumeric(reader, field)
        valuesDocID = -1
        return this
    }

    override fun value(slot: Int): Double {
        return values[slot]
    }

    @Throws(IOException::class)
    override fun compareTop(doc: Int): Int {
        return Double.compare(topValue, sortKey(doc))
    }

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
                val docX = XYEncodingUtils.decode((encoded shr 32).toInt()).toDouble()
                val docY = XYEncodingUtils.decode((encoded and 0xFFFFFFFFL).toInt()).toDouble()
                val diffX = x - docX
                val diffY = y - docY
                val distance = sqrt(diffX * diffX + diffY * diffY)
                minValue = min(minValue, distance)
            }
        }
        return minValue
    }
}
