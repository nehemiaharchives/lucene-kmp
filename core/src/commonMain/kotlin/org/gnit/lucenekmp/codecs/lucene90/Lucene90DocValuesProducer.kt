package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat.Companion.SKIP_INDEX_JUMP_LENGTH_PER_LEVEL
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat.Companion.SKIP_INDEX_MAX_LEVEL
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat.Companion.TERMS_DICT_BLOCK_LZ4_SHIFT
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus.END
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus.FOUND
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus.NOT_FOUND
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.LongValues
import org.gnit.lucenekmp.util.compress.LZ4
import org.gnit.lucenekmp.util.packed.DirectMonotonicReader
import org.gnit.lucenekmp.util.packed.DirectReader
import kotlin.math.max
import kotlin.math.min
import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.toUnsignedInt

/** reader for [Lucene90DocValuesFormat]  */
internal class Lucene90DocValuesProducer : DocValuesProducer {
    private val numerics: IntObjectHashMap<NumericEntry>
    private val binaries: IntObjectHashMap<BinaryEntry>
    private val sorted: IntObjectHashMap<SortedEntry>
    private val sortedSets: IntObjectHashMap<SortedSetEntry>
    private val sortedNumerics: IntObjectHashMap<SortedNumericEntry>
    private val skippers: IntObjectHashMap<DocValuesSkipperEntry>
    private val data: IndexInput
    private val maxDoc: Int
    private var version = -1
    private val merging: Boolean

    /** expert: instantiates a new reader  */
    constructor(
        state: SegmentReadState,
        dataCodec: String,
        dataExtension: String,
        metaCodec: String,
        metaExtension: String
    ) {
        val metaName: String =
            IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, metaExtension)
        this.maxDoc = state.segmentInfo.maxDoc()
        numerics = IntObjectHashMap()
        binaries = IntObjectHashMap()
        sorted = IntObjectHashMap()
        sortedSets = IntObjectHashMap()
        sortedNumerics = IntObjectHashMap()
        skippers = IntObjectHashMap()
        merging = false

        state.directory.openChecksumInput(metaName).use { `in` ->
            var priorE: Throwable? = null
            try {
                version =
                    CodecUtil.checkIndexHeader(
                        `in`,
                        metaCodec,
                        Lucene90DocValuesFormat.VERSION_START,
                        Lucene90DocValuesFormat.VERSION_CURRENT,
                        state.segmentInfo.getId(),
                        state.segmentSuffix
                    )

                readFields(`in`, state.fieldInfos)
            } catch (exception: Throwable) {
                priorE = exception
            } finally {
                CodecUtil.checkFooter(`in`, priorE)
            }
        }
        val dataName: String =
            IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, dataExtension)
        // Doc-values have a forward-only access pattern, so pass ReadAdvice.NORMAL to perform
        // readahead.
        this.data =
            state.directory.openInput(dataName, state.context.withReadAdvice(ReadAdvice.NORMAL))
        var success = false
        try {
            val version2: Int =
                CodecUtil.checkIndexHeader(
                    data,
                    dataCodec,
                    Lucene90DocValuesFormat.VERSION_START,
                    Lucene90DocValuesFormat.VERSION_CURRENT,
                    state.segmentInfo.getId(),
                    state.segmentSuffix
                )
            if (version != version2) {
                throw CorruptIndexException(
                    "Format versions mismatch: meta=$version, data=$version2", data
                )
            }

            // NOTE: data file is too costly to verify checksum against all the bytes on open,
            // but for now we at least verify proper structure of the checksum footer: which looks
            // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
            // such as file truncation.
            CodecUtil.retrieveChecksum(data)

            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this.data)
            }
        }
    }

    // Used for cloning
    private constructor(
        numerics: IntObjectHashMap<NumericEntry>,
        binaries: IntObjectHashMap<BinaryEntry>,
        sorted: IntObjectHashMap<SortedEntry>,
        sortedSets: IntObjectHashMap<SortedSetEntry>,
        sortedNumerics: IntObjectHashMap<SortedNumericEntry>,
        skippers: IntObjectHashMap<DocValuesSkipperEntry>,
        data: IndexInput,
        maxDoc: Int,
        version: Int,
        merging: Boolean
    ) {
        this.numerics = numerics
        this.binaries = binaries
        this.sorted = sorted
        this.sortedSets = sortedSets
        this.sortedNumerics = sortedNumerics
        this.skippers = skippers
        this.data = data.clone()
        this.maxDoc = maxDoc
        this.version = version
        this.merging = merging
    }

    override val mergeInstance: DocValuesProducer
        get() = Lucene90DocValuesProducer(
            numerics,
            binaries,
            sorted,
            sortedSets,
            sortedNumerics,
            skippers,
            data,
            maxDoc,
            version,
            true
        )

    @Throws(IOException::class)
    private fun readFields(meta: IndexInput, infos: FieldInfos) {
        var fieldNumber: Int = meta.readInt()
        while (fieldNumber != -1) {
            val info: FieldInfo? = infos.fieldInfo(fieldNumber)
            if (info == null) {
                throw CorruptIndexException("Invalid field number: $fieldNumber", meta)
            }
            val type: Byte = meta.readByte()
            if (info.docValuesSkipIndexType() !== DocValuesSkipIndexType.NONE) {
                skippers.put(info.number, readDocValueSkipperMeta(meta))
            }
            if (type == Lucene90DocValuesFormat.NUMERIC) {
                numerics.put(info.number, readNumeric(meta))
            } else if (type == Lucene90DocValuesFormat.BINARY) {
                binaries.put(info.number, readBinary(meta))
            } else if (type == Lucene90DocValuesFormat.SORTED) {
                sorted.put(info.number, readSorted(meta))
            } else if (type == Lucene90DocValuesFormat.SORTED_SET) {
                sortedSets.put(info.number, readSortedSet(meta))
            } else if (type == Lucene90DocValuesFormat.SORTED_NUMERIC) {
                sortedNumerics.put(info.number, readSortedNumeric(meta))
            } else {
                throw CorruptIndexException("invalid type: $type", meta)
            }
            fieldNumber = meta.readInt()
        }
    }

    @Throws(IOException::class)
    private fun readNumeric(meta: IndexInput): NumericEntry {
        val entry = NumericEntry()
        readNumeric(meta, entry)
        return entry
    }

    @Throws(IOException::class)
    private fun readDocValueSkipperMeta(meta: IndexInput): DocValuesSkipperEntry {
        val offset: Long = meta.readLong()
        val length: Long = meta.readLong()
        val maxValue: Long = meta.readLong()
        val minValue: Long = meta.readLong()
        val docCount: Int = meta.readInt()
        val maxDocID: Int = meta.readInt()

        return DocValuesSkipperEntry(offset, length, minValue, maxValue, docCount, maxDocID)
    }

    @Throws(IOException::class)
    private fun readNumeric(meta: IndexInput, entry: NumericEntry) {
        entry.docsWithFieldOffset = meta.readLong()
        entry.docsWithFieldLength = meta.readLong()
        entry.jumpTableEntryCount = meta.readShort()
        entry.denseRankPower = meta.readByte()
        entry.numValues = meta.readLong()
        val tableSize: Int = meta.readInt()
        if (tableSize > 256) {
            throw CorruptIndexException("invalid table size: $tableSize", meta)
        }
        if (tableSize >= 0) {
            entry.table = LongArray(tableSize)
            for (i in 0..<tableSize) {
                entry.table!![i] = meta.readLong()
            }
        }
        if (tableSize < -1) {
            entry.blockShift = -2 - tableSize
        } else {
            entry.blockShift = -1
        }
        entry.bitsPerValue = meta.readByte()
        entry.minValue = meta.readLong()
        entry.gcd = meta.readLong()
        entry.valuesOffset = meta.readLong()
        entry.valuesLength = meta.readLong()
        entry.valueJumpTableOffset = meta.readLong()
    }

    @Throws(IOException::class)
    private fun readBinary(meta: IndexInput): BinaryEntry {
        val entry = BinaryEntry()
        entry.dataOffset = meta.readLong()
        entry.dataLength = meta.readLong()
        entry.docsWithFieldOffset = meta.readLong()
        entry.docsWithFieldLength = meta.readLong()
        entry.jumpTableEntryCount = meta.readShort()
        entry.denseRankPower = meta.readByte()
        entry.numDocsWithField = meta.readInt()
        entry.minLength = meta.readInt()
        entry.maxLength = meta.readInt()
        if (entry.minLength < entry.maxLength) {
            entry.addressesOffset = meta.readLong()

            // Old count of uncompressed addresses
            val numAddresses = entry.numDocsWithField + 1L

            val blockShift: Int = meta.readVInt()
            entry.addressesMeta = DirectMonotonicReader.loadMeta(meta, numAddresses, blockShift)
            entry.addressesLength = meta.readLong()
        }
        return entry
    }

    @Throws(IOException::class)
    private fun readSorted(meta: IndexInput): SortedEntry {
        val entry = SortedEntry()
        entry.ordsEntry = NumericEntry()
        readNumeric(meta, entry.ordsEntry)
        entry.termsDictEntry = TermsDictEntry()
        readTermDict(meta, entry.termsDictEntry)
        return entry
    }

    @Throws(IOException::class)
    private fun readSortedSet(meta: IndexInput): SortedSetEntry {
        val entry = SortedSetEntry()
        val multiValued: Byte = meta.readByte()
        when (multiValued) {
            0.toByte() -> {
                entry.singleValueEntry = readSorted(meta)
                return entry
            }

            1.toByte() -> {}
            else -> throw CorruptIndexException("Invalid multiValued flag: $multiValued", meta)
        }
        entry.ordsEntry = SortedNumericEntry()
        readSortedNumeric(meta, entry.ordsEntry)
        entry.termsDictEntry = TermsDictEntry()
        readTermDict(meta, entry.termsDictEntry)
        return entry
    }

    @Throws(IOException::class)
    private fun readSortedNumeric(meta: IndexInput): SortedNumericEntry {
        val entry = SortedNumericEntry()
        readSortedNumeric(meta, entry)
        return entry
    }

    @Throws(IOException::class)
    private fun readSortedNumeric(meta: IndexInput, entry: SortedNumericEntry): SortedNumericEntry {
        readNumeric(meta, entry)
        entry.numDocsWithField = meta.readInt()
        if (entry.numDocsWithField.toLong() != entry.numValues) {
            entry.addressesOffset = meta.readLong()
            val blockShift: Int = meta.readVInt()
            entry.addressesMeta =
                DirectMonotonicReader.loadMeta(meta, entry.numDocsWithField.toLong() + 1, blockShift)
            entry.addressesLength = meta.readLong()
        }
        return entry
    }

    @Throws(IOException::class)
    override fun close() {
        data.close()
    }

    private data class DocValuesSkipperEntry(
        val offset: Long,
        val length: Long,
        val minValue: Long,
        val maxValue: Long,
        val docCount: Int,
        val maxDocId: Int
    )

    private open class NumericEntry {
        var table: LongArray? = null
        var blockShift: Int = 0
        var bitsPerValue: Byte = 0
        var docsWithFieldOffset: Long = 0
        var docsWithFieldLength: Long = 0
        var jumpTableEntryCount: Short = 0
        var denseRankPower: Byte = 0
        var numValues: Long = 0
        var minValue: Long = 0
        var gcd: Long = 0
        var valuesOffset: Long = 0
        var valuesLength: Long = 0
        var valueJumpTableOffset: Long = 0 // -1 if no jump-table
    }

    private class BinaryEntry {
        var dataOffset: Long = 0
        var dataLength: Long = 0
        var docsWithFieldOffset: Long = 0
        var docsWithFieldLength: Long = 0
        var jumpTableEntryCount: Short = 0
        var denseRankPower: Byte = 0
        var numDocsWithField: Int = 0
        var minLength: Int = 0
        var maxLength: Int = 0
        var addressesOffset: Long = 0
        var addressesLength: Long = 0
        lateinit var addressesMeta: DirectMonotonicReader.Meta
    }

    private class TermsDictEntry {
        var termsDictSize: Long = 0
        lateinit var termsAddressesMeta: DirectMonotonicReader.Meta
        var maxTermLength: Int = 0
        var termsDataOffset: Long = 0
        var termsDataLength: Long = 0
        var termsAddressesOffset: Long = 0
        var termsAddressesLength: Long = 0
        var termsDictIndexShift: Int = 0
        lateinit var termsIndexAddressesMeta: DirectMonotonicReader.Meta
        var termsIndexOffset: Long = 0
        var termsIndexLength: Long = 0
        var termsIndexAddressesOffset: Long = 0
        var termsIndexAddressesLength: Long = 0

        var maxBlockLength: Int = 0
    }

    private class SortedEntry {
        lateinit var ordsEntry: NumericEntry
        lateinit var termsDictEntry: TermsDictEntry
    }

    private class SortedSetEntry {
        lateinit var singleValueEntry: SortedEntry
        lateinit var ordsEntry: SortedNumericEntry
        lateinit var termsDictEntry: TermsDictEntry
    }

    private class SortedNumericEntry : NumericEntry() {
        var numDocsWithField: Int = 0
        lateinit var addressesMeta: DirectMonotonicReader.Meta
        var addressesOffset: Long = 0
        var addressesLength: Long = 0
    }

    @Throws(IOException::class)
    override fun getNumeric(field: FieldInfo): NumericDocValues {
        val entry: NumericEntry = numerics[field.number]!!
        return getNumeric(entry)
    }

    private abstract class DenseNumericDocValues(val maxDoc: Int) : NumericDocValues() {
        var doc: Int = -1

        override fun docID(): Int {
            return doc
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return advance(doc + 1)
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            if (target >= maxDoc) {
                return NO_MORE_DOCS.also { doc = it }
            }
            return target.also { doc = it }
        }

        override fun advanceExact(target: Int): Boolean {
            doc = target
            return true
        }

        override fun cost(): Long {
            return maxDoc.toLong()
        }
    }

    private abstract class SparseNumericDocValues(val disi: IndexedDISI) : NumericDocValues() {

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return disi.advance(target)
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            return disi.advanceExact(target)
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return disi.nextDoc()
        }

        override fun docID(): Int {
            return disi.docID()
        }

        override fun cost(): Long {
            return disi.cost()
        }
    }

    private fun getDirectReaderInstance(
        slice: RandomAccessInput, bitsPerValue: Int, offset: Long, numValues: Long
    ): LongValues {
        return if (merging) {
            DirectReader.getMergeInstance(slice, bitsPerValue, offset, numValues)!!
        } else {
            DirectReader.getInstance(slice, bitsPerValue, offset)
        }
    }

    @Throws(IOException::class)
    private fun getNumeric(entry: NumericEntry): NumericDocValues {
        if (entry.docsWithFieldOffset == -2L) {
            // empty
            return DocValues.emptyNumeric()
        } else if (entry.docsWithFieldOffset == -1L) {
            // dense
            if (entry.bitsPerValue.toInt() == 0) {
                return object : DenseNumericDocValues(maxDoc) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return entry.minValue
                    }
                }
            } else {
                val slice: RandomAccessInput =
                    data.randomAccessSlice(entry.valuesOffset, entry.valuesLength)
                // Prefetch the first page of data. Following pages are expected to get prefetched through
                // read-ahead.
                if (slice.length() > 0) {
                    slice.prefetch(0, 1)
                }
                if (entry.blockShift >= 0) {
                    // dense but split into blocks of different bits per value
                    return object : DenseNumericDocValues(maxDoc) {
                        val vBPVReader: VaryingBPVReader = this@Lucene90DocValuesProducer.VaryingBPVReader(entry, slice)

                        @Throws(IOException::class)
                        override fun longValue(): Long {
                            return vBPVReader.getLongValue(doc.toLong())
                        }
                    }
                } else {
                    val values: LongValues =
                        getDirectReaderInstance(slice, entry.bitsPerValue.toInt(), 0L, entry.numValues)
                    if (entry.table != null) {
                        val table = entry.table
                        return object : DenseNumericDocValues(maxDoc) {
                            @Throws(IOException::class)
                            override fun longValue(): Long {
                                return table!![values.get(doc.toLong()).toInt()]
                            }
                        }
                    } else if (entry.gcd == 1L && entry.minValue == 0L) {
                        // Common case for ordinals, which are encoded as numerics
                        return object : DenseNumericDocValues(maxDoc) {
                            @Throws(IOException::class)
                            override fun longValue(): Long {
                                return values.get(doc.toLong())
                            }
                        }
                    } else {
                        val mul = entry.gcd
                        val delta = entry.minValue
                        return object : DenseNumericDocValues(maxDoc) {
                            @Throws(IOException::class)
                            override fun longValue(): Long {
                                return mul * values.get(doc.toLong()) + delta
                            }
                        }
                    }
                }
            }
        } else {
            // sparse
            val disi =
                IndexedDISI(
                    data,
                    entry.docsWithFieldOffset,
                    entry.docsWithFieldLength,
                    entry.jumpTableEntryCount.toInt(),
                    entry.denseRankPower,
                    entry.numValues
                )
            if (entry.bitsPerValue.toInt() == 0) {
                return object : SparseNumericDocValues(disi) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return entry.minValue
                    }
                }
            } else {
                val slice: RandomAccessInput =
                    data.randomAccessSlice(entry.valuesOffset, entry.valuesLength)
                // Prefetch the first page of data. Following pages are expected to get prefetched through
                // read-ahead.
                if (slice.length() > 0) {
                    slice.prefetch(0, 1)
                }
                if (entry.blockShift >= 0) {
                    // sparse and split into blocks of different bits per value
                    return object : SparseNumericDocValues(disi) {
                        val vBPVReader: VaryingBPVReader = this@Lucene90DocValuesProducer.VaryingBPVReader(entry, slice)

                        @Throws(IOException::class)
                        override fun longValue(): Long {
                            val index = disi.index()
                            return vBPVReader.getLongValue(index.toLong())
                        }
                    }
                } else {
                    val values: LongValues =
                        getDirectReaderInstance(slice, entry.bitsPerValue.toInt(), 0L, entry.numValues)
                    if (entry.table != null) {
                        val table = entry.table
                        return object : SparseNumericDocValues(disi) {
                            @Throws(IOException::class)
                            override fun longValue(): Long {
                                return table!![values.get(disi.index().toLong()).toInt()]
                            }
                        }
                    } else if (entry.gcd == 1L && entry.minValue == 0L) {
                        return object : SparseNumericDocValues(disi) {
                            @Throws(IOException::class)
                            override fun longValue(): Long {
                                return values.get(disi.index().toLong())
                            }
                        }
                    } else {
                        val mul = entry.gcd
                        val delta = entry.minValue
                        return object : SparseNumericDocValues(disi) {
                            @Throws(IOException::class)
                            override fun longValue(): Long {
                                return mul * values.get(disi.index().toLong()) + delta
                            }
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun getNumericValues(entry: NumericEntry): LongValues {
        if (entry.bitsPerValue.toInt() == 0) {
            return object : LongValues() {
                override fun get(index: Long): Long {
                    return entry.minValue
                }
            }
        } else {
            val slice: RandomAccessInput =
                data.randomAccessSlice(entry.valuesOffset, entry.valuesLength)
            // Prefetch the first page of data. Following pages are expected to get prefetched through
            // read-ahead.
            if (slice.length() > 0) {
                slice.prefetch(0, 1)
            }
            if (entry.blockShift >= 0) {
                return object : LongValues() {
                    val vBPVReader: VaryingBPVReader = this@Lucene90DocValuesProducer.VaryingBPVReader(entry, slice)

                    override fun get(index: Long): Long {
                        try {
                            return vBPVReader.getLongValue(index)
                        } catch (e: IOException) {
                            throw RuntimeException(e)
                        }
                    }
                }
            } else {
                val values: LongValues =
                    getDirectReaderInstance(slice, entry.bitsPerValue.toInt(), 0L, entry.numValues)
                if (entry.table != null) {
                    val table = entry.table
                    return object : LongValues() {
                        override fun get(index: Long): Long {
                            return table!![values.get(index).toInt()]
                        }
                    }
                } else if (entry.gcd != 1L) {
                    val gcd = entry.gcd
                    val minValue = entry.minValue
                    return object : LongValues() {
                        override fun get(index: Long): Long {
                            return values.get(index) * gcd + minValue
                        }
                    }
                } else if (entry.minValue != 0L) {
                    val minValue = entry.minValue
                    return object : LongValues() {
                        override fun get(index: Long): Long {
                            return values.get(index) + minValue
                        }
                    }
                } else {
                    return values
                }
            }
        }
    }

    private abstract class DenseBinaryDocValues(val maxDoc: Int) : BinaryDocValues() {
        var doc: Int = -1

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return advance(doc + 1)
        }

        override fun docID(): Int {
            return doc
        }

        override fun cost(): Long {
            return maxDoc.toLong()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            if (target >= maxDoc) {
                return NO_MORE_DOCS.also { doc = it }
            }
            return target.also { doc = it }
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            doc = target
            return true
        }
    }

    private abstract class SparseBinaryDocValues(val disi: IndexedDISI) : BinaryDocValues() {

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return disi.nextDoc()
        }

        override fun docID(): Int {
            return disi.docID()
        }

        override fun cost(): Long {
            return disi.cost()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return disi.advance(target)
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            return disi.advanceExact(target)
        }
    }

    @Throws(IOException::class)
    override fun getBinary(field: FieldInfo): BinaryDocValues {
        val entry: BinaryEntry = binaries[field.number]!!

        if (entry.docsWithFieldOffset == -2L) {
            return DocValues.emptyBinary()
        }

        val bytesSlice: RandomAccessInput = data.randomAccessSlice(entry.dataOffset, entry.dataLength)
        // Prefetch the first page of data. Following pages are expected to get prefetched through
        // read-ahead.
        if (bytesSlice.length() > 0) {
            bytesSlice.prefetch(0, 1)
        }

        if (entry.docsWithFieldOffset == -1L) {
            // dense
            if (entry.minLength == entry.maxLength) {
                // fixed length
                val length = entry.maxLength
                return object : DenseBinaryDocValues(maxDoc) {
                    val bytes: BytesRef = BytesRef(ByteArray(length), 0, length)

                    @Throws(IOException::class)
                    override fun binaryValue(): BytesRef {
                        bytesSlice.readBytes(doc.toLong() * length, bytes.bytes, 0, length)
                        return bytes
                    }
                }
            } else {
                // variable length
                val addressesData: RandomAccessInput =
                    this.data.randomAccessSlice(entry.addressesOffset, entry.addressesLength)
                // Prefetch the first page of data. Following pages are expected to get prefetched through
                // read-ahead.
                if (addressesData.length() > 0) {
                    addressesData.prefetch(0, 1)
                }
                val addresses: LongValues =
                    DirectMonotonicReader.getInstance(entry.addressesMeta, addressesData, merging)
                return object : DenseBinaryDocValues(maxDoc) {
                    val bytes: BytesRef = BytesRef(ByteArray(entry.maxLength), 0, entry.maxLength)

                    @Throws(IOException::class)
                    override fun binaryValue(): BytesRef {
                        val startOffset: Long = addresses.get(doc.toLong())
                        bytes.length = (addresses.get(doc + 1L) - startOffset).toInt()
                        bytesSlice.readBytes(startOffset, bytes.bytes, 0, bytes.length)
                        return bytes
                    }
                }
            }
        } else {
            // sparse
            val disi =
                IndexedDISI(
                    data,
                    entry.docsWithFieldOffset,
                    entry.docsWithFieldLength,
                    entry.jumpTableEntryCount.toInt(),
                    entry.denseRankPower,
                    entry.numDocsWithField.toLong()
                )
            if (entry.minLength == entry.maxLength) {
                // fixed length
                val length = entry.maxLength
                return object : SparseBinaryDocValues(disi) {
                    val bytes: BytesRef = BytesRef(ByteArray(length), 0, length)

                    @Throws(IOException::class)
                    override fun binaryValue(): BytesRef {
                        bytesSlice.readBytes(disi.index().toLong() * length, bytes.bytes, 0, length)
                        return bytes
                    }
                }
            } else {
                // variable length
                val addressesData: RandomAccessInput =
                    this.data.randomAccessSlice(entry.addressesOffset, entry.addressesLength)
                // Prefetch the first page of data. Following pages are expected to get prefetched through
                // read-ahead.
                if (addressesData.length() > 0) {
                    addressesData.prefetch(0, 1)
                }
                val addresses: LongValues =
                    DirectMonotonicReader.getInstance(entry.addressesMeta, addressesData)
                return object : SparseBinaryDocValues(disi) {
                    val bytes: BytesRef = BytesRef(ByteArray(entry.maxLength), 0, entry.maxLength)

                    @Throws(IOException::class)
                    override fun binaryValue(): BytesRef {
                        val index = disi.index()
                        val startOffset: Long = addresses.get(index.toLong())
                        bytes.length = (addresses.get(index + 1L) - startOffset).toInt()
                        bytesSlice.readBytes(startOffset, bytes.bytes, 0, bytes.length)
                        return bytes
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun getSorted(field: FieldInfo): SortedDocValues {
        val entry: SortedEntry = sorted[field.number]!!
        return getSorted(entry)
    }

    @Throws(IOException::class)
    private fun getSorted(entry: SortedEntry): SortedDocValues {
        // Specialize the common case for ordinals: single block of packed integers.
        val ordsEntry: NumericEntry = entry.ordsEntry
        if (ordsEntry.blockShift < 0 // single block
            && ordsEntry.bitsPerValue > 0
        ) { // more than 1 value

            check(!(ordsEntry.gcd != 1L || ordsEntry.minValue != 0L || ordsEntry.table != null)) { "Ordinals shouldn't use GCD, offset or table compression" }

            val slice: RandomAccessInput =
                data.randomAccessSlice(ordsEntry.valuesOffset, ordsEntry.valuesLength)
            // Prefetch the first page of data. Following pages are expected to get prefetched through
            // read-ahead.
            if (slice.length() > 0) {
                slice.prefetch(0, 1)
            }
            val values: LongValues =
                getDirectReaderInstance(slice, ordsEntry.bitsPerValue.toInt(), 0L, ordsEntry.numValues)

            if (ordsEntry.docsWithFieldOffset == -1L) { // dense
                return object : BaseSortedDocValues(entry) {
                    private val maxDoc = this@Lucene90DocValuesProducer.maxDoc
                    private var doc = -1

                    @Throws(IOException::class)
                    override fun ordValue(): Int {
                        return values.get(doc.toLong()).toInt()
                    }

                    @Throws(IOException::class)
                    override fun advanceExact(target: Int): Boolean {
                        doc = target
                        return true
                    }

                    override fun docID(): Int {
                        return doc
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        return advance(doc + 1)
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        if (target >= maxDoc) {
                            return NO_MORE_DOCS.also { doc = it }
                        }
                        return target.also { doc = it }
                    }

                    override fun cost(): Long {
                        return maxDoc.toLong()
                    }
                }
            } else if (ordsEntry.docsWithFieldOffset >= 0) { // sparse but non-empty
                val disi =
                    IndexedDISI(
                        data,
                        ordsEntry.docsWithFieldOffset,
                        ordsEntry.docsWithFieldLength,
                        ordsEntry.jumpTableEntryCount.toInt(),
                        ordsEntry.denseRankPower,
                        ordsEntry.numValues
                    )

                return object : BaseSortedDocValues(entry) {
                    @Throws(IOException::class)
                    override fun ordValue(): Int {
                        return values.get(disi.index().toLong()).toInt()
                    }

                    @Throws(IOException::class)
                    override fun advanceExact(target: Int): Boolean {
                        return disi.advanceExact(target)
                    }

                    override fun docID(): Int {
                        return disi.docID()
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        return disi.nextDoc()
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        return disi.advance(target)
                    }

                    override fun cost(): Long {
                        return disi.cost()
                    }
                }
            }
        }

        val ords: NumericDocValues = getNumeric(entry.ordsEntry)
        return object : BaseSortedDocValues(entry) {
            @Throws(IOException::class)
            override fun ordValue(): Int {
                return ords.longValue().toInt()
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                return ords.advanceExact(target)
            }

            override fun docID(): Int {
                return ords.docID()
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                return ords.nextDoc()
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                return ords.advance(target)
            }

            override fun cost(): Long {
                return ords.cost()
            }
        }
    }

    private abstract inner class BaseSortedDocValues(val entry: SortedEntry) : SortedDocValues() {
        val termsEnum: TermsEnum

        init {
            this.termsEnum = termsEnum()
        }

        override val valueCount: Int
            get() = Math.toIntExact(entry.termsDictEntry.termsDictSize)

        @Throws(IOException::class)
        override fun lookupOrd(ord: Int): BytesRef? {
            termsEnum.seekExact(ord.toLong())
            return termsEnum.term()
        }

        @Throws(IOException::class)
        override fun lookupTerm(key: BytesRef): Int {
            val status: SeekStatus = termsEnum.seekCeil(key)
            return when (status) {
                FOUND -> Math.toIntExact(termsEnum.ord())
                NOT_FOUND, END -> Math.toIntExact(-1L - termsEnum.ord())
                /*else -> return Math.toIntExact(-1L - termsEnum.ord())*/ // unreachable
            }
        }

        @Throws(IOException::class)
        override fun termsEnum(): TermsEnum {
            return TermsDict(entry.termsDictEntry, data)
        }
    }

    private abstract inner class BaseSortedSetDocValues(val entry: SortedSetEntry, val data: IndexInput) :
        SortedSetDocValues() {
        val termsEnum: TermsEnum

        init {
            this.termsEnum = termsEnum()
        }

        override val valueCount: Long
            get() = entry.termsDictEntry.termsDictSize

        @Throws(IOException::class)
        override fun lookupOrd(ord: Long): BytesRef? {
            termsEnum.seekExact(ord)
            return termsEnum.term()
        }

        @Throws(IOException::class)
        override fun lookupTerm(key: BytesRef): Long {
            val status: SeekStatus = termsEnum.seekCeil(key)
            return when (status) {
                FOUND -> termsEnum.ord()
                NOT_FOUND, END -> -1L - termsEnum.ord()
                /*else -> return -1L - termsEnum.ord()*/ // unreachable
            }
        }

        @Throws(IOException::class)
        override fun termsEnum(): TermsEnum {
            return TermsDict(entry.termsDictEntry, data)
        }
    }

    private inner class TermsDict(val entry: TermsDictEntry, data: IndexInput) : BaseTermsEnum() {
        val blockAddresses: LongValues
        val bytes: IndexInput
        val blockMask: Long
        val indexAddresses: LongValues
        val indexBytes: RandomAccessInput
        val term: BytesRef
        val blockBuffer: BytesRef
        val blockInput: ByteArrayDataInput
        var ord: Long = -1
        var currentCompressedBlockStart: Long = -1
        var currentCompressedBlockEnd: Long = -1

        init {
            val addressesSlice: RandomAccessInput =
                data.randomAccessSlice(entry.termsAddressesOffset, entry.termsAddressesLength)
            blockAddresses =
                DirectMonotonicReader.getInstance(entry.termsAddressesMeta, addressesSlice, merging)
            bytes = data.slice("terms", entry.termsDataOffset, entry.termsDataLength)
            blockMask = (1L shl TERMS_DICT_BLOCK_LZ4_SHIFT) - 1
            val indexAddressesSlice: RandomAccessInput =
                data.randomAccessSlice(entry.termsIndexAddressesOffset, entry.termsIndexAddressesLength)
            indexAddresses =
                DirectMonotonicReader.getInstance(
                    entry.termsIndexAddressesMeta, indexAddressesSlice, merging
                )
            indexBytes = data.randomAccessSlice(entry.termsIndexOffset, entry.termsIndexLength)
            term = BytesRef(entry.maxTermLength)

            // add the max term length for the dictionary
            // add 7 padding bytes can help decompression run faster.
            val bufferSize = entry.maxBlockLength + entry.maxTermLength + LZ4_DECOMPRESSOR_PADDING
            blockBuffer = BytesRef(ByteArray(bufferSize), 0, bufferSize)
            blockInput = ByteArrayDataInput()
        }

        @Throws(IOException::class)
        override fun next(): BytesRef? {
            if (++ord >= entry.termsDictSize) {
                return null
            }

            if ((ord and blockMask) == 0L) {
                decompressBlock()
            } else {
                val input: DataInput = blockInput
                val token: Int = Byte.toUnsignedInt(input.readByte())
                var prefixLength = token and 0x0F
                var suffixLength = 1 + (token ushr 4)
                if (prefixLength == 15) {
                    prefixLength += input.readVInt()
                }
                if (suffixLength == 16) {
                    suffixLength += input.readVInt()
                }
                term.length = prefixLength + suffixLength
                input.readBytes(term.bytes, prefixLength, suffixLength)
            }
            return term
        }

        @Throws(IOException::class)
        override fun seekExact(ord: Long) {
            if (ord < 0 || ord >= entry.termsDictSize) {
                throw IndexOutOfBoundsException()
            }
            // Signed shift since ord is -1 when the terms enum is not positioned
            val currentBlockIndex = this.ord shr TERMS_DICT_BLOCK_LZ4_SHIFT
            val blockIndex = ord shr TERMS_DICT_BLOCK_LZ4_SHIFT
            if (ord < this.ord || blockIndex != currentBlockIndex) {
                // The looked up ord is before the current ord or belongs to a different block, seek again
                val blockAddress: Long = blockAddresses.get(blockIndex)
                bytes.seek(blockAddress)
                this.ord = (blockIndex shl TERMS_DICT_BLOCK_LZ4_SHIFT) - 1
            }
            // Scan to the looked up ord
            while (this.ord < ord) {
                next()
            }
        }

        @Throws(IOException::class)
        fun getTermFromIndex(index: Long): BytesRef {
            require(index >= 0 && index <= (entry.termsDictSize - 1) ushr entry.termsDictIndexShift)
            val start: Long = indexAddresses.get(index)
            term.length = (indexAddresses.get(index + 1) - start).toInt()
            indexBytes.readBytes(start, term.bytes, 0, term.length)
            return term
        }

        @Throws(IOException::class)
        fun seekTermsIndex(text: BytesRef): Long {
            var lo = 0L
            var hi = (entry.termsDictSize - 1) shr entry.termsDictIndexShift
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                getTermFromIndex(mid)
                val cmp = term.compareTo(text)
                if (cmp <= 0) {
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }

            require(hi < 0 || getTermFromIndex(hi) <= text)
            require(
                hi == ((entry.termsDictSize - 1) shr entry.termsDictIndexShift)
                        || getTermFromIndex(hi + 1) > text
            )
            require(
                (hi < 0) xor (entry.termsDictSize > 0) // return -1 iff empty term dict
            )

            return hi
        }

        @Throws(IOException::class)
        fun getFirstTermFromBlock(block: Long): BytesRef {
            require(block >= 0 && block <= (entry.termsDictSize - 1) ushr TERMS_DICT_BLOCK_LZ4_SHIFT)
            val blockAddress: Long = blockAddresses.get(block)
            bytes.seek(blockAddress)
            term.length = bytes.readVInt()
            bytes.readBytes(term.bytes, 0, term.length)
            return term
        }

        @Throws(IOException::class)
        fun seekBlock(text: BytesRef): Long {
            val index = seekTermsIndex(text)
            if (index == -1L) {
                // empty terms dict
                this.ord = 0
                return -2L
            }

            val ordLo = index shl entry.termsDictIndexShift
            val ordHi = min(entry.termsDictSize, ordLo + (1L shl entry.termsDictIndexShift)) - 1L

            var blockLo = ordLo ushr TERMS_DICT_BLOCK_LZ4_SHIFT
            var blockHi = ordHi ushr TERMS_DICT_BLOCK_LZ4_SHIFT

            while (blockLo <= blockHi) {
                val blockMid = (blockLo + blockHi) ushr 1
                getFirstTermFromBlock(blockMid)
                val cmp = term.compareTo(text)
                if (cmp <= 0) {
                    blockLo = blockMid + 1
                } else {
                    blockHi = blockMid - 1
                }
            }

            require(blockHi < 0 || getFirstTermFromBlock(blockHi) <= text)
            require(
                blockHi == ((entry.termsDictSize - 1) ushr TERMS_DICT_BLOCK_LZ4_SHIFT)
                        || getFirstTermFromBlock(blockHi + 1) > text
            )

            // read the block only if term dict is not empty
            require(entry.termsDictSize > 0)
            // reset ord and bytes to the ceiling block even if
            // text is before the first term (blockHi == -1)
            val block = max(blockHi, 0)
            val blockAddress: Long = blockAddresses.get(block)
            this.ord = block shl TERMS_DICT_BLOCK_LZ4_SHIFT
            bytes.seek(blockAddress)
            decompressBlock()

            return blockHi
        }

        @Throws(IOException::class)
        override fun seekCeil(text: BytesRef): SeekStatus {
            val block = seekBlock(text)
            if (block == -2L) {
                // empty terms dict
                require(entry.termsDictSize == 0L)
                return END
            } else if (block == -1L) {
                // before the first term
                return NOT_FOUND
            }

            while (true) {
                val cmp = term.compareTo(text)
                if (cmp == 0) {
                    return FOUND
                } else if (cmp > 0) {
                    return NOT_FOUND
                }
                if (next() == null) {
                    return END
                }
            }
        }

        @Throws(IOException::class)
        fun decompressBlock() {
            // The first term is kept uncompressed, so no need to decompress block if only
            // look up the first term when doing seek block.
            term.length = bytes.readVInt()
            bytes.readBytes(term.bytes, 0, term.length)
            val offset: Long = bytes.filePointer
            if (offset < entry.termsDataLength - 1) {
                // Avoid decompress again if we are reading a same block.
                if (currentCompressedBlockStart != offset) {
                    blockBuffer.offset = term.length
                    blockBuffer.length = bytes.readVInt()
                    // Decompress the remaining of current block, using the first term as a dictionary
                    /*java.lang.System.arraycopy(term.bytes, 0, blockBuffer.bytes, 0, blockBuffer.offset)*/
                    term.bytes.copyInto(
                        destination = blockBuffer.bytes,
                        destinationOffset = 0,
                        startIndex = 0,
                        endIndex = blockBuffer.offset
                    )
                    LZ4.decompress(bytes, blockBuffer.length, blockBuffer.bytes, blockBuffer.offset)
                    currentCompressedBlockStart = offset
                    currentCompressedBlockEnd = bytes.filePointer
                } else {
                    // Skip decompression but need to re-seek to block end.
                    bytes.seek(currentCompressedBlockEnd)
                }

                // Reset the buffer.
                blockInput.reset(blockBuffer.bytes, blockBuffer.offset, blockBuffer.length)
            }
        }

        @Throws(IOException::class)
        override fun term(): BytesRef {
            return term
        }

        @Throws(IOException::class)
        override fun ord(): Long {
            return ord
        }

        @Throws(IOException::class)
        override fun totalTermFreq(): Long {
            return -1L
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
        override fun docFreq(): Int {
            throw UnsupportedOperationException()
        }

    }

    val LZ4_DECOMPRESSOR_PADDING: Int = 7

    @Throws(IOException::class)
    override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
        val entry: SortedNumericEntry = sortedNumerics[field.number]!!
        return getSortedNumeric(entry)
    }

    @Throws(IOException::class)
    private fun getSortedNumeric(entry: SortedNumericEntry): SortedNumericDocValues {
        if (entry.numValues == entry.numDocsWithField.toLong()) {
            return DocValues.singleton(getNumeric(entry))
        }

        val addressesInput: RandomAccessInput =
            data.randomAccessSlice(entry.addressesOffset, entry.addressesLength)
        // Prefetch the first page of data. Following pages are expected to get prefetched through
        // read-ahead.
        if (addressesInput.length() > 0) {
            addressesInput.prefetch(0, 1)
        }
        val addresses: LongValues =
            DirectMonotonicReader.getInstance(entry.addressesMeta, addressesInput, merging)

        val values: LongValues = getNumericValues(entry)

        if (entry.docsWithFieldOffset == -1L) {
            // dense
            return object : SortedNumericDocValues() {
                var doc: Int = -1
                var start: Long = 0
                var end: Long = 0
                var count: Int = 0

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    return advance(doc + 1)
                }

                override fun docID(): Int {
                    return doc
                }

                override fun cost(): Long {
                    return maxDoc.toLong()
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    if (target >= maxDoc) {
                        return NO_MORE_DOCS.also { doc = it }
                    }
                    start = addresses.get(target.toLong())
                    end = addresses.get(target + 1L)
                    count = (end - start).toInt()
                    return target.also { doc = it }
                }

                @Throws(IOException::class)
                override fun advanceExact(target: Int): Boolean {
                    start = addresses.get(target.toLong())
                    end = addresses.get(target + 1L)
                    count = (end - start).toInt()
                    doc = target
                    return true
                }

                @Throws(IOException::class)
                override fun nextValue(): Long {
                    return values.get(start++)
                }

                override fun docValueCount(): Int {
                    return count
                }
            }
        } else {
            // sparse
            val disi =
                IndexedDISI(
                    data,
                    entry.docsWithFieldOffset,
                    entry.docsWithFieldLength,
                    entry.jumpTableEntryCount.toInt(),
                    entry.denseRankPower,
                    entry.numDocsWithField.toLong()
                )
            return object : SortedNumericDocValues() {
                var set: Boolean = false
                var start: Long = 0
                var end: Long = 0
                var count: Int = 0

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    set = false
                    return disi.nextDoc()
                }

                override fun docID(): Int {
                    return disi.docID()
                }

                override fun cost(): Long {
                    return disi.cost()
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    set = false
                    return disi.advance(target)
                }

                @Throws(IOException::class)
                override fun advanceExact(target: Int): Boolean {
                    set = false
                    return disi.advanceExact(target)
                }

                @Throws(IOException::class)
                override fun nextValue(): Long {
                    set()
                    return values.get(start++)
                }

                override fun docValueCount(): Int {
                    set()
                    return count
                }

                fun set() {
                    if (!set) {
                        val index = disi.index()
                        start = addresses.get(index.toLong())
                        end = addresses.get(index + 1L)
                        count = (end - start).toInt()
                        set = true
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun getSortedSet(field: FieldInfo): SortedSetDocValues {
        val entry: SortedSetEntry = sortedSets[field.number]!!
        if (entry.singleValueEntry != null) {
            return DocValues.singleton(getSorted(entry.singleValueEntry))
        }

        // Specialize the common case for ordinals: single block of packed integers.
        val ordsEntry: SortedNumericEntry = entry.ordsEntry
        if (ordsEntry.blockShift < 0 && ordsEntry.bitsPerValue > 0) {
            check(!(ordsEntry.gcd != 1L || ordsEntry.minValue != 0L || ordsEntry.table != null)) { "Ordinals shouldn't use GCD, offset or table compression" }

            val addressesInput: RandomAccessInput =
                data.randomAccessSlice(ordsEntry.addressesOffset, ordsEntry.addressesLength)
            // Prefetch the first page of data. Following pages are expected to get prefetched through
            // read-ahead.
            if (addressesInput.length() > 0) {
                addressesInput.prefetch(0, 1)
            }
            val addresses: LongValues =
                DirectMonotonicReader.getInstance(ordsEntry.addressesMeta, addressesInput)

            val slice: RandomAccessInput =
                data.randomAccessSlice(ordsEntry.valuesOffset, ordsEntry.valuesLength)
            // Prefetch the first page of data. Following pages are expected to get prefetched through
            // read-ahead.
            if (slice.length() > 0) {
                slice.prefetch(0, 1)
            }
            val values: LongValues = DirectReader.getInstance(slice, ordsEntry.bitsPerValue.toInt())

            if (ordsEntry.docsWithFieldOffset == -1L) { // dense
                return object : BaseSortedSetDocValues(entry, data) {
                    private val maxDoc = this@Lucene90DocValuesProducer.maxDoc
                    private var doc = -1
                    private var curr: Long = 0
                    private var count = 0

                    @Throws(IOException::class)
                    override fun nextOrd(): Long {
                        return values.get(curr++)
                    }

                    @Throws(IOException::class)
                    override fun advanceExact(target: Int): Boolean {
                        curr = addresses.get(target.toLong())
                        val end: Long = addresses.get(target + 1L)
                        count = (end - curr).toInt()
                        doc = target
                        return true
                    }

                    override fun docValueCount(): Int {
                        return count
                    }

                    override fun docID(): Int {
                        return doc
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        return advance(doc + 1)
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        if (target >= maxDoc) {
                            return NO_MORE_DOCS.also { doc = it }
                        }
                        curr = addresses.get(target.toLong())
                        val end: Long = addresses.get(target + 1L)
                        count = (end - curr).toInt()
                        return target.also { doc = it }
                    }

                    override fun cost(): Long {
                        return maxDoc.toLong()
                    }
                }
            } else if (ordsEntry.docsWithFieldOffset >= 0) { // sparse but non-empty
                val disi =
                    IndexedDISI(
                        data,
                        ordsEntry.docsWithFieldOffset,
                        ordsEntry.docsWithFieldLength,
                        ordsEntry.jumpTableEntryCount.toInt(),
                        ordsEntry.denseRankPower,
                        ordsEntry.numValues
                    )

                return object : BaseSortedSetDocValues(entry, data) {
                    var set: Boolean = false
                    var curr: Long = 0
                    var count: Int = 0

                    @Throws(IOException::class)
                    override fun nextOrd(): Long {
                        set()
                        return values.get(curr++)
                    }

                    @Throws(IOException::class)
                    override fun advanceExact(target: Int): Boolean {
                        set = false
                        return disi.advanceExact(target)
                    }

                    override fun docValueCount(): Int {
                        set()
                        return count
                    }

                    override fun docID(): Int {
                        return disi.docID()
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        set = false
                        return disi.nextDoc()
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        set = false
                        return disi.advance(target)
                    }

                    override fun cost(): Long {
                        return disi.cost()
                    }

                    fun set() {
                        if (!set) {
                            val index = disi.index()
                            curr = addresses.get(index.toLong())
                            val end: Long = addresses.get(index + 1L)
                            count = (end - curr).toInt()
                            set = true
                        }
                    }
                }
            }
        }

        val ords: SortedNumericDocValues = getSortedNumeric(ordsEntry)
        return object : BaseSortedSetDocValues(entry, data) {
            @Throws(IOException::class)
            override fun nextOrd(): Long {
                return ords.nextValue()
            }

            override fun docValueCount(): Int {
                return ords.docValueCount()
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                return ords.advanceExact(target)
            }

            override fun docID(): Int {
                return ords.docID()
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                return ords.nextDoc()
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                return ords.advance(target)
            }

            override fun cost(): Long {
                return ords.cost()
            }
        }
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        CodecUtil.checksumEntireFile(data)
    }

    /**
     * Reader for longs split into blocks of different bits per values. The longs are requested by
     * index and must be accessed in monotonically increasing order.
     */
    // Note: The order requirement could be removed as the jump-tables allow for backwards iteration
    // Note 2: The rankSlice is only used if an advance of > 1 block is called. Its construction could
    // be lazy
    private inner class VaryingBPVReader(val entry: NumericEntry, // 2 slices to avoid cache thrashing when using rank
                                         val slice: RandomAccessInput
    ) {
        val rankSlice = if (entry.valueJumpTableOffset == -1L)
            null
        else
            data.randomAccessSlice(
                entry.valueJumpTableOffset, data.length() - entry.valueJumpTableOffset
            )
        val shift: Int = entry.blockShift
        val mul: Long = entry.gcd
        val mask: Int = (1 shl shift) - 1

        var block: Long = -1
        var delta: Long = 0
        var offset: Long = 0
        var blockEndOffset: Long = 0
        var values: LongValues? = null

        init {
            if (rankSlice != null && rankSlice.length() > 0) {
                // Prefetch the first page of data. Following pages are expected to get prefetched through
                // read-ahead.
                rankSlice.prefetch(0, 1)
            }
        }

        @Throws(IOException::class)
        fun getLongValue(index: Long): Long {
            val block = index ushr shift
            if (this.block != block) {
                var bitsPerValue: Int
                do {
                    // If the needed block is the one directly following the current block, it is cheaper to
                    // avoid the cache
                    if (rankSlice != null && block != this.block + 1) {
                        blockEndOffset = rankSlice.readLong(block * Long.SIZE_BYTES) - entry.valuesOffset
                        this.block = block - 1
                    }
                    offset = blockEndOffset
                    bitsPerValue = slice.readByte(offset++).toInt()
                    delta = slice.readLong(offset)
                    offset += Long.SIZE_BYTES.toLong()
                    if (bitsPerValue == 0) {
                        blockEndOffset = offset
                    } else {
                        val length: Int = slice.readInt(offset)
                        offset += Int.SIZE_BYTES.toLong()
                        blockEndOffset = offset + length
                    }
                    this.block++
                } while (this.block != block)
                val numValues: Int =
                    Math.toIntExact(min(1L shl shift, entry.numValues - (block shl shift)))
                values =
                    if (bitsPerValue == 0)
                        LongValues.ZEROES
                    else
                        getDirectReaderInstance(slice, bitsPerValue, offset, numValues.toLong())
            }
            return mul * values!!.get(index and mask.toLong()) + delta
        }
    }

    @Throws(IOException::class)
    override fun getSkipper(field: FieldInfo): DocValuesSkipper {
        val entry: DocValuesSkipperEntry = skippers[field.number]!!

        val input: IndexInput = data.slice("doc value skipper", entry.offset, entry.length)
        // Prefetch the first page of data. Following pages are expected to get prefetched through
        // read-ahead.
        if (input.length() > 0) {
            input.prefetch(0, 1)
        }
        // TODO: should we write to disk the actual max level for this segment
        return object : DocValuesSkipper() {
            val minDocID: IntArray = IntArray(SKIP_INDEX_MAX_LEVEL)
            val maxDocID: IntArray = IntArray(SKIP_INDEX_MAX_LEVEL)

            init {
                for (i in 0..<SKIP_INDEX_MAX_LEVEL) {
                    maxDocID[i] = -1
                    minDocID[i] = maxDocID[i]
                }
            }

            val minValue: LongArray = LongArray(SKIP_INDEX_MAX_LEVEL)
            val maxValue: LongArray = LongArray(SKIP_INDEX_MAX_LEVEL)
            val docCount: IntArray = IntArray(SKIP_INDEX_MAX_LEVEL)
            var levels: Int = 1

            @Throws(IOException::class)
            override fun advance(target: Int) {
                if (target > entry.maxDocId) {
                    // skipper is exhausted
                    for (i in 0..<SKIP_INDEX_MAX_LEVEL) {
                        maxDocID[i] = DocIdSetIterator.NO_MORE_DOCS
                        minDocID[i] = maxDocID[i]
                    }
                } else {
                    // find next interval
                    require(target > maxDocID[0]) { "target must be bigger that current interval" }
                    while (true) {
                        levels = input.readByte().toInt()
                        require(
                            levels <= SKIP_INDEX_MAX_LEVEL && levels > 0
                        ) { "level out of range [$levels]" }
                        var valid = true
                        // check if current interval is competitive or we can jump to the next position
                        for (level in levels - 1 downTo 0) {
                            if ((input.readInt().also { maxDocID[level] = it }) < target) {
                                input.skipBytes(SKIP_INDEX_JUMP_LENGTH_PER_LEVEL[level]) // the jump for the level
                                valid = false
                                break
                            }
                            minDocID[level] = input.readInt()
                            maxValue[level] = input.readLong()
                            minValue[level] = input.readLong()
                            docCount[level] = input.readInt()
                        }
                        if (valid) {
                            // adjust levels
                            while (levels < SKIP_INDEX_MAX_LEVEL && maxDocID[levels] >= target) {
                                levels++
                            }
                            break
                        }
                    }
                }
            }

            override fun numLevels(): Int {
                return levels
            }

            override fun minDocID(level: Int): Int {
                return minDocID[level]
            }

            override fun maxDocID(level: Int): Int {
                return maxDocID[level]
            }

            override fun minValue(level: Int): Long {
                return minValue[level]
            }

            override fun maxValue(level: Int): Long {
                return maxValue[level]
            }

            override fun docCount(level: Int): Int {
                return docCount[level]
            }

            override fun minValue(): Long {
                return entry.minValue
            }

            override fun maxValue(): Long {
                return entry.maxValue
            }

            override fun docCount(): Int {
                return entry.docCount
            }
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun readTermDict(meta: IndexInput, entry: TermsDictEntry) {
            entry.termsDictSize = meta.readVLong()
            val blockShift: Int = meta.readInt()
            val addressesSize =
                ((entry.termsDictSize + (1L shl TERMS_DICT_BLOCK_LZ4_SHIFT) - 1)
                        ushr TERMS_DICT_BLOCK_LZ4_SHIFT)
            entry.termsAddressesMeta = DirectMonotonicReader.loadMeta(meta, addressesSize, blockShift)
            entry.maxTermLength = meta.readInt()
            entry.maxBlockLength = meta.readInt()
            entry.termsDataOffset = meta.readLong()
            entry.termsDataLength = meta.readLong()
            entry.termsAddressesOffset = meta.readLong()
            entry.termsAddressesLength = meta.readLong()
            entry.termsDictIndexShift = meta.readInt()
            val indexSize =
                (entry.termsDictSize + (1L shl entry.termsDictIndexShift) - 1) ushr entry.termsDictIndexShift
            entry.termsIndexAddressesMeta = DirectMonotonicReader.loadMeta(meta, 1 + indexSize, blockShift)
            entry.termsIndexOffset = meta.readLong()
            entry.termsIndexLength = meta.readLong()
            entry.termsIndexAddressesOffset = meta.readLong()
            entry.termsIndexAddressesLength = meta.readLong()
        }
    }
}
