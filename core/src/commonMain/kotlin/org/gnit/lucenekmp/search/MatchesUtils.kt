package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.IOSupplier


/**
 * Contains static functions that aid the implementation of [Matches] and [ ] interfaces.
 */
object MatchesUtils {
    /**
     * Indicates a match with no term positions, for example on a Point or DocValues field, or a field
     * indexed as docs and freqs only
     */
    val MATCH_WITH_NO_TERMS: Matches = object : Matches {
        override fun getMatches(field: String): MatchesIterator? {
            return null
        }

        override val subMatches: MutableCollection<Matches>
            get() = mutableListOf<Matches>()

        override fun iterator(): MutableIterator<String> {
            return /*java.util.Collections.emptyIterator<String>()*/ mutableListOf<String>().iterator()
        }
    }

    /** Amalgamate a collection of [Matches] into a single object  */
    fun fromSubMatches(subMatches: MutableList<Matches>?): Matches? {
        if (subMatches == null || subMatches.isEmpty()) {
            return null
        }
        val sm: MutableList<Matches> = subMatches.filter { m: Matches -> m != MATCH_WITH_NO_TERMS }.toMutableList()
        if (sm.isEmpty()) {
            return MATCH_WITH_NO_TERMS
        }
        if (sm.size == 1) {
            return sm.get(0)
        }

        return object : Matches {
            @Throws(IOException::class)
            override fun getMatches(field: String): MatchesIterator? {
                val subIterators: MutableList<MatchesIterator> = ArrayList<MatchesIterator>(sm.size)
                for (m in sm) {
                    val it: MatchesIterator? = m.getMatches(field)
                    if (it != null) {
                        subIterators.add(it)
                    }
                }
                return DisjunctionMatchesIterator.fromSubIterators(subIterators)
            }

            override fun iterator(): Iterator<String> {
                // for each sub-match, iterate its fields (it's an Iterable of the fields), and return the
                // distinct set
                return sm
                    .flatMap{ it }
                    .distinct()
                    .iterator()
            }

            override val subMatches: MutableCollection<Matches>
                get() = subMatches
        }
    }

    /** Create a Matches for a single field  */
    @Throws(IOException::class)
    fun forField(field: String, mis: IOSupplier<MatchesIterator>?): Matches? {
        // The indirection here, using a Supplier object rather than a MatchesIterator
        // directly, is to allow for multiple calls to Matches.getMatches() to return
        // new iterators.  We still need to call MatchesIteratorSupplier.get() eagerly
        // to work out if we have a hit or not.

        val mi: MatchesIterator? = mis!!.get()
        if (mi == null) {
            return null
        }
        return object : Matches {
            var cached: Boolean = true

            @Throws(IOException::class)
            override fun getMatches(f: String): MatchesIterator? {
                if (field == f == false) {
                    return null
                }
                if (cached == false) {
                    return mis.get()
                }
                cached = false
                return mi
            }

            override fun iterator(): MutableIterator<String> {
                return mutableSetOf<String>(field).iterator()
            }

            override val subMatches: MutableCollection<Matches>
                get() = mutableListOf<Matches>()
        }
    }

    /** Create a MatchesIterator that iterates in order over all matches in a set of subiterators  */
    @Throws(IOException::class)
    fun disjunction(subMatches: MutableList<MatchesIterator>): MatchesIterator? {
        return DisjunctionMatchesIterator.fromSubIterators(subMatches)
    }

    /**
     * Create a MatchesIterator that is a disjunction over a list of terms extracted from a [ ].
     *
     *
     * Only terms that have at least one match in the given document will be included
     */
    @Throws(IOException::class)
    fun disjunction(
        context: LeafReaderContext, doc: Int, query: Query, field: String, terms: BytesRefIterator
    ): MatchesIterator? {
        return DisjunctionMatchesIterator.fromTermsEnum(context, doc, query, field, terms)
    }
}
