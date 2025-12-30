package org.gnit.lucenekmp.analysis.cn.smart.hhmm

import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap

/**
 * Graph representing possible tokens at each start offset in the sentence.
 *
 * @lucene.experimental
 */
class SegGraph {
    /** Map of start offsets to ArrayList of tokens at that position */
    private val tokenListTable: IntObjectHashMap<ArrayList<SegToken>> = IntObjectHashMap()

    private var maxStart: Int = -1

    /**
     * Returns true if a mapping for the specified start offset exists
     */
    fun isStartExist(s: Int): Boolean {
        return tokenListTable[s] != null
    }

    /**
     * Get the list of tokens at the specified start offset
     */
    fun getStartList(s: Int): List<SegToken>? {
        return tokenListTable[s]
    }

    /**
     * Get the highest start offset in the map
     */
    fun getMaxStart(): Int = maxStart

    /**
     * Set the SegToken.index for each token, based upon its order by startOffset.
     */
    fun makeIndex(): List<SegToken> {
        val result = ArrayList<SegToken>()
        var s = -1
        var count = 0
        val size = tokenListTable.size()
        var index = 0
        while (count < size) {
            if (isStartExist(s)) {
                val tokenList = tokenListTable[s]
                if (tokenList != null) {
                    for (st in tokenList) {
                        st.index = index
                        result.add(st)
                        index++
                    }
                }
                count++
            }
            s++
        }
        return result
    }

    /**
     * Add a SegToken to the mapping, creating a new mapping at the token's startOffset if one
     * does not exist.
     */
    fun addToken(token: SegToken) {
        val s = token.startOffset
        val existing = tokenListTable[s]
        if (existing == null) {
            val newList = ArrayList<SegToken>()
            newList.add(token)
            tokenListTable.put(s, newList)
        } else {
            existing.add(token)
        }
        if (s > maxStart) maxStart = s
    }

    /**
     * Return a List of all tokens in the map, ordered by startOffset.
     */
    fun toTokenList(): List<SegToken> {
        val result = ArrayList<SegToken>()
        var s = -1
        var count = 0
        val size = tokenListTable.size()
        while (count < size) {
            if (isStartExist(s)) {
                val tokenList = tokenListTable[s]
                if (tokenList != null) {
                    for (st in tokenList) {
                        result.add(st)
                    }
                }
                count++
            }
            s++
        }
        return result
    }

    override fun toString(): String {
        val tokenList = toTokenList()
        val sb = StringBuilder()
        for (t in tokenList) {
            sb.append(t).append("\n")
        }
        return sb.toString()
    }
}
