package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.Test2BConstants
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

//@SuppressCodecs("SimpleText", "Direct")
//@TimeoutSuite(millis = 80 * TimeUnits.HOUR) // effectively no limit
// The six hour time was achieved on a Linux 3.13 system with these specs:
// 3-core AMD at 2.5Ghz, 12 GB RAM, 5GB test heap, 2 test JVMs, 2TB SATA.
//@Monster("takes ~ 6 hours if the heap is 5gb")
//@SuppressSysoutChecks(bugUrl = "Stuff gets printed.")
class Test2BBinaryDocValues : LuceneTestCase() {

    // indexes IndexWriter.MAX_DOCS docs with a fixed binary field
    @Test
    @Throws(Exception::class)
    fun testFixedBinary() {
        val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("2BFixedBinary"))
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
        val bytes = ByteArray(4)
        val data = BytesRef(bytes)
        val dvField = BinaryDocValuesField("dv", data)
        doc.add(dvField)

        for (i in 0..<Test2BConstants.MAX_DOCS) {
            bytes[0] = (i ushr 24).toByte()
            bytes[1] = (i ushr 16).toByte()
            bytes[2] = (i ushr 8).toByte()
            bytes[3] = i.toByte()
            w.addDocument(doc)
            if (i % 100000 == 0) {
                println("indexed: $i")
            }
        }

        w.forceMerge(1)
        w.close()

        println("verifying...")

        val r = DirectoryReader.open(dir)
        var expectedValue = 0
        for (context in r.leaves()) {
            val reader = context.reader()
            val dv = assertNotNull(reader.getBinaryDocValues("dv"))
            for (i in 0..<reader.maxDoc()) {
                bytes[0] = (expectedValue ushr 24).toByte()
                bytes[1] = (expectedValue ushr 16).toByte()
                bytes[2] = (expectedValue ushr 8).toByte()
                bytes[3] = expectedValue.toByte()
                assertEquals(i, dv.nextDoc())
                val term = assertNotNull(dv.binaryValue())
                assertEquals(data, term)
                expectedValue++
            }
        }

        r.close()
        dir.close()
    }

    // indexes IndexWriter.MAX_DOCS docs with a variable binary field
    @Test
    @Throws(Exception::class)
    fun testVariableBinary() {
        val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("2BVariableBinary"))
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
        val bytes = ByteArray(4)
        val encoder = ByteArrayDataOutput(bytes)
        val data = BytesRef(bytes)
        val dvField = BinaryDocValuesField("dv", data)
        doc.add(dvField)

        for (i in 0..<Test2BConstants.MAX_DOCS) {
            encoder.reset(bytes)
            encoder.writeVInt(i % 65535) // 1, 2, or 3 bytes
            data.length = encoder.position
            w.addDocument(doc)
            if (i % 100000 == 0) {
                println("indexed: $i")
            }
        }

        w.forceMerge(1)
        w.close()

        println("verifying...")

        val r = DirectoryReader.open(dir)
        var expectedValue = 0
        val input = ByteArrayDataInput()
        for (context in r.leaves()) {
            val reader = context.reader()
            val dv = assertNotNull(reader.getBinaryDocValues("dv"))
            for (i in 0..<reader.maxDoc()) {
                assertEquals(i, dv.nextDoc())
                val term = assertNotNull(dv.binaryValue())
                input.reset(term.bytes, term.offset, term.length)
                assertEquals(expectedValue % 65535, input.readVInt())
                assertTrue(input.eof())
                expectedValue++
            }
        }

        r.close()
        dir.close()
    }
}
