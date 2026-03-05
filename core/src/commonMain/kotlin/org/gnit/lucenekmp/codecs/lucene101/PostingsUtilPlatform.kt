package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.store.IndexInput

internal expect fun readVIntBlockPlatform(
    docIn: IndexInput,
    docBuffer: IntArray,
    freqBuffer: IntArray,
    num: Int,
    indexHasFreq: Boolean,
    decodeFreq: Boolean
)
