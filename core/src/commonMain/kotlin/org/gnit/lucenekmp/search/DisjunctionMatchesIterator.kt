package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.PriorityQueue


/**
 * A [MatchesIterator] that combines matches from a set of sub-iterators
 *
 *
 * Matches are sorted by their start positions, and then by their end positions, so that prefixes
 * sort first. Matches may overlap, or be duplicated if they appear in more than one of the
 * sub-iterators.
 */
internal class DisjunctionMatchesIterator private constructor(matches: MutableList<MatchesIterator>) : MatchesIterator {
    // MatchesIterator over a set of terms that only loads the first matching term at construction,
    // waiting until the iterator is actually used before it loads all other matching terms.
    private class TermsEnumDisjunctionMatchesIterator(
        private val first: MatchesIterator,
        terms: BytesRefIterator,
        te: TermsEnum,
        doc: Int,
        query: Query
    ) : MatchesIterator {
        private val terms: BytesRefIterator
        private val te: TermsEnum
        private val doc: Int
        override var query: Query
            get(): Query {
                return it!!.query
            }

        private var it: MatchesIterator? = null

        init {
            this.terms = terms
            this.te = te
            this.doc = doc
            this.query = query
        }

        @Throws(IOException::class)
        fun init() {
            val mis: MutableList<MatchesIterator> = ArrayList<MatchesIterator>()
            mis.add(first)
            var reuse: PostingsEnum? = null
            var term: BytesRef? = terms.next()
            while (term != null) {
                if (te.seekExact(term)) {
                    val pe: PostingsEnum = te.postings(reuse, PostingsEnum.OFFSETS.toInt())
                    if (pe.advance(doc) == doc) {
                        mis.add(TermMatchesIterator(query, pe))
                        reuse = null
                    } else {
                        reuse = pe
                    }
                }
                term = terms.next()
            }
            it = fromSubIterators(mis)
        }

        @Throws(IOException::class)
        override fun next(): Boolean {
            if (it == null) {
                init()
            }
            checkNotNull(it)
            return it!!.next()
        }

        override fun startPosition(): Int {
            return it!!.startPosition()
        }

        override fun endPosition(): Int {
            return it!!.endPosition()
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return it!!.startOffset()
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return it!!.endOffset()
        }

        @get:Throws(IOException::class)
        override val subMatches: MatchesIterator?
            get() = it!!.subMatches

    }

    private val queue: PriorityQueue<MatchesIterator>

    private var started = false

    init {
        queue =
            object : PriorityQueue<MatchesIterator>(matches.size) {
                override fun lessThan(a: MatchesIterator, b: MatchesIterator): Boolean {
                    if (a.startPosition() == -1 && b.startPosition() == -1) {
                        try {
                            return a.startOffset() < b.startOffset() || (a.startOffset() == b.startOffset() && a.endOffset() < b.endOffset())
                                    || (a.startOffset() == b.startOffset() && a.endOffset() == b.endOffset())
                        } catch (e: IOException) {
                            throw IllegalArgumentException("Failed to retrieve term offset", e)
                        }
                    }
                    return a.startPosition() < b.startPosition() || (a.startPosition() == b.startPosition() && a.endPosition() < b.endPosition())
                            || (a.startPosition() == b.startPosition() && a.endPosition() == b.endPosition())
                }
            }
        for (mi in matches) {
            if (mi.next()) {
                queue.add(mi)
            }
        }
    }

    @Throws(IOException::class)
    override fun next(): Boolean {
        if (started == false) {
            started = true
            return queue.size() > 0
        }
        if (queue.top().next() == false) {
            queue.pop()
        }
        if (queue.size() > 0) {
            queue.updateTop()
            return true
        }
        return false
    }

    override fun startPosition(): Int {
        return queue.top().startPosition()
    }

    override fun endPosition(): Int {
        return queue.top().endPosition()
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        return queue.top().startOffset()
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        return queue.top().endOffset()
    }

    @get:Throws(IOException::class)
    override val subMatches: MatchesIterator?
        get() = queue.top().subMatches

    override val query: Query
        get() = queue.top().query


    companion object {
        /**
         * Create a [DisjunctionMatchesIterator] over a list of terms
         *
         *
         * Only terms that have at least one match in the given document will be included
         */
        @Throws(IOException::class)
        fun fromTerms(
            context: LeafReaderContext, doc: Int, query: Query, field: String, terms: MutableList<Term>
        ): MatchesIterator? {
            requireNotNull<String>(field)
            for (term in terms) {
                require(field == term.field() != false) {
                    ("Tried to generate iterator from terms in multiple fields: expected ["
                            + field
                            + "] but got ["
                            + term.field()
                            + "]")
                }
            }
            return fromTermsEnum(context, doc, query, field, asBytesRefIterator(terms))
        }

        private fun asBytesRefIterator(terms: MutableList<Term>): BytesRefIterator {
            return object : BytesRefIterator {
                var i: Int = 0

                override fun next(): BytesRef? {
                    if (i >= terms.size) return null
                    return terms[i++].bytes()
                }
            }
        }

        /**
         * Create a [DisjunctionMatchesIterator] over a list of terms extracted from a [ ]
         *
         *
         * Only terms that have at least one match in the given document will be included
         */
        @Throws(IOException::class)
        fun fromTermsEnum(
            context: LeafReaderContext, doc: Int, query: Query, field: String, terms: BytesRefIterator
        ): MatchesIterator? {
            requireNotNull<String>(field)
            val t: Terms = Terms.getTerms(context.reader(), field)
            val te: TermsEnum = t.iterator()
            var reuse: PostingsEnum? = null
            var term: BytesRef? = terms.next()
            while (term != null) {
                if (te.seekExact(term)) {
                    val pe: PostingsEnum = te.postings(
                        reuse = reuse,
                        flags = PostingsEnum.OFFSETS.toInt()
                    )
                    if (pe.advance(doc) == doc) {
                        return TermsEnumDisjunctionMatchesIterator(
                            TermMatchesIterator(query, pe), terms, te, doc, query
                        )
                    } else {
                        reuse = pe
                    }
                }
                term = terms.next()
            }
            return null
        }

        @Throws(IOException::class)
        fun fromSubIterators(mis: MutableList<MatchesIterator>): MatchesIterator? {
            if (mis.size == 0) return null
            if (mis.size == 1) return mis[0]
            return DisjunctionMatchesIterator(mis)
        }
    }
}
