package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.search.CombinedFieldQuery.FieldAndWeight
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.SmallFloat
import kotlin.math.roundToInt

/**
 * Scorer that sums document's norms from multiple fields.
 *
 * <p>For all fields, norms must be encoded using [SmallFloat.intToByte4]. This scorer also
 * requires that either all fields or no fields have norms enabled. Having only some fields with
 * norms enabled can result in errors or undefined behavior.
 */
class MultiNormsLeafSimScorer
/** Sole constructor: Score documents of `reader` with `scorer`. */
@Throws(IOException::class)
constructor(
    private val scorer: SimScorer,
    reader: LeafReader,
    normFields: Collection<FieldAndWeight>,
    needsScores: Boolean,
) {
    private val norms: NumericDocValues?

    init {
        if (needsScores) {
            val normsList = ArrayList<NumericDocValues>()
            val weightList = ArrayList<Float>()
            val duplicateCheckingSet = HashSet<String>()
            for (field in normFields) {
                assert(duplicateCheckingSet.add(field.field)) {
                    "There is a duplicated field [${field.field}] used to construct MultiNormsLeafSimScorer"
                }

                val norms = reader.getNormValues(field.field)
                if (norms != null) {
                    normsList.add(norms)
                    weightList.add(field.weight)
                }
            }

            norms = if (normsList.isEmpty()) {
                null
            } else {
                val normsArr = normsList.toTypedArray()
                val weightArr = FloatArray(normsList.size)
                for (i in weightList.indices) {
                    weightArr[i] = weightList[i]
                }
                MultiFieldNormValues(normsArr, weightArr)
            }
        } else {
            norms = null
        }
    }

    fun getSimScorer(): SimScorer {
        return scorer
    }

    @Throws(IOException::class)
    private fun getNormValue(doc: Int): Long {
        if (norms != null) {
            val found = norms.advanceExact(doc)
            assert(found)
            return norms.longValue()
        } else {
            return 1L // default norm
        }
    }

    /**
     * Score the provided document assuming the given term document frequency. This method must be
     * called on non-decreasing sequences of doc ids.
     *
     * @see SimScorer.score
     */
    @Throws(IOException::class)
    fun score(doc: Int, freq: Float): Float {
        return scorer.score(freq, getNormValue(doc))
    }

    /**
     * Explain the score for the provided document assuming the given term document frequency. This
     * method must be called on non-decreasing sequences of doc ids.
     *
     * @see SimScorer.explain
     */
    @Throws(IOException::class)
    fun explain(doc: Int, freqExpl: Explanation): Explanation {
        return scorer.explain(freqExpl, getNormValue(doc))
    }

    private class MultiFieldNormValues(
        private val normsArr: Array<NumericDocValues>,
        private val weightArr: FloatArray,
    ) : NumericDocValues() {
        private var current = 0L
        private var docID = -1

        override fun longValue(): Long {
            return current
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            docID = target
            var normValue = 0f
            var found = false
            for (i in normsArr.indices) {
                if (normsArr[i].advanceExact(target)) {
                    normValue += weightArr[i] * LENGTH_TABLE[Byte.toUnsignedInt(normsArr[i].longValue().toByte())]
                    found = true
                }
            }
            current = SmallFloat.intToByte4(normValue.roundToInt()).toLong()
            return found
        }

        override fun docID(): Int {
            return docID
        }

        override fun nextDoc(): Int {
            throw UnsupportedOperationException()
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun cost(): Long {
            throw UnsupportedOperationException()
        }
    }

    companion object {
        /** Cache of decoded norms. */
        private val LENGTH_TABLE = FloatArray(256)

        init {
            for (i in 0..255) {
                LENGTH_TABLE[i] = SmallFloat.byte4ToInt(i.toByte()).toFloat()
            }
        }
    }
}
