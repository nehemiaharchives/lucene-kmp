package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test date sorting, i.e. auto-sorting of fields with type "long". See
 * http://issues.apache.org/jira/browse/LUCENE-1045
 */
class TestDateSort : LuceneTestCase() {
    private var directory: Directory? = null
    private var reader: IndexReader? = null

    @BeforeTest
    @Throws(Exception::class)
    fun setUpTestDateSort() {
        // Create an index writer.
        directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory!!)

        // oldest doc:
        // Add the first document.  text = "Document 1"  dateTime = Oct 10 03:25:22 EDT 2007
        writer.addDocument(createDocument("Document 1", 1192001122000L))
        // Add the second document.  text = "Document 2"  dateTime = Oct 10 03:25:26 EDT 2007
        writer.addDocument(createDocument("Document 2", 1192001126000L))
        // Add the third document.  text = "Document 3"  dateTime = Oct 11 07:12:13 EDT 2007
        writer.addDocument(createDocument("Document 3", 1192101133000L))
        // Add the fourth document.  text = "Document 4"  dateTime = Oct 11 08:02:09 EDT 2007
        writer.addDocument(createDocument("Document 4", 1192104129000L))
        // latest doc:
        // Add the fifth document.  text = "Document 5"  dateTime = Oct 12 13:25:43 EDT 2007
        writer.addDocument(createDocument("Document 5", 1192209943000L))

        reader = writer.reader
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDownTestDateSort() {
        reader!!.close()
        directory!!.close()
    }

    @Test
    @Throws(Exception::class)
    fun testReverseDateSort() {
        val searcher = newSearcher(reader!!)

        val sort = Sort(SortField(DATE_TIME_FIELD, SortField.Type.STRING, true))
        val query: Query = TermQuery(Term(TEXT_FIELD, "document"))

        // Execute the search and process the search results.
        val actualOrder = arrayOfNulls<String>(5)
        val hits = searcher.search(query, 1000, sort).scoreDocs
        val storedFields = searcher.storedFields()
        for (i in hits.indices) {
            val document = storedFields.document(hits[i].doc)
            val text = document.get(TEXT_FIELD)
            actualOrder[i] = text
        }

        // Set up the expected order (i.e. Document 5, 4, 3, 2, 1).
        val expectedOrder = arrayOfNulls<String>(5)
        expectedOrder[0] = "Document 5"
        expectedOrder[1] = "Document 4"
        expectedOrder[2] = "Document 3"
        expectedOrder[3] = "Document 2"
        expectedOrder[4] = "Document 1"

        assertEquals(expectedOrder.toList(), actualOrder.toList())
    }

    private fun createDocument(text: String, time: Long): Document {
        val document = Document()

        // Add the text field.
        val textField = newTextField(TEXT_FIELD, text, Field.Store.YES)
        document.add(textField)

        // Add the date/time field.
        val dateTimeString = DateTools.timeToString(time, DateTools.Resolution.SECOND)
        val dateTimeField = newStringField(DATE_TIME_FIELD, dateTimeString, Field.Store.YES)
        document.add(dateTimeField)
        document.add(SortedDocValuesField(DATE_TIME_FIELD, newBytesRef(dateTimeString)))

        return document
    }

    companion object {
        private const val TEXT_FIELD = "text"
        private const val DATE_TIME_FIELD = "dateTime"
    }
}
