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

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestTermRangeQuery : LuceneTestCase() {
    private var docCount = 0
    private lateinit var dir: Directory

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusive() {
        val query: Query = TermRangeQuery.newStringRange("content", "A", "C", false, false)
        initializeIndex(arrayOf("A", "B", "C", "D"))
        var reader: IndexReader = DirectoryReader.open(dir)
        var searcher = newSearcher(reader)
        var hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size, "A,B,C,D, only B in range")
        reader.close()

        initializeIndex(arrayOf("A", "B", "D"))
        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size, "A,B,D, only B in range")
        reader.close()

        addDoc("C")
        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size, "C added, still only B in range")
        reader.close()
    }

    @Test
    @Throws(Exception::class)
    fun testInclusive() {
        val query: Query = TermRangeQuery.newStringRange("content", "A", "C", true, true)

        initializeIndex(arrayOf("A", "B", "C", "D"))
        var reader: IndexReader = DirectoryReader.open(dir)
        var searcher = newSearcher(reader)
        var hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size, "A,B,C,D - A,B,C in range")
        reader.close()

        initializeIndex(arrayOf("A", "B", "D"))
        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(2, hits.size, "A,B,D - A and B in range")
        reader.close()

        addDoc("C")
        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size, "C added - A, B, C in range")
        reader.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAllDocs() {
        initializeIndex(arrayOf("A", "B", "C", "D"))
        val reader: IndexReader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)

        var query = TermRangeQuery("content", null, null, true, true)
        assertEquals(4, searcher.search(query, 1000).scoreDocs.size)

        query = TermRangeQuery.newStringRange("content", "", null, true, true)
        assertEquals(4, searcher.search(query, 1000).scoreDocs.size)

        query = TermRangeQuery.newStringRange("content", "", null, true, false)
        assertEquals(4, searcher.search(query, 1000).scoreDocs.size)

        // and now another one
        query = TermRangeQuery.newStringRange("content", "B", null, true, true)
        assertEquals(3, searcher.search(query, 1000).scoreDocs.size)
        reader.close()
    }

    /**
     * This test should not be here, but it tests the fuzzy query rewrite mode
     * (TOP_TERMS_SCORING_BOOLEAN_REWRITE) with constant score and checks, that only the lower end of
     * terms is put into the range
     */
    @Test
    @Throws(Exception::class)
    fun testTopTermsRewrite() {
        initializeIndex(arrayOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K"))

        val reader: IndexReader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)
        val rewriteMethod: MultiTermQuery.RewriteMethod =
            MultiTermQuery.TopTermsScoringBooleanQueryRewrite(50)
        val query =
            TermRangeQuery.newStringRange("content", "B", "J", true, true, rewriteMethod)
        checkBooleanTerms(searcher, query, "B", "C", "D", "E", "F", "G", "H", "I", "J")

        val savedClauseCount = IndexSearcher.maxClauseCount
        try {
            IndexSearcher.maxClauseCount = 3
            checkBooleanTerms(searcher, query, "B", "C", "D")
        } finally {
            IndexSearcher.maxClauseCount = savedClauseCount
        }
        reader.close()
    }

    @Throws(IOException::class)
    private fun checkBooleanTerms(searcher: IndexSearcher, query: TermRangeQuery, vararg terms: String) {
        val bq = searcher.rewrite(query) as BooleanQuery
        val allowedTerms = asSet(*terms)
        assertEquals(allowedTerms.size, bq.clauses().size)
        for (c in bq.clauses()) {
            assertTrue(c.query is TermQuery)
            val tq = c.query as TermQuery
            val term = tq.getTerm().text()
            assertTrue(allowedTerms.contains(term), "invalid term: $term")
            allowedTerms.remove(term) // remove to fail on double terms
        }
        assertEquals(0, allowedTerms.size)
    }

    @Test
    fun testEqualsHashcode() {
        var query: Query = TermRangeQuery.newStringRange("content", "A", "C", true, true)

        var other: Query = TermRangeQuery.newStringRange("content", "A", "C", true, true)

        assertEquals(query, query, "query equals itself is true")
        assertEquals(query, other, "equivalent queries are equal")
        assertEquals(query.hashCode(), other.hashCode(), "hashcode must return same value when equals is true")

        other = TermRangeQuery.newStringRange("notcontent", "A", "C", true, true)
        assertFalse(query == other, "Different fields are not equal")

        other = TermRangeQuery.newStringRange("content", "X", "C", true, true)
        assertFalse(query == other, "Different lower terms are not equal")

        other = TermRangeQuery.newStringRange("content", "A", "Z", true, true)
        assertFalse(query == other, "Different upper terms are not equal")

        query = TermRangeQuery.newStringRange("content", null, "C", true, true)
        other = TermRangeQuery.newStringRange("content", null, "C", true, true)
        assertEquals(query, other, "equivalent queries with null lowerterms are equal()")
        assertEquals(query.hashCode(), other.hashCode(), "hashcode must return same value when equals is true")

        query = TermRangeQuery.newStringRange("content", "C", null, true, true)
        other = TermRangeQuery.newStringRange("content", "C", null, true, true)
        assertEquals(query, other, "equivalent queries with null upperterms are equal()")
        assertEquals(query.hashCode(), other.hashCode(), "hashcode returns same value")

        query = TermRangeQuery.newStringRange("content", null, "C", true, true)
        other = TermRangeQuery.newStringRange("content", "C", null, true, true)
        assertFalse(query == other, "queries with different upper and lower terms are not equal")

        query = TermRangeQuery.newStringRange("content", "A", "C", false, false)
        other = TermRangeQuery.newStringRange("content", "A", "C", true, true)
        assertFalse(query == other, "queries with different inclusive are not equal")
    }

    private class SingleCharAnalyzer : Analyzer() {

        private class SingleCharTokenizer : Tokenizer() {
            var buffer = CharArray(1)
            var done = false
            var termAtt: CharTermAttribute

            init {
                termAtt = addAttribute(CharTermAttribute::class)
            }

            @Throws(IOException::class)
            override fun incrementToken(): Boolean {
                if (done) return false
                else {
                    val count = input.read(buffer, 0, buffer.size)
                    clearAttributes()
                    done = true
                    if (count == 1) {
                        termAtt.copyBuffer(buffer, 0, 1)
                    }
                    return true
                }
            }

            @Throws(IOException::class)
            override fun reset() {
                super.reset()
                done = false
            }
        }

        override fun createComponents(fieldName: String): TokenStreamComponents {
            return TokenStreamComponents(SingleCharTokenizer())
        }
    }

    @Throws(IOException::class)
    private fun initializeIndex(values: Array<String>) {
        initializeIndex(values, MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
    }

    @Throws(IOException::class)
    private fun initializeIndex(values: Array<String>, analyzer: Analyzer) {
        val writer =
            IndexWriter(dir, newIndexWriterConfig(analyzer).setOpenMode(OpenMode.CREATE))
        for (i in values.indices) {
            insertDoc(writer, values[i])
        }
        writer.close()
    }

    // shouldnt create an analyzer for every doc?
    @Throws(IOException::class)
    private fun addDoc(content: String) {
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                    .setOpenMode(OpenMode.APPEND)
            )
        insertDoc(writer, content)
        writer.close()
    }

    @Throws(IOException::class)
    private fun insertDoc(writer: IndexWriter, content: String) {
        val doc = Document()

        doc.add(newStringField("id", "id$docCount", Field.Store.YES))
        doc.add(newTextField("content", content, Field.Store.NO))

        writer.addDocument(doc)
        docCount++
    }

    // LUCENE-38
    @Test
    @Throws(Exception::class)
    fun testExclusiveLowerNull() {
        val analyzer: Analyzer = SingleCharAnalyzer()
        // http://issues.apache.org/jira/browse/LUCENE-38
        val query: Query = TermRangeQuery.newStringRange("content", null, "C", false, false)
        initializeIndex(arrayOf("A", "B", "", "C", "D"), analyzer)
        var reader: IndexReader = DirectoryReader.open(dir)
        var searcher = newSearcher(reader)
        var numHits = searcher.search(query, 1000).totalHits.value
        // When Lucene-38 is fixed, use the assert on the next line:
        assertEquals(3, numHits, "A,B,<empty string>,C,D => A, B & <empty string> are in range")
        // until Lucene-38 is fixed, use this assert:
        // assertEquals("A,B,<empty string>,C,D => A, B & <empty string> are in range", 2,
        // hits.length());

        reader.close()
        initializeIndex(arrayOf("A", "B", "", "D"), analyzer)
        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        numHits = searcher.search(query, 1000).totalHits.value
        // When Lucene-38 is fixed, use the assert on the next line:
        assertEquals(3, numHits, "A,B,<empty string>,D => A, B & <empty string> are in range")
        // until Lucene-38 is fixed, use this assert:
        // assertEquals("A,B,<empty string>,D => A, B & <empty string> are in range", 2, hits.length());
        reader.close()
        addDoc("C")
        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        numHits = searcher.search(query, 1000).totalHits.value
        // When Lucene-38 is fixed, use the assert on the next line:
        assertEquals(3, numHits, "C added, still A, B & <empty string> are in range")
        // until Lucene-38 is fixed, use this assert
        // assertEquals("C added, still A, B & <empty string> are in range", 2, hits.length());
        reader.close()
    }

    // LUCENE-38
    @Test
    @Throws(Exception::class)
    fun testInclusiveLowerNull() {
        // http://issues.apache.org/jira/browse/LUCENE-38
        val analyzer: Analyzer = SingleCharAnalyzer()
        val query: Query = TermRangeQuery.newStringRange("content", null, "C", true, true)
        initializeIndex(arrayOf("A", "B", "", "C", "D"), analyzer)
        var reader: IndexReader = DirectoryReader.open(dir)
        var searcher = newSearcher(reader)
        var numHits = searcher.search(query, 1000).totalHits.value
        // When Lucene-38 is fixed, use the assert on the next line:
        assertEquals(4, numHits, "A,B,<empty string>,C,D => A,B,<empty string>,C in range")
        // until Lucene-38 is fixed, use this assert
        // assertEquals("A,B,<empty string>,C,D => A,B,<empty string>,C in range", 3, hits.length());
        reader.close()
        initializeIndex(arrayOf("A", "B", "", "D"), analyzer)
        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        numHits = searcher.search(query, 1000).totalHits.value
        // When Lucene-38 is fixed, use the assert on the next line:
        assertEquals(3, numHits, "A,B,<empty string>,D - A, B and <empty string> in range")
        // until Lucene-38 is fixed, use this assert
        // assertEquals("A,B,<empty string>,D => A, B and <empty string> in range", 2, hits.length());
        reader.close()
        addDoc("C")
        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        numHits = searcher.search(query, 1000).totalHits.value
        // When Lucene-38 is fixed, use the assert on the next line:
        assertEquals(4, numHits, "C added => A,B,<empty string>,C in range")
        // until Lucene-38 is fixed, use this assert
        // assertEquals("C added => A,B,<empty string>,C in range", 3, hits.length());
        reader.close()
    }

    private fun asSet(vararg terms: String): MutableSet<String> {
        return terms.toMutableSet()
    }
}
