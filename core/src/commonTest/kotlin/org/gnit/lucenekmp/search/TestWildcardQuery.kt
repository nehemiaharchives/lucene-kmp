/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.automaton.Operations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TestWildcardQuery tests the '*' and '?' wildcard characters. */
class TestWildcardQuery : LuceneTestCase() {
    @Test
    fun testEquals() {
        val wq1 = WildcardQuery(Term("field", "b*a"))
        val wq2 = WildcardQuery(Term("field", "b*a"))
        val wq3 = WildcardQuery(Term("field", "b*a"))

        // reflexive?
        assertEquals(wq1, wq2)
        assertEquals(wq2, wq1)

        // transitive?
        assertEquals(wq2, wq3)
        assertEquals(wq1, wq3)

        assertFalse(wq1.equals(null))

        val fq = FuzzyQuery(Term("field", "b*a"))
        assertFalse(wq1.equals(fq))
        assertFalse(fq.equals(wq1))
    }

    /**
     * Tests if a WildcardQuery that has no wildcard in the term is rewritten to a single TermQuery.
     * The boost should be preserved, and the rewrite should return a ConstantScoreQuery if the
     * WildcardQuery had a ConstantScore rewriteMethod.
     */
    @Test
    @Throws(Exception::class)
    fun testTermWithoutWildcard() {
        val indexStore = getIndexStore("field", arrayOf("nowildcard", "nowildcardx"))
        val reader: IndexReader = DirectoryReader.open(indexStore)
        val searcher = newSearcher(reader)

        val wq: MultiTermQuery = WildcardQuery(Term("field", "nowildcard"))
        assertMatches(searcher, wq, 1)

        var q =
            searcher.rewrite(
                WildcardQuery(
                    Term("field", "nowildcard"),
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    MultiTermQuery.SCORING_BOOLEAN_REWRITE,
                ),
            )
        assertTrue(q is TermQuery)

        q =
            searcher.rewrite(
                WildcardQuery(
                    Term("field", "nowildcard"),
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    MultiTermQuery.CONSTANT_SCORE_REWRITE,
                ),
            )
        assertTrue(q is MultiTermQueryConstantScoreWrapper<*>)

        q =
            searcher.rewrite(
                WildcardQuery(
                    Term("field", "nowildcard"),
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE,
                ),
            )
        assertTrue(q is MultiTermQueryConstantScoreBlendedWrapper<*>)

        q =
            searcher.rewrite(
                WildcardQuery(
                    Term("field", "nowildcard"),
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE,
                ),
            )
        assertTrue(q is ConstantScoreQuery)
        reader.close()
        indexStore.close()
    }

    /** Tests if a WildcardQuery with an empty term is rewritten to an empty BooleanQuery */
    @Test
    @Throws(Exception::class)
    fun testEmptyTerm() {
        val indexStore = getIndexStore("field", arrayOf("nowildcard", "nowildcardx"))
        val reader: IndexReader = DirectoryReader.open(indexStore)
        val searcher = newSearcher(reader)

        val wq: MultiTermQuery =
            WildcardQuery(
                Term("field", ""),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                MultiTermQuery.SCORING_BOOLEAN_REWRITE,
            )
        assertMatches(searcher, wq, 0)
        val q = searcher.rewrite(wq)
        assertTrue(q is MatchNoDocsQuery)
        reader.close()
        indexStore.close()
    }

    /**
     * Tests if a WildcardQuery that has only a trailing * in the term is rewritten to a single
     * PrefixQuery. The boost and rewriteMethod should be preserved.
     */
    @Test
    @Throws(Exception::class)
    fun testPrefixTerm() {
        val indexStore = getIndexStore("field", arrayOf("prefix", "prefixx"))
        val reader: IndexReader = DirectoryReader.open(indexStore)
        val searcher = newSearcher(reader)

        var wq: MultiTermQuery = WildcardQuery(Term("field", "prefix*"))
        assertMatches(searcher, wq, 2)

        wq = WildcardQuery(Term("field", "*"))
        assertMatches(searcher, wq, 2)
        val terms: Terms = MultiTerms.getTerms(searcher.indexReader, "field")!!
        assertFalse(wq.getTermsEnum(terms)::class.simpleName!!.contains("AutomatonTermsEnum"))
        reader.close()
        indexStore.close()
    }

    /** Tests Wildcard queries with an asterisk. */
    @Test
    @Throws(Exception::class)
    fun testAsterisk() {
        val indexStore = getIndexStore("body", arrayOf("metal", "metals"))
        val reader: IndexReader = DirectoryReader.open(indexStore)
        val searcher = newSearcher(reader)
        val query1: Query = TermQuery(Term("body", "metal"))
        val query2: Query = WildcardQuery(Term("body", "metal*"))
        val query3: Query = WildcardQuery(Term("body", "m*tal"))
        val query4: Query = WildcardQuery(Term("body", "m*tal*"))
        val query5: Query = WildcardQuery(Term("body", "m*tals"))

        val query6 = BooleanQuery.Builder()
        query6.add(query5, BooleanClause.Occur.SHOULD)

        val query7 = BooleanQuery.Builder()
        query7.add(query3, BooleanClause.Occur.SHOULD)
        query7.add(query5, BooleanClause.Occur.SHOULD)

        // Queries do not automatically lower-case search terms:
        val query8: Query = WildcardQuery(Term("body", "M*tal*"))

        assertMatches(searcher, query1, 1)
        assertMatches(searcher, query2, 2)
        assertMatches(searcher, query3, 1)
        assertMatches(searcher, query4, 2)
        assertMatches(searcher, query5, 1)
        assertMatches(searcher, query6.build(), 1)
        assertMatches(searcher, query7.build(), 2)
        assertMatches(searcher, query8, 0)
        assertMatches(searcher, WildcardQuery(Term("body", "*tall")), 0)
        assertMatches(searcher, WildcardQuery(Term("body", "*tal")), 1)
        assertMatches(searcher, WildcardQuery(Term("body", "*tal*")), 2)
        reader.close()
        indexStore.close()
    }

    /**
     * Tests Wildcard queries with a question mark.
     *
     * @throws Exception if an error occurs
     */
    @Test
    @Throws(Exception::class)
    fun testQuestionmark() {
        val indexStore = getIndexStore("body", arrayOf("metal", "metals", "mXtals", "mXtXls"))
        val reader: IndexReader = DirectoryReader.open(indexStore)
        val searcher = newSearcher(reader)
        val query1: Query = WildcardQuery(Term("body", "m?tal"))
        val query2: Query = WildcardQuery(Term("body", "metal?"))
        val query3: Query = WildcardQuery(Term("body", "metals?"))
        val query4: Query = WildcardQuery(Term("body", "m?t?ls"))
        val query5: Query = WildcardQuery(Term("body", "M?t?ls"))
        val query6: Query = WildcardQuery(Term("body", "meta??"))

        assertMatches(searcher, query1, 1)
        assertMatches(searcher, query2, 1)
        assertMatches(searcher, query3, 0)
        assertMatches(searcher, query4, 3)
        assertMatches(searcher, query5, 0)
        assertMatches(searcher, query6, 1) // Query: 'meta??' matches 'metals' not 'metal'
        reader.close()
        indexStore.close()
    }

    /** Tests if wildcard escaping works */
    @Test
    @Throws(Exception::class)
    fun testEscapes() {
        val indexStore =
            getIndexStore(
                "field",
                arrayOf("foo*bar", "foo??bar", "fooCDbar", "fooSOMETHINGbar", "foo\\"),
            )
        val reader: IndexReader = DirectoryReader.open(indexStore)
        val searcher = newSearcher(reader)

        // without escape: matches foo??bar, fooCDbar, foo*bar, and fooSOMETHINGbar
        var unescaped = WildcardQuery(Term("field", "foo*bar"))
        assertMatches(searcher, unescaped, 4)

        // with escape: only matches foo*bar
        var escaped = WildcardQuery(Term("field", "foo\\*bar"))
        assertMatches(searcher, escaped, 1)

        // without escape: matches foo??bar and fooCDbar
        unescaped = WildcardQuery(Term("field", "foo??bar"))
        assertMatches(searcher, unescaped, 2)

        // with escape: matches foo??bar only
        escaped = WildcardQuery(Term("field", "foo\\?\\?bar"))
        assertMatches(searcher, escaped, 1)

        // check escaping at end: lenient parse yields "foo\"
        val atEnd = WildcardQuery(Term("field", "foo\\"))
        assertMatches(searcher, atEnd, 1)

        reader.close()
        indexStore.close()
    }

    @Throws(Exception::class)
    private fun getIndexStore(field: String, contents: Array<String>): Directory {
        val indexStore = newDirectory()
        val writer = RandomIndexWriter(random(), indexStore)
        for (i in contents.indices) {
            val doc = Document()
            doc.add(newTextField(field, contents[i], Field.Store.YES))
            writer.addDocument(doc)
        }
        writer.close()

        return indexStore
    }

    @Throws(Exception::class)
    private fun assertMatches(searcher: IndexSearcher, q: Query, expectedMatches: Int) {
        val result = searcher.search(q, 1000).scoreDocs
        assertEquals(expectedMatches, result.size)
    }

    /**
     * Test that wild card queries are parsed to the correct type and are searched correctly. This
     * test looks at both parsing and execution of wildcard queries. Although placed here, it also
     * tests prefix queries, verifying that prefix queries are not parsed into wild card queries, and
     * vice-versa.
     */
    @Test
    @Throws(Exception::class)
    fun testParsingAndSearching() {
        val field = "content"
        val docs =
            arrayOf(
                "\\ abcdefg1",
                "\\79 hijklmn1",
                "\\\\ opqrstu1",
            )

        // queries that should find all docs
        val matchAll =
            arrayOf<Query>(
                WildcardQuery(Term(field, "*")),
                WildcardQuery(Term(field, "*1")),
                WildcardQuery(Term(field, "**1")),
                WildcardQuery(Term(field, "*?")),
                WildcardQuery(Term(field, "*?1")),
                WildcardQuery(Term(field, "?*1")),
                WildcardQuery(Term(field, "**")),
                WildcardQuery(Term(field, "***")),
                WildcardQuery(Term(field, "\\\\*")),
            )

        // queries that should find no docs
        val matchNone =
            arrayOf<Query>(
                WildcardQuery(Term(field, "a*h")),
                WildcardQuery(Term(field, "a?h")),
                WildcardQuery(Term(field, "*a*h")),
                WildcardQuery(Term(field, "?a")),
                WildcardQuery(Term(field, "a?")),
            )

        val matchOneDocPrefix =
            arrayOf(
                arrayOf(
                    PrefixQuery(Term(field, "a")),
                    PrefixQuery(Term(field, "ab")),
                    PrefixQuery(Term(field, "abc")),
                ), // these should find only doc 0
                arrayOf(
                    PrefixQuery(Term(field, "h")),
                    PrefixQuery(Term(field, "hi")),
                    PrefixQuery(Term(field, "hij")),
                    PrefixQuery(Term(field, "\\7")),
                ), // these should find only doc 1
                arrayOf(
                    PrefixQuery(Term(field, "o")),
                    PrefixQuery(Term(field, "op")),
                    PrefixQuery(Term(field, "opq")),
                    PrefixQuery(Term(field, "\\\\")),
                ), // these should find only doc 2
            )

        val matchOneDocWild =
            arrayOf(
                arrayOf(
                    WildcardQuery(Term(field, "*a*")), // these should find only doc 0
                    WildcardQuery(Term(field, "*ab*")),
                    WildcardQuery(Term(field, "*abc**")),
                    WildcardQuery(Term(field, "ab*e*")),
                    WildcardQuery(Term(field, "*g?")),
                    WildcardQuery(Term(field, "*f?1")),
                ),
                arrayOf(
                    WildcardQuery(Term(field, "*h*")), // these should find only doc 1
                    WildcardQuery(Term(field, "*hi*")),
                    WildcardQuery(Term(field, "*hij**")),
                    WildcardQuery(Term(field, "hi*k*")),
                    WildcardQuery(Term(field, "*n?")),
                    WildcardQuery(Term(field, "*m?1")),
                    WildcardQuery(Term(field, "hij**")),
                ),
                arrayOf(
                    WildcardQuery(Term(field, "*o*")), // these should find only doc 2
                    WildcardQuery(Term(field, "*op*")),
                    WildcardQuery(Term(field, "*opq**")),
                    WildcardQuery(Term(field, "op*q*")),
                    WildcardQuery(Term(field, "*u?")),
                    WildcardQuery(Term(field, "*t?1")),
                    WildcardQuery(Term(field, "opq**")),
                ),
            )

        // prepare the index
        val dir = newDirectory()
        val iw =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
            )
        for (i in docs.indices) {
            val doc = Document()
            doc.add(newTextField(field, docs[i], Field.Store.NO))
            iw.addDocument(doc)
        }
        iw.close()

        val reader: IndexReader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)

        // test queries that must find all
        for (q in matchAll) {
            if (VERBOSE) println("matchAll: q=$q ${q::class.qualifiedName}")
            val hits = searcher.search(q, 1000).scoreDocs
            assertEquals(docs.size, hits.size)
        }

        // test queries that must find none
        for (q in matchNone) {
            if (VERBOSE) println("matchNone: q=$q ${q::class.qualifiedName}")
            val hits = searcher.search(q, 1000).scoreDocs
            assertEquals(0, hits.size)
        }

        // thest the prefi queries find only one doc
        for (i in matchOneDocPrefix.indices) {
            for (j in matchOneDocPrefix[i].indices) {
                val q = matchOneDocPrefix[i][j]
                if (VERBOSE) {
                    println("match 1 prefix: doc=${docs[i]} q=$q ${q::class.qualifiedName}")
                }
                val hits = searcher.search(q, 1000).scoreDocs
                assertEquals(1, hits.size)
                assertEquals(i, hits[0].doc)
            }
        }

        // test the wildcard queries find only one doc
        for (i in matchOneDocWild.indices) {
            for (j in matchOneDocWild[i].indices) {
                val q = matchOneDocWild[i][j]
                if (VERBOSE) {
                    println("match 1 wild: doc=${docs[i]} q=$q ${q::class.qualifiedName}")
                }
                val hits = searcher.search(q, 1000).scoreDocs
                assertEquals(1, hits.size)
                assertEquals(i, hits[0].doc)
            }
        }

        reader.close()
        dir.close()
    }

    /** Tests large Wildcard queries */
    @Test
    @Throws(Exception::class)
    fun testLarge() {
        // big string from a user
        val big =
            "{group-bm-http-server-02083.node.dm.reg,group-bm-http-server-02082.node.dm.reg,group-bm-http-server-02081.node.dm.reg,group-bm-http-server-02080.node.dm.reg,group-bm-http-server-02079.node.dm.reg,group-bm-http-server-02078.node.dm.reg,group-bm-http-server-02077.node.dm.reg,group-bm-http-server-02076.node.dm.reg,group-bm-http-server-02073.node.dm.reg,group-bm-http-server-02070.node.dm.reg,group-bm-http-server-02067.node.dm.reg,group-bm-http-server-02064.node.dm.reg,group-bm-http-server-02029.node.dm.reg,group-bm-http-server-02028.node.dm.reg,group-bm-http-server-02027.node.dm.reg,group-bm-http-server-02026.node.dm.reg,group-bm-http-server-02025.node.dm.reg,group-bm-http-server-02023.node.dm.reg,group-bm-http-server-02022.node.dm.reg,group-bm-http-server-02021.node.dm.reg,group-bm-http-server-02020.node.dm.reg,group-bm-http-server-02019.node.dm.reg,group-bm-http-server-02018.node.dm.reg,group-bm-http-server-02016.node.dm.reg,group-bm-http-server-02015.node.dm.reg,group-bm-http-server-02014.node.dm.reg,group-bm-http-server-02009.node.dm.reg,group-bm-http-server-02007.node.dm.reg,group-bm-http-server-02004.node.dm.reg,group-bm-http-server-02003.node.dm.reg,group-bm-http-server-02002.node.dm.reg,group-bm-http-server-01311.node.dm.reg,group-bm-http-server-01309.node.dm.reg,group-bm-http-server-01307.node.dm.reg}"
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newStringField("body", big, Field.Store.YES))
        writer.addDocument(doc)
        writer.close()

        val reader: IndexReader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)
        val query: Query = WildcardQuery(Term("body", "$big*"))
        assertMatches(searcher, query, 1)

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCostEstimate() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        for (i in 0..<1000) {
            var doc = Document()
            doc.add(newStringField("body", "foo bar", Field.Store.NO))
            writer.addDocument(doc)
            doc = Document()
            doc.add(newStringField("body", "foo wuzzle", Field.Store.NO))
            writer.addDocument(doc)
            doc = Document()
            doc.add(newStringField("body", "bar $i", Field.Store.NO))
            writer.addDocument(doc)
        }
        writer.flush()
        writer.forceMerge(1)
        writer.close()

        val reader: IndexReader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)
        val lrc: LeafReaderContext = reader.leaves()[0]

        var query = WildcardQuery(Term("body", "foo*"))
        var rewritten = searcher.rewrite(query)
        var weight = rewritten.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f)
        var supplier = weight.scorerSupplier(lrc)!!
        assertEquals(2000L, supplier.cost()) // Sum the terms doc freqs

        query = WildcardQuery(Term("body", "bar*"))
        rewritten = searcher.rewrite(query)
        weight = rewritten.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f)
        supplier = weight.scorerSupplier(lrc)!!
        assertEquals(3000L, supplier.cost()) // Too many terms, assume worst-case all terms match

        reader.close()
        dir.close()
    }
}
