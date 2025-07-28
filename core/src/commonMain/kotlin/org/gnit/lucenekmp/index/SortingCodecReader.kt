package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.index.FreqProxTermsWriter.SortingTerms
import org.gnit.lucenekmp.index.NumericDocValuesWriter.NumericDVs
import org.gnit.lucenekmp.index.NumericDocValuesWriter.SortingNumericDocValues
import org.gnit.lucenekmp.index.SortedDocValuesWriter.SortingSortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValuesWriter.SortingSortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValuesWriter.DocOrds
import org.gnit.lucenekmp.index.SortedSetDocValuesWriter.SortingSortedSetDocValues
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.packed.PackedInts


/**
 * An [CodecReader] which supports sorting documents by a given [ ]. This can be used to re-sort and index after it's been created by wrapping all readers of
 * the index with this reader and adding it to a fresh IndexWriter via [ ][IndexWriter.addIndexes]. NOTE: This reader should only be used for merging.
 * Pulling fields from this reader might be very costly and memory intensive.
 *
 * @lucene.experimental
 */
class SortingCodecReader private constructor(
    `in`: CodecReader,
    // pkg-protected to avoid synthetic accessor methods
    val docMap: Sorter.DocMap,
    override val metaData: LeafMetaData
) : FilterCodecReader(`in`) {
    private class SortingBits(val `in`: Bits, val docMap: Sorter.DocMap) :
        Bits {
        override fun get(index: Int): Boolean {
            return `in`.get(docMap.newToOld(index))
        }

        override fun length(): Int {
            return `in`.length()
        }
    }

    private class SortingPointValues(
        private val `in`: PointValues,
        private val docMap: Sorter.DocMap
    ) : PointValues() {

        override val pointTree: PointTree
            get() = SortingPointTree(`in`.pointTree, docMap)

        override val minPackedValue: ByteArray
            get() = `in`.minPackedValue

        override val maxPackedValue: ByteArray
            get() = `in`.maxPackedValue

        override val numDimensions: Int
            get() = `in`.numDimensions

        override val numIndexDimensions: Int
            get() = `in`.numIndexDimensions

        override val bytesPerDimension: Int
            get() = `in`.bytesPerDimension

        override fun size(): Long {
            return `in`.size()
        }

        override val docCount: Int
            get() = `in`.docCount
    }

    private class SortingPointTree(
        private val indexTree: PointValues.PointTree,
        private val docMap: Sorter.DocMap
    ) : PointValues.PointTree {
        private val sortingIntersectVisitor: SortingIntersectVisitor = SortingIntersectVisitor(docMap)

        override fun clone(): PointValues.PointTree {
            return SortingPointTree(indexTree.clone(), docMap)
        }

        @Throws(IOException::class)
        override fun moveToChild(): Boolean {
            return indexTree.moveToChild()
        }

        @Throws(IOException::class)
        override fun moveToSibling(): Boolean {
            return indexTree.moveToSibling()
        }

        @Throws(IOException::class)
        override fun moveToParent(): Boolean {
            return indexTree.moveToParent()
        }

        override val minPackedValue: ByteArray
            get() = indexTree.minPackedValue

        override val maxPackedValue: ByteArray
            get() = indexTree.maxPackedValue

        override fun size(): Long {
            return indexTree.size()
        }

        @Throws(IOException::class)
        override fun visitDocIDs(visitor: PointValues.IntersectVisitor) {
            sortingIntersectVisitor.setIntersectVisitor(visitor)
            indexTree.visitDocIDs(sortingIntersectVisitor)
        }

        @Throws(IOException::class)
        override fun visitDocValues(visitor: PointValues.IntersectVisitor) {
            sortingIntersectVisitor.setIntersectVisitor(visitor)
            indexTree.visitDocValues(sortingIntersectVisitor)
        }
    }

    private class SortingIntersectVisitor(private val docMap: Sorter.DocMap) :
        PointValues.IntersectVisitor {

        private var visitor: PointValues.IntersectVisitor? = null

        fun setIntersectVisitor(visitor: PointValues.IntersectVisitor) {
            this.visitor = visitor
        }

        @Throws(IOException::class)
        override fun visit(docID: Int) {
            visitor!!.visit(docMap.oldToNew(docID))
        }

        @Throws(IOException::class)
        override fun visit(docID: Int, packedValue: ByteArray) {
            visitor!!.visit(docMap.oldToNew(docID), packedValue)
        }

        override fun compare(
            minPackedValue: ByteArray,
            maxPackedValue: ByteArray
        ): PointValues.Relation {
            return visitor!!.compare(minPackedValue, maxPackedValue)
        }
    }

    /**
     * Factory for SortingValuesIterator. This enables us to create new iterators as needed without
     * recomputing the sorting mappings.
     */
    class SortingIteratorSupplier(private val docBits: FixedBitSet, private val docToOrd: IntArray,
                                  private val size: Int
    )/* :
        java.util.function.Supplier<SortingValuesIterator>*/ {

        /*override*/ fun get(): SortingValuesIterator {
            return SortingValuesIterator(docBits, docToOrd, size)
        }

        fun size(): Int {
            return size
        }
    }

    /**
     * Iterator over KnnVectorValues accepting a mapping to differently-sorted docs. Consequently
     * index() may skip around, not increasing monotonically as iteration proceeds.
     */
    class SortingValuesIterator internal constructor(
        private val docBits: FixedBitSet,
        private val docToOrd: IntArray,
        size: Int
    ) : KnnVectorValues.DocIndexIterator() {
        private val docsWithValues: DocIdSetIterator = BitSetIterator(docBits, size.toLong())

        var doc: Int = -1

        override fun docID(): Int {
            return doc
        }

        override fun index(): Int {
            assert(docBits.get(doc))
            return docToOrd[doc]
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            if (doc != NO_MORE_DOCS) {
                doc = docsWithValues.nextDoc()
            }
            return doc
        }

        override fun cost(): Long {
            return docBits.cardinality().toLong()
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }
    }

    /** Sorting FloatVectorValues that maps ordinals using the provided sortMap  */
    private class SortingFloatVectorValues(
        val delegate: FloatVectorValues,
        sortMap: Sorter.DocMap
    ) : FloatVectorValues() {
        val iteratorSupplier: SortingIteratorSupplier

        init {
            checkNotNull(delegate)
            // SortingValuesIterator consumes the iterator and records the docs and ord mapping
            iteratorSupplier = iteratorSupplier(delegate, sortMap)
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): FloatArray {
            // ords are interpreted in the delegate's ord-space.
            return delegate.vectorValue(ord)
        }

        override fun dimension(): Int {
            return delegate.dimension()
        }

        override fun size(): Int {
            return iteratorSupplier.size()
        }

        override fun copy(): FloatVectorValues {
            throw UnsupportedOperationException()
        }

        override fun iterator(): DocIndexIterator {
            return iteratorSupplier.get()
        }
    }

    private class SortingByteVectorValues(
        val delegate: ByteVectorValues,
        sortMap: Sorter.DocMap
    ) : ByteVectorValues() {
        // SortingValuesIterator consumes the iterator and records the docs and ord mapping
        val iteratorSupplier: SortingIteratorSupplier = iteratorSupplier(delegate, sortMap)

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): ByteArray {
            return delegate.vectorValue(ord)
        }

        override fun iterator(): DocIndexIterator {
            return iteratorSupplier.get()
        }

        override fun dimension(): Int {
            return delegate.dimension()
        }

        override fun size(): Int {
            return iteratorSupplier.size()
        }

        override fun copy(): ByteVectorValues {
            throw UnsupportedOperationException()
        }
    }

    override val postingsReader: FieldsProducer?
        get() {
            val postingsReader: FieldsProducer? = `in`.postingsReader
            if (postingsReader == null) {
                return null
            }
            return object : FieldsProducer() {
                @Throws(IOException::class)
                override fun close() {
                    postingsReader.close()
                }

                @Throws(IOException::class)
                override fun checkIntegrity() {
                    postingsReader.checkIntegrity()
                }

                override fun iterator(): MutableIterator<String> {
                    return postingsReader.iterator()
                }

                @Throws(IOException::class)
                override fun terms(field: String?): Terms? {
                    val terms: Terms? = postingsReader.terms(field)
                    return if (terms == null)
                        null
                    else
                        SortingTerms(
                            terms, `in`.fieldInfos.fieldInfo(field!!)!!.indexOptions, docMap
                        )
                }

                override fun size(): Int {
                    return postingsReader.size()
                }
            }
        }

    override val fieldsReader: StoredFieldsReader?
        get() {
            val delegate: StoredFieldsReader? = `in`.fieldsReader
            if (delegate == null) {
                return null
            }
            return newStoredFieldsReader(delegate)
        }

    private fun newStoredFieldsReader(delegate: StoredFieldsReader): StoredFieldsReader {
        return object : StoredFieldsReader() {
            @Throws(IOException::class)
            override fun prefetch(docID: Int) {
                delegate.prefetch(docMap.newToOld(docID))
            }

            @Throws(IOException::class)
            override fun document(docID: Int, visitor: StoredFieldVisitor) {
                delegate.document(docMap.newToOld(docID), visitor)
            }

            override fun clone(): StoredFieldsReader {
                return newStoredFieldsReader(delegate.clone())
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                delegate.checkIntegrity()
            }

            @Throws(IOException::class)
            override fun close() {
                delegate.close()
            }
        }
    }

    override val liveDocs: Bits?
        get() {
            val inLiveDocs: Bits? = `in`.liveDocs
            return if (inLiveDocs == null) {
                null
            } else {
                SortingBits(inLiveDocs, docMap)
            }
        }

    override val pointsReader: PointsReader?
        get() {
            val delegate: PointsReader? = `in`.pointsReader
            if (delegate == null) {
                return null
            }
            return object : PointsReader() {
                @Throws(IOException::class)
                override fun checkIntegrity() {
                    delegate.checkIntegrity()
                }

                @Throws(IOException::class)
                override fun getValues(field: String): PointValues? {
                    val values: PointValues? = delegate.getValues(field)
                    if (values == null) {
                        return null
                    }
                    return SortingPointValues(delegate.getValues(field)!!, docMap)
                }

                @Throws(IOException::class)
                override fun close() {
                    delegate.close()
                }
            }
        }

    override val vectorReader: KnnVectorsReader?
        get() {
            val delegate: KnnVectorsReader? = `in`.vectorReader
            if (delegate == null) {
                return null
            }
            return object : KnnVectorsReader() {
                @Throws(IOException::class)
                override fun checkIntegrity() {
                    delegate.checkIntegrity()
                }

                @Throws(IOException::class)
                override fun getFloatVectorValues(field: String): FloatVectorValues {
                    return SortingFloatVectorValues(delegate.getFloatVectorValues(field)!!, docMap)
                }

                @Throws(IOException::class)
                override fun getByteVectorValues(field: String): ByteVectorValues {
                    return SortingByteVectorValues(delegate.getByteVectorValues(field)!!, docMap)
                }

                override fun search(
                    field: String,
                    target: FloatArray,
                    knnCollector: KnnCollector,
                    acceptDocs: Bits
                ) {
                    throw UnsupportedOperationException()
                }

                override fun search(
                    field: String,
                    target: ByteArray,
                    knnCollector: KnnCollector,
                    acceptDocs: Bits
                ) {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun close() {
                    delegate.close()
                }
            }
        }

    override val normsReader: NormsProducer?
        get() {
            val delegate: NormsProducer? = `in`.normsReader
            if (delegate == null) {
                return null
            }
            return object : NormsProducer() {
                @Throws(IOException::class)
                override fun getNorms(field: FieldInfo): NumericDocValues {
                    return SortingNumericDocValues(
                        getOrCreateNorms(
                            field.name
                        ) { getNumericDocValues(delegate.getNorms(field)) }
                    )
                }

                @Throws(IOException::class)
                override fun checkIntegrity() {
                    delegate.checkIntegrity()
                }

                @Throws(IOException::class)
                override fun close() {
                    delegate.close()
                }
            }
        }

    override val docValuesReader: DocValuesProducer?
        get() {
            val delegate: DocValuesProducer? = `in`.docValuesReader
            if (delegate == null) {
                return null
            }
            return object : DocValuesProducer() {
                @Throws(IOException::class)
                override fun getNumeric(field: FieldInfo): NumericDocValues {
                    return SortingNumericDocValues(
                        getOrCreateDV(
                            field.name
                        ) { getNumericDocValues(delegate.getNumeric(field)) }
                    )
                }

                @Throws(IOException::class)
                override fun getBinary(field: FieldInfo): BinaryDocValues {
                    return BinaryDocValuesWriter.SortingBinaryDocValues(
                        getOrCreateDV(
                            field.name
                        ) {
                            BinaryDocValuesWriter.BinaryDVs(
                                maxDoc(), docMap, delegate.getBinary(field)
                            )
                        }
                    )
                }

                @Throws(IOException::class)
                override fun getSorted(field: FieldInfo): SortedDocValues {
                    val oldDocValues: SortedDocValues = delegate.getSorted(field)
                    return SortingSortedDocValues(
                        oldDocValues,
                        getOrCreateDV(
                            field.name
                        ) {
                            val ords = IntArray(maxDoc())
                            Arrays.fill(ords, -1)
                            var docID: Int
                            while ((oldDocValues.nextDoc()
                                    .also { docID = it }) != NO_MORE_DOCS
                            ) {
                                val newDocID: Int = docMap.oldToNew(docID)
                                ords[newDocID] = oldDocValues.ordValue()
                            }
                            ords
                        }
                    )
                }

                @Throws(IOException::class)
                override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
                    val oldDocValues: SortedNumericDocValues = delegate.getSortedNumeric(field)
                    return SortingSortedNumericDocValues(
                        oldDocValues,
                        getOrCreateDV(
                            field.name
                        ) {
                            SortedNumericDocValuesWriter.LongValues(
                                maxDoc(), docMap, oldDocValues, PackedInts.FAST
                            )
                        }
                    )
                }

                @Throws(IOException::class)
                override fun getSortedSet(field: FieldInfo): SortedSetDocValues {
                    val oldDocValues: SortedSetDocValues = delegate.getSortedSet(field)
                    return SortingSortedSetDocValues(
                        oldDocValues,
                        this@SortingCodecReader.getOrCreateDV(
                            field.name
                        ) {
                            DocOrds(
                                maxDoc(),
                                docMap,
                                oldDocValues,
                                PackedInts.FAST,
                                DocOrds.START_BITS_PER_VALUE
                            )
                        }
                    )
                }

                @Throws(IOException::class)
                override fun checkIntegrity() {
                    delegate.checkIntegrity()
                }

                @Throws(IOException::class)
                override fun close() {
                    delegate.close()
                }

                @Throws(IOException::class)
                override fun getSkipper(field: FieldInfo): DocValuesSkipper? {
                    // We can hardly return information about min/max values if doc IDs have been reordered.
                    return null
                }
            }
        }

    @Throws(IOException::class)
    private fun getNumericDocValues(oldNumerics: NumericDocValues): NumericDVs {
        val docsWithField = FixedBitSet(maxDoc())
        val values = LongArray(maxDoc())
        var docID: Int
        while ((oldNumerics.nextDoc().also { docID = it }) != NO_MORE_DOCS) {
            val newDocID: Int = docMap.oldToNew(docID)
            docsWithField.set(newDocID)
            values[newDocID] = oldNumerics.longValue()
        }
        return NumericDVs(values, docsWithField)
    }

    override val termVectorsReader: TermVectorsReader?
        get() = newTermVectorsReader(`in`.termVectorsReader)

    private fun newTermVectorsReader(delegate: TermVectorsReader?): TermVectorsReader? {
        if (delegate == null) {
            return null
        }
        return object : TermVectorsReader() {
            @Throws(IOException::class)
            override fun prefetch(doc: Int) {
                delegate.prefetch(docMap.newToOld(doc))
            }

            @Throws(IOException::class)
            override fun get(doc: Int): Fields? {
                return delegate.get(docMap.newToOld(doc))
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                delegate.checkIntegrity()
            }

            override fun clone(): TermVectorsReader {
                return newTermVectorsReader(delegate.clone())!!
            }

            @Throws(IOException::class)
            override fun close() {
                delegate.close()
            }
        }
    }

    override fun toString(): String {
        return "SortingCodecReader($`in`)"
    }

    override val coreCacheHelper: CacheHelper
        // no caching on sorted views
        get() = /*null*/ throw UnsupportedOperationException(
            "SortingCodecReader does not support coreCacheHelper"
        )

    override val readerCacheHelper: CacheHelper
        get() = /*null*/ throw UnsupportedOperationException(
            "SortingCodecReader does not support readerCacheHelper"
        )

    /*override fun getMetaData(): LeafMetaData {
        return metaData
    }*/

    // we try to cache the last used DV or Norms instance since during merge
    // this instance is used more than once. We could in addition to this single instance
    // also cache the fields that are used for sorting since we do the work twice for these fields
    private var cachedField: String? = null
    private var cachedObject: Any? = null
    private var cacheIsNorms = false

    @Throws(IOException::class)
    private fun <T> getOrCreateNorms(field: String, supplier: IOSupplier<T>): T {
        return getOrCreate(field, true, supplier)
    }

    // TODO think what to do with this method, it is not thread-safe
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun <T> getOrCreate(field: String, norms: Boolean, supplier: IOSupplier<T>): T {
        if ((field == cachedField && cacheIsNorms == norms) == false) {
            assert(assertCreatedOnlyOnce(field, norms))
            cachedObject = supplier.get()
            cachedField = field
            cacheIsNorms = norms
        }
        checkNotNull(cachedObject)
        return cachedObject as T
    }

    private val cacheStats: MutableMap<String, Int> = HashMap() // only with assertions enabled

    private fun assertCreatedOnlyOnce(field: String, norms: Boolean): Boolean {
        // TODO think what to do with this method, it is not thread-safe
        /*assert(java.lang.Thread.holdsLock(this))*/

        // this is mainly there to make sure we change anything in the way we merge we realize it early
        val key = field + "N:" + norms
        val timesCached = cacheStats.getOrPut(key) { 0 } + 1
        cacheStats[key] = timesCached
        if (timesCached > 1) {
            assert(norms == false) { "[$field] norms must not be cached twice" }
            var isSortField = false
            // For things that aren't sort fields, it's possible for sort to be null here
            // In the event that we accidentally cache twice, its better not to throw an NPE
            if (metaData.sort != null) {
                for (sf in metaData.sort.sort) {
                    if (field == sf.field) {
                        isSortField = true
                        break
                    }
                }
            }
            assert(
                timesCached == 2
            ) {
                ("["
                        + field
                        + "] must not be cached more than twice but was cached: "
                        + timesCached
                        + " times isSortField: "
                        + isSortField)
            }
            assert(
                isSortField
            ) { "only sort fields should be cached twice but [$field] is not a sort field" }
        }
        return true
    }

    @Throws(IOException::class)
    private fun <T> getOrCreateDV(field: String, supplier: IOSupplier<T>): T {
        return getOrCreate(field, false, supplier)
    }

    companion object {
        /**
         * Creates a factory for SortingValuesIterator. Does the work of computing the (new docId to old
         * ordinal) mapping, and caches the result, enabling it to create new iterators cheaply.
         *
         * @param values the values over which to iterate
         * @param docMap the mapping from "old" docIds to "new" (sorted) docIds.
         */
        @Throws(IOException::class)
        fun iteratorSupplier(
            values: KnnVectorValues, docMap: Sorter.DocMap
        ): SortingIteratorSupplier {
            val docToOrd = IntArray(docMap.size())
            val docBits = FixedBitSet(docMap.size())
            var count = 0
            // Note: docToOrd will contain zero for docids that have no vector. This is OK though
            // because the iterator cannot be positioned on such docs
            val iter: KnnVectorValues.DocIndexIterator = values.iterator()
            var doc: Int = iter.nextDoc()
            while (doc != NO_MORE_DOCS) {
                val newDocId: Int = docMap.oldToNew(doc)
                if (newDocId != -1) {
                    docToOrd[newDocId] = iter.index()
                    docBits.set(newDocId)
                    ++count
                }
                doc = iter.nextDoc()
            }
            return SortingIteratorSupplier(docBits, docToOrd, count)
        }

        /**
         * Return a sorted view of `reader` according to the order defined by `sort`
         * . If the reader is already sorted, this method might return the reader as-is.
         */
        @Throws(IOException::class)
        fun wrap(
            reader: CodecReader,
            sort: Sort
        ): CodecReader {
            return wrap(reader, Sorter(sort).sort(reader)!!, sort)
        }

        /**
         * Expert: same as [.wrap] but operates directly
         * on a [Sorter.DocMap].
         */
        fun wrap(
            reader: CodecReader,
            docMap: Sorter.DocMap?,
            sort: Sort
        ): CodecReader {
            val metaData: LeafMetaData = reader.metaData
            val newMetaData =
                LeafMetaData(
                    metaData.createdVersionMajor, metaData.minVersion, sort, metaData.hasBlocks
                )
            if (docMap == null) {
                // the reader is already sorted
                return object : FilterCodecReader(reader) {
                    override val coreCacheHelper: CacheHelper
                        get() = /*null*/ throw UnsupportedOperationException(
                            "SortingCodecReader does not support coreCacheHelper"
                        )

                    override val readerCacheHelper: CacheHelper
                        get() = /*null*/ throw UnsupportedOperationException(
                            "SortingCodecReader does not support readerCacheHelper"
                        )

                    override val metaData: LeafMetaData
                        get() = newMetaData

                    override fun toString(): String {
                        return "SortingCodecReader($`in`)"
                    }
                }
            }
            require(reader.maxDoc() == docMap.size()) {
                ("reader.maxDoc() should be equal to docMap.size(), got "
                        + reader.maxDoc()
                        + " != "
                        + docMap.size())
            }
            assert(Sorter.isConsistent(docMap))
            return SortingCodecReader(reader, docMap, newMetaData)
        }
    }
}
