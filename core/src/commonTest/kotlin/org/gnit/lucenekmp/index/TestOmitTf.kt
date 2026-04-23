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
package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.CollectorManager
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.SimpleCollector
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.search.similarities.TFIDFSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestOmitTf : LuceneTestCase() {

    class SimpleSimilarity : TFIDFSimilarity() {
        override fun lengthNorm(length: Int): Float {
            return 1f
        }

        override fun tf(freq: Float): Float {
            return freq
        }

        override fun idf(docFreq: Long, docCount: Long): Float {
            return 1.0f
        }

        override fun idfExplain(
            collectionStats: CollectionStatistics,
            termStats: TermStatistics,
        ): Explanation {
            return Explanation.match(1.0f, "Inexplicable")
        }
    }

    // Make sure first adding docs that do not omitTermFreqAndPositions for
    // field X, then adding docs that do omitTermFreqAndPositions for that same
    // field,
    @Test
    @Throws(Exception::class)
    fun testMixedRAM() {
        val ram = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val writer =
            IndexWriter(
                ram,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(newLogMergePolicy(2)),
            )
        val d = Document()

        // this field will have Tf
        val f1 = newField("f1", "This field has term freqs", normalType)
        d.add(f1)

        // this field will NOT have Tf
        val f2 = newField("f2", "This field has NO Tf in all docs", omitType)
        d.add(f2)

        for (i in 0..<5) writer.addDocument(d)

        for (i in 0..<20) writer.addDocument(d)

        // force merge
        writer.forceMerge(1)

        // flush
        writer.close()

        val reader = getOnlyLeafReader(DirectoryReader.open(ram))
        val fi = reader.fieldInfos
        assertEquals(
            IndexOptions.DOCS_AND_FREQS_AND_POSITIONS,
            fi.fieldInfo("f1")!!.indexOptions,
            "OmitTermFreqAndPositions field bit should not be set.",
        )
        assertEquals(
            IndexOptions.DOCS,
            fi.fieldInfo("f2")!!.indexOptions,
            "OmitTermFreqAndPositions field bit should be set.",
        )

        reader.close()
        ram.close()
    }

    @Throws(Throwable::class)
    private fun assertNoPrx(dir: Directory) {
        val files = dir.listAll()
        for (i in files.indices) {
            assertFalse(files[i].endsWith(".prx"))
            assertFalse(files[i].endsWith(".pos"))
        }
    }

    // Verifies no *.prx exists when all fields omit term freq:
    @Test
    @Throws(Throwable::class)
    fun testNoPrxFile() {
        val ram = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val writer =
            IndexWriter(
                ram,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(3)
                    .setMergePolicy(newLogMergePolicy()),
            )
        val lmp = writer.config.mergePolicy as LogMergePolicy
        lmp.mergeFactor = 2
        lmp.noCFSRatio = 0.0
        val d = Document()

        val f1 = newField("f1", "This field has term freqs", omitType)
        d.add(f1)

        for (i in 0..<30) writer.addDocument(d)

        writer.commit()

        assertNoPrx(ram)

        writer.close()
        ram.close()
    }

    // Test scores with one field with Term Freqs and one without, otherwise with equal content
    @Test
    @Throws(Exception::class)
    fun testBasic() {
        val dir = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(2)
                    .setSimilarity(SimpleSimilarity())
                    .setMergePolicy(newLogMergePolicy(2)),
            )

        val sb = StringBuilder(265)
        val term = "term"
        for (i in 0..<30) {
            val d = Document()
            sb.append(term).append(" ")
            val content = sb.toString()
            val noTf = newField("noTf", content + if (i % 2 == 0) "" else " notf", omitType)
            d.add(noTf)

            val tf = newField("tf", content + if (i % 2 == 0) " tf" else "", normalType)
            d.add(tf)

            writer.addDocument(d)
            // System.out.println(d);
        }

        writer.forceMerge(1)
        // flush
        writer.close()

        /*
         * Verify the index
         */
        val reader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)
        searcher.similarity = SimpleSimilarity()

        val a = Term("noTf", term)
        val b = Term("tf", term)
        val c = Term("noTf", "notf")
        val d = Term("tf", "tf")
        val q1 = TermQuery(a)
        val q2 = TermQuery(b)
        val q3 = TermQuery(c)
        val q4 = TermQuery(d)

        val pq = PhraseQuery(a.field(), a.bytes(), c.bytes())
        val expected =
            expectThrows(Exception::class) {
                searcher.search(pq, 10)
            }
        var cause: Throwable = expected
        // If the searcher uses an executor service, the IAE is wrapped into other exceptions
        while (cause.cause != null) {
            cause = cause.cause!!
        }
        assertTrue(cause is IllegalStateException, "Expected an IAE, got $cause")

        searcher.search(
            q1,
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : ScoreAssertingCollector() {
                        @Throws(IOException::class)
                        override fun collect(doc: Int) {
                            // System.out.println("Q1: Doc=" + doc + " score=" + score);
                            val score = scorer!!.score()
                            assertTrue(score == 1.0f, "got score=$score")
                        }
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>): Unit = Unit
            },
        )

        searcher.search(
            q2,
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : ScoreAssertingCollector() {
                        @Throws(IOException::class)
                        override fun collect(doc: Int) {
                            // System.out.println("Q2: Doc=" + doc + " score=" + score);
                            val score = scorer!!.score()
                            assertEquals(1.0f + doc, score, 0.00001f)
                        }
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>): Unit = Unit
            },
        )

        searcher.search(
            q3,
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : ScoreAssertingCollector() {
                        @Throws(IOException::class)
                        override fun collect(doc: Int) {
                            // System.out.println("Q1: Doc=" + doc + " score=" + score);
                            val score = scorer!!.score()
                            assertTrue(score == 1.0f)
                            assertFalse(doc % 2 == 0)
                        }
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>): Unit = Unit
            },
        )

        searcher.search(
            q4,
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : ScoreAssertingCollector() {
                        @Throws(IOException::class)
                        override fun collect(doc: Int) {
                            val score = scorer!!.score()
                            // System.out.println("Q1: Doc=" + doc + " score=" + score);
                            assertTrue(score == 1.0f)
                            assertTrue(doc % 2 == 0)
                        }
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>): Unit = Unit
            },
        )

        val bq = BooleanQuery.Builder()
        bq.add(q1, Occur.MUST)
        bq.add(q4, Occur.MUST)

        val count = searcher.count(bq.build())
        assertEquals(15, count)

        reader.close()
        dir.close()
    }

    /**
     * test that when freqs are omitted, that totalTermFreq and sumTotalTermFreq are docFreq, and
     * sumDocFreq
     */
    @Test
    @Throws(Exception::class)
    fun testStats() {
        val dir = newDirectory()
        val iw =
            RandomIndexWriter(random(), dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS)
        ft.freeze()
        val f = newField("foo", "bar", ft)
        doc.add(f)
        iw.addDocument(doc)
        val ir = iw.getReader(true, false)
        iw.close()
        val docFreq: Int = ir.docFreq(Term("foo", BytesRef("bar")))
        val totalTermFreq: Long = ir.totalTermFreq(Term("foo", BytesRef("bar")))
        assertEquals(totalTermFreq, docFreq.toLong())
        val sumDocFreq = ir.getSumDocFreq("foo")
        val sumTotalTermFreq = ir.getSumTotalTermFreq("foo")
        assertEquals(sumDocFreq, sumTotalTermFreq)
        ir.close()
        dir.close()
    }

    private abstract class ScoreAssertingCollector : SimpleCollector() {
        override var scorer: Scorable? = null
        override var weight: Weight? = null

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }
    }

    companion object {
        val omitType = FieldType(TextField.TYPE_NOT_STORED)
        val normalType = FieldType(TextField.TYPE_NOT_STORED)

        init {
            omitType.setIndexOptions(IndexOptions.DOCS)
        }
    }
}
