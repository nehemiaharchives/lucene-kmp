package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexReaderContext
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.search.MultiTermQuery.RewriteMethod
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef


abstract class TermCollectingRewrite<B> : RewriteMethod() {
    protected abstract val topLevelBuilder: B

    /** Finalize the creation of the query from the builder.  */
    protected abstract fun build(builder: B): Query

    /** Add a MultiTermQuery term to the top-level query builder.  */
    @Throws(IOException::class)
    protected fun addClause(topLevel: B, term: Term, docCount: Int, boost: Float) {
        addClause(topLevel, term, docCount, boost, null)
    }

    @Throws(IOException::class)
    protected abstract fun addClause(
        topLevel: B, term: Term, docCount: Int, boost: Float, states: TermStates?
    )

    @Throws(IOException::class)
    fun collectTerms(reader: IndexReader, query: MultiTermQuery, collector: TermCollector) {
        val topReaderContext: IndexReaderContext = reader.context
        for (context in topReaderContext.leaves()) {
            val terms: Terms? = context.reader().terms(query.field)
            if (terms == null) {
                // field does not exist
                continue
            }

            val termsEnum: TermsEnum = checkNotNull(getTermsEnum(query, terms, collector.attributes))
            if (termsEnum === TermsEnum.EMPTY) continue

            collector.setReaderContext(topReaderContext, context)
            collector.setNextEnum(termsEnum)
            var bytes: BytesRef?
            while ((termsEnum.next().also { bytes = it }) != null) {
                if (!collector.collect(bytes!!)) return  // interrupt whole term collection, so also don't iterate other subReaders
            }
        }
    }

    abstract class TermCollector {
        protected var readerContext: LeafReaderContext? = null
        protected var topReaderContext: IndexReaderContext? = null

        fun setReaderContext(
            topReaderContext: IndexReaderContext, readerContext: LeafReaderContext
        ) {
            this.readerContext = readerContext
            this.topReaderContext = topReaderContext
        }

        /** attributes used for communication with the enum  */
        val attributes: AttributeSource = AttributeSource()

        /** return false to stop collecting  */
        @Throws(IOException::class)
        abstract fun collect(bytes: BytesRef): Boolean

        /** the next segment's [TermsEnum] that is used to collect terms  */
        @Throws(IOException::class)
        abstract fun setNextEnum(termsEnum: TermsEnum)
    }
}
