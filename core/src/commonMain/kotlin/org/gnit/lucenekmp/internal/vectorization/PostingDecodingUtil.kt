package org.gnit.lucenekmp.internal.vectorization

import kotlinx.io.IOException
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
        // Default implementation, which takes advantage of the C2 compiler's loop unrolling and
        // auto-vectorization.
        `in`.readInts(c, cIndex, count)
        val maxIter = (bShift - 1) / dec
        for (i in 0..<count) {
            for (j in 0..maxIter) {
                b[count * j + i] = (c[cIndex + i] ushr (bShift - j * dec)) and bMask
            }
            c[cIndex + i] = c[cIndex + i] and cMask
        }
    }
}
