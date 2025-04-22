package org.gnit.lucenekmp.codecs.lucene94

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.FieldInfosFormat
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.toBinaryString
import kotlin.experimental.or

/**
 * Lucene 9.0 Field Infos format.
 *
 *
 * Field names are stored in the field info file, with suffix `.fnm`.
 *
 *
 * FieldInfos (.fnm) --&gt; Header,FieldsCount, &lt;FieldName,FieldNumber,
 * FieldBits,DocValuesBits,DocValuesGen,Attributes,DimensionCount,DimensionNumBytes&gt;
 * <sup>FieldsCount</sup>,Footer
 *
 *
 * Data types:
 *
 *
 *  * Header --&gt; [IndexHeader][CodecUtil.checkIndexHeader]
 *  * FieldsCount --&gt; [VInt][DataOutput.writeVInt]
 *  * FieldName --&gt; [String][DataOutput.writeString]
 *  * FieldBits, IndexOptions, DocValuesBits --&gt; [Byte][DataOutput.writeByte]
 *  * FieldNumber, DimensionCount, DimensionNumBytes --&gt; [VInt][DataOutput.writeInt]
 *  * Attributes --&gt; [Map&amp;lt;String,String&amp;gt;][DataOutput.writeMapOfStrings]
 *  * DocValuesGen --&gt; [Int64][DataOutput.writeLong]
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * Field Descriptions:
 *
 *
 *  * FieldsCount: the number of fields in this file.
 *  * FieldName: name of the field as a UTF-8 String.
 *  * FieldNumber: the field's number. Note that unlike previous versions of Lucene, the fields
 * are not numbered implicitly by their order in the file, instead explicitly.
 *  * FieldBits: a byte containing field options.
 *
 *  * The low order bit (0x1) is one for fields that have term vectors stored, and zero for
 * fields without term vectors.
 *  * If the second lowest order-bit is set (0x2), norms are omitted for the indexed field.
 *  * If the third lowest-order bit is set (0x4), payloads are stored for the indexed
 * field.
 *
 *  * IndexOptions: a byte containing index options.
 *
 *  * 0: not indexed
 *  * 1: indexed as DOCS_ONLY
 *  * 2: indexed as DOCS_AND_FREQS
 *  * 3: indexed as DOCS_AND_FREQS_AND_POSITIONS
 *  * 4: indexed as DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
 *
 *  * DocValuesBits: a byte containing per-document value types. The type recorded as two
 * four-bit integers, with the high-order bits representing `norms` options, and
 * the low-order bits representing `DocValues` options. Each four-bit integer can be
 * decoded as such:
 *
 *  * 0: no DocValues for this field.
 *  * 1: NumericDocValues. ([DocValuesType.NUMERIC])
 *  * 2: BinaryDocValues. (`DocValuesType#BINARY`)
 *  * 3: SortedDocValues. (`DocValuesType#SORTED`)
 *
 *  * DocValuesGen is the generation count of the field's DocValues. If this is -1, there are no
 * DocValues updates to that field. Anything above zero means there are updates stored by
 * [DocValuesFormat].
 *  * Attributes: a key-value map of codec-private attributes.
 *  * PointDimensionCount, PointNumBytes: these are non-zero only if the field is indexed as
 * points, e.g. using [org.apache.lucene.document.LongPoint]
 *  * VectorDimension: it is non-zero if the field is indexed as vectors.
 *  * VectorEncoding: a byte containing the encoding of vector values:
 *
 *  * 0: BYTE. Samples are stored as signed bytes
 *  * 1: FLOAT32. Samples are stored in IEEE 32-bit floating point format.
 *
 *  * VectorSimilarityFunction: a byte containing distance function used for similarity
 * calculation.
 *
 *  * 0: EUCLIDEAN distance. ([VectorSimilarityFunction.EUCLIDEAN])
 *  * 1: DOT_PRODUCT similarity. ([VectorSimilarityFunction.DOT_PRODUCT])
 *  * 2: COSINE similarity. ([VectorSimilarityFunction.COSINE])
 *  * 3: MAXIMUM_INNER_PRODUCT similarity. ([             ][VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT])
 *
 *
 *
 * @lucene.experimental
 */
class Lucene94FieldInfosFormat
/** Sole constructor.  */
    : FieldInfosFormat() {
    @Throws(IOException::class)
    override fun read(
        directory: Directory, segmentInfo: SegmentInfo, segmentSuffix: String, context: IOContext
    ): FieldInfos {
        val fileName: String =
            IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, EXTENSION)
        directory.openChecksumInput(fileName).use { input ->
            var priorE: Throwable? = null
            var infos: Array<FieldInfo?>? = null
            try {
                val format: Int =
                    CodecUtil.checkIndexHeader(
                        input,
                        CODEC_NAME,
                        FORMAT_START,
                        FORMAT_CURRENT,
                        segmentInfo.getId(),
                        segmentSuffix
                    )

                val size: Int = input.readVInt() // read in the size
                infos = kotlin.arrayOfNulls<FieldInfo>(size)

                // previous field's attribute map, we share when possible:
                var lastAttributes = mutableMapOf<String, String>()

                for (i in 0..<size) {
                    val name: String = input.readString()
                    val fieldNumber: Int = input.readVInt()
                    if (fieldNumber < 0) {
                        throw CorruptIndexException(
                            "invalid field number for field: $name, fieldNumber=$fieldNumber", input
                        )
                    }
                    val bits: Byte = input.readByte()
                    val storeTermVector = (bits.toInt() and STORE_TERMVECTOR.toInt()) != 0
                    val omitNorms = (bits.toInt() and OMIT_NORMS.toInt()) != 0
                    val storePayloads = (bits.toInt() and STORE_PAYLOADS.toInt()) != 0
                    val isSoftDeletesField = (bits.toInt() and SOFT_DELETES_FIELD.toInt()) != 0
                    val isParentField =
                        if (format >= FORMAT_PARENT_FIELD) (bits.toInt() and PARENT_FIELD_FIELD.toInt()) != 0 else false

                    if ((bits.toInt() and 0xC0) != 0) {
                        throw CorruptIndexException(
                            "unused bits are set \"" + Int.toBinaryString(bits.toInt()) + "\"", input
                        )
                    }
                    if (format < FORMAT_PARENT_FIELD && (bits.toInt() and 0xF0) != 0) {
                        throw CorruptIndexException(
                            "parent field bit is set but shouldn't \"" + Int.toBinaryString(bits.toInt()) + "\"",
                            input
                        )
                    }
                    if (format < FORMAT_DOCVALUE_SKIPPER && (bits.toInt() and DOCVALUES_SKIPPER.toInt()) != 0) {
                        throw CorruptIndexException(
                            ("doc values skipper bit is set but shouldn't \""
                                    + Int.toBinaryString(bits.toInt())
                                    + "\""),
                            input
                        )
                    }

                    val indexOptions: IndexOptions = getIndexOptions(input, input.readByte())

                    // DV Types are packed in one byte
                    val docValuesType: DocValuesType = getDocValuesType(input, input.readByte())
                    val docValuesSkipIndex: DocValuesSkipIndexType = if (format >= FORMAT_DOCVALUE_SKIPPER) {
                        getDocValuesSkipIndexType(input, input.readByte())
                    } else {
                        DocValuesSkipIndexType.NONE
                    }
                    val dvGen: Long = input.readLong()
                    var attributes: MutableMap<String, String> = input.readMapOfStrings()
                    // just use the last field's map if its the same
                    if (attributes == lastAttributes) {
                        attributes = lastAttributes
                    }
                    lastAttributes = attributes
                    val pointDataDimensionCount: Int = input.readVInt()
                    val pointNumBytes: Int
                    var pointIndexDimensionCount = pointDataDimensionCount
                    if (pointDataDimensionCount != 0) {
                        pointIndexDimensionCount = input.readVInt()
                        pointNumBytes = input.readVInt()
                    } else {
                        pointNumBytes = 0
                    }
                    val vectorDimension: Int = input.readVInt()
                    val vectorEncoding: VectorEncoding = getVectorEncoding(input, input.readByte())
                    val vectorDistFunc: VectorSimilarityFunction = getDistFunc(input, input.readByte())

                    try {
                        infos[i] =
                            FieldInfo(
                                name,
                                fieldNumber,
                                storeTermVector,
                                omitNorms,
                                storePayloads,
                                indexOptions,
                                docValuesType,
                                docValuesSkipIndex,
                                dvGen,
                                attributes,
                                pointDataDimensionCount,
                                pointIndexDimensionCount,
                                pointNumBytes,
                                vectorDimension,
                                vectorEncoding,
                                vectorDistFunc,
                                isSoftDeletesField,
                                isParentField
                            )
                        infos[i]!!.checkConsistency()
                    } catch (e: IllegalStateException) {
                        throw CorruptIndexException(
                            "invalid fieldinfo for field: $name, fieldNumber=$fieldNumber", input, e
                        )
                    }
                }
            } catch (exception: Throwable) {
                priorE = exception
            } finally {
                CodecUtil.checkFooter(input, priorE)
            }
            return FieldInfos(infos as Array<FieldInfo>)
        }
    }

    @Throws(IOException::class)
    override fun write(
        directory: Directory,
        segmentInfo: SegmentInfo,
        segmentSuffix: String,
        infos: FieldInfos,
        context: IOContext
    ) {
        val fileName: String =
            IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, EXTENSION)
        directory.createOutput(fileName, context).use { output ->
            CodecUtil.writeIndexHeader(
                output,
                CODEC_NAME,
                FORMAT_CURRENT,
                segmentInfo.getId(),
                segmentSuffix
            )
            output.writeVInt(infos.size())
            for (fi in infos) {
                fi.checkConsistency()

                output.writeString(fi.name)
                output.writeVInt(fi.number)

                var bits: Byte = 0x0
                if (fi.hasTermVectors()) bits = bits or STORE_TERMVECTOR
                if (fi.omitsNorms()) bits = bits or OMIT_NORMS
                if (fi.hasPayloads()) bits = bits or STORE_PAYLOADS
                if (fi.isSoftDeletesField) bits = bits or SOFT_DELETES_FIELD
                if (fi.isParentField) bits = bits or PARENT_FIELD_FIELD
                output.writeByte(bits)

                output.writeByte(indexOptionsByte(fi.getIndexOptions()))

                // pack the DV type and hasNorms in one byte
                output.writeByte(docValuesByte(fi.getDocValuesType()))
                output.writeByte(docValuesSkipIndexByte(fi.docValuesSkipIndexType()))
                output.writeLong(fi.getDocValuesGen())
                output.writeMapOfStrings(fi.attributes())
                output.writeVInt(fi.getPointDimensionCount())
                if (fi.getPointDimensionCount() != 0) {
                    output.writeVInt(fi.getPointIndexDimensionCount())
                    output.writeVInt(fi.getPointNumBytes())
                }
                output.writeVInt(fi.getVectorDimension())
                output.writeByte(fi.getVectorEncoding().ordinal.toByte())
                output.writeByte(distFuncToOrd(fi.getVectorSimilarityFunction()))
            }
            CodecUtil.writeFooter(output)
        }
    }

    companion object {
        init {
            // We "mirror" DocValues enum values with the constants below; let's try to ensure if we add a
            // new DocValuesType while this format is
            // still used for writing, we remember to fix this encoding:
            require(DocValuesType.entries.size == 6)
        }

        private fun docValuesByte(type: DocValuesType): Byte {
            return when (type) {
                DocValuesType.NONE -> 0
                DocValuesType.NUMERIC -> 1
                DocValuesType.BINARY -> 2
                DocValuesType.SORTED -> 3
                DocValuesType.SORTED_SET -> 4
                DocValuesType.SORTED_NUMERIC -> 5
                else ->         // BUG
                    throw AssertionError("unhandled DocValuesType: $type")
            }
        }

        private fun docValuesSkipIndexByte(type: DocValuesSkipIndexType): Byte {
            return when (type) {
                DocValuesSkipIndexType.NONE -> 0
                DocValuesSkipIndexType.RANGE -> 1
                else ->         // BUG
                    throw AssertionError("unhandled DocValuesSkipIndexType: $type")
            }
        }

        @Throws(IOException::class)
        private fun getDocValuesType(input: IndexInput, b: Byte): DocValuesType {
            return when (b) {
                0.toByte() -> DocValuesType.NONE
                1.toByte() -> DocValuesType.NUMERIC
                2.toByte() -> DocValuesType.BINARY
                3.toByte() -> DocValuesType.SORTED
                4.toByte() -> DocValuesType.SORTED_SET
                5.toByte() -> DocValuesType.SORTED_NUMERIC
                else -> throw CorruptIndexException("invalid docvalues byte: $b", input)
            }
        }

        @Throws(IOException::class)
        private fun getDocValuesSkipIndexType(input: IndexInput, b: Byte): DocValuesSkipIndexType {
            return when (b) {
                0.toByte() -> DocValuesSkipIndexType.NONE
                1.toByte() -> DocValuesSkipIndexType.RANGE
                else -> throw CorruptIndexException("invalid docvaluesskipindex byte: $b", input)
            }
        }

        @Throws(IOException::class)
        private fun getVectorEncoding(input: IndexInput, b: Byte): VectorEncoding {
            if (b < 0 || b >= VectorEncoding.entries.size) {
                throw CorruptIndexException("invalid vector encoding: $b", input)
            }
            return VectorEncoding.entries[b.toInt()]
        }

        @Throws(IOException::class)
        private fun getDistFunc(input: IndexInput, b: Byte): VectorSimilarityFunction {
            try {
                return distOrdToFunc(b)
            } catch (e: IllegalArgumentException) {
                throw CorruptIndexException("invalid distance function: $b", input, e)
            }
        }

        // List of vector similarity functions. This list is defined here, in order
        // to avoid an undesirable dependency on the declaration and order of values
        // in VectorSimilarityFunction. The list values and order have been chosen to
        // match that of VectorSimilarityFunction in, at least, Lucene 9.10. Values
        val SIMILARITY_FUNCTIONS: MutableList<VectorSimilarityFunction> = mutableListOf<VectorSimilarityFunction>(
            VectorSimilarityFunction.EUCLIDEAN,
            VectorSimilarityFunction.DOT_PRODUCT,
            VectorSimilarityFunction.COSINE,
            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
        )

        fun distOrdToFunc(i: Byte): VectorSimilarityFunction {
            require(!(i < 0 || i >= SIMILARITY_FUNCTIONS.size)) { "invalid distance function: $i" }
            return SIMILARITY_FUNCTIONS[i.toInt()]
        }

        fun distFuncToOrd(func: VectorSimilarityFunction): Byte {
            for (i in SIMILARITY_FUNCTIONS.indices) {
                if (SIMILARITY_FUNCTIONS[i] == func) {
                    return i.toByte()
                }
            }
            throw IllegalArgumentException("invalid distance function: $func")
        }

        init {
            // We "mirror" IndexOptions enum values with the constants below; let's try to ensure if we add
            // a new IndexOption while this format is
            // still used for writing, we remember to fix this encoding:
            require(IndexOptions.entries.size == 5)
        }

        private fun indexOptionsByte(indexOptions: IndexOptions): Byte {
            return when (indexOptions) {
                IndexOptions.NONE -> 0
                IndexOptions.DOCS -> 1
                IndexOptions.DOCS_AND_FREQS -> 2
                IndexOptions.DOCS_AND_FREQS_AND_POSITIONS -> 3
                IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS -> 4
                else ->         // BUG:
                    throw AssertionError("unhandled IndexOptions: $indexOptions")
            }
        }

        @Throws(IOException::class)
        private fun getIndexOptions(input: IndexInput, b: Byte): IndexOptions {
            return when (b) {
                0.toByte() -> IndexOptions.NONE
                1.toByte() -> IndexOptions.DOCS
                2.toByte() -> IndexOptions.DOCS_AND_FREQS
                3.toByte() -> IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                4.toByte() -> IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
                else ->         // BUG
                    throw CorruptIndexException("invalid IndexOptions byte: $b", input)
            }
        }

        /** Extension of field infos  */
        const val EXTENSION: String = "fnm"

        // Codec header
        const val CODEC_NAME: String = "Lucene94FieldInfos"
        const val FORMAT_START: Int = 0

        // this doesn't actually change the file format but uses up one more bit an existing bit pattern
        const val FORMAT_PARENT_FIELD: Int = 1
        const val FORMAT_DOCVALUE_SKIPPER: Int = 2
        const val FORMAT_CURRENT: Int = FORMAT_DOCVALUE_SKIPPER

        // Field flags
        const val STORE_TERMVECTOR: Byte = 0x1
        const val OMIT_NORMS: Byte = 0x2
        const val STORE_PAYLOADS: Byte = 0x4
        const val SOFT_DELETES_FIELD: Byte = 0x8
        const val PARENT_FIELD_FIELD: Byte = 0x10
        const val DOCVALUES_SKIPPER: Byte = 0x20
    }
}
