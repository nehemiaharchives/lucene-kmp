package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.Bits

/**
 * Concatenates multiple Bits together, on every lookup.
 *
 *
 * **NOTE**: This is very costly, as every lookup must do a binary search to locate the right
 * sub-reader.
 *
 * @lucene.experimental
 */
class MultiBits private constructor(
    subs: Array<Bits>,
    starts: IntArray,
    defaultValue: Boolean
) : Bits {
    private val subs: Array<Bits>

    // length is 1+subs.length (the last entry has the maxDoc):
    private val starts: IntArray

    private val defaultValue: Boolean

    init {
        assert(starts.size == 1 + subs.size)
        this.subs = subs
        this.starts = starts
        this.defaultValue = defaultValue
    }

    private fun checkLength(reader: Int, doc: Int): Boolean {
        val length = starts[1 + reader] - starts[reader]
        assert(
            doc - starts[reader] < length
        ) {
            ("doc="
                    + doc
                    + " reader="
                    + reader
                    + " starts[reader]="
                    + starts[reader]
                    + " length="
                    + length)
        }
        return true
    }

    override fun get(index: Int): Boolean {
        val reader: Int = ReaderUtil.subIndex(index, starts)
        assert(reader != -1)
        val bits: Bits = subs[reader]
        if (bits == null) {
            return defaultValue
        } else {
            assert(checkLength(reader, index))
            return bits.get(index - starts[reader])
        }
    }

    override fun toString(): String {
        val b = StringBuilder()
        b.append(subs.size).append(" subs: ")
        for (i in subs.indices) {
            if (i != 0) {
                b.append("; ")
            }
            if (subs[i] == null) {
                b.append("s=").append(starts[i]).append(" l=null")
            } else {
                b.append("s=")
                    .append(starts[i])
                    .append(" l=")
                    .append(subs[i].length())
                    .append(" b=")
                    .append(subs[i])
            }
        }
        b.append(" end=").append(starts[subs.size])
        return b.toString()
    }

    override fun length(): Int {
        return starts[starts.size - 1]
    }

    companion object {
        /**
         * Returns a single [Bits] instance for this reader, merging live Documents on the fly. This
         * method will return null if the reader has no deletions.
         *
         *
         * **NOTE**: this is a very slow way to access live docs. For example, each Bits access will
         * require a binary search. It's better to get the sub-readers and iterate through them yourself.
         */
        fun getLiveDocs(reader: IndexReader): Bits? {
            if (reader.hasDeletions()) {
                val leaves: MutableList<LeafReaderContext> = reader.leaves()
                val size = leaves.size
                assert(size > 0) { "A reader with deletions must have at least one leave" }
                if (size == 1) {
                    return leaves[0].reader().liveDocs
                }
                val liveDocs: Array<Bits> =
                    kotlin.arrayOfNulls<Bits>(size) as Array<Bits>
                val starts = IntArray(size + 1)
                for (i in 0..<size) {
                    // record all liveDocs, even if they are null
                    val ctx: LeafReaderContext = leaves[i]
                    liveDocs[i] = ctx.reader().liveDocs!!
                    starts[i] = ctx.docBase
                }
                starts[size] = reader.maxDoc()
                return MultiBits(liveDocs, starts, true)
            } else {
                return null
            }
        }
    }
}
