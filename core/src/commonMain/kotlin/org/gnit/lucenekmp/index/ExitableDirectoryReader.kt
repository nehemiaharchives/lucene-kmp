package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.index.FilterLeafReader.FilterTerms
import org.gnit.lucenekmp.index.FilterLeafReader.FilterTermsEnum
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton

/**
 * The [ExitableDirectoryReader] wraps a real index [DirectoryReader] and allows for a
 * [QueryTimeout] implementation object to be checked periodically to see if the thread should
 * exit or not. If [QueryTimeout.shouldExit] returns true, an [ExitingReaderException]
 * is thrown.
 */
class ExitableDirectoryReader @Throws(IOException::class) constructor(
    `in`: DirectoryReader,
    private val queryTimeout: QueryTimeout
) : FilterDirectoryReader(`in`, ExitableSubReaderWrapper(queryTimeout)) {

    /** Exception that is thrown to prematurely terminate a term enumeration.  */
    class ExitingReaderException(msg: String) : RuntimeException(msg)

    /** Wrapper class for a SubReaderWrapper that is used by the ExitableDirectoryReader.  */
    class ExitableSubReaderWrapper(private val queryTimeout: QueryTimeout) : SubReaderWrapper() {
        /** Constructor * */
        init {
            requireNotNull(queryTimeout)
        }

        override fun wrap(reader: LeafReader): LeafReader {
            return ExitableFilterAtomicReader(reader, queryTimeout)
        }
    }

    /** Wrapper class for another FilterAtomicReader. This is used by ExitableSubReaderWrapper.  */
    class ExitableFilterAtomicReader(
        `in`: LeafReader,
        private val queryTimeout: QueryTimeout
    ) : FilterLeafReader(`in`) {

        companion object {
            const val DOCS_BETWEEN_TIMEOUT_CHECK: Int = 1000
        }

        /** Constructor * */
        init {
            requireNotNull(queryTimeout)
        }

        @Throws(IOException::class)
        override fun getPointValues(field: String): PointValues? {
            val pointValues = `in`.getPointValues(field) ?: return null
            return ExitablePointValues(pointValues, queryTimeout)
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            val terms = `in`.terms(field)
            return if (terms == null) null else ExitableTerms(terms, queryTimeout)
        }

        override val readerCacheHelper: CacheHelper?
            get() = `in`.readerCacheHelper

        override val coreCacheHelper: CacheHelper?
            get() = `in`.coreCacheHelper

        @Throws(IOException::class)
        override fun getNumericDocValues(field: String): NumericDocValues? {
            val numericDocValues = super.getNumericDocValues(field) ?: return null
            return object : FilterNumericDocValues(numericDocValues) {
                private var docToCheck = 0

                override fun advance(target: Int): Int {
                    val advance = super.advance(target)
                    if (advance >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = advance + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advance
                }

                override fun advanceExact(target: Int): Boolean {
                    val advanceExact = super.advanceExact(target)
                    if (target >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = target + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advanceExact
                }

                override fun nextDoc(): Int {
                    val nextDoc = super.nextDoc()
                    if (nextDoc >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = nextDoc + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return nextDoc
                }
            }
        }

        @Throws(IOException::class)
        override fun getBinaryDocValues(field: String): BinaryDocValues? {
            val binaryDocValues = super.getBinaryDocValues(field) ?: return null
            return object : FilterBinaryDocValues(binaryDocValues) {
                private var docToCheck = 0

                override fun advance(target: Int): Int {
                    val advance = super.advance(target)
                    if (target >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = target + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advance
                }

                override fun advanceExact(target: Int): Boolean {
                    val advanceExact = super.advanceExact(target)
                    if (target >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = target + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advanceExact
                }

                override fun nextDoc(): Int {
                    val nextDoc = super.nextDoc()
                    if (nextDoc >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = nextDoc + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return nextDoc
                }
            }
        }

        @Throws(IOException::class)
        override fun getSortedDocValues(field: String): SortedDocValues? {
            val sortedDocValues = super.getSortedDocValues(field) ?: return null
            return object : FilterSortedDocValues(sortedDocValues) {
                private var docToCheck = 0

                override fun advance(target: Int): Int {
                    val advance = super.advance(target)
                    if (advance >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = advance + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advance
                }

                override fun advanceExact(target: Int): Boolean {
                    val advanceExact = super.advanceExact(target)
                    if (target >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = target + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advanceExact
                }

                override fun nextDoc(): Int {
                    val nextDoc = super.nextDoc()
                    if (nextDoc >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = nextDoc + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return nextDoc
                }
            }
        }

        @Throws(IOException::class)
        override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
            val sortedNumericDocValues = super.getSortedNumericDocValues(field) ?: return null
            return object : FilterSortedNumericDocValues(sortedNumericDocValues) {
                private var docToCheck = 0

                override fun advance(target: Int): Int {
                    val advance = super.advance(target)
                    if (advance >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = advance + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advance
                }

                override fun advanceExact(target: Int): Boolean {
                    val advanceExact = super.advanceExact(target)
                    if (target >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = target + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advanceExact
                }

                override fun nextDoc(): Int {
                    val nextDoc = super.nextDoc()
                    if (nextDoc >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = nextDoc + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return nextDoc
                }
            }
        }

        @Throws(IOException::class)
        override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
            val sortedSetDocValues = super.getSortedSetDocValues(field) ?: return null
            return object : FilterSortedSetDocValues(sortedSetDocValues) {
                private var docToCheck = 0

                override fun advance(target: Int): Int {
                    val advance = super.advance(target)
                    if (advance >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = advance + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advance
                }

                override fun advanceExact(target: Int): Boolean {
                    val advanceExact = super.advanceExact(target)
                    if (target >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = target + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return advanceExact
                }

                override fun nextDoc(): Int {
                    val nextDoc = super.nextDoc()
                    if (nextDoc >= docToCheck) {
                        checkAndThrow(`in`)
                        docToCheck = nextDoc + DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return nextDoc
                }
            }
        }

        @Throws(IOException::class)
        override fun getFloatVectorValues(field: String): FloatVectorValues? {
            val vectorValues = `in`.getFloatVectorValues(field) ?: return null
            return ExitableFloatVectorValues(vectorValues)
        }

        @Throws(IOException::class)
        override fun getByteVectorValues(field: String): ByteVectorValues? {
            val vectorValues = `in`.getByteVectorValues(field) ?: return null
            return ExitableByteVectorValues(vectorValues)
        }

        @Throws(IOException::class)
        override fun searchNearestVectors(
            field: String,
            target: FloatArray,
            knnCollector: KnnCollector,
            acceptDocs: Bits?
        ) {
            val updatedAcceptDocs = acceptDocs ?: Bits.MatchAllBits(maxDoc())
            val timeoutCheckingAcceptDocs = object : Bits {
                private var calls = 0

                override fun get(index: Int): Boolean {
                    if (calls++ % 16 == 0) {
                        checkAndThrowForSearchVectors()
                    }
                    return updatedAcceptDocs.get(index)
                }

                override fun length(): Int {
                    return updatedAcceptDocs.length()
                }
            }
            `in`.searchNearestVectors(field, target, knnCollector, timeoutCheckingAcceptDocs)
        }

        @Throws(IOException::class)
        override fun searchNearestVectors(
            field: String,
            target: ByteArray,
            knnCollector: KnnCollector,
            acceptDocs: Bits?
        ) {
            val updatedAcceptDocs = acceptDocs ?: Bits.MatchAllBits(maxDoc())
            val timeoutCheckingAcceptDocs = object : Bits {
                private var calls = 0

                override fun get(index: Int): Boolean {
                    if (calls++ % 16 == 0) {
                        checkAndThrowForSearchVectors()
                    }
                    return updatedAcceptDocs.get(index)
                }

                override fun length(): Int {
                    return updatedAcceptDocs.length()
                }
            }
            `in`.searchNearestVectors(field, target, knnCollector, timeoutCheckingAcceptDocs)
        }

        private fun checkAndThrowForSearchVectors() {
            if (queryTimeout.shouldExit()) {
                throw ExitingReaderException(
                    "The request took too long to search nearest vectors. Timeout: $queryTimeout, Reader=${`in`}"
                )
            }
        }

        /**
         * Throws [ExitingReaderException] if [QueryTimeout.shouldExit] returns true, or
         * if `Thread.interrupted()` returns true.
         *
         * @param in underneath docValues
         */
        private fun checkAndThrow(`in`: DocIdSetIterator) {
            if (queryTimeout.shouldExit()) {
                throw ExitingReaderException(
                    "The request took too long to iterate over doc values. Timeout: $queryTimeout, DocValues=${`in`}"
                )
            }
        }

        private inner class ExitableFloatVectorValues(private val vectorValues: FloatVectorValues) : FloatVectorValues() {
            override fun dimension(): Int {
                return vectorValues.dimension()
            }

            override fun vectorValue(ord: Int): FloatArray {
                return vectorValues.vectorValue(ord)
            }

            override fun ordToDoc(ord: Int): Int {
                return vectorValues.ordToDoc(ord)
            }

            override fun size(): Int {
                return vectorValues.size()
            }

            override fun iterator(): DocIndexIterator {
                return createExitableIterator(vectorValues.iterator(), queryTimeout)
            }

            override fun scorer(target: FloatArray): VectorScorer? {
                return vectorValues.scorer(target)
            }

            override fun copy(): FloatVectorValues {
                throw UnsupportedOperationException()
            }
        }

        private inner class ExitableByteVectorValues(private val vectorValues: ByteVectorValues) : ByteVectorValues() {
            override fun dimension(): Int {
                return vectorValues.dimension()
            }

            override fun size(): Int {
                return vectorValues.size()
            }

            override fun vectorValue(ord: Int): ByteArray {
                return vectorValues.vectorValue(ord)
            }

            override fun ordToDoc(ord: Int): Int {
                return vectorValues.ordToDoc(ord)
            }

            override fun iterator(): DocIndexIterator {
                return createExitableIterator(vectorValues.iterator(), queryTimeout)
            }

            override fun scorer(query: ByteArray): VectorScorer? {
                return vectorValues.scorer(query)
            }

            override fun copy(): ByteVectorValues {
                throw UnsupportedOperationException()
            }
        }
    }

    /** Wrapper class for another PointValues implementation that is used by ExitableFields.  */
    private class ExitablePointValues(
        private val `in`: PointValues,
        private val queryTimeout: QueryTimeout
    ) : PointValues() {

        init {
            requireNotNull(queryTimeout)
            checkAndThrow()
        }

        /**
         * Throws [ExitingReaderException] if [QueryTimeout.shouldExit] returns true, or
         * if `Thread.interrupted()` returns true.
         */
        private fun checkAndThrow() {
            if (queryTimeout.shouldExit()) {
                throw ExitingReaderException(
                    "The request took too long to iterate over point values. Timeout: $queryTimeout, PointValues=${`in`}"
                )
            }
        }

        override val pointTree: PointValues.PointTree
            get() {
                checkAndThrow()
                return ExitablePointTree(`in`, `in`.pointTree, queryTimeout)
            }

        override val minPackedValue: ByteArray
            get() {
                checkAndThrow()
                return `in`.minPackedValue
            }

        override val maxPackedValue: ByteArray
            get() {
                checkAndThrow()
                return `in`.maxPackedValue
            }

        override val numDimensions: Int
            get() {
                checkAndThrow()
                return `in`.numDimensions
            }

        override val numIndexDimensions: Int
            get() {
                checkAndThrow()
                return `in`.numIndexDimensions
            }

        override val bytesPerDimension: Int
            get() {
                checkAndThrow()
                return `in`.bytesPerDimension
            }

        override fun size(): Long {
            checkAndThrow()
            return `in`.size()
        }

        override val docCount: Int
            get() {
                checkAndThrow()
                return `in`.docCount
            }
    }

    private class ExitablePointTree(
        private val pointValues: PointValues,
        private val `in`: PointValues.PointTree,
        private val queryTimeout: QueryTimeout
    ) : PointValues.PointTree {
        private val exitableIntersectVisitor = ExitableIntersectVisitor(queryTimeout)
        private var calls = 0

        /**
         * Throws [ExitingReaderException] if [QueryTimeout.shouldExit] returns true, or
         * if `Thread.interrupted()` returns true.
         */
        private fun checkAndThrowWithSampling() {
            if (calls++ % 16 == 0) {
                checkAndThrow()
            }
        }

        private fun checkAndThrow() {
            if (queryTimeout.shouldExit()) {
                throw ExitingReaderException(
                    "The request took too long to intersect point values. Timeout: $queryTimeout, PointValues=$pointValues"
                )
            }
        }

        override fun clone(): PointValues.PointTree {
            checkAndThrow()
            return ExitablePointTree(pointValues, `in`.clone(), queryTimeout)
        }

        override fun moveToChild(): Boolean {
            checkAndThrowWithSampling()
            return `in`.moveToChild()
        }

        override fun moveToSibling(): Boolean {
            checkAndThrowWithSampling()
            return `in`.moveToSibling()
        }

        override fun moveToParent(): Boolean {
            checkAndThrowWithSampling()
            return `in`.moveToParent()
        }

        override val minPackedValue: ByteArray
            get() {
                checkAndThrowWithSampling()
                return `in`.minPackedValue
            }

        override val maxPackedValue: ByteArray
            get() {
                checkAndThrowWithSampling()
                return `in`.maxPackedValue
            }

        override fun size(): Long {
            checkAndThrow()
            return `in`.size()
        }

        override fun visitDocIDs(visitor: PointValues.IntersectVisitor) {
            checkAndThrow()
            `in`.visitDocIDs(visitor)
        }

        override fun visitDocValues(visitor: PointValues.IntersectVisitor) {
            checkAndThrow()
            exitableIntersectVisitor.setIntersectVisitor(visitor)
            `in`.visitDocValues(exitableIntersectVisitor)
        }
    }

    private class ExitableIntersectVisitor(private val queryTimeout: QueryTimeout) : PointValues.IntersectVisitor {
        private var `in`: PointValues.IntersectVisitor? = null
        private var calls = 0

        fun setIntersectVisitor(`in`: PointValues.IntersectVisitor) {
            this.`in` = `in`
        }

        /**
         * Throws [ExitingReaderException] if [QueryTimeout.shouldExit] returns true, or
         * if `Thread.interrupted()` returns true.
         */
        private fun checkAndThrowWithSampling() {
            if (calls++ % 16 == 0) {
                checkAndThrow()
            }
        }

        private fun checkAndThrow() {
            if (queryTimeout.shouldExit()) {
                throw ExitingReaderException(
                    "The request took too long to intersect point values. Timeout: $queryTimeout, PointValues=${`in`}"
                )
            }
        }

        override fun visit(docID: Int) {
            checkAndThrowWithSampling()
            `in`!!.visit(docID)
        }

        override fun visit(docID: Int, packedValue: ByteArray) {
            checkAndThrowWithSampling()
            `in`!!.visit(docID, packedValue)
        }

        override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
            checkAndThrow()
            return `in`!!.compare(minPackedValue, maxPackedValue)
        }

        override fun grow(count: Int) {
            checkAndThrow()
            `in`!!.grow(count)
        }
    }

    /** Wrapper class for another Terms implementation that is used by ExitableFields.  */
    open class ExitableTerms(terms: Terms, private val queryTimeout: QueryTimeout) : FilterTerms(terms) {
        /** Constructor * */
        init {
            requireNotNull(queryTimeout)
        }

        override fun intersect(compiled: CompiledAutomaton, startTerm: BytesRef?): TermsEnum {
            return ExitableTermsEnum(`in`.intersect(compiled, startTerm), queryTimeout)
        }

        override fun iterator(): TermsEnum {
            return ExitableTermsEnum(`in`.iterator(), queryTimeout)
        }

        override val sumTotalTermFreq: Long
            get() = `in`.sumTotalTermFreq

        override val min: BytesRef?
            get() = `in`.min

        override val max: BytesRef?
            get() = `in`.max
    }

    /**
     * Wrapper class for TermsEnum that is used by ExitableTerms for implementing an exitable
     * enumeration of terms.
     */
    class ExitableTermsEnum(termsEnum: TermsEnum, private val queryTimeout: QueryTimeout) : FilterTermsEnum(termsEnum) {
        private var calls = 0

        /** Constructor * */
        init {
            requireNotNull(queryTimeout)
            checkTimeoutWithSampling()
        }

        /**
         * Throws [ExitingReaderException] if [QueryTimeout.shouldExit] returns true, or
         * if `Thread.interrupted()` returns true.
         */
        private fun checkTimeoutWithSampling() {
            if ((calls++ and 15) == 0) {
                if (queryTimeout.shouldExit()) {
                    throw ExitingReaderException(
                        "The request took too long to iterate over terms. Timeout: $queryTimeout, TermsEnum=${`in`}"
                    )
                }
            }
        }

        override fun next(): BytesRef? {
            // Before every iteration, check if the iteration should exit
            checkTimeoutWithSampling()
            return `in`.next()
        }
    }

    @Throws(IOException::class)
    override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
        requireNotNull(queryTimeout) { "Query timeout must not be null" }
        return ExitableDirectoryReader(`in`, queryTimeout)
    }

    override val readerCacheHelper: CacheHelper?
        get() = `in`.readerCacheHelper

    override fun toString(): String {
        return "ExitableDirectoryReader(${`in`})"
    }

    companion object {
        private fun createExitableIterator(
            delegate: KnnVectorValues.DocIndexIterator,
            queryTimeout: QueryTimeout
        ): KnnVectorValues.DocIndexIterator {
            return object : KnnVectorValues.DocIndexIterator() {
                private var nextCheck = 0

                override fun index(): Int {
                    return delegate.index()
                }

                override fun docID(): Int {
                    return delegate.docID()
                }

                override fun nextDoc(): Int {
                    val doc = delegate.nextDoc()
                    if (doc >= nextCheck) {
                        checkAndThrow()
                        nextCheck = doc + ExitableFilterAtomicReader.DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return doc
                }

                override fun cost(): Long {
                    return delegate.cost()
                }

                override fun advance(target: Int): Int {
                    val doc = delegate.advance(target)
                    if (doc >= nextCheck) {
                        checkAndThrow()
                        nextCheck = doc + ExitableFilterAtomicReader.DOCS_BETWEEN_TIMEOUT_CHECK
                    }
                    return doc
                }

                private fun checkAndThrow() {
                    if (queryTimeout.shouldExit()) {
                        throw ExitingReaderException(
                            "The request took too long to iterate over knn vector values. Timeout: $queryTimeout, KnnVectorValues=$delegate"
                        )
                    }
                }
            }
        }

        /**
         * Wraps a provided DirectoryReader. Note that for convenience, the returned reader can be used
         * normally (e.g. passed to [DirectoryReader.openIfChanged]) and so on.
         */
        @Throws(IOException::class)
        fun wrap(`in`: DirectoryReader, queryTimeout: QueryTimeout): DirectoryReader {
            requireNotNull(queryTimeout) { "Query timeout must not be null" }
            return ExitableDirectoryReader(`in`, queryTimeout)
        }
    }
}
