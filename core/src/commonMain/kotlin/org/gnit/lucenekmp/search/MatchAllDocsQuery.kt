package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext


/** A query that matches all documents.  */
class MatchAllDocsQuery : Query() {
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : ConstantScoreWeight(this, boost) {
            override fun toString(): String {
                return "weight(" + this@MatchAllDocsQuery + ")"
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                return MatchAllScorerSupplier(score(), scoreMode!!, context.reader().maxDoc())
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }

            override fun count(context: LeafReaderContext): Int {
                return context.reader().numDocs()
            }
        }
    }

    override fun toString(field: String?): String {
        return "*:*"
    }

    override fun equals(o: Any?): Boolean {
        return sameClassAs(o)
    }

    override fun hashCode(): Int {
        return classHash()
    }

    override fun visit(visitor: QueryVisitor) {
        visitor.visitLeaf(this)
    }
}
