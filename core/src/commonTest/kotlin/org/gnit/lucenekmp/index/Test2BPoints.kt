package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.Test2BConstants
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// e.g. run like this: ant test -Dtestcase=Test2BPoints -Dtests.nightly=true -Dtests.verbose=true
// -Dtests.monster=true
//
//   or: python -u /l/util/src/python/repeatLuceneTest.py -heap 6g -once -nolog -tmpDir /b/tmp
// -logDir /l/logs Test2BPoints.test2D -verbose

//@SuppressCodecs("SimpleText", "Direct", "Compressing")
//@TimeoutSuite(millis = Integer.MAX_VALUE) // hopefully ~24 days is long enough ;)
//@Monster("takes at least 4 hours and consumes many GB of temp disk space")
class Test2BPoints : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun test1D() {
        val dir: Directory = FSDirectory.open(createTempDir("2BPoints1D"))

        val iwc = IndexWriterConfig(MockAnalyzer(random()))
            .setCodec(getCodec())
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(256.0)
            .setMergeScheduler(ConcurrentMergeScheduler())
            .setMergePolicy(newLogMergePolicy(false, 10))
            .setOpenMode(IndexWriterConfig.OpenMode.CREATE)

        (iwc.mergeScheduler as ConcurrentMergeScheduler).setMaxMergesAndThreads(6, 3)

        val w = IndexWriter(dir, iwc)

        val mp = w.config.mergePolicy
        if (mp is LogByteSizeMergePolicy) {
            // 1 petabyte:
            mp.maxMergeMB = 1024.0 * 1024 * 1024
        }

        val numDocs = (Test2BConstants.MAX_DOCS / 26) + 1
        var counter = 0
        for (i in 0..<numDocs) {
            val doc = Document()
            for (j in 0..<26) {
                val x = (random().nextInt().toLong() shl 32) or counter.toLong()
                doc.add(LongPoint("long", x))
                counter++
            }
            w.addDocument(doc)
            if (VERBOSE && i % 100000 == 0) {
                println("$i of $numDocs...")
            }
        }
        w.forceMerge(1)
        val r = DirectoryReader.open(w)
        val s = IndexSearcher(r)
        assertEquals(numDocs, s.count(LongPoint.newRangeQuery("long", Long.MIN_VALUE, Long.MAX_VALUE)))
        assertTrue(assertNotNull(r.leaves()[0].reader().getPointValues("long")).size() > 0)
        // TODO reduced pointCountThreshold = Int.MAX_VALUE to 0 for dev speed
        r.close()
        w.close()
        println("TEST: now CheckIndex")
        TestUtil.checkIndex(dir)
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun test2D() {
        val dir: Directory = FSDirectory.open(createTempDir("2BPoints2D"))

        val iwc = IndexWriterConfig(MockAnalyzer(random()))
            .setCodec(getCodec())
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(256.0)
            .setMergeScheduler(ConcurrentMergeScheduler())
            .setMergePolicy(newLogMergePolicy(false, 10))
            .setOpenMode(IndexWriterConfig.OpenMode.CREATE)

        (iwc.mergeScheduler as ConcurrentMergeScheduler).setMaxMergesAndThreads(6, 3)

        val w = IndexWriter(dir, iwc)

        val mp = w.config.mergePolicy
        if (mp is LogByteSizeMergePolicy) {
            // 1 petabyte:
            mp.maxMergeMB = 1024.0 * 1024 * 1024
        }

        val numDocs = (Test2BConstants.MAX_DOCS / 26) + 1
        var counter = 0
        for (i in 0..<numDocs) {
            val doc = Document()
            for (j in 0..<26) {
                val x = (random().nextInt().toLong() shl 32) or counter.toLong()
                val y = (random().nextInt().toLong() shl 32) or random().nextInt().toLong()
                doc.add(LongPoint("long", x, y))
                counter++
            }
            w.addDocument(doc)
            if (VERBOSE && i % 100000 == 0) {
                println("$i of $numDocs...")
            }
        }
        w.forceMerge(1)
        val r = DirectoryReader.open(w)
        val s = IndexSearcher(r)
        assertEquals(
            numDocs,
            s.count(
                LongPoint.newRangeQuery(
                    "long",
                    longArrayOf(Long.MIN_VALUE, Long.MIN_VALUE),
                    longArrayOf(Long.MAX_VALUE, Long.MAX_VALUE)
                )
            )
        )
        assertTrue(assertNotNull(r.leaves()[0].reader().getPointValues("long")).size() > 0)
        // TODO reduced pointCountThreshold = Int.MAX_VALUE to 0 for dev speed
        r.close()
        w.close()
        println("TEST: now CheckIndex")
        TestUtil.checkIndex(dir)
        dir.close()
    }

    companion object {
        private fun getCodec(): Codec {
            return Codec.default
        }
    }
}
