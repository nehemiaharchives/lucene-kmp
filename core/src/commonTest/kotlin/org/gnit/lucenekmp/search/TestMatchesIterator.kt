package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.ReaderUtil
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.tests.search.AssertingMatches
import org.gnit.lucenekmp.tests.search.MatchesTestBase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestMatchesIterator : MatchesTestBase() {
    override fun getDocuments(): Array<String> {
        return arrayOf(
            "w1 w2 w3 w4 w5",
            "w1 w3 w2 w3 zz",
            "w1 xx w2 yy w4",
            "w1 w2 w1 w4 w2 w3",
            "a phrase sentence with many phrase sentence iterations of a phrase sentence",
            "nothing matches this document",
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTermQuery() {
        val t = Term(FIELD_WITH_OFFSETS, "w1")
        val q: Query = NamedMatches.wrapQuery("q", TermQuery(t))
        checkMatches(
            q,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 0, 0, 0, 2),
                intArrayOf(1, 0, 0, 0, 2),
                intArrayOf(2, 0, 0, 0, 2),
                intArrayOf(3, 0, 0, 0, 2, 2, 2, 6, 8),
                intArrayOf(4),
            ),
        )
        checkLabelCount(q, FIELD_WITH_OFFSETS, intArrayOf(1, 1, 1, 1, 0, 0))
        assertIsLeafMatch(q, FIELD_WITH_OFFSETS)
        checkSubMatches(q, arrayOf(arrayOf("q"), arrayOf("q"), arrayOf("q"), arrayOf("q"), emptyArray(), emptyArray()))
    }

    @Test
    @Throws(IOException::class)
    fun testTermQueryNoStoredOffsets() {
        val q: Query = TermQuery(Term(FIELD_NO_OFFSETS, "w1"))
        checkMatches(
            q,
            FIELD_NO_OFFSETS,
            arrayOf(
                intArrayOf(0, 0, 0, -1, -1),
                intArrayOf(1, 0, 0, -1, -1),
                intArrayOf(2, 0, 0, -1, -1),
                intArrayOf(3, 0, 0, -1, -1, 2, 2, -1, -1),
                intArrayOf(4),
            ),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTermQueryNoPositions() {
        for (field in arrayOf(FIELD_DOCS_ONLY, FIELD_FREQS)) {
            val q: Query = TermQuery(Term(field, "w1"))
            checkNoPositionsMatches(q, field, booleanArrayOf(true, true, true, true, false))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDisjunction() {
        val w1: Query = NamedMatches.wrapQuery("w1", TermQuery(Term(FIELD_WITH_OFFSETS, "w1")))
        val w3: Query = NamedMatches.wrapQuery("w3", TermQuery(Term(FIELD_WITH_OFFSETS, "w3")))
        val q: Query =
            BooleanQuery.Builder()
                .add(w1, BooleanClause.Occur.SHOULD)
                .add(w3, BooleanClause.Occur.SHOULD)
                .build()
        checkMatches(
            q,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 0, 0, 0, 2, 2, 2, 6, 8),
                intArrayOf(1, 0, 0, 0, 2, 1, 1, 3, 5, 3, 3, 9, 11),
                intArrayOf(2, 0, 0, 0, 2),
                intArrayOf(3, 0, 0, 0, 2, 2, 2, 6, 8, 5, 5, 15, 17),
                intArrayOf(4),
            ),
        )
        checkLabelCount(q, FIELD_WITH_OFFSETS, intArrayOf(2, 2, 1, 2, 0, 0))
        assertIsLeafMatch(q, FIELD_WITH_OFFSETS)
        checkSubMatches(
            q,
            arrayOf(arrayOf("w1", "w3"), arrayOf("w1", "w3"), arrayOf("w1"), arrayOf("w1", "w3"), emptyArray(), emptyArray()),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testDisjunctionNoPositions() {
        for (field in arrayOf(FIELD_DOCS_ONLY, FIELD_FREQS)) {
            val q: Query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term(field, "w1")), BooleanClause.Occur.SHOULD)
                    .add(TermQuery(Term(field, "w3")), BooleanClause.Occur.SHOULD)
                    .build()
            checkNoPositionsMatches(q, field, booleanArrayOf(true, true, true, true, false))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testReqOpt() {
        val q: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term(FIELD_WITH_OFFSETS, "w1")), BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term(FIELD_WITH_OFFSETS, "w3")), BooleanClause.Occur.MUST)
                .build()
        checkMatches(
            q,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 0, 0, 0, 2, 2, 2, 6, 8),
                intArrayOf(1, 0, 0, 0, 2, 1, 1, 3, 5, 3, 3, 9, 11),
                intArrayOf(2),
                intArrayOf(3, 0, 0, 0, 2, 2, 2, 6, 8, 5, 5, 15, 17),
                intArrayOf(4),
            ),
        )
        checkLabelCount(q, FIELD_WITH_OFFSETS, intArrayOf(2, 2, 0, 2, 0, 0))
    }

    @Test
    @Throws(IOException::class)
    fun testReqOptNoPositions() {
        for (field in arrayOf(FIELD_DOCS_ONLY, FIELD_FREQS)) {
            val q: Query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term(field, "w1")), BooleanClause.Occur.SHOULD)
                    .add(TermQuery(Term(field, "w3")), BooleanClause.Occur.MUST)
                    .build()
            checkNoPositionsMatches(q, field, booleanArrayOf(true, true, false, true, false))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testMinShouldMatch() {
        val w1: Query = NamedMatches.wrapQuery("w1", TermQuery(Term(FIELD_WITH_OFFSETS, "w1")))
        val w3: Query = NamedMatches.wrapQuery("w3", TermQuery(Term(FIELD_WITH_OFFSETS, "w3")))
        val w4: Query = TermQuery(Term(FIELD_WITH_OFFSETS, "w4"))
        val xx: Query = NamedMatches.wrapQuery("xx", TermQuery(Term(FIELD_WITH_OFFSETS, "xx")))
        val q: Query =
            BooleanQuery.Builder()
                .add(w3, BooleanClause.Occur.SHOULD)
                .add(
                    BooleanQuery.Builder()
                        .add(w1, BooleanClause.Occur.SHOULD)
                        .add(w4, BooleanClause.Occur.SHOULD)
                        .add(xx, BooleanClause.Occur.SHOULD)
                        .setMinimumNumberShouldMatch(2)
                        .build(),
                    BooleanClause.Occur.SHOULD,
                ).build()
        checkMatches(
            q,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 0, 0, 0, 2, 2, 2, 6, 8, 3, 3, 9, 11),
                intArrayOf(1, 1, 1, 3, 5, 3, 3, 9, 11),
                intArrayOf(2, 0, 0, 0, 2, 1, 1, 3, 5, 4, 4, 12, 14),
                intArrayOf(3, 0, 0, 0, 2, 2, 2, 6, 8, 3, 3, 9, 11, 5, 5, 15, 17),
                intArrayOf(4),
            ),
        )
        checkLabelCount(q, FIELD_WITH_OFFSETS, intArrayOf(3, 1, 3, 3, 0, 0))
        assertIsLeafMatch(q, FIELD_WITH_OFFSETS)
        checkSubMatches(
            q,
            arrayOf(arrayOf("w1", "w3"), arrayOf("w3"), arrayOf("w1", "xx"), arrayOf("w1", "w3"), emptyArray(), emptyArray()),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testMinShouldMatchNoPositions() {
        for (field in arrayOf(FIELD_FREQS, FIELD_DOCS_ONLY)) {
            val q: Query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term(field, "w3")), BooleanClause.Occur.SHOULD)
                    .add(
                        BooleanQuery.Builder()
                            .add(TermQuery(Term(field, "w1")), BooleanClause.Occur.SHOULD)
                            .add(TermQuery(Term(field, "w4")), BooleanClause.Occur.SHOULD)
                            .add(TermQuery(Term(field, "xx")), BooleanClause.Occur.SHOULD)
                            .setMinimumNumberShouldMatch(2)
                            .build(),
                        BooleanClause.Occur.SHOULD,
                    ).build()
            checkNoPositionsMatches(q, field, booleanArrayOf(true, true, true, true, false))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testExclusion() {
        val q: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term(FIELD_WITH_OFFSETS, "w3")), BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term(FIELD_WITH_OFFSETS, "zz")), BooleanClause.Occur.MUST_NOT)
                .build()
        checkMatches(
            q,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 2, 2, 6, 8),
                intArrayOf(1),
                intArrayOf(2),
                intArrayOf(3, 5, 5, 15, 17),
                intArrayOf(4),
            ),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testExclusionNoPositions() {
        for (field in arrayOf(FIELD_FREQS, FIELD_DOCS_ONLY)) {
            val q: Query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term(field, "w3")), BooleanClause.Occur.SHOULD)
                    .add(TermQuery(Term(field, "zz")), BooleanClause.Occur.MUST_NOT)
                    .build()
            checkNoPositionsMatches(q, field, booleanArrayOf(true, false, false, true, false))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testConjunction() {
        val q: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term(FIELD_WITH_OFFSETS, "w3")), BooleanClause.Occur.MUST)
                .add(TermQuery(Term(FIELD_WITH_OFFSETS, "w4")), BooleanClause.Occur.MUST)
                .build()
        checkMatches(
            q,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 2, 2, 6, 8, 3, 3, 9, 11),
                intArrayOf(1),
                intArrayOf(2),
                intArrayOf(3, 3, 3, 9, 11, 5, 5, 15, 17),
                intArrayOf(4),
            ),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testConjunctionNoPositions() {
        for (field in arrayOf(FIELD_FREQS, FIELD_DOCS_ONLY)) {
            val q: Query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term(field, "w3")), BooleanClause.Occur.MUST)
                    .add(TermQuery(Term(field, "w4")), BooleanClause.Occur.MUST)
                    .build()
            checkNoPositionsMatches(q, field, booleanArrayOf(true, false, false, true, false))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testWildcards() {
        val q: Query = PrefixQuery(Term(FIELD_WITH_OFFSETS, "x"))
        checkMatches(q, FIELD_WITH_OFFSETS, arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(2, 1, 1, 3, 5), intArrayOf(3), intArrayOf(4)))

        val rq: Query = RegexpQuery(Term(FIELD_WITH_OFFSETS, "w[1-2]"))
        checkMatches(
            rq,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 0, 0, 0, 2, 1, 1, 3, 5),
                intArrayOf(1, 0, 0, 0, 2, 2, 2, 6, 8),
                intArrayOf(2, 0, 0, 0, 2, 2, 2, 6, 8),
                intArrayOf(3, 0, 0, 0, 2, 1, 1, 3, 5, 2, 2, 6, 8, 4, 4, 12, 14),
                intArrayOf(4),
            ),
        )
        checkLabelCount(rq, FIELD_WITH_OFFSETS, intArrayOf(1, 1, 1, 1, 0))
        assertIsLeafMatch(rq, FIELD_WITH_OFFSETS)
    }

    @Test
    @Throws(IOException::class)
    fun testNoMatchWildcards() {
        val nomatch: Query = PrefixQuery(Term(FIELD_WITH_OFFSETS, "wibble"))
        val matches =
            searcher!!
                .createWeight(searcher!!.rewrite(nomatch), ScoreMode.COMPLETE_NO_SCORES, 1f)
                .matches(searcher!!.leafContexts[0], 0)
        assertNull(matches)
    }

    @Test
    @Throws(IOException::class)
    fun testWildcardsNoPositions() {
        for (field in arrayOf(FIELD_FREQS, FIELD_DOCS_ONLY)) {
            val q: Query = PrefixQuery(Term(field, "x"))
            checkNoPositionsMatches(q, field, booleanArrayOf(false, false, true, false, false))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSynonymQuery() {
        val q: Query =
            SynonymQuery.Builder(FIELD_WITH_OFFSETS)
                .addTerm(Term(FIELD_WITH_OFFSETS, "w1"))
                .addTerm(Term(FIELD_WITH_OFFSETS, "w2"))
                .build()
        checkMatches(
            q,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 0, 0, 0, 2, 1, 1, 3, 5),
                intArrayOf(1, 0, 0, 0, 2, 2, 2, 6, 8),
                intArrayOf(2, 0, 0, 0, 2, 2, 2, 6, 8),
                intArrayOf(3, 0, 0, 0, 2, 1, 1, 3, 5, 2, 2, 6, 8, 4, 4, 12, 14),
                intArrayOf(4),
            ),
        )
        assertIsLeafMatch(q, FIELD_WITH_OFFSETS)
    }

    @Test
    @Throws(IOException::class)
    fun testSynonymQueryNoPositions() {
        for (field in arrayOf(FIELD_FREQS, FIELD_DOCS_ONLY)) {
            val q: Query =
                SynonymQuery.Builder(field)
                    .addTerm(Term(field, "w1"))
                    .addTerm(Term(field, "w2"))
                    .build()
            checkNoPositionsMatches(q, field, booleanArrayOf(true, true, true, true, false))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testMultipleFields() {
        val q: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("id", "1")), BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term(FIELD_WITH_OFFSETS, "w3")), BooleanClause.Occur.MUST)
                .build()
        val w = searcher!!.createWeight(searcher!!.rewrite(q), ScoreMode.COMPLETE, 1f)

        val ctx = searcher!!.leafContexts[ReaderUtil.subIndex(1, searcher!!.leafContexts)]
        val m = w.matches(ctx, 1 - ctx.docBase)
        assertNotNull(m)
        checkFieldMatches(m.getMatches("id")!!, intArrayOf(-1, 0, 0, -1, -1))
        checkFieldMatches(m.getMatches(FIELD_WITH_OFFSETS)!!, intArrayOf(-1, 1, 1, 3, 5, 3, 3, 9, 11))
        assertNull(m.getMatches("bogus"))

        val fields = mutableSetOf<String>()
        for (field in m) {
            fields.add(field)
        }
        assertEquals(2, fields.size)
        assertTrue(fields.contains(FIELD_WITH_OFFSETS))
        assertTrue(fields.contains("id"))

        assertEquals(2, AssertingMatches.unWrap(m).subMatches.size)
    }

    //  0         1         2         3         4         5         6         7
    // "a phrase sentence with many phrase sentence iterations of a phrase sentence",

    @Test
    @Throws(IOException::class)
    fun testSloppyPhraseQueryWithRepeats() {
        val pq = PhraseQuery(10, FIELD_WITH_OFFSETS, "phrase", "sentence", "sentence")
        checkMatches(
            pq,
            FIELD_WITH_OFFSETS,
            arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(4, 1, 6, 2, 43, 2, 11, 9, 75, 5, 11, 28, 75, 6, 11, 35, 75)),
        )
        checkLabelCount(pq, FIELD_WITH_OFFSETS, intArrayOf(0, 0, 0, 0, 1))
        assertIsLeafMatch(pq, FIELD_WITH_OFFSETS)
    }

    @Test
    @Throws(IOException::class)
    fun testSloppyPhraseQuery() {
        val pq = PhraseQuery(4, FIELD_WITH_OFFSETS, "a", "sentence")
        checkMatches(
            pq,
            FIELD_WITH_OFFSETS,
            arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(4, 0, 2, 0, 17, 6, 9, 35, 59, 9, 11, 58, 75)),
        )
        assertIsLeafMatch(pq, FIELD_WITH_OFFSETS)
    }

    @Test
    @Throws(IOException::class)
    fun testExactPhraseQuery() {
        val pq = PhraseQuery(FIELD_WITH_OFFSETS, "phrase", "sentence")
        checkMatches(
            pq,
            FIELD_WITH_OFFSETS,
            arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(4, 1, 2, 2, 17, 5, 6, 28, 43, 10, 11, 60, 75)),
        )

        val a = Term(FIELD_WITH_OFFSETS, "a")
        val s = Term(FIELD_WITH_OFFSETS, "sentence")
        val pq2 =
            PhraseQuery.Builder()
                .add(a)
                .add(s, 2)
                .build()
        checkMatches(
            pq2,
            FIELD_WITH_OFFSETS,
            arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(4, 0, 2, 0, 17, 9, 11, 58, 75)),
        )
        assertIsLeafMatch(pq2, FIELD_WITH_OFFSETS)
    }

    //  0         1         2         3         4         5         6         7
    // "a phrase sentence with many phrase sentence iterations of a phrase sentence",

    @Test
    @Throws(IOException::class)
    fun testSloppyMultiPhraseQuery() {
        val p = Term(FIELD_WITH_OFFSETS, "phrase")
        val s = Term(FIELD_WITH_OFFSETS, "sentence")
        val i = Term(FIELD_WITH_OFFSETS, "iterations")
        val mpq =
            MultiPhraseQuery.Builder()
                .add(p)
                .add(arrayOf(s, i))
                .setSlop(4)
                .build()
        checkMatches(
            mpq,
            FIELD_WITH_OFFSETS,
            arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(4, 1, 2, 2, 17, 5, 6, 28, 43, 5, 7, 28, 54, 10, 11, 60, 75)),
        )
        assertIsLeafMatch(mpq, FIELD_WITH_OFFSETS)
    }

    @Test
    @Throws(IOException::class)
    fun testExactMultiPhraseQuery() {
        val mpq =
            MultiPhraseQuery.Builder()
                .add(Term(FIELD_WITH_OFFSETS, "sentence"))
                .add(arrayOf(Term(FIELD_WITH_OFFSETS, "with"), Term(FIELD_WITH_OFFSETS, "iterations")))
                .build()
        checkMatches(
            mpq,
            FIELD_WITH_OFFSETS,
            arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(4, 2, 3, 9, 22, 6, 7, 35, 54)),
        )

        val mpq2 =
            MultiPhraseQuery.Builder()
                .add(arrayOf(Term(FIELD_WITH_OFFSETS, "a"), Term(FIELD_WITH_OFFSETS, "many")))
                .add(Term(FIELD_WITH_OFFSETS, "phrase"))
                .build()
        checkMatches(
            mpq2,
            FIELD_WITH_OFFSETS,
            arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(4, 0, 1, 0, 8, 4, 5, 23, 34, 9, 10, 58, 66)),
        )
        assertIsLeafMatch(mpq2, FIELD_WITH_OFFSETS)
    }

    //  0         1         2         3         4         5         6         7
    // "a phrase sentence with many phrase sentence iterations of a phrase sentence",

    @Test
    @Throws(IOException::class)
    fun testPointQuery() {
        var pointQuery: Query =
            IndexOrDocValuesQuery(
                IntPoint.newExactQuery(FIELD_POINT, 10),
                NumericDocValuesField.newSlowExactQuery(FIELD_POINT, 10),
            )
        val t = Term(FIELD_WITH_OFFSETS, "w1")
        var query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(t), BooleanClause.Occur.MUST)
                .add(pointQuery, BooleanClause.Occur.MUST)
                .build()

        checkMatches(pointQuery, FIELD_WITH_OFFSETS, emptyArray())

        checkMatches(
            query,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 0, 0, 0, 2),
                intArrayOf(1, 0, 0, 0, 2),
                intArrayOf(2, 0, 0, 0, 2),
                intArrayOf(3, 0, 0, 0, 2, 2, 2, 6, 8),
                intArrayOf(4),
            ),
        )

        pointQuery =
            IndexOrDocValuesQuery(
                IntPoint.newExactQuery(FIELD_POINT, 11),
                NumericDocValuesField.newSlowExactQuery(FIELD_POINT, 11),
            )

        query =
            BooleanQuery.Builder()
                .add(TermQuery(t), BooleanClause.Occur.MUST)
                .add(pointQuery, BooleanClause.Occur.MUST)
                .build()
        checkMatches(query, FIELD_WITH_OFFSETS, emptyArray())

        query =
            BooleanQuery.Builder()
                .add(TermQuery(t), BooleanClause.Occur.MUST)
                .add(pointQuery, BooleanClause.Occur.SHOULD)
                .build()
        checkMatches(
            query,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0, 0, 0, 0, 2),
                intArrayOf(1, 0, 0, 0, 2),
                intArrayOf(2, 0, 0, 0, 2),
                intArrayOf(3, 0, 0, 0, 2, 2, 2, 6, 8),
                intArrayOf(4),
            ),
        )
    }

    @Test
    @Throws(IOException::class)
    fun testMinimalSeekingWithWildcards() {
        val reader = SeekCountingLeafReader(getOnlyLeafReader(this.reader!!))
        this.searcher = IndexSearcher(reader)
        val query: Query = PrefixQuery(Term(FIELD_WITH_OFFSETS, "w"))
        val w = searcher!!.createWeight(query.rewrite(searcher!!), ScoreMode.COMPLETE, 1f)

        // docs 0-3 match several different terms here, but we only seek to the first term and
        // then short-cut return; other terms are ignored until we try and iterate over matches
        val expectedSeeks = intArrayOf(1, 1, 1, 1, 6, 6)
        var i = 0
        for (ctx in reader.leaves()) {
            for (doc in 0..<ctx.reader().maxDoc()) {
                reader.seeks = 0
                w.matches(ctx, doc)
                assertEquals(expectedSeeks[i], reader.seeks, "Unexpected seek count on doc $doc")
                i++
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testFromSubIteratorsMethod() {
        class CountIterator(count: Int) : MatchesIterator {
            private var count = count
            private var max = count

            @Throws(IOException::class)
            override fun next(): Boolean {
                return if (count == 0) {
                    false
                } else {
                    count--
                    true
                }
            }

            override fun startPosition(): Int {
                return max - count
            }

            override fun endPosition(): Int {
                return max - count
            }

            @Throws(IOException::class)
            override fun startOffset(): Int {
                throw AssertionError()
            }

            @Throws(IOException::class)
            override fun endOffset(): Int {
                throw AssertionError()
            }

            override val subMatches: MatchesIterator?
                get() = throw AssertionError()

            override val query: Query
                get() = throw AssertionError()
        }

        val checks =
            arrayOf(
                intArrayOf(0),
                intArrayOf(1),
                intArrayOf(0, 0),
                intArrayOf(0, 1),
                intArrayOf(1, 0),
                intArrayOf(1, 1),
                intArrayOf(0, 0, 0),
                intArrayOf(0, 0, 1),
                intArrayOf(0, 1, 0),
                intArrayOf(1, 0, 0),
                intArrayOf(1, 0, 1),
                intArrayOf(1, 1, 0),
                intArrayOf(1, 1, 1),
            )

        for (counts in checks) {
            val its: MutableList<MatchesIterator> = counts.map { CountIterator(it) }.toMutableList()
            val expectedCount = counts.sum()
            val merged = DisjunctionMatchesIterator.fromSubIterators(its)
            var actualCount = 0
            while (merged!!.next()) {
                actualCount++
            }
            assertEquals(expectedCount, actualCount, "Sub-iterator count is not right for: ${counts.contentToString()}")
        }
    }

    private class SeekCountingLeafReader(reader: LeafReader) : FilterLeafReader(reader) {
        var seeks = 0

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            val terms = super.terms(field) ?: return null
            return object : FilterTerms(terms) {
                @Throws(IOException::class)
                override fun iterator(): TermsEnum {
                    val iterator = super.iterator()
                    return object : FilterTermsEnum(iterator) {
                        @Throws(IOException::class)
                        override fun seekExact(text: BytesRef): Boolean {
                            seeks++
                            return super.seekExact(text)
                        }
                    }
                }
            }
        }

        override val coreCacheHelper: CacheHelper?
            get() = null

        override val readerCacheHelper: CacheHelper?
            get() = null
    }
}
