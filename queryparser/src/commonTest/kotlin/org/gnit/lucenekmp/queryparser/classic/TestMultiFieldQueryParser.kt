package org.gnit.lucenekmp.queryparser.classic

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.queryparser.util.QueryParserTestBase.QPTestAnalyzer
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.RegexpQuery
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockSynonymFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Tests QueryParser. */
class TestMultiFieldQueryParser : LuceneTestCase() {
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
        assertStopQueryEquals("stop", "")
        assertStopQueryEquals("(stop)", "")
        assertStopQueryEquals("((stop))", "")
    }

    // verify parsing of query using a stopping analyzer
    private fun assertStopQueryEquals(qtxt: String, expectedRes: String) {
        val fields = arrayOf("b", "t")
        val occur = arrayOf(Occur.SHOULD, Occur.SHOULD)
        val a = QPTestAnalyzer()
        val mfqp = MultiFieldQueryParser(fields, a)

        var q = mfqp.parse(qtxt)
        assertEquals(expectedRes, q.toString())

        q = MultiFieldQueryParser.parse(qtxt, fields, occur, a)
        assertEquals(expectedRes, q.toString())
    }

    @Test
    fun testSimple() {
        val fields = arrayOf("b", "t")
        val mfqp = MultiFieldQueryParser(fields, MockAnalyzer(random()))

        var q = mfqp.parse("one")
        assertEquals("b:one t:one", q.toString())

        q = mfqp.parse("one two")
        assertEquals("(b:one t:one) (b:two t:two)", q.toString())

        q = mfqp.parse("+one +two")
        assertEquals("+(b:one t:one) +(b:two t:two)", q.toString())

        q = mfqp.parse("+one -two -three")
        assertEquals("+(b:one t:one) -(b:two t:two) -(b:three t:three)", q.toString())

        q = mfqp.parse("one^2 two")
        assertEquals("(b:one t:one)^2.0 (b:two t:two)", q.toString())

        q = mfqp.parse("one~ two")
        assertEquals("(b:one~2 t:one~2) (b:two t:two)", q.toString())

        q = mfqp.parse("one~0.8 two^2")
        assertEquals("(b:one~0 t:one~0) (b:two t:two)^2.0", q.toString())

        q = mfqp.parse("one* two*")
        assertEquals("(b:one* t:one*) (b:two* t:two*)", q.toString())

        q = mfqp.parse("[a TO c] two")
        assertEquals("(b:[a TO c] t:[a TO c]) (b:two t:two)", q.toString())

        q = mfqp.parse("w?ldcard")
        assertEquals("b:w?ldcard t:w?ldcard", q.toString())

        q = mfqp.parse("\"foo bar\"")
        assertEquals("b:\"foo bar\" t:\"foo bar\"", q.toString())

        q = mfqp.parse("\"aa bb cc\" \"dd ee\"")
        assertEquals("(b:\"aa bb cc\" t:\"aa bb cc\") (b:\"dd ee\" t:\"dd ee\")", q.toString())

        q = mfqp.parse("\"foo bar\"~4")
        assertEquals("b:\"foo bar\"~4 t:\"foo bar\"~4", q.toString())

        // LUCENE-1213: MultiFieldQueryParser was ignoring slop when phrase had a field.
        q = mfqp.parse("b:\"foo bar\"~4")
        assertEquals("b:\"foo bar\"~4", q.toString())

        // make sure that terms which have a field are not touched:
        q = mfqp.parse("one f:two")
        assertEquals("(b:one t:one) f:two", q.toString())

        // AND mode:
        mfqp.setDefaultOperator(QueryParserBase.AND_OPERATOR)
        q = mfqp.parse("one two")
        assertEquals("+(b:one t:one) +(b:two t:two)", q.toString())
        q = mfqp.parse("\"aa bb cc\" \"dd ee\"")
        assertEquals("+(b:\"aa bb cc\" t:\"aa bb cc\") +(b:\"dd ee\" t:\"dd ee\")", q.toString())
    }

    @Test
    fun testBoostsSimple() {
        val boosts = hashMapOf<String, Float>()
        boosts["b"] = 5f
        boosts["t"] = 10f
        val fields = arrayOf("b", "t")
        val mfqp = MultiFieldQueryParser(fields, MockAnalyzer(random()), boosts)

        // Check for simple
        var q = mfqp.parse("one")
        assertEquals("(b:one)^5.0 (t:one)^10.0", q.toString())

        // Check for AND
        q = mfqp.parse("one AND two")
        assertEquals("+((b:one)^5.0 (t:one)^10.0) +((b:two)^5.0 (t:two)^10.0)", q.toString())

        // Check for OR
        q = mfqp.parse("one OR two")
        assertEquals("((b:one)^5.0 (t:one)^10.0) ((b:two)^5.0 (t:two)^10.0)", q.toString())

        // Check for AND and a field
        q = mfqp.parse("one AND two AND foo:test")
        assertEquals("+((b:one)^5.0 (t:one)^10.0) +((b:two)^5.0 (t:two)^10.0) +foo:test", q.toString())

        // Check boost with slop
        // See https://github.com/apache/lucene/issues/12195
        q = mfqp.parse("\"one two\"~2")
        assertEquals("(b:\"one two\"~2)^5.0 (t:\"one two\"~2)^10.0", q.toString())

        // check boost with fuzzy
        q = mfqp.parse("one~")
        assertEquals("(b:one~2)^5.0 (t:one~2)^10.0", q.toString())

        // check boost with prefix
        q = mfqp.parse("one*")
        assertEquals("(b:one*)^5.0 (t:one*)^10.0", q.toString())

        // check boost with wildcard
        q = mfqp.parse("o?n*e")
        assertEquals("(b:o?n*e)^5.0 (t:o?n*e)^10.0", q.toString())

        // check boost with regex
        q = mfqp.parse("/[a-z][123]/")
        assertEquals("(b:/[a-z][123]/)^5.0 (t:/[a-z][123]/)^10.0", q.toString())

        // check boost with range
        q = mfqp.parse("[one TO two]")
        assertEquals("(b:[one TO two])^5.0 (t:[one TO two])^10.0", q.toString())

        q = mfqp.parse("one^3 AND two^4")
        assertEquals("+((b:one)^5.0 (t:one)^10.0)^3.0 +((b:two)^5.0 (t:two)^10.0)^4.0", q.toString())
    }

    @Test
    fun testStaticMethod1() {
        val fields = arrayOf("b", "t")
        val queries = arrayOf("one", "two")
        var q = MultiFieldQueryParser.parse(queries, fields, MockAnalyzer(random()))
        assertEquals("b:one t:two", q.toString())

        val queries2 = arrayOf("+one", "+two")
        q = MultiFieldQueryParser.parse(queries2, fields, MockAnalyzer(random()))
        assertEquals("(+b:one) (+t:two)", q.toString())

        val queries3 = arrayOf("one", "+two")
        q = MultiFieldQueryParser.parse(queries3, fields, MockAnalyzer(random()))
        assertEquals("b:one (+t:two)", q.toString())

        val queries4 = arrayOf("one +more", "+two")
        q = MultiFieldQueryParser.parse(queries4, fields, MockAnalyzer(random()))
        assertEquals("(b:one +b:more) (+t:two)", q.toString())

        val queries5 = arrayOf("blah")
        expectThrows(IllegalArgumentException::class) {
            MultiFieldQueryParser.parse(queries5, fields, MockAnalyzer(random()))
        }

        // check also with stop words for this static form (qtxts[], fields[]).
        val stopA = QPTestAnalyzer()

        val queries6 = arrayOf("((+stop))", "+((stop))")
        q = MultiFieldQueryParser.parse(queries6, fields, stopA)
        assertEquals("", q.toString())

        val queries7 = arrayOf("one ((+stop)) +more", "+((stop)) +two")
        q = MultiFieldQueryParser.parse(queries7, fields, stopA)
        assertEquals("(b:one +b:more) (+t:two)", q.toString())
    }

    @Test
    fun testStaticMethod2() {
        val fields = arrayOf("b", "t")
        val flags = arrayOf(BooleanClause.Occur.MUST, BooleanClause.Occur.MUST_NOT)
        var q = MultiFieldQueryParser.parse("one", fields, flags, MockAnalyzer(random()))
        assertEquals("+b:one -t:one", q.toString())

        q = MultiFieldQueryParser.parse("one two", fields, flags, MockAnalyzer(random()))
        assertEquals("+(b:one b:two) -(t:one t:two)", q.toString())

        expectThrows(IllegalArgumentException::class) {
            val flags2 = arrayOf(BooleanClause.Occur.MUST)
            MultiFieldQueryParser.parse("blah", fields, flags2, MockAnalyzer(random()))
        }
    }

    @Test
    fun testStaticMethod2Old() {
        val fields = arrayOf("b", "t")
        // int[] flags = {MultiFieldQueryParser.REQUIRED_FIELD, MultiFieldQueryParser.PROHIBITED_FIELD};
        val flags = arrayOf(BooleanClause.Occur.MUST, BooleanClause.Occur.MUST_NOT)

        var q = MultiFieldQueryParser.parse("one", fields, flags, MockAnalyzer(random()))
        assertEquals("+b:one -t:one", q.toString())

        q = MultiFieldQueryParser.parse("one two", fields, flags, MockAnalyzer(random()))
        assertEquals("+(b:one b:two) -(t:one t:two)", q.toString())

        expectThrows(IllegalArgumentException::class) {
            val flags2 = arrayOf(BooleanClause.Occur.MUST)
            MultiFieldQueryParser.parse("blah", fields, flags2, MockAnalyzer(random()))
        }
    }

    @Test
    fun testStaticMethod3() {
        val queries = arrayOf("one", "two", "three")
        val fields = arrayOf("f1", "f2", "f3")
        val flags = arrayOf(BooleanClause.Occur.MUST, BooleanClause.Occur.MUST_NOT, BooleanClause.Occur.SHOULD)
        var q = MultiFieldQueryParser.parse(queries, fields, flags, MockAnalyzer(random()))
        assertEquals("+f1:one -f2:two f3:three", q.toString())

        expectThrows(IllegalArgumentException::class) {
            val flags2 = arrayOf(BooleanClause.Occur.MUST)
            MultiFieldQueryParser.parse(queries, fields, flags2, MockAnalyzer(random()))
        }
    }

    @Test
    fun testStaticMethod3Old() {
        val queries = arrayOf("one", "two")
        val fields = arrayOf("b", "t")
        val flags = arrayOf(BooleanClause.Occur.MUST, BooleanClause.Occur.MUST_NOT)
        var q = MultiFieldQueryParser.parse(queries, fields, flags, MockAnalyzer(random()))
        assertEquals("+b:one -t:two", q.toString())

        expectThrows(IllegalArgumentException::class) {
            val flags2 = arrayOf(BooleanClause.Occur.MUST)
            MultiFieldQueryParser.parse(queries, fields, flags2, MockAnalyzer(random()))
        }
    }

    @Test
    fun testAnalyzerReturningNull() {
        val fields = arrayOf("f1", "f2", "f3")
        val parser = MultiFieldQueryParser(fields, AnalyzerReturningNull())
        var q = parser.parse("bla AND blo")
        assertEquals("+(f2:bla f3:bla) +(f2:blo f3:blo)", q.toString())
        // the following queries are not affected as their terms are not analyzed anyway:
        q = parser.parse("bla*")
        assertEquals("f1:bla* f2:bla* f3:bla*", q.toString())
        q = parser.parse("bla~")
        assertEquals("f1:bla~2 f2:bla~2 f3:bla~2", q.toString())
        q = parser.parse("[a TO c]")
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

        val mfqp = MultiFieldQueryParser(arrayOf("body"), analyzer)
        mfqp.setDefaultOperator(QueryParser.Operator.AND)
        val q = mfqp.parse("the footest")
        val ir = DirectoryReader.open(ramDir)
        val `is` = newSearcher(ir)
        val hits = `is`.search(requireNotNull(q), 1000).scoreDocs
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

    @Test
    fun testSimpleRegex() {
        val fields = arrayOf("a", "b")
        val mfqp = MultiFieldQueryParser(fields, MockAnalyzer(random()))

        val bq = BooleanQuery.Builder()
        bq.add(RegexpQuery(Term("a", "[a-z][123]")), Occur.SHOULD)
        bq.add(RegexpQuery(Term("b", "[a-z][123]")), Occur.SHOULD)
        assertEquals(bq.build(), mfqp.parse("/[a-z][123]/"))
    }

    /** whitespace+lowercase analyzer with synonyms (dogs,dog) and (guinea pig,cavy) */
    private class MockSynonymAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
            return TokenStreamComponents(tokenizer, MockSynonymFilter(tokenizer))
        }
    }

    @Test
    fun testSynonyms() {
        val fields = arrayOf("b", "t")
        val parser = MultiFieldQueryParser(fields, MockSynonymAnalyzer())
        var q = parser.parse("dogs")
        assertEquals("Synonym(b:dog b:dogs) Synonym(t:dog t:dogs)", q.toString())
        q = parser.parse("guinea pig")
        assertFalse(parser.getSplitOnWhitespace())
        assertEquals("((+b:guinea +b:pig) b:cavy) ((+t:guinea +t:pig) t:cavy)", q.toString())
        parser.setSplitOnWhitespace(true)
        q = parser.parse("guinea pig")
        assertEquals("(b:guinea t:guinea) (b:pig t:pig)", q.toString())
    }
}
