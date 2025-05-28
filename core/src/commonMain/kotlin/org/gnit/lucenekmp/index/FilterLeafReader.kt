package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOBooleanSupplier
import org.gnit.lucenekmp.util.Unwrappable

/**
 * A `FilterLeafReader` contains another LeafReader, which it uses as its basic source of
 * data, possibly transforming the data along the way or providing additional functionality. The
 * class `FilterLeafReader` itself simply implements all abstract methods of `
 * IndexReader` with versions that pass all requests to the contained index reader. Subclasses
 * of `FilterLeafReader` may further override some of these methods and may also provide
 * additional methods and fields.
 *
 *
 * **NOTE**: If you override [.getLiveDocs], you will likely need to override [ ][.numDocs] as well and vice-versa.
 *
 *
 * **NOTE**: If this [FilterLeafReader] does not change the content the contained
 * reader, you could consider delegating calls to [.getCoreCacheHelper] and [ ][.getReaderCacheHelper].
 */
abstract class FilterLeafReader protected constructor(`in`: LeafReader) : LeafReader() {
    /** Base class for filtering [Fields] implementations.  */
    abstract class FilterFields protected constructor(`in`: Fields) : Fields() {
        /** The underlying Fields instance.  */
        protected val `in`: Fields

        /**
         * Creates a new FilterFields.
         *
         * @param in the underlying Fields instance.
         */
        init {
            if (`in` == null) {
                throw NullPointerException("incoming Fields must not be null")
            }
            this.`in` = `in`
        }

        override fun iterator(): MutableIterator<String> {
            return `in`.iterator()
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            return `in`.terms(field)!!
        }

        override fun size(): Int {
            return `in`.size()
        }
    }

    /**
     * Base class for filtering [Terms] implementations.
     *
     *
     * **NOTE**: If the order of terms and documents is not changed, and if these terms are
     * going to be intersected with automata, you could consider overriding [.intersect] for
     * better performance.
     */
    abstract class FilterTerms protected constructor(`in`: Terms) : Terms() {
        /** The underlying Terms instance.  */
        protected val `in`: Terms

        /**
         * Creates a new FilterTerms
         *
         * @param in the underlying Terms instance.
         */
        init {
            if (`in` == null) {
                throw NullPointerException("incoming Terms must not be null")
            }
            this.`in` = `in`
        }

        @Throws(IOException::class)
        override fun iterator(): TermsEnum {
            return `in`.iterator()
        }

        @Throws(IOException::class)
        override fun size(): Long {
            return `in`.size()
        }

        override val sumDocFreq: Long
            get() {
                return `in`.sumDocFreq
            }

        override val docCount: Int
            get() {
                return `in`.docCount
            }

        override fun hasFreqs(): Boolean {
            return `in`.hasFreqs()
        }

        override fun hasOffsets(): Boolean {
            return `in`.hasOffsets()
        }

        override fun hasPositions(): Boolean {
            return `in`.hasPositions()
        }

        override fun hasPayloads(): Boolean {
            return `in`.hasPayloads()
        }

        override fun getStats(): Any {
            return `in`.getStats()
        }
    }

    /** Base class for filtering [TermsEnum] implementations.  */
    abstract class FilterTermsEnum protected constructor(`in`: TermsEnum) : TermsEnum() {
        /** The underlying TermsEnum instance.  */
        protected val `in`: TermsEnum

        /**
         * Creates a new FilterTermsEnum
         *
         * @param in the underlying TermsEnum instance.
         */
        init {
            if (`in` == null) {
                throw NullPointerException("incoming TermsEnum must not be null")
            }
            this.`in` = `in`
        }

        override fun attributes(): AttributeSource {
            return `in`.attributes()
        }

        @Throws(IOException::class)
        override fun seekCeil(text: BytesRef): SeekStatus {
            return `in`.seekCeil(text)
        }

        @Throws(IOException::class)
        override fun seekExact(text: BytesRef): Boolean {
            return `in`.seekExact(text)
        }

        @Throws(IOException::class)
        override fun seekExact(ord: Long) {
            `in`.seekExact(ord)
        }

        @Throws(IOException::class)
        override fun next(): BytesRef? {
            return `in`.next()
        }

        @Throws(IOException::class)
        override fun term(): BytesRef? {
            return `in`.term()
        }

        @Throws(IOException::class)
        override fun ord(): Long {
            return `in`.ord()
        }

        @Throws(IOException::class)
        override fun docFreq(): Int {
            return `in`.docFreq()
        }

        @Throws(IOException::class)
        override fun totalTermFreq(): Long {
            return `in`.totalTermFreq()
        }

        @Throws(IOException::class)
        override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
            return `in`.postings(reuse, flags)
        }

        @Throws(IOException::class)
        override fun impacts(flags: Int): ImpactsEnum {
            return `in`.impacts(flags)
        }

        @Throws(IOException::class)
        override fun seekExact(term: BytesRef, state: TermState) {
            `in`.seekExact(term, state)
        }

        @Throws(IOException::class)
        override fun prepareSeekExact(text: BytesRef): IOBooleanSupplier? {
            return `in`.prepareSeekExact(text)
        }

        @Throws(IOException::class)
        override fun termState(): TermState {
            return `in`.termState()
        }
    }

    /** Base class for filtering [PostingsEnum] implementations.  */
    abstract class FilterPostingsEnum protected constructor(`in`: PostingsEnum) : PostingsEnum(),
        Unwrappable<PostingsEnum> {
        /** The underlying PostingsEnum instance.  */
        protected val `in`: PostingsEnum

        /**
         * Create a new FilterPostingsEnum
         *
         * @param in the underlying PostingsEnum instance.
         */
        init {
            if (`in` == null) {
                throw NullPointerException("incoming PostingsEnum must not be null")
            }
            this.`in` = `in`
        }

        override fun docID(): Int {
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            return `in`.freq()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return `in`.nextDoc()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return `in`.advance(target)
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            return `in`.nextPosition()
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return `in`.startOffset()
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return `in`.endOffset()
        }

        override val payload: BytesRef?
            get() = `in`.payload

        override fun cost(): Long {
            return `in`.cost()
        }

        override fun unwrap(): PostingsEnum {
            return `in`
        }
    }

    /** Returns the wrapped [LeafReader].  */
    /** The underlying LeafReader.  */
    val delegate: LeafReader

    /**
     * Construct a FilterLeafReader based on the specified base reader.
     *
     *
     * Note that base reader is closed if this FilterLeafReader is closed.
     *
     * @param in specified base reader.
     */
    init {
        if (`in` == null) {
            throw NullPointerException("incoming LeafReader must not be null")
        }
        this.delegate = `in`
        `in`.registerParentReader(this)
    }

    override val liveDocs: Bits?
        get() {
            ensureOpen()
            return delegate.liveDocs
        }

    override val fieldInfos: FieldInfos
        get() = delegate.fieldInfos

    @Throws(IOException::class)
    override fun getPointValues(field: String): PointValues {
        return delegate.getPointValues(field)!!
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(field: String): FloatVectorValues {
        return delegate.getFloatVectorValues(field)!!
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(field: String): ByteVectorValues? {
        return delegate.getByteVectorValues(field)
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits
    ) {
        delegate.searchNearestVectors(field, target, knnCollector, acceptDocs)
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits
    ) {
        delegate.searchNearestVectors(field, target, knnCollector, acceptDocs)
    }

    @Throws(IOException::class)
    override fun termVectors(): TermVectors {
        ensureOpen()
        return delegate.termVectors()
    }

    override fun numDocs(): Int {
        // Don't call ensureOpen() here (it could affect performance)
        return delegate.numDocs()
    }

    override fun maxDoc(): Int {
        // Don't call ensureOpen() here (it could affect performance)
        return delegate.maxDoc()
    }

    @Throws(IOException::class)
    override fun storedFields(): StoredFields {
        ensureOpen()
        return delegate.storedFields()
    }

    @Throws(IOException::class)
    override fun doClose() {
        delegate.close()
    }

    @Throws(IOException::class)
    override fun terms(field: String): Terms {
        ensureOpen()
        return delegate.terms(field)!!
    }

    override fun toString(): String {
        val buffer = StringBuilder("FilterLeafReader(")
        buffer.append(this.delegate)
        buffer.append(')')
        return buffer.toString()
    }

    @Throws(IOException::class)
    override fun getNumericDocValues(field: String): NumericDocValues? {
        ensureOpen()
        return delegate.getNumericDocValues(field)
    }

    @Throws(IOException::class)
    override fun getBinaryDocValues(field: String): BinaryDocValues? {
        ensureOpen()
        return delegate.getBinaryDocValues(field)
    }

    @Throws(IOException::class)
    override fun getSortedDocValues(field: String): SortedDocValues? {
        ensureOpen()
        return delegate.getSortedDocValues(field)
    }

    @Throws(IOException::class)
    override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
        ensureOpen()
        return delegate.getSortedNumericDocValues(field)
    }

    @Throws(IOException::class)
    override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
        ensureOpen()
        return delegate.getSortedSetDocValues(field)
    }

    @Throws(IOException::class)
    override fun getDocValuesSkipper(field: String): DocValuesSkipper? {
        ensureOpen()
        return delegate.getDocValuesSkipper(field)
    }

    @Throws(IOException::class)
    override fun getNormValues(field: String): NumericDocValues? {
        ensureOpen()
        return delegate.getNormValues(field)
    }

    override val metaData: LeafMetaData
        get() {
            ensureOpen()
            return delegate.metaData
        }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        ensureOpen()
        delegate.checkIntegrity()
    }

    companion object {
        /**
         * Get the wrapped instance by `reader` as long as this reader is an instance of [ ].
         */
        fun unwrap(reader: LeafReader): LeafReader {
            var reader = reader
            while (reader is FilterLeafReader) {
                reader = reader.delegate
            }
            return reader
        }
    }
}
