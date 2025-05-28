package org.gnit.lucenekmp.codecs

import okio.IOException
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues

/**
 * Abstract API that produces numeric, binary, sorted, sortedset, and sortednumeric docvalues.
 *
 * @lucene.experimental
 */
abstract class DocValuesProducer
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {
    /**
     * Returns [NumericDocValues] for this field. The returned instance need not be thread-safe:
     * it will only be used by a single thread. The behavior is undefined if the doc values type of
     * the given field is not [DocValuesType.NUMERIC]. The return value is never `null`.
     */
    @Throws(IOException::class)
    abstract fun getNumeric(field: FieldInfo): NumericDocValues

    /**
     * Returns [BinaryDocValues] for this field. The returned instance need not be thread-safe:
     * it will only be used by a single thread. The behavior is undefined if the doc values type of
     * the given field is not [DocValuesType.BINARY]. The return value is never `null`.
     */
    @Throws(IOException::class)
    abstract fun getBinary(field: FieldInfo): BinaryDocValues

    /**
     * Returns [SortedDocValues] for this field. The returned instance need not be thread-safe:
     * it will only be used by a single thread. The behavior is undefined if the doc values type of
     * the given field is not [DocValuesType.SORTED]. The return value is never `null`.
     */
    @Throws(IOException::class)
    abstract fun getSorted(field: FieldInfo): SortedDocValues

    /**
     * Returns [SortedNumericDocValues] for this field. The returned instance need not be
     * thread-safe: it will only be used by a single thread. The behavior is undefined if the doc
     * values type of the given field is not [DocValuesType.SORTED_NUMERIC]. The return value is
     * never `null`.
     */
    @Throws(IOException::class)
    abstract fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues

    /**
     * Returns [SortedSetDocValues] for this field. The returned instance need not be
     * thread-safe: it will only be used by a single thread. The behavior is undefined if the doc
     * values type of the given field is not [DocValuesType.SORTED_SET]. The return value is
     * never `null`.
     */
    @Throws(IOException::class)
    abstract fun getSortedSet(field: FieldInfo): SortedSetDocValues

    /**
     * Returns a [DocValuesSkipper] for this field. The returned instance need not be
     * thread-safe: it will only be used by a single thread. The return value is undefined if [ ][FieldInfo.docValuesSkipIndexType] returns [DocValuesSkipIndexType.NONE].
     */
    @Throws(IOException::class)
    abstract fun getSkipper(field: FieldInfo): DocValuesSkipper?

    /**
     * Checks consistency of this producer
     *
     *
     * Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
     * against large data files.
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    abstract fun checkIntegrity()

    open val mergeInstance: DocValuesProducer
        /**
         * Returns an instance optimized for merging. This instance may only be consumed in the thread
         * that called [.getMergeInstance].
         *
         *
         * The default implementation returns `this`
         */
        get() = this
}
