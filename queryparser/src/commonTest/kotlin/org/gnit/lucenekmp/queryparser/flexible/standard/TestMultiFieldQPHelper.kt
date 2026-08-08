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
package org.gnit.lucenekmp.queryparser.flexible.standard

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This test case is a copy of the core Lucene query parser test, it was adapted to use new
 * QueryParserHelper instead of the old query parser.
 *
 * Tests QueryParser.
 */
class TestMultiFieldQPHelper : LuceneTestCase() {

    /**
     * test stop words parsing for both the non static form, and for the corresponding static form
     * (qtxt, fields[]).
     */
    @Test
    fun testStopwordsParsing() {
        assertStopQueryEquals("one", "b:one t:one")
        assertStopQueryEquals("one stop", "b:one t:one")
        assertStopQueryEquals("one (stop)", "b:one t:one")
        assertStopQueryEquals("one ((stop))", "b:one t:one")
        assertStopQueryIsMatchNoDocsQuery("stop")
        assertStopQueryIsMatchNoDocsQuery("(stop)")
        assertStopQueryIsMatchNoDocsQuery("((stop))")
    }

    // verify parsing of query using a stopping analyzer
    private fun assertStopQueryIsMatchNoDocsQuery(qtxt: String) {
        val fields = arrayOf("b", "t")
        val a = TestQPHelper.QPTestAnalyzer()
        val mfqp = StandardQueryParser()
        mfqp.multiFields = fields.map { it as CharSequence }.toTypedArray()
        mfqp.analyzer = a

        val q = mfqp.parse(qtxt, null)
        assertTrue(q is MatchNoDocsQuery)
    }

    // verify parsing of query using a stopping analyzer
    private fun assertStopQueryEquals(qtxt: String, expectedRes: String) {
        val fields = arrayOf("b", "t")
        val occur = arrayOf(Occur.SHOULD, Occur.SHOULD)
        val a = TestQPHelper.QPTestAnalyzer()
        val mfqp = StandardQueryParser()
        mfqp.multiFields = fields.map { it as CharSequence }.toTypedArray()
        mfqp.analyzer = a

        var q = mfqp.parse(qtxt, null)
        assertEquals(expectedRes, q.toString().trim())

        q = QueryParserUtil.parse(qtxt, fields, occur, a)
        assertEquals(expectedRes, q.toString().trim())
    }

    @Test
    fun testSimple() {
        val fields = arrayOf("b", "t")
        val mfqp = StandardQueryParser()
        mfqp.multiFields = fields.map { it as CharSequence }.toTypedArray()
        mfqp.analyzer = MockAnalyzer(random())

        var q = mfqp.parse("one", null)
        assertEquals("b:one t:one", q.toString())

        q = mfqp.parse("one two", null)
        assertEquals("(b:one t:one) (b:two t:two)", q.toString())

        q = mfqp.parse("+one +two", null)
        assertEquals("+(b:one t:one) +(b:two t:two)", q.toString())

        q = mfqp.parse("+one -two -three", null)
        assertEquals("+(b:one t:one) -(b:two t:two) -(b:three t:three)", q.toString())

        q = mfqp.parse("one^2 two", null)
        assertEquals("(b:one t:one)^2.0 (b:two t:two)", q.toString())

        q = mfqp.parse("one~ two", null)
        assertEquals("(b:one~2 t:one~2) (b:two t:two)", q.toString())

        q = mfqp.parse("one~0.8 two^2", null)
        assertEquals("(b:one~0 t:one~0) (b:two t:two)^2.0", q.toString())

        q = mfqp.parse("one* two*", null)
        assertEquals("(b:one* t:one*) (b:two* t:two*)", q.toString())

        q = mfqp.parse("[a TO c] two", null)
        assertEquals("(b:[a TO c] t:[a TO c]) (b:two t:two)", q.toString())

        q = mfqp.parse("w?ldcard", null)
        assertEquals("b:w?ldcard t:w?ldcard", q.toString())

        q = mfqp.parse("\"foo bar\"", null)
        assertEquals("b:\"foo bar\" t:\"foo bar\"", q.toString())

        q = mfqp.parse("\"aa bb cc\" \"dd ee\"", null)
        assertEquals("(b:\"aa bb cc\" t:\"aa bb cc\") (b:\"dd ee\" t:\"dd ee\")", q.toString())

        q = mfqp.parse("\"foo bar\"~4", null)
        assertEquals("b:\"foo bar\"~4 t:\"foo bar\"~4", q.toString())

        // LUCENE-1213: QueryParser was ignoring slop when phrase
        // had a field.
        q = mfqp.parse("b:\"foo bar\"~4", null)
        assertEquals("b:\"foo bar\"~4", q.toString())

        // make sure that terms which have a field are not touched:
        q = mfqp.parse("one f:two", null)
        assertEquals("(b:one t:one) f:two", q.toString())

        // AND mode:
        mfqp.defaultOperator = StandardQueryConfigHandler.Operator.AND
        q = mfqp.parse("one two", null)
        assertEquals("+(b:one t:one) +(b:two t:two)", q.toString())
        q = mfqp.parse("\"aa bb cc\" \"dd ee\"", null)
        assertEquals("+(b:\"aa bb cc\" t:\"aa bb cc\") +(b:\"dd ee\" t:\"dd ee\")", q.toString())
    }

    @Test
    fun testBoostsSimple() {
        val boosts: MutableMap<String, Float> = mutableMapOf()
        boosts["b"] = 5f
        boosts["t"] = 10f
        val fields = arrayOf("b", "t")
        val mfqp = StandardQueryParser()
        mfqp.multiFields = fields.map { it as CharSequence }.toTypedArray()
        mfqp.fieldsBoost = boosts
        mfqp.analyzer = MockAnalyzer(random())

        // Check for simple
        var q = mfqp.parse("one", null)
        assertEquals("(b:one)^5.0 (t:one)^10.0", q.toString())

        // Check for AND
        q = mfqp.parse("one AND two", null)
        assertEquals("+((b:one)^5.0 (t:one)^10.0) +((b:two)^5.0 (t:two)^10.0)", q.toString())

        // Check for OR
        q = mfqp.parse("one OR two", null)
        assertEquals("((b:one)^5.0 (t:one)^10.0) ((b:two)^5.0 (t:two)^10.0)", q.toString())

        // Check for AND and a field
        q = mfqp.parse("one AND two AND foo:test", null)
        assertEquals("+((b:one)^5.0 (t:one)^10.0) +((b:two)^5.0 (t:two)^10.0) +foo:test", q.toString())

        q = mfqp.parse("one^3 AND two^4", null)
        assertEquals("+((b:one)^5.0 (t:one)^10.0)^3.0 +((b:two)^5.0 (t:two)^10.0)^4.0", q.toString())
    }

    @Test
    fun testStaticMethod1() {
        val fields = arrayOf("b", "t")
        val queries = arrayOf("one", "two")
        var q = QueryParserUtil.parse(queries, fields, MockAnalyzer(random()))
        assertEquals("b:one t:two", q.toString())

        val queries2 = arrayOf("+one", "+two")
        q = QueryParserUtil.parse(queries2, fields, MockAnalyzer(random()))
        assertEquals("b:one t:two", q.toString())

        val queries3 = arrayOf("one", "+two")
        q = QueryParserUtil.parse(queries3, fields, MockAnalyzer(random()))
        assertEquals("b:one t:two", q.toString())

        val queries4 = arrayOf("one +more", "+two")
        q = QueryParserUtil.parse(queries4, fields, MockAnalyzer(random()))
        assertEquals("(b:one +b:more) t:two", q.toString())

        val queries5 = arrayOf("blah")
        // expected exception, array length differs
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            QueryParserUtil.parse(queries5, fields, MockAnalyzer(random()))
        }

        // check also with stop words for this static form (qtxts[], fields[]).
        val stopA = TestQPHelper.QPTestAnalyzer()

        val queries6 = arrayOf("((+stop))", "+((stop))")
        q = QueryParserUtil.parse(queries6, fields, stopA)
        assertEquals("MatchNoDocsQuery(\"\") MatchNoDocsQuery(\"\")", q.toString())
        // assertEquals(" ", q.toString());

        val queries7 = arrayOf("one ((+stop)) +more", "+((stop)) +two")
        q = QueryParserUtil.parse(queries7, fields, stopA)
        assertEquals("(b:one +b:more) (+t:two)", q.toString())
    }

    @Test
    fun testStaticMethod2() {
        val fields = arrayOf("b", "t")
        val flags = arrayOf(BooleanClause.Occur.MUST, BooleanClause.Occur.MUST_NOT)
        var q = QueryParserUtil.parse("one", fields, flags, MockAnalyzer(random()))
        assertEquals("+b:one -t:one", q.toString())

        q = QueryParserUtil.parse("one two", fields, flags, MockAnalyzer(random()))
        assertEquals("+(b:one b:two) -(t:one t:two)", q.toString())

        // expected exception, array length differs
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            val flags2 = arrayOf(BooleanClause.Occur.MUST)
            QueryParserUtil.parse("blah", fields, flags2, MockAnalyzer(random()))
        }
    }

    @Test
    fun testStaticMethod2Old() {
        val fields = arrayOf("b", "t")
        val flags = arrayOf(BooleanClause.Occur.MUST, BooleanClause.Occur.MUST_NOT)
        val parser = StandardQueryParser()
        parser.multiFields = fields.map { it as CharSequence }.toTypedArray()
        parser.analyzer = MockAnalyzer(random())

        var q =
            QueryParserUtil.parse(
                "one", fields, flags, MockAnalyzer(random())
            ) // , fields, flags, new
        // MockAnalyzer());
        assertEquals("+b:one -t:one", q.toString())

        q = QueryParserUtil.parse("one two", fields, flags, MockAnalyzer(random()))
        assertEquals("+(b:one b:two) -(t:one t:two)", q.toString())

        // expected exception, array length differs
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            val flags2 = arrayOf(BooleanClause.Occur.MUST)
            QueryParserUtil.parse("blah", fields, flags2, MockAnalyzer(random()))
        }
    }

    @Test
    fun testStaticMethod3() {
        val queries = arrayOf("one", "two", "three")
        val fields = arrayOf("f1", "f2", "f3")
        val flags =
            arrayOf(BooleanClause.Occur.MUST, BooleanClause.Occur.MUST_NOT, BooleanClause.Occur.SHOULD)
        val q = QueryParserUtil.parse(queries, fields, flags, MockAnalyzer(random()))
        assertEquals("+f1:one -f2:two f3:three", q.toString())

        // expected exception, array length differs
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            val flags2 = arrayOf(BooleanClause.Occur.MUST)
            QueryParserUtil.parse(queries, fields, flags2, MockAnalyzer(random()))
        }
    }

    @Test
    fun testStaticMethod3Old() {
        val queries = arrayOf("one", "two")
        val fields = arrayOf("b", "t")
        val flags = arrayOf(BooleanClause.Occur.MUST, BooleanClause.Occur.MUST_NOT)
        val q = QueryParserUtil.parse(queries, fields, flags, MockAnalyzer(random()))
        assertEquals("+b:one -t:two", q.toString())

        // expected exception, array length differs
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            val flags2 = arrayOf(BooleanClause.Occur.MUST)
            QueryParserUtil.parse(queries, fields, flags2, MockAnalyzer(random()))
        }
    }

    @Test
    fun testAnalyzerReturningNull() {
        val fields = arrayOf("f1", "f2", "f3")
        val parser = StandardQueryParser()
        parser.multiFields = fields.map { it as CharSequence }.toTypedArray()
        parser.analyzer = AnalyzerReturningNull()

        var q = parser.parse("bla AND blo", null)
        assertEquals("+(f2:bla f3:bla) +(f2:blo f3:blo)", q.toString())
        // the following queries are not affected as their terms are not
        // analyzed anyway:
        q = parser.parse("bla*", null)
        assertEquals("f1:bla* f2:bla* f3:bla*", q.toString())
        q = parser.parse("bla~", null)
        assertEquals("f1:bla~2 f2:bla~2 f3:bla~2", q.toString())
        q = parser.parse("[a TO c]", null)
        assertEquals("f1:[a TO c] f2:[a TO c] f3:[a TO c]", q.toString())
    }

    @Test
    fun testStopWordSearching() {
        val analyzer = MockAnalyzer(random())
        val ramDir = newDirectory()
        val iw = IndexWriter(ramDir, newIndexWriterConfig(analyzer))
        val doc = Document()
        doc.add(newTextField("body", "blah the footest blah", Field.Store.NO))
        iw.addDocument(doc)
        iw.close()

        val mfqp = StandardQueryParser()

        mfqp.multiFields = arrayOf("body")
        mfqp.analyzer = analyzer
        mfqp.defaultOperator = StandardQueryConfigHandler.Operator.AND
        val q = mfqp.parse("the footest", null)
        val ir = DirectoryReader.open(ramDir)
        val searcher = newSearcher(ir)
        val hits = searcher.search(q, 1000).scoreDocs
        assertEquals(1, hits.size)
        ir.close()
        ramDir.close()
    }

    /** Return no tokens for field "f1". */
    private class AnalyzerReturningNull : Analyzer(PER_FIELD_REUSE_STRATEGY) {
        override fun initReader(fieldName: String, reader: Reader): Reader {
            return if ("f1" == fieldName) {
                // we don't use the reader, so close it:
                IOUtils.closeWhileHandlingException(reader)
                // return empty reader, so MockTokenizer returns no tokens:
                StringReader("")
            } else {
                super.initReader(fieldName, reader)
            }
        }

        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
            return TokenStreamComponents(tokenizer, tokenizer)
        }
    }
}
