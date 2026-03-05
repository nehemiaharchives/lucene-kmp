package org.gnit.lucenekmp.internal.vectorization

import okio.IOException
import org.gnit.lucenekmp.store.IndexInput

/** Utility class to decode postings.  */
class PostingDecodingUtil(`in`: IndexInput) {
    /** The wrapper [IndexInput].  */
    val `in`: IndexInput

    /** Sole constructor, called by sub-classes.  */
    init {
        this.`in` = `in`
    }

    /**
     * Core methods for decoding blocks of docs / freqs / positions / offsets.
     *
     *
     *  * Read `count` ints.
     *  * For all `i` &gt;= 0 so that `bShift - i * dec` &gt; 0, apply shift `bShift - i * dec` and store the result in `b` at offset `count * i`.
     *  * Apply mask `cMask` and store the result in `c` starting at offset `cIndex`.
     *
     */
    @Throws(IOException::class)
    fun splitInts(
        count: Int, b: IntArray, bShift: Int, dec: Int, bMask: Int, c: IntArray, cIndex: Int, cMask: Int
    ) {
        splitIntsPlatform(`in`, count, b, bShift, dec, bMask, c, cIndex, cMask)
    }
}
