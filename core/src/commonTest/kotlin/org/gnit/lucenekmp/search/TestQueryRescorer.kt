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
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestQueryRescorer : LuceneTestCase() {
    private fun getSearcher(r: IndexReader): IndexSearcher {
        val searcher = newSearcher(r)

        // We rely on more tokens = lower score:
        searcher.similarity = ClassicSimilarity()

        return searcher
    }

    fun randomSentence(): String {
        val length = random().nextInt(10)
        val sentence = StringBuilder("${dictionary[0]} ")
        repeat(length) {
            sentence.append("${dictionary[random().nextInt(dictionary.size - 1)]} ")
        }
        return sentence.toString()
    }

    @Throws(Exception::class)
    private fun publishDocs(numDocs: Int, fieldName: String, dir: Directory): IndexReader {
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())
        for (i in 0..<numDocs) {
            val d = Document()
            d.add(newStringField("id", i.toString(), Field.Store.YES))
            d.add(newTextField(fieldName, randomSentence(), Field.Store.NO))
            w.addDocument(d)
        }
        val reader = w.getReader(true, false)
        w.close()
        return reader
    }

    @Test
    @Throws(Exception::class)
    fun testRescoreOfASubsetOfHits() {
        val dir = newDirectory()
        val numDocs = 100
        val fieldName = "field"
        val reader = publishDocs(numDocs, fieldName, dir)

        // Construct a query that will get numDocs hits.
        val wordOne = dictionary[0]
        val termQuery = TermQuery(Term(fieldName, wordOne))
        val searcher = getSearcher(reader)
        searcher.similarity = BM25Similarity()
        val hits = searcher.search(termQuery, numDocs)

        // Next, use a more specific phrase query that will return different scores
        // from the above term query
        val wordTwo = RandomPicks.randomFrom(random(), dictionary.toTypedArray())
        val phraseQuery = PhraseQuery(1, fieldName, wordOne, wordTwo)

        // rescore, requesting a smaller topN
        val topN = random().nextInt(numDocs - 1)
        val phraseQueryHits = QueryRescorer.rescore(searcher, hits, phraseQuery, 2.0, topN)
        assertEquals(topN, phraseQueryHits.scoreDocs.size)

        for (i in 1..<phraseQueryHits.scoreDocs.size) {
            assertTrue(phraseQueryHits.scoreDocs[i].score <= phraseQueryHits.scoreDocs[i - 1].score)
        }
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRescoreIsIdempotent() {
        val dir = newDirectory()
        val numDocs = 100
        val fieldName = "field"
        val reader = publishDocs(numDocs, fieldName, dir)

        // Construct a query that will get numDocs hits.
        val wordOne = dictionary[0]
        val termQuery = TermQuery(Term(fieldName, wordOne))
        val searcher = getSearcher(reader)
        searcher.similarity = BM25Similarity()
        val hits1 = searcher.search(termQuery, numDocs)
        val hits2 = searcher.search(termQuery, numDocs)

        // Next, use a more specific phrase query that will return different scores
        // from the above term query
        val wordTwo = RandomPicks.randomFrom(random(), dictionary.toTypedArray())
        val phraseQuery = PhraseQuery(1, fieldName, wordOne, wordTwo)

        // rescore, requesting the same hits as topN
        var topN = numDocs
        val firstRescoreHits = QueryRescorer.rescore(searcher, hits1, phraseQuery, 2.0, topN)

        // now rescore again, where topN is less than numDocs
        topN = random().nextInt(numDocs - 1)
        val secondRescoreHits = QueryRescorer.rescore(searcher, hits2, phraseQuery, 2.0, topN).scoreDocs
        val expectedTopNScoreDocs = ArrayUtil.copyOfSubArray(firstRescoreHits.scoreDocs, 0, topN)
        CheckHits.checkEqual(phraseQuery, expectedTopNScoreDocs, secondRescoreHits)

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasic() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())

        var doc = Document()
        doc.add(newStringField("id", "0", Field.Store.YES))
        doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        // 1 extra token, but wizard and oz are close;
        doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO))
        w.addDocument(doc)
        val r = w.getReader(true, false)
        w.close()

        // Do ordinary BooleanQuery:
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "wizard")), Occur.SHOULD)
        bq.add(TermQuery(Term("field", "oz")), Occur.SHOULD)
        val searcher = getSearcher(r)
        searcher.similarity = ClassicSimilarity()

        val hits = searcher.search(bq.build(), 10)
        assertEquals(2L, hits.totalHits.value)
        assertEquals("0", searcher.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(hits.scoreDocs[1].doc).get("id"))

        // Now, resort using PhraseQuery:
        val pq = PhraseQuery(5, "field", "wizard", "oz")

        val hits2 = QueryRescorer.rescore(searcher, hits, pq, 2.0, 10)

        // Resorting changed the order:
        assertEquals(2L, hits2.totalHits.value)
        assertEquals("1", searcher.storedFields().document(hits2.scoreDocs[0].doc).get("id"))
        assertEquals("0", searcher.storedFields().document(hits2.scoreDocs[1].doc).get("id"))

        r.close()
        dir.close()
    }

    // Test LUCENE-5682
    @Test
    @Throws(Exception::class)
    fun testNullScorerTermQuery() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())

        var doc = Document()
        doc.add(newStringField("id", "0", Field.Store.YES))
        doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        // 1 extra token, but wizard and oz are close;
        doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO))
        w.addDocument(doc)
        val r = w.getReader(true, false)
        w.close()

        // Do ordinary BooleanQuery:
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "wizard")), Occur.SHOULD)
        bq.add(TermQuery(Term("field", "oz")), Occur.SHOULD)
        val searcher = getSearcher(r)
        searcher.similarity = ClassicSimilarity()

        val hits = searcher.search(bq.build(), 10)
        assertEquals(2L, hits.totalHits.value)
        assertEquals("0", searcher.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(hits.scoreDocs[1].doc).get("id"))

        // Now, resort using TermQuery on term that does not exist.
        val tq = TermQuery(Term("field", "gold"))

        val hits2 = QueryRescorer.rescore(searcher, hits, tq, 2.0, 10)

        // Just testing that null scorer is handled.
        assertEquals(2L, hits2.totalHits.value)

        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCustomCombine() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())

        var doc = Document()
        doc.add(newStringField("id", "0", Field.Store.YES))
        doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        // 1 extra token, but wizard and oz are close;
        doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO))
        w.addDocument(doc)
        val r = w.getReader(true, false)
        w.close()

        // Do ordinary BooleanQuery:
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "wizard")), Occur.SHOULD)
        bq.add(TermQuery(Term("field", "oz")), Occur.SHOULD)
        val searcher = getSearcher(r)

        val hits = searcher.search(bq.build(), 10)
        assertEquals(2L, hits.totalHits.value)
        assertEquals("0", searcher.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(hits.scoreDocs[1].doc).get("id"))

        // Now, resort using PhraseQuery, but with an
        // opposite-world combine:
        val pq = PhraseQuery(5, "field", "wizard", "oz")

        val hits2 =
            object : QueryRescorer(pq) {
                override fun combine(
                    firstPassScore: Float,
                    secondPassMatches: Boolean,
                    secondPassScore: Float,
                ): Float {
                    var score = firstPassScore
                    if (secondPassMatches) {
                        score -= 2.0f * secondPassScore
                    }
                    return score
                }
            }.rescore(searcher, hits, 10)

        // Resorting didn't change the order:
        assertEquals(2L, hits2.totalHits.value)
        assertEquals("0", searcher.storedFields().document(hits2.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(hits2.scoreDocs[1].doc).get("id"))

        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExplain() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())

        var doc = Document()
        doc.add(newStringField("id", "0", Field.Store.YES))
        doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        // 1 extra token, but wizard and oz are close;
        doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO))
        w.addDocument(doc)
        val r = w.getReader(true, false)
        w.close()

        // Do ordinary BooleanQuery:
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "wizard")), Occur.SHOULD)
        bq.add(TermQuery(Term("field", "oz")), Occur.SHOULD)
        val searcher = getSearcher(r)

        val hits = searcher.search(bq.build(), 10)
        assertEquals(2L, hits.totalHits.value)
        assertEquals("0", searcher.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(hits.scoreDocs[1].doc).get("id"))

        // Now, resort using PhraseQuery:
        val pq = PhraseQuery("field", "wizard", "oz")

        val rescorer: Rescorer =
            object : QueryRescorer(pq) {
                override fun combine(
                    firstPassScore: Float,
                    secondPassMatches: Boolean,
                    secondPassScore: Float,
                ): Float {
                    var score = firstPassScore
                    if (secondPassMatches) {
                        score += 2.0f * secondPassScore
                    }
                    return score
                }
            }

        val hits2 = rescorer.rescore(searcher, hits, 10)

        // Resorting changed the order:
        assertEquals(2L, hits2.totalHits.value)
        assertEquals("1", searcher.storedFields().document(hits2.scoreDocs[0].doc).get("id"))
        assertEquals("0", searcher.storedFields().document(hits2.scoreDocs[1].doc).get("id"))

        var docID = hits2.scoreDocs[0].doc
        var explain = rescorer.explain(searcher, searcher.explain(bq.build(), docID), docID)
        var s = explain.toString()
        assertTrue(s.contains("TestQueryRescorer$"))
        assertTrue(s.contains("combined first and second pass score"))
        assertTrue(s.contains("first pass score"))
        assertTrue(s.contains("= second pass score"))
        assertEquals(hits2.scoreDocs[0].score.toDouble(), explain.value.toDouble(), 0.0)

        docID = hits2.scoreDocs[1].doc
        explain = rescorer.explain(searcher, searcher.explain(bq.build(), docID), docID)
        s = explain.toString()
        assertTrue(s.contains("TestQueryRescorer$"))
        assertTrue(s.contains("combined first and second pass score"))
        assertTrue(s.contains("first pass score"))
        assertTrue(s.contains("no second pass score"))
        assertFalse(s.contains("= second pass score"))
        assertEquals(hits2.scoreDocs[1].score.toDouble(), explain.value.toDouble(), 0.0)

        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMissingSecondPassScore() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())

        var doc = Document()
        doc.add(newStringField("id", "0", Field.Store.YES))
        doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        // 1 extra token, but wizard and oz are close;
        doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO))
        w.addDocument(doc)
        val r = w.getReader(true, false)
        w.close()

        // Do ordinary BooleanQuery:
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "wizard")), Occur.SHOULD)
        bq.add(TermQuery(Term("field", "oz")), Occur.SHOULD)
        val searcher = getSearcher(r)

        val hits = searcher.search(bq.build(), 10)
        assertEquals(2L, hits.totalHits.value)
        assertEquals("0", searcher.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(hits.scoreDocs[1].doc).get("id"))

        // Now, resort using PhraseQuery, no slop:
        val pq = PhraseQuery("field", "wizard", "oz")

        val hits2 = QueryRescorer.rescore(searcher, hits, pq, 2.0, 10)

        // Resorting changed the order:
        assertEquals(2L, hits2.totalHits.value)
        assertEquals("1", searcher.storedFields().document(hits2.scoreDocs[0].doc).get("id"))
        assertEquals("0", searcher.storedFields().document(hits2.scoreDocs[1].doc).get("id"))

        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandom() {
        val dir = newDirectory()
        val numDocs = atLeast(1000)
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())

        val idToNum = IntArray(numDocs)
        val maxValue = TestUtil.nextInt(random(), 10, 1000000)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.YES))
            val numTokens = TestUtil.nextInt(random(), 1, 10)
            val b = StringBuilder()
            repeat(numTokens) {
                b.append("a ")
            }
            doc.add(newTextField("field", b.toString(), Field.Store.NO))
            idToNum[i] = random().nextInt(maxValue)
            doc.add(NumericDocValuesField("num", idToNum[i].toLong()))
            w.addDocument(doc)
        }
        val r = w.getReader(true, false)
        w.close()

        val s = newSearcher(r)
        val numHits = TestUtil.nextInt(random(), 1, numDocs)
        val reverse = random().nextBoolean()

        // System.out.println("numHits=" + numHits + " reverse=" + reverse);
        val hits = s.search(TermQuery(Term("field", "a")), numHits)

        val hits2 =
            object : QueryRescorer(FixedScoreQuery(idToNum, reverse)) {
                override fun combine(
                    firstPassScore: Float,
                    secondPassMatches: Boolean,
                    secondPassScore: Float,
                ): Float {
                    return secondPassScore
                }
            }.rescore(s, hits, numHits)

        val expected = Array(numHits) { i -> hits.scoreDocs[i].doc }

        val reverseInt = if (reverse) -1 else 1

        expected.sortWith { a, b ->
            val av = idToNum[r.storedFields().document(a).get("id")!!.toInt()]
            val bv = idToNum[r.storedFields().document(b).get("id")!!.toInt()]
            if (av < bv) {
                -reverseInt
            } else if (bv < av) {
                reverseInt
            } else {
                // Tie break by docID, ascending
                a - b
            }
        }

        var fail = false
        for (i in 0..<numHits) {
            // System.out.println("expected=" + expected[i] + " vs " + hits2.scoreDocs[i].doc + " v=" +
            // idToNum[Integer.parseInt(r.storedFields().document(expected[i]).get("id"))]);
            if (expected[i] != hits2.scoreDocs[i].doc) {
                // System.out.println("  diff!");
                fail = true
            }
        }
        assertFalse(fail)

        r.close()
        dir.close()
    }

    /** Just assigns score == idToNum[doc("id")] for each doc. */
    private class FixedScoreQuery(
        private val idToNum: IntArray,
        private val reverse: Boolean,
    ) : Query() {
        @Throws(Exception::class)
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : Weight(this@FixedScoreQuery) {
                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                    val scorer =
                        object : Scorer() {
                            var docID = -1

                            override fun docID(): Int {
                                return docID
                            }

                            override fun iterator(): DocIdSetIterator {
                                return object : DocIdSetIterator() {
                                    override fun docID(): Int {
                                        return docID
                                    }

                                    override fun cost(): Long {
                                        return 1
                                    }

                                    override fun nextDoc(): Int {
                                        docID++
                                        if (docID >= context.reader().maxDoc()) {
                                            return NO_MORE_DOCS.also { docID = it }
                                        }
                                        return docID
                                    }

                                    override fun advance(target: Int): Int {
                                        docID = target
                                        if (docID >= context.reader().maxDoc()) {
                                            docID = NO_MORE_DOCS
                                        }
                                        return docID
                                    }
                                }
                            }

                            @Throws(IOException::class)
                            override fun score(): Float {
                                val num = idToNum[context.reader().storedFields().document(docID).get("id")!!.toInt()]
                                return if (reverse) {
                                    // System.out.println("score doc=" + docID + " num=" + num);
                                    num.toFloat()
                                } else {
                                    // System.out.println("score doc=" + docID + " num=" + -num);
                                    1f / (1 + num)
                                }
                            }

                            @Throws(IOException::class)
                            override fun getMaxScore(upTo: Int): Float {
                                return Float.POSITIVE_INFINITY
                            }
                        }
                    return DefaultScorerSupplier(scorer)
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return false
                }

                @Throws(IOException::class)
                override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                    return Explanation.noMatch("")
                }
            }
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun toString(field: String?): String {
            return "FixedScoreQuery ${idToNum.size} ids; reverse=$reverse"
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && equalsTo(other as FixedScoreQuery)
        }

        private fun equalsTo(other: FixedScoreQuery): Boolean {
            return reverse == other.reverse && idToNum.contentEquals(other.idToNum)
        }

        override fun hashCode(): Int {
            var hash = classHash()
            hash = 31 * hash + if (reverse) 0 else 1
            hash = 31 * hash + idToNum.contentHashCode()
            return hash
        }
    }

    companion object {
        // We rely on more tokens = lower score:
        fun newIndexWriterConfig(): IndexWriterConfig {
            return LuceneTestCase.newIndexWriterConfig().setSimilarity(ClassicSimilarity())
        }

        private val dictionary = listOf("river", "quick", "brown", "fox", "jumped", "lazy", "fence")
    }
}
