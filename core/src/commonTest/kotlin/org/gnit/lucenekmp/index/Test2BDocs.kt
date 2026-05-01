package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.util.getLogger
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.Test2BConstants
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

//@SuppressCodecs("SimpleText", "Direct")
//@TimeoutSuite(millis = 80 * TimeUnits.HOUR) // effectively no limit
//@Monster("Takes ~30min")
//@SuppressSysoutChecks(bugUrl = "Stuff gets printed")
class Test2BDocs : LuceneTestCase() {

    init {
        configureTestLogging()
    }

    private val logger = getLogger()

    // indexes Integer.MAX_VALUE docs with indexed field(s)
    @Test
    @Throws(Exception::class)
    fun test2BDocs() {
        val totalStart = TimeSource.Monotonic.markNow()

        val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("2BDocs"))
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
        val field: Field = Field("f1", "a", StringField.TYPE_NOT_STORED)
        doc.add(field)

        val indexStart = TimeSource.Monotonic.markNow()
        for (i in 0..<Test2BConstants.MAX_DOCS) {
            w.addDocument(doc)
            if (i % (10 * 1000 * 1000) == 0) {
                logger.debug { "phase=index progressDoc=$i" }
            }
        }
        logger.debug { "phase=index elapsedMs=${indexStart.elapsedNow().inWholeMilliseconds}" }

        val mergeCloseStart = TimeSource.Monotonic.markNow()
        w.forceMerge(1)
        w.close()
        logger.debug { "phase=merge_close elapsedMs=${mergeCloseStart.elapsedNow().inWholeMilliseconds}" }

        logger.debug { "phase=verify start=true" }

        val r = DirectoryReader.open(dir)

        val term = BytesRef(1)
        term.bytes[0] = 'a'.code.toByte()
        term.length = 1

        var skips = 0L

        val rnd = random()

        val verifyStart = TimeSource.Monotonic.markNow()
        val start = System.nanoTime()
        var nsTermsIterator = 0L
        var nsSeekExact = 0L
        var nsPostings = 0L
        var nsTargetCalc = 0L
        var nsAdvance = 0L
        var nsPostAdvance = 0L
        var advanceCalls = 0L

        for (context in r.leaves()) {
            val reader = context.reader()
            val lim = context.reader().maxDoc()
            var docsReuse: PostingsEnum? = null

            val terms = assertNotNull(reader.terms("f1"))
            for (i in 0..<10000) {
                val termsIteratorStartNs = System.nanoTime()
                val te = terms.iterator()
                nsTermsIterator += System.nanoTime() - termsIteratorStartNs
                val seekStartNs = System.nanoTime()
                assertTrue(te.seekExact(term))
                nsSeekExact += System.nanoTime() - seekStartNs
                val postingsStartNs = System.nanoTime()
                docsReuse = assertNotNull(te.postings(docsReuse))
                val docs = docsReuse
                nsPostings += System.nanoTime() - postingsStartNs

                // skip randomly through the term
                var target = -1
                while (true) {
                    val targetCalcStartNs = System.nanoTime()
                    var maxSkipSize = lim - target + 1
                    // do a smaller skip half of the time
                    if (rnd.nextBoolean()) {
                        maxSkipSize = minOf(256, maxSkipSize)
                    }
                    var newTarget = target + rnd.nextInt(maxSkipSize) + 1
                    if (newTarget >= lim) {
                        if (target + 1 >= lim) {
                            break // we already skipped to end, so break.
                        }
                        newTarget = lim - 1 // skip to end
                    }
                    target = newTarget
                    nsTargetCalc += System.nanoTime() - targetCalcStartNs

                    val advanceStartNs = System.nanoTime()
                    val res = docs.advance(target)
                    nsAdvance += System.nanoTime() - advanceStartNs
                    advanceCalls++
                    if (res == DocIdSetIterator.NO_MORE_DOCS) {
                        break
                    }

                    val postAdvanceStartNs = System.nanoTime()
                    assertTrue(res >= target)

                    skips++
                    target = res
                    nsPostAdvance += System.nanoTime() - postAdvanceStartNs
                }
            }
        }

        r.close()
        dir.close()

        val end = System.nanoTime()

        logger.debug { "phase=verify_total elapsedMs=${verifyStart.elapsedNow().inWholeMilliseconds}" }
        logger.debug { "phase=total elapsedMs=${totalStart.elapsedNow().inWholeMilliseconds}" }
        logger.debug { "phase=verify_summary skipCount=$skips seconds=${TimeUnit.NANOSECONDS.toSeconds(end - start)}" }
        logger.debug { "substep=terms_iterator elapsedMs=${TimeUnit.NANOSECONDS.toMillis(nsTermsIterator)} elapsedNs=$nsTermsIterator" }
        logger.debug { "substep=seek_exact elapsedMs=${TimeUnit.NANOSECONDS.toMillis(nsSeekExact)} elapsedNs=$nsSeekExact" }
        logger.debug { "substep=postings_setup elapsedMs=${TimeUnit.NANOSECONDS.toMillis(nsPostings)} elapsedNs=$nsPostings" }
        logger.debug { "substep=target_calc elapsedMs=${TimeUnit.NANOSECONDS.toMillis(nsTargetCalc)} elapsedNs=$nsTargetCalc" }
        logger.debug { "substep=advance elapsedMs=${TimeUnit.NANOSECONDS.toMillis(nsAdvance)} elapsedNs=$nsAdvance calls=$advanceCalls" }
        logger.debug { "substep=post_advance elapsedMs=${TimeUnit.NANOSECONDS.toMillis(nsPostAdvance)} elapsedNs=$nsPostAdvance" }
        assertTrue(skips > 0)
    }
}
