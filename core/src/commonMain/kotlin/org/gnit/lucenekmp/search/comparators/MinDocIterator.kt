package org.gnit.lucenekmp.search.comparators

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import kotlin.math.max


/** Docs iterator that starts iterating from a configurable minimum document  */
class MinDocIterator internal constructor(val segmentMinDoc: Int, val maxDoc: Int) : DocIdSetIterator() {
    var doc: Int = -1

    override fun docID(): Int {
        return doc
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        return advance(doc + 1)
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        require(target > doc)
        if (doc == -1) {
            // skip directly to minDoc
            doc = max(target, segmentMinDoc)
        } else {
            doc = target
        }
        if (doc >= maxDoc) {
            doc = NO_MORE_DOCS
        }
        return doc
    }

    override fun cost(): Long {
        return (maxDoc - segmentMinDoc).toLong()
    }
}
