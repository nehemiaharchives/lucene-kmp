package org.gnit.lucenekmp.codecs.blockterms

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.TermStats
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.packed.MonotonicBlockPackedWriter
import org.gnit.lucenekmp.util.packed.PackedInts

/**
 * Selects every Nth term as and index term, and hold term bytes (mostly) fully expanded in memory.
 * This terms index supports seeking by ord. See [VariableGapTermsIndexWriter] for a more
 * memory efficient terms index that does not support seeking by ord.
 *
 * @lucene.experimental
 */
class FixedGapTermsIndexWriter(
    state: SegmentWriteState,
    termIndexInterval: Int = DEFAULT_TERM_INDEX_INTERVAL
) : TermsIndexWriterBase() {
    protected var out: IndexOutput?

    private val termIndexInterval: Int
    private val fields: MutableList<SimpleFieldWriter> = mutableListOf()

    init {
        require(termIndexInterval > 0) { "invalid termIndexInterval: $termIndexInterval" }
        this.termIndexInterval = termIndexInterval
        val indexFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, TERMS_INDEX_EXTENSION
            )
        out = state.directory.createOutput(indexFileName, state.context)
        var success = false
        try {
            CodecUtil.writeIndexHeader(
                out!!, CODEC_NAME, VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix
            )
            out!!.writeVInt(termIndexInterval)
            out!!.writeVInt(PackedInts.VERSION_CURRENT)
            out!!.writeVInt(BLOCKSIZE)
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(out)
            }
        }
    }

    override fun addField(
        field: FieldInfo,
        termsFilePointer: Long
    ): FieldWriter {
        // System.out.println("FGW: addFfield=" + field.name);
        val writer = SimpleFieldWriter(field, termsFilePointer)
        fields.add(writer)
        return writer
    }

    /**
     * NOTE: if your codec does not sort in unicode code point order, you must override this method,
     * to simply return indexedTerm.length.
     */
    protected fun indexedTermPrefixLength(
        priorTerm: BytesRef,
        indexedTerm: BytesRef
    ): Int {
        // As long as codec sorts terms in unicode codepoint
        // order, we can safely strip off the non-distinguishing
        // suffix to save RAM in the loaded terms index.
        return StringHelper.sortKeyLength(priorTerm, indexedTerm)
    }

    private inner class SimpleFieldWriter(
        val fieldInfo: FieldInfo,
        termsFilePointer: Long
    ) : FieldWriter() {
        var numIndexTerms: Int = 0
        val indexStart: Long = out!!.filePointer
        val termsStart: Long = termsFilePointer
        var packedIndexStart: Long = 0
        var packedOffsetsStart: Long = 0
        private var numTerms: Long = 0

        private var offsetsBuffer: ByteBuffersDataOutput? = ByteBuffersDataOutput.newResettableInstance()
        private var termOffsets: MonotonicBlockPackedWriter? = MonotonicBlockPackedWriter(offsetsBuffer!!, BLOCKSIZE)
        private var currentOffset: Long = 0

        private var addressBuffer: ByteBuffersDataOutput? = ByteBuffersDataOutput.newResettableInstance()
        private var termAddresses: MonotonicBlockPackedWriter? = MonotonicBlockPackedWriter(addressBuffer!!, BLOCKSIZE)

        private val lastTerm: BytesRefBuilder =
            BytesRefBuilder()

        init {
            // we write terms+1 offsets, term n's length is n+1 - n
            try {
                termOffsets!!.add(0L)
            } catch (bogus: IOException) {
                throw RuntimeException(bogus)
            }
        }

        @Throws(IOException::class)
        override fun checkIndexTerm(
            text: BytesRef,
            stats: TermStats
        ): Boolean {
            // First term is first indexed term:
            // System.out.println("FGW: checkIndexTerm text=" + text.utf8ToString());
            if (0L == (numTerms++ % termIndexInterval)) {
                return true
            } else {
                if (0L == numTerms % termIndexInterval) {
                    // save last term just before next index term so we
                    // can compute wasted suffix
                    lastTerm.copyBytes(text)
                }
                return false
            }
        }

        @Throws(IOException::class)
        override fun add(
            text: BytesRef,
            stats: TermStats,
            termsFilePointer: Long
        ) {
            val indexedTermLength: Int
            if (numIndexTerms == 0) {
                // no previous term: no bytes to write
                indexedTermLength = 0
            } else {
                indexedTermLength = indexedTermPrefixLength(lastTerm.get(), text)
            }

            // System.out.println("FGW: add text=" + text.utf8ToString() + " " + text + " fp=" +
            // termsFilePointer);

            // write only the min prefix that shows the diff
            // against prior term
            out!!.writeBytes(text.bytes, text.offset, indexedTermLength)

            // save delta terms pointer
            termAddresses!!.add(termsFilePointer - termsStart)

            // save term length (in bytes)
            assert(indexedTermLength <= Short.MAX_VALUE)
            currentOffset += indexedTermLength.toLong()
            termOffsets!!.add(currentOffset)

            lastTerm.copyBytes(text)
            numIndexTerms++
        }

        @Throws(IOException::class)
        override fun finish(termsFilePointer: Long) {
            // write primary terms dict offsets

            packedIndexStart = out!!.filePointer

            // relative to our indexStart
            termAddresses!!.finish()
            addressBuffer!!.copyTo(out!!)

            packedOffsetsStart = out!!.filePointer

            // write offsets into the byte[] terms
            termOffsets!!.finish()
            offsetsBuffer!!.copyTo(out!!)

            // our referrer holds onto us, while other fields are
            // being written, so don't tie up this RAM:
            termAddresses = null
            termOffsets = termAddresses
            addressBuffer = null
            offsetsBuffer = null
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (out != null) {
            var success = false
            try {
                val dirStart: Long = out!!.filePointer
                val fieldCount = fields.size

                var nonNullFieldCount = 0
                for (i in 0..<fieldCount) {
                    val field = fields[i]
                    if (field.numIndexTerms > 0) {
                        nonNullFieldCount++
                    }
                }

                out!!.writeVInt(nonNullFieldCount)
                for (i in 0..<fieldCount) {
                    val field = fields[i]
                    if (field.numIndexTerms > 0) {
                        out!!.writeVInt(field.fieldInfo.number)
                        out!!.writeVInt(field.numIndexTerms)
                        out!!.writeVLong(field.termsStart)
                        out!!.writeVLong(field.indexStart)
                        out!!.writeVLong(field.packedIndexStart)
                        out!!.writeVLong(field.packedOffsetsStart)
                    }
                }
                writeTrailer(dirStart)
                CodecUtil.writeFooter(out!!)
                success = true
            } finally {
                if (success) {
                    IOUtils.close(out)
                } else {
                    IOUtils.closeWhileHandlingException(out)
                }
                out = null
            }
        }
    }

    @Throws(IOException::class)
    private fun writeTrailer(dirStart: Long) {
        out!!.writeLong(dirStart)
    }

    companion object {
        /** Extension of terms index file  */
        const val TERMS_INDEX_EXTENSION: String = "tii"

        const val CODEC_NAME: String = "FixedGapTermsIndex"
        const val VERSION_START: Int = 4
        const val VERSION_CURRENT: Int = VERSION_START

        const val BLOCKSIZE: Int = 4096
        const val DEFAULT_TERM_INDEX_INTERVAL: Int = 32
    }
}
