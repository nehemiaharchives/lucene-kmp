package org.gnit.lucenekmp.util


/**
 * A [BytesRef] sorter tries to use a efficient radix sorter if [StringSorter.cmp] is a
 * [BytesRefComparator], otherwise fallback to [StringSorter.fallbackSorter]
 *
 * @lucene.internal
 */
abstract class StringSorter protected constructor(cmp: Comparator<BytesRef>) : Sorter() {
    private val cmp: Comparator<BytesRef>
    protected val scratch1: BytesRefBuilder = BytesRefBuilder()
    protected val scratch2: BytesRefBuilder = BytesRefBuilder()
    protected val pivotBuilder: BytesRefBuilder = BytesRefBuilder()
    protected val scratchBytes1: BytesRef = BytesRef()
    protected val scratchBytes2: BytesRef = BytesRef()
    protected val pivot: BytesRef = BytesRef()

    init {
        this.cmp = cmp
    }

    protected abstract fun get(builder: BytesRefBuilder, result: BytesRef, i: Int)

    override fun compare(i: Int, j: Int): Int {
        get(scratch1, scratchBytes1, i)
        get(scratch2, scratchBytes2, j)
        return cmp.compare(scratchBytes1, scratchBytes2)
    }

    override fun sort(from: Int, to: Int) {
        if (cmp is BytesRefComparator) {
            radixSorter(cmp)!!.sort(from, to)
        } else {
            fallbackSorter(cmp)!!.sort(from, to)
        }
    }

    /** A radix sorter for [BytesRef]  */
    protected open inner class MSBStringRadixSorter(private val cmp: BytesRefComparator) :
        MSBRadixSorter(
            cmp.comparedBytesCount
        ) {
        protected override fun swap(i: Int, j: Int) {
            this@StringSorter.swap(i, j)
        }

        protected override fun byteAt(i: Int, k: Int): Int {
            get(scratch1, scratchBytes1, i)
            return cmp.byteAt(scratchBytes1, k)
        }

        protected override fun getFallbackSorter(k: Int): Sorter {
            return fallbackSorter(Comparator { o1: BytesRef, o2: BytesRef -> cmp.compare(o1, o2, k) })
        }
    }

    protected open fun radixSorter(cmp: BytesRefComparator): Sorter {
        return this.MSBStringRadixSorter(cmp)
    }

    protected open fun fallbackSorter(cmp: Comparator<BytesRef>): Sorter {
        return object : IntroSorter() {
            override fun swap(i: Int, j: Int) {
                this@StringSorter.swap(i, j)
            }

            override fun compare(i: Int, j: Int): Int {
                get(scratch1, scratchBytes1, i)
                get(scratch2, scratchBytes2, j)
                return cmp.compare(scratchBytes1, scratchBytes2)
            }

            override fun setPivot(i: Int) {
                get(pivotBuilder, pivot, i)
            }

            override fun comparePivot(j: Int): Int {
                get(scratch1, scratchBytes1, j)
                return cmp.compare(pivot, scratchBytes1)
            }
        }
    }
}
