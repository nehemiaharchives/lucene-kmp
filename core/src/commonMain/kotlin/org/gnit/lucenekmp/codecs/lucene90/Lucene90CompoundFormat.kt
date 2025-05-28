package org.gnit.lucenekmp.codecs.lucene90

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.CompoundDirectory
import org.gnit.lucenekmp.codecs.CompoundFormat
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.jvm.JvmRecord

/**
 * Lucene 9.0 compound file format
 *
 *
 * Files:
 *
 *
 *  * `.cfs`: An optional "virtual" file consisting of all the other index files for
 * systems that frequently run out of file handles.
 *  * `.cfe`: The "virtual" compound file's entry table holding all entries in the
 * corresponding .cfs file.
 *
 *
 *
 * Description:
 *
 *
 *  * Compound (.cfs) --&gt; Header, FileData <sup>FileCount</sup>, Footer
 *  * Compound Entry Table (.cfe) --&gt; Header, FileCount, &lt;FileName, DataOffset,
 * DataLength&gt; <sup>FileCount</sup>
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * FileCount --&gt; [VInt][DataOutput.writeVInt]
 *  * DataOffset,DataLength,Checksum --&gt; [UInt64][DataOutput.writeLong]
 *  * FileName --&gt; [String][DataOutput.writeString]
 *  * FileData --&gt; raw file data
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 *
 * Notes:
 *
 *
 *  * FileCount indicates how many files are contained in this compound file. The entry table
 * that follows has that many entries.
 *  * Each directory entry contains a long pointer to the start of this file's data section, the
 * files length, and a String with that file's name. The start of file's data section is
 * aligned to 8 bytes to not introduce additional unaligned accesses with mmap.
 *
 */
class Lucene90CompoundFormat
/** Sole constructor.  */
    : CompoundFormat() {
    @Throws(IOException::class)
    override fun getCompoundReader(dir: Directory, si: SegmentInfo): CompoundDirectory {
        return Lucene90CompoundReader(dir, si)
    }

    @Throws(IOException::class)
    override fun write(dir: Directory, si: SegmentInfo, context: IOContext) {
        val dataFile: String = IndexFileNames.segmentFileName(si.name, "", DATA_EXTENSION)
        val entriesFile: String = IndexFileNames.segmentFileName(si.name, "", ENTRIES_EXTENSION)

        dir.createOutput(dataFile, context).use { data ->
            dir.createOutput(entriesFile, context).use { entries ->
                CodecUtil.writeIndexHeader(data, DATA_CODEC, VERSION_CURRENT, si.getId(), "")
                CodecUtil.writeIndexHeader(entries, ENTRY_CODEC, VERSION_CURRENT, si.getId(), "")

                writeCompoundFile(entries, data, dir, si)

                CodecUtil.writeFooter(data)
                CodecUtil.writeFooter(entries)
            }
        }
    }

    @JvmRecord
    private data class SizedFile(val name: String, val length: Long)

    private class SizedFileQueue(maxSize: Int) : PriorityQueue<SizedFile>(maxSize) {
        override fun lessThan(sf1: SizedFile, sf2: SizedFile): Boolean {
            return sf1.length < sf2.length
        }
    }

    @Throws(IOException::class)
    private fun writeCompoundFile(
        entries: IndexOutput, data: IndexOutput, dir: Directory, si: SegmentInfo
    ) {
        // write number of files
        val numFiles: Int = si.files().size
        entries.writeVInt(numFiles)
        // first put files in ascending size order so small files fit more likely into one page
        val pq = SizedFileQueue(numFiles)
        for (filename in si.files()) {
            pq.add(SizedFile(filename, dir.fileLength(filename)))
        }
        while (pq.size() > 0) {
            val sizedFile: SizedFile = pq.pop()!!
            val file = sizedFile.name
            // align file start offset
            val startOffset: Long = data.alignFilePointer(Long.SIZE_BYTES)
            dir.openChecksumInput(file).use { `in` ->

                // just copies the index header, verifying that its id matches what we expect
                CodecUtil.verifyAndCopyIndexHeader(`in`, data, si.getId())

                // copy all bytes except the footer
                val numBytesToCopy: Long = `in`.length() - CodecUtil.footerLength() - `in`.filePointer
                data.copyBytes(`in`, numBytesToCopy)

                // verify footer (checksum) matches for the incoming file we are copying
                val checksum: Long = CodecUtil.checkFooter(`in`)

                // this is poached from CodecUtil.writeFooter, but we need to use our own checksum, not
                // data.getChecksum(), but I think
                // adding a public method to CodecUtil to do that is somewhat dangerous:
                CodecUtil.writeBEInt(data, CodecUtil.FOOTER_MAGIC)
                CodecUtil.writeBEInt(data, 0)
                CodecUtil.writeBELong(data, checksum)
            }
            val endOffset: Long = data.filePointer

            val length = endOffset - startOffset

            // write entry for file
            entries.writeString(IndexFileNames.stripSegmentName(file))
            entries.writeLong(startOffset)
            entries.writeLong(length)
        }
    }

    companion object {
        /** Extension of compound file  */
        const val DATA_EXTENSION: String = "cfs"

        /** Extension of compound file entries  */
        const val ENTRIES_EXTENSION: String = "cfe"

        const val DATA_CODEC: String = "Lucene90CompoundData"
        const val ENTRY_CODEC: String = "Lucene90CompoundEntries"
        const val VERSION_START: Int = 0
        const val VERSION_CURRENT: Int = VERSION_START
    }
}
