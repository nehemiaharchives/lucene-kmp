package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.StoredFieldsWriter
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.compressing.Compressor
import org.gnit.lucenekmp.codecs.compressing.Decompressor
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsFormat
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils

internal class SortingStoredFieldsConsumer(
    codec: Codec,
    directory: Directory,
    info: SegmentInfo
) : StoredFieldsConsumer(codec, directory, info) {
    var tmpDirectory: TrackingTmpOutputDirectoryWrapper? = null

    @Throws(IOException::class)
    override fun initStoredFieldsWriter() {
        if (writer == null) {
            this.tmpDirectory = TrackingTmpOutputDirectoryWrapper(directory)
            this.writer =
                TEMP_STORED_FIELDS_FORMAT.fieldsWriter(tmpDirectory!!, info, IOContext.DEFAULT)
        }
    }

    @Throws(IOException::class)
    override fun flush(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?
    ) {
        super.flush(state, sortMap)
        val reader: StoredFieldsReader =
            TEMP_STORED_FIELDS_FORMAT.fieldsReader(
                tmpDirectory!!, state.segmentInfo, state.fieldInfos, IOContext.DEFAULT
            )
        // Don't pull a merge instance, since merge instances optimize for
        // sequential access while we consume stored fields in random order here.
        val sortWriter: StoredFieldsWriter =
            codec.storedFieldsFormat().fieldsWriter(state.directory, state.segmentInfo, state.context)
        try {
            reader.checkIntegrity()
            val visitor = CopyVisitor(sortWriter)
            for (docID in 0..<state.segmentInfo.maxDoc()) {
                sortWriter.startDocument()
                reader.document(if (sortMap == null) docID else sortMap.newToOld(docID), visitor)
                sortWriter.finishDocument()
            }
            sortWriter.finish(state.segmentInfo.maxDoc())
        } finally {
            IOUtils.close(reader, sortWriter)
            tmpDirectory?.let { dir ->
                IOUtils.deleteFiles(dir, dir.getTemporaryFiles().values)
            }
        }
    }

    override fun abort() {
        try {
            super.abort()
        } finally {
            if (tmpDirectory != null) {
                IOUtils.deleteFilesIgnoringExceptions(
                    tmpDirectory!!, tmpDirectory!!.getTemporaryFiles().values
                )
            }
        }
    }

    /** A visitor that copies every field it sees in the provided [StoredFieldsWriter].  */
    private class CopyVisitor(val writer: StoredFieldsWriter) :
        StoredFieldVisitor() {

        @Throws(IOException::class)
        override fun binaryField(
            fieldInfo: FieldInfo,
            value: StoredFieldDataInput
        ) {
            writer.writeField(fieldInfo, value)
        }

        @Throws(IOException::class)
        override fun binaryField(fieldInfo: FieldInfo, value: ByteArray) {
            // TODO: can we avoid new BR here
            writer.writeField(fieldInfo, BytesRef(value))
        }

        @Throws(IOException::class)
        override fun stringField(fieldInfo: FieldInfo, value: String) {
            writer.writeField(
                fieldInfo, value
            )
        }

        @Throws(IOException::class)
        override fun intField(fieldInfo: FieldInfo, value: Int) {
            writer.writeField(fieldInfo, value)
        }

        @Throws(IOException::class)
        override fun longField(fieldInfo: FieldInfo, value: Long) {
            writer.writeField(fieldInfo, value)
        }

        @Throws(IOException::class)
        override fun floatField(fieldInfo: FieldInfo, value: Float) {
            writer.writeField(fieldInfo, value)
        }

        @Throws(IOException::class)
        override fun doubleField(fieldInfo: FieldInfo, value: Double) {
            writer.writeField(fieldInfo, value)
        }

        @Throws(IOException::class)
        override fun needsField(fieldInfo: FieldInfo): Status {
            return Status.YES
        }
    }

    companion object {
        val NO_COMPRESSION: CompressionMode =
            object : CompressionMode() {
                override fun newCompressor(): Compressor {
                    return object : Compressor() {
                        @Throws(IOException::class)
                        override fun close() {
                        }

                        @Throws(IOException::class)
                        override fun compress(
                            buffersInput: ByteBuffersDataInput,
                            out: DataOutput
                        ) {
                            out.copyBytes(buffersInput, buffersInput.length())
                        }
                    }
                }

                override fun newDecompressor(): Decompressor {
                    return object : Decompressor() {
                        @Throws(IOException::class)
                        override fun decompress(
                            `in`: DataInput,
                            originalLength: Int,
                            offset: Int,
                            length: Int,
                            bytes: BytesRef
                        ) {
                            bytes.bytes = ArrayUtil.growNoCopy(bytes.bytes, length)
                            `in`.skipBytes(offset.toLong())
                            `in`.readBytes(bytes.bytes, 0, length)
                            bytes.offset = 0
                            bytes.length = length
                        }

                        override fun clone(): Decompressor {
                            return this
                        }
                    }
                }
            }
        private val TEMP_STORED_FIELDS_FORMAT: StoredFieldsFormat =
            Lucene90CompressingStoredFieldsFormat(
                "TempStoredFields", NO_COMPRESSION, 128 * 1024, 1, 10
            )
    }
}
