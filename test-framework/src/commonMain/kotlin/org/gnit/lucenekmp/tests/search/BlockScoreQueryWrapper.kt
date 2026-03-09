package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.math.max
import kotlin.math.min

/**
 * Query wrapper that reduces the size of max-score blocks to more easily detect problems with the
 * max-score logic.
 */
class BlockScoreQueryWrapper(
    private val query: Query,
    private val blockLength: Int
) : Query() {
    /** Sole constructor. */

    override fun toString(field: String?): String {
        return query.toString(field)
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null || this::class != obj::class) {
            return false
        }
        val that = obj as BlockScoreQueryWrapper
        return query == that.query && blockLength == that.blockLength
    }

    override fun hashCode(): Int {
        var h = classHash()
        h = 31 * h + query.hashCode()
        h = 31 * h + blockLength
        return h
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewritten = query.rewrite(indexSearcher)
        if (rewritten !== query) {
            return BlockScoreQueryWrapper(rewritten, blockLength)
        }
        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        query.visit(visitor)
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        val inWeight = query.createWeight(searcher, scoreMode, boost)
        if (!scoreMode.needsScores()) {
            return inWeight
        }
        return object : Weight(this) {
            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return inWeight.isCacheable(ctx)
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val inScorer = inWeight.scorer(context) ?: return null

                var tmpDocs = IntArray(2)
                var tmpScores = FloatArray(2)
                tmpDocs[0] = -1
                val it = inScorer.iterator()
                var upto = 1
                var doc = it.nextDoc()
                while (true) {
                    tmpDocs = ArrayUtil.grow(tmpDocs, upto + 1)
                    tmpScores = ArrayUtil.grow(tmpScores, upto + 1)
                    tmpDocs[upto] = doc
                    if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                        upto++
                        break
                    }
                    tmpScores[upto] = inScorer.score()
                    upto++
                    doc = it.nextDoc()
                }
                val docs = ArrayUtil.copyOfSubArray(tmpDocs, 0, upto)
                val scores = ArrayUtil.copyOfSubArray(tmpScores, 0, upto)

                val scorer = object : Scorer() {
                    var i = 0

                    override fun docID(): Int {
                        return docs[i]
                    }

                    @Throws(IOException::class)
                    override fun score(): Float {
                        return scores[i]
                    }

                    override fun iterator(): DocIdSetIterator {
                        return object : DocIdSetIterator() {
                            @Throws(IOException::class)
                            override fun nextDoc(): Int {
                                assert(docs[i] != NO_MORE_DOCS)
                                return docs[++i]
                            }

                            override fun docID(): Int {
                                return docs[i]
                            }

                            override fun cost(): Long {
                                return (docs.size - 2).toLong()
                            }

                            @Throws(IOException::class)
                            override fun advance(target: Int): Int {
                                i = Arrays.binarySearch(docs, target)
                                if (i < 0) {
                                    i = -1 - i
                                }
                                assert(docs[i] >= target)
                                return docs[i]
                            }
                        }
                    }

                    private fun startOfBlock(target: Int): Int {
                        var i = Arrays.binarySearch(docs, target)
                        if (i < 0) {
                            i = -1 - i
                        }
                        return i - i % blockLength
                    }

                    private fun endOfBlock(target: Int): Int {
                        return min(startOfBlock(target) + blockLength, docs.size - 1)
                    }

                    var lastShallowTarget = -1

                    @Throws(IOException::class)
                    override fun advanceShallow(target: Int): Int {
                        lastShallowTarget = target
                        if (target == DocIdSetIterator.NO_MORE_DOCS) {
                            return DocIdSetIterator.NO_MORE_DOCS
                        }
                        return docs[endOfBlock(target)] - 1
                    }

                    @Throws(IOException::class)
                    override fun getMaxScore(upTo: Int): Float {
                        var maxScore = 0f
                        for (j in startOfBlock(max(docs[i], lastShallowTarget))..docs.size - 1) {
                            if (docs[j] > upTo) {
                                break
                            }
                            maxScore = max(maxScore, scores[j])
                            if (j == docs.size - 1) {
                                break
                            }
                        }
                        return maxScore
                    }
                }
                return DefaultScorerSupplier(scorer)
            }

            @Throws(IOException::class)
            override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                return inWeight.explain(context, doc)
            }
        }
    }
}
