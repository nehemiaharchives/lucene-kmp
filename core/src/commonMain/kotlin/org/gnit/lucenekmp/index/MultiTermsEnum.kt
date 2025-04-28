package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.MultiPostingsEnum.EnumWithSlice
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.math.min

/**
 * Exposes [TermsEnum] API, merged from [TermsEnum] API of sub-segments. This does a
 * merge sort, by term text, of the sub-readers.
 *
 * @lucene.experimental
 */
class MultiTermsEnum(slices: Array<ReaderSlice>) : BaseTermsEnum() {
    private val queue: TermMergeQueue = TermMergeQueue(slices.size)

    // all of our subs (one per sub-reader)
    private val subs: Array<TermsEnumWithSlice?>

    // current subs that have at least one term for this field
    private val currentSubs: Array<TermsEnumWithSlice?>

    /** Returns sub-reader slices positioned to the current term.  */
    val matchArray: Array<TermsEnumWithSlice?>
    private val subDocs: Array<EnumWithSlice?>

    private var lastSeek: BytesRef? = null
    private var lastSeekExact = false
    private val lastSeekScratch: BytesRefBuilder = BytesRefBuilder()

    /** Returns how many sub-reader slices contain the current term. @see #getMatchArray  */
    var matchCount: Int = 0
        private set
    private var numSubs = 0
    private var current: BytesRef? = null

    /**
     * Sole constructor.
     *
     * @param slices Which sub-reader slices we should merge.
     */
    init {
        this.matchArray = kotlin.arrayOfNulls<TermsEnumWithSlice>(slices.size)
        subs = kotlin.arrayOfNulls<TermsEnumWithSlice>(slices.size)
        subDocs = kotlin.arrayOfNulls<EnumWithSlice>(slices.size)
        for (i in slices.indices) {
            subs[i] = TermsEnumWithSlice(i, slices[i])
            subDocs[i] = EnumWithSlice()
            subDocs[i]!!.slice = slices[i]
        }
        currentSubs = kotlin.arrayOfNulls<TermsEnumWithSlice>(slices.size)
    }

    override fun term(): BytesRef? {
        return current
    }

    /**
     * The terms array must be newly created TermsEnum, ie [TermsEnum.next] has not yet been
     * called.
     */
    @Throws(IOException::class)
    fun reset(termsEnumsIndex: Array<TermsEnumIndex>): TermsEnum {
        require(termsEnumsIndex.size <= matchArray.size)
        numSubs = 0
        this.matchCount = 0
        queue.clear()
        for (i in termsEnumsIndex.indices) {
            val termsEnumIndex = checkNotNull(termsEnumsIndex[i])
            val term: BytesRef? = termsEnumIndex.next()
            if (term != null) {
                val entry = subs[termsEnumIndex.subIndex]
                entry!!.reset(termsEnumIndex)
                queue.add(entry)
                currentSubs[numSubs++] = entry
            } else {
                // field has no terms
            }
        }

        return if (queue.size() == 0) {
            EMPTY
        } else {
            this
        }
    }

    @Throws(IOException::class)
    override fun seekExact(term: BytesRef): Boolean {
        queue.clear()
        this.matchCount = 0

        var seekOpt = false
        if (lastSeek != null && lastSeek!! <= term) {
            seekOpt = true
        }

        lastSeek = null
        lastSeekExact = true

        for (i in 0..<numSubs) {
            val status: Boolean
            // LUCENE-2130: if we had just seek'd already, prior
            // to this seek, and the new seek term is after the
            // previous one, don't try to re-seek this sub if its
            // current term is already beyond this new seek term.
            // Doing so is a waste because this sub will simply
            // seek to the same spot.
            if (seekOpt) {
                val curTerm: BytesRef? = currentSubs[i]!!.term()
                if (curTerm != null) {
                    val cmp = term.compareTo(curTerm)
                    status = if (cmp == 0) {
                        true
                    } else if (cmp < 0) {
                        false
                    } else {
                        currentSubs[i]!!.seekExact(term)
                    }
                } else {
                    status = false
                }
            } else {
                status = currentSubs[i]!!.seekExact(term)
            }

            if (status) {
                this.matchArray[this.matchCount++] = currentSubs[i]!!
                current = currentSubs[i]!!.term()
                require(term == currentSubs[i]!!.term())
            }
        }

        // if at least one sub had exact match to the requested
        // term then we found match
        return this.matchCount > 0
    }

    @Throws(IOException::class)
    override fun seekCeil(term: BytesRef): SeekStatus {
        queue.clear()
        this.matchCount = 0
        lastSeekExact = false

        var seekOpt = false
        if (lastSeek != null && lastSeek!! <= term) {
            seekOpt = true
        }

        lastSeekScratch.copyBytes(term)
        lastSeek = lastSeekScratch.get()

        for (i in 0..<numSubs) {
            val status: SeekStatus
            // LUCENE-2130: if we had just seek'd already, prior
            // to this seek, and the new seek term is after the
            // previous one, don't try to re-seek this sub if its
            // current term is already beyond this new seek term.
            // Doing so is a waste because this sub will simply
            // seek to the same spot.
            if (seekOpt) {
                val curTerm: BytesRef? = currentSubs[i]!!.term()
                status = if (curTerm != null) {
                    val cmp = term.compareTo(curTerm)
                    if (cmp == 0) {
                        SeekStatus.FOUND
                    } else if (cmp < 0) {
                        SeekStatus.NOT_FOUND
                    } else {
                        currentSubs[i]!!.seekCeil(term)
                    }
                } else {
                    SeekStatus.END
                }
            } else {
                status = currentSubs[i]!!.seekCeil(term)
            }

            if (status === SeekStatus.FOUND) {
                this.matchArray[this.matchCount++] = currentSubs[i]!!
                current = currentSubs[i]!!.term()
                queue.add(currentSubs[i]!!)
            } else {
                if (status === SeekStatus.NOT_FOUND) {
                    checkNotNull(currentSubs[i]!!.term())
                    queue.add(currentSubs[i]!!)
                } else {
                    require(status === SeekStatus.END)
                }
            }
        }

        if (this.matchCount > 0) {
            // at least one sub had exact match to the requested term
            return SeekStatus.FOUND
        } else if (queue.size() > 0) {
            // no sub had exact match, but at least one sub found
            // a term after the requested term -- advance to that
            // next term:
            pullTop()
            return SeekStatus.NOT_FOUND
        } else {
            return SeekStatus.END
        }
    }

    override fun seekExact(ord: Long) {
        throw UnsupportedOperationException()
    }

    override fun ord(): Long {
        throw UnsupportedOperationException()
    }

    private fun pullTop() {
        // extract all subs from the queue that have the same
        // top term
        require(this.matchCount == 0)
        this.matchCount = queue.fillTop(this.matchArray as Array<TermsEnumWithSlice>)
        current = this.matchArray[0].term()
    }

    @Throws(IOException::class)
    private fun pushTop() {
        // call next() on each top, and reorder queue
        for (i in 0..<this.matchCount) {
            val top: TermsEnumWithSlice = queue.top()
            if (top.next() == null) {
                queue.pop()
            } else {
                queue.updateTop()
            }
        }
        this.matchCount = 0
    }

    @Throws(IOException::class)
    override fun next(): BytesRef? {
        if (lastSeekExact) {
            // Must seekCeil at this point, so those subs that
            // didn't have the term can find the following term.
            // NOTE: we could save some CPU by only seekCeil the
            // subs that didn't match the last exact seek... but
            // most impls short-circuit if you seekCeil to term
            // they are already on.
            val status: SeekStatus = seekCeil(current!!)
            require(status === SeekStatus.FOUND)
            lastSeekExact = false
        }
        lastSeek = null

        // restore queue
        pushTop()

        // gather equal top fields
        if (queue.size() > 0) {
            // TODO: we could maybe defer this somewhat costly operation until one of the APIs that
            // needs to see the top is invoked (docFreq, postings, etc.)
            pullTop()
        } else {
            current = null
        }

        return current
    }

    @Throws(IOException::class)
    override fun docFreq(): Int {
        var sum = 0
        for (i in 0..<this.matchCount) {
            sum += this.matchArray[i]!!.termsEnum!!.docFreq()
        }
        return sum
    }

    @Throws(IOException::class)
    override fun totalTermFreq(): Long {
        var sum: Long = 0
        for (i in 0..<this.matchCount) {
            val v = this.matchArray[i]!!.termsEnum!!.totalTermFreq()
            require(v != -1L)
            sum += v
        }
        return sum
    }

    @Throws(IOException::class)
    override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
        var docsEnum: MultiPostingsEnum

        // Can only reuse if incoming enum is also a MultiDocsEnum
        if (reuse != null && reuse is MultiPostingsEnum) {
            docsEnum = reuse
            // ... and was previously created w/ this MultiTermsEnum:
            if (!docsEnum.canReuse(this)) {
                docsEnum = MultiPostingsEnum(this, subs.size)
            }
        } else {
            docsEnum = MultiPostingsEnum(this, subs.size)
        }

        var upto = 0

        ArrayUtil.timSort(this.matchArray, 0, this.matchCount) { o1, o2 -> o1!!.subIndex - o2!!.subIndex }

        for (i in 0..<this.matchCount) {
            val entry = this.matchArray[i]

            require(
                entry!!.subIndex < docsEnum.subPostingsEnums.size
            ) { "${entry.subIndex} vs ${docsEnum.subPostingsEnums.size}; ${subs.size}" }
            val subPostingsEnum = checkNotNull(
                entry.termsEnum!!.postings(docsEnum.subPostingsEnums[entry.subIndex], flags)
            )
            docsEnum.subPostingsEnums[entry.subIndex] = subPostingsEnum
            subDocs[upto]!!.postingsEnum = subPostingsEnum
            subDocs[upto]!!.slice = entry.subSlice
            upto++
        }

        return docsEnum.reset(subDocs as Array<EnumWithSlice>, upto)
    }

    @Throws(IOException::class)
    override fun impacts(flags: Int): ImpactsEnum {
        // implemented to not fail CheckIndex, but you shouldn't be using impacts on a slow reader
        return SlowImpactsEnum(postings(null, flags))
    }

    class TermsEnumWithSlice(index: Int, internal val subSlice: ReaderSlice) : TermsEnumIndex(null, index) {
        init {
            require(subSlice.length >= 0) { "length=" + subSlice.length }
        }

        override fun toString(): String {
            return subSlice.toString() + ":" + super.toString()
        }
    }

    private class TermMergeQueue(size: Int) : PriorityQueue<TermsEnumWithSlice>(size) {
        val stack: IntArray = IntArray(size)

        override fun lessThan(termsA: TermsEnumWithSlice, termsB: TermsEnumWithSlice): Boolean {
            return termsA.compareTermTo(termsB) < 0
        }

        /**
         * Add the [.top] slice as well as all slices that are positionned on the same term to
         * `tops` and return how many of them there are.
         */
        fun fillTop(tops: Array<TermsEnumWithSlice>): Int {
            val size: Int = size()
            if (size == 0) {
                return 0
            }
            tops[0] = top()
            var numTop = 1
            stack[0] = 1
            var stackLen = 1

            while (stackLen != 0) {
                val index = stack[--stackLen]
                val leftChild = index shl 1
                var child = leftChild
                val end = min(size, leftChild + 1)
                while (child <= end) {
                    val te = get(child)
                    if (te.compareTermTo(tops[0]) == 0) {
                        tops[numTop++] = te
                        stack[stackLen++] = child
                    }
                    ++child
                }
            }
            return numTop
        }

        fun get(i: Int): TermsEnumWithSlice {
            return heapArray[i] as TermsEnumWithSlice
        }
    }

    override fun toString(): String {
        return "MultiTermsEnum(" + subs.contentToString() + ")"
    }
}
