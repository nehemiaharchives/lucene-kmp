package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.search.DocIdSetIterator

internal abstract class DocValuesWriter<T : DocIdSetIterator> {
    @Throws(IOException::class)
    abstract fun flush(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?,
        consumer: DocValuesConsumer
    )

    abstract val docValues: T
}
