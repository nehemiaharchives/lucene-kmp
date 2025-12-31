package org.gnit.lucenekmp.analysis.morph

import okio.IOException
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.internal.hppc.IntIntHashMap
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.fst.FST

/** [Viterbi] subclass for n-best path calculation. */
abstract class ViterbiNBest<T : Token, U : MorphData>(
    fst: TokenInfoFST,
    fstReader: FST.BytesReader,
    dictionary: BinaryDictionary<out MorphData>,
    userFST: TokenInfoFST?,
    userFSTReader: FST.BytesReader?,
    userDictionary: Dictionary<out MorphData>?,
    costs: ConnectionCosts
) : Viterbi<T, ViterbiNBest.PositionNBest>(
    fst,
    fstReader,
    dictionary,
    userFST,
    userFSTReader,
    userDictionary,
    costs,
    { PositionNBest() }
) {

    protected val dictionaryMap: MutableMap<TokenType, Dictionary<out U>> = mutableMapOf()

    private var nBestCost: Int = 0

    protected var lattice: Lattice<U>? = null

    @Throws(IOException::class)
    override fun backtraceNBest(endPosData: Position, useEOS: Boolean) {
        if (lattice == null) {
            lattice = Lattice()
        }

        val endPos = endPosData.pos
        val fragment = buffer.get(lastBackTracePos, endPos - lastBackTracePos)
        lattice!!.setup(fragment, dictionaryMap, positions, lastBackTracePos, endPos, useEOS)
        lattice!!.markUnreachable()
        lattice!!.calcLeftCost(costs)
        lattice!!.calcRightCost(costs)

        val bestCost = lattice!!.bestCost()
        if (VERBOSE) {
            println("DEBUG: 1-BEST COST: $bestCost")
        }
        for (node in lattice!!.bestPathNodeList()) {
            registerNode(node.value, fragment)
        }

        var n = 2
        while (true) {
            val nbest = lattice!!.nBestNodeList(n)
            if (nbest.size() == 0) {
                break
            }
            val cost = lattice!!.cost(nbest.get(0))
            if (VERBOSE) {
                println("DEBUG: ${n}-BEST COST: $cost")
            }
            if (bestCost + nBestCost < cost) {
                break
            }
            for (node in nbest) {
                registerNode(node.value, fragment)
            }
            n++
        }
        if (VERBOSE) {
            lattice!!.debugPrint()
        }
    }

    /** Add n-best tokens to the pending list. */
    protected abstract fun registerNode(node: Int, fragment: CharArray)

    override fun fixupPendingList() {
        pending.sortWith { a, b ->
            val aOff = a.offset
            val bOff = b.offset
            if (aOff != bOff) return@sortWith aOff - bOff
            val aLen = a.length
            val bLen = b.length
            if (aLen != bLen) return@sortWith aLen - bLen
            b.type.ordinal - a.type.ordinal
        }

        var i = 1
        while (i < pending.size) {
            val a = pending[i - 1]
            val b = pending[i]
            if (a.offset == b.offset && a.length == b.length) {
                pending.removeAt(i)
                i--
            }
            i++
        }

        val map = IntIntHashMap()
        for (t in pending) {
            map.put(t.offset, 0)
            map.put(t.offset + t.length, 0)
        }

        val offsets = map.keys().toArray()
        Arrays.sort(offsets)

        for (index in offsets.indices) {
            map.put(offsets[index], index)
        }

        for (t in pending) {
            t.positionLength = map.get(t.offset + t.length) - map.get(t.offset)
        }

        pending.reverse()
    }

    protected open fun setNBestCost(value: Int) {
        nBestCost = value
        outputNBest = 0 < nBestCost
    }

    protected open fun getNBestCost(): Int = nBestCost

    fun getLatticeRootBase(): Int = lattice?.getRootBase() ?: 0

    fun probeDelta(start: Int, end: Int): Int = lattice?.probeDelta(start, end) ?: 0

    /** [Viterbi.Position] extension; this holds all forward pointers to calculate n-best path. */
    class PositionNBest : Viterbi.Position() {
        var forwardCount: Int = 0
        var forwardPos: IntArray = IntArray(8)
        var forwardID: IntArray = IntArray(8)
        var forwardIndex: IntArray = IntArray(8)
        var forwardType: Array<TokenType> = Array(8) { TokenType.KNOWN }

        private fun growForward() {
            forwardPos = ArrayUtil.grow(forwardPos, 1 + forwardCount)
            forwardID = ArrayUtil.grow(forwardID, 1 + forwardCount)
            forwardIndex = ArrayUtil.grow(forwardIndex, 1 + forwardCount)
            val newForwardType = Array(forwardPos.size) { TokenType.KNOWN }
            for (i in forwardType.indices) {
                newForwardType[i] = forwardType[i]
            }
            forwardType = newForwardType
        }

        fun addForward(forwardPos: Int, forwardIndex: Int, forwardID: Int, forwardType: TokenType) {
            if (forwardCount == this.forwardID.size) {
                growForward()
            }
            this.forwardPos[forwardCount] = forwardPos
            this.forwardIndex[forwardCount] = forwardIndex
            this.forwardID[forwardCount] = forwardID
            this.forwardType[forwardCount] = forwardType
            forwardCount++
        }

        // Position.reset() is final in this port, so we don't override it here.

        fun getForwardType(index: Int): TokenType = forwardType[index]

        fun getForwardID(index: Int): Int = forwardID[index]

        fun getForwardPos(index: Int): Int = forwardPos[index]
    }

    /** Yet another lattice data structure for keeping n-best path. */
    protected class Lattice<U : MorphData> {
        private lateinit var fragment: CharArray
        private lateinit var dictionaryMap: Map<TokenType, Dictionary<out U>>
        private var useEOS: Boolean = false

        private var rootCapacity = 0
        private var rootSize = 0
        private var rootBase = 0

        private lateinit var lRoot: IntArray
        private lateinit var rRoot: IntArray

        private var capacity = 0
        private var nodeCount = 0

        private lateinit var nodeDicType: Array<TokenType>
        private lateinit var nodeWordID: IntArray
        private lateinit var nodeMark: IntArray
        private lateinit var nodeLeftID: IntArray
        private lateinit var nodeRightID: IntArray
        private lateinit var nodeWordCost: IntArray
        private lateinit var nodeLeftCost: IntArray
        private lateinit var nodeRightCost: IntArray
        private lateinit var nodeLeftNode: IntArray
        private lateinit var nodeRightNode: IntArray
        private lateinit var nodeLeft: IntArray
        private lateinit var nodeRight: IntArray
        private lateinit var nodeLeftChain: IntArray
        private lateinit var nodeRightChain: IntArray

        fun getNodeLeft(node: Int): Int = nodeLeft[node]

        fun getNodeRight(node: Int): Int = nodeRight[node]

        fun getNodeDicType(node: Int): TokenType = nodeDicType[node]

        fun getNodeWordID(node: Int): Int = nodeWordID[node]

        fun getRootBase(): Int = rootBase

        private fun setupRoot(baseOffset: Int, lastOffset: Int) {
            val size = lastOffset - baseOffset + 1
            if (rootCapacity < size) {
                val oversize = ArrayUtil.oversize(size, Int.SIZE_BYTES)
                lRoot = IntArray(oversize)
                rRoot = IntArray(oversize)
                rootCapacity = oversize
            }
            lRoot.fill(-1, 0, size)
            rRoot.fill(-1, 0, size)
            rootSize = size
            rootBase = baseOffset
        }

        private fun reserve(n: Int) {
            if (capacity < n) {
                val oversize = ArrayUtil.oversize(n, Int.SIZE_BYTES)
                nodeDicType = Array(oversize) { TokenType.KNOWN }
                nodeWordID = IntArray(oversize)
                nodeMark = IntArray(oversize)
                nodeLeftID = IntArray(oversize)
                nodeRightID = IntArray(oversize)
                nodeWordCost = IntArray(oversize)
                nodeLeftCost = IntArray(oversize)
                nodeRightCost = IntArray(oversize)
                nodeLeftNode = IntArray(oversize)
                nodeRightNode = IntArray(oversize)
                nodeLeft = IntArray(oversize)
                nodeRight = IntArray(oversize)
                nodeLeftChain = IntArray(oversize)
                nodeRightChain = IntArray(oversize)
                capacity = oversize
            }
        }

        private fun setupNodePool(n: Int) {
            reserve(n)
            nodeCount = 0
            if (VERBOSE) {
                println("DEBUG: setupNodePool: n = $n")
                println("DEBUG: setupNodePool: lattice.capacity = $capacity")
            }
        }

        private fun addNode(dicType: TokenType, wordID: Int, left: Int, right: Int): Int {
            val node = nodeCount++
            nodeDicType[node] = dicType
            nodeWordID[node] = wordID
            nodeMark[node] = 0

            if (wordID < 0) {
                nodeWordCost[node] = 0
                nodeLeftCost[node] = 0
                nodeRightCost[node] = 0
                nodeLeftID[node] = 0
                nodeRightID[node] = 0
            } else {
                val dic = dictionaryMap[dicType]!!
                nodeWordCost[node] = dic.getWordCost(wordID)
                nodeLeftID[node] = dic.getLeftId(wordID)
                nodeRightID[node] = dic.getRightId(wordID)
            }

            nodeLeft[node] = left
            nodeRight[node] = right
            if (0 <= left) {
                nodeLeftChain[node] = lRoot[left]
                lRoot[left] = node
            } else {
                nodeLeftChain[node] = -1
            }
            if (0 <= right) {
                nodeRightChain[node] = rRoot[right]
                rRoot[right] = node
            } else {
                nodeRightChain[node] = -1
            }
            return node
        }

        private fun positionCount(positions: Viterbi.WrappedPositionArray<PositionNBest>, beg: Int, end: Int): Int {
            var count = 0
            for (i in beg until end) {
                count += positions.get(i).count
            }
            return count
        }

        fun setup(
            fragment: CharArray,
            dictionaryMap: Map<TokenType, Dictionary<out U>>,
            positions: Viterbi.WrappedPositionArray<PositionNBest>,
            prevOffset: Int,
            endOffset: Int,
            useEOS: Boolean
        ) {
            this.fragment = fragment
            this.dictionaryMap = dictionaryMap
            this.useEOS = useEOS

            setupRoot(prevOffset, endOffset)
            setupNodePool(positionCount(positions, prevOffset + 1, endOffset + 1) + 2)

            val first = positions.get(prevOffset)
            addNode(first.backType[0], first.backID[0], -1, 0)
            addNode(TokenType.KNOWN, -1, endOffset - rootBase, -1)

            for (offset in endOffset downTo prevOffset + 1) {
                val right = offset - rootBase
                if (0 <= lRoot[right]) {
                    val pos = positions.get(offset)
                    for (i in 0 until pos.count) {
                        addNode(pos.backType[i], pos.backID[i], pos.backPos[i] - rootBase, right)
                    }
                }
            }
        }

        fun markUnreachable() {
            for (index in 1 until rootSize - 1) {
                if (rRoot[index] < 0) {
                    var node = lRoot[index]
                    while (node >= 0) {
                        nodeMark[node] = -1
                        node = nodeLeftChain[node]
                    }
                }
            }
        }

        private fun connectionCost(costs: ConnectionCosts, left: Int, right: Int): Int {
            val leftID = nodeLeftID[right]
            return if (leftID == 0 && !useEOS) 0 else costs.get(nodeRightID[left], leftID)
        }

        fun calcLeftCost(costs: ConnectionCosts) {
            for (index in 0 until rootSize) {
                var node = lRoot[index]
                while (node >= 0) {
                    if (nodeMark[node] >= 0) {
                        var leastNode = -1
                        var leastCost = Int.MAX_VALUE
                        var leftNode = rRoot[index]
                        while (leftNode >= 0) {
                            if (nodeMark[leftNode] >= 0) {
                                val cost = nodeLeftCost[leftNode] + nodeWordCost[leftNode] + connectionCost(costs, leftNode, node)
                                if (cost < leastCost) {
                                    leastCost = cost
                                    leastNode = leftNode
                                }
                            }
                            leftNode = nodeRightChain[leftNode]
                        }
                        nodeLeftNode[node] = leastNode
                        nodeLeftCost[node] = leastCost
                    }
                    node = nodeLeftChain[node]
                }
            }
        }

        fun calcRightCost(costs: ConnectionCosts) {
            for (index in rootSize - 1 downTo 0) {
                var node = rRoot[index]
                while (node >= 0) {
                    if (nodeMark[node] >= 0) {
                        var leastNode = -1
                        var leastCost = Int.MAX_VALUE
                        var rightNode = lRoot[index]
                        while (rightNode >= 0) {
                            if (nodeMark[rightNode] >= 0) {
                                val cost = nodeRightCost[rightNode] + nodeWordCost[rightNode] + connectionCost(costs, node, rightNode)
                                if (cost < leastCost) {
                                    leastCost = cost
                                    leastNode = rightNode
                                }
                            }
                            rightNode = nodeLeftChain[rightNode]
                        }
                        nodeRightNode[node] = leastNode
                        nodeRightCost[node] = leastCost
                    }
                    node = nodeRightChain[node]
                }
            }
        }

        private fun markSameSpanNode(refNode: Int, value: Int) {
            val left = nodeLeft[refNode]
            val right = nodeRight[refNode]
            var node = lRoot[left]
            while (node >= 0) {
                if (nodeRight[node] == right) {
                    nodeMark[node] = value
                }
                node = nodeLeftChain[node]
            }
        }

        fun bestPathNodeList(): IntArrayList {
            val list = IntArrayList()
            var node = nodeRightNode[0]
            while (node != 1) {
                list.add(node)
                markSameSpanNode(node, 1)
                node = nodeRightNode[node]
            }
            return list
        }

        fun cost(node: Int): Int = nodeLeftCost[node] + nodeWordCost[node] + nodeRightCost[node]

        fun nBestNodeList(n: Int): IntArrayList {
            val list = IntArrayList()
            var leastCost = Int.MAX_VALUE
            var leastLeft = -1
            var leastRight = -1
            for (node in 2 until nodeCount) {
                if (nodeMark[node] == 0) {
                    val cost = cost(node)
                    if (cost < leastCost) {
                        leastCost = cost
                        leastLeft = nodeLeft[node]
                        leastRight = nodeRight[node]
                        list.clear()
                        list.add(node)
                    } else if (cost == leastCost && (nodeLeft[node] != leastLeft || nodeRight[node] != leastRight)) {
                        list.add(node)
                    }
                }
            }
            for (cursor in list) {
                markSameSpanNode(cursor.value, n)
            }
            return list
        }

        fun bestCost(): Int = nodeLeftCost[1]

        fun probeDelta(start: Int, end: Int): Int {
            val left = start - rootBase
            val right = end - rootBase
            if (left < 0 || rootSize < right) {
                return Int.MAX_VALUE
            }
            var probedCost = Int.MAX_VALUE
            var node = lRoot[left]
            while (node >= 0) {
                if (nodeRight[node] == right) {
                    probedCost = minOf(probedCost, cost(node))
                }
                node = nodeLeftChain[node]
            }
            return probedCost - bestCost()
        }

        fun debugPrint() {
            if (VERBOSE) {
                for (node in 0 until nodeCount) {
                    println("DEBUG NODE: node=$node, mark=${nodeMark[node]}, cost=${cost(node)}, left=${nodeLeft[node]}, right=${nodeRight[node]}")
                }
            }
        }
    }
}
