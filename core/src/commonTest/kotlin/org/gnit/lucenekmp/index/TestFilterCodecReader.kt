package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class TestFilterCodecReader : LuceneTestCase() {

    @Test
    fun testDeclaredMethodsOverridden() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                val doc = Document()
                doc.add(StoredField("id", "1"))
                w.addDocument(doc)
                DirectoryReader.open(w).use { reader ->
                    val leafReader = reader.leaves()[0].reader() as CodecReader
                    val r = object : FilterCodecReader(leafReader) {
                        override val coreCacheHelper: IndexReader.CacheHelper?
                            get() = leafReader.coreCacheHelper

                        override val readerCacheHelper: IndexReader.CacheHelper?
                            get() = leafReader.readerCacheHelper
                    }

                    assertSame(leafReader.liveDocs, r.liveDocs)
                    assertSame(leafReader.fieldInfos, r.fieldInfos)
                    assertEquals(leafReader.numDocs(), r.numDocs())
                    assertEquals(leafReader.maxDoc(), r.maxDoc())
                    assertEquals(
                        leafReader.storedFields().document(0).get("id"),
                        r.storedFields().document(0).get("id")
                    )
                }
            }
        }
    }

    @Test
    fun testGetDelegate() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                w.addDocument(Document())
                DirectoryReader.open(w).use { reader ->
                    val leafReader = reader.leaves()[0].reader() as CodecReader
                    val r = FilterCodecReader.wrapLiveDocs(leafReader, null, 1)

                    assertSame(FilterCodecReader.unwrap(r), leafReader)
                    assertSame(r.delegate, leafReader)
                    assertNull(r.liveDocs)
                    assertEquals(1, r.numDocs())
                }
            }
        }
    }
}
