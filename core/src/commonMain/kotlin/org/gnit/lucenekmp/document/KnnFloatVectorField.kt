package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.KnnFloatVectorQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.VectorUtil

/**
 * A field that contains a single floating-point numeric vector (or none) for each document. Vectors
 * are dense - that is, every dimension of a vector contains an explicit value, stored packed into
 * an array (of type float[]) whose length is the vector dimension. Values can be retrieved using
 * [FloatVectorValues], which is a forward-only docID-based iterator and also offers
 * random-access by dense ordinal (not docId). [VectorSimilarityFunction] may be used to
 * compare vectors at query time (for example as part of result ranking). A [ ] may be associated with a search similarity function defining the metric used
 * for nearest-neighbor search among vectors of that field.
 *
 * @lucene.experimental
 */
class KnnFloatVectorField : Field {
    /**
     * Creates a numeric vector field. Fields are single-valued: each document has either one value or
     * no value. Vectors of a single field share the same dimension and similarity function. Note that
     * some vector similarities (like [VectorSimilarityFunction.DOT_PRODUCT]) require values to
     * be unit-length, which can be enforced using [VectorUtil.l2normalize].
     *
     * @param name field name
     * @param vector value
     * @param similarityFunction a function defining vector proximity.
     * @throws IllegalArgumentException if any parameter is null, or the vector is empty or has
     * dimension &gt; 1024.
     */
    constructor(name: String, vector: FloatArray, similarityFunction: VectorSimilarityFunction) : super(
        name,
        createType(vector, similarityFunction)
    ) {
        fieldsData = VectorUtil.checkFinite(vector) // null check done above
    }

    /**
     * Creates a numeric vector field with the default EUCLIDEAN_HNSW (L2) similarity. Fields are
     * single-valued: each document has either one value or no value. Vectors of a single field share
     * the same dimension and similarity function.
     *
     * @param name field name
     * @param vector value
     * @throws IllegalArgumentException if any parameter is null, or the vector is empty or has
     * dimension &gt; 1024.
     */
    constructor(name: String, vector: FloatArray) : this(name, vector, VectorSimilarityFunction.EUCLIDEAN)

    /**
     * Creates a numeric vector field. Fields are single-valued: each document has either one value or
     * no value. Vectors of a single field share the same dimension and similarity function.
     *
     * @param name field name
     * @param vector value
     * @param fieldType field type
     * @throws IllegalArgumentException if any parameter is null, or the vector is empty or has
     * dimension &gt; 1024.
     */
    constructor(name: String, vector: FloatArray, fieldType: FieldType) : super(name, fieldType) {
        require(fieldType.vectorEncoding() === VectorEncoding.FLOAT32) {
            ("Attempt to create a vector for field "
                    + name
                    + " using float[] but the field encoding is "
                    + fieldType.vectorEncoding())
        }

        require(vector.size == fieldType.vectorDimension()) { "The number of vector dimensions does not match the field type" }
        fieldsData = VectorUtil.checkFinite(vector)
    }

    /** Return the vector value of this field  */
    fun vectorValue(): FloatArray {
        return fieldsData as FloatArray
    }

    /**
     * Set the vector value of this field
     *
     * @param value the value to set; must not be null, and length must match the field type
     */
    fun setVectorValue(value: FloatArray) {
        requireNotNull(value) { "value must not be null" }
        require(value.size == type.vectorDimension()) { "value length " + value.size + " must match field dimension " + type.vectorDimension() }
        fieldsData = value
    }

    companion object {
        private fun createType(v: FloatArray, similarityFunction: VectorSimilarityFunction): FieldType {
            requireNotNull(v) { "vector value must not be null" }
            val dimension = v.size
            require(dimension != 0) { "cannot index an empty vector" }
            requireNotNull(similarityFunction) { "similarity function must not be null" }
            val type = FieldType()
            type.setVectorAttributes(dimension, VectorEncoding.FLOAT32, similarityFunction)
            type.freeze()
            return type
        }

        /**
         * A convenience method for creating a vector field type.
         *
         * @param dimension dimension of vectors
         * @param similarityFunction a function defining vector proximity.
         * @throws IllegalArgumentException if any parameter is null, or has dimension &gt; 1024.
         */
        fun createFieldType(
            dimension: Int, similarityFunction: VectorSimilarityFunction
        ): FieldType {
            val type = FieldType()
            type.setVectorAttributes(dimension, VectorEncoding.FLOAT32, similarityFunction)
            type.freeze()
            return type
        }

        /**
         * Create a new vector query for the provided field targeting the float vector
         *
         * @param field The field to query
         * @param queryVector The float vector target
         * @param k The number of nearest neighbors to gather
         * @return A new vector query
         */
        fun newVectorQuery(field: String, queryVector: FloatArray, k: Int): Query {
            return KnnFloatVectorQuery(field, queryVector, k)
        }
    }
}
