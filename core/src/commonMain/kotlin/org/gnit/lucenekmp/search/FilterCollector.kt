package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * [Collector] delegator.
 *
 * @lucene.experimental
 */
abstract class FilterCollector(protected val `in`: Collector) : Collector {

    @Throws(IOException::class)
    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
        return `in`.getLeafCollector(context)
    }

    override var weight: Weight? = `in`.weight
        get() = `in`.weight
     fun set(weight: Weight) {
        `in`.weight = weight
    }

    override fun toString(): String {
        return this::class.simpleName + "(" + `in` + ")"
    }

    override fun scoreMode(): ScoreMode {
        return `in`.scoreMode()
    }
}
