package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexReaderContext
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.tests.search.ShardSearchingTestBase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

// TODO
//   - other queries besides PrefixQuery & TermQuery (but:
//     FuzzyQ will be problematic... the top N terms it
//     takes means results will differ)
//   - NRQ/F
//   - BQ, negated clauses, negated prefix clauses
//   - test pulling docs in 2nd round trip...
//   - filter too

@SuppressCodecs("SimpleText", "Direct")
class TestShardSearching : ShardSearchingTestBase() {

    private class PreviousSearchState(
        val query: Query,
        val sort: Sort?,
        val searchAfterLocal: ScoreDoc?,
        val searchAfterShard: ScoreDoc?,
        versions: LongArray,
        val numHitsPaged: Int
    ) {
        val searchTimeNanos: Long = System.nanoTime()
        val versions: LongArray = versions.copyOf()
    }

    @Test
    fun testSimple() {
        val numNodes = TestUtil.nextInt(random(), 1, 10)

        val runTimeSec = if (TEST_NIGHTLY) atLeast(5).toDouble() else atLeast(1).toDouble()

        val minDocsToMakeTerms = TestUtil.nextInt(random(), 5, 20)

        val maxSearcherAgeSeconds = TestUtil.nextInt(random(), 1, 3)

        if (VERBOSE) {
            println(
                "TEST: numNodes=$numNodes runTimeSec=$runTimeSec maxSearcherAgeSeconds=$maxSearcherAgeSeconds"
            )
        }

        start(numNodes, runTimeSec, maxSearcherAgeSeconds)

        val priorSearches = ArrayList<PreviousSearchState>()
        var terms: MutableList<BytesRef>? = null
        while (System.nanoTime() < endTimeNanos) {
            val doFollowon = priorSearches.isNotEmpty() && random().nextInt(7) == 1

            // Pick a random node; we will run the query on this node:
            val myNodeID = random().nextInt(numNodes)

            val localShardSearcher: NodeState.ShardIndexSearcher

            val prevSearchState: PreviousSearchState?

            if (doFollowon) {
                // Pretend user issued a followon query:
                prevSearchState = priorSearches[random().nextInt(priorSearches.size)]

                if (VERBOSE) {
                    println(
                        "\nTEST: follow-on query age=" +
                            ((System.nanoTime() - prevSearchState.searchTimeNanos) / 1000000000.0)
                    )
                }

                try {
                    localShardSearcher = nodes[myNodeID].acquire(prevSearchState.versions)
                } catch (_: SearcherExpiredException) {
                    // Expected, sometimes; in a "real" app we would
                    // either forward this error to the user ("too
                    // much time has passed; please re-run your
                    // search") or sneakily just switch to newest
                    // searcher w/o telling them...
                    if (VERBOSE) {
                        println("  searcher expired during local shard searcher init")
                    }
                    priorSearches.remove(prevSearchState)
                    continue
                }
            } else {
                if (VERBOSE) {
                    println("\nTEST: fresh query")
                }
                // Do fresh query:
                localShardSearcher = nodes[myNodeID].acquire()
                prevSearchState = null
            }

            val subs = arrayOfNulls<IndexReader>(numNodes)

            var searchState: PreviousSearchState? = null

            try {
                // Mock: now make a single reader (MultiReader) from all node
                // searchers.  In a real shard env you can't do this... we
                // do it to confirm results from the shard searcher
                // are correct:
                var docCount = 0
                try {
                    var nodeID = 0
                    while (nodeID < numNodes) {
                        val subVersion = localShardSearcher.nodeVersions[nodeID]
                        val sub = nodes[nodeID].searchers.acquire(subVersion)
                        if (sub == null) {
                            var rollbackNodeID = nodeID - 1
                            while (rollbackNodeID >= 0) {
                                subs[rollbackNodeID]!!.decRef()
                                subs[rollbackNodeID] = null
                                rollbackNodeID--
                            }
                            throw SearcherExpiredException("nodeID=$nodeID version=$subVersion")
                        }
                        subs[nodeID] = sub.indexReader
                        docCount += subs[nodeID]!!.maxDoc()
                        nodeID++
                    }
                } catch (_: SearcherExpiredException) {
                    // Expected
                    if (VERBOSE) {
                        println("  searcher expired during mock reader init")
                    }
                    continue
                }

                val mockReader = MultiReader(*subs.map { requireNotNull(it) }.toTypedArray())
                val mockSearcher = IndexSearcher(mockReader)

                val query: Query?
                val sort: Sort?

                if (prevSearchState != null) {
                    query = prevSearchState.query
                    sort = prevSearchState.sort
                } else {
                    if (terms == null && docCount > minDocsToMakeTerms) {
                        // TODO: try to "focus" on high freq terms sometimes too
                        // TODO: maybe also periodically reset the terms...?
                        val termsEnum = MultiTerms.getTerms(mockReader, "body")!!.iterator()
                        terms = ArrayList()
                        while (termsEnum.next() != null) {
                            terms.add(BytesRef.deepCopyOf(termsEnum.term()!!))
                        }
                        if (VERBOSE) {
                            println("TEST: init terms: ${terms.size} terms")
                        }
                        if (terms.isEmpty()) {
                            terms = null
                        }
                    }

                    if (VERBOSE) {
                        println("  maxDoc=${mockReader.maxDoc()}")
                    }

                    if (terms != null) {
                        query =
                            if (random().nextBoolean()) {
                                TermQuery(Term("body", terms[random().nextInt(terms.size)]))
                            } else {
                                val t = terms[random().nextInt(terms.size)].utf8ToString()
                                val prefix =
                                    if (t.length <= 1) {
                                        t
                                    } else {
                                        t.substring(0, TestUtil.nextInt(random(), 1, 2))
                                    }
                                PrefixQuery(Term("body", prefix))
                            }

                        sort =
                            if (random().nextBoolean()) {
                                null
                            } else {
                                // TODO: sort by more than 1 field
                                when (random().nextInt(3)) {
                                    0 -> Sort(SortField.FIELD_SCORE)
                                    1 -> null
                                    2 ->
                                        Sort(
                                            SortField(
                                                "docid_intDV",
                                                SortField.Type.INT,
                                                random().nextBoolean()
                                            )
                                        )
                                    else ->
                                        Sort(
                                            SortField(
                                                "titleDV",
                                                SortField.Type.STRING,
                                                random().nextBoolean()
                                            )
                                        )
                                }
                            }
                    } else {
                        query = null
                        sort = null
                    }
                }

                if (query != null) {
                    try {
                        searchState = assertSame(mockSearcher, localShardSearcher, query, sort, prevSearchState)
                    } catch (_: SearcherExpiredException) {
                        // Expected; in a "real" app we would
                        // either forward this error to the user ("too
                        // much time has passed; please re-run your
                        // search") or sneakily just switch to newest
                        // searcher w/o telling them...
                        if (VERBOSE) {
                            println("  searcher expired during search")
                        }
                        // We can't do this in general: on a very slow
                        // computer it's possible the local searcher
                        // expires before we can finish our search:
                        // assert prevSearchState != null;
                        if (prevSearchState != null) {
                            priorSearches.remove(prevSearchState)
                        }
                    }
                }
            } finally {
                nodes[myNodeID].release(localShardSearcher)
                for (sub in subs) {
                    sub?.decRef()
                }
            }

            if (searchState != null && searchState.searchAfterLocal != null && random().nextInt(5) == 3) {
                priorSearches.add(searchState)
                if (priorSearches.size > 200) {
                    priorSearches.shuffle(random())
                    priorSearches.subList(100, priorSearches.size).clear()
                }
            }
        }

        finish()
    }

    private fun assertSame(
        mockSearcher: IndexSearcher,
        shardSearcher: NodeState.ShardIndexSearcher,
        q: Query,
        sort: Sort?,
        state: PreviousSearchState?
    ): PreviousSearchState? {
        var numHits = TestUtil.nextInt(random(), 1, 100)
        if (state != null && state.searchAfterLocal == null) {
            // In addition to what we last searched:
            numHits += state.numHitsPaged
        }

        if (VERBOSE) {
            println("TEST: query=$q sort=$sort numHits=$numHits")
            if (state != null) {
                println(
                    "  prev: searchAfterLocal=${state.searchAfterLocal} searchAfterShard=${state.searchAfterShard} numHitsPaged=${state.numHitsPaged}"
                )
            }
        }

        // Single (mock local) searcher:
        val hits =
            if (sort == null) {
                if (state != null && state.searchAfterLocal != null) {
                    mockSearcher.searchAfter(state.searchAfterLocal, q, numHits)
                } else {
                    mockSearcher.search(q, numHits)
                }
            } else {
                mockSearcher.search(q, numHits, sort)
            }

        // Shard searcher
        val shardHits =
            if (sort == null) {
                if (state != null && state.searchAfterShard != null) {
                    shardSearcher.searchAfter(state.searchAfterShard, q, numHits)
                } else {
                    shardSearcher.search(q, numHits)
                }
            } else {
                shardSearcher.search(q, numHits, sort)
            }

        val numNodes = shardSearcher.nodeVersions.size
        val base = IntArray(numNodes)
        val subs: MutableList<IndexReaderContext> = mockSearcher.topReaderContext.children()!!
        assertEquals(numNodes, subs.size)

        for (nodeID in 0 until numNodes) {
            base[nodeID] = subs[nodeID].docBaseInParent
        }

        if (VERBOSE) {
            println("  single searcher: ${hits.totalHits.value}")
            for (sd in hits.scoreDocs) {
                println("    doc=${sd.doc} score=${sd.score}")
            }
            println("  shard searcher: ${shardHits.totalHits.value}")
            for (sd in shardHits.scoreDocs) {
                println(
                    "    doc=${sd.doc} (rebased: ${sd.doc + base[sd.shardIndex]}) score=${sd.score} shard=${sd.shardIndex}"
                )
            }
        }

        var numHitsPaged = hits.scoreDocs.size
        if (state != null && state.searchAfterLocal != null) {
            numHitsPaged += state.numHitsPaged
        }

        val moreHits: Boolean
        val bottomHit: ScoreDoc?
        val bottomHitShards: ScoreDoc?

        if (numHitsPaged.toLong() < hits.totalHits.value) {
            // More hits to page through
            moreHits = true
            if (sort == null) {
                bottomHit = hits.scoreDocs[hits.scoreDocs.size - 1]
                val sd = shardHits.scoreDocs[shardHits.scoreDocs.size - 1]
                // Must copy because below we rebase:
                bottomHitShards = ScoreDoc(sd.doc, sd.score, sd.shardIndex)
                if (VERBOSE) {
                    println("  save bottomHit=$bottomHit")
                }
            } else {
                bottomHit = null
                bottomHitShards = null
            }
        } else {
            assertEquals(hits.totalHits.value, numHitsPaged.toLong())
            bottomHit = null
            bottomHitShards = null
            moreHits = false
        }

        // Must rebase so assertEquals passes:
        for (sd in shardHits.scoreDocs) {
            sd.doc += base[sd.shardIndex]
        }

        TestUtil.assertConsistent(hits, shardHits)

        return if (moreHits) {
            // Return a continuation:
            PreviousSearchState(
                q,
                sort,
                bottomHit,
                bottomHitShards,
                shardSearcher.nodeVersions,
                numHitsPaged
            )
        } else {
            null
        }
    }
}
