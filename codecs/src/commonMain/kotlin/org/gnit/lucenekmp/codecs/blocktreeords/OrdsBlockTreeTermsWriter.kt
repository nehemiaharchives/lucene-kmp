package org.gnit.lucenekmp.codecs.blocktreeords

import okio.IOException
import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsWriterBase
import org.gnit.lucenekmp.codecs.blocktreeords.FSTOrdsOutputs.Output
import org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.ToStringUtils
import org.gnit.lucenekmp.util.fst.BytesRefFSTEnum
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.Util

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
 * This is just like {@link Lucene90BlockTreeTermsWriter}, except it also stores a version per term,
 * and adds a method to its TermsEnum implementation to seekExact only if the version is &gt;= the
 * specified version. The version is added to the terms index to avoid seeking if no term in the
 * block has a high enough version. The term blocks file is .tiv and the terms index extension is
 * .tipv.
 *
 * @lucene.experimental
 */
internal class OrdsBlockTreeTermsWriter(
  state: SegmentWriteState,
  private val postingsWriter: PostingsWriterBase,
  minItemsInBlock: Int,
  maxItemsInBlock: Int
) : FieldsConsumer() {

  private val out: IndexOutput
  private val indexOut: IndexOutput
  private val maxDoc: Int
  private val minItemsInBlock: Int
  private val maxItemsInBlock: Int
  private val fieldInfos: FieldInfos

  private data class FieldMetaData(
    val fieldInfo: FieldInfo,
    val rootCode: Output,
    val numTerms: Long,
    val indexStartFP: Long,
    val sumTotalTermFreq: Long,
    val sumDocFreq: Long,
    val docCount: Int,
    val minTerm: BytesRef,
    val maxTerm: BytesRef
  ) {
    init {
      assert(numTerms > 0) { "numTerms must be > 0" }
    }
  }

  private val fields: MutableList<FieldMetaData> = ArrayList()

  init {
    Lucene90BlockTreeTermsWriter.validateSettings(minItemsInBlock, maxItemsInBlock)
    maxDoc = state.segmentInfo.maxDoc()

    val termsFileName = IndexFileNames.segmentFileName(
      state.segmentInfo.name,
      state.segmentSuffix,
      TERMS_EXTENSION
    )
    out = state.directory.createOutput(termsFileName, state.context)

    var success = false
    var indexOutLocal: IndexOutput? = null
    try {
      fieldInfos = requireNotNull(state.fieldInfos)
      this.minItemsInBlock = minItemsInBlock
      this.maxItemsInBlock = maxItemsInBlock
      CodecUtil.writeIndexHeader(
        out,
        TERMS_CODEC_NAME,
        VERSION_CURRENT,
        state.segmentInfo.getId(),
        state.segmentSuffix
      )

      val termsIndexFileName = IndexFileNames.segmentFileName(
        state.segmentInfo.name,
        state.segmentSuffix,
        TERMS_INDEX_EXTENSION
      )
      indexOutLocal = state.directory.createOutput(termsIndexFileName, state.context)
      CodecUtil.writeIndexHeader(
        indexOutLocal,
        TERMS_INDEX_CODEC_NAME,
        VERSION_CURRENT,
        state.segmentInfo.getId(),
        state.segmentSuffix
      )

      postingsWriter.init(out, state)
      success = true
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(out, indexOutLocal)
      }
    }

    indexOut = requireNotNull(indexOutLocal)
  }

  @Throws(IOException::class)
  override fun write(fields: Fields, norms: NormsProducer?) {
    var lastField: String? = null
    for (field in fields) {
      if (lastField != null) {
        assert(lastField < field)
      }
      lastField = field

      val terms = fields.terms(field) ?: continue
      val termsEnum = terms.iterator()
      val fieldInfo = requireNotNull(fieldInfos.fieldInfo(field))
      val termsWriter = TermsWriter(fieldInfo)
      while (true) {
        val term = termsEnum.next() ?: break
        termsWriter.write(term, termsEnum, norms)
      }
      termsWriter.finish()
    }
  }

  private open class PendingEntry(val isTerm: Boolean)

  private class PendingTerm(term: BytesRef, val state: BlockTermState) : PendingEntry(true) {
    val termBytes: ByteArray = ByteArray(term.length)

    init {
      term.bytes.copyInto(termBytes, 0, term.offset, term.offset + term.length)
    }

    override fun toString(): String {
      return ToStringUtils.bytesRefToString(termBytes)
    }
  }

  private data class SubIndex(val index: FST<Output>, val termOrdStart: Long)

  private class PendingBlock(
    val prefix: BytesRef,
    val fp: Long,
    val hasTerms: Boolean,
    private val totalTermCount: Long,
    val isFloor: Boolean,
    val floorLeadByte: Int,
    var subIndices: MutableList<SubIndex>?
  ) : PendingEntry(false) {
    var index: FST<Output>? = null
    var totFloorTermCount: Long = 0

    override fun toString(): String {
      return "BLOCK: " + ToStringUtils.bytesRefToString(prefix)
    }

    @Throws(IOException::class)
    fun compileIndex(
      blocks: List<PendingBlock>,
      scratchBytes: ByteBuffersDataOutput,
      scratchIntsRef: IntsRefBuilder
    ) {
      assert((isFloor && blocks.size > 1) || (!isFloor && blocks.size == 1))
      assert(this === blocks[0])
      assert(scratchBytes.size() == 0L)

      var lastSumTotalTermCount = 0L
      var sumTotalTermCount = totalTermCount
      scratchBytes.writeVLong(encodeOutput(fp, hasTerms, isFloor))
      if (isFloor) {
        scratchBytes.writeVInt(blocks.size - 1)
        for (i in 1 until blocks.size) {
          val sub = blocks[i]
          assert(sub.floorLeadByte != -1)
          scratchBytes.writeByte(sub.floorLeadByte.toByte())
          scratchBytes.writeVLong(sumTotalTermCount - lastSumTotalTermCount)
          lastSumTotalTermCount = sumTotalTermCount
          sumTotalTermCount += sub.totalTermCount
          assert(sub.fp > fp)
          scratchBytes.writeVLong(((sub.fp - fp) shl 1) or if (sub.hasTerms) 1L else 0L)
        }
      }

      val fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, FST_OUTPUTS).build()
      val bytes = scratchBytes.toArrayCopy()
      assert(bytes.isNotEmpty())
      fstCompiler.add(
        Util.toIntsRef(prefix, scratchIntsRef),
        FST_OUTPUTS.newOutput(
          BytesRef(bytes, 0, bytes.size),
          0,
          Long.MAX_VALUE - (sumTotalTermCount - 1)
        )
      )
      scratchBytes.reset()

      var termOrdOffset = 0L
      for (block in blocks) {
        val blockSubIndices = block.subIndices
        if (blockSubIndices != null) {
          for (subIndex in blockSubIndices) {
            append(fstCompiler, subIndex.index, termOrdOffset + subIndex.termOrdStart, scratchIntsRef)
          }
          block.subIndices = null
        }
        termOrdOffset += block.totalTermCount
      }
      totFloorTermCount = termOrdOffset

      assert(sumTotalTermCount == totFloorTermCount)

      index = FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader())
      assert(index != null)
      assert(subIndices == null)
    }

    @Throws(IOException::class)
    private fun append(
      fstCompiler: FSTCompiler<Output>,
      subIndex: FST<Output>,
      termOrdOffset: Long,
      scratchIntsRef: IntsRefBuilder
    ) {
      val subIndexEnum = BytesRefFSTEnum(subIndex)
      var indexEnt = subIndexEnum.next()
      while (indexEnt != null) {
        val output = indexEnt.output!!
        val newOutput = FST_OUTPUTS.newOutput(
          output.bytes(),
          termOrdOffset + output.startOrd(),
          output.endOrd() - termOrdOffset
        )
        fstCompiler.add(Util.toIntsRef(requireNotNull(indexEnt.input), scratchIntsRef), newOutput)
        indexEnt = subIndexEnum.next()
      }
    }
  }

  private val scratchBytes: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
  private val scratchIntsRef = IntsRefBuilder()

  private inner class TermsWriter(private val fieldInfo: FieldInfo) {
    private var numTerms: Long = 0
    val docsSeen: FixedBitSet = FixedBitSet(maxDoc)
    var sumTotalTermFreq: Long = 0
    var sumDocFreq: Long = 0
    var indexStartFP: Long = 0

    private val lastTerm = BytesRefBuilder()
    private var prefixStarts = IntArray(8)
    private val pending: MutableList<PendingEntry> = ArrayList()
    private val newBlocks: MutableList<PendingBlock> = ArrayList()

    private var firstPendingTerm: PendingTerm? = null
    private var lastPendingTerm: PendingTerm? = null

    init {
      postingsWriter.setField(fieldInfo)
    }

    @Throws(IOException::class)
    fun writeBlocks(prefixLength: Int, count: Int) {
      assert(count > 0)
      assert(prefixLength > 0 || count == pending.size)

      var lastSuffixLeadLabel = -1
      var hasTerms = false
      var hasSubBlocks = false

      val start = pending.size - count
      val end = pending.size
      var nextBlockStart = start
      var nextFloorLeadLabel = -1

      for (i in start until end) {
        val ent = pending[i]
        val suffixLeadLabel = if (ent.isTerm) {
          val term = ent as PendingTerm
          if (term.termBytes.size == prefixLength) {
            assert(lastSuffixLeadLabel == -1)
            -1
          } else {
            term.termBytes[prefixLength].toInt() and 0xff
          }
        } else {
          val block = ent as PendingBlock
          assert(block.prefix.length > prefixLength)
          block.prefix.bytes[block.prefix.offset + prefixLength].toInt() and 0xff
        }

        if (suffixLeadLabel != lastSuffixLeadLabel) {
          val itemsInBlock = i - nextBlockStart
          if (itemsInBlock >= minItemsInBlock && end - nextBlockStart > maxItemsInBlock) {
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

      assert(newBlocks.isNotEmpty())
      val firstBlock = newBlocks[0]
      assert(firstBlock.isFloor || newBlocks.size == 1)

      firstBlock.compileIndex(newBlocks, scratchBytes, scratchIntsRef)

      pending.subList(pending.size - count, pending.size).clear()
      pending.add(firstBlock)
      newBlocks.clear()
    }

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
      assert(end > start)

      val startFP = out.filePointer
      val hasFloorLeadLabel = isFloor && floorLeadLabel != -1

      val prefix = BytesRef(prefixLength + if (hasFloorLeadLabel) 1 else 0)
      lastTerm.bytes().copyInto(prefix.bytes, 0, 0, prefixLength)
      prefix.length = prefixLength

      val numEntries = end - start
      var code = numEntries shl 1
      if (end == pending.size) {
        code = code or 1
      }
      out.writeVInt(code)

      val isLeafBlock = !hasSubBlocks
      val subIndices: MutableList<SubIndex>?
      var totalTermCount: Long
      var absolute = true

      if (isLeafBlock) {
        subIndices = null
        for (i in start until end) {
          val ent = pending[i] as PendingTerm
          assert(StringHelper.startsWith(ent.termBytes, prefix))
          val state = ent.state
          val suffix = ent.termBytes.size - prefixLength
          suffixWriter.writeVInt(suffix)
          suffixWriter.writeBytes(ent.termBytes, prefixLength, suffix)
          assert(floorLeadLabel == -1 || (ent.termBytes[prefixLength].toInt() and 0xff) >= floorLeadLabel)

          statsWriter.writeVInt(state.docFreq)
          if (fieldInfo.indexOptions != IndexOptions.DOCS) {
            assert(state.totalTermFreq >= state.docFreq)
            statsWriter.writeVLong(state.totalTermFreq - state.docFreq)
          }

          postingsWriter.encodeTerm(metaWriter, fieldInfo, state, absolute)
          absolute = false
        }
        totalTermCount = (end - start).toLong()
      } else {
        subIndices = ArrayList()
        totalTermCount = 0L
        for (i in start until end) {
          val ent = pending[i]
          if (ent.isTerm) {
            val term = ent as PendingTerm
            assert(StringHelper.startsWith(term.termBytes, prefix))
            val state = term.state
            val suffix = term.termBytes.size - prefixLength
            suffixWriter.writeVInt(suffix shl 1)
            suffixWriter.writeBytes(term.termBytes, prefixLength, suffix)
            assert(floorLeadLabel == -1 || (term.termBytes[prefixLength].toInt() and 0xff) >= floorLeadLabel)

            statsWriter.writeVInt(state.docFreq)
            if (fieldInfo.indexOptions != IndexOptions.DOCS) {
              assert(state.totalTermFreq >= state.docFreq)
              statsWriter.writeVLong(state.totalTermFreq - state.docFreq)
            }

            postingsWriter.encodeTerm(metaWriter, fieldInfo, state, absolute)
            absolute = false
            totalTermCount++
          } else {
            val block = ent as PendingBlock
            assert(StringHelper.startsWith(block.prefix, prefix))
            val suffix = block.prefix.length - prefixLength
            assert(suffix > 0)

            suffixWriter.writeVInt((suffix shl 1) or 1)
            suffixWriter.writeBytes(block.prefix.bytes, prefixLength, suffix)
            assert(floorLeadLabel == -1 || (block.prefix.bytes[prefixLength].toInt() and 0xff) >= floorLeadLabel)
            assert(block.fp < startFP)

            suffixWriter.writeVLong(startFP - block.fp)
            suffixWriter.writeVLong(block.totFloorTermCount)
            subIndices.add(SubIndex(block.index!!, totalTermCount))
            totalTermCount += block.totFloorTermCount
          }
        }
        assert(subIndices.isNotEmpty())
      }

      out.writeVInt((suffixWriter.size().toInt() shl 1) or if (isLeafBlock) 1 else 0)
      suffixWriter.copyTo(out)
      suffixWriter.reset()

      out.writeVInt(statsWriter.size().toInt())
      statsWriter.copyTo(out)
      statsWriter.reset()

      out.writeVInt(metaWriter.size().toInt())
      metaWriter.copyTo(out)
      metaWriter.reset()

      if (hasFloorLeadLabel) {
        prefix.bytes[prefix.length++] = floorLeadLabel.toByte()
      }

      return PendingBlock(
        prefix,
        startFP,
        hasTerms,
        totalTermCount,
        isFloor,
        floorLeadLabel,
        subIndices
      )
    }

    @Throws(IOException::class)
    fun write(text: BytesRef, termsEnum: TermsEnum, norms: NormsProducer?) {
      val state = postingsWriter.writeTerm(text, termsEnum, docsSeen, norms) ?: return
      assert(state.docFreq != 0)
      assert(fieldInfo.indexOptions == IndexOptions.DOCS || state.totalTermFreq >= state.docFreq)
      sumDocFreq += state.docFreq
      sumTotalTermFreq += state.totalTermFreq
      pushTerm(text)

      val term = PendingTerm(BytesRef.deepCopyOf(text), state)
      pending.add(term)
      numTerms++
      if (firstPendingTerm == null) {
        firstPendingTerm = term
      }
      lastPendingTerm = term
    }

    @Throws(IOException::class)
    private fun pushTerm(text: BytesRef) {
      val limit = kotlin.math.min(lastTerm.length(), text.length)
      var pos = 0
      while (pos < limit && lastTerm.byteAt(pos) == text.bytes[text.offset + pos]) {
        pos++
      }

      for (i in lastTerm.length() - 1 downTo pos) {
        val prefixTopSize = pending.size - prefixStarts[i]
        if (prefixTopSize >= minItemsInBlock) {
          writeBlocks(i + 1, prefixTopSize)
          prefixStarts[i] -= prefixTopSize - 1
        }
      }

      if (prefixStarts.size < text.length) {
        prefixStarts = ArrayUtil.grow(prefixStarts, text.length)
      }

      for (i in pos until text.length) {
        prefixStarts[i] = pending.size
      }

      lastTerm.copyBytes(text)
    }

    @Throws(IOException::class)
    fun finish() {
      if (numTerms > 0) {
        writeBlocks(0, pending.size)

        assert(pending.size == 1 && !pending[0].isTerm)
        val root = pending[0] as PendingBlock
        assert(root.prefix.length == 0)
        assert(root.index!!.getEmptyOutput() != null)

        indexStartFP = indexOut.filePointer
        root.index!!.save(indexOut, indexOut)

        val minTerm = BytesRef(firstPendingTerm!!.termBytes)
        val maxTerm = BytesRef(lastPendingTerm!!.termBytes)

        fields.add(
          FieldMetaData(
            fieldInfo,
            root.index!!.getEmptyOutput()!!,
            numTerms,
            indexStartFP,
            sumTotalTermFreq,
            sumDocFreq,
            docsSeen.cardinality(),
            minTerm,
            maxTerm
          )
        )
      } else {
        assert(docsSeen.cardinality() == 0)
      }
    }

    private val suffixWriter: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
    private val statsWriter: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
    private val metaWriter: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
  }

  private var closed = false

  override fun close() {
    if (closed) return
    closed = true

    var success = false
    try {
      val dirStart = out.filePointer
      val indexDirStart = indexOut.filePointer

      out.writeVInt(fields.size)
      for (field in fields) {
        out.writeVInt(field.fieldInfo.number)
        out.writeVLong(field.numTerms)
        out.writeVInt(field.rootCode.bytes().length)
        out.writeBytes(field.rootCode.bytes().bytes, field.rootCode.bytes().offset, field.rootCode.bytes().length)
        if (field.fieldInfo.indexOptions != IndexOptions.DOCS) {
          out.writeVLong(field.sumTotalTermFreq)
        }
        out.writeVLong(field.sumDocFreq)
        out.writeVInt(field.docCount)
        indexOut.writeVLong(field.indexStartFP)
        writeBytesRef(out, field.minTerm)
        writeBytesRef(out, field.maxTerm)
      }
      out.writeLong(dirStart)
      CodecUtil.writeFooter(out)
      indexOut.writeLong(indexDirStart)
      CodecUtil.writeFooter(indexOut)
      success = true
    } finally {
      if (success) {
        IOUtils.close(out, indexOut, postingsWriter)
      } else {
        IOUtils.closeWhileHandlingException(out, indexOut, postingsWriter)
      }
    }
  }

  companion object {
    val FST_OUTPUTS: FSTOrdsOutputs = FSTOrdsOutputs()
    val NO_OUTPUT: Output = FST_OUTPUTS.noOutput

    const val DEFAULT_MIN_BLOCK_SIZE: Int = 25
    const val DEFAULT_MAX_BLOCK_SIZE: Int = 48

    const val OUTPUT_FLAGS_NUM_BITS: Int = 2
    const val OUTPUT_FLAGS_MASK: Int = 0x3
    const val OUTPUT_FLAG_IS_FLOOR: Int = 0x1
    const val OUTPUT_FLAG_HAS_TERMS: Int = 0x2

    const val TERMS_EXTENSION: String = "tio"
    const val TERMS_CODEC_NAME: String = "OrdsBlockTreeTerms"

    const val VERSION_START: Int = 1
    const val VERSION_CURRENT: Int = VERSION_START

    const val TERMS_INDEX_EXTENSION: String = "tipo"
    const val TERMS_INDEX_CODEC_NAME: String = "OrdsBlockTreeIndex"

    fun encodeOutput(fp: Long, hasTerms: Boolean, isFloor: Boolean): Long {
      assert(fp < (1L shl 62))
      return (fp shl 2) or
        (if (hasTerms) OUTPUT_FLAG_HAS_TERMS.toLong() else 0L) or
        (if (isFloor) OUTPUT_FLAG_IS_FLOOR.toLong() else 0L)
    }

    @Throws(IOException::class)
    private fun writeBytesRef(out: IndexOutput, bytes: BytesRef) {
      out.writeVInt(bytes.length)
      out.writeBytes(bytes.bytes, bytes.offset, bytes.length)
    }
  }
}
