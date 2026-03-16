package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor

abstract class SpanContainQuery(
    var bigInternal: SpanQuery,
    var littleInternal: SpanQuery,
) : SpanQuery(), Cloneable<SpanContainQuery> {
    init {
        bigInternal = requireNotNull(bigInternal)
        littleInternal = requireNotNull(littleInternal)
        requireNotNull(bigInternal.getField())
        requireNotNull(littleInternal.getField())
        require(bigInternal.getField() == littleInternal.getField()) { "big and little not same field" }
    }

    override fun getField(): String? {
        return bigInternal.getField()
    }

    fun getBig(): SpanQuery {
        return bigInternal
    }

    fun getLittle(): SpanQuery {
        return littleInternal
    }

    abstract inner class SpanContainWeight(
        searcher: IndexSearcher,
        terms: Map<Term, TermStates>?,
        val bigWeight: SpanWeight,
        val littleWeight: SpanWeight,
        boost: Float,
    ) : SpanWeight(this@SpanContainQuery, searcher, terms, boost) {
        @Throws(IOException::class)
        fun prepareConjunction(context: LeafReaderContext, postings: Postings): ArrayList<Spans>? {
            val bigSpans = bigWeight.getSpans(context, postings) ?: return null
            val littleSpans = littleWeight.getSpans(context, postings) ?: return null
            val bigAndLittle = ArrayList<Spans>()
            bigAndLittle.add(bigSpans)
            bigAndLittle.add(littleSpans)
            return bigAndLittle
        }

        override fun extractTermStates(contexts: MutableMap<Term, TermStates>) {
            bigWeight.extractTermStates(contexts)
            littleWeight.extractTermStates(contexts)
        }
    }

    fun toString(field: String?, name: String): String {
        val buffer = StringBuilder()
        buffer.append(name)
        buffer.append("(")
        buffer.append(bigInternal.toString(field))
        buffer.append(", ")
        buffer.append(littleInternal.toString(field))
        buffer.append(")")
        return buffer.toString()
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewrittenBig = bigInternal.rewrite(indexSearcher) as SpanQuery
        val rewrittenLittle = littleInternal.rewrite(indexSearcher) as SpanQuery
        if (bigInternal !== rewrittenBig || littleInternal !== rewrittenLittle) {
            val clone = clone()
            clone.bigInternal = rewrittenBig
            clone.littleInternal = rewrittenLittle
            return clone
        }
        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(getField())) {
            val v = visitor.getSubVisitor(BooleanClause.Occur.MUST, this)
            bigInternal.visit(v)
            littleInternal.visit(v)
        }
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(other as SpanContainQuery)
    }

    private fun equalsTo(other: SpanContainQuery): Boolean {
        return bigInternal == other.bigInternal && littleInternal == other.littleInternal
    }

    override fun hashCode(): Int {
        var h = classHash().rotateLeft(1)
        h = h xor bigInternal.hashCode()
        h = h.rotateLeft(1)
        h = h xor littleInternal.hashCode()
        return h
    }

    private fun Int.rotateLeft(bitCount: Int): Int {
        return (this shl bitCount) or (this ushr (32 - bitCount))
    }
}
