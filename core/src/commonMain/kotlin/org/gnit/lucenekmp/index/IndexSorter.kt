package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.*
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.util.LongValues
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.util.packed.PackedInts


/**
 * Handles how documents should be sorted in an index, both within a segment and between segments.
 *
 *
 * Implementers must provide the following methods:
 *
 *
 *  * [.getDocComparator] - an object that determines how documents within
 * a segment are to be sorted
 *  * [.getComparableProviders] - an array of objects that return a sortable long
 * value per document and segment
 *  * [.getProviderName] - the SPI-registered name of a [SortFieldProvider] to
 * serialize the sort
 *
 *
 *
 * The companion [SortFieldProvider] should be registered with SPI via `META-INF/services`
 */
interface IndexSorter {
    /** Used for sorting documents across segments  */
    interface ComparableProvider {
        /**
         * Returns a long so that the natural ordering of long values matches the ordering of doc IDs
         * for the given comparator
         */
        @Throws(IOException::class)
        fun getAsComparableLong(docID: Int): Long
    }

    /** A comparator of doc IDs, used for sorting documents within a segment  */
    interface DocComparator {
        /**
         * Compare docID1 against docID2. The contract for the return value is the same as [ ][Comparator.compare].
         */
        fun compare(docID1: Int, docID2: Int): Int
    }

    /**
     * Get an array of [ComparableProvider], one per segment, for merge sorting documents in
     * different segments
     *
     * @param readers the readers to be merged
     */
    @Throws(IOException::class)
    fun getComparableProviders(readers: MutableList<out LeafReader>): Array<ComparableProvider>

    /**
     * Get a comparator that determines the sort order of docs within a single Reader.
     *
     *
     * NB We cannot simply use the [FieldComparator] API because it requires docIDs to be
     * sent in-order. The default implementations allocate array[maxDoc] to hold native values for
     * comparison, but 1) they are transient (only alive while sorting this one segment) and 2) in the
     * typical index sorting case, they are only used to sort newly flushed segments, which will be
     * smaller than merged segments
     *
     * @param reader the Reader to sort
     * @param maxDoc the number of documents in the Reader
     */
    @Throws(IOException::class)
    fun getDocComparator(reader: LeafReader, maxDoc: Int): DocComparator

    /**
     * The SPI-registered name of a [SortFieldProvider] that will deserialize the parent
     * SortField
     */
    val providerName: String

    /** Provide a NumericDocValues instance for a LeafReader  */
    interface NumericDocValuesProvider {
        /** Returns the NumericDocValues instance for this LeafReader  */
        @Throws(IOException::class)
        fun get(reader: LeafReader): NumericDocValues
    }

    /** Provide a SortedDocValues instance for a LeafReader  */
    interface SortedDocValuesProvider {
        /** Returns the SortedDocValues instance for this LeafReader  */
        @Throws(IOException::class)
        fun get(reader: LeafReader): SortedDocValues
    }

    /** Sorts documents based on integer values from a NumericDocValues instance  */
    class IntSorter(
        override val providerName: String,
        private val missingValue: Int,
        reverse: Boolean,
        private val valuesProvider: NumericDocValuesProvider
    ) : IndexSorter {
        private val reverseMul: Int

        /** Creates a new IntSorter  */
        init {
            this.reverseMul = if (reverse) -1 else 1
        }

        @Throws(IOException::class)
        override fun getComparableProviders(readers: MutableList<out LeafReader>): Array<ComparableProvider> {
            val providers = kotlin.arrayOfNulls<ComparableProvider>(readers.size)
            val missingValue: Long
            if (this.missingValue != null) {
                missingValue = this.missingValue.toLong()
            } else {
                missingValue = 0L
            }

            for (readerIndex in readers.indices) {
                val values = valuesProvider.get(readers[readerIndex])

                providers[readerIndex] =
                    object : ComparableProvider {
                        override fun getAsComparableLong(docID: Int): Long {
                            if (values.advanceExact(docID)) {
                                return values.longValue()
                            } else {
                                return missingValue
                            }
                        }
                    }
            }
            return providers as Array<ComparableProvider>
        }

        @Throws(IOException::class)
        override fun getDocComparator(reader: LeafReader, maxDoc: Int): DocComparator {
            val dvs = valuesProvider.get(reader)
            val values = IntArray(maxDoc)
            if (this.missingValue != null) {
                /*java.util.Arrays.fill(values, this.missingValue)*/
                values.fill(this.missingValue)
            }
            while (true) {
                val docID = dvs.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                values[docID] = dvs.longValue() as Int
            }

            return object : DocComparator {
                override fun compare(docID1: Int, docID2: Int): Int {
                    return reverseMul * Int.compare(
                        values[docID1],
                        values[docID2]
                    )
                }
            }
        }

        fun getProviderName(): String {
            return providerName
        }
    }

    /** Sorts documents based on long values from a NumericDocValues instance  */
    class LongSorter(
        override val providerName: String,
        private val missingValue: Long,
        reverse: Boolean,
        private val valuesProvider: NumericDocValuesProvider
    ) : IndexSorter {
        private val reverseMul: Int

        /** Creates a new LongSorter  */
        init {
            this.reverseMul = if (reverse) -1 else 1
        }

        @Throws(IOException::class)
        override fun getComparableProviders(readers: MutableList<out LeafReader>): Array<ComparableProvider> {
            val providers = kotlin.arrayOfNulls<ComparableProvider>(readers.size)
            val missingValue: Long
            if (this.missingValue != null) {
                missingValue = this.missingValue
            } else {
                missingValue = 0L
            }

            for (readerIndex in readers.indices) {
                val values = valuesProvider.get(readers.get(readerIndex))

                providers[readerIndex] =
                    object : ComparableProvider {
                        @Throws(IOException::class)
                        override fun getAsComparableLong(docID: Int): Long {
                            if (values.advanceExact(docID)) {
                                return values.longValue()
                            } else {
                                return missingValue
                            }
                        }
                    }
            }
            return providers as Array<ComparableProvider>
        }

        @Throws(IOException::class)
        override fun getDocComparator(reader: LeafReader, maxDoc: Int): DocComparator {
            val dvs = valuesProvider.get(reader)
            val values = LongArray(maxDoc)
            if (this.missingValue != null) {
                /*java.util.Arrays.fill(values, this.missingValue)*/
                values.fill(this.missingValue)
            }
            while (true) {
                val docID = dvs.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                values[docID] = dvs.longValue()
            }

            return object : DocComparator {
                override fun compare(docID1: Int, docID2: Int): Int {
                    return reverseMul * Long.compare(
                        values[docID1],
                        values[docID2]
                    )
                }
            }
        }

        fun getProviderName(): String {
            return providerName
        }
    }

    /** Sorts documents based on float values from a NumericDocValues instance  */
    class FloatSorter(
        override val providerName: String,
        private val missingValue: Float,
        reverse: Boolean,
        private val valuesProvider: NumericDocValuesProvider
    ) : IndexSorter {
        private val reverseMul: Int

        /** Creates a new FloatSorter  */
        init {
            this.reverseMul = if (reverse) -1 else 1
        }

        @Throws(IOException::class)
        override fun getComparableProviders(readers: MutableList<out LeafReader>): Array<ComparableProvider> {
            val providers = kotlin.arrayOfNulls<ComparableProvider>(readers.size)
            val missValueBits: Int = Float.floatToIntBits(if (missingValue != null) missingValue else 0.0f)

            for (readerIndex in readers.indices) {
                val values = valuesProvider.get(readers.get(readerIndex))

                providers[readerIndex] =
                    object : ComparableProvider {
                        @Throws(IOException::class)
                        override fun getAsComparableLong(docID: Int): Long {
                            val valueBits =
                                if (values.advanceExact(docID)) values.longValue() as Int else missValueBits
                            return NumericUtils.sortableFloatBits(valueBits).toLong()
                        }
                    }
            }
            return providers as Array<ComparableProvider>
        }

        @Throws(IOException::class)
        override fun getDocComparator(reader: LeafReader, maxDoc: Int): DocComparator {
            val dvs = valuesProvider.get(reader)
            val values = FloatArray(maxDoc)
            if (this.missingValue != null) {
                /*java.util.Arrays.fill(values, this.missingValue)*/
                values.fill(this.missingValue)
            }
            while (true) {
                val docID = dvs.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                values[docID] = Float.intBitsToFloat(dvs.longValue() as Int)
            }

            return object : DocComparator {
                override fun compare(docID1: Int, docID2: Int): Int {
                    return reverseMul * Float.compare(
                        values[docID1],
                        values[docID2]
                    )
                }
            }
        }

        fun getProviderName(): String {
            return providerName
        }
    }

    /** Sorts documents based on double values from a NumericDocValues instance  */
    class DoubleSorter(
        override val providerName: String,
        private val missingValue: Double,
        reverse: Boolean,
        private val valuesProvider: NumericDocValuesProvider
    ) : IndexSorter {
        private val reverseMul: Int

        /** Creates a new DoubleSorter  */
        init {
            this.reverseMul = if (reverse) -1 else 1
        }

        @Throws(IOException::class)
        override fun getComparableProviders(readers: MutableList<out LeafReader>): Array<ComparableProvider> {
            val providers = kotlin.arrayOfNulls<ComparableProvider>(readers.size)
            val missingValueBits: Long =
                Double.doubleToLongBits(if (missingValue != null) missingValue else 0.0)

            for (readerIndex in readers.indices) {
                val values = valuesProvider.get(readers.get(readerIndex))

                providers[readerIndex] =
                    object : ComparableProvider {
                        @Throws(IOException::class)
                        override fun getAsComparableLong(docID: Int): Long {
                            val valueBits =
                                if (values.advanceExact(docID)) values.longValue() else missingValueBits
                            return NumericUtils.sortableDoubleBits(valueBits)
                        }
                    }
            }
            return providers as Array<ComparableProvider>
        }

        @Throws(IOException::class)
        override fun getDocComparator(reader: LeafReader, maxDoc: Int): DocComparator {
            val dvs = valuesProvider.get(reader)
            val values = DoubleArray(maxDoc)
            if (missingValue != null) {
                /*java.util.Arrays.fill(values, missingValue)*/
                values.fill(missingValue)
            }
            while (true) {
                val docID = dvs.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                values[docID] = Double.longBitsToDouble(dvs.longValue())
            }

            return object : DocComparator {
                override fun compare(docID1: Int, docID2: Int): Int {
                    return reverseMul * Double.compare(
                        values[docID1],
                        values[docID2]
                    )
                }
            }
        }

        fun getProviderName(): String {
            return providerName
        }
    }

    /** Sorts documents based on terms from a SortedDocValues instance  */
    open class StringSorter(
        override val providerName: String,
        private val missingValue: Any,
        reverse: Boolean,
        private val valuesProvider: SortedDocValuesProvider
    ) : IndexSorter {
        private val reverseMul: Int

        /** Creates a new StringSorter  */
        init {
            this.reverseMul = if (reverse) -1 else 1
        }

        @Throws(IOException::class)
        override fun getComparableProviders(readers: MutableList<out LeafReader>): Array<ComparableProvider> {
            val providers = kotlin.arrayOfNulls<ComparableProvider>(readers.size)
            val values: Array<SortedDocValues?> = kotlin.arrayOfNulls<SortedDocValues>(readers.size)
            for (i in readers.indices) {
                val sorted = valuesProvider.get(readers[i])
                values[i] = sorted
            }
            val ordinalMap: OrdinalMap = OrdinalMap.build(null, values as Array<SortedDocValues>, PackedInts.DEFAULT)
            val missingOrd: Int
            if (missingValue === SortField.STRING_LAST) {
                missingOrd = Int.Companion.MAX_VALUE
            } else {
                missingOrd = Int.Companion.MIN_VALUE
            }

            for (readerIndex in readers.indices) {
                val readerValues = values[readerIndex]
                val globalOrds: LongValues = ordinalMap.getGlobalOrds(readerIndex)
                providers[readerIndex] =
                    object : ComparableProvider {
                        @Throws(IOException::class)
                        override fun getAsComparableLong(docID: Int): Long {
                            if (readerValues.advanceExact(docID)) {
                                // translate segment's ord to global ord space:
                                return globalOrds.get(readerValues.ordValue().toLong())
                            } else {
                                return missingOrd.toLong()
                            }
                        }
                    }
            }
            return providers as Array<ComparableProvider>
        }

        @Throws(IOException::class)
        override fun getDocComparator(reader: LeafReader, maxDoc: Int): DocComparator {
            val sorted = valuesProvider.get(reader)
            val missingOrd: Int
            if (missingValue === SortField.STRING_LAST) {
                missingOrd = Int.Companion.MAX_VALUE
            } else {
                missingOrd = Int.Companion.MIN_VALUE
            }

            val ords = IntArray(maxDoc)
            /*java.util.Arrays.fill(ords, missingOrd)*/
            ords.fill(missingOrd)
            var docID: Int
            while ((sorted.nextDoc().also { docID = it }) != NO_MORE_DOCS) {
                ords[docID] = sorted.ordValue()
            }

            return object : DocComparator {
                override fun compare(docID1: Int, docID2: Int): Int {
                    return reverseMul * Int.compare(
                        ords[docID1],
                        ords[docID2]
                    )
                }
            }
        }

        fun getProviderName(): String {
            return providerName
        }
    }
}
