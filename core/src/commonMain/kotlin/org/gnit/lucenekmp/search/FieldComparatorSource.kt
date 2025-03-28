package org.gnit.lucenekmp.search

/**
 * Provides a [FieldComparator] for custom field sorting.
 *
 * @lucene.experimental
 */
abstract class FieldComparatorSource {
    /**
     * Creates a comparator for the field in the given index.
     *
     * @param fieldname Name of the field to create comparator for.
     * @return FieldComparator.
     */
    abstract fun newComparator(
        fieldname: String, numHits: Int, pruning: Pruning, reversed: Boolean
    ): FieldComparator<*>
}
