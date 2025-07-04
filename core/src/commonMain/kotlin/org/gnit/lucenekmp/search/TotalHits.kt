package org.gnit.lucenekmp.search

/**
 * Description of the total number of hits of a query. The total hit count can't generally be
 * computed accurately without visiting all matches, which is costly for queries that match lots of
 * documents. Given that it is often enough to have a lower bounds of the number of hits, such as
 * "there are more than 1000 hits", Lucene has options to stop counting as soon as a threshold has
 * been reached in order to improve query times.
 *
 * @param value The value of the total hit count. Must be interpreted in the context of [     ][.relation].
 * @param relation Whether [.value] is the exact hit count, in which case [.relation] is
 * equal to [Relation.EQUAL_TO], or a lower bound of the total hit count, in which case
 * [.relation] is equal to [Relation.GREATER_THAN_OR_EQUAL_TO].
 */
data class TotalHits(val value: Long, val relation: Relation) {
    /** How the [TotalHits.value] should be interpreted.  */
    enum class Relation {
        /** The total hit count is equal to [TotalHits.value].  */
        EQUAL_TO,

        /** The total hit count is greater than or equal to [TotalHits.value].  */
        GREATER_THAN_OR_EQUAL_TO
    }

    override fun toString(): String {
        return value.toString() + (if (relation == Relation.EQUAL_TO) "" else "+") + " hits"
    }

    /** Sole constructor.  */
    init {
        require(value >= 0) { "value must be >= 0, got $value" }
        requireNotNull<Relation>(relation)
    }
}
