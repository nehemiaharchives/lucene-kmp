package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.store.IndexInput

internal actual fun readVIntBlockPlatform(
    docIn: IndexInput,
    docBuffer: IntArray,
    freqBuffer: IntArray,
    num: Int,
    indexHasFreq: Boolean,
    decodeFreq: Boolean
) {
    if (indexHasFreq && decodeFreq) {
        for (i in 0..<num) {
            freqBuffer[i] = docBuffer[i] and 0x01
            docBuffer[i] = docBuffer[i] ushr 1
            if (freqBuffer[i] == 0) {
                freqBuffer[i] = docIn.readVInt()
            }
        }
    } else if (indexHasFreq) {
        for (i in 0..<num) {
            docBuffer[i] = docBuffer[i] ushr 1
        }
    }
}
