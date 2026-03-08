package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import okio.IOException
import okio.Path
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.search.FieldDoc
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper.Throttling
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.Version
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.ReentrantLock
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// TODO:
//   - old parallel indices are only pruned on commit/close; can we do it on refresh?

/**
 * Simple example showing how to use ParallelLeafReader to index new stuff (postings, DVs, etc.)
 * from previously stored fields, on the fly (during NRT reader reopen), after the initial
 * indexing. The test indexes just a single stored field with text "content X" (X is a number
 * embedded in the text).
 *
 * Then, on reopen, for any newly created segments (flush or merge), it builds a new parallel
 * segment by loading all stored docs, parsing out that X, and adding it as DV and numeric indexed
 * (trie) field.
 *
 * Finally, for searching, it builds a top-level MultiReader, with ParallelLeafReader for each
 * segment, and then tests that random numeric range queries, and sorting by the new DV field, work
 * correctly.
 *
 * Each per-segment index lives in a private directory next to the main index, and they are deleted
 * once their segments are removed from the index. They are "volatile", meaning if e.g. the index
 * is replicated to another machine, it's OK to not copy parallel segments indices, since they will
 * just be regenerated (at a cost though).
 */
class TestDemoParallelLeafReader : LuceneTestCase() {
    companion object {
        const val DEBUG = false
        const val SCHEMA_GEN_KEY = "schema_gen"

        fun getSchemaGen(info: SegmentInfo): Long {
            val s = info.diagnostics[SCHEMA_GEN_KEY]
            return if (s == null) {
                -1
            } else {
                s.toLong()
            }
        }

        private data class SegmentIDAndGen(val segID: String, val schemaGen: Long) {
            constructor(s: String) : this(
                s.substring(0, s.lastIndexOf('_')).also {
                    require(s.lastIndexOf('_') > 0) { "invalid SegmentIDAndGen \"$s\"" }
                },
                s.substring(s.lastIndexOf('_') + 1).toLong(),
            )

            override fun toString(): String {
                return "${segID}_${schemaGen}"
            }
        }

        // TODO: maybe the leading id could be further restricted?  It's from StringHelper.idToString:
        val SEG_GEN_SUB_DIR_PATTERN = Regex("^.+_([0-9]+)$")

        private fun segSubDirs(segsPath: Path): List<Path> {
            val fs = Files.getFileSystem()
            if (!fs.exists(segsPath)) {
                return emptyList()
            }

            val result = ArrayList<Path>()
            for (path in fs.list(segsPath)) {
                // Must be form <segIDString>_<longGen>
                if (fs.metadata(path).isDirectory &&
                    SEG_GEN_SUB_DIR_PATTERN.matches(path.toString().substringAfterLast('/'))
                ) {
                    result.add(path)
                }
            }

            return result
        }

        private fun checkAllNumberDVs(r: IndexReader) {
            checkAllNumberDVs(r, "number", true, 1)
        }

        private fun checkAllNumberDVs(
            r: IndexReader,
            fieldName: String,
            doThrow: Boolean,
            multiplier: Int,
        ) {
            val numbers = MultiDocValues.getNumericValues(r, fieldName)!!
            val maxDoc = r.maxDoc()
            val storedFields = r.storedFields()
            var failed = false
            for (i in 0..<maxDoc) {
                val oldDoc = storedFields.document(i)
                val value = multiplier.toLong() * oldDoc.get("text")!!.split(" ")[1].toLong()
                assertEquals(i, numbers.nextDoc())
                if (value != numbers.longValue()) {
                    println(
                        "FAIL: docID=$i $oldDoc value=$value number=${numbers.longValue()} numbers=$numbers"
                    )
                    failed = true
                } else if (failed) {
                    println("OK: docID=$i $oldDoc value=$value number=${numbers.longValue()}")
                }
            }
            if (failed) {
                if (r !is LeafReader) {
                    println("TEST FAILED; check leaves")
                    for (ctx in r.leaves()) {
                        println("CHECK LEAF=${ctx.reader()}")
                        checkAllNumberDVs(ctx.reader(), fieldName, false, 1)
                    }
                }
                if (doThrow) {
                    assertFalse(failed, "FAILED field=$fieldName r=$r")
                } else {
                    println("FAILED field=$fieldName r=$r")
                }
            }
        }

        private fun testNumericDVSort(s: IndexSearcher) {
            // Confirm we can sort by the new DV field:
            val hits =
                s.search(
                    MatchAllDocsQuery(),
                    100,
                    Sort(SortField("number", SortField.Type.LONG)),
                )
            val storedFields = s.storedFields()
            var last = Long.MIN_VALUE
            for (scoreDoc in hits.scoreDocs) {
                val value = storedFields.document(scoreDoc.doc).get("text")!!.split(" ")[1].toLong()
                assertTrue(value >= last)
                assertEquals(value, ((scoreDoc as FieldDoc).fields!![0] as Long))
                last = value
            }
        }

        private fun testPointRangeQuery(s: IndexSearcher) {
            for (i in 0..<100) {
                // Confirm we can range search by the new indexed (numeric) field:
                var min = random().nextLong()
                var max = random().nextLong()
                if (min > max) {
                    val x = min
                    min = max
                    max = x
                }

                val hits = s.search(LongPoint.newRangeQuery("number", min, max), 100)
                val storedFields = s.storedFields()
                for (scoreDoc in hits.scoreDocs) {
                    val value = storedFields.document(scoreDoc.doc).get("text")!!.split(" ")[1].toLong()
                    assertTrue(value >= min)
                    assertTrue(value <= max)
                }

                hits.scoreDocs.sortBy { it.doc }

                val numbers = MultiDocValues.getNumericValues(s.indexReader, "number")!!
                for (hit in hits.scoreDocs) {
                    if (numbers.docID() < hit.doc) {
                        numbers.advance(hit.doc)
                    }
                    assertEquals(hit.doc, numbers.docID())
                    val value = storedFields.document(hit.doc).get("text")!!.split(" ")[1].toLong()
                    assertEquals(value, numbers.longValue())
                }
            }
        }
    }

    inner abstract class ReindexingReader(root: Path) : AutoCloseable {
        val w: IndexWriter
        val mgr: ReaderManager
        private val parallelReadersLock = ReentrantLock()

        private val indexDir: Directory
        private val segsPath: Path

        /** Which segments have been closed, but their parallel index is not yet not removed. */
        private val closedSegments = mutableSetOf<SegmentIDAndGen>()

        /** Holds currently open parallel readers for each segment. */
        private val parallelReaders = mutableMapOf<SegmentIDAndGen, LeafReader>()
        private val parallelReaderDirs = mutableMapOf<SegmentIDAndGen, Directory>()

        init {
            // Normal index is stored under "index":
            indexDir = openDirectory(root.resolve("index"))

            // Per-segment parallel indices are stored under subdirs "segs":
            segsPath = root.resolve("segs")
            Files.getFileSystem().createDirectories(segsPath)

            val iwc = getIndexWriterConfig()
            iwc.mergePolicy = ReindexingMergePolicy(iwc.mergePolicy)
            if (DEBUG) {
                println("TEST: use IWC:\n$iwc")
            }
            w = IndexWriter(indexDir, iwc)

            w.config.mergedSegmentWarmer =
                IndexWriter.IndexReaderWarmer { reader ->
                    // This will build the parallel index for the merged segment before the merge
                    // becomes visible, so reopen delay is only due to
                    // newly flushed segments:
                    if (DEBUG) {
                        println("TEST: now warm $reader")
                    }
                    // TODO: it's not great that we pass false here; it means we close the reader &
                    // reopen again for NRT reader; still we did "warm" by
                    // building the parallel index, if necessary
                    getParallelLeafReader(reader, false, getCurrentSchemaGen())
                }

            // start with empty commit:
            w.commit()
            mgr = ReaderManager(ParallelLeafDirectoryReader(DirectoryReader.open(w)))
        }

        fun printRefCounts() {
            println("All refCounts:")
            for ((key, value) in parallelReaders) {
                println("  $key $value refCount=${value.getRefCount()}")
            }
        }

        protected abstract fun getIndexWriterConfig(): IndexWriterConfig

        /**
         * Optional method to validate that the provided parallel reader in fact reflects the changes
         * in schemaGen.
         */
        protected open fun checkParallelReader(
            reader: LeafReader,
            parallelReader: LeafReader,
            schemaGen: Long,
        ) {}

        /** Override to customize Directory impl. */
        protected open fun openDirectory(path: Path): Directory {
            return FSDirectory.open(path)
        }

        fun commit() {
            w.commit()
        }

        fun getCurrentReader(reader: LeafReader, schemaGen: Long): LeafReader {
            val parallelReader = getParallelLeafReader(reader, true, schemaGen)
            if (parallelReader != null) {
                // We should not be embedding one ParallelLeafReader inside another:
                assertFalse(parallelReader is ParallelLeafReader)
                assertFalse(reader is ParallelLeafReader)

                // NOTE: important that parallelReader is first, so if there are field name overlaps,
                // because changes to the schema
                // overwrote existing field names, it wins:
                val newReader =
                    object : ParallelLeafReader(false, parallelReader, reader) {
                        override val liveDocs: Bits?
                            get() {
                                return getParallelReaders()[1].liveDocs
                            }

                        override fun numDocs(): Int {
                            return getParallelReaders()[1].numDocs()
                        }
                    }

                // Because ParallelLeafReader does its own (extra) incRef:
                runBlocking {
                    parallelReader.decRef()
                }

                return newReader
            } else {
                // This segment was already current as of currentSchemaGen:
                return reader
            }
        }

        private inner class ParallelLeafDirectoryReader(internalReader: DirectoryReader) :
            FilterDirectoryReader(
                internalReader,
                object : SubReaderWrapper() {
                    val currentSchemaGen = getCurrentSchemaGen()

                    override fun wrap(reader: LeafReader): LeafReader {
                        return try {
                            getCurrentReader(reader, currentSchemaGen)
                        } catch (ioe: IOException) {
                            // TODO: must close on exc here:
                            throw RuntimeException(ioe)
                        }
                    }
                },
            ) {
            override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
                return ParallelLeafDirectoryReader(`in`)
            }

            override fun doClose() {
                var firstExc: Throwable? = null
                for (r in sequentialSubReaders) {
                    if (r is ParallelLeafReader) {
                        // try to close each reader, even if an exception is thrown
                        try {
                            runBlocking {
                                r.decRef()
                            }
                        } catch (t: Throwable) {
                            if (firstExc == null) {
                                firstExc = t
                            }
                        }
                    }
                }
                // Also close in, so it decRef's the SegmentInfos
                try {
                    `in`.close()
                } catch (t: Throwable) {
                    if (firstExc == null) {
                        firstExc = t
                    }
                }

                // throw the first exception
                if (firstExc != null) {
                    throw IOUtils.rethrowAlways(firstExc)
                }
            }

            override val readerCacheHelper: CacheHelper?
                get() = null
        }

        override fun close() {
            w.close()
            if (DEBUG) {
                println("TEST: after close writer index=${SegmentInfos.readLatestCommit(indexDir)}")
            }

            mgr.close()
            pruneOldSegments(true)
            assertNoExtraSegments()
            IOUtils.close(parallelReaderDirs.values)
            parallelReaderDirs.clear()
            indexDir.close()
        }

        // Make sure we deleted all parallel indices for segments that are no longer in the main index:
        private fun assertNoExtraSegments() {
            if (DEBUG) {
                val liveIDs = HashSet<String>()
                for (info in SegmentInfos.readLatestCommit(indexDir)) {
                    liveIDs.add(info.info.name)
                }

                for (path in segSubDirs(segsPath)) {
                    val segIDGen = SegmentIDAndGen(path.toString().substringAfterLast('/'))
                    if (!liveIDs.contains(segIDGen.segID)) {
                        println("TEST: leftover parallel segment dir=${path}")
                    }
                }
            }
        }

        private inner class ParallelReaderClosed(
            private val segIDGen: SegmentIDAndGen,
            private val dir: Directory,
        ) : IndexReader.ClosedListener {
            override fun onClose(ignored: IndexReader.CacheKey) {
                try {
                    // TODO: make this sync finer, i.e. just the segment + schemaGen
                    parallelReadersLock.lock()
                    try {
                        if (DEBUG) {
                            println(
                                "TEST: now close parallel parLeafReader dir=$dir segIDGen=$segIDGen"
                            )
                        }
                        parallelReaders.remove(segIDGen)
                        closedSegments.add(segIDGen)
                    } finally {
                        parallelReadersLock.unlock()
                    }
                } catch (ioe: IOException) {
                    println("TEST: hit IOExc closing dir=$dir")
                    println(ioe)
                    throw RuntimeException(ioe)
                }
            }
        }

        // Returns a ref
        fun getParallelLeafReader(leaf: LeafReader, doCache: Boolean, schemaGen: Long): LeafReader? {
            assertTrue(leaf is SegmentReader)
            val info = (leaf as SegmentReader).segmentInfo.info

            val infoSchemaGen = getSchemaGen(info)

            if (DEBUG) {
                println(
                    "TEST: getParallelLeafReader: $leaf infoSchemaGen=$infoSchemaGen vs schemaGen=$schemaGen doCache=$doCache"
                )
            }

            if (infoSchemaGen == schemaGen) {
                if (DEBUG) {
                    println(
                        "TEST: segment is already current schemaGen=$schemaGen; skipping"
                    )
                }
                return null
            }

            if (infoSchemaGen > schemaGen) {
                throw IllegalStateException(
                    "segment infoSchemaGen ($infoSchemaGen) cannot be greater than requested schemaGen ($schemaGen)"
                )
            }

            val segIDGen = SegmentIDAndGen(info.name, schemaGen)

            // While loop because the parallel reader may be closed out from under us, so we must retry:
            while (true) {
                // TODO: make this sync finer, i.e. just the segment + schemaGen
                parallelReadersLock.lock()
                try {
                    var parReader = parallelReaders[segIDGen]

                    assertTrue(doCache || parReader == null)

                    if (parReader == null) {
                        val leafIndex = segsPath.resolve(segIDGen.toString())
                        val dir = openDirectory(leafIndex)

                        if (!slowFileExists(dir, "done")) {
                            if (DEBUG) {
                                println(
                                    "TEST: build segment index for $leaf $segIDGen (source: ${info.diagnostics["source"]}) dir=$leafIndex"
                                )
                            }

                            if (dir.listAll().isNotEmpty()) {
                                // It crashed before finishing last time:
                                if (DEBUG) {
                                    println(
                                        "TEST: remove old incomplete index files: $leafIndex"
                                    )
                                }
                                IOUtils.rm(leafIndex)
                            }

                            reindex(infoSchemaGen, schemaGen, leaf, dir)

                            // Marker file, telling us this index is in fact done.  This way if we crash while
                            // doing the reindexing for a given segment, we will
                            // later try again:
                            dir.createOutput("done", IOContext.DEFAULT).close()
                        } else {
                            if (DEBUG) {
                                println(
                                    "TEST: segment index already exists for $leaf $segIDGen (source: ${info.diagnostics["source"]}) dir=$leafIndex"
                                )
                            }
                        }

                        if (DEBUG) {
                            println("TEST: now check index $dir")
                        }
                        // TestUtil.checkIndex(dir);

                        val infos = SegmentInfos.readLatestCommit(dir)
                        assertEquals(1, infos.size())
                        val parLeafReader =
                            SegmentReader(infos.info(0), Version.LATEST.major, IOContext.DEFAULT)

                        // checkParallelReader(leaf, parLeafReader, schemaGen);

                        if (DEBUG) {
                            println(
                                "TEST: opened parallel reader: $parLeafReader"
                            )
                        }
                        if (doCache) {
                            parallelReaders[segIDGen] = parLeafReader
                            parallelReaderDirs[segIDGen] = dir

                            // Our id+gen could have been previously closed, e.g. if it was a merged segment that
                            // was warmed, so we must clear this else
                            // the pruning may remove our directory:
                            closedSegments.remove(segIDGen)

                            runBlocking {
                                parLeafReader.readerCacheHelper.addClosedListener(
                                    ParallelReaderClosed(segIDGen, dir)
                                )
                            }
                        } else {
                            // Used only for merged segment warming:
                            // Messy: we close this reader now, instead of leaving open for reuse:
                            if (DEBUG) {
                                println("TEST: now decRef non cached refCount=${parLeafReader.getRefCount()}")
                            }
                            runBlocking {
                                parLeafReader.decRef()
                            }
                            dir.close()

                            // Must do this after dir is closed, else another thread could "rm -rf" while we are
                            // closing (which makes MDW.close's
                            // checkIndex angry):
                            closedSegments.add(segIDGen)
                        }
                        parReader = parLeafReader
                    } else {
                        if (!parReader.tryIncRef()) {
                            // We failed: this reader just got closed by another thread, e.g. refresh thread
                            // opening a new reader, so this reader is now
                            // closed and we must try again.
                            if (DEBUG) {
                                println(
                                    "TEST: tryIncRef failed for $parReader; retry"
                                )
                            }
                            parReader = null
                        } else {
                            if (DEBUG) {
                                println(
                                    "TEST: use existing already opened parReader=$parReader refCount=${parReader.getRefCount()}"
                                )
                            }
                            return parReader
                        }
                        continue
                    }

                    return parReader
                } finally {
                    parallelReadersLock.unlock()
                }
            }
        }

        // TODO: we could pass a writer already opened...?
        protected abstract fun reindex(
            oldSchemaGen: Long,
            newSchemaGen: Long,
            reader: LeafReader,
            parallelDir: Directory,
        )

        /** Returns the gen for the current schema. */
        protected abstract fun getCurrentSchemaGen(): Long

        /**
         * Returns the gen that should be merged, meaning those changes will be folded back into the
         * main index.
         */
        protected open fun getMergingSchemaGen(): Long {
            return getCurrentSchemaGen()
        }

        /**
         * Removes the parallel index that are no longer in the last commit point. We can't remove
         * this when the parallel reader is closed because it may still be referenced by the last
         * commit.
         */
        private fun pruneOldSegments(removeOldGens: Boolean) {
            val lastCommit = SegmentInfos.readLatestCommit(indexDir)
            if (DEBUG) {
                println("TEST: prune")
            }

            val liveIDs = HashSet<String>()
            for (info in lastCommit) {
                val idString = info.info.name
                liveIDs.add(idString)
            }

            val currentSchemaGen = getCurrentSchemaGen()

            if (Files.getFileSystem().exists(segsPath)) {
                for (path in segSubDirs(segsPath)) {
                    if (Files.getFileSystem().metadata(path).isDirectory) {
                        val segIDGen = SegmentIDAndGen(path.name)
                        assertTrue(segIDGen.schemaGen <= currentSchemaGen)
                        if (!liveIDs.contains(segIDGen.segID) &&
                            (closedSegments.contains(segIDGen) ||
                                (removeOldGens && segIDGen.schemaGen < currentSchemaGen))
                        ) {
                            if (DEBUG) {
                                println("TEST: remove $segIDGen")
                            }
                            try {
                                parallelReaderDirs.remove(segIDGen)?.close()
                                IOUtils.rm(path)
                                closedSegments.remove(segIDGen)
                            } catch (ioe: IOException) {
                                // OK, we'll retry later
                                if (DEBUG) {
                                    println("TEST: ignore ioe during delete $path:$ioe")
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Just replaces the sub-readers with parallel readers, so reindexed fields are merged into
         * new segments.
         */
        private inner class ReindexingMergePolicy(internalMergePolicy: MergePolicy) :
            FilterMergePolicy(internalMergePolicy) {
            inner class ReindexingOneMerge(segments: MutableList<SegmentCommitInfo>) : OneMerge(segments) {
                val parallelReaders = ArrayList<ParallelLeafReader>()
                val schemaGen: Long

                init {
                    // Commit up front to which schemaGen we will merge; we don't want a schema change
                    // sneaking in for some of our leaf readers but not others:
                    schemaGen = getMergingSchemaGen()
                    val currentSchemaGen = getCurrentSchemaGen()

                    // Defensive sanity check:
                    if (schemaGen > currentSchemaGen) {
                        throw IllegalStateException(
                            "currentSchemaGen ($currentSchemaGen) must always be >= mergingSchemaGen ($schemaGen)"
                        )
                    }
                }

                override fun wrapForMerge(reader: CodecReader): CodecReader {
                    val wrapped = getCurrentReader(reader, schemaGen)
                    if (wrapped is ParallelLeafReader) {
                        parallelReaders.add(wrapped)
                    }
                    return SlowCodecReaderWrapper.wrap(wrapped)
                }

                override fun mergeFinished(success: Boolean, segmentDropped: Boolean) {
                    super.mergeFinished(success, segmentDropped)
                    var th: Throwable? = null
                    for (r in parallelReaders) {
                        try {
                            runBlocking {
                                r.decRef()
                            }
                        } catch (t: Throwable) {
                            if (th == null) {
                                th = t
                            }
                        }
                    }

                    if (th != null) {
                        throw IOUtils.rethrowAlways(th)
                    }
                }

                override fun setMergeInfo(info: SegmentCommitInfo) {
                    // Record that this merged segment is current as of this schemaGen:
                    info.info.addDiagnostics(
                        mutableMapOf<String, String>(SCHEMA_GEN_KEY to schemaGen.toString())
                    )
                    super.setMergeInfo(info)
                }
            }

            fun wrap(spec: MergeSpecification?): MergeSpecification? {
                if (spec == null) {
                    return null
                }
                val wrapped = MergeSpecification()
                for (merge in spec.merges) {
                    wrapped.add(ReindexingOneMerge(merge.segments.toMutableList()))
                }
                return wrapped
            }

            /** Create a new `MergePolicy` that sorts documents with the given `sort`. */
            override fun findMerges(
                mergeTrigger: MergeTrigger?,
                segmentInfos: SegmentInfos?,
                mergeContext: MergeContext?,
            ): MergeSpecification? {
                return wrap(`in`.findMerges(mergeTrigger, segmentInfos, mergeContext))
            }

            override fun findForcedMerges(
                segmentInfos: SegmentInfos?,
                maxSegmentCount: Int,
                segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
                mergeContext: MergeContext?,
            ): MergeSpecification? {
                // TODO: do we need to force-force this?  Ie, wrapped MP may think index is already
                // optimized, yet maybe its schemaGen is old?  need test!
                return wrap(
                    `in`.findForcedMerges(segmentInfos, maxSegmentCount, segmentsToMerge, mergeContext)
                )
            }

            override fun findForcedDeletesMerges(
                segmentInfos: SegmentInfos?,
                mergeContext: MergeContext?,
            ): MergeSpecification? {
                return wrap(`in`.findForcedDeletesMerges(segmentInfos, mergeContext))
            }

            override fun findFullFlushMerges(
                mergeTrigger: MergeTrigger,
                segmentInfos: SegmentInfos,
                mergeContext: MergeContext,
            ): MergeSpecification? {
                return wrap(`in`.findFullFlushMerges(mergeTrigger, segmentInfos, mergeContext))
            }

            override fun useCompoundFile(
                segments: SegmentInfos,
                newSegment: SegmentCommitInfo,
                mergeContext: MergeContext,
            ): Boolean {
                return `in`.useCompoundFile(segments, newSegment, mergeContext)
            }

            override fun toString(): String {
                return "ReindexingMergePolicy($`in`)"
            }
        }
    }

    private fun getReindexer(root: Path): ReindexingReader {
        return object : ReindexingReader(root) {
            override fun getIndexWriterConfig(): IndexWriterConfig {
                val iwc = newIndexWriterConfig()
                val tmp = TieredMergePolicy()
                // We write tiny docs, so we need tiny floor to avoid O(N^2) merging:
                tmp.setFloorSegmentMB(.01)
                iwc.mergePolicy = tmp
                return iwc
            }

            override fun openDirectory(path: Path): Directory {
                val dir = newMockFSDirectory(path)
                dir.useSlowOpenClosers = false
                dir.setThrottling(Throttling.NEVER)
                return dir
            }

            override fun reindex(
                oldSchemaGen: Long,
                newSchemaGen: Long,
                reader: LeafReader,
                parallelDir: Directory,
            ) {
                val iwc = newIndexWriterConfig()

                // The order of our docIDs must precisely match incoming reader:
                iwc.mergePolicy = LogByteSizeMergePolicy()
                val w = IndexWriter(parallelDir, iwc)
                val maxDoc = reader.maxDoc()
                val storedFields = reader.storedFields()

                // Slowly parse the stored field into a new doc values field:
                for (i in 0..<maxDoc) {
                    // TODO: is this still O(blockSize^2)?
                    val oldDoc = storedFields.document(i)
                    val newDoc = Document()
                    val value = oldDoc.get("text")!!.split(" ")[1].toLong()
                    newDoc.add(NumericDocValuesField("number", value))
                    newDoc.add(LongPoint("number", value))
                    w.addDocument(newDoc)
                }

                w.forceMerge(1)
                w.close()
            }

            override fun getCurrentSchemaGen(): Long {
                return 0
            }
        }
    }

    /** Schema change by adding a new number_<schemaGen> DV field each time. */
    @OptIn(ExperimentalAtomicApi::class)
    private fun getReindexerNewDVFields(
        root: Path,
        currentSchemaGen: AtomicLong,
    ): ReindexingReader {
        return object : ReindexingReader(root) {
            override fun getIndexWriterConfig(): IndexWriterConfig {
                val iwc = newIndexWriterConfig()
                val tmp = TieredMergePolicy()
                // We write tiny docs, so we need tiny floor to avoid O(N^2) merging:
                tmp.setFloorSegmentMB(.01)
                iwc.mergePolicy = tmp
                return iwc
            }

            override fun openDirectory(path: Path): Directory {
                val dir = newMockFSDirectory(path)
                dir.useSlowOpenClosers = false
                dir.setThrottling(Throttling.NEVER)
                return dir
            }

            override fun reindex(
                oldSchemaGen: Long,
                newSchemaGen: Long,
                reader: LeafReader,
                parallelDir: Directory,
            ) {
                val iwc = newIndexWriterConfig()

                // The order of our docIDs must precisely match incoming reader:
                iwc.mergePolicy = LogByteSizeMergePolicy()
                val w = IndexWriter(parallelDir, iwc)
                val maxDoc = reader.maxDoc()
                val storedFields = reader.storedFields()

                if (oldSchemaGen <= 0) {
                    // Must slowly parse the stored field into a new doc values field:
                    for (i in 0..<maxDoc) {
                        // TODO: is this still O(blockSize^2)?
                        val oldDoc = storedFields.document(i)
                        val newDoc = Document()
                        val value = oldDoc.get("text")!!.split(" ")[1].toLong()
                        newDoc.add(NumericDocValuesField("number_$newSchemaGen", value))
                        newDoc.add(LongPoint("number", value))
                        w.addDocument(newDoc)
                    }
                } else {
                    // Just carry over doc values from previous field:
                    val oldValues = reader.getNumericDocValues("number_$oldSchemaGen")
                    assertNotNull(oldValues, "oldSchemaGen=$oldSchemaGen")
                    for (i in 0..<maxDoc) {
                        // TODO: is this still O(blockSize^2)?
                        assertEquals(i, oldValues.nextDoc())
                        storedFields.document(i)
                        val newDoc = Document()
                        newDoc.add(
                            NumericDocValuesField("number_$newSchemaGen", oldValues.longValue())
                        )
                        w.addDocument(newDoc)
                    }
                }

                w.forceMerge(1)
                w.close()
            }

            override fun getCurrentSchemaGen(): Long {
                return currentSchemaGen.load()
            }

            override fun checkParallelReader(reader: LeafReader, parallelReader: LeafReader, schemaGen: Long) {
                val fieldName = "number_$schemaGen"
                if (DEBUG) {
                    println(
                        "TEST: now check parallel number DVs field=$fieldName r=$reader parR=$parallelReader"
                    )
                }
                val numbers = parallelReader.getNumericDocValues(fieldName) ?: return
                val maxDoc = reader.maxDoc()
                val storedFields = reader.storedFields()
                var failed = false
                for (i in 0..<maxDoc) {
                    val oldDoc = storedFields.document(i)
                    val value = oldDoc.get("text")!!.split(" ")[1].toLong()
                    assertEquals(i, numbers.nextDoc())
                    if (value != numbers.longValue()) {
                        if (DEBUG) {
                            println(
                                "FAIL: docID=$i $oldDoc value=$value number=${numbers.longValue()} numbers=$numbers"
                            )
                        }
                        failed = true
                    } else if (failed) {
                        if (DEBUG) {
                            println(
                                "OK: docID=$i $oldDoc value=$value number=${numbers.longValue()}"
                            )
                        }
                    }
                }
                assertFalse(failed, "FAILED field=$fieldName r=$reader")
            }
        }
    }

    /** Schema change by adding changing how the same "number" DV field is indexed. */
    @OptIn(ExperimentalAtomicApi::class)
    private fun getReindexerSameDVField(
        root: Path,
        currentSchemaGen: AtomicLong,
        mergingSchemaGen: AtomicLong,
    ): ReindexingReader {
        return object : ReindexingReader(root) {
            override fun getIndexWriterConfig(): IndexWriterConfig {
                val iwc = newIndexWriterConfig()
                val tmp = TieredMergePolicy()
                // We write tiny docs, so we need tiny floor to avoid O(N^2) merging:
                tmp.setFloorSegmentMB(.01)
                iwc.mergePolicy = tmp
                if (TEST_NIGHTLY) {
                    // during nightly tests, we might use too many files if we aren't careful
                    iwc.setUseCompoundFile(true)
                }
                return iwc
            }

            override fun openDirectory(path: Path): Directory {
                val dir = newMockFSDirectory(path)
                dir.useSlowOpenClosers = false
                dir.setThrottling(Throttling.NEVER)
                return dir
            }

            override fun reindex(
                oldSchemaGen: Long,
                newSchemaGen: Long,
                reader: LeafReader,
                parallelDir: Directory,
            ) {
                val iwc = newIndexWriterConfig()

                // The order of our docIDs must precisely match incoming reader:
                iwc.mergePolicy = LogByteSizeMergePolicy()
                val w = IndexWriter(parallelDir, iwc)
                val maxDoc = reader.maxDoc()
                val storedFields = reader.storedFields()

                if (oldSchemaGen <= 0) {
                    // Must slowly parse the stored field into a new doc values field:
                    for (i in 0..<maxDoc) {
                        // TODO: is this still O(blockSize^2)?
                        val oldDoc = storedFields.document(i)
                        val newDoc = Document()
                        val value = oldDoc.get("text")!!.split(" ")[1].toLong()
                        newDoc.add(NumericDocValuesField("number", newSchemaGen * value))
                        newDoc.add(LongPoint("number", value))
                        w.addDocument(newDoc)
                    }
                } else {
                    val oldValues = reader.getNumericDocValues("number")
                    assertNotNull(oldValues, "oldSchemaGen=$oldSchemaGen")
                    for (i in 0..<maxDoc) {
                        // TODO: is this still O(blockSize^2)?
                        storedFields.document(i)
                        val newDoc = Document()
                        assertEquals(i, oldValues.nextDoc())
                        val value = newSchemaGen * (oldValues.longValue() / oldSchemaGen)
                        newDoc.add(NumericDocValuesField("number", value))
                        newDoc.add(LongPoint("number", value))
                        w.addDocument(newDoc)
                    }
                }

                w.forceMerge(1)
                w.close()
            }

            override fun getCurrentSchemaGen(): Long {
                return currentSchemaGen.load()
            }

            override fun getMergingSchemaGen(): Long {
                return mergingSchemaGen.load()
            }

            override fun checkParallelReader(reader: LeafReader, parallelReader: LeafReader, schemaGen: Long) {
                if (DEBUG) {
                    println(
                        "TEST: now check parallel number DVs r=$reader parR=$parallelReader"
                    )
                }
                val numbers = parallelReader.getNumericDocValues("numbers") ?: return
                val maxDoc = reader.maxDoc()
                val storedFields = reader.storedFields()
                var failed = false
                for (i in 0..<maxDoc) {
                    val oldDoc = storedFields.document(i)
                    var value = oldDoc.get("text")!!.split(" ")[1].toLong()
                    value *= schemaGen
                    assertEquals(i, numbers.nextDoc())
                    if (value != numbers.longValue()) {
                        println(
                            "FAIL: docID=$i $oldDoc value=$value number=${numbers.longValue()} numbers=$numbers"
                        )
                        failed = true
                    } else if (failed) {
                        println(
                            "OK: docID=$i $oldDoc value=$value number=${numbers.longValue()}"
                        )
                    }
                }
                assertFalse(failed, "FAILED r=$reader")
            }
        }
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testBasicMultipleSchemaGens() {
        val currentSchemaGen = AtomicLong(0L)

        // TODO: separate refresh thread, search threads, indexing threads
        val root = createTempDir()
        val reindexer = getReindexerNewDVFields(root, currentSchemaGen)
        reindexer.commit()

        var doc = Document()
        doc.add(newTextField("text", "number ${random().nextLong()}", Field.Store.YES))
        reindexer.w.addDocument(doc)

        if (DEBUG) {
            println("TEST: refresh @ 1 doc")
        }
        reindexer.mgr.maybeRefresh()
        var r = reindexer.mgr.acquire()
        if (DEBUG) {
            println("TEST: got reader=$r")
        }
        try {
            checkAllNumberDVs(r, "number_${currentSchemaGen.load()}", true, 1)
        } finally {
            reindexer.mgr.release(r)
        }

        currentSchemaGen.fetchAndIncrement()

        if (DEBUG) {
            println("TEST: increment schemaGen")
            println("TEST: commit")
        }
        reindexer.commit()

        doc = Document()
        doc.add(newTextField("text", "number ${random().nextLong()}", Field.Store.YES))
        reindexer.w.addDocument(doc)

        if (DEBUG) {
            println("TEST: refresh @ 2 docs")
        }
        reindexer.mgr.maybeRefresh()
        r = reindexer.mgr.acquire()
        if (DEBUG) {
            println("TEST: got reader=$r")
        }
        try {
            checkAllNumberDVs(r, "number_${currentSchemaGen.load()}", true, 1)
        } finally {
            reindexer.mgr.release(r)
        }

        if (DEBUG) {
            println("TEST: forceMerge")
        }
        reindexer.w.forceMerge(1)

        currentSchemaGen.fetchAndIncrement()

        if (DEBUG) {
            println("TEST: commit")
        }
        reindexer.commit()

        if (DEBUG) {
            println("TEST: refresh after forceMerge")
        }
        reindexer.mgr.maybeRefresh()
        r = reindexer.mgr.acquire()
        if (DEBUG) {
            println("TEST: got reader=$r")
        }
        try {
            checkAllNumberDVs(r, "number_${currentSchemaGen.load()}", true, 1)
        } finally {
            reindexer.mgr.release(r)
        }

        if (DEBUG) {
            println("TEST: close writer")
        }
        reindexer.close()
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testRandomMultipleSchemaGens() {
        val currentSchemaGen = AtomicLong(0L)
        var reindexer: ReindexingReader? = null

        // TODO: separate refresh thread, search threads, indexing threads
        val numDocs = atLeast(if (TEST_NIGHTLY) 20000 else 200)
        var maxID = 0
        val root = createTempDir()
        var refreshEveryNumDocs = 100
        var commitCloseNumDocs = 1000
        for (i in 0..<numDocs) {
            if (reindexer == null) {
                reindexer = getReindexerNewDVFields(root, currentSchemaGen)
            }

            val doc = Document()
            val id: String
            val updateID: String?
            if (maxID > 0 && random().nextInt(10) == 7) {
                // Replace a doc
                id = random().nextInt(maxID).toString()
                updateID = id
            } else {
                id = (maxID++).toString()
                updateID = null
            }

            doc.add(newStringField("id", id, Field.Store.NO))
            doc.add(newTextField("text", "number ${random().nextLong()}", Field.Store.YES))
            if (updateID == null) {
                reindexer.w.addDocument(doc)
            } else {
                reindexer.w.updateDocument(Term("id", updateID), doc)
            }
            if (random().nextInt(refreshEveryNumDocs) == 17) {
                if (DEBUG) {
                    println("TEST TOP: refresh @ ${i + 1} docs")
                }
                reindexer.mgr.maybeRefresh()

                val r = reindexer.mgr.acquire()
                if (DEBUG) {
                    println("TEST TOP: got reader=$r")
                }
                try {
                    checkAllNumberDVs(r, "number_${currentSchemaGen.load()}", true, 1)
                } finally {
                    reindexer.mgr.release(r)
                }
                if (DEBUG) {
                    reindexer.printRefCounts()
                }
                refreshEveryNumDocs = (1.25 * refreshEveryNumDocs).toInt()
            }

            if (random().nextInt(500) == 17) {
                currentSchemaGen.fetchAndIncrement()
                if (DEBUG) {
                    println(
                        "TEST TOP: advance schemaGen to $currentSchemaGen"
                    )
                }
            }

            if (i > 0 && random().nextInt(10) == 7) {
                // Random delete:
                reindexer.w.deleteDocuments(Term("id", random().nextInt(i).toString()))
            }

            if (random().nextInt(commitCloseNumDocs) == 17) {
                if (DEBUG) {
                    println("TEST TOP: commit @ ${i + 1} docs")
                }
                reindexer.commit()
                commitCloseNumDocs = (1.25 * commitCloseNumDocs).toInt()
            }

            // Sometimes close & reopen writer/manager, to confirm the parallel segments persist:
            if (random().nextInt(commitCloseNumDocs) == 17) {
                if (DEBUG) {
                    println("TEST TOP: close writer @ ${i + 1} docs")
                }
                reindexer.close()
                reindexer = null
                commitCloseNumDocs = (1.25 * commitCloseNumDocs).toInt()
            }
        }

        reindexer?.close()
    }

    /**
     * First schema change creates a new "number" DV field off the stored field; subsequent changes
     * just change the value of that number field for all docs.
     */
    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testRandomMultipleSchemaGensSameField() {
        val currentSchemaGen = AtomicLong(0L)
        val mergingSchemaGen = AtomicLong(0L)

        var reindexer: ReindexingReader? = null

        // TODO: separate refresh thread, search threads, indexing threads
        val numDocs = atLeast(if (TEST_NIGHTLY) 20000 else 200)
        var maxID = 0
        val root = createTempDir()
        var refreshEveryNumDocs = 100
        var commitCloseNumDocs = 1000

        for (i in 0..<numDocs) {
            if (reindexer == null) {
                if (DEBUG) {
                    println("TEST TOP: open new reader/writer")
                }
                reindexer = getReindexerSameDVField(root, currentSchemaGen, mergingSchemaGen)
            }

            val doc = Document()
            val id: String
            val updateID: String?
            if (maxID > 0 && random().nextInt(10) == 7) {
                // Replace a doc
                id = random().nextInt(maxID).toString()
                updateID = id
            } else {
                id = (maxID++).toString()
                updateID = null
            }

            doc.add(newStringField("id", id, Field.Store.NO))
            doc.add(
                newTextField(
                    "text",
                    "number ${TestUtil.nextInt(random(), -10000, 10000)}",
                    Field.Store.YES,
                )
            )
            if (updateID == null) {
                reindexer.w.addDocument(doc)
            } else {
                reindexer.w.updateDocument(Term("id", updateID), doc)
            }
            if (random().nextInt(refreshEveryNumDocs) == 17) {
                if (DEBUG) {
                    println("TEST TOP: refresh @ ${i + 1} docs")
                }
                reindexer.mgr.maybeRefresh()
                val r = reindexer.mgr.acquire()
                if (DEBUG) {
                    println("TEST TOP: got reader=$r")
                }
                try {
                    checkAllNumberDVs(r, "number", true, currentSchemaGen.load().toInt())
                } finally {
                    reindexer.mgr.release(r)
                }
                if (DEBUG) {
                    reindexer.printRefCounts()
                }
                refreshEveryNumDocs = (1.25 * refreshEveryNumDocs).toInt()
            }

            if (random().nextInt(500) == 17) {
                currentSchemaGen.fetchAndIncrement()
                if (DEBUG) {
                    println(
                        "TEST TOP: advance schemaGen to $currentSchemaGen"
                    )
                }
                if (random().nextBoolean()) {
                    mergingSchemaGen.fetchAndIncrement()
                    if (DEBUG) {
                        println(
                            "TEST TOP: advance mergingSchemaGen to $mergingSchemaGen"
                        )
                    }
                }
            }

            if (i > 0 && random().nextInt(10) == 7) {
                // Random delete:
                reindexer.w.deleteDocuments(Term("id", random().nextInt(i).toString()))
            }

            if (random().nextInt(commitCloseNumDocs) == 17) {
                if (DEBUG) {
                    println("TEST TOP: commit @ ${i + 1} docs")
                }
                reindexer.commit()
                commitCloseNumDocs = (1.25 * commitCloseNumDocs).toInt()
            }

            // Sometimes close & reopen writer/manager, to confirm the parallel segments persist:
            if (random().nextInt(commitCloseNumDocs) == 17) {
                if (DEBUG) {
                    println("TEST TOP: close writer @ ${i + 1} docs")
                }
                reindexer.close()
                reindexer = null
                commitCloseNumDocs = (1.25 * commitCloseNumDocs).toInt()
            }
        }

        reindexer?.close()

        // Verify main index never reflects schema changes beyond mergingSchemaGen:
        newFSDirectory(root.resolve("index")).use { dir ->
            DirectoryReader.open(dir).use { r ->
                for (ctx in r.leaves()) {
                    val leaf = ctx.reader()
                    val numbers = leaf.getNumericDocValues("number")
                    if (numbers != null) {
                        val maxDoc = leaf.maxDoc()
                        val storedFields = leaf.storedFields()
                        for (i in 0..<maxDoc) {
                            val doc = storedFields.document(i)
                            val value = doc.get("text")!!.split(" ")[1].toLong()
                            assertEquals(i, numbers.nextDoc())
                            val dvValue = numbers.longValue()
                            if (value == 0L) {
                                assertEquals(0L, dvValue)
                            } else {
                                assertTrue(dvValue % value == 0L)
                                assertTrue(dvValue / value <= mergingSchemaGen.load())
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testBasic() {
        val tempPath = createTempDir()
        val reindexer = getReindexer(tempPath)

        // Start with initial empty commit:
        reindexer.commit()

        var doc = Document()
        doc.add(newTextField("text", "number ${random().nextLong()}", Field.Store.YES))
        reindexer.w.addDocument(doc)

        if (DEBUG) {
            println("TEST: refresh @ 1 doc")
        }
        reindexer.mgr.maybeRefresh()
        var r = reindexer.mgr.acquire()
        if (DEBUG) {
            println("TEST: got reader=$r")
        }
        try {
            checkAllNumberDVs(r)
            val s = newSearcher(r)
            testNumericDVSort(s)
            testPointRangeQuery(s)
        } finally {
            reindexer.mgr.release(r)
        }

        if (DEBUG) {
            println("TEST: commit")
        }
        reindexer.commit()

        doc = Document()
        doc.add(newTextField("text", "number ${random().nextLong()}", Field.Store.YES))
        reindexer.w.addDocument(doc)

        if (DEBUG) {
            println("TEST: refresh @ 2 docs")
        }
        reindexer.mgr.maybeRefresh()
        r = reindexer.mgr.acquire()
        if (DEBUG) {
            println("TEST: got reader=$r")
        }
        try {
            checkAllNumberDVs(r)
            val s = newSearcher(r)
            testNumericDVSort(s)
            testPointRangeQuery(s)
        } finally {
            reindexer.mgr.release(r)
        }

        if (DEBUG) {
            println("TEST: forceMerge")
        }
        reindexer.w.forceMerge(1)

        if (DEBUG) {
            println("TEST: commit")
        }
        reindexer.commit()

        if (DEBUG) {
            println("TEST: refresh after forceMerge")
        }
        reindexer.mgr.maybeRefresh()
        r = reindexer.mgr.acquire()
        if (DEBUG) {
            println("TEST: got reader=$r")
        }
        try {
            checkAllNumberDVs(r)
            val s = newSearcher(r)
            testNumericDVSort(s)
            testPointRangeQuery(s)
        } finally {
            reindexer.mgr.release(r)
        }

        if (DEBUG) {
            println("TEST: close writer")
        }
        reindexer.close()
    }

    @Test
    fun testRandom() {
        val root = createTempDir()
        var reindexer: ReindexingReader? = null

        // TODO: separate refresh thread, search threads, indexing threads
        val numDocs = atLeast(if (TEST_NIGHTLY) 20000 else 200)
        var maxID = 0
        var refreshEveryNumDocs = 100
        var commitCloseNumDocs = 1000
        for (i in 0..<numDocs) {
            if (reindexer == null) {
                reindexer = getReindexer(root)
            }

            val doc = Document()
            val id: String
            val updateID: String?
            if (maxID > 0 && random().nextInt(10) == 7) {
                // Replace a doc
                id = random().nextInt(maxID).toString()
                updateID = id
            } else {
                id = (maxID++).toString()
                updateID = null
            }

            doc.add(newStringField("id", id, Field.Store.NO))
            doc.add(newTextField("text", "number ${random().nextLong()}", Field.Store.YES))
            if (updateID == null) {
                reindexer.w.addDocument(doc)
            } else {
                reindexer.w.updateDocument(Term("id", updateID), doc)
            }

            if (random().nextInt(refreshEveryNumDocs) == 17) {
                if (DEBUG) {
                    println("TEST: refresh @ ${i + 1} docs")
                }
                reindexer.mgr.maybeRefresh()
                val r = reindexer.mgr.acquire()
                if (DEBUG) {
                    println("TEST: got reader=$r")
                }
                try {
                    checkAllNumberDVs(r)
                    val s = newSearcher(r)
                    testNumericDVSort(s)
                    testPointRangeQuery(s)
                } finally {
                    reindexer.mgr.release(r)
                }
                refreshEveryNumDocs = (1.25 * refreshEveryNumDocs).toInt()
            }

            if (i > 0 && random().nextInt(10) == 7) {
                // Random delete:
                reindexer.w.deleteDocuments(Term("id", random().nextInt(i).toString()))
            }

            if (random().nextInt(commitCloseNumDocs) == 17) {
                if (DEBUG) {
                    println("TEST: commit @ ${i + 1} docs")
                }
                reindexer.commit()
                commitCloseNumDocs = (1.25 * commitCloseNumDocs).toInt()
            }

            // Sometimes close & reopen writer/manager, to confirm the parallel segments persist:
            if (random().nextInt(commitCloseNumDocs) == 17) {
                if (DEBUG) {
                    println("TEST: close writer @ ${i + 1} docs")
                }
                reindexer.close()
                reindexer = null
                commitCloseNumDocs = (1.25 * commitCloseNumDocs).toInt()
            }
        }
        reindexer?.close()
    }

    // TODO: test exceptions
}
