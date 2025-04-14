package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.DocIDMerger
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.NumericDocValues

/**
 * Abstract API that consumes normalization values. Concrete implementations of this actually do
 * "something" with the norms (write it into the index in a specific format).
 *
 *
 * The lifecycle is:
 *
 *
 *  1. NormsConsumer is created by [NormsFormat.normsConsumer].
 *  1. [.addNormsField] is called for each field with normalization values. The API is a
 * "pull" rather than "push", and the implementation is free to iterate over the values
 * multiple times ([Iterable.iterator]).
 *  1. After all fields are added, the consumer is [.close]d.
 *
 *
 * @lucene.experimental
 */
abstract class NormsConsumer
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {
    /**
     * Writes normalization values for a field.
     *
     * @param field field information
     * @param normsProducer NormsProducer of the numeric norm values
     * @throws IOException if an I/O error occurred.
     */
    @Throws(IOException::class)
    abstract fun addNormsField(field: FieldInfo, normsProducer: NormsProducer)

    /**
     * Merges in the fields from the readers in `mergeState`. The default implementation
     * calls [.mergeNormsField] for each field, filling segments with missing norms for the
     * field with zeros. Implementations can override this method for more sophisticated merging
     * (bulk-byte copying, etc).
     */
    @Throws(IOException::class)
    fun merge(mergeState: MergeState) {
        for (normsProducer in mergeState.normsProducers) {
            normsProducer?.checkIntegrity()
        }
        for (mergeFieldInfo in mergeState.mergeFieldInfos!!) {
            if (mergeFieldInfo.hasNorms()) {
                mergeNormsField(mergeFieldInfo, mergeState)
            }
        }
    }

    /** Tracks state of one numeric sub-reader that we are merging  */
    private class NumericDocValuesSub(docMap: MergeState.DocMap, val values: NumericDocValues) : DocIDMerger.Sub(docMap) {

        init {
            require(values.docID() == -1)
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return values.nextDoc()
        }
    }

    /**
     * Merges the norms from `toMerge`.
     *
     *
     * The default implementation calls [.addNormsField], passing an Iterable that merges and
     * filters deleted documents on the fly.
     */
    @Throws(IOException::class)
    fun mergeNormsField(mergeFieldInfo: FieldInfo, mergeState: MergeState) {
        // TODO: try to share code with default merge of DVConsumer by passing MatchAllBits

        addNormsField(
            mergeFieldInfo,
            object : NormsProducer() {
                @Throws(IOException::class)
                override fun getNorms(fieldInfo: FieldInfo): NumericDocValues {
                    require(fieldInfo === mergeFieldInfo) { "wrong fieldInfo" }

                    val subs: MutableList<NumericDocValuesSub> = ArrayList<NumericDocValuesSub>()
                    require(mergeState.docMaps!!.size == mergeState.docValuesProducers.size)
                    for (i in 0..<mergeState.docValuesProducers.size) {
                        var norms: NumericDocValues? = null
                        val normsProducer: NormsProducer? = mergeState.normsProducers[i]
                        if (normsProducer != null) {
                            val readerFieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(mergeFieldInfo.name)
                            if (readerFieldInfo != null && readerFieldInfo.hasNorms()) {
                                norms = normsProducer.getNorms(readerFieldInfo)
                            }
                        }

                        if (norms != null) {
                            subs.add(NumericDocValuesSub(mergeState.docMaps[i], norms))
                        }
                    }

                    val docIDMerger: DocIDMerger<NumericDocValuesSub> =
                        DocIDMerger.of(subs, mergeState.needsIndexSort)

                    return object : NumericDocValues() {
                        private var docID = -1
                        private var current: NumericDocValuesSub? = null

                        override fun docID(): Int {
                            return docID
                        }

                        @Throws(IOException::class)
                        override fun nextDoc(): Int {
                            current = docIDMerger.next()
                            docID = if (current == null) {
                                NO_MORE_DOCS
                            } else {
                                current!!.mappedDocID
                            }
                            return docID
                        }

                        @Throws(IOException::class)
                        override fun advance(target: Int): Int {
                            throw UnsupportedOperationException()
                        }

                        @Throws(IOException::class)
                        override fun advanceExact(target: Int): Boolean {
                            throw UnsupportedOperationException()
                        }

                        override fun cost(): Long {
                            return 0
                        }

                        @Throws(IOException::class)
                        override fun longValue(): Long {
                            return current!!.values.longValue()
                        }
                    }
                }

                override fun checkIntegrity() {}

                override fun close() {}
            })
    }
}
