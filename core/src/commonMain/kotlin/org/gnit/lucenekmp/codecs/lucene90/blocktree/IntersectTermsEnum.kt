package org.gnit.lucenekmp.codecs.lucene90.blocktree

import org.gnit.lucenekmp.codecs.lucene90.blocktree.SegmentTermsEnum.OutputAccumulator
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
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.util.automaton.TransitionAccessor
import org.gnit.lucenekmp.util.fst.FST.Arc
import org.gnit.lucenekmp.util.fst.FST.BytesReader
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.System
import kotlin.experimental.and

/**
 * This is used to implement efficient [Terms.intersect] for block-tree. Note that it cannot
 * seek, except for the initial term on init. It just "nexts" through the intersection of the
 * automaton and the terms. It does not use the terms index at all: on init, it loads the root
 * block, and scans its way to the initial term. Likewise, in next it scans until it finds a term
 * that matches the current automaton transition.
 */
internal class IntersectTermsEnum(
    val fr: FieldReader,
    automaton: TransitionAccessor,
    runAutomaton: ByteRunnable,
    commonSuffix: BytesRef,
    startTerm: BytesRef?
) : BaseTermsEnum() {
    // static boolean DEBUG = BlockTreeTermsWriter.DEBUG;
    val `in`: IndexInput

    var stack: Array<IntersectTermsEnumFrame?>

    private var arcs: Array<Arc<BytesRef>?> = kotlin.arrayOfNulls(5)

    val runAutomaton: ByteRunnable
    val automaton: TransitionAccessor
    val commonSuffix: BytesRef?

    private var currentFrame: IntersectTermsEnumFrame?
    private var currentTransition: Transition

    private val term: BytesRef = BytesRef()

    private val fstReader: BytesReader

    private var savedStartTerm: BytesRef? = null

    private val outputAccumulator: OutputAccumulator = OutputAccumulator()

    // TODO: in some cases we can filter by length  eg
    // regexp foo*bar must be at least length 6 bytes
    init {
        checkNotNull(automaton)
        checkNotNull(runAutomaton)

        this.runAutomaton = runAutomaton
        this.automaton = automaton
        this.commonSuffix = commonSuffix

        `in` = fr.parent.termsIn.clone()
        stack = kotlin.arrayOfNulls<IntersectTermsEnumFrame>(5)
        for (idx in stack.indices) {
            stack[idx] = IntersectTermsEnumFrame(this, idx)
        }
        for (arcIdx in arcs.indices) {
            arcs[arcIdx] = Arc()
        }

        fstReader = fr.index.getBytesReader()

        // TODO: if the automaton is "smallish" we really
        // should use the terms index to seek at least to
        // the initial term and likely to subsequent terms
        // (or, maybe just fallback to ATE for such cases).
        // Else the seek cost of loading the frames will be
        // too costly.
        val arc: Arc<BytesRef> = fr.index.getFirstArc(arcs[0]!!)
        // Empty string prefix must have an output in the index!
        require(arc.isFinal)

        // Special pushFrame since it's the first one:
        val f: IntersectTermsEnumFrame = stack[0]!!
        f.fpOrig = fr.rootBlockFP
        f.fp = f.fpOrig
        f.prefix = 0
        f.setState(0)
        f.arc = arc
        f.load(fr.rootCode)

        // for assert:
        require(setSavedStartTerm(startTerm))

        currentFrame = f
        outputAccumulator.push(currentFrame!!.arc!!.output()!!)

        if (startTerm != null) {
            seekToStartTerm(startTerm)
        }
        currentTransition = currentFrame!!.transition
    }

    // only for assert:
    private fun setSavedStartTerm(startTerm: BytesRef?): Boolean {
        savedStartTerm = if (startTerm == null) null else BytesRef.deepCopyOf(startTerm)
        return true
    }

    @Throws(IOException::class)
    override fun termState(): TermState {
        currentFrame!!.decodeMetaData()
        return currentFrame!!.termState.clone()
    }

    @Throws(IOException::class)
    private fun getFrame(ord: Int): IntersectTermsEnumFrame {
        if (ord >= stack.size) {
            val next: Array<IntersectTermsEnumFrame?> =
                kotlin.arrayOfNulls(
                    ArrayUtil.oversize(
                        1 + ord,
                        RamUsageEstimator.NUM_BYTES_OBJECT_REF
                    )
                )
            System.arraycopy(stack, 0, next, 0, stack.size)
            for (stackOrd in stack.size..<next.size) {
                next[stackOrd] = IntersectTermsEnumFrame(this, stackOrd)
            }
            stack = next
        }
        require(stack[ord]!!.ord == ord)
        return stack[ord]!!
    }

    private fun getArc(ord: Int): Arc<BytesRef> {
        if (ord >= arcs.size) {
            val next: Array<Arc<BytesRef>?> =
                kotlin.arrayOfNulls(ArrayUtil.oversize(1 + ord, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
            System.arraycopy(arcs, 0, next, 0, arcs.size)
            for (arcOrd in arcs.size..<next.size) {
                next[arcOrd] = Arc()
            }
            arcs = next
        }
        return arcs[ord]!!
    }

    @Throws(IOException::class)
    private fun pushFrame(state: Int): IntersectTermsEnumFrame {
        checkNotNull(currentFrame)

        val f: IntersectTermsEnumFrame = getFrame(if (currentFrame == null) 0 else 1 + currentFrame!!.ord)

        f.fpOrig = currentFrame!!.lastSubFP
        f.fp = f.fpOrig
        f.prefix = currentFrame!!.prefix + currentFrame!!.suffix
        f.setState(state)

        // Walk the arc through the index -- we only
        // "bother" with this so we can get the floor data
        // from the index and skip floor blocks when
        // possible:
        var arc: Arc<BytesRef>? = currentFrame!!.arc
        var idx: Int = currentFrame!!.prefix
        require(currentFrame!!.suffix > 0)

        val initOutputCount: Int = outputAccumulator.outputCount()
        while (idx < f.prefix) {
            val target: Int = (term.bytes[idx] and 0xff.toByte()).toInt()
            // TODO: we could be more efficient for the next()
            // case by using current arc as starting point,
            // passed to findTargetArc
            arc = fr.index.findTargetArc(target, arc!!, getArc(1 + idx), fstReader)
            checkNotNull(arc)
            outputAccumulator.push(arc.output()!!)
            idx++
        }

        f.arc = arc
        f.outputNum = outputAccumulator.outputCount() - initOutputCount
        require(arc!!.isFinal)
        outputAccumulator.push(arc.nextFinalOutput()!!)
        f.load(outputAccumulator)
        outputAccumulator.pop(arc.nextFinalOutput()!!)
        return f
    }

    override fun term(): BytesRef {
        return term
    }

    @Throws(IOException::class)
    override fun docFreq(): Int {
        currentFrame!!.decodeMetaData()
        return currentFrame!!.termState.docFreq
    }

    @Throws(IOException::class)
    override fun totalTermFreq(): Long {
        currentFrame!!.decodeMetaData()
        return currentFrame!!.termState.totalTermFreq
    }

    @Throws(IOException::class)
    override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
        currentFrame!!.decodeMetaData()
        return fr.parent.postingsReader.postings(fr.fieldInfo, currentFrame!!.termState, reuse, flags)
    }

    @Throws(IOException::class)
    override fun impacts(flags: Int): ImpactsEnum {
        currentFrame!!.decodeMetaData()
        return fr.parent.postingsReader.impacts(fr.fieldInfo, currentFrame!!.termState, flags)
    }

    private val state: Int
        get() {
            var state: Int = currentFrame!!.state
            for (idx in 0..<currentFrame!!.suffix) {
                state =
                    runAutomaton.step(
                        state, (currentFrame!!.suffixBytes[currentFrame!!.startBytePos + idx] and 0xff.toByte()).toInt()
                    )
                require(state != -1)
            }
            return state
        }

    // NOTE: specialized to only doing the first-time
    // seek, but we could generalize it to allow
    // arbitrary seekExact/Ceil.  Note that this is a
    // seekFloor!
    @Throws(IOException::class)
    private fun seekToStartTerm(target: BytesRef) {
        require(currentFrame!!.ord == 0)
        if (term.length < target.length) {
            term.bytes = ArrayUtil.grow(term.bytes, target.length)
        }
        val arc: Arc<BytesRef>? = arcs[0]
        require(arc === currentFrame!!.arc)

        for (idx in 0..target.length) {
            while (true) {
                val savNextEnt: Int = currentFrame!!.nextEnt
                val savePos: Int = currentFrame!!.suffixesReader.getPosition()
                val saveLengthPos: Int = currentFrame!!.suffixLengthsReader.getPosition()
                val saveStartBytePos: Int = currentFrame!!.startBytePos
                val saveSuffix: Int = currentFrame!!.suffix
                val saveLastSubFP: Long = currentFrame!!.lastSubFP
                val saveTermBlockOrd: Int = currentFrame!!.termState.termBlockOrd

                val isSubBlock: Boolean = currentFrame!!.next()

                term.length = currentFrame!!.prefix + currentFrame!!.suffix
                if (term.bytes.size < term.length) {
                    term.bytes = ArrayUtil.grow(term.bytes, term.length)
                }
                System.arraycopy(
                    currentFrame!!.suffixBytes,
                    currentFrame!!.startBytePos,
                    term.bytes,
                    currentFrame!!.prefix,
                    currentFrame!!.suffix
                )

                if (isSubBlock && StringHelper.startsWith(target, term)) {
                    // Recurse
                    currentFrame = pushFrame(this.state)
                    break
                } else {
                    val cmp = term.compareTo(target)
                    if (cmp < 0) {
                        if (currentFrame!!.nextEnt == currentFrame!!.entCount) {
                            if (!currentFrame!!.isLastInFloor) {
                                // Advance to next floor block
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
                        // Fallback to prior entry: the semantics of
                        // this method is that the first call to
                        // next() will return the term after the
                        // requested term
                        currentFrame!!.nextEnt = savNextEnt
                        currentFrame!!.lastSubFP = saveLastSubFP
                        currentFrame!!.startBytePos = saveStartBytePos
                        currentFrame!!.suffix = saveSuffix
                        currentFrame!!.suffixesReader.setPosition(savePos)
                        currentFrame!!.suffixLengthsReader.setPosition(saveLengthPos)
                        currentFrame!!.termState.termBlockOrd = saveTermBlockOrd
                        System.arraycopy(
                            currentFrame!!.suffixBytes,
                            currentFrame!!.startBytePos,
                            term.bytes,
                            currentFrame!!.prefix,
                            currentFrame!!.suffix
                        )
                        term.length = currentFrame!!.prefix + currentFrame!!.suffix
                        // If the last entry was a block we don't
                        // need to bother recursing and pushing to
                        // the last term under it because the first
                        // next() will simply skip the frame anyway
                        return
                    }
                }
            }
        }

        require(false)
    }

    @Throws(IOException::class)
    private fun popPushNext(): Boolean {
        // Pop finished frames
        while (currentFrame!!.nextEnt == currentFrame!!.entCount) {
            if (!currentFrame!!.isLastInFloor) {
                // Advance to next floor block
                currentFrame!!.loadNextFloorBlock()
                break
            } else {
                if (currentFrame!!.ord == 0) {
                    throw NoMoreTermsException.Companion.INSTANCE
                }
                val lastFP: Long = currentFrame!!.fpOrig
                outputAccumulator.pop(currentFrame!!.outputNum)
                currentFrame = stack[currentFrame!!.ord - 1]
                currentTransition = currentFrame!!.transition
                require(currentFrame!!.lastSubFP == lastFP)
            }
        }

        return currentFrame!!.next()
    }

    // Only used internally when there are no more terms in next():
    private class NoMoreTermsException : RuntimeException() {
        fun fillInStackTrace(): Throwable {
            // Do nothing:
            return this
        }

        companion object {
            // Only used internally when there are no more terms in next():
            val INSTANCE: NoMoreTermsException = NoMoreTermsException()
        }
    }

    @Throws(IOException::class)
    override fun next(): BytesRef? {
        try {
            return _next()
        } catch (eoi: NoMoreTermsException) {
            // Provoke NPE if we are (illegally!) called again:
            currentFrame = null
            return null
        }
    }

    @Throws(IOException::class)
    private fun _next(): BytesRef? {
        var isSubBlock = popPushNext()

        nextTerm@ while (true) {
            require(currentFrame!!.transition === currentTransition)

            var state: Int
            var lastState: Int

            // NOTE: suffix == 0 can only happen on the first term in a block, when
            // there is a term exactly matching a prefix in the index.  If we
            // could somehow re-org the code so we only checked this case immediately
            // after pushing a frame...
            if (currentFrame!!.suffix != 0) {
                val suffixBytes: ByteArray = currentFrame!!.suffixBytes

                // This is the first byte of the suffix of the term we are now on:
                val label = suffixBytes[currentFrame!!.startBytePos].toInt() and 0xff

                if (label < currentTransition.min) {
                    // Common case: we are scanning terms in this block to "catch up" to
                    // current transition in the automaton:
                    val minTrans: Int = currentTransition.min
                    while (currentFrame!!.nextEnt < currentFrame!!.entCount) {
                        isSubBlock = currentFrame!!.next()
                        if ((suffixBytes[currentFrame!!.startBytePos].toInt() and 0xff) >= minTrans) {
                            continue@nextTerm
                        }
                    }

                    // End of frame:
                    isSubBlock = popPushNext()
                    continue@nextTerm
                }

                // Advance where we are in the automaton to match this label:
                while (label > currentTransition.max) {
                    if (currentFrame!!.transitionIndex >= currentFrame!!.transitionCount - 1) {
                        // Pop this frame: no further matches are possible because
                        // we've moved beyond what the max transition will allow
                        if (currentFrame!!.ord == 0) {
                            // Provoke NPE if we are (illegally!) called again:
                            currentFrame = null
                            return null
                        }
                        outputAccumulator.pop(currentFrame!!.outputNum)
                        currentFrame = stack[currentFrame!!.ord - 1]
                        currentTransition = currentFrame!!.transition
                        isSubBlock = popPushNext()
                        continue@nextTerm
                    }
                    currentFrame!!.transitionIndex++
                    automaton.getNextTransition(currentTransition)

                    if (label < currentTransition.min) {
                        val minTrans: Int = currentTransition.min
                        while (currentFrame!!.nextEnt < currentFrame!!.entCount) {
                            isSubBlock = currentFrame!!.next()
                            if ((suffixBytes[currentFrame!!.startBytePos].toInt() and 0xff) >= minTrans) {
                                continue@nextTerm
                            }
                        }

                        // End of frame:
                        isSubBlock = popPushNext()
                        continue@nextTerm
                    }
                }

                if (commonSuffix != null && !isSubBlock) {
                    val termLen: Int = currentFrame!!.prefix + currentFrame!!.suffix
                    if (termLen < commonSuffix.length) {
                        // No match
                        isSubBlock = popPushNext()
                        continue@nextTerm
                    }

                    val commonSuffixBytes: ByteArray = commonSuffix.bytes

                    val lenInPrefix: Int = commonSuffix.length - currentFrame!!.suffix
                    require(commonSuffix.offset == 0)
                    var suffixBytesPos: Int
                    var commonSuffixBytesPos = 0

                    if (lenInPrefix > 0) {
                        // A prefix of the common suffix overlaps with
                        // the suffix of the block prefix so we first
                        // test whether the prefix part matches:
                        val termBytes: ByteArray = term.bytes
                        var termBytesPos: Int = currentFrame!!.prefix - lenInPrefix
                        require(termBytesPos >= 0)
                        val termBytesPosEnd: Int = currentFrame!!.prefix
                        while (termBytesPos < termBytesPosEnd) {
                            if (termBytes[termBytesPos++] != commonSuffixBytes[commonSuffixBytesPos++]) {
                                isSubBlock = popPushNext()
                                continue@nextTerm
                            }
                        }
                        suffixBytesPos = currentFrame!!.startBytePos
                    } else {
                        suffixBytesPos = currentFrame!!.startBytePos + currentFrame!!.suffix - commonSuffix.length
                    }

                    // Test overlapping suffix part:
                    val commonSuffixBytesPosEnd: Int = commonSuffix.length
                    while (commonSuffixBytesPos < commonSuffixBytesPosEnd) {
                        if (suffixBytes[suffixBytesPos++] != commonSuffixBytes[commonSuffixBytesPos++]) {
                            isSubBlock = popPushNext()
                            continue@nextTerm
                        }
                    }
                }

                // TODO: maybe we should do the same linear test
                // that AutomatonTermsEnum does, so that if we
                // reach a part of the automaton where .* is
                // "temporarily" accepted, we just blindly .next()
                // until the limit

                // See if the term suffix matches the automaton:

                // We know from above that the first byte in our suffix (label) matches
                // the current transition, so we step from the 2nd byte
                // in the suffix:
                lastState = currentFrame!!.state
                state = currentTransition.dest

                val end: Int = currentFrame!!.startBytePos + currentFrame!!.suffix
                for (idx in currentFrame!!.startBytePos + 1..<end) {
                    lastState = state
                    state = runAutomaton.step(state, suffixBytes[idx].toInt() and 0xff)
                    if (state == -1) {
                        // No match
                        isSubBlock = popPushNext()
                        continue@nextTerm
                    }
                }
            } else {
                state = currentFrame!!.state
                lastState = currentFrame!!.lastState
            }

            if (isSubBlock) {
                // Match!  Recurse:
                copyTerm()
                currentFrame = pushFrame(state)
                currentTransition = currentFrame!!.transition
                currentFrame!!.lastState = lastState
            } else if (runAutomaton.isAccept(state)) {
                copyTerm()
                require(
                    savedStartTerm == null || term > savedStartTerm!!
                ) { "saveStartTerm=" + savedStartTerm!!.utf8ToString() + " term=" + term.utf8ToString() }
                return term
            } else {
                // This term is a prefix of a term accepted by the automaton, but is not itself accepted
            }

            isSubBlock = popPushNext()
        }
    }

    private fun copyTerm() {
        val len: Int = currentFrame!!.prefix + currentFrame!!.suffix
        if (term.bytes.size < len) {
            term.bytes = ArrayUtil.grow(term.bytes, len)
        }
        System.arraycopy(
            currentFrame!!.suffixBytes,
            currentFrame!!.startBytePos,
            term.bytes,
            currentFrame!!.prefix,
            currentFrame!!.suffix
        )
        term.length = len
    }

    override fun seekExact(text: BytesRef): Boolean {
        throw UnsupportedOperationException()
    }

    override fun seekExact(ord: Long) {
        throw UnsupportedOperationException()
    }

    override fun ord(): Long {
        throw UnsupportedOperationException()
    }

    override fun seekCeil(text: BytesRef): SeekStatus {
        throw UnsupportedOperationException()
    }
}
