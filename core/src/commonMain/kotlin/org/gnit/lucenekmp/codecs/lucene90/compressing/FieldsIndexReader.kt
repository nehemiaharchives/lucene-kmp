package org.gnit.lucenekmp.codecs.lucene90.compressing


import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.lucene90.compressing.FieldsIndexWriter.Companion.VERSION_CURRENT
import org.gnit.lucenekmp.codecs.lucene90.compressing.FieldsIndexWriter.Companion.VERSION_START
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.packed.DirectMonotonicReader
import okio.IOException
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.UncheckedIOException

internal class FieldsIndexReader : FieldsIndex {
    private val maxDoc: Int
    private val blockShift: Int
    private val numChunks: Int
    private val docsMeta: DirectMonotonicReader.Meta
    private val startPointersMeta: DirectMonotonicReader.Meta
    private val indexInput: IndexInput
    private val docsStartPointer: Long
    private val docsEndPointer: Long
    private val startPointersStartPointer: Long
    private val startPointersEndPointer: Long
    private val docs: DirectMonotonicReader
    private val startPointers: DirectMonotonicReader
    val maxPointer: Long

    constructor(
        dir: Directory,
        name: String,
        suffix: String,
        extension: String,
        codecName: String,
        id: ByteArray,
        metaIn: IndexInput,
        context: IOContext
    ) {
        maxDoc = metaIn.readInt()
        blockShift = metaIn.readInt()
        numChunks = metaIn.readInt()
        docsStartPointer = metaIn.readLong()
        docsMeta = DirectMonotonicReader.loadMeta(metaIn, numChunks.toLong(), blockShift)
        startPointersStartPointer = metaIn.readLong()
        docsEndPointer = startPointersStartPointer
        startPointersMeta = DirectMonotonicReader.loadMeta(metaIn, numChunks.toLong(), blockShift)
        startPointersEndPointer = metaIn.readLong()
        maxPointer = metaIn.readLong()

        indexInput =
            dir.openInput(
                IndexFileNames.segmentFileName(name, suffix, extension),
                context.withReadAdvice(ReadAdvice.RANDOM_PRELOAD)
            )
        var success = false
        try {
            CodecUtil.checkIndexHeader(
                indexInput, codecName + "Idx", VERSION_START, VERSION_CURRENT, id, suffix
            )
            CodecUtil.retrieveChecksum(indexInput)
            success = true
        } finally {
            if (!success) {
                indexInput.close()
            }
        }
        val docsSlice: RandomAccessInput =
            indexInput.randomAccessSlice(docsStartPointer, docsEndPointer - docsStartPointer)
        val startPointersSlice: RandomAccessInput =
            indexInput.randomAccessSlice(
                startPointersStartPointer, startPointersEndPointer - startPointersStartPointer
            )
        docs = DirectMonotonicReader.getInstance(docsMeta, docsSlice)
        startPointers = DirectMonotonicReader.getInstance(startPointersMeta, startPointersSlice)
    }

    private constructor(other: FieldsIndexReader) {
        maxDoc = other.maxDoc
        numChunks = other.numChunks
        blockShift = other.blockShift
        docsMeta = other.docsMeta
        startPointersMeta = other.startPointersMeta
        indexInput = other.indexInput.clone()
        docsStartPointer = other.docsStartPointer
        docsEndPointer = other.docsEndPointer
        startPointersStartPointer = other.startPointersStartPointer
        startPointersEndPointer = other.startPointersEndPointer
        maxPointer = other.maxPointer
        val docsSlice: RandomAccessInput =
            indexInput.randomAccessSlice(docsStartPointer, docsEndPointer - docsStartPointer)
        val startPointersSlice: RandomAccessInput =
            indexInput.randomAccessSlice(
                startPointersStartPointer, startPointersEndPointer - startPointersStartPointer
            )
        docs = DirectMonotonicReader.getInstance(docsMeta, docsSlice)
        startPointers = DirectMonotonicReader.getInstance(startPointersMeta, startPointersSlice)
    }

    @Throws(IOException::class)
    override fun close() {
        indexInput.close()
    }

    override fun getBlockID(docID: Int): Long {
        Objects.checkIndex(docID, maxDoc)
        var blockIndex: Long = docs.binarySearch(0, numChunks.toLong(), docID.toLong())
        if (blockIndex < 0) {
            blockIndex = -2 - blockIndex
        }
        return blockIndex
    }

    override fun getBlockStartPointer(blockIndex: Long): Long {
        return startPointers.get(blockIndex)
    }

    override fun getBlockLength(blockIndex: Long): Long {
        val endPointer: Long = if (blockIndex == (numChunks - 1).toLong()) {
            maxPointer
        } else {
            startPointers.get(blockIndex + 1)
        }
        return endPointer - getBlockStartPointer(blockIndex)
    }

    override fun clone(): FieldsIndex {
        try {
            return FieldsIndexReader(this)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        CodecUtil.checksumEntireFile(indexInput)
    }
}
