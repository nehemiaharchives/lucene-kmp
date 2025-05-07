package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import kotlin.jvm.JvmOverloads


/** A query that matches no documents.  */
class MatchNoDocsQuery
/** Default constructor  */ @JvmOverloads constructor(private val reason: String = "") : Query() {
    /** Provides a reason explaining why this query was used  */

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : Weight(this) {
            @Throws(IOException::class)
            override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                return Explanation.noMatch(reason)
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                return null
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }

            override fun count(context: LeafReaderContext): Int {
                return 0
            }
        }
    }

    override fun visit(visitor: QueryVisitor) {
        visitor.visitLeaf(this)
    }

    override fun toString(field: String?): String {
        return "MatchNoDocsQuery(\"$reason\")"
    }

    override fun equals(o: Any?): Boolean {
        return sameClassAs(o)
    }

    override fun hashCode(): Int {
        return classHash()
    }
}
