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
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** This class tests the MultiPhraseQuery class. */
class TestMultiPhraseQuery : LuceneTestCase() {
    @Test
    fun testPhrasePrefix() {
        val indexStore = newDirectory()
        val writer = RandomIndexWriter(random(), indexStore)
        add("blueberry pie", writer)
        add("blueberry strudel", writer)
        add("blueberry pizza", writer)
        add("blueberry chewing gum", writer)
        add("bluebird pizza", writer)
        add("bluebird foobar pizza", writer)
        add("piccadilly circus", writer)

        val reader = writer.getReader(true, false)
        val searcher = newSearcher(reader)

        // search for "blueberry pi*":
        val query1builder = MultiPhraseQuery.Builder()
        // search for "strawberry pi*":
        val query2builder = MultiPhraseQuery.Builder()
        query1builder.add(Term("body", "blueberry"))
        query2builder.add(Term("body", "strawberry"))

        val termsWithPrefix = ArrayList<Term>()

        // this TermEnum gives "piccadilly", "pie" and "pizza".
        var prefix = "pi"
        val te = MultiTerms.getTerms(reader, "body")!!.iterator()
        te.seekCeil(BytesRef(prefix))
        do {
            val s = te.term()!!.utf8ToString()
            if (s.startsWith(prefix)) {
                termsWithPrefix.add(Term("body", s))
            } else {
                break
            }
        } while (te.next() != null)

        query1builder.add(termsWithPrefix.toTypedArray())
        var query1 = query1builder.build()
        assertEquals("body:\"blueberry (piccadilly pie pizza)\"", query1.toString())

        query2builder.add(termsWithPrefix.toTypedArray())
        val query2 = query2builder.build()
        assertEquals("body:\"strawberry (piccadilly pie pizza)\"", query2.toString())

        var result: Array<ScoreDoc>
        result = searcher.search(query1, 1000).scoreDocs
        assertEquals(2, result.size)
        result = searcher.search(query2, 1000).scoreDocs
        assertEquals(0, result.size)

        // search for "blue* pizza":
        val query3builder = MultiPhraseQuery.Builder()
        termsWithPrefix.clear()
        prefix = "blue"
        te.seekCeil(BytesRef(prefix))

        do {
            if (te.term()!!.utf8ToString().startsWith(prefix)) {
                termsWithPrefix.add(Term("body", te.term()!!.utf8ToString()))
            }
        } while (te.next() != null)

        query3builder.add(termsWithPrefix.toTypedArray())
        query3builder.add(Term("body", "pizza"))

        var query3 = query3builder.build()

        result = searcher.search(query3, 1000).scoreDocs
        assertEquals(2, result.size) // blueberry pizza, bluebird pizza
        assertEquals("body:\"(blueberry bluebird) pizza\"", query3.toString())

        // test slop:
        query3builder.setSlop(1)
        query3 = query3builder.build()
        result = searcher.search(query3, 1000).scoreDocs

        // just make sure no exc:
        searcher.explain(query3, 0)

        assertEquals(3, result.size) // blueberry pizza, bluebird pizza, bluebird
        // foobar pizza

        val query4builder = MultiPhraseQuery.Builder()
        expectThrows(IllegalArgumentException::class) {
            query4builder.add(Term("field1", "foo"))
            query4builder.add(Term("field2", "foobar"))
        }

        writer.close()
        reader.close()
        indexStore.close()
    }

    // LUCENE-2580
    @Test
    fun testTall() {
        val indexStore = newDirectory()
        val writer = RandomIndexWriter(random(), indexStore)
        add("blueberry chocolate pie", writer)
        add("blueberry chocolate tart", writer)
        val r = writer.getReader(true, false)
        writer.close()

        val searcher = newSearcher(r)
        val qb = MultiPhraseQuery.Builder()
        qb.add(Term("body", "blueberry"))
        qb.add(Term("body", "chocolate"))
        qb.add(arrayOf(Term("body", "pie"), Term("body", "tart")))
        assertEquals(2, searcher.count(qb.build()))
        r.close()
        indexStore.close()
    }

    @Test
    fun testMultiExactWithRepeats() {
        val indexStore = newDirectory()
        val writer = RandomIndexWriter(random(), indexStore)
        add("a b c d e f g h i k", writer)
        val r = writer.getReader(true, false)
        writer.close()

        val searcher = newSearcher(r)
        val qb = MultiPhraseQuery.Builder()
        qb.add(arrayOf(Term("body", "a"), Term("body", "d")), 0)
        qb.add(arrayOf(Term("body", "a"), Term("body", "f")), 2)
        assertEquals(1, searcher.count(qb.build())) // should match on "a b"
        r.close()
        indexStore.close()
    }

    private fun add(s: String, writer: RandomIndexWriter) {
        val doc = Document()
        doc.add(newTextField("body", s, Field.Store.YES))
        writer.addDocument(doc)
    }

    @Test
    fun testBooleanQueryContainingSingleTermPrefixQuery() {
        // this tests against bug 33161 (now fixed)
        // In order to cause the bug, the outer query must have more than one term
        // and all terms required.
        // The contained PhraseMultiQuery must contain exactly one term array.
        val indexStore = newDirectory()
        val writer = RandomIndexWriter(random(), indexStore)
        add("blueberry pie", writer)
        add("blueberry chewing gum", writer)
        add("blue raspberry pie", writer)

        val reader = writer.getReader(true, false)
        val searcher = newSearcher(reader)
        // This query will be equivalent to +body:pie +body:"blue*"
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("body", "pie")), BooleanClause.Occur.MUST)

        val troubleBuilder = MultiPhraseQuery.Builder()
        troubleBuilder.add(arrayOf(Term("body", "blueberry"), Term("body", "blue")))
        q.add(troubleBuilder.build(), BooleanClause.Occur.MUST)

        // exception will be thrown here without fix
        val hits = searcher.search(q.build(), 1000).scoreDocs

        assertEquals(2, hits.size, "Wrong number of hits")

        // just make sure no exc:
        searcher.explain(q.build(), 0)

        writer.close()
        reader.close()
        indexStore.close()
    }

    @Test
    fun testPhrasePrefixWithBooleanQuery() {
        val indexStore = newDirectory()
        val writer = RandomIndexWriter(random(), indexStore)
        add("This is a test", "object", writer)
        add("a note", "note", writer)

        val reader = writer.getReader(true, false)
        val searcher = newSearcher(reader)

        // This query will be equivalent to +type:note +body:"a t*"
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("type", "note")), BooleanClause.Occur.MUST)

        val troubleBuilder = MultiPhraseQuery.Builder()
        troubleBuilder.add(Term("body", "a"))
        troubleBuilder.add(arrayOf(Term("body", "test"), Term("body", "this")))
        q.add(troubleBuilder.build(), BooleanClause.Occur.MUST)

        // exception will be thrown here without fix for #35626:
        val hits = searcher.search(q.build(), 1000).scoreDocs
        assertEquals(0, hits.size, "Wrong number of hits")
        writer.close()
        reader.close()
        indexStore.close()
    }

    @Test
    fun testNoDocs() {
        val indexStore = newDirectory()
        val writer = RandomIndexWriter(random(), indexStore)
        add("a note", "note", writer)

        val reader = writer.getReader(true, false)
        val searcher = newSearcher(reader)

        val qb = MultiPhraseQuery.Builder()
        qb.add(Term("body", "a"))
        qb.add(arrayOf(Term("body", "nope"), Term("body", "nope")))
        val q = qb.build()
        assertEquals(0, searcher.count(q), "Wrong number of hits")

        // just make sure no exc:
        searcher.explain(q, 0)

        writer.close()
        reader.close()
        indexStore.close()
    }

    @Test
    fun testHashCodeAndEquals() {
        val query1builder = MultiPhraseQuery.Builder()
        var query1 = query1builder.build()

        val query2builder = MultiPhraseQuery.Builder()
        var query2 = query2builder.build()

        assertEquals(query1.hashCode(), query2.hashCode())
        assertEquals(query1, query2)

        val term1 = Term("someField", "someText")

        query1builder.add(term1)
        query1 = query1builder.build()

        query2builder.add(term1)
        query2 = query2builder.build()

        assertEquals(query1.hashCode(), query2.hashCode())
        assertEquals(query1, query2)

        val term2 = Term("someField", "someMoreText")

        query1builder.add(term2)
        query1 = query1builder.build()

        assertFalse(query1.hashCode() == query2.hashCode())
        assertFalse(query1 == query2)

        query2builder.add(term2)
        query2 = query2builder.build()

        assertEquals(query1.hashCode(), query2.hashCode())
        assertEquals(query1, query2)
    }

    private fun add(s: String, type: String, writer: RandomIndexWriter) {
        val doc = Document()
        doc.add(newTextField("body", s, Field.Store.YES))
        doc.add(newStringField("type", type, Field.Store.NO))
        writer.addDocument(doc)
    }

    // LUCENE-2526
    @Test
    fun testEmptyToString() {
        MultiPhraseQuery.Builder().build().toString()
    }

    @Test
    fun testZeroPosIncr() {
        val dir = ByteBuffersDirectory()
        val tokens = arrayOfNulls<Token>(3)
        tokens[0] = Token()
        tokens[0]!!.append("a")
        tokens[0]!!.setPositionIncrement(1)
        tokens[1] = Token()
        tokens[1]!!.append("b")
        tokens[1]!!.setPositionIncrement(0)
        tokens[2] = Token()
        tokens[2]!!.append("c")
        tokens[2]!!.setPositionIncrement(0)

        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(TextField("field", CannedTokenStream(*tokens.map { it!! }.toTypedArray())))
        writer.addDocument(doc)
        doc = Document()
        doc.add(TextField("field", CannedTokenStream(*tokens.map { it!! }.toTypedArray())))
        writer.addDocument(doc)
        val r = writer.getReader(true, false)
        writer.close()
        val s = newSearcher(r)
        val mpqb = MultiPhraseQuery.Builder()
        // mpq.setSlop(1);

        // NOTE: not great that if we do the else clause here we
        // get different scores!  MultiPhraseQuery counts that
        // phrase as occurring twice per doc (it should be 1, I
        // think?).  This is because MultipleTermPositions is able to
        // return the same position more than once (0, in this
        // case):
        if (true) {
            mpqb.add(arrayOf(Term("field", "b"), Term("field", "c")), 0)
            mpqb.add(arrayOf(Term("field", "a")), 0)
        } else {
            mpqb.add(arrayOf(Term("field", "a")), 0)
            mpqb.add(arrayOf(Term("field", "b"), Term("field", "c")), 0)
        }
        val hits = s.search(mpqb.build(), 2)
        assertEquals(2L, hits.totalHits.value)
        assertEquals(hits.scoreDocs[0].score, hits.scoreDocs[1].score, 1e-5f)
        /*
        for(int hit=0;hit<hits.totalHits.value();hit++) {
          ScoreDoc sd = hits.scoreDocs[hit];
          System.out.println("  hit doc=" + sd.doc + " score=" + sd.score);
        }
        */
        r.close()
        dir.close()
    }

    companion object {
        private fun makeToken(text: String, posIncr: Int): Token {
            val t = Token()
            t.append(text)
            t.setPositionIncrement(posIncr)
            return t
        }

        private val INCR_0_DOC_TOKENS =
            arrayOf(
                makeToken("x", 1),
                makeToken("a", 1),
                makeToken("1", 0),
                makeToken("m", 1), // not existing, relying on slop=2
                makeToken("b", 1),
                makeToken("1", 0),
                makeToken("n", 1), // not existing, relying on slop=2
                makeToken("c", 1),
                makeToken("y", 1)
            )

        private val INCR_0_QUERY_TOKENS_AND =
            arrayOf(
                makeToken("a", 1),
                makeToken("1", 0),
                makeToken("b", 1),
                makeToken("1", 0),
                makeToken("c", 1)
            )

        private val INCR_0_QUERY_TOKENS_AND_OR_MATCH =
            arrayOf(
                arrayOf(makeToken("a", 1)),
                arrayOf(makeToken("x", 1), makeToken("1", 0)),
                arrayOf(makeToken("b", 2)),
                arrayOf(makeToken("x", 2), makeToken("1", 0)),
                arrayOf(makeToken("c", 3))
            )

        private val INCR_0_QUERY_TOKENS_AND_OR_NO_MATCHN =
            arrayOf(
                arrayOf(makeToken("x", 1)),
                arrayOf(makeToken("a", 1), makeToken("1", 0)),
                arrayOf(makeToken("x", 2)),
                arrayOf(makeToken("b", 2), makeToken("1", 0)),
                arrayOf(makeToken("c", 3))
            )
    }

    /**
     * using query parser, MPQ will be created, and will not be strict about having all query terms in
     * each position - one of each position is sufficient (OR logic)
     */
    @Test
    fun testZeroPosIncrSloppyParsedAnd() {
        val qb = MultiPhraseQuery.Builder()
        qb.add(arrayOf(Term("field", "a"), Term("field", "1")), -1)
        qb.add(arrayOf(Term("field", "b"), Term("field", "1")), 0)
        qb.add(arrayOf(Term("field", "c")), 1)
        doTestZeroPosIncrSloppy(qb.build(), 0)
        qb.setSlop(1)
        doTestZeroPosIncrSloppy(qb.build(), 0)
        qb.setSlop(2)
        doTestZeroPosIncrSloppy(qb.build(), 1)
    }

    private fun doTestZeroPosIncrSloppy(q: Query, nExpected: Int) {
        val dir = newDirectory() // random dir
        val cfg = newIndexWriterConfig()
        val writer = IndexWriter(dir, cfg)
        val doc = Document()
        doc.add(TextField("field", CannedTokenStream(*INCR_0_DOC_TOKENS)))
        writer.addDocument(doc)
        val r = DirectoryReader.open(writer)
        writer.close()
        val s = newSearcher(r)

        if (VERBOSE) {
            println("QUERY=$q")
        }

        val hits = s.search(q, 1)
        assertEquals(nExpected.toLong(), hits.totalHits.value, "wrong number of results")

        if (VERBOSE) {
            for (hit in 0..<hits.totalHits.value.toInt()) {
                val sd = hits.scoreDocs[hit]
                println("  hit doc=${sd.doc} score=${sd.score}")
            }
        }

        r.close()
        dir.close()
    }

    /** PQ AND Mode - Manually creating a phrase query */
    @Test
    fun testZeroPosIncrSloppyPqAnd() {
        val builder = PhraseQuery.Builder()
        var pos = -1
        for (tap in INCR_0_QUERY_TOKENS_AND) {
            pos += tap.getPositionIncrement()
            builder.add(Term("field", tap.toString()), pos)
        }
        builder.setSlop(0)
        doTestZeroPosIncrSloppy(builder.build(), 0)
        builder.setSlop(1)
        doTestZeroPosIncrSloppy(builder.build(), 0)
        builder.setSlop(2)
        doTestZeroPosIncrSloppy(builder.build(), 1)
    }

    /** MPQ AND Mode - Manually creating a multiple phrase query */
    @Test
    fun testZeroPosIncrSloppyMpqAnd() {
        val mpqb = MultiPhraseQuery.Builder()
        var pos = -1
        for (tap in INCR_0_QUERY_TOKENS_AND) {
            pos += tap.getPositionIncrement()
            mpqb.add(arrayOf(Term("field", tap.toString())), pos) // AND logic
        }
        doTestZeroPosIncrSloppy(mpqb.build(), 0)
        mpqb.setSlop(1)
        doTestZeroPosIncrSloppy(mpqb.build(), 0)
        mpqb.setSlop(2)
        doTestZeroPosIncrSloppy(mpqb.build(), 1)
    }

    /** MPQ Combined AND OR Mode - Manually creating a multiple phrase query */
    @Test
    fun testZeroPosIncrSloppyMpqAndOrMatch() {
        val mpqb = MultiPhraseQuery.Builder()
        for (tap in INCR_0_QUERY_TOKENS_AND_OR_MATCH) {
            val terms = tapTerms(tap)
            val pos = tap[0].getPositionIncrement() - 1
            mpqb.add(terms, pos) // AND logic in pos, OR across lines
        }
        doTestZeroPosIncrSloppy(mpqb.build(), 0)
        mpqb.setSlop(1)
        doTestZeroPosIncrSloppy(mpqb.build(), 0)
        mpqb.setSlop(2)
        doTestZeroPosIncrSloppy(mpqb.build(), 1)
    }

    /** MPQ Combined AND OR Mode - Manually creating a multiple phrase query - with no match */
    @Test
    fun testZeroPosIncrSloppyMpqAndOrNoMatch() {
        val mpqb = MultiPhraseQuery.Builder()
        for (tap in INCR_0_QUERY_TOKENS_AND_OR_NO_MATCHN) {
            val terms = tapTerms(tap)
            val pos = tap[0].getPositionIncrement() - 1
            mpqb.add(terms, pos) // AND logic in pos, OR across lines
        }
        doTestZeroPosIncrSloppy(mpqb.build(), 0)
        mpqb.setSlop(2)
        doTestZeroPosIncrSloppy(mpqb.build(), 0)
    }

    private fun tapTerms(tap: Array<Token>): Array<Term> {
        val terms = arrayOfNulls<Term>(tap.size)
        for (i in terms.indices) {
            terms[i] = Term("field", tap[i].toString())
        }
        return terms.map { it!! }.toTypedArray()
    }

    @Test
    fun testNegativeSlop() {
        val queryBuilder = MultiPhraseQuery.Builder()
        queryBuilder.add(Term("field", "two"))
        queryBuilder.add(Term("field", "one"))
        expectThrows(IllegalArgumentException::class) {
            queryBuilder.setSlop(-2)
        }
    }
}
