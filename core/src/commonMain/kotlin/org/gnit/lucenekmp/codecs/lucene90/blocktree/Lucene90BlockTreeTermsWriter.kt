package org.gnit.lucenekmp.codecs.lucene90.blocktree

import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsWriterBase
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.ToStringUtils
import org.gnit.lucenekmp.util.compress.LZ4
import org.gnit.lucenekmp.util.compress.LZ4.HighCompressionHashTable
import org.gnit.lucenekmp.util.compress.LowercaseAsciiCompression
import org.gnit.lucenekmp.util.fst.ByteSequenceOutputs
import org.gnit.lucenekmp.util.fst.BytesRefFSTEnum
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.FSTCompiler.Companion.getOnHeapReaderWriter
import org.gnit.lucenekmp.util.fst.Util
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min


/*
 TODO:

   - Currently there is a one-to-one mapping of indexed
     term to term block, but we could decouple the two, ie,
     put more terms into the index than there are blocks.
     The index would take up more RAM but then it'd be able
     to avoid seeking more often and could make PK/FuzzyQ
     faster if the additional indexed terms could store
     the offset into the terms block.

   - The blocks are not written in true depth-first
     order, meaning if you just next() the file pointer will
     sometimes jump backwards.  For example, block foo* will
     be written before block f* because it finished before.
     This could possibly hurt performance if the terms dict is
     not hot, since OSs anticipate sequential file access.  We
     could fix the writer to re-order the blocks as a 2nd
     pass.

   - Each block encodes the term suffixes packed
     sequentially using a separate vInt per term, which is
     1) wasteful and 2) slow (must linear scan to find a
     particular suffix).  We should instead 1) make
     random-access array so we can directly access the Nth
     suffix, and 2) bulk-encode this array using bulk int[]
     codecs; then at search time we can binary search when
     we seek a particular term.
*/
/**
 * Block-based terms index and dictionary writer.
 *
 *
 * Writes terms dict and index, block-encoding (column stride) each term's metadata for each set
 * of terms between two index terms.
 *
 *
 * Files:
 *
 *
 *  * `.tim`: [Term Dictionary](#Termdictionary)
 *  * `.tmd`: [Term Metadata](#Termmetadata)
 *  * `.tip`: [Term Index](#Termindex)
 *
 *
 *
 * <a id="Termdictionary"></a>
 *
 * <h2>Term Dictionary</h2>
 *
 *
 * The .tim file contains the list of terms in each field along with per-term statistics (such as
 * docfreq) and per-term metadata (typically pointers to the postings list for that term in the
 * inverted index).
 *
 *
 * The .tim is arranged in blocks: with blocks containing a variable number of entries (by
 * default 25-48), where each entry is either a term or a reference to a sub-block.
 *
 *
 * NOTE: The term dictionary can plug into different postings implementations: the postings
 * writer/reader are actually responsible for encoding and decoding the Postings Metadata and Term
 * Metadata sections.
 *
 *
 *  * TermsDict (.tim) --&gt; Header, FieldDict<sup>NumFields</sup>, Footer
 *  * FieldDict --&gt; *PostingsHeader*, NodeBlock<sup>NumBlocks</sup>
 *  * NodeBlock --&gt; (OuterNode | InnerNode)
 *  * OuterNode --&gt; EntryCount, SuffixLength, Byte<sup>SuffixLength</sup>, StatsLength, &lt;
 * TermStats &gt;<sup>EntryCount</sup>, MetaLength,
 * &lt;*TermMetadata*&gt;<sup>EntryCount</sup>
 *  * InnerNode --&gt; EntryCount, SuffixLength[,Sub], Byte<sup>SuffixLength</sup>, StatsLength,
 * &lt; TermStats  &gt;<sup>EntryCount</sup>, MetaLength, &lt;*TermMetadata
 * * &gt;<sup>EntryCount</sup>
 *  * TermStats --&gt; DocFreq, TotalTermFreq
 *  * Header --&gt; [CodecHeader][CodecUtil.writeHeader]
 *  * EntryCount,SuffixLength,StatsLength,DocFreq,MetaLength --&gt; [       VInt][DataOutput.writeVInt]
 *  * TotalTermFreq --&gt; [VLong][DataOutput.writeVLong]
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 *
 * Notes:
 *
 *
 *  * Header is a [CodecHeader][CodecUtil.writeHeader] storing the version information for
 * the BlockTree implementation.
 *  * DocFreq is the count of documents which contain the term.
 *  * TotalTermFreq is the total number of occurrences of the term. This is encoded as the
 * difference between the total number of occurrences and the DocFreq.
 *  * PostingsHeader and TermMetadata are plugged into by the specific postings implementation:
 * these contain arbitrary per-file data (such as parameters or versioning information) and
 * per-term data (such as pointers to inverted files).
 *  * For inner nodes of the tree, every entry will steal one bit to mark whether it points to
 * child nodes(sub-block). If so, the corresponding TermStats and TermMetaData are omitted.
 *
 *
 *
 * <a id="Termmetadata"></a>
 *
 * <h2>Term Metadata</h2>
 *
 *
 * The .tmd file contains the list of term metadata (such as FST index metadata) and field level
 * statistics (such as sum of total term freq).
 *
 *
 *  * TermsMeta (.tmd) --&gt; Header, NumFields, &lt;FieldStats&gt;<sup>NumFields</sup>,
 * TermIndexLength, TermDictLength, Footer
 *  * FieldStats --&gt; FieldNumber, NumTerms, RootCodeLength, Byte<sup>RootCodeLength</sup>,
 * SumTotalTermFreq, SumDocFreq, DocCount, MinTerm, MaxTerm, IndexStartFP, FSTHeader,
 * *FSTMetadata*
 *  * Header,FSTHeader --&gt; [CodecHeader][CodecUtil.writeHeader]
 *  * TermIndexLength, TermDictLength --&gt; [Uint64][DataOutput.writeLong]
 *  * MinTerm,MaxTerm --&gt; [VInt][DataOutput.writeVInt] length followed by the byte[]
 *  * NumFields,FieldNumber,RootCodeLength,DocCount --&gt; [VInt][DataOutput.writeVInt]
 *  * NumTerms,SumTotalTermFreq,SumDocFreq,IndexStartFP --&gt; [       VLong][DataOutput.writeVLong]
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 *
 * Notes:
 *
 *
 *  * FieldNumber is the fields number from [FieldInfos]. (.fnm)
 *  * NumTerms is the number of unique terms for the field.
 *  * RootCode points to the root block for the field.
 *  * SumDocFreq is the total number of postings, the number of term-document pairs across the
 * entire field.
 *  * DocCount is the number of documents that have at least one posting for this field.
 *  * MinTerm, MaxTerm are the lowest and highest term in this field.
 *
 *
 * <a id="Termindex"></a>
 *
 * <h2>Term Index</h2>
 *
 *
 * The .tip file contains an index into the term dictionary, so that it can be accessed randomly.
 * The index is also used to determine when a given term cannot exist on disk (in the .tim file),
 * saving a disk seek.
 *
 *
 *  * TermsIndex (.tip) --&gt; Header, FSTIndex<sup>NumFields</sup>Footer
 *  * Header --&gt; [CodecHeader][CodecUtil.writeHeader]
 *
 *  * FSTIndex --&gt; [FST&amp;lt;byte[]&amp;gt;][FST]
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 *
 * Notes:
 *
 *
 *  * The .tip file contains a separate FST for each field. The FST maps a term prefix to the
 * on-disk block that holds all terms starting with that prefix. Each field's IndexStartFP
 * points to its FST.
 *  * It's possible that an on-disk block would contain too many terms (more than the allowed
 * maximum (default: 48)). When this happens, the block is sub-divided into new blocks (called
 * "floor blocks"), and then the output in the FST for the block's prefix encodes the leading
 * byte of each sub-block, and its file pointer.
 *
 *
 * @see Lucene90BlockTreeTermsReader
 *
 * @lucene.experimental
 */
class Lucene90BlockTreeTermsWriter(
    state: SegmentWriteState,
    postingsWriter: PostingsWriterBase,
    minItemsInBlock: Int,
    maxItemsInBlock: Int,
    version: Int
) : FieldsConsumer() {
    // public static boolean DEBUG = false;
    // public static boolean DEBUG2 = false;
    // private final static boolean SAVE_DOT_FILES = false;
    private val metaOut: IndexOutput
    private val termsOut: IndexOutput
    private val indexOut: IndexOutput
    val maxDoc: Int
    val minItemsInBlock: Int
    val maxItemsInBlock: Int
    val version: Int

    val postingsWriter: PostingsWriterBase
    val fieldInfos: FieldInfos

    private val fields: MutableList<ByteBuffersDataOutput> = ArrayList()

    /**
     * Create a new writer. The number of items (terms or sub-blocks) per block will aim to be between
     * minItemsPerBlock and maxItemsPerBlock, though in some cases the blocks may be smaller than the
     * min.
     */
    constructor(
        state: SegmentWriteState,
        postingsWriter: PostingsWriterBase,
        minItemsInBlock: Int,
        maxItemsInBlock: Int
    ) : this(
        state,
        postingsWriter,
        minItemsInBlock,
        maxItemsInBlock,
        Lucene90BlockTreeTermsReader.VERSION_CURRENT
    )

    @Throws(IOException::class)
    override fun write(fields: Fields, norms: NormsProducer) {
        // if (DEBUG) System.out.println("\nBTTW.write seg=" + segment);

        var lastField: String? = null
        for (field in fields) {
            require(lastField == null || lastField < field)
            lastField = field

            // if (DEBUG) System.out.println("\nBTTW.write seg=" + segment + " field=" + field);
            val terms: Terms? = fields.terms(field)
            if (terms == null) {
                continue
            }

            val termsEnum: TermsEnum = terms.iterator()
            val termsWriter: TermsWriter = this.TermsWriter(fieldInfos.fieldInfo(field)!!)
            while (true) {
                val term: BytesRef? = termsEnum.next()

                // if (DEBUG) System.out.println("BTTW: next term " + term);
                if (term == null) {
                    break
                }

                // if (DEBUG) System.out.println("write field=" + fieldInfo.name + " term=" +
                // ToStringUtils.bytesRefToString(term));
                termsWriter.write(term, termsEnum, norms)
            }

            termsWriter.finish()

            // if (DEBUG) System.out.println("\nBTTW.write done seg=" + segment + " field=" + field);
        }
    }

    private open class PendingEntry protected constructor(val isTerm: Boolean)

    private class PendingTerm(term: BytesRef, state: BlockTermState) : PendingEntry(true) {
        val termBytes: ByteArray = ByteArray(term.length)

        // stats + metadata
        val state: BlockTermState

        init {
            System.arraycopy(term.bytes, term.offset, termBytes, 0, term.length)
            this.state = state
        }

        override fun toString(): String {
            return "TERM: " + ToStringUtils.bytesRefToString(termBytes)
        }
    }

    private inner class PendingBlock(
        val prefix: BytesRef,
        val fp: Long,
        val hasTerms: Boolean,
        val isFloor: Boolean,
        val floorLeadByte: Int,
        var subIndices: MutableList<FST<BytesRef>>?
    ) : PendingEntry(false) {
        var index: FST<BytesRef>? = null


        override fun toString(): String {
            return "BLOCK: prefix=" + ToStringUtils.bytesRefToString(prefix)
        }

        @Throws(IOException::class)
        fun compileIndex(
            blocks: MutableList<PendingBlock>,
            scratchBytes: ByteBuffersDataOutput,
            scratchIntsRef: IntsRefBuilder
        ) {
            require(
                (isFloor && blocks.size > 1) || (!isFloor && blocks.size == 1)
            ) { "isFloor=$isFloor blocks=$blocks" }
            require(this == blocks[0])

            require(scratchBytes.size() == 0L)

            // write the leading vLong in MSB order for better outputs sharing in the FST
            if (version >= Lucene90BlockTreeTermsReader.VERSION_MSB_VLONG_OUTPUT) {
                writeMSBVLong(encodeOutput(fp, hasTerms, isFloor), scratchBytes)
            } else {
                scratchBytes.writeVLong(encodeOutput(fp, hasTerms, isFloor))
            }
            if (isFloor) {
                scratchBytes.writeVInt(blocks.size - 1)
                for (i in 1..<blocks.size) {
                    val sub = blocks[i]
                    require(sub.floorLeadByte != -1)
                    // if (DEBUG) {
                    //  System.out.println("    write floorLeadByte=" +
                    // Integer.toHexString(sub.floorLeadByte&0xff));
                    // }
                    scratchBytes.writeByte(sub.floorLeadByte.toByte())
                    require(sub.fp > fp)
                    scratchBytes.writeVLong((sub.fp - fp) shl 1 or (if (sub.hasTerms) 1 else 0).toLong())
                }
            }

            var estimateSize: Long = prefix.length.toLong()
            for (block in blocks) {
                if (block.subIndices != null) {
                    for (subIndex in block.subIndices) {
                        estimateSize += subIndex.numBytes()
                    }
                }
            }
            val estimateBitsRequired: Int = PackedInts.bitsRequired(estimateSize)
            val pageBits = min(15, max(6, estimateBitsRequired))

            val outputs: ByteSequenceOutputs = ByteSequenceOutputs.getSingleton()
            val fstVersion: Int = if (version >= Lucene90BlockTreeTermsReader.VERSION_CURRENT) {
                FST.VERSION_CURRENT
            } else {
                FST.VERSION_90
            }
            val fstCompiler: FSTCompiler<BytesRef> =
                FSTCompiler.Builder(
                    FST.INPUT_TYPE.BYTE1,
                    outputs
                ) // Disable suffixes sharing for block tree index because suffixes are mostly dropped
                    // from the FST index and left in the term blocks.
                    .suffixRAMLimitMB(0.0)
                    .dataOutput(getOnHeapReaderWriter(pageBits))
                    .setVersion(fstVersion)
                    .build()
            // if (DEBUG) {
            //  System.out.println("  compile index for prefix=" + prefix);
            // }
            // indexBuilder.DEBUG = false;
            val bytes: ByteArray = scratchBytes.toArrayCopy()
            require(bytes.isNotEmpty())
            fstCompiler.add(Util.toIntsRef(prefix, scratchIntsRef), BytesRef(bytes, 0, bytes.size))
            scratchBytes.reset()

            // Copy over index for all sub-blocks
            for (block in blocks) {
                if (block.subIndices != null) {
                    for (subIndex in block.subIndices) {
                        append(fstCompiler, subIndex, scratchIntsRef)
                    }
                    block.subIndices = null
                }
            }

            index = FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader())

            require(subIndices == null)

            /*
      Writer w = new OutputStreamWriter(new FileOutputStream("out.dot"));
      Util.toDot(index, w, false, false);
      System.out.println("SAVED to out.dot");
      w.close();
      */
        }

        // TODO: maybe we could add bulk-add method to
        // Builder  Takes FST and unions it w/ current
        // FST.
        @Throws(IOException::class)
        fun append(
            fstCompiler: FSTCompiler<BytesRef>, subIndex: FST<BytesRef>, scratchIntsRef: IntsRefBuilder
        ) {
            val subIndexEnum: BytesRefFSTEnum<BytesRef> = BytesRefFSTEnum(subIndex)
            var indexEnt: BytesRefFSTEnum.InputOutput<BytesRef>?
            while ((subIndexEnum.next().also { indexEnt = it }) != null) {
                // if (DEBUG) {
                //  System.out.println("      add sub=" + indexEnt.input + " " + indexEnt.input + " output="
                // + indexEnt.output);
                // }
                fstCompiler.add(Util.toIntsRef(indexEnt!!.input!!, scratchIntsRef), indexEnt.output!!)
            }
        }
    }

    private val scratchBytes: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
    private val scratchIntsRef: IntsRefBuilder = IntsRefBuilder()

    private class StatsWriter(private val out: DataOutput, private val hasFreqs: Boolean) {
        private var singletonCount = 0

        @Throws(IOException::class)
        fun add(df: Int, ttf: Long) {
            // Singletons (DF==1, TTF==1) are run-length encoded
            if (df == 1 && (!hasFreqs || ttf == 1L)) {
                singletonCount++
            } else {
                finish()
                out.writeVInt(df shl 1)
                if (hasFreqs) {
                    out.writeVLong(ttf - df)
                }
            }
        }

        @Throws(IOException::class)
        fun finish() {
            if (singletonCount > 0) {
                out.writeVInt(((singletonCount - 1) shl 1) or 1)
                singletonCount = 0
            }
        }
    }

    internal inner class TermsWriter(private val fieldInfo: FieldInfo) {
        private var numTerms: Long = 0
        val docsSeen: FixedBitSet
        var sumTotalTermFreq: Long = 0
        var sumDocFreq: Long = 0

        // Records index into pending where the current prefix at that
        // length "started"; for example, if current term starts with 't',
        // startsByPrefix[0] is the index into pending for the first
        // term/sub-block starting with 't'.  We use this to figure out when
        // to write a new block:
        private val lastTerm: BytesRefBuilder = BytesRefBuilder()
        private var prefixStarts = IntArray(8)

        // Pending stack of terms and blocks.  As terms arrive (in sorted order)
        // we append to this stack, and once the top of the stack has enough
        // terms starting with a common prefix, we write a new block with
        // those terms and replace those terms in the stack with a new block:
        private val pending: MutableList<PendingEntry> = ArrayList()

        // Reused in writeBlocks:
        private val newBlocks: MutableList<PendingBlock> = ArrayList()

        private var firstPendingTerm: PendingTerm? = null
        private var lastPendingTerm: PendingTerm? = null

        /** Writes the top count entries in pending, using prevTerm to compute the prefix.  */
        @Throws(IOException::class)
        fun writeBlocks(prefixLength: Int, count: Int) {
            require(count > 0)

            // if (DEBUG2) {
            //  BytesRef br = new BytesRef(lastTerm.bytes());
            //  br.length = prefixLength;
            //  System.out.println("writeBlocks: seg=" + segment + " prefix=" +
            // ToStringUtils.bytesRefToString(br) + " count=" + count);
            // }

            // Root block better write all remaining pending entries:
            require(prefixLength > 0 || count == pending.size)

            var lastSuffixLeadLabel = -1

            // True if we saw at least one term in this block (we record if a block
            // only points to sub-blocks in the terms index so we can avoid seeking
            // to it when we are looking for a term):
            var hasTerms = false
            var hasSubBlocks = false

            val start = pending.size - count
            val end = pending.size
            var nextBlockStart = start
            var nextFloorLeadLabel = -1

            for (i in start..<end) {
                val ent = pending[i]

                val suffixLeadLabel: Int

                if (ent.isTerm) {
                    val term = ent as PendingTerm
                    if (term.termBytes.size == prefixLength) {
                        // Suffix is 0, i.e. prefix 'foo' and term is
                        // 'foo' so the term has empty string suffix
                        // in this block
                        require(
                            lastSuffixLeadLabel == -1
                        ) { "i=$i lastSuffixLeadLabel=$lastSuffixLeadLabel" }
                        suffixLeadLabel = -1
                    } else {
                        suffixLeadLabel = term.termBytes[prefixLength].toInt() and 0xff
                    }
                } else {
                    val block = ent as PendingBlock
                    require(block.prefix.length > prefixLength)
                    suffixLeadLabel = (block.prefix.bytes[block.prefix.offset + prefixLength] and 0xff.toByte()).toInt()
                }

                // if (DEBUG) System.out.println("  i=" + i + " ent=" + ent + " suffixLeadLabel=" +
                // suffixLeadLabel);
                if (suffixLeadLabel != lastSuffixLeadLabel) {
                    val itemsInBlock = i - nextBlockStart
                    if (itemsInBlock >= minItemsInBlock && end - nextBlockStart > maxItemsInBlock) {
                        // The count is too large for one block, so we must break it into "floor" blocks, where
                        // we record
                        // the leading label of the suffix of the first term in each floor block, so at search
                        // time we can
                        // jump to the right floor block.  We just use a naive greedy segmenter here: make a new
                        // floor
                        // block as soon as we have at least minItemsInBlock.  This is not always best: it often
                        // produces
                        // a too-small block as the final block:
                        val isFloor = itemsInBlock < count
                        newBlocks.add(
                            writeBlock(
                                prefixLength,
                                isFloor,
                                nextFloorLeadLabel,
                                nextBlockStart,
                                i,
                                hasTerms,
                                hasSubBlocks
                            )
                        )

                        hasTerms = false
                        hasSubBlocks = false
                        nextFloorLeadLabel = suffixLeadLabel
                        nextBlockStart = i
                    }

                    lastSuffixLeadLabel = suffixLeadLabel
                }

                if (ent.isTerm) {
                    hasTerms = true
                } else {
                    hasSubBlocks = true
                }
            }

            // Write last block, if any:
            if (nextBlockStart < end) {
                val itemsInBlock = end - nextBlockStart
                val isFloor = itemsInBlock < count
                newBlocks.add(
                    writeBlock(
                        prefixLength,
                        isFloor,
                        nextFloorLeadLabel,
                        nextBlockStart,
                        end,
                        hasTerms,
                        hasSubBlocks
                    )
                )
            }

            require(!newBlocks.isEmpty())

            val firstBlock = newBlocks[0]

            require(firstBlock.isFloor || newBlocks.size == 1)

            firstBlock.compileIndex(newBlocks, scratchBytes, scratchIntsRef)

            // Remove slice from the top of the pending stack, that we just wrote:
            pending.subList(pending.size - count, pending.size).clear()

            // Append new block
            pending.add(firstBlock)

            newBlocks.clear()
        }

        private fun allEqual(b: ByteArray, startOffset: Int, endOffset: Int, value: Byte): Boolean {
            Objects.checkFromToIndex(startOffset, endOffset, b.size)
            for (i in startOffset..<endOffset) {
                if (b[i] != value) {
                    return false
                }
            }
            return true
        }

        /**
         * Writes the specified slice (start is inclusive, end is exclusive) from pending stack as a new
         * block. If isFloor is true, there were too many (more than maxItemsInBlock) entries sharing
         * the same prefix, and so we broke it into multiple floor blocks where we record the starting
         * label of the suffix of each floor block.
         */
        @Throws(IOException::class)
        private fun writeBlock(
            prefixLength: Int,
            isFloor: Boolean,
            floorLeadLabel: Int,
            start: Int,
            end: Int,
            hasTerms: Boolean,
            hasSubBlocks: Boolean
        ): PendingBlock {
            require(end > start)

            val startFP: Long = termsOut.getFilePointer()

            val hasFloorLeadLabel = isFloor && floorLeadLabel != -1

            val prefix = BytesRef(prefixLength + (if (hasFloorLeadLabel) 1 else 0))
            System.arraycopy(lastTerm.get().bytes, 0, prefix.bytes, 0, prefixLength)
            prefix.length = prefixLength

            // if (DEBUG2) System.out.println("    writeBlock field=" + fieldInfo.name + " prefix=" +
            // ToStringUtils.bytesRefToString(prefix) + " fp=" + startFP + " isFloor=" + isFloor +
            // " isLastInFloor=" + (end == pending.size()) + " floorLeadLabel=" + floorLeadLabel +
            // " start=" + start + " end=" + end + " hasTerms=" + hasTerms + " hasSubBlocks=" +
            // hasSubBlocks);

            // Write block header:
            val numEntries = end - start
            var code = numEntries shl 1
            if (end == pending.size) {
                // Last block:
                code = code or 1
            }
            termsOut.writeVInt(code)

            /*
      if (DEBUG) {
        System.out.println("  writeBlock " + (isFloor  "(floor) " : "") + "seg=" + segment + " pending.size()=" +
        pending.size() + " prefixLength=" + prefixLength + " indexPrefix=" + ToStringUtils.bytesRefToString(prefix) +
        " entCount=" + (end-start+1) + " startFP=" + startFP + (isFloor  (" floorLeadLabel=" + Integer.toHexString(floorLeadLabel)) : ""));
      }
      */

            // 1st pass: pack term suffix bytes into byte[] blob
            // TODO: cutover to bulk int codec... simple64

            // We optimize the leaf block case (block has only terms), writing a more
            // compact format in this case:
            val isLeafBlock = !hasSubBlocks

            // System.out.println("  isLeaf=" + isLeafBlock);
            val subIndices: MutableList<FST<BytesRef>>?

            var absolute = true

            if (isLeafBlock) {
                // Block contains only ordinary terms:
                subIndices = null
                val statsWriter =
                    StatsWriter(this.statsWriter, fieldInfo.getIndexOptions() !== IndexOptions.DOCS)
                for (i in start..<end) {
                    val ent = pending[i]
                    require(ent.isTerm) { "i=$i" }

                    val term = ent as PendingTerm

                    require(StringHelper.startsWith(term.termBytes, prefix)) { "$term prefix=$prefix" }
                    val state: BlockTermState = term.state
                    val suffix = term.termBytes.size - prefixLength

                    // if (DEBUG2) {
                    //  BytesRef suffixBytes = new BytesRef(suffix);
                    //  System.arraycopy(term.termBytes, prefixLength, suffixBytes.bytes, 0, suffix);
                    //  suffixBytes.length = suffix;
                    //  System.out.println("    write term suffix=" +
                    // ToStringUtils.bytesRefToString(suffixBytes));
                    // }

                    // For leaf block we write suffix straight
                    suffixLengthsWriter.writeVInt(suffix)
                    suffixWriter.append(term.termBytes, prefixLength, suffix)
                    require(floorLeadLabel == -1 || (term.termBytes[prefixLength].toInt() and 0xff) >= floorLeadLabel)

                    // Write term stats, to separate byte[] blob:
                    statsWriter.add(state.docFreq, state.totalTermFreq)

                    // Write term meta data
                    postingsWriter.encodeTerm(metaWriter, fieldInfo, state, absolute)
                    absolute = false
                }
                statsWriter.finish()
            } else {
                // Block has at least one prefix term or a sub block:
                subIndices = ArrayList()
                val statsWriter =
                    StatsWriter(this.statsWriter, fieldInfo.getIndexOptions() !== IndexOptions.DOCS)
                for (i in start..<end) {
                    val ent = pending[i]
                    if (ent.isTerm) {
                        val term = ent as PendingTerm

                        require(
                            StringHelper.startsWith(
                                term.termBytes,
                                prefix
                            )
                        ) { "$term prefix=$prefix" }
                        val state: BlockTermState = term.state
                        val suffix = term.termBytes.size - prefixLength

                        // if (DEBUG2) {
                        //  BytesRef suffixBytes = new BytesRef(suffix);
                        //  System.arraycopy(term.termBytes, prefixLength, suffixBytes.bytes, 0, suffix);
                        //  suffixBytes.length = suffix;
                        //  System.out.println("      write term suffix=" +
                        // ToStringUtils.bytesRefToString(suffixBytes));
                        // }

                        // For non-leaf block we borrow 1 bit to record
                        // if entry is term or sub-block, and 1 bit to record if
                        // it's a prefix term.  Terms cannot be larger than ~32 KB
                        // so we won't run out of bits:
                        suffixLengthsWriter.writeVInt(suffix shl 1)
                        suffixWriter.append(term.termBytes, prefixLength, suffix)

                        // Write term stats, to separate byte[] blob:
                        statsWriter.add(state.docFreq, state.totalTermFreq)

                        // TODO: now that terms dict "sees" these longs,
                        // we can explore better column-stride encodings
                        // to encode all long[0]s for this block at
                        // once, all long[1]s, etc., e.g. using
                        // Simple64.  Alternatively, we could interleave
                        // stats + meta ... no reason to have them
                        // separate anymore:

                        // Write term meta data
                        postingsWriter.encodeTerm(metaWriter, fieldInfo, state, absolute)
                        absolute = false
                    } else {
                        val block = ent as PendingBlock
                        require(StringHelper.startsWith(block.prefix, prefix))
                        val suffix: Int = block.prefix.length - prefixLength
                        require(StringHelper.startsWith(block.prefix, prefix))

                        require(suffix > 0)

                        // For non-leaf block we borrow 1 bit to record
                        // if entry is term or sub-block:f
                        suffixLengthsWriter.writeVInt((suffix shl 1) or 1)
                        suffixWriter.append(block.prefix.bytes, prefixLength, suffix)

                        // if (DEBUG2) {
                        //  BytesRef suffixBytes = new BytesRef(suffix);
                        //  System.arraycopy(block.prefix.bytes, prefixLength, suffixBytes.bytes, 0, suffix);
                        //  suffixBytes.length = suffix;
                        //  System.out.println("      write sub-block suffix=" +
                        // ToStringUtils.bytesRefToString(suffixBytes) + " subFP=" + block.fp + " subCode=" +
                        // (startFP-block.fp) + " floor=" + block.isFloor);
                        // }
                        require(
                            floorLeadLabel == -1
                                    || (block.prefix.bytes[prefixLength] and 0xff.toByte()) >= floorLeadLabel
                        ) {
                            ("floorLeadLabel="
                                    + floorLeadLabel
                                    + " suffixLead="
                                    + (block.prefix.bytes[prefixLength] and 0xff.toByte()))
                        }
                        require(block.fp < startFP)

                        suffixLengthsWriter.writeVLong(startFP - block.fp)
                        subIndices.add(block.index!!)
                    }
                }
                statsWriter.finish()

                require(subIndices.isNotEmpty())
            }

            // Write suffixes byte[] blob to terms dict output, either uncompressed, compressed with LZ4
            // or with LowercaseAsciiCompression.
            var compressionAlg = CompressionAlgorithm.NO_COMPRESSION
            // If there are 2 suffix bytes or less per term, then we don't bother compressing as suffix
            // are unlikely what
            // makes the terms dictionary large, and it also tends to be frequently the case for dense IDs
            // like
            // auto-increment IDs, so not compressing in that case helps not hurt ID lookups by too much.
            // We also only start compressing when the prefix length is greater than 2 since blocks whose
            // prefix length is
            // 1 or 2 always all get visited when running a fuzzy query whose max number of edits is 2.
            if (suffixWriter.length() > 2L * numEntries && prefixLength > 2) {
                // LZ4 inserts references whenever it sees duplicate strings of 4 chars or more, so only try
                // it out if the
                // average suffix length is greater than 6.
                if (suffixWriter.length() > 6L * numEntries) {
                    if (compressionHashTable == null) {
                        compressionHashTable = HighCompressionHashTable()
                    }
                    LZ4.compress(
                        suffixWriter.bytes(), 0, suffixWriter.length(), spareWriter, compressionHashTable!!
                    )
                    if (spareWriter.size() < suffixWriter.length() - (suffixWriter.length() ushr 2)) {
                        // LZ4 saved more than 25%, go for it
                        compressionAlg = CompressionAlgorithm.LZ4
                    }
                }
                if (compressionAlg === CompressionAlgorithm.NO_COMPRESSION) {
                    spareWriter.reset()
                    if (spareBytes.size < suffixWriter.length()) {
                        spareBytes = ByteArray(ArrayUtil.oversize(suffixWriter.length(), 1))
                    }
                    if (LowercaseAsciiCompression.compress(
                            suffixWriter.bytes(), suffixWriter.length(), spareBytes, spareWriter
                        )
                    ) {
                        compressionAlg = CompressionAlgorithm.LOWERCASE_ASCII
                    }
                }
            }
            var token = (suffixWriter.length().toLong()) shl 3
            if (isLeafBlock) {
                token = token or 0x04L
            }
            token = token or compressionAlg.code.toLong()
            termsOut.writeVLong(token)
            if (compressionAlg === CompressionAlgorithm.NO_COMPRESSION) {
                termsOut.writeBytes(suffixWriter.bytes(), suffixWriter.length())
            } else {
                spareWriter.copyTo(termsOut)
            }
            suffixWriter.setLength(0)
            spareWriter.reset()

            // Write suffix lengths
            val numSuffixBytes: Int = Math.toIntExact(suffixLengthsWriter.size())
            spareBytes = ArrayUtil.growNoCopy(spareBytes, numSuffixBytes)
            suffixLengthsWriter.copyTo(ByteArrayDataOutput(spareBytes))
            suffixLengthsWriter.reset()
            if (allEqual(spareBytes, 1, numSuffixBytes, spareBytes[0])) {
                // Structured fields like IDs often have most values of the same length
                termsOut.writeVInt((numSuffixBytes shl 1) or 1)
                termsOut.writeByte(spareBytes[0])
            } else {
                termsOut.writeVInt(numSuffixBytes shl 1)
                termsOut.writeBytes(spareBytes, numSuffixBytes)
            }

            // Stats
            val numStatsBytes: Int = Math.toIntExact(statsWriter.size())
            termsOut.writeVInt(numStatsBytes)
            statsWriter.copyTo(termsOut)
            statsWriter.reset()

            // Write term meta data byte[] blob
            termsOut.writeVInt(metaWriter.size().toInt())
            metaWriter.copyTo(termsOut)
            metaWriter.reset()

            // if (DEBUG) {
            //   System.out.println("      fpEnd=" + out.getFilePointer());
            // }
            if (hasFloorLeadLabel) {
                // We already allocated to length+1 above:
                prefix.bytes[prefix.length++] = floorLeadLabel.toByte()
            }

            return this@Lucene90BlockTreeTermsWriter.PendingBlock(
                prefix,
                startFP,
                hasTerms,
                isFloor,
                floorLeadLabel,
                subIndices!!
            )
        }

        /** Writes one term's worth of postings.  */
        @Throws(IOException::class)
        fun write(text: BytesRef, termsEnum: TermsEnum, norms: NormsProducer) {
            /*
      if (DEBUG) {
        int[] tmp = new int[lastTerm.length];
        System.arraycopy(prefixStarts, 0, tmp, 0, tmp.length);
        System.out.println("BTTW: write term=" + ToStringUtils.bytesRefToString(text) + " prefixStarts=" + Arrays.toString(tmp) +
        " pending.size()=" + pending.size());
      }
      */

            val state: BlockTermState? = postingsWriter.writeTerm(text, termsEnum, docsSeen, norms)
            if (state != null) {
                require(state.docFreq != 0)
                require(
                    fieldInfo.getIndexOptions() === IndexOptions.DOCS
                            || state.totalTermFreq >= state.docFreq
                ) { "postingsWriter=$postingsWriter" }
                pushTerm(text)

                val term = PendingTerm(text, state)
                pending.add(term)

                // if (DEBUG) System.out.println("    add pending term = " + text + " pending.size()=" +
                // pending.size());
                sumDocFreq += state.docFreq
                sumTotalTermFreq += state.totalTermFreq
                numTerms++
                if (firstPendingTerm == null) {
                    firstPendingTerm = term
                }
                lastPendingTerm = term
            }
        }

        /** Pushes the new term to the top of the stack, and writes new blocks.  */
        @Throws(IOException::class)
        private fun pushTerm(text: BytesRef) {
            // Find common prefix between last term and current term:
            var prefixLength: Int =

                Arrays.mismatch(
                    lastTerm.bytes(),
                    0,
                    lastTerm.length(),
                    text.bytes,
                    text.offset,
                    text.offset + text.length
                )
            if (prefixLength == -1) { // Only happens for the first term, if it is empty
                require(lastTerm.length() == 0)
                prefixLength = 0
            }

            // if (DEBUG) System.out.println("  shared=" + pos + "  lastTerm.length=" + lastTerm.length);

            // Close the "abandoned" suffix now:
            for (i in lastTerm.length() - 1 downTo prefixLength) {
                // How many items on top of the stack share the current suffix
                // we are closing:

                val prefixTopSize = pending.size - prefixStarts[i]
                if (prefixTopSize >= minItemsInBlock) {
                    // if (DEBUG) System.out.println("pushTerm i=" + i + " prefixTopSize=" + prefixTopSize +
                    // " minItemsInBlock=" + minItemsInBlock);
                    writeBlocks(i + 1, prefixTopSize)
                    prefixStarts[i] -= prefixTopSize - 1
                }
            }

            if (prefixStarts.size < text.length) {
                prefixStarts = ArrayUtil.grow(prefixStarts, text.length)
            }

            // Init new tail:
            for (i in prefixLength..<text.length) {
                prefixStarts[i] = pending.size
            }

            lastTerm.copyBytes(text)
        }

        // Finishes all terms in this field
        @Throws(IOException::class)
        fun finish() {
            if (numTerms > 0) {
                // if (DEBUG) System.out.println("BTTW: finish prefixStarts=" +
                // Arrays.toString(prefixStarts));

                // Add empty term to force closing of all final blocks:

                pushTerm(BytesRef())

                // TODO: if pending.size() is already 1 with a non-zero prefix length
                // we can save writing a "degenerate" root block, but we have to
                // fix all the places that assume the root block's prefix is the empty string:
                pushTerm(BytesRef())
                writeBlocks(0, pending.size)

                // We better have one final "root" block:
                require(
                    pending.size == 1 && !pending[0].isTerm
                ) { "pending.size()=" + pending.size + " pending=" + pending }
                val root = pending[0] as PendingBlock
                require(root.prefix.length == 0)
                val rootCode: BytesRef = checkNotNull(root.index!!.getEmptyOutput())
                val metaOut = ByteBuffersDataOutput()
                fields.add(metaOut)

                metaOut.writeVInt(fieldInfo.number)
                metaOut.writeVLong(numTerms)
                metaOut.writeVInt(rootCode.length)
                metaOut.writeBytes(rootCode.bytes, rootCode.offset, rootCode.length)
                require(fieldInfo.getIndexOptions() !== IndexOptions.NONE)
                if (fieldInfo.getIndexOptions() !== IndexOptions.DOCS) {
                    metaOut.writeVLong(sumTotalTermFreq)
                }
                metaOut.writeVLong(sumDocFreq)
                metaOut.writeVInt(docsSeen.cardinality())
                writeBytesRef(metaOut, BytesRef(firstPendingTerm!!.termBytes))
                writeBytesRef(metaOut, BytesRef(lastPendingTerm!!.termBytes))
                metaOut.writeVLong(indexOut.getFilePointer())
                // Write FST to index
                root.index!!.save(metaOut, indexOut)

                // System.out.println("  write FST " + indexStartFP + " field=" + fieldInfo.name);

                /*
        if (DEBUG) {
          final String dotFileName = segment + "_" + fieldInfo.name + ".dot";
          Writer w = new OutputStreamWriter(new FileOutputStream(dotFileName));
          Util.toDot(root.index, w, false, false);
          System.out.println("SAVED to " + dotFileName);
          w.close();
        }
        */
            } else {
                require(
                    sumTotalTermFreq == 0L
                            || fieldInfo.getIndexOptions() === IndexOptions.DOCS && sumTotalTermFreq == -1L
                )
                require(sumDocFreq == 0L)
                require(docsSeen.cardinality() == 0)
            }
        }

        private val suffixLengthsWriter: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
        private val suffixWriter: BytesRefBuilder = BytesRefBuilder()
        private val statsWriter: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
        private val metaWriter: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
        private val spareWriter: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
        private var spareBytes: ByteArray = BytesRef.EMPTY_BYTES
        private var compressionHashTable: HighCompressionHashTable? = null

        init {
            require(fieldInfo.getIndexOptions() !== IndexOptions.NONE)
            docsSeen = FixedBitSet(maxDoc)
            postingsWriter.setField(fieldInfo)
        }
    }

    private var closed = false

    /** Expert constructor that allows configuring the version, used for bw tests.  */
    init {
        validateSettings(minItemsInBlock, maxItemsInBlock)

        this.minItemsInBlock = minItemsInBlock
        this.maxItemsInBlock = maxItemsInBlock
        require(
            !(version < Lucene90BlockTreeTermsReader.VERSION_START
                    || version > Lucene90BlockTreeTermsReader.VERSION_CURRENT)
        ) {
            ("Expected version in range ["
                    + Lucene90BlockTreeTermsReader.VERSION_START
                    + ", "
                    + Lucene90BlockTreeTermsReader.VERSION_CURRENT
                    + "], but got "
                    + version)
        }
        this.version = version

        this.maxDoc = state.segmentInfo.maxDoc()
        this.fieldInfos = state.fieldInfos
        this.postingsWriter = postingsWriter

        val termsName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name,
                state.segmentSuffix,
                Lucene90BlockTreeTermsReader.TERMS_EXTENSION
            )
        termsOut = state.directory.createOutput(termsName, state.context)
        var success = false
        var metaOut: IndexOutput? = null
        var indexOut: IndexOutput? = null
        try {
            CodecUtil.writeIndexHeader(
                termsOut,
                Lucene90BlockTreeTermsReader.TERMS_CODEC_NAME,
                version,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )

            val indexName: String =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name,
                    state.segmentSuffix,
                    Lucene90BlockTreeTermsReader.TERMS_INDEX_EXTENSION
                )
            indexOut = state.directory.createOutput(indexName, state.context)
            CodecUtil.writeIndexHeader(
                indexOut,
                Lucene90BlockTreeTermsReader.TERMS_INDEX_CODEC_NAME,
                version,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )

            // segment = state.segmentInfo.name;
            val metaName: String =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name,
                    state.segmentSuffix,
                    Lucene90BlockTreeTermsReader.TERMS_META_EXTENSION
                )
            metaOut = state.directory.createOutput(metaName, state.context)
            CodecUtil.writeIndexHeader(
                metaOut,
                Lucene90BlockTreeTermsReader.TERMS_META_CODEC_NAME,
                version,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )

            postingsWriter.init(metaOut, state) // have consumer write its format/header

            this.metaOut = metaOut
            this.indexOut = indexOut
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(metaOut, termsOut, indexOut)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (closed) {
            return
        }
        closed = true

        var success = false
        try {
            metaOut.writeVInt(fields.size)
            for (fieldMeta in fields) {
                fieldMeta.copyTo(metaOut)
            }
            CodecUtil.writeFooter(indexOut)
            metaOut.writeLong(indexOut.getFilePointer())
            CodecUtil.writeFooter(termsOut)
            metaOut.writeLong(termsOut.getFilePointer())
            CodecUtil.writeFooter(metaOut)
            success = true
        } finally {
            if (success) {
                IOUtils.close(metaOut, termsOut, indexOut, postingsWriter)
            } else {
                IOUtils.closeWhileHandlingException(metaOut, termsOut, indexOut, postingsWriter)
            }
        }
    }

    companion object {
        /**
         * Suggested default value for the `minItemsInBlock` parameter to [ ][.Lucene90BlockTreeTermsWriter].
         */
        const val DEFAULT_MIN_BLOCK_SIZE: Int = 25

        /**
         * Suggested default value for the `maxItemsInBlock` parameter to [ ][.Lucene90BlockTreeTermsWriter].
         */
        const val DEFAULT_MAX_BLOCK_SIZE: Int = 48

        /** Throws `IllegalArgumentException` if any of these settings is invalid.  */
        fun validateSettings(minItemsInBlock: Int, maxItemsInBlock: Int) {
            require(minItemsInBlock > 1) { "minItemsInBlock must be >= 2; got $minItemsInBlock" }
            require(minItemsInBlock <= maxItemsInBlock) {
                ("maxItemsInBlock must be >= minItemsInBlock; got maxItemsInBlock="
                        + maxItemsInBlock
                        + " minItemsInBlock="
                        + minItemsInBlock)
            }
            require(2 * (minItemsInBlock - 1) <= maxItemsInBlock) {
                ("maxItemsInBlock must be at least 2*(minItemsInBlock-1); got maxItemsInBlock="
                        + maxItemsInBlock
                        + " minItemsInBlock="
                        + minItemsInBlock)
            }
        }

        fun encodeOutput(fp: Long, hasTerms: Boolean, isFloor: Boolean): Long {
            require(fp < (1L shl 62))
            return ((fp shl 2)
                    or (if (hasTerms) Lucene90BlockTreeTermsReader.OUTPUT_FLAG_HAS_TERMS else 0)
                .toLong()
                    or (if (isFloor) Lucene90BlockTreeTermsReader.OUTPUT_FLAG_IS_FLOOR else 0).toLong())
        }

        /**
         * Encodes long value to variable length byte[], in MSB order. Use [ ][FieldReader.readMSBVLong] to decode.
         *
         *
         * Package private for testing
         */
        @Throws(IOException::class)
        fun writeMSBVLong(l: Long, scratchBytes: DataOutput) {
            var l = l
            require(l >= 0)
            // Keep zero bits on most significant byte to have more chance to get prefix bytes shared.
            // e.g. we expect 0x7FFF stored as [0x81, 0xFF, 0x7F] but not [0xFF, 0xFF, 0x40]
            val bytesNeeded: Int = (Long.SIZE_BITS - Long.numberOfLeadingZeros(l) - 1) / 7 + 1
            l = l shl Long.SIZE_BITS - bytesNeeded * 7
            for (i in 1..<bytesNeeded) {
                scratchBytes.writeByte((((l ushr 57) and 0x7FL) or 0x80L).toByte())
                l = l shl 7
            }
            scratchBytes.writeByte((((l ushr 57) and 0x7FL)).toByte())
        }

        @Throws(IOException::class)
        private fun writeBytesRef(out: DataOutput, bytes: BytesRef) {
            out.writeVInt(bytes.length)
            out.writeBytes(bytes.bytes, bytes.offset, bytes.length)
        }
    }
}
