package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.FilteredTermsEnum
import org.gnit.lucenekmp.index.PrefixCodedTerms
import org.gnit.lucenekmp.index.PrefixCodedTerms.TermIterator
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.ByteRunAutomaton
import okio.IOException
import org.gnit.lucenekmp.jdkport.SortedSet
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.BytesRefComparator
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.StringSorter
import kotlin.reflect.cast

/**
 * Specialization for a disjunction over many terms that, by default, behaves like a [ ] over a [BooleanQuery] containing only [ ][org.apache.lucene.search.BooleanClause.Occur.SHOULD] clauses.
 *
 *
 * For instance in the following example, both `q1` and `q2` would yield the same
 * scores:
 *
 * <pre class="prettyprint">
 * Query q1 = new TermInSetQuery("field", new BytesRef("foo"), new BytesRef("bar"));
 *
 * BooleanQuery bq = new BooleanQuery();
 * bq.add(new TermQuery(new Term("field", "foo")), Occur.SHOULD);
 * bq.add(new TermQuery(new Term("field", "bar")), Occur.SHOULD);
 * Query q2 = new ConstantScoreQuery(bq);
</pre> *
 *
 *
 * Unless a custom [MultiTermQuery.RewriteMethod] is provided, this query executes like a
 * regular disjunction where there are few terms. However, when there are many terms, instead of
 * merging iterators on the fly, it will populate a bit set with matching docs for the least-costly
 * terms and maintain a size-limited set of more costly iterators that are merged on the fly. For
 * more details, see [MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE].
 *
 *
 * Users may also provide a custom [MultiTermQuery.RewriteMethod] to define different
 * execution behavior, such as relying on doc values (see: [ ][MultiTermQuery.DOC_VALUES_REWRITE]), or if scores are required (see: [ ][MultiTermQuery.SCORING_BOOLEAN_REWRITE]). See [MultiTermQuery] documentation for more
 * rewrite options.
 *
 *
 * NOTE: This query produces scores that are equal to its boost
 */
class TermInSetQuery : MultiTermQuery, Accountable {
    override val field: String
    private val termData: PrefixCodedTerms
    private val termDataHashCode: Int // cached hashcode of termData

    constructor(field: String, terms: MutableCollection<BytesRef>) : this(
        field,
        packTerms(field, terms)
    )

    /** Creates a new [TermInSetQuery] from the given collection of terms.  */
    constructor(
        rewriteMethod: RewriteMethod,
        field: String,
        terms: MutableCollection<BytesRef>
    ) : super(field, rewriteMethod) {
        this.field = field
        this.termData = packTerms(field, terms)
        termDataHashCode = termData.hashCode()
    }

    private constructor(field: String, termData: PrefixCodedTerms) : super(
        field,
        MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE
    ) {
        this.field = field
        this.termData = termData
        termDataHashCode = termData.hashCode()
    }

    override val termsCount: Long
        get() = termData.size()

    val bytesRefIterator: BytesRefIterator
        /**
         * Get an iterator over the encoded terms for query inspection.
         *
         * @lucene.experimental
         */
        get() {
            val iterator: TermIterator = this.termData.iterator()
            return object : BytesRefIterator {
                override fun next(): BytesRef? {
                    return iterator.next()
                }
            }
        }

    override fun visit(visitor: QueryVisitor) {
        if (!visitor.acceptField(field)) {
            return
        }
        if (termData.size() == 1L) {
            visitor.consumeTerms(this, Term(field, termData.iterator().next()!!))
        }
        if (termData.size() > 1) {
            visitor.consumeTermsMatching(this, field) { this.asByteRunAutomaton() }
        }
    }

    // TODO: This is pretty heavy-weight. If we have TermInSetQuery directly extend AutomatonQuery
    // we won't have to do this (see GH#12176).
    private fun asByteRunAutomaton(): ByteRunAutomaton {
        try {
            val a: Automaton =
                Automata.makeBinaryStringUnion(termData.iterator())
            return ByteRunAutomaton(a, true)
        } catch (e: IOException) {
            // Shouldn't happen since termData.iterator() provides an interator implementation that
            // never throws:
            throw UncheckedIOException(e)
        }
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(this::class.cast(other))
    }

    private fun equalsTo(other: TermInSetQuery): Boolean {
        // no need to check 'field' explicitly since it is encoded in 'termData'
        // termData might be heavy to compare so check the hash code first
        return termDataHashCode == other.termDataHashCode && termData == other.termData
    }

    override fun hashCode(): Int {
        return 31 * classHash() + termDataHashCode
    }

    override fun toString(defaultField: String?): String {
        val builder = StringBuilder()
        builder.append(field)
        builder.append(":(")

        val iterator: TermIterator = termData.iterator()
        var first = true
        var term: BytesRef? = iterator.next()
        while (term != null) {
            if (!first) {
                builder.append(' ')
            }
            first = false
            builder.append(Term.toString(term))
            term = iterator.next()
        }
        builder.append(')')

        return builder.toString()
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + termData.ramBytesUsed()
    }

    override val childResources: MutableCollection<Accountable>
        get() = mutableListOf()

    @Throws(IOException::class)
    override fun getTermsEnum(
        terms: Terms,
        atts: AttributeSource
    ): TermsEnum {
        return SetEnum(terms.iterator())
    }

    /**
     * Like a baby [org.apache.lucene.index.AutomatonTermsEnum], ping-pong intersects the terms
     * dict against our encoded query terms.
     */
    private inner class SetEnum(termsEnum: TermsEnum) :
        FilteredTermsEnum(termsEnum) {
        private val iterator: TermIterator = termData.iterator()
        private var seekTerm: BytesRef?

        init {
            seekTerm = iterator.next()
        }

        @Throws(IOException::class)
        override fun accept(term: BytesRef): AcceptStatus {
            // next() our iterator until it is >= the incoming term
            // if it matches exactly, it's a hit, otherwise it's a miss
            var cmp = 0
            while (seekTerm != null && (seekTerm!!.compareTo(term).also { cmp = it }) < 0) {
                seekTerm = iterator.next()
            }
            return if (seekTerm == null) {
                AcceptStatus.END
            } else if (cmp == 0) {
                AcceptStatus.YES_AND_SEEK
            } else {
                AcceptStatus.NO_AND_SEEK
            }
        }

        @Throws(IOException::class)
        override fun nextSeekTerm(currentTerm: BytesRef?): BytesRef? {
            // next() our iterator until it is > the currentTerm, must always make progress.
            if (currentTerm == null) {
                return seekTerm
            }
            while (seekTerm != null && seekTerm!! <= currentTerm) {
                seekTerm = iterator.next()
            }
            return seekTerm
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(TermInSetQuery::class)

        private fun packTerms(
            field: String,
            terms: MutableCollection<BytesRef>
        ): PrefixCodedTerms {
            val sortedTerms: Array<BytesRef> =
                terms.toTypedArray<BytesRef>()
            // already sorted if we are a SortedSet with natural order
            val sorted = terms is SortedSet && terms.comparator() == null
            if (!sorted) {
                object : StringSorter(BytesRefComparator.NATURAL) {
                    override fun get(
                        builder: BytesRefBuilder,
                        result: BytesRef,
                        i: Int
                    ) {
                        val term: BytesRef = sortedTerms[i]
                        result.length = term.length
                        result.offset = term.offset
                        result.bytes = term.bytes
                    }

                    override fun swap(i: Int, j: Int) {
                        val b: BytesRef = sortedTerms[i]
                        sortedTerms[i] = sortedTerms[j]
                        sortedTerms[j] = b
                    }
                }.sort(0, sortedTerms.size)
            }
            val builder: PrefixCodedTerms.Builder =
                PrefixCodedTerms.Builder()
            var previous: BytesRefBuilder? = null
            for (term in sortedTerms) {
                if (previous == null) {
                    previous = BytesRefBuilder()
                } else if (previous.get() == term) {
                    continue  // deduplicate
                }
                builder.add(field, term)
                previous.copyBytes(term)
            }

            return builder.finish()
        }
    }
}
