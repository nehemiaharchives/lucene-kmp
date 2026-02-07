package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterMergePolicy
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MergePolicy
import org.gnit.lucenekmp.index.MergePolicy.MergeContext
import org.gnit.lucenekmp.index.MergePolicy.MergeSpecification
import org.gnit.lucenekmp.index.MergePolicy.OneMerge
import org.gnit.lucenekmp.index.MergeScheduler
import org.gnit.lucenekmp.index.MergeTrigger
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.SegmentInfos
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.internal.tests.IndexWriterAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.toByteArray
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.Lock
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.NullInfoStream
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
//import kotlin.jvm.Synchronized
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Base test case for [MergePolicy].  */
abstract class BaseMergePolicyTestCase : LuceneTestCase() {
    /** Create a new [MergePolicy] instance.  */
    protected abstract fun mergePolicy(): MergePolicy

    /**
     * Assert that the given segment infos match expectations of the merge policy, assuming segments
     * that have only been either flushed or merged with this merge policy.
     */
    @Throws(IOException::class)
    protected abstract fun assertSegmentInfos(
        policy: MergePolicy,
        infos: SegmentInfos
    )

    /** Assert that the given merge matches expectations of the merge policy.  */
    @Throws(IOException::class)
    protected abstract fun assertMerge(
        policy: MergePolicy,
        merge: MergeSpecification
    )

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun testForceMergeNotNeeded() {
        newDirectory().use { dir ->
            val mayMerge = AtomicBoolean(true)
            val mergeScheduler: MergeScheduler =
                object : SerialMergeScheduler() {
                    /*@Synchronized*/
                    @Throws(IOException::class)
                    override suspend fun merge(mergeSource: MergeSource, trigger: MergeTrigger) {
                        if (mayMerge.load() == false) {
                            val merge: OneMerge? = mergeSource.nextMerge
                            if (merge != null) {
                                println("TEST: we should not need any merging, yet merge policy returned merge $merge")
                                throw AssertionError()
                            }
                        }

                        super.merge(mergeSource, trigger)
                    }
                }

            val mp: MergePolicy = mergePolicy()
            assumeFalse("this test cannot tolerate random forceMerges", mp.toString().contains("MockRandomMergePolicy"))
            mp.noCFSRatio = (if (random().nextBoolean()) 0 else 1).toDouble()

            val iwc: IndexWriterConfig = newIndexWriterConfig(MockAnalyzer(random()))
            iwc.setMergeScheduler(mergeScheduler)
            iwc.setMergePolicy(mp)

            val writer = IndexWriter(dir, iwc)
            val numSegments: Int = TestUtil.nextInt(random(), 2, 20)
            for (i in 0..<numSegments) {
                val numDocs: Int = TestUtil.nextInt(random(), 1, 5)
                for (j in 0..<numDocs) {
                    writer.addDocument(Document())
                }
                DirectoryReader.open(writer).close()
            }
            for (i in 5 downTo 0) {
                val segmentCount: Int = INDEX_WRITER_ACCESS.getSegmentCount(writer)
                val maxNumSegments = if (i == 0) 1 else TestUtil.nextInt(random(), 1, 10)
                mayMerge.store(segmentCount > maxNumSegments)
                if (VERBOSE) {
                    println(("TEST: now forceMerge(maxNumSegments=$maxNumSegments) vs segmentCount=$segmentCount"))
                }
                writer.forceMerge(maxNumSegments)
            }
            writer.close()
        }
    }

    @Throws(IOException::class)
    fun testFindForcedDeletesMerges() {
        val mp: MergePolicy = mergePolicy()
        if (mp is FilterMergePolicy) {
            assumeFalse("test doesn't work with MockRandomMP", mp.unwrap() is MockRandomMergePolicy)
        }
        val infos = SegmentInfos(Version.LATEST.major)
        newDirectory().use { directory ->
            val context: MergeContext = MockMergeContext { `_`: SegmentCommitInfo -> 0 }
            val numSegs: Int = random().nextInt(10)
            for (i in 0..<numSegs) {
                val info =
                    SegmentInfo(
                        directory,  // dir
                        Version.LATEST,  // version
                        Version.LATEST,  // min version
                        TestUtil.randomSimpleString(random()),  // name
                        random()
                            .nextInt(Int.MAX_VALUE),  // maxDoc
                        random()
                            .nextBoolean(),  // isCompoundFile
                        false,
                        null,  // codec
                        mutableMapOf(),  // diagnostics
                        TestUtil.randomSimpleString( // id
                            random(),
                            StringHelper.ID_LENGTH,
                            StringHelper.ID_LENGTH
                        )
                            .toByteArray(StandardCharsets.US_ASCII),
                        mutableMapOf(),  // attributes
                        null /* indexSort */
                    )
                info.setFiles(mutableListOf())
                infos.add(
                    SegmentCommitInfo(
                        info,
                        random().nextInt(1),
                        0,
                        -1,
                        -1,
                        -1,
                        StringHelper.randomId()
                    )
                )
            }
            val forcedDeletesMerges: MergeSpecification? = mp.findForcedDeletesMerges(infos, context)
            if (forcedDeletesMerges != null) {
                assertEquals(0, forcedDeletesMerges.merges.size.toLong())
            }
        }
    }

    /** Simple mock merge context for tests  */
    class MockMergeContext(private val numDeletesFunc: (SegmentCommitInfo) -> Int) : MergeContext {
        override val infoStream: InfoStream =
            object : NullInfoStream() {
                override fun isEnabled(component: String): Boolean {
                    // otherwise tests that simulate merging may bottleneck on generating messages
                    return false
                }
            }

        override var mergingSegments: MutableSet<SegmentCommitInfo> = mutableSetOf()

        override fun numDeletesToMerge(info: SegmentCommitInfo): Int {
            return numDeletesFunc(info)
        }

        override fun numDeletedDocs(info: SegmentCommitInfo): Int {
            return numDeletesToMerge(info)
        }

        /*override fun getMergingSegments(): MutableSet<SegmentCommitInfo> {
            return mergingSegments
        }*/

        /*fun setMergingSegments(mergingSegments: MutableSet<SegmentCommitInfo>) {
            this.mergingSegments = mergingSegments
        }*/
    }

    /** Simulate an append-only use-case, ie. there are no deletes.  */
    @Throws(IOException::class)
    fun testSimulateAppendOnly() {
        doTestSimulateAppendOnly(mergePolicy(), 100000000, 10000)
    }

    /**
     * Simulate an append-only use-case, ie. there are no deletes. `totalDocs` exist in the
     * index in the end, and flushes contribute at most `maxDocsPerFlush` documents.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    protected fun doTestSimulateAppendOnly(mergePolicy: MergePolicy, totalDocs: Int, maxDocsPerFlush: Int) {
        val stats = IOStats()
        val segNameGenerator = AtomicLong(0)
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos = SegmentInfos(Version.LATEST.major)
        val avgDocSizeMB = 5.0 / 1024 // 5kB
        var numDocs = 0
        while (numDocs < totalDocs) {
            val flushDocCount: Int = TestUtil.nextInt(random(), 1, maxDocsPerFlush)
            numDocs += flushDocCount
            val flushSizeMB = flushDocCount * avgDocSizeMB
            stats.flushBytesWritten = (stats.flushBytesWritten + flushSizeMB * 1024 * 1024).toLong()
            segmentInfos.add(
                makeSegmentCommitInfo("_" + segNameGenerator.fetchAndIncrement(), flushDocCount, 0, flushSizeMB, IndexWriter.SOURCE_FLUSH)
            )

            var merges: MergeSpecification? = mergePolicy.findFullFlushMerges(MergeTrigger.SEGMENT_FLUSH, segmentInfos, mergeContext)
            if (merges == null) {
                merges = mergePolicy.findMerges(MergeTrigger.SEGMENT_FLUSH, segmentInfos, mergeContext)
            }
            while (merges != null) {
                assertTrue(merges.merges.isNotEmpty())
                assertMerge(mergePolicy, merges)
                for (oneMerge in merges.merges) {
                    segmentInfos = applyMerge(segmentInfos, oneMerge, "_" + segNameGenerator.fetchAndIncrement(), stats)
                }
                merges = mergePolicy.findMerges(MergeTrigger.MERGE_FINISHED, segmentInfos, mergeContext)
            }
            assertSegmentInfos(mergePolicy, segmentInfos)
        }

        if (VERBOSE) {
            println("Write amplification for append-only: " + (stats.flushBytesWritten + stats.mergeBytesWritten).toDouble() / stats.flushBytesWritten)
        }
    }

    /** Simulate an update use-case where documents are uniformly updated across segments.  */
    @Throws(IOException::class)
    fun testSimulateUpdates() {
        val numDocs: Int = atLeast(1000000)
        doTestSimulateUpdates(mergePolicy(), numDocs, 2500)
    }

    /**
     * Simulate an update use-case where documents are uniformly updated across segments. `totalDocs` exist in the index in the end, and flushes contribute at most `maxDocsPerFlush` documents.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    protected fun doTestSimulateUpdates(mergePolicy: MergePolicy, totalDocs: Int, maxDocsPerFlush: Int) {
        val stats = IOStats()
        val segNameGenerator = AtomicLong(0)
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos = SegmentInfos(Version.LATEST.major)
        val avgDocSizeMB = 5.0 / 1024 // 5kB
        var numDocs = 0
        while (numDocs < totalDocs) {
            val flushDocCount: Int = if (usually()) {
                // reasonable value
                TestUtil.nextInt(random(), maxDocsPerFlush / 2, maxDocsPerFlush)
            } else {
                // crazy value
                TestUtil.nextInt(random(), 1, maxDocsPerFlush)
            }
            // how many of these documents are actually updates
            val delCount = (flushDocCount * 0.9 * numDocs / totalDocs).toInt()
            numDocs += flushDocCount - delCount
            segmentInfos = applyDeletes(segmentInfos, delCount)
            val flushSize = flushDocCount * avgDocSizeMB
            stats.flushBytesWritten = (stats.flushBytesWritten + flushSize * 1024 * 1024).toLong()
            segmentInfos.add(
                makeSegmentCommitInfo("_" + segNameGenerator.fetchAndIncrement(), flushDocCount, 0, flushSize, IndexWriter.SOURCE_FLUSH)
            )
            var merges: MergeSpecification? = mergePolicy.findFullFlushMerges(MergeTrigger.SEGMENT_FLUSH, segmentInfos, mergeContext)
            if (merges == null) {
                merges = mergePolicy.findMerges(MergeTrigger.SEGMENT_FLUSH, segmentInfos, mergeContext)
            }
            while (merges != null) {
                assertMerge(mergePolicy, merges)
                for (oneMerge in merges.merges) {
                    segmentInfos = applyMerge(segmentInfos, oneMerge, "_" + segNameGenerator.fetchAndIncrement(), stats)
                }
                merges = mergePolicy.findMerges(MergeTrigger.MERGE_FINISHED, segmentInfos, mergeContext)
            }
            assertSegmentInfos(mergePolicy, segmentInfos)
        }

        if (VERBOSE) {
            println("Write amplification for update: " + (stats.flushBytesWritten + stats.mergeBytesWritten).toDouble() / stats.flushBytesWritten)
            val totalDelCount: Int = segmentInfos.asList().sumOf { obj: SegmentCommitInfo -> obj.delCount }
            val totalMaxDoc: Int = segmentInfos.asList().map { s: SegmentCommitInfo -> s.info }.sumOf { obj: SegmentInfo -> obj.maxDoc() }
            println("Final live ratio: " + (1 - totalDelCount.toDouble() / totalMaxDoc))
        }
    }

    /** Statistics about bytes written to storage.  */
    class IOStats {
        /** Bytes written through flushes.  */
        var flushBytesWritten: Long = 0

        /** Bytes written through merges.  */
        var mergeBytesWritten: Long = 0
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun testNoPathologicalMerges() {
        val mergePolicy: MergePolicy = mergePolicy()
        val stats = IOStats()
        val segNameGenerator = AtomicLong(0)
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos = SegmentInfos(Version.LATEST.major)
        // Both the docs per flush and doc size are small because these are the typical cases that used
        // to trigger pathological O(n^2) merging due to floor segment sizes
        val avgDocSizeMB = 10.0 / 1024 / 1024
        val maxDocsPerFlush = 3
        val totalDocs = 10000
        var numFlushes = 0
        var numDocs = 0
        while (numDocs < totalDocs) {
            val flushDocCount: Int = TestUtil.nextInt(random(), 1, maxDocsPerFlush)
            numDocs += flushDocCount
            val flushSizeMB = flushDocCount * avgDocSizeMB
            stats.flushBytesWritten = (stats.flushBytesWritten + flushSizeMB * 1024 * 1024).toLong()
            segmentInfos.add(makeSegmentCommitInfo("_" + segNameGenerator.fetchAndIncrement(), flushDocCount, 0, flushSizeMB, IndexWriter.SOURCE_FLUSH))
            ++numFlushes

            var merges: MergeSpecification? = mergePolicy.findMerges(MergeTrigger.SEGMENT_FLUSH, segmentInfos, mergeContext)
            while (merges != null) {
                assertTrue(merges.merges.isNotEmpty())
                assertMerge(mergePolicy, merges)
                for (oneMerge in merges.merges) {
                    segmentInfos = applyMerge(segmentInfos, oneMerge, "_" + segNameGenerator.fetchAndIncrement(), stats)
                }
                merges = mergePolicy.findMerges(MergeTrigger.MERGE_FINISHED, segmentInfos, mergeContext)
            }
            assertSegmentInfos(mergePolicy, segmentInfos)
        }

        val writeAmplification = (stats.flushBytesWritten + stats.mergeBytesWritten).toDouble() / stats.flushBytesWritten
        // Assuming a merge factor of 2, which is the value that triggers the most write amplification,
        // the total write amplification would be ~ log(numFlushes)/log(2). We allow merge policies to
        // have a write amplification up to log(numFlushes)/log(1.5). Greater values would indicate a
        // problem with the merge policy.
        val maxAllowedWriteAmplification = ln(numFlushes.toDouble()) / ln(1.5)
        assertTrue(writeAmplification < maxAllowedWriteAmplification)
    }

    companion object {
        private val INDEX_WRITER_ACCESS: IndexWriterAccess =
            TestSecrets.getIndexWriterAccess()

        /**
         * Make a new [SegmentCommitInfo] with the given `maxDoc`, `numDeletedDocs` and
         * `sizeInBytes`, which are usually the numbers that merge policies care about.
         */
        protected fun makeSegmentCommitInfo(
            name: String, maxDoc: Int, numDeletedDocs: Int, sizeMB: Double, source: String
        ): SegmentCommitInfo {
            require(name.startsWith("_") != false) { "name must start with an _, got $name" }
            val id = ByteArray(StringHelper.ID_LENGTH)
            random().nextBytes(id)
            val info =
                SegmentInfo(
                    FAKE_DIRECTORY,
                    Version.LATEST,
                    Version.LATEST,
                    name,
                    maxDoc,
                    isCompoundFile = false,
                    hasBlocks = false,
                    codec = TestUtil.getDefaultCodec(),
                    diagnostics = mutableMapOf(),
                    id = id,
                    attributes = mutableMapOf(IndexWriter.SOURCE to source),
                    indexSort = null
                )
            info.setFiles(mutableSetOf(name + "_size=" + (sizeMB * 1024 * 1024).toLong().toString() + ".fake"))
            return SegmentCommitInfo(
                info,
                numDeletedDocs,
                0,
                0,
                0,
                0,
                StringHelper.randomId()
            )
        }

        /** A directory that computes the length of a file based on its name.  */
        private val FAKE_DIRECTORY: Directory =
            object : Directory() {
                @Throws(IOException::class)
                override fun listAll(): Array<String> {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun deleteFile(name: String) {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun fileLength(name: String): Long {
                    if (name.endsWith(".liv")) {
                        return 0L
                    }
                    require(name.endsWith(".fake") != false) { name }
                    val startIndex = name.indexOf("_size=") + "_size=".length
                    val endIndex = name.length - ".fake".length
                    return name.substring(startIndex, endIndex).toLong()
                }

                @Throws(IOException::class)
                override fun createOutput(name: String, context: IOContext): IndexOutput {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun sync(names: MutableCollection<String>) {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun rename(source: String, dest: String) {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun syncMetaData() {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun openInput(name: String, context: IOContext): IndexInput {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun obtainLock(name: String): Lock {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun close() {
                    throw UnsupportedOperationException()
                }

                @get:Throws(IOException::class)
                override val pendingDeletions: MutableSet<String>
                    get() {
                        throw UnsupportedOperationException()
                    }
            }

        /**
         * Apply a merge to a [SegmentInfos] instance, accumulating the number of written bytes into
         * `stats`.
         */
        @Throws(IOException::class)
        protected fun applyMerge(infos: SegmentInfos, merge: OneMerge, mergedSegmentName: String, stats: IOStats): SegmentInfos {
            var newMaxDoc = 0
            var newSize = 0.0
            for (sci in merge.segments) {
                val numLiveDocs: Int = sci.info.maxDoc() - sci.delCount
                newSize += sci.sizeInBytes().toDouble() * numLiveDocs / sci.info.maxDoc() / 1024 / 1024
                newMaxDoc += numLiveDocs
            }
            val mergedInfo = makeSegmentCommitInfo(mergedSegmentName, newMaxDoc, 0, newSize, IndexWriter.SOURCE_MERGE)

            val mergedAway: MutableSet<SegmentCommitInfo> = HashSet(merge.segments)
            var mergedSegmentAdded = false
            val newInfos =
                SegmentInfos(Version.LATEST.major)
            for (i in 0..<infos.size()) {
                val info = infos.info(i)
                if (mergedAway.contains(info)) {
                    if (mergedSegmentAdded == false) {
                        newInfos.add(mergedInfo)
                        mergedSegmentAdded = true
                    }
                } else {
                    newInfos.add(info)
                }
            }
            stats.mergeBytesWritten = (stats.mergeBytesWritten + newSize * 1024 * 1024).toLong()
            return newInfos
        }

        /** Apply `numDeletes` uniformly across all segments of `infos`.  */
        protected fun applyDeletes(infos: SegmentInfos, numDeletes: Int): SegmentInfos {
            var numDeletes = numDeletes
            val infoList: MutableList<SegmentCommitInfo> = infos.asList()
            val totalNumDocs: Int = infoList.sumOf { s: SegmentCommitInfo -> s.info.maxDoc() - s.delCount }
            require(numDeletes <= totalNumDocs) { "More deletes than documents" }
            val w = numDeletes.toDouble() / totalNumDocs
            val newInfoList: MutableList<SegmentCommitInfo> = mutableListOf()
            for (i in infoList.indices) {
                assert(numDeletes >= 0)
                val sci = infoList[i]
                val segDeletes: Int = if (i == infoList.size - 1) {
                    numDeletes
                } else {
                    min(numDeletes, ceil(w * (sci.info.maxDoc() - sci.delCount)).toInt())
                }
                val newDelCount: Int = sci.delCount + segDeletes
                assert(newDelCount <= sci.info.maxDoc())
                if (newDelCount < sci.info.maxDoc()) { // drop fully deleted segments
                    val newInfo =
                        SegmentCommitInfo(
                            sci.info,
                            sci.delCount + segDeletes,
                            0,
                            sci.delGen + 1,
                            sci.fieldInfosGen,
                            sci.docValuesGen,
                            StringHelper.randomId()
                        )
                    newInfoList.add(newInfo)
                }
                numDeletes -= segDeletes
            }
            assert(numDeletes == 0)
            val newInfos = SegmentInfos(Version.LATEST.major)
            newInfos.addAll(newInfoList)
            return newInfos
        }
    }
}
