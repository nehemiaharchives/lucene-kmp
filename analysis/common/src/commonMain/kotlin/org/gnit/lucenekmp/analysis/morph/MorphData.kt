package org.gnit.lucenekmp.analysis.morph

/** High-level interface that represents morphological information in a dictionary */
interface MorphData {
    /**
     * Get left id of specified word
     *
     * @return left id
     */
    fun getLeftId(morphId: Int): Int

    /**
     * Get right id of specified word
     *
     * @return right id
     */
    fun getRightId(morphId: Int): Int

    /**
     * Get word cost of specified word
     *
     * @return word's cost
     */
    fun getWordCost(morphId: Int): Int
}
