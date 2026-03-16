package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode

/**
 * Wrapper to allow [SpanQuery] objects participate in composite single-field SpanQueries by
 * 'lying' about their search field. That is, the masked SpanQuery will function as normal, but
 * [SpanQuery.getField] simply hands back the value supplied in this class's constructor.
 *
 * <p>This can be used to support Queries like [SpanNearQuery] or [SpanOrQuery] across
 * different fields, which is not ordinarily permitted.
 *
 * <p>This can be useful for denormalized relational data: for example, when indexing a document
 * with conceptually many 'children':
 *
 * <pre>
 *  teacherid: 1
 *  studentfirstname: james
 *  studentsurname: jones
 *
 *  teacherid: 2
 *  studenfirstname: james
 *  studentsurname: smith
 *  studentfirstname: sally
 *  studentsurname: jones
 * </pre>
 *
 * <p>a SpanNearQuery with a slop of 0 can be applied across two [SpanTermQuery] objects as
 * follows:
 *
 * <pre>
 *    SpanQuery q1  = new SpanTermQuery(new Term("studentfirstname", "james"));
 *    SpanQuery q2  = new SpanTermQuery(new Term("studentsurname", "jones"));
 *    SpanQuery q2m = new FieldMaskingSpanQuery(q2, "studentfirstname");
 *    Query q = new SpanNearQuery(new SpanQuery[]{q1, q2m}, -1, false);
 * </pre>
 *
 * to search for 'studentfirstname:james studentsurname:jones' and find teacherid 1 without matching
 * teacherid 2 (which has a 'james' in position 0 and 'jones' in position 1).
 *
 * <p>Note: as [getField] returns the masked field, scoring will be done using the
 * Similarity and collection statistics of the field name supplied, but with the term statistics of
 * the real field. This may lead to exceptions, poor performance, and unexpected scoring behaviour.
 */
class FieldMaskingSpanQuery(
    private val maskedQuery: SpanQuery,
    private val field: String,
) : SpanQuery() {
    init {
        requireNotNull(maskedQuery)
        requireNotNull(field)
    }

    override fun getField(): String {
        return field
    }

    fun getMaskedQuery(): SpanQuery {
        return maskedQuery
    }

    // :NOTE: getBoost and setBoost are not proxied to the maskedQuery
    // ...this is done to be more consistent with things like SpanFirstQuery

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        return maskedQuery.createWeight(searcher, scoreMode, boost)
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewritten = maskedQuery.rewrite(indexSearcher) as SpanQuery
        if (rewritten !== maskedQuery) {
            return FieldMaskingSpanQuery(rewritten, field)
        }

        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            maskedQuery.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this))
        }
    }

    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        buffer.append("mask(")
        buffer.append(maskedQuery.toString(field))
        buffer.append(")")
        buffer.append(" as ")
        buffer.append(this.field)
        return buffer.toString()
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(other as FieldMaskingSpanQuery)
    }

    private fun equalsTo(other: FieldMaskingSpanQuery): Boolean {
        return getField() == other.getField() && getMaskedQuery() == other.getMaskedQuery()
    }

    override fun hashCode(): Int {
        return classHash() xor getMaskedQuery().hashCode() xor getField().hashCode()
    }
}
