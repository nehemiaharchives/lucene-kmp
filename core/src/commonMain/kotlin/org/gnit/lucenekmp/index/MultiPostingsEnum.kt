package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.util.BytesRef

/**
 * Exposes [PostingsEnum], merged from [PostingsEnum] API of sub-segments.
 *
 * @lucene.experimental
 */
class MultiPostingsEnum(private val parent: MultiTermsEnum, subReaderCount: Int) : PostingsEnum() {
    val subPostingsEnums: Array<PostingsEnum?> = kotlin.arrayOfNulls<PostingsEnum>(subReaderCount)

    /** Returns sub-readers we are merging.  */
    val subs: Array<EnumWithSlice?>

    /**
     * How many sub-readers we are merging.
     *
     * @see .getSubs
     */
    var numSubs: Int = 0
    var upto: Int = 0
    var current: PostingsEnum? = null
    var currentBase: Int = 0
    var doc: Int = -1

    /**
     * Sole constructor.
     *
     * @param parent The [MultiTermsEnum] that created us.
     * @param subReaderCount How many sub-readers are being merged.
     */
    init {
        this.subs = kotlin.arrayOfNulls<EnumWithSlice>(subReaderCount)
        for (i in subs.indices) {
            subs[i] = EnumWithSlice()
        }
    }

    /** Returns `true` if this instance can be reused by the provided [MultiTermsEnum].  */
    fun canReuse(parent: MultiTermsEnum): Boolean {
        return this.parent === parent
    }

    /** Re-use and reset this instance on the provided slices.  */
    fun reset(newSubs: Array<EnumWithSlice>, numSubs: Int): MultiPostingsEnum {
        this.numSubs = numSubs
        for (i in 0..<numSubs) {
            this.subs[i]!!.postingsEnum = newSubs[i].postingsEnum
            this.subs[i]!!.slice = newSubs[i].slice
        }
        upto = -1
        doc = -1
        current = null
        return this
    }

    @Throws(IOException::class)
    override fun freq(): Int {
        checkNotNull(current)
        return current!!.freq()
    }

    override fun docID(): Int {
        return doc
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        require(target > doc)
        while (true) {
            if (current != null) {
                val doc: Int
                doc = if (target < currentBase) {
                    // target was in the previous slice but there was no matching doc after it
                    current!!.nextDoc()
                } else {
                    current!!.advance(target - currentBase)
                }
                if (doc == NO_MORE_DOCS) {
                    current = null
                } else {
                    return (doc + currentBase).also { this.doc = it }
                }
            } else if (upto == numSubs - 1) {
                return NO_MORE_DOCS.also { this.doc = it }
            } else {
                upto++
                current = subs[upto]!!.postingsEnum
                currentBase = subs[upto]!!.slice!!.start
            }
        }
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        while (true) {
            if (current == null) {
                if (upto == numSubs - 1) {
                    return NO_MORE_DOCS.also { this.doc = it }
                } else {
                    upto++
                    current = subs[upto]!!.postingsEnum
                    currentBase = subs[upto]!!.slice!!.start
                }
            }

            val doc = current!!.nextDoc()
            if (doc != NO_MORE_DOCS) {
                return (currentBase + doc).also { this.doc = it }
            } else {
                current = null
            }
        }
    }

    @Throws(IOException::class)
    override fun nextPosition(): Int {
        return current!!.nextPosition()
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        return current!!.startOffset()
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        return current!!.endOffset()
    }

    override val payload: BytesRef?
        get() = current!!.payload

    // TODO: implement bulk read more efficiently than super
    /** Holds a [PostingsEnum] along with the corresponding [ReaderSlice].  */
    class EnumWithSlice internal constructor() {
        /** [PostingsEnum] for this sub-reader.  */
        var postingsEnum: PostingsEnum? = null

        /** [ReaderSlice] describing how this sub-reader fits into the composite reader.  */
        var slice: ReaderSlice? = null

        override fun toString(): String {
            return "$slice:$postingsEnum"
        }
    }

    override fun cost(): Long {
        var cost: Long = 0
        for (i in 0..<numSubs) {
            cost += subs[i]!!.postingsEnum!!.cost()
        }
        return cost
    }

    override fun toString(): String {
        return "MultiDocsAndPositionsEnum(" + this.subs.contentToString() + ")"
    }
}
