package org.gnit.lucenekmp.codecs.lucene101

import kotlinx.io.IOException
import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.PostingsReaderBase
import org.gnit.lucenekmp.codecs.PostingsWriterBase
import org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsReader
import org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlin.jvm.JvmOverloads
import kotlin.reflect.KClass


/**
 * Lucene 10.1 postings format, which encodes postings in packed integer blocks for fast decode.
 *
 *
 * Basic idea:
 *
 *
 *  * **Packed Blocks and VInt Blocks**:
 *
 * In packed blocks, integers are encoded with the same bit width ([packed][PackedInts]): the block size (i.e. number of integers inside block) is fixed (currently 128).
 * Additionally blocks that are all the same value are encoded in an optimized way.
 *
 * In VInt blocks, integers are encoded as [VInt][DataOutput.writeVInt]: the block
 * size is variable.
 *  * **Block structure**:
 *
 * When the postings are long enough, Lucene101PostingsFormat will try to encode most
 * integer data as a packed block.
 *
 * Take a term with 259 documents as an example, the first 256 document ids are encoded as
 * two packed blocks, while the remaining 3 are encoded as one VInt block.
 *
 * Different kinds of data are always encoded separately into different packed blocks, but
 * may possibly be interleaved into the same VInt block.
 *
 * This strategy is applied to pairs: &lt;document number, frequency&gt;, &lt;position,
 * payload length&gt;, &lt;position, offset start, offset length&gt;, and &lt;position,
 * payload length, offsetstart, offset length&gt;.
 *  * **Skipdata**:
 *
 * Skipdata is interleaved with blocks on 2 levels. Level 0 skip data is interleaved
 * between every packed block. Level 1 skip data is interleaved between every 32 packed
 * blocks.
 *  * **Positions, Payloads, and Offsets**:
 *
 * A position is an integer indicating where the term occurs within one document. A payload
 * is a blob of metadata associated with current position. An offset is a pair of integers
 * indicating the tokenized start/end offsets for given term in current position: it is
 * essentially a specialized payload.
 *
 * When payloads and offsets are not omitted, numPositions==numPayloads==numOffsets
 * (assuming a null payload contributes one count). As mentioned in block structure, it is
 * possible to encode these three either combined or separately.
 *
 * In all cases, payloads and offsets are stored together. When encoded as a packed block,
 * position data is separated out as .pos, while payloads and offsets are encoded in .pay
 * (payload metadata will also be stored directly in .pay). When encoded as VInt blocks, all
 * these three are stored interleaved into the .pos (so is payload metadata).
 *
 * With this strategy, the majority of payload and offset data will be outside .pos file.
 * So for queries that require only position data, running on a full index with payloads and
 * offsets, this reduces disk pre-fetches.
 *
 *
 *
 * Files and detailed format:
 *
 *
 *  * `.tim`: [Term Dictionary](#Termdictionary)
 *  * `.tip`: [Term Index](#Termindex)
 *  * `.doc`: [Frequencies and Skip Data](#Frequencies)
 *  * `.pos`: [Positions](#Positions)
 *  * `.pay`: [Payloads and Offsets](#Payloads)
 *
 *
 * <a id="Termdictionary"></a>
 *
 * <dl>
 * <dd>**Term Dictionary**
 *
 * The .tim file contains the list of terms in each field along with per-term statistics
 * (such as docfreq) and pointers to the frequencies, positions, payload and skip data in the
 * .doc, .pos, and .pay files. See [Lucene90BlockTreeTermsWriter] for more details on
 * the format.
 *
 * NOTE: The term dictionary can plug into different postings implementations: the postings
 * writer/reader are actually responsible for encoding and decoding the PostingsHeader and
 * TermMetadata sections described here:
 *
 *  * PostingsHeader --&gt; Header, PackedBlockSize
 *  * TermMetadata --&gt; (DocFPDelta|SingletonDocID), PosFPDelta, PosVIntBlockFPDelta,
 * PayFPDelta
 *  * Header, --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * PackedBlockSize, SingletonDocID --&gt; [VInt][DataOutput.writeVInt]
 *  * DocFPDelta, PosFPDelta, PayFPDelta, PosVIntBlockFPDelta --&gt; [             ][DataOutput.writeVLong]
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * Notes:
 *
 *  * Header is a [IndexHeader][CodecUtil.writeIndexHeader] storing the version
 * information for the postings.
 *  * PackedBlockSize is the fixed block size for packed blocks. In packed block, bit width
 * is determined by the largest integer. Smaller block size result in smaller variance
 * among width of integers hence smaller indexes. Larger block size result in more
 * efficient bulk i/o hence better acceleration. This value should always be a multiple
 * of 64, currently fixed as 128 as a tradeoff. It is also the skip interval used to
 * accelerate [org.apache.lucene.index.PostingsEnum.advance].
 *  * DocFPDelta determines the position of this term's TermFreqs within the .doc file. In
 * particular, it is the difference of file offset between this term's data and previous
 * term's data (or zero, for the first term in the block).On disk it is stored as the
 * difference from previous value in sequence.
 *  * PosFPDelta determines the position of this term's TermPositions within the .pos file.
 * While PayFPDelta determines the position of this term's &lt;TermPayloads,
 * TermOffsets&gt; within the .pay file. Similar to DocFPDelta, it is the difference
 * between two file positions (or neglected, for fields that omit payloads and offsets).
 *  * PosVIntBlockFPDelta determines the position of this term's last TermPosition in last
 * pos packed block within the .pos file. It is synonym for PayVIntBlockFPDelta or
 * OffsetVIntBlockFPDelta. This is actually used to indicate whether it is necessary to
 * load following payloads and offsets from .pos instead of .pay. Every time a new block
 * of positions are to be loaded, the PostingsReader will use this value to check
 * whether current block is packed format or VInt. When packed format, payloads and
 * offsets are fetched from .pay, otherwise from .pos. (this value is neglected when
 * total number of positions i.e. totalTermFreq is less or equal to PackedBlockSize).
 *  * SingletonDocID is an optimization when a term only appears in one document. In this
 * case, instead of writing a file pointer to the .doc file (DocFPDelta), and then a
 * VIntBlock at that location, the single document ID is written to the term dictionary.
 *
</dd></dl> *
 *
 * <a id="Termindex"></a>
 *
 * <dl>
 * <dd>**Term Index**
 *
 * The .tip file contains an index into the term dictionary, so that it can be accessed
 * randomly. See [Lucene90BlockTreeTermsWriter] for more details on the format.
</dd></dl> *
 *
 * <a id="Frequencies"></a>
 *
 * <dl>
 * <dd>**Frequencies and Skip Data**
 *
 * The .doc file contains the lists of documents which contain each term, along with the
 * frequency of the term in that document (except when frequencies are omitted: [       ][IndexOptions.DOCS]). Skip data is saved at the end of each term's postings. The skip data
 * is saved once for the entire postings list.
 *
 *  * docFile(.doc) --&gt; Header, &lt;TermFreqs&gt;<sup>TermCount</sup>, Footer
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * TermFreqs --&gt; &lt;PackedBlock32&gt; <sup>PackedDocBlockNum/32</sup>, VIntBlock
 *  * PackedBlock32 --&gt; Level1SkipData, &lt;PackedBlock&gt; <sup>32</sup>
 *  * PackedBlock --&gt; Level0SkipData, PackedDocDeltaBlock, PackedFreqBlock
 *  * VIntBlock --&gt;
 * &lt;DocDelta[,Freq]&gt;<sup>DocFreq-PackedBlockSize*PackedDocBlockNum</sup>
 *  * Level1SkipData --&gt; DocDelta, DocFPDelta, Skip1NumBytes, ImpactLength, Impacts,
 * PosFPDelta, NextPosUpto, PayFPDelta, NextPayByteUpto
 *  * Level0SkipData --&gt; Skip0NumBytes, DocDelta, DocFPDelta, PackedBlockLength,
 * ImpactLength, Impacts, PosFPDelta, NextPosUpto, PayFPDelta, NextPayByteUpto
 *  * PackedFreqBlock --&gt; [PackedInts], uses patching
 *  * PackedDocDeltaBlock --&gt; [PackedInts], does not use patching
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * Notes:
 *
 *  * PackedDocDeltaBlock is theoretically generated from two steps:
 *
 *  1. Calculate the difference between each document number and previous one, and get
 * a d-gaps list (for the first document, use absolute value);
 *  1. For those d-gaps from first one to
 * PackedDocBlockNum*PackedBlockSize<sup>th</sup>, separately encode as packed
 * blocks.
 *
 * If frequencies are not omitted, PackedFreqBlock will be generated without d-gap step.
 *  * VIntBlock stores remaining d-gaps (along with frequencies when possible) with a
 * format that encodes DocDelta and Freq:
 *
 * DocDelta: if frequencies are indexed, this determines both the document number and
 * the frequency. In particular, DocDelta/2 is the difference between this document
 * number and the previous document number (or zero when this is the first document in a
 * TermFreqs). When DocDelta is odd, the frequency is one. When DocDelta is even, the
 * frequency is read as another VInt. If frequencies are omitted, DocDelta contains the
 * gap (not multiplied by 2) between document numbers and no frequency information is
 * stored.
 *
 * For example, the TermFreqs for a term which occurs once in document seven and
 * three times in document eleven, with frequencies indexed, would be the following
 * sequence of VInts:
 *
 * 15, 8, 3
 *
 * If frequencies were omitted ([IndexOptions.DOCS]) it would be this sequence
 * of VInts instead:
 *
 * 7,4
 *  * PackedDocBlockNum is the number of packed blocks for current term's docids or
 * frequencies. In particular, PackedDocBlockNum = floor(DocFreq/PackedBlockSize)
 *  * On skip data, DocDelta is the delta between the last doc of the previous block - or
 * -1 if there is no previous block - and the last doc of this block. This helps know by
 * how much the doc ID should be incremented in case the block gets skipped.
 *  * Skip0Length is the length of skip data at level 0. Encoding it is useful when skip
 * data is never needed to quickly skip over skip data, e.g. if only using nextDoc(). It
 * is also used when only the first fields of skip data are needed, in order to skip
 * over remaining fields without reading them.
 *  * ImpactLength and Impacts are only stored if frequencies are indexed.
 *  * Since positions and payloads are also block encoded, the skip should skip to related
 * block first, then fetch the values according to in-block offset. PosFPSkip and
 * PayFPSkip record the file offsets of related block in .pos and .pay, respectively.
 * While PosBlockOffset indicates which value to fetch inside the related block
 * (PayBlockOffset is unnecessary since it is always equal to PosBlockOffset). Same as
 * DocFPSkip, the file offsets are relative to the start of current term's TermFreqs,
 * and stored as a difference sequence.
 *  * PayByteUpto indicates the start offset of the current payload. It is equivalent to
 * the sum of the payload lengths in the current block up to PosBlockOffset
 *  * ImpactLength is the total length of CompetitiveFreqDelta and CompetitiveNormDelta
 * pairs. CompetitiveFreqDelta and CompetitiveNormDelta are used to safely skip score
 * calculation for uncompetitive documents; See [             ] for more details.
 *
</dd></dl> *
 *
 * <a id="Positions"></a>
 *
 * <dl>
 * <dd>**Positions**
 *
 * The .pos file contains the lists of positions that each term occurs at within documents.
 * It also sometimes stores part of payloads and offsets for speedup.
 *
 *  * PosFile(.pos) --&gt; Header, &lt;TermPositions&gt; <sup>TermCount</sup>, Footer
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * TermPositions --&gt; &lt;PackedPosDeltaBlock&gt; <sup>PackedPosBlockNum</sup>,
 * VIntBlock
 *  * VIntBlock --&gt; &lt;PositionDelta[, PayloadLength], PayloadData, OffsetDelta,
 * OffsetLength&gt;<sup>PosVIntCount</sup>
 *  * PackedPosDeltaBlock --&gt; [PackedInts]
 *  * PositionDelta, OffsetDelta, OffsetLength --&gt; [VInt][DataOutput.writeVInt]
 *  * PayloadData --&gt; [byte][DataOutput.writeByte]<sup>PayLength</sup>
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * Notes:
 *
 *  * TermPositions are order by term (terms are implicit, from the term dictionary), and
 * position values for each term document pair are incremental, and ordered by document
 * number.
 *  * PackedPosBlockNum is the number of packed blocks for current term's positions,
 * payloads or offsets. In particular, PackedPosBlockNum =
 * floor(totalTermFreq/PackedBlockSize)
 *  * PosVIntCount is the number of positions encoded as VInt format. In particular,
 * PosVIntCount = totalTermFreq - PackedPosBlockNum*PackedBlockSize
 *  * The procedure how PackedPosDeltaBlock is generated is the same as PackedDocDeltaBlock
 * in chapter [Frequencies and Skip Data](#Frequencies).
 *  * PositionDelta is, if payloads are disabled for the term's field, the difference
 * between the position of the current occurrence in the document and the previous
 * occurrence (or zero, if this is the first occurrence in this document). If payloads
 * are enabled for the term's field, then PositionDelta/2 is the difference between the
 * current and the previous position. If payloads are enabled and PositionDelta is odd,
 * then PayloadLength is stored, indicating the length of the payload at the current
 * term position.
 *  * For example, the TermPositions for a term which occurs as the fourth term in one
 * document, and as the fifth and ninth term in a subsequent document, would be the
 * following sequence of VInts (payloads disabled):
 *
 * 4, 5, 4
 *  * PayloadData is metadata associated with the current term position. If PayloadLength
 * is stored at the current position, then it indicates the length of this payload. If
 * PayloadLength is not stored, then this payload has the same length as the payload at
 * the previous position.
 *  * OffsetDelta/2 is the difference between this position's startOffset from the previous
 * occurrence (or zero, if this is the first occurrence in this document). If
 * OffsetDelta is odd, then the length (endOffset-startOffset) differs from the previous
 * occurrence and an OffsetLength follows. Offset data is only written for [             ][IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS].
 *
</dd></dl> *
 *
 * <a id="Payloads"></a>
 *
 * <dl>
 * <dd>**Payloads and Offsets**
 *
 * The .pay file will store payloads and offsets associated with certain term-document
 * positions. Some payloads and offsets will be separated out into .pos file, for performance
 * reasons.
 *
 *  * PayFile(.pay): --&gt; Header, &lt;TermPayloads, TermOffsets&gt;
 * <sup>TermCount</sup>, Footer
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * TermPayloads --&gt; &lt;PackedPayLengthBlock, SumPayLength, PayData&gt;
 * <sup>PackedPayBlockNum</sup>
 *  * TermOffsets --&gt; &lt;PackedOffsetStartDeltaBlock, PackedOffsetLengthBlock&gt;
 * <sup>PackedPayBlockNum</sup>
 *  * PackedPayLengthBlock, PackedOffsetStartDeltaBlock, PackedOffsetLengthBlock --&gt;
 * [PackedInts]
 *  * SumPayLength --&gt; [VInt][DataOutput.writeVInt]
 *  * PayData --&gt; [byte][DataOutput.writeByte]<sup>SumPayLength</sup>
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * Notes:
 *
 *  * The order of TermPayloads/TermOffsets will be the same as TermPositions, note that
 * part of payload/offsets are stored in .pos.
 *  * The procedure how PackedPayLengthBlock and PackedOffsetLengthBlock are generated is
 * the same as PackedFreqBlock in chapter [Frequencies and Skip
 * Data](#Frequencies). While PackedStartDeltaBlock follows a same procedure as
 * PackedDocDeltaBlock.
 *  * PackedPayBlockNum is always equal to PackedPosBlockNum, for the same term. It is also
 * synonym for PackedOffsetBlockNum.
 *  * SumPayLength is the total length of payloads written within one block, should be the
 * sum of PayLengths in one packed block.
 *  * PayLength in PackedPayLengthBlock is the length of each payload associated with the
 * current position.
 *
</dd></dl> *
 *
 * @lucene.experimental
 */
class Lucene101PostingsFormat @JvmOverloads constructor(
    minTermBlockSize: Int = Lucene90BlockTreeTermsWriter.DEFAULT_MIN_BLOCK_SIZE,
    maxTermBlockSize: Int = Lucene90BlockTreeTermsWriter.DEFAULT_MAX_BLOCK_SIZE,
    version: Int = VERSION_CURRENT
) : PostingsFormat("Lucene101") {
    private val version: Int
    private val minTermBlockSize: Int
    private val maxTermBlockSize: Int

    /** Expert constructor that allows setting the version.  */
    /**
     * Creates `Lucene101PostingsFormat` with custom values for `minBlockSize` and `maxBlockSize` passed to block terms dictionary.
     *
     * @see      Lucene90BlockTreeTermsWriter.Lucene90BlockTreeTermsWriter
     */
    /** Creates `Lucene101PostingsFormat` with default settings.  */
    init {
        require(!(version < VERSION_START || version > VERSION_CURRENT)) { "Version out of range: " + version }
        this.version = version
        Lucene90BlockTreeTermsWriter.validateSettings(minTermBlockSize, maxTermBlockSize)
        this.minTermBlockSize = minTermBlockSize
        this.maxTermBlockSize = maxTermBlockSize
    }

    @Throws(IOException::class)
    public override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        val postingsWriter: PostingsWriterBase = Lucene101PostingsWriter(state, version)
        var success = false
        try {
            val ret: FieldsConsumer =
                Lucene90BlockTreeTermsWriter(
                    state, postingsWriter, minTermBlockSize, maxTermBlockSize
                )
            success = true
            return ret
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(postingsWriter)
            }
        }
    }

    @Throws(IOException::class)
    public override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        val postingsReader: PostingsReaderBase = Lucene101PostingsReader(state)
        var success = false
        try {
            val ret: FieldsProducer = Lucene90BlockTreeTermsReader(postingsReader, state)
            success = true
            return ret
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(postingsReader)
            }
        }
    }

    /**
     * Holds all state required for [Lucene101PostingsReader] to produce a [ ] without re-seeking the terms dict.
     *
     * @lucene.internal
     */
    class IntBlockTermState : BlockTermState() {
        /** file pointer to the start of the doc ids enumeration, in [.DOC_EXTENSION] file  */
        var docStartFP: Long = 0

        /** file pointer to the start of the positions enumeration, in [.POS_EXTENSION] file  */
        var posStartFP: Long = 0

        /** file pointer to the start of the payloads enumeration, in [.PAY_EXTENSION] file  */
        var payStartFP: Long = 0

        /**
         * file offset for the last position in the last block, if there are more than [ ][ForUtil.BLOCK_SIZE] positions; otherwise -1
         *
         *
         * One might think to use total term frequency to track how many positions are left to read
         * as we decode the blocks, and decode the last block differently when num_left_positions &lt;
         * BLOCK_SIZE. Unfortunately this won't work since the tracking will be messed up when we skip
         * blocks as the skipper will only tell us new position offset (start of block) and number of
         * positions to skip for that block, without telling us how many positions it has skipped.
         */
        var lastPosBlockOffset: Long

        /**
         * docid when there is a single pulsed posting, otherwise -1. freq is always implicitly
         * totalTermFreq in this case.
         */
        var singletonDocID: Int

        /** Sole constructor.  */
        init {
            lastPosBlockOffset = -1
            singletonDocID = -1
        }

        public override fun clone(): IntBlockTermState {
            val other = IntBlockTermState()
            other.copyFrom(this)
            return other
        }

        public override fun copyFrom(_other: TermState) {
            super.copyFrom(_other)
            val other = _other as IntBlockTermState
            docStartFP = other.docStartFP
            posStartFP = other.posStartFP
            payStartFP = other.payStartFP
            lastPosBlockOffset = other.lastPosBlockOffset
            singletonDocID = other.singletonDocID
        }

        override fun toString(): String {
            return (super.toString()
                    + " docStartFP="
                    + docStartFP
                    + " posStartFP="
                    + posStartFP
                    + " payStartFP="
                    + payStartFP
                    + " lastPosBlockOffset="
                    + lastPosBlockOffset
                    + " singletonDocID="
                    + singletonDocID)
        }
    }

    companion object {
        /** Filename extension for some small metadata about how postings are encoded.  */
        const val META_EXTENSION: String = "psm"

        /**
         * Filename extension for document number, frequencies, and skip data. See chapter: [Frequencies and Skip Data](#Frequencies)
         */
        const val DOC_EXTENSION: String = "doc"

        /** Filename extension for positions. See chapter: [Positions](#Positions)  */
        const val POS_EXTENSION: String = "pos"

        /**
         * Filename extension for payloads and offsets. See chapter: [Payloads and
 * Offsets](#Payloads)
         */
        const val PAY_EXTENSION: String = "pay"

        /** Size of blocks.  */
        val BLOCK_SIZE: Int = ForUtil.BLOCK_SIZE

        val BLOCK_MASK: Int = BLOCK_SIZE - 1

        /** We insert skip data on every block and every SKIP_FACTOR=32 blocks.  */
        const val LEVEL1_FACTOR: Int = 32

        /** Total number of docs covered by level 1 skip data: 32 * 128 = 4,096  */
        val LEVEL1_NUM_DOCS: Int = LEVEL1_FACTOR * BLOCK_SIZE

        val LEVEL1_MASK: Int = LEVEL1_NUM_DOCS - 1

        val impactsEnumImpl: KClass<out ImpactsEnum>
            /**
             * Return the class that implements [ImpactsEnum] in this [PostingsFormat]. This is
             * internally used to help the JVM make good inlining decisions.
             *
             * @lucene.internal
             */
            get() = Lucene101PostingsReader.BlockPostingsEnum::class

        const val TERMS_CODEC: String = "Lucene90PostingsWriterTerms"
        const val META_CODEC: String = "Lucene101PostingsWriterMeta"
        const val DOC_CODEC: String = "Lucene101PostingsWriterDoc"
        const val POS_CODEC: String = "Lucene101PostingsWriterPos"
        const val PAY_CODEC: String = "Lucene101PostingsWriterPay"

        const val VERSION_START: Int = 0

        /**
         * Version that started encoding dense blocks as bit sets. Note: the old format is a subset of the
         * new format, so Lucene101PostingsReader is able to read the old format without checking the
         * version.
         */
        const val VERSION_DENSE_BLOCKS_AS_BITSETS: Int = 1

        val VERSION_CURRENT: Int = VERSION_DENSE_BLOCKS_AS_BITSETS
    }
}
