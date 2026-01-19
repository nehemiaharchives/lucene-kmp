package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.Objects

/**
 * A [DoubleValuesSource] which computes the vector similarity scores between the query vector
 * and the [org.apache.lucene.document.KnnFloatVectorField] for documents.
 */
internal class FloatVectorSimilarityValuesSource(
    private val queryVector: FloatArray,
    fieldName: String
) : VectorSimilarityValuesSource(fieldName) {
    @Throws(IOException::class)
    override fun getScorer(ctx: LeafReaderContext): VectorScorer? {
        val vectorValues: FloatVectorValues? =
            ctx.reader().getFloatVectorValues(fieldName)
        if (vectorValues == null) {
            FloatVectorValues.checkField(ctx.reader(), fieldName)
            return null
        }
        return vectorValues.scorer(queryVector)
    }

    override fun hashCode(): Int {
        return Objects.hash(fieldName, queryVector.contentHashCode())
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null || this::class != obj::class) return false
        val other = obj as FloatVectorSimilarityValuesSource
        return fieldName == other.fieldName
                && queryVector.contentEquals(other.queryVector)
    }

    override fun toString(): String {
        return ("FloatVectorSimilarityValuesSource(fieldName="
                + fieldName
                + " queryVector="
                + queryVector.contentToString() + ")")
    }
}
