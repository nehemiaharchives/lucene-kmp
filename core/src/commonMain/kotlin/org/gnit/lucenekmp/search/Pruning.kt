package org.gnit.lucenekmp.search

/** Controls [LeafFieldComparator] how to skip documents  */
enum class Pruning {
    /** Not allowed to skip documents.  */
    NONE,

    /**
     * Allowed to skip documents that compare strictly better than the top value, or strictly worse
     * than the bottom value.
     */
    GREATER_THAN,

    /**
     * Allowed to skip documents that compare better than the top value, or worse than or equal to the
     * bottom value.
     */
    GREATER_THAN_OR_EQUAL_TO
}
