package org.gnit.lucenekmp.codecs.lucene99


import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.SegmentInfoFormat
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.store.*
import org.gnit.lucenekmp.util.Version
import okio.IOException

/**
 * Lucene 9.9 Segment info format.
 *
 *
 * Files:
 *
 *
 *  * `.si`: Header, SegVersion, SegSize, IsCompoundFile, Diagnostics, Files,
 * Attributes, IndexSort, Footer
 *
 *
 * Data types:
 *
 *
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * SegSize --&gt; [Int32][DataOutput.writeInt]
 *  * SegVersion --&gt; [String][DataOutput.writeString]
 *  * SegMinVersion --&gt; [String][DataOutput.writeString]
 *  * Files --&gt; [Set&amp;lt;String&amp;gt;][DataOutput.writeSetOfStrings]
 *  * Diagnostics,Attributes --&gt; [Map&amp;lt;String,String&amp;gt;][DataOutput.writeMapOfStrings]
 *  * IsCompoundFile --&gt; [Int8][DataOutput.writeByte]
 *  * HasBlocks --&gt; [Int8][DataOutput.writeByte]
 *  * IndexSort --&gt; [Int32][DataOutput.writeVInt] count, followed by `count`
 * SortField
 *  * SortField --&gt; [String][DataOutput.writeString] sort class, followed by a per-sort
 * bytestream (see [SortFieldProvider.readSortField])
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * Field Descriptions:
 *
 *
 *  * SegVersion is the code version that created the segment.
 *  * SegMinVersion is the minimum code version that contributed documents to the segment.
 *  * SegSize is the number of documents contained in the segment index.
 *  * IsCompoundFile records whether the segment is written as a compound file or not. If this is
 * -1, the segment is not a compound file. If it is 1, the segment is a compound file.
 *  * HasBlocks records whether the segment contains documents written as a block and guarantees
 * consecutive document ids for all documents in the block
 *  * The Diagnostics Map is privately written by [IndexWriter], as a debugging aid, for
 * each segment it creates. It includes metadata like the current Lucene version, OS, Java
 * version, why the segment was created (merge, flush, addIndexes), etc.
 *  * Files is a list of files referred to by this segment.
 *
 *
 * @see SegmentInfos
 *
 * @lucene.experimental
 */
class Lucene99SegmentInfoFormat
/** Sole constructor.  */
    : SegmentInfoFormat() {
    @Throws(IOException::class)
    override fun read(
        dir: Directory,
        segment: String,
        segmentID: ByteArray,
        context: IOContext
    ): SegmentInfo {
        val fileName: String = IndexFileNames.segmentFileName(segment, "", SI_EXTENSION)
        dir.openChecksumInput(fileName).use { input ->
            var priorE: Throwable? = null
            var si: SegmentInfo? = null
            try {
                CodecUtil.checkIndexHeader(
                    input, CODEC_NAME, VERSION_START, VERSION_CURRENT, segmentID, ""
                )
                si = parseSegmentInfo(dir, input, segment, segmentID)
            } catch (exception: Throwable) {
                priorE = exception
            } finally {
                CodecUtil.checkFooter(input, priorE)
            }
            return si!!
        }
    }

    @Throws(IOException::class)
    private fun parseSegmentInfo(
        dir: Directory, input: DataInput, segment: String, segmentID: ByteArray
    ): SegmentInfo {
        val version: Version = Version.fromBits(input.readInt(), input.readInt(), input.readInt())
        val hasMinVersion: Byte = input.readByte()
        val minVersion: Version? = when (hasMinVersion) {
            0.toByte() -> null
            1.toByte() -> Version.fromBits(input.readInt(), input.readInt(), input.readInt())
            else -> throw CorruptIndexException("Illegal boolean value $hasMinVersion", input)
        }

        val docCount: Int = input.readInt()
        if (docCount < 0) {
            throw CorruptIndexException("invalid docCount: $docCount", input)
        }
        val isCompoundFile = input.readByte() == SegmentInfo.YES.toByte()
        val hasBlocks = input.readByte() == SegmentInfo.YES.toByte()

        val diagnostics: MutableMap<String, String> = input.readMapOfStrings()
        val files: MutableSet<String> = input.readSetOfStrings()
        val attributes: MutableMap<String, String> = input.readMapOfStrings()

        val numSortFields: Int = input.readVInt()
        val indexSort: Sort?
        if (numSortFields > 0) {
            val sortFields: Array<SortField?> = kotlin.arrayOfNulls(numSortFields)
            for (i in 0..<numSortFields) {
                val name: String = input.readString()
                sortFields[i] = SortFieldProvider.forName(name).readSortField(input)
            }
            indexSort = Sort(*(sortFields as Array<SortField>))
        } else if (numSortFields < 0) {
            throw CorruptIndexException("invalid index sort field count: $numSortFields", input)
        } else {
            indexSort = null
        }

        val si =
            SegmentInfo(
                dir,
                version,
                minVersion,
                segment,
                docCount,
                isCompoundFile,
                hasBlocks,
                null,
                diagnostics,
                segmentID,
                attributes,
                indexSort
            )
        si.setFiles(files)
        return si
    }

    @Throws(IOException::class)
    override fun write(dir: Directory, si: SegmentInfo, ioContext: IOContext) {
        val fileName: String = IndexFileNames.segmentFileName(si.name, "", SI_EXTENSION)

        dir.createOutput(fileName, ioContext).use { output ->
            // Only add the file once we've successfully created it, else IFD assert can trip:
            si.addFile(fileName)
            CodecUtil.writeIndexHeader(output, CODEC_NAME, VERSION_CURRENT, si.getId(), "")

            writeSegmentInfo(output, si)
            CodecUtil.writeFooter(output)
        }
    }

    @Throws(IOException::class)
    private fun writeSegmentInfo(output: DataOutput, si: SegmentInfo) {
        val version: Version = si.getVersion()
        require(version.major >= 7) { "invalid major version: should be >= 7 but got: " + version.major + " segment=" + si }
        // Write the Lucene version that created this segment, since 3.1
        output.writeInt(version.major)
        output.writeInt(version.minor)
        output.writeInt(version.bugfix)

        // Write the min Lucene version that contributed docs to the segment, since 7.0
        if (si.minVersion != null) {
            output.writeByte(1.toByte())
            val minVersion: Version = si.minVersion!!
            output.writeInt(minVersion.major)
            output.writeInt(minVersion.minor)
            output.writeInt(minVersion.bugfix)
        } else {
            output.writeByte(0.toByte())
        }

        require(version.prerelease == 0)
        output.writeInt(si.maxDoc())

        output.writeByte((if (si.useCompoundFile) SegmentInfo.YES else SegmentInfo.NO).toByte())
        output.writeByte((if (si.hasBlocks) SegmentInfo.YES else SegmentInfo.NO).toByte())
        output.writeMapOfStrings(si.getDiagnostics())
        val files: MutableSet<String> = si.files()
        for (file in files) {
            require(
                IndexFileNames.parseSegmentName(file) == si.name
            ) { "invalid files: expected segment=" + si.name + ", got=" + files }
        }
        output.writeSetOfStrings(files)
        output.writeMapOfStrings(si.attributes)

        val indexSort: Sort? = si.getIndexSort()
        val numSortFields = indexSort?.sort?.size ?: 0
        output.writeVInt(numSortFields)
        for (i in 0..<numSortFields) {
            val sortField: SortField = indexSort!!.sort[i]
            val sorter: IndexSorter? = sortField.getIndexSorter()
            requireNotNull(sorter) { "cannot serialize SortField $sortField" }
            output.writeString(sorter.providerName)
            SortFieldProvider.write(sortField, output)
        }
    }

    companion object {
        /** File extension used to store [SegmentInfo].  */
        const val SI_EXTENSION: String = "si"

        const val CODEC_NAME: String = "Lucene90SegmentInfo"
        const val VERSION_START: Int = 0
        const val VERSION_CURRENT: Int = VERSION_START
    }
}
