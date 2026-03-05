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
        var i = 0
        while (i < num) {
            val value = docBuffer[i]
            val marker = value and 0x01
            freqBuffer[i] = marker
            docBuffer[i] = value ushr 1
            if (marker == 0) {
                freqBuffer[i] = docIn.readVInt()
            }
            i++
        }
    } else if (indexHasFreq) {
        var i = 0
        while (i < num) {
            docBuffer[i] = docBuffer[i] ushr 1
            i++
        }
    }
}
