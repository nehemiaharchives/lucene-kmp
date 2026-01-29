/*
 * Kotlin port of org.apache.lucene.codecs.blocktreeords.OrdsIntersectTermsEnum
 * Preserves structure and behavior of the upstream Java implementation while
 * using Kotlin idioms where safe. Keep side-by-side comparison easy.
 */

package org.gnit.lucenekmp.codecs.blocktreeords

import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.automaton.ByteRunnable
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.TransitionAccessor
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.jdkport.assert

// NOTE: cannot seek!
internal class OrdsIntersectTermsEnum(
  internal val fr: OrdsFieldReader,
  compiled: CompiledAutomaton,
  startTerm: BytesRef?
) : BaseTermsEnum() {

  internal val `in`: IndexInput = (fr.parent.`in` ?: throw IllegalStateException("terms input is null")).clone()

  private var stack: Array<OrdsIntersectTermsEnumFrame?> = arrayOfNulls(5)

  @Suppress("UNCHECKED_CAST")
  private var arcs: Array<FST.Arc<FSTOrdsOutputs.Output>> = Array(5) { FST.Arc() }

  internal val byteRunnable: ByteRunnable = (compiled.runAutomaton ?: compiled.nfaRunAutomaton) as ByteRunnable
  internal val transitionAccessor: TransitionAccessor = (compiled.automaton ?: compiled.nfaRunAutomaton) as TransitionAccessor
  private val commonSuffixRef: BytesRef? = compiled.commonSuffixRef

  private var currentFrame: OrdsIntersectTermsEnumFrame? = null

  private val term: BytesRef = BytesRef()

  private val fstReader: FST.BytesReader? = fr.index?.getBytesReader()

  // TODO: if the automaton is "smallish" we really
  // should use the terms index to seek at least to
  // the initial term and likely to subsequent terms
  // (or, maybe just fallback to ATE for such cases).
  // Else the seek cost of loading the frames will be
  // too costly.

  private var savedStartTerm: BytesRef? = null

  init {
    // initialize stack and arcs
    for (idx in stack.indices) stack[idx] = OrdsIntersectTermsEnumFrame(this, idx)
    for (arcIdx in arcs.indices) arcs[arcIdx] = FST.Arc()

    val arc = fr.index!!.getFirstArc(arcs[0])
    assert(arc.isFinal)

    // setup root frame
    val f = stack[0]!!
    f.fp = fr.rootBlockFP
    f.fpOrig = fr.rootBlockFP
    f.prefix = 0
    f.state = 0
    f.arc = arc
    f.outputPrefix = arc.output() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
    f.load(fr.rootCode)

    // for assert
    setSavedStartTerm(startTerm)

    currentFrame = f
    if (startTerm != null) seekToStartTerm(startTerm)
  }

  // only for assert
  private fun setSavedStartTerm(startTerm: BytesRef?): Boolean {
    savedStartTerm = startTerm?.let { BytesRef.deepCopyOf(it) }
    return true
  }

  override fun termState(): TermState {
    currentFrame!!.decodeMetaData()
    return currentFrame!!.termState.clone()
  }

    private fun getFrame(ord: Int): OrdsIntersectTermsEnumFrame {
    if (ord >= stack.size) {
      val next = arrayOfNulls<OrdsIntersectTermsEnumFrame>(ArrayUtil.oversize(1 + ord, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
      stack.copyInto(next, 0, 0, stack.size)
      for (stackOrd in stack.size until next.size) next[stackOrd] = OrdsIntersectTermsEnumFrame(this, stackOrd)
      stack = next
    }
    assert(stack[ord]!!.ord == ord)
    return stack[ord]!!
  }

  private fun getArc(ord: Int): FST.Arc<FSTOrdsOutputs.Output> {
    if (ord >= arcs.size) {
      val next = arrayOfNulls<FST.Arc<FSTOrdsOutputs.Output>>(ArrayUtil.oversize(1 + ord, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
      arcs.copyInto(next, 0, 0, arcs.size)
      for (arcOrd in arcs.size until next.size) next[arcOrd] = FST.Arc()
      arcs = next as Array<FST.Arc<FSTOrdsOutputs.Output>>
    }
    return arcs[ord]
  }

    private fun pushFrame(state: Int): OrdsIntersectTermsEnumFrame {
    val f = getFrame(if (currentFrame == null) 0 else 1 + currentFrame!!.ord)

    f.fp = currentFrame!!.lastSubFP
    f.fpOrig = currentFrame!!.lastSubFP
    f.prefix = currentFrame!!.prefix + currentFrame!!.suffix
    f.state = state

    var arc: FST.Arc<FSTOrdsOutputs.Output> = currentFrame!!.arc!!
    val idx = currentFrame!!.prefix
    val output = currentFrame!!.outputPrefix ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
    val out = output

    while (idx < f.prefix) {
      val target = term.bytes[idx].toInt() and 0xff
      arc = fr.index!!.findTargetArc(target, arc, getArc(1 + idx), fstReader!!)!!
    }

    f.arc = arc
    f.outputPrefix = out
    assert(arc.isFinal)
    val nextFinal = arc.nextFinalOutput() ?: OrdsBlockTreeTermsWriter.NO_OUTPUT
    f.load(OrdsBlockTreeTermsWriter.FST_OUTPUTS.add(out, nextFinal))
    return f
  }

  override fun term(): BytesRef = term

  override fun docFreq(): Int {
    currentFrame!!.decodeMetaData()
    return currentFrame!!.termState.docFreq
  }

    override fun totalTermFreq(): Long {
    currentFrame!!.decodeMetaData()
    return currentFrame!!.termState.totalTermFreq
  }

  override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
    currentFrame!!.decodeMetaData()
    return fr.parent.postingsReader.postings(fr.fieldInfo, currentFrame!!.termState, reuse, flags)
  }

  override fun impacts(flags: Int): ImpactsEnum {
    currentFrame!!.decodeMetaData()
    return fr.parent.postingsReader.impacts(fr.fieldInfo, currentFrame!!.termState, flags)
  }

  private fun getState(): Int {
    var state = currentFrame!!.state
    for (idx in 0 until currentFrame!!.suffix) {
      state = byteRunnable.step(state, currentFrame!!.suffixBytes[currentFrame!!.startBytePos + idx].toInt() and 0xff)
      check(state != -1)
    }
    return state
  }

  private fun seekToStartTerm(target: BytesRef) {
    assert(currentFrame!!.ord == 0)
    if (term.length < target.length) term.bytes = ArrayUtil.grow(term.bytes, target.length)
    var arc = arcs[0]
    check(arc == currentFrame!!.arc)

    for (idx in 0..target.length) {
      while (true) {
        val savePos = currentFrame!!.suffixesReader.position
        val saveStartBytePos = currentFrame!!.startBytePos
        val saveSuffix = currentFrame!!.suffix
        val saveLastSubFP = currentFrame!!.lastSubFP
        val saveTermBlockOrd = currentFrame!!.termState.termBlockOrd

        val isSubBlock = currentFrame!!.next()

        term.length = currentFrame!!.prefix + currentFrame!!.suffix
        if (term.bytes.size < term.length) term.bytes = ArrayUtil.grow(term.bytes, term.length)
        currentFrame!!.suffixBytes.copyInto(term.bytes, currentFrame!!.prefix, currentFrame!!.startBytePos, currentFrame!!.startBytePos + currentFrame!!.suffix)

        if (isSubBlock && StringHelper.startsWith(target, term)) {
          currentFrame = pushFrame(getState())
          break
        } else {
          val cmp = term.compareTo(target)
          if (cmp < 0) {
            if (currentFrame!!.nextEnt == currentFrame!!.entCount) {
              if (!currentFrame!!.isLastInFloor) {
                currentFrame!!.loadNextFloorBlock()
                continue
              } else {
                return
              }
            }
            continue
          } else if (cmp == 0) {
            return
          } else {
            currentFrame!!.nextEnt--
            currentFrame!!.lastSubFP = saveLastSubFP
            currentFrame!!.startBytePos = saveStartBytePos
            currentFrame!!.suffix = saveSuffix
            currentFrame!!.suffixesReader.position = savePos
            currentFrame!!.termState.termBlockOrd = saveTermBlockOrd
            currentFrame!!.suffixBytes.copyInto(term.bytes, currentFrame!!.prefix, currentFrame!!.startBytePos, currentFrame!!.startBytePos + currentFrame!!.suffix)
            term.length = currentFrame!!.prefix + currentFrame!!.suffix
            return
          }
        }
      }
    }

    throw AssertionError()
  }

  override fun next(): BytesRef? {
    nextTerm@ while (true) {
      while (currentFrame!!.nextEnt == currentFrame!!.entCount) {
        if (!currentFrame!!.isLastInFloor) {
          currentFrame!!.loadNextFloorBlock()
        } else {
          if (currentFrame!!.ord == 0) return null
          val lastFP = currentFrame!!.fpOrig
          currentFrame = stack[currentFrame!!.ord - 1]
          assert(currentFrame!!.lastSubFP == lastFP)
        }
      }

      val isSubBlock = currentFrame!!.next()

      if (currentFrame!!.suffix != 0) {
        val label = currentFrame!!.suffixBytes[currentFrame!!.startBytePos].toInt() and 0xff
        while (label > currentFrame!!.curTransitionMax) {
          if (currentFrame!!.transitionIndex >= currentFrame!!.transitionCount - 1) {
            currentFrame!!.isLastInFloor = true
            currentFrame!!.nextEnt = currentFrame!!.entCount
            continue@nextTerm
          }
          currentFrame!!.transitionIndex++
          transitionAccessor.getNextTransition(currentFrame!!.transition)
          currentFrame!!.curTransitionMax = currentFrame!!.transition.max
        }
      }

      if (commonSuffixRef != null && !isSubBlock) {
        val termLen = currentFrame!!.prefix + currentFrame!!.suffix
        if (termLen < commonSuffixRef.length) continue@nextTerm

        val suffixBytes = currentFrame!!.suffixBytes
        val commonSuffixBytes = commonSuffixRef.bytes

        val lenInPrefix = commonSuffixRef.length - currentFrame!!.suffix
        assert(commonSuffixRef.offset == 0)
        var suffixBytesPos: Int
        var commonSuffixBytesPos = 0

        if (lenInPrefix > 0) {
          val termBytes = term.bytes
          var termBytesPos = currentFrame!!.prefix - lenInPrefix
          assert(termBytesPos >= 0)
          val termBytesPosEnd = currentFrame!!.prefix
          while (termBytesPos < termBytesPosEnd) {
            if (termBytes[termBytesPos++] != commonSuffixBytes[commonSuffixBytesPos++]) continue@nextTerm
          }
          suffixBytesPos = currentFrame!!.startBytePos
        } else {
          suffixBytesPos = currentFrame!!.startBytePos + currentFrame!!.suffix - commonSuffixRef.length
        }

        val commonSuffixBytesPosEnd = commonSuffixRef.length
        while (commonSuffixBytesPos < commonSuffixBytesPosEnd) {
          if (suffixBytes[suffixBytesPos++] != commonSuffixBytes[commonSuffixBytesPos++]) continue@nextTerm
        }
      }

      var state = currentFrame!!.state
      for (idx in 0 until currentFrame!!.suffix) {
        state = byteRunnable.step(state, currentFrame!!.suffixBytes[currentFrame!!.startBytePos + idx].toInt() and 0xff)
        if (state == -1) continue@nextTerm
      }

      if (isSubBlock) {
        copyTerm()
        currentFrame = pushFrame(state)
      } else if (byteRunnable.isAccept(state)) {
        copyTerm()
        val sst = savedStartTerm
        check(sst == null || term.compareTo(sst) > 0)
        return term
      } else {
        // no-op
      }
    }
  }

  private fun copyTerm() {
    val len = currentFrame!!.prefix + currentFrame!!.suffix
    if (term.bytes.size < len) term.bytes = ArrayUtil.grow(term.bytes, len)
    currentFrame!!.suffixBytes.copyInto(term.bytes, currentFrame!!.prefix, currentFrame!!.startBytePos, currentFrame!!.startBytePos + currentFrame!!.suffix)
    term.length = len
  }

  override fun seekExact(text: BytesRef): Boolean { throw UnsupportedOperationException() }
  override fun seekExact(ord: Long) { throw UnsupportedOperationException() }
  override fun ord(): Long { throw UnsupportedOperationException() }
  override fun seekCeil(text: BytesRef): SeekStatus { throw UnsupportedOperationException() }
}
