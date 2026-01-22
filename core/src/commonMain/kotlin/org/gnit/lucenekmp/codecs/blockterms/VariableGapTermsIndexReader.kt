package org.gnit.lucenekmp.codecs.blockterms


import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.fst.BytesRefFSTEnum
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs

/**
 * See [VariableGapTermsIndexWriter]
 *
 * @lucene.experimental
 */
class VariableGapTermsIndexReader(state: SegmentReadState) : TermsIndexReaderBase() {
    private val fstOutputs: PositiveIntOutputs = PositiveIntOutputs.singleton

    val fields: HashMap<String, FST<Long>> = HashMap()

    init {
        val metaFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name,
                state.segmentSuffix,
                VariableGapTermsIndexWriter.TERMS_META_EXTENSION
            )
        val indexFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name,
                state.segmentSuffix,
                VariableGapTermsIndexWriter.TERMS_INDEX_EXTENSION
            )

        state.directory.openChecksumInput(metaFileName).use { metaIn ->
            state.directory.openChecksumInput(indexFileName).use { indexIn ->
                var priorE: Throwable? = null
                try {
                    CodecUtil.checkIndexHeader(
                        metaIn,
                        VariableGapTermsIndexWriter.META_CODEC_NAME,
                        VariableGapTermsIndexWriter.VERSION_START,
                        VariableGapTermsIndexWriter.VERSION_CURRENT,
                        state.segmentInfo.getId(),
                        state.segmentSuffix
                    )

                    CodecUtil.checkIndexHeader(
                        indexIn,
                        VariableGapTermsIndexWriter.CODEC_NAME,
                        VariableGapTermsIndexWriter.VERSION_START,
                        VariableGapTermsIndexWriter.VERSION_CURRENT,
                        state.segmentInfo.getId(),
                        state.segmentSuffix
                    )

                    // Read directory
                    var field: Int = metaIn.readInt()
                    while (field != -1) {
                        val indexStart: Long = metaIn.readVLong()
                        val fieldInfo: FieldInfo? = state.fieldInfos.fieldInfo(field)
                        if (indexIn.filePointer != indexStart) {
                            throw CorruptIndexException(
                                "Gap in FST, expected position " + indexIn.filePointer + ", got " + indexStart,
                                metaIn
                            )
                        }
                        val fst: FST<Long> = FST(FST.readMetadata(metaIn, fstOutputs), indexIn)
                        val previous: FST<Long>? = fields.put(fieldInfo!!.name, fst)
                        if (previous != null) {
                            throw CorruptIndexException(
                                "duplicate field: " + fieldInfo.name,
                                metaIn
                            )
                        }
                        field = metaIn.readInt()
                    }
                } catch (t: Throwable) {
                    priorE = t
                } finally {
                    CodecUtil.checkFooter(metaIn, priorE)
                    CodecUtil.checkFooter(indexIn, priorE)
                }
            }
        }
    }

    private class IndexEnum(fst: FST<Long>) : FieldIndexEnum() {
        private val fstEnum: BytesRefFSTEnum<Long> = BytesRefFSTEnum(fst)
        private var current: BytesRefFSTEnum.InputOutput<Long>? = null

        override fun term(): BytesRef? {
            if (current == null) {
                return null
            } else {
                return current!!.input
            }
        }

        @Throws(IOException::class)
        override fun seek(target: BytesRef): Long {
            // System.out.println("VGR: seek field=" + fieldInfo.name + " target=" + target);
            current = fstEnum.seekFloor(target)
            // System.out.println("  got input=" + current.input + " output=" + current.output);
            return current!!.output!!
        }

        @Throws(IOException::class)
        override fun next(): Long {
            // System.out.println("VGR: next field=" + fieldInfo.name);
            current = fstEnum.next()
            if (current == null) {
                // System.out.println("  eof");
                return -1
            } else {
                return current!!.output!!
            }
        }

        override fun ord(): Long {
            throw UnsupportedOperationException()
        }

        override fun seek(ord: Long): Long {
            throw UnsupportedOperationException()
        }
    }

    override fun supportsOrd(): Boolean {
        return false
    }

    override fun getFieldEnum(fieldInfo: FieldInfo): FieldIndexEnum? {
        val fieldData: FST<Long>? = fields[fieldInfo.name]
        if (fieldData == null) {
            return null
        } else {
            return IndexEnum(fieldData)
        }
    }

    override fun close() {
    }

    override fun toString(): String {
        return this::class.simpleName + "(fields=" + fields.size + ")"
    }
}
