package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestKeywordAnalyzer : BaseTokenStreamTestCase() {
    private lateinit var directory: Directory
    private lateinit var reader: IndexReader
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        directory = newDirectory()
        analyzer = SimpleAnalyzer()
        val writer = IndexWriter(directory, IndexWriterConfig(analyzer))

        val doc = Document()
        doc.add(StringField("partnum", "Q36", Field.Store.YES))
        doc.add(TextField("description", "Illidium Space Modulator", Field.Store.YES))
        writer.addDocument(doc)

        writer.close()

        reader = DirectoryReader.open(directory)
    }

    @AfterTest
    fun tearDown() {
        IOUtils.close(analyzer, reader, directory)
    }

    /*
  public void testPerFieldAnalyzer() throws Exception {
    PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(new SimpleAnalyzer());
    analyzer.addAnalyzer("partnum", new KeywordAnalyzer());

    QueryParser queryParser = new QueryParser("description", analyzer);
    Query query = queryParser.parse("partnum:Q36 AND SPACE");

    ScoreDoc[] hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals("Q36 kept as-is",
              "+partnum:Q36 +space", query.toString("description"));
    assertEquals("doc found!", 1, hits.length);
  }
  */

    @Test
    fun testMutipleDocument() {
        val dir = newDirectory()
        val analyzer: Analyzer = KeywordAnalyzer()
        val writer = IndexWriter(dir, IndexWriterConfig(analyzer))
        var doc = Document()
        doc.add(TextField("partnum", "Q36", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(TextField("partnum", "Q37", Field.Store.YES))
        writer.addDocument(doc)
        writer.close()

        val reader: IndexReader = DirectoryReader.open(dir)
        var td: PostingsEnum? = MultiTerms.getTermPostingsEnum(reader, "partnum", BytesRef("Q36"))
        assertNotNull(td)
        assertTrue(td.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        td = MultiTerms.getTermPostingsEnum(reader, "partnum", BytesRef("Q37"))
        assertNotNull(td)
        assertTrue(td.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        IOUtils.close(reader, analyzer, dir)
    }

    // LUCENE-1441
    @Test
    fun testOffsets() {
        KeywordAnalyzer().use { analyzer ->
            analyzer.tokenStream("field", StringReader("abcd")).use { stream: TokenStream ->
                val offsetAtt = stream.addAttribute(OffsetAttribute::class)
                stream.reset()
                assertTrue(stream.incrementToken())
                assertEquals(0, offsetAtt.startOffset())
                assertEquals(4, offsetAtt.endOffset())
                assertFalse(stream.incrementToken())
                stream.end()
            }
        }
    }

    /** blast some random strings through the analyzer */
    @Test
    fun testRandomStrings() {
        val analyzer: Analyzer = KeywordAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}

