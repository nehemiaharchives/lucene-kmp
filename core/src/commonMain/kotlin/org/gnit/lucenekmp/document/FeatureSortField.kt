package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.FieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.search.SimpleFieldComparator
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.util.BytesRef


/** Sorts using the value of a specified feature name from a [FeatureField].  */
internal class FeatureSortField(field: String, featureName: String) : SortField(requireNotNull(field), Type.CUSTOM, true) {
    private val featureName: String = requireNotNull(featureName)

    override fun getComparator(numHits: Int, pruning: Pruning): FieldComparator<*> {
        return FeatureComparator(numHits, field!!, featureName)
    }

    override var missingValue: Any?
        get() {
            // not in java lucene
            throw IllegalArgumentException("Missing value not supported for FeatureSortField")
        }
        set(missingValue) {
            throw IllegalArgumentException("Missing value not supported for FeatureSortField")
        }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + featureName.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (this::class != obj!!::class) return false
        val other = obj as FeatureSortField
        return featureName == other.featureName
    }

    override fun toString(): String {
        val builder: StringBuilder = StringBuilder()
        builder.append("<feature:")
        builder.append('"')
        builder.append(field)
        builder.append('"')
        builder.append(" featureName=")
        builder.append(featureName)
        builder.append('>')
        return builder.toString()
    }

    /** Parses a feature field's values as float and sorts by descending value  */
    internal inner class FeatureComparator(numHits: Int, private val field: String, featureName: String) : SimpleFieldComparator<Float>() {
        private val featureName: BytesRef = BytesRef(featureName)
        private val values: FloatArray = FloatArray(numHits)
        private var bottom = 0f
        private var topValue = 0f
        private var currentReaderPostingsValues: PostingsEnum? = null

        @Throws(IOException::class)
        override fun doSetNextReader(context: LeafReaderContext) {
            val terms: Terms = Terms.getTerms(context.reader(), field)
            val termsEnum: TermsEnum = terms.iterator()
            if (termsEnum.seekExact(featureName)) {
                currentReaderPostingsValues =
                    termsEnum.postings(currentReaderPostingsValues, PostingsEnum.FREQS.toInt())
            } else {
                currentReaderPostingsValues = null
            }
        }

        @Throws(IOException::class)
        private fun getValueForDoc(doc: Int): Float {
            if (currentReaderPostingsValues != null && doc >= currentReaderPostingsValues!!.docID() && (currentReaderPostingsValues!!.docID() == doc || currentReaderPostingsValues!!.advance(doc) == doc)) {
                return FeatureField.decodeFeatureValue(currentReaderPostingsValues!!.freq().toFloat())
            } else {
                return 0.0f
            }
        }

        override fun compare(slot1: Int, slot2: Int): Int {
            return Float.compare(values[slot1], values[slot2])
        }

        @Throws(IOException::class)
        override fun compareBottom(doc: Int): Int {
            return Float.compare(bottom, getValueForDoc(doc))
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            values[slot] = getValueForDoc(doc)
        }

        override fun setBottom(bottom: Int) {
            this.bottom = values[bottom]
        }

        override fun setTopValue(value: Float) {
            topValue = value
        }

        override fun value(slot: Int): Float {
            return values[slot]
        }

        @Throws(IOException::class)
        override fun compareTop(doc: Int): Int {
            return Float.compare(topValue, getValueForDoc(doc))
        }
    }
}
