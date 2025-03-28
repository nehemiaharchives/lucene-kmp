package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.Attribute

/**
 * Sets the custom term frequency of a term within one document. If this attribute is present in
 * your analysis chain for a given field, that field must be indexed with [ ][IndexOptions.DOCS_AND_FREQS].
 */
interface TermFrequencyAttribute : Attribute {
    /** Returns the custom term frequency.  */
    /** Set the custom term frequency of the current term within one document.  */
    var termFrequency: Int
}
