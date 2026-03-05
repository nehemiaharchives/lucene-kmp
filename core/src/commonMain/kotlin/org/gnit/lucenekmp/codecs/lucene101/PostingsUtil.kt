package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.GroupVIntUtil
import okio.IOException

/** Utility class to encode/decode postings block.  */
internal object PostingsUtil {
    /**
     * Read values that have been written using variable-length encoding and group-varint encoding
     * instead of bit-packing.
     */
    @Throws(IOException::class)
    fun readVIntBlock(
        docIn: IndexInput,
        docBuffer: IntArray,
        freqBuffer: IntArray,
        num: Int,
        indexHasFreq: Boolean,
        decodeFreq: Boolean
    ) {
        GroupVIntUtil.readGroupVInts(docIn, docBuffer, num)
        readVIntBlockPlatform(docIn, docBuffer, freqBuffer, num, indexHasFreq, decodeFreq)
    }

    /** Write freq buffer with variable-length encoding and doc buffer with group-varint encoding.  */
    @Throws(IOException::class)
    fun writeVIntBlock(
        docOut: DataOutput, docBuffer: IntArray, freqBuffer: IntArray, num: Int, writeFreqs: Boolean
    ) {
        if (writeFreqs) {
            for (i in 0..<num) {
                docBuffer[i] = (docBuffer[i] shl 1) or (if (freqBuffer[i] == 1) 1 else 0)
            }
        }
        docOut.writeGroupVInts(docBuffer, num)
        if (writeFreqs) {
            for (i in 0..<num) {
                val freq = freqBuffer[i]
                if (freq != 1) {
                    docOut.writeVInt(freq)
                }
            }
        }
    }
}
