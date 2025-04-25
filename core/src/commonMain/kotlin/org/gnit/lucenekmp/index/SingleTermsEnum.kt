package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.util.BytesRef

/**
 * Subclass of FilteredTermsEnum for enumerating a single term.
 *
 *
 * For example, this can be used by [MultiTermQuery]s that need only visit one term, but
 * want to preserve MultiTermQuery semantics such as [MultiTermQuery.getRewriteMethod].
 */
class SingleTermsEnum(tenum: TermsEnum, termText: BytesRef?) : FilteredTermsEnum(tenum) {
    private val singleRef: BytesRef? = termText

    /**
     * Creates a new `SingleTermsEnum`.
     *
     *
     * After calling the constructor the enumeration is already pointing to the term, if it exists.
     */
    init {
        setInitialSeekTerm(termText)
    }

    override fun accept(term: BytesRef): AcceptStatus {
        return if (term == singleRef) AcceptStatus.YES else AcceptStatus.END
    }
}
