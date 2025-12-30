package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.jdkport.fromCharArray

/** Outputs the dot (graphviz) string for the viterbi lattice. */
class GraphvizFormatter<T : MorphData>(private val costs: ConnectionCosts) {
    companion object {
        private const val BOS_LABEL = "BOS"
        private const val EOS_LABEL = "EOS"
        private const val FONT_NAME = "Helvetica"
    }

    private val bestPathMap: MutableMap<String, String> = HashMap()
    private val sb = StringBuilder()

    init {
        sb.append(formatHeader())
        sb.append("  init [style=invis]\n")
        sb.append("  init -> 0.0 [label=\"$BOS_LABEL\"]\n")
    }

    fun finish(): String {
        sb.append(formatTrailer())
        return sb.toString()
    }

    fun onBacktrace(
        dictProvider: DictionaryProvider<T>,
        positions: Viterbi.WrappedPositionArray<out Viterbi.Position>,
        lastBackTracePos: Int,
        endPosData: Viterbi.Position,
        fromIDX: Int,
        fragment: CharArray,
        isEnd: Boolean
    ) {
        setBestPathMap(positions, lastBackTracePos, endPosData, fromIDX)
        sb.append(formatNodes(dictProvider, positions, lastBackTracePos, endPosData, fragment))
        if (isEnd) {
            sb.append("  fini [style=invis]\n")
            sb.append("  ")
            sb.append(getNodeID(endPosData.pos, fromIDX))
            sb.append(" -> fini [label=\"$EOS_LABEL\"]")
        }
    }

    private fun setBestPathMap(
        positions: Viterbi.WrappedPositionArray<out Viterbi.Position>,
        startPos: Int,
        endPosData: Viterbi.Position,
        fromIDX: Int
    ) {
        bestPathMap.clear()
        var pos = endPosData.pos
        var bestIDX = fromIDX
        while (pos > startPos) {
            val posData = positions.get(pos)
            val backPos = posData.backPos[bestIDX]
            val backIDX = posData.backIndex[bestIDX]

            val toNodeID = getNodeID(pos, bestIDX)
            val fromNodeID = getNodeID(backPos, backIDX)
            bestPathMap[fromNodeID] = toNodeID
            pos = backPos
            bestIDX = backIDX
        }
    }

    private fun formatNodes(
        dictProvider: DictionaryProvider<T>,
        positions: Viterbi.WrappedPositionArray<out Viterbi.Position>,
        startPos: Int,
        endPosData: Viterbi.Position,
        fragment: CharArray
    ): String {
        val sb = StringBuilder()
        for (pos in (startPos + 1)..endPosData.pos) {
            val posData = positions.get(pos)
            for (idx in 0 until posData.count) {
                sb.append("  ")
                sb.append(getNodeID(pos, idx))
                sb.append(" [label=\"")
                sb.append(pos)
                sb.append(": ")
                sb.append(posData.lastRightID[idx])
                sb.append("\"]\n")
            }
        }

        for (pos in endPosData.pos downTo (startPos + 1)) {
            val posData = positions.get(pos)
            for (idx in 0 until posData.count) {
                val backPos = posData.backPos[idx]
                val backIDX = posData.backIndex[idx]
                val backPosData = positions.get(backPos)
                val toNodeID = getNodeID(pos, idx)
                val fromNodeID = getNodeID(backPos, backIDX)

                sb.append("  ")
                sb.append(fromNodeID)
                sb.append(" -> ")
                sb.append(toNodeID)

                val attrs = if (toNodeID == bestPathMap[fromNodeID]) {
                    " color=\"#40e050\" fontcolor=\"#40a050\" penwidth=3 fontsize=20"
                } else {
                    ""
                }

                val backType = posData.backType[idx]
                val backID = posData.backID[idx]
                val dict = dictProvider.get(backType)
                val wordCost = dict.getWordCost(backID)
                val bgCost = costs.get(
                    backPosData.lastRightID[backIDX],
                    dict.getLeftId(backID)
                )
                val surfaceForm = String.fromCharArray(fragment, backPos - startPos, pos - backPos)

                sb.append(" [label=\"")
                sb.append(surfaceForm)
                sb.append(' ')
                sb.append(wordCost)
                if (bgCost >= 0) sb.append('+')
                sb.append(bgCost)
                sb.append("\"")
                sb.append(attrs)
                sb.append("]\n")
            }
        }
        return sb.toString()
    }

    private fun formatHeader(): String {
        return "digraph viterbi {\n" +
            "  graph [ fontsize=30 labelloc=\"t\" label=\"\" splines=true overlap=false rankdir = \"LR\"];\n" +
            "  edge [ fontname=\"$FONT_NAME\" fontcolor=\"red\" color=\"#606060\" ]\n" +
            "  node [ style=\"filled\" fillcolor=\"#e8e8f0\" shape=\"Mrecord\" fontname=\"$FONT_NAME\" ]\n"
    }

    private fun formatTrailer(): String = "}"

    private fun getNodeID(pos: Int, idx: Int): String = "$pos.$idx"

    fun interface DictionaryProvider<T : MorphData> {
        fun get(type: TokenType): Dictionary<out T>
    }
}
