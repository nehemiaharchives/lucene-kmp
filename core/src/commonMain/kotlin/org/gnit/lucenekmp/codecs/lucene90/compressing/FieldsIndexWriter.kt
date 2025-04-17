package org.gnit.lucenekmp.codecs.lucene90.compressing


import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.packed.DirectMonotonicWriter
import kotlinx.io.IOException

/**
 * Efficient index format for block-based [Codec]s.
 *
 *
 * For each block of compressed stored fields, this stores the first document of the block and
 * the start pointer of the block in a [DirectMonotonicWriter]. At read time, the docID is
 * binary-searched in the [DirectMonotonicReader] that records doc IDS, and the returned index
 * is used to look up the start pointer in the [DirectMonotonicReader] that records start
 * pointers.
 *
 * @lucene.internal
 */
class FieldsIndexWriter internal constructor(
    private val dir: Directory,
    private val name: String,
    private val suffix: String,
    private val extension: String,
    private val codecName: String,
    private val id: ByteArray,
    private val blockShift: Int,
    private val ioContext: IOContext
) : AutoCloseable {
    private var docsOut: IndexOutput?
    private var filePointersOut: IndexOutput?
    private var totalDocs = 0
    private var totalChunks = 0
    private var previousFP: Long = 0

    init {
        this.docsOut = dir.createTempOutput(name, "$codecName-doc_ids", ioContext)
        var success = false
        try {
            CodecUtil.writeHeader(docsOut!!, codecName + "Docs", VERSION_CURRENT)
            filePointersOut = dir.createTempOutput(name, codecName + "file_pointers", ioContext)
            CodecUtil.writeHeader(filePointersOut!!, codecName + "FilePointers", VERSION_CURRENT)
            success = true
        } finally {
            if (!success) {
                close()
            }
        }
    }

    @Throws(IOException::class)
    fun writeIndex(numDocs: Int, startPointer: Long) {
        require(startPointer >= previousFP)
        docsOut!!.writeVInt(numDocs)
        filePointersOut!!.writeVLong(startPointer - previousFP)
        previousFP = startPointer
        totalDocs += numDocs
        totalChunks++
    }

    @Throws(IOException::class)
    fun finish(numDocs: Int, maxPointer: Long, metaOut: IndexOutput) {
        check(numDocs == totalDocs) { "Expected $numDocs docs, but got $totalDocs" }
        CodecUtil.writeFooter(docsOut!!)
        CodecUtil.writeFooter(filePointersOut!!)
        IOUtils.close(docsOut!!, filePointersOut!!)

        dir.createOutput(IndexFileNames.segmentFileName(name, suffix, extension), ioContext).use { dataOut ->
            CodecUtil.writeIndexHeader(dataOut, codecName + "Idx", VERSION_CURRENT, id, suffix)
            metaOut.writeInt(numDocs)
            metaOut.writeInt(blockShift)
            metaOut.writeInt(totalChunks + 1)
            metaOut.writeLong(dataOut.getFilePointer())

            dir.openChecksumInput(docsOut!!.getName()).use { docsIn ->
                CodecUtil.checkHeader(docsIn, codecName + "Docs", VERSION_CURRENT, VERSION_CURRENT)
                var priorE: Throwable? = null
                try {
                    val docs: DirectMonotonicWriter =
                        DirectMonotonicWriter.getInstance(metaOut, dataOut, totalChunks.toLong() + 1L, blockShift)
                    var doc: Long = 0
                    docs.add(doc)
                    for (i in 0..<totalChunks) {
                        doc += docsIn.readVInt()
                        docs.add(doc)
                    }
                    docs.finish()
                    if (doc != totalDocs.toLong()) {
                        throw CorruptIndexException("Docs don't add up", docsIn)
                    }
                } catch (e: Throwable) {
                    priorE = e
                } finally {
                    CodecUtil.checkFooter(docsIn, priorE)
                }
            }
            dir.deleteFile(docsOut!!.getName())
            docsOut = null

            metaOut.writeLong(dataOut.getFilePointer())
            dir.openChecksumInput(filePointersOut!!.getName()).use { filePointersIn ->
                CodecUtil.checkHeader(
                    filePointersIn, codecName + "FilePointers", VERSION_CURRENT, VERSION_CURRENT
                )
                var priorE: Throwable? = null
                try {
                    val filePointers: DirectMonotonicWriter =
                        DirectMonotonicWriter.getInstance(metaOut, dataOut, totalChunks.toLong() + 1L, blockShift)
                    var fp: Long = 0
                    for (i in 0..<totalChunks) {
                        fp += filePointersIn.readVLong()
                        filePointers.add(fp)
                    }
                    if (maxPointer < fp) {
                        throw CorruptIndexException("File pointers don't add up", filePointersIn)
                    }
                    filePointers.add(maxPointer)
                    filePointers.finish()
                } catch (e: Throwable) {
                    priorE = e
                } finally {
                    CodecUtil.checkFooter(filePointersIn, priorE)
                }
            }
            dir.deleteFile(filePointersOut!!.getName())
            filePointersOut = null

            metaOut.writeLong(dataOut.getFilePointer())
            metaOut.writeLong(maxPointer)
            CodecUtil.writeFooter(dataOut)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            IOUtils.close(docsOut!!, filePointersOut!!)
        } finally {
            val fileNames: MutableList<String> = ArrayList()
            if (docsOut != null) {
                fileNames.add(docsOut!!.getName())
            }
            if (filePointersOut != null) {
                fileNames.add(filePointersOut!!.getName())
            }
            try {
                IOUtils.deleteFiles(dir, fileNames)
            } finally {
                filePointersOut = null
                docsOut = filePointersOut
            }
        }
    }

    companion object {
        const val VERSION_START: Int = 0
        const val VERSION_CURRENT: Int = 0
    }
}
