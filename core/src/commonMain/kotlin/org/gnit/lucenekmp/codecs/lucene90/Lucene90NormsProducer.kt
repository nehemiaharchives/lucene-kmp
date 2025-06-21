package org.gnit.lucenekmp.codecs.lucene90


import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.lucene90.Lucene90NormsFormat.Companion.VERSION_CURRENT
import org.gnit.lucenekmp.codecs.lucene90.Lucene90NormsFormat.Companion.VERSION_START
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.Cloneable
import okio.IOException

/** Reader for [Lucene90NormsFormat]  */
internal class Lucene90NormsProducer(
    private val state: SegmentReadState,
    private val dataCodec: String,
    private val dataExtension: String,
    private val metaCodec: String,
    private val metaExtension: String
) : NormsProducer(), Cloneable<Lucene90NormsProducer> {
    // metadata maps (just file pointers and minimal stuff)
    private val norms: IntObjectHashMap<NormsEntry> = IntObjectHashMap()
    private val maxDoc: Int = state.segmentInfo.maxDoc()
    private var data: IndexInput
    private var merging = false
    private var disiInputs: IntObjectHashMap<IndexInput>? = null
    private var disiJumpTables: IntObjectHashMap<RandomAccessInput>? = null
    private var dataInputs: IntObjectHashMap<RandomAccessInput>? = null

    init {
        val metaName: String =
            IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, metaExtension)
        var version = -1

        state.directory.openChecksumInput(metaName).use { `in` ->
            var priorE: Throwable? = null
            try {
                version =
                    CodecUtil.checkIndexHeader(
                        `in`,
                        metaCodec,
                        VERSION_START,
                        VERSION_CURRENT,
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
        // Norms have a forward-only access pattern, so pass ReadAdvice.NORMAL to perform readahead.
        data = state.directory.openInput(dataName, state.context.withReadAdvice(ReadAdvice.NORMAL))
        var success = false
        try {
            val version2: Int =
                CodecUtil.checkIndexHeader(
                    data,
                    dataCodec,
                    VERSION_START,
                    VERSION_CURRENT,
                    state.segmentInfo.getId(),
                    state.segmentSuffix
                )
            if (version != version2) {
                throw CorruptIndexException(
                    "Format versions mismatch: meta=$version,data=$version2", data
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

    override val mergeInstance: NormsProducer
        get() {
            val clone: Lucene90NormsProducer
            try {
                clone = this.clone()
            } catch (e: /*CloneNotSupported*/Exception) {
                // cannot happen
                throw RuntimeException(e)
            }
            clone.data = data.clone()
            clone.disiInputs = IntObjectHashMap()
            clone.disiJumpTables = IntObjectHashMap()
            clone.dataInputs = IntObjectHashMap()
            clone.merging = true
            return clone
        }

    internal class NormsEntry {
        var denseRankPower: Byte = 0
        var bytesPerNorm: Byte = 0
        var docsWithFieldOffset: Long = 0
        var docsWithFieldLength: Long = 0
        var jumpTableEntryCount: Short = 0
        var numDocsWithField: Int = 0
        var normsOffset: Long = 0
    }

    internal abstract class DenseNormsIterator(val maxDoc: Int) : NumericDocValues() {
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

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            this.doc = target
            return true
        }

        override fun cost(): Long {
            return maxDoc.toLong()
        }
    }

    internal abstract class SparseNormsIterator(val disi: IndexedDISI) : NumericDocValues() {

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

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            return disi.advanceExact(target)
        }

        override fun cost(): Long {
            return disi.cost()
        }
    }

    @Throws(IOException::class)
    private fun readFields(meta: IndexInput, infos: FieldInfos) {
        var fieldNumber: Int = meta.readInt()
        while (fieldNumber != -1) {
            val info: FieldInfo? = infos.fieldInfo(fieldNumber)
            if (info == null) {
                throw CorruptIndexException("Invalid field number: $fieldNumber", meta)
            } else if (!info.hasNorms()) {
                throw CorruptIndexException("Invalid field: " + info.name, meta)
            }
            val entry = NormsEntry()
            entry.docsWithFieldOffset = meta.readLong()
            entry.docsWithFieldLength = meta.readLong()
            entry.jumpTableEntryCount = meta.readShort()
            entry.denseRankPower = meta.readByte()
            entry.numDocsWithField = meta.readInt()
            entry.bytesPerNorm = meta.readByte()
            when (entry.bytesPerNorm) {
                0.toByte(), 1.toByte(), 2.toByte(), 4.toByte(), 8.toByte() -> {}
                else -> throw CorruptIndexException(
                    "Invalid bytesPerValue: " + entry.bytesPerNorm + ", field: " + info.name, meta
                )
            }
            entry.normsOffset = meta.readLong()
            norms.put(info.number, entry)
            fieldNumber = meta.readInt()
        }
    }

    @Throws(IOException::class)
    private fun getDataInput(field: FieldInfo, entry: NormsEntry): RandomAccessInput {
        var slice: RandomAccessInput? = null
        if (merging) {
            slice = dataInputs!![field.number]
        }
        if (slice == null) {
            slice =
                data.randomAccessSlice(
                    entry.normsOffset, entry.numDocsWithField * entry.bytesPerNorm.toLong()
                )
            if (merging) {
                dataInputs!!.put(field.number, slice)
            }
            // Prefetch the first page of data. Following pages are expected to get prefetched through
            // read-ahead.
            if (slice.length() > 0) {
                slice.prefetch(0, 1)
            }
        }
        return slice
    }

    @Throws(IOException::class)
    private fun getDisiInput(field: FieldInfo, entry: NormsEntry): IndexInput {
        if (!merging) {
            return IndexedDISI.createBlockSlice(
                data,
                "docs",
                entry.docsWithFieldOffset,
                entry.docsWithFieldLength,
                entry.jumpTableEntryCount.toInt()
            )
        }

        var `in`: IndexInput? = disiInputs!![field.number]
        if (`in` == null) {
            `in` =
                IndexedDISI.createBlockSlice(
                    data,
                    "docs",
                    entry.docsWithFieldOffset,
                    entry.docsWithFieldLength,
                    entry.jumpTableEntryCount.toInt()
                )
            disiInputs!!.put(field.number, `in`)
        }

        val inF: IndexInput = `in` // same as in but final

        // Wrap so that reads can be interleaved from the same thread if two
        // norms instances are pulled and consumed in parallel. Merging usually
        // doesn't need this feature but CheckIndex might, plus we need merge
        // instances to behave well and not be trappy.
        return object : IndexInput("docs") {
            override var filePointer: Long = 0

            @Throws(IOException::class)
            override fun readBytes(b: ByteArray, off: Int, len: Int) {
                inF.seek(this.filePointer)
                this.filePointer += len.toLong()
                inF.readBytes(b, off, len)
            }

            @Throws(IOException::class)
            override fun readByte(): Byte {
                throw UnsupportedOperationException("Unused by IndexedDISI")
            }

            @Throws(IOException::class)
            override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
                throw UnsupportedOperationException("Unused by IndexedDISI")
            }

            @Throws(IOException::class)
            override fun readShort(): Short {
                inF.seek(this.filePointer)
                this.filePointer += Short.SIZE_BYTES.toLong()
                return inF.readShort()
            }

            @Throws(IOException::class)
            override fun readLong(): Long {
                inF.seek(this.filePointer)
                this.filePointer += Long.SIZE_BYTES.toLong()
                return inF.readLong()
            }

            @Throws(IOException::class)
            override fun seek(pos: Long) {
                this.filePointer = pos
            }

            override fun length(): Long {
                return inF.length()
            }

            @Throws(IOException::class)
            override fun close() {
                throw UnsupportedOperationException("Unused by IndexedDISI")
            }

            @Throws(IOException::class)
            override fun prefetch(offset: Long, length: Long) {
                // Not delegating to the wrapped instance on purpose. This is only used for merging.
            }
        }
    }

    @Throws(IOException::class)
    private fun getDisiJumpTable(field: FieldInfo, entry: NormsEntry): RandomAccessInput {
        var jumpTable: RandomAccessInput? = null
        if (merging) {
            jumpTable = disiJumpTables!![field.number]
        }
        if (jumpTable == null) {
            jumpTable =
                IndexedDISI.createJumpTable(
                    data,
                    entry.docsWithFieldOffset,
                    entry.docsWithFieldLength,
                    entry.jumpTableEntryCount.toInt()
                )
            if (merging) {
                disiJumpTables!!.put(field.number, jumpTable)
            }
        }
        return jumpTable!!
    }

    @Throws(IOException::class)
    override fun getNorms(field: FieldInfo): NumericDocValues {
        val entry: NormsEntry = norms[field.number]!!
        if (entry.docsWithFieldOffset == -2L) {
            // empty
            return DocValues.emptyNumeric()
        } else if (entry.docsWithFieldOffset == -1L) {
            // dense
            if (entry.bytesPerNorm.toInt() == 0) {
                return object : DenseNormsIterator(maxDoc) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return entry.normsOffset
                    }
                }
            }
            val slice: RandomAccessInput = getDataInput(field, entry)
            when (entry.bytesPerNorm) {
                1.toByte() -> return object : DenseNormsIterator(maxDoc) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return slice.readByte(doc.toLong()).toLong()
                    }
                }

                2.toByte() -> return object : DenseNormsIterator(maxDoc) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return slice.readShort((doc.toLong()) shl 1).toLong()
                    }
                }

                4.toByte() -> return object : DenseNormsIterator(maxDoc) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return slice.readInt((doc.toLong()) shl 2).toLong()
                    }
                }

                8.toByte() -> return object : DenseNormsIterator(maxDoc) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return slice.readLong((doc.toLong()) shl 3)
                    }
                }

                else ->           // should not happen, we already validate bytesPerNorm in readFields
                    throw AssertionError()
            }
        } else {
            // sparse
            val disiInput: IndexInput = getDisiInput(field, entry)
            val disiJumpTable: RandomAccessInput = getDisiJumpTable(field, entry)
            val disi =
                IndexedDISI(
                    disiInput,
                    disiJumpTable,
                    entry.jumpTableEntryCount.toInt(),
                    entry.denseRankPower,
                    entry.numDocsWithField.toLong()
                )

            if (entry.bytesPerNorm.toInt() == 0) {
                return object : SparseNormsIterator(disi) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return entry.normsOffset
                    }
                }
            }
            val slice: RandomAccessInput = getDataInput(field, entry)
            when (entry.bytesPerNorm) {
                1.toByte() -> return object : SparseNormsIterator(disi) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return slice.readByte(disi.index().toLong()).toLong()
                    }
                }

                2.toByte() -> return object : SparseNormsIterator(disi) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return slice.readShort((disi.index().toLong()) shl 1).toLong()
                    }
                }

                4.toByte() -> return object : SparseNormsIterator(disi) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return slice.readInt((disi.index().toLong()) shl 2).toLong()
                    }
                }

                8.toByte() -> return object : SparseNormsIterator(disi) {
                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return slice.readLong((disi.index().toLong()) shl 3)
                    }
                }

                else ->           // should not happen, we already validate bytesPerNorm in readFields
                    throw AssertionError()
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        data.close()
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        CodecUtil.checksumEntireFile(data)
    }

    override fun toString(): String {
        return this::class.simpleName + "(fields=" + norms.size() + ")"
    }

override fun clone(): Lucene90NormsProducer {
    val clone = Lucene90NormsProducer(
        state,
        dataCodec,
        dataExtension,
        metaCodec,
        metaExtension
    )

    // Copy the norms map
    norms.forEach { entry ->
        clone.norms.put(entry.key, entry.value)
    }

    clone.data = data.clone()
    clone.merging = merging

    // Copy maps if they exist
    if (disiInputs != null) {
        clone.disiInputs = IntObjectHashMap<IndexInput>().apply {
            disiInputs!!.forEach { entry ->
                put(entry.key, entry.value)
            }
        }
    }

    if (disiJumpTables != null) {
        clone.disiJumpTables = IntObjectHashMap<RandomAccessInput>().apply {
            disiJumpTables!!.forEach { entry ->
                put(entry.key, entry.value)
            }
        }
    }

    if (dataInputs != null) {
        clone.dataInputs = IntObjectHashMap<RandomAccessInput>().apply {
            dataInputs!!.forEach { entry ->
                put(entry.key, entry.value)
            }
        }
    }

    return clone
}
}
