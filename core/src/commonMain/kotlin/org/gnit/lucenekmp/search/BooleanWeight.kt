package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.Similarity


/** Expert: the Weight for BooleanQuery, used to normalize, score and explain these queries.  */
internal class BooleanWeight(override val query: BooleanQuery, searcher: IndexSearcher, val scoreMode: ScoreMode, boost: Float) :
    Weight(
        query
    ) {
    /** The Similarity implementation.  */
    val similarity: Similarity = searcher.getSimilarity()

    internal class WeightedBooleanClause internal constructor(val clause: BooleanClause, val weight: Weight)

    val weightedClauses: ArrayList<WeightedBooleanClause> = ArrayList<WeightedBooleanClause>()

    init {
        for (c in query) {
            val w =
                searcher.createWeight(
                    c.query, if (c.isScoring) scoreMode else ScoreMode.COMPLETE_NO_SCORES, boost
                )
            weightedClauses.add(WeightedBooleanClause(c, w))
        }
    }

    @Throws(IOException::class)
    override fun explain(context: LeafReaderContext, doc: Int): Explanation {
        val minShouldMatch: Int = query.minimumNumberShouldMatch
        val subs: MutableList<Explanation> = ArrayList()
        var fail = false
        var matchCount = 0
        var shouldMatchCount = 0
        for (wc in weightedClauses) {
            val w: Weight = wc.weight
            val c: BooleanClause = wc.clause
            val e = w.explain(context, doc)
            if (e.isMatch) {
                if (c.isScoring) {
                    subs.add(e)
                } else if (c.isRequired) {
                    subs.add(
                        Explanation.match(
                            0f,
                            "match on required clause, product of:",
                            Explanation.match(0f, "${Occur.FILTER} clause"),
                            e
                        )
                    )
                } else if (c.isProhibited) {
                    subs.add(
                        Explanation.noMatch("match on prohibited clause (" + c.query.toString() + ")", e)
                    )
                    fail = true
                }
                if (!c.isProhibited) {
                    matchCount++
                }
                if (c.occur == Occur.SHOULD) {
                    shouldMatchCount++
                }
            } else if (c.isRequired) {
                subs.add(
                    Explanation.noMatch("no match on required clause (" + c.query.toString() + ")", e)
                )
                fail = true
            }
        }
        if (fail) {
            return Explanation.noMatch(
                "Failure to meet condition(s) of required/prohibited clause(s)", subs
            )
        } else if (matchCount == 0) {
            return Explanation.noMatch("No matching clauses", subs)
        } else if (shouldMatchCount < minShouldMatch) {
            return Explanation.noMatch(
                "Failure to match minimum number of optional clauses: $minShouldMatch", subs
            )
        } else {
            // Replicating the same floating-point errors as the scorer does is quite
            // complex (essentially because of how ReqOptSumScorer casts intermediate
            // contributions to the score to floats), so in order to make sure that
            // explanations have the same value as the score, we pull a scorer and
            // use it to compute the score.
            val scorer: Scorer = scorer(context)!!
            val advanced = scorer.iterator().advance(doc)
            require(advanced == doc)
            return Explanation.match(scorer.score(), "sum of:", subs)
        }
    }

    @Throws(IOException::class)
    override fun matches(context: LeafReaderContext, doc: Int): Matches? {
        val minShouldMatch: Int = query.minimumNumberShouldMatch
        val matches: MutableList<Matches> = ArrayList()
        var shouldMatchCount = 0
        for (wc in weightedClauses) {
            val w: Weight = wc.weight
            val bc: BooleanClause = wc.clause
            val m = w.matches(context, doc)
            if (bc.isProhibited) {
                if (m != null) {
                    return null
                }
            }
            if (bc.isRequired) {
                if (m == null) {
                    return null
                }
                matches.add(m)
            }
            if (bc.occur == Occur.SHOULD) {
                if (m != null) {
                    matches.add(m)
                    shouldMatchCount++
                }
            }
        }
        if (shouldMatchCount < minShouldMatch) {
            return null
        }
        return MatchesUtils.fromSubMatches(matches)
    }

    @Throws(IOException::class)
    override fun count(context: LeafReaderContext): Int {
        val numDocs: Int = context.reader().numDocs()
        if (query.isPureDisjunction) {
            return optCount(context, Occur.SHOULD)
        }
        val positiveCount: Int = if ((!query.getClauses(Occur.FILTER).isEmpty() || !query.getClauses(Occur.MUST).isEmpty()) && query.minimumNumberShouldMatch == 0
        ) {
            reqCount(context)
        } else {
            // The query has a non-zero min-should match. We could handles some cases, e.g.
            // minShouldMatch=N and we can find N SHOULD clauses that match all docs, but are there
            // real-world queries that would benefit from Lucene handling this case
            -1
        }

        if (positiveCount == 0) {
            return 0
        }

        val prohibitedCount = optCount(context, Occur.MUST_NOT)
        return if (prohibitedCount == -1) {
            -1
        } else if (prohibitedCount == 0) {
            positiveCount
        } else if (prohibitedCount == numDocs) {
            0
        } else if (positiveCount == numDocs) {
            numDocs - prohibitedCount
        } else {
            -1
        }
    }

    /**
     * Return the number of matches of required clauses, or -1 if unknown, or numDocs if there are no
     * required clauses.
     */
    @Throws(IOException::class)
    private fun reqCount(context: LeafReaderContext): Int {
        val numDocs: Int = context.reader().numDocs()
        var reqCount = numDocs
        for (weightedClause in weightedClauses) {
            if (!weightedClause.clause.isRequired) {
                continue
            }
            val count: Int = weightedClause.weight.count(context)
            if (count == -1 || count == 0) {
                // If the count of one clause is unknown, then the count of the conjunction is unknown too.
                // If one clause doesn't match any docs then the conjunction doesn't match any docs either.
                return count
            } else if (count == numDocs) {
                // the query matches all docs, it can be safely ignored
            } else if (reqCount == numDocs) {
                // all clauses seen so far match all docs, so the count of the new clause is also the count
                // of the conjunction
                reqCount = count
            } else {
                // We have two clauses whose count is in [1, numDocs), we can't figure out the number of
                // docs that match the conjunction without running the query.
                return -1
            }
        }
        return reqCount
    }

    /**
     * Return the number of matches of optional clauses, or -1 if unknown, or 0 if there are no
     * optional clauses.
     */
    @Throws(IOException::class)
    private fun optCount(context: LeafReaderContext, occur: Occur): Int {
        val numDocs: Int = context.reader().numDocs()
        var optCount = 0
        var unknownCount = false
        for (weightedClause in weightedClauses) {
            if (weightedClause.clause.occur != occur) {
                continue
            }
            val count: Int = weightedClause.weight.count(context)
            if (count == -1) {
                // If one clause has a number of matches that is unknown, let's be more aggressive to check
                // whether remain clauses could match all docs.
                unknownCount = true
                continue
            } else if (count == numDocs) {
                // If either clause matches all docs, then the disjunction matches all docs.
                return count
            } else if (count == 0) {
                // We can safely ignore this clause, it doesn't affect the count.
            } else if (optCount == 0) {
                // This is the first clause we see that has a non-zero count, it becomes the count of the
                // disjunction.
                optCount = count
            } else {
                // We have two clauses whose count is in [1, numDocs), we can't figure out the number of
                // docs that match the disjunction without running the query.
                unknownCount = true
            }
        }
        // If at least one of clauses has a number of matches that is unknown and no clause matches all
        // docs, then the number of matches of
        // the disjunction is unknown
        return if (unknownCount) -1 else optCount
    }

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
        if (query.clauses().size
            > AbstractMultiTermQueryConstantScoreWrapper.BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD
        ) {
            // Disallow caching large boolean queries to not encourage users
            // to build large boolean queries as a workaround to the fact that
            // we disallow caching large TermInSetQueries.
            return false
        }
        for (wc in weightedClauses) {
            val w: Weight = wc.weight
            if (!w.isCacheable(ctx)) return false
        }
        return true
    }

    @Throws(IOException::class)
    override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
        var minShouldMatch: Int = query.minimumNumberShouldMatch

        val scorers: MutableMap<Occur, MutableCollection<ScorerSupplier>> = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>().apply {
            Occur.entries.forEach { occur ->
                this[occur] = mutableListOf()
            }
        }
        for (occur in Occur.entries) {
            scorers.put(occur, ArrayList())
        }

        for (wc in weightedClauses) {
            val w: Weight = wc.weight
            val c: BooleanClause = wc.clause
            val subScorer = w.scorerSupplier(context)
            if (subScorer == null) {
                if (c.isRequired) {
                    return null
                }
            } else {
                scorers[c.occur]!!.add(subScorer)
            }
        }

        // scorer simplifications:
        if (scorers[Occur.SHOULD]!!.size == minShouldMatch) {
            // any optional clauses are in fact required
            scorers[Occur.MUST]!!.addAll(scorers[Occur.SHOULD]!!)
            scorers[Occur.SHOULD]!!.clear()
            minShouldMatch = 0
        }

        if (scorers[Occur.FILTER]!!.isEmpty()
            && scorers[Occur.MUST]!!.isEmpty()
            && scorers[Occur.SHOULD]!!.isEmpty()
        ) {
            // no required and optional clauses.
            return null
        } else if (scorers[Occur.SHOULD]!!.size < minShouldMatch) {
            // either >1 req scorer, or there are 0 req scorers and at least 1
            // optional scorer. Therefore if there are not enough optional scorers
            // no documents will be matched by the query
            return null
        }

        if (!scoreMode.needsScores() && minShouldMatch == 0 && scorers[Occur.MUST]!!.size + scorers[Occur.FILTER]!!.size > 0
        ) {
            // Purely optional clauses are useless without scoring.
            scorers[Occur.SHOULD]!!.clear()
        }

        return BooleanScorerSupplier(
            this, scorers, scoreMode, minShouldMatch, context.reader().maxDoc()
        )
    }
}
