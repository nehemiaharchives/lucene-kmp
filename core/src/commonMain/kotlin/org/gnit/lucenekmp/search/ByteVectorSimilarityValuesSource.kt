package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.Objects

/**
 * A [DoubleValuesSource] which computes the vector similarity scores between the query vector
 * and the [org.apache.lucene.document.KnnByteVectorField] for documents.
 */
internal class ByteVectorSimilarityValuesSource(
    private val queryVector: ByteArray,
    fieldName: String
) : VectorSimilarityValuesSource(fieldName) {
    @Throws(IOException::class)
    override fun getScorer(ctx: LeafReaderContext): VectorScorer? {
        val vectorValues: ByteVectorValues? = ctx.reader().getByteVectorValues(fieldName)
        if (vectorValues == null) {
            ByteVectorValues.checkField(ctx.reader(), fieldName)
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
        val other = obj as ByteVectorSimilarityValuesSource
        return fieldName == other.fieldName
                && queryVector.contentEquals(other.queryVector)
    }

    override fun toString(): String {
        return ("ByteVectorSimilarityValuesSource(fieldName="
                + fieldName
                + " queryVector="
                + queryVector.contentToString() + ")")
    }
}
