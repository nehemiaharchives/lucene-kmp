package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.FilterLeafReader.FilterFields
import org.gnit.lucenekmp.index.FilterLeafReader.FilterTerms
import org.gnit.lucenekmp.index.FilterLeafReader.FilterTermsEnum

/**
 * A [Fields] implementation that merges multiple Fields into one, and maps around deleted
 * documents. This is used for merging.
 *
 * @lucene.internal
 */
class MappedMultiFields(mergeState: MergeState, multiFields: MultiFields) : FilterFields(multiFields) {
    val mergeState: MergeState

    /**
     * Create a new MappedMultiFields for merging, based on the supplied mergestate and merged view of
     * terms.
     */
    init {
        this.mergeState = mergeState
    }

    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        val terms: MultiTerms? = `in`.terms(field) as MultiTerms
        if (terms == null) {
            return null
        } else {
            return MappedMultiTerms(field, mergeState, terms)
        }
    }

    private class MappedMultiTerms(val field: String?, val mergeState: MergeState, multiTerms: MultiTerms) :
        FilterTerms(multiTerms) {

        @Throws(IOException::class)
        override fun iterator(): TermsEnum {
            val iterator: TermsEnum = `in`.iterator()
            if (iterator === TermsEnum.EMPTY) {
                // LUCENE-6826
                return TermsEnum.EMPTY
            } else {
                return MappedMultiTermsEnum(field!!, mergeState, iterator as MultiTermsEnum)
            }
        }

        @Throws(IOException::class)
        override fun size(): Long {
            throw UnsupportedOperationException()
        }

        @get:Throws(IOException::class)
        val sumTotalTermFreq: Long
            get() {
                throw UnsupportedOperationException()
            }

        @get:Throws(IOException::class)
        val sumDocFreq: Long
            get() {
                throw UnsupportedOperationException()
            }

        @get:Throws(IOException::class)
        val docCount: Int
            get() {
                throw UnsupportedOperationException()
            }
    }

    private class MappedMultiTermsEnum(field: String, mergeState: MergeState, multiTermsEnum: MultiTermsEnum) :
        FilterTermsEnum(multiTermsEnum) {
        val mergeState: MergeState
        val field: String

        init {
            this.field = field
            this.mergeState = mergeState
        }

        @Throws(IOException::class)
        override fun docFreq(): Int {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun totalTermFreq(): Long {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
            val mappingDocsAndPositionsEnum: MappingMultiPostingsEnum
            if (reuse is MappingMultiPostingsEnum) {
                if (reuse.field.equals(this.field)) {
                    mappingDocsAndPositionsEnum = reuse
                } else {
                    mappingDocsAndPositionsEnum = MappingMultiPostingsEnum(field, mergeState)
                }
            } else {
                mappingDocsAndPositionsEnum = MappingMultiPostingsEnum(field, mergeState)
            }

            val docsAndPositionsEnum: MultiPostingsEnum =
                `in`.postings(mappingDocsAndPositionsEnum.multiDocsAndPositionsEnum, flags) as MultiPostingsEnum
            mappingDocsAndPositionsEnum.reset(docsAndPositionsEnum)
            return mappingDocsAndPositionsEnum
        }
    }
}
