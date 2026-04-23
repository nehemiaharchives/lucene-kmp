package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestElevationComparator : LuceneTestCase() {
    private lateinit var directory: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher
    private val priority = mutableMapOf<BytesRef, Int>()

    @BeforeTest
    @Throws(Exception::class)
    fun setUpTestElevationComparator() {
        directory = newDirectory()
        val writer =
            IndexWriter(
                directory,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy(1000))
                    .setSimilarity(ClassicSimilarity()),
            )
        writer.addDocument(adoc(arrayOf("id", "a", "title", "ipod", "str_s", "a")))
        writer.addDocument(adoc(arrayOf("id", "b", "title", "ipod ipod", "str_s", "b")))
        writer.addDocument(adoc(arrayOf("id", "c", "title", "ipod ipod ipod", "str_s", "c")))
        writer.addDocument(adoc(arrayOf("id", "x", "title", "boosted", "str_s", "x")))
        writer.addDocument(adoc(arrayOf("id", "y", "title", "boosted boosted", "str_s", "y")))
        writer.addDocument(adoc(arrayOf("id", "z", "title", "boosted boosted boosted", "str_s", "z")))

        reader = DirectoryReader.open(writer)
        writer.close()

        searcher = newSearcher(reader)
        searcher.similarity = BM25Similarity()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDownTestElevationComparator() {
        reader.close()
        directory.close()
    }

    @Test
    @Throws(Throwable::class)
    fun testSorting() {
        runTest(false)
    }

    @Test
    @Throws(Throwable::class)
    fun testSortingReversed() {
        runTest(true)
    }

    @Throws(Throwable::class)
    private fun runTest(reversed: Boolean) {
        val newq = BooleanQuery.Builder()
        val query = TermQuery(Term("title", "ipod"))

        newq.add(query, BooleanClause.Occur.SHOULD)
        newq.add(getElevatedQuery(arrayOf("id", "a", "id", "x")), BooleanClause.Occur.SHOULD)

        val sort =
            Sort(
                SortField("id", ElevationComparatorSource(priority), false),
                SortField(null, SortField.Type.SCORE, reversed),
            )

        val topDocs =
            searcher.search(
                newq.build(),
                TopFieldCollectorManager(sort, 50, null, Int.MAX_VALUE),
            )
        val nDocsReturned = topDocs.scoreDocs.size

        assertEquals(4, nDocsReturned)

        // 0 & 3 were elevated
        assertEquals(0, topDocs.scoreDocs[0].doc)
        assertEquals(3, topDocs.scoreDocs[1].doc)

        if (reversed) {
            assertEquals(1, topDocs.scoreDocs[2].doc)
            assertEquals(2, topDocs.scoreDocs[3].doc)
        } else {
            assertEquals(2, topDocs.scoreDocs[2].doc)
            assertEquals(1, topDocs.scoreDocs[3].doc)
        }

        /*
         StoredFields storedFields = searcher.storedFields();
         for (int i = 0; i < nDocsReturned; i++) {
          ScoreDoc scoreDoc = topDocs.scoreDocs[i];
          ids[i] = scoreDoc.doc;
          scores[i] = scoreDoc.score;
          documents[i] = storedFields.document(ids[i]);
          System.out.println("ids[i] = " + ids[i]);
          System.out.println("documents[i] = " + documents[i]);
          System.out.println("scores[i] = " + scores[i]);
         }
         */
    }

    private fun getElevatedQuery(vals: Array<String>): Query {
        val b = BooleanQuery.Builder()
        var max = (vals.size / 2) + 5
        for (i in 0..<vals.size - 1 step 2) {
            b.add(TermQuery(Term(vals[i], vals[i + 1])), BooleanClause.Occur.SHOULD)
            priority[BytesRef(vals[i + 1])] = max--
            // System.out.println(" pri doc=" + vals[i+1] + " pri=" + (1+max));
        }
        val q = b.build()
        return BoostQuery(q, 0f)
    }

    private fun adoc(vals: Array<String>): Document {
        val doc = Document()
        for (i in 0..<vals.size - 2 step 2) {
            doc.add(newTextField(vals[i], vals[i + 1], Field.Store.YES))
            if (vals[i] == "id") {
                doc.add(SortedDocValuesField(vals[i], BytesRef(vals[i + 1])))
            }
        }
        return doc
    }
}

class ElevationComparatorSource(private val priority: Map<BytesRef, Int>) : FieldComparatorSource() {
    override fun newComparator(
        fieldname: String,
        numHits: Int,
        pruning: Pruning,
        reversed: Boolean,
    ): FieldComparator<*> {
        return object : FieldComparator<Int>() {
            private val values = IntArray(numHits)
            private var bottomVal = 0

            @Throws(IOException::class)
            override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
                return object : LeafFieldComparator {
                    override fun setBottom(slot: Int) {
                        bottomVal = values[slot]
                    }

                    override fun compareTop(doc: Int): Int {
                        throw UnsupportedOperationException()
                    }

                    @Throws(IOException::class)
                    private fun docVal(doc: Int): Int {
                        val idIndex: SortedDocValues = DocValues.getSorted(context.reader(), fieldname)
                        return if (idIndex.advance(doc) == doc) {
                            val term = idIndex.lookupOrd(idIndex.ordValue())
                            val prio = priority[term]
                            prio ?: 0
                        } else {
                            0
                        }
                    }

                    @Throws(IOException::class)
                    override fun compareBottom(doc: Int): Int {
                        return docVal(doc) - bottomVal
                    }

                    @Throws(IOException::class)
                    override fun copy(slot: Int, doc: Int) {
                        values[slot] = docVal(doc)
                    }

                    override fun setScorer(scorer: Scorable) {
                    }
                }
            }

            override fun compare(slot1: Int, slot2: Int): Int {
                // values will be small enough that there is no overflow concern
                return values[slot2] - values[slot1]
            }

            override fun compareValues(first: Int?, second: Int?): Int {
                // values will be small enough that there is no overflow concern
                return second!! - first!!
            }

            override fun setTopValue(value: Int?) {
                throw UnsupportedOperationException()
            }

            override fun value(slot: Int): Int {
                return values[slot]
            }
        }
    }
}
