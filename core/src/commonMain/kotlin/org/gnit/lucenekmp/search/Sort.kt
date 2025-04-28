package org.gnit.lucenekmp.search

import kotlinx.io.IOException


/**
 * Encapsulates sort criteria for returned hits.
 *
 *
 * A [Sort] can be created with an empty constructor, yielding an object that will instruct
 * searches to return their hits sorted by relevance; or it can be created with one or more [ ]s.
 *
 * @see SortField
 *
 * @since lucene 1.4
 */
class Sort(vararg fields: SortField) {
    // internal representation of the sort criteria
    private val fields: Array<SortField>

    /**
     * Sorts by computed relevance. This is the same sort criteria as calling [ ][IndexSearcher.search]without a sort criteria, only with
     * slightly more overhead.
     */
    constructor() : this(SortField.FIELD_SCORE)

    /**
     * Sets the sort to the given criteria in succession: the first SortField is checked first, but if
     * it produces a tie, then the second SortField is used to break the tie, etc. Finally, if there
     * is still a tie after all SortFields are checked, the internal Lucene docid is used to break it.
     */
    init {
        require(fields.isNotEmpty()) { "There must be at least 1 sort field" }
        this.fields = arrayOf(*fields)
    }

    val sort: Array<SortField>
        /**
         * Representation of the sort criteria.
         *
         * @return Array of SortField objects used in this sort criteria
         */
        get() = fields

    /**
     * Rewrites the SortFields in this Sort, returning a new Sort if any of the fields changes during
     * their rewriting.
     *
     * @param searcher IndexSearcher to use in the rewriting
     * @return `this` if the Sort/Fields have not changed, or a new Sort if there is a change
     * @throws IOException Can be thrown by the rewriting
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun rewrite(searcher: IndexSearcher?): Sort {
        var changed = false
        val rewrittenSortFields: Array<SortField?> = kotlin.arrayOfNulls<SortField>(fields.size)
        for (i in fields.indices) {
            rewrittenSortFields[i] = fields[i].rewrite(searcher!!)
            if (fields[i] !== rewrittenSortFields[i]) {
                changed = true
            }
        }

        return if (changed) Sort(*rewrittenSortFields as Array<SortField>) else this
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        for (i in fields.indices) {
            buffer.append(fields[i].toString())
            if ((i + 1) < fields.size) buffer.append(',')
        }

        return buffer.toString()
    }

    /** Returns true if `o` is equal to this.  */
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Sort) return false
        val other = o
        return this.fields.contentEquals(other.fields)
    }

    /** Returns a hash code value for this object.  */
    override fun hashCode(): Int {
        return 0x45aaf665 + fields.contentHashCode()
    }

    /** Returns true if the relevance score is needed to sort documents.  */
    fun needsScores(): Boolean {
        for (sortField in fields) {
            if (sortField.needsScores()) {
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * Represents sorting by computed relevance. Using this sort criteria returns the same results as
         * calling [IndexSearcher#search()][IndexSearcher.search]without a sort criteria,
         * only with slightly more overhead.
         */
        val RELEVANCE: Sort = Sort()

        /** Represents sorting by index order.  */
        val INDEXORDER: Sort = Sort(SortField.FIELD_DOC)
    }
}
