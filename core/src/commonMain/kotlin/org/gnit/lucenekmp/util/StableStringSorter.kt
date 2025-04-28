package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.util.StableMSBRadixSorter.MergeSorter

internal abstract class StableStringSorter(cmp: Comparator<BytesRef>) : StringSorter(cmp) {
    /** Save the i-th value into the j-th position in temporary storage.  */
    protected abstract fun save(i: Int, j: Int)

    /** Restore values between i-th and j-th(excluding) in temporary storage into original storage.  */
    protected abstract fun restore(i: Int, j: Int)

    override fun radixSorter(cmp: BytesRefComparator): Sorter {
        return object : StableMSBRadixSorter(cmp.comparedBytesCount) {
            override fun save(i: Int, j: Int) {
                this@StableStringSorter.save(i, j)
            }

            override fun restore(i: Int, j: Int) {
                this@StableStringSorter.restore(i, j)
            }

            override fun swap(i: Int, j: Int) {
                this@StableStringSorter.swap(i, j)
            }

            override fun byteAt(i: Int, k: Int): Byte {
                get(scratch1, scratchBytes1, i)
                return cmp.byteAt(scratchBytes1, k).toByte()
            }

            override fun getFallbackSorter(k: Int): Sorter {
                return fallbackSorter(Comparator { o1: BytesRef, o2: BytesRef ->
                    cmp.compare(
                        o1,
                        o2,
                        k
                    )
                })
            }
        }
    }

    override fun fallbackSorter(cmp: Comparator<BytesRef>): Sorter {
        // TODO: Maybe tim sort is better
        return object : MergeSorter() {
            override fun save(i: Int, j: Int) {
                this@StableStringSorter.save(i, j)
            }

            override fun restore(i: Int, j: Int) {
                this@StableStringSorter.restore(i, j)
            }

            override fun compare(i: Int, j: Int): Int {
                get(scratch1, scratchBytes1, i)
                get(scratch2, scratchBytes2, j)
                return cmp.compare(scratchBytes1, scratchBytes2)
            }

            override fun swap(i: Int, j: Int) {
                this@StableStringSorter.swap(i, j)
            }
        }
    }
}
