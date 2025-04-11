package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.MergeState.DocMap
import org.gnit.lucenekmp.index.MultiPostingsEnum.EnumWithSlice
import org.gnit.lucenekmp.util.BytesRef


/**
 * Exposes flex API, merged from flex API of sub-segments, remapping docIDs (this is used for
 * segment merging).
 *
 * @lucene.experimental
 */
internal class MappingMultiPostingsEnum(val field: String?, mergeState: MergeState) : PostingsEnum() {
    var multiDocsAndPositionsEnum: MultiPostingsEnum? = null
    val docIDMerger: DocIDMerger<MappingPostingsSub>
    private var current: MappingPostingsSub? = null
    private val allSubs: Array<MappingPostingsSub?>
    private val subs: MutableList<MappingPostingsSub> = mutableListOf<MappingPostingsSub>()

    internal class MappingPostingsSub(docMap: DocMap?) : DocIDMerger.Sub(docMap!!) {
        var postings: PostingsEnum? = null

        override fun nextDoc(): Int {
            try {
                return postings!!.nextDoc()
            } catch (ioe: IOException) {
                throw RuntimeException(ioe)
            }
        }
    }

    /** Sole constructor.  */
    init {
        allSubs = kotlin.arrayOfNulls<MappingPostingsSub>(mergeState.fieldsProducers.size)
        for (i in allSubs.indices) {
            allSubs[i] = MappingPostingsSub(mergeState.docMaps!![i])
        }
        this.docIDMerger = DocIDMerger.of(subs, allSubs.size, mergeState.needsIndexSort)
    }

    @Throws(IOException::class)
    fun reset(postingsEnum: MultiPostingsEnum): MappingMultiPostingsEnum {
        this.multiDocsAndPositionsEnum = postingsEnum
        val subsArray: Array<EnumWithSlice?> = postingsEnum.subs
        val count: Int = postingsEnum.numSubs
        subs.clear()
        for (i in 0..<count) {
            val sub = allSubs[subsArray[i]!!.slice!!.readerIndex]
            sub!!.postings = subsArray[i]!!.postingsEnum
            subs.add(sub)
        }
        docIDMerger.reset()
        return this
    }

    @Throws(IOException::class)
    override fun freq(): Int {
        return current!!.postings!!.freq()
    }

    override fun docID(): Int {
        if (current == null) {
            return -1
        } else {
            return current!!.mappedDocID
        }
    }

    override fun advance(target: Int): Int {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        current = docIDMerger.next()
        if (current == null) {
            return NO_MORE_DOCS
        } else {
            return current!!.mappedDocID
        }
    }

    @Throws(IOException::class)
    override fun nextPosition(): Int {
        val pos = current!!.postings!!.nextPosition()
        if (pos < 0) {
            throw CorruptIndexException(
                "position=" + pos + " is negative, field=\"" + field + " doc=" + current!!.mappedDocID,
                current!!.postings.toString()
            )
        } else if (pos > IndexWriter.MAX_POSITION) {
            throw CorruptIndexException(
                ("position="
                        + pos
                        + " is too large (> IndexWriter.MAX_POSITION="
                        + IndexWriter.MAX_POSITION
                        + "), field=\""
                        + field
                        + "\" doc="
                        + current!!.mappedDocID),
                current!!.postings.toString()
            )
        }
        return pos
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        return current!!.postings!!.startOffset()
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        return current!!.postings!!.endOffset()
    }

    @get:Throws(IOException::class)
    override val payload: BytesRef?
        get() = current!!.postings!!.payload

    override fun cost(): Long {
        var cost: Long = 0
        for (sub in subs) {
            cost += sub.postings!!.cost()
        }
        return cost
    }
}