package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.tests.util.LuceneTestCase

abstract class BaseVectorizationTestCase : LuceneTestCase() {
    protected fun beforeClass() {
        assumeTrue(
            "Test only works when JDK's vector incubator module is enabled.",
            PANAMA_PROVIDER::class != LUCENE_PROVIDER::class
        )
    }

    companion object {
        protected val LUCENE_PROVIDER: VectorizationProvider = defaultProvider()
        protected val PANAMA_PROVIDER: VectorizationProvider = maybePanamaProvider()

        fun defaultProvider(): VectorizationProvider {
            return DefaultVectorizationProvider()
        }

        fun maybePanamaProvider(): VectorizationProvider {
            return VectorizationProvider.getInstance()
        }
    }
}
