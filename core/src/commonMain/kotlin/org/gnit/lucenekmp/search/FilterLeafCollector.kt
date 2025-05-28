package org.gnit.lucenekmp.search

import okio.IOException


/**
 * [LeafCollector] delegator.
 *
 * @lucene.experimental
 */
abstract class FilterLeafCollector
/** Sole constructor.  */(protected val `in`: LeafCollector) : LeafCollector {
    @Throws(IOException::class)
    public override fun setScorer(scorer: Scorable) {
        `in`.setScorer(scorer)
    }

    @Throws(IOException::class)
    public override fun collect(doc: Int) {
        `in`.collect(doc)
    }

    @Throws(IOException::class)
    public override fun finish() {
        `in`.finish()
    }

    override fun toString(): String {
        var name: String = this::class.simpleName!!
        if (name.length == 0) {
            // an anonoymous subclass will have empty name?
            name = "FilterLeafCollector"
        }
        return "$name($`in`)"
    }
}
