package org.gnit.lucenekmp.util.bkd

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.MutablePointTree
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.PointTree
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.internal.hppc.LongArrayList
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.TrackingDirectoryWrapper
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IORunnable
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.util.bkd.BKDUtil.ByteArrayPredicate
import kotlin.math.max
import kotlin.math.min


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
/**
 * Recursively builds a block KD-tree to assign all incoming points in N-dim space to smaller and
 * smaller N-dim rectangles (cells) until the number of points in a given rectangle is &lt;= `
 * config.maxPointsInLeafNode`. The tree is partially balanced, which means the leaf nodes
 * will have the requested `config.maxPointsInLeafNode` except one that might have
 * less. Leaf nodes may straddle the two bottom levels of the binary tree. Values that fall exactly
 * on a cell boundary may be in either cell.
 *
 *
 * The number of dimensions can be 1 to 8, but every byte[] value is fixed length.
 *
 *
 * This consumes heap during writing: it allocates a `Long[numLeaves]`, a `
 * byte[numLeaves*(1+config.bytesPerDim)]` and then uses up to the specified `maxMBSortInHeap` heap space for writing.
 *
 *
 * **NOTE**: This can write at most Integer.MAX_VALUE * `config.maxPointsInLeafNode
` *  / config.bytesPerDim total points.
 *
 * @lucene.experimental
 */
open class BKDWriter(
    maxDoc: Int,
    tempDir: Directory,
    tempFileNamePrefix: String,
    config: BKDConfig,
    maxMBSortInHeap: Double,
    totalPointCount: Long
) : AutoCloseable {
    /** BKD tree configuration  */
    protected val config: BKDConfig

    private val comparator: ByteArrayComparator
    private val equalsPredicate: ByteArrayPredicate
    private val commonPrefixComparator: ByteArrayComparator

    val tempDir: TrackingDirectoryWrapper
    val tempFileNamePrefix: String
    val maxMBSortInHeap: Double

    val scratchDiff: ByteArray
    val scratch: ByteArray
    val scratchBytesRef1: BytesRef = BytesRef()
    val scratchBytesRef2: BytesRef = BytesRef()
    val commonPrefixLengths: IntArray

    protected val docsSeen: FixedBitSet

    private var pointWriter: PointWriter? = null
    private var finished = false

    private var tempInput: IndexOutput? = null
    private val maxPointsSortInHeap: Int

    /** Minimum per-dim values, packed  */
    protected val minPackedValue: ByteArray

    /** Maximum per-dim values, packed  */
    protected val maxPackedValue: ByteArray

    protected var pointCount: Long = 0

    /** An upper bound on how many points the caller will add (includes deletions)  */
    private val totalPointCount: Long

    private val maxDoc: Int
    private val docIdsWriter: DocIdsWriter

    init {
        verifyParams(maxMBSortInHeap, totalPointCount)
        // We use tracking dir to deal with removing files on exception, so each place that
        // creates temp files doesn't need crazy try/finally/sucess logic:
        this.tempDir = TrackingDirectoryWrapper(tempDir)
        this.tempFileNamePrefix = tempFileNamePrefix
        this.maxMBSortInHeap = maxMBSortInHeap

        this.totalPointCount = totalPointCount
        this.maxDoc = maxDoc

        this.config = config
        this.comparator = ArrayUtil.getUnsignedComparator(config.bytesPerDim)
        this.equalsPredicate = BKDUtil.getEqualsPredicate(config.bytesPerDim)
        this.commonPrefixComparator = BKDUtil.getPrefixLengthComparator(config.bytesPerDim)

        docsSeen = FixedBitSet(maxDoc)

        scratchDiff = ByteArray(config.bytesPerDim)
        scratch = ByteArray(config.packedBytesLength())
        commonPrefixLengths = IntArray(config.numDims)

        minPackedValue = ByteArray(config.packedIndexBytesLength())
        maxPackedValue = ByteArray(config.packedIndexBytesLength())

        // Maximum number of points we hold in memory at any time
        maxPointsSortInHeap = ((maxMBSortInHeap * 1024 * 1024) / (config.bytesPerDoc())).toInt()
        docIdsWriter = DocIdsWriter(config.maxPointsInLeafNode)
        // Finally, we must be able to hold at least the leaf node in heap during build:
        require(maxPointsSortInHeap >= config.maxPointsInLeafNode) {
            ("maxMBSortInHeap="
                    + maxMBSortInHeap
                    + " only allows for maxPointsSortInHeap="
                    + maxPointsSortInHeap
                    + ", but this is less than maxPointsInLeafNode="
                    + config.maxPointsInLeafNode
                    + "; "
                    + "either increase maxMBSortInHeap or decrease maxPointsInLeafNode")
        }
    }

    @Throws(IOException::class)
    private fun initPointWriter() {
        require(pointWriter == null) { "Point writer is already initialized" }
        // Total point count is an estimation but the final point count must be equal or lower to that
        // number.
        if (totalPointCount > maxPointsSortInHeap) {
            pointWriter = OfflinePointWriter(config, tempDir, tempFileNamePrefix, "spill", 0)
            tempInput = (pointWriter as OfflinePointWriter).out
        } else {
            pointWriter = HeapPointWriter(config, Math.toIntExact(totalPointCount))
        }
    }

    @Throws(IOException::class)
    fun add(packedValue: ByteArray, docID: Int) {
        require(packedValue.size == config.packedBytesLength()) {
            ("packedValue should be length="
                    + config.packedBytesLength()
                    + " (got: "
                    + packedValue.size
                    + ")")
        }
        check(pointCount < totalPointCount) {
            ("totalPointCount="
                    + totalPointCount
                    + " was passed when we were created, but we just hit "
                    + (pointCount + 1)
                    + " values")
        }
        if (pointCount == 0L) {
            initPointWriter()
            System.arraycopy(packedValue, 0, minPackedValue, 0, config.packedIndexBytesLength())
            System.arraycopy(packedValue, 0, maxPackedValue, 0, config.packedIndexBytesLength())
        } else {
            for (dim in 0..<config.numIndexDims) {
                val offset: Int = dim * config.bytesPerDim
                if (comparator.compare(packedValue, offset, minPackedValue, offset) < 0) {
                    System.arraycopy(packedValue, offset, minPackedValue, offset, config.bytesPerDim)
                } else if (comparator.compare(packedValue, offset, maxPackedValue, offset) > 0) {
                    System.arraycopy(packedValue, offset, maxPackedValue, offset, config.bytesPerDim)
                }
            }
        }
        pointWriter!!.append(packedValue, docID)
        pointCount++
        docsSeen.set(docID)
    }

    private class MergeReader(pointValues: PointValues, private val docMap: MergeState.DocMap) {
        private val pointTree: PointTree = pointValues.pointTree
        private val packedBytesLength: Int = pointValues.bytesPerDimension * pointValues.numDimensions
        private val mergeIntersectsVisitor: MergeIntersectsVisitor = MergeIntersectsVisitor(packedBytesLength)

        /** Which doc in this block we are up to  */
        private var docBlockUpto = 0

        /** Current doc ID  */
        var docID: Int = 0

        /** Current packed value  */
        val packedValue: ByteArray = ByteArray(packedBytesLength)

        init {
            // move to first child of the tree and collect docs
            while (pointTree.moveToChild()) {
                // Intentionally empty - the side effect of moveToChild() is what matters
            }
            pointTree.visitDocValues(mergeIntersectsVisitor)
        }

        @Throws(IOException::class)
        fun next(): Boolean {
            // System.out.println("MR.next this=" + this);
            while (true) {
                if (docBlockUpto == mergeIntersectsVisitor.docsInBlock) {
                    if (collectNextLeaf() == false) {
                        require(mergeIntersectsVisitor.docsInBlock == 0)
                        return false
                    }
                    require(mergeIntersectsVisitor.docsInBlock > 0)
                    docBlockUpto = 0
                }

                val index = docBlockUpto++
                val oldDocID = mergeIntersectsVisitor.docIDs[index]

                val mappedDocID: Int
                if (docMap == null) {
                    mappedDocID = oldDocID
                } else {
                    mappedDocID = docMap.get(oldDocID)
                }

                if (mappedDocID != -1) {
                    // Not deleted!
                    docID = mappedDocID
                    System.arraycopy(
                        mergeIntersectsVisitor.packedValues,
                        index * packedBytesLength,
                        packedValue,
                        0,
                        packedBytesLength
                    )
                    return true
                }
            }
        }

        @Throws(IOException::class)
        fun collectNextLeaf(): Boolean {
            require(pointTree.moveToChild() == false)
            mergeIntersectsVisitor.reset()
            do {
                if (pointTree.moveToSibling()) {
                    // move to first child of this node and collect docs
                    while (pointTree.moveToChild()) {
                        // Intentionally empty - the side effect of moveToChild() is what matters
                    }
                    pointTree.visitDocValues(mergeIntersectsVisitor)
                    return true
                }
            } while (pointTree.moveToParent())
            return false
        }
    }

    private class MergeIntersectsVisitor(private val packedBytesLength: Int) : IntersectVisitor {
        var docsInBlock: Int = 0
        var packedValues: ByteArray
        var docIDs: IntArray

        init {
            this.docIDs = IntArray(0)
            this.packedValues = ByteArray(0)
        }

        fun reset() {
            docsInBlock = 0
        }

        override fun grow(count: Int) {
            require(docsInBlock == 0)
            if (docIDs.size < count) {
                docIDs = ArrayUtil.grow(docIDs, count)
                val packedValuesSize: Int = Math.toIntExact(docIDs.size * packedBytesLength.toLong())
                check(packedValuesSize <= ArrayUtil.MAX_ARRAY_LENGTH) {
                    ("array length must be <= to "
                            + ArrayUtil.MAX_ARRAY_LENGTH
                            + " but was: "
                            + packedValuesSize)
                }
                packedValues = ArrayUtil.growExact(packedValues, packedValuesSize)
            }
        }

        override fun visit(docID: Int) {
            throw UnsupportedOperationException()
        }

        override fun visit(docID: Int, packedValue: ByteArray) {
            System.arraycopy(
                packedValue, 0, packedValues, docsInBlock * packedBytesLength, packedBytesLength
            )
            docIDs[docsInBlock++] = docID
        }

        override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
            return Relation.CELL_CROSSES_QUERY
        }
    }

    private class BKDMergeQueue(bytesPerDim: Int, maxSize: Int) : PriorityQueue<MergeReader>(maxSize) {
        private val comparator: ByteArrayComparator = ArrayUtil.getUnsignedComparator(bytesPerDim)

        override fun lessThan(a: MergeReader, b: MergeReader): Boolean {
            require(a !== b)

            val cmp: Int = comparator.compare(a.packedValue, 0, b.packedValue, 0)

            if (cmp < 0) {
                return true
            } else if (cmp > 0) {
                return false
            }

            // Tie break by sorting smaller docIDs earlier:
            return a.docID < b.docID
        }
    }

    /** flat representation of a kd-tree  */
    private interface BKDTreeLeafNodes {
        /** number of leaf nodes  */
        fun numLeaves(): Int

        /**
         * pointer to the leaf node previously written. Leaves are order from left to right, so leaf at
         * `index` 0 is the leftmost leaf and the leaf at `numleaves()` -1 is the rightmost
         * leaf
         */
        fun getLeafLP(index: Int): Long

        /**
         * split value between two leaves. The split value at position n corresponds to the leaves at (n
         * -1) and n.
         */
        fun getSplitValue(index: Int): BytesRef

        /**
         * split dimension between two leaves. The split dimension at position n corresponds to the
         * leaves at (n -1) and n.
         */
        fun getSplitDimension(index: Int): Int
    }

    /**
     * Write a field from a [MutablePointTree]. This way of writing points is faster than
     * regular writes with [BKDWriter.add] since there is opportunity for reordering points
     * before writing them to disk. This method does not use transient disk in order to reorder
     * points.
     */
    @Throws(IOException::class)
    fun writeField(
        metaOut: IndexOutput,
        indexOut: IndexOutput,
        dataOut: IndexOutput,
        fieldName: String,
        reader: MutablePointTree
    ): IORunnable? {
        return if (config.numDims == 1) {
            writeField1Dim(metaOut, indexOut, dataOut, fieldName, reader)
        } else {
            writeFieldNDims(metaOut, indexOut, dataOut, fieldName, reader)
        }
    }

    private fun computePackedValueBounds(
        values: MutablePointTree,
        from: Int,
        to: Int,
        minPackedValue: ByteArray,
        maxPackedValue: ByteArray,
        scratch: BytesRef
    ) {
        if (from == to) {
            return
        }
        values.getValue(from, scratch)
        System.arraycopy(
            scratch.bytes, scratch.offset, minPackedValue, 0, config.packedIndexBytesLength()
        )
        System.arraycopy(
            scratch.bytes, scratch.offset, maxPackedValue, 0, config.packedIndexBytesLength()
        )
        for (i in from + 1..<to) {
            values.getValue(i, scratch)
            for (dim in 0..<config.numIndexDims) {
                val startOffset: Int = dim * config.bytesPerDim
                val endOffset: Int = startOffset + config.bytesPerDim
                if (Arrays.compareUnsigned(
                        scratch.bytes,
                        scratch.offset + startOffset,
                        scratch.offset + endOffset,
                        minPackedValue,
                        startOffset,
                        endOffset
                    )
                    < 0
                ) {
                    System.arraycopy(
                        scratch.bytes,
                        scratch.offset + startOffset,
                        minPackedValue,
                        startOffset,
                        config.bytesPerDim
                    )
                } else if (Arrays.compareUnsigned(
                        scratch.bytes,
                        scratch.offset + startOffset,
                        scratch.offset + endOffset,
                        maxPackedValue,
                        startOffset,
                        endOffset
                    )
                    > 0
                ) {
                    System.arraycopy(
                        scratch.bytes,
                        scratch.offset + startOffset,
                        maxPackedValue,
                        startOffset,
                        config.bytesPerDim
                    )
                }
            }
        }
    }

    /* In the 2+D case, we recursively pick the split dimension, compute the
   * median value and partition other values around it. */
    @Throws(IOException::class)
    private fun writeFieldNDims(
        metaOut: IndexOutput,
        indexOut: IndexOutput,
        dataOut: IndexOutput,
        fieldName: String,
        values: MutablePointTree
    ): IORunnable? {
        check(pointCount == 0L) { "cannot mix add and writeField" }

        // Catch user silliness:
        check(finished != true) { "already finished" }

        // Mark that we already finished:
        finished = true

        pointCount = values.size()

        if (pointCount == 0L) {
            return null
        }

        val numLeaves: Int =
            Math.toIntExact(
                (pointCount + config.maxPointsInLeafNode - 1) / config.maxPointsInLeafNode
            )
        val numSplits = numLeaves - 1

        checkMaxLeafNodeCount(numLeaves)

        val splitPackedValues = ByteArray(Math.multiplyExact(numSplits, config.bytesPerDim))
        val splitDimensionValues = ByteArray(numSplits)
        val leafBlockFPs = LongArray(numLeaves)

        // compute the min/max for this slice
        computePackedValueBounds(
            values, 0, Math.toIntExact(pointCount), minPackedValue, maxPackedValue, scratchBytesRef1
        )
        for (i in 0..<Math.toIntExact(pointCount)) {
            docsSeen.set(values.getDocID(i))
        }

        val dataStartFP: Long = dataOut.filePointer
        val parentSplits = IntArray(config.numIndexDims)
        build(
            0,
            numLeaves,
            values,
            0,
            Math.toIntExact(pointCount),
            dataOut,
            minPackedValue.copyOf(),
            maxPackedValue.copyOf(),
            parentSplits,
            splitPackedValues,
            splitDimensionValues,
            leafBlockFPs,
            IntArray(config.maxPointsInLeafNode)
        )
        require(parentSplits.contentEquals(IntArray(config.numIndexDims)))

        scratchBytesRef1.length = config.bytesPerDim
        scratchBytesRef1.bytes = splitPackedValues

        return makeWriter(metaOut, indexOut, splitDimensionValues, leafBlockFPs, dataStartFP)
    }

    /* In the 1D case, we can simply sort points in ascending order and use the
   * same writing logic as we use at merge time. */
    @Throws(IOException::class)
    private fun writeField1Dim(
        metaOut: IndexOutput,
        indexOut: IndexOutput,
        dataOut: IndexOutput,
        fieldName: String,
        reader: MutablePointTree
    ): IORunnable? {
        MutablePointTreeReaderUtils.sort(config, maxDoc, reader, 0, Math.toIntExact(reader.size()))

        val oneDimWriter: OneDimensionBKDWriter =
            this.OneDimensionBKDWriter(metaOut, indexOut, dataOut)

        reader.visitDocValues(
            object : IntersectVisitor {
                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                    oneDimWriter.add(packedValue, docID)
                }

                override fun visit(docID: Int) {
                    throw IllegalStateException()
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                    return Relation.CELL_CROSSES_QUERY
                }
            })

        return oneDimWriter.finish()
    }

    /**
     * More efficient bulk-add for incoming [PointValues]s. This does a merge sort of the
     * already sorted values and currently only works when numDims==1. This returns -1 if all
     * documents containing dimensional values were deleted.
     */
    @Throws(IOException::class)
    fun merge(
        metaOut: IndexOutput,
        indexOut: IndexOutput,
        dataOut: IndexOutput,
        docMaps: MutableList<MergeState.DocMap>?,
        readers: MutableList<PointValues>
    ): IORunnable? {
        require(docMaps == null || readers.size == docMaps.size)

        val queue = BKDMergeQueue(config.bytesPerDim, readers.size)

        for (i in readers.indices) {
            val pointValues: PointValues = readers[i]
            require(pointValues.numDimensions == config.numDims && pointValues.bytesPerDimension == config.bytesPerDim && pointValues.numIndexDimensions == config.numIndexDims)
            val docMap: MergeState.DocMap? = if (docMaps == null) {
                null
            } else {
                docMaps[i]
            }
            val reader = MergeReader(pointValues, docMap!!)
            if (reader.next()) {
                queue.add(reader)
            }
        }

        val oneDimWriter: OneDimensionBKDWriter = this.OneDimensionBKDWriter(metaOut, indexOut, dataOut)

        while (queue.size() != 0) {
            val reader: MergeReader = queue.top()

            // System.out.println("iter reader=" + reader);
            oneDimWriter.add(reader.packedValue, reader.docID)

            if (reader.next()) {
                queue.updateTop()
            } else {
                // This segment was exhausted
                queue.pop()
            }
        }

        return oneDimWriter.finish()
    }

    private inner class OneDimensionBKDWriter(metaOut: IndexOutput, indexOut: IndexOutput, dataOut: IndexOutput) {
        val metaOut: IndexOutput
        val indexOut: IndexOutput
        val dataOut: IndexOutput
        val dataStartFP: Long
        val leafBlockFPs: LongArrayList = LongArrayList()
        val leafBlockStartValues: MutableList<ByteArray> = ArrayList<ByteArray>()
        val leafValues: ByteArray = ByteArray(config.maxPointsInLeafNode * config.packedBytesLength())
        val leafDocs: IntArray = IntArray(config.maxPointsInLeafNode)
        private var valueCount: Long = 0
        private var leafCount = 0
        private var leafCardinality = 0

        // for asserts
        val lastPackedValue: ByteArray
        private var lastDocID = 0

        init {
            if (config.numIndexDims != 1) {
                throw UnsupportedOperationException(
                    "config.numIndexDims must be 1 but got " + config.numIndexDims
                )
            }
            check(pointCount == 0L) { "cannot mix add and merge" }

            // Catch user silliness:
            check(finished != true) { "already finished" }

            // Mark that we already finished:
            finished = true

            this.metaOut = metaOut
            this.indexOut = indexOut
            this.dataOut = dataOut
            this.dataStartFP = dataOut.filePointer

            lastPackedValue = ByteArray(config.packedBytesLength())
        }

        @Throws(IOException::class)
        fun add(packedValue: ByteArray, docID: Int) {
            require(
                valueInOrder(
                    config, valueCount + leafCount, 0, lastPackedValue, packedValue, 0, docID, lastDocID
                )
            )

            if (leafCount == 0
                || (equalsPredicate.test(
                    leafValues, (leafCount - 1) * config.bytesPerDim, packedValue, 0
                )
                        == false)
            ) {
                leafCardinality++
            }
            System.arraycopy(
                packedValue,
                0,
                leafValues,
                leafCount * config.packedBytesLength(),
                config.packedBytesLength()
            )
            leafDocs[leafCount] = docID
            docsSeen.set(docID)
            leafCount++

            check(valueCount + leafCount <= totalPointCount) {
                ("totalPointCount="
                        + totalPointCount
                        + " was passed when we were created, but we just hit "
                        + (valueCount + leafCount)
                        + " values")
            }

            if (leafCount == config.maxPointsInLeafNode) {
                // We write a block once we hit exactly the max count ... this is different from
                // when we write N > 1 dimensional points where we write between max/2 and max per leaf
                // block
                writeLeafBlock(leafCardinality)
                leafCardinality = 0
                leafCount = 0
            }

            require(
                (docID.also { lastDocID = it }) >= 0 // only assign when asserts are enabled
            )
        }

        @Throws(IOException::class)
        fun finish(): IORunnable? {
            if (leafCount > 0) {
                writeLeafBlock(leafCardinality)
                leafCardinality = 0
                leafCount = 0
            }

            if (valueCount == 0L) {
                return null
            }

            pointCount = valueCount

            scratchBytesRef1.length = config.bytesPerDim
            scratchBytesRef1.offset = 0
            require(leafBlockStartValues.size + 1 == leafBlockFPs.size())
            val leafNodes: BKDTreeLeafNodes =
                object : BKDTreeLeafNodes {
                    override fun getLeafLP(index: Int): Long {
                        return leafBlockFPs.get(index)
                    }

                    override fun getSplitValue(index: Int): BytesRef {
                        scratchBytesRef1.bytes = leafBlockStartValues[index]
                        return scratchBytesRef1
                    }

                    override fun getSplitDimension(index: Int): Int {
                        return 0
                    }

                    override fun numLeaves(): Int {
                        return leafBlockFPs.size()
                    }
                }
            return IORunnable {
                writeIndex(metaOut, indexOut, config.maxPointsInLeafNode, leafNodes, dataStartFP)
            }
        }

        @Throws(IOException::class)
        fun writeLeafBlock(leafCardinality: Int) {
            require(leafCount != 0)
            if (valueCount == 0L) {
                System.arraycopy(leafValues, 0, minPackedValue, 0, config.packedIndexBytesLength())
            }
            System.arraycopy(
                leafValues,
                (leafCount - 1) * config.packedBytesLength(),
                maxPackedValue,
                0,
                config.packedIndexBytesLength()
            )

            valueCount += leafCount.toLong()

            if (leafBlockFPs.size() > 0) {
                // Save the first (minimum) value in each leaf block except the first, to build the split
                // value index in the end:
                leafBlockStartValues.add(
                    ArrayUtil.copyOfSubArray(leafValues, 0, config.packedBytesLength())
                )
            }
            leafBlockFPs.add(dataOut.filePointer)
            checkMaxLeafNodeCount(leafBlockFPs.size())

            // Find per-dim common prefix:
            commonPrefixLengths[0] =
                commonPrefixComparator.compare(
                    leafValues, 0, leafValues, (leafCount - 1) * config.packedBytesLength()
                )

            writeLeafBlockDocs(dataOut, leafDocs, 0, leafCount)
            writeCommonPrefixes(dataOut, commonPrefixLengths, leafValues)

            scratchBytesRef1.length = config.packedBytesLength()
            scratchBytesRef1.bytes = leafValues

            val packedValues: (Int) -> BytesRef =
                { i: Int ->
                    scratchBytesRef1.offset = config.packedBytesLength() * i
                    scratchBytesRef1
                }
            require(
                valuesInOrderAndBounds(
                    config,
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
            writeLeafBlockPackedValues(
                dataOut, commonPrefixLengths, leafCount, 0, packedValues, leafCardinality
            )
        }
    }

    private fun getNumLeftLeafNodes(numLeaves: Int): Int {
        require(numLeaves > 1) { "getNumLeftLeaveNodes() called with $numLeaves" }
        // return the level that can be filled with this number of leaves
        val lastFullLevel: Int = 31 - Int.numberOfLeadingZeros(numLeaves)
        // how many leaf nodes are in the full level
        val leavesFullLevel = 1 shl lastFullLevel
        // half of the leaf nodes from the full level goes to the left
        var numLeftLeafNodes = leavesFullLevel / 2
        // leaf nodes that do not fit in the full level
        val unbalancedLeafNodes = numLeaves - leavesFullLevel
        // distribute unbalanced leaf nodes
        numLeftLeafNodes += min(unbalancedLeafNodes, numLeftLeafNodes)
        // we should always place unbalanced leaf nodes on the left
        require(
            numLeftLeafNodes >= numLeaves - numLeftLeafNodes
                    && numLeftLeafNodes <= 2L * (numLeaves - numLeftLeafNodes)
        )
        return numLeftLeafNodes
    }

    // TODO: if we fixed each partition step to just record the file offset at the "split point", we
    // could probably handle variable length
    // encoding and not have our own ByteSequencesReader/Writer
    // useful for debugging:
    /*
  private void printPathSlice(String desc, PathSlice slice, int dim) throws IOException {
    System.out.println("    " + desc + " dim=" + dim + " count=" + slice.count + ":");
    try(PointReader r = slice.writer.getReader(slice.start, slice.count)) {
      int count = 0;
      while (r.next()) {
        byte[] v = r.packedValue();
        System.out.println("      " + count + ": " + new BytesRef(v, dim*config.bytesPerDim, config.bytesPerDim));
        count++;
        if (count == slice.count) {
          break;
        }
      }
    }
  }
  */
    private fun checkMaxLeafNodeCount(numLeaves: Int) {
        check(config.bytesPerDim * numLeaves.toLong() <= ArrayUtil.MAX_ARRAY_LENGTH) {
            ("too many nodes; increase config.maxPointsInLeafNode (currently "
                    + config.maxPointsInLeafNode
                    + ") and reindex")
        }
    }

    /**
     * Writes the BKD tree to the provided [IndexOutput]s and returns a [Runnable] that
     * writes the index of the tree if at least one point has been added, or `null` otherwise.
     */
    @Throws(IOException::class)
    fun finish(metaOut: IndexOutput, indexOut: IndexOutput, dataOut: IndexOutput): IORunnable? {
        // System.out.println("\nBKDTreeWriter.finish pointCount=" + pointCount + " out=" + out + "
        // heapWriter=" + heapPointWriter);

        // TODO: specialize the 1D case  it's much faster at indexing time (no partitioning on
        // recurse...)

        // Catch user silliness:

        check(finished != true) { "already finished" }

        if (pointCount == 0L) {
            return null
        }

        // mark as finished
        finished = true

        pointWriter?.close()
        val points = BKDRadixSelector.PathSlice(pointWriter!!, 0, pointCount)
        // clean up pointers
        tempInput = null
        pointWriter = null

        val numLeaves: Int =
            Math.toIntExact(
                (pointCount + config.maxPointsInLeafNode - 1) / config.maxPointsInLeafNode
            )
        val numSplits = numLeaves - 1

        checkMaxLeafNodeCount(numLeaves)

        // NOTE: we could save the 1+ here, to use a bit less heap at search time, but then we'd need a
        // somewhat costly check at each
        // step of the recursion to recompute the split dim:

        // Indexed by nodeID, but first (root) nodeID is 1.  We do 1+ because the lead byte at each
        // recursion says which dim we split on.
        val splitPackedValues = ByteArray(Math.multiplyExact(numSplits, config.bytesPerDim))
        val splitDimensionValues = ByteArray(numSplits)

        // +1 because leaf count is power of 2 (e.g. 8), and innerNodeCount is power of 2 minus 1 (e.g.
        // 7)
        val leafBlockFPs = LongArray(numLeaves)

        // Make sure the math above "worked":
        require(
            pointCount / numLeaves <= config.maxPointsInLeafNode
        ) {
            ("pointCount="
                    + pointCount
                    + " numLeaves="
                    + numLeaves
                    + " config.maxPointsInLeafNode="
                    + config.maxPointsInLeafNode)
        }

        // We re-use the selector so we do not need to create an object every time.
        val radixSelector =
            BKDRadixSelector(config, maxPointsSortInHeap, tempDir, tempFileNamePrefix)

        val dataStartFP: Long = dataOut.filePointer
        var success = false
        try {
            val parentSplits = IntArray(config.numIndexDims)
            build(
                0,
                numLeaves,
                points,
                dataOut,
                radixSelector,
                minPackedValue.copyOf(),
                maxPackedValue.copyOf(),
                parentSplits,
                splitPackedValues,
                splitDimensionValues,
                leafBlockFPs,
                IntArray(config.maxPointsInLeafNode)
            )
            require(parentSplits.contentEquals(IntArray(config.numIndexDims)))

            // If no exception, we should have cleaned everything up:
            require(tempDir.createdFiles.isEmpty())

            // System.out.println("write time: " + ((System.nanoTime() - t1) / (double)
            //   TimeUnit.SECONDS.toNanos(1)) + " ms");
            success = true
        } finally {
            if (success == false) {
                IOUtils.deleteFilesIgnoringExceptions(tempDir, tempDir.createdFiles)
            }
        }

        scratchBytesRef1.bytes = splitPackedValues
        scratchBytesRef1.length = config.bytesPerDim
        return makeWriter(metaOut, indexOut, splitDimensionValues, leafBlockFPs, dataStartFP)
    }

    private fun makeWriter(
        metaOut: IndexOutput,
        indexOut: IndexOutput,
        splitDimensionValues: ByteArray,
        leafBlockFPs: LongArray,
        dataStartFP: Long
    ): IORunnable {
        val leafNodes: BKDTreeLeafNodes =
            object : BKDTreeLeafNodes {
                override fun getLeafLP(index: Int): Long {
                    return leafBlockFPs[index]
                }

                override fun getSplitValue(index: Int): BytesRef {
                    scratchBytesRef1.offset = index * config.bytesPerDim
                    return scratchBytesRef1
                }

                override fun getSplitDimension(index: Int): Int {
                    return splitDimensionValues[index].toInt() and 0xff
                }

                override fun numLeaves(): Int {
                    return leafBlockFPs.size
                }
            }

        return IORunnable {
            // Write index:
            writeIndex(metaOut, indexOut, config.maxPointsInLeafNode, leafNodes, dataStartFP)
        }
    }

    /**
     * Packs the two arrays, representing a semi-balanced binary tree, into a compact byte[]
     * structure.
     */
    @Throws(IOException::class)
    private fun packIndex(leafNodes: BKDTreeLeafNodes): ByteArray {
        /* Reused while packing the index */
        val writeBuffer: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()

        // This is the "file" we append the byte[] to:
        val blocks: MutableList<ByteArray> = ArrayList<ByteArray>()
        val lastSplitValues = ByteArray(config.bytesPerDim * config.numIndexDims)
        // System.out.println("\npack index");
        val totalSize =
            recursePackIndex(
                writeBuffer,
                leafNodes,
                0L,
                blocks as MutableList<ByteArray?>,
                lastSplitValues,
                BooleanArray(config.numIndexDims),
                false,
                0,
                leafNodes.numLeaves()
            )

        // Compact the byte[] blocks into single byte index:
        val index = ByteArray(totalSize)
        var upto = 0
        for (block in blocks) {
            System.arraycopy(block, 0, index, upto, block.size)
            upto += block.size
        }
        require(upto == totalSize)

        return index
    }

    /** Appends the current contents of writeBuffer as another block on the growing in-memory file  */
    private fun appendBlock(writeBuffer: ByteBuffersDataOutput, blocks: MutableList<ByteArray>): Int {
        val block: ByteArray = writeBuffer.toArrayCopy()
        blocks.add(block)
        writeBuffer.reset()
        return block.size
    }

    /**
     * lastSplitValues is per-dimension split value previously seen; we use this to prefix-code the
     * split byte[] on each inner node
     */
    @Throws(IOException::class)
    private fun recursePackIndex(
        writeBuffer: ByteBuffersDataOutput,
        leafNodes: BKDTreeLeafNodes,
        minBlockFP: Long,
        blocks: MutableList<ByteArray?>,
        lastSplitValues: ByteArray,
        negativeDeltas: BooleanArray,
        isLeft: Boolean,
        leavesOffset: Int,
        numLeaves: Int
    ): Int {
        if (numLeaves == 1) {
            if (isLeft) {
                require(leafNodes.getLeafLP(leavesOffset) - minBlockFP == 0L)
                return 0
            } else {
                val delta = leafNodes.getLeafLP(leavesOffset) - minBlockFP
                require(
                    leafNodes.numLeaves() == numLeaves || delta > 0
                ) { "expected delta > 0; got numLeaves =$numLeaves and delta=$delta" }
                writeBuffer.writeVLong(delta)
                return appendBlock(writeBuffer, blocks as MutableList<ByteArray>)
            }
        } else {
            val leftBlockFP: Long
            if (isLeft) {
                // The left tree's left most leaf block FP is always the minimal FP:
                require(leafNodes.getLeafLP(leavesOffset) == minBlockFP)
                leftBlockFP = minBlockFP
            } else {
                leftBlockFP = leafNodes.getLeafLP(leavesOffset)
                val delta = leftBlockFP - minBlockFP
                require(
                    leafNodes.numLeaves() == numLeaves || delta > 0
                ) { "expected delta > 0; got numLeaves =$numLeaves and delta=$delta" }
                writeBuffer.writeVLong(delta)
            }

            val numLeftLeafNodes = getNumLeftLeafNodes(numLeaves)
            val rightOffset = leavesOffset + numLeftLeafNodes
            val splitOffset = rightOffset - 1

            val splitDim = leafNodes.getSplitDimension(splitOffset)
            val splitValue: BytesRef = leafNodes.getSplitValue(splitOffset)
            val address: Int = splitValue.offset

            // System.out.println("recursePack inner nodeID=" + nodeID + " splitDim=" + splitDim + "
            // splitValue=" + new BytesRef(splitPackedValues, address, config.bytesPerDim));

            // find common prefix with last split value in this dim:
            val prefix: Int =
                commonPrefixComparator.compare(
                    splitValue.bytes, address, lastSplitValues, splitDim * config.bytesPerDim
                )

            // System.out.println("writeNodeData nodeID=" + nodeID + " splitDim=" + splitDim + " numDims="
            // + numDims + " config.bytesPerDim=" + config.bytesPerDim + " prefix=" + prefix);
            var firstDiffByteDelta: Int
            if (prefix < config.bytesPerDim) {
                // System.out.println("  delta byte cur=" +
                // Integer.toHexString(splitPackedValues[address+prefix]&0xFF) + " prev=" +
                // Integer.toHexString(lastSplitValues[splitDim * config.bytesPerDim + prefix]&0xFF) + "
                // negated=" + negativeDeltas[splitDim]);
                firstDiffByteDelta =
                    ((splitValue.bytes[address + prefix].toInt() and 0xFF)
                            - (lastSplitValues[splitDim * config.bytesPerDim + prefix].toInt() and 0xFF))
                if (negativeDeltas[splitDim]) {
                    firstDiffByteDelta = -firstDiffByteDelta
                }
                // System.out.println("  delta=" + firstDiffByteDelta);
                require(firstDiffByteDelta > 0)
            } else {
                firstDiffByteDelta = 0
            }

            // pack the prefix, splitDim and delta first diff byte into a single vInt:
            val code: Int =
                ((firstDiffByteDelta * (1 + config.bytesPerDim) + prefix) * config.numIndexDims
                        + splitDim)

            // System.out.println("  code=" + code);
            // System.out.println("  splitValue=" + new BytesRef(splitPackedValues, address,
            // config.bytesPerDim));
            writeBuffer.writeVInt(code)

            // write the split value, prefix coded vs. our parent's split value:
            val suffix: Int = config.bytesPerDim - prefix
            val savSplitValue = ByteArray(suffix)
            if (suffix > 1) {
                writeBuffer.writeBytes(splitValue.bytes, address + prefix + 1, suffix - 1)
            }

            val cmp: ByteArray = lastSplitValues.copyOf()

            System.arraycopy(
                lastSplitValues, splitDim * config.bytesPerDim + prefix, savSplitValue, 0, suffix
            )

            // copy our split value into lastSplitValues for our children to prefix-code against
            System.arraycopy(
                splitValue.bytes,
                address + prefix,
                lastSplitValues,
                splitDim * config.bytesPerDim + prefix,
                suffix
            )

            val numBytes = appendBlock(writeBuffer, blocks as MutableList<ByteArray>)

            // placeholder for left-tree numBytes; we need this so that at search time if we only need to
            // recurse into the right sub-tree we can
            // quickly seek to its starting point
            val idxSav = blocks.size
            blocks.add(null)

            val savNegativeDelta = negativeDeltas[splitDim]
            negativeDeltas[splitDim] = true

            val leftNumBytes =
                recursePackIndex(
                    writeBuffer,
                    leafNodes,
                    leftBlockFP,
                    blocks,
                    lastSplitValues,
                    negativeDeltas,
                    true,
                    leavesOffset,
                    numLeftLeafNodes
                )

            if (numLeftLeafNodes != 1) {
                writeBuffer.writeVInt(leftNumBytes)
            } else {
                require(leftNumBytes == 0) { "leftNumBytes=$leftNumBytes" }
            }

            val bytes2: ByteArray = writeBuffer.toArrayCopy()
            writeBuffer.reset()
            // replace our placeholder:
            blocks[idxSav] = bytes2

            negativeDeltas[splitDim] = false
            val rightNumBytes =
                recursePackIndex(
                    writeBuffer,
                    leafNodes,
                    leftBlockFP,
                    blocks,
                    lastSplitValues,
                    negativeDeltas,
                    false,
                    rightOffset,
                    numLeaves - numLeftLeafNodes
                )

            negativeDeltas[splitDim] = savNegativeDelta

            // restore lastSplitValues to what caller originally passed us:
            System.arraycopy(
                savSplitValue, 0, lastSplitValues, splitDim * config.bytesPerDim + prefix, suffix
            )

            require(lastSplitValues.contentEquals(cmp))

            return numBytes + bytes2.size + leftNumBytes + rightNumBytes
        }
    }

    @Throws(IOException::class)
    private fun writeIndex(
        metaOut: IndexOutput,
        indexOut: IndexOutput,
        countPerLeaf: Int,
        leafNodes: BKDTreeLeafNodes,
        dataStartFP: Long
    ) {
        val packedIndex = packIndex(leafNodes)
        writeIndex(metaOut, indexOut, countPerLeaf, leafNodes.numLeaves(), packedIndex, dataStartFP)
    }

    @Throws(IOException::class)
    private fun writeIndex(
        metaOut: IndexOutput,
        indexOut: IndexOutput,
        countPerLeaf: Int,
        numLeaves: Int,
        packedIndex: ByteArray,
        dataStartFP: Long
    ) {
        CodecUtil.writeHeader(metaOut, CODEC_NAME, VERSION_CURRENT)
        metaOut.writeVInt(config.numDims)
        metaOut.writeVInt(config.numIndexDims)
        metaOut.writeVInt(countPerLeaf)
        metaOut.writeVInt(config.bytesPerDim)

        require(numLeaves > 0)
        metaOut.writeVInt(numLeaves)
        metaOut.writeBytes(minPackedValue, 0, config.packedIndexBytesLength())
        metaOut.writeBytes(maxPackedValue, 0, config.packedIndexBytesLength())

        metaOut.writeVLong(pointCount)
        metaOut.writeVInt(docsSeen.cardinality())
        metaOut.writeVInt(packedIndex.size)
        metaOut.writeLong(dataStartFP)
        // If metaOut and indexOut are the same file, we account for the fact that
        // writing a long makes the index start 8 bytes later.
        metaOut.writeLong(indexOut.filePointer + (if (metaOut === indexOut) Long.SIZE_BYTES else 0))

        indexOut.writeBytes(packedIndex, 0, packedIndex.size)
    }

    @Throws(IOException::class)
    private fun writeLeafBlockDocs(out: DataOutput, docIDs: IntArray, start: Int, count: Int) {
        require(count > 0) { "config.maxPointsInLeafNode=" + config.maxPointsInLeafNode }
        out.writeVInt(count)
        docIdsWriter.writeDocIds(docIDs, start, count, out)
    }

    @Throws(IOException::class)
    private fun writeLeafBlockPackedValues(
        out: DataOutput,
        commonPrefixLengths: IntArray,
        count: Int,
        sortedDim: Int,
        packedValues: (Int) -> BytesRef,
        leafCardinality: Int
    ) {
        val prefixLenSum: Int = commonPrefixLengths.sum()
        if (prefixLenSum == config.packedBytesLength()) {
            // all values in this block are equal
            out.writeByte((-1).toByte())
        } else {
            require(commonPrefixLengths[sortedDim] < config.bytesPerDim)
            // estimate if storing the values with cardinality is cheaper than storing all values.
            val compressedByteOffset: Int = sortedDim * config.bytesPerDim + commonPrefixLengths[sortedDim]
            val highCardinalityCost: Int
            val lowCardinalityCost: Int
            if (count == leafCardinality) {
                // all values in this block are different
                highCardinalityCost = 0
                lowCardinalityCost = 1
            } else {
                // compute cost of runLen compression
                var numRunLens = 0
                var i = 0
                while (i < count) {
                    // do run-length compression on the byte at compressedByteOffset
                    val runLen = runLen(packedValues, i, min(i + 0xff, count), compressedByteOffset)
                    require(runLen <= 0xff)
                    numRunLens++
                    i += runLen
                }
                // Add cost of runLen compression
                highCardinalityCost =
                    count * (config.packedBytesLength() - prefixLenSum - 1) + 2 * numRunLens
                // +1 is the byte needed for storing the cardinality
                lowCardinalityCost = leafCardinality * (config.packedBytesLength() - prefixLenSum + 1)
            }
            if (lowCardinalityCost <= highCardinalityCost) {
                out.writeByte((-2).toByte())
                writeLowCardinalityLeafBlockPackedValues(out, commonPrefixLengths, count, packedValues)
            } else {
                out.writeByte(sortedDim.toByte())
                writeHighCardinalityLeafBlockPackedValues(
                    out, commonPrefixLengths, count, sortedDim, packedValues, compressedByteOffset
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun writeLowCardinalityLeafBlockPackedValues(
        out: DataOutput,
        commonPrefixLengths: IntArray,
        count: Int,
        packedValues: (Int) -> BytesRef
    ) {
        if (config.numIndexDims != 1) {
            writeActualBounds(out, commonPrefixLengths, count, packedValues)
        }
        var value: BytesRef = packedValues(0)
        System.arraycopy(value.bytes, value.offset, scratch, 0, config.packedBytesLength())
        var cardinality = 1
        for (i in 1..<count) {
            value = packedValues(i)
            for (dim in 0..<config.numDims) {
                val start: Int = dim * config.bytesPerDim
                if (equalsPredicate.test(value.bytes, value.offset + start, scratch, start) == false) {
                    out.writeVInt(cardinality)
                    for (j in 0..<config.numDims) {
                        out.writeBytes(
                            scratch,
                            j * config.bytesPerDim + commonPrefixLengths[j],
                            config.bytesPerDim - commonPrefixLengths[j]
                        )
                    }
                    System.arraycopy(value.bytes, value.offset, scratch, 0, config.packedBytesLength())
                    cardinality = 1
                    break
                } else if (dim == config.numDims - 1) {
                    cardinality++
                }
            }
        }
        out.writeVInt(cardinality)
        for (i in 0..<config.numDims) {
            out.writeBytes(
                scratch,
                i * config.bytesPerDim + commonPrefixLengths[i],
                config.bytesPerDim - commonPrefixLengths[i]
            )
        }
    }

    @Throws(IOException::class)
    private fun writeHighCardinalityLeafBlockPackedValues(
        out: DataOutput,
        commonPrefixLengths: IntArray,
        count: Int,
        sortedDim: Int,
        packedValues: (Int) -> BytesRef,
        compressedByteOffset: Int
    ) {
        if (config.numIndexDims != 1) {
            writeActualBounds(out, commonPrefixLengths, count, packedValues)
        }
        commonPrefixLengths[sortedDim]++
        var i = 0
        while (i < count) {
            // do run-length compression on the byte at compressedByteOffset
            val runLen = runLen(packedValues, i, min(i + 0xff, count), compressedByteOffset)
            require(runLen <= 0xff)
            val first: BytesRef = packedValues(i)
            val prefixByte: Byte = first.bytes[first.offset + compressedByteOffset]
            out.writeByte(prefixByte)
            out.writeByte(runLen.toByte())
            writeLeafBlockPackedValuesRange(out, commonPrefixLengths, i, i + runLen, packedValues)
            i += runLen
            require(i <= count)
        }
    }

    @Throws(IOException::class)
    private fun writeActualBounds(
        out: DataOutput,
        commonPrefixLengths: IntArray,
        count: Int,
        packedValues: (Int) -> BytesRef
    ) {
        for (dim in 0..<config.numIndexDims) {
            val commonPrefixLength = commonPrefixLengths[dim]
            val suffixLength: Int = config.bytesPerDim - commonPrefixLength
            if (suffixLength > 0) {
                val minMax: Array<BytesRef> =
                    computeMinMax(
                        count, packedValues, dim * config.bytesPerDim + commonPrefixLength, suffixLength
                    )
                val min: BytesRef = minMax[0]
                val max: BytesRef = minMax[1]
                out.writeBytes(min.bytes, min.offset, min.length)
                out.writeBytes(max.bytes, max.offset, max.length)
            }
        }
    }

    @Throws(IOException::class)
    private fun writeLeafBlockPackedValuesRange(
        out: DataOutput,
        commonPrefixLengths: IntArray,
        start: Int,
        end: Int,
        packedValues: (Int) -> BytesRef
    ) {
        for (i in start..<end) {
            val ref: BytesRef = packedValues(i)
            require(ref.length == config.packedBytesLength())

            for (dim in 0..<config.numDims) {
                val prefix = commonPrefixLengths[dim]
                out.writeBytes(
                    ref.bytes,
                    ref.offset + dim * config.bytesPerDim + prefix,
                    config.bytesPerDim - prefix
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun writeCommonPrefixes(out: DataOutput, commonPrefixes: IntArray, packedValue: ByteArray) {
        for (dim in 0..<config.numDims) {
            out.writeVInt(commonPrefixes[dim])
            // System.out.println(commonPrefixes[dim] + " of " + config.bytesPerDim);
            out.writeBytes(packedValue, dim * config.bytesPerDim, commonPrefixes[dim])
        }
    }

    override fun close() {
        finished = true
        if (tempInput != null) {
            // NOTE: this should only happen on exception, e.g. caller calls close w/o calling finish:
            try {
                tempInput!!.close()
            } finally {
                tempDir.deleteFile(tempInput!!.name!!)
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
        checkNotNull(priorException)

        // TODO: we could improve this, to always validate checksum as we recurse, if we shared left and
        // right reader after recursing to children, and possibly within recursed children,
        // since all together they make a single pass through the file.  But this is a sizable re-org,
        // and would mean leaving readers (IndexInputs) open for longer:
        if (writer is OfflinePointWriter) {
            // We are reading from a temp file; go verify the checksum:
            val tempFileName: String = writer.name
            if (tempDir.createdFiles.contains(tempFileName)) {
                tempDir.openChecksumInput(tempFileName).use { `in` ->
                    CodecUtil.checkFooter(`in`, priorException)
                }
            }
        }

        // We are reading from heap; nothing to add:
        throw IOUtils.rethrowAlways(priorException)
    }

    /**
     * Pick the next dimension to split.
     *
     * @param minPackedValue the min values for all dimensions
     * @param maxPackedValue the max values for all dimensions
     * @param parentSplits how many times each dim has been split on the parent levels
     * @return the dimension to split
     */
    protected fun split(minPackedValue: ByteArray, maxPackedValue: ByteArray, parentSplits: IntArray): Int {
        // First look at whether there is a dimension that has split less than 2x less than
        // the dim that has most splits, and return it if there is such a dimension and it
        // does not only have equals values. This helps ensure all dimensions are indexed.
        var maxNumSplits = 0
        for (numSplits in parentSplits) {
            maxNumSplits = max(maxNumSplits, numSplits)
        }
        for (dim in 0..<config.numIndexDims) {
            val offset: Int = dim * config.bytesPerDim
            if (parentSplits[dim] < maxNumSplits / 2
                && comparator.compare(minPackedValue, offset, maxPackedValue, offset) != 0
            ) {
                return dim
            }
        }

        // Find which dim has the largest span so we can split on it:
        var splitDim = -1
        for (dim in 0..<config.numIndexDims) {
            NumericUtils.subtract(config.bytesPerDim, dim, maxPackedValue, minPackedValue, scratchDiff)
            if (splitDim == -1 || comparator.compare(scratchDiff, 0, scratch, 0) > 0) {
                System.arraycopy(scratchDiff, 0, scratch, 0, config.bytesPerDim)
                splitDim = dim
            }
        }

        // System.out.println("SPLIT: " + splitDim);
        return splitDim
    }

    /** Pull a partition back into heap once the point count is low enough while recursing.  */
    @Throws(IOException::class)
    private fun switchToHeap(source: PointWriter): HeapPointWriter {
        val count: Int = Math.toIntExact(source.count())
        try {
            source.getReader(0, source.count()).use { reader ->
                HeapPointWriter(config, count).use { writer ->
                    for (i in 0..<count) {
                        val hasNext: Boolean = reader.next()
                        require(hasNext)
                        writer.append(reader.pointValue()!!)
                    }
                    source.destroy()
                    return writer
                }
            }
        } catch (t: Throwable) {
            throw verifyChecksum(t, source)
        }
    }

    /* Recursively reorders the provided reader and writes the bkd-tree on the fly; this method is used
   * when we are writing a new segment directly from IndexWriter's indexing buffer (MutablePointsReader). */
    @Throws(IOException::class)
    private fun build(
        leavesOffset: Int,
        numLeaves: Int,
        reader: MutablePointTree,
        from: Int,
        to: Int,
        out: IndexOutput,
        minPackedValue: ByteArray,
        maxPackedValue: ByteArray,
        parentSplits: IntArray,
        splitPackedValues: ByteArray,
        splitDimensionValues: ByteArray,
        leafBlockFPs: LongArray,
        spareDocIds: IntArray
    ) {
        if (numLeaves == 1) {
            // leaf node
            val count = to - from
            require(count <= config.maxPointsInLeafNode)

            // Compute common prefixes
            Arrays.fill(commonPrefixLengths, config.bytesPerDim)
            reader.getValue(from, scratchBytesRef1)
            for (i in from + 1..<to) {
                reader.getValue(i, scratchBytesRef2)
                for (dim in 0..<config.numDims) {
                    val offset: Int = dim * config.bytesPerDim
                    val dimensionPrefixLength = commonPrefixLengths[dim]
                    commonPrefixLengths[dim] =
                        min(
                            dimensionPrefixLength,
                            commonPrefixComparator.compare(
                                scratchBytesRef1.bytes,
                                scratchBytesRef1.offset + offset,
                                scratchBytesRef2.bytes,
                                scratchBytesRef2.offset + offset
                            )
                        )
                }
            }

            // Find the dimension that has the least number of unique bytes at commonPrefixLengths[dim]
            val usedBytes: Array<FixedBitSet?> = kotlin.arrayOfNulls<FixedBitSet>(config.numDims)
            for (dim in 0..<config.numDims) {
                if (commonPrefixLengths[dim] < config.bytesPerDim) {
                    usedBytes[dim] = FixedBitSet(256)
                }
            }
            for (i in from + 1..<to) {
                for (dim in 0..<config.numDims) {
                    if (usedBytes[dim] != null) {
                        val b: Byte = reader.getByteAt(i, dim * config.bytesPerDim + commonPrefixLengths[dim])
                        usedBytes[dim]!!.set(Byte.toUnsignedInt(b))
                    }
                }
            }
            var sortedDim = 0
            var sortedDimCardinality = Int.Companion.MAX_VALUE
            for (dim in 0..<config.numDims) {
                if (usedBytes[dim] != null) {
                    val cardinality: Int = usedBytes[dim]!!.cardinality()
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

            var comparator: BytesRef = scratchBytesRef1
            var collector: BytesRef = scratchBytesRef2
            reader.getValue(from, comparator)
            var leafCardinality = 1
            for (i in from + 1..<to) {
                reader.getValue(i, collector)
                for (dim in 0..<config.numDims) {
                    val start: Int = dim * config.bytesPerDim
                    if (equalsPredicate.test(
                            collector.bytes,
                            collector.offset + start,
                            comparator.bytes,
                            comparator.offset + start
                        )
                        == false
                    ) {
                        leafCardinality++
                        val scratch: BytesRef = collector
                        collector = comparator
                        comparator = scratch
                        break
                    }
                }
            }
            // Save the block file pointer:
            leafBlockFPs[leavesOffset] = out.filePointer

            // Write doc IDs
            val docIDs = spareDocIds
            for (i in from..<to) {
                docIDs[i - from] = reader.getDocID(i)
            }
            // System.out.println("writeLeafBlock pos=" + out.filePointer);
            writeLeafBlockDocs(out, docIDs, 0, count)

            // Write the common prefixes:
            reader.getValue(from, scratchBytesRef1)
            System.arraycopy(
                scratchBytesRef1.bytes, scratchBytesRef1.offset, scratch, 0, config.packedBytesLength()
            )
            writeCommonPrefixes(out, commonPrefixLengths, scratch)

            // Write the full values:
            val packedValues: (Int) -> BytesRef =
                { i: Int ->
                    reader.getValue(from + i, scratchBytesRef1)
                    scratchBytesRef1
                }
            require(
                valuesInOrderAndBounds(
                    config, count, sortedDim, minPackedValue, maxPackedValue, packedValues, docIDs, 0
                )
            )
            writeLeafBlockPackedValues(
                out, commonPrefixLengths, count, sortedDim, packedValues, leafCardinality
            )
        } else {
            // inner node

            var splitDim = 0 //TODO initialized with zero to avoid nullable Int?, however not sure if this is correct
            // compute the split dimension and partition around it
            if (config.numIndexDims == 1) {
                splitDim = 0
            } else {
                // for dimensions > 2 we recompute the bounds for the current inner node to help the
                // algorithm choose best
                // split dimensions. Because it is an expensive operation, the frequency we recompute the
                // bounds is given
                // by SPLITS_BEFORE_EXACT_BOUNDS.
                if (numLeaves != leafBlockFPs.size && config.numIndexDims > 2 && parentSplits.sum() % SPLITS_BEFORE_EXACT_BOUNDS == 0) {
                    splitDim = split(minPackedValue, maxPackedValue, parentSplits)
                }

                // How many leaves will be in the left tree:
                val numLeftLeafNodes = getNumLeftLeafNodes(numLeaves)
                // How many points will be in the left tree:
                val mid: Int = from + numLeftLeafNodes * config.maxPointsInLeafNode

                val commonPrefixLen: Int =
                    commonPrefixComparator.compare(
                        minPackedValue,
                        splitDim * config.bytesPerDim,
                        maxPackedValue,
                        splitDim * config.bytesPerDim
                    )

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

                val rightOffset = leavesOffset + numLeftLeafNodes
                val splitOffset = rightOffset - 1
                // set the split value
                val address: Int = splitOffset * config.bytesPerDim
                splitDimensionValues[splitOffset] = splitDim.toByte()
                reader.getValue(mid, scratchBytesRef1)
                System.arraycopy(
                    scratchBytesRef1.bytes,
                    scratchBytesRef1.offset + splitDim * config.bytesPerDim,
                    splitPackedValues,
                    address,
                    config.bytesPerDim
                )

                val minSplitPackedValue: ByteArray =
                    ArrayUtil.copyOfSubArray(minPackedValue, 0, config.packedIndexBytesLength())
                val maxSplitPackedValue: ByteArray =
                    ArrayUtil.copyOfSubArray(maxPackedValue, 0, config.packedIndexBytesLength())
                System.arraycopy(
                    scratchBytesRef1.bytes,
                    scratchBytesRef1.offset + splitDim * config.bytesPerDim,
                    minSplitPackedValue,
                    splitDim * config.bytesPerDim,
                    config.bytesPerDim
                )
                System.arraycopy(
                    scratchBytesRef1.bytes,
                    scratchBytesRef1.offset + splitDim * config.bytesPerDim,
                    maxSplitPackedValue,
                    splitDim * config.bytesPerDim,
                    config.bytesPerDim
                )

                // recurse
                parentSplits[splitDim]++
                build(
                    leavesOffset,
                    numLeftLeafNodes,
                    reader,
                    from,
                    mid,
                    out,
                    minPackedValue,
                    maxSplitPackedValue,
                    parentSplits,
                    splitPackedValues,
                    splitDimensionValues,
                    leafBlockFPs,
                    spareDocIds
                )
                build(
                    rightOffset,
                    numLeaves - numLeftLeafNodes,
                    reader,
                    mid,
                    to,
                    out,
                    minSplitPackedValue,
                    maxPackedValue,
                    parentSplits,
                    splitPackedValues,
                    splitDimensionValues,
                    leafBlockFPs,
                    spareDocIds
                )
                parentSplits[splitDim]--
            }
        }
    }


    @Throws(IOException::class)
    private fun computePackedValueBounds(
        slice: BKDRadixSelector.PathSlice, minPackedValue: ByteArray, maxPackedValue: ByteArray
    ) {
        slice.writer.getReader(slice.start, slice.count).use { reader ->
            if (reader.next() == false) {
                return
            }
            var value: BytesRef = reader.pointValue()!!.packedValue()
            System.arraycopy(
                value.bytes, value.offset, minPackedValue, 0, config.packedIndexBytesLength()
            )
            System.arraycopy(
                value.bytes, value.offset, maxPackedValue, 0, config.packedIndexBytesLength()
            )
            while (reader.next()) {
                value = reader.pointValue()!!.packedValue()
                for (dim in 0..<config.numIndexDims) {
                    val startOffset: Int = dim * config.bytesPerDim
                    if (comparator.compare(
                            value.bytes, value.offset + startOffset, minPackedValue, startOffset
                        )
                        < 0
                    ) {
                        System.arraycopy(
                            value.bytes,
                            value.offset + startOffset,
                            minPackedValue,
                            startOffset,
                            config.bytesPerDim
                        )
                    } else if (comparator.compare(
                            value.bytes, value.offset + startOffset, maxPackedValue, startOffset
                        )
                        > 0
                    ) {
                        System.arraycopy(
                            value.bytes,
                            value.offset + startOffset,
                            maxPackedValue,
                            startOffset,
                            config.bytesPerDim
                        )
                    }
                }
            }
        }
    }

    /**
     * The point writer contains the data that is going to be splitted using radix selection. / * This
     * method is used when we are merging previously written segments, in the numDims > 1 case.
     */
    @Throws(IOException::class)
    private fun build(
        leavesOffset: Int,
        numLeaves: Int,
        points: BKDRadixSelector.PathSlice,
        out: IndexOutput,
        radixSelector: BKDRadixSelector,
        minPackedValue: ByteArray,
        maxPackedValue: ByteArray,
        parentSplits: IntArray,
        splitPackedValues: ByteArray,
        splitDimensionValues: ByteArray,
        leafBlockFPs: LongArray,
        spareDocIds: IntArray
    ) {
        if (numLeaves == 1) {
            // Leaf node: write block
            // We can write the block in any order so by default we write it sorted by the dimension that
            // has the
            // least number of unique bytes at commonPrefixLengths[dim], which makes compression more
            // efficient

            val heapSource: HeapPointWriter
            if (points.writer is HeapPointWriter == false) {
                // Adversarial cases can cause this, e.g. merging big segments with most of the points
                // deleted
                heapSource = switchToHeap(points.writer)
            } else {
                heapSource = points.writer
            }

            val from: Int = Math.toIntExact(points.start)
            val to: Int = Math.toIntExact(points.start + points.count)
            // we store common prefix on scratch
            computeCommonPrefixLength(heapSource, scratch, from, to)

            var sortedDim = 0
            var sortedDimCardinality = Int.Companion.MAX_VALUE
            val usedBytes: Array<FixedBitSet?> = kotlin.arrayOfNulls<FixedBitSet>(config.numDims)
            for (dim in 0..<config.numDims) {
                if (commonPrefixLengths[dim] < config.bytesPerDim) {
                    usedBytes[dim] = FixedBitSet(256)
                }
            }
            // Find the dimension to compress
            for (dim in 0..<config.numDims) {
                val prefix = commonPrefixLengths[dim]
                if (prefix < config.bytesPerDim) {
                    val offset: Int = dim * config.bytesPerDim
                    for (i in from..<to) {
                        val value: PointValue = heapSource.getPackedValueSlice(i)!!
                        val packedValue: BytesRef = value.packedValue()
                        val bucket: Int = packedValue.bytes[packedValue.offset + offset + prefix].toInt() and 0xff
                        usedBytes[dim]!!.set(bucket)
                    }
                    val cardinality: Int = usedBytes[dim]!!.cardinality()
                    if (cardinality < sortedDimCardinality) {
                        sortedDim = dim
                        sortedDimCardinality = cardinality
                    }
                }
            }

            // sort the chosen dimension
            radixSelector.heapRadixSort(heapSource, from, to, sortedDim, commonPrefixLengths[sortedDim])
            // compute cardinality
            val leafCardinality: Int = heapSource.computeCardinality(from, to, commonPrefixLengths)

            // Save the block file pointer:
            leafBlockFPs[leavesOffset] = out.filePointer

            // System.out.println("  write leaf block @ fp=" + out.filePointer);

            // Write docIDs first, as their own chunk, so that at intersect time we can add all docIDs w/o
            // loading the values:
            val count = to - from
            require(count > 0) { "numLeaves=$numLeaves leavesOffset=$leavesOffset" }
            require(count <= spareDocIds.size) { "count=" + count + " > length=" + spareDocIds.size }
            // Write doc IDs
            val docIDs = spareDocIds
            for (i in 0..<count) {
                docIDs[i] = heapSource.getPackedValueSlice(from + i)!!.docID()
            }
            writeLeafBlockDocs(out, docIDs, 0, count)

            // TODO: minor opto: we don't really have to write the actual common prefixes, because
            // BKDReader on recursing can regenerate it for us
            // from the index, much like how terms dict does so from the FST:

            // Write the common prefixes:
            writeCommonPrefixes(out, commonPrefixLengths, scratch)

            // Write the full values:
            val packedValues: (Int) -> BytesRef =
                { i: Int -> heapSource.getPackedValueSlice(from + i)!!.packedValue() }
            require(
                valuesInOrderAndBounds(
                    config, count, sortedDim, minPackedValue, maxPackedValue, packedValues, docIDs, 0
                )
            )
            writeLeafBlockPackedValues(
                out, commonPrefixLengths, count, sortedDim, packedValues, leafCardinality
            )
        } else {
            // Inner node: partition/recurse

            val splitDim: Int
            if (config.numIndexDims == 1) {
                splitDim = 0
            } else {
                // for dimensions > 2 we recompute the bounds for the current inner node to help the
                // algorithm choose best
                // split dimensions. Because it is an expensive operation, the frequency we recompute the
                // bounds is given
                // by SPLITS_BEFORE_EXACT_BOUNDS.
                if (numLeaves != leafBlockFPs.size && config.numIndexDims > 2 && parentSplits.sum() % SPLITS_BEFORE_EXACT_BOUNDS == 0
                ) {
                    computePackedValueBounds(points, minPackedValue, maxPackedValue)
                }
                splitDim = split(minPackedValue, maxPackedValue, parentSplits)
            }

            require(
                numLeaves <= leafBlockFPs.size
            ) { "numLeaves=" + numLeaves + " leafBlockFPs.length=" + leafBlockFPs.size }

            // How many leaves will be in the left tree:
            val numLeftLeafNodes = getNumLeftLeafNodes(numLeaves)
            // How many points will be in the left tree:
            val leftCount = numLeftLeafNodes * config.maxPointsInLeafNode.toLong()

            val slices: Array<BKDRadixSelector.PathSlice?> = kotlin.arrayOfNulls<BKDRadixSelector.PathSlice>(2)

            val commonPrefixLen: Int =
                commonPrefixComparator.compare(
                    minPackedValue,
                    splitDim * config.bytesPerDim,
                    maxPackedValue,
                    splitDim * config.bytesPerDim
                )

            val splitValue: ByteArray =
                radixSelector.select(
                    points,
                    slices,
                    points.start,
                    points.start + points.count,
                    points.start + leftCount,
                    splitDim,
                    commonPrefixLen
                )

            val rightOffset = leavesOffset + numLeftLeafNodes
            val splitValueOffset = rightOffset - 1

            splitDimensionValues[splitValueOffset] = splitDim.toByte()
            val address: Int = splitValueOffset * config.bytesPerDim
            System.arraycopy(splitValue, 0, splitPackedValues, address, config.bytesPerDim)

            val minSplitPackedValue = ByteArray(config.packedIndexBytesLength())
            System.arraycopy(minPackedValue, 0, minSplitPackedValue, 0, config.packedIndexBytesLength())

            val maxSplitPackedValue = ByteArray(config.packedIndexBytesLength())
            System.arraycopy(maxPackedValue, 0, maxSplitPackedValue, 0, config.packedIndexBytesLength())

            System.arraycopy(
                splitValue,
                0,
                minSplitPackedValue,
                splitDim * config.bytesPerDim,
                config.bytesPerDim
            )
            System.arraycopy(
                splitValue,
                0,
                maxSplitPackedValue,
                splitDim * config.bytesPerDim,
                config.bytesPerDim
            )

            parentSplits[splitDim]++
            // Recurse on left tree:
            build(
                leavesOffset,
                numLeftLeafNodes,
                slices[0]!!,
                out,
                radixSelector,
                minPackedValue,
                maxSplitPackedValue,
                parentSplits,
                splitPackedValues,
                splitDimensionValues,
                leafBlockFPs,
                spareDocIds
            )

            // Recurse on right tree:
            build(
                rightOffset,
                numLeaves - numLeftLeafNodes,
                slices[1]!!,
                out,
                radixSelector,
                minSplitPackedValue,
                maxPackedValue,
                parentSplits,
                splitPackedValues,
                splitDimensionValues,
                leafBlockFPs,
                spareDocIds
            )

            parentSplits[splitDim]--
        }
    }

    fun computeCommonPrefixLength(
        heapPointWriter: HeapPointWriter, commonPrefix: ByteArray, from: Int, to: Int
    ) {
        Arrays.fill(commonPrefixLengths, config.bytesPerDim)
        var value: PointValue = heapPointWriter.getPackedValueSlice(from)!!
        var packedValue: BytesRef = value.packedValue()
        for (dim in 0..<config.numDims) {
            System.arraycopy(
                packedValue.bytes,
                packedValue.offset + dim * config.bytesPerDim,
                commonPrefix,
                dim * config.bytesPerDim,
                config.bytesPerDim
            )
        }
        for (i in from + 1..<to) {
            value = heapPointWriter.getPackedValueSlice(i)!!
            packedValue = value.packedValue()
            for (dim in 0..<config.numDims) {
                if (commonPrefixLengths[dim] != 0) {
                    commonPrefixLengths[dim] =
                        min(
                            commonPrefixLengths[dim],
                            commonPrefixComparator.compare(
                                commonPrefix,
                                dim * config.bytesPerDim,
                                packedValue.bytes,
                                packedValue.offset + dim * config.bytesPerDim
                            )
                        )
                }
            }
        }
    }

    companion object {
        const val CODEC_NAME: String = "BKD"
        const val VERSION_START: Int = 4 // version used by Lucene 7.0

        // public static final int VERSION_CURRENT = VERSION_START;
        const val VERSION_LEAF_STORES_BOUNDS: Int = 5
        const val VERSION_SELECTIVE_INDEXING: Int = 6
        const val VERSION_LOW_CARDINALITY_LEAVES: Int = 7
        const val VERSION_META_FILE: Int = 9
        const val VERSION_CURRENT: Int = VERSION_META_FILE

        /** Number of splits before we compute the exact bounding box of an inner node.  */
        private const val SPLITS_BEFORE_EXACT_BOUNDS = 4

        /** Default maximum heap to use, before spilling to (slower) disk  */
        const val DEFAULT_MAX_MB_SORT_IN_HEAP: Float = 16.0f

        private fun verifyParams(maxMBSortInHeap: Double, totalPointCount: Long) {
            require(!(maxMBSortInHeap < 0.0)) { "maxMBSortInHeap must be >= 0.0 (got: $maxMBSortInHeap)" }
            require(totalPointCount >= 0) { "totalPointCount must be >=0 (got: $totalPointCount)" }
        }

        /**
         * Return an array that contains the min and max values for the [offset, offset+length] interval
         * of the given [BytesRef]s.
         */
        private fun computeMinMax(
            count: Int, packedValues: (Int) -> BytesRef, offset: Int, length: Int
        ): Array<BytesRef> {
            require(length > 0)
            val min = BytesRefBuilder()
            val max = BytesRefBuilder()
            val first: BytesRef = packedValues(0)
            min.copyBytes(first.bytes, first.offset + offset, length)
            max.copyBytes(first.bytes, first.offset + offset, length)
            for (i in 1..<count) {
                val candidate: BytesRef = packedValues(i)
                if (Arrays.compareUnsigned(
                        min.bytes(),
                        0,
                        length,
                        candidate.bytes,
                        candidate.offset + offset,
                        candidate.offset + offset + length
                    )
                    > 0
                ) {
                    min.copyBytes(candidate.bytes, candidate.offset + offset, length)
                } else if (Arrays.compareUnsigned(
                        max.bytes(),
                        0,
                        length,
                        candidate.bytes,
                        candidate.offset + offset,
                        candidate.offset + offset + length
                    )
                    < 0
                ) {
                    max.copyBytes(candidate.bytes, candidate.offset + offset, length)
                }
            }
            return arrayOf<BytesRef>(min.get(), max.get())
        }

        private fun runLen(
            packedValues: (Int) -> BytesRef, start: Int, end: Int, byteOffset: Int
        ): Int {
            val first: BytesRef = packedValues(start)
            val b: Byte = first.bytes[first.offset + byteOffset]
            for (i in start + 1..<end) {
                val ref: BytesRef = packedValues(i)
                val b2: Byte = ref.bytes[ref.offset + byteOffset]
                require(Byte.toUnsignedInt(b2) >= Byte.toUnsignedInt(b))
                if (b != b2) {
                    return i - start
                }
            }
            return end - start
        }

        // only called from assert
        private fun valuesInOrderAndBounds(
            config: BKDConfig,
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
            for (i in 0..<count) {
                val packedValue: BytesRef = values(i)
                require(packedValue.length == config.packedBytesLength())
                require(
                    valueInOrder(
                        config,
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
                require(valueInBounds(config, packedValue, minPackedValue, maxPackedValue))
            }
            return true
        }

        // only called from assert
        private fun valueInOrder(
            config: BKDConfig,
            ord: Long,
            sortedDim: Int,
            lastPackedValue: ByteArray,
            packedValue: ByteArray,
            packedValueOffset: Int,
            doc: Int,
            lastDoc: Int
        ): Boolean {
            val dimOffset: Int = sortedDim * config.bytesPerDim
            if (ord > 0) {
                var cmp: Int =
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
                        ("values out of order: last value="
                                + BytesRef(lastPackedValue)
                                + " current value="
                                + BytesRef(packedValue, packedValueOffset, config.packedBytesLength())
                                + " ord="
                                + ord)
                    )
                }
                if (cmp == 0 && config.numDims > config.numIndexDims) {
                    cmp =
                        Arrays.compareUnsigned(
                            lastPackedValue,
                            config.packedIndexBytesLength(),
                            config.packedBytesLength(),
                            packedValue,
                            packedValueOffset + config.packedIndexBytesLength(),
                            packedValueOffset + config.packedBytesLength()
                        )
                    if (cmp > 0) {
                        throw AssertionError(
                            ("data values out of order: last value="
                                    + BytesRef(lastPackedValue)
                                    + " current value="
                                    + BytesRef(packedValue, packedValueOffset, config.packedBytesLength())
                                    + " ord="
                                    + ord)
                        )
                    }
                }
                if (cmp == 0 && doc < lastDoc) {
                    throw AssertionError(
                        "docs out of order: last doc=$lastDoc current doc=$doc ord=$ord"
                    )
                }
            }
            System.arraycopy(
                packedValue, packedValueOffset, lastPackedValue, 0, config.packedBytesLength()
            )
            return true
        }

        // only called from assert
        private fun valueInBounds(
            config: BKDConfig, packedValue: BytesRef, minPackedValue: ByteArray, maxPackedValue: ByteArray
        ): Boolean {
            for (dim in 0..<config.numIndexDims) {
                val offset: Int = config.bytesPerDim * dim
                if (Arrays.compareUnsigned(
                        packedValue.bytes,
                        packedValue.offset + offset,
                        packedValue.offset + offset + config.bytesPerDim,
                        minPackedValue,
                        offset,
                        offset + config.bytesPerDim
                    )
                    < 0
                ) {
                    return false
                }
                if (Arrays.compareUnsigned(
                        packedValue.bytes,
                        packedValue.offset + offset,
                        packedValue.offset + offset + config.bytesPerDim,
                        maxPackedValue,
                        offset,
                        offset + config.bytesPerDim
                    )
                    > 0
                ) {
                    return false
                }
            }

            return true
        }
    }
}
