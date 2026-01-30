package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.index.FilterLeafReader.FilterFields
import org.gnit.lucenekmp.index.FilterLeafReader.FilterTerms
import org.gnit.lucenekmp.index.FilterLeafReader.FilterTermsEnum

/**
 * A [Fields] implementation that merges multiple Fields into one, and maps around deleted
 * documents. This is used for merging.
 *
 * @lucene.internal
 */
class MappedMultiFields(val mergeState: MergeState, multiFields: MultiFields) : FilterFields(multiFields) {

    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        val terms: MultiTerms? = `in`.terms(field) as MultiTerms?
        return if (terms == null) {
            null
        } else {
            MappedMultiTerms(field, mergeState, terms)
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

        override val sumTotalTermFreq: Long
            get() {
                throw UnsupportedOperationException()
            }

        override val sumDocFreq: Long
            get() {
                throw UnsupportedOperationException()
            }

        override val docCount: Int
            get() {
                throw UnsupportedOperationException()
            }
    }

    private class MappedMultiTermsEnum(val field: String, val mergeState: MergeState, multiTermsEnum: MultiTermsEnum) :
        FilterTermsEnum(multiTermsEnum) {

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
            val mappingDocsAndPositionsEnum: MappingMultiPostingsEnum = if (reuse is MappingMultiPostingsEnum) {
                if (reuse.field.equals(this.field)) {
                    reuse
                } else {
                    MappingMultiPostingsEnum(field, mergeState)
                }
            } else {
                MappingMultiPostingsEnum(field, mergeState)
            }

            val docsAndPositionsEnum: MultiPostingsEnum =
                `in`.postings(mappingDocsAndPositionsEnum.multiDocsAndPositionsEnum, flags) as MultiPostingsEnum
            mappingDocsAndPositionsEnum.reset(docsAndPositionsEnum)
            return mappingDocsAndPositionsEnum
        }
    }
}
