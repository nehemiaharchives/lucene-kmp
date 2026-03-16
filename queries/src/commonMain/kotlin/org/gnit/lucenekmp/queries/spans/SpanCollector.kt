package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term

/** Expert: collects postings data for span matches. */
interface SpanCollector {
    @Throws(IOException::class)
    fun collectLeaf(postings: PostingsEnum, position: Int, term: Term)

    fun reset()
}
