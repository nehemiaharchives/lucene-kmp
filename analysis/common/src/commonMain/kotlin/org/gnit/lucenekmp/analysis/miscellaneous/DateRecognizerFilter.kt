package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** Filters all tokens that cannot be recognized as a date. */
class DateRecognizerFilter : FilteringTokenFilter {
    companion object {
        const val DATE_TYPE: String = "date"
    }

    private val termAtt = addAttribute(CharTermAttribute::class)
    private val dateRecognizer: DateRecognizer

    /**
     * Uses the default English date recognizer.
     */
    constructor(input: TokenStream) : this(input, null)

    constructor(input: TokenStream, dateRecognizer: DateRecognizer?) : super(input) {
        this.dateRecognizer = dateRecognizer ?: EnglishDefaultDateRecognizer
    }

    override fun accept(): Boolean {
        return dateRecognizer.isDate(termAtt.toString())
    }
}
