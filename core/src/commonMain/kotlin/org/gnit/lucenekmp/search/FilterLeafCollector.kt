package org.gnit.lucenekmp.search

import okio.IOException


/**
 * [LeafCollector] delegator.
 *
 * @lucene.experimental
 */
abstract class FilterLeafCollector
/** Sole constructor.  */(protected val `in`: LeafCollector) : LeafCollector {
    override var scorer: Scorable?
        get() {
            return `in`.scorer
        }
        set(scorer) {
        `in`.scorer = scorer
    }

    @Throws(IOException::class)
    override fun collect(doc: Int) {
        `in`.collect(doc)
    }

    @Throws(IOException::class)
    override fun finish() {
        `in`.finish()
    }

    override fun toString(): String {
        var name: String = this::class.simpleName!!
        if (name.isEmpty()) {
            // an anonoymous subclass will have empty name?
            name = "FilterLeafCollector"
        }
        return "$name($`in`)"
    }
}
