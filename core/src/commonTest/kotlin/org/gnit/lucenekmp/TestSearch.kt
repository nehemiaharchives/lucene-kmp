package org.gnit.lucenekmp

import okio.Path.Companion.toPath
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MergePolicy
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.StringWriter
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals


/** JUnit adaptation of an older test case SearchTest.  */
class TestSearch : LuceneTestCase() {
    /**
     * This test performs a number of searches. It also compares output of searches using multi-file
     * index segments with single-file index segments.
     *
     *
     * TODO: someone should check that the results of the searches are still correct by adding
     * assert statements. Right now, the test passes if the results are the same between multi-file
     * and single-file formats, even if the results are wrong.
     */
    @Test
    fun testSearch() {
        var sw = StringWriter()
        //var pw: PrintWriter = PrintWriter(sw, true)
        doTestSearch(random(), /*pw,*/ false)
        //pw.close()
        sw.close()
        val multiFileOutput: String = sw.toString()

        // System.out.println(multiFileOutput);
        sw = StringWriter()
        //pw = PrintWriter(sw, true)
        doTestSearch(random(), /*pw,*/ true)
        //pw.close()
        sw.close()
        val singleFileOutput: String = sw.toString()

        assertEquals(multiFileOutput, singleFileOutput)
    }

    private fun doTestSearch(random: Random, /*out: PrintWriter, */useCompoundFile: Boolean) {
        val randomDirNumber = random.nextInt(1000)
        val path = "/tmp/$randomDirNumber/".toPath()
        Files.createFile(path)
        val directory: Directory = /*newDirectory()*/ FSDirectory.open(path = path)
        val analyzer: Analyzer = MockAnalyzer(random)
        val conf = /*newIndexWriterConfig(analyzer)*/ IndexWriterConfig(analyzer)
        val mp: MergePolicy = conf.mergePolicy
        mp.noCFSRatio = if (useCompoundFile) 1.0 else 0.0
        val writer = IndexWriter(directory, conf)

        val docs = arrayOf<String?>(
            "a b c d e",
            "a b c d e a b c d e",
            "a b c d e f g h i j",
            "a c e",
            "e c a",
            "a c e a c e",
            "a c e a b c"
        )
        for (j in docs.indices) {
            val d = Document()
            d.add(/*newTextField("contents", docs[j], Field.Store.YES)*/ TextField("contents", docs[j]!!, Field.Store.YES))
            d.add(NumericDocValuesField("id", j.toLong()))
            writer.addDocument(d)
        }
        writer.close()

        val reader: IndexReader = DirectoryReader.open(directory)
        val searcher = /*newSearcher(reader)*/ IndexSearcher(reader)

        var hits: Array<ScoreDoc>? = null

        val sort = Sort(SortField.FIELD_SCORE, SortField("id", SortField.Type.INT))

        for (query in buildQueries()) {
            /*out.*/println("Query: " + query.toString("contents"))
            if (VERBOSE) {
                println("TEST: query=$query")
            }

            hits = searcher.search(query, 1000, sort).scoreDocs!!

            /*out.*/println(hits.size.toString() + " total results")
            val storedFields: StoredFields = searcher.storedFields()
            var i = 0
            while (i < hits.size && i < 10) {
                val d: Document = storedFields.document(hits[i].doc)
                /*out.*/println(i.toString() + " " + hits[i].score + " " + d.get("contents"))
                i++
            }
        }
        reader.close()
        directory.close()
    }

    private fun buildQueries(): MutableList<Query> {
        val queries: MutableList<Query> = mutableListOf()

        val booleanAB: BooleanQuery.Builder = BooleanQuery.Builder()
        booleanAB.add(TermQuery(Term("contents", "a")), BooleanClause.Occur.SHOULD)
        booleanAB.add(TermQuery(Term("contents", "b")), BooleanClause.Occur.SHOULD)
        queries.add(booleanAB.build())

        val phraseAB = PhraseQuery("contents", "a", "b")
        queries.add(phraseAB)

        val phraseABC = PhraseQuery("contents", "a", "b", "c")
        queries.add(phraseABC)

        val booleanAC: BooleanQuery.Builder = BooleanQuery.Builder()
        booleanAC.add(TermQuery(Term("contents", "a")), BooleanClause.Occur.SHOULD)
        booleanAC.add(TermQuery(Term("contents", "c")), BooleanClause.Occur.SHOULD)
        queries.add(booleanAC.build())

        val phraseAC = PhraseQuery("contents", "a", "c")
        queries.add(phraseAC)

        val phraseACE = PhraseQuery("contents", "a", "c", "e")
        queries.add(phraseACE)

        return queries
    }
}
