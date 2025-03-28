package org.gnit.lucenekmp.index

/** Options for skip indexes on doc values.  */
enum class DocValuesSkipIndexType {
    /** No skip index should be created.  */
    NONE {
        override fun isCompatibleWith(dvType: DocValuesType?): Boolean {
            return true
        }
    },

    /**
     * Record range of values. This is suitable for [DocValuesType.NUMERIC], [ ][DocValuesType.SORTED_NUMERIC], [DocValuesType.SORTED] and [ ][DocValuesType.SORTED_SET] doc values, and will record the min/max values per range of doc IDs.
     */
    RANGE {
        override fun isCompatibleWith(dvType: DocValuesType?): Boolean {
            return dvType === DocValuesType.NUMERIC || dvType === DocValuesType.SORTED_NUMERIC || dvType === DocValuesType.SORTED || dvType === DocValuesType.SORTED_SET
        }
    }; // TODO: add support for pre-aggregated integer/float/double

    abstract fun isCompatibleWith(dvType: DocValuesType?): Boolean
}
