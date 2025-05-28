package org.gnit.lucenekmp.util.bkd

import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntroSelector
import org.gnit.lucenekmp.util.IntroSorter
import org.gnit.lucenekmp.util.MSBRadixSorter
import org.gnit.lucenekmp.util.RadixSelector
import org.gnit.lucenekmp.util.Selector
import org.gnit.lucenekmp.util.Sorter

import kotlin.jvm.JvmRecord
import kotlin.math.min

/**
 * Offline Radix selector for BKD tree.
 *
 * @lucene.internal
 */
class BKDRadixSelector(// BKD tree configuration
    private val config: BKDConfig, // flag to when we are moving to sort on heap
    private var maxPointsSortInHeap: Int, tempDir: Directory, tempFileNamePrefix: String
) {
    // histogram array
    private val histogram: LongArray

    // number of bytes to be sorted: config.bytesPerDim + Integer.BYTES
    private val bytesSorted: Int

    // reusable buffer
    private val offlineBuffer: ByteArray

    // holder for partition points
    private val partitionBucket: IntArray

    // scratch array to hold temporary data
    private val scratch: ByteArray

    // Directory to create new Offline writer
    private val tempDir: Directory

    // prefix for temp files
    private val tempFileNamePrefix: String

    /** Sole constructor.  */
    init {
        this.maxPointsSortInHeap = maxPointsSortInHeap
        this.tempDir = tempDir
        this.tempFileNamePrefix = tempFileNamePrefix
        // Selection and sorting is done in a given dimension. In case the value of the dimension are
        // equal
        // between two points we tie break first using the data-only dimensions and if those are still
        // equal
        // we tie-break on the docID. Here we account for all bytes used in the process.
        this.bytesSorted =
            (config.bytesPerDim
                    + (config.numDims - config.numIndexDims) * config.bytesPerDim + Int.SIZE_BYTES)
        val numberOfPointsOffline = MAX_SIZE_OFFLINE_BUFFER / config.bytesPerDoc()
        this.offlineBuffer = ByteArray(numberOfPointsOffline * config.bytesPerDoc())
        this.partitionBucket = IntArray(bytesSorted)
        this.histogram = LongArray(HISTOGRAM_SIZE)
        this.scratch = ByteArray(bytesSorted)
    }

    /**
     * It uses the provided `points` from the given `from` to the given `to` to
     * populate the `partitionSlices` array holder (length &gt; 1) with two path slices so the
     * path slice at position 0 contains `partition - from` points where the value of the `dim` is lower or equal to the `to -from` points on the slice at position 1.
     *
     *
     * The `dimCommonPrefix` provides a hint for the length of the common prefix length for
     * the `dim` where are partitioning the points.
     *
     *
     * It return the value of the `dim` at the partition point.
     *
     *
     * If the provided `points` is wrapping an [OfflinePointWriter], the writer is
     * destroyed in the process to save disk space.
     */
    @Throws(IOException::class)
    fun select(
        points: PathSlice,
        partitionSlices: Array<PathSlice?>,
        from: Long,
        to: Long,
        partitionPoint: Long,
        dim: Int,
        dimCommonPrefix: Int
    ): ByteArray {
        checkArgs(from, to, partitionPoint)

        require(
            partitionSlices.size > 1
        ) { "[partition alices] must be > 1, got " + partitionSlices.size }

        // If we are on heap then we just select on heap
        if (points.writer is HeapPointWriter) {
            val partition =
                heapRadixSelect(
                    points.writer,
                    dim,
                    Math.toIntExact(from),
                    Math.toIntExact(to),
                    Math.toIntExact(partitionPoint),
                    dimCommonPrefix
                )
            partitionSlices[0] = PathSlice(points.writer, from, partitionPoint - from)
            partitionSlices[1] = PathSlice(points.writer, partitionPoint, to - partitionPoint)
            return partition
        }

        val offlinePointWriter: OfflinePointWriter = points.writer as OfflinePointWriter

        getPointWriter(partitionPoint - from, "left$dim").use { left ->
            getPointWriter(to - partitionPoint, "right$dim").use { right ->
                partitionSlices[0] = PathSlice(left, 0, partitionPoint - from)
                partitionSlices[1] = PathSlice(right, 0, to - partitionPoint)
                return buildHistogramAndPartition(
                    offlinePointWriter, left, right, from, to, partitionPoint, 0, dimCommonPrefix, dim
                )
            }
        }
    }

    fun checkArgs(from: Long, to: Long, partitionPoint: Long) {
        require(partitionPoint >= from) { "partitionPoint must be >= from" }
        require(partitionPoint < to) { "partitionPoint must be < to" }
    }

    @Throws(IOException::class)
    private fun findCommonPrefixAndHistogram(
        points: OfflinePointWriter, from: Long, to: Long, dim: Int, dimCommonPrefix: Int
    ): Int {
        // find common prefix
        var commonPrefixPosition = bytesSorted
        val offset: Int = dim * config.bytesPerDim
        points.getReader(from, to - from, offlineBuffer).use { reader ->
            require(commonPrefixPosition > dimCommonPrefix)
            reader.next()
            var pointValue: PointValue = reader.pointValue()
            var packedValueDocID: BytesRef = pointValue.packedValueDocIDBytes()
            // copy dimension
            System.arraycopy(
                packedValueDocID.bytes,
                packedValueDocID.offset + offset,
                scratch,
                0,
                config.bytesPerDim
            )
            // copy data dimensions and docID
            System.arraycopy(
                packedValueDocID.bytes,
                packedValueDocID.offset + config.packedIndexBytesLength(),
                scratch,
                config.bytesPerDim,
                (config.numDims - config.numIndexDims) * config.bytesPerDim + Int.SIZE_BYTES
            )
            for (i in from + 1..<to) {
                reader.next()
                pointValue = reader.pointValue()
                if (commonPrefixPosition == dimCommonPrefix) {
                    histogram[getBucket(offset, commonPrefixPosition, pointValue)]++
                    // we do not need to check for common prefix anymore,
                    // just finish the histogram and break
                    for (j in i + 1..<to) {
                        reader.next()
                        pointValue = reader.pointValue()
                        histogram[getBucket(offset, commonPrefixPosition, pointValue)]++
                    }
                    break
                } else {
                    // Check common prefix and adjust histogram
                    val startIndex: Int = min(dimCommonPrefix, config.bytesPerDim)
                    val endIndex: Int = min(commonPrefixPosition, config.bytesPerDim)
                    packedValueDocID = pointValue.packedValueDocIDBytes()
                    val j: Int =
                        Arrays.mismatch(
                            scratch,
                            startIndex,
                            endIndex,
                            packedValueDocID.bytes,
                            packedValueDocID.offset + offset + startIndex,
                            packedValueDocID.offset + offset + endIndex
                        )
                    if (j == -1) {
                        if (commonPrefixPosition > config.bytesPerDim) {
                            // Tie-break on data dimensions + docID
                            val startTieBreak = config.packedIndexBytesLength()
                            val endTieBreak: Int = startTieBreak + commonPrefixPosition - config.bytesPerDim
                            val k: Int =
                                Arrays.mismatch(
                                    scratch,
                                    config.bytesPerDim,
                                    commonPrefixPosition,
                                    packedValueDocID.bytes,
                                    packedValueDocID.offset + startTieBreak,
                                    packedValueDocID.offset + endTieBreak
                                )
                            if (k != -1) {
                                commonPrefixPosition = config.bytesPerDim + k
                                Arrays.fill(histogram, 0)
                                histogram[scratch[commonPrefixPosition].toInt() and 0xff] = i - from
                            }
                        }
                    } else {
                        commonPrefixPosition = dimCommonPrefix + j
                        Arrays.fill(histogram, 0)
                        histogram[scratch[commonPrefixPosition].toInt() and 0xff] = i - from
                    }
                    if (commonPrefixPosition != bytesSorted) {
                        histogram[getBucket(offset, commonPrefixPosition, pointValue)]++
                    }
                }
            }
        }
        // Build partition buckets up to commonPrefix
        for (i in 0..<commonPrefixPosition) {
            partitionBucket[i] = scratch[i].toInt() and 0xff
        }
        return commonPrefixPosition
    }

    private fun getBucket(offset: Int, commonPrefixPosition: Int, pointValue: PointValue): Int {
        val bucket: Int
        if (commonPrefixPosition < config.bytesPerDim) {
            val packedValue: BytesRef = pointValue.packedValue()
            bucket = packedValue.bytes[packedValue.offset + offset + commonPrefixPosition].toInt() and 0xff
        } else {
            val packedValueDocID: BytesRef = pointValue.packedValueDocIDBytes()
            bucket =
                (packedValueDocID
                    .bytes[(packedValueDocID.offset
                        + config.packedIndexBytesLength()
                        + commonPrefixPosition)
                        - config.bytesPerDim].toInt()
                        and 0xff)
        }
        return bucket
    }

    @Throws(IOException::class)
    private fun buildHistogramAndPartition(
        points: OfflinePointWriter,
        left: PointWriter,
        right: PointWriter,
        from: Long,
        to: Long,
        partitionPoint: Long,
        iteration: Int,
        baseCommonPrefix: Int,
        dim: Int
    ): ByteArray {
        // Find common prefix from baseCommonPrefix and build histogram
        var iteration = iteration
        var commonPrefix = findCommonPrefixAndHistogram(points, from, to, dim, baseCommonPrefix)

        // If all equals we just partition the points
        if (commonPrefix == bytesSorted) {
            offlinePartition(points, left, right, null, from, to, dim, commonPrefix - 1, partitionPoint)
            return partitionPointFromCommonPrefix()
        }

        var leftCount: Long = 0
        var rightCount: Long = 0

        // Count left points and record the partition point
        for (i in 0..<HISTOGRAM_SIZE) {
            val size = histogram[i]
            if (leftCount + size > partitionPoint - from) {
                partitionBucket[commonPrefix] = i
                break
            }
            leftCount += size
        }
        // Count right points
        for (i in partitionBucket[commonPrefix] + 1..<HISTOGRAM_SIZE) {
            rightCount += histogram[i]
        }

        val delta = histogram[partitionBucket[commonPrefix]]
        require(
            leftCount + rightCount + delta == to - from
        ) { (leftCount + rightCount + delta).toString() + " / " + (to - from) }

        // Special case when points are equal except last byte, we can just tie-break
        if (commonPrefix == bytesSorted - 1) {
            val tieBreakCount = (partitionPoint - from - leftCount)
            offlinePartition(points, left, right, null, from, to, dim, commonPrefix, tieBreakCount)
            return partitionPointFromCommonPrefix()
        }

        // Create the delta points writer
        val deltaPoints: PointWriter
        getDeltaPointWriter(left, right, delta, iteration).use { tempDeltaPoints ->
            // Divide the points. This actually destroys the current writer
            offlinePartition(points, left, right, tempDeltaPoints, from, to, dim, commonPrefix, 0)
            deltaPoints = tempDeltaPoints
        }
        val newPartitionPoint = partitionPoint - from - leftCount

        if (deltaPoints is HeapPointWriter) {
            return heapPartition(
                deltaPoints,
                left,
                right,
                dim,
                0,
                deltaPoints.count().toInt(),
                Math.toIntExact(newPartitionPoint),
                ++commonPrefix
            )
        } else {
            return buildHistogramAndPartition(
                deltaPoints as OfflinePointWriter,
                left,
                right,
                0,
                deltaPoints.count(),
                newPartitionPoint,
                ++iteration,
                ++commonPrefix,
                dim
            )
        }
    }

    @Throws(IOException::class)
    private fun offlinePartition(
        points: OfflinePointWriter,
        left: PointWriter,
        right: PointWriter,
        deltaPoints: PointWriter?,
        from: Long,
        to: Long,
        dim: Int,
        bytePosition: Int,
        numDocsTiebreak: Long
    ) {
        require(bytePosition == bytesSorted - 1 || deltaPoints != null)
        val offset: Int = dim * config.bytesPerDim
        var tiebreakCounter: Long = 0
        points.getReader(from, to - from, offlineBuffer).use { reader ->
            while (reader.next()) {
                val pointValue: PointValue = reader.pointValue()
                val bucket = getBucket(offset, bytePosition, pointValue)
                if (bucket < this.partitionBucket[bytePosition]) {
                    // to the left side
                    left.append(pointValue)
                } else if (bucket > this.partitionBucket[bytePosition]) {
                    // to the right side
                    right.append(pointValue)
                } else {
                    if (bytePosition == bytesSorted - 1) {
                        if (tiebreakCounter < numDocsTiebreak) {
                            left.append(pointValue)
                            tiebreakCounter++
                        } else {
                            right.append(pointValue)
                        }
                    } else {
                        deltaPoints?.append(pointValue)
                    }
                }
            }
        }
        // Delete original file
        points.destroy()
    }

    private fun partitionPointFromCommonPrefix(): ByteArray {
        val partition = ByteArray(config.bytesPerDim)
        for (i in 0..<config.bytesPerDim) {
            partition[i] = partitionBucket[i].toByte()
        }
        return partition
    }

    @Throws(IOException::class)
    private fun heapPartition(
        points: HeapPointWriter,
        left: PointWriter,
        right: PointWriter,
        dim: Int,
        from: Int,
        to: Int,
        partitionPoint: Int,
        commonPrefix: Int
    ): ByteArray {
        val partition = heapRadixSelect(points, dim, from, to, partitionPoint, commonPrefix)
        for (i in from..<to) {
            val value: PointValue? = points.getPackedValueSlice(i)
            if (i < partitionPoint) {
                left.append(value!!)
            } else {
                right.append(value!!)
            }
        }
        return partition
    }

    private fun heapRadixSelect(
        points: HeapPointWriter,
        dim: Int,
        from: Int,
        to: Int,
        partitionPoint: Int,
        commonPrefixLength: Int
    ): ByteArray {
        val dimOffset: Int = dim * config.bytesPerDim + commonPrefixLength
        val dimCmpBytes: Int = config.bytesPerDim - commonPrefixLength
        val dataOffset = config.packedIndexBytesLength() - dimCmpBytes
        object : RadixSelector(bytesSorted - commonPrefixLength) {
            override fun swap(i: Int, j: Int) {
                points.swap(i, j)
            }

            override fun byteAt(i: Int, k: Int): Byte {
                require(k >= 0) { "negative prefix $k" }
                return points.byteAt(i, if (k < dimCmpBytes) dimOffset + k else dataOffset + k)
            }

            override fun getFallbackSelector(d: Int): Selector {
                val skypedBytes = d + commonPrefixLength
                val dimStart: Int = dim * config.bytesPerDim
                return object : IntroSelector() {
                    override fun swap(i: Int, j: Int) {
                        points.swap(i, j)
                    }

                    override fun setPivot(i: Int) {
                        if (skypedBytes < config.bytesPerDim) {
                            points.copyDim(i, dimStart, scratch, 0)
                        }
                        points.copyDataDimsAndDoc(i, scratch, config.bytesPerDim)
                    }

                    override fun compare(i: Int, j: Int): Int {
                        if (skypedBytes < config.bytesPerDim) {
                            val cmp: Int = points.compareDim(i, j, dimStart)
                            if (cmp != 0) {
                                return cmp
                            }
                        }
                        return points.compareDataDimsAndDoc(i, j)
                    }

                    override fun comparePivot(j: Int): Int {
                        if (skypedBytes < config.bytesPerDim) {
                            val cmp: Int = points.compareDim(j, scratch, 0, dimStart)
                            if (cmp != 0) {
                                return cmp
                            }
                        }
                        return points.compareDataDimsAndDoc(j, scratch, config.bytesPerDim)
                    }
                }
            }
        }.select(from, to, partitionPoint)

        val partition = ByteArray(config.bytesPerDim)
        val pointValue: PointValue? = points.getPackedValueSlice(partitionPoint)
        val packedValue: BytesRef = pointValue!!.packedValue()
        System.arraycopy(
            packedValue.bytes,
            packedValue.offset + dim * config.bytesPerDim,
            partition,
            0,
            config.bytesPerDim
        )
        return partition
    }

    /** Sort the heap writer by the specified dim. It is used to sort the leaves of the tree  */
    fun heapRadixSort(
        points: HeapPointWriter, from: Int, to: Int, dim: Int, commonPrefixLength: Int
    ) {
        val dimOffset: Int = dim * config.bytesPerDim + commonPrefixLength
        val dimCmpBytes: Int = config.bytesPerDim - commonPrefixLength
        val dataOffset = config.packedIndexBytesLength() - dimCmpBytes
        object : MSBRadixSorter(bytesSorted - commonPrefixLength) {
            override fun byteAt(i: Int, k: Int): Byte {
                require(k >= 0) { "negative prefix $k" }
                return points.byteAt(i, if (k < dimCmpBytes) dimOffset + k else dataOffset + k)
            }

            override fun swap(i: Int, j: Int) {
                points.swap(i, j)
            }

            override fun getFallbackSorter(k: Int): Sorter {
                val skypedBytes = k + commonPrefixLength
                val dimStart: Int = dim * config.bytesPerDim
                return object : IntroSorter() {
                    override fun swap(i: Int, j: Int) {
                        points.swap(i, j)
                    }

                    override fun setPivot(i: Int) {
                        if (skypedBytes < config.bytesPerDim) {
                            points.copyDim(i, dimStart, scratch, 0)
                        }
                        points.copyDataDimsAndDoc(i, scratch, config.bytesPerDim)
                    }

                    override fun compare(i: Int, j: Int): Int {
                        if (skypedBytes < config.bytesPerDim) {
                            val cmp: Int = points.compareDim(i, j, dimStart)
                            if (cmp != 0) {
                                return cmp
                            }
                        }
                        return points.compareDataDimsAndDoc(i, j)
                    }

                    override fun comparePivot(j: Int): Int {
                        if (skypedBytes < config.bytesPerDim) {
                            val cmp: Int = points.compareDim(j, scratch, 0, dimStart)
                            if (cmp != 0) {
                                return cmp
                            }
                        }
                        return points.compareDataDimsAndDoc(j, scratch, config.bytesPerDim)
                    }
                }
            }
        }.sort(from, to)
    }

    @Throws(IOException::class)
    private fun getDeltaPointWriter(
        left: PointWriter, right: PointWriter, delta: Long, iteration: Int
    ): PointWriter {
        return if (delta <= getMaxPointsSortInHeap(left, right)) {
            HeapPointWriter(config, Math.toIntExact(delta))
        } else {
            OfflinePointWriter(
                config, tempDir, tempFileNamePrefix, "delta$iteration", delta
            )
        }
    }

    private fun getMaxPointsSortInHeap(left: PointWriter, right: PointWriter): Int {
        var pointsUsed = 0
        if (left is HeapPointWriter) {
            pointsUsed += left.size
        }
        if (right is HeapPointWriter) {
            pointsUsed += right.size
        }
        require(maxPointsSortInHeap >= pointsUsed)
        return maxPointsSortInHeap - pointsUsed
    }

    @Throws(IOException::class)
    fun getPointWriter(count: Long, desc: String): PointWriter {
        // As we recurse, we hold two on-heap point writers at any point. Therefore the
        // max size for these objects is half of the total points we can have on-heap.
        if (count <= maxPointsSortInHeap / 2) {
            val size: Int = Math.toIntExact(count)
            return HeapPointWriter(config, size)
        } else {
            return OfflinePointWriter(config, tempDir, tempFileNamePrefix, desc, count)
        }
    }

    /** Sliced reference to points in an PointWriter.  */
    @JvmRecord
    data class PathSlice(val writer: PointWriter, val start: Long, val count: Long)
    companion object {
        // size of the histogram
        private const val HISTOGRAM_SIZE = 256

        // size of the online buffer: 8 KB
        private const val MAX_SIZE_OFFLINE_BUFFER = 1024 * 8
    }
}
