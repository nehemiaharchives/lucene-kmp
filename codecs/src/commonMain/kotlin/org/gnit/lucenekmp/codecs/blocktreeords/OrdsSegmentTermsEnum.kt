package org.gnit.lucenekmp.codecs.blocktreeords

import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.Util

/**
 * Iterates through terms in this field.
 */
/**
 * Port of org.apache.lucene.codecs.blocktreeords.OrdsSegmentTermsEnum
 * Preserves Java ordering and behavior; uses small Kotlin idiomatic adjustments
 * where safe (e.g. byte-array operations).
 */
internal class OrdsSegmentTermsEnum(internal val fr: OrdsFieldReader) : BaseTermsEnum() {
  // Lazy init:
  internal var `in`: IndexInput? = null

  private var stack: Array<OrdsSegmentTermsEnumFrame?> = arrayOfNulls(0)
  private val staticFrame: OrdsSegmentTermsEnumFrame = OrdsSegmentTermsEnumFrame(this, -1)
  internal var currentFrame: OrdsSegmentTermsEnumFrame = staticFrame
  internal var termExists: Boolean = false

  private var targetBeforeCurrentLength: Int = 0
  private val scratchReader: ByteArrayDataInput = ByteArrayDataInput()

  // What prefix of the current term was present in the index:
  private var validIndexPrefix: Int = 0
  // assert only:
  private var eof: Boolean = false

  internal val term: BytesRefBuilder = BytesRefBuilder()
  private val fstReader: FST.BytesReader? = fr.index?.getBytesReader()

  @Suppress("UNCHECKED_CAST")
  private var arcs: Array<FST.Arc<FSTOrdsOutputs.Output>> = Array(1) { FST.Arc() }

  private var positioned: Boolean = false

  init {
    stack = arrayOfNulls(0)
    currentFrame = staticFrame
    validIndexPrefix = 0
  }

  internal fun initIndexInput() {
    if (this.`in` == null) this.`in` = fr.parent.`in`.clone()
  }

  private fun getFrame(ord: Int): OrdsSegmentTermsEnumFrame {
    if (ord >= stack.size) {
      val next = arrayOfNulls<OrdsSegmentTermsEnumFrame>(ArrayUtil.oversize(1 + ord, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
      stack.copyInto(next, 0, 0, stack.size)
      for (stackOrd in stack.size until next.size) next[stackOrd] = OrdsSegmentTermsEnumFrame(this, stackOrd)
      stack = next
    }
    check(stack[ord]!!.ord == ord)
    return stack[ord]!!
  }

  private fun getArc(ord: Int): FST.Arc<FSTOrdsOutputs.Output> {
    if (ord >= arcs.size) {
      val next = arrayOfNulls<FST.Arc<FSTOrdsOutputs.Output>>(ArrayUtil.oversize(1 + ord, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
      arcs.copyInto(next, 0, 0, arcs.size)
      for (arcOrd in arcs.size until next.size) next[arcOrd] = FST.Arc()
      @Suppress("UNCHECKED_CAST")
      arcs = next as Array<FST.Arc<FSTOrdsOutputs.Output>>
    }
    return arcs[ord]
  }

  // Pushes a frame we seek'd to
  internal fun pushFrame(arc: FST.Arc<FSTOrdsOutputs.Output>?, frameData: FSTOrdsOutputs.Output?, length: Int): OrdsSegmentTermsEnumFrame {
    require(frameData != null)
    scratchReader.reset(frameData.bytes().bytes, frameData.bytes().offset, frameData.bytes().length)
    val code = scratchReader.readVLong()
    val fpSeek = code ushr OrdsBlockTreeTermsWriter.OUTPUT_FLAGS_NUM_BITS
    val f = getFrame(1 + currentFrame.ord)
    f.hasTerms = (code and OrdsBlockTreeTermsWriter.OUTPUT_FLAG_HAS_TERMS.toLong()) != 0L
    f.hasTermsOrig = f.hasTerms
    f.isFloor = (code and OrdsBlockTreeTermsWriter.OUTPUT_FLAG_IS_FLOOR.toLong()) != 0L
    if (f.isFloor) {
      f.termOrdOrig = frameData.startOrd()
      f.setFloorData(scratchReader, frameData.bytes())
    }
    pushFrame(arc, fpSeek, length, frameData.startOrd())
    return f
  }

  // Pushes next'd frame or seek'd frame; we later lazy-load the frame only when needed
  internal fun pushFrame(arc: FST.Arc<FSTOrdsOutputs.Output>?, fp: Long, length: Int, termOrd: Long): OrdsSegmentTermsEnumFrame {
    val f = getFrame(1 + currentFrame.ord)
    f.arc = arc
    if (f.fpOrig == fp && f.nextEnt != -1) {
      if (f.prefix > targetBeforeCurrentLength) f.rewind()
      check(length == f.prefix)
      check(termOrd == f.termOrdOrig)
    } else {
      f.nextEnt = -1
      f.prefix = length
      f.state.termBlockOrd = 0
      f.termOrdOrig = termOrd
      f.termOrd = termOrd
      f.fpOrig = fp
      f.fp = fp
      f.lastSubFP = -1L
    }
    return f
  }

  private fun clearEOF(): Boolean { eof = false; return true }
  private fun setEOF(): Boolean { eof = true; return true }

  override fun seekExact(text: BytesRef): Boolean {
    if (fr.index == null) throw IllegalStateException("terms index was not loaded")
    term.grow(1 + text.length)
    check(clearEOF())

    val reader = fstReader ?: throw IllegalStateException("fst reader missing")
    var arc: FST.Arc<FSTOrdsOutputs.Output>
    var targetUpto: Int
    var output: FSTOrdsOutputs.Output

    targetBeforeCurrentLength = currentFrame.ord

    if (positioned && currentFrame !== staticFrame) {
      arc = arcs[0]
      output = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
      targetUpto = 0
      var lastFrame: OrdsSegmentTermsEnumFrame = stack[0]!!
      check(validIndexPrefix <= term.length())
      val targetLimit = kotlin.math.min(text.length, validIndexPrefix)
      var cmp = 0

      while (targetUpto < targetLimit) {
        cmp = (term.byteAt(targetUpto).toInt() and 0xFF) - (text.bytes[text.offset + targetUpto].toInt() and 0xFF)
        if (cmp != 0) break
        arc = arcs[1 + targetUpto]
        val arcOut0 = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
        if (arcOut0 != OrdsBlockTreeTermsWriter.NO_OUTPUT) output = OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, arcOut0)
        if (arc.isFinal) lastFrame = stack[1 + lastFrame.ord]!!
        targetUpto++
      }

      if (cmp == 0) {
        val targetLimit2 = kotlin.math.min(text.length, term.length())
        while (targetUpto < targetLimit2) {
          cmp = (term.byteAt(targetUpto).toInt() and 0xFF) - (text.bytes[text.offset + targetUpto].toInt() and 0xFF)
          if (cmp != 0) break
          targetUpto++
        }
        if (cmp == 0) cmp = term.length() - text.length
      }

      if (cmp < 0) {
        currentFrame = lastFrame
      } else if (cmp > 0) {
        targetBeforeCurrentLength = lastFrame.ord
        currentFrame = lastFrame
        currentFrame.rewind()
      } else {
        if (termExists) return true
      }
    } else {
      targetBeforeCurrentLength = -1
      arc = fr.index.getFirstArc(arcs[0])
      check(arc.isFinal)
      output = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
      currentFrame = staticFrame
      targetUpto = 0
      val rootNextFinal = arc.nextFinalOutput()
      currentFrame = if (rootNextFinal != null) pushFrame(arc, OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, rootNextFinal), 0) else pushFrame(arc, output, 0)
    }

    positioned = true

    while (targetUpto < text.length) {
      val targetLabel = text.bytes[text.offset + targetUpto].toInt() and 0xFF
      val nextArc = fr.index.findTargetArc(targetLabel, arc, getArc(1 + targetUpto), reader)
      if (nextArc == null) {
        validIndexPrefix = currentFrame.prefix
        currentFrame.scanToFloorFrame(text)
        if (!currentFrame.hasTerms) {
          termExists = false
          term.setByteAt(targetUpto, text.bytes[text.offset + targetUpto])
          term.setLength(1 + targetUpto)
          return false
        }
        currentFrame.loadBlock()
        val result = currentFrame.scanToTerm(text, true)
        return result == SeekStatus.FOUND
      } else {
        arc = nextArc
        term.setByteAt(targetUpto, text.bytes[text.offset + targetUpto])
        val arcOut1 = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
        if (arcOut1 != OrdsBlockTreeTermsWriter.NO_OUTPUT) output = OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, arcOut1)
        targetUpto++
        if (arc.isFinal) {
          val nf = arc.nextFinalOutput()
          currentFrame = if (nf != null) pushFrame(arc, OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, nf), targetUpto) else pushFrame(arc, output, targetUpto)
        }
      }
    }

    validIndexPrefix = currentFrame.prefix
    currentFrame.scanToFloorFrame(text)
    if (!currentFrame.hasTerms) {
      termExists = false
      term.setLength(targetUpto)
      return false
    }
    currentFrame.loadBlock()
    val result = currentFrame.scanToTerm(text, true)
    return result == SeekStatus.FOUND
  }

  override fun seekCeil(text: BytesRef): SeekStatus {
    if (fr.index == null) throw IllegalStateException("terms index was not loaded")
    term.grow(1 + text.length)
    check(clearEOF())

    val reader = fstReader ?: throw IllegalStateException("fst reader missing")
    var arc: FST.Arc<FSTOrdsOutputs.Output>
    var targetUpto: Int
    var output: FSTOrdsOutputs.Output
    targetBeforeCurrentLength = currentFrame.ord

    if (positioned && currentFrame !== staticFrame) {
      arc = arcs[0]
      output = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
      targetUpto = 0
      var lastFrame: OrdsSegmentTermsEnumFrame = stack[0]!!
      check(validIndexPrefix <= term.length())
      val targetLimit = kotlin.math.min(text.length, validIndexPrefix)
      var cmp = 0

      while (targetUpto < targetLimit) {
        cmp = (term.byteAt(targetUpto).toInt() and 0xFF) - (text.bytes[text.offset + targetUpto].toInt() and 0xFF)
        if (cmp != 0) break
        arc = arcs[1 + targetUpto]
        val arcOut0 = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
        if (arcOut0 != OrdsBlockTreeTermsWriter.NO_OUTPUT) {
          output = OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, arcOut0)
        }
        if (arc.isFinal) lastFrame = stack[1 + lastFrame.ord]!!
        targetUpto++
       }

      if (cmp == 0) {
        val savedTargetUpto = targetUpto
        val targetLimit2 = kotlin.math.min(text.length, term.length())
        while (targetUpto < targetLimit2) {
          cmp = (term.byteAt(targetUpto).toInt() and 0xFF) - (text.bytes[text.offset + targetUpto].toInt() and 0xFF)
          if (cmp != 0) break
          targetUpto++
        }
        if (cmp == 0) cmp = term.length() - text.length
        targetUpto = savedTargetUpto
      }

      if (cmp < 0) {
        currentFrame = lastFrame
      } else if (cmp > 0) {
        targetBeforeCurrentLength = 0
        currentFrame = lastFrame
        currentFrame.rewind()
      } else {
        check(term.length() == text.length)
        if (termExists) return SeekStatus.FOUND
      }

    } else {

      targetBeforeCurrentLength = -1
      arc = fr.index.getFirstArc(arcs[0])
      check(arc.isFinal)
      output = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
      currentFrame = staticFrame
      targetUpto = 0
      val rootNextFinal = arc.nextFinalOutput() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
      currentFrame = pushFrame(arc, OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, rootNextFinal), 0)
    }

    positioned = true

    while (targetUpto < text.length) {
      val targetLabel = text.bytes[text.offset + targetUpto].toInt() and 0xFF
      val nextArc = fr.index.findTargetArc(targetLabel, arc, getArc(1 + targetUpto), reader)
      if (nextArc == null) {
        validIndexPrefix = currentFrame.prefix
        currentFrame.scanToFloorFrame(text)
        currentFrame.loadBlock()
        val result = currentFrame.scanToTerm(text, false)
        if (result == SeekStatus.END) {
          term.copyBytes(text)
          termExists = false
          return if (next() != null) SeekStatus.NOT_FOUND else SeekStatus.END
        }
        return result
      } else {
        term.setByteAt(targetUpto, text.bytes[text.offset + targetUpto])
        arc = nextArc
        val arcOut1 = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
        if (arcOut1 != OrdsBlockTreeTermsWriter.NO_OUTPUT) {
          output = OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, arcOut1)
        }
        targetUpto++
        if (arc.isFinal) {
          val nextFinal = arc.nextFinalOutput() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
          currentFrame = pushFrame(arc, OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, nextFinal), targetUpto)
        }
      }
    }

    validIndexPrefix = currentFrame.prefix
    currentFrame.scanToFloorFrame(text)
    currentFrame.loadBlock()
    val result = currentFrame.scanToTerm(text, false)
    if (result == SeekStatus.END) {
      term.copyBytes(text)
      termExists = false
      return if (next() != null) SeekStatus.NOT_FOUND else SeekStatus.END
    }
    return result
  }

  @Suppress("unused")
  private fun printSeekState(out: Appendable) {
    if (currentFrame === staticFrame) {
      out.append("  no prior seek\n")
      return
    }
    out.append("  prior seek state:\n")
    var ord = 0
    var isSeekFrame = true
    while (true) {
      val f = getFrame(ord)
      val prefix = BytesRef(term.bytes(), 0, f.prefix)
      if (f.nextEnt == -1) {
        out.append("    frame ")
          .append(if (isSeekFrame) "(seek)" else "(next)")
          .append(" ord=")
          .append(ord.toString())
          .append(" fp=")
          .append(f.fp.toString())
          .append(if (f.isFloor) (" (fpOrig=" + f.fpOrig + ")") else "")
          .append(" prefixLen=")
          .append(f.prefix.toString())
          .append(" prefix=")
          .append(prefix.utf8ToString())
          .append(if (f.nextEnt == -1) "" else (" (of " + f.entCount + ")"))
          .append(" hasTerms=")
          .append(f.hasTerms.toString())
          .append(" isFloor=")
          .append(f.isFloor.toString())
          .append(" isLastInFloor=")
          .append(f.isLastInFloor.toString())
          .append(" mdUpto=")
          .append(f.metaDataUpto.toString())
          .append(" tbOrd=")
          .append(f.getTermBlockOrd().toString())
          .append(" termOrd=")
          .append(f.termOrd.toString())
          .append('\n')
      } else {
        out.append("    frame ")
          .append(if (isSeekFrame) "(seek, loaded)" else "(next, loaded)")
          .append(" ord=")
          .append(ord.toString())
          .append(" fp=")
          .append(f.fp.toString())
          .append(if (f.isFloor) (" (fpOrig=" + f.fpOrig + ")") else "")
          .append(" prefixLen=")
          .append(f.prefix.toString())
          .append(" prefix=")
          .append(prefix.utf8ToString())
          .append(" nextEnt=")
          .append(f.nextEnt.toString())
          .append(if (f.nextEnt == -1) "" else (" (of " + f.entCount + ")"))
          .append(" hasTerms=")
          .append(f.hasTerms.toString())
          .append(" isFloor=")
          .append(f.isFloor.toString())
          .append(" lastSubFP=")
          .append(f.lastSubFP.toString())
          .append(" isLastInFloor=")
          .append(f.isLastInFloor.toString())
          .append(" mdUpto=")
          .append(f.metaDataUpto.toString())
          .append(" tbOrd=")
          .append(f.getTermBlockOrd().toString())
          .append(" termOrd=")
          .append(f.termOrd.toString())
          .append('\n')
      }
      if (fr.index != null) {
        check(!isSeekFrame || f.arc != null)
        if (f.prefix > 0 && isSeekFrame && f.arc!!.label() != (term.byteAt(f.prefix - 1).toInt() and 0xFF)) {
          out.append("      broken seek state: arc.label=" + f.arc!!.label() + " vs term byte=" + (term.byteAt(f.prefix - 1).toInt() and 0xFF) + "\n")
          throw RuntimeException("seek state is broken")
        }
        val output = Util.get(fr.index, BytesRef(term.bytes(), 0, f.prefix))
        if (output == null) {
          out.append("      broken seek state: prefix is not final in index\n")
          throw RuntimeException("seek state is broken")
        } else if (isSeekFrame && !f.isFloor) {
          val outBytes = output.bytes()
          val reader = ByteArrayDataInput(outBytes.bytes, outBytes.offset, outBytes.length)
          val codeOrig = reader.readVLong()
          val code = (f.fp shl OrdsBlockTreeTermsWriter.OUTPUT_FLAGS_NUM_BITS) or
            (if (f.hasTerms) OrdsBlockTreeTermsWriter.OUTPUT_FLAG_HAS_TERMS.toLong() else 0L) or
            (if (f.isFloor) OrdsBlockTreeTermsWriter.OUTPUT_FLAG_IS_FLOOR.toLong() else 0L)
          if (codeOrig != code) {
            out.append("      broken seek state: output code=${codeOrig} doesn't match frame code=${code}\n")
            throw RuntimeException("seek state is broken")
          }
        }
      }
      if (f === currentFrame) break
      if (f.prefix == validIndexPrefix) isSeekFrame = false
      ord++
    }
  }

  override fun next(): BytesRef? {
    if (this.`in` == null) {
      val arc: FST.Arc<FSTOrdsOutputs.Output>? = if (fr.index != null) {
          val a = fr.index.getFirstArc(arcs[0])
        check(a.isFinal)
        a
      } else null
      currentFrame = pushFrame(arc, fr.rootCode, 0)
      currentFrame.loadBlock()
      positioned = true
    }

    targetBeforeCurrentLength = currentFrame.ord
    check(!eof)

    if (currentFrame === staticFrame || !positioned) {
      val result = seekExact(term.get())
      check(result)
    }

    while (currentFrame.nextEnt == currentFrame.entCount) {
      if (!currentFrame.isLastInFloor) {
        currentFrame.loadNextFloorBlock()
      } else {
        if (currentFrame.ord == 0) {
          check(setEOF())
          term.setLength(0)
          validIndexPrefix = 0
          currentFrame.rewind()
          termExists = false
          positioned = false
          return null
        }
        val lastFP = currentFrame.fpOrig
        currentFrame = stack[currentFrame.ord - 1]!!
        if (currentFrame.nextEnt == -1 || currentFrame.lastSubFP != lastFP) {
          currentFrame.scanToFloorFrame(term.get())
          currentFrame.loadBlock()
          currentFrame.scanToSubBlock(lastFP)
        }
        validIndexPrefix = kotlin.math.min(validIndexPrefix, currentFrame.prefix)
      }
    }

    while (true) {
      val prevTermOrd = currentFrame.termOrd
      if (currentFrame.next()) {
        currentFrame = pushFrame(null, currentFrame.lastSubFP, term.length(), prevTermOrd)
        currentFrame.isFloor = false
        currentFrame.loadBlock()
      } else {
        positioned = true
        return term.get()
      }
    }
  }

  override fun term(): BytesRef {
    check(!eof)
    return term.get()
  }

  override fun ord(): Long {
    check(!eof)
    check(currentFrame.termOrd > 0)
    return currentFrame.termOrd - 1
  }

  override fun docFreq(): Int {
    check(!eof)
    currentFrame.decodeMetaData()
    return currentFrame.state.docFreq
  }

  override fun totalTermFreq(): Long {
    check(!eof)
    currentFrame.decodeMetaData()
    return currentFrame.state.totalTermFreq
  }

  override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
    check(!eof)
    currentFrame.decodeMetaData()
    return fr.parent.postingsReader.postings(fr.fieldInfo, currentFrame.state, reuse, flags)
  }

  override fun impacts(flags: Int): ImpactsEnum {
    check(!eof)
    currentFrame.decodeMetaData()
    return fr.parent.postingsReader.impacts(fr.fieldInfo, currentFrame.state, flags)
  }

  override fun seekExact(term: BytesRef, state: TermState) {
    val target = term
    check(clearEOF())
    if (target.compareTo(this.term.get()) != 0 || !termExists) {
      check(state is BlockTermState)
      @Suppress("USELESS_CAST")
      val blockState = state as BlockTermState
      currentFrame = staticFrame
      currentFrame.state.copyFrom(state)
      this.term.copyBytes(target)
      currentFrame.metaDataUpto = currentFrame.getTermBlockOrd()
      currentFrame.termOrd = blockState.ord + 1
      check(currentFrame.metaDataUpto > 0)
      validIndexPrefix = 0
    }
    positioned = true
  }

  override fun termState(): TermState {
    check(!eof)
    currentFrame.decodeMetaData()
    val ts = currentFrame.state.clone() as BlockTermState
    check(currentFrame.termOrd > 0)
    ts.ord = currentFrame.termOrd - 1
    return ts
  }

  override fun seekExact(ord: Long) {
    if (ord < 0L || ord >= fr.numTerms) throw IllegalArgumentException("targetOrd out of bounds (got: $ord, numTerms=${fr.numTerms})")
    check(clearEOF())

    val io = getByOutput(ord)
    term.grow(io.input.size)
    for (i in io.input.indices) term.setByteAt(i, io.input[i].toByte())
    term.setLength(io.input.size)

    currentFrame = if (io.input.isEmpty()) staticFrame else getFrame(io.input.size - 1)
    val arcForIo = getArc(io.input.size)

    targetBeforeCurrentLength = Int.MAX_VALUE
    currentFrame = pushFrame(arcForIo, io.output, io.input.size)
    if (currentFrame.termOrd > ord) {
      currentFrame.rewind()
    }

    currentFrame.scanToFloorFrame(ord)
    currentFrame.loadBlock()
    while (currentFrame.termOrd <= ord) {
      currentFrame.next()
    }

    check(currentFrame.termOrd == ord + 1L)
    check(termExists)

    validIndexPrefix = 0
    positioned = false
  }

  // scanToTerm implementation moved to OrdsSegmentTermsEnumFrame to match Java layout.

  override fun toString(): String = "OrdsSegmentTermsEnum(seg=${fr.parent})"

  private class InputOutput(var input: IntArray = IntArray(0), var output: FSTOrdsOutputs.Output? = null) {
    override fun toString(): String = "InputOutput(input=${input.contentToString()} output=$output)"
  }

  private val arc: FST.Arc<FSTOrdsOutputs.Output> = FST.Arc()

  private fun getByOutput(targetOrd: Long): InputOutput {
    val fst = fr.index ?: throw IllegalStateException("terms index was not loaded")
    val reader = fst.getBytesReader()

    var result = IntArray(4)
    var upto = 0
    var resultLen: Int

    fst.getFirstArc(arc)
    var output: FSTOrdsOutputs.Output = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT

    var bestUpto = 0
    var bestOutput: FSTOrdsOutputs.Output? = null

    while (true) {
      if (arc.isFinal) {
        val finalOutput = OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, arc.nextFinalOutput() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT)
        if (targetOrd >= finalOutput.startOrd() && targetOrd <= Long.MAX_VALUE - finalOutput.endOrd()) {
          bestOutput = finalOutput
          bestUpto = upto
        }
      }

      if (FST.targetHasArcs(arc)) {
        if (result.size <= upto) result = result.copyOf(result.size * 2)

        fst.readFirstRealTargetArc(arc.target(), arc, reader)

        if (arc.bytesPerArc() != 0 && arc.nodeFlags() == FST.ARCS_FOR_BINARY_SEARCH) {
          var low = 0
          var high = arc.numArcs() - 1
          var mid = 0
          var found = false
          while (low <= high) {
            mid = (low + high) ushr 1
            reader.position = arc.posArcsStart()
            reader.skipBytes(arc.bytesPerArc() * mid.toLong())
            val flags = reader.readByte()
            fst.readLabel(reader)
            val minArcOutput = if ((flags.toInt() and FST.BIT_ARC_HAS_OUTPUT) != 0) {
              OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, OrdsBlockTreeTermsWriter.FST_OUTPUTS.read(reader))
            } else {
              output
            }
            if (targetOrd > Long.MAX_VALUE - minArcOutput.endOrd()) {
              low = mid + 1
            } else if (targetOrd < minArcOutput.startOrd()) {
              high = mid - 1
            } else {
              found = true
              break
            }
          }

          if (!found) {
            resultLen = bestUpto
            val io = InputOutput(result.copyOf(resultLen), bestOutput)
            return io
          }

          fst.readArcByIndex(arc, reader, mid)
          result[upto++] = arc.label()
          output = OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT)

        } else {
          while (true) {
            val minArcOutput = OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(output, arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT)
            val endOrd = Long.MAX_VALUE - minArcOutput.endOrd()
            if (targetOrd >= minArcOutput.startOrd() && targetOrd <= endOrd) {
              output = minArcOutput
              if (result.size <= upto) result = result.copyOf(result.size * 2)
              result[upto++] = arc.label()
              break
            } else if (targetOrd < endOrd || arc.isLast) {
              resultLen = bestUpto
              val io = InputOutput(result.copyOf(resultLen), bestOutput)
              return io
            } else {
              fst.readNextRealArc(arc, reader)
            }
          }
        }
      } else {
        resultLen = bestUpto
        val io = InputOutput(result.copyOf(resultLen), bestOutput)
        return io
      }
    }
  }
}
