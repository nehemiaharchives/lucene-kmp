package org.gnit.lucenekmp.codecs.blockterms

import okio.IOException
import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsWriterBase
import org.gnit.lucenekmp.codecs.TermStats
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import kotlin.math.min


// TODO: currently we encode all terms between two indexed
// terms as a block; but, we could decouple the two, ie
// allow several blocks in between two indexed terms
/**
 * Writes terms dict, block-encoding (column stride) each term's metadata for each set of terms
 * between two index terms.
 *
 * @lucene.experimental
 */
class BlockTermsWriter(
    termsIndexWriter: TermsIndexWriterBase,
    state: SegmentWriteState,
    postingsWriter: PostingsWriterBase
) : FieldsConsumer() {
    protected var out: IndexOutput?
    val postingsWriter: PostingsWriterBase
    val fieldInfos: FieldInfos?
    var currentField: FieldInfo? = null
    private val termsIndexWriter: TermsIndexWriterBase
    private val maxDoc: Int

    private class FieldMetaData(
        val fieldInfo: FieldInfo,
        val numTerms: Long,
        val termsStartPointer: Long,
        val sumTotalTermFreq: Long,
        val sumDocFreq: Long,
        val docCount: Int
    ) {

        init {
            assert(numTerms > 0)
        }
    }

    private val fields: MutableList<FieldMetaData> = mutableListOf()

    // private final String segment;
    init {
        val termsFileName: String = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, TERMS_EXTENSION)
        this.termsIndexWriter = termsIndexWriter
        maxDoc = state.segmentInfo.maxDoc()
        out = state.directory.createOutput(termsFileName, state.context)
        var success = false
        try {
            fieldInfos = state.fieldInfos
            CodecUtil.writeIndexHeader(
                out!!, CODEC_NAME, VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix
            )
            currentField = null
            this.postingsWriter = postingsWriter

            // segment = state.segmentName;

            // System.out.println("BTW.init seg=" + state.segmentName);
            postingsWriter.init(out!!, state) // have consumer write its format/header
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(out)
            }
        }
    }

    @Throws(IOException::class)
    override fun write(
        fields: Fields,
        norms: NormsProducer?
    ) {
        for (field in fields) {
            val terms: Terms? = fields.terms(field)
            if (terms == null) {
                continue
            }

            val termsEnum: TermsEnum = terms.iterator()

            val termsWriter = addField(fieldInfos!!.fieldInfo(field)!!)

            while (true) {
                val term: BytesRef? = termsEnum.next()
                if (term == null) {
                    break
                }

                termsWriter.write(term, termsEnum, norms!!)
            }

            termsWriter.finish()
        }
    }

    @Throws(IOException::class)
    private fun addField(field: FieldInfo): TermsWriter {
        // System.out.println("\nBTW.addField seg=" + segment + " field=" + field.name);
        assert(currentField == null || currentField!!.name < field.name)
        currentField = field
        val fieldIndexWriter: TermsIndexWriterBase.FieldWriter =
            termsIndexWriter.addField(field, out!!.filePointer)
        return TermsWriter(fieldIndexWriter, field, postingsWriter)
    }

    override fun close() {
        if (out != null) {
            try {
                val dirStart: Long = out!!.filePointer

                out!!.writeVInt(fields.size)
                for (field in fields) {
                    out!!.writeVInt(field.fieldInfo.number)
                    out!!.writeVLong(field.numTerms)
                    out!!.writeVLong(field.termsStartPointer)
                    if (field.fieldInfo.indexOptions != IndexOptions.DOCS) {
                        out!!.writeVLong(field.sumTotalTermFreq)
                    }
                    out!!.writeVLong(field.sumDocFreq)
                    out!!.writeVInt(field.docCount)
                }
                writeTrailer(dirStart)
                CodecUtil.writeFooter(out!!)
            } finally {
                IOUtils.close(out, postingsWriter, termsIndexWriter)
                out = null
            }
        }
    }

    @Throws(IOException::class)
    private fun writeTrailer(dirStart: Long) {
        out!!.writeLong(dirStart)
    }

    private class TermEntry {
        val term: BytesRefBuilder = BytesRefBuilder()
        var state: BlockTermState? = null
    }

    internal inner class TermsWriter(
        private val fieldIndexWriter: TermsIndexWriterBase.FieldWriter,
        private val fieldInfo: FieldInfo,
        postingsWriter: PostingsWriterBase
    ) {
        private val postingsWriter: PostingsWriterBase
        private val termsStartPointer: Long
        private var numTerms: Long = 0
        private val docsSeen: FixedBitSet = FixedBitSet(maxDoc)
        var sumTotalTermFreq: Long = 0
        var sumDocFreq: Long = 0
        var docCount: Int = 0

        private var pendingTerms: Array<TermEntry>

        private var pendingCount = 0

        private val lastPrevTerm: BytesRefBuilder =
            BytesRefBuilder()

        @Throws(IOException::class)
        fun write(
            text: BytesRef,
            termsEnum: TermsEnum,
            norms: NormsProducer
        ) {
            val state: BlockTermState? =
                postingsWriter.writeTerm(text, termsEnum, docsSeen, norms)
            if (state == null) {
                // No docs for this term:
                return
            }
            sumDocFreq += state.docFreq.toLong()
            sumTotalTermFreq += state.totalTermFreq

            assert(state.docFreq > 0)

            // System.out.println("BTW: finishTerm term=" + fieldInfo.name + ":" + text.utf8ToString() + "
            // " + text + " seg=" + segment + " df=" + stats.docFreq);
            val stats = TermStats(state.docFreq, state.totalTermFreq)
            val isIndexTerm: Boolean = fieldIndexWriter.checkIndexTerm(text, stats)

            if (isIndexTerm) {
                if (pendingCount > 0) {
                    // Instead of writing each term, live, we gather terms
                    // in RAM in a pending buffer, and then write the
                    // entire block in between index terms:
                    flushBlock()
                }
                fieldIndexWriter.add(text, stats, out!!.filePointer)
                // System.out.println("  index term!");
            }

            pendingTerms =
                ArrayUtil.grow<TermEntry>(pendingTerms, pendingCount + 1)
            for (i in pendingCount..<pendingTerms.size) {
                pendingTerms[i] = TermEntry()
            }
            val te = pendingTerms[pendingCount]
            te.term.copyBytes(text)
            te.state = state

            pendingCount++
            numTerms++
        }

        // Finishes all terms in this field
        @Throws(IOException::class)
        fun finish() {
            if (pendingCount > 0) {
                flushBlock()
            }
            // EOF marker:
            out!!.writeVInt(0)

            fieldIndexWriter.finish(out!!.filePointer)
            if (numTerms > 0) {
                fields.add(
                    FieldMetaData(
                        fieldInfo,
                        numTerms,
                        termsStartPointer,
                        if (fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS
                        )
                            sumTotalTermFreq
                        else
                            -1,
                        sumDocFreq,
                        docsSeen.cardinality()
                    )
                )
            }
        }

        private fun sharedPrefix(
            term1: BytesRef,
            term2: BytesRef
        ): Int {
            assert(term1.offset == 0)
            assert(term2.offset == 0)
            var pos1 = 0
            val pos1End: Int = pos1 + min(term1.length, term2.length)
            var pos2 = 0
            while (pos1 < pos1End) {
                if (term1.bytes[pos1] != term2.bytes[pos2]) {
                    return pos1
                }
                pos1++
                pos2++
            }
            return pos1
        }

        private val bytesWriter: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()

        init {
            pendingTerms = Array<TermEntry>(32) { TermEntry() }

            termsStartPointer = out!!.filePointer
            this.postingsWriter = postingsWriter
            postingsWriter.setField(fieldInfo)
        }

        @Throws(IOException::class)
        private fun flushBlock() {
            // System.out.println("BTW.flushBlock seg=" + segment + " pendingCount=" + pendingCount + "
            // fp=" + out!!.filePointer);

            // First pass: compute common prefix for all terms
            // in the block, against term before first term in
            // this block:

            var commonPrefix = sharedPrefix(lastPrevTerm.get(), pendingTerms[0].term.get())
            for (termCount in 1..<pendingCount) {
                commonPrefix = min(
                    commonPrefix,
                    sharedPrefix(lastPrevTerm.get(), pendingTerms[termCount].term.get())
                )
            }

            out!!.writeVInt(pendingCount)
            out!!.writeVInt(commonPrefix)

            // 2nd pass: write suffixes, as separate byte[] blob
            for (termCount in 0..<pendingCount) {
                val suffix: Int = pendingTerms[termCount].term.length() - commonPrefix
                // TODO: cutover to better intblock codec, instead
                // of interleaving here:
                bytesWriter.writeVInt(suffix)
                bytesWriter.writeBytes(pendingTerms[termCount].term.bytes(), commonPrefix, suffix)
            }
            out!!.writeVInt(Math.toIntExact(bytesWriter.size()))
            bytesWriter.copyTo(out!!)
            bytesWriter.reset()

            // 3rd pass: write the freqs as byte[] blob
            // TODO: cutover to better intblock codec.  simple64
            // write prefix, suffix first:
            for (termCount in 0..<pendingCount) {
                val state: BlockTermState =
                    checkNotNull(pendingTerms[termCount].state)
                bytesWriter.writeVInt(state.docFreq)
                if (fieldInfo.indexOptions != IndexOptions.DOCS) {
                    bytesWriter.writeVLong(state.totalTermFreq - state.docFreq)
                }
            }
            out!!.writeVInt(Math.toIntExact(bytesWriter.size()))
            bytesWriter.copyTo(out!!)
            bytesWriter.reset()

            // 4th pass: write the metadata
            var absolute = true
            for (termCount in 0..<pendingCount) {
                val state: BlockTermState = pendingTerms[termCount].state!!
                postingsWriter.encodeTerm(bytesWriter, fieldInfo, state, absolute)
                absolute = false
            }
            out!!.writeVInt(Math.toIntExact(bytesWriter.size()))
            bytesWriter.copyTo(out!!)
            bytesWriter.reset()

            lastPrevTerm.copyBytes(pendingTerms[pendingCount - 1].term)
            pendingCount = 0
        }
    }

    companion object {
        const val CODEC_NAME: String = "BlockTermsWriter"

        // Initial format
        const val VERSION_START: Int = 4
        const val VERSION_CURRENT: Int = VERSION_START

        /** Extension of terms file  */
        const val TERMS_EXTENSION: String = "tib"
    }
}
