package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.Test2BConstants
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

//@SuppressCodecs("SimpleText", "Direct")
//@TimeoutSuite(millis = 8 * TimeUnits.HOUR)
// The two hour time was achieved on a Linux 3.13 system with these specs:
// 3-core AMD at 2.5Ghz, 12 GB RAM, 5GB test heap, 2 test JVMs, 2TB SATA.
//@Monster("takes ~ 2 hours if the heap is 5gb")
//@SuppressSysoutChecks(bugUrl = "Stuff gets printed")
class Test2BNumericDocValues : LuceneTestCase() {

    // indexes IndexWriter.MAX_DOCS docs with an increasing dv field
    @Test
    @Throws(Exception::class)
    fun testNumerics() {
        val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("2BNumerics"))
        if (dir is MockDirectoryWrapper) {
            dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }

        val w = IndexWriter(
            dir,
            IndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
                .setRAMBufferSizeMB(256.0)
                .setMergeScheduler(ConcurrentMergeScheduler())
                .setMergePolicy(newLogMergePolicy(false, 10))
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                .setCodec(TestUtil.getDefaultCodec())
        )

        val doc = Document()
        val dvField = NumericDocValuesField("dv", 0)
        doc.add(dvField)

        for (i in 0..<Test2BConstants.MAX_DOCS) {
            dvField.setLongValue(i.toLong())
            w.addDocument(doc)
            if (i % 100000 == 0) {
                println("indexed: $i")
            }
        }

        w.forceMerge(1)
        w.close()

        println("verifying...")

        val r = DirectoryReader.open(dir)
        var expectedValue = 0L
        for (context in r.leaves()) {
            val reader = context.reader()
            val dv = assertNotNull(reader.getNumericDocValues("dv"))
            for (i in 0..<reader.maxDoc()) {
                assertEquals(i, dv.nextDoc())
                assertEquals(expectedValue, dv.longValue())
                expectedValue++
            }
        }

        r.close()
        dir.close()
    }
}
