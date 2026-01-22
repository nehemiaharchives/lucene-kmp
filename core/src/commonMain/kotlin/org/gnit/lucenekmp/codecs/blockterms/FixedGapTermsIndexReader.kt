package org.gnit.lucenekmp.codecs.blockterms

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.Accountables
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.PagedBytes
import org.gnit.lucenekmp.util.packed.MonotonicBlockPackedReader


/**
 * TermsIndexReader for simple every Nth terms indexes.
 *
 * @see FixedGapTermsIndexWriter
 *
 * @lucene.experimental
 */
class FixedGapTermsIndexReader(state: SegmentReadState) : TermsIndexReaderBase() {
    // NOTE: long is overkill here, but we use this in a
    // number of places to multiply out the actual ord, and we
    // will overflow int during those multiplies.  So to avoid
    // having to upgrade each multiple to long in multiple
    // places (error-prone), we use long here:
    private val indexInterval: Long

    private val packedIntsVersion: Int
    private val blocksize: Int

    // all fields share this single logical byte[]
    private val termBytesReader: PagedBytes.Reader

    val fields: HashMap<String, FieldIndexData> = HashMap<String, FieldIndexData>()

    init {
        val termBytes = PagedBytes(PAGED_BYTES_BITS)

        val fileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name,
                state.segmentSuffix,
                FixedGapTermsIndexWriter.TERMS_INDEX_EXTENSION
            )
        val `in`: IndexInput =
            state.directory.openInput(fileName, state.context)

        var success = false

        try {
            CodecUtil.checkIndexHeader(
                `in`,
                FixedGapTermsIndexWriter.CODEC_NAME,
                FixedGapTermsIndexWriter.VERSION_CURRENT,
                FixedGapTermsIndexWriter.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )

            CodecUtil.checksumEntireFile(`in`)

            indexInterval = `in`.readVInt().toLong()
            if (indexInterval < 1) {
                throw CorruptIndexException(
                    "invalid indexInterval: $indexInterval",
                    `in`
                )
            }
            packedIntsVersion = `in`.readVInt()
            blocksize = `in`.readVInt()

            seekDir(`in`)

            // Read directory
            val numFields: Int = `in`.readVInt()
            if (numFields < 0) {
                throw CorruptIndexException(
                    "invalid numFields: $numFields",
                    `in`
                )
            }
            // System.out.println("FGR: init seg=" + segment + " div=" + indexDivisor + " nF=" +
            // numFields);
            for (i in 0..<numFields) {
                val field: Int = `in`.readVInt()
                val numIndexTerms =
                    `in`.readVInt()
                        .toLong() // TODO: change this to a vLong if we fix writer to support > 2B index
                // terms
                if (numIndexTerms < 0) {
                    throw CorruptIndexException(
                        "invalid numIndexTerms: $numIndexTerms",
                        `in`
                    )
                }
                val termsStart: Long = `in`.readVLong()
                val indexStart: Long = `in`.readVLong()
                val packedIndexStart: Long = `in`.readVLong()
                val packedOffsetsStart: Long = `in`.readVLong()
                if (packedIndexStart < indexStart) {
                    throw CorruptIndexException(
                        ("invalid packedIndexStart: "
                                + packedIndexStart
                                + " indexStart: "
                                + indexStart
                                + " numIndexTerms: "
                                + numIndexTerms),
                        `in`
                    )
                }
                val fieldInfo: FieldInfo = state.fieldInfos.fieldInfo(field)!!
                val previous: FieldIndexData? =
                    fields.put(
                        fieldInfo.name,
                        FieldIndexData(
                            `in`,
                            termBytes,
                            indexStart,
                            termsStart,
                            packedIndexStart,
                            packedOffsetsStart,
                            numIndexTerms
                        )
                    )
                if (previous != null) {
                    throw CorruptIndexException(
                        "duplicate field: " + fieldInfo.name,
                        `in`
                    )
                }
            }
            success = true
        } finally {
            if (success) {
                IOUtils.close(`in`)
            } else {
                IOUtils.closeWhileHandlingException(`in`)
            }
            termBytesReader = termBytes.freeze(true)
        }
    }

    private inner class IndexEnum(private val fieldIndex: FieldIndexData) :
        TermsIndexReaderBase.FieldIndexEnum() {
        private val term: BytesRef = BytesRef()
        private var ord: Long = 0

        override fun term(): BytesRef {
            return term
        }

        override fun seek(target: BytesRef): Long {
            var lo: Long = 0 // binary search
            var hi = fieldIndex.numIndexTerms - 1

            while (hi >= lo) {
                val mid = (lo + hi) ushr 1

                val offset: Long = fieldIndex.termOffsets.get(mid)
                val length = (fieldIndex.termOffsets.get(1 + mid) - offset).toInt()
                termBytesReader.fillSlice(term, fieldIndex.termBytesStart + offset, length)

                val delta = target.compareTo(term)
                if (delta < 0) {
                    hi = mid - 1
                } else if (delta > 0) {
                    lo = mid + 1
                } else {
                    assert(mid >= 0)
                    ord = mid * indexInterval
                    return fieldIndex.termsStart + fieldIndex.termsDictOffsets.get(mid)
                }
            }

            if (hi < 0) {
                assert(hi == -1L)
                hi = 0
            }

            val offset: Long = fieldIndex.termOffsets.get(hi)
            val length = (fieldIndex.termOffsets.get(1 + hi) - offset).toInt()
            termBytesReader.fillSlice(term, fieldIndex.termBytesStart + offset, length)

            ord = hi * indexInterval
            return fieldIndex.termsStart + fieldIndex.termsDictOffsets.get(hi)
        }

        override fun next(): Long {
            val idx = 1 + (ord / indexInterval)
            if (idx >= fieldIndex.numIndexTerms) {
                return -1
            }
            ord += indexInterval

            val offset: Long = fieldIndex.termOffsets.get(idx)
            val length = (fieldIndex.termOffsets.get(1 + idx) - offset).toInt()
            termBytesReader.fillSlice(term, fieldIndex.termBytesStart + offset, length)
            return fieldIndex.termsStart + fieldIndex.termsDictOffsets.get(idx)
        }

        override fun ord(): Long {
            return ord
        }

        override fun seek(ord: Long): Long {
            val idx = ord / indexInterval
            // caller must ensure ord is in bounds
            assert(idx < fieldIndex.numIndexTerms)
            val offset: Long = fieldIndex.termOffsets.get(idx)
            val length = (fieldIndex.termOffsets.get(1 + idx) - offset).toInt()
            termBytesReader.fillSlice(term, fieldIndex.termBytesStart + offset, length)
            this.ord = idx * indexInterval
            return fieldIndex.termsStart + fieldIndex.termsDictOffsets.get(idx)
        }
    }

    override fun supportsOrd(): Boolean {
        return true
    }

    inner class FieldIndexData(
        `in`: IndexInput,
        termBytes: PagedBytes,
        indexStart: Long,
        val termsStart: Long,
        packedIndexStart: Long,
        packedOffsetsStart: Long,
        numIndexTerms: Long
    ) : Accountable {
        // where this field's terms begin in the packed byte[]
        // data
        val termBytesStart: Long = termBytes.pointer

        // offset into index termBytes
        val termOffsets: MonotonicBlockPackedReader

        // index pointers into main terms dict
        val termsDictOffsets: MonotonicBlockPackedReader

        val numIndexTerms: Long

        init {

            val clone: IndexInput = `in`.clone()
            clone.seek(indexStart)

            this.numIndexTerms = numIndexTerms
            assert(this.numIndexTerms > 0) { "numIndexTerms=$numIndexTerms" }

            clone.use {
                val numTermBytes = packedIndexStart - indexStart
                termBytes.copy(clone, numTermBytes)

                // records offsets into main terms dict file
                termsDictOffsets =
                    MonotonicBlockPackedReader.of(
                        clone,
                        packedIntsVersion,
                        blocksize,
                        numIndexTerms
                    )

                // records offsets into byte[] term data
                termOffsets =
                    MonotonicBlockPackedReader.of(
                        clone,
                        packedIntsVersion,
                        blocksize,
                        1 + numIndexTerms
                    )
            }
        }

        override fun ramBytesUsed(): Long {
            return ((if (termOffsets != null) termOffsets.ramBytesUsed() else 0)
                    + (if (termsDictOffsets != null) termsDictOffsets.ramBytesUsed() else 0))
        }

        override val childResources: MutableCollection<Accountable>
            get() {
                val resources: MutableList<Accountable> = mutableListOf()
                if (termOffsets != null) {
                    resources.add(
                        Accountables.namedAccountable(
                            "term lengths",
                            termOffsets
                        )
                    )
                }
                if (termsDictOffsets != null) {
                    resources.add(
                        Accountables.namedAccountable(
                            "offsets",
                            termsDictOffsets
                        )
                    )
                }
                return resources
            }

        override fun toString(): String {
            return "FixedGapTermIndex(indexterms=$numIndexTerms)"
        }
    }

    override fun getFieldEnum(fieldInfo: FieldInfo): FieldIndexEnum {
        return IndexEnum(fields[fieldInfo.name]!!)
    }

    override fun close() {
    }

    @Throws(IOException::class)
    private fun seekDir(input: IndexInput) {
        input.seek(input.length() - CodecUtil.footerLength() - 8)
        val dirOffset: Long = input.readLong()
        input.seek(dirOffset)
    }

    override fun toString(): String {
        return (this::class.simpleName
                + "(fields="
                + fields.size
                + ",interval="
                + indexInterval
                + ")")
    }

    companion object {
        private const val PAGED_BYTES_BITS = 15
    }
}
