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

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [PhraseQuery].
 *
 * @see TestPositionIncrement
 */
class TestPhraseQuery : LuceneTestCase() {
    /** threshold for comparing floats */
    private val SCORE_COMP_THRESH = 1e-6f

    private var query: PhraseQuery? = null

    @BeforeTest
    fun beforeClass() {
        directory = newDirectory()
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(MockTokenizer(MockTokenizer.WHITESPACE, false))
                }

                override fun getPositionIncrementGap(fieldName: String?): Int {
                    return 100
                }
            }
        val writer = RandomIndexWriter(random(), directory!!, analyzer)

        var doc = Document()
        doc.add(newTextField("field", "one two three four five", Field.Store.YES))
        doc.add(newTextField("repeated", "this is a repeated field - first part", Field.Store.YES))
        val repeatedField =
            newTextField("repeated", "second part of a repeated field", Field.Store.YES)
        doc.add(repeatedField)
        doc.add(newTextField("palindrome", "one two three two one", Field.Store.YES))
        writer.addDocument(doc)

        doc = Document()
        doc.add(newTextField("nonexist", "phrase exist notexist exist found", Field.Store.YES))
        writer.addDocument(doc)

        doc = Document()
        doc.add(newTextField("nonexist", "phrase exist notexist exist found", Field.Store.YES))
        writer.addDocument(doc)

        reader = writer.getReader(true, false)
        writer.close()

        searcher = IndexSearcher(reader!!)
    }

    @AfterTest
    fun afterClass() {
        searcher = null
        reader?.close()
        reader = null
        directory?.close()
        directory = null
    }

    @Test
    fun testNotCloseEnough() {
        query = PhraseQuery(2, "field", "one", "five")
        val hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(0, hits.size)
        QueryUtils.check(random(), query!!, searcher!!)
    }

    @Test
    fun testBarelyCloseEnough() {
        query = PhraseQuery(3, "field", "one", "five")
        val hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size)
        QueryUtils.check(random(), query!!, searcher!!)
    }

    /** Ensures slop of 0 works for exact matches, but not reversed */
    @Test
    fun testExact() {
        // slop is zero by default
        query = PhraseQuery("field", "four", "five")
        var hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "exact match")
        QueryUtils.check(random(), query!!, searcher!!)

        query = PhraseQuery("field", "two", "one")
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(0, hits.size, "reverse not exact")
        QueryUtils.check(random(), query!!, searcher!!)
    }

    @Test
    fun testSlop1() {
        // Ensures slop of 1 works with terms in order.
        query = PhraseQuery(1, "field", "one", "two")
        var hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "in order")
        QueryUtils.check(random(), query!!, searcher!!)

        // Ensures slop of 1 does not work for phrases out of order;
        // must be at least 2.
        query = PhraseQuery(1, "field", "two", "one")
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(0, hits.size, "reversed, slop not 2 or more")
        QueryUtils.check(random(), query!!, searcher!!)
    }

    /** As long as slop is at least 2, terms can be reversed */
    @Test
    fun testOrderDoesntMatter() {
        // must be at least two for reverse order match
        query = PhraseQuery(2, "field", "two", "one")
        var hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "just sloppy enough")
        QueryUtils.check(random(), query!!, searcher!!)

        query = PhraseQuery(2, "field", "three", "one")
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(0, hits.size, "not sloppy enough")
        QueryUtils.check(random(), query!!, searcher!!)
    }

    /** slop is the total number of positional moves allowed to line up a phrase */
    @Test
    fun testMultipleTerms() {
        query = PhraseQuery(2, "field", "one", "three", "five")
        var hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "two total moves")
        QueryUtils.check(random(), query!!, searcher!!)

        // it takes six moves to match this phrase
        query = PhraseQuery(5, "field", "five", "three", "one")
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(0, hits.size, "slop of 5 not close enough")
        QueryUtils.check(random(), query!!, searcher!!)

        query = PhraseQuery(6, "field", "five", "three", "one")
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "slop of 6 just right")
        QueryUtils.check(random(), query!!, searcher!!)
    }

    @Test
    fun testPhraseQueryWithStopAnalyzer() {
        val directory = newDirectory()
        val stopAnalyzer =
            MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET)
        val writer =
            RandomIndexWriter(random(), directory, newIndexWriterConfig(stopAnalyzer))
        val doc = Document()
        doc.add(newTextField("field", "the stop words are here", Field.Store.YES))
        writer.addDocument(doc)
        val reader = writer.getReader(true, false)
        writer.close()

        val searcher = newSearcher(reader)

        // valid exact phrase query
        val query = PhraseQuery("field", "stop", "words")
        val hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)
        QueryUtils.check(random(), query, searcher)

        reader.close()
        directory.close()
    }

    @Test
    fun testPhraseQueryInConjunctionScorer() {
        val directory = newDirectory()
        var writer = RandomIndexWriter(random(), directory)

        var doc = Document()
        doc.add(newTextField("source", "marketing info", Field.Store.YES))
        writer.addDocument(doc)

        doc = Document()
        doc.add(newTextField("contents", "foobar", Field.Store.YES))
        doc.add(newTextField("source", "marketing info", Field.Store.YES))
        writer.addDocument(doc)

        var reader = writer.getReader(true, false)
        writer.close()

        var searcher = newSearcher(reader)

        var phraseQuery = PhraseQuery("source", "marketing", "info")
        var hits = searcher.search(phraseQuery, 1000).scoreDocs
        assertEquals(2, hits.size)
        QueryUtils.check(random(), phraseQuery, searcher)

        var termQuery = TermQuery(Term("contents", "foobar"))
        var booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(termQuery, BooleanClause.Occur.MUST)
        booleanQuery.add(phraseQuery, BooleanClause.Occur.MUST)
        hits = searcher.search(booleanQuery.build(), 1000).scoreDocs
        assertEquals(1, hits.size)
        QueryUtils.check(random(), termQuery, searcher)

        reader.close()

        writer =
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.CREATE)
            )
        doc = Document()
        doc.add(newTextField("contents", "map entry woo", Field.Store.YES))
        writer.addDocument(doc)

        doc = Document()
        doc.add(newTextField("contents", "woo map entry", Field.Store.YES))
        writer.addDocument(doc)

        doc = Document()
        doc.add(newTextField("contents", "map foobarword entry woo", Field.Store.YES))
        writer.addDocument(doc)

        reader = writer.getReader(true, false)
        writer.close()

        searcher = newSearcher(reader)

        termQuery = TermQuery(Term("contents", "woo"))
        phraseQuery = PhraseQuery("contents", "map", "entry")

        hits = searcher.search(termQuery, 1000).scoreDocs
        assertEquals(3, hits.size)
        hits = searcher.search(phraseQuery, 1000).scoreDocs
        assertEquals(2, hits.size)

        booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(termQuery, BooleanClause.Occur.MUST)
        booleanQuery.add(phraseQuery, BooleanClause.Occur.MUST)
        hits = searcher.search(booleanQuery.build(), 1000).scoreDocs
        assertEquals(2, hits.size)

        booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(phraseQuery, BooleanClause.Occur.MUST)
        booleanQuery.add(termQuery, BooleanClause.Occur.MUST)
        hits = searcher.search(booleanQuery.build(), 1000).scoreDocs
        assertEquals(2, hits.size)
        QueryUtils.check(random(), booleanQuery.build(), searcher)

        reader.close()
        directory.close()
    }

    @Test
    fun testSlopScoring() {
        val directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(newLogMergePolicy())
                    .setSimilarity(BM25Similarity())
            )

        val doc = Document()
        doc.add(newTextField("field", "foo firstname lastname foo", Field.Store.YES))
        writer.addDocument(doc)

        val doc2 = Document()
        doc2.add(newTextField("field", "foo firstname zzz lastname foo", Field.Store.YES))
        writer.addDocument(doc2)

        val doc3 = Document()
        doc3.add(newTextField("field", "foo firstname zzz yyy lastname foo", Field.Store.YES))
        writer.addDocument(doc3)

        val reader = writer.getReader(true, false)
        writer.close()

        val searcher = newSearcher(reader)
        searcher.similarity = ClassicSimilarity()
        val query = PhraseQuery(Int.MAX_VALUE, "field", "firstname", "lastname")
        val hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        // Make sure that those matches where the terms appear closer to
        // each other get a higher score:
        assertEquals(1.0f, hits[0].score, 0.01f)
        assertEquals(0, hits[0].doc)
        assertEquals(0.63f, hits[1].score, 0.01f)
        assertEquals(1, hits[1].doc)
        assertEquals(0.47f, hits[2].score, 0.01f)
        assertEquals(2, hits[2].doc)
        QueryUtils.check(random(), query, searcher)
        reader.close()
        directory.close()
    }

    @Test
    fun testToString() {
        var q = PhraseQuery("field", *emptyArray<String>())
        assertEquals("\"\"", q.toString())

        var builder = PhraseQuery.Builder()
        builder.add(Term("field", "hi"), 1)
        q = builder.build()
        assertEquals("field:\"? hi\"", q.toString())

        builder = PhraseQuery.Builder()
        builder.add(Term("field", "hi"), 1)
        builder.add(Term("field", "test"), 5)
        q = builder.build() // Query "this hi this is a test is"

        assertEquals("field:\"? hi ? ? ? test\"", q.toString())

        builder = PhraseQuery.Builder()
        builder.add(Term("field", "hi"), 1)
        builder.add(Term("field", "hello"), 1)
        builder.add(Term("field", "test"), 5)
        q = builder.build()
        assertEquals("field:\"? hi|hello ? ? ? test\"", q.toString())

        builder = PhraseQuery.Builder()
        builder.add(Term("field", "hi"), 1)
        builder.add(Term("field", "hello"), 1)
        builder.add(Term("field", "test"), 5)
        builder.setSlop(5)
        q = builder.build()
        assertEquals("field:\"? hi|hello ? ? ? test\"~5", q.toString())
    }

    @Test
    fun testWrappedPhrase() {
        query = PhraseQuery(100, "repeated", "first", "part", "second", "part")

        var hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "slop of 100 just right")
        QueryUtils.check(random(), query!!, searcher!!)

        query = PhraseQuery(99, "repeated", "first", "part", "second", "part")

        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(0, hits.size, "slop of 99 not enough")
        QueryUtils.check(random(), query!!, searcher!!)
    }

    // work on two docs like this: "phrase exist notexist exist found"
    @Test
    fun testNonExistingPhrase() {
        // phrase without repetitions that exists in 2 docs
        query = PhraseQuery(2, "nonexist", "phrase", "notexist", "found")

        var hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(2, hits.size, "phrase without repetitions exists in 2 docs")
        QueryUtils.check(random(), query!!, searcher!!)

        // phrase with repetitions that exists in 2 docs
        query = PhraseQuery(1, "nonexist", "phrase", "exist", "exist")

        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(2, hits.size, "phrase with repetitions exists in two docs")
        QueryUtils.check(random(), query!!, searcher!!)

        // phrase I with repetitions that does not exist in any doc
        query = PhraseQuery(1000, "nonexist", "phrase", "notexist", "phrase")

        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(0, hits.size, "nonexisting phrase with repetitions does not exist in any doc")
        QueryUtils.check(random(), query!!, searcher!!)

        // phrase II with repetitions that does not exist in any doc
        query = PhraseQuery(1000, "nonexist", "phrase", "exist", "exist", "exist")

        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(0, hits.size, "nonexisting phrase with repetitions does not exist in any doc")
        QueryUtils.check(random(), query!!, searcher!!)
    }

    /**
     * Working on a 2 fields like this: Field("field", "one two three four five") Field("palindrome",
     * "one two three two one") Phrase of size 2 occuriong twice, once in order and once in reverse,
     * because doc is a palyndrome, is counted twice. Also, in this case order in query does not
     * matter. Also, when an exact match is found, both sloppy scorer and exact scorer scores the
     * same.
     */
    @Test
    fun testPalyndrome2() {
        // search on non palyndrome, find phrase with no slop, using exact phrase scorer
        query = PhraseQuery("field", "two", "three") // to use exact phrase scorer
        var hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "phrase found with exact phrase scorer")
        val score0 = hits[0].score
        // System.out.println("(exact) field: two three: "+score0);
        QueryUtils.check(random(), query!!, searcher!!)

        // search on non palyndrome, find phrase with slop 2, though no slop required here.
        query = PhraseQuery("field", "two", "three") // to use sloppy scorer
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "just sloppy enough")
        val score1 = hits[0].score
        // System.out.println("(sloppy) field: two three: "+score1);
        assertEquals(
            score0,
            score1,
            SCORE_COMP_THRESH,
            "exact scorer and sloppy scorer score the same when slop does not matter"
        )
        QueryUtils.check(random(), query!!, searcher!!)

        // search ordered in palyndrome, find it twice
        query =
            PhraseQuery(
                2,
                "palindrome",
                "two",
                "three"
            ) // must be at least two for both ordered and reversed to match
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "just sloppy enough")
        // float score2 = hits[0].score;
        // System.out.println("palindrome: two three: "+score2);
        QueryUtils.check(random(), query!!, searcher!!)

        // commented out for sloppy-phrase efficiency (issue 736) - see SloppyPhraseScorer.phraseFreq().
        // assertTrue("ordered scores higher in palindrome",score1+SCORE_COMP_THRESH<score2);

        // search reveresed in palyndrome, find it twice
        query =
            PhraseQuery(
                2,
                "palindrome",
                "three",
                "two"
            ) // must be at least two for both ordered and reversed to match
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "just sloppy enough")
        // float score3 = hits[0].score;
        // System.out.println("palindrome: three two: "+score3);
        QueryUtils.check(random(), query!!, searcher!!)

        // commented out for sloppy-phrase efficiency (issue 736) - see SloppyPhraseScorer.phraseFreq().
        // assertTrue("reversed scores higher in palindrome",score1+SCORE_COMP_THRESH<score3);
        // assertEquals("ordered or reversed does not matter",score2, score3, SCORE_COMP_THRESH);
    }

    /**
     * Working on a 2 fields like this: Field("field", "one two three four five") Field("palindrome",
     * "one two three two one") Phrase of size 3 occuriong twice, once in order and once in reverse,
     * because doc is a palyndrome, is counted twice. Also, in this case order in query does not
     * matter. Also, when an exact match is found, both sloppy scorer and exact scorer scores the
     * same.
     */
    @Test
    fun testPalyndrome3() {
        // search on non palyndrome, find phrase with no slop, using exact phrase scorer
        // slop=0 to use exact phrase scorer
        query = PhraseQuery(0, "field", "one", "two", "three")
        var hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "phrase found with exact phrase scorer")
        val score0 = hits[0].score
        // System.out.println("(exact) field: one two three: "+score0);
        QueryUtils.check(random(), query!!, searcher!!)

        // just make sure no exc:
        searcher!!.explain(query!!, 0)

        // search on non palyndrome, find phrase with slop 3, though no slop required here.
        // slop=4 to use sloppy scorer
        query = PhraseQuery(4, "field", "one", "two", "three")
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "just sloppy enough")
        val score1 = hits[0].score
        // System.out.println("(sloppy) field: one two three: "+score1);
        assertEquals(
            score0,
            score1,
            SCORE_COMP_THRESH,
            "exact scorer and sloppy scorer score the same when slop does not matter"
        )
        QueryUtils.check(random(), query!!, searcher!!)

        // search ordered in palyndrome, find it twice
        // slop must be at least four for both ordered and reversed to match
        query = PhraseQuery(4, "palindrome", "one", "two", "three")
        hits = searcher!!.search(query!!, 1000).scoreDocs

        // just make sure no exc:
        searcher!!.explain(query!!, 0)

        assertEquals(1, hits.size, "just sloppy enough")
        // float score2 = hits[0].score;
        // System.out.println("palindrome: one two three: "+score2);
        QueryUtils.check(random(), query!!, searcher!!)

        // commented out for sloppy-phrase efficiency (issue 736) - see SloppyPhraseScorer.phraseFreq().
        // assertTrue("ordered scores higher in palindrome",score1+SCORE_COMP_THRESH<score2);

        // search reveresed in palyndrome, find it twice
        // must be at least four for both ordered and reversed to match
        query = PhraseQuery(4, "palindrome", "three", "two", "one")
        hits = searcher!!.search(query!!, 1000).scoreDocs
        assertEquals(1, hits.size, "just sloppy enough")
        // float score3 = hits[0].score;
        // System.out.println("palindrome: three two one: "+score3);
        QueryUtils.check(random(), query!!, searcher!!)

        // commented out for sloppy-phrase efficiency (issue 736) - see SloppyPhraseScorer.phraseFreq().
        // assertTrue("reversed scores higher in palindrome",score1+SCORE_COMP_THRESH<score3);
        // assertEquals("ordered or reversed does not matter",score2, score3, SCORE_COMP_THRESH);
    }

    // LUCENE-1280
    @Test
    fun testEmptyPhraseQuery() {
        val q2 = BooleanQuery.Builder()
        q2.add(PhraseQuery("field", *emptyArray<String>()), BooleanClause.Occur.MUST)
        q2.build().toString()
    }

    /* test that a single term is rewritten to a term query */
    @Test
    fun testRewrite() {
        val pq = PhraseQuery("foo", "bar")
        val rewritten = pq.rewrite(searcher!!)
        assertTrue(rewritten is TermQuery)
    }

    /** Tests PhraseQuery with terms at the same position in the query. */
    @Test
    fun testZeroPosIncr() {
        val dir = newDirectory()
        val tokens = Array(3) { Token() }
        tokens[0].append("a")
        tokens[0].setPositionIncrement(1)
        tokens[1].append("aa")
        tokens[1].setPositionIncrement(0)
        tokens[2].append("b")
        tokens[2].setPositionIncrement(1)

        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(TextField("field", CannedTokenStream(*tokens)))
        writer.addDocument(doc)
        val r = writer.getReader(true, false)
        writer.close()
        val searcher = newSearcher(r)

        // Sanity check; simple "a b" phrase:
        var pqBuilder = PhraseQuery.Builder()
        pqBuilder.add(Term("field", "a"), 0)
        pqBuilder.add(Term("field", "b"), 1)
        assertEquals(1, searcher.count(pqBuilder.build()))

        // Now with "a|aa b"
        pqBuilder = PhraseQuery.Builder()
        pqBuilder.add(Term("field", "a"), 0)
        pqBuilder.add(Term("field", "aa"), 0)
        pqBuilder.add(Term("field", "b"), 1)
        assertEquals(1, searcher.count(pqBuilder.build()))

        // Now with "a|z b" which should not match; this isn't a MultiPhraseQuery
        pqBuilder = PhraseQuery.Builder()
        pqBuilder.add(Term("field", "a"), 0)
        pqBuilder.add(Term("field", "z"), 0)
        pqBuilder.add(Term("field", "b"), 1)
        assertEquals(0, searcher.count(pqBuilder.build()))

        r.close()
        dir.close()
    }

    @Test
    fun testRandomPhrases() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())

        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(analyzer).setMergePolicy(newLogMergePolicy())
            )
        val docs = ArrayList<List<String>>()
        val d = Document()
        val f = newTextField("f", "", Field.Store.NO)
        d.add(f)

        val r = random()

        val NUM_DOCS = atLeast(10)
        for (i in 0..<NUM_DOCS) {
            // at night, must be > 4096 so it spans multiple chunks
            val termCount = if (TEST_NIGHTLY) atLeast(4097) else atLeast(200)

            val doc = ArrayList<String>()

            val sb = StringBuilder()
            while (doc.size < termCount) {
                if (r.nextInt(5) == 1 || docs.size == 0) {
                    // make new non-empty-string term
                    var term: String
                    while (true) {
                        term = TestUtil.randomUnicodeString(r)
                        if (term.length > 0) {
                            break
                        }
                    }
                    val ts: TokenStream = analyzer.tokenStream("ignore", term)
                    try {
                        val termAttr = ts.addAttribute(CharTermAttribute::class)
                        ts.reset()
                        while (ts.incrementToken()) {
                            val text = termAttr.toString()
                            doc.add(text)
                            sb.append(text).append(' ')
                        }
                        ts.end()
                    } finally {
                        ts.close()
                    }
                } else {
                    // pick existing sub-phrase
                    val lastDoc = docs[r.nextInt(docs.size)]
                    val len = TestUtil.nextInt(r, 1, 10)
                    val start = r.nextInt(lastDoc.size - len)
                    for (k in start..<start + len) {
                        val t = lastDoc[k]
                        doc.add(t)
                        sb.append(t).append(' ')
                    }
                }
            }
            docs.add(doc)
            f.setStringValue(sb.toString())
            w.addDocument(d)
        }

        val reader = w.getReader(true, false)
        val s = newSearcher(reader)
        w.close()

        // now search
        val num = atLeast(3)
        for (i in 0..<num) {
            val docID = r.nextInt(docs.size)
            val doc = docs[docID]

            val numTerm = TestUtil.nextInt(r, 2, 20)
            val start = r.nextInt(doc.size - numTerm)
            val builder = PhraseQuery.Builder()
            val sb = StringBuilder()
            for (t in start..<start + numTerm) {
                builder.add(Term("f", doc[t]), t)
                sb.append(doc[t]).append(' ')
            }
            val pq = builder.build()

            val hits = s.search(pq, NUM_DOCS)
            var found = false
            for (j in hits.scoreDocs.indices) {
                if (hits.scoreDocs[j].doc == docID) {
                    found = true
                    break
                }
            }

            assertTrue(found, "phrase '$sb' not found; start=$start, it=$i, expected doc $docID")
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testNegativeSlop() {
        expectThrows(IllegalArgumentException::class) {
            PhraseQuery(-2, "field", "two", "one")
        }
    }

    @Test
    fun testNegativePosition() {
        val builder = PhraseQuery.Builder()
        expectThrows(IllegalArgumentException::class) {
            builder.add(Term("field", "two"), -42)
        }
    }

    @Test
    fun testBackwardPositions() {
        val builder = PhraseQuery.Builder()
        builder.add(Term("field", "one"), 1)
        builder.add(Term("field", "two"), 5)
        expectThrows(IllegalArgumentException::class) {
            builder.add(Term("field", "three"), 4)
        }
    }

    @Test
    fun testTopPhrases() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val docs = ArrayUtil.copyArray(DOCS)
        docs.shuffle(random())
        for (value in DOCS) {
            val doc = Document()
            doc.add(TextField("f", value, Field.Store.NO))
            w.addDocument(doc)
        }
        val r = DirectoryReader.open(w)
        w.close()
        val searcher = newSearcher(r)
        for (
            query in listOf(
                PhraseQuery("f", "b", "c"), // common phrase
                PhraseQuery("f", "e", "f"), // always appear next to each other
                PhraseQuery("f", "d", "d") // repeated term
            )
        ) {
            for (topN in 1..2) {
                var collectorManager = TopScoreDocCollectorManager(topN, Int.MAX_VALUE)
                val hits1 = searcher.search(query, collectorManager).scoreDocs

                collectorManager = TopScoreDocCollectorManager(topN, 1)
                val topDocs2 = searcher.search(query, collectorManager)
                val hits2 = topDocs2.scoreDocs

                assertTrue(hits1.isNotEmpty(), "$query")
                CheckHits.checkEqual(query, hits1, hits2)
            }
        }
        r.close()
        dir.close()
    }

    @Test
    fun testMergeImpacts() {
        val impacts1 = DummyImpactsEnum(1000)
        val impacts2 = DummyImpactsEnum(2000)
        val mergedImpacts =
            ExactPhraseMatcher.mergeImpacts(arrayOf<ImpactsEnum>(impacts1, impacts2))

        impacts1.reset(
            arrayOf(
                arrayOf(Impact(3, 10), Impact(5, 12), Impact(8, 13)),
                arrayOf(Impact(3, 10), Impact(5, 11), Impact(8, 13), Impact(12, 14))
            ),
            intArrayOf(110, 945)
        )

        // Merge with empty impacts
        impacts2.reset(emptyArray(), intArrayOf())
        assertImpactsEquals(
            arrayOf(
                arrayOf(Impact(3, 10), Impact(5, 12), Impact(8, 13)),
                arrayOf(Impact(3, 10), Impact(5, 11), Impact(8, 13), Impact(12, 14))
            ),
            intArrayOf(110, 945),
            mergedImpacts.impacts
        )

        // Merge with dummy impacts
        impacts2.reset(arrayOf(arrayOf(Impact(Int.MAX_VALUE, 1))), intArrayOf(5000))
        assertImpactsEquals(
            arrayOf(
                arrayOf(Impact(3, 10), Impact(5, 12), Impact(8, 13)),
                arrayOf(Impact(3, 10), Impact(5, 11), Impact(8, 13), Impact(12, 14))
            ),
            intArrayOf(110, 945),
            mergedImpacts.impacts
        )

        // Merge with dummy impacts that we don't special case
        impacts2.reset(arrayOf(arrayOf(Impact(Int.MAX_VALUE, 2))), intArrayOf(5000))
        assertImpactsEquals(
            arrayOf(
                arrayOf(Impact(3, 10), Impact(5, 12), Impact(8, 13)),
                arrayOf(Impact(3, 10), Impact(5, 11), Impact(8, 13), Impact(12, 14))
            ),
            intArrayOf(110, 945),
            mergedImpacts.impacts
        )

        // First level of impacts2 doesn't cover the first level of impacts1
        impacts2.reset(
            arrayOf(
                arrayOf(Impact(2, 10), Impact(6, 13)),
                arrayOf(Impact(3, 9), Impact(5, 11), Impact(7, 13))
            ),
            intArrayOf(90, 1000)
        )
        assertImpactsEquals(
            arrayOf(
                arrayOf(Impact(3, 10), Impact(5, 12), Impact(7, 13)),
                arrayOf(Impact(3, 10), Impact(5, 11), Impact(7, 13))
            ),
            intArrayOf(110, 945),
            mergedImpacts.impacts
        )

        // Second level of impacts2 doesn't cover the first level of impacts1
        impacts2.reset(
            arrayOf(
                arrayOf(Impact(2, 10), Impact(6, 11)),
                arrayOf(Impact(3, 9), Impact(5, 11), Impact(7, 13))
            ),
            intArrayOf(150, 900)
        )
        assertImpactsEquals(
            arrayOf(
                arrayOf(Impact(2, 10), Impact(3, 11), Impact(5, 12), Impact(6, 13)),
                arrayOf(Impact(3, 10), Impact(5, 11), Impact(8, 13), Impact(12, 14))
            ),
            intArrayOf(110, 945),
            mergedImpacts.impacts
        )

        impacts2.reset(
            arrayOf(
                arrayOf(Impact(4, 10), Impact(9, 13)),
                arrayOf(
                    Impact(1, 1),
                    Impact(4, 10),
                    Impact(5, 11),
                    Impact(8, 13),
                    Impact(12, 14),
                    Impact(13, 15)
                )
            ),
            intArrayOf(113, 950)
        )
        assertImpactsEquals(
            arrayOf(
                arrayOf(Impact(3, 10), Impact(4, 12), Impact(8, 13)),
                arrayOf(Impact(3, 10), Impact(5, 11), Impact(8, 13), Impact(12, 14))
            ),
            intArrayOf(110, 945),
            mergedImpacts.impacts
        )

        // Make sure negative norms are treated as unsigned
        impacts1.reset(
            arrayOf(
                arrayOf(Impact(3, 10), Impact(5, -10), Impact(8, -5)),
                arrayOf(Impact(3, 10), Impact(5, -15), Impact(8, -5), Impact(12, -3))
            ),
            intArrayOf(110, 945)
        )
        impacts2.reset(
            arrayOf(
                arrayOf(Impact(2, 10), Impact(12, -4)),
                arrayOf(Impact(3, 9), Impact(12, -4), Impact(20, -1))
            ),
            intArrayOf(150, 960)
        )
        assertImpactsEquals(
            arrayOf(
                arrayOf(Impact(2, 10), Impact(8, -4)),
                arrayOf(Impact(3, 10), Impact(8, -4), Impact(12, -3))
            ),
            intArrayOf(110, 945),
            mergedImpacts.impacts
        )
    }

    @Test
    fun testRandomTopDocs() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val numDocs =
            if (TEST_NIGHTLY) {
                atLeast(128 * 8 * 8 * 3)
            } else {
                atLeast(100)
            } // at night, make sure some terms have skip data
        for (i in 0..<numDocs) {
            val doc = Document()
            val numTerms = random().nextInt(1 shl random().nextInt(5))
            val text =
                List(numTerms) {
                    if (random().nextBoolean()) {
                        "a"
                    } else if (random().nextBoolean()) {
                        "b"
                    } else {
                        "c"
                    }
                }.joinToString(" ")
            doc.add(TextField("foo", text, Field.Store.NO))
            w.addDocument(doc)
        }
        val reader = DirectoryReader.open(w)
        w.close()
        val searcher = newSearcher(reader)

        for (firstTerm in arrayOf("a", "b", "c")) {
            for (secondTerm in arrayOf("a", "b", "c")) {
                val query = PhraseQuery("foo", newBytesRef(firstTerm), newBytesRef(secondTerm))

                var completeManager = TopScoreDocCollectorManager(10, Int.MAX_VALUE) // COMPLETE
                var topScoresManager = TopScoreDocCollectorManager(10, 10) // TOP_SCORES

                var complete = searcher.search(query, completeManager)
                var topScores = searcher.search(query, topScoresManager)
                CheckHits.checkEqual(query, complete.scoreDocs, topScores.scoreDocs)

                val filteredQuery =
                    BooleanQuery.Builder()
                        .add(query, Occur.MUST)
                        .add(TermQuery(Term("foo", "b")), Occur.FILTER)
                        .build()

                completeManager = TopScoreDocCollectorManager(10, Int.MAX_VALUE) // COMPLETE
                topScoresManager = TopScoreDocCollectorManager(10, 10) // TOP_SCORES

                complete = searcher.search(filteredQuery, completeManager)
                topScores = searcher.search(filteredQuery, topScoresManager)
                CheckHits.checkEqual(query, complete.scoreDocs, topScores.scoreDocs)
            }
        }
        reader.close()
        dir.close()
    }

    @Test
    fun testNullTerm() {
        var e = expectThrows(NullPointerException::class) {
            PhraseQuery.Builder().add(nullValue<Term>())
        }
        assertEquals("Cannot add a null term to PhraseQuery", e.message)

        e = expectThrows(NullPointerException::class) {
            PhraseQuery("field", nullValue<BytesRef>())
        }
        assertEquals("Cannot add a null term to PhraseQuery", e.message)

        e = expectThrows(NullPointerException::class) {
            PhraseQuery("field", nullValue<String>())
        }
        assertEquals("Cannot add a null term to PhraseQuery", e.message)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> nullValue(): T {
        return null as T
    }

    private fun assertImpactsEquals(impacts: Array<Array<Impact>>, docIdUpTo: IntArray, actual: Impacts) {
        assertEquals(impacts.size, actual.numLevels())
        for (i in impacts.indices) {
            assertEquals(docIdUpTo[i], actual.getDocIdUpTo(i))
            assertEquals(impacts[i].toList(), actual.getImpacts(i))
        }
    }

    private class DummyImpactsEnum(private val costValue: Long) : ImpactsEnum() {
        private lateinit var impactsValue: Array<Array<Impact>>
        private lateinit var docIdUpToValue: IntArray

        fun reset(impacts: Array<Array<Impact>>, docIdUpTo: IntArray) {
            impactsValue = impacts
            docIdUpToValue = docIdUpTo
        }

        override fun advanceShallow(target: Int) {
            throw UnsupportedOperationException()
        }

        override val impacts: Impacts
            get() =
                object : Impacts() {
                    override fun numLevels(): Int {
                        return impactsValue.size
                    }

                    override fun getDocIdUpTo(level: Int): Int {
                        return docIdUpToValue[level]
                    }

                    override fun getImpacts(level: Int): MutableList<Impact> {
                        return impactsValue[level].toMutableList()
                    }
                }

        override fun freq(): Int {
            throw UnsupportedOperationException()
        }

        override fun nextPosition(): Int {
            throw UnsupportedOperationException()
        }

        override fun startOffset(): Int {
            throw UnsupportedOperationException()
        }

        override fun endOffset(): Int {
            throw UnsupportedOperationException()
        }

        override val payload: BytesRef?
            get() {
                throw UnsupportedOperationException()
            }

        override fun docID(): Int {
            throw UnsupportedOperationException()
        }

        override fun nextDoc(): Int {
            throw UnsupportedOperationException()
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun cost(): Long {
            return costValue
        }
    }

    companion object {
        private var searcher: IndexSearcher? = null
        private var reader: IndexReader? = null
        private var directory: Directory? = null

        private val DOCS =
            arrayOf(
                "a b c d e f g h",
                "b c b",
                "c d d d e f g b",
                "c b a b c",
                "a a b b c c d d",
                "a b c d a b c d a b c d"
            )
    }
}
