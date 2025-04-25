package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocIDMerger
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.EmptyDocValuesProducer
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FilteredTermsEnum
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.OrdinalMap
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.LongBitSet
import org.gnit.lucenekmp.util.LongValues
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.search.DocIdSetIterator
import kotlinx.io.IOException

/**
 * Abstract API that consumes numeric, binary and sorted docvalues. Concrete implementations of this
 * actually do "something" with the docvalues (write it into the index in a specific format).
 *
 *
 * The lifecycle is:
 *
 *
 *  1. DocValuesConsumer is created by [DocValuesFormat.fieldsConsumer].
 *  1. [.addNumericField], [.addBinaryField], [.addSortedField], [       ][.addSortedSetField], or [.addSortedNumericField] are called for each Numeric, Binary,
 * Sorted, SortedSet, or SortedNumeric docvalues field. The API is a "pull" rather than
 * "push", and the implementation is free to iterate over the values multiple times ([       ][Iterable.iterator]).
 *  1. After all fields are added, the consumer is [.close]d.
 *
 *
 * @lucene.experimental
 */
abstract class DocValuesConsumer
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {
    /**
     * Writes numeric docvalues for a field.
     *
     * @param field field information
     * @param valuesProducer Numeric values to write.
     * @throws IOException if an I/O error occurred.
     */
    @Throws(IOException::class)
    abstract fun addNumericField(field: FieldInfo, valuesProducer: DocValuesProducer)

    /**
     * Writes binary docvalues for a field.
     *
     * @param field field information
     * @param valuesProducer Binary values to write.
     * @throws IOException if an I/O error occurred.
     */
    @Throws(IOException::class)
    abstract fun addBinaryField(field: FieldInfo, valuesProducer: DocValuesProducer)

    /**
     * Writes pre-sorted binary docvalues for a field.
     *
     * @param field field information
     * @param valuesProducer produces the values and ordinals to write
     * @throws IOException if an I/O error occurred.
     */
    @Throws(IOException::class)
    abstract fun addSortedField(field: FieldInfo, valuesProducer: DocValuesProducer)

    /**
     * Writes pre-sorted numeric docvalues for a field
     *
     * @param field field information
     * @param valuesProducer produces the values to write
     * @throws IOException if an I/O error occurred.
     */
    @Throws(IOException::class)
    abstract fun addSortedNumericField(field: FieldInfo, valuesProducer: DocValuesProducer)

    /**
     * Writes pre-sorted set docvalues for a field
     *
     * @param field field information
     * @param valuesProducer produces the values to write
     * @throws IOException if an I/O error occurred.
     */
    @Throws(IOException::class)
    abstract fun addSortedSetField(field: FieldInfo, valuesProducer: DocValuesProducer)

    /**
     * Merges in the fields from the readers in `mergeState`. The default implementation
     * calls [.mergeNumericField], [.mergeBinaryField], [.mergeSortedField], [ ][.mergeSortedSetField], or [.mergeSortedNumericField] for each field, depending on its
     * type. Implementations can override this method for more sophisticated merging (bulk-byte
     * copying, etc).
     */
    @Throws(IOException::class)
    open fun merge(mergeState: MergeState) {
        for (docValuesProducer in mergeState.docValuesProducers) {
            docValuesProducer?.checkIntegrity()
        }

        for (mergeFieldInfo in mergeState.mergeFieldInfos!!) {
            val type: DocValuesType = mergeFieldInfo.getDocValuesType()
            if (type !== DocValuesType.NONE) {
                if (type === DocValuesType.NUMERIC) {
                    mergeNumericField(mergeFieldInfo, mergeState)
                } else if (type === DocValuesType.BINARY) {
                    mergeBinaryField(mergeFieldInfo, mergeState)
                } else if (type === DocValuesType.SORTED) {
                    mergeSortedField(mergeFieldInfo, mergeState)
                } else if (type === DocValuesType.SORTED_SET) {
                    mergeSortedSetField(mergeFieldInfo, mergeState)
                } else if (type === DocValuesType.SORTED_NUMERIC) {
                    mergeSortedNumericField(mergeFieldInfo, mergeState)
                } else {
                    throw AssertionError("type=$type")
                }
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
     * Merges the numeric docvalues from `MergeState`.
     *
     *
     * The default implementation calls [.addNumericField], passing a DocValuesProducer that
     * merges and filters deleted documents on the fly.
     */
    @Throws(IOException::class)
    fun mergeNumericField(mergeFieldInfo: FieldInfo, mergeState: MergeState) {
        addNumericField(
            mergeFieldInfo,
            object : EmptyDocValuesProducer() {
                @Throws(IOException::class)
                override fun getNumeric(fieldInfo: FieldInfo): NumericDocValues {
                    require(fieldInfo === mergeFieldInfo) { "wrong fieldInfo" }

                    val subs: MutableList<NumericDocValuesSub> = mutableListOf<NumericDocValuesSub>()
                    require(mergeState.docMaps!!.size == mergeState.docValuesProducers.size)
                    for (i in 0..<mergeState.docValuesProducers.size) {
                        var values: NumericDocValues? = null
                        val docValuesProducer: DocValuesProducer? = mergeState.docValuesProducers[i]
                        if (docValuesProducer != null) {
                            val readerFieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(mergeFieldInfo.name)
                            if (readerFieldInfo != null
                                && readerFieldInfo.getDocValuesType() === DocValuesType.NUMERIC
                            ) {
                                values = docValuesProducer.getNumeric(readerFieldInfo)
                            }
                        }
                        if (values != null) {
                            subs.add(NumericDocValuesSub(mergeState.docMaps[i], values))
                        }
                    }

                    return mergeNumericValues(subs, mergeState.needsIndexSort)
                }
            })
    }

    /** Tracks state of one binary sub-reader that we are merging  */
    private class BinaryDocValuesSub(docMap: MergeState.DocMap, val values: BinaryDocValues) : DocIDMerger.Sub(docMap) {

        init {
            require(values.docID() == -1)
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return values.nextDoc()
        }
    }

    /**
     * Merges the binary docvalues from `MergeState`.
     *
     *
     * The default implementation calls [.addBinaryField], passing a DocValuesProducer that
     * merges and filters deleted documents on the fly.
     */
    @Throws(IOException::class)
    fun mergeBinaryField(mergeFieldInfo: FieldInfo, mergeState: MergeState) {
        addBinaryField(
            mergeFieldInfo,
            object : EmptyDocValuesProducer() {
                @Throws(IOException::class)
                override fun getBinary(fieldInfo: FieldInfo): BinaryDocValues {
                    require(fieldInfo === mergeFieldInfo) { "wrong fieldInfo" }

                    val subs: MutableList<BinaryDocValuesSub> = mutableListOf<BinaryDocValuesSub>()

                    var cost: Long = 0
                    for (i in 0..<mergeState.docValuesProducers.size) {
                        var values: BinaryDocValues? = null
                        val docValuesProducer: DocValuesProducer? = mergeState.docValuesProducers[i]
                        if (docValuesProducer != null) {
                            val readerFieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(mergeFieldInfo.name)
                            if (readerFieldInfo != null
                                && readerFieldInfo.getDocValuesType() === DocValuesType.BINARY
                            ) {
                                values = docValuesProducer.getBinary(readerFieldInfo)
                            }
                        }
                        if (values != null) {
                            cost += values.cost()
                            subs.add(BinaryDocValuesSub(mergeState.docMaps!![i], values))
                        }
                    }

                    val docIDMerger: DocIDMerger<BinaryDocValuesSub> =
                        DocIDMerger.of(subs, mergeState.needsIndexSort)
                    val finalCost = cost

                    return object : BinaryDocValues() {
                        private var current: BinaryDocValuesSub? = null
                        private var docID = -1

                        override fun docID(): Int {
                            return docID
                        }

                        @Throws(IOException::class)
                        override fun nextDoc(): Int {
                            current = docIDMerger.next()
                            current?.let { docID = it.mappedDocID }
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
                            return finalCost
                        }

                        @Throws(IOException::class)
                        override fun binaryValue(): BytesRef {
                            return current!!.values.binaryValue()
                        }
                    }
                }
            })
    }

    /** Tracks state of one sorted numeric sub-reader that we are merging  */
    private class SortedNumericDocValuesSub(docMap: MergeState.DocMap, val values: SortedNumericDocValues) :
        DocIDMerger.Sub(docMap) {

        init {
            require(values.docID() == -1)
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return values.nextDoc()
        }
    }

    /**
     * Merges the sorted docvalues from `toMerge`.
     *
     *
     * The default implementation calls [.addSortedNumericField], passing iterables that
     * filter deleted documents.
     */
    @Throws(IOException::class)
    fun mergeSortedNumericField(mergeFieldInfo: FieldInfo, mergeState: MergeState) {
        addSortedNumericField(
            mergeFieldInfo,
            object : EmptyDocValuesProducer() {
                @Throws(IOException::class)
                override fun getSortedNumeric(fieldInfo: FieldInfo): SortedNumericDocValues {
                    require(fieldInfo === mergeFieldInfo) { "wrong FieldInfo" }

                    // We must make new iterators + DocIDMerger for each iterator:
                    val subs: MutableList<SortedNumericDocValuesSub> = mutableListOf<SortedNumericDocValuesSub>()
                    var cost: Long = 0
                    var allSingletons = true
                    for (i in 0..<mergeState.docValuesProducers.size) {
                        val docValuesProducer: DocValuesProducer? = mergeState.docValuesProducers[i]
                        var values: SortedNumericDocValues? = null
                        if (docValuesProducer != null) {
                            val readerFieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(mergeFieldInfo.name)
                            if (readerFieldInfo != null
                                && readerFieldInfo.getDocValuesType() === DocValuesType.SORTED_NUMERIC
                            ) {
                                values = docValuesProducer.getSortedNumeric(readerFieldInfo)
                            }
                        }
                        if (values == null) {
                            values = DocValues.emptySortedNumeric()
                        }
                        cost += values.cost()
                        if (allSingletons && DocValues.unwrapSingleton(values) == null) {
                            allSingletons = false
                        }
                        subs.add(SortedNumericDocValuesSub(mergeState.docMaps!![i], values))
                    }

                    if (allSingletons) {
                        // All subs are single-valued.
                        // We specialize for that case since it makes it easier for codecs to optimize
                        // for single-valued fields.
                        val singleValuedSubs: MutableList<NumericDocValuesSub> =
                            mutableListOf<NumericDocValuesSub>()
                        for (sub in subs) {
                            val singleValuedValues: NumericDocValues =
                                checkNotNull(DocValues.unwrapSingleton(sub.values))
                            singleValuedSubs.add(NumericDocValuesSub(sub.docMap, singleValuedValues))
                        }
                        return DocValues.singleton(
                            mergeNumericValues(singleValuedSubs, mergeState.needsIndexSort)
                        )
                    }

                    val finalCost = cost

                    val docIDMerger: DocIDMerger<SortedNumericDocValuesSub> =
                        DocIDMerger.of(subs, mergeState.needsIndexSort)

                    return object : SortedNumericDocValues() {
                        private var docID = -1
                        private var currentSub: SortedNumericDocValuesSub? = null

                        override fun docID(): Int {
                            return docID
                        }

                        @Throws(IOException::class)
                        override fun nextDoc(): Int {
                            currentSub = docIDMerger.next()
                            currentSub?.let { docID = it.mappedDocID }

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

                        override fun docValueCount(): Int {
                            return currentSub!!.values.docValueCount()
                        }

                        override fun cost(): Long {
                            return finalCost
                        }

                        @Throws(IOException::class)
                        override fun nextValue(): Long {
                            return currentSub!!.values.nextValue()
                        }
                    }
                }
            })
    }

    /**
     * A merged [TermsEnum]. This helps avoid relying on the default terms enum, which calls
     * [SortedDocValues.lookupOrd] or [SortedSetDocValues.lookupOrd] on every
     * call to [TermsEnum.next].
     */
    private class MergedTermsEnum(private val ordinalMap: OrdinalMap, private val subs: Array<TermsEnum>) : BaseTermsEnum() {
        private val valueCount: Long = ordinalMap.valueCount
        private var ord: Long = -1
        private var term: BytesRef? = null

        @Throws(IOException::class)
        override fun term(): BytesRef? {
            return term
        }

        @Throws(IOException::class)
        override fun ord(): Long {
            return ord
        }

        @Throws(IOException::class)
        override fun next(): BytesRef? {
            if (++ord >= valueCount) {
                return null
            }
            val subNum: Int = ordinalMap.getFirstSegmentNumber(ord)
            val sub: TermsEnum = subs[subNum]
            val subOrd: Long = ordinalMap.getFirstSegmentOrd(ord)
            do {
                term = sub.next()
            } while (sub.ord() < subOrd)
            require(sub.ord() == subOrd)
            return term
        }

        override fun attributes(): AttributeSource {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun seekCeil(text: BytesRef): SeekStatus {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun seekExact(ord: Long) {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun docFreq(): Int {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun totalTermFreq(): Long {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun impacts(flags: Int): ImpactsEnum {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun termState(): TermState {
            throw UnsupportedOperationException()
        }
    }

    /** Tracks state of one sorted sub-reader that we are merging  */
    private class SortedDocValuesSub(docMap: MergeState.DocMap, val values: SortedDocValues, val map: LongValues) :
        DocIDMerger.Sub(docMap) {

        init {
            require(values.docID() == -1)
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return values.nextDoc()
        }
    }

    /**
     * Merges the sorted docvalues from `toMerge`.
     *
     *
     * The default implementation calls [.addSortedField], passing an Iterable that merges
     * ordinals and values and filters deleted documents .
     */
    @Throws(IOException::class)
    fun mergeSortedField(fieldInfo: FieldInfo, mergeState: MergeState) {
        val toMerge: MutableList<SortedDocValues> = mutableListOf<SortedDocValues>()
        for (i in 0..<mergeState.docValuesProducers.size) {
            var values: SortedDocValues?? = null
            val docValuesProducer: DocValuesProducer? = mergeState.docValuesProducers[i]
            if (docValuesProducer != null) {
                val readerFieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(fieldInfo.name)
                if (readerFieldInfo != null && readerFieldInfo.getDocValuesType() === DocValuesType.SORTED) {
                    values = docValuesProducer.getSorted(readerFieldInfo)
                }
            }
            if (values == null) {
                values = DocValues.emptySorted()
            }
            toMerge.add(values)
        }

        val numReaders = toMerge.size
        val dvs: Array<SortedDocValues> = toMerge.toTypedArray<SortedDocValues>()

        // step 1: iterate thru each sub and mark terms still in use
        val liveTerms: Array<TermsEnum?> = kotlin.arrayOfNulls<TermsEnum>(dvs.size)
        val weights = LongArray(liveTerms.size)
        for (sub in 0..<numReaders) {
            val dv: SortedDocValues = dvs[sub]
            val liveDocs: Bits? = mergeState.liveDocs[sub]
            if (liveDocs == null) {
                liveTerms[sub] = dv.termsEnum()
                weights[sub] = dv.valueCount.toLong()
            } else {
                val bitset = LongBitSet(dv.valueCount.toLong())
                var docID: Int
                while ((dv.nextDoc().also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                    if (liveDocs.get(docID)) {
                        val ord: Int = dv.ordValue()
                        if (ord >= 0) {
                            bitset.set(ord.toLong())
                        }
                    }
                }
                liveTerms[sub] = BitsFilteredTermsEnum(dv.termsEnum()!!, bitset)
                weights[sub] = bitset.cardinality()
            }
        }

        // step 2: create ordinal map (this conceptually does the "merging")
        val map: OrdinalMap = OrdinalMap.build(null, liveTerms as Array<TermsEnum>, weights, PackedInts.COMPACT)

        // step 3: add field
        addSortedField(
            fieldInfo,
            object : EmptyDocValuesProducer() {
                @Throws(IOException::class)
                override fun getSorted(fieldInfoIn: FieldInfo): SortedDocValues {
                    require(fieldInfoIn === fieldInfo) { "wrong FieldInfo" }

                    // We must make new iterators + DocIDMerger for each iterator:
                    val subs: MutableList<SortedDocValuesSub> = mutableListOf<SortedDocValuesSub>()
                    for (i in 0..<mergeState.docValuesProducers.size) {
                        var values: SortedDocValues? = null
                        val docValuesProducer: DocValuesProducer? = mergeState.docValuesProducers[i]
                        if (docValuesProducer != null) {
                            val readerFieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(fieldInfo.name)
                            if (readerFieldInfo != null
                                && readerFieldInfo.getDocValuesType() === DocValuesType.SORTED
                            ) {
                                values = docValuesProducer.getSorted(readerFieldInfo)
                            }
                        }
                        if (values == null) {
                            values = DocValues.emptySorted()
                        }

                        subs.add(SortedDocValuesSub(mergeState.docMaps!![i], values, map.getGlobalOrds(i)))
                    }

                    return mergeSortedValues(subs, mergeState.needsIndexSort, map)
                }
            })
    }

    /** Tracks state of one sorted set sub-reader that we are merging  */
    private class SortedSetDocValuesSub(docMap: MergeState.DocMap, val values: SortedSetDocValues, val map: LongValues) :
        DocIDMerger.Sub(docMap) {

        init {
            require(values.docID() == -1)
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return values.nextDoc()
        }

        override fun toString(): String {
            return "SortedSetDocValuesSub(mappedDocID=$mappedDocID values=$values)"
        }
    }

    /**
     * Merges the sortedset docvalues from `toMerge`.
     *
     *
     * The default implementation calls [.addSortedSetField], passing an Iterable that merges
     * ordinals and values and filters deleted documents .
     */
    @Throws(IOException::class)
    fun mergeSortedSetField(mergeFieldInfo: FieldInfo, mergeState: MergeState) {
        val toMerge: MutableList<SortedSetDocValues> = mutableListOf<SortedSetDocValues>()
        for (i in 0..<mergeState.docValuesProducers.size) {
            var values: SortedSetDocValues? = null
            val docValuesProducer: DocValuesProducer? = mergeState.docValuesProducers[i]
            if (docValuesProducer != null) {
                val fieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(mergeFieldInfo.name)
                if (fieldInfo != null && fieldInfo.getDocValuesType() === DocValuesType.SORTED_SET) {
                    values = docValuesProducer.getSortedSet(fieldInfo)
                }
            }
            if (values == null) {
                values = DocValues.emptySortedSet()
            }
            toMerge.add(values)
        }

        // step 1: iterate thru each sub and mark terms still in use
        val liveTerms: Array<TermsEnum?> = kotlin.arrayOfNulls<TermsEnum>(toMerge.size)
        val weights = LongArray(liveTerms.size)
        for (sub in liveTerms.indices) {
            val dv: SortedSetDocValues = toMerge[sub]
            val liveDocs: Bits? = mergeState.liveDocs[sub]
            if (liveDocs == null) {
                liveTerms[sub] = dv.termsEnum()
                weights[sub] = dv.valueCount
            } else {
                val bitset = LongBitSet(dv.valueCount)
                var docID: Int
                while ((dv.nextDoc().also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                    if (liveDocs.get(docID)) {
                        for (i in 0..<dv.docValueCount()) {
                            bitset.set(dv.nextOrd())
                        }
                    }
                }
                liveTerms[sub] = BitsFilteredTermsEnum(dv.termsEnum(), bitset)
                weights[sub] = bitset.cardinality()
            }
        }

        // step 2: create ordinal map (this conceptually does the "merging")
        val map: OrdinalMap = OrdinalMap.build(null, liveTerms as Array<TermsEnum>, weights, PackedInts.COMPACT)

        // step 3: add field
        addSortedSetField(
            mergeFieldInfo,
            object : EmptyDocValuesProducer() {
                @Throws(IOException::class)
                override fun getSortedSet(fieldInfo: FieldInfo): SortedSetDocValues {
                    require(fieldInfo === mergeFieldInfo) { "wrong FieldInfo" }

                    // We must make new iterators + DocIDMerger for each iterator:
                    val subs: MutableList<SortedSetDocValuesSub> = mutableListOf<SortedSetDocValuesSub>()

                    var cost: Long = 0
                    var allSingletons = true

                    for (i in 0..<mergeState.docValuesProducers.size) {
                        var values: SortedSetDocValues? = null
                        val docValuesProducer: DocValuesProducer? = mergeState.docValuesProducers[i]
                        if (docValuesProducer != null) {
                            val readerFieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(mergeFieldInfo.name)
                            if (readerFieldInfo != null
                                && readerFieldInfo.getDocValuesType() === DocValuesType.SORTED_SET
                            ) {
                                values = docValuesProducer.getSortedSet(readerFieldInfo)
                            }
                        }
                        if (values == null) {
                            values = DocValues.emptySortedSet()
                        }
                        cost += values.cost()
                        if (allSingletons && DocValues.unwrapSingleton(values) == null) {
                            allSingletons = false
                        }
                        subs.add(
                            SortedSetDocValuesSub(mergeState.docMaps!![i], values, map.getGlobalOrds(i))
                        )
                    }

                    if (allSingletons) {
                        // All subs are single-valued.
                        // We specialize for that case since it makes it easier for codecs to optimize
                        // for single-valued fields.
                        val singleValuedSubs: MutableList<SortedDocValuesSub> =
                            mutableListOf<SortedDocValuesSub>()
                        for (sub in subs) {
                            val singleValuedValues: SortedDocValues =
                                checkNotNull(DocValues.unwrapSingleton(sub.values))
                            singleValuedSubs.add(
                                SortedDocValuesSub(sub.docMap, singleValuedValues, sub.map)
                            )
                        }
                        return DocValues.singleton(
                            mergeSortedValues(singleValuedSubs, mergeState.needsIndexSort, map)
                        )
                    }

                    val docIDMerger: DocIDMerger<SortedSetDocValuesSub> =
                        DocIDMerger.of(subs, mergeState.needsIndexSort)

                    val finalCost = cost

                    return object : SortedSetDocValues() {
                        private var docID = -1
                        private var currentSub: SortedSetDocValuesSub? = null

                        override fun docID(): Int {
                            return docID
                        }

                        @Throws(IOException::class)
                        override fun nextDoc(): Int {
                            currentSub = docIDMerger.next()
                            currentSub?.let { docID = it.mappedDocID }

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

                        @Throws(IOException::class)
                        override fun nextOrd(): Long {
                            val subOrd: Long = currentSub!!.values.nextOrd()
                            return currentSub!!.map.get(subOrd)
                        }

                        override fun docValueCount(): Int {
                            return currentSub!!.values.docValueCount()
                        }

                        override fun cost(): Long {
                            return finalCost
                        }

                        @Throws(IOException::class)
                        override fun lookupOrd(ord: Long): BytesRef {
                            val segmentNumber: Int = map.getFirstSegmentNumber(ord)
                            val segmentOrd: Long = map.getFirstSegmentOrd(ord)
                            return toMerge[segmentNumber].lookupOrd(segmentOrd)
                        }

                        override val valueCount: Long
                            get() = map.valueCount

                        @Throws(IOException::class)
                        override fun termsEnum(): TermsEnum {
                            val subs: Array<TermsEnum?> = kotlin.arrayOfNulls<TermsEnum>(toMerge.size)
                            for (sub in subs.indices) {
                                subs[sub] = toMerge[sub].termsEnum()
                            }
                            return MergedTermsEnum(map, subs as Array<TermsEnum>)
                        }
                    }
                }
            })
    }

    // TODO: seek-by-ord to nextSetBit
    internal class BitsFilteredTermsEnum(`in`: TermsEnum, liveTerms: LongBitSet) : FilteredTermsEnum(`in`, false) {
        val liveTerms: LongBitSet

        init {
            checkNotNull(liveTerms)
            this.liveTerms = liveTerms
        }

        @Throws(IOException::class)
        override fun accept(term: BytesRef): AcceptStatus {
            if (liveTerms.get(ord())) {
                return AcceptStatus.YES
            } else {
                return AcceptStatus.NO
            }
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun mergeNumericValues(
            subs: MutableList<NumericDocValuesSub>, indexIsSorted: Boolean
        ): NumericDocValues {
            var cost: Long = 0
            for (sub in subs) {
                cost += sub.values.cost()
            }
            val finalCost = cost

            val docIDMerger: DocIDMerger<NumericDocValuesSub> = DocIDMerger.of(subs, indexIsSorted)

            return object : NumericDocValues() {
                private var docID = -1
                private var current: NumericDocValuesSub? = null

                override fun docID(): Int {
                    return docID
                }

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    current = docIDMerger.next()
                    if (current == null) {
                        docID = NO_MORE_DOCS
                    } else {
                        docID = current!!.mappedDocID
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
                    return finalCost
                }

                @Throws(IOException::class)
                override fun longValue(): Long {
                    return current!!.values.longValue()
                }
            }
        }

        @Throws(IOException::class)
        private fun mergeSortedValues(
            subs: MutableList<SortedDocValuesSub>, indexIsSorted: Boolean, map: OrdinalMap
        ): SortedDocValues {
            var cost: Long = 0
            for (sub in subs) {
                cost += sub.values.cost()
            }
            val finalCost = cost

            val docIDMerger: DocIDMerger<SortedDocValuesSub> = DocIDMerger.of(subs, indexIsSorted)

            return object : SortedDocValues() {
                private var docID = -1
                private var current: SortedDocValuesSub? = null

                override fun docID(): Int {
                    return docID
                }

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    current = docIDMerger.next()
                    if (current == null) {
                        docID = NO_MORE_DOCS
                    } else {
                        docID = current!!.mappedDocID
                    }
                    return docID
                }

                @Throws(IOException::class)
                override fun ordValue(): Int {
                    val subOrd: Int = current!!.values.ordValue()
                    require(subOrd != -1)
                    return current!!.map.get(subOrd.toLong()).toInt()
                }

                override fun advance(target: Int): Int {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun advanceExact(target: Int): Boolean {
                    throw UnsupportedOperationException()
                }

                override fun cost(): Long {
                    return finalCost
                }

                override val valueCount: Int
                    get() = map.valueCount.toInt()

                @Throws(IOException::class)
                override fun lookupOrd(ord: Int): BytesRef {
                    val segmentNumber: Int = map.getFirstSegmentNumber(ord.toLong())
                    val segmentOrd = map.getFirstSegmentOrd(ord.toLong()).toInt()
                    return subs[segmentNumber].values.lookupOrd(segmentOrd)
                }

                @Throws(IOException::class)
                override fun termsEnum(): TermsEnum {
                    val termsEnumSubs: Array<TermsEnum?> = kotlin.arrayOfNulls<TermsEnum>(subs.size)
                    for (sub in termsEnumSubs.indices) {
                        termsEnumSubs[sub] = subs[sub].values.termsEnum()
                    }
                    return MergedTermsEnum(map, termsEnumSubs as Array<TermsEnum>)
                }
            }
        }

        /** Helper: returns true if the given docToValue count contains only at most one value  */
        fun isSingleValued(docToValueCount: Iterable<Number>): Boolean {
            for (count in docToValueCount) {
                if (count.toLong() > 1) {
                    return false
                }
            }
            return true
        }

        /** Helper: returns single-valued view, using `missingValue` when count is zero  */
        fun singletonView(
            docToValueCount: Iterable<Number>,
            values: Iterable<Number>,
            missingValue: Number
        ): Iterable<Number> {
            require(isSingleValued(docToValueCount))
            return object : Iterable<Number> {
                override fun iterator(): MutableIterator<Number> {
                    val countIterator: Iterator<Number> = docToValueCount.iterator()
                    val valuesIterator: Iterator<Number> = values.iterator()
                    return object : MutableIterator<Number> {
                        override fun hasNext(): Boolean {
                            return countIterator.hasNext()
                        }

                        override fun next(): Number {
                            val count = countIterator.next().toInt()
                            if (count == 0) {
                                return missingValue
                            } else {
                                return valuesIterator.next()
                            }
                        }

                        override fun remove() {
                            throw UnsupportedOperationException()
                        }
                    }
                }
            }
        }
    }
}
