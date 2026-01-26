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
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.MathUtil
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.bkd.BKDConfig
import org.gnit.lucenekmp.util.bkd.BKDReader

/** Forked from [BKDReader] and simplified/specialized for SimpleText's usage */
internal class SimpleTextBKDReader(
    val `in`: IndexInput,
    numDims: Int,
    numIndexDims: Int,
    maxPointsInLeafNode: Int,
    bytesPerDim: Int,
    val leafBlockFPs: LongArray,
    private val splitPackedValues: ByteArray,
    override val minPackedValue: ByteArray,
    override val maxPackedValue: ByteArray,
    val pointCount: Long,
    override val docCount: Int
) : PointValues() {

    // Packed array of byte[] holding all split values in the full binary tree:
    private val leafNodeOffset: Int
    val config: BKDConfig
    val bytesPerIndexEntry: Int
    val version: Int

    init {
        config = BKDConfig(numDims, numIndexDims, bytesPerDim, maxPointsInLeafNode)
        // no version check here because callers of this API (SimpleText) have no back compat:
        bytesPerIndexEntry = if (numIndexDims == 1) bytesPerDim else bytesPerDim + 1
        this.leafNodeOffset = leafBlockFPs.size
        this.version = SimpleTextBKDWriter.VERSION_CURRENT
        assert(minPackedValue.size == config.packedIndexBytesLength())
        assert(maxPackedValue.size == config.packedIndexBytesLength())
    }

    override val pointTree
        get(): PointTree {
            return SimpleTextPointTree(`in`.clone(), 1, 1, minPackedValue, maxPackedValue)
        }

    private inner class SimpleTextPointTree(
        private val `in`: IndexInput,
        nodeID: Int,
        level: Int,
        minPackedValue: ByteArray,
        maxPackedValue: ByteArray
    ) : PointTree {

        val scratchDocIDs: IntArray
        val scratchPackedValue: ByteArray
        var nodeID: Int
        var level: Int
        val rootNode: Int

        // holds the min / max value of the current node.
        override val minPackedValue: ByteArray
            get() {
                return field.copyOf()
            }

        override val maxPackedValue: ByteArray
            get() {
                return field.copyOf()
            }


        // holds the previous value of the split dimension
        private val splitDimValueStack: Array<ByteArray?>

        // holds the splitDim for each level:
        private val splitDims: IntArray

        init {
            scratchDocIDs = IntArray(config.maxPointsInLeafNode)
            scratchPackedValue = ByteArray(config.packedBytesLength())
            this.nodeID = nodeID
            this.rootNode = nodeID
            this.level = level
            this.maxPackedValue = maxPackedValue.copyOf()
            this.minPackedValue = minPackedValue.copyOf()
            val treeDepth = getTreeDepth(leafNodeOffset)
            splitDimValueStack = arrayOfNulls(treeDepth + 1)
            splitDims = IntArray(treeDepth + 1)
        }

        private fun getTreeDepth(numLeaves: Int): Int {
            // First +1 because all the non-leave nodes makes another power
            // of 2; e.g. to have a fully balanced tree with 4 leaves you
            // need a depth=3 tree:

            // Second +1 because MathUtil.log computes floor of the logarithm; e.g.
            // with 5 leaves you need a depth=4 tree:
            return MathUtil.log(numLeaves, 2) + 2
        }

        override fun clone(): PointTree {
            val index =
                SimpleTextPointTree(`in`.clone(), nodeID, level, minPackedValue, maxPackedValue)
            if (!isLeafNode()) {
                // copy node data
                index.splitDims[level] = splitDims[level]
                index.splitDimValueStack[level] = splitDimValueStack[level]
            }
            return index
        }

        override fun moveToChild(): Boolean {
            if (isLeafNode()) {
                return false
            }
            pushLeft()
            return true
        }

        private fun pushLeft() {
            var address = nodeID * bytesPerIndexEntry
            if (config.numIndexDims == 1) {
                splitDims[level] = 0
            } else {
                splitDims[level] = splitPackedValues[address++].toInt() and 0xff
            }
            val splitDimPos = splitDims[level] * config.bytesPerDim
            if (splitDimValueStack[level] == null) {
                splitDimValueStack[level] = ByteArray(config.bytesPerDim)
            }
            // save the dimension we are going to change
            maxPackedValue.copyInto(
                requireNotNull(splitDimValueStack[level]),
                destinationOffset = 0,
                startIndex = splitDimPos,
                endIndex = splitDimPos + config.bytesPerDim
            )
            assert(
                Arrays.compareUnsigned(
                    maxPackedValue,
                    splitDimPos,
                    splitDimPos + config.bytesPerDim,
                    splitPackedValues,
                    address,
                    address + config.bytesPerDim
                ) >= 0
            ) {
                "config.bytesPerDim=${config.bytesPerDim} splitDim=${splitDims[level]} config.numIndexDims=${config.numIndexDims} config.numDims=${config.numDims}"
            }
            nodeID *= 2
            level++
            // add the split dim value:
            splitPackedValues.copyInto(
                maxPackedValue,
                destinationOffset = splitDimPos,
                startIndex = address,
                endIndex = address + config.bytesPerDim
            )
        }

        override fun moveToSibling(): Boolean {
            if (nodeID != rootNode && (nodeID and 1) == 0) {
                pop(true)
                pushRight()
                return true
            }
            return false
        }

        private fun pushRight() {
            var address = nodeID * bytesPerIndexEntry
            if (config.numIndexDims == 1) {
                splitDims[level] = 0
            } else {
                splitDims[level] = splitPackedValues[address++].toInt() and 0xff
            }
            val splitDimPos = splitDims[level] * config.bytesPerDim
            // we should have already visit the left node
            assert(splitDimValueStack[level] != null)
            // save the dimension we are going to change
            minPackedValue.copyInto(
                requireNotNull(splitDimValueStack[level]),
                destinationOffset = 0,
                startIndex = splitDimPos,
                endIndex = splitDimPos + config.bytesPerDim
            )
            assert(
                Arrays.compareUnsigned(
                    minPackedValue,
                    splitDimPos,
                    splitDimPos + config.bytesPerDim,
                    splitPackedValues,
                    address,
                    address + config.bytesPerDim
                ) <= 0
            ) {
                "config.bytesPerDim=${config.bytesPerDim} splitDim=${splitDims[level]} config.numIndexDims=${config.numIndexDims} config.numDims=${config.numDims}"
            }
            nodeID = 2 * nodeID + 1
            level++
            // add the split dim value:
            splitPackedValues.copyInto(
                minPackedValue,
                destinationOffset = splitDimPos,
                startIndex = address,
                endIndex = address + config.bytesPerDim
            )
        }

        override fun moveToParent(): Boolean {
            if (nodeID == rootNode) {
                return false
            }
            pop((nodeID and 1) == 0)
            return true
        }

        private fun pop(isLeft: Boolean) {
            nodeID /= 2
            level--
            // restore the split dimension
            if (isLeft) {
                requireNotNull(splitDimValueStack[level]).copyInto(
                    maxPackedValue,
                    destinationOffset = splitDims[level] * config.bytesPerDim,
                    startIndex = 0,
                    endIndex = config.bytesPerDim
                )
            } else {
                requireNotNull(splitDimValueStack[level]).copyInto(
                    minPackedValue,
                    destinationOffset = splitDims[level] * config.bytesPerDim,
                    startIndex = 0,
                    endIndex = config.bytesPerDim
                )
            }
        }

        override fun size(): Long {
            var leftMostLeafNode = nodeID
            while (leftMostLeafNode < leafNodeOffset) {
                leftMostLeafNode *= 2
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
            assert(numLeaves == getNumLeavesSlow(nodeID)) { "$numLeaves ${getNumLeavesSlow(nodeID)}" }
            return sizeFromBalancedTree(leftMostLeafNode, rightMostLeafNode)
        }

        private fun sizeFromBalancedTree(leftMostLeafNode: Int, rightMostLeafNode: Int): Long {
            // number of points that need to be distributed between leaves, one per leaf
            val extraPoints =
                Math.toIntExact(
                    (config.maxPointsInLeafNode.toLong() * leafNodeOffset) - pointCount
                )
            assert(extraPoints < leafNodeOffset) { "point excess should be lower than leafNodeOffset" }
            // offset where we stop adding one point to the leaves
            val nodeOffset = leafNodeOffset - extraPoints
            var count = 0L
            for (node in leftMostLeafNode..rightMostLeafNode) {
                // offsetPosition provides which extra point will be added to this node
                if (balanceTreeNodePosition(0, leafNodeOffset, node - leafNodeOffset, 0, 0) < nodeOffset) {
                    count += config.maxPointsInLeafNode.toLong()
                } else {
                    count += (config.maxPointsInLeafNode - 1).toLong()
                }
            }
            return count
        }

        private fun balanceTreeNodePosition(
            minNode: Int,
            maxNode: Int,
            node: Int,
            position: Int,
            level: Int
        ): Int {
            return if (maxNode - minNode == 1) {
                position
            } else {
                val mid = (minNode + maxNode + 1) ushr 1
                if (mid > node) {
                    balanceTreeNodePosition(minNode, mid, node, position, level + 1)
                } else {
                    balanceTreeNodePosition(mid, maxNode, node, position + (1 shl level), level + 1)
                }
            }
        }

        private fun getNumLeavesSlow(node: Int): Int {
            return if (node >= 2 * leafNodeOffset) {
                0
            } else if (node >= leafNodeOffset) {
                1
            } else {
                val leftCount = getNumLeavesSlow(node * 2)
                val rightCount = getNumLeavesSlow(node * 2 + 1)
                leftCount + rightCount
            }
        }

        @Throws(IOException::class)
        override fun visitDocIDs(visitor: PointValues.IntersectVisitor) {
            addAll(visitor, false)
        }

        @Throws(IOException::class)
        fun addAll(visitor: PointValues.IntersectVisitor, grown: Boolean) {
            var grownLocal = grown
            if (!grownLocal) {
                val size = size()
                if (size <= Int.MAX_VALUE.toLong()) {
                    visitor.grow(size.toInt())
                    grownLocal = true
                }
            }
            if (isLeafNode()) {
                // Leaf node
                val scratch = BytesRefBuilder()
                `in`.seek(leafBlockFPs[nodeID - leafNodeOffset])
                readLine(`in`, scratch)
                val count = parseInt(scratch, SimpleTextPointsWriter.BLOCK_COUNT)
                for (i in 0 until count) {
                    readLine(`in`, scratch)
                    visitor.visit(parseInt(scratch, SimpleTextPointsWriter.BLOCK_DOC_ID))
                }
            } else {
                pushLeft()
                addAll(visitor, grownLocal)
                pop(true)
                pushRight()
                addAll(visitor, grownLocal)
                pop(false)
            }
        }

        @Throws(IOException::class)
        override fun visitDocValues(visitor: PointValues.IntersectVisitor) {
            if (isLeafNode()) {
                // Leaf node
                val leafID = nodeID - leafNodeOffset

                // Leaf node; scan and filter all points in this block:
                val count = readDocIDs(`in`, leafBlockFPs[leafID], scratchDocIDs)

                // Again, this time reading values and checking with the visitor
                visitor.grow(count)
                // NOTE: we don't do prefix coding, so we ignore commonPrefixLengths
                assert(scratchPackedValue.size == config.packedBytesLength())
                val scratch = BytesRefBuilder()
                for (i in 0 until count) {
                    readLine(`in`, scratch)
                    assert(startsWith(scratch, SimpleTextPointsWriter.BLOCK_VALUE))
                    val br =
                        SimpleTextUtil.fromBytesRefString(
                            stripPrefix(scratch, SimpleTextPointsWriter.BLOCK_VALUE)
                        )
                    assert(br.length == config.packedBytesLength())
                    br.bytes.copyInto(
                        scratchPackedValue,
                        destinationOffset = 0,
                        startIndex = br.offset,
                        endIndex = br.offset + config.packedBytesLength()
                    )
                    visitor.visit(scratchDocIDs[i], scratchPackedValue)
                }
            } else {
                pushLeft()
                visitDocValues(visitor)
                pop(true)
                pushRight()
                visitDocValues(visitor)
                pop(false)
            }
        }

        @Throws(IOException::class)
        fun readDocIDs(`in`: IndexInput, blockFP: Long, docIDs: IntArray): Int {
            val scratch = BytesRefBuilder()
            `in`.seek(blockFP)
            readLine(`in`, scratch)
            val count = parseInt(scratch, SimpleTextPointsWriter.BLOCK_COUNT)
            for (i in 0 until count) {
                readLine(`in`, scratch)
                docIDs[i] = parseInt(scratch, SimpleTextPointsWriter.BLOCK_DOC_ID)
            }
            return count
        }

        fun isLeafNode(): Boolean {
            return nodeID >= leafNodeOffset
        }

        private fun parseInt(scratch: BytesRefBuilder, prefix: BytesRef): Int {
            assert(startsWith(scratch, prefix))
            return stripPrefix(scratch, prefix).toInt()
        }

        private fun stripPrefix(scratch: BytesRefBuilder, prefix: BytesRef): String {
            return String.fromByteArray(
                scratch.bytes().copyOfRange(prefix.length, scratch.length()),
                StandardCharsets.UTF_8
            )
        }

        private fun startsWith(scratch: BytesRefBuilder, prefix: BytesRef): Boolean {
            return StringHelper.startsWith(scratch.get(), prefix)
        }

        @Throws(IOException::class)
        private fun readLine(`in`: IndexInput, scratch: BytesRefBuilder) {
            SimpleTextUtil.readLine(`in`, scratch)
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
}
