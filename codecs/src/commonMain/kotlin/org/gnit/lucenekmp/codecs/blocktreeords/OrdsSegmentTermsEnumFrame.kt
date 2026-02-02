/*
 * Port of org.apache.lucene.codecs.blocktreeords.OrdsSegmentTermsEnumFrame
 * Kept field/method order to ease side-by-side comparison with upstream Java.
 */

package org.gnit.lucenekmp.codecs.blocktreeords

import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.fst.FST

internal class OrdsSegmentTermsEnumFrame internal constructor(
  private val ste: OrdsSegmentTermsEnum,
  val ord: Int
) {
  var hasTerms: Boolean = false
  var hasTermsOrig: Boolean = false
  var isFloor: Boolean = false

  var arc: FST.Arc<FSTOrdsOutputs.Output>? = null

  // File pointer where this block was loaded from
  var fp: Long = 0L
  var fpOrig: Long = 0L
  var fpEnd: Long = 0L

  var suffixBytes: ByteArray = ByteArray(128)
  val suffixesReader: ByteArrayDataInput = ByteArrayDataInput()

  var statBytes: ByteArray = ByteArray(64)
  val statsReader: ByteArrayDataInput = ByteArrayDataInput()

  var floorData: ByteArray = ByteArray(32)
  val floorDataReader: ByteArrayDataInput = ByteArrayDataInput()

  // Length of prefix shared by all terms in this block
  var prefix: Int = 0

  // Number of entries (term or sub-block) in this block
  var entCount: Int = 0

  // Which term we will next read, or -1 if the block isn't loaded yet
  var nextEnt: Int = -1

  // Starting termOrd for this frame, used to reset termOrd in rewind()
  var termOrdOrig: Long = 0L

  // 1 + ordinal of the current term
  var termOrd: Long = 0L

  // True if this block is either not a floor block, or, it's the last sub-block of a floor block
  var isLastInFloor: Boolean = false

  // True if all entries are terms
  var isLeafBlock: Boolean = false

  var lastSubFP: Long = -1L

  // Starting byte of next floor block:
  var nextFloorLabel: Int = 0

  // Starting termOrd of next floor block:
  var nextFloorTermOrd: Long = 0L

  var numFollowFloorBlocks: Int = 0

  // Next term to decode metaData; we decode metaData lazily
  var metaDataUpto: Int = 0

  val state: BlockTermState

  // metadata
  var bytes: ByteArray? = null
  var bytesReader: ByteArrayDataInput? = null

  // cached values used during nextNonLeaf
  var subCode: Long = 0L
  var startBytePos: Int = 0
  var suffix: Int = 0

  init {
    state = ste.fr.parent.postingsReader.newTermState()
    state.totalTermFreq = -1
  }

  fun setFloorData(inBuf: ByteArrayDataInput, source: BytesRef) {
    val numBytes = source.length - (inBuf.position - source.offset)
    check(numBytes > 0)
    if (numBytes > floorData.size) floorData = ByteArray(ArrayUtil.oversize(numBytes, 1))
    source.bytes.copyInto(floorData, 0, source.offset + inBuf.position, source.offset + inBuf.position + numBytes)
    floorDataReader.reset(floorData, 0, numBytes)
    numFollowFloorBlocks = floorDataReader.readVInt()
    nextFloorLabel = floorDataReader.readByte().toInt() and 0xff
    nextFloorTermOrd = termOrdOrig + floorDataReader.readVLong()
  }

  fun getTermBlockOrd(): Int = if (isLeafBlock) nextEnt else state.termBlockOrd

  fun loadNextFloorBlock() {
    fp = fpEnd
    nextEnt = -1
    loadBlock()
  }

  /* Does initial decode of next block of terms; this
  doesn't actually decode the docFreq, totalTermFreq,
  postings details (frq/prx offset, etc.) metadata;
  it just loads them as byte[] blobs which are then
  decoded on-demand if the metadata is ever requested
  for any term in this block.  This enables terms-only
  intensive consumes (eg certain MTQs, respelling) to
  not pay the price of decoding metadata they won't
  use. */
  fun loadBlock() {
    ste.initIndexInput()
    if (nextEnt != -1) return

    ste.`in`!!.seek(fp)
    var code = ste.`in`!!.readVInt()
    entCount = code ushr 1
    check(entCount > 0)
    isLastInFloor = (code and 1) != 0
    // term suffixes
    code = ste.`in`!!.readVInt()
    isLeafBlock = (code and 1) != 0
    var numBytes = code ushr 1
    if (suffixBytes.size < numBytes) suffixBytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
    ste.`in`!!.readBytes(suffixBytes, 0, numBytes)
    suffixesReader.reset(suffixBytes, 0, numBytes)

    // stats
    numBytes = ste.`in`!!.readVInt()
    if (statBytes.size < numBytes) statBytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
    ste.`in`!!.readBytes(statBytes, 0, numBytes)
    statsReader.reset(statBytes, 0, numBytes)
    metaDataUpto = 0

    state.termBlockOrd = 0
    nextEnt = 0
    lastSubFP = -1

    // metadata
    numBytes = ste.`in`!!.readVInt()
    if (bytes == null) {
      bytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
      bytesReader = ByteArrayDataInput()
    } else if (bytes!!.size < numBytes) {
      bytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
    }
    ste.`in`!!.readBytes(bytes!!, 0, numBytes)
    bytesReader!!.reset(bytes!!, 0, numBytes)

    fpEnd = ste.`in`!!.filePointer
  }

  fun rewind() {
    fp = fpOrig
    termOrd = termOrdOrig
    nextEnt = -1
    hasTerms = hasTermsOrig
    if (isFloor) {
      floorDataReader.rewind()
      numFollowFloorBlocks = floorDataReader.readVInt()
      check(numFollowFloorBlocks > 0)
      nextFloorLabel = floorDataReader.readByte().toInt() and 0xff
      nextFloorTermOrd = termOrdOrig + floorDataReader.readVLong()
    }
  }

  fun next(): Boolean = if (isLeafBlock) nextLeaf() else nextNonLeaf()

  fun nextLeaf(): Boolean {
    check(nextEnt != -1 && nextEnt < entCount)
    nextEnt++
    termOrd++
    suffix = suffixesReader.readVInt()
    startBytePos = suffixesReader.position
    ste.term.setLength(prefix + suffix)
    ste.term.grow(ste.term.length())
    suffixesReader.readBytes(ste.term.bytes(), prefix, suffix)
    ste.termExists = true
    return false
  }

  fun nextNonLeaf(): Boolean {
    check(nextEnt != -1 && nextEnt < entCount)
    nextEnt++
    val code = suffixesReader.readVInt()
    suffix = code ushr 1
    startBytePos = suffixesReader.position
    ste.term.setLength(prefix + suffix)
    ste.term.grow(ste.term.length())
    suffixesReader.readBytes(ste.term.bytes(), prefix, suffix)
    if ((code and 1) == 0) {
      ste.termExists = true
      subCode = 0L
      state.termBlockOrd++
      termOrd++
      return false
    } else {
      ste.termExists = false
      subCode = suffixesReader.readVLong()
      termOrd += suffixesReader.readVLong()
      lastSubFP = fp - subCode
      return true
    }
  }

  fun scanToFloorFrame(target: BytesRef) {
    if (!isFloor || target.length <= prefix) return
    val targetLabel = target.bytes[target.offset + prefix].toInt() and 0xff
    if (targetLabel < nextFloorLabel) return
    check(numFollowFloorBlocks != 0)

    var newFP: Long
      var lastFloorTermOrd: Long
      while (true) {
      val code = floorDataReader.readVLong()
      newFP = fpOrig + (code ushr 1)
      hasTerms = (code and 1L) != 0L
      isLastInFloor = numFollowFloorBlocks == 1
      numFollowFloorBlocks--
      lastFloorTermOrd = nextFloorTermOrd
      if (isLastInFloor) {
        nextFloorLabel = 256
        nextFloorTermOrd = Long.MAX_VALUE
        break
      } else {
        nextFloorLabel = floorDataReader.readByte().toInt() and 0xff
        nextFloorTermOrd += floorDataReader.readVLong()
        if (targetLabel < nextFloorLabel) break
      }
    }

    fp = newFP
    nextEnt = -1
    termOrd = lastFloorTermOrd
  }

  /** Seek to the floor sub-block that contains the given term ordinal. */
  fun scanToFloorFrame(targetOrd: Long) {
    if (!isFloor || targetOrd < nextFloorTermOrd) return
    check(numFollowFloorBlocks != 0)
    var lastFloorTermOrd = nextFloorTermOrd
    var newFP = fpOrig
    while (numFollowFloorBlocks > 0) {
      val code = floorDataReader.readVLong()
      newFP = fpOrig + (code ushr 1)
      hasTerms = (code and 1L) != 0L
      isLastInFloor = numFollowFloorBlocks == 1
      numFollowFloorBlocks--
      lastFloorTermOrd = nextFloorTermOrd
      if (isLastInFloor) {
        nextFloorLabel = 256
        nextFloorTermOrd = Long.MAX_VALUE
        break
      } else {
        nextFloorLabel = floorDataReader.readByte().toInt() and 0xff
        nextFloorTermOrd += floorDataReader.readVLong()
        if (targetOrd < nextFloorTermOrd) break
      }
    }
    if (newFP != fp) {
      fp = newFP
      nextEnt = -1
      termOrd = lastFloorTermOrd
    }
  }

  fun decodeMetaData() {
    val limit = getTermBlockOrd()
    var absolute = metaDataUpto == 0
    check(limit > 0)
    while (metaDataUpto < limit) {
      state.docFreq = statsReader.readVInt()
      if (ste.fr.fieldInfo.indexOptions == IndexOptions.DOCS) {
        state.totalTermFreq = state.docFreq.toLong()
      } else {
        state.totalTermFreq = state.docFreq + statsReader.readVLong()
      }
      ste.fr.parent.postingsReader.decodeTerm(bytesReader!!, ste.fr.fieldInfo, state, absolute)
      metaDataUpto++
      absolute = false
    }
    state.termBlockOrd = metaDataUpto
  }

  // Used only by assert
  private fun prefixMatches(target: BytesRef): Boolean {
    for (bytePos in 0 until prefix) {
      if (target.bytes[target.offset + bytePos] != ste.term.byteAt(bytePos)) return false
    }
    return true
  }

  fun scanToSubBlock(lastSubFP: Long) {
    if (nextEnt != -1 && fp == lastSubFP) return
    // scan until the sub-block with lastSubFP is found
    if (nextEnt == -1) loadBlock()
    while (true) {
      if (nextEnt == entCount) return
      val code = suffixesReader.readVInt()
      val suffix = code ushr 1
      val savePos = suffixesReader.position
      suffixesReader.skipBytes(suffix.toLong())
      if ((code and 1) == 0) {
        // term
        state.termBlockOrd++
        nextEnt++
        continue
      } else {
        val subFP = fp - suffixesReader.readVLong()
        val termOrdDelta = suffixesReader.readVLong()
        nextEnt++
        if (subFP == lastSubFP) return
        if (subFP < lastSubFP) continue
        // otherwise we went past it; rewind to previous entry so next() will return it
        suffixesReader.position = savePos
        nextEnt--
        return
      }
    }
  }

  fun scanToTerm(target: BytesRef, exactOnly: Boolean): TermsEnum.SeekStatus {
    return if (isLeafBlock) scanToTermLeaf(target, exactOnly) else scanToTermNonLeaf(target, exactOnly)
  }

  fun scanToTermLeaf(target: BytesRef, exactOnly: Boolean): TermsEnum.SeekStatus {
    assert(nextEnt != -1)

    ste.termExists = true
    subCode = 0L

    if (nextEnt == entCount) {
      if (exactOnly) fillTerm()
      return TermsEnum.SeekStatus.END
    }

    assert(prefixMatches(target))

    nextTerm@ while (true) {
      nextEnt++
      termOrd++

      suffix = suffixesReader.readVInt()
      val termLen = prefix + suffix
      startBytePos = suffixesReader.position
      suffixesReader.skipBytes(suffix.toLong())

      val targetLimit = target.offset + kotlin.math.min(target.length, termLen)
      var targetPos = target.offset + prefix

      var bytePos = startBytePos
      while (true) {
        val cmp: Int
        val stop: Boolean
        if (targetPos < targetLimit) {
          cmp = (suffixBytes[bytePos++].toInt() and 0xFF) - (target.bytes[targetPos++].toInt() and 0xFF)
          stop = false
        } else {
          assert(targetPos == targetLimit)
          cmp = termLen - target.length
          stop = true
        }

        if (cmp < 0) {
          if (nextEnt == entCount) {
            if (exactOnly) fillTerm()
            break@nextTerm
          } else {
            continue@nextTerm
          }
        } else if (cmp > 0) {
          fillTerm()
          return TermsEnum.SeekStatus.NOT_FOUND
        } else if (stop) {
          assert(ste.termExists)
          fillTerm()
          return TermsEnum.SeekStatus.FOUND
        }
      }
    }

    if (exactOnly) fillTerm()
    return TermsEnum.SeekStatus.END
  }

  fun scanToTermNonLeaf(target: BytesRef, exactOnly: Boolean): TermsEnum.SeekStatus {
    assert(nextEnt != -1)

    if (nextEnt == entCount) {
      if (exactOnly) {
        fillTerm()
        ste.termExists = subCode == 0L
      }
      return TermsEnum.SeekStatus.END
    }

    assert(prefixMatches(target))

    nextTerm@ while (true) {
      nextEnt++

      val code = suffixesReader.readVInt()
      suffix = code ushr 1
      val termLen = prefix + suffix
      startBytePos = suffixesReader.position
      suffixesReader.skipBytes(suffix.toLong())
      val prevTermOrd = termOrd
      if ((code and 1) == 0) {
        ste.termExists = true
        state.termBlockOrd++
        termOrd++
        subCode = 0L
      } else {
        ste.termExists = false
        subCode = suffixesReader.readVLong()
        termOrd += suffixesReader.readVLong()
        lastSubFP = fp - subCode
      }

      val targetLimit = target.offset + kotlin.math.min(target.length, termLen)
      var targetPos = target.offset + prefix

      var bytePos = startBytePos
      while (true) {
        val cmp: Int
        val stop: Boolean
        if (targetPos < targetLimit) {
          cmp = (suffixBytes[bytePos++].toInt() and 0xFF) - (target.bytes[targetPos++].toInt() and 0xFF)
          stop = false
        } else {
          assert(targetPos == targetLimit)
          cmp = termLen - target.length
          stop = true
        }

        if (cmp < 0) {
          if (nextEnt == entCount) {
            if (exactOnly) fillTerm()
            break@nextTerm
          } else {
            continue@nextTerm
          }
        } else if (cmp > 0) {
          fillTerm()
          if (!exactOnly && !ste.termExists) {
            ste.currentFrame = ste.pushFrame(null, ste.currentFrame.lastSubFP, termLen, prevTermOrd)
            ste.currentFrame.loadBlock()
            while (ste.currentFrame.next()) {
              ste.currentFrame = ste.pushFrame(null, ste.currentFrame.lastSubFP, ste.term.length(), prevTermOrd)
              ste.currentFrame.loadBlock()
            }
          }
          return TermsEnum.SeekStatus.NOT_FOUND
        } else if (stop) {
          assert(ste.termExists)
          fillTerm()
          return TermsEnum.SeekStatus.FOUND
        }
      }
    }

    if (exactOnly) fillTerm()
    return TermsEnum.SeekStatus.END
  }

  private fun fillTerm() {
    val termLength = prefix + suffix
    ste.term.setLength(termLength)
    ste.term.grow(termLength)
    suffixBytes.copyInto(ste.term.bytes(), prefix, startBytePos, startBytePos + suffix)
  }
}
