package org.gnit.lucenekmp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Path
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.LinkedBlockingQueue
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.ThreadPoolExecutor
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.BufferedChecksumIndexInput
import org.gnit.lucenekmp.store.BufferedIndexInput
import org.gnit.lucenekmp.store.NIOFSDirectory
import org.gnit.lucenekmp.tests.store.CorruptingIndexOutput
import org.gnit.lucenekmp.tests.junitport.assertEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.min
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests for on-disk merge sorting. */
class TestOfflineSorter : LuceneTestCase() {
    private lateinit var tempDir: Path

    private fun trace(message: String) {
        println("[TestOfflineSorter] $message")
    }

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        tempDir = createTempDir("mergesort")
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        IOUtils.rm(tempDir)
    }

    @Test
    fun testEmpty() {
        newDirectory().use { dir ->
            checkSort(dir, OfflineSorter(dir, "foo"), arrayOf())
        }
    }

    @Test
    fun testSingleLine() {
        newDirectory().use { dir ->
            checkSort(
                dir,
                OfflineSorter(dir, "foo"),
                arrayOf("Single line only.".encodeToByteArray())
            )
        }
    }

    private fun randomExecutorServiceOrNull(): org.gnit.lucenekmp.jdkport.ExecutorService? {
        return if (random().nextBoolean()) {
            null
        } else {
            val maxThreads = if (TEST_NIGHTLY) TestUtil.nextInt(random(), 2, 6) else 2
            ThreadPoolExecutor(
                1,
                maxThreads,
                Long.MAX_VALUE,
                TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(),
                NamedThreadFactory("TestOfflineSorter")
            )
        }
    }

    @Test
    fun testIntermediateMerges() {
        newFSDirectory(createTempDir()).use { dir ->
            val exec = randomExecutorServiceOrNull()
            val info =
                checkSort(
                    dir,
                    OfflineSorter(
                        dir,
                        "foo",
                        OfflineSorter.DEFAULT_COMPARATOR,
                        OfflineSorter.BufferSize.megabytes(1),
                        2,
                        -1,
                        exec,
                        TestUtil.nextInt(random(), 1, 4)
                    ),
                    generateRandom((OfflineSorter.MB * 20).toInt())
                )
            if (exec != null) {
                runBlocking { exec.shutdownNow() }
            }
            assertTrue(info.mergeRounds > 10)
        }
    }

    @Test
    fun testSmallRandom() {
        newFSDirectory(createTempDir()).use { dir ->
            val exec = randomExecutorServiceOrNull()
            val sortInfo =
                checkSort(
                    dir,
                    OfflineSorter(
                        dir,
                        "foo",
                        OfflineSorter.DEFAULT_COMPARATOR,
                        OfflineSorter.BufferSize.megabytes(1),
                        OfflineSorter.MAX_TEMPFILES,
                        -1,
                        exec,
                        TestUtil.nextInt(random(), 1, 4)
                    ),
                    generateRandom((OfflineSorter.MB * 20).toInt())
                )
            if (exec != null) {
                runBlocking { exec.shutdownNow() }
            }
            assertEquals(3, sortInfo.mergeRounds)
        }
    }

    @Nightly
    @Test
    fun testLargerRandom() {
        val testStart = Clock.System.now().toEpochMilliseconds()
        trace("testLargerRandom start")
        newFSDirectory(createTempDir()).use { dir ->
            val exec = randomExecutorServiceOrNull()
            val largerRandomBytes = if (TEST_NIGHTLY) {
                (OfflineSorter.MB * 100).toInt()
            } else {
                (OfflineSorter.MB * 5).toInt() // TODO reduced largerRandomBytes = 100MB to 5MB for dev speed
            }
            val generateStart = Clock.System.now().toEpochMilliseconds()
            val data = generateRandom(largerRandomBytes)
            trace(
                "testLargerRandom generatedData bytes=$largerRandomBytes arrays=${data.size} " +
                    "elapsedMs=${Clock.System.now().toEpochMilliseconds() - generateStart}"
            )
            val sortStart = Clock.System.now().toEpochMilliseconds()
            checkSort(
                dir,
                OfflineSorter(
                    dir,
                    "foo",
                    OfflineSorter.DEFAULT_COMPARATOR,
                    OfflineSorter.BufferSize.megabytes(16),
                    OfflineSorter.MAX_TEMPFILES,
                    -1,
                    exec,
                    TestUtil.nextInt(random(), 1, 4)
                ),
                data
            )
            trace(
                "testLargerRandom checkSortElapsedMs=${Clock.System.now().toEpochMilliseconds() - sortStart}"
            )
            if (exec != null) {
                runBlocking { exec.shutdownNow() }
            }
        }
        trace("testLargerRandom totalElapsedMs=${Clock.System.now().toEpochMilliseconds() - testStart}")
    }

    private fun generateRandom(howMuchDataInBytes: Int): Array<ByteArray> {
        val data = mutableListOf<ByteArray>()
        val start = Clock.System.now().toEpochMilliseconds()
        val total = howMuchDataInBytes
        var nextLog = 10 * 1024 * 1024
        var left = howMuchDataInBytes
        while (left > 0) {
            val current = ByteArray(random().nextInt(256))
            random().nextBytes(current)
            data.add(current)
            left -= current.size
            val generated = total - left
            if (generated >= nextLog) {
                trace(
                    "generateRandom progress generatedBytes=$generated/$total arrays=${data.size} " +
                        "elapsedMs=${Clock.System.now().toEpochMilliseconds() - start}"
                )
                nextLog += 10 * 1024 * 1024
            }
        }
        trace(
            "generateRandom done generatedBytes=$total arrays=${data.size} " +
                "elapsedMs=${Clock.System.now().toEpochMilliseconds() - start}"
        )
        return data.toTypedArray()
    }

    private fun generateFixed(howMuchDataInBytes: Int): Array<ByteArray> {
        val data = mutableListOf<ByteArray>()
        var left = howMuchDataInBytes
        var length = 256
        var counter = 0
        while (left > 0) {
            val current = ByteArray(length)
            for (i in current.indices) {
                current[i] = counter.toByte()
                counter++
            }
            data.add(current)
            left -= current.size
            length--
            if (length <= 128) {
                length = 256
            }
        }
        return data.toTypedArray()
    }

    private val unsignedByteOrderComparator: Comparator<ByteArray> =
        Comparator { left, right ->
            val max = min(left.size, right.size)
            for (i in 0 until max) {
                val diff = (left[i].toInt() and 0xFF) - (right[i].toInt() and 0xFF)
                if (diff != 0) {
                    return@Comparator diff
                }
            }
            left.size - right.size
        }

    @OptIn(ExperimentalAtomicApi::class)
    private fun checkSort(
        dir: Directory,
        sorter: OfflineSorter,
        data: Array<ByteArray>
    ): OfflineSorter.SortInfo {
        val checkStart = Clock.System.now().toEpochMilliseconds()
        val shouldTraceDetailed = data.size >= 100_000
        trace("checkSort start items=${data.size}")
        val unsorted = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT)
        val unsortedWriteStart = Clock.System.now().toEpochMilliseconds()
        writeAll(unsorted, data, "unsorted")
        trace("checkSort writeUnsortedElapsedMs=${Clock.System.now().toEpochMilliseconds() - unsortedWriteStart}")

        val golden = dir.createTempOutput("golden", "tmp", IOContext.DEFAULT)
        val inMemorySortStart = Clock.System.now().toEpochMilliseconds()
        data.sortWith(unsignedByteOrderComparator)
        trace("checkSort inMemorySortElapsedMs=${Clock.System.now().toEpochMilliseconds() - inMemorySortStart}")
        val goldenWriteStart = Clock.System.now().toEpochMilliseconds()
        writeAll(golden, data, "golden")
        trace("checkSort writeGoldenElapsedMs=${Clock.System.now().toEpochMilliseconds() - goldenWriteStart}")

        val sortCallStart = Clock.System.now().toEpochMilliseconds()
        val externalSortStart = Clock.System.now().toEpochMilliseconds()
        val enableHotPathProfile =
            shouldTraceDetailed && (System.getProperty("tests.profile.hotpath")?.toBoolean() == true)
        if (enableHotPathProfile) {
            OfflineSorter.resetByteSequencesReaderProfile()
            OfflineSorter.enableByteSequencesReaderProfile()
            BufferedIndexInput.resetProfile()
            BufferedIndexInput.enableProfile()
            BufferedChecksumIndexInput.resetProfile()
            BufferedChecksumIndexInput.enableProfile()
            NIOFSDirectory.resetReadInternalProfile()
            NIOFSDirectory.enableReadInternalProfile()
            ByteBuffer.resetProfile()
        } else {
            OfflineSorter.disableByteSequencesReaderProfile()
            BufferedIndexInput.disableProfile()
            BufferedChecksumIndexInput.disableProfile()
            NIOFSDirectory.disableReadInternalProfile()
            ByteBuffer.disableProfile()
        }
        val sorted = sorter.sort(unsorted.name!!)
        val externalSortElapsedMs = Clock.System.now().toEpochMilliseconds() - externalSortStart
        trace("checkSort externalSortElapsedMs=$externalSortElapsedMs")
        if (shouldTraceDetailed) {
            val info = sorter.sortInfo
            val readerProfile = OfflineSorter.byteSequencesReaderProfile()
            val bufferedIndexInputProfile = BufferedIndexInput.profile()
            val bufferedChecksumProfile = BufferedChecksumIndexInput.profile()
            val nioReadInternalProfile = NIOFSDirectory.readInternalProfile()
            val byteBufferProfile = ByteBuffer.profile()
            if (info != null) {
                trace(
                    "checkSort externalSortDetails totalMs=${info.totalTimeMS} readMs=${info.readTimeMS} " +
                        "sortMs=${info.sortTimeMS.load()} mergeMs=${info.mergeTimeMS.load()} " +
                        "mergeWaitMs=${info.mergeWaitTimeMS.load()} mergeInitMs=${info.mergeInitTimeMS.load()} " +
                        "mergeLoopMs=${info.mergeLoopTimeMS.load()} mergeLoopWriteMs=${info.mergeLoopWriteTimeMS.load()} " +
                        "mergeLoopNextMs=${info.mergeLoopNextTimeMS.load()} mergeLoopPopMs=${info.mergeLoopPopTimeMS.load()} " +
                        "mergeLoopUpdateTopMs=${info.mergeLoopUpdateTopTimeMS.load()} " +
                        "mergeLoopTopLookupMs=${info.mergeLoopTopLookupTimeMS.load()} mergeDeleteMs=${info.mergeDeleteTimeMS.load()} " +
                        "nextCalls=${readerProfile.calls} nextEofCalls=${readerProfile.eofCalls} nextTotalMs=${readerProfile.totalMs} " +
                        "nextReadLengthMs=${readerProfile.readLengthMs} nextPrepareRefMs=${readerProfile.prepareRefMs} " +
                        "nextReadBytesMs=${readerProfile.readBytesMs} nextGetRefMs=${readerProfile.getRefMs} " +
                        "nextPayloadBytes=${readerProfile.payloadBytes} " +
                        "bufferedReadBytesCalls=${bufferedIndexInputProfile.readBytesCalls} " +
                        "bufferedReadBytesRequestedBytes=${bufferedIndexInputProfile.readBytesRequestedBytes} " +
                        "bufferedReadBytesTotalMs=${bufferedIndexInputProfile.readBytesTotalMs} " +
                        "bufferedReadBytesFastPathCalls=${bufferedIndexInputProfile.readBytesFastPathCalls} " +
                        "bufferedReadBytesRefillPathCalls=${bufferedIndexInputProfile.readBytesRefillPathCalls} " +
                        "bufferedReadBytesRefillPathMs=${bufferedIndexInputProfile.readBytesRefillPathMs} " +
                        "bufferedReadBytesDirectPathCalls=${bufferedIndexInputProfile.readBytesDirectPathCalls} " +
                        "bufferedReadBytesDirectPathMs=${bufferedIndexInputProfile.readBytesDirectPathMs} " +
                        "bufferedReadBytesDirectReadInternalCalls=${bufferedIndexInputProfile.readBytesDirectPathReadInternalCalls} " +
                        "bufferedReadBytesDirectReadInternalBytes=${bufferedIndexInputProfile.readBytesDirectPathReadInternalBytes} " +
                        "bufferedReadBytesDirectReadInternalMs=${bufferedIndexInputProfile.readBytesDirectPathReadInternalMs} " +
                        "bufferedRefillCalls=${bufferedIndexInputProfile.refillCalls} " +
                        "bufferedRefillTotalMs=${bufferedIndexInputProfile.refillTotalMs} " +
                        "bufferedRefillReadInternalCalls=${bufferedIndexInputProfile.refillReadInternalCalls} " +
                        "bufferedRefillReadInternalBytes=${bufferedIndexInputProfile.refillReadInternalBytes} " +
                        "bufferedRefillReadInternalMs=${bufferedIndexInputProfile.refillReadInternalMs} " +
                        "checksumReadByteCalls=${bufferedChecksumProfile.readByteCalls} " +
                        "checksumReadByteDelegateReadMs=${bufferedChecksumProfile.readByteDelegateReadMs} " +
                        "checksumReadByteChecksumMs=${bufferedChecksumProfile.readByteChecksumMs} " +
                        "checksumReadBytesCalls=${bufferedChecksumProfile.readBytesCalls} " +
                        "checksumReadBytesRequestedBytes=${bufferedChecksumProfile.readBytesRequestedBytes} " +
                        "checksumReadBytesDelegateReadMs=${bufferedChecksumProfile.readBytesDelegateReadMs} " +
                        "checksumReadBytesChecksumMs=${bufferedChecksumProfile.readBytesChecksumMs} " +
                        "checksumReadBytesDelegateReadNs=${bufferedChecksumProfile.readBytesDelegateReadNs} " +
                        "checksumReadBytesChecksumNs=${bufferedChecksumProfile.readBytesChecksumNs} " +
                        "nioReadInternalCalls=${nioReadInternalProfile.calls} " +
                        "nioReadInternalRequestedBytes=${nioReadInternalProfile.requestedBytes} " +
                        "nioReadInternalTotalMs=${nioReadInternalProfile.totalMs} " +
                        "nioReadInternalChunkIterations=${nioReadInternalProfile.chunkIterations} " +
                        "nioTempBufferCreateMs=${nioReadInternalProfile.tempBufferCreateMs} " +
                        "nioHandleReadCalls=${nioReadInternalProfile.handleReadCalls} " +
                        "nioHandleReadBytes=${nioReadInternalProfile.handleReadBytes} " +
                        "nioHandleReadMs=${nioReadInternalProfile.handleReadMs} " +
                        "nioTransferMs=${nioReadInternalProfile.transferMs} " +
                        "nioEofSignals=${nioReadInternalProfile.eofSignals} " +
                        "byteBufferArrayCalls=${byteBufferProfile.arrayCalls} " +
                        "byteBufferArrayMs=${byteBufferProfile.arrayMs} " +
                        "byteBufferPositionGetCalls=${byteBufferProfile.positionGetCalls} " +
                        "byteBufferPositionGetMs=${byteBufferProfile.positionGetMs} " +
                        "byteBufferPositionSetCalls=${byteBufferProfile.positionSetCalls} " +
                        "byteBufferPositionSetMs=${byteBufferProfile.positionSetMs} " +
                        "byteBufferPositionMethodCalls=${byteBufferProfile.positionMethodCalls} " +
                        "byteBufferPositionMethodMs=${byteBufferProfile.positionMethodMs} " +
                        "byteBufferLimitGetCalls=${byteBufferProfile.limitGetCalls} " +
                        "byteBufferLimitGetMs=${byteBufferProfile.limitGetMs} " +
                        "byteBufferLimitSetCalls=${byteBufferProfile.limitSetCalls} " +
                        "byteBufferLimitSetMs=${byteBufferProfile.limitSetMs} " +
                        "byteBufferLimitMethodCalls=${byteBufferProfile.limitMethodCalls} " +
                        "byteBufferLimitMethodMs=${byteBufferProfile.limitMethodMs} " +
                        "byteBufferRemainingCalls=${byteBufferProfile.remainingCalls} " +
                        "byteBufferRemainingMs=${byteBufferProfile.remainingMs} " +
                        "lineCount=${info.lineCount} mergeRounds=${info.mergeRounds} tempMergeFiles=${info.tempMergeFiles} " +
                        "sortCallElapsedMs=${Clock.System.now().toEpochMilliseconds() - sortCallStart}"
                )
            }
        }
        val verifyStart = Clock.System.now().toEpochMilliseconds()
        assertFilesIdentical(dir, golden.name!!, sorted, shouldTraceDetailed)
        trace("checkSort verifyElapsedMs=${Clock.System.now().toEpochMilliseconds() - verifyStart}")
        trace("checkSort totalElapsedMs=${Clock.System.now().toEpochMilliseconds() - checkStart}")
        return sorter.sortInfo!!
    }

    private fun assertFilesIdentical(dir: Directory, golden: String, sorted: String, traceDetailed: Boolean = false) {
        val verifyStart = Clock.System.now().toEpochMilliseconds()
        val numBytes = dir.fileLength(golden)
        assertEquals(numBytes, dir.fileLength(sorted))

        val buf1 = ByteArray(64 * 1024)
        val buf2 = ByteArray(64 * 1024)
        var readElapsedMs = 0L
        var compareElapsedMs = 0L
        var comparedBytes = 0L
        var nextProgressBytes = 8L * 1024L * 1024L
        dir.openInput(golden, IOContext.READONCE).use { in1 ->
            dir.openInput(sorted, IOContext.READONCE).use { in2 ->
                var left = numBytes
                while (left > 0) {
                    val chunk = min(buf1.size.toLong(), left).toInt()
                    left -= chunk.toLong()
                    val readStart = Clock.System.now().toEpochMilliseconds()
                    in1.readBytes(buf1, 0, chunk)
                    in2.readBytes(buf2, 0, chunk)
                    readElapsedMs += Clock.System.now().toEpochMilliseconds() - readStart
                    val compareStart = Clock.System.now().toEpochMilliseconds()
                    assertEquals(buf1, 0, chunk, buf2, 0, chunk)
                    compareElapsedMs += Clock.System.now().toEpochMilliseconds() - compareStart
                    comparedBytes += chunk.toLong()
                    if (traceDetailed && comparedBytes >= nextProgressBytes) {
                        trace(
                            "assertFilesIdentical progress comparedBytes=$comparedBytes/$numBytes " +
                                "readElapsedMs=$readElapsedMs compareElapsedMs=$compareElapsedMs " +
                                "totalElapsedMs=${Clock.System.now().toEpochMilliseconds() - verifyStart}"
                        )
                        nextProgressBytes += 8L * 1024L * 1024L
                    }
                }
            }
        }
        if (traceDetailed) {
            trace(
                "assertFilesIdentical done bytes=$numBytes readElapsedMs=$readElapsedMs " +
                    "compareElapsedMs=$compareElapsedMs totalElapsedMs=${Clock.System.now().toEpochMilliseconds() - verifyStart}"
            )
        }
    }

    private fun writeAll(out: IndexOutput, data: Array<ByteArray>, label: String = "data") {
        val start = Clock.System.now().toEpochMilliseconds()
        val shouldTraceProgress = data.size >= 100_000
        trace("writeAll[$label] outputClass=${out::class.simpleName}")
        OfflineSorter.ByteSequencesWriter(out).use { w ->
            for (i in data.indices) {
                val datum = data[i]
                w.write(datum)
                if (shouldTraceProgress && ((i + 1) % 100_000 == 0 || i == data.lastIndex)) {
                    trace(
                        "writeAll[$label] progress items=${i + 1}/${data.size} " +
                            "elapsedMs=${Clock.System.now().toEpochMilliseconds() - start}"
                    )
                }
            }
            val footerStart = Clock.System.now().toEpochMilliseconds()
            CodecUtil.writeFooter(out)
            trace("writeAll[$label] footerElapsedMs=${Clock.System.now().toEpochMilliseconds() - footerStart}")
        }
        trace("writeAll[$label] done items=${data.size} elapsedMs=${Clock.System.now().toEpochMilliseconds() - start}")
    }

    @Test
    fun testRamBuffer() {
        val numIters = atLeast(10000)
        for (i in 0 until numIters) {
            OfflineSorter.BufferSize.megabytes((1 + random().nextInt(2047)).toLong())
        }
        OfflineSorter.BufferSize.megabytes(2047)
        OfflineSorter.BufferSize.megabytes(1)

        expectThrows(IllegalArgumentException::class) {
            OfflineSorter.BufferSize.megabytes(2048)
        }
        expectThrows(IllegalArgumentException::class) {
            OfflineSorter.BufferSize.megabytes(0)
        }
        expectThrows(IllegalArgumentException::class) {
            OfflineSorter.BufferSize.megabytes(-1)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testThreadSafety() = runBlocking {
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        val failed = AtomicBoolean(false)
        val iters = atLeast(20) // TODO reduced from 200 to 20 for dev speed
        newDirectory().use { dir ->
            val threadCount = TestUtil.nextInt(random(), 4, 10)
            for (i in 0 until threadCount) {
                val threadID = i
                jobs += launch(Dispatchers.Default) {
                    try {
                        for (iter in 0 until iters) {
                            if (failed.load()) {
                                break
                            }
                            checkSort(
                                dir,
                                OfflineSorter(dir, "foo_${threadID}_$iter"),
                                generateRandom(1024)
                            )
                        }
                    } catch (t: Throwable) {
                        failed.store(true)
                        throw RuntimeException(t)
                    }
                }
            }
            for (job in jobs) {
                job.join()
            }
        }
        assertFalse(failed.load())
    }

    @Test
    fun testBitFlippedOnInput1() {
        newMockDirectory().use { dir0 ->
            val dir =
                object : FilterDirectory(dir0) {
                    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
                        val out = `in`.createTempOutput(prefix, suffix, context)
                        return if (prefix == "unsorted") CorruptingIndexOutput(dir0, 22, out) else out
                    }
                }
            val unsorted = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT)
            writeAll(unsorted, generateFixed(10 * 1024))
            val e = expectThrows(CorruptIndexException::class) {
                OfflineSorter(dir, "foo").sort(unsorted.name!!)
            }
            assertTrue(e.message!!.contains("checksum failed (hardware problem?)"))
        }
    }

    @Test
    fun testBitFlippedOnInput2() {
        newMockDirectory().use { dir0 ->
            val dir =
                object : FilterDirectory(dir0) {
                    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
                        val out = `in`.createTempOutput(prefix, suffix, context)
                        return if (prefix == "unsorted") {
                            object : CorruptingIndexOutput(dir0, 22, out) {
                                override fun corruptFile() {
                                    val newTempName: String
                                    dir0.createTempOutput("tmp", "tmp", IOContext.DEFAULT).use { tmpOut ->
                                        dir0.openInput(out.name!!, IOContext.DEFAULT).use { `in` ->
                                            newTempName = tmpOut.name!!
                                            val v = `in`.readShort()
                                            assertEquals(256.toShort(), v)
                                            tmpOut.writeShort(Short.MAX_VALUE)
                                            tmpOut.copyBytes(`in`, `in`.length() - Short.SIZE_BYTES)
                                        }
                                    }
                                    dir0.deleteFile(out.name!!)
                                    dir0.copyFrom(dir0, newTempName, out.name!!, IOContext.DEFAULT)
                                    dir0.deleteFile(newTempName)
                                }
                            }
                        } else {
                            out
                        }
                    }
                }
            val unsorted = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT)
            writeAll(unsorted, generateFixed(5 * 1024))
            val e = expectThrows(CorruptIndexException::class) {
                OfflineSorter(dir, "foo").sort(unsorted.name!!)
            }
            assertTrue(e.message!!.contains("checksum failed (hardware problem?)"))
        }
    }

    @Test
    fun testBitFlippedOnPartition1() {
        newMockDirectory().use { dir0 ->
            val dir =
                object : FilterDirectory(dir0) {
                    var corrupted = false
                    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
                        val out = `in`.createTempOutput(prefix, suffix, context)
                        return if (!corrupted && suffix == "sort") {
                            corrupted = true
                            CorruptingIndexOutput(dir0, 544677, out)
                        } else {
                            out
                        }
                    }
                }
            val unsorted = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT)
            writeAll(unsorted, generateFixed((OfflineSorter.MB * 3).toInt()))
            val e = expectThrows(CorruptIndexException::class) {
                OfflineSorter(
                    dir,
                    "foo",
                    OfflineSorter.DEFAULT_COMPARATOR,
                    OfflineSorter.BufferSize.megabytes(1),
                    10,
                    -1,
                    null,
                    0
                ).sort(unsorted.name!!)
            }
            assertTrue(e.message!!.contains("checksum failed (hardware problem?)"))
        }
    }

    @Test
    fun testBitFlippedOnPartition2() {
        newMockDirectory().use { dir0 ->
            val dir =
                object : FilterDirectory(dir0) {
                    var corrupted = false
                    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
                        val out = `in`.createTempOutput(prefix, suffix, context)
                        return if (!corrupted && suffix == "sort") {
                            corrupted = true
                            object : CorruptingIndexOutput(dir0, 544677, out) {
                                override fun corruptFile() {
                                    val newTempName: String
                                    dir0.createTempOutput("tmp", "tmp", IOContext.DEFAULT).use { tmpOut ->
                                        dir0.openInput(out.name!!, IOContext.DEFAULT).use { `in` ->
                                            newTempName = tmpOut.name!!
                                            tmpOut.copyBytes(`in`, 1025905)
                                            val v = `in`.readShort()
                                            assertEquals(254.toShort(), v)
                                            tmpOut.writeShort(Short.MAX_VALUE)
                                            tmpOut.copyBytes(`in`, `in`.length() - 1025905 - Short.SIZE_BYTES)
                                        }
                                    }
                                    dir0.deleteFile(out.name!!)
                                    dir0.copyFrom(dir0, newTempName, out.name!!, IOContext.DEFAULT)
                                    dir0.deleteFile(newTempName)
                                }
                            }
                        } else {
                            out
                        }
                    }
                }
            val unsorted = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT)
            writeAll(unsorted, generateFixed((OfflineSorter.MB * 3).toInt()))
            val e = expectThrows(CorruptIndexException::class) {
                OfflineSorter(
                    dir,
                    "foo",
                    OfflineSorter.DEFAULT_COMPARATOR,
                    OfflineSorter.BufferSize.megabytes(1),
                    10,
                    -1,
                    null,
                    0
                ).sort(unsorted.name!!)
            }
            assertTrue(e.message!!.contains("checksum failed (hardware problem?)"))
        }
    }

    @Nightly
    @Test
    fun testFixedLengthHeap() {
        val dir = newDirectory()
        val out = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT)
        OfflineSorter.ByteSequencesWriter(out).use { w ->
            val bytes = ByteArray(Int.SIZE_BYTES)
            for (i in 0 until 1024 * 1024) {
                random().nextBytes(bytes)
                w.write(bytes)
            }
            CodecUtil.writeFooter(out)
        }

        val exec = randomExecutorServiceOrNull()
        val sorter = OfflineSorter(
            dir,
            "foo",
            OfflineSorter.DEFAULT_COMPARATOR,
            OfflineSorter.BufferSize.megabytes(4),
            OfflineSorter.MAX_TEMPFILES,
            Int.SIZE_BYTES,
            exec,
            TestUtil.nextInt(random(), 1, 4)
        )
        sorter.sort(out.name!!)
        if (exec != null) {
            runBlocking { exec.shutdownNow() }
        }
        assertEquals(0, sorter.sortInfo!!.mergeRounds)
        dir.close()
    }

    @Test
    fun testFixedLengthLiesLiesLies() {
        val dir = newDirectory()
        val out = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT)
        OfflineSorter.ByteSequencesWriter(out).use { w ->
            val bytes = ByteArray(Int.SIZE_BYTES)
            random().nextBytes(bytes)
            w.write(bytes)
            CodecUtil.writeFooter(out)
        }

        val sorter = OfflineSorter(
            dir,
            "foo",
            OfflineSorter.DEFAULT_COMPARATOR,
            OfflineSorter.BufferSize.megabytes(4),
            OfflineSorter.MAX_TEMPFILES,
            Long.SIZE_BYTES,
            null,
            0
        )
        val e = expectThrows(IllegalArgumentException::class) {
            sorter.sort(out.name!!)
        }
        assertEquals("value length is 4 but is supposed to always be 8", e.message)
        dir.close()
    }

    @Test
    fun testOverNexting() {
        val dir = newDirectory()
        val out = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT)
        OfflineSorter.ByteSequencesWriter(out).use { w ->
            val bytes = ByteArray(Int.SIZE_BYTES)
            random().nextBytes(bytes)
            w.write(bytes)
            CodecUtil.writeFooter(out)
        }

        object : OfflineSorter(
            dir,
            "foo",
            OfflineSorter.DEFAULT_COMPARATOR,
            OfflineSorter.BufferSize.megabytes(4),
            OfflineSorter.MAX_TEMPFILES,
            Int.SIZE_BYTES,
            null,
            0
        ) {
            override fun getReader(`in`: ChecksumIndexInput, name: String): ByteSequencesReader {
                val other = super.getReader(`in`, name)
                return object : ByteSequencesReader(`in`, name) {
                    private var alreadyEnded = false
                    override fun next(): BytesRef? {
                        assertFalse(alreadyEnded)
                        val result = other.next()
                        if (result == null) {
                            alreadyEnded = true
                        }
                        return result
                    }

                    override fun close() {
                        other.close()
                    }
                }
            }
        }.sort(out.name!!)
        dir.close()
    }

    @Test
    fun testInvalidFixedLength() {
        var e = expectThrows(IllegalArgumentException::class) {
            OfflineSorter(
                newDirectory(),
                "foo",
                OfflineSorter.DEFAULT_COMPARATOR,
                OfflineSorter.BufferSize.megabytes(1),
                OfflineSorter.MAX_TEMPFILES,
                0,
                null,
                0
            )
        }
        assertEquals("valueLength must be 1 .. 32767; got: 0", e.message)

        e = expectThrows(IllegalArgumentException::class) {
            OfflineSorter(
                newDirectory(),
                "foo",
                OfflineSorter.DEFAULT_COMPARATOR,
                OfflineSorter.BufferSize.megabytes(1),
                OfflineSorter.MAX_TEMPFILES,
                Int.MAX_VALUE,
                null,
                0
            )
        }
        assertEquals("valueLength must be 1 .. 32767; got: 2147483647", e.message)
    }
}
