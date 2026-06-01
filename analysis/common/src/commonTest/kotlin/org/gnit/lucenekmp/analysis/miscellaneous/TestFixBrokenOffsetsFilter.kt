package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test

@Deprecated("")
class TestFixBrokenOffsetsFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testBogusTermVectors() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorOffsets(true)
        val field = Field(
            "foo",
            FixBrokenOffsetsFilter(
                CannedTokenStream(Token("bar", 5, 10), Token("bar", 1, 4))
            ),
            ft
        )
        doc.add(field)
        iw.addDocument(doc)
        iw.close()
        dir.close()
    }
}
