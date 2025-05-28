package org.gnit.lucenekmp.util.bkd

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.MathUtil

/**
 * Handles reading a block KD-tree in byte[] space previously written with [BKDWriter].
 *
 * @lucene.experimental
 */
class BKDReader(metaIn: IndexInput, indexIn: IndexInput, dataIn: IndexInput) : PointValues() {
    val config: BKDConfig
    val numLeaves: Int
    val `in`: IndexInput
    override val minPackedValue: ByteArray
    override val maxPackedValue: ByteArray
    val pointCount: Long
    override val docCount: Int
    val version: Int = CodecUtil.checkHeader(
        metaIn, BKDWriter.CODEC_NAME, BKDWriter.VERSION_START, BKDWriter.VERSION_CURRENT
    )
    var minLeafBlockFP: Long = 0

    private var indexStartPointer: Long = 0
    private val numIndexBytes: Int
    private val indexIn: IndexInput

    // if true, the tree is a legacy balanced tree
    private val isTreeBalanced: Boolean

    /**
     * Caller must pre-seek the provided [IndexInput] to the index location that [ ][BKDWriter.finish] returned. BKD tree is always stored off-heap.
     */
    init {
        val numDims: Int = metaIn.readVInt()
        val numIndexDims: Int = if (version >= BKDWriter.VERSION_SELECTIVE_INDEXING) {
            metaIn.readVInt()
        } else {
            numDims
        }
        val maxPointsInLeafNode: Int = metaIn.readVInt()
        val bytesPerDim: Int = metaIn.readVInt()
        config = BKDConfig(numDims, numIndexDims, bytesPerDim, maxPointsInLeafNode)

        // Read index:
        numLeaves = metaIn.readVInt()
        require(numLeaves > 0)

        minPackedValue = ByteArray(config.packedIndexBytesLength())
        maxPackedValue = ByteArray(config.packedIndexBytesLength())

        metaIn.readBytes(minPackedValue, 0, config.packedIndexBytesLength())
        metaIn.readBytes(maxPackedValue, 0, config.packedIndexBytesLength())
        val comparator: ByteArrayComparator =
            ArrayUtil.getUnsignedComparator(config.bytesPerDim)
        for (dim in 0..<config.numIndexDims) {
            if (comparator.compare(
                    minPackedValue,
                    dim * config.bytesPerDim,
                    maxPackedValue,
                    dim * config.bytesPerDim
                )
                > 0
            ) {
                throw CorruptIndexException(
                    ("minPackedValue "
                            + BytesRef(minPackedValue)
                            + " is > maxPackedValue "
                            + BytesRef(maxPackedValue)
                            + " for dim="
                            + dim),
                    metaIn
                )
            }
        }

        pointCount = metaIn.readVLong()
        docCount = metaIn.readVInt()

        numIndexBytes = metaIn.readVInt()
        if (version >= BKDWriter.VERSION_META_FILE) {
            minLeafBlockFP = metaIn.readLong()
            indexStartPointer = metaIn.readLong()
        } else {
            indexStartPointer = indexIn.filePointer
            minLeafBlockFP = indexIn.readVLong()
            indexIn.seek(indexStartPointer)
        }
        this.indexIn = indexIn
        this.`in` = dataIn
        // for only one leaf, balanced and unbalanced trees can be handled the same way
        // we set it to unbalanced.
        this.isTreeBalanced = numLeaves != 1 && isTreeBalanced()
    }

    @Throws(IOException::class)
    private fun isTreeBalanced(): Boolean {
        if (version >= BKDWriter.VERSION_META_FILE) {
            // since lucene 8.6 all trees are unbalanced.
            return false
        }
        if (config.numDims > 1) {
            // high dimensional tree in pre-8.6 indices are balanced.
            require(1 shl MathUtil.log(numLeaves, 2) == numLeaves)
            return true
        }
        if (1 shl MathUtil.log(numLeaves, 2) != numLeaves) {
            // if we don't have enough leaves to fill the last level then it is unbalanced
            return false
        }
        // count of the last node for unbalanced trees
        val lastLeafNodePointCount: Int = Math.toIntExact(pointCount % config.maxPointsInLeafNode)
        // navigate to last node
        val pointTree: PointTree = this.pointTree
        do {
            while (pointTree.moveToSibling()) {
                // intentionally empty, what matters is side effects of moveToSibling
            }
        } while (pointTree.moveToChild())
        // count number of docs in the node
        val count = intArrayOf(0)
        pointTree.visitDocIDs(
            object : IntersectVisitor {
                override fun visit(docID: Int) {
                    count[0]++
                }

                @Throws(IOException::class)
                override fun visit(iterator: DocIdSetIterator) {
                    var docID: Int
                    while ((iterator.nextDoc().also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                        visit(docID)
                    }
                }

                override fun visit(ref: IntsRef) {
                    count[0] += ref.length
                }

                override fun visit(docID: Int, packedValue: ByteArray) {
                    throw AssertionError()
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                    throw AssertionError()
                }
            })
        return count[0] != lastLeafNodePointCount
    }

    override val pointTree: PointTree
        get() = BKDPointTree(
            indexIn.slice("packedIndex", indexStartPointer, numIndexBytes.toLong()),
            this.`in`.clone(),
            config,
            numLeaves,
            version,
            pointCount,
            minPackedValue,
            maxPackedValue,
            isTreeBalanced
        )

    private class BKDPointTree(
        // used to read the packed tree off-heap
        private val innerNodes: IndexInput,
        // used to read the packed leaves off-heap
        private val leafNodes: IndexInput,
        // tree parameters
        private val config: BKDConfig,
        numLeaves: Int,
        // version of the index
        private val version: Int,
        pointCount: Long,
        private var nodeID: Int,
        // level is 1-based so that we can do level-1 w/o checking each time:
        private var level: Int,
        minPackedValue: ByteArray,
        maxPackedValue: ByteArray,
        scratchIterator: BKDReaderDocIDSetIterator,
        scratchDataPackedValue: ByteArray,
        scratchMinIndexPackedValue: ByteArray,
        scratchMaxIndexPackedValue: ByteArray,
        commonPrefixLengths: IntArray,
        // if true the tree is balanced, otherwise unbalanced
        private val isTreeBalanced: Boolean
    ) : PointTree {
        // during clone, the node root can be different to 1
        private val nodeRoot: Int = nodeID

        // holds the minimum (left most) leaf block file pointer for each level we've recursed to:
        private val leafBlockFPStack: LongArray

        // holds the address, in the off-heap index, after reading the node data of each level:
        private val readNodeDataPositions: IntArray

        // holds the address, in the off-heap index, of the right-node of each level:
        private val rightNodePositions: IntArray

        // holds the splitDim position for each level:
        private val splitDimsPos: IntArray

        // true if the per-dim delta we read for the node at this level is a negative offset vs. the
        // last split on this dim; this is a packed
        // 2D array, i.e. to access array[level][dim] you read from negativeDeltas[level*numDims+dim].
        // this will be true if the last time we
        // split on this dimension, we next pushed to the left sub-tree:
        private val negativeDeltas: BooleanArray

        // holds the packed per-level split values
        private val splitValuesStack: Array<ByteArray?>

        // holds the min / max value of the current node.
        override val minPackedValue: ByteArray = minPackedValue.copyOf()
        override val maxPackedValue: ByteArray = maxPackedValue.copyOf()

        // holds the previous value of the split dimension
        private val splitDimValueStack: Array<ByteArray?>

        // number of leaves
        private val leafNodeOffset: Int = numLeaves

        // total number of points
        val pointCount: Long

        // last node might not be fully populated
        private val lastLeafNodePointCount: Int

        // right most leaf node ID
        private val rightMostLeafNode: Int

        // helper objects for reading doc values
        private val scratchDataPackedValue: ByteArray
        private val scratchMinIndexPackedValue: ByteArray
        private val scratchMaxIndexPackedValue: ByteArray
        private val commonPrefixLengths: IntArray
        private val scratchIterator: BKDReaderDocIDSetIterator
        private val docIdsWriter: DocIdsWriter

        constructor(
            innerNodes: IndexInput,
            leafNodes: IndexInput,
            config: BKDConfig,
            numLeaves: Int,
            version: Int,
            pointCount: Long,
            minPackedValue: ByteArray,
            maxPackedValue: ByteArray,
            isTreeBalanced: Boolean
        ) : this(
            innerNodes,
            leafNodes,
            config,
            numLeaves,
            version,
            pointCount,
            1,
            1,
            minPackedValue,
            maxPackedValue,
            BKDReaderDocIDSetIterator(config.maxPointsInLeafNode),
            ByteArray(config.packedBytesLength()),
            ByteArray(config.packedIndexBytesLength()),
            ByteArray(config.packedIndexBytesLength()),
            IntArray(config.numDims),
            isTreeBalanced
        ) {
            // read root node
            readNodeData(false)
        }

        init {
            // stack arrays that keep information at different levels
            val treeDepth = getTreeDepth(numLeaves)
            splitDimValueStack = kotlin.arrayOfNulls<ByteArray>(treeDepth)
            splitValuesStack = kotlin.arrayOfNulls<ByteArray>(treeDepth)
            splitValuesStack[0] = ByteArray(config.packedIndexBytesLength())
            leafBlockFPStack = LongArray(treeDepth + 1)
            readNodeDataPositions = IntArray(treeDepth + 1)
            rightNodePositions = IntArray(treeDepth)
            splitDimsPos = IntArray(treeDepth)
            negativeDeltas = BooleanArray(config.numIndexDims * treeDepth)
            // information about the unbalance of the tree so we can report the exact size below a node
            this.pointCount = pointCount
            rightMostLeafNode = (1 shl treeDepth - 1) - 1
            val lastLeafNodePointCount: Int = Math.toIntExact(pointCount % config.maxPointsInLeafNode)
            this.lastLeafNodePointCount =
                if (lastLeafNodePointCount == 0) config.maxPointsInLeafNode else lastLeafNodePointCount
            // scratch objects, reused between clones so NN search are not creating those objects
            // in every clone.
            this.scratchIterator = scratchIterator
            this.commonPrefixLengths = commonPrefixLengths
            this.scratchDataPackedValue = scratchDataPackedValue
            this.scratchMinIndexPackedValue = scratchMinIndexPackedValue
            this.scratchMaxIndexPackedValue = scratchMaxIndexPackedValue
            this.docIdsWriter = scratchIterator.docIdsWriter
        }

        override fun clone(): PointTree {
            val index =
                BKDPointTree(
                    innerNodes.clone(),
                    leafNodes.clone(),
                    config,
                    leafNodeOffset,
                    version,
                    pointCount,
                    nodeID,
                    level,
                    minPackedValue,
                    maxPackedValue,
                    scratchIterator,
                    scratchDataPackedValue,
                    scratchMinIndexPackedValue,
                    scratchMaxIndexPackedValue,
                    commonPrefixLengths,
                    isTreeBalanced
                )
            index.leafBlockFPStack[index.level] = leafBlockFPStack[level]
            if (this.isLeafNode == false) {
                // copy node data
                index.rightNodePositions[index.level] = rightNodePositions[level]
                index.readNodeDataPositions[index.level] = readNodeDataPositions[level]
                index.splitValuesStack[index.level] = splitValuesStack[level]!!.copyOf()
                System.arraycopy(
                    negativeDeltas,
                    level * config.numIndexDims,
                    index.negativeDeltas,
                    level * config.numIndexDims,
                    config.numIndexDims
                )
                index.splitDimsPos[level] = splitDimsPos[level]
            }
            return index
        }

        @Throws(IOException::class)
        override fun moveToChild(): Boolean {
            if (this.isLeafNode) {
                return false
            }
            resetNodeDataPosition()
            pushBoundsLeft()
            pushLeft()
            return true
        }

        @Throws(IOException::class)
        fun resetNodeDataPosition() {
            // move position of the inner nodes index to visit the first child
            require(readNodeDataPositions[level] <= innerNodes.filePointer)
            innerNodes.seek(readNodeDataPositions[level].toLong())
        }

        fun pushBoundsLeft() {
            val splitDimPos = splitDimsPos[level]
            if (splitDimValueStack[level] == null) {
                splitDimValueStack[level] = ByteArray(config.bytesPerDim)
            }
            // save the dimension we are going to change
            System.arraycopy(
                maxPackedValue, splitDimPos, splitDimValueStack[level]!!, 0, config.bytesPerDim
            )
            require(
                ArrayUtil.getUnsignedComparator(config.bytesPerDim)
                    .compare(maxPackedValue, splitDimPos, splitValuesStack[level]!!, splitDimPos)
                        >= 0
            ) {
                ("config.bytesPerDim="
                        + config.bytesPerDim
                        + " splitDimPos="
                        + splitDimsPos[level]
                        + " config.numIndexDims="
                        + config.numIndexDims
                        + " config.numDims="
                        + config.numDims)
            }
            // add the split dim value:
            System.arraycopy(
                splitValuesStack[level]!!, splitDimPos, maxPackedValue, splitDimPos, config.bytesPerDim
            )
        }

        @Throws(IOException::class)
        fun pushLeft() {
            nodeID *= 2
            level++
            readNodeData(true)
        }

        fun pushBoundsRight() {
            val splitDimPos = splitDimsPos[level]
            // we should have already visited the left node
            checkNotNull(splitDimValueStack[level])
            // save the dimension we are going to change
            System.arraycopy(
                minPackedValue, splitDimPos, splitDimValueStack[level]!!, 0, config.bytesPerDim
            )
            require(
                ArrayUtil.getUnsignedComparator(config.bytesPerDim)
                    .compare(minPackedValue, splitDimPos, splitValuesStack[level]!!, splitDimPos)
                        <= 0
            ) {
                ("config.bytesPerDim="
                        + config.bytesPerDim
                        + " splitDimPos="
                        + splitDimsPos[level]
                        + " config.numIndexDims="
                        + config.numIndexDims
                        + " config.numDims="
                        + config.numDims)
            }
            // add the split dim value:
            System.arraycopy(
                splitValuesStack[level]!!, splitDimPos, minPackedValue, splitDimPos, config.bytesPerDim
            )
        }

        @Throws(IOException::class)
        fun pushRight() {
            val nodePosition = rightNodePositions[level]
            require(
                nodePosition >= innerNodes.filePointer
            ) { "nodePosition = " + nodePosition + " < currentPosition=" + innerNodes.filePointer }
            innerNodes.seek(nodePosition.toLong())
            nodeID = 2 * nodeID + 1
            level++
            readNodeData(false)
        }

        @Throws(IOException::class)
        override fun moveToSibling(): Boolean {
            if (this.isLeftNode == false || this.isRootNode) {
                return false
            }
            pop()
            popBounds(maxPackedValue)
            pushBoundsRight()
            pushRight()
            require(nodeExists())
            return true
        }

        fun pop() {
            nodeID /= 2
            level--
        }

        fun popBounds(packedValue: ByteArray) {
            // restore the split dimension
            System.arraycopy(
                splitDimValueStack[level]!!, 0, packedValue, splitDimsPos[level], config.bytesPerDim
            )
        }

        override fun moveToParent(): Boolean {
            if (this.isRootNode) {
                return false
            }
            val packedValue = if (this.isLeftNode) maxPackedValue else minPackedValue
            pop()
            popBounds(packedValue)
            return true
        }

        val isRootNode: Boolean
            get() = nodeID == nodeRoot

        val isLeftNode: Boolean
            get() = (nodeID and 1) == 0

        val isLeafNode: Boolean
            get() = nodeID >= leafNodeOffset

        fun nodeExists(): Boolean {
            return nodeID - leafNodeOffset < leafNodeOffset
        }

        val leafBlockFP: Long
            /** Only valid after pushLeft or pushRight, not pop!  */
            get() {
                require(this.isLeafNode) { "nodeID=$nodeID is not a leaf" }
                return leafBlockFPStack[level]
            }

        override fun size(): Long {
            var leftMostLeafNode = nodeID
            while (leftMostLeafNode < leafNodeOffset) {
                leftMostLeafNode = leftMostLeafNode * 2
            }
            var rightMostLeafNode = nodeID
            while (rightMostLeafNode < leafNodeOffset) {
                rightMostLeafNode = rightMostLeafNode * 2 + 1
            }
            val numLeaves: Int = if (rightMostLeafNode >= leftMostLeafNode) {
                // both are on the same level
                rightMostLeafNode - leftMostLeafNode + 1
            } else {
                // left is one level deeper than right
                rightMostLeafNode - leftMostLeafNode + 1 + leafNodeOffset
            }
            require(numLeaves == getNumLeavesSlow(nodeID)) { numLeaves.toString() + " " + getNumLeavesSlow(nodeID) }
            if (isTreeBalanced) {
                // before lucene 8.6, trees might have been constructed as fully balanced trees.
                return sizeFromBalancedTree(leftMostLeafNode, rightMostLeafNode)
            }
            // size for an unbalanced tree.
            return if (rightMostLeafNode == this.rightMostLeafNode)
                (numLeaves - 1).toLong() * config.maxPointsInLeafNode + lastLeafNodePointCount
            else
                numLeaves.toLong() * config.maxPointsInLeafNode
        }

        fun sizeFromBalancedTree(leftMostLeafNode: Int, rightMostLeafNode: Int): Long {
            // number of points that need to be distributed between leaves, one per leaf
            val extraPoints: Int =
                Math.toIntExact((config.maxPointsInLeafNode.toLong() * this.leafNodeOffset) - pointCount)
            require(extraPoints < leafNodeOffset) { "point excess should be lower than leafNodeOffset" }
            // offset where we stop adding one point to the leaves
            val nodeOffset = leafNodeOffset - extraPoints
            var count: Long = 0
            for (node in leftMostLeafNode..rightMostLeafNode) {
                // offsetPosition provides which extra point will be added to this node
                count += if (balanceTreeNodePosition(0, leafNodeOffset, node - leafNodeOffset, 0, 0) < nodeOffset) {
                    config.maxPointsInLeafNode
                } else {
                    config.maxPointsInLeafNode - 1
                }
            }
            return count
        }

        fun balanceTreeNodePosition(
            minNode: Int, maxNode: Int, node: Int, position: Int, level: Int
        ): Int {
            if (maxNode - minNode == 1) {
                return position
            }
            val mid = (minNode + maxNode + 1) ushr 1
            return if (mid > node) {
                balanceTreeNodePosition(minNode, mid, node, position, level + 1)
            } else {
                balanceTreeNodePosition(mid, maxNode, node, position + (1 shl level), level + 1)
            }
        }

        @Throws(IOException::class)
        override fun visitDocIDs(visitor: IntersectVisitor) {
            resetNodeDataPosition()
            addAll(visitor, false)
        }

        @Throws(IOException::class)
        fun addAll(visitor: IntersectVisitor, grown: Boolean) {
            var grown = grown
            if (grown == false) {
                val size = size()
                if (size <= Int.Companion.MAX_VALUE) {
                    visitor.grow(size.toInt())
                    grown = true
                }
            }
            if (this.isLeafNode) {
                // Leaf node
                leafNodes.seek(this.leafBlockFP)
                // How many points are stored in this leaf cell:
                val count: Int = leafNodes.readVInt()
                // No need to call grow(), it has been called up-front
                docIdsWriter.readInts(leafNodes, count, visitor)
            } else {
                pushLeft()
                addAll(visitor, grown)
                pop()
                pushRight()
                addAll(visitor, grown)
                pop()
            }
        }

        @Throws(IOException::class)
        override fun visitDocValues(visitor: IntersectVisitor) {
            resetNodeDataPosition()
            visitLeavesOneByOne(visitor)
        }

        @Throws(IOException::class)
        fun visitLeavesOneByOne(visitor: IntersectVisitor) {
            if (this.isLeafNode) {
                // Leaf node
                visitDocValues(visitor, this.leafBlockFP)
            } else {
                pushLeft()
                visitLeavesOneByOne(visitor)
                pop()
                pushRight()
                visitLeavesOneByOne(visitor)
                pop()
            }
        }

        @Throws(IOException::class)
        fun visitDocValues(visitor: IntersectVisitor, fp: Long) {
            // Leaf node; scan and filter all points in this block:
            val count = readDocIDs(leafNodes, fp, scratchIterator)
            if (version >= BKDWriter.VERSION_LOW_CARDINALITY_LEAVES) {
                visitDocValuesWithCardinality(
                    commonPrefixLengths,
                    scratchDataPackedValue,
                    scratchMinIndexPackedValue,
                    scratchMaxIndexPackedValue,
                    leafNodes,
                    scratchIterator,
                    count,
                    visitor
                )
            } else {
                visitDocValuesNoCardinality(
                    commonPrefixLengths,
                    scratchDataPackedValue,
                    scratchMinIndexPackedValue,
                    scratchMaxIndexPackedValue,
                    leafNodes,
                    scratchIterator,
                    count,
                    visitor
                )
            }
        }

        @Throws(IOException::class)
        fun readDocIDs(`in`: IndexInput, blockFP: Long, iterator: BKDReaderDocIDSetIterator): Int {
            `in`.seek(blockFP)
            // How many points are stored in this leaf cell:
            val count: Int = `in`.readVInt()

            docIdsWriter.readInts(`in`, count, iterator.docIDs)

            return count
        }

        // for assertions
        fun getNumLeavesSlow(node: Int): Int {
            if (node >= 2 * leafNodeOffset) {
                return 0
            } else if (node >= leafNodeOffset) {
                return 1
            } else {
                val leftCount = getNumLeavesSlow(node * 2)
                val rightCount = getNumLeavesSlow(node * 2 + 1)
                return leftCount + rightCount
            }
        }

        @Throws(IOException::class)
        fun readNodeData(isLeft: Boolean) {
            leafBlockFPStack[level] = leafBlockFPStack[level - 1]
            if (isLeft == false) {
                // read leaf block FP delta
                leafBlockFPStack[level] += innerNodes.readVLong()
            }

            if (this.isLeafNode == false) {
                System.arraycopy(
                    negativeDeltas,
                    (level - 1) * config.numIndexDims,
                    negativeDeltas,
                    level * config.numIndexDims,
                    config.numIndexDims
                )
                negativeDeltas[level * config.numIndexDims + (splitDimsPos[level - 1] / config.bytesPerDim)] =
                    isLeft

                if (splitValuesStack[level] == null) {
                    splitValuesStack[level] = splitValuesStack[level - 1]!!.copyOf()
                } else {
                    System.arraycopy(
                        splitValuesStack[level - 1]!!,
                        0,
                        splitValuesStack[level]!!,
                        0,
                        config.packedIndexBytesLength()
                    )
                }

                // read split dim, prefix, firstDiffByteDelta encoded as int:
                var code: Int = innerNodes.readVInt()
                val splitDim: Int = code % config.numIndexDims
                splitDimsPos[level] = splitDim * config.bytesPerDim
                code /= config.numIndexDims
                val prefix: Int = code % (1 + config.bytesPerDim)
                val suffix: Int = config.bytesPerDim - prefix

                if (suffix > 0) {
                    var firstDiffByteDelta: Int = code / (1 + config.bytesPerDim)
                    if (negativeDeltas[level * config.numIndexDims + splitDim]) {
                        firstDiffByteDelta = -firstDiffByteDelta
                    }
                    val startPos = splitDimsPos[level] + prefix
                    val oldByte = splitValuesStack[level]!![startPos].toInt() and 0xFF
                    splitValuesStack[level]!![startPos] = (oldByte + firstDiffByteDelta).toByte()
                    innerNodes.readBytes(splitValuesStack[level]!!, startPos + 1, suffix - 1)
                } else {
                    // our split value is == last split value in this dim, which can happen when there are
                    // many duplicate values
                }
                val leftNumBytes: Int = if (nodeID * 2 < leafNodeOffset) {
                    innerNodes.readVInt()
                } else {
                    0
                }
                rightNodePositions[level] = Math.toIntExact(innerNodes.filePointer) + leftNumBytes
                readNodeDataPositions[level] = Math.toIntExact(innerNodes.filePointer)
            }
        }

        fun getTreeDepth(numLeaves: Int): Int {
            // First +1 because all the non-leave nodes makes another power
            // of 2; e.g. to have a fully balanced tree with 4 leaves you
            // need a depth=3 tree:

            // Second +1 because MathUtil.log computes floor of the logarithm; e.g.
            // with 5 leaves you need a depth=4 tree:

            return MathUtil.log(numLeaves, 2) + 2
        }

        @Throws(IOException::class)
        fun visitDocValuesNoCardinality(
            commonPrefixLengths: IntArray,
            scratchDataPackedValue: ByteArray,
            scratchMinIndexPackedValue: ByteArray,
            scratchMaxIndexPackedValue: ByteArray,
            `in`: IndexInput,
            scratchIterator: BKDReaderDocIDSetIterator,
            count: Int,
            visitor: IntersectVisitor
        ) {
            readCommonPrefixes(commonPrefixLengths, scratchDataPackedValue, `in`)
            if (config.numIndexDims != 1 && version >= BKDWriter.VERSION_LEAF_STORES_BOUNDS) {
                val minPackedValue = scratchMinIndexPackedValue
                System.arraycopy(
                    scratchDataPackedValue, 0, minPackedValue, 0, config.packedIndexBytesLength()
                )
                val maxPackedValue = scratchMaxIndexPackedValue
                // Copy common prefixes before reading adjusted box
                System.arraycopy(minPackedValue, 0, maxPackedValue, 0, config.packedIndexBytesLength())
                readMinMax(commonPrefixLengths, minPackedValue, maxPackedValue, `in`)

                // The index gives us range of values for each dimension, but the actual range of values
                // might be much more narrow than what the index told us, so we double check the relation
                // here, which is cheap yet might help figure out that the block either entirely matches
                // or does not match at all. This is especially more likely in the case that there are
                // multiple dimensions that have correlation, ie. splitting on one dimension also
                // significantly changes the range of values in another dimension.
                val r: Relation = visitor.compare(minPackedValue, maxPackedValue)
                if (r === Relation.CELL_OUTSIDE_QUERY) {
                    return
                }
                visitor.grow(count)
                if (r === Relation.CELL_INSIDE_QUERY) {
                    for (i in 0..<count) {
                        visitor.visit(scratchIterator.docIDs[i])
                    }
                    return
                }
            } else {
                visitor.grow(count)
            }

            val compressedDim = readCompressedDim(`in`)

            if (compressedDim == -1) {
                visitUniqueRawDocValues(scratchDataPackedValue, scratchIterator, count, visitor)
            } else {
                visitCompressedDocValues(
                    commonPrefixLengths,
                    scratchDataPackedValue,
                    `in`,
                    scratchIterator,
                    count,
                    visitor,
                    compressedDim
                )
            }
        }

        @Throws(IOException::class)
        fun visitDocValuesWithCardinality(
            commonPrefixLengths: IntArray,
            scratchDataPackedValue: ByteArray,
            scratchMinIndexPackedValue: ByteArray,
            scratchMaxIndexPackedValue: ByteArray,
            `in`: IndexInput,
            scratchIterator: BKDReaderDocIDSetIterator,
            count: Int,
            visitor: IntersectVisitor
        ) {
            readCommonPrefixes(commonPrefixLengths, scratchDataPackedValue, `in`)
            val compressedDim = readCompressedDim(`in`)
            if (compressedDim == -1) {
                // all values are the same
                visitor.grow(count)
                visitUniqueRawDocValues(scratchDataPackedValue, scratchIterator, count, visitor)
            } else {
                if (config.numIndexDims != 1) {
                    val minPackedValue = scratchMinIndexPackedValue
                    System.arraycopy(
                        scratchDataPackedValue, 0, minPackedValue, 0, config.packedIndexBytesLength()
                    )
                    val maxPackedValue = scratchMaxIndexPackedValue
                    // Copy common prefixes before reading adjusted box
                    System.arraycopy(minPackedValue, 0, maxPackedValue, 0, config.packedIndexBytesLength())
                    readMinMax(commonPrefixLengths, minPackedValue, maxPackedValue, `in`)

                    // The index gives us range of values for each dimension, but the actual range of values
                    // might be much more narrow than what the index told us, so we double check the relation
                    // here, which is cheap yet might help figure out that the block either entirely matches
                    // or does not match at all. This is especially more likely in the case that there are
                    // multiple dimensions that have correlation, ie. splitting on one dimension also
                    // significantly changes the range of values in another dimension.
                    val r: Relation = visitor.compare(minPackedValue, maxPackedValue)
                    if (r === Relation.CELL_OUTSIDE_QUERY) {
                        return
                    }
                    visitor.grow(count)

                    if (r === Relation.CELL_INSIDE_QUERY) {
                        for (i in 0..<count) {
                            visitor.visit(scratchIterator.docIDs[i])
                        }
                        return
                    }
                } else {
                    visitor.grow(count)
                }

                if (compressedDim == -2) {
                    // low cardinality values
                    visitSparseRawDocValues(
                        commonPrefixLengths, scratchDataPackedValue, `in`, scratchIterator, count, visitor
                    )
                } else {
                    // high cardinality
                    visitCompressedDocValues(
                        commonPrefixLengths,
                        scratchDataPackedValue,
                        `in`,
                        scratchIterator,
                        count,
                        visitor,
                        compressedDim
                    )
                }
            }
        }

        @Throws(IOException::class)
        fun readMinMax(
            commonPrefixLengths: IntArray, minPackedValue: ByteArray, maxPackedValue: ByteArray, `in`: IndexInput
        ) {
            for (dim in 0..<config.numIndexDims) {
                val prefix = commonPrefixLengths[dim]
                `in`.readBytes(
                    minPackedValue, dim * config.bytesPerDim + prefix, config.bytesPerDim - prefix
                )
                `in`.readBytes(
                    maxPackedValue, dim * config.bytesPerDim + prefix, config.bytesPerDim - prefix
                )
            }
        }

        // read cardinality and point
        @Throws(IOException::class)
        fun visitSparseRawDocValues(
            commonPrefixLengths: IntArray,
            scratchPackedValue: ByteArray,
            `in`: IndexInput,
            scratchIterator: BKDReaderDocIDSetIterator,
            count: Int,
            visitor: IntersectVisitor
        ) {
            var i = 0
            while (i < count) {
                val length: Int = `in`.readVInt()
                for (dim in 0..<config.numDims) {
                    val prefix = commonPrefixLengths[dim]
                    `in`.readBytes(
                        scratchPackedValue,
                        dim * config.bytesPerDim + prefix,
                        config.bytesPerDim - prefix
                    )
                }
                scratchIterator.reset(i, length)
                visitor.visit(scratchIterator, scratchPackedValue)
                i += length
            }
            if (i != count) {
                throw CorruptIndexException(
                    "Sub blocks do not add up to the expected count: $count != $i", `in`
                )
            }
        }

        // point is under commonPrefix
        @Throws(IOException::class)
        fun visitUniqueRawDocValues(
            scratchPackedValue: ByteArray,
            scratchIterator: BKDReaderDocIDSetIterator,
            count: Int,
            visitor: IntersectVisitor
        ) {
            scratchIterator.reset(0, count)
            visitor.visit(scratchIterator, scratchPackedValue)
        }

        @Throws(IOException::class)
        fun visitCompressedDocValues(
            commonPrefixLengths: IntArray,
            scratchPackedValue: ByteArray,
            `in`: IndexInput,
            scratchIterator: BKDReaderDocIDSetIterator,
            count: Int,
            visitor: IntersectVisitor,
            compressedDim: Int
        ) {
            // the byte at `compressedByteOffset` is compressed using run-length compression,
            // other suffix bytes are stored verbatim
            val compressedByteOffset: Int =
                compressedDim * config.bytesPerDim + commonPrefixLengths[compressedDim]
            commonPrefixLengths[compressedDim]++
            var i = 0
            while (i < count) {
                scratchPackedValue[compressedByteOffset] = `in`.readByte()
                val runLen: Int = Byte.toUnsignedInt(`in`.readByte())
                for (j in 0..<runLen) {
                    for (dim in 0..<config.numDims) {
                        val prefix = commonPrefixLengths[dim]
                        `in`.readBytes(
                            scratchPackedValue,
                            dim * config.bytesPerDim + prefix,
                            config.bytesPerDim - prefix
                        )
                    }
                    visitor.visit(scratchIterator.docIDs[i + j], scratchPackedValue)
                }
                i += runLen
            }
            if (i != count) {
                throw CorruptIndexException(
                    "Sub blocks do not add up to the expected count: $count != $i", `in`
                )
            }
        }

        @Throws(IOException::class)
        fun readCompressedDim(`in`: IndexInput): Int {
            val compressedDim: Int = `in`.readByte().toInt()
            if (compressedDim < -2 || compressedDim >= config.numDims || (version < BKDWriter.VERSION_LOW_CARDINALITY_LEAVES && compressedDim == -2)) {
                throw CorruptIndexException("Got compressedDim=$compressedDim", `in`)
            }
            return compressedDim
        }

        @Throws(IOException::class)
        fun readCommonPrefixes(
            commonPrefixLengths: IntArray, scratchPackedValue: ByteArray, `in`: IndexInput
        ) {
            for (dim in 0..<config.numDims) {
                val prefix: Int = `in`.readVInt()
                commonPrefixLengths[dim] = prefix
                if (prefix > 0) {
                    `in`.readBytes(scratchPackedValue, dim * config.bytesPerDim, prefix)
                }
                // System.out.println("R: " + dim + " of " + numDims + " prefix=" + prefix);
            }
        }

        override fun toString(): String {
            return "nodeID=$nodeID"
        }
    }

    override val numDimensions: Int
        get() = config.numDims

    override val numIndexDimensions: Int
        get() = config.numIndexDims

    override val bytesPerDimension: Int
        get() = config.bytesPerDim

    override fun size(): Long {
        return pointCount
    }

    /** Reusable [DocIdSetIterator] to handle low cardinality leaves.  */
    private class BKDReaderDocIDSetIterator(maxPointsInLeafNode: Int) : DocIdSetIterator() {
        private var idx = 0
        private var length = 0
        private var offset = 0
        private var docID = 0
        val docIDs: IntArray = IntArray(maxPointsInLeafNode)
        val docIdsWriter: DocIdsWriter = DocIdsWriter(maxPointsInLeafNode)

        override fun docID(): Int {
            return docID
        }

        fun reset(offset: Int, length: Int) {
            this.offset = offset
            this.length = length
            require(offset + length <= docIDs.size)
            this.docID = -1
            this.idx = 0
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            if (idx == length) {
                docID = NO_MORE_DOCS
            } else {
                docID = docIDs[offset + idx]
                idx++
            }
            return docID
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return slowAdvance(target)
        }

        override fun cost(): Long {
            return length.toLong()
        }
    }
}
