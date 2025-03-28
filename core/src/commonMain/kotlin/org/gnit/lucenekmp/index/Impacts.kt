package org.gnit.lucenekmp.index

/** Information about upcoming impacts, ie. (freq, norm) pairs.  */
abstract class Impacts
/** Sole constructor. Typically invoked by sub classes.  */
protected constructor() {
    /**
     * Return the number of levels on which we have impacts. The returned value is always greater than
     * 0 and may not always be the same, even on a single postings list, depending on the current doc
     * ID.
     */
    abstract fun numLevels(): Int

    /**
     * Return the maximum inclusive doc ID until which the list of impacts returned by [ ][.getImpacts] is valid. This is a non-decreasing function of `level`.
     */
    abstract fun getDocIdUpTo(level: Int): Int

    /**
     * Return impacts on the given level. These impacts are sorted by increasing frequency and
     * increasing unsigned norm, and only valid until the doc ID returned by [ ][.getDocIdUpTo] for the same level, included. The returned list is never empty and should
     * implement [java.util.RandomAccess] if it contains more than a single element. NOTE: There
     * is no guarantee that these impacts actually appear in postings, only that they trigger scores
     * that are greater than or equal to the impacts that actually appear in postings.
     */
    abstract fun getImpacts(level: Int): MutableList<Impact>
}
