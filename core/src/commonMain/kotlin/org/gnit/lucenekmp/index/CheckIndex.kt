package org.gnit.lucenekmp.index

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.index.CheckIndex.Status.DocValuesStatus
import org.gnit.lucenekmp.index.CheckIndex.Status.SegmentInfoStatus
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.HnswGraphProvider
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DocumentStoredFieldVisitor
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.internal.hppc.IntIntHashMap
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.FieldExistsQuery
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.LeafFieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TopKnnCollector
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.Lock
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.CommandLineUtil
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOBooleanSupplier
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.LongBitSet
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.ByteRunAutomaton
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.hnsw.HnswGraph
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.compareUnsigned
import org.gnit.lucenekmp.jdkport.exitProcess
import org.gnit.lucenekmp.jdkport.pop
import org.gnit.lucenekmp.jdkport.printStackTrace
import org.gnit.lucenekmp.jdkport.push
import org.gnit.lucenekmp.jdkport.sort
import org.gnit.lucenekmp.jdkport.toUnsignedString
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.min

/**
 * Basic tool and API to check the health of an index and write a new segments file that removes
 * reference to problematic segments.
 *
 *
 * As this tool checks every byte in the index, on a large index it can take quite a long time to
 * run.
 *
 * @lucene.experimental Please make a complete backup of your index before using this to exorcise
 * corrupted documents from your index!
 */
class CheckIndex(
    dir: Directory,
    writeLock: Lock = dir.obtainLock(IndexWriter.WRITE_LOCK_NAME)
) : AutoCloseable {
    private val dir: Directory
    private val writeLock: Lock
    private var infoStream: PrintStream?

    @Volatile
    private var closed = false

    /**
     * Returned from [.checkIndex] detailing the health and status of the index.
     *
     * @lucene.experimental
     */
    class Status internal constructor() {
        /** True if no problems were found with the index.  */
        var clean: Boolean = false

        /** True if we were unable to locate and load the segments_N file.  */
        var missingSegments: Boolean = false

        /** Name of latest segments_N file in the index.  */
        var segmentsFileName: String? = null

        /** Number of segments in the index.  */
        var numSegments: Int = 0

        /**
         * Empty unless you passed specific segments list to check as optional 3rd argument.
         *
         * @see CheckIndex.checkIndex
         */
        var segmentsChecked: MutableList<String> = mutableListOf()

        /** True if the index was created with a newer version of Lucene than the CheckIndex tool.  */
        var toolOutOfDate: Boolean = false

        /** List of [SegmentInfoStatus] instances, detailing status of each segment.  */
        var segmentInfos: MutableList<SegmentInfoStatus> = mutableListOf()

        /** Directory index is in.  */
        var dir: Directory? = null

        /**
         * SegmentInfos instance containing only segments that had no problems (this is used with the
         * [CheckIndex.exorciseIndex] method to repair the index).
         */
        var newSegments: SegmentInfos? = null

        /** How many documents will be lost to bad segments.  */
        var totLoseDocCount: Int = 0

        /** How many bad segments were found.  */
        var numBadSegments: Int = 0

        /**
         * True if we checked only specific segments ([.checkIndex] was called with non-null
         * argument).
         */
        var partial: Boolean = false

        /** The greatest segment name.  */
        var maxSegmentName: Long = 0

        /** Whether the SegmentInfos.counter is greater than any of the segments' names.  */
        var validCounter: Boolean = false

        /** Holds the userData of the last commit in the index  */
        var userData: MutableMap<String, String>? = null

        /**
         * Holds the status of each segment in the index. See [.segmentInfos].
         *
         * @lucene.experimental
         */
        class SegmentInfoStatus internal constructor() {
            /** Name of the segment.  */
            var name: String? = null

            /** Codec used to read this segment.  */
            var codec: Codec? = null

            /** Document count (does not take deletions into account).  */
            var maxDoc: Int = 0

            /** True if segment is compound file format.  */
            var compound: Boolean = false

            /** Number of files referenced by this segment.  */
            var numFiles: Int = 0

            /** Net size (MB) of the files referenced by this segment.  */
            var sizeMB: Double = 0.0

            /** True if this segment has pending deletions.  */
            var hasDeletions: Boolean = false

            /** Current deletions generation.  */
            var deletionsGen: Long = 0

            /** True if we were able to open a CodecReader on this segment.  */
            var openReaderPassed: Boolean = false

            /** doc count in this segment  */
            var toLoseDocCount: Int = 0

            /**
             * Map that includes certain debugging details that IndexWriter records into each segment it
             * creates
             */
            var diagnostics: MutableMap<String, String>? = null

            /** Status for testing of livedocs  */
            var liveDocStatus: LiveDocStatus? = null

            /** Status for testing of field infos  */
            var fieldInfoStatus: FieldInfoStatus? = null

            /** Status for testing of field norms (null if field norms could not be tested).  */
            var fieldNormStatus: FieldNormStatus? = null

            /** Status for testing of indexed terms (null if indexed terms could not be tested).  */
            var termIndexStatus: TermIndexStatus? = null

            /** Status for testing of stored fields (null if stored fields could not be tested).  */
            var storedFieldStatus: StoredFieldStatus? = null

            /** Status for testing of term vectors (null if term vectors could not be tested).  */
            var termVectorStatus: TermVectorStatus? = null

            /** Status for testing of DocValues (null if DocValues could not be tested).  */
            var docValuesStatus: DocValuesStatus? = null

            /** Status for testing of PointValues (null if PointValues could not be tested).  */
            var pointsStatus: PointsStatus? = null

            /** Status of index sort  */
            var indexSortStatus: IndexSortStatus? = null

            /** Status of vectors  */
            var vectorValuesStatus: VectorValuesStatus? = null

            /** Status of HNSW graph  */
            var hnswGraphsStatus: HnswGraphsStatus? = null

            /** Status of soft deletes  */
            var softDeletesStatus: SoftDeletesStatus? = null

            /** Exception thrown during segment test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing livedocs  */
        class LiveDocStatus() {
            /** Number of deleted documents.  */
            var numDeleted: Int = 0

            /** Exception thrown during term index test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing field infos.  */
        class FieldInfoStatus() {
            /** Number of fields successfully tested  */
            var totFields: Long = 0L

            /** Exception thrown during term index test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing field norms.  */
        class FieldNormStatus() {
            /** Number of fields successfully tested  */
            var totFields: Long = 0L

            /** Exception thrown during term index test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing term index.  */
        class TermIndexStatus internal constructor() {
            /** Number of terms with at least one live doc.  */
            var termCount: Long = 0L

            /** Number of terms with zero live docs.  */
            var delTermCount: Long = 0L

            /** Total frequency across all terms.  */
            var totFreq: Long = 0L

            /** Total number of positions.  */
            var totPos: Long = 0L

            /** Exception thrown during term index test (null on success)  */
            var error: Throwable? = null

            /**
             * Holds details of block allocations in the block tree terms dictionary (this is only set if
             * the [PostingsFormat] for this segment uses block tree).
             */
            var blockTreeStats: MutableMap<String, Any>? = null
        }

        /** Status from testing stored fields.  */
        class StoredFieldStatus internal constructor() {
            /** Number of documents tested.  */
            var docCount: Int = 0

            /** Total number of stored fields tested.  */
            var totFields: Long = 0

            /** Exception thrown during stored fields test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing stored fields.  */
        class TermVectorStatus internal constructor() {
            /** Number of documents tested.  */
            var docCount: Int = 0

            /** Total number of term vectors tested.  */
            var totVectors: Long = 0

            /** Exception thrown during term vector test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing DocValues  */
        class DocValuesStatus internal constructor() {
            /** Total number of docValues tested.  */
            var totalValueFields: Long = 0

            /** Total number of numeric fields  */
            var totalNumericFields: Long = 0

            /** Total number of binary fields  */
            var totalBinaryFields: Long = 0

            /** Total number of sorted fields  */
            var totalSortedFields: Long = 0

            /** Total number of sortednumeric fields  */
            var totalSortedNumericFields: Long = 0

            /** Total number of sortedset fields  */
            var totalSortedSetFields: Long = 0

            /** Total number of skipping index tested.  */
            var totalSkippingIndex: Long = 0

            /** Exception thrown during doc values test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing PointValues  */
        class PointsStatus internal constructor() {
            /** Total number of values points tested.  */
            var totalValuePoints: Long = 0

            /** Total number of fields with points.  */
            var totalValueFields: Int = 0

            /** Exception thrown during point values test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing vector values  */
        class VectorValuesStatus internal constructor() {
            /** Total number of vector values tested.  */
            var totalVectorValues: Long = 0

            /** Total number of fields with vectors.  */
            var totalKnnVectorFields: Int = 0

            /** Exception thrown during vector values test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing a single HNSW graph  */
        class HnswGraphStatus internal constructor() {
            /** Number of nodes at each level  */
            var numNodesAtLevel: Array<Int>? = null

            /** Connectedness at each level represented as a fraction  */
            var connectednessAtLevel: Array<String>? = null
        }

        /** Status from testing all HNSW graphs  */
        class HnswGraphsStatus internal constructor() {
            /** Status of the HNSW graph keyed with field name  */
            var hnswGraphsStatusByField: MutableMap<String, HnswGraphStatus> = HashMap()

            /** Exception thrown during term index test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing index sort  */
        class IndexSortStatus internal constructor() {
            /** Exception thrown during term index test (null on success)  */
            var error: Throwable? = null
        }

        /** Status from testing soft deletes  */
        class SoftDeletesStatus internal constructor() {
            /** Exception thrown during soft deletes test (null on success)  */
            var error: Throwable? = null
        }
    }

    private fun ensureOpen() {
        if (closed) {
            throw AlreadyClosedException("this instance is closed")
        }
    }

    override fun close() {
        closed = true
        IOUtils.close(writeLock)
    }

    private var level = 0

    /**
     * Sets Level, the higher the value, the more additional checks are performed. This will likely
     * drastically increase time it takes to run CheckIndex! See [Level]
     */
    fun setLevel(v: Int) {
        Level.checkIfLevelInBounds(v)
        level = v
    }

    /** See [.setLevel].  */
    fun getLevel(): Int {
        return level
    }

    /** See [.setFailFast].  */
    /**
     * If true, just throw the original exception immediately when corruption is detected, rather than
     * continuing to iterate to other segments looking for more corruption.
     */
    var failFast: Boolean = false

    private var verbose = false

    /** Set threadCount used for parallelizing index integrity checking.  */
    fun setThreadCount(tc: Int) {
        require(tc > 0) { "setThreadCount requires a number larger than 0, but got: $tc" }
        threadCount = tc
    }

    private var threadCount: Int = /*java.lang.Runtime.getRuntime().availableProcessors()*/ 1

    /**
     * Set infoStream where messages should go. If null, no messages are printed. If verbose is true
     * then more details are printed.
     */
    fun setInfoStream(out: PrintStream, verbose: Boolean) {
        infoStream = out
        this.verbose = verbose
    }

    /** Set infoStream where messages should go. See [.setInfoStream].  */
    fun setInfoStream(out: PrintStream) {
        setInfoStream(out, false)
    }

    /**
     * Returns a [Status] instance detailing the state of the index.
     *
     * @param onlySegments list of specific segment names to check
     *
     * As this method checks every byte in the specified segments, on a large index it can take
     * quite a long time to run.
     */
    /**
     * Returns a [Status] instance detailing the state of the index.
     *
     *
     * As this method checks every byte in the index, on a large index it can take quite a long
     * time to run.
     *
     *
     * **WARNING**: make sure you only call this when the index is not opened by any writer.
     */
    @Throws(IOException::class)
    fun checkIndex(onlySegments: MutableList<String>? = null): Status {
        var executorService: ExecutorService? = null

        // if threadCount == 1, then no executor is created and use the main thread to do index checking
        // sequentially

        // TODO for now skip this feature, implement if needed
        /*if (threadCount > 1) {
            executorService =
                Executors.newFixedThreadPool(
                    threadCount,
                    NamedThreadFactory("async-check-index")
                )
        }*/

        msg(infoStream!!, "Checking index with threadCount: $threadCount")
        try {
            return checkIndex(onlySegments!!, executorService!!)
        } finally {
            if (executorService != null) {

                executorService.shutdown()

                runBlocking {
                    try {
                        executorService.awaitTermination(5, TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        msg(
                            infoStream!!,
                            "ERROR: Interrupted exception occurred when shutting down executor service"
                        )
                        if (infoStream != null) e.printStackTrace(infoStream!!)
                    } finally {
                        executorService.shutdownNow()
                    }

                }
            }
        }
    }

    /**
     * Returns a [Status] instance detailing the state of the index.
     *
     *
     * This method allows caller to pass in customized ExecutorService to speed up the check.
     *
     *
     * **WARNING**: make sure you only call this when the index is not opened by any writer.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Throws(IOException::class)
    fun checkIndex(
        onlySegments: MutableList<String>,
        executorService: ExecutorService
    ): Status {
        ensureOpen()
        val startNS: Long = System.nanoTime()

        val result = Status()
        result.dir = dir
        val files: Array<String> = dir.listAll()
        val lastSegmentsFile: String? = SegmentInfos.getLastCommitSegmentsFileName(files)
        if (lastSegmentsFile == null) {
            throw IndexNotFoundException(
                "no segments* file found in " + dir + ": files: " + files.contentToString()
            )
        }

        // https://github.com/apache/lucene/issues/7820: also attempt to open any older commit
        // points (segments_N), which will catch certain corruption like missing _N.si files
        // for segments not also referenced by the newest commit point (which was already
        // loaded, successfully, above).  Note that we do not do a deeper check of segments
        // referenced ONLY by these older commit points, because such corruption would not
        // prevent a new IndexWriter from opening on the newest commit point.  but it is still
        // corruption, e.g. a reader opened on those old commit points can hit corruption
        // exceptions which we (still) will not detect here.  progress not perfection!
        var lastCommit: SegmentInfos? = null

        val allSegmentsFiles: MutableList<String> = mutableListOf()
        for (fileName in files) {
            if (fileName.startsWith(IndexFileNames.SEGMENTS) && fileName != SegmentInfos.OLD_SEGMENTS_GEN
            ) {
                allSegmentsFiles.add(fileName)
            }
        }

        // Sort descending by generation so that we always attempt to read the last commit first.  This
        // way if an index has a broken last commit AND a broken old commit, we report the last commit
        // error first:
        allSegmentsFiles.sort { a: String, b: String ->
            val genA: Long = SegmentInfos.generationFromSegmentsFileName(a)
            val genB: Long = SegmentInfos.generationFromSegmentsFileName(b)
            -Long.compare(genA, genB)
        }

        for (fileName in allSegmentsFiles) {
            val isLastCommit = fileName == lastSegmentsFile

            val infos: SegmentInfos

            try {
                // Do not use SegmentInfos.read(Directory) since the spooky
                // retrying it does is not necessary here (we hold the write lock):
                // always open old indices if codecs are around
                infos = SegmentInfos.readCommit(dir, fileName, 0)
            } catch (t: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(t)
                }

                val message: String = if (isLastCommit) {
                    ("ERROR: could not read latest commit point from segments file \""
                            + fileName
                            + "\" in directory")
                } else {
                    ("ERROR: could not read old (not latest) commit point segments file \""
                            + fileName
                            + "\" in directory")
                }
                msg(infoStream!!, message)
                result.missingSegments = true
                if (infoStream != null) {
                    t.printStackTrace(infoStream!!)
                }
                return result
            }

            if (isLastCommit) {
                // record the latest commit point: we will deeply check all segments referenced by it
                lastCommit = infos
            }
        }

        // we know there is a lastSegmentsFileName, so we must've attempted to load it in the above for
        // loop.  if it failed to load, we threw the exception (fastFail == true) or we returned the
        // failure (fastFail == false).  so if we get here, we should // always have a valid lastCommit:
        checkNotNull(lastCommit)

        if (lastCommit == null) {
            msg(infoStream!!, "ERROR: could not read any segments file in directory")
            result.missingSegments = true
            return result
        }

        if (infoStream != null) {
            var maxDoc = 0
            var delCount = 0
            for (info in lastCommit) {
                maxDoc += info.info.maxDoc()
                delCount += info.delCount
            }
            infoStream!!.print("${100.0 * delCount / maxDoc}% total deletions; $maxDoc documents; $delCount deletions%n")
        }

        // find the oldest and newest segment versions
        var oldest: Version? = null
        var newest: Version? = null
        var oldSegs: String? = null
        for (si in lastCommit) {
            val version: Version = si.info.version
            if (version == null) {
                // pre-3.1 segment
                oldSegs = "pre-3.1"
            } else {
                if (oldest == null || !version.onOrAfter(oldest)) {
                    oldest = version
                }
                if (newest == null || version.onOrAfter(newest)) {
                    newest = version
                }
            }
        }

        val numSegments: Int = lastCommit.size()
        val segmentsFileName: String? = lastCommit.segmentsFileName
        result.segmentsFileName = segmentsFileName
        result.numSegments = numSegments
        result.userData = lastCommit.userData
        val userDataString: String = if (lastCommit.userData.isNotEmpty()) {
            " userData=" + lastCommit.userData
        } else {
            ""
        }

        var versionString = ""
        if (oldSegs != null) {
            versionString = if (newest != null) {
                "versions=[$oldSegs .. $newest]"
            } else {
                "version=$oldSegs"
            }
        } else if (newest != null) { // implies oldest != null
            versionString =
                if (oldest == newest)
                    ("version=$oldest")
                else
                    ("versions=[$oldest .. $newest]")
        }

        msg(
            infoStream!!,
            ("Segments file="
                    + segmentsFileName
                    + " numSegments="
                    + numSegments
                    + " "
                    + versionString
                    + " id="
                    + StringHelper.idToString(lastCommit.getId())
                    + userDataString)
        )

        if (onlySegments != null) {
            result.partial = true
            if (infoStream != null) {
                infoStream!!.print("\nChecking only these segments:")
                for (s in onlySegments) {
                    infoStream!!.print(" $s")
                }
            }
            result.segmentsChecked.addAll(onlySegments)
            msg(infoStream!!, ":")
        }

        result.newSegments = lastCommit.clone()
        result.newSegments!!.clear()
        result.maxSegmentName = -1

        // checks segments sequentially
        if (executorService == null) {
            for (i in 0..<numSegments) {
                val info: SegmentCommitInfo = lastCommit.info(i)
                updateMaxSegmentName(result, info)
                if (onlySegments != null && !onlySegments.contains(info.info.name)) {
                    continue
                }

                msg(
                    infoStream!!,
                    ((1 + i)
                        .toString() + " of "
                            + numSegments
                            + ": name="
                            + info.info.name
                            + " maxDoc="
                            + info.info.maxDoc())
                )
                val segmentInfoStatus: SegmentInfoStatus = testSegment(lastCommit, info, infoStream!!)

                processSegmentInfoStatusResult(result, info, segmentInfoStatus)
            }
        } else {
            val outputs: Array<ByteArrayOutputStream> =
                kotlin.arrayOfNulls<ByteArrayOutputStream>(numSegments) as Array<ByteArrayOutputStream>
            val futures: Array<CompletableDeferred<SegmentInfoStatus>> =
                kotlin.arrayOfNulls<CompletableDeferred<SegmentInfoStatus>>(numSegments) as Array<CompletableDeferred<SegmentInfoStatus>>

            // checks segments concurrently
            val segmentCommitInfos: MutableList<SegmentCommitInfo> = mutableListOf()
            for (sci in lastCommit) {
                segmentCommitInfos.add(sci)
            }

            // sort segmentCommitInfos by segment size, as smaller segment tends to finish faster, and
            // hence its output can be printed out faster
            segmentCommitInfos.sort { info1: SegmentCommitInfo, info2: SegmentCommitInfo ->
                try {
                    return@sort Long.compare(info1.sizeInBytes(), info2.sizeInBytes())
                } catch (e: IOException) {
                    msg(
                        infoStream!!,
                        "ERROR: IOException occurred when comparing SegmentCommitInfo file sizes"
                    )
                    if (infoStream != null) e.printStackTrace(infoStream!!)
                    return@sort 0
                }
            }

            // start larger segments earlier
            for (i in numSegments - 1 downTo 0) {
                val info: SegmentCommitInfo = segmentCommitInfos[i]
                updateMaxSegmentName(result, info)
                if (onlySegments != null && !onlySegments.contains(info.info.name)) {
                    continue
                }

                val finalSis: SegmentInfos = lastCommit

                val output = ByteArrayOutputStream()
                val stream = PrintStream(output, true, StandardCharsets.UTF_8)
                msg(
                    stream,
                    ((1 + i)
                        .toString() + " of "
                            + numSegments
                            + ": name="
                            + info.info.name
                            + " maxDoc="
                            + info.info.maxDoc())
                )

                outputs[i] = output
                futures[i] =
                    runAsyncSegmentCheck({
                        testSegment(
                            finalSis,
                            info,
                            stream
                        )
                    }, executorService)
            }

            for (i in 0..<numSegments) {
                val info: SegmentCommitInfo = segmentCommitInfos[i]
                if (onlySegments != null && !onlySegments.contains(info.info.name)) {
                    continue
                }

                val output: ByteArrayOutputStream = outputs[i]

                // print segment results in order
                var segmentInfoStatus: SegmentInfoStatus? = null
                try {
                    segmentInfoStatus = futures[i].getCompleted() /*.get()*/
                } catch (e: InterruptedException) {
                    // the segment test output should come before interrupted exception message that follows,
                    // hence it's not emitted from finally clause
                    msg(infoStream!!, output)
                    msg(
                        infoStream!!,
                        "ERROR: Interrupted exception occurred when getting segment check result for segment "
                                + info.info.name
                    )
                    if (infoStream != null) e.printStackTrace(infoStream!!)
                } catch (e: ExecutionException) {
                    msg(infoStream!!, output.toString(StandardCharsets.UTF_8))

                    assert(failFast)
                    throw CheckIndexException(
                        "Segment " + info.info.name + " check failed.", e.cause!!
                    )
                }

                msg(infoStream!!, output)

                processSegmentInfoStatusResult(result, info, segmentInfoStatus!!)
            }
        }

        if (0 == result.numBadSegments) {
            result.clean = true
        } else {
            msg(
                infoStream!!,
                ("WARNING: "
                        + result.numBadSegments
                        + " broken segments (containing "
                        + result.totLoseDocCount
                        + " documents) detected")
            )
        }

        result.validCounter = result.maxSegmentName < lastCommit.counter
        if (!result.validCounter) {
            result.clean = false
            result.newSegments!!.counter = result.maxSegmentName + 1
            msg(
                infoStream!!,
                ("ERROR: Next segment name counter "
                        + lastCommit.counter
                        + " is not greater than max segment name "
                        + result.maxSegmentName)
            )
        }

        if (result.clean) {
            msg(infoStream!!, "No problems were detected with this index.\n")
        }

        msg(infoStream!!, "Took ${nsToSec(System.nanoTime() - startNS)} sec total.")

        return result
    }

    private fun updateMaxSegmentName(result: Status, info: SegmentCommitInfo) {
        val segmentName = info.info.name.substring(1).toLong(Character.MAX_RADIX)
        if (segmentName > result.maxSegmentName) {
            result.maxSegmentName = segmentName
        }
    }

    private fun processSegmentInfoStatusResult(
        result: Status, info: SegmentCommitInfo, segmentInfoStatus: SegmentInfoStatus
    ) {
        result.segmentInfos.add(segmentInfoStatus)
        if (segmentInfoStatus.error != null) {
            result.totLoseDocCount += segmentInfoStatus.toLoseDocCount
            result.numBadSegments++
        } else {
            // Keeper
            result.newSegments!!.add(info.clone())
        }
    }

    // replace JDK CompletableFuture with coroutine CompletableDeferred
    private fun <R> runAsyncSegmentCheck(
        asyncCallable: Callable<R>, executorService: ExecutorService
    ): CompletableDeferred<R> {
        val deferred = CompletableDeferred<R>()
        executorService.execute {
            try {
                deferred.complete(asyncCallable.call())
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            }
        }
        return deferred
    }

    private fun <T> callableToSupplier(callable: Callable<T>): () -> T {
        return {
            try {
                callable.call()
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Error) {
                throw e
            } catch (e: Throwable) {
                throw Exception(e)
            }
        }
    }

    @Throws(IOException::class)
    private fun testSegment(
        sis: SegmentInfos,
        info: SegmentCommitInfo,
        infoStream: PrintStream
    ): SegmentInfoStatus {
        val segInfoStat = SegmentInfoStatus()
        segInfoStat.name = info.info.name
        segInfoStat.maxDoc = info.info.maxDoc()

        val version: Version = info.info.version
        if (info.info.maxDoc() <= 0) {
            throw CheckIndexException(" illegal number of documents: maxDoc=" + info.info.maxDoc())
        }

        var toLoseDocCount: Int = info.info.maxDoc()

        var reader: SegmentReader? = null

        try {
            msg(infoStream, "    version=" + (version ?: "3.0"))
            msg(infoStream, "    id=" + StringHelper.idToString(info.info.getId()))
            val codec: Codec = info.info.codec
            msg(infoStream, "    codec=$codec")
            segInfoStat.codec = codec
            msg(infoStream, "    compound=" + info.info.useCompoundFile)
            segInfoStat.compound = info.info.useCompoundFile
            msg(infoStream, "    numFiles=" + info.files().size)
            val indexSort: Sort? = info.info.indexSort
            if (indexSort != null) {
                msg(infoStream, "    sort=$indexSort")
            }
            segInfoStat.numFiles = info.files().size
            segInfoStat.sizeMB = info.sizeInBytes() / (1024.0 * 1024.0)
            // nf#format is not thread-safe, and would generate random non-valid results in concurrent
            // setting

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(nf) {
            msg(infoStream, "    size (MB)= ${segInfoStat.sizeMB}")
            //}
            val diagnostics: MutableMap<String, String> = info.info.getDiagnostics()
            segInfoStat.diagnostics = diagnostics
            if (diagnostics.isNotEmpty()) {
                msg(infoStream, "    diagnostics = $diagnostics")
            }

            if (!info.hasDeletions()) {
                msg(infoStream, "    no deletions")
                segInfoStat.hasDeletions = false
            } else {
                msg(infoStream, "    has deletions [delGen=" + info.delGen + "]")
                segInfoStat.hasDeletions = true
                segInfoStat.deletionsGen = info.delGen
            }

            val startOpenReaderNS: Long = System.nanoTime()
            if (infoStream != null) infoStream.print("    test: open reader.........")
            reader = SegmentReader(
                info,
                sis.indexCreatedVersionMajor,
                IOContext.DEFAULT
            )
            msg(
                infoStream,
                "OK [took ${nsToSec(System.nanoTime() - startOpenReaderNS)} sec]"
            )

            segInfoStat.openReaderPassed = true

            val startIntegrityNS: Long = System.nanoTime()
            if (infoStream != null) infoStream.print("    test: check integrity.....")
            reader.checkIntegrity()
            msg(
                infoStream,
                "OK [took ${nsToSec(System.nanoTime() - startIntegrityNS)} sec]"
            )

            if (reader.maxDoc() != info.info.maxDoc()) {
                throw CheckIndexException(
                    ("SegmentReader.maxDoc() "
                            + reader.maxDoc()
                            + " != SegmentInfo.maxDoc "
                            + info.info.maxDoc())
                )
            }

            val numDocs: Int = reader.numDocs()
            toLoseDocCount = numDocs

            if (reader.hasDeletions()) {
                if (numDocs != info.info.maxDoc() - info.delCount) {
                    throw CheckIndexException(
                        ("delete count mismatch: info="
                                + (info.info.maxDoc() - info.delCount)
                                + " vs reader="
                                + numDocs)
                    )
                }
                if ((info.info.maxDoc() - numDocs) > reader.maxDoc()) {
                    throw CheckIndexException(
                        ("too many deleted docs: maxDoc()="
                                + reader.maxDoc()
                                + " vs del count="
                                + (info.info.maxDoc() - numDocs))
                    )
                }
                if (info.info.maxDoc() - numDocs != info.delCount) {
                    throw CheckIndexException(
                        ("delete count mismatch: info="
                                + info.delCount
                                + " vs reader="
                                + (info.info.maxDoc() - numDocs))
                    )
                }
            } else {
                if (info.delCount != 0) {
                    throw CheckIndexException(
                        ("delete count mismatch: info="
                                + info.delCount
                                + " vs reader="
                                + (info.info.maxDoc() - numDocs))
                    )
                }
            }
            if (level >= Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS) {
                // Test Livedocs
                segInfoStat.liveDocStatus = testLiveDocs(reader, infoStream, failFast)

                // Test Fieldinfos
                segInfoStat.fieldInfoStatus = testFieldInfos(reader, infoStream, failFast)

                // Test Field Norms
                segInfoStat.fieldNormStatus = testFieldNorms(reader, infoStream, failFast)

                // Test the Term Index
                segInfoStat.termIndexStatus = testPostings(reader, infoStream, verbose, level, failFast)

                // Test Stored Fields
                segInfoStat.storedFieldStatus = testStoredFields(reader, infoStream, failFast)

                // Test Term Vectors
                segInfoStat.termVectorStatus =
                    testTermVectors(reader, infoStream, verbose, level, failFast)

                // Test Docvalues
                segInfoStat.docValuesStatus = testDocValues(reader, infoStream, failFast)

                // Test PointValues
                segInfoStat.pointsStatus = testPoints(reader, infoStream, failFast)

                // Test FloatVectorValues and ByteVectorValues
                segInfoStat.vectorValuesStatus = testVectors(reader, infoStream, failFast)

                // Test HNSW graph
                segInfoStat.hnswGraphsStatus = testHnswGraphs(reader, infoStream, failFast)

                // Test Index Sort
                if (indexSort != null) {
                    segInfoStat.indexSortStatus = testSort(reader, indexSort, infoStream, failFast)
                }

                // Test Soft Deletes
                val softDeletesField: String? = reader.fieldInfos.softDeletesField
                if (softDeletesField != null) {
                    segInfoStat.softDeletesStatus =
                        checkSoftDeletes(softDeletesField, info, reader, infoStream, failFast)
                }

                // Rethrow the first exception we encountered
                //  This will cause stats for failed segments to be incremented properly
                // We won't be able to (easily) stop check running in another thread, so we may as well
                // wait for all of them to complete before we proceed, and that we don't throw
                // CheckIndexException
                // below while the segment part check may still print out messages
                if (segInfoStat.liveDocStatus!!.error != null) {
                    throw CheckIndexException("Live docs test failed", segInfoStat.liveDocStatus!!.error!!)
                } else if (segInfoStat.fieldInfoStatus!!.error!! != null) {
                    throw CheckIndexException(
                        "Field Info test failed", segInfoStat.fieldInfoStatus!!.error!!
                    )
                } else if (segInfoStat.fieldNormStatus!!.error != null) {
                    throw CheckIndexException(
                        "Field Norm test failed", segInfoStat.fieldNormStatus!!.error!!
                    )
                } else if (segInfoStat.termIndexStatus!!.error != null) {
                    throw CheckIndexException(
                        "Term Index test failed", segInfoStat.termIndexStatus!!.error!!
                    )
                } else if (segInfoStat.storedFieldStatus!!.error != null) {
                    throw CheckIndexException(
                        "Stored Field test failed", segInfoStat.storedFieldStatus!!.error!!
                    )
                } else if (segInfoStat.termVectorStatus!!.error!! != null) {
                    throw CheckIndexException(
                        "Term Vector test failed", segInfoStat.termVectorStatus!!.error!!
                    )
                } else if (segInfoStat.docValuesStatus!!.error!! != null) {
                    throw CheckIndexException("DocValues test failed", segInfoStat.docValuesStatus!!.error!!)
                } else if (segInfoStat.pointsStatus!!.error!! != null) {
                    throw CheckIndexException("Points test failed", segInfoStat.pointsStatus!!.error!!)
                } else if (segInfoStat.vectorValuesStatus!!.error!! != null) {
                    throw CheckIndexException(
                        "Vectors test failed", segInfoStat.vectorValuesStatus!!.error!!
                    )
                } else if (segInfoStat.indexSortStatus != null
                    && segInfoStat.indexSortStatus!!.error!! != null
                ) {
                    throw CheckIndexException(
                        "Index Sort test failed", segInfoStat.indexSortStatus!!.error!!
                    )
                } else if (segInfoStat.softDeletesStatus != null
                    && segInfoStat.softDeletesStatus!!.error != null
                ) {
                    throw CheckIndexException(
                        "Soft Deletes test failed", segInfoStat.softDeletesStatus!!.error!!
                    )
                }
            }

            msg(infoStream, "")
        } catch (t: Throwable) {
            if (failFast) {
                throw IOUtils.rethrowAlways(t)
            }
            segInfoStat.error = t
            segInfoStat.toLoseDocCount = toLoseDocCount
            msg(infoStream, "FAILED")
            val comment: String = "exorciseIndex() would remove reference to this segment"
            msg(infoStream, "    WARNING: $comment; full exception:")
            if (infoStream != null) t.printStackTrace(infoStream)
            msg(infoStream, "")
        } finally {
            if (reader != null) reader.close()
        }
        return segInfoStat
    }

    /**
     * Walks the entire N-dimensional points space, verifying that all points fall within the last
     * cell's boundaries.
     *
     * @lucene.internal
     */
    class VerifyPointsVisitor(
        private val fieldName: String,
        maxDoc: Int,
        values: PointValues
    ) : IntersectVisitor {
        /** Returns total number of points in this BKD tree  */
        var pointCountSeen: Long = 0
            private set
        private var lastDocID = -1
        private val docsSeen: FixedBitSet
        private val lastMinPackedValue: ByteArray
        private val lastMaxPackedValue: ByteArray
        private val lastPackedValue: ByteArray
        private val globalMinPackedValue: ByteArray
        private val globalMaxPackedValue: ByteArray
        private val packedBytesCount: Int
        private val packedIndexBytesCount: Int
        private val numDataDims: Int = values.numDimensions
        private val numIndexDims: Int = values.numIndexDimensions
        private val bytesPerDim: Int = values.bytesPerDimension
        private val comparator: ByteArrayComparator = ArrayUtil.getUnsignedComparator(bytesPerDim)

        /** Sole constructor  */
        init {
            packedBytesCount = numDataDims * bytesPerDim
            packedIndexBytesCount = numIndexDims * bytesPerDim
            globalMinPackedValue = values.minPackedValue
            globalMaxPackedValue = values.maxPackedValue
            docsSeen = FixedBitSet(maxDoc)
            lastMinPackedValue = ByteArray(packedIndexBytesCount)
            lastMaxPackedValue = ByteArray(packedIndexBytesCount)
            lastPackedValue = ByteArray(packedBytesCount)

            if (values.docCount > values.size()) {
                throw CheckIndexException(
                    ("point values for field \""
                            + fieldName
                            + "\" claims to have size="
                            + values.size()
                            + " points and inconsistent docCount="
                            + values.docCount)
                )
            }

            if (values.docCount > maxDoc) {
                throw CheckIndexException(
                    ("point values for field \""
                            + fieldName
                            + "\" claims to have docCount="
                            + values.docCount
                            + " but that's greater than maxDoc="
                            + maxDoc)
                )
            }

            if (globalMinPackedValue == null) {
                if (values.size() != 0L) {
                    throw CheckIndexException(
                        ("getMinPackedValue is null points for field \""
                                + fieldName
                                + "\" yet size="
                                + values.size())
                    )
                }
            } else if (globalMinPackedValue.size != packedIndexBytesCount) {
                throw CheckIndexException(
                    ("getMinPackedValue for field \""
                            + fieldName
                            + "\" return length="
                            + globalMinPackedValue.size
                            + " array, but should be "
                            + packedBytesCount)
                )
            }
            if (globalMaxPackedValue == null) {
                if (values.size() != 0L) {
                    throw CheckIndexException(
                        ("getMaxPackedValue is null points for field \""
                                + fieldName
                                + "\" yet size="
                                + values.size())
                    )
                }
            } else if (globalMaxPackedValue.size != packedIndexBytesCount) {
                throw CheckIndexException(
                    ("getMaxPackedValue for field \""
                            + fieldName
                            + "\" return length="
                            + globalMaxPackedValue.size
                            + " array, but should be "
                            + packedBytesCount)
                )
            }
        }

        val docCountSeen: Long
            /** Returns total number of unique docIDs in this BKD tree  */
            get() = docsSeen.cardinality().toLong()

        override fun visit(docID: Int) {
            throw CheckIndexException(
                "codec called IntersectVisitor.visit without a packed value for docID=$docID"
            )
        }

        override fun visit(docID: Int, packedValue: ByteArray) {
            checkPackedValue("packed value", packedValue, docID)
            pointCountSeen++
            docsSeen.set(docID)

            for (dim in 0..<numIndexDims) {
                val offset = bytesPerDim * dim

                // Compare to last cell:
                if (comparator.compare(packedValue, offset, lastMinPackedValue, offset) < 0) {
                    // This doc's point, in this dimension, is lower than the minimum value of the last cell
                    // checked:
                    throw CheckIndexException(
                        ("packed points value "
                                + packedValue.contentToString() + " for field=\""
                                + fieldName
                                + "\", docID="
                                + docID
                                + " is out-of-bounds of the last cell min="
                                + lastMinPackedValue.contentToString() + " max="
                                + lastMaxPackedValue.contentToString() + " dim="
                                + dim)
                    )
                }

                if (comparator.compare(packedValue, offset, lastMaxPackedValue, offset) > 0) {
                    // This doc's point, in this dimension, is greater than the maximum value of the last cell
                    // checked:
                    throw CheckIndexException(
                        ("packed points value "
                                + packedValue.contentToString() + " for field=\""
                                + fieldName
                                + "\", docID="
                                + docID
                                + " is out-of-bounds of the last cell min="
                                + lastMinPackedValue.contentToString() + " max="
                                + lastMaxPackedValue.contentToString() + " dim="
                                + dim)
                    )
                }
            }

            // In the 1D data case, PointValues must make a single in-order sweep through all values, and
            // tie-break by
            // increasing docID:
            // for data dimension > 1, leaves are sorted by the dimension with the lowest cardinality to
            // improve block compression
            if (numDataDims == 1) {
                val cmp: Int = comparator.compare(lastPackedValue, 0, packedValue, 0)
                if (cmp > 0) {
                    throw CheckIndexException(
                        ("packed points value "
                                + packedValue.contentToString() + " for field=\""
                                + fieldName
                                + "\", for docID="
                                + docID
                                + " is out-of-order vs the previous document's value "
                                + lastPackedValue.contentToString())
                    )
                } else if (cmp == 0) {
                    if (docID < lastDocID) {
                        throw CheckIndexException(
                            ("packed points value is the same, but docID="
                                    + docID
                                    + " is out of order vs previous docID="
                                    + lastDocID
                                    + ", field=\""
                                    + fieldName
                                    + "\"")
                        )
                    }
                }
                System.arraycopy(packedValue, 0, lastPackedValue, 0, bytesPerDim)
                lastDocID = docID
            }
        }

        override fun compare(
            minPackedValue: ByteArray,
            maxPackedValue: ByteArray
        ): Relation {
            checkPackedValue("min packed value", minPackedValue, -1)
            System.arraycopy(minPackedValue, 0, lastMinPackedValue, 0, packedIndexBytesCount)
            checkPackedValue("max packed value", maxPackedValue, -1)
            System.arraycopy(maxPackedValue, 0, lastMaxPackedValue, 0, packedIndexBytesCount)

            for (dim in 0..<numIndexDims) {
                val offset = bytesPerDim * dim

                if (comparator.compare(minPackedValue, offset, maxPackedValue, offset) > 0) {
                    throw CheckIndexException(
                        ("packed points cell minPackedValue "
                                + minPackedValue.contentToString() + " is out-of-bounds of the cell's maxPackedValue "
                                + maxPackedValue.contentToString() + " dim="
                                + dim
                                + " field=\""
                                + fieldName
                                + "\"")
                    )
                }

                // Make sure this cell is not outside the global min/max:
                if (comparator.compare(minPackedValue, offset, globalMinPackedValue, offset) < 0) {
                    throw CheckIndexException(
                        ("packed points cell minPackedValue "
                                + minPackedValue.contentToString() + " is out-of-bounds of the global minimum "
                                + globalMinPackedValue.contentToString() + " dim="
                                + dim
                                + " field=\""
                                + fieldName
                                + "\"")
                    )
                }

                if (comparator.compare(maxPackedValue, offset, globalMinPackedValue, offset) < 0) {
                    throw CheckIndexException(
                        ("packed points cell maxPackedValue "
                                + maxPackedValue.contentToString() + " is out-of-bounds of the global minimum "
                                + globalMinPackedValue.contentToString() + " dim="
                                + dim
                                + " field=\""
                                + fieldName
                                + "\"")
                    )
                }

                if (comparator.compare(minPackedValue, offset, globalMaxPackedValue, offset) > 0) {
                    throw CheckIndexException(
                        ("packed points cell minPackedValue "
                                + minPackedValue.contentToString() + " is out-of-bounds of the global maximum "
                                + globalMaxPackedValue.contentToString() + " dim="
                                + dim
                                + " field=\""
                                + fieldName
                                + "\"")
                    )
                }
                if (comparator.compare(maxPackedValue, offset, globalMaxPackedValue, offset) > 0) {
                    throw CheckIndexException(
                        ("packed points cell maxPackedValue "
                                + maxPackedValue.contentToString() + " is out-of-bounds of the global maximum "
                                + globalMaxPackedValue.contentToString() + " dim="
                                + dim
                                + " field=\""
                                + fieldName
                                + "\"")
                    )
                }
            }

            // We always pretend the query shape is so complex that it crosses every cell, so
            // that packedValue is passed for every document
            return Relation.CELL_CROSSES_QUERY
        }

        private fun checkPackedValue(desc: String, packedValue: ByteArray, docID: Int) {
            if (packedValue == null) {
                throw CheckIndexException(
                    "$desc is null for docID=$docID field=\"$fieldName\""
                )
            }

            if (packedValue.size != (if (docID < 0) packedIndexBytesCount else packedBytesCount)) {
                throw CheckIndexException(
                    (desc
                            + " has incorrect length="
                            + packedValue.size
                            + " vs expected="
                            + packedIndexBytesCount
                            + " for docID="
                            + docID
                            + " field=\""
                            + fieldName
                            + "\"")
                )
            }
        }
    }

    private class ConstantRelationIntersectVisitor(val relation: Relation) :
        IntersectVisitor {
        @Throws(IOException::class)
        override fun visit(docID: Int) {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun visit(docID: Int, packedValue: ByteArray) {
            throw UnsupportedOperationException()
        }

        override fun compare(
            minPackedValue: ByteArray,
            maxPackedValue: ByteArray
        ): Relation {
            return relation
        }

    }

    fun interface DocValuesIteratorSupplier {
        @Throws(IOException::class)
        fun get(fi: FieldInfo): DocValuesIterator
    }

    /**
     * Repairs the index using previously returned result from [.checkIndex]. Note that this
     * does not remove any of the unreferenced files after it's done; you must separately open an
     * [IndexWriter], which deletes unreferenced files when it's created.
     *
     *
     * **WARNING**: this writes a new segments file into the index, effectively removing all
     * documents in broken segments from the index. BE CAREFUL.
     */
    @Throws(IOException::class)
    fun exorciseIndex(result: Status) {
        ensureOpen()
        require(!result.partial) { "can only exorcise an index that was fully checked (this status checked a subset of segments)" }
        result.newSegments!!.changed()
        result.newSegments!!.commit(result.dir!!)
    }

    /**
     * Expert: create a directory with the specified lock. This should really not be used except for
     * unit tests!!!! It exists only to support special tests (such as TestIndexWriterExceptions*),
     * that would otherwise be more complicated to debug if they had to close the writer for each
     * check.
     */
    /** Create a new CheckIndex on the directory.  */
    init {
        this.dir = dir
        this.writeLock = writeLock
        this.infoStream = null
    }

    /** Run-time configuration options for CheckIndex commands.  */
    class Options
    /** Sole constructor.  */
    {
        var doExorcise: Boolean = false
        var verbose: Boolean = false
        var level: Int = Level.DEFAULT_VALUE
        var threadCount: Int = 0
        var onlySegments: MutableList<String>? = mutableListOf()

        /** Get the directory containing the index.  */
        var indexPath: String? = null

        /** Get the name of the FSDirectory implementation class to use.  */
        var dirImpl: String? = null
        var out: PrintStream? = null
    }

    /** Class with static variables with information about CheckIndex's -level parameter.  */
    object Level {
        /** Minimum valid level.  */
        const val MIN_VALUE: Int = 1

        /** Maximum valid level.  */
        const val MAX_VALUE: Int = 3

        /** The default level if none is specified.  */
        const val DEFAULT_VALUE: Int = MIN_VALUE

        /** Minimum level required to run checksum checks.  */
        const val MIN_LEVEL_FOR_CHECKSUM_CHECKS: Int = 1

        /** Minimum level required to run integrity checks.  */
        const val MIN_LEVEL_FOR_INTEGRITY_CHECKS: Int = 2

        /** Minimum level required to run slow checks.  */
        const val MIN_LEVEL_FOR_SLOW_CHECKS: Int = 3

        /** Checks if given level value is within the allowed bounds else it raises an Exception.  */
        @Throws(IllegalArgumentException::class)
        fun checkIfLevelInBounds(levelVal: Int) {
            require(levelVal in MIN_VALUE..MAX_VALUE) {
                "ERROR: given value: '$levelVal' for -level option is out of bounds. Please use a value from '$MIN_VALUE'->'$MAX_VALUE'"
            }
        }
    }

    /**
     * Actually perform the index check
     *
     * @param opts The options to use for this check
     * @return 0 iff the index is clean, 1 otherwise
     */
    @Throws(IOException::class, InterruptedException::class)
    fun doCheck(opts: Options): Int {
        setLevel(opts.level)
        setInfoStream(opts.out!!, opts.verbose)
        // user provided thread count via command line argument, overriding the default with user
        // provided value
        if (opts.threadCount > 0) {
            setThreadCount(opts.threadCount)
        }

        val result = checkIndex(opts.onlySegments)

        if (result.missingSegments) {
            return 1
        }

        if (!result.clean) {
            if (!opts.doExorcise) {
                opts.out!!.println(
                    ("WARNING: would write new segments file, and "
                            + result.totLoseDocCount
                            + " documents would be lost, if -exorcise were specified\n")
                )
            } else {
                opts.out!!.println("WARNING: " + result.totLoseDocCount + " documents will be lost\n")
                opts.out!!.println(
                    ("NOTE: will write new segments file in 5 seconds; this will remove "
                            + result.totLoseDocCount
                            + " docs from the index. YOU WILL LOSE DATA. THIS IS YOUR LAST CHANCE TO CTRL+C!")
                )
                runBlocking {
                    repeat(5) { i ->
                        delay(1_000)
                        opts.out!!.println("  ${5 - i}...")
                    }
                }
                opts.out!!.println("Writing...")
                exorciseIndex(result)
                opts.out!!.println("OK")
                opts.out!!.println(
                    "Wrote new segments file \"" + result.newSegments!!.segmentsFileName + "\""
                )
            }
        }
        opts.out!!.println()

        return if (result.clean) 0 else 1
    }

    /**
     * The marker RuntimeException used by CheckIndex APIs when index integrity failure is detected.
     */
    class CheckIndexException : RuntimeException {
        /**
         * Constructs a new CheckIndexException with the error message
         *
         * @param message the detailed error message.
         */
        constructor(message: String) : super(message)

        /**
         * Constructs a new CheckIndexException with the error message, and the root cause
         *
         * @param message the detailed error message.
         * @param cause the underlying cause.
         */
        constructor(message: String, cause: Throwable) : super(message, cause)
    }

    companion object {
        private fun msg(out: PrintStream, msg: ByteArrayOutputStream) {
            if (out != null) {
                out.println(msg.toString(StandardCharsets.UTF_8))
            }
        }

        private fun msg(out: PrintStream, msg: String) {
            if (out != null) {
                out.println(msg)
            }
        }

        /** Tests index sort order.  */
        @Throws(IOException::class)
        fun testSort(
            reader: CodecReader,
            sort: Sort,
            infoStream: PrintStream,
            failFast: Boolean
        ): Status.IndexSortStatus {
            // This segment claims its documents are sorted according to the incoming sort ... let's make
            // sure:

            val startNS: Long = System.nanoTime()

            val status: Status.IndexSortStatus = Status.IndexSortStatus()

            if (sort != null) {
                if (infoStream != null) {
                    infoStream.print("    test: index sort..........")
                }

                val fields: Array<SortField> = sort.sort
                val reverseMul = IntArray(fields.size)
                val comparators: Array<LeafFieldComparator> =
                    kotlin.arrayOfNulls<LeafFieldComparator>(fields.size) as Array<LeafFieldComparator>

                val readerContext = LeafReaderContext(reader)

                for (i in fields.indices) {
                    reverseMul[i] = if (fields[i].reverse) -1 else 1
                    comparators[i] = fields[i].getComparator(1, Pruning.NONE)
                        .getLeafComparator(readerContext)
                }

                try {
                    val metaData: LeafMetaData = reader.metaData
                    val fieldInfos: FieldInfos = reader.fieldInfos
                    check(
                        !(metaData.hasBlocks
                                && fieldInfos.parentField == null && metaData.createdVersionMajor >= Version.LUCENE_10_0_0.major)
                    ) {
                        ("parent field is not set but the index has document blocks and was created with version: "
                                + metaData.createdVersionMajor)
                    }
                    val iter: DocIdSetIterator = if (metaData.hasBlocks && fieldInfos.parentField != null) {
                        reader.getNumericDocValues(fieldInfos.parentField)!!
                    } else {
                        DocIdSetIterator.all(reader.maxDoc())
                    }
                    var prevDoc: Int = iter.nextDoc()
                    var nextDoc: Int
                    while ((iter.nextDoc()
                            .also { nextDoc = it }) != DocIdSetIterator.NO_MORE_DOCS
                    ) {
                        var cmp = 0
                        for (i in comparators.indices) {
                            // TODO: would be better if copy() didn't cause a term lookup in TermOrdVal & co,
                            // the segments are always the same here...
                            comparators[i].copy(0, prevDoc)
                            comparators[i].setBottom(0)
                            cmp = reverseMul[i] * comparators[i].compareBottom(nextDoc)
                            if (cmp != 0) {
                                break
                            }
                        }
                        if (cmp > 0) {
                            throw CheckIndexException(
                                ("segment has indexSort="
                                        + sort
                                        + " but docID="
                                        + (prevDoc)
                                        + " sorts after docID="
                                        + nextDoc)
                            )
                        }
                        prevDoc = nextDoc
                    }
                    msg(
                        infoStream,
                        "OK [took ${nsToSec(System.nanoTime() - startNS)} sec]"
                    )
                } catch (e: Throwable) {
                    if (failFast) {
                        throw IOUtils.rethrowAlways(e)
                    }
                    msg(infoStream, "ERROR [" + e.message + "]")
                    status.error = e
                    if (infoStream != null) {
                        e.printStackTrace(infoStream)
                    }
                }
            }

            return status
        }

        /** Test live docs.  */
        @Throws(IOException::class)
        fun testLiveDocs(
            reader: CodecReader, infoStream: PrintStream, failFast: Boolean
        ): Status.LiveDocStatus {
            val startNS: Long = System.nanoTime()
            val status: Status.LiveDocStatus = Status.LiveDocStatus()

            try {
                if (infoStream != null) infoStream.print("    test: check live docs.....")
                val numDocs: Int = reader.numDocs()
                if (reader.hasDeletions()) {
                    val liveDocs: Bits? = reader.liveDocs
                    if (liveDocs == null) {
                        throw CheckIndexException("segment should have deletions, but liveDocs is null")
                    } else {
                        var numLive = 0
                        for (j in 0..<liveDocs.length()) {
                            if (liveDocs.get(j)) {
                                numLive++
                            }
                        }
                        if (numLive != numDocs) {
                            throw CheckIndexException(
                                "liveDocs count mismatch: info=$numDocs, vs bits=$numLive"
                            )
                        }
                    }

                    status.numDeleted = reader.numDeletedDocs()
                    msg(
                        infoStream,
                        "OK [${status.numDeleted} deleted docs] [took ${nsToSec(System.nanoTime() - startNS)} sec]"
                    )
                } else {
                    val liveDocs: Bits? = reader.liveDocs
                    if (liveDocs != null) {
                        // it's ok for it to be non-null here, as long as none are set right
                        for (j in 0..<liveDocs.length()) {
                            if (!liveDocs.get(j)) {
                                throw CheckIndexException(
                                    "liveDocs mismatch: info says no deletions but doc $j is deleted."
                                )
                            }
                        }
                    }
                    msg(
                        infoStream,
                        "OK [took ${nsToSec(System.nanoTime() - startNS)} sec]"
                    )
                }
            } catch (e: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR [" + e.message + "]")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        /** Test field infos.  */
        @Throws(IOException::class)
        fun testFieldInfos(
            reader: CodecReader, infoStream: PrintStream, failFast: Boolean
        ): Status.FieldInfoStatus {
            val startNS: Long = System.nanoTime()
            val status: Status.FieldInfoStatus = Status.FieldInfoStatus()

            try {
                // Test Field Infos
                if (infoStream != null) {
                    infoStream.print("    test: field infos.........")
                }
                val fieldInfos: FieldInfos = reader.fieldInfos
                for (f in fieldInfos) {
                    f.checkConsistency()
                }
                msg(
                    infoStream,
                    "OK [${fieldInfos.size()} fields] [took ${nsToSec(System.nanoTime() - startNS)} sec]"
                )
                status.totFields = fieldInfos.size().toLong()
            } catch (e: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR [" + e.message + "]")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        /** Test field norms.  */
        @Throws(IOException::class)
        fun testFieldNorms(
            reader: CodecReader, infoStream: PrintStream, failFast: Boolean
        ): Status.FieldNormStatus {
            val startNS: Long = System.nanoTime()
            val status: Status.FieldNormStatus = Status.FieldNormStatus()

            try {
                // Test Field Norms
                if (infoStream != null) {
                    infoStream.print("    test: field norms.........")
                }
                var normsReader: NormsProducer? = reader.normsReader
                if (normsReader != null) {
                    normsReader = normsReader.mergeInstance
                }
                for (info in reader.fieldInfos) {
                    if (info.hasNorms()) {
                        checkNumericDocValues(info.name, normsReader!!.getNorms(info), normsReader.getNorms(info))
                        ++status.totFields
                    }
                }

                msg(
                    infoStream,
                    "OK [${status.totFields} fields] [took ${nsToSec(System.nanoTime() - startNS)} sec]"
                )
            } catch (e: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR [" + e.message + "]")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        /**
         * checks Fields api is consistent with itself. searcher is optional, to verify with queries. Can
         * be null.
         */
        @Throws(IOException::class)
        private fun checkFields(
            fields: Fields,
            liveDocs: Bits?,
            maxDoc: Int,
            fieldInfos: FieldInfos,
            normsProducer: NormsProducer?,
            doPrint: Boolean,
            isVectors: Boolean,
            infoStream: PrintStream,
            verbose: Boolean,
            level: Int
        ): Status.TermIndexStatus {
            // TODO: we should probably return our own stats thing...!
            val startNS: Long = if (doPrint) {
                System.nanoTime()
            } else {
                0
            }

            val status: Status.TermIndexStatus = Status.TermIndexStatus()
            var computedFieldCount = 0

            var postings: PostingsEnum? = null

            var lastField: String? = null
            for (field in fields) {
                // MultiFieldsEnum relies upon this order...

                if (lastField != null && field.compareTo(lastField) <= 0) {
                    throw CheckIndexException(
                        "fields out of order: lastField=$lastField field=$field"
                    )
                }
                lastField = field

                // check that the field is in fieldinfos, and is indexed.
                // TODO: add a separate test to check this for different reader impls
                val fieldInfo: FieldInfo? = fieldInfos.fieldInfo(field)
                if (fieldInfo == null) {
                    throw CheckIndexException(
                        "fieldsEnum inconsistent with fieldInfos, no fieldInfos for: $field"
                    )
                }
                if (fieldInfo.indexOptions == IndexOptions.NONE) {
                    throw CheckIndexException(
                        "fieldsEnum inconsistent with fieldInfos, isIndexed == false for: $field"
                    )
                }

                // TODO: really the codec should not return a field
                // from FieldsEnum if it has no Terms... but we do
                // this today:
                // assert fields.terms(field) != null;
                computedFieldCount++

                val terms: Terms? = fields.terms(field)
                if (terms == null) {
                    continue
                }

                if (terms.docCount > maxDoc) {
                    throw CheckIndexException(
                        ("docCount > maxDoc for field: "
                                + field
                                + ", docCount="
                                + terms.docCount
                                + ", maxDoc="
                                + maxDoc)
                    )
                }

                val hasFreqs: Boolean = terms.hasFreqs()
                val hasPositions: Boolean = terms.hasPositions()
                val hasPayloads: Boolean = terms.hasPayloads()
                val hasOffsets: Boolean = terms.hasOffsets()

                val maxTerm: BytesRef?
                val minTerm: BytesRef?
                if (isVectors) {
                    // Term vectors impls can be very slow for getMax
                    maxTerm = null
                    minTerm = null
                } else {
                    var bb: BytesRef? = terms.min
                    if (bb != null) {
                        assert(bb.isValid())
                        minTerm = BytesRef.deepCopyOf(bb)
                    } else {
                        minTerm = null
                    }

                    bb = terms.max
                    if (bb != null) {
                        assert(bb.isValid())
                        maxTerm = BytesRef.deepCopyOf(bb)
                        if (minTerm == null) {
                            throw CheckIndexException(
                                "field \"$field\" has null minTerm but non-null maxTerm"
                            )
                        }
                    } else {
                        maxTerm = null
                        if (minTerm != null) {
                            throw CheckIndexException(
                                "field \"$field\" has non-null minTerm but null maxTerm"
                            )
                        }
                    }
                }

                // term vectors cannot omit TF:
                val expectedHasFreqs =
                    (isVectors || fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS)

                if (hasFreqs != expectedHasFreqs) {
                    throw CheckIndexException(
                        ("field \""
                                + field
                                + "\" should have hasFreqs="
                                + expectedHasFreqs
                                + " but got "
                                + hasFreqs)
                    )
                }

                if (!isVectors) {
                    val expectedHasPositions =
                        fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                    if (hasPositions != expectedHasPositions) {
                        throw CheckIndexException(
                            ("field \""
                                    + field
                                    + "\" should have hasPositions="
                                    + expectedHasPositions
                                    + " but got "
                                    + hasPositions)
                        )
                    }

                    val expectedHasPayloads: Boolean = fieldInfo.hasPayloads()
                    if (hasPayloads != expectedHasPayloads) {
                        throw CheckIndexException(
                            ("field \""
                                    + field
                                    + "\" should have hasPayloads="
                                    + expectedHasPayloads
                                    + " but got "
                                    + hasPayloads)
                        )
                    }

                    val expectedHasOffsets =
                        (fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
                    if (hasOffsets != expectedHasOffsets) {
                        throw CheckIndexException(
                            ("field \""
                                    + field
                                    + "\" should have hasOffsets="
                                    + expectedHasOffsets
                                    + " but got "
                                    + hasOffsets)
                        )
                    }
                }

                val termsEnum: TermsEnum = terms.iterator()

                var hasOrd = true
                val termCountStart: Long = status.delTermCount + status.termCount

                var lastTerm: BytesRefBuilder? = null

                var sumTotalTermFreq: Long = 0
                var sumDocFreq: Long = 0
                val visitedDocs = FixedBitSet(maxDoc)
                while (true) {
                    val term: BytesRef? = termsEnum.next()
                    if (term == null) {
                        break
                    }

                    // System.out.println("CI: field=" + field + " check term=" + term + " docFreq=" + termsEnum.docFreq());
                    assert(term.isValid())

                    // make sure terms arrive in order according to
                    // the comp
                    if (lastTerm == null) {
                        lastTerm = BytesRefBuilder()
                        lastTerm.copyBytes(term)
                    } else {
                        if (lastTerm.get() >= term) {
                            throw CheckIndexException(
                                "terms out of order: lastTerm=" + lastTerm.get() + " term=" + term
                            )
                        }
                        lastTerm.copyBytes(term)
                    }

                    if (!isVectors) {
                        if (minTerm == null) {
                            // We checked this above:
                            assert(maxTerm == null)
                            throw CheckIndexException(
                                "field=\"$field\": invalid term: term=$term, minTerm=$minTerm"
                            )
                        }

                        if (term < minTerm) {
                            throw CheckIndexException(
                                "field=\"$field\": invalid term: term=$term, minTerm=$minTerm"
                            )
                        }

                        if (term > maxTerm!!) {
                            throw CheckIndexException(
                                "field=\"$field\": invalid term: term=$term, maxTerm=$maxTerm"
                            )
                        }
                    }

                    val docFreq: Int = termsEnum.docFreq()
                    if (docFreq <= 0) {
                        throw CheckIndexException("docfreq: $docFreq is out of bounds")
                    }
                    sumDocFreq += docFreq.toLong()

                    postings = termsEnum.postings(postings, PostingsEnum.ALL.toInt())

                    if (!hasFreqs) {
                        if (termsEnum.totalTermFreq() != termsEnum.docFreq().toLong()) {
                            throw CheckIndexException(
                                ("field \""
                                        + field
                                        + "\" hasFreqs is false, but TermsEnum.totalTermFreq()="
                                        + termsEnum.totalTermFreq()
                                        + " (should be "
                                        + termsEnum.docFreq()
                                        + ")")
                            )
                        }
                    }

                    if (hasOrd) {
                        var ord: Long = -1
                        try {
                            ord = termsEnum.ord()
                        } catch (uoe: UnsupportedOperationException) {
                            hasOrd = false
                        }

                        if (hasOrd) {
                            val ordExpected: Long = status.delTermCount + status.termCount - termCountStart
                            if (ord != ordExpected) {
                                throw CheckIndexException(
                                    "ord mismatch: TermsEnum has ord=$ord vs actual=$ordExpected"
                                )
                            }
                        }
                    }

                    var lastDoc = -1
                    var docCount = 0
                    var hasNonDeletedDocs = false
                    var totalTermFreq: Long = 0
                    while (true) {
                        val doc: Int = postings.nextDoc()
                        if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                            break
                        }
                        visitedDocs.set(doc)
                        val freq: Int = postings.freq()
                        if (freq <= 0) {
                            throw CheckIndexException(
                                "term $term: doc $doc: freq $freq is out of bounds"
                            )
                        }
                        if (!hasFreqs) {
                            // When a field didn't index freq, it must
                            // consistently "lie" and pretend that freq was
                            // 1:
                            if (postings.freq() != 1) {
                                throw CheckIndexException(
                                    ("term "
                                            + term
                                            + ": doc "
                                            + doc
                                            + ": freq "
                                            + freq
                                            + " != 1 when Terms.hasFreqs() is false")
                                )
                            }
                        }
                        totalTermFreq += freq.toLong()

                        if (liveDocs == null || liveDocs.get(doc)) {
                            hasNonDeletedDocs = true
                            status.totFreq++
                            if (freq >= 0) {
                                status.totPos += freq.toLong()
                            }
                        }
                        docCount++

                        if (doc <= lastDoc) {
                            throw CheckIndexException(
                                "term $term: doc $doc <= lastDoc $lastDoc"
                            )
                        }
                        if (doc >= maxDoc) {
                            throw CheckIndexException("term $term: doc $doc >= maxDoc $maxDoc")
                        }

                        lastDoc = doc

                        var lastPos = -1
                        var lastOffset = 0
                        if (hasPositions) {
                            for (j in 0..<freq) {
                                val pos: Int = postings.nextPosition()

                                if (pos < 0) {
                                    throw CheckIndexException(
                                        "term $term: doc $doc: pos $pos is out of bounds"
                                    )
                                }
                                if (pos > IndexWriter.MAX_POSITION) {
                                    throw CheckIndexException(
                                        ("term "
                                                + term
                                                + ": doc "
                                                + doc
                                                + ": pos "
                                                + pos
                                                + " > IndexWriter.MAX_POSITION="
                                                + IndexWriter.MAX_POSITION)
                                    )
                                }
                                if (pos < lastPos) {
                                    throw CheckIndexException(
                                        "term $term: doc $doc: pos $pos < lastPos $lastPos"
                                    )
                                }
                                lastPos = pos
                                val payload: BytesRef? = postings.payload
                                if (payload != null) {
                                    assert(payload.isValid())
                                }
                                if (payload != null && payload.length < 1) {
                                    throw CheckIndexException(
                                        ("term "
                                                + term
                                                + ": doc "
                                                + doc
                                                + ": pos "
                                                + pos
                                                + " payload length is out of bounds "
                                                + payload.length)
                                    )
                                }
                                if (hasOffsets) {
                                    val startOffset: Int = postings.startOffset()
                                    val endOffset: Int = postings.endOffset()
                                    if (startOffset < 0) {
                                        throw CheckIndexException(
                                            ("term "
                                                    + term
                                                    + ": doc "
                                                    + doc
                                                    + ": pos "
                                                    + pos
                                                    + ": startOffset "
                                                    + startOffset
                                                    + " is out of bounds")
                                        )
                                    }
                                    if (startOffset < lastOffset) {
                                        throw CheckIndexException(
                                            ("term "
                                                    + term
                                                    + ": doc "
                                                    + doc
                                                    + ": pos "
                                                    + pos
                                                    + ": startOffset "
                                                    + startOffset
                                                    + " < lastStartOffset "
                                                    + lastOffset
                                                    + "; consider using the FixBrokenOffsets tool in Lucene's backward-codecs module to correct your index")
                                        )
                                    }
                                    if (endOffset < 0) {
                                        throw CheckIndexException(
                                            ("term "
                                                    + term
                                                    + ": doc "
                                                    + doc
                                                    + ": pos "
                                                    + pos
                                                    + ": endOffset "
                                                    + endOffset
                                                    + " is out of bounds")
                                        )
                                    }
                                    if (endOffset < startOffset) {
                                        throw CheckIndexException(
                                            ("term "
                                                    + term
                                                    + ": doc "
                                                    + doc
                                                    + ": pos "
                                                    + pos
                                                    + ": endOffset "
                                                    + endOffset
                                                    + " < startOffset "
                                                    + startOffset)
                                        )
                                    }
                                    lastOffset = startOffset
                                }
                            }
                        }
                    }

                    if (hasNonDeletedDocs) {
                        status.termCount++
                    } else {
                        status.delTermCount++
                    }

                    val totalTermFreq2: Long = termsEnum.totalTermFreq()

                    if (docCount != docFreq) {
                        throw CheckIndexException(
                            "term $term docFreq=$docFreq != tot docs w/o deletions $docCount"
                        )
                    }
                    if (docFreq > terms.docCount) {
                        throw CheckIndexException(
                            "term " + term + " docFreq=" + docFreq + " > docCount=" + terms.docCount
                        )
                    }
                    if (totalTermFreq2 <= 0) {
                        throw CheckIndexException("totalTermFreq: $totalTermFreq2 is out of bounds")
                    }
                    sumTotalTermFreq += totalTermFreq
                    if (totalTermFreq != totalTermFreq2) {
                        throw CheckIndexException(
                            ("term "
                                    + term
                                    + " totalTermFreq="
                                    + totalTermFreq2
                                    + " != recomputed totalTermFreq="
                                    + totalTermFreq)
                        )
                    }
                    if (totalTermFreq2 < docFreq) {
                        throw CheckIndexException(
                            "totalTermFreq: $totalTermFreq2 is out of bounds, docFreq=$docFreq"
                        )
                    }

                    // Test skipping
                    if (hasPositions) {
                        for (idx in 0..6) {
                            val skipDocID = (((idx + 1) * maxDoc.toLong()) / 8).toInt()
                            postings = termsEnum.postings(postings, PostingsEnum.ALL.toInt())
                            val docID: Int = postings.advance(skipDocID)
                            if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                                break
                            } else {
                                if (docID < skipDocID) {
                                    throw CheckIndexException(
                                        "term $term: advance(docID=$skipDocID) returned docID=$docID"
                                    )
                                }
                                val freq: Int = postings.freq()
                                if (freq <= 0) {
                                    throw CheckIndexException("termFreq $freq is out of bounds")
                                }
                                var lastPosition = -1
                                var lastOffset = 0
                                for (posUpto in 0..<freq) {
                                    val pos: Int = postings.nextPosition()

                                    if (pos < 0) {
                                        throw CheckIndexException("position $pos is out of bounds")
                                    }
                                    if (pos < lastPosition) {
                                        throw CheckIndexException(
                                            "position $pos is < lastPosition $lastPosition"
                                        )
                                    }
                                    lastPosition = pos
                                    if (hasOffsets) {
                                        val startOffset: Int = postings.startOffset()
                                        val endOffset: Int = postings.endOffset()
                                        // NOTE: we cannot enforce any bounds whatsoever on vectors... they were a
                                        // free-for-all before
                                        // but for offsets in the postings lists these checks are fine: they were always
                                        // enforced by IndexWriter
                                        if (!isVectors) {
                                            if (startOffset < 0) {
                                                throw CheckIndexException(
                                                    ("term "
                                                            + term
                                                            + ": doc "
                                                            + docID
                                                            + ": pos "
                                                            + pos
                                                            + ": startOffset "
                                                            + startOffset
                                                            + " is out of bounds")
                                                )
                                            }
                                            if (startOffset < lastOffset) {
                                                throw CheckIndexException(
                                                    ("term "
                                                            + term
                                                            + ": doc "
                                                            + docID
                                                            + ": pos "
                                                            + pos
                                                            + ": startOffset "
                                                            + startOffset
                                                            + " < lastStartOffset "
                                                            + lastOffset)
                                                )
                                            }
                                            if (endOffset < 0) {
                                                throw CheckIndexException(
                                                    ("term "
                                                            + term
                                                            + ": doc "
                                                            + docID
                                                            + ": pos "
                                                            + pos
                                                            + ": endOffset "
                                                            + endOffset
                                                            + " is out of bounds")
                                                )
                                            }
                                            if (endOffset < startOffset) {
                                                throw CheckIndexException(
                                                    ("term "
                                                            + term
                                                            + ": doc "
                                                            + docID
                                                            + ": pos "
                                                            + pos
                                                            + ": endOffset "
                                                            + endOffset
                                                            + " < startOffset "
                                                            + startOffset)
                                                )
                                            }
                                        }
                                        lastOffset = startOffset
                                    }
                                }

                                val nextDocID: Int = postings.nextDoc()
                                if (nextDocID == DocIdSetIterator.NO_MORE_DOCS) {
                                    break
                                }
                                if (nextDocID <= docID) {
                                    throw CheckIndexException(
                                        ("term "
                                                + term
                                                + ": advance(docID="
                                                + skipDocID
                                                + "), then .next() returned docID="
                                                + nextDocID
                                                + " vs prev docID="
                                                + docID)
                                    )
                                }
                            }

                            if (isVectors) {
                                // Only 1 doc in the postings for term vectors, so we only test 1 advance:
                                break
                            }
                        }
                    } else {
                        for (idx in 0..6) {
                            val skipDocID = (((idx + 1) * maxDoc.toLong()) / 8).toInt()
                            postings = termsEnum.postings(postings, PostingsEnum.NONE.toInt())
                            val docID: Int = postings.advance(skipDocID)
                            if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                                break
                            } else {
                                if (docID < skipDocID) {
                                    throw CheckIndexException(
                                        "term $term: advance(docID=$skipDocID) returned docID=$docID"
                                    )
                                }
                                val nextDocID: Int = postings.nextDoc()
                                if (nextDocID == DocIdSetIterator.NO_MORE_DOCS) {
                                    break
                                }
                                if (nextDocID <= docID) {
                                    throw CheckIndexException(
                                        ("term "
                                                + term
                                                + ": advance(docID="
                                                + skipDocID
                                                + "), then .next() returned docID="
                                                + nextDocID
                                                + " vs prev docID="
                                                + docID)
                                    )
                                }
                            }
                            if (isVectors) {
                                // Only 1 doc in the postings for term vectors, so we only test 1 advance:
                                break
                            }
                        }
                    }

                    // Checking score blocks is heavy, we only do it on long postings lists, on every 1024th
                    // term or if slow checks are enabled.
                    if (level >= Level.MIN_LEVEL_FOR_SLOW_CHECKS || docFreq > 1024 || (status.termCount + status.delTermCount) % 1024 == 0L) {
                        // First check max scores and block uptos
                        // But only if slow checks are enabled since we visit all docs
                        if (level >= Level.MIN_LEVEL_FOR_SLOW_CHECKS) {
                            var max = -1
                            var maxFreq = 0
                            val impactsEnum: ImpactsEnum =
                                termsEnum.impacts(PostingsEnum.FREQS.toInt())
                            postings = termsEnum.postings(postings, PostingsEnum.FREQS.toInt())
                            var doc: Int = impactsEnum.nextDoc()
                            while (true) {
                                if (postings.nextDoc() != doc) {
                                    throw CheckIndexException(
                                        "Wrong next doc: " + doc + ", expected " + postings.docID()
                                    )
                                }
                                if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                                    break
                                }
                                if (postings.freq() != impactsEnum.freq()) {
                                    throw CheckIndexException(
                                        "Wrong freq, expected " + postings.freq() + ", but got " + impactsEnum.freq()
                                    )
                                }
                                if (doc > max) {
                                    impactsEnum.advanceShallow(doc)
                                    val impacts: Impacts = impactsEnum.impacts
                                    checkImpacts(impacts, doc)
                                    max = impacts.getDocIdUpTo(0)
                                    val impacts0: MutableList<Impact> = impacts.getImpacts(0)
                                    maxFreq = impacts0[impacts0.size - 1].freq
                                }
                                if (impactsEnum.freq() > maxFreq) {
                                    throw CheckIndexException(
                                        ("freq "
                                                + impactsEnum.freq()
                                                + " is greater than the max freq according to impacts "
                                                + maxFreq)
                                    )
                                }
                                doc = impactsEnum.nextDoc()
                            }
                        }

                        // Now check advancing
                        val impactsEnum: ImpactsEnum =
                            termsEnum.impacts(PostingsEnum.FREQS.toInt())
                        postings = termsEnum.postings(postings, PostingsEnum.FREQS.toInt())

                        var max = -1
                        var maxFreq = 0
                        while (true) {
                            var doc: Int = impactsEnum.docID()
                            val advance: Boolean
                            val target: Int
                            if (((field.hashCode() + doc) and 1) == 1) {
                                advance = false
                                target = doc + 1
                            } else {
                                advance = true
                                val delta = min(
                                    1 + ((31 * field.hashCode() + doc) and 0x1ff),
                                    DocIdSetIterator.NO_MORE_DOCS - doc
                                )
                                target = impactsEnum.docID() + delta
                            }

                            if (target > max && target % 2 == 1) {
                                val delta = min(
                                    (31 * field.hashCode() + target) and 0x1ff,
                                    DocIdSetIterator.NO_MORE_DOCS - target
                                )
                                max = target + delta
                                impactsEnum.advanceShallow(target)
                                val impacts: Impacts = impactsEnum.impacts
                                checkImpacts(impacts, doc)
                                maxFreq = Int.MAX_VALUE
                                for (impactsLevel in 0..<impacts.numLevels()) {
                                    if (impacts.getDocIdUpTo(impactsLevel) >= max) {
                                        val perLevelImpacts: MutableList<Impact> =
                                            impacts.getImpacts(impactsLevel)
                                        maxFreq = perLevelImpacts[perLevelImpacts.size - 1].freq
                                        break
                                    }
                                }
                            }

                            doc = if (advance) {
                                impactsEnum.advance(target)
                            } else {
                                impactsEnum.nextDoc()
                            }

                            if (postings.advance(target) != doc) {
                                throw CheckIndexException(
                                    ("Impacts do not advance to the same document as postings for target "
                                            + target
                                            + ", postings: "
                                            + postings.docID()
                                            + ", impacts: "
                                            + doc)
                                )
                            }
                            if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                                break
                            }
                            if (postings.freq() != impactsEnum.freq()) {
                                throw CheckIndexException(
                                    "Wrong freq, expected " + postings.freq() + ", but got " + impactsEnum.freq()
                                )
                            }

                            if (doc >= max) {
                                val delta = min(
                                    (31 * field.hashCode() + target and 0x1ff),
                                    DocIdSetIterator.NO_MORE_DOCS - doc
                                )
                                max = doc + delta
                                impactsEnum.advanceShallow(doc)
                                val impacts: Impacts = impactsEnum.impacts
                                checkImpacts(impacts, doc)
                                maxFreq = Int.MAX_VALUE
                                for (impactsLevel in 0..<impacts.numLevels()) {
                                    if (impacts.getDocIdUpTo(impactsLevel) >= max) {
                                        val perLevelImpacts: MutableList<Impact> =
                                            impacts.getImpacts(impactsLevel)
                                        maxFreq = perLevelImpacts[perLevelImpacts.size - 1].freq
                                        break
                                    }
                                }
                            }

                            if (impactsEnum.freq() > maxFreq) {
                                throw CheckIndexException(
                                    ("Term frequency "
                                            + impactsEnum.freq()
                                            + " is greater than the max freq according to impacts "
                                            + maxFreq)
                                )
                            }
                        }
                    }
                }

                if (minTerm != null && status.termCount + status.delTermCount == 0L) {
                    throw CheckIndexException(
                        "field=\"$field\": minTerm is non-null yet we saw no terms: $minTerm"
                    )
                }

                val fieldTerms: Terms? = fields.terms(field)
                if (fieldTerms == null) {
                    // Unusual: the FieldsEnum returned a field but
                    // the Terms for that field is null; this should
                    // only happen if it's a ghost field (field with
                    // no terms, e.g. there used to be terms but all
                    // docs got deleted and then merged away):
                } else {
                    val fieldTermCount: Long = (status.delTermCount + status.termCount) - termCountStart

                    val stats: Any = checkNotNull(fieldTerms.getStats())
                    if (status.blockTreeStats == null) {
                        status.blockTreeStats = HashMap()
                    }
                    status.blockTreeStats!![field] = stats

                    val actualSumDocFreq: Long = fields.terms(field)!!.sumDocFreq
                    if (sumDocFreq != actualSumDocFreq) {
                        throw CheckIndexException(
                            ("sumDocFreq for field "
                                    + field
                                    + "="
                                    + actualSumDocFreq
                                    + " != recomputed sumDocFreq="
                                    + sumDocFreq)
                        )
                    }

                    val actualSumTotalTermFreq: Long = fields.terms(field)!!.sumTotalTermFreq
                    if (sumTotalTermFreq != actualSumTotalTermFreq) {
                        throw CheckIndexException(
                            ("sumTotalTermFreq for field "
                                    + field
                                    + "="
                                    + actualSumTotalTermFreq
                                    + " != recomputed sumTotalTermFreq="
                                    + sumTotalTermFreq)
                        )
                    }

                    if (!hasFreqs && sumTotalTermFreq != sumDocFreq) {
                        throw CheckIndexException(
                            ("sumTotalTermFreq for field "
                                    + field
                                    + " should be "
                                    + sumDocFreq
                                    + ", got sumTotalTermFreq="
                                    + sumTotalTermFreq)
                        )
                    }

                    val v: Int = fieldTerms.docCount
                    if (visitedDocs.cardinality() != v) {
                        throw CheckIndexException(
                            ("docCount for field "
                                    + field
                                    + "="
                                    + v
                                    + " != recomputed docCount="
                                    + visitedDocs.cardinality())
                        )
                    }

                    if (fieldInfo.hasNorms() && !isVectors) {
                        val norms: NumericDocValues = normsProducer!!.getNorms(fieldInfo)
                        // count of valid norm values found for the field
                        var actualCount = 0
                        // Cross-check terms with norms
                        run {
                            var doc: Int = norms.nextDoc()
                            while (doc != DocIdSetIterator.NO_MORE_DOCS
                            ) {
                                if (liveDocs != null && !liveDocs.get(doc)) {
                                    // Norms may only be out of sync with terms on deleted documents.
                                    // This happens when a document fails indexing and in that case it
                                    // should be immediately marked as deleted by the IndexWriter.
                                    doc = norms.nextDoc()
                                    continue
                                }
                                val norm: Long = norms.longValue()
                                if (norm != 0L) {
                                    actualCount++
                                    if (!visitedDocs.get(doc)) {
                                        throw CheckIndexException(
                                            ("Document "
                                                    + doc
                                                    + " doesn't have terms according to postings but has a norm value that is not zero: "
                                                    + Long.toUnsignedString(norm))
                                        )
                                    }
                                } else if (visitedDocs.get(doc)) {
                                    throw CheckIndexException(
                                        ("Document "
                                                + doc
                                                + " has terms according to postings but its norm value is 0, which may only be used on documents that have no terms")
                                    )
                                }
                                doc = norms.nextDoc()
                            }
                        }
                        var expectedCount = 0
                        var doc: Int = visitedDocs.nextSetBit(0)
                        while (doc != DocIdSetIterator.NO_MORE_DOCS
                        ) {
                            if (liveDocs != null && !liveDocs.get(doc)) {
                                // Norms may only be out of sync with terms on deleted documents.
                                // This happens when a document fails indexing and in that case it
                                // should be immediately marked as deleted by the IndexWriter.
                                doc =
                                    if (doc + 1 >= visitedDocs.length())
                                        DocIdSetIterator.NO_MORE_DOCS
                                    else
                                        visitedDocs.nextSetBit(doc + 1)
                                continue
                            }
                            expectedCount++
                            doc =
                                if (doc + 1 >= visitedDocs.length())
                                    DocIdSetIterator.NO_MORE_DOCS
                                else
                                    visitedDocs.nextSetBit(doc + 1)
                        }
                        if (expectedCount != actualCount) {
                            throw CheckIndexException(
                                "actual norm count: $actualCount but expected: $expectedCount"
                            )
                        }
                    }

                    // Test seek to last term:
                    if (lastTerm != null) {
                        if (termsEnum.seekCeil(lastTerm.get()) != TermsEnum.SeekStatus.FOUND) {
                            throw CheckIndexException("seek to last term " + lastTerm.get() + " failed")
                        }
                        if (termsEnum.term() != lastTerm.get()) {
                            throw CheckIndexException(
                                ("seek to last term "
                                        + lastTerm.get()
                                        + " returned FOUND but seeked to the wrong term "
                                        + termsEnum.term())
                            )
                        }

                        val expectedDocFreq: Int = termsEnum.docFreq()
                        val d: PostingsEnum =
                            termsEnum.postings(null, PostingsEnum.NONE.toInt())
                        var docFreq = 0
                        while (d.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                            docFreq++
                        }
                        if (docFreq != expectedDocFreq) {
                            throw CheckIndexException(
                                ("docFreq for last term "
                                        + lastTerm.get()
                                        + "="
                                        + expectedDocFreq
                                        + " != recomputed docFreq="
                                        + docFreq)
                            )
                        }
                    }

                    // check unique term count
                    var termCount: Long = -1

                    if (fieldTermCount > 0) {
                        termCount = fields.terms(field)!!.size()

                        if (termCount != -1L && termCount != fieldTermCount) {
                            throw CheckIndexException(
                                "termCount mismatch $termCount vs $fieldTermCount"
                            )
                        }
                    }

                    // Test seeking by ord
                    if (hasOrd && status.termCount - termCountStart > 0) {
                        val seekCount = min(10000L, termCount).toInt()
                        if (seekCount > 0) {
                            val seekTerms: Array<BytesRef> = kotlin.arrayOfNulls<BytesRef>(seekCount) as Array<BytesRef>

                            // Seek by ord
                            for (i in seekCount - 1 downTo 0) {
                                val ord = i * (termCount / seekCount)
                                termsEnum.seekExact(ord)
                                val actualOrd: Long = termsEnum.ord()
                                if (actualOrd != ord) {
                                    throw CheckIndexException("seek to ord $ord returned ord $actualOrd")
                                }
                                seekTerms[i] = BytesRef.deepCopyOf(termsEnum.term()!!)
                            }

                            // Seek by term
                            for (i in seekCount - 1 downTo 0) {
                                if (termsEnum.seekCeil(seekTerms[i]) != TermsEnum.SeekStatus.FOUND) {
                                    throw CheckIndexException("seek to existing term " + seekTerms[i] + " failed")
                                }
                                if (termsEnum.term() != seekTerms[i]) {
                                    throw CheckIndexException(
                                        ("seek to existing term "
                                                + seekTerms[i]
                                                + " returned FOUND but seeked to the wrong term "
                                                + termsEnum.term())
                                    )
                                }

                                postings =
                                    termsEnum.postings(postings, PostingsEnum.NONE.toInt())
                                if (postings == null) {
                                    throw CheckIndexException(
                                        "null DocsEnum from to existing term " + seekTerms[i]
                                    )
                                }
                            }
                        }
                    }

                    // Test Terms#intersect
                    // An automaton that should match a good number of terms
                    var automaton: Automaton =
                        Operations.concatenate(
                            mutableListOf(
                                Automata.makeAnyBinary(),
                                Automata.makeCharRange('a'.code, 'e'.code),
                                Automata.makeAnyBinary()
                            )
                        )
                    var startTerm: BytesRef? = null
                    checkTermsIntersect(terms, automaton, startTerm!!)

                    startTerm = BytesRef()
                    checkTermsIntersect(terms, automaton, startTerm)

                    automaton = Automata.makeNonEmptyBinary()
                    startTerm = BytesRef(byteArrayOf('l'.code.toByte()))
                    checkTermsIntersect(terms, automaton, startTerm)

                    // a term that likely compares greater than every other term in the dictionary
                    startTerm = BytesRef(
                        byteArrayOf(
                            0xFF.toByte(),
                            0xFF.toByte(),
                            0xFF.toByte(),
                            0xFF.toByte()
                        )
                    )
                    checkTermsIntersect(terms, automaton, startTerm)
                }
            }

            val fieldCount: Int = fields.size()

            if (fieldCount != -1) {
                if (fieldCount < 0) {
                    throw CheckIndexException("invalid fieldCount: $fieldCount")
                }
                if (fieldCount != computedFieldCount) {
                    throw CheckIndexException(
                        ("fieldCount mismatch "
                                + fieldCount
                                + " vs recomputed field count "
                                + computedFieldCount)
                    )
                }
            }

            if (doPrint) {
                msg(
                    infoStream,
                    "OK [${status.termCount} terms; ${status.totFreq} terms/docs pairs; ${status.totPos} tokens] [took ${
                        nsToSec(
                            System.nanoTime() - startNS
                        )
                    } sec]"
                )
            }

            if (verbose && status.blockTreeStats != null && infoStream != null && status.termCount > 0) {
                for (ent in status.blockTreeStats!!.entries) {
                    infoStream.println("      field \"" + ent.key + "\":")
                    infoStream.println("      " + ent.value.toString().replace("\n", "\n      "))
                }
            }

            return status
        }

        @Throws(IOException::class)
        private fun checkTermsIntersect(
            terms: Terms,
            automaton: Automaton,
            startTerm: BytesRef
        ) {
            var automaton: Automaton = automaton
            val allTerms: TermsEnum = terms.iterator()
            automaton = Operations.determinize(
                automaton,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
            )
            val compiledAutomaton = CompiledAutomaton(automaton, finite = false, simplify = true, isBinary = true)
            val runAutomaton = ByteRunAutomaton(automaton, true)
            val filteredTerms: TermsEnum = terms.intersect(compiledAutomaton, startTerm)
            var term: BytesRef?
            term = if (startTerm != null) {
                when (allTerms.seekCeil(startTerm)) {
                    TermsEnum.SeekStatus.FOUND -> allTerms.next()
                    TermsEnum.SeekStatus.NOT_FOUND -> allTerms.term()
                    TermsEnum.SeekStatus.END -> null
                    else -> null
                }
            } else {
                allTerms.next()
            }
            while (term != null) {
                if (runAutomaton.run(term.bytes, term.offset, term.length)) {
                    val filteredTerm: BytesRef? = filteredTerms.next()
                    if (term != filteredTerm) {
                        throw CheckIndexException(
                            "Expected next filtered term: $term, but got $filteredTerm"
                        )
                    }
                }
                term = allTerms.next()
            }
            val filteredTerm: BytesRef? = filteredTerms.next()
            if (filteredTerm != null) {
                throw CheckIndexException("Expected exhausted TermsEnum, but got $filteredTerm")
            }
        }

        /**
         * For use in tests only.
         *
         * @lucene.internal
         */
        fun checkImpacts(impacts: Impacts, lastTarget: Int) {
            val numLevels: Int = impacts.numLevels()
            if (numLevels < 1) {
                throw CheckIndexException("The number of impact levels must be >= 1, got $numLevels")
            }

            val docIdUpTo0: Int = impacts.getDocIdUpTo(0)
            if (docIdUpTo0 < lastTarget) {
                throw CheckIndexException(
                    ("getDocIdUpTo returned "
                            + docIdUpTo0
                            + " on level 0, which is less than the target "
                            + lastTarget)
                )
            }

            for (impactsLevel in 1..<numLevels) {
                val docIdUpTo: Int = impacts.getDocIdUpTo(impactsLevel)
                val previousDocIdUpTo: Int = impacts.getDocIdUpTo(impactsLevel - 1)
                if (docIdUpTo < previousDocIdUpTo) {
                    throw CheckIndexException(
                        ("Decreasing return for getDocIdUpTo: level "
                                + (impactsLevel - 1)
                                + " returned "
                                + previousDocIdUpTo
                                + " but level "
                                + impactsLevel
                                + " returned "
                                + docIdUpTo
                                + " for target "
                                + lastTarget)
                    )
                }
            }

            for (impactsLevel in 0..<numLevels) {
                val perLevelImpacts: MutableList<Impact> = impacts.getImpacts(impactsLevel)
                if (perLevelImpacts.isEmpty()) {
                    throw CheckIndexException("Got empty list of impacts on level $impactsLevel")
                }
                val first: Impact = perLevelImpacts[0]
                if (first.freq < 1) {
                    throw CheckIndexException("First impact had a freq <= 0: $first")
                }
                if (first.norm == 0L) {
                    throw CheckIndexException("First impact had a norm == 0: $first")
                }
                // Impacts must be in increasing order of norm AND freq
                var previous: Impact = first
                for (i in 1..<perLevelImpacts.size) {
                    val impact: Impact = perLevelImpacts[i]
                    if (impact.freq <= previous.freq || Long.compareUnsigned(
                            impact.norm,
                            previous.norm
                        ) <= 0
                    ) {
                        throw CheckIndexException(
                            "Impacts are not ordered or contain dups, got $previous then $impact"
                        )
                    }
                }
                if (impactsLevel > 0) {
                    // Make sure that impacts at level N trigger better scores than an impactsLevel N-1
                    val previousIt: MutableIterator<Impact> =
                        impacts.getImpacts(impactsLevel - 1).iterator()
                    previous = previousIt.next()
                    val it: MutableIterator<Impact> = perLevelImpacts.iterator()
                    var impact: Impact = it.next()
                    while (previousIt.hasNext()) {
                        previous = previousIt.next()
                        if (previous.freq <= impact.freq
                            && Long.compareUnsigned(previous.norm, impact.norm) >= 0
                        ) {
                            // previous triggers a lower score than the current impact, all good
                            continue
                        }
                        if (!it.hasNext()) {
                            throw CheckIndexException(
                                ("Found impact "
                                        + previous
                                        + " on level "
                                        + (impactsLevel - 1)
                                        + " but no impact on level "
                                        + impactsLevel
                                        + " triggers a better score: "
                                        + perLevelImpacts)
                            )
                        }
                        impact = it.next()
                    }
                }
            }
        }

        /** Test the term index.  */
        @Throws(IOException::class)
        fun testPostings(
            reader: CodecReader,
            infoStream: PrintStream,
            verbose: Boolean = false,
            level: Int = Level.MIN_LEVEL_FOR_SLOW_CHECKS,
            failFast: Boolean = false
        ): Status.TermIndexStatus {
            // TODO: we should go and verify term vectors match, if the Level is high enough to
            // include slow checks

            var status: Status.TermIndexStatus
            val maxDoc: Int = reader.maxDoc()

            try {
                if (infoStream != null) {
                    infoStream.print("    test: terms, freq, prox...")
                }

                var fields: FieldsProducer? = reader.postingsReader
                if (fields != null) {
                    fields = fields.mergeInstance
                } else {
                    return Status.TermIndexStatus()
                }
                val fieldInfos: FieldInfos = reader.fieldInfos
                var normsProducer: NormsProducer? = reader.normsReader
                if (normsProducer != null) {
                    normsProducer = normsProducer.mergeInstance
                }
                status =
                    checkFields(
                        fields,
                        reader.liveDocs!!,
                        maxDoc,
                        fieldInfos,
                        normsProducer!!,
                        doPrint = true,
                        isVectors = false,
                        infoStream = infoStream,
                        verbose = verbose,
                        level = level
                    )
            } catch (e: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR: $e")
                status = Status.TermIndexStatus()
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        /** Test the points index.  */
        @Throws(IOException::class)
        fun testPoints(
            reader: CodecReader, infoStream: PrintStream, failFast: Boolean
        ): Status.PointsStatus {
            if (infoStream != null) {
                infoStream.print("    test: points..............")
            }
            val startNS: Long = System.nanoTime()
            val fieldInfos: FieldInfos = reader.fieldInfos
            val status: Status.PointsStatus = Status.PointsStatus()
            try {
                if (fieldInfos.hasPointValues()) {
                    val pointsReader: PointsReader? = reader.pointsReader
                    if (pointsReader == null) {
                        throw CheckIndexException(
                            "there are fields with points, but reader.@ointsReader is null"
                        )
                    }
                    for (fieldInfo in fieldInfos) {
                        if (fieldInfo.pointDimensionCount > 0) {
                            val values: PointValues? = pointsReader.getValues(fieldInfo.name)
                            if (values == null) {
                                continue
                            }

                            status.totalValueFields++

                            val size: Long = values.size()
                            val docCount: Int = values.docCount

                            val crossCost: Long =
                                values.estimatePointCount(
                                    ConstantRelationIntersectVisitor(Relation.CELL_CROSSES_QUERY)
                                )
                            if (crossCost < size / 2) {
                                throw CheckIndexException(
                                    "estimatePointCount should return >= size/2 when all cells match"
                                )
                            }
                            val insideCost: Long =
                                values.estimatePointCount(
                                    ConstantRelationIntersectVisitor(Relation.CELL_INSIDE_QUERY)
                                )
                            if (insideCost < size) {
                                throw CheckIndexException(
                                    "estimatePointCount should return >= size when all cells fully match"
                                )
                            }
                            val outsideCost: Long =
                                values.estimatePointCount(
                                    ConstantRelationIntersectVisitor(Relation.CELL_OUTSIDE_QUERY)
                                )
                            if (outsideCost != 0L) {
                                throw CheckIndexException(
                                    "estimatePointCount should return 0 when no cells match"
                                )
                            }

                            val visitor =
                                VerifyPointsVisitor(fieldInfo.name, reader.maxDoc(), values)
                            values.intersect(visitor)

                            if (visitor.pointCountSeen != size) {
                                throw CheckIndexException(
                                    ("point values for field \""
                                            + fieldInfo.name
                                            + "\" claims to have size="
                                            + size
                                            + " points, but in fact has "
                                            + visitor.pointCountSeen)
                                )
                            }

                            if (visitor.docCountSeen != docCount.toLong()) {
                                throw CheckIndexException(
                                    ("point values for field \""
                                            + fieldInfo.name
                                            + "\" claims to have docCount="
                                            + docCount
                                            + " but in fact has "
                                            + visitor.docCountSeen)
                                )
                            }

                            status.totalValuePoints += visitor.pointCountSeen
                        }
                    }
                }

                msg(
                    infoStream,
                    "OK [${status.totalValueFields} fields, ${status.totalValuePoints} points] [took ${nsToSec(System.nanoTime() - startNS)} sec]"
                )
            } catch (e: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR: $e")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        /** Test the vectors index.  */
        @Throws(IOException::class)
        fun testVectors(
            reader: CodecReader, infoStream: PrintStream, failFast: Boolean
        ): Status.VectorValuesStatus {
            infoStream.print("    test: vectors.............")
            val startNS: Long = System.nanoTime()
            val fieldInfos: FieldInfos = reader.fieldInfos
            val status: Status.VectorValuesStatus = Status.VectorValuesStatus()
            try {
                if (fieldInfos.hasVectorValues()) {
                    for (fieldInfo in fieldInfos) {
                        if (fieldInfo.hasVectorValues()) {
                            val dimension: Int = fieldInfo.vectorDimension
                            if (dimension <= 0) {
                                throw CheckIndexException(
                                    ("Field \""
                                            + fieldInfo.name
                                            + "\" has vector values but dimension is "
                                            + dimension)
                                )
                            }
                            if (reader.getFloatVectorValues(fieldInfo.name) == null
                                && reader.getByteVectorValues(fieldInfo.name) == null
                            ) {
                                continue
                            }

                            status.totalKnnVectorFields++
                            when (fieldInfo.vectorEncoding) {
                                VectorEncoding.BYTE -> checkByteVectorValues(
                                    requireNotNull(
                                        reader.getByteVectorValues(
                                            fieldInfo.name
                                        )
                                    ),
                                    fieldInfo,
                                    status,
                                    reader
                                )

                                VectorEncoding.FLOAT32 -> checkFloatVectorValues(
                                    requireNotNull(
                                        reader.getFloatVectorValues(
                                            fieldInfo.name
                                        )
                                    ),
                                    fieldInfo,
                                    status,
                                    reader
                                )

                                else -> throw CheckIndexException(
                                    ("Field \""
                                            + fieldInfo.name
                                            + "\" has unexpected vector encoding: "
                                            + fieldInfo.vectorEncoding)
                                )
                            }
                        }
                    }
                }
                msg(
                    infoStream,
                    "OK [${status.totalKnnVectorFields} fields, ${status.totalVectorValues} vectors] [took ${nsToSec(System.nanoTime() - startNS)} sec]"
                )
            } catch (e: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR: $e")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        /** Test the HNSW graph.  */
        @Throws(IOException::class)
        fun testHnswGraphs(
            reader: CodecReader, infoStream: PrintStream, failFast: Boolean
        ): Status.HnswGraphsStatus {
            if (infoStream != null) {
                infoStream.print("    test: hnsw graphs.........")
            }
            val startNS: Long = System.nanoTime()
            val status: Status.HnswGraphsStatus = Status.HnswGraphsStatus()
            val vectorsReader: KnnVectorsReader = reader.vectorReader!!
            val fieldInfos: FieldInfos = reader.fieldInfos

            try {
                if (fieldInfos.hasVectorValues()) {
                    for (fieldInfo in fieldInfos) {
                        if (fieldInfo.hasVectorValues()) {
                            val fieldReader: KnnVectorsReader =
                                getFieldReaderForName(vectorsReader, fieldInfo.name)
                            if (fieldReader is HnswGraphProvider) {
                                val hnswGraph: HnswGraph = fieldReader.getGraph(fieldInfo.name)!!
                                testHnswGraph(hnswGraph, fieldInfo.name, status)
                            }
                        }
                    }
                }
                msg(
                    infoStream,
                    "OK [${status.hnswGraphsStatusByField.size} fields] [took ${nsToSec(System.nanoTime() - startNS)} sec]"
                )
                printHnswInfo(infoStream, status.hnswGraphsStatusByField)
            } catch (e: Exception) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR: $e")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        private fun getFieldReaderForName(
            vectorsReader: KnnVectorsReader, fieldName: String
        ): KnnVectorsReader {
            return if (vectorsReader is PerFieldKnnVectorsFormat.FieldsReader) {
                vectorsReader.getFieldReader(fieldName)!!
            } else {
                vectorsReader
            }
        }

        private fun printHnswInfo(
            infoStream: PrintStream, fieldsStatus: MutableMap<String, Status.HnswGraphStatus>
        ) {
            for (entry in fieldsStatus.entries) {
                val fieldName = entry.key
                val status: Status.HnswGraphStatus = entry.value
                msg(infoStream, "      hnsw field name: $fieldName")

                val numLevels: Int = min(status.numNodesAtLevel!!.size, status.connectednessAtLevel!!.size)
                for (level in numLevels - 1 downTo 0) {
                    val numNodes: Int = status.numNodesAtLevel!![level]
                    val connectedness: String = status.connectednessAtLevel!![level]
                    msg(
                        infoStream,
                        "        level $level: $numNodes nodes, $connectedness connected"
                    )
                }
            }
        }

        @Throws(IOException::class, CheckIndexException::class)
        private fun testHnswGraph(
            hnswGraph: HnswGraph, fieldName: String, status: Status.HnswGraphsStatus
        ) {
            if (hnswGraph != null) {
                status.hnswGraphsStatusByField[fieldName] = Status.HnswGraphStatus()
                val numLevels: Int = hnswGraph.numLevels()
                status.hnswGraphsStatusByField[fieldName]!!.numNodesAtLevel =
                    arrayOfNulls<Int?>(numLevels) as Array<Int>?
                status.hnswGraphsStatusByField[fieldName]!!.connectednessAtLevel =
                    arrayOfNulls<String?>(numLevels) as Array<String>

                // Perform checks on each level of the HNSW graph
                for (level in numLevels - 1 downTo 0) {
                    // Collect BitSet of all nodes on this level
                    val nodesOnThisLevel: BitSet =
                        FixedBitSet(hnswGraph.size())
                    var nodesIterator: HnswGraph.NodesIterator =
                        hnswGraph.getNodesOnLevel(level)
                    while (nodesIterator.hasNext()) {
                        nodesOnThisLevel.set(nodesIterator.nextInt())
                    }

                    nodesIterator = hnswGraph.getNodesOnLevel(level)
                    // Perform checks on each node on the level
                    while (nodesIterator.hasNext()) {
                        val node: Int = nodesIterator.nextInt()
                        if (node < 0 || node > hnswGraph.size() - 1) {
                            throw CheckIndexException(
                                ("Field \""
                                        + fieldName
                                        + "\" has node: "
                                        + node
                                        + " not in the expected range [0, "
                                        + (hnswGraph.size() - 1)
                                        + "]")
                            )
                        }

                        // Perform checks on the node's neighbors
                        hnswGraph.seek(level, node)
                        var nbr: Int
                        var lastNeighbor = -1
                        var firstNeighbor = -1
                        while ((hnswGraph.nextNeighbor()
                                .also { nbr = it }) != DocIdSetIterator.NO_MORE_DOCS
                        ) {
                            if (!nodesOnThisLevel.get(nbr)) {
                                throw CheckIndexException(
                                    ("Field \""
                                            + fieldName
                                            + "\" has node: "
                                            + node
                                            + " with a neighbor "
                                            + nbr
                                            + " which is not on its level ("
                                            + level
                                            + ")")
                                )
                            }
                            if (firstNeighbor == -1) {
                                firstNeighbor = nbr
                            }
                            if (nbr < lastNeighbor) {
                                throw CheckIndexException(
                                    ("Field \""
                                            + fieldName
                                            + "\" has neighbors out of order for node "
                                            + node
                                            + ": "
                                            + nbr
                                            + "<"
                                            + lastNeighbor
                                            + " 1st="
                                            + firstNeighbor)
                                )
                            } else if (nbr == lastNeighbor) {
                                throw CheckIndexException(
                                    ("Field \""
                                            + fieldName
                                            + "\" has repeated neighbors of node "
                                            + node
                                            + " with value "
                                            + nbr)
                                )
                            }
                            lastNeighbor = nbr
                        }
                    }
                    val numNodesOnLayer: Int = nodesIterator.size()
                    status.hnswGraphsStatusByField[fieldName]!!.numNodesAtLevel!![level] = numNodesOnLayer

                    // Evaluate connectedness at this level by measuring the number of nodes reachable from the
                    // entry point
                    val connectedNodes: IntIntHashMap = getConnectedNodesOnLevel(hnswGraph, numNodesOnLayer, level)
                    status.hnswGraphsStatusByField[fieldName]!!.connectednessAtLevel!![level] =
                        connectedNodes.size().toString() + "/" + numNodesOnLayer
                }
            }
        }

        @Throws(IOException::class)
        private fun getConnectedNodesOnLevel(
            hnswGraph: HnswGraph, numNodesOnLayer: Int, level: Int
        ): IntIntHashMap {
            val connectedNodes = IntIntHashMap(numNodesOnLayer)
            val entryPoint: Int = hnswGraph.entryNode()
            val stack: ArrayDeque<Int> = ArrayDeque()
            stack.push(entryPoint)
            while (!stack.isEmpty()) {
                val node: Int = stack.pop()
                if (connectedNodes.containsKey(node)) {
                    continue
                }
                connectedNodes.put(node, 1)
                hnswGraph.seek(level, node)
                var friendOrd: Int
                while ((hnswGraph.nextNeighbor()
                        .also { friendOrd = it }) != DocIdSetIterator.NO_MORE_DOCS
                ) {
                    stack.push(friendOrd)
                }
            }
            return connectedNodes
        }

        private fun vectorsReaderSupportsSearch(
            codecReader: CodecReader,
            fieldName: String
        ): Boolean {
            var vectorsReader: KnnVectorsReader = codecReader.vectorReader!!
            if (vectorsReader is PerFieldKnnVectorsFormat.FieldsReader) {
                vectorsReader = vectorsReader.getFieldReader(fieldName)!!
            }
            return vectorsReader !is FlatVectorsReader
        }

        @Throws(IOException::class)
        private fun checkFloatVectorValues(
            values: FloatVectorValues,
            fieldInfo: FieldInfo,
            status: Status.VectorValuesStatus,
            codecReader: CodecReader
        ) {
            var count = 0
            val everyNdoc = max(values.size() / 64, 1)
            while (count < values.size()) {
                // search the first maxNumSearches vectors to exercise the graph
                if (values.ordToDoc(count) % everyNdoc == 0) {
                    val collector: KnnCollector =
                        TopKnnCollector(10, Int.MAX_VALUE)
                    if (vectorsReaderSupportsSearch(codecReader, fieldInfo.name)) {
                        codecReader.vectorReader!!.search(fieldInfo.name, values.vectorValue(count), collector, null)
                        val docs: TopDocs = collector.topDocs()
                        if (docs.scoreDocs!!.isEmpty()) {
                            throw CheckIndexException(
                                "Field \"" + fieldInfo.name + "\" failed to search k nearest neighbors"
                            )
                        }
                    }
                }
                val valueLength: Int = values.vectorValue(count).size
                if (valueLength != fieldInfo.vectorDimension) {
                    throw CheckIndexException(
                        ("Field \""
                                + fieldInfo.name
                                + "\" has a value whose dimension="
                                + valueLength
                                + " not matching the field's dimension="
                                + fieldInfo.vectorDimension)
                    )
                }
                ++count
            }
            if (count != values.size()) {
                throw CheckIndexException(
                    ("Field \""
                            + fieldInfo.name
                            + "\" has size="
                            + values.size()
                            + " but when iterated, returns "
                            + count
                            + " docs with values")
                )
            }
            status.totalVectorValues += count.toLong()
        }

        @Throws(IOException::class)
        private fun checkByteVectorValues(
            values: ByteVectorValues,
            fieldInfo: FieldInfo,
            status: Status.VectorValuesStatus,
            codecReader: CodecReader
        ) {
            var count = 0
            val everyNdoc = max(values.size() / 64, 1)
            val supportsSearch = vectorsReaderSupportsSearch(codecReader, fieldInfo.name)
            while (count < values.size()) {
                // search the first maxNumSearches vectors to exercise the graph
                if (supportsSearch && values.ordToDoc(count) % everyNdoc == 0) {
                    val collector: KnnCollector =
                        TopKnnCollector(10, Int.MAX_VALUE)
                    codecReader
                        .vectorReader!!.search(fieldInfo.name, values.vectorValue(count), collector, null)
                    val docs: TopDocs = collector.topDocs()
                    if (docs.scoreDocs!!.isEmpty()) {
                        throw CheckIndexException(
                            "Field \"" + fieldInfo.name + "\" failed to search k nearest neighbors"
                        )
                    }
                }
                val valueLength: Int = values.vectorValue(count).size
                if (valueLength != fieldInfo.vectorDimension) {
                    throw CheckIndexException(
                        ("Field \""
                                + fieldInfo.name
                                + "\" has a value whose dimension="
                                + valueLength
                                + " not matching the field's dimension="
                                + fieldInfo.vectorDimension)
                    )
                }
                ++count
            }
            if (count != values.size()) {
                throw CheckIndexException(
                    ("Field \""
                            + fieldInfo.name
                            + "\" has size="
                            + values.size()
                            + " but when iterated, returns "
                            + count
                            + " docs with values")
                )
            }
            status.totalVectorValues += count.toLong()
        }

        /** Test stored fields.  */
        @Throws(IOException::class)
        fun testStoredFields(
            reader: CodecReader, infoStream: PrintStream, failFast: Boolean
        ): Status.StoredFieldStatus {
            val startNS: Long = System.nanoTime()
            val status: Status.StoredFieldStatus = Status.StoredFieldStatus()

            try {
                if (infoStream != null) {
                    infoStream.print("    test: stored fields.......")
                }

                // Scan stored fields for all documents
                val liveDocs: Bits? = reader.liveDocs
                val storedFields: StoredFieldsReader = reader.fieldsReader!!.mergeInstance
                for (j in 0..<reader.maxDoc()) {
                    // Intentionally pull even deleted documents to
                    // make sure they too are not corrupt:
                    val visitor = DocumentStoredFieldVisitor()
                    if ((j and 0x03) == 0) {
                        storedFields.prefetch(j)
                    }
                    storedFields.document(j, visitor)
                    val doc: Document = visitor.document
                    if (liveDocs == null || liveDocs.get(j)) {
                        status.docCount++
                        status.totFields += doc.getFields().size.toLong()
                    }
                }

                // Validate docCount
                if (status.docCount != reader.numDocs()) {
                    throw CheckIndexException(
                        "docCount=" + status.docCount + " but saw " + status.docCount + " undeleted docs"
                    )
                }

                msg(
                    infoStream,
                    "OK [${status.totFields} total field count; avg ${((status.totFields.toFloat()) / status.docCount)} fields per doc] [took ${nsToSec(System.nanoTime() - startNS)} sec]"
                )
            } catch (e: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR [" + e.message + "]")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        /** Test docvalues.  */
        @Throws(IOException::class)
        fun testDocValues(
            reader: CodecReader, infoStream: PrintStream, failFast: Boolean
        ): DocValuesStatus {
            val startNS: Long = System.nanoTime()

            val status = DocValuesStatus()
            try {
                if (infoStream != null) {
                    infoStream.print("    test: docvalues...........")
                }
                var dvReader: DocValuesProducer? = reader.docValuesReader
                if (dvReader != null) {
                    dvReader = dvReader.mergeInstance
                }
                for (fieldInfo in reader.fieldInfos) {
                    if (fieldInfo.docValuesType != DocValuesType.NONE) {
                        status.totalValueFields++
                        checkDocValues(fieldInfo, dvReader!!, status)
                    }
                }

                msg(
                    infoStream,
                    "OK [${status.totalValueFields} docvalues fields; ${status.totalBinaryFields} BINARY; ${status.totalNumericFields} NUMERIC; ${status.totalSortedFields} SORTED; ${status.totalSortedNumericFields} SORTED_NUMERIC; ${status.totalSortedSetFields} SORTED_SET; ${status.totalSkippingIndex} SKIPPING INDEX] [took ${
                        nsToSec(
                            System.nanoTime() - startNS
                        )
                    } sec]"
                )
            } catch (e: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR [" + e.message + "]")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }
            return status
        }

        @Throws(IOException::class)
        private fun checkDocValueSkipper(
            fi: FieldInfo,
            skipper: DocValuesSkipper
        ) {
            val fieldName: String = fi.name
            if (skipper.maxDocID(0) != -1) {
                throw CheckIndexException(
                    ("binary dv iterator for field: "
                            + fieldName
                            + " should start at docID=-1, but got "
                            + skipper.maxDocID(0))
                )
            }
            if (skipper.docCount() > 0 && skipper.minValue() > skipper.maxValue()) {
                throw CheckIndexException(
                    ("skipper dv iterator for field: "
                            + fieldName
                            + " reports wrong global value range, got  "
                            + skipper.minValue()
                            + " > "
                            + skipper.maxValue())
                )
            }
            var docCount = 0
            var doc: Int
            while (true) {
                doc = skipper.maxDocID(0) + 1
                skipper.advance(doc)
                if (skipper.maxDocID(0) == DocIdSetIterator.NO_MORE_DOCS) {
                    break
                }
                if (skipper.minDocID(0) < doc) {
                    throw CheckIndexException(
                        ("skipper dv iterator for field: "
                                + fieldName
                                + " reports wrong minDocID, got "
                                + skipper.minDocID(0)
                                + " < "
                                + doc)
                    )
                }
                val levels: Int = skipper.numLevels()
                for (level in 0..<levels) {
                    if (skipper.minDocID(level) > skipper.maxDocID(level)) {
                        throw CheckIndexException(
                            ("skipper dv iterator for field: "
                                    + fieldName
                                    + " reports wrong doc range, got "
                                    + skipper.minDocID(level)
                                    + " > "
                                    + skipper.maxDocID(level))
                        )
                    }
                    if (skipper.minValue() > skipper.minValue(level)) {
                        throw CheckIndexException(
                            ("skipper dv iterator for field: "
                                    + fieldName
                                    + " : global minValue  "
                                    + skipper.minValue()
                                    + " , got  "
                                    + skipper.minValue(level))
                        )
                    }
                    if (skipper.maxValue() < skipper.maxValue(level)) {
                        throw CheckIndexException(
                            ("skipper dv iterator for field: "
                                    + fieldName
                                    + " : global maxValue  "
                                    + skipper.maxValue()
                                    + " , got  "
                                    + skipper.maxValue(level))
                        )
                    }
                    if (skipper.minValue(level) > skipper.maxValue(level)) {
                        throw CheckIndexException(
                            ("skipper dv iterator for field: "
                                    + fieldName
                                    + " reports wrong value range, got  "
                                    + skipper.minValue(level)
                                    + " > "
                                    + skipper.maxValue(level))
                        )
                    }
                }
                docCount += skipper.docCount(0)
            }
            if (skipper.docCount() != docCount) {
                throw CheckIndexException(
                    ("skipper dv iterator for field: "
                            + fieldName
                            + " inconsistent docCount, got "
                            + skipper.docCount()
                            + " != "
                            + docCount)
                )
            }
        }

        @Throws(IOException::class)
        private fun checkDVIterator(fi: FieldInfo, producer: DocValuesIteratorSupplier) {
            val field: String = fi.name

            // Check advance
            var it1: DocValuesIterator = producer.get(fi)
            var it2: DocValuesIterator = producer.get(fi)
            var i = 0
            run {
                var doc: Int = it1.nextDoc()
                while (true) {
                    if (i++ % 10 == 1) {
                        var doc2: Int = it2.advance(doc - 1)
                        if (doc2 < doc - 1) {
                            throw CheckIndexException(
                                ("dv iterator field="
                                        + field
                                        + ": doc="
                                        + (doc - 1)
                                        + " went backwords (got: "
                                        + doc2
                                        + ")")
                            )
                        }
                        if (doc2 == doc - 1) {
                            doc2 = it2.nextDoc()
                        }
                        if (doc2 != doc) {
                            throw CheckIndexException(
                                ("dv iterator field="
                                        + field
                                        + ": doc="
                                        + doc
                                        + " was not found through advance() (got: "
                                        + doc2
                                        + ")")
                            )
                        }
                        if (it2.docID() != doc) {
                            throw CheckIndexException(
                                ("dv iterator field="
                                        + field
                                        + ": doc="
                                        + doc
                                        + " reports wrong doc ID (got: "
                                        + it2.docID()
                                        + ")")
                            )
                        }
                    }

                    if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                        break
                    }
                    doc = it1.nextDoc()
                }
            }

            // Check advanceExact
            it1 = producer.get(fi)
            it2 = producer.get(fi)
            i = 0
            var lastDoc = -1
            var doc: Int = it1.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                if (i++ % 13 == 1) {
                    val found: Boolean = it2.advanceExact(doc - 1)
                    if ((doc - 1 == lastDoc) != found) {
                        throw CheckIndexException(
                            ("dv iterator field="
                                    + field
                                    + ": doc="
                                    + (doc - 1)
                                    + " disagrees about whether document exists (got: "
                                    + found
                                    + ")")
                        )
                    }
                    if (it2.docID() != doc - 1) {
                        throw CheckIndexException(
                            ("dv iterator field="
                                    + field
                                    + ": doc="
                                    + (doc - 1)
                                    + " reports wrong doc ID (got: "
                                    + it2.docID()
                                    + ")")
                        )
                    }

                    val found2: Boolean = it2.advanceExact(doc - 1)
                    if (found != found2) {
                        throw CheckIndexException(
                            "dv iterator field=" + field + ": doc=" + (doc - 1) + " has unstable advanceExact"
                        )
                    }

                    if (i % 2 == 0) {
                        val doc2: Int = it2.nextDoc()
                        if (doc != doc2) {
                            throw CheckIndexException(
                                ("dv iterator field="
                                        + field
                                        + ": doc="
                                        + doc
                                        + " was not found through advance() (got: "
                                        + doc2
                                        + ")")
                            )
                        }
                        if (it2.docID() != doc) {
                            throw CheckIndexException(
                                ("dv iterator field="
                                        + field
                                        + ": doc="
                                        + doc
                                        + " reports wrong doc ID (got: "
                                        + it2.docID()
                                        + ")")
                            )
                        }
                    }
                }

                lastDoc = doc
                doc = it1.nextDoc()
            }
        }

        @Throws(IOException::class)
        private fun checkBinaryDocValues(
            fieldName: String,
            bdv: BinaryDocValues,
            bdv2: BinaryDocValues
        ) {
            if (bdv.docID() != -1) {
                throw CheckIndexException(
                    ("binary dv iterator for field: "
                            + fieldName
                            + " should start at docID=-1, but got "
                            + bdv.docID())
                )
            }
            // TODO: we could add stats to DVs, e.g. total doc count w/ a value for this field
            var doc: Int = bdv.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                val value: BytesRef = bdv.binaryValue()!!
                value.isValid()

                if (!bdv2.advanceExact(doc)) {
                    throw CheckIndexException("advanceExact did not find matching doc ID: $doc")
                }
                val value2: BytesRef? = bdv2.binaryValue()
                if (value != value2) {
                    throw CheckIndexException(
                        "nextDoc and advanceExact report different values: $value != $value2"
                    )
                }
                doc = bdv.nextDoc()
            }
        }

        @Throws(IOException::class)
        private fun checkSortedDocValues(
            fieldName: String,
            dv: SortedDocValues,
            dv2: SortedDocValues
        ) {
            if (dv.docID() != -1) {
                throw CheckIndexException(
                    ("sorted dv iterator for field: "
                            + fieldName
                            + " should start at docID=-1, but got "
                            + dv.docID())
                )
            }
            val maxOrd: Int = dv.valueCount - 1
            val seenOrds = FixedBitSet(dv.valueCount)
            var maxOrd2 = -1
            var doc: Int = dv.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                val ord: Int = dv.ordValue()
                if (ord == -1) {
                    throw CheckIndexException("dv for field: $fieldName has -1 ord")
                } else if (ord < -1 || ord > maxOrd) {
                    throw CheckIndexException("ord out of bounds: $ord")
                } else {
                    maxOrd2 = max(maxOrd2, ord)
                    seenOrds.set(ord)
                }

                if (!dv2.advanceExact(doc)) {
                    throw CheckIndexException("advanceExact did not find matching doc ID: $doc")
                }
                val ord2: Int = dv2.ordValue()
                if (ord != ord2) {
                    throw CheckIndexException(
                        "nextDoc and advanceExact report different ords: $ord != $ord2"
                    )
                }
                doc = dv.nextDoc()
            }
            if (maxOrd != maxOrd2) {
                throw CheckIndexException(
                    ("dv for field: "
                            + fieldName
                            + " reports wrong maxOrd="
                            + maxOrd
                            + " but this is not the case: "
                            + maxOrd2)
                )
            }
            if (seenOrds.cardinality() != dv.valueCount) {
                throw CheckIndexException(
                    ("dv for field: "
                            + fieldName
                            + " has holes in its ords, valueCount="
                            + dv.valueCount
                            + " but only used: "
                            + seenOrds.cardinality())
                )
            }
            var lastValue: BytesRef? = null
            for (i in 0..maxOrd) {
                val term: BytesRef = dv.lookupOrd(i)!!
                term.isValid()
                if (lastValue != null) {
                    if (term <= lastValue) {
                        throw CheckIndexException(
                            "dv for field: $fieldName has ords out of order: $lastValue >=$term"
                        )
                    }
                }
                lastValue = BytesRef.deepCopyOf(term)
            }
        }

        @Throws(IOException::class)
        private fun checkSortedSetDocValues(
            fieldName: String,
            dv: SortedSetDocValues,
            dv2: SortedSetDocValues
        ) {
            val maxOrd: Long = dv.valueCount - 1
            val seenOrds = LongBitSet(dv.valueCount)
            var maxOrd2: Long = -1
            var docID: Int = dv.nextDoc()
            while (docID != DocIdSetIterator.NO_MORE_DOCS) {
                val count: Int = dv.docValueCount()
                if (count == 0) {
                    throw CheckIndexException(
                        ("sortedset dv for field: "
                                + fieldName
                                + " returned docValueCount=0 for docID="
                                + docID)
                    )
                }
                if (!dv2.advanceExact(docID)) {
                    throw CheckIndexException("advanceExact did not find matching doc ID: $docID")
                }
                val count2: Int = dv2.docValueCount()
                if (count != count2) {
                    throw CheckIndexException(
                        "advanceExact reports different value count: $count != $count2"
                    )
                }
                var lastOrd: Long = -1
                var ordCount = 0
                for (i in 0..<count) {
                    if (count != dv.docValueCount()) {
                        throw CheckIndexException(
                            ("value count changed from "
                                    + count
                                    + " to "
                                    + dv.docValueCount()
                                    + " during iterating over all values")
                        )
                    }
                    val ord: Long = dv.nextOrd()
                    val ord2: Long = dv2.nextOrd()
                    if (ord != ord2) {
                        throw CheckIndexException(
                            "advanceExact reports different value: $ord != $ord2"
                        )
                    }
                    if (ord <= lastOrd) {
                        throw CheckIndexException(
                            "ords out of order: $ord <= $lastOrd for doc: $docID"
                        )
                    }
                    if (ord !in 0..maxOrd) {
                        throw CheckIndexException("ord out of bounds: $ord")
                    }
                    lastOrd = ord
                    maxOrd2 = max(maxOrd2, ord)
                    seenOrds.set(ord)
                    ordCount++
                }
                if (dv.docValueCount() != dv2.docValueCount()) {
                    throw CheckIndexException(
                        ("dv and dv2 report different values count after iterating over all values: "
                                + dv.docValueCount()
                                + " != "
                                + dv2.docValueCount())
                    )
                }
                if (ordCount == 0) {
                    throw CheckIndexException(
                        "dv for field: $fieldName returned docID=$docID yet has no ordinals"
                    )
                }
                docID = dv.nextDoc()
            }
            if (maxOrd != maxOrd2) {
                throw CheckIndexException(
                    ("dv for field: "
                            + fieldName
                            + " reports wrong maxOrd="
                            + maxOrd
                            + " but this is not the case: "
                            + maxOrd2)
                )
            }
            if (seenOrds.cardinality() != dv.valueCount) {
                throw CheckIndexException(
                    ("dv for field: "
                            + fieldName
                            + " has holes in its ords, valueCount="
                            + dv.valueCount
                            + " but only used: "
                            + seenOrds.cardinality())
                )
            }

            var lastValue: BytesRef? = null
            for (i in 0..maxOrd) {
                val term: BytesRef = dv.lookupOrd(i)!!
                assert(term.isValid())
                if (lastValue != null) {
                    if (term <= lastValue) {
                        throw CheckIndexException(
                            "dv for field: $fieldName has ords out of order: $lastValue >=$term"
                        )
                    }
                }
                lastValue = BytesRef.deepCopyOf(term)
            }
        }

        @Throws(IOException::class)
        private fun checkSortedNumericDocValues(
            fieldName: String,
            ndv: SortedNumericDocValues,
            ndv2: SortedNumericDocValues
        ) {
            if (ndv.docID() != -1) {
                throw CheckIndexException(
                    ("dv iterator for field: "
                            + fieldName
                            + " should start at docID=-1, but got "
                            + ndv.docID())
                )
            }
            // TODO: we could add stats to DVs, e.g. total doc count w/ a value for this field
            var docID: Int = ndv.nextDoc()
            while (docID != DocIdSetIterator.NO_MORE_DOCS) {
                val count: Int = ndv.docValueCount()
                if (count == 0) {
                    throw CheckIndexException(
                        ("sorted numeric dv for field: "
                                + fieldName
                                + " returned docValueCount=0 for docID="
                                + docID)
                    )
                }
                if (!ndv2.advanceExact(docID)) {
                    throw CheckIndexException("advanceExact did not find matching doc ID: $docID")
                }
                val count2: Int = ndv2.docValueCount()
                if (count != count2) {
                    throw CheckIndexException(
                        "advanceExact reports different value count: $count != $count2"
                    )
                }
                var previous = Long.MIN_VALUE
                for (j in 0..<count) {
                    val value: Long = ndv.nextValue()
                    if (value < previous) {
                        throw CheckIndexException(
                            "values out of order: $value < $previous for doc: $docID"
                        )
                    }
                    previous = value

                    val value2: Long = ndv2.nextValue()
                    if (value != value2) {
                        throw CheckIndexException(
                            "advanceExact reports different value: $value != $value2"
                        )
                    }
                }
                docID = ndv.nextDoc()
            }
        }

        @Throws(IOException::class)
        private fun checkNumericDocValues(
            fieldName: String,
            ndv: NumericDocValues,
            ndv2: NumericDocValues
        ) {
            if (ndv.docID() != -1) {
                throw CheckIndexException(
                    ("dv iterator for field: "
                            + fieldName
                            + " should start at docID=-1, but got "
                            + ndv.docID())
                )
            }
            // TODO: we could add stats to DVs, e.g. total doc count w/ a value for this field
            var doc: Int = ndv.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                val value: Long = ndv.longValue()

                if (!ndv2.advanceExact(doc)) {
                    throw CheckIndexException("advanceExact did not find matching doc ID: $doc")
                }
                val value2: Long = ndv2.longValue()
                if (value != value2) {
                    throw CheckIndexException(
                        "advanceExact reports different value: $value != $value2"
                    )
                }
                doc = ndv.nextDoc()
            }
        }

        @Throws(Exception::class)
        private fun checkDocValues(
            fi: FieldInfo,
            dvReader: DocValuesProducer,
            status: DocValuesStatus
        ) {
            if (fi.docValuesSkipIndexType() !== DocValuesSkipIndexType.NONE) {
                status.totalSkippingIndex++
                checkDocValueSkipper(fi, dvReader.getSkipper(fi)!!)
            }
            when (fi.docValuesType) {
                DocValuesType.SORTED -> {
                    status.totalSortedFields++
                    checkDVIterator(
                        fi
                    ) { field: FieldInfo ->
                        dvReader.getSorted(field)
                    }
                    checkSortedDocValues(fi.name, dvReader.getSorted(fi), dvReader.getSorted(fi))
                }

                DocValuesType.SORTED_NUMERIC -> {
                    status.totalSortedNumericFields++
                    checkDVIterator(
                        fi
                    ) { field: FieldInfo ->
                        dvReader.getSortedNumeric(field)
                    }
                    checkSortedNumericDocValues(
                        fi.name, dvReader.getSortedNumeric(fi), dvReader.getSortedNumeric(fi)
                    )
                }

                DocValuesType.SORTED_SET -> {
                    status.totalSortedSetFields++
                    checkDVIterator(
                        fi
                    ) { field: FieldInfo ->
                        dvReader.getSortedSet(field)
                    }
                    checkSortedSetDocValues(fi.name, dvReader.getSortedSet(fi), dvReader.getSortedSet(fi))
                }

                DocValuesType.BINARY -> {
                    status.totalBinaryFields++
                    checkDVIterator(
                        fi
                    ) { field: FieldInfo ->
                        dvReader.getBinary(field)
                    }
                    checkBinaryDocValues(fi.name, dvReader.getBinary(fi), dvReader.getBinary(fi))
                }

                DocValuesType.NUMERIC -> {
                    status.totalNumericFields++
                    checkDVIterator(
                        fi
                    ) { field: FieldInfo ->
                        dvReader.getNumeric(field)
                    }
                    checkNumericDocValues(fi.name, dvReader.getNumeric(fi), dvReader.getNumeric(fi))
                }

                DocValuesType.NONE -> throw AssertionError()
                else -> throw AssertionError()
            }
        }

        /** Test term vectors.  */
        @Throws(IOException::class)
        fun testTermVectors(
            reader: CodecReader,
            infoStream: PrintStream,
            verbose: Boolean = false,
            level: Int = Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS,
            failFast: Boolean = false
        ): Status.TermVectorStatus {
            val startNS: Long = System.nanoTime()
            val status: Status.TermVectorStatus = Status.TermVectorStatus()
            val fieldInfos: FieldInfos = reader.fieldInfos

            try {
                if (infoStream != null) {
                    infoStream.print("    test: term vectors........")
                }

                var postings: PostingsEnum? = null

                // Only used if the Level is high enough to include slow checks:
                var postingsDocs: PostingsEnum? = null

                val liveDocs: Bits? = reader.liveDocs

                var postingsFields: FieldsProducer?
                // TODO: testTermsIndex
                if (level >= Level.MIN_LEVEL_FOR_SLOW_CHECKS) {
                    postingsFields = reader.postingsReader
                    if (postingsFields != null) {
                        postingsFields = postingsFields.mergeInstance
                    }
                } else {
                    postingsFields = null
                }

                var vectorsReader: TermVectorsReader? = reader.termVectorsReader

                if (vectorsReader != null) {
                    vectorsReader = vectorsReader.mergeInstance
                    for (j in 0..<reader.maxDoc()) {
                        if ((j and 0x03) == 0) {
                            vectorsReader.prefetch(j)
                        }
                        // Intentionally pull/visit (but don't count in
                        // stats) deleted documents to make sure they too
                        // are not corrupt:
                        val tfv: Fields? = vectorsReader.get(j)

                        // TODO: can we make a IS(FIR) that searches just
                        // this term vector... to pass for searcher
                        if (tfv != null) {
                            // First run with no deletions:
                            checkFields(
                                tfv, null, 1, fieldInfos, null,
                                doPrint = false,
                                isVectors = true,
                                infoStream = infoStream,
                                verbose = verbose,
                                level = level
                            )

                            // Only agg stats if the doc is live:
                            val doStats = liveDocs == null || liveDocs.get(j)

                            if (doStats) {
                                status.docCount++
                            }

                            for (field in tfv) {
                                if (doStats) {
                                    status.totVectors++
                                }

                                // Make sure FieldInfo thinks this field is vector'd:
                                val fieldInfo: FieldInfo = fieldInfos.fieldInfo(field)!!
                                if (!fieldInfo.hasTermVectors()) {
                                    throw CheckIndexException(
                                        ("docID="
                                                + j
                                                + " has term vectors for field="
                                                + field
                                                + " but FieldInfo has storeTermVector=false")
                                    )
                                }

                                if (level >= Level.MIN_LEVEL_FOR_SLOW_CHECKS) {
                                    val terms: Terms = tfv.terms(field)!!
                                    val termsEnum: TermsEnum = terms.iterator()
                                    val postingsHasFreq = fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS
                                    val postingsHasPayload: Boolean = fieldInfo.hasPayloads()
                                    val vectorsHasPayload: Boolean = terms.hasPayloads()

                                    if (postingsFields == null) {
                                        throw CheckIndexException(
                                            "vector field=$field does not exist in postings; doc=$j"
                                        )
                                    }
                                    val postingsTerms: Terms? = postingsFields.terms(field)
                                    if (postingsTerms == null) {
                                        throw CheckIndexException(
                                            "vector field=$field does not exist in postings; doc=$j"
                                        )
                                    }
                                    val postingsTermsEnum: TermsEnum = postingsTerms.iterator()

                                    val hasProx = terms.hasOffsets() || terms.hasPositions()
                                    var seekExactCounter = 0
                                    var term: BytesRef?
                                    while ((termsEnum.next().also { term = it }) != null) {
                                        // This is the term vectors:

                                        postings = termsEnum.postings(
                                            postings,
                                            PostingsEnum.ALL.toInt()
                                        )

                                        val termExists: Boolean
                                        if ((seekExactCounter++ and 0x01) == 0) {
                                            termExists = postingsTermsEnum.seekExact(term!!)
                                        } else {
                                            val termExistsSupplier: IOBooleanSupplier? =
                                                postingsTermsEnum.prepareSeekExact(term!!)
                                            termExists = termExistsSupplier != null && termExistsSupplier.get()
                                        }
                                        if (!termExists) {
                                            throw CheckIndexException(
                                                ("vector term="
                                                        + term
                                                        + " field="
                                                        + field
                                                        + " doc="
                                                        + j
                                                        + " does not exist in postings (null or not found)")
                                            )
                                        }

                                        // This is the inverted index ("real" postings):
                                        postingsDocs = postingsTermsEnum.postings(
                                            postingsDocs,
                                            PostingsEnum.ALL.toInt()
                                        )

                                        val advanceDoc: Int = postingsDocs.advance(j)
                                        if (advanceDoc != j) {
                                            throw CheckIndexException(
                                                ("vector term="
                                                        + term
                                                        + " field="
                                                        + field
                                                        + ": doc="
                                                        + j
                                                        + " was not found in postings (got: "
                                                        + advanceDoc
                                                        + ")")
                                            )
                                        }

                                        val doc: Int = postings.nextDoc()

                                        if (doc != 0) {
                                            throw CheckIndexException(
                                                "vector for doc $j didn't return docID=0: got docID=$doc"
                                            )
                                        }

                                        if (postingsHasFreq) {
                                            val tf: Int = postings.freq()
                                            if (postingsHasFreq && postingsDocs.freq() != tf) {
                                                throw CheckIndexException(
                                                    ("vector term="
                                                            + term
                                                            + " field="
                                                            + field
                                                            + " doc="
                                                            + j
                                                            + ": freq="
                                                            + tf
                                                            + " differs from postings freq="
                                                            + postingsDocs.freq())
                                                )
                                            }

                                            // Term vectors has prox
                                            if (hasProx) {
                                                for (i in 0..<tf) {
                                                    val pos: Int = postings.nextPosition()
                                                    if (postingsTerms.hasPositions()) {
                                                        val postingsPos: Int = postingsDocs.nextPosition()
                                                        if (terms.hasPositions() && pos != postingsPos) {
                                                            throw CheckIndexException(
                                                                ("vector term="
                                                                        + term
                                                                        + " field="
                                                                        + field
                                                                        + " doc="
                                                                        + j
                                                                        + ": pos="
                                                                        + pos
                                                                        + " differs from postings pos="
                                                                        + postingsPos)
                                                            )
                                                        }
                                                    }

                                                    // Call the methods to at least make
                                                    // sure they don't throw exc:
                                                    val startOffset: Int = postings.startOffset()
                                                    val endOffset: Int = postings.endOffset()

                                                    // TODO: these are too anal...
                                                    /*
                        if (endOffset < startOffset) {
                        throw new RuntimeException("vector startOffset=" + startOffset + " is > endOffset=" + endOffset);
                        }
                        if (startOffset < lastStartOffset) {
                        throw new RuntimeException("vector startOffset=" + startOffset + " is < prior startOffset=" + lastStartOffset);
                        }
                        lastStartOffset = startOffset;
                         */
                                                    if (startOffset != -1 && endOffset != -1 && postingsTerms.hasOffsets()) {
                                                        val postingsStartOffset: Int = postingsDocs.startOffset()
                                                        val postingsEndOffset: Int = postingsDocs.endOffset()
                                                        if (startOffset != postingsStartOffset) {
                                                            throw CheckIndexException(
                                                                ("vector term="
                                                                        + term
                                                                        + " field="
                                                                        + field
                                                                        + " doc="
                                                                        + j
                                                                        + ": startOffset="
                                                                        + startOffset
                                                                        + " differs from postings startOffset="
                                                                        + postingsStartOffset)
                                                            )
                                                        }
                                                        if (endOffset != postingsEndOffset) {
                                                            throw CheckIndexException(
                                                                ("vector term="
                                                                        + term
                                                                        + " field="
                                                                        + field
                                                                        + " doc="
                                                                        + j
                                                                        + ": endOffset="
                                                                        + endOffset
                                                                        + " differs from postings endOffset="
                                                                        + postingsEndOffset)
                                                            )
                                                        }
                                                    }

                                                    val payload: BytesRef? = postings.payload

                                                    if (payload != null) {
                                                        assert(vectorsHasPayload)
                                                    }

                                                    if (postingsHasPayload && vectorsHasPayload) {
                                                        if (payload == null) {
                                                            // we have payloads, but not at this position.
                                                            // postings has payloads too, it should not have one at this position
                                                            if (postingsDocs.payload != null) {
                                                                throw CheckIndexException(
                                                                    ("vector term="
                                                                            + term
                                                                            + " field="
                                                                            + field
                                                                            + " doc="
                                                                            + j
                                                                            + " has no payload but postings does: "
                                                                            + postingsDocs.payload)
                                                                )
                                                            }
                                                        } else {
                                                            // we have payloads, and one at this position
                                                            // postings should also have one at this position, with the same bytes.
                                                            if (postingsDocs.payload == null) {
                                                                throw CheckIndexException(
                                                                    ("vector term="
                                                                            + term
                                                                            + " field="
                                                                            + field
                                                                            + " doc="
                                                                            + j
                                                                            + " has payload="
                                                                            + payload
                                                                            + " but postings does not.")
                                                                )
                                                            }
                                                            val postingsPayload: BytesRef? =
                                                                postingsDocs.payload
                                                            if (payload != postingsPayload) {
                                                                throw CheckIndexException(
                                                                    ("vector term="
                                                                            + term
                                                                            + " field="
                                                                            + field
                                                                            + " doc="
                                                                            + j
                                                                            + " has payload="
                                                                            + payload
                                                                            + " but differs from postings payload="
                                                                            + postingsPayload)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val vectorAvg = if (status.docCount == 0) 0f else status.totVectors / status.docCount.toFloat()
                msg(
                    infoStream,
                    "OK [${status.totVectors} total term vector count; avg $vectorAvg term/freq vector fields per doc] [took ${
                        nsToSec(
                            System.nanoTime() - startNS
                        )
                    } sec]"
                )
            } catch (e: Throwable) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR [" + e.message + "]")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        private var assertsOn = false

        private fun testAsserts(): Boolean {
            assertsOn = true
            return true
        }

        /**
         * Check whether asserts are enabled or not.
         *
         * @return true iff asserts are enabled
         */
        fun assertsOn(): Boolean {
            assert(testAsserts())
            return assertsOn
        }

        /**
         * Command-line interface to check and exorcise corrupt segments from an index.
         *
         *
         * Run it like this:
         *
         * <pre>
         * java -ea:org.apache.lucene... org.apache.lucene.index.CheckIndex pathToIndex [-exorcise] [-verbose] [-segment X] [-segment Y]
        </pre> *
         *
         *
         *  * `-exorcise`: actually write a new segments_N file, removing any problematic
         * segments. *LOSES DATA*
         *  * `-segment X`: only check the specified segment(s). This can be specified
         * multiple times, to check more than one segment: `-segment _2 * -segment _a`.
         * You can't use this with the -exorcise option.
         *
         *
         *
         * **WARNING**: `-exorcise` should only be used on an emergency basis as it will
         * cause documents (perhaps many) to be permanently removed from the index. Always make a backup
         * copy of your index before running this! Do not run this tool on an index that is actively being
         * written to. You have been warned!
         *
         *
         * Run without -exorcise, this tool will open the index, report version information and report
         * any exceptions it hits and what action it would take if -exorcise were specified. With
         * -exorcise, this tool will remove any segments that have issues and write a new segments_N file.
         * This means all documents contained in the affected segments will be removed.
         *
         *
         * This tool exits with exit code 1 if the index cannot be opened or has any corruption, else
         * 0.
         */
        @Throws(IOException::class, InterruptedException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = doMain(args)
            exitProcess(exitCode) /*System.exit(exitCode)*/
        }

        // actual main: returns exit code instead of terminating JVM (for easy testing)
        // "System.out required: command line tool"
        @Throws(IOException::class, InterruptedException::class)
        private fun doMain(args: Array<String>): Int {
            val opts: Options
            try {
                opts = parseOptions(args)
            } catch (e: IllegalArgumentException) {
                println(e.message)
                return 1
            }

            if (!assertsOn()) {
                println(
                    "\nNOTE: testing will be more thorough if you run java with '-ea:org.apache.lucene...', so assertions are enabled"
                )
            }

            println("\nOpening index @ " + opts.indexPath + "\n")
            val directory: Directory
            val path: Path = opts.indexPath!!.toPath()
            directory = try {
                if (opts.dirImpl == null) {
                    FSDirectory.open(path)
                } else {
                    CommandLineUtil.newFSDirectory(opts.dirImpl!!, path)
                }
            } catch (t: Throwable) {
                println("ERROR: could not open directory \"" + opts.indexPath + "\"; exiting")
                /*t.printStackTrace(System.out)*/
                println(t.stackTraceToString())
                return 1
            }

            directory.use { dir ->
                CheckIndex(dir).use { checker ->

                    val output = ByteArrayOutputStream()
                    opts.out = /*System.out*/ PrintStream(output, true, StandardCharsets.UTF_8)
                    return checker.doCheck(opts)
                }
            }
        }

        /**
         * Parse command line args into fields
         *
         * @param args The command line arguments
         * @return An Options struct
         * @throws IllegalArgumentException if any of the CLI args are invalid
         *
         * System.err required: command line tool
         */
        fun parseOptions(args: Array<String>): Options {
            val opts = Options()

            var i = 0
            while (i < args.size) {
                val arg = args[i]
                when (arg) {
                    "-level" -> {
                        require(i != args.size - 1) { "ERROR: missing value for -level option" }
                        i++
                        val level = args[i].toInt()
                        Level.checkIfLevelInBounds(level)
                        opts.level = level
                    }
                    "-exorcise" -> {
                        opts.doExorcise = true
                    }
                    "-verbose" -> {
                        opts.verbose = true
                    }
                    "-segment" -> {
                        require(i != args.size - 1) { "ERROR: missing name for -segment option" }
                        i++
                        opts.onlySegments!!.add(args[i])
                    }
                    "-dir-impl" -> {
                        require(i != args.size - 1) { "ERROR: missing value for -dir-impl option" }
                        i++
                        opts.dirImpl = args[i]
                    }
                    "-threadCount" -> {
                        require(i != args.size - 1) { "-threadCount requires a following number" }
                        i++
                        opts.threadCount = args[i].toInt()
                        require(opts.threadCount > 0) { "-threadCount requires a number larger than 0, but got: " + opts.threadCount }
                    }
                    else -> {
                        require(opts.indexPath == null) { "ERROR: unexpected extra argument '" + args[i] + "'" }
                        opts.indexPath = args[i]
                    }
                }
                i++
            }

            requireNotNull(opts.indexPath) {
                ("\nERROR: index path not specified"
                        + "\nUsage: java org.apache.lucene.index.CheckIndex pathToIndex [-exorcise] [-level X] [-segment X] [-segment Y] [-threadCount X] [-dir-impl X]\n"
                        + "\n"
                        + "  -exorcise: actually write a new segments_N file, removing any problematic segments\n"
                        + "  -level X: sets the detail level of the check. The higher the value, the more checks are done.\n"
                        + "         1 - (Default) Checksum checks only.\n"
                        + "         2 - All level 1 checks + logical integrity checks.\n"
                        + "         3 - All level 2 checks + slow checks.\n"
                        + "  -codec X: when exorcising, codec to write the new segments_N file with\n"
                        + "  -verbose: print additional details\n"
                        + "  -segment X: only check the specified segments.  This can be specified multiple\n"
                        + "              times, to check more than one segment, e.g. '-segment _2 -segment _a'.\n"
                        + "              You can't use this with the -exorcise option\n"
                        + "  -threadCount X: number of threads used to check index concurrently.\n"
                        + "                  When not specified, this will default to the number of CPU cores.\n"
                        + "                  When '-threadCount 1' is used, index checking will be performed sequentially.\n"
                        + "  -dir-impl X: use a specific "
                        + FSDirectory::class.simpleName
                        + " implementation. "
                        + "If no package is specified the "
                        + /*FSDirectory::class.java.getPackage().getName()*/ "org.apache.lucene.store"
                        + " package will be used.\n"
                        + "CheckIndex only verifies file checksums as default.\n"
                        + "Use -level with value of '2' or higher if you also want to check segment file contents.\n\n"
                        + "**WARNING**: -exorcise *LOSES DATA*. This should only be used on an emergency basis as it will cause\n"
                        + "documents (perhaps many) to be permanently removed from the index.  Always make\n"
                        + "a backup copy of your index before running this!  Do not run this tool on an index\n"
                        + "that is actively being written to.  You have been warned!\n"
                        + "\n"
                        + "Run without -exorcise, this tool will open the index, report version information\n"
                        + "and report any exceptions it hits and what action it would take if -exorcise were\n"
                        + "specified.  With -exorcise, this tool will remove any segments that have issues and\n"
                        + "write a new segments_N file.  This means all documents contained in the affected\n"
                        + "segments will be removed.\n"
                        + "\n"
                        + "This tool exits with exit code 1 if the index cannot be opened or has any\n"
                        + "corruption, else 0.\n")
            }

            if (opts.onlySegments!!.isEmpty()) {
                opts.onlySegments = null
            } else require(!opts.doExorcise) { "ERROR: cannot specify both -exorcise and -segment" }

            return opts
        }

        @Throws(IOException::class)
        private fun checkSoftDeletes(
            softDeletesField: String,
            info: SegmentCommitInfo,
            reader: SegmentReader,
            infoStream: PrintStream,
            failFast: Boolean
        ): Status.SoftDeletesStatus {
            val status: Status.SoftDeletesStatus = Status.SoftDeletesStatus()
            if (infoStream != null) infoStream.print("    test: check soft deletes.....")
            try {
                val softDeletes: Int =
                    PendingSoftDeletes.countSoftDeletes(
                        FieldExistsQuery.getDocValuesDocIdSetIterator(
                            softDeletesField,
                            reader
                        )!!,
                        reader.liveDocs!!
                    )
                if (softDeletes != info.getSoftDelCount()) {
                    throw CheckIndexException(
                        "actual soft deletes: " + softDeletes + " but expected: " + info.getSoftDelCount()
                    )
                }
            } catch (e: Exception) {
                if (failFast) {
                    throw IOUtils.rethrowAlways(e)
                }
                msg(infoStream, "ERROR [" + e.message + "]")
                status.error = e
                if (infoStream != null) {
                    e.printStackTrace(infoStream)
                }
            }

            return status
        }

        private fun nsToSec(ns: Long): Double {
            return ns / TimeUnit.SECONDS.toNanos(1).toDouble()
        }
    }
}
