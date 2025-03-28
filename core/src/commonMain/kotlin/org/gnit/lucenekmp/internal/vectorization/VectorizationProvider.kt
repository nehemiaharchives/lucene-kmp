package org.gnit.lucenekmp.internal.vectorization

abstract class VectorizationProvider {
    companion object {
        fun getInstance(): VectorizationProvider = DefaultVectorizationProvider()
    }

    /**
     * Returns a singleton (stateless) [VectorUtilSupport] to support SIMD usage in [ ].
     */
    abstract fun getVectorUtilSupport(): VectorUtilSupport
}
