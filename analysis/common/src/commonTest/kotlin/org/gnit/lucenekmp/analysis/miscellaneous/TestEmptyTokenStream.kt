package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestEmptyTokenStream : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testConsume() {
        val ts: TokenStream = EmptyTokenStream()
        ts.reset()
        assertFalse(ts.incrementToken())
        ts.end()
        ts.close()
        // try again with reuse:
        ts.reset()
        assertFalse(ts.incrementToken())
        ts.end()
        ts.close()
    }

    @Test
    @Throws(IOException::class)
    fun testConsume2() {
        assertTokenStreamContents(EmptyTokenStream(), arrayOf())
    }

    @Test
    @Throws(IOException::class)
    fun testIndexWriter_LUCENE4656() {
        val directory: Directory = newDirectory()
        val writer = IndexWriter(directory, newIndexWriterConfig())

        val ts: TokenStream = EmptyTokenStream()
        assertFalse(ts.hasAttribute(TermToBytesRefAttribute::class))

        val doc = Document()
        doc.add(StringField("id", "0", Field.Store.YES))
        doc.add(TextField("description", ts))

        // this should not fail because we have no TermToBytesRefAttribute
        writer.addDocument(doc)

        assertEquals(1, writer.getDocStats().numDocs)

        writer.close()
        directory.close()
    }
}
