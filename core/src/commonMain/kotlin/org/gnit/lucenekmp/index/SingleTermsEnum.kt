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
    private val singleRef: BytesRef?

    /**
     * Creates a new `SingleTermsEnum`.
     *
     *
     * After calling the constructor the enumeration is already pointing to the term, if it exists.
     */
    init {
        singleRef = termText
        setInitialSeekTerm(termText)
    }

    protected override fun accept(term: BytesRef?): AcceptStatus? {
        return if (term!!.equals(singleRef)) AcceptStatus.YES else AcceptStatus.END
    }
}
