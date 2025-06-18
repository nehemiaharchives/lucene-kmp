package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.packed.PackedInts

/**
 * A wrapper for CompositeIndexReader providing access to DocValues.
 *
 *
 * **NOTE**: for multi readers, you'll get better performance by gathering the sub readers
 * using [IndexReader.getContext] to get the atomic leaves and then operate per-LeafReader,
 * instead of using this class.
 *
 *
 * **NOTE**: This is very costly.
 *
 * @lucene.experimental
 * @lucene.internal
 */
object MultiDocValues {
    /**
     * Returns a NumericDocValues for a reader's norms (potentially merging on-the-fly).
     *
     *
     * This is a slow way to access normalization values. Instead, access them per-segment with
     * [LeafReader.getNormValues]
     */
    @Throws(IOException::class)
    fun getNormValues(
        r: IndexReader,
        field: String
    ): NumericDocValues? {
        val leaves: MutableList<LeafReaderContext> = r.leaves()
        val size = leaves.size
        if (size == 0) {
            return null
        } else if (size == 1) {
            return leaves[0].reader().getNormValues(field)
        }

        // Check if any of the leaf reader which has this field has norms.
        var normFound = false
        for (leaf in leaves) {
            val reader: LeafReader = leaf.reader()
            val info: FieldInfo? = reader.fieldInfos.fieldInfo(field)
            if (info != null && info.hasNorms()) {
                normFound = true
                break
            }
        }
        if (normFound == false) {
            return null
        }

        return object : NumericDocValues() {
            private var nextLeaf = 0
            private var currentValues: NumericDocValues? = null
            private var currentLeaf: LeafReaderContext? = null
            private var docID = -1

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                while (true) {
                    if (currentValues == null) {
                        if (nextLeaf == leaves.size) {
                            docID = NO_MORE_DOCS
                            return docID
                        }
                        currentLeaf = leaves[nextLeaf]
                        currentValues = currentLeaf!!.reader().getNormValues(field)
                        nextLeaf++
                        continue
                    }

                    val newDocID: Int = currentValues!!.nextDoc()

                    if (newDocID == NO_MORE_DOCS) {
                        currentValues = null
                    } else {
                        docID = currentLeaf!!.docBase + newDocID
                        return docID
                    }
                }
            }

            override fun docID(): Int {
                return docID
            }

            @Throws(IOException::class)
            override fun advance(targetDocID: Int): Int {
                require(targetDocID > docID) {
                    ("can only advance beyond current document: on docID="
                            + docID
                            + " but targetDocID="
                            + targetDocID)
                }
                val readerIndex: Int = ReaderUtil.subIndex(targetDocID, leaves)
                if (readerIndex >= nextLeaf) {
                    if (readerIndex == leaves.size) {
                        currentValues = null
                        docID = NO_MORE_DOCS
                        return docID
                    }
                    currentLeaf = leaves[readerIndex]
                    currentValues = currentLeaf!!.reader().getNormValues(field)
                    if (currentValues == null) {
                        return nextDoc()
                    }
                    nextLeaf = readerIndex + 1
                }
                val newDocID: Int = currentValues!!.advance(targetDocID - currentLeaf!!.docBase)
                if (newDocID == NO_MORE_DOCS) {
                    currentValues = null
                    return nextDoc()
                } else {
                    docID = currentLeaf!!.docBase + newDocID
                    return docID
                }
            }

            @Throws(IOException::class)
            override fun advanceExact(targetDocID: Int): Boolean {
                require(targetDocID >= docID) {
                    ("can only advance beyond current document: on docID="
                            + docID
                            + " but targetDocID="
                            + targetDocID)
                }
                val readerIndex: Int = ReaderUtil.subIndex(targetDocID, leaves)
                if (readerIndex >= nextLeaf) {
                    require(readerIndex != leaves.size) { "Out of range: $targetDocID" }
                    currentLeaf = leaves[readerIndex]
                    currentValues = currentLeaf!!.reader().getNormValues(field)
                    nextLeaf = readerIndex + 1
                }
                docID = targetDocID
                if (currentValues == null) {
                    return false
                }
                return currentValues!!.advanceExact(targetDocID - currentLeaf!!.docBase)
            }

            @Throws(IOException::class)
            override fun longValue(): Long {
                return currentValues!!.longValue()
            }

            override fun cost(): Long {
                // TODO
                return 0
            }
        }
    }

    /** Returns a NumericDocValues for a reader's docvalues (potentially merging on-the-fly)  */
    @Throws(IOException::class)
    fun getNumericValues(
        r: IndexReader,
        field: String
    ): NumericDocValues? {
        val leaves: MutableList<LeafReaderContext> = r.leaves()
        val size = leaves.size
        if (size == 0) {
            return null
        } else if (size == 1) {
            return leaves[0].reader().getNumericDocValues(field)
        }

        var anyReal = false
        for (leaf in leaves) {
            val fieldInfo: FieldInfo? = leaf.reader().fieldInfos.fieldInfo(field)
            if (fieldInfo != null) {
                val dvType: DocValuesType = fieldInfo.docValuesType
                if (dvType == DocValuesType.NUMERIC) {
                    anyReal = true
                    break
                }
            }
        }

        if (anyReal == false) {
            return null
        }

        return object : NumericDocValues() {
            private var nextLeaf = 0
            private var currentValues: NumericDocValues? = null
            private var currentLeaf: LeafReaderContext? = null
            private var docID = -1

            override fun docID(): Int {
                return docID
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                while (true) {
                    while (currentValues == null) {
                        if (nextLeaf == leaves.size) {
                            docID = NO_MORE_DOCS
                            return docID
                        }
                        currentLeaf = leaves[nextLeaf]
                        currentValues = currentLeaf!!.reader().getNumericDocValues(field)
                        nextLeaf++
                    }

                    val newDocID: Int = currentValues!!.nextDoc()

                    if (newDocID == NO_MORE_DOCS) {
                        currentValues = null
                    } else {
                        docID = currentLeaf!!.docBase + newDocID
                        return docID
                    }
                }
            }

            @Throws(IOException::class)
            override fun advance(targetDocID: Int): Int {
                require(targetDocID > docID) {
                    ("can only advance beyond current document: on docID="
                            + docID
                            + " but targetDocID="
                            + targetDocID)
                }
                val readerIndex: Int = ReaderUtil.subIndex(targetDocID, leaves)
                if (readerIndex >= nextLeaf) {
                    if (readerIndex == leaves.size) {
                        currentValues = null
                        docID = NO_MORE_DOCS
                        return docID
                    }
                    currentLeaf = leaves[readerIndex]
                    currentValues = currentLeaf!!.reader().getNumericDocValues(field)
                    nextLeaf = readerIndex + 1
                    if (currentValues == null) {
                        return nextDoc()
                    }
                }
                val newDocID: Int = currentValues!!.advance(targetDocID - currentLeaf!!.docBase)
                if (newDocID == NO_MORE_DOCS) {
                    currentValues = null
                    return nextDoc()
                } else {
                    docID = currentLeaf!!.docBase + newDocID
                    return docID
                }
            }

            @Throws(IOException::class)
            override fun advanceExact(targetDocID: Int): Boolean {
                require(targetDocID >= docID) {
                    ("can only advance beyond current document: on docID="
                            + docID
                            + " but targetDocID="
                            + targetDocID)
                }
                val readerIndex: Int = ReaderUtil.subIndex(targetDocID, leaves)
                if (readerIndex >= nextLeaf) {
                    require(readerIndex != leaves.size) { "Out of range: $targetDocID" }
                    currentLeaf = leaves[readerIndex]
                    currentValues = currentLeaf!!.reader().getNumericDocValues(field)
                    nextLeaf = readerIndex + 1
                }
                docID = targetDocID
                if (currentValues == null) {
                    return false
                }
                return currentValues!!.advanceExact(targetDocID - currentLeaf!!.docBase)
            }

            @Throws(IOException::class)
            override fun longValue(): Long {
                return currentValues!!.longValue()
            }

            override fun cost(): Long {
                // TODO
                return 0
            }
        }
    }

    /** Returns a BinaryDocValues for a reader's docvalues (potentially merging on-the-fly)  */
    @Throws(IOException::class)
    fun getBinaryValues(
        r: IndexReader,
        field: String
    ): BinaryDocValues? {
        val leaves: MutableList<LeafReaderContext> = r.leaves()
        val size = leaves.size
        if (size == 0) {
            return null
        } else if (size == 1) {
            return leaves[0].reader().getBinaryDocValues(field)
        }

        var anyReal = false
        for (leaf in leaves) {
            val fieldInfo: FieldInfo? = leaf.reader().fieldInfos.fieldInfo(field)
            if (fieldInfo != null) {
                val dvType: DocValuesType = fieldInfo.docValuesType
                if (dvType == DocValuesType.BINARY) {
                    anyReal = true
                    break
                }
            }
        }

        if (anyReal == false) {
            return null
        }

        return object : BinaryDocValues() {
            private var nextLeaf = 0
            private var currentValues: BinaryDocValues? = null
            private var currentLeaf: LeafReaderContext? = null
            private var docID = -1

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                while (true) {
                    while (currentValues == null) {
                        if (nextLeaf == leaves.size) {
                            docID = NO_MORE_DOCS
                            return docID
                        }
                        currentLeaf = leaves[nextLeaf]
                        currentValues = currentLeaf!!.reader().getBinaryDocValues(field)
                        nextLeaf++
                    }

                    val newDocID: Int = currentValues!!.nextDoc()

                    if (newDocID == NO_MORE_DOCS) {
                        currentValues = null
                    } else {
                        docID = currentLeaf!!.docBase + newDocID
                        return docID
                    }
                }
            }

            override fun docID(): Int {
                return docID
            }

            @Throws(IOException::class)
            override fun advance(targetDocID: Int): Int {
                require(targetDocID > docID) {
                    ("can only advance beyond current document: on docID="
                            + docID
                            + " but targetDocID="
                            + targetDocID)
                }
                val readerIndex: Int = ReaderUtil.subIndex(targetDocID, leaves)
                if (readerIndex >= nextLeaf) {
                    if (readerIndex == leaves.size) {
                        currentValues = null
                        docID = NO_MORE_DOCS
                        return docID
                    }
                    currentLeaf = leaves[readerIndex]
                    currentValues = currentLeaf!!.reader().getBinaryDocValues(field)
                    nextLeaf = readerIndex + 1
                    if (currentValues == null) {
                        return nextDoc()
                    }
                }
                val newDocID: Int = currentValues!!.advance(targetDocID - currentLeaf!!.docBase)
                if (newDocID == NO_MORE_DOCS) {
                    currentValues = null
                    return nextDoc()
                } else {
                    docID = currentLeaf!!.docBase + newDocID
                    return docID
                }
            }

            @Throws(IOException::class)
            override fun advanceExact(targetDocID: Int): Boolean {
                require(targetDocID >= docID) {
                    ("can only advance beyond current document: on docID="
                            + docID
                            + " but targetDocID="
                            + targetDocID)
                }
                val readerIndex: Int = ReaderUtil.subIndex(targetDocID, leaves)
                if (readerIndex >= nextLeaf) {
                    require(readerIndex != leaves.size) { "Out of range: $targetDocID" }
                    currentLeaf = leaves[readerIndex]
                    currentValues = currentLeaf!!.reader().getBinaryDocValues(field)
                    nextLeaf = readerIndex + 1
                }
                docID = targetDocID
                if (currentValues == null) {
                    return false
                }
                return currentValues!!.advanceExact(targetDocID - currentLeaf!!.docBase)
            }

            @Throws(IOException::class)
            override fun binaryValue(): BytesRef? {
                return currentValues!!.binaryValue()
            }

            override fun cost(): Long {
                // TODO
                return 0
            }
        }
    }

    /**
     * Returns a SortedNumericDocValues for a reader's docvalues (potentially merging on-the-fly)
     *
     *
     * This is a slow way to access sorted numeric values. Instead, access them per-segment with
     * [LeafReader.getSortedNumericDocValues]
     */
    @Throws(IOException::class)
    fun getSortedNumericValues(
        r: IndexReader, field: String
    ): SortedNumericDocValues? {
        val leaves: MutableList<LeafReaderContext> = r.leaves()
        val size = leaves.size
        if (size == 0) {
            return null
        } else if (size == 1) {
            return leaves[0].reader().getSortedNumericDocValues(field)
        }

        var anyReal = false
        val values: Array<SortedNumericDocValues> =
            kotlin.arrayOfNulls<SortedNumericDocValues>(size) as Array<SortedNumericDocValues>
        var totalCost: Long = 0
        for (i in 0..<size) {
            val context: LeafReaderContext = leaves[i]
            var v: SortedNumericDocValues? = context.reader().getSortedNumericDocValues(field)
            if (v == null) {
                v = DocValues.emptySortedNumeric()
            } else {
                anyReal = true
            }
            values[i] = v
            totalCost += v.cost()
        }

        if (anyReal == false) {
            return null
        }

        val finalTotalCost = totalCost

        return object : SortedNumericDocValues() {
            private var nextLeaf = 0
            private var currentValues: SortedNumericDocValues? = null
            private var currentLeaf: LeafReaderContext? = null
            private var docID = -1

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                while (true) {
                    if (currentValues == null) {
                        if (nextLeaf == leaves.size) {
                            docID = NO_MORE_DOCS
                            return docID
                        }
                        currentLeaf = leaves[nextLeaf]
                        currentValues = values[nextLeaf]
                        nextLeaf++
                    }

                    val newDocID: Int = currentValues!!.nextDoc()

                    if (newDocID == NO_MORE_DOCS) {
                        currentValues = null
                    } else {
                        docID = currentLeaf!!.docBase + newDocID
                        return docID
                    }
                }
            }

            override fun docID(): Int {
                return docID
            }

            @Throws(IOException::class)
            override fun advance(targetDocID: Int): Int {
                require(targetDocID > docID) {
                    ("can only advance beyond current document: on docID="
                            + docID
                            + " but targetDocID="
                            + targetDocID)
                }
                val readerIndex: Int = ReaderUtil.subIndex(targetDocID, leaves)
                if (readerIndex >= nextLeaf) {
                    if (readerIndex == leaves.size) {
                        currentValues = null
                        docID = NO_MORE_DOCS
                        return docID
                    }
                    currentLeaf = leaves[readerIndex]
                    currentValues = values[readerIndex]
                    nextLeaf = readerIndex + 1
                }
                val newDocID: Int = currentValues!!.advance(targetDocID - currentLeaf!!.docBase)
                if (newDocID == NO_MORE_DOCS) {
                    currentValues = null
                    return nextDoc()
                } else {
                    docID = currentLeaf!!.docBase + newDocID
                    return docID
                }
            }

            @Throws(IOException::class)
            override fun advanceExact(targetDocID: Int): Boolean {
                require(targetDocID >= docID) {
                    ("can only advance beyond current document: on docID="
                            + docID
                            + " but targetDocID="
                            + targetDocID)
                }
                val readerIndex: Int = ReaderUtil.subIndex(targetDocID, leaves)
                if (readerIndex >= nextLeaf) {
                    require(readerIndex != leaves.size) { "Out of range: $targetDocID" }
                    currentLeaf = leaves[readerIndex]
                    currentValues = values[readerIndex]
                    nextLeaf = readerIndex + 1
                }
                docID = targetDocID
                if (currentValues == null) {
                    return false
                }
                return currentValues!!.advanceExact(targetDocID - currentLeaf!!.docBase)
            }

            override fun cost(): Long {
                return finalTotalCost
            }

            override fun docValueCount(): Int {
                return currentValues!!.docValueCount()
            }

            @Throws(IOException::class)
            override fun nextValue(): Long {
                return currentValues!!.nextValue()
            }
        }
    }

    /**
     * Returns a SortedDocValues for a reader's docvalues (potentially doing extremely slow things).
     *
     *
     * This is an extremely slow way to access sorted values. Instead, access them per-segment with
     * [LeafReader.getSortedDocValues]
     */
    @Throws(IOException::class)
    fun getSortedValues(
        r: IndexReader,
        field: String
    ): SortedDocValues? {
        val leaves: MutableList<LeafReaderContext> = r.leaves()
        val size = leaves.size

        if (size == 0) {
            return null
        } else if (size == 1) {
            return leaves[0].reader().getSortedDocValues(field)
        }

        var anyReal = false
        val values: Array<SortedDocValues> =
            kotlin.arrayOfNulls<SortedDocValues>(size) as Array<SortedDocValues>
        val starts = IntArray(size + 1)
        var totalCost: Long = 0
        for (i in 0..<size) {
            val context: LeafReaderContext = leaves[i]
            var v: SortedDocValues? = context.reader().getSortedDocValues(field)
            if (v == null) {
                v = DocValues.emptySorted()
            } else {
                anyReal = true
                totalCost += v.cost()
            }
            values[i] = v
            starts[i] = context.docBase
        }
        starts[size] = r.maxDoc()

        if (anyReal == false) {
            return null
        } else {
            val cacheHelper: IndexReader.CacheHelper? = r.readerCacheHelper
            val owner: IndexReader.CacheKey? =
                cacheHelper?.key
            val mapping: OrdinalMap = OrdinalMap.build(
                owner,
                values,
                PackedInts.DEFAULT
            )
            return MultiSortedDocValues(values, starts, mapping, totalCost)
        }
    }

    /**
     * Returns a SortedSetDocValues for a reader's docvalues (potentially doing extremely slow
     * things).
     *
     *
     * This is an extremely slow way to access sorted values. Instead, access them per-segment with
     * [LeafReader.getSortedSetDocValues]
     */
    @Throws(IOException::class)
    fun getSortedSetValues(
        r: IndexReader,
        field: String
    ): SortedSetDocValues? {
        val leaves: MutableList<LeafReaderContext> = r.leaves()
        val size = leaves.size

        if (size == 0) {
            return null
        } else if (size == 1) {
            return leaves[0].reader().getSortedSetDocValues(field)
        }

        var anyReal = false
        val values: Array<SortedSetDocValues> =
            kotlin.arrayOfNulls<SortedSetDocValues>(size) as Array<SortedSetDocValues>
        val starts = IntArray(size + 1)
        var totalCost: Long = 0
        for (i in 0..<size) {
            val context: LeafReaderContext = leaves[i]
            var v: SortedSetDocValues? = context.reader().getSortedSetDocValues(field)
            if (v == null) {
                v = DocValues.emptySortedSet()
            } else {
                anyReal = true
                totalCost += v.cost()
            }
            values[i] = v
            starts[i] = context.docBase
        }
        starts[size] = r.maxDoc()

        if (anyReal == false) {
            return null
        } else {
            val cacheHelper: IndexReader.CacheHelper? = r.readerCacheHelper
            val owner: IndexReader.CacheKey? =
                cacheHelper?.key
            val mapping: OrdinalMap = OrdinalMap.build(
                owner,
                values,
                PackedInts.DEFAULT
            )
            return MultiSortedSetDocValues(values, starts, mapping, totalCost)
        }
    }

    /**
     * Implements SortedDocValues over n subs, using an OrdinalMap
     *
     * @lucene.internal
     */
    class MultiSortedDocValues(
        values: Array<SortedDocValues>,
        docStarts: IntArray,
        mapping: OrdinalMap,
        totalCost: Long
    ) : SortedDocValues() {
        /** docbase for each leaf: parallel with [.values]  */
        val docStarts: IntArray

        /** leaf values  */
        val values: Array<SortedDocValues>

        /** ordinal map mapping ords from `values` to global ord space  */
        val mapping: OrdinalMap

        private val totalCost: Long

        private var nextLeaf = 0
        private var currentValues: SortedDocValues? = null
        private var currentDocStart = 0
        private var docID = -1

        /** Creates a new MultiSortedDocValues over `values`  */
        init {
            assert(docStarts.size == values.size + 1)
            this.values = values
            this.docStarts = docStarts
            this.mapping = mapping
            this.totalCost = totalCost
        }

        override fun docID(): Int {
            return docID
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            while (true) {
                while (currentValues == null) {
                    if (nextLeaf == values.size) {
                        docID = NO_MORE_DOCS
                        return docID
                    }
                    currentDocStart = docStarts[nextLeaf]
                    currentValues = values[nextLeaf]
                    nextLeaf++
                }

                val newDocID: Int = currentValues!!.nextDoc()

                if (newDocID == NO_MORE_DOCS) {
                    currentValues = null
                } else {
                    docID = currentDocStart + newDocID
                    return docID
                }
            }
        }

        @Throws(IOException::class)
        override fun advance(targetDocID: Int): Int {
            require(targetDocID > docID) {
                ("can only advance beyond current document: on docID="
                        + docID
                        + " but targetDocID="
                        + targetDocID)
            }
            val readerIndex: Int = ReaderUtil.subIndex(targetDocID, docStarts)
            if (readerIndex >= nextLeaf) {
                if (readerIndex == values.size) {
                    currentValues = null
                    docID = NO_MORE_DOCS
                    return docID
                }
                currentDocStart = docStarts[readerIndex]
                currentValues = values[readerIndex]
                nextLeaf = readerIndex + 1
            }
            val newDocID: Int = currentValues!!.advance(targetDocID - currentDocStart)
            if (newDocID == NO_MORE_DOCS) {
                currentValues = null
                return nextDoc()
            } else {
                docID = currentDocStart + newDocID
                return docID
            }
        }

        @Throws(IOException::class)
        override fun advanceExact(targetDocID: Int): Boolean {
            require(targetDocID >= docID) {
                ("can only advance beyond current document: on docID="
                        + docID
                        + " but targetDocID="
                        + targetDocID)
            }
            val readerIndex: Int = ReaderUtil.subIndex(targetDocID, docStarts)
            if (readerIndex >= nextLeaf) {
                require(readerIndex != values.size) { "Out of range: $targetDocID" }
                currentDocStart = docStarts[readerIndex]
                currentValues = values[readerIndex]
                nextLeaf = readerIndex + 1
            }
            docID = targetDocID
            if (currentValues == null) {
                return false
            }
            return currentValues!!.advanceExact(targetDocID - currentDocStart)
        }

        @Throws(IOException::class)
        override fun ordValue(): Int {
            return mapping.getGlobalOrds(nextLeaf - 1).get(currentValues!!.ordValue().toLong()).toInt()
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Int): BytesRef? {
            val subIndex: Int = mapping.getFirstSegmentNumber(ord.toLong())
            val segmentOrd = mapping.getFirstSegmentOrd(ord.toLong()).toInt()
            return values[subIndex].lookupOrd(segmentOrd)
        }

        override val valueCount: Int
            get() = mapping.valueCount.toInt()

        override fun cost(): Long {
            return totalCost
        }
    }

    /**
     * Implements MultiSortedSetDocValues over n subs, using an OrdinalMap
     *
     * @lucene.internal
     */
    class MultiSortedSetDocValues(
        values: Array<SortedSetDocValues>,
        docStarts: IntArray,
        mapping: OrdinalMap,
        totalCost: Long
    ) : SortedSetDocValues() {
        /** docbase for each leaf: parallel with [.values]  */
        val docStarts: IntArray

        /** leaf values  */
        val values: Array<SortedSetDocValues>

        /** ordinal map mapping ords from `values` to global ord space  */
        val mapping: OrdinalMap

        private val totalCost: Long

        private var nextLeaf = 0
        private var currentValues: SortedSetDocValues? = null
        private var currentDocStart = 0
        private var docID = -1

        /** Creates a new MultiSortedSetDocValues over `values`  */
        init {
            assert(docStarts.size == values.size + 1)
            this.values = values
            this.docStarts = docStarts
            this.mapping = mapping
            this.totalCost = totalCost
        }

        override fun docID(): Int {
            return docID
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            while (true) {
                while (currentValues == null) {
                    if (nextLeaf == values.size) {
                        docID = NO_MORE_DOCS
                        return docID
                    }
                    currentDocStart = docStarts[nextLeaf]
                    currentValues = values[nextLeaf]
                    nextLeaf++
                }

                val newDocID: Int = currentValues!!.nextDoc()

                if (newDocID == NO_MORE_DOCS) {
                    currentValues = null
                } else {
                    docID = currentDocStart + newDocID
                    return docID
                }
            }
        }

        @Throws(IOException::class)
        override fun advance(targetDocID: Int): Int {
            require(targetDocID > docID) {
                ("can only advance beyond current document: on docID="
                        + docID
                        + " but targetDocID="
                        + targetDocID)
            }
            val readerIndex: Int = ReaderUtil.subIndex(targetDocID, docStarts)
            if (readerIndex >= nextLeaf) {
                if (readerIndex == values.size) {
                    currentValues = null
                    docID = NO_MORE_DOCS
                    return docID
                }
                currentDocStart = docStarts[readerIndex]
                currentValues = values[readerIndex]
                nextLeaf = readerIndex + 1
            }
            val newDocID: Int = currentValues!!.advance(targetDocID - currentDocStart)
            if (newDocID == NO_MORE_DOCS) {
                currentValues = null
                return nextDoc()
            } else {
                docID = currentDocStart + newDocID
                return docID
            }
        }

        @Throws(IOException::class)
        override fun advanceExact(targetDocID: Int): Boolean {
            require(targetDocID >= docID) {
                ("can only advance beyond current document: on docID="
                        + docID
                        + " but targetDocID="
                        + targetDocID)
            }
            val readerIndex: Int = ReaderUtil.subIndex(targetDocID, docStarts)
            if (readerIndex >= nextLeaf) {
                require(readerIndex != values.size) { "Out of range: $targetDocID" }
                currentDocStart = docStarts[readerIndex]
                currentValues = values[readerIndex]
                nextLeaf = readerIndex + 1
            }
            docID = targetDocID
            if (currentValues == null) {
                return false
            }
            return currentValues!!.advanceExact(targetDocID - currentDocStart)
        }

        @Throws(IOException::class)
        override fun nextOrd(): Long {
            val segmentOrd: Long = currentValues!!.nextOrd()
            return mapping.getGlobalOrds(nextLeaf - 1).get(segmentOrd)
        }

        override fun docValueCount(): Int {
            return currentValues!!.docValueCount()
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Long): BytesRef? {
            val subIndex: Int = mapping.getFirstSegmentNumber(ord)
            val segmentOrd: Long = mapping.getFirstSegmentOrd(ord)
            return values[subIndex].lookupOrd(segmentOrd)
        }

        override val valueCount: Long
            get() = mapping.valueCount

        override fun cost(): Long {
            return totalCost
        }
    }
}
