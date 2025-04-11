package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.MappedMultiFields
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.MultiFields
import org.gnit.lucenekmp.index.ReaderSlice

/**
 * Abstract API that consumes terms, doc, freq, prox, offset and payloads postings. Concrete
 * implementations of this actually do "something" with the postings (write it into the index in a
 * specific format).
 *
 * @lucene.experimental
 */
abstract class FieldsConsumer
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {
    // TODO: can we somehow compute stats for you...
    // TODO: maybe we should factor out "limited" (only
    // iterables, no counts/stats) base classes from
    // Fields/Terms/Docs/AndPositions
    /**
     * Write all fields, terms and postings. This the "pull" API, allowing you to iterate more than
     * once over the postings, somewhat analogous to using a DOM API to traverse an XML tree.
     *
     *
     * **Notes**:
     *
     *
     *  * You must compute index statistics, including each Term's docFreq and totalTermFreq, as
     * well as the summary sumTotalTermFreq, sumTotalDocFreq and docCount.
     *  * You must skip terms that have no docs and fields that have no terms, even though the
     * provided Fields API will expose them; this typically requires lazily writing the field or
     * term until you've actually seen the first term or document.
     *  * The provided Fields instance is limited: you cannot call any methods that return
     * statistics/counts; you cannot pass a non-null live docs when pulling docs/positions
     * enums.
     *
     */
    @Throws(IOException::class)
    abstract fun write(fields: Fields, norms: NormsProducer)

    /**
     * Merges in the fields from the readers in `mergeState`. The default implementation
     * skips and maps around deleted documents, and calls [.write].
     * Implementations can override this method for more sophisticated merging (bulk-byte copying,
     * etc).
     */
    @Throws(IOException::class)
    fun merge(mergeState: MergeState, norms: NormsProducer) {
        val fields: MutableList<Fields> = mutableListOf<Fields>()
        val slices: MutableList<ReaderSlice> = mutableListOf<ReaderSlice>()

        var docBase = 0

        for (readerIndex in 0..<mergeState.fieldsProducers.size) {
            val f: FieldsProducer? = mergeState.fieldsProducers[readerIndex]

            val maxDoc: Int = mergeState.maxDocs[readerIndex]
            if (f != null) {
                f.checkIntegrity()
                slices.add(ReaderSlice(docBase, maxDoc, readerIndex))
                fields.add(f)
            }
            docBase += maxDoc
        }

        val mergedFields: Fields =
            MappedMultiFields(
                mergeState,
                MultiFields(
                    fields.toTypedArray(), slices.toTypedArray()
                )
            )
        write(mergedFields, norms)
    }

    // NOTE: strange but necessary so javadocs linting is happy:
    @Throws(IOException::class)
    abstract override fun close()
}
