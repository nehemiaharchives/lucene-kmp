package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleDocValuesField
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FloatDocValuesField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.English
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import okio.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.Test

/** Tests IndexSearcher's searchAfter() method */
class TestSearchAfter : LuceneTestCase() {
    private lateinit var dir: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher
    private var iter: Int = 0
    private lateinit var allSortFields: MutableList<SortField>

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        allSortFields = mutableListOf(
            SortField("int", SortField.Type.INT, false),
            SortField("long", SortField.Type.LONG, false),
            SortField("float", SortField.Type.FLOAT, false),
            SortField("double", SortField.Type.DOUBLE, false),
            SortField("bytes", SortField.Type.STRING, false),
            SortField("bytesval", SortField.Type.STRING_VAL, false),
            SortField("int", SortField.Type.INT, true),
            SortField("long", SortField.Type.LONG, true),
            SortField("float", SortField.Type.FLOAT, true),
            SortField("double", SortField.Type.DOUBLE, true),
            SortField("bytes", SortField.Type.STRING, true),
            SortField("bytesval", SortField.Type.STRING_VAL, true),
            SortField.FIELD_SCORE,
            SortField.FIELD_DOC,
        )

        // Also test missing first / last for the "string" sorts:
        for (field in arrayOf("bytes", "sortedbytesdocvalues")) {
            for (rev in 0..<2) {
                val reversed = rev == 0
                var sf = SortField(field, SortField.Type.STRING, reversed)
                sf.missingValue = SortField.STRING_FIRST
                allSortFields.add(sf)

                sf = SortField(field, SortField.Type.STRING, reversed)
                sf.missingValue = SortField.STRING_LAST
                allSortFields.add(sf)
            }
        }

        // Also test missing first / last for the "string_val" sorts:
        for (field in arrayOf("sortedbytesdocvaluesval", "straightbytesdocvalues")) {
            for (rev in 0..<2) {
                val reversed = rev == 0
                var sf = SortField(field, SortField.Type.STRING_VAL, reversed)
                sf.missingValue = SortField.STRING_FIRST
                allSortFields.add(sf)

                sf = SortField(field, SortField.Type.STRING_VAL, reversed)
                sf.missingValue = SortField.STRING_LAST
                allSortFields.add(sf)
            }
        }

        val limit = allSortFields.size
        for (i in 0..<limit) {
            val sf = allSortFields[i]
            if (sf.type == SortField.Type.INT) {
                val sf2 = SortField(sf.field, SortField.Type.INT, sf.reverse)
                sf2.missingValue = random().nextInt()
                allSortFields.add(sf2)
            } else if (sf.type == SortField.Type.LONG) {
                val sf2 = SortField(sf.field, SortField.Type.LONG, sf.reverse)
                sf2.missingValue = random().nextLong()
                allSortFields.add(sf2)
            } else if (sf.type == SortField.Type.FLOAT) {
                val sf2 = SortField(sf.field, SortField.Type.FLOAT, sf.reverse)
                sf2.missingValue = random().nextFloat()
                allSortFields.add(sf2)
            } else if (sf.type == SortField.Type.DOUBLE) {
                val sf2 = SortField(sf.field, SortField.Type.DOUBLE, sf.reverse)
                sf2.missingValue = random().nextDouble()
                allSortFields.add(sf2)
            }
        }

        dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(200)
        val r = random()
        for (i in 0..<numDocs) {
            val fields: MutableList<Field> = ArrayList()
            fields.add(newTextField("english", English.intToEnglish(i), Field.Store.NO))
            fields.add(newTextField("oddeven", if (i % 2 == 0) "even" else "odd", Field.Store.NO))
            fields.add(NumericDocValuesField("byte", r.nextInt().toByte().toLong()))
            fields.add(NumericDocValuesField("short", r.nextInt().toShort().toLong()))
            fields.add(NumericDocValuesField("int", r.nextInt().toLong()))
            fields.add(NumericDocValuesField("long", r.nextLong()))
            fields.add(FloatDocValuesField("float", r.nextFloat()))
            fields.add(DoubleDocValuesField("double", r.nextDouble()))
            fields.add(
                SortedDocValuesField(
                    "bytes",
                    BytesRef(TestUtil.randomRealisticUnicodeString(random())),
                )
            )
            fields.add(
                BinaryDocValuesField(
                    "bytesval",
                    BytesRef(TestUtil.randomRealisticUnicodeString(random())),
                )
            )

            val document = Document()
            document.add(StoredField("id", "$i"))
            if (VERBOSE) {
                println("  add doc id=$i")
            }
            for (field in fields) {
                // So we are sometimes missing that field:
                if (random().nextInt(5) != 4) {
                    document.add(field)
                    if (VERBOSE) {
                        println("    $field")
                    }
                }
            }

            iw.addDocument(document)

            if (random().nextInt(50) == 17) {
                iw.commit()
            }
        }
        reader = iw.reader
        iw.close()
        searcher = newSearcher(reader)
        if (VERBOSE) {
            println("  searcher=$searcher")
        }
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testQueries() {
        // because the first page has a null 'after', we get a normal collector.
        // so we need to run the test a few times to ensure we will collect multiple
        // pages.
        val n = atLeast(20)
        for (i in 0..<n) {
            assertQuery(MatchAllDocsQuery())
            assertQuery(TermQuery(Term("english", "one")))
            val bq = BooleanQuery.Builder()
            bq.add(TermQuery(Term("english", "one")), BooleanClause.Occur.SHOULD)
            bq.add(TermQuery(Term("oddeven", "even")), BooleanClause.Occur.SHOULD)
            assertQuery(bq.build())
        }
    }

    @Throws(Exception::class)
    fun assertQuery(query: Query) {
        assertQuery(query, null)
        assertQuery(query, Sort.RELEVANCE)
        assertQuery(query, Sort.INDEXORDER)
        for (sortField in allSortFields) {
            assertQuery(query, Sort(sortField))
        }
        for (i in 0..<20) {
            assertQuery(query, getRandomSort())
        }
    }

    fun getRandomSort(): Sort {
        val sortFields = arrayOfNulls<SortField>(TestUtil.nextInt(random(), 2, 7))
        for (i in sortFields.indices) {
            sortFields[i] = allSortFields[random().nextInt(allSortFields.size)]
        }
        return Sort(*sortFields.requireNoNulls())
    }

    @Throws(Exception::class)
    fun assertQuery(query: Query, sort: Sort?) {
        val maxDoc = searcher.indexReader.maxDoc()
        val pageSize = TestUtil.nextInt(random(), 1, maxDoc * 2)
        if (VERBOSE) {
            println("\nassertQuery ${iter++}: query=$query sort=$sort pageSize=$pageSize")
        }
        val doScores: Boolean
        val allManager: CollectorManager<*, out TopDocs>
        if (sort == null) {
            allManager = TopScoreDocCollectorManager(maxDoc, null, Int.MAX_VALUE)
            doScores = false
        } else if (sort == Sort.RELEVANCE) {
            allManager = TopFieldCollectorManager(sort, maxDoc, null, Int.MAX_VALUE)
            doScores = true
        } else {
            allManager = TopFieldCollectorManager(sort, maxDoc, null, Int.MAX_VALUE)
            doScores = random().nextBoolean()
        }
        val all = searcher.search(query, allManager)
        if (doScores) {
            TopFieldCollector.populateScores(all.scoreDocs, searcher, query)
        }

        if (VERBOSE) {
            println("  all.totalHits.value()=${all.totalHits.value}")
            var upto = 0
            val storedFields = searcher.storedFields()
            for (scoreDoc in all.scoreDocs) {
                println("    hit ${upto++}: id=${storedFields.document(scoreDoc.doc).get("id")} $scoreDoc")
            }
        }
        var pageStart = 0
        var lastBottom: ScoreDoc? = null
        while (pageStart < all.totalHits.value) {
            val pagedManager: CollectorManager<*, out TopDocs> =
                if (sort == null) {
                    if (VERBOSE) {
                        println("  iter lastBottom=$lastBottom")
                    }
                    TopScoreDocCollectorManager(pageSize, lastBottom, Int.MAX_VALUE)
                } else {
                    if (VERBOSE) {
                        println("  iter lastBottom=$lastBottom")
                    }
                    TopFieldCollectorManager(sort, pageSize, lastBottom as FieldDoc?, Int.MAX_VALUE)
                }
            val paged = searcher.search(query, pagedManager)
            if (doScores) {
                TopFieldCollector.populateScores(paged.scoreDocs, searcher, query)
            }

            if (VERBOSE) {
                println("    ${paged.scoreDocs.size} hits on page")
            }

            if (paged.scoreDocs.isEmpty()) {
                break
            }
            assertPage(pageStart, all, paged)
            pageStart += paged.scoreDocs.size
            lastBottom = paged.scoreDocs[paged.scoreDocs.size - 1]
        }
        assertEquals(all.scoreDocs.size, pageStart)
    }

    @Throws(IOException::class)
    fun assertPage(pageStart: Int, all: TopDocs, paged: TopDocs) {
        assertEquals(all.totalHits.value, paged.totalHits.value)
        val storedFields: StoredFields = searcher.storedFields()
        for (i in paged.scoreDocs.indices) {
            val sd1 = all.scoreDocs[pageStart + i]
            val sd2 = paged.scoreDocs[i]
            if (VERBOSE) {
                println("    hit ${pageStart + i}")
                println("      expected id=${storedFields.document(sd1.doc).get("id")} $sd1")
                println("        actual id=${storedFields.document(sd2.doc).get("id")} $sd2")
            }
            assertEquals(sd1.doc, sd2.doc)
            assertEquals(sd1.score, sd2.score, 0f)
            if (sd1 is FieldDoc) {
                assertIs<FieldDoc>(sd2)
                assertTrue(sd1.fields != null)
                assertTrue(sd2.fields != null)
                assertContentEquals(sd1.fields!!, sd2.fields!!)
            }
        }
    }
}
