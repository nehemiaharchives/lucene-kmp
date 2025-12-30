package org.gnit.lucenekmp.analysis.cn.smart.hhmm

/**
 * A pair of tokens in SegGraph
 *
 * @lucene.experimental
 */
class SegTokenPair(
    var charArray: CharArray,

    /** index of the first token in SegGraph */
    var from: Int,

    /** index of the second token in SegGraph */
    var to: Int,

    var weight: Double,
) {
    override fun hashCode(): Int {
        var result = charArray.contentHashCode()
        result = 31 * result + from
        result = 31 * result + to
        val temp = weight.toBits()
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SegTokenPair) return false
        return charArray.contentEquals(other.charArray) &&
            from == other.from &&
            to == other.to &&
            weight.toBits() == other.weight.toBits()
    }
}
