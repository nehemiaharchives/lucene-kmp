package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.DoubleValues
import org.gnit.lucenekmp.search.DoubleValuesSource
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.util.BytesRef

/**
 * A [DoubleValuesSource] instance which can be used to read the values of a feature from a
 * [FeatureField] for documents.
 */
internal class FeatureDoubleValuesSource(field: String, featureName: String) : DoubleValuesSource() {
    private val featureName: BytesRef = BytesRef(requireNotNull(featureName))
    private val field: String = requireNotNull(field)

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun getValues(ctx: LeafReaderContext, scores: DoubleValues?): DoubleValues {
        val terms: Terms = Terms.getTerms(ctx.reader(), field)
        val termsEnum: TermsEnum = terms.iterator()
        if (termsEnum.seekExact(featureName)) {
            val currentReaderPostingsValues: PostingsEnum = termsEnum.postings(null, PostingsEnum.FREQS.toInt())!!
            return FeatureDoubleValues(currentReaderPostingsValues)
        } else {
            return DoubleValues.EMPTY
        }
    }

    override fun needsScores(): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun rewrite(reader: IndexSearcher): DoubleValuesSource {
        return this
    }

    override fun hashCode(): Int {
        return Objects.hash(field, featureName)
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (obj::class != this::class) {
            return false
        }
        val other = obj as FeatureDoubleValuesSource
        return field == other.field && featureName == other.featureName
    }

    override fun toString(): String {
        return "FeatureDoubleValuesSource(" + field + ", " + featureName.utf8ToString() + ")"
    }

    internal class FeatureDoubleValues(private val currentReaderPostingsValues: PostingsEnum) : DoubleValues() {

        @Throws(IOException::class)
        override fun doubleValue(): Double {
            return FeatureField.decodeFeatureValue(currentReaderPostingsValues.freq().toFloat()).toDouble()
        }

        @Throws(IOException::class)
        override fun advanceExact(doc: Int): Boolean {
            if (doc >= currentReaderPostingsValues.docID()
                && (currentReaderPostingsValues.docID() == doc
                        || currentReaderPostingsValues.advance(doc) == doc)
            ) {
                return true
            } else {
                return false
            }
        }
    }
}
