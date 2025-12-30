package org.gnit.lucenekmp.analysis.cn.smart.hhmm

import org.gnit.lucenekmp.analysis.cn.smart.Utility
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap

/**
 * Graph representing possible token pairs (bigrams) at each start offset in the sentence.
 *
 * @lucene.experimental
 */
class BiSegGraph(segGraph: SegGraph) {
    private val tokenPairListTable: IntObjectHashMap<ArrayList<SegTokenPair>> = IntObjectHashMap()
    private var segTokenList: List<SegToken> = emptyList()

    private val bigramDict: BigramDictionary = BigramDictionary.getInstance()

    init {
        segTokenList = segGraph.makeIndex()
        generateBiSegGraph(segGraph)
    }

    private fun generateBiSegGraph(segGraph: SegGraph) {
        val smooth = 0.1
        var wordPairFreq = 0
        val maxStart = segGraph.getMaxStart()
        val tinyDouble = 1.0 / Utility.MAX_FREQUENCE

        segTokenList = segGraph.makeIndex()
        var key = -1
        while (key < maxStart) {
            if (segGraph.isStartExist(key)) {
                val tokenList = segGraph.getStartList(key) ?: emptyList()
                for (t1 in tokenList) {
                    val oneWordFreq = t1.weight.toDouble()
                    var next = t1.endOffset
                    var nextTokens: List<SegToken>? = null
                    while (next <= maxStart) {
                        if (segGraph.isStartExist(next)) {
                            nextTokens = segGraph.getStartList(next)
                            break
                        }
                        next++
                    }
                    if (nextTokens == null) {
                        break
                    }
                    for (t2 in nextTokens) {
                        val idBuffer = CharArray(t1.charArray.size + t2.charArray.size + 1)
                        t1.charArray.copyInto(idBuffer, 0, 0, t1.charArray.size)
                        idBuffer[t1.charArray.size] = BigramDictionary.WORD_SEGMENT_CHAR
                        t2.charArray.copyInto(idBuffer, t1.charArray.size + 1, 0, t2.charArray.size)

                        wordPairFreq = bigramDict.getFrequency(idBuffer)

                        val weight = -kotlin.math.ln(
                            smooth * (1.0 + oneWordFreq) / (Utility.MAX_FREQUENCE + 0.0) +
                                (1.0 - smooth) * ((1.0 - tinyDouble) * wordPairFreq / (1.0 + oneWordFreq) + tinyDouble)
                        )

                        val tokenPair = SegTokenPair(idBuffer, t1.index, t2.index, weight)
                        addSegTokenPair(tokenPair)
                    }
                }
            }
            key++
        }
    }

    fun isToExist(to: Int): Boolean {
        return tokenPairListTable[to] != null
    }

    fun getToList(to: Int): List<SegTokenPair> {
        return tokenPairListTable[to] ?: emptyList()
    }

    fun addSegTokenPair(tokenPair: SegTokenPair) {
        val to = tokenPair.to
        val list = tokenPairListTable[to]
        if (list == null) {
            val newList = ArrayList<SegTokenPair>()
            newList.add(tokenPair)
            tokenPairListTable.put(to, newList)
        } else {
            list.add(tokenPair)
        }
    }

    fun getToCount(): Int {
        return tokenPairListTable.size()
    }

    fun getShortPath(): List<SegToken> {
        val nodeCount = getToCount()
        val path = ArrayList<PathNode>()
        val zeroPath = PathNode()
        zeroPath.weight = 0.0
        zeroPath.preNode = 0
        path.add(zeroPath)

        for (current in 1..nodeCount) {
            val edges = getToList(current)
            var minWeight = Double.MAX_VALUE
            var minEdge: SegTokenPair? = null
            for (edge in edges) {
                val preNode = path[edge.from]
                val weight = edge.weight
                if (preNode.weight + weight < minWeight) {
                    minWeight = preNode.weight + weight
                    minEdge = edge
                }
            }
            val newNode = PathNode()
            newNode.weight = minWeight
            newNode.preNode = minEdge?.from ?: 0
            path.add(newNode)
        }

        val lastNode = path.size - 1
        var current = lastNode
        val rpath = IntArrayList()
        val resultPath = ArrayList<SegToken>()

        rpath.add(current)
        while (current != 0) {
            val currentPathNode = path[current]
            val preNode = currentPathNode.preNode
            rpath.add(preNode)
            current = preNode
        }
        for (j in rpath.size() - 1 downTo 0) {
            val id = rpath.get(j)
            val t = segTokenList[id]
            resultPath.add(t)
        }
        return resultPath
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (cursor in tokenPairListTable.values()) {
            val list: ArrayList<SegTokenPair>? = cursor?.value
            if (list != null) {
                for (pair in list) {
                    sb.append(pair).append("\n")
                }
            }
        }
        return sb.toString()
    }
}
