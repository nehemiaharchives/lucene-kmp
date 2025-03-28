package org.gnit.lucenekmp.index


/**
 * Controls how much information is stored in the postings lists.
 *
 * @lucene.experimental
 */
enum class IndexOptions {
    // NOTE: order is important here; enum ordinals can be used to compare options.
    /** Not indexed  */
    NONE,

    /**
     * Only documents are indexed: term frequencies and positions are omitted. Phrase and other
     * positional queries on the field will throw an exception, and scoring will behave as if any term
     * in the document appears only once.
     */
    DOCS,

    /**
     * Only documents and term frequencies are indexed: positions are omitted. This enables normal
     * scoring, except [PhraseQuery] and other positional queries will throw an exception.
     */
    DOCS_AND_FREQS,

    /**
     * Indexes documents, frequencies and positions. This is a typical default for full-text search:
     * full scoring is enabled and positional queries are supported.
     */
    DOCS_AND_FREQS_AND_POSITIONS,

    /**
     * Indexes documents, frequencies, positions and offsets. Character offsets are encoded alongside
     * the positions.
     */
    DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS,
}
