package org.gnit.lucenekmp.analysis.morph

/** High-level dictionary interface for morphological analyzers. */
interface Dictionary<T : MorphData> {
    /**
     * Get left id of specified word
     *
     * @return left id
     */
    fun getLeftId(morphId: Int): Int = getMorphAttributes().getLeftId(morphId)

    /**
     * Get right id of specified word
     *
     * @return right id
     */
    fun getRightId(morphId: Int): Int = getMorphAttributes().getRightId(morphId)

    /**
     * Get word cost of specified word
     *
     * @return word's cost
     */
    fun getWordCost(morphId: Int): Int = getMorphAttributes().getWordCost(morphId)

    fun getMorphAttributes(): T
}
