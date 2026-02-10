package org.gnit.lucenekmp.codecs.lucene90

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import okio.FileNotFoundException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.CompoundDirectory
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.IOUtils

/**
 * Class for accessing a compound stream. This class implements a directory, but is limited to only
 * read operations. Directory methods that would normally modify data throw an exception.
 *
 * @lucene.experimental
 */
internal class Lucene90CompoundReader(private val directory: Directory, si: SegmentInfo) : CompoundDirectory() {
    private val logger = KotlinLogging.logger {}
    /** Offset/Length for a slice inside of a compound file  */
    class FileEntry {
        var offset: Long = 0
        var length: Long = 0
    }

    private val segmentName: String = si.name
    private val entries: MutableMap<String, FileEntry>
    private val handle: IndexInput
    private var version = 0

    /** Create a new CompoundFileDirectory.  */ // TODO: we should just pre-strip "entries" and append segment name up-front like simpletext
    // this need not be a "general purpose" directory anymore (it only writes index files)
    init {
        val dataFileName: String =
            IndexFileNames.segmentFileName(segmentName, "", Lucene90CompoundFormat.DATA_EXTENSION)
        val entriesFileName: String =
            IndexFileNames.segmentFileName(segmentName, "", Lucene90CompoundFormat.ENTRIES_EXTENSION)
        this.entries = readEntries(si.getId(), directory, entriesFileName)
        var success = false

        // find the last FileEntry in the map (largest offset+length) and add length of codec footer:
        val expectedLength: Long =
            ((entries.values.maxOfOrNull { e -> e.offset + e.length }
                ?: CodecUtil.indexHeaderLength(Lucene90CompoundFormat.DATA_CODEC, "")).toLong()
                + CodecUtil.footerLength())

        handle = directory.openInput(dataFileName, IOContext.DEFAULT.withReadAdvice(ReadAdvice.NORMAL))
        logger.debug { "CFS open: segment=$segmentName dataFile=$dataFileName handle=$handle" }
        try {
            CodecUtil.checkIndexHeader(
                handle, Lucene90CompoundFormat.DATA_CODEC, version, version, si.getId(), ""
            )

            // NOTE: data file is too costly to verify checksum against all the bytes on open,
            // but for now we at least verify proper structure of the checksum footer: which looks
            // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
            // such as file truncation.
            CodecUtil.retrieveChecksum(handle)

            // We also validate length, because e.g. if you strip 16 bytes off the .cfs we otherwise
            // would not detect it:
            if (handle.length() != expectedLength) {
                throw CorruptIndexException(
                    "length should be " + expectedLength + " bytes, but is " + handle.length() + " instead",
                    handle
                )
            }

            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(handle)
            }
        }
    }

    /** Helper method that reads CFS entries from an input stream  */
    @Throws(IOException::class)
    private fun readEntries(
        segmentID: ByteArray, dir: Directory, entriesFileName: String
    ): MutableMap<String, FileEntry> {
        var mapping: MutableMap<String, FileEntry>? = null
        dir.openChecksumInput(entriesFileName).use { entriesStream ->
            var priorE: Throwable? = null
            try {
                version =
                    CodecUtil.checkIndexHeader(
                        entriesStream,
                        Lucene90CompoundFormat.ENTRY_CODEC,
                        Lucene90CompoundFormat.VERSION_START,
                        Lucene90CompoundFormat.VERSION_CURRENT,
                        segmentID,
                        ""
                    )

                mapping = readMapping(entriesStream)
            } catch (exception: Throwable) {
                priorE = exception
            } finally {
                CodecUtil.checkFooter(entriesStream, priorE)
            }
        }
        return mapping!!
    }

    @Throws(IOException::class)
    private fun readMapping(entriesStream: IndexInput): MutableMap<String, FileEntry> {
        val numEntries: Int = entriesStream.readVInt()
        val mapping: MutableMap<String, FileEntry> = CollectionUtil.newHashMap(numEntries)
        for (i in 0..<numEntries) {
            val fileEntry = FileEntry()
            val id: String = entriesStream.readString()
            val previous = mapping.put(id, fileEntry)
            if (previous != null) {
                throw CorruptIndexException("Duplicate cfs entry id=$id in CFS ", entriesStream)
            }
            fileEntry.offset = entriesStream.readLong()
            fileEntry.length = entriesStream.readLong()
        }
        return mapping
    }

    @Throws(IOException::class)
    override fun close() {
        logger.debug { "CFS close: segment=$segmentName handle=$handle" }
        IOUtils.close(handle)
    }

    @Throws(IOException::class)
    override fun openInput(name: String, context: IOContext): IndexInput {
        ensureOpen()
        val id: String = IndexFileNames.stripSegmentName(name)
        val entry = entries[id]
        if (entry == null) {
            val datFileName: String =
                IndexFileNames.segmentFileName(segmentName, "", Lucene90CompoundFormat.DATA_EXTENSION)
            throw FileNotFoundException(
                ("No sub-file with id "
                        + id
                        + " found in compound file \""
                        + datFileName
                        + "\" (fileName="
                        + name
                        + " files: "
                        + entries.keys
                        + ")")
            )
        }
        return handle.slice(name, entry.offset, entry.length, context.readAdvice)
    }

    /** Returns an array of strings, one for each file in the directory.  */
    override fun listAll(): Array<String> {
        ensureOpen()
        val res = entries.keys.toTypedArray<String>()

        // Add the segment name
        for (i in res.indices) {
            res[i] = segmentName + res[i]
        }
        return res
    }

    /**
     * Returns the length of a file in the directory.
     *
     * @throws IOException if the file does not exist
     */
    @Throws(IOException::class)
    override fun fileLength(name: String): Long {
        ensureOpen()
        val e: FileEntry? = entries[IndexFileNames.stripSegmentName(name)]
        if (e == null) throw FileNotFoundException(name)
        return e.length
    }

    override fun toString(): String {
        return "CompoundFileDirectory(segment=\"$segmentName\" in dir=$directory)"
    }

    override val pendingDeletions: MutableSet<String>
        get() = mutableSetOf()

    @Throws(IOException::class)
    override fun checkIntegrity() {
        CodecUtil.checksumEntireFile(handle)
    }
}
