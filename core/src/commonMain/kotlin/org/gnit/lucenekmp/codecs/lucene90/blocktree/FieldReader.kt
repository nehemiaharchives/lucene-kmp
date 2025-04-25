package org.gnit.lucenekmp.codecs.lucene90.blocktree

import org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsReader.Companion.VERSION_MSB_VLONG_OUTPUT
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.fst.ByteSequenceOutputs
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.OffHeapFSTStore
import kotlinx.io.IOException

/**
 * BlockTree's implementation of [Terms].
 *
 * @lucene.internal
 */
class FieldReader internal constructor(
    parent: Lucene90BlockTreeTermsReader,
    fieldInfo: FieldInfo,
    numTerms: Long,
    rootCode: BytesRef,
    sumTotalTermFreq: Long,
    sumDocFreq: Long,
    docCount: Int,
    indexStartFP: Long,
    metaIn: IndexInput,
    indexIn: IndexInput,
    minTerm: BytesRef,
    maxTerm: BytesRef
) : Terms() {
    // private final boolean DEBUG = BlockTreeTermsWriter.DEBUG;
    val numTerms: Long
    val fieldInfo: FieldInfo
    val sumTotalTermFreq: Long

    override fun getSumTotalTermFreq(): Long {
        return sumTotalTermFreq
    }

    val sumDocFreq: Long

    override fun getSumDocFreq(): Long {
        return sumDocFreq
    }

    val docCount: Int

    override fun getDocCount(): Int {
        return docCount
    }

    val rootBlockFP: Long
    val rootCode: BytesRef
    val minTerm: BytesRef?
    val maxTerm: BytesRef?
    val parent: Lucene90BlockTreeTermsReader

    val index: FST<BytesRef>

    // private boolean DEBUG;
    init {
        require(numTerms > 0)
        this.fieldInfo = fieldInfo
        // DEBUG = BlockTreeTermsReader.DEBUG && fieldInfo.name.equals("id");
        this.parent = parent
        this.numTerms = numTerms
        this.sumTotalTermFreq = sumTotalTermFreq
        this.sumDocFreq = sumDocFreq
        this.docCount = docCount
        this.minTerm = minTerm
        this.maxTerm = maxTerm
        // if (DEBUG) {
        //   System.out.println("BTTR: seg=" + segment + " field=" + fieldInfo.name + " rootBlockCode="
        // + rootCode + " divisor=" + indexDivisor);
        // }
        rootBlockFP =
            (readVLongOutput(ByteArrayDataInput(rootCode.bytes, rootCode.offset, rootCode.length))
                    ushr Lucene90BlockTreeTermsReader.OUTPUT_FLAGS_NUM_BITS)
        // Initialize FST always off-heap.
        val metadata: FST.FSTMetadata<BytesRef> /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
            FST.readMetadata(metaIn, ByteSequenceOutputs.getSingleton())
        index = FST.fromFSTReader(metadata, OffHeapFSTStore(indexIn, indexStartFP, metadata))!!
        /*
     if (false) {
     final String dotFileName = segment + "_" + fieldInfo.name + ".dot";
     Writer w = new OutputStreamWriter(new FileOutputStream(dotFileName));
     Util.toDot(index, w, false, false);
     System.out.println("FST INDEX: SAVED to " + dotFileName);
     w.close();
     }
    */
        val emptyOutput: BytesRef? = metadata.getEmptyOutput()
        if (rootCode != emptyOutput) {
            // TODO: this branch is never taken
            require(false)
            this.rootCode = rootCode
        } else {
            this.rootCode = emptyOutput
        }
    }

    @Throws(IOException::class)
    fun readVLongOutput(`in`: DataInput): Long {
        return if (parent.version >= VERSION_MSB_VLONG_OUTPUT) {
            readMSBVLong(`in`)
        } else {
            `in`.readVLong()
        }
    }

    @get:Throws(IOException::class)
    override val min: BytesRef?
        get() {
            return minTerm ?: // Older index that didn't store min/maxTerm
            super.getMin()
        }

    @get:Throws(IOException::class)
    override val max: BytesRef?
        get() {
            return maxTerm ?: // Older index that didn't store min/maxTerm
            super.getMax()
        }

    @get:Throws(IOException::class)
    val stats: Stats
        /** For debugging -- used by CheckIndex too  */
        get() = SegmentTermsEnum(this).computeBlockStats()

    override fun hasFreqs(): Boolean {
        return fieldInfo.getIndexOptions() >= IndexOptions.DOCS_AND_FREQS
    }

    override fun hasOffsets(): Boolean {
        return (fieldInfo
            .getIndexOptions() >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
    }

    override fun hasPositions(): Boolean {
        return fieldInfo.getIndexOptions() >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
    }

    override fun hasPayloads(): Boolean {
        return fieldInfo.hasPayloads()
    }

    @Throws(IOException::class)
    override fun iterator(): TermsEnum {
        return SegmentTermsEnum(this)
    }

    override fun size(): Long {
        return numTerms
    }

    @Throws(IOException::class)
    override fun intersect(compiled: CompiledAutomaton, startTerm: BytesRef?): TermsEnum {
        // if (DEBUG) System.out.println("  FieldReader.intersect startTerm=" +
        // ToStringUtils.bytesRefToString(startTerm));
        // System.out.println("intersect: " + compiled.type + " a=" + compiled.automaton);
        // TODO: we could push "it's a range" or "it's a prefix" down into IntersectTermsEnum
        // can we optimize knowing that...
        require(compiled.type === CompiledAutomaton.AUTOMATON_TYPE.NORMAL) { "please use CompiledAutomaton.getTermsEnum instead" }
        return IntersectTermsEnum(
            this,
            compiled.getTransitionAccessor()!!,
            compiled.getByteRunnable()!!,
            compiled.commonSuffixRef!!,
            startTerm
        )
    }

    override fun toString(): String {
        return ("BlockTreeTerms(seg="
                + parent.segment
                + " terms="
                + numTerms
                + ",postings="
                + sumDocFreq
                + ",positions="
                + sumTotalTermFreq
                + ",docs="
                + docCount
                + ")")
    }

    companion object {
        /**
         * Decodes a variable length byte[] in MSB order back to long, as written by [ ][Lucene90BlockTreeTermsWriter.writeMSBVLong].
         *
         *
         * Package private for testing.
         */
        @Throws(IOException::class)
        fun readMSBVLong(`in`: DataInput): Long {
            var l = 0L
            while (true) {
                val b: Byte = `in`.readByte()
                l = (l shl 7) or (b.toLong() and 0x7FL)
                if ((b.toInt() and 0x80) == 0) {
                    break
                }
            }
            return l
        }
    }
}
