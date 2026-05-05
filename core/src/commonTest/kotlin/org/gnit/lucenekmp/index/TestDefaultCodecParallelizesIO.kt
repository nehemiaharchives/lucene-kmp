package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.store.SerialIOCountingDirectory
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOBooleanSupplier
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TestDefaultCodecParallelizesIO : LuceneTestCase() {
    private lateinit var dir: SerialIOCountingDirectory
    private lateinit var reader: IndexReader

    @BeforeTest
    @Throws(Exception::class)
    fun beforeClass() {
        val bbDir: Directory = ByteBuffersDirectory()
        val startTime = System.currentTimeMillis()
        LineFileDocs(random()).use { docs ->
            IndexWriter(
                bbDir,
                IndexWriterConfig()
                    // Disable CFS, this test needs to know about files that are open with the
                    // RANDOM_PRELOAD advice, which CFS doesn't allow us to detect.
                    .setUseCompoundFile(false)
                    .setMergePolicy(newLogMergePolicy(false))
                    .setCodec(TestUtil.getDefaultCodec())
            ).use { w ->
                val numDocs = atLeast(10_000)
                repeat(numDocs) {
                    val doc: Document = docs.nextDoc()
                    w.addDocument(doc)
                }
                w.forceMerge(1)
            }
        }
        val elapsedMs = System.currentTimeMillis() - startTime
        println("=== Indexing took $elapsedMs ms for ~${atLeast(10_000)} docs ===")
        dir = SerialIOCountingDirectory(bbDir)
        reader = DirectoryReader.open(dir)
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        IOUtils.close(reader, dir)
    }

    /** Simulate term lookup in a BooleanQuery. */
    @Test
    @Throws(IOException::class)
    fun testTermsSeekExact() {
        val prevCount = dir.count()

        val terms: Terms = getOnlyLeafReader(reader).terms("body")!!
        val termValues = arrayOf("a", "which", "the", "for", "he")
        val suppliers = arrayOfNulls<IOBooleanSupplier>(termValues.size)
        for (i in termValues.indices) {
            val te = terms.iterator()
            suppliers[i] = te.prepareSeekExact(BytesRef(termValues[i]))
        }
        val afterPrepareCount = dir.count()
        var nonNullIOSuppliers = 0
        for (supplier in suppliers) {
            if (supplier != null) {
                nonNullIOSuppliers++
                supplier.get()
            }
        }

        assertTrue(nonNullIOSuppliers > 0, "expected at least one term supplier from LineFileDocs fallback corpus")
        val newCount = dir.count()
        val prepareDelta = afterPrepareCount - prevCount
        val readDelta = newCount - afterPrepareCount
        val delta = newCount - prevCount
        assertTrue(delta > 0, "expected positive serial I/O count delta but was $delta")
        assertTrue(
            delta < nonNullIOSuppliers,
            "expected seekExact prefetch to reduce serial I/O count: delta=$delta prepareDelta=$prepareDelta readDelta=$readDelta suppliers=$nonNullIOSuppliers"
        )
    }

    /** Simulate stored fields retrieval. */
    @Test
    @Throws(IOException::class)
    fun testStoredFields() {
        val prevCount = dir.count()

        val leafReader = getOnlyLeafReader(reader)
        val storedFields = leafReader.storedFields()
        val docs = IntArray(20)
        for (i in docs.indices) {
            docs[i] = random().nextInt(leafReader.maxDoc())
            storedFields.prefetch(docs[i])
        }
        for (doc in docs) {
            storedFields.document(doc)
        }

        val newCount = dir.count()
        assertTrue(newCount - prevCount > 0)
        assertTrue(newCount - prevCount < docs.size)
    }
}
