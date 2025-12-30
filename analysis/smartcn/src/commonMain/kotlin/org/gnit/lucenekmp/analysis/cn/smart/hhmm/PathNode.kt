package org.gnit.lucenekmp.analysis.cn.smart.hhmm

/**
 * SmartChineseAnalyzer internal node representation
 *
 * @lucene.experimental
 */
class PathNode : Comparable<PathNode> {
    var weight: Double = 0.0
    var preNode: Int = 0

    override fun compareTo(other: PathNode): Int {
        return when {
            weight < other.weight -> -1
            weight == other.weight -> 0
            else -> 1
        }
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + preNode
        val temp = weight.toBits()
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PathNode) return false
        return preNode == other.preNode && weight.toBits() == other.weight.toBits()
    }
}
