package org.gnit.lucenekmp.document

/** Describes how an [IndexableField] should be inverted for indexing terms and postings.  */
enum class InvertableType {
    /**
     * The field should be treated as a single value whose binary content is returned by [ ][IndexableField.binaryValue]. The term frequency is assumed to be one. If you need to index
     * multiple values, you should pass multiple [IndexableField] instances to the [ ]. If the same value is provided multiple times, the term frequency will be equal to
     * the number of times that this value occurred in the same document.
     */
    BINARY,

    /**
     * The field should be inverted through its [ ][IndexableField.tokenStream].
     */
    TOKEN_STREAM
}
