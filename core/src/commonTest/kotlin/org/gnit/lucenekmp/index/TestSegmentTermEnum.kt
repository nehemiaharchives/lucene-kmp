package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestSegmentTermEnum : LuceneTestCase() {

    lateinit var dir: Directory

    @BeforeTest
    fun setUp() {
        dir = newDirectory()
    }

    @AfterTest
    fun tearDown() {
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testTermEnum() {
        var writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        // ADD 100 documents with term : aaa
        // add 100 documents with terms: aaa bbb
        // Therefore, term 'aaa' has document frequency of 200 and term 'bbb' 100
        for (i in 0..99) {
            addDoc(writer, "aaa")
            addDoc(writer, "aaa bbb")
        }

        writer.close()

        // verify document frequency of terms in an multi segment index
        verifyDocFreq()

        // merge segments
        writer =
            IndexWriter(
                dir, newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(IndexWriterConfig.OpenMode.APPEND)
            )
        writer.forceMerge(1)
        writer.close()

        // verify document frequency of terms in a single segment index
        verifyDocFreq()
    }

    @Test
    @Throws(IOException::class)
    fun testPrevTermAtEnd() {
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setCodec(TestUtil.alwaysPostingsFormat(TestUtil.getDefaultPostingsFormat()))
            )
        addDoc(writer, "aaa bbb")
        writer.close()
        val reader: LeafReader = getOnlyLeafReader(DirectoryReader.open(dir))
        val terms: TermsEnum = reader.terms("content")!!.iterator()
        assertNotNull(terms.next())
        assertEquals("aaa", terms.term()!!.utf8ToString())
        assertNotNull(terms.next())
        val ordB: Long
        try {
            ordB = terms.ord()
        } catch (uoe: UnsupportedOperationException) {
            // ok -- codec is not required to support ord
            reader.close()
            return
        }
        assertEquals("bbb", terms.term()!!.utf8ToString())
        assertNull(terms.next())

        terms.seekExact(ordB)
        assertEquals("bbb", terms.term()!!.utf8ToString())
        reader.close()
    }

    @Throws(IOException::class)
    private fun verifyDocFreq() {
        val reader: IndexReader = DirectoryReader.open(dir)
        val termEnum: TermsEnum = MultiTerms.getTerms(reader, "content")!!.iterator()

        // create enumeration of all terms
        // go to the first term (aaa)
        termEnum.next()
        // assert that term is 'aaa'
        assertEquals("aaa", termEnum.term()!!.utf8ToString())
        assertEquals(200, termEnum.docFreq().toLong())
        // go to the second term (bbb)
        termEnum.next()
        // assert that term is 'bbb'
        assertEquals("bbb", termEnum.term()!!.utf8ToString())
        assertEquals(100, termEnum.docFreq().toLong())

        // create enumeration of terms after term 'aaa',
        // including 'aaa'
        termEnum.seekCeil(BytesRef("aaa"))
        // assert that term is 'aaa'
        assertEquals("aaa", termEnum.term()!!.utf8ToString())
        assertEquals(200, termEnum.docFreq().toLong())
        // go to term 'bbb'
        termEnum.next()
        // assert that term is 'bbb'
        assertEquals("bbb", termEnum.term()!!.utf8ToString())
        assertEquals(100, termEnum.docFreq().toLong())
        reader.close()
    }

    @Throws(IOException::class)
    private fun addDoc(writer: IndexWriter, value: String) {
        val doc = Document()
        doc.add(newTextField("content", value, Field.Store.NO))
        writer.addDocument(doc)
    }

}