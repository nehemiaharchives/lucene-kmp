package org.gnit.lucenekmp.analysis.morph

/** Token type reflecting the original source of this token */
enum class TokenType {
    /** Known words from the system dictionary. */
    KNOWN,
    /** Unknown words (heuristically segmented). */
    UNKNOWN,
    /** Known words from the user dictionary. */
    USER
}
