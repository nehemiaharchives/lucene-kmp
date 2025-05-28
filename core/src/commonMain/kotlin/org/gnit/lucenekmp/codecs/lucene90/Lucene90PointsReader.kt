package org.gnit.lucenekmp.codecs.lucene90

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.bkd.BKDReader

/** Reads point values previously written with [Lucene90PointsWriter]  */
class Lucene90PointsReader(private val readState: SegmentReadState) : PointsReader() {
    private var indexIn: IndexInput
    private var dataIn: IndexInput
    private val readers: IntObjectHashMap<PointValues> = IntObjectHashMap()

    /** Sole constructor  */
    init {

        val metaFileName: String =
            IndexFileNames.segmentFileName(
                readState.segmentInfo.name,
                readState.segmentSuffix,
                Lucene90PointsFormat.META_EXTENSION
            )
        val indexFileName: String =
            IndexFileNames.segmentFileName(
                readState.segmentInfo.name,
                readState.segmentSuffix,
                Lucene90PointsFormat.INDEX_EXTENSION
            )
        val dataFileName: String =
            IndexFileNames.segmentFileName(
                readState.segmentInfo.name,
                readState.segmentSuffix,
                Lucene90PointsFormat.DATA_EXTENSION
            )

        var success = false
        try {
            indexIn =
                readState.directory.openInput(
                    indexFileName, readState.context.withReadAdvice(ReadAdvice.RANDOM_PRELOAD)
                )
            CodecUtil.checkIndexHeader(
                indexIn,
                Lucene90PointsFormat.INDEX_CODEC_NAME,
                Lucene90PointsFormat.VERSION_START,
                Lucene90PointsFormat.VERSION_CURRENT,
                readState.segmentInfo.getId(),
                readState.segmentSuffix
            )
            CodecUtil.retrieveChecksum(indexIn)

            // Points read whole ranges of bytes at once, so pass ReadAdvice.NORMAL to perform readahead.
            dataIn =
                readState.directory.openInput(
                    dataFileName, readState.context.withReadAdvice(ReadAdvice.NORMAL)
                )
            CodecUtil.checkIndexHeader(
                dataIn,
                Lucene90PointsFormat.DATA_CODEC_NAME,
                Lucene90PointsFormat.VERSION_START,
                Lucene90PointsFormat.VERSION_CURRENT,
                readState.segmentInfo.getId(),
                readState.segmentSuffix
            )
            CodecUtil.retrieveChecksum(dataIn)

            var indexLength: Long = -1
            var dataLength: Long = -1
            readState.directory.openChecksumInput(metaFileName).use { metaIn ->
                var priorE: Throwable? = null
                try {
                    CodecUtil.checkIndexHeader(
                        metaIn,
                        Lucene90PointsFormat.META_CODEC_NAME,
                        Lucene90PointsFormat.VERSION_START,
                        Lucene90PointsFormat.VERSION_CURRENT,
                        readState.segmentInfo.getId(),
                        readState.segmentSuffix
                    )

                    while (true) {
                        val fieldNumber: Int = metaIn.readInt()
                        if (fieldNumber == -1) {
                            break
                        } else if (fieldNumber < 0) {
                            throw CorruptIndexException("Illegal field number: $fieldNumber", metaIn)
                        }
                        val reader: PointValues = BKDReader(metaIn, indexIn, dataIn)
                        readers.put(fieldNumber, reader)
                    }
                    indexLength = metaIn.readLong()
                    dataLength = metaIn.readLong()
                } catch (t: Throwable) {
                    priorE = t
                } finally {
                    CodecUtil.checkFooter(metaIn, priorE)
                }
            }
            // At this point, checksums of the meta file have been validated so we
            // know that indexLength and dataLength are very likely correct.
            CodecUtil.retrieveChecksum(indexIn, indexLength)
            CodecUtil.retrieveChecksum(dataIn, dataLength)
            success = true
        } finally {
            if (success == false) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    /**
     * Returns the underlying [BKDReader].
     *
     * @lucene.internal
     */
    override fun getValues(fieldName: String): PointValues {
        val fieldInfo: FieldInfo? = readState.fieldInfos.fieldInfo(fieldName)
        requireNotNull(fieldInfo) { "field=\"$fieldName\" is unrecognized" }
        require(fieldInfo.pointDimensionCount != 0) { "field=\"$fieldName\" did not index point values" }

        return readers.get(fieldInfo.number)!!
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        CodecUtil.checksumEntireFile(indexIn)
        CodecUtil.checksumEntireFile(dataIn)
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(indexIn, dataIn)
        // Free up heap:
        readers.clear()
    }
}
