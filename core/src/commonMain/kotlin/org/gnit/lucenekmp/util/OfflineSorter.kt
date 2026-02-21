package org.gnit.lucenekmp.util

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.Semaphore
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.TrackingDirectoryWrapper
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock

/**
 * On-disk sorting of byte arrays.
 *
 * @lucene.experimental
 * @lucene.internal
 */
open class OfflineSorter @Throws(IOException::class) constructor(
    private val dir: Directory,
    private val tempFileNamePrefix: String,
    private val comparator: Comparator<BytesRef>,
    private val ramBufferSize: BufferSize,
    private val maxTempFiles: Int,
    private val valueLength: Int,
    exec: ExecutorService?,
    maxPartitionsInRAM: Int
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        /** Convenience constant for megabytes */
        const val MB: Long = 1024L * 1024L

        /** Convenience constant for gigabytes */
        const val GB: Long = MB * 1024L

        /** Minimum recommended buffer size for sorting. */
        const val MIN_BUFFER_SIZE_MB: Long = 32L

        /** Absolute minimum required buffer size for sorting. */
        const val ABSOLUTE_MIN_SORT_BUFFER_SIZE: Long = MB / 2L

        private const val MIN_BUFFER_SIZE_MSG = "At least 0.5MB RAM buffer is needed"

        /** Maximum number of temporary files before doing an intermediate merge. */
        const val MAX_TEMPFILES: Int = 10

        /** Default comparator: sorts in binary (codepoint) order */
        val DEFAULT_COMPARATOR: Comparator<BytesRef> = Comparator { a, b -> a.compareTo(b) }
    }

    private val exec: ExecutorService
    private val partitionsInRAM: Semaphore

    var sortInfo: SortInfo? = null
        private set

    private fun trace(message: () -> String) {}

    init {
        if (exec != null) {
            this.exec = exec
            require(maxPartitionsInRAM > 0) { "maxPartitionsInRAM must be > 0; got $maxPartitionsInRAM" }
        } else {
            this.exec = SameThreadExecutorService()
        }
        this.partitionsInRAM = Semaphore(if (exec != null) maxPartitionsInRAM else 1)

        require(ramBufferSize.bytes.toLong() >= ABSOLUTE_MIN_SORT_BUFFER_SIZE) {
            "$MIN_BUFFER_SIZE_MSG: ${ramBufferSize.bytes}"
        }
        require(maxTempFiles >= 2) { "maxTempFiles must be >= 2" }
        require(valueLength == -1 || (valueLength in 1..Short.MAX_VALUE.toInt())) {
            "valueLength must be 1 .. ${Short.MAX_VALUE}; got: $valueLength"
        }
    }

    @Throws(IOException::class)
    constructor(dir: Directory, tempFileNamePrefix: String) : this(
        dir,
        tempFileNamePrefix,
        DEFAULT_COMPARATOR,
        BufferSize.automatic(),
        MAX_TEMPFILES,
        -1,
        null,
        0
    )

    @Throws(IOException::class)
    constructor(dir: Directory, tempFileNamePrefix: String, comparator: Comparator<BytesRef>) : this(
        dir,
        tempFileNamePrefix,
        comparator,
        BufferSize.automatic(),
        MAX_TEMPFILES,
        -1,
        null,
        0
    )

    /** A bit more descriptive unit for constructors. */
    class BufferSize private constructor(bytes: Long) {
        val bytes: Int

        init {
            require(bytes <= Int.MAX_VALUE.toLong()) {
                "Buffer too large for Java (${Int.MAX_VALUE / MB}MB max): $bytes"
            }
            require(bytes >= ABSOLUTE_MIN_SORT_BUFFER_SIZE) { "$MIN_BUFFER_SIZE_MSG: $bytes" }
            this.bytes = bytes.toInt()
        }

        companion object {
            /** Creates a [BufferSize] in MB. */
            fun megabytes(mb: Long): BufferSize = BufferSize(mb * MB)

            /** Approximately half of the currently available free heap. */
            fun automatic(): BufferSize {
                // KMP approximation: use minimum recommended size by default.
                return BufferSize(MIN_BUFFER_SIZE_MB * MB)
            }
        }
    }

    /** Sort info (debugging mostly). */
    @OptIn(ExperimentalAtomicApi::class)
    inner class SortInfo {
        var tempMergeFiles: Int = 0
        var mergeRounds: Int = 0
        var lineCount: Long = 0L
        val mergeTimeMS: AtomicLong = AtomicLong(0L)
        val sortTimeMS: AtomicLong = AtomicLong(0L)
        var totalTimeMS: Long = 0L
        var readTimeMS: Long = 0L
        val bufferSize: Long = ramBufferSize.bytes.toLong()

        override fun toString(): String {
            return "time=${totalTimeMS / 1000.0} sec. total (${readTimeMS / 1000.0} reading, " +
                "${sortTimeMS.load() / 1000.0} sorting, ${mergeTimeMS.load() / 1000.0} merging), " +
                "lines=$lineCount, temp files=$tempMergeFiles, merges=$mergeRounds, " +
                "soft ram limit=${bufferSize.toDouble() / MB} MB"
        }
    }

    private class Partition(
        val buffer: SortableBytesRefArray?,
        val exhausted: Boolean,
        val count: Long,
        val fileName: String?
    ) {
        constructor(buffer: SortableBytesRefArray, exhausted: Boolean) : this(
            buffer, exhausted, buffer.size().toLong(), null
        )

        constructor(fileName: String, count: Long) : this(
            null, true, count, fileName
        )
    }

    protected open fun getWriter(out: IndexOutput, itemCount: Long): ByteSequencesWriter {
        return ByteSequencesWriter(out)
    }

    protected open fun getReader(`in`: ChecksumIndexInput, name: String): ByteSequencesReader {
        return ByteSequencesReader(`in`, name)
    }

    fun getDirectory(): Directory = dir

    fun getTempFileNamePrefix(): String = tempFileNamePrefix

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun sort(inputFileName: String): String {
        sortInfo = SortInfo()
        val sortInfo = requireNotNull(sortInfo)
        val startMS = Clock.System.now().toEpochMilliseconds()
        trace {
            "OfflineSorter.sort start inputFileName=$inputFileName " +
                "ramBufferBytes=${ramBufferSize.bytes} maxTempFiles=$maxTempFiles " +
                "valueLength=$valueLength"
        }
        val segments = mutableListOf<org.gnit.lucenekmp.jdkport.Future<Partition>>()
        var levelCounts = IntArray(1)
        val trackingDir = TrackingDirectoryWrapper(dir)

        var success = false
        try {
            getReader(dir.openChecksumInput(inputFileName), inputFileName).use { reader ->
                while (true) {
                    val part = readPartition(reader)
                    if (part.count == 0L) {
                        assert(part.exhausted)
                        break
                    }
                    val job = SortPartitionTask(trackingDir, part)
                    segments.add(exec.submit(job))
                    sortInfo.tempMergeFiles++
                    sortInfo.lineCount += part.count
                    levelCounts[0]++
                    trace {
                        "OfflineSorter.sort partitionScheduled count=${part.count} exhausted=${part.exhausted} " +
                            "segments=${segments.size} elapsedMs=${Clock.System.now().toEpochMilliseconds() - startMS}"
                    }

                    var mergeLevel = 0
                    while (levelCounts[mergeLevel] == maxTempFiles) {
                        val mergeStartMS = Clock.System.now().toEpochMilliseconds()
                        mergePartitions(trackingDir, segments)
                        trace {
                            "OfflineSorter.sort intermediateMergeSubmitted mergeLevel=$mergeLevel " +
                                "segmentsAfterSubmit=${segments.size} mergeScheduleElapsedMs=${Clock.System.now().toEpochMilliseconds() - mergeStartMS} " +
                                "elapsedMs=${Clock.System.now().toEpochMilliseconds() - startMS}"
                        }
                        if (mergeLevel + 2 > levelCounts.size) {
                            levelCounts = ArrayUtil.grow(levelCounts, mergeLevel + 2)
                        }
                        levelCounts[mergeLevel + 1]++
                        levelCounts[mergeLevel] = 0
                        mergeLevel++
                    }
                    if (part.exhausted) {
                        break
                    }
                }

                while (segments.size > 1) {
                    val mergeStartMS = Clock.System.now().toEpochMilliseconds()
                    mergePartitions(trackingDir, segments)
                    trace {
                        "OfflineSorter.sort finalMergeSubmitted segmentsAfterSubmit=${segments.size} " +
                            "mergeScheduleElapsedMs=${Clock.System.now().toEpochMilliseconds() - mergeStartMS} " +
                            "elapsedMs=${Clock.System.now().toEpochMilliseconds() - startMS}"
                    }
                }

                val result = if (segments.isEmpty()) {
                    trackingDir.createTempOutput(tempFileNamePrefix, "sort", IOContext.DEFAULT).use { out ->
                        CodecUtil.writeFooter(out)
                        out.name!!
                    }
                } else {
                    getPartition(segments[0]).fileName!!
                }

                sortInfo.totalTimeMS = Clock.System.now().toEpochMilliseconds() - startMS
                trace {
                    "OfflineSorter.sort finished inputFileName=$inputFileName result=$result " +
                        "totalElapsedMs=${sortInfo.totalTimeMS} readTimeMs=${sortInfo.readTimeMS} " +
                        "sortTimeMs=${sortInfo.sortTimeMS.load()} mergeTimeMs=${sortInfo.mergeTimeMS.load()} " +
                        "lineCount=${sortInfo.lineCount} mergeRounds=${sortInfo.mergeRounds}"
                }
                CodecUtil.checkFooter(reader.`in`)
                success = true
                return result
            }
        } finally {
            if (!success) {
                IOUtils.deleteFilesIgnoringExceptions(trackingDir, trackingDir.createdFiles)
            }
        }
    }

    @Throws(IOException::class)
    private fun verifyChecksum(priorException: Throwable, reader: ByteSequencesReader) {
        dir.openChecksumInput(reader.name).use { `in` ->
            CodecUtil.checkFooter(`in`, priorException)
        }
    }

    @Throws(IOException::class)
    private fun mergePartitions(
        trackingDir: Directory,
        segments: MutableList<org.gnit.lucenekmp.jdkport.Future<Partition>>
    ) {
        val startMS = Clock.System.now().toEpochMilliseconds()
        val originalSegmentCount = segments.size
        val segmentsToMerge =
            if (segments.size > maxTempFiles) {
                segments.subList(segments.size - maxTempFiles, segments.size).toMutableList()
            } else {
                segments.toMutableList()
            }
        requireNotNull(sortInfo).mergeRounds++
        val task = MergePartitionsTask(trackingDir, segmentsToMerge)
        if (segments.size > maxTempFiles) {
            segments.subList(segments.size - maxTempFiles, segments.size).clear()
        } else {
            segments.clear()
        }
        segments.add(exec.submit(task))
        requireNotNull(sortInfo).tempMergeFiles++
        trace {
            "OfflineSorter.mergePartitions submitted originalSegments=$originalSegmentCount " +
                "segmentsToMerge=${segmentsToMerge.size} segmentsAfterSubmit=${segments.size} " +
                "elapsedMs=${Clock.System.now().toEpochMilliseconds() - startMS}"
        }
    }

    @Throws(IOException::class)
    private fun readPartition(reader: ByteSequencesReader): Partition {
        partitionsInRAM.acquire()
        var success = false
        try {
            val start = Clock.System.now().toEpochMilliseconds()
            val buffer: SortableBytesRefArray
            var exhausted = false
            if (valueLength != -1) {
                buffer = FixedLengthBytesRefArray(valueLength)
                val limit = ramBufferSize.bytes / valueLength
                for (i in 0 until limit) {
                    val item = try {
                        reader.next()
                    } catch (t: Throwable) {
                        verifyChecksum(t, reader); null
                    }
                    if (item == null) {
                        exhausted = true
                        break
                    }
                    buffer.append(item)
                }
            } else {
                val bufferBytesUsed = Counter.newCounter()
                buffer = BytesRefArray(bufferBytesUsed)
                while (true) {
                    val item = try {
                        reader.next()
                    } catch (t: Throwable) {
                        verifyChecksum(t, reader); null
                    }
                    if (item == null) {
                        exhausted = true
                        break
                    }
                    buffer.append(item)
                    if (bufferBytesUsed.get() > ramBufferSize.bytes.toLong()) {
                        break
                    }
                }
            }
            val elapsed = Clock.System.now().toEpochMilliseconds() - start
            requireNotNull(sortInfo).readTimeMS += elapsed
            success = true
            val result = Partition(buffer, exhausted)
            trace {
                "OfflineSorter.readPartition count=${result.count} exhausted=${result.exhausted} " +
                    "elapsedMs=$elapsed"
            }
            return result
        } finally {
            if (!success) {
                partitionsInRAM.release()
            }
        }
    }

    class ByteSequencesWriter(val out: IndexOutput) : AutoCloseable {
        @Throws(IOException::class)
        fun write(ref: BytesRef) {
            write(ref.bytes, ref.offset, ref.length)
        }

        @Throws(IOException::class)
        fun write(bytes: ByteArray) {
            write(bytes, 0, bytes.size)
        }

        @Throws(IOException::class)
        fun write(bytes: ByteArray, off: Int, len: Int) {
            require(len <= Short.MAX_VALUE.toInt()) { "len must be <= ${Short.MAX_VALUE}; got $len" }
            out.writeShort(len.toShort())
            out.writeBytes(bytes, off, len)
        }

        override fun close() {
            out.close()
        }
    }

    open class ByteSequencesReader(val `in`: ChecksumIndexInput, val name: String) : BytesRefIterator, AutoCloseable {
        private val end: Long = `in`.length() - CodecUtil.footerLength().toLong()
        private val ref = BytesRefBuilder()

        @Throws(IOException::class)
        override open fun next(): BytesRef? {
            if (`in`.filePointer >= end) {
                return null
            }
            val length = `in`.readShort().toInt() and 0xFFFF
            ref.growNoCopy(length)
            ref.setLength(length)
            `in`.readBytes(ref.bytes(), 0, length)
            return ref.get()
        }

        override open fun close() {
            `in`.close()
        }
    }

    private class FileAndTop(val fd: Int, var current: BytesRef)

    private inner class SortPartitionTask(
        private val dir: Directory,
        private val part: Partition
    ) : Callable<Partition> {
        @OptIn(ExperimentalAtomicApi::class)
        override fun call(): Partition {
            val taskStartMS = Clock.System.now().toEpochMilliseconds()
            try {
                dir.createTempOutput(tempFileNamePrefix, "sort", IOContext.DEFAULT).use { tempFile ->
                    getWriter(tempFile, part.buffer!!.size().toLong()).use { out ->
                        val iteratorStartMS = Clock.System.now().toEpochMilliseconds()
                        val iter = part.buffer.iterator(comparator)
                        val iteratorElapsedMS = Clock.System.now().toEpochMilliseconds() - iteratorStartMS
                        requireNotNull(sortInfo).sortTimeMS.addAndFetch(
                            iteratorElapsedMS
                        )
                        var count = 0
                        val writeStartMS = Clock.System.now().toEpochMilliseconds()
                        var spare = iter.next()
                        while (spare != null) {
                            out.write(spare)
                            count++
                            spare = iter.next()
                        }
                        assert(count.toLong() == part.count)
                        CodecUtil.writeFooter(out.out)
                        part.buffer.clear()
                        trace {
                            "OfflineSorter.sortPartition done file=${tempFile.name} count=$count " +
                                "iteratorElapsedMs=$iteratorElapsedMS writeElapsedMs=${Clock.System.now().toEpochMilliseconds() - writeStartMS} " +
                                "taskElapsedMs=${Clock.System.now().toEpochMilliseconds() - taskStartMS}"
                        }
                        return Partition(tempFile.name!!, part.count)
                    }
                }
            } finally {
                partitionsInRAM.release()
            }
        }
    }

    @Throws(IOException::class)
    private fun getPartition(future: org.gnit.lucenekmp.jdkport.Future<Partition>): Partition {
        return try {
            runBlocking { future.get() }
        } catch (e: InterruptedException) {
            throw ThreadInterruptedException(CancellationException(e.message ?: "interrupted"))
        } catch (e: ExecutionException) {
            throw IOUtils.rethrowAlways(e.cause ?: e)
        }
    }

    private inner class MergePartitionsTask(
        private val dir: Directory,
        private val segmentsToMerge: List<org.gnit.lucenekmp.jdkport.Future<Partition>>
    ) : Callable<Partition> {
        @OptIn(ExperimentalAtomicApi::class)
        override fun call(): Partition {
            val taskStartMS = Clock.System.now().toEpochMilliseconds()
            val waitStartMS = Clock.System.now().toEpochMilliseconds()
            var totalCount = 0L
            for (segment in segmentsToMerge) {
                totalCount += getPartition(segment).count
            }
            val waitElapsedMS = Clock.System.now().toEpochMilliseconds() - waitStartMS
            val queue = object : PriorityQueue<FileAndTop>(segmentsToMerge.size) {
                override fun lessThan(a: FileAndTop, b: FileAndTop): Boolean {
                    return comparator.compare(a.current, b.current) < 0
                }
            }
            val streams = arrayOfNulls<ByteSequencesReader>(segmentsToMerge.size)
            var newSegmentName: String? = null
            val startMS = Clock.System.now().toEpochMilliseconds()
            try {
                getWriter(dir.createTempOutput(tempFileNamePrefix, "sort", IOContext.DEFAULT), totalCount).use { writer ->
                    newSegmentName = writer.out.name!!
                    val initStartMS = Clock.System.now().toEpochMilliseconds()
                    for (i in segmentsToMerge.indices) {
                        val segment = getPartition(segmentsToMerge[i])
                        streams[i] = getReader(dir.openChecksumInput(segment.fileName!!), segment.fileName)
                        val item = try {
                            streams[i]!!.next()
                        } catch (t: Throwable) {
                            verifyChecksum(t, streams[i]!!); null
                        }
                        requireNotNull(item)
                        queue.insertWithOverflow(FileAndTop(i, item))
                    }
                    val initElapsedMS = Clock.System.now().toEpochMilliseconds() - initStartMS
                    val mergeLoopStartMS = Clock.System.now().toEpochMilliseconds()
                    var mergedCount = 0L
                    var top = queue.topOrNull()
                    while (top != null) {
                        writer.write(top.current)
                        mergedCount++
                        top.current = try {
                            streams[top.fd]!!.next()
                        } catch (t: Throwable) {
                            verifyChecksum(t, streams[top.fd]!!); null
                        } ?: run {
                            queue.pop()
                            top = queue.topOrNull()
                            continue
                        }
                        queue.updateTop()
                        top = queue.topOrNull()
                    }
                    CodecUtil.writeFooter(writer.out)
                    for (reader in streams) {
                        if (reader != null) {
                            CodecUtil.checkFooter(reader.`in`)
                        }
                    }
                    val mergeElapsedMS = Clock.System.now().toEpochMilliseconds() - startMS
                    sortInfo!!.mergeTimeMS.addAndFetch(mergeElapsedMS)
                    trace {
                        "OfflineSorter.mergeTask mergedCount=$mergedCount totalCount=$totalCount " +
                            "segmentsToMerge=${segmentsToMerge.size} waitElapsedMs=$waitElapsedMS " +
                            "initElapsedMs=$initElapsedMS mergeLoopElapsedMs=${Clock.System.now().toEpochMilliseconds() - mergeLoopStartMS} " +
                            "mergeElapsedMs=$mergeElapsedMS taskElapsedMs=${Clock.System.now().toEpochMilliseconds() - taskStartMS}"
                    }
                }
            } finally {
                IOUtils.closeWhileHandlingException(streams.filterNotNull())
            }
            val deleteStartMS = Clock.System.now().toEpochMilliseconds()
            val toDelete = mutableListOf<String>()
            for (segment in segmentsToMerge) {
                toDelete.add(getPartition(segment).fileName!!)
            }
            IOUtils.deleteFiles(dir, toDelete)
            trace {
                "OfflineSorter.mergeTask deleteTempFiles count=${toDelete.size} " +
                    "elapsedMs=${Clock.System.now().toEpochMilliseconds() - deleteStartMS}"
            }
            return Partition(requireNotNull(newSegmentName), totalCount)
        }
    }
}
