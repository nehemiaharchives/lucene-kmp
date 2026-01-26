/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.codecs.simpletext

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.MutablePointTree
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.TrackingDirectoryWrapper
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.util.bkd.BKDConfig
import org.gnit.lucenekmp.util.bkd.BKDRadixSelector
import org.gnit.lucenekmp.util.bkd.BKDWriter
import org.gnit.lucenekmp.util.bkd.HeapPointWriter
import org.gnit.lucenekmp.util.bkd.MutablePointTreeReaderUtils
import org.gnit.lucenekmp.util.bkd.OfflinePointWriter
import org.gnit.lucenekmp.util.bkd.PointWriter

// TODO
//   - allow variable length byte[] (across docs and dims), but this is quite a bit more hairy
//   - we could also index "auto-prefix terms" here, and use better compression, and maybe only use
// for the "fully contained" case so we'd
//     only index docIDs
//   - the index could be efficiently encoded as an FST, so we don't have wasteful
//     (monotonic) long[] leafBlockFPs; or we could use MonotonicLongValues ... but then
//     the index is already plenty small: 60M OSM points --> 1.1 MB with 128 points
//     per leaf, and you can reduce that by putting more points per leaf
//   - we could use threads while building; the higher nodes are very parallelizable

/** Forked from [BKDWriter] and simplified/specialized for SimpleText's usage */
internal class SimpleTextBKDWriter(
    private val maxDoc: Int,
    tempDir: Directory,
    private val tempFileNamePrefix: String,
    protected val config: BKDConfig,
    private val maxMBSortInHeap: Double,
    private val totalPointCount: Long
) : AutoCloseable {

    val scratch = BytesRefBuilder()
    private val tempDir: TrackingDirectoryWrapper

    private val scratchDiff: ByteArray
    private val scratch1: ByteArray
    private val scratch2: ByteArray
    private val scratchBytesRef1 = BytesRef()
    private val scratchBytesRef2 = BytesRef()
    private val commonPrefixLengths: IntArray

    protected val docsSeen: FixedBitSet

    private var pointWriter: PointWriter? = null
    private var finished = false

    private var tempInput: IndexOutput? = null

    private val maxPointsSortInHeap: Int

    /** Minimum per-dim values, packed */
    protected val minPackedValue: ByteArray

    /** Maximum per-dim values, packed */
    protected val maxPackedValue: ByteArray

    var pointCount: Long = 0

    init {
        verifyParams(maxMBSortInHeap, totalPointCount)
        // We use tracking dir to deal with removing files on exception, so each place that
        // creates temp files doesn't need crazy try/finally/sucess logic:
        this.tempDir = TrackingDirectoryWrapper(tempDir)

        docsSeen = FixedBitSet(maxDoc)

        scratchDiff = ByteArray(config.bytesPerDim)
        scratch1 = ByteArray(config.packedBytesLength())
        scratch2 = ByteArray(config.packedBytesLength())
        commonPrefixLengths = IntArray(config.numDims)

        minPackedValue = ByteArray(config.packedIndexBytesLength())
        maxPackedValue = ByteArray(config.packedIndexBytesLength())

        // Maximum number of points we hold in memory at any time
        maxPointsSortInHeap =
            ((maxMBSortInHeap * 1024 * 1024) / (config.bytesPerDoc() * config.numDims)).toInt()

        // Finally, we must be able to hold at least the leaf node in heap during build:
        if (maxPointsSortInHeap < config.maxPointsInLeafNode) {
            throw IllegalArgumentException(
                "maxMBSortInHeap=$maxMBSortInHeap only allows for maxPointsSortInHeap=$maxPointsSortInHeap, but this is less than config.maxPointsInLeafNode=${config.maxPointsInLeafNode}; either increase maxMBSortInHeap or decrease config.maxPointsInLeafNode"
            )
        }
    }

    companion object {
        const val CODEC_NAME: String = "BKD"
        const val VERSION_START: Int = 0
        const val VERSION_COMPRESSED_DOC_IDS: Int = 1
        const val VERSION_COMPRESSED_VALUES: Int = 2
        const val VERSION_IMPLICIT_SPLIT_DIM_1D: Int = 3
        const val VERSION_CURRENT: Int = VERSION_IMPLICIT_SPLIT_DIM_1D

        /** Default maximum heap to use, before spilling to (slower) disk */
        const val DEFAULT_MAX_MB_SORT_IN_HEAP: Float = 16.0f

        fun verifyParams(maxMBSortInHeap: Double, totalPointCount: Long) {
            if (maxMBSortInHeap < 0.0) {
                throw IllegalArgumentException("maxMBSortInHeap must be >= 0.0 (got: $maxMBSortInHeap)")
            }
            if (totalPointCount < 0) {
                throw IllegalArgumentException("totalPointCount must be >=0 (got: $totalPointCount)")
            }
        }
    }

    @Throws(IOException::class)
    fun add(packedValue: ByteArray, docID: Int) {
        if (packedValue.size != config.packedBytesLength()) {
            throw IllegalArgumentException(
                "packedValue should be length=${config.packedBytesLength()} (got: ${packedValue.size})"
            )
        }
        if (pointCount >= totalPointCount) {
            throw IllegalStateException(
                "totalPointCount=$totalPointCount was passed when we were created, but we just hit ${pointCount + 1} values"
            )
        }
        if (pointCount == 0L) {
            assert(pointWriter == null) { "Point writer is already initialized" }
            // total point count is an estimation but the final point count must be equal or lower to that
            // number.
            pointWriter =
                if (totalPointCount > maxPointsSortInHeap) {
                    val writer =
                        OfflinePointWriter(config, tempDir, tempFileNamePrefix, "spill", 0)
                    tempInput = writer.out
                    writer
                } else {
                    HeapPointWriter(config, Math.toIntExact(totalPointCount))
                }
            packedValue.copyInto(minPackedValue, 0, 0, config.packedIndexBytesLength())
            packedValue.copyInto(maxPackedValue, 0, 0, config.packedIndexBytesLength())
        } else {
            for (dim in 0 until config.numIndexDims) {
                val offset = dim * config.bytesPerDim
                if (
                    Arrays.compareUnsigned(
                        packedValue,
                        offset,
                        offset + config.bytesPerDim,
                        minPackedValue,
                        offset,
                        offset + config.bytesPerDim
                    ) < 0
                ) {
                    packedValue.copyInto(minPackedValue, offset, offset, offset + config.bytesPerDim)
                }
                if (
                    Arrays.compareUnsigned(
                        packedValue,
                        offset,
                        offset + config.bytesPerDim,
                        maxPackedValue,
                        offset,
                        offset + config.bytesPerDim
                    ) > 0
                ) {
                    packedValue.copyInto(maxPackedValue, offset, offset, offset + config.bytesPerDim)
                }
            }
        }
        requireNotNull(pointWriter).append(packedValue, docID)
        pointCount++
        docsSeen.set(docID)
    }

    /** How many points have been added so far */
    val pointCountValue: Long
        get() = pointCount

    /**
     * Write a field from a [MutablePointTree]. This way of writing points is faster than
     * regular writes with [BKDWriter.add] since there is opportunity for reordering points
     * before writing them to disk. This method does not use transient disk in order to reorder
     * points.
     */
    @Throws(IOException::class)
    fun writeField(out: IndexOutput, fieldName: String, reader: MutablePointTree): Long {
        return if (config.numIndexDims == 1) {
            writeField1Dim(out, fieldName, reader)
        } else {
            writeFieldNDims(out, fieldName, reader)
        }
    }

    /* In the 2+D case, we recursively pick the split dimension, compute the
     * median value and partition other values around it. */
    @Throws(IOException::class)
    private fun writeFieldNDims(out: IndexOutput, fieldName: String, values: MutablePointTree): Long {
        if (pointCount != 0L) {
            throw IllegalStateException("cannot mix add and writeField")
        }

        // Catch user silliness:
        if (finished) {
            throw IllegalStateException("already finished")
        }

        // Mark that we already finished:
        finished = true

        var countPerLeaf = (values.size()).also { pointCount = it }
        var innerNodeCount = 1L

        while (countPerLeaf > config.maxPointsInLeafNode) {
            countPerLeaf = (countPerLeaf + 1) / 2
            innerNodeCount *= 2
        }

        val numLeaves = Math.toIntExact(innerNodeCount)

        checkMaxLeafNodeCount(numLeaves)

        val splitPackedValues = ByteArray(numLeaves * (config.bytesPerDim + 1))
        val leafBlockFPs = LongArray(numLeaves)

        // compute the min/max for this slice
        minPackedValue.fill(0xff.toByte())
        maxPackedValue.fill(0)
        for (i in 0 until Math.toIntExact(pointCount)) {
            values.getValue(i, scratchBytesRef1)
            for (dim in 0 until config.numIndexDims) {
                val offset = dim * config.bytesPerDim
                if (
                    Arrays.compareUnsigned(
                        scratchBytesRef1.bytes,
                        scratchBytesRef1.offset + offset,
                        scratchBytesRef1.offset + offset + config.bytesPerDim,
                        minPackedValue,
                        offset,
                        offset + config.bytesPerDim
                    ) < 0
                ) {
                    scratchBytesRef1.bytes.copyInto(
                        minPackedValue,
                        destinationOffset = offset,
                        startIndex = scratchBytesRef1.offset + offset,
                        endIndex = scratchBytesRef1.offset + offset + config.bytesPerDim
                    )
                }
                if (
                    Arrays.compareUnsigned(
                        scratchBytesRef1.bytes,
                        scratchBytesRef1.offset + offset,
                        scratchBytesRef1.offset + offset + config.bytesPerDim,
                        maxPackedValue,
                        offset,
                        offset + config.bytesPerDim
                    ) > 0
                ) {
                    scratchBytesRef1.bytes.copyInto(
                        maxPackedValue,
                        destinationOffset = offset,
                        startIndex = scratchBytesRef1.offset + offset,
                        endIndex = scratchBytesRef1.offset + offset + config.bytesPerDim
                    )
                }
            }

            docsSeen.set(values.getDocID(i))
        }

        build(
            1,
            numLeaves,
            values,
            0,
            Math.toIntExact(pointCount),
            out,
            minPackedValue,
            maxPackedValue,
            splitPackedValues,
            leafBlockFPs,
            IntArray(config.maxPointsInLeafNode)
        )

        val indexFP = out.filePointer
        writeIndex(out, leafBlockFPs, splitPackedValues, Math.toIntExact(countPerLeaf))
        return indexFP
    }

    /* In the 1D case, we can simply sort points in ascending order and use the
     * same writing logic as we use at merge time. */
    @Throws(IOException::class)
    private fun writeField1Dim(out: IndexOutput, fieldName: String, reader: MutablePointTree): Long {
        MutablePointTreeReaderUtils.sort(config, maxDoc, reader, 0, Math.toIntExact(reader.size()))

        val oneDimWriter = OneDimensionBKDWriter(out)

        reader.visitDocValues(
            object : IntersectVisitor {
                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                    oneDimWriter.add(packedValue, docID)
                }

                @Throws(IOException::class)
                override fun visit(docID: Int) {
                    throw IllegalStateException()
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                    return Relation.CELL_CROSSES_QUERY
                }
            }
        )

        return oneDimWriter.finish()
    }

    private inner class OneDimensionBKDWriter(private val out: IndexOutput) {

        private val leafBlockFPs: MutableList<Long> = ArrayList()
        private val leafBlockStartValues: MutableList<ByteArray> = ArrayList()
        private val leafValues = ByteArray(config.maxPointsInLeafNode * config.packedBytesLength())
        private val leafDocs = IntArray(config.maxPointsInLeafNode)
        private var valueCount = 0L
        private var leafCount = 0

        // for asserts
        private val lastPackedValue: ByteArray
        private var lastDocID = 0

        init {
            if (config.numIndexDims != 1) {
                throw UnsupportedOperationException(
                    "config.numIndexDims must be 1 but got ${config.numIndexDims}"
                )
            }
            if (pointCount != 0L) {
                throw IllegalStateException("cannot mix add and merge")
            }

            // Catch user silliness:
            if (finished) {
                throw IllegalStateException("already finished")
            }

            // Mark that we already finished:
            finished = true

            lastPackedValue = ByteArray(config.packedBytesLength())
        }

        @Throws(IOException::class)
        fun add(packedValue: ByteArray, docID: Int) {
            assert(
                valueInOrder(
                    valueCount + leafCount,
                    0,
                    lastPackedValue,
                    packedValue,
                    0,
                    docID,
                    lastDocID
                )
            )

            packedValue.copyInto(
                leafValues,
                destinationOffset = leafCount * config.packedBytesLength(),
                startIndex = 0,
                endIndex = config.packedBytesLength()
            )
            leafDocs[leafCount] = docID
            docsSeen.set(docID)
            leafCount++

            if (valueCount > totalPointCount) {
                throw IllegalStateException(
                    "totalPointCount=$totalPointCount was passed when we were created, but we just hit $pointCount values"
                )
            }

            if (leafCount == config.maxPointsInLeafNode) {
                // We write a block once we hit exactly the max count ... this is different from
                // when we flush a new segment, where we write between max/2 and max per leaf block,
                // so merged segments will behave differently from newly flushed segments:
                writeLeafBlock()
                leafCount = 0
            }

            assert((lastDocID.also { lastDocID = docID }) >= 0)
        }

        @Throws(IOException::class)
        fun finish(): Long {
            if (leafCount > 0) {
                writeLeafBlock()
                leafCount = 0
            }

            if (valueCount == 0L) {
                return -1
            }

            pointCount = valueCount

            val indexFP = out.filePointer

            val numInnerNodes = leafBlockStartValues.size

            val index = ByteArray((1 + numInnerNodes) * (1 + config.bytesPerDim))
            rotateToTree(1, 0, numInnerNodes, index, leafBlockStartValues)
            val arr = LongArray(leafBlockFPs.size)
            for (i in leafBlockFPs.indices) {
                arr[i] = leafBlockFPs[i]
            }
            writeIndex(out, arr, index, config.maxPointsInLeafNode)
            return indexFP
        }

        @Throws(IOException::class)
        private fun writeLeafBlock() {
            assert(leafCount != 0)
            if (valueCount == 0L) {
                leafValues.copyInto(minPackedValue, 0, 0, config.packedIndexBytesLength())
            }
            leafValues.copyInto(
                maxPackedValue,
                destinationOffset = 0,
                startIndex = (leafCount - 1) * config.packedBytesLength(),
                endIndex = (leafCount - 1) * config.packedBytesLength() + config.packedIndexBytesLength()
            )

            valueCount += leafCount.toLong()

            if (leafBlockFPs.isNotEmpty()) {
                // Save the first (minimum) value in each leaf block except the first, to build the split
                // value index in the end:
                leafBlockStartValues.add(
                    ArrayUtil.copyOfSubArray(leafValues, 0, config.packedBytesLength())
                )
            }
            leafBlockFPs.add(out.filePointer)
            checkMaxLeafNodeCount(leafBlockFPs.size)

            commonPrefixLengths.fill(config.bytesPerDim)
            // Find per-dim common prefix:
            for (dim in 0 until config.numDims) {
                val offset1 = dim * config.bytesPerDim
                val offset2 = (leafCount - 1) * config.packedBytesLength() + offset1
                for (j in 0 until commonPrefixLengths[dim]) {
                    if (leafValues[offset1 + j] != leafValues[offset2 + j]) {
                        commonPrefixLengths[dim] = j
                        break
                    }
                }
            }

            writeLeafBlockDocs(out, leafDocs, 0, leafCount)

            val packedValues: (Int) -> BytesRef = object : (Int) -> BytesRef {
                private val scratch = BytesRef().apply {
                    length = config.packedBytesLength()
                    bytes = leafValues
                }

                override fun invoke(i: Int): BytesRef {
                    scratch.offset = config.packedBytesLength() * i
                    return scratch
                }
            }
            assert(
                valuesInOrderAndBounds(
                    leafCount,
                    0,
                    ArrayUtil.copyOfSubArray(leafValues, 0, config.packedBytesLength()),
                    ArrayUtil.copyOfSubArray(
                        leafValues,
                        (leafCount - 1) * config.packedBytesLength(),
                        leafCount * config.packedBytesLength()
                    ),
                    packedValues,
                    leafDocs,
                    0
                )
            )
            writeLeafBlockPackedValues(out, commonPrefixLengths, leafCount, 0, packedValues)
        }
    }

    // TODO: there must be a simpler way?
    private fun rotateToTree(
        nodeID: Int,
        offset: Int,
        count: Int,
        index: ByteArray,
        leafBlockStartValues: List<ByteArray>
    ) {
        if (count == 1) {
            // Leaf index node
            leafBlockStartValues[offset].copyInto(
                index,
                destinationOffset = nodeID * (1 + config.bytesPerDim) + 1,
                startIndex = 0,
                endIndex = config.bytesPerDim
            )
        } else if (count > 1) {
            // Internal index node: binary partition of count
            var countAtLevel = 1
            var totalCount = 0
            while (true) {
                val countLeft = count - totalCount
                if (countLeft <= countAtLevel) {
                    // This is the last level, possibly partially filled:
                    val lastLeftCount = kotlin.math.min(countAtLevel / 2, countLeft)
                    require(lastLeftCount >= 0)
                    val leftHalf = (totalCount - 1) / 2 + lastLeftCount

                    val rootOffset = offset + leftHalf

                    leafBlockStartValues[rootOffset].copyInto(
                        index,
                        destinationOffset = nodeID * (1 + config.bytesPerDim) + 1,
                        startIndex = 0,
                        endIndex = config.bytesPerDim
                    )

                    // Recurse left
                    rotateToTree(2 * nodeID, offset, leftHalf, index, leafBlockStartValues)

                    // Recurse right
                    rotateToTree(
                        2 * nodeID + 1,
                        rootOffset + 1,
                        count - leftHalf - 1,
                        index,
                        leafBlockStartValues
                    )
                    return
                }
                totalCount += countAtLevel
                countAtLevel *= 2
            }
        } else {
            assert(count == 0)
        }
    }

    private fun checkMaxLeafNodeCount(numLeaves: Int) {
        if ((1 + config.bytesPerDim).toLong() * numLeaves > ArrayUtil.MAX_ARRAY_LENGTH.toLong()) {
            throw IllegalStateException(
                "too many nodes; increase config.maxPointsInLeafNode (currently ${config.maxPointsInLeafNode}) and reindex"
            )
        }
    }

    /**
     * Writes the BKD tree to the provided [IndexOutput] and returns the file offset where index
     * was written.
     */
    @Throws(IOException::class)
    fun finish(out: IndexOutput): Long {
        // Catch user silliness:
        if (pointCount == 0L) {
            throw IllegalStateException("must index at least one point")
        }

        // Catch user silliness:
        if (finished) {
            throw IllegalStateException("already finished")
        }

        // mark as finished
        finished = true

        pointWriter!!.close()
        val points = BKDRadixSelector.PathSlice(pointWriter!!, 0, pointCount)
        // clean up pointers
        tempInput = null
        pointWriter = null

        var countPerLeaf = pointCount
        var innerNodeCount = 1L

        while (countPerLeaf > config.maxPointsInLeafNode) {
            countPerLeaf = (countPerLeaf + 1) / 2
            innerNodeCount *= 2
        }

        val numLeaves = innerNodeCount.toInt()

        checkMaxLeafNodeCount(numLeaves)

        // Indexed by nodeID, but first (root) nodeID is 1.  We do 1+ because the lead byte at each
        // recursion says which dim we split on.
        val splitPackedValues = ByteArray(Math.multiplyExact(numLeaves, 1 + config.bytesPerDim))

        // +1 because leaf count is power of 2 (e.g. 8), and innerNodeCount is power of 2 minus 1 (e.g.
        // 7)
        val leafBlockFPs = LongArray(numLeaves)

        // Make sure the math above "worked":
        assert(pointCount / numLeaves <= config.maxPointsInLeafNode.toLong()) {
            "pointCount=$pointCount numLeaves=$numLeaves config.maxPointsInLeafNode=${config.maxPointsInLeafNode}"
        }

        // We re-use the selector so we do not need to create an object every time.
        val radixSelector = BKDRadixSelector(config, maxPointsSortInHeap, tempDir, tempFileNamePrefix)

        var success = false
        try {
            build(
                1,
                numLeaves,
                points,
                out,
                radixSelector,
                minPackedValue,
                maxPackedValue,
                splitPackedValues,
                leafBlockFPs,
                IntArray(config.maxPointsInLeafNode)
            )

            // If no exception, we should have cleaned everything up:
            assert(tempDir.createdFiles.isEmpty())

            success = true
        } finally {
            if (!success) {
                IOUtils.deleteFilesIgnoringExceptions(tempDir, tempDir.createdFiles)
            }
        }

        // Write index:
        val indexFP = out.filePointer
        writeIndex(out, leafBlockFPs, splitPackedValues, Math.toIntExact(countPerLeaf))
        return indexFP
    }

    /** Subclass can change how it writes the index. */
    @Throws(IOException::class)
    private fun writeIndex(
        out: IndexOutput,
        leafBlockFPs: LongArray,
        splitPackedValues: ByteArray,
        maxPointsInLeafNode: Int
    ) {
        write(out, SimpleTextPointsWriter.NUM_DATA_DIMS)
        writeInt(out, config.numDims)
        newline(out)

        write(out, SimpleTextPointsWriter.NUM_INDEX_DIMS)
        writeInt(out, config.numIndexDims)
        newline(out)

        write(out, SimpleTextPointsWriter.BYTES_PER_DIM)
        writeInt(out, config.bytesPerDim)
        newline(out)

        write(out, SimpleTextPointsWriter.MAX_LEAF_POINTS)
        writeInt(out, maxPointsInLeafNode)
        newline(out)

        write(out, SimpleTextPointsWriter.INDEX_COUNT)
        writeInt(out, leafBlockFPs.size)
        newline(out)

        write(out, SimpleTextPointsWriter.MIN_VALUE)
        var br = BytesRef(minPackedValue, 0, minPackedValue.size)
        write(out, br.toString())
        newline(out)

        write(out, SimpleTextPointsWriter.MAX_VALUE)
        br = BytesRef(maxPackedValue, 0, maxPackedValue.size)
        write(out, br.toString())
        newline(out)

        write(out, SimpleTextPointsWriter.POINT_COUNT)
        writeLong(out, pointCount)
        newline(out)

        write(out, SimpleTextPointsWriter.DOC_COUNT)
        writeInt(out, docsSeen.cardinality())
        newline(out)

        for (fp in leafBlockFPs) {
            write(out, SimpleTextPointsWriter.BLOCK_FP)
            writeLong(out, fp)
            newline(out)
        }

        assert(splitPackedValues.size % (1 + config.bytesPerDim) == 0)
        val count = splitPackedValues.size / (1 + config.bytesPerDim)
        assert(count == leafBlockFPs.size)

        write(out, SimpleTextPointsWriter.SPLIT_COUNT)
        writeInt(out, count)
        newline(out)

        for (i in 0 until count) {
            write(out, SimpleTextPointsWriter.SPLIT_DIM)
            writeInt(out, splitPackedValues[i * (1 + config.bytesPerDim)].toInt() and 0xff)
            newline(out)
            write(out, SimpleTextPointsWriter.SPLIT_VALUE)
            br = BytesRef(
                splitPackedValues,
                1 + (i * (1 + config.bytesPerDim)),
                config.bytesPerDim
            )
            write(out, br.toString())
            newline(out)
        }
    }

    @Throws(IOException::class)
    protected fun writeLeafBlockDocs(out: IndexOutput, docIDs: IntArray, start: Int, count: Int) {
        write(out, SimpleTextPointsWriter.BLOCK_COUNT)
        writeInt(out, count)
        newline(out)
        for (i in 0 until count) {
            write(out, SimpleTextPointsWriter.BLOCK_DOC_ID)
            writeInt(out, docIDs[start + i])
            newline(out)
        }
    }

    @Throws(IOException::class)
    protected fun writeLeafBlockPackedValues(
        out: IndexOutput,
        commonPrefixLengths: IntArray,
        count: Int,
        sortedDim: Int,
        packedValues: (Int) -> BytesRef
    ) {
        for (i in 0 until count) {
            val packedValue = packedValues(i)
            // NOTE: we don't do prefix coding, so we ignore commonPrefixLengths
            write(out, SimpleTextPointsWriter.BLOCK_VALUE)
            write(out, packedValue.toString())
            newline(out)
        }
    }
    override fun close() {
        if (tempInput != null) {
            // NOTE: this should only happen on exception, e.g. caller calls close w/o calling finish:
            try {
                tempInput!!.close()
            } finally {
                tempDir.deleteFile(requireNotNull(tempInput).name!!)
                tempInput = null
            }
        }
    }

    /**
     * Called on exception, to check whether the checksum is also corrupt in this source, and add that
     * information (checksum matched or didn't) as a suppressed exception.
     */
    @Throws(IOException::class)
    private fun verifyChecksum(priorException: Throwable, writer: PointWriter): Error {
        // TODO: we could improve this, to always validate checksum as we recurse, if we shared left and
        // right reader after recursing to children, and possibly within recursed children,
        // since all together they make a single pass through the file.  But this is a sizable re-org,
        // and would mean leaving readers (IndexInputs) open for longer:
        if (writer is OfflinePointWriter) {
            // We are reading from a temp file; go verify the checksum:
            val tempFileName = writer.name
            tempDir.openChecksumInput(tempFileName).use { `in` ->
                CodecUtil.checkFooter(`in`, priorException)
            }
        }

        // We are reading from heap; nothing to add:
        throw IOUtils.rethrowAlways(priorException)
    }

    /** Called only in assert */
    private fun valueInBounds(
        packedValue: BytesRef,
        minPackedValue: ByteArray,
        maxPackedValue: ByteArray
    ): Boolean {
        for (dim in 0 until config.numIndexDims) {
            val offset = config.bytesPerDim * dim
            if (
                Arrays.compareUnsigned(
                    packedValue.bytes,
                    packedValue.offset + offset,
                    packedValue.offset + offset + config.bytesPerDim,
                    minPackedValue,
                    offset,
                    offset + config.bytesPerDim
                ) < 0
            ) {
                return false
            }
            if (
                Arrays.compareUnsigned(
                    packedValue.bytes,
                    packedValue.offset + offset,
                    packedValue.offset + offset + config.bytesPerDim,
                    maxPackedValue,
                    offset,
                    offset + config.bytesPerDim
                ) > 0
            ) {
                return false
            }
        }

        return true
    }

    protected fun split(minPackedValue: ByteArray, maxPackedValue: ByteArray): Int {
        // Find which dim has the largest span so we can split on it:
        var splitDim = -1
        for (dim in 0 until config.numIndexDims) {
            NumericUtils.subtract(config.bytesPerDim, dim, maxPackedValue, minPackedValue, scratchDiff)
            if (
                splitDim == -1 ||
                Arrays.compareUnsigned(
                    scratchDiff,
                    0,
                    config.bytesPerDim,
                    scratch1,
                    0,
                    config.bytesPerDim
                ) > 0
            ) {
                scratchDiff.copyInto(scratch1, 0, 0, config.bytesPerDim)
                splitDim = dim
            }
        }

        return splitDim
    }

    /** Pull a partition back into heap once the point count is low enough while recursing. */
    @Throws(IOException::class)
    private fun switchToHeap(source: PointWriter): HeapPointWriter {
        val count = Math.toIntExact(source.count())
        try {
            source.getReader(0, count.toLong()).use { reader ->
                HeapPointWriter(config, count).use { writer ->
                    for (i in 0 until count) {
                        val hasNext = reader.next()
                        assert(hasNext)
                        writer.append(reader.pointValue()!!)
                    }
                    return writer
                }
            }
        } catch (t: Throwable) {
            throw verifyChecksum(t, source)
        }
    }

    /* Recursively reorders the provided reader and writes the bkd-tree on the fly. */
    @Throws(IOException::class)
    private fun build(
        nodeID: Int,
        leafNodeOffset: Int,
        reader: MutablePointTree,
        from: Int,
        to: Int,
        out: IndexOutput,
        minPackedValue: ByteArray,
        maxPackedValue: ByteArray,
        splitPackedValues: ByteArray,
        leafBlockFPs: LongArray,
        spareDocIds: IntArray
    ) {

        if (nodeID >= leafNodeOffset) {
            // leaf node
            val count = to - from
            assert(count <= config.maxPointsInLeafNode)

            // Compute common prefixes
            commonPrefixLengths.fill(config.bytesPerDim)
            reader.getValue(from, scratchBytesRef1)
            for (i in from + 1 until to) {
                reader.getValue(i, scratchBytesRef2)
                for (dim in 0 until config.numDims) {
                    val offset = dim * config.bytesPerDim
                    for (j in 0 until commonPrefixLengths[dim]) {
                        if (
                            scratchBytesRef1.bytes[scratchBytesRef1.offset + offset + j]
                            != scratchBytesRef2.bytes[scratchBytesRef2.offset + offset + j]
                        ) {
                            commonPrefixLengths[dim] = j
                            break
                        }
                    }
                }
            }

            // Find the dimension that has the least number of unique bytes at commonPrefixLengths[dim]
            val usedBytes = arrayOfNulls<FixedBitSet>(config.numDims)
            for (dim in 0 until config.numDims) {
                if (commonPrefixLengths[dim] < config.bytesPerDim) {
                    usedBytes[dim] = FixedBitSet(256)
                }
            }
            for (i in from + 1 until to) {
                for (dim in 0 until config.numDims) {
                    val set = usedBytes[dim]
                    if (set != null) {
                        val b = reader.getByteAt(i, dim * config.bytesPerDim + commonPrefixLengths[dim])
                        set.set(b.toInt() and 0xff)
                    }
                }
            }
            var sortedDim = 0
            var sortedDimCardinality = Int.MAX_VALUE
            for (dim in 0 until config.numDims) {
                val set = usedBytes[dim]
                if (set != null) {
                    val cardinality = set.cardinality()
                    if (cardinality < sortedDimCardinality) {
                        sortedDim = dim
                        sortedDimCardinality = cardinality
                    }
                }
            }

            // sort by sortedDim
            MutablePointTreeReaderUtils.sortByDim(
                config,
                sortedDim,
                commonPrefixLengths,
                reader,
                from,
                to,
                scratchBytesRef1,
                scratchBytesRef2
            )

            // Save the block file pointer:
            leafBlockFPs[nodeID - leafNodeOffset] = out.filePointer

            // Write doc IDs
            val docIDs = spareDocIds
            for (i in from until to) {
                docIDs[i - from] = reader.getDocID(i)
            }
            writeLeafBlockDocs(out, docIDs, 0, count)

            // Write the common prefixes:
            reader.getValue(from, scratchBytesRef1)
            scratchBytesRef1.bytes.copyInto(
                scratch1,
                destinationOffset = 0,
                startIndex = scratchBytesRef1.offset,
                endIndex = scratchBytesRef1.offset + config.packedBytesLength()
            )

            // Write the full values:
            val packedValues: (Int) -> BytesRef = { i ->
                reader.getValue(from + i, scratchBytesRef1)
                scratchBytesRef1
            }
            assert(valuesInOrderAndBounds(count, sortedDim, minPackedValue, maxPackedValue, packedValues, docIDs, 0))
            writeLeafBlockPackedValues(out, commonPrefixLengths, count, sortedDim, packedValues)

        } else {
            // inner node

            // compute the split dimension and partition around it
            val splitDim = split(minPackedValue, maxPackedValue)
            val mid = (from + to + 1) ushr 1

            var commonPrefixLen = config.bytesPerDim
            for (i in 0 until config.bytesPerDim) {
                if (
                    minPackedValue[splitDim * config.bytesPerDim + i]
                    != maxPackedValue[splitDim * config.bytesPerDim + i]
                ) {
                    commonPrefixLen = i
                    break
                }
            }
            MutablePointTreeReaderUtils.partition(
                config,
                maxDoc,
                splitDim,
                commonPrefixLen,
                reader,
                from,
                to,
                mid,
                scratchBytesRef1,
                scratchBytesRef2
            )

            // set the split value
            val address = nodeID * (1 + config.bytesPerDim)
            splitPackedValues[address] = splitDim.toByte()
            reader.getValue(mid, scratchBytesRef1)
            scratchBytesRef1.bytes.copyInto(
                splitPackedValues,
                destinationOffset = address + 1,
                startIndex = scratchBytesRef1.offset + splitDim * config.bytesPerDim,
                endIndex = scratchBytesRef1.offset + splitDim * config.bytesPerDim + config.bytesPerDim
            )

            val minSplitPackedValue = ArrayUtil.copyOfSubArray(minPackedValue, 0, config.packedIndexBytesLength())
            val maxSplitPackedValue = ArrayUtil.copyOfSubArray(maxPackedValue, 0, config.packedIndexBytesLength())
            scratchBytesRef1.bytes.copyInto(
                minSplitPackedValue,
                destinationOffset = splitDim * config.bytesPerDim,
                startIndex = scratchBytesRef1.offset + splitDim * config.bytesPerDim,
                endIndex = scratchBytesRef1.offset + splitDim * config.bytesPerDim + config.bytesPerDim
            )
            scratchBytesRef1.bytes.copyInto(
                maxSplitPackedValue,
                destinationOffset = splitDim * config.bytesPerDim,
                startIndex = scratchBytesRef1.offset + splitDim * config.bytesPerDim,
                endIndex = scratchBytesRef1.offset + splitDim * config.bytesPerDim + config.bytesPerDim
            )

            // recurse
            build(
                nodeID * 2,
                leafNodeOffset,
                reader,
                from,
                mid,
                out,
                minPackedValue,
                maxSplitPackedValue,
                splitPackedValues,
                leafBlockFPs,
                spareDocIds
            )
            build(
                nodeID * 2 + 1,
                leafNodeOffset,
                reader,
                mid,
                to,
                out,
                minSplitPackedValue,
                maxPackedValue,
                splitPackedValues,
                leafBlockFPs,
                spareDocIds
            )
        }
    }

    /** The array (sized numDims) of PathSlice describe the cell we have currently recursed to. */
    @Throws(IOException::class)
    private fun build(
        nodeID: Int,
        leafNodeOffset: Int,
        points: BKDRadixSelector.PathSlice,
        out: IndexOutput,
        radixSelector: BKDRadixSelector,
        minPackedValue: ByteArray,
        maxPackedValue: ByteArray,
        splitPackedValues: ByteArray,
        leafBlockFPs: LongArray,
        spareDocIds: IntArray
    ) {

        if (nodeID >= leafNodeOffset) {

            // Leaf node: write block
            // We can write the block in any order so by default we write it sorted by the dimension that
            // has the
            // least number of unique bytes at commonPrefixLengths[dim], which makes compression more
            // efficient
            val heapSource: HeapPointWriter = if (points.writer !is HeapPointWriter) {
                // Adversarial cases can cause this, e.g. merging big segments with most of the points
                // deleted
                switchToHeap(points.writer)
            } else {
                points.writer as HeapPointWriter
            }

            val from = Math.toIntExact(points.start)
            val to = Math.toIntExact(points.start + points.count)

            // we store common prefix on scratch1
            computeCommonPrefixLength(heapSource, scratch1)

            var sortedDim = 0
            var sortedDimCardinality = Int.MAX_VALUE
            val usedBytes = arrayOfNulls<FixedBitSet>(config.numDims)
            for (dim in 0 until config.numDims) {
                if (commonPrefixLengths[dim] < config.bytesPerDim) {
                    usedBytes[dim] = FixedBitSet(256)
                }
            }
            // Find the dimension to compress
            for (dim in 0 until config.numDims) {
                val prefix = commonPrefixLengths[dim]
                if (prefix < config.bytesPerDim) {
                    val offset = dim * config.bytesPerDim
                    for (i in 0 until heapSource.count()) {
                        val value = heapSource.getPackedValueSlice(i.toInt())
                        val packedValue = value!!.packedValue()
                        val bucket = packedValue.bytes[packedValue.offset + offset + prefix].toInt() and 0xff
                        usedBytes[dim]!!.set(bucket)
                    }
                    val cardinality = usedBytes[dim]!!.cardinality()
                    if (cardinality < sortedDimCardinality) {
                        sortedDim = dim
                        sortedDimCardinality = cardinality
                    }
                }
            }

            // sort the chosen dimension
            radixSelector.heapRadixSort(
                heapSource,
                from,
                to,
                sortedDim,
                commonPrefixLengths[sortedDim]
            )

            // Save the block file pointer:
            leafBlockFPs[nodeID - leafNodeOffset] = out.filePointer

            // Write docIDs first, as their own chunk, so that at intersect time we can add all docIDs w/o
            // loading the values:
            val count = to - from
            assert(count > 0) { "nodeID=$nodeID leafNodeOffset=$leafNodeOffset" }
            // Write doc IDs
            val docIDs = spareDocIds
            for (i in 0 until count) {
                docIDs[i] = heapSource.getPackedValueSlice(from + i)!!.docID()
            }
            writeLeafBlockDocs(out, spareDocIds, 0, count)

            // Write the full values:
            val packedValues: (Int) -> BytesRef = { i ->
                val value = heapSource.getPackedValueSlice(from + i)
                value!!.packedValue()
            }
            assert(valuesInOrderAndBounds(count, sortedDim, minPackedValue, maxPackedValue, packedValues, docIDs, 0))
            writeLeafBlockPackedValues(out, commonPrefixLengths, count, sortedDim, packedValues)

        } else {
            // Inner node: partition/recurse

            val splitDim = if (config.numIndexDims > 1) {
                split(minPackedValue, maxPackedValue)
            } else {
                0
            }

            assert(nodeID < splitPackedValues.size) {
                "nodeID=$nodeID splitValues.length=${splitPackedValues.size}"
            }

            // How many points will be in the left tree:
            val rightCount = points.count / 2
            val leftCount = points.count - rightCount

            var commonPrefixLen =
                Arrays.mismatch(
                    minPackedValue,
                    splitDim * config.bytesPerDim,
                    splitDim * config.bytesPerDim + config.bytesPerDim,
                    maxPackedValue,
                    splitDim * config.bytesPerDim,
                    splitDim * config.bytesPerDim + config.bytesPerDim
                )
            if (commonPrefixLen == -1) {
                commonPrefixLen = config.bytesPerDim
            }

            val pathSlices = arrayOfNulls<BKDRadixSelector.PathSlice>(2)

            val splitValue =
                radixSelector.select(
                    points,
                    pathSlices,
                    points.start,
                    points.start + points.count,
                    points.start + leftCount,
                    splitDim,
                    commonPrefixLen
                )

            val address = nodeID * (1 + config.bytesPerDim)
            splitPackedValues[address] = splitDim.toByte()
            splitValue.copyInto(splitPackedValues, address + 1, 0, config.bytesPerDim)

            val minSplitPackedValue = ByteArray(config.packedIndexBytesLength())
            minPackedValue.copyInto(minSplitPackedValue, 0, 0, config.packedIndexBytesLength())

            val maxSplitPackedValue = ByteArray(config.packedIndexBytesLength())
            maxPackedValue.copyInto(maxSplitPackedValue, 0, 0, config.packedIndexBytesLength())

            splitValue.copyInto(
                minSplitPackedValue,
                destinationOffset = splitDim * config.bytesPerDim,
                startIndex = 0,
                endIndex = config.bytesPerDim
            )
            splitValue.copyInto(
                maxSplitPackedValue,
                destinationOffset = splitDim * config.bytesPerDim,
                startIndex = 0,
                endIndex = config.bytesPerDim
            )

            // Recurse on left tree:
            build(
                2 * nodeID,
                leafNodeOffset,
                requireNotNull(pathSlices[0]),
                out,
                radixSelector,
                minPackedValue,
                maxSplitPackedValue,
                splitPackedValues,
                leafBlockFPs,
                spareDocIds
            )

            // Recurse on right tree:
            build(
                2 * nodeID + 1,
                leafNodeOffset,
                requireNotNull(pathSlices[1]),
                out,
                radixSelector,
                minSplitPackedValue,
                maxPackedValue,
                splitPackedValues,
                leafBlockFPs,
                spareDocIds
            )
        }
    }

    private fun computeCommonPrefixLength(heapPointWriter: HeapPointWriter, commonPrefix: ByteArray) {
        commonPrefixLengths.fill(config.bytesPerDim)
        var value = heapPointWriter.getPackedValueSlice(0)
        var packedValue = value!!.packedValue()
        for (dim in 0 until config.numDims) {
            packedValue.bytes.copyInto(
                commonPrefix,
                destinationOffset = dim * config.bytesPerDim,
                startIndex = packedValue.offset + dim * config.bytesPerDim,
                endIndex = packedValue.offset + dim * config.bytesPerDim + config.bytesPerDim
            )
        }
        for (i in 1 until heapPointWriter.count()) {
            value = heapPointWriter.getPackedValueSlice(i.toInt())
            packedValue = value!!.packedValue()
            for (dim in 0 until config.numDims) {
                if (commonPrefixLengths[dim] != 0) {
                    val j =
                        Arrays.mismatch(
                            commonPrefix,
                            dim * config.bytesPerDim,
                            dim * config.bytesPerDim + commonPrefixLengths[dim],
                            packedValue.bytes,
                            packedValue.offset + dim * config.bytesPerDim,
                            packedValue.offset + dim * config.bytesPerDim + commonPrefixLengths[dim]
                        )
                    if (j != -1) {
                        commonPrefixLengths[dim] = j
                    }
                }
            }
        }
    }

    // only called from assert
    @Throws(IOException::class)
    private fun valuesInOrderAndBounds(
        count: Int,
        sortedDim: Int,
        minPackedValue: ByteArray,
        maxPackedValue: ByteArray,
        values: (Int) -> BytesRef,
        docs: IntArray,
        docsOffset: Int
    ): Boolean {
        val lastPackedValue = ByteArray(config.packedBytesLength())
        var lastDoc = -1
        for (i in 0 until count) {
            val packedValue = values(i)
            assert(packedValue.length == config.packedBytesLength())
            assert(
                valueInOrder(
                    i.toLong(),
                    sortedDim,
                    lastPackedValue,
                    packedValue.bytes,
                    packedValue.offset,
                    docs[docsOffset + i],
                    lastDoc
                )
            )
            lastDoc = docs[docsOffset + i]

            // Make sure this value does in fact fall within this leaf cell:
            assert(valueInBounds(packedValue, minPackedValue, maxPackedValue))
        }
        return true
    }

    // only called from assert
    private fun valueInOrder(
        ord: Long,
        sortedDim: Int,
        lastPackedValue: ByteArray,
        packedValue: ByteArray,
        packedValueOffset: Int,
        doc: Int,
        lastDoc: Int
    ): Boolean {
        val dimOffset = sortedDim * config.bytesPerDim
        if (ord > 0) {
            var cmp =
                Arrays.compareUnsigned(
                    lastPackedValue,
                    dimOffset,
                    dimOffset + config.bytesPerDim,
                    packedValue,
                    packedValueOffset + dimOffset,
                    packedValueOffset + dimOffset + config.bytesPerDim
                )
            if (cmp > 0) {
                throw AssertionError(
                    "values out of order: last value=${BytesRef(lastPackedValue)} current value=${BytesRef(packedValue, packedValueOffset, config.packedBytesLength())} ord=$ord sortedDim=$sortedDim"
                )
            }
            if (cmp == 0 && config.numDims > config.numIndexDims) {
                val dataOffset = config.numIndexDims * config.bytesPerDim
                cmp =
                    Arrays.compareUnsigned(
                        lastPackedValue,
                        dataOffset,
                        config.packedBytesLength(),
                        packedValue,
                        packedValueOffset + dataOffset,
                        packedValueOffset + config.packedBytesLength()
                    )
                if (cmp > 0) {
                    throw AssertionError(
                        "data values out of order: last value=${BytesRef(lastPackedValue)} current value=${BytesRef(packedValue, packedValueOffset, config.packedBytesLength())} ord=$ord"
                    )
                }
            }
            if (cmp == 0 && doc < lastDoc) {
                throw AssertionError(
                    "docs out of order: last doc=$lastDoc current doc=$doc ord=$ord sortedDim=$sortedDim"
                )
            }
        }
        packedValue.copyInto(lastPackedValue, 0, packedValueOffset, packedValueOffset + config.packedBytesLength())
        return true
    }

    @Throws(IOException::class)
    private fun write(out: IndexOutput, s: String) {
        SimpleTextUtil.write(out, s, scratch)
    }

    @Throws(IOException::class)
    private fun writeInt(out: IndexOutput, x: Int) {
        SimpleTextUtil.write(out, x.toString(), scratch)
    }

    @Throws(IOException::class)
    private fun writeLong(out: IndexOutput, x: Long) {
        SimpleTextUtil.write(out, x.toString(), scratch)
    }

    @Throws(IOException::class)
    private fun write(out: IndexOutput, b: BytesRef) {
        SimpleTextUtil.write(out, b)
    }

    @Throws(IOException::class)
    private fun newline(out: IndexOutput) {
        SimpleTextUtil.writeNewline(out)
    }
}
