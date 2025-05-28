package org.gnit.lucenekmp.codecs.lucene90.blocktree

import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOBooleanSupplier
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.fst.FST.Arc
import org.gnit.lucenekmp.util.fst.FST.BytesReader
import org.gnit.lucenekmp.util.fst.Util
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import kotlin.experimental.and
import kotlin.math.min
//import java.io.PrintStream

/** Iterates through terms in this field.  */
class SegmentTermsEnum(val fr: FieldReader) : BaseTermsEnum() {
    // Lazy init:
    var `in`: IndexInput? = null

    private var stack: Array<SegmentTermsEnumFrame?>
    private val staticFrame: SegmentTermsEnumFrame
    var currentFrame: SegmentTermsEnumFrame
    var termExists: Boolean = false

    private var targetBeforeCurrentLength = 0

    // static boolean DEBUG = BlockTreeTermsWriter.DEBUG;
    private val outputAccumulator = OutputAccumulator()

    // What prefix of the current term was present in the index; when we only next() through the
    // index, this stays at 0.  It's only set when
    // we seekCeil/Exact:
    private var validIndexPrefix: Int

    // assert only:
    private var eof = false

    val term: BytesRefBuilder = BytesRefBuilder()
    private val fstReader: BytesReader?

    private var arcs: Array<Arc<BytesRef>?> = kotlin.arrayOfNulls(1)

    init {
        // if (DEBUG) {
        //   System.out.println("BTTR.init seg=" + fr.parent.segment);
        // }
        stack = kotlin.arrayOfNulls<SegmentTermsEnumFrame>(0)

        // Used to hold seek by TermState, or cached seek
        staticFrame = SegmentTermsEnumFrame(this, -1)

        fstReader = if (fr.index == null) {
            null
        } else {
            fr.index.getBytesReader()
        }

        // Init w/ root block; don't use index since it may
        // not (and need not) have been loaded
        for (arcIdx in arcs.indices) {
            arcs[arcIdx] = Arc()
        }

        currentFrame = staticFrame
        val arc: Arc<BytesRef>?
        if (fr.index != null) {
            arc = fr.index.getFirstArc(arcs[0]!!)
            // Empty string prefix must have an output in the index!
            require(arc.isFinal)
        } else {
            arc = null
        }
        // currentFrame = pushFrame(arc, rootCode, 0);
        // currentFrame.loadBlock();
        validIndexPrefix = 0

        // if (DEBUG) {
        //   System.out.println("init frame state " + currentFrame.ord);
        //   printSeekState();
        // }

        // System.out.println();
        // computeBlockStats().print(System.out);
    }

    // Not private to avoid synthetic access$NNN methods
    fun initIndexInput() {
        if (this.`in` == null) {
            this.`in` = fr.parent.termsIn.clone()
        }
    }

    /** Runs next() through the entire terms dict, computing aggregate statistics.  */
    @Throws(IOException::class)
    fun computeBlockStats(): Stats {
        val stats = Stats(fr.parent.segment, fr.fieldInfo.name)
        if (fr.index != null) {
            stats.indexNumBytes = fr.index.ramBytesUsed()
        }

        currentFrame = staticFrame
        var arc: Arc<BytesRef>?
        if (fr.index != null) {
            arc = fr.index.getFirstArc(arcs[0]!!)
            // Empty string prefix must have an output in the index!
            require(arc.isFinal)
        } else {
            arc = null
        }

        // Empty string prefix must have an output in the
        // index!
        currentFrame = pushFrame(arc!!, fr.rootCode, 0)
        currentFrame.fpOrig = currentFrame.fp
        currentFrame.loadBlock()
        validIndexPrefix = 0

        stats.startBlock(currentFrame, !currentFrame.isLastInFloor)

        allTerms@ while (true) {
            // Pop finished blocks

            while (currentFrame.nextEnt == currentFrame.entCount) {
                stats.endBlock(currentFrame)
                if (!currentFrame.isLastInFloor) {
                    // Advance to next floor block
                    currentFrame.loadNextFloorBlock()
                    stats.startBlock(currentFrame, true)
                    break
                } else {
                    if (currentFrame.ord == 0) {
                        break@allTerms
                    }
                    val lastFP = currentFrame.fpOrig
                    currentFrame = stack[currentFrame.ord - 1]!!
                    require(lastFP == currentFrame.lastSubFP)
                    // if (DEBUG) {
                    //   System.out.println("  reset validIndexPrefix=" + validIndexPrefix);
                    // }
                }
            }

            while (true) {
                if (currentFrame.next()) {
                    // Push to new block:
                    currentFrame = pushFrame(null, currentFrame.lastSubFP, term.length())
                    currentFrame.fpOrig = currentFrame.fp
                    // This is a "next" frame -- even if it's
                    // floor'd we must pretend it isn't so we don't
                    // try to scan to the right floor frame:
                    currentFrame.loadBlock()
                    stats.startBlock(currentFrame, !currentFrame.isLastInFloor)
                } else {
                    stats.term(term.get())
                    break
                }
            }
        }

        stats.finish()

        // Put root frame back:
        currentFrame = staticFrame
        if (fr.index != null) {
            arc = fr.index.getFirstArc(arcs[0]!!)
            // Empty string prefix must have an output in the index!
            require(arc.isFinal)
        } else {
            arc = null
        }
        currentFrame = pushFrame(arc, fr.rootCode, 0)
        currentFrame.rewind()
        currentFrame.loadBlock()
        validIndexPrefix = 0
        term.clear()

        return stats
    }

    @Throws(IOException::class)
    private fun getFrame(ord: Int): SegmentTermsEnumFrame {
        if (ord >= stack.size) {
            val next: Array<SegmentTermsEnumFrame?> =
                kotlin.arrayOfNulls(
                    ArrayUtil.oversize(
                        1 + ord,
                        RamUsageEstimator.NUM_BYTES_OBJECT_REF
                    )
                )
            System.arraycopy(stack, 0, next, 0, stack.size)
            for (stackOrd in stack.size..<next.size) {
                next[stackOrd] = SegmentTermsEnumFrame(this, stackOrd)
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
    fun pushFrame(arc: Arc<BytesRef>?, frameData: BytesRef, length: Int): SegmentTermsEnumFrame {
        outputAccumulator.reset()
        outputAccumulator.push(frameData)
        return pushFrame(arc, length)
    }

    // Pushes a frame we seek'd to
    @Throws(IOException::class)
    fun pushFrame(arc: Arc<BytesRef>?, length: Int): SegmentTermsEnumFrame {
        outputAccumulator.prepareRead()
        val code = fr.readVLongOutput(outputAccumulator)
        val fpSeek = code ushr Lucene90BlockTreeTermsReader.OUTPUT_FLAGS_NUM_BITS
        val f = getFrame(1 + currentFrame.ord)
        f.hasTerms = (code and Lucene90BlockTreeTermsReader.OUTPUT_FLAG_HAS_TERMS.toLong()) != 0L
        f.hasTermsOrig = f.hasTerms
        f.isFloor = (code and Lucene90BlockTreeTermsReader.OUTPUT_FLAG_IS_FLOOR.toLong()) != 0L
        if (f.isFloor) {
            f.setFloorData(outputAccumulator)
        }
        pushFrame(arc, fpSeek, length)

        return f
    }

    // Pushes next'd frame or seek'd frame; we later
    // lazy-load the frame only when needed
    @Throws(IOException::class)
    fun pushFrame(arc: Arc<BytesRef>?, fp: Long, length: Int): SegmentTermsEnumFrame {
        val f = getFrame(1 + currentFrame.ord)
        f.arc = arc
        if (f.fpOrig == fp && f.nextEnt != -1) {
            // if (DEBUG) System.out.println("      push reused frame ord=" + f.ord + " fp=" + f.fp +
            // " isFloor=" + f.isFloor + " hasTerms=" + f.hasTerms + " pref=" + term + " nextEnt=" +
            // f.nextEnt + " targetBeforeCurrentLength=" + targetBeforeCurrentLength + " term.length=" +
            // term.length + " vs prefix=" + f.prefix);
            // if (f.prefix > targetBeforeCurrentLength) {
            if (f.ord > targetBeforeCurrentLength) {
                f.rewind()
            } else {
                // if (DEBUG) {
                //   System.out.println("        skip rewind!");
                // }
            }
            require(length == f.prefixLength)
        } else {
            f.nextEnt = -1
            f.prefixLength = length
            f.state.termBlockOrd = 0
            f.fp = fp
            f.fpOrig = f.fp
            f.lastSubFP = -1
            // if (DEBUG) {
            //   final int sav = term.length;
            //   term.length = length;
            //   System.out.println("      push new frame ord=" + f.ord + " fp=" + f.fp + " hasTerms=" +
            // f.hasTerms + " isFloor=" + f.isFloor + " pref=" + ToStringUtils.bytesRefToString(term));
            //   term.length = sav;
            // }
        }

        return f
    }

    // asserts only
    private fun clearEOF(): Boolean {
        eof = false
        return true
    }

    // asserts only
    private fun setEOF(): Boolean {
        eof = true
        return true
    }

    @Throws(IOException::class)
    private fun prepareSeekExact(target: BytesRef, prefetch: Boolean): IOBooleanSupplier? {
        checkNotNull(fr.index) { "terms index was not loaded" }

        if (fr.size() > 0 && (target < fr.min!! || target > fr.max!!)) {
            return null
        }

        term.grow(1 + target.length)

        require(clearEOF())

        // if (DEBUG) {
        //   System.out.println("\nBTTR.seekExact seg=" + fr.parent.segment + " target=" +
        // fr.fieldInfo.name + ":" + ToStringUtils.bytesRefToString(target) + " current=" +
        // ToStringUtils.bytesRefToString(term) +
        // " (exists=" + termExists + ") validIndexPrefix=" + validIndexPrefix);
        //   printSeekState(System.out);
        // }
        var arc: Arc<BytesRef>
        var targetUpto: Int

        targetBeforeCurrentLength = currentFrame.ord
        outputAccumulator.reset()

        if (currentFrame !== staticFrame) {
            // We are already seek'd; find the common
            // prefix of new seek term vs current term and
            // re-use the corresponding seek state.  For
            // example, if app first seeks to foobar, then
            // seeks to foobaz, we can re-use the seek state
            // for the first 5 bytes.

            // if (DEBUG) {
            //   System.out.println("  re-use current seek state validIndexPrefix=" + validIndexPrefix);
            // }

            arc = arcs[0]!!
            require(arc.isFinal)
            outputAccumulator.push(arc.output()!!)
            targetUpto = 0

            var lastFrame = stack[0]
            require(validIndexPrefix <= term.length())

            val targetLimit: Int = min(target.length, validIndexPrefix)

            var cmp = 0

            // First compare up to valid seek frames:
            while (targetUpto < targetLimit) {
                cmp = (term.byteAt(targetUpto) and 0xFF.toByte()) - (target.bytes[target.offset + targetUpto] and 0xFF.toByte())
                // if (DEBUG) {
                //    System.out.println("    cycle targetUpto=" + targetUpto + " (vs limit=" + targetLimit
                // + ") cmp=" + cmp + " (targetLabel=" + (char) (target.bytes[target.offset + targetUpto]) +
                // " vs termLabel=" + (char) (term.bytes[targetUpto]) + ")"   + " arc.output=" + arc.output
                // + " output=" + output);
                // }
                if (cmp != 0) {
                    break
                }
                arc = arcs[1 + targetUpto]!!
                require(
                    arc.label() == (target.bytes[target.offset + targetUpto] and 0xFF.toByte()).toInt()
                ) {
                    ("arc.label="
                            + arc.label().toChar() + " targetLabel="
                            + (target.bytes[target.offset + targetUpto] and 0xFF.toByte()).toInt().toChar())
                }
                outputAccumulator.push(arc.output()!!)

                if (arc.isFinal) {
                    lastFrame = stack[1 + lastFrame!!.ord]
                }
                targetUpto++
            }

            if (cmp == 0) {
                // Second compare the rest of the term, but
                // don't save arc/output/frame; we only do this
                // to find out if the target term is before,
                // equal or after the current term
                cmp =
                    Arrays.compareUnsigned(
                        term.bytes(),
                        targetUpto,
                        term.length(),
                        target.bytes,
                        target.offset + targetUpto,
                        target.offset + target.length
                    )
            }

            if (cmp < 0) {
                // Common case: target term is after current
                // term, ie, app is seeking multiple terms
                // in sorted order
                // if (DEBUG) {
                //   System.out.println("  target is after current (shares prefixLen=" + targetUpto + ");
                // frame.ord=" + lastFrame.ord);
                // }
                currentFrame = lastFrame!!
            } else if (cmp > 0) {
                // Uncommon case: target term
                // is before current term; this means we can
                // keep the currentFrame but we must rewind it
                // (so we scan from the start)
                targetBeforeCurrentLength = lastFrame!!.ord
                // if (DEBUG) {
                //   System.out.println("  target is before current (shares prefixLen=" + targetUpto + ");
                // rewind frame ord=" + lastFrame.ord);
                // }
                currentFrame = lastFrame
                currentFrame.rewind()
            } else {
                // Target is exactly the same as current term
                require(term.length() == target.length)
                if (termExists) {
                    // if (DEBUG) {
                    //   System.out.println("  target is same as current; return true");
                    // }
                    return IOBooleanSupplier { true }
                } else {
                    // if (DEBUG) {
                    //   System.out.println("  target is same as current but term doesn't exist");
                    // }
                }
                // validIndexPrefix = currentFrame.depth;
                // term.length = target.length;
                // return termExists;
            }
        } else {
            targetBeforeCurrentLength = -1
            arc = fr.index.getFirstArc(arcs[0]!!)

            // Emy string prefix must have an output (block) in the index!
            require(arc.isFinal)
            checkNotNull(arc.output())

            // if (DEBUG) {
            //   System.out.println("    no seek state; push root frame");
            // }
            outputAccumulator.push(arc.output()!!)

            currentFrame = staticFrame

            // term.length = 0;
            targetUpto = 0
            outputAccumulator.push(arc.nextFinalOutput()!!)
            currentFrame = pushFrame(arc, 0)
            outputAccumulator.pop(arc.nextFinalOutput()!!)
        }

        // if (DEBUG) {
        //   System.out.println("  start index loop targetUpto=" + targetUpto + " output=" + output +
        // " currentFrame.ord=" + currentFrame.ord + " targetBeforeCurrentLength=" +
        // targetBeforeCurrentLength);
        // }

        // We are done sharing the common prefix with the incoming target and where we are currently
        // seek'd; now continue walking the index:
        while (targetUpto < target.length) {
            val targetLabel: Int = (target.bytes[target.offset + targetUpto] and 0xFF.toByte()).toInt()

            val nextArc: Arc<BytesRef>? =
                fr.index.findTargetArc(targetLabel, arc, getArc(1 + targetUpto), fstReader!!)

            if (nextArc == null) {
                // Index is exhausted
                // if (DEBUG) {
                //   System.out.println("    index: index exhausted label=" + ((char) targetLabel) + " " +
                // toHex(targetLabel));
                // }

                validIndexPrefix = currentFrame.prefixLength

                // validIndexPrefix = targetUpto;
                currentFrame.scanToFloorFrame(target)

                if (!currentFrame.hasTerms) {
                    termExists = false
                    term.setByteAt(targetUpto, targetLabel.toByte())
                    term.setLength(1 + targetUpto)
                    // if (DEBUG) {
                    //   System.out.println("  FAST NOT_FOUND term=" + ToStringUtils.bytesRefToString(term));
                    // }
                    return null
                }

                if (prefetch) {
                    currentFrame.prefetchBlock()
                }

                return IOBooleanSupplier {
                    currentFrame.loadBlock()
                    val result: SeekStatus = currentFrame.scanToTerm(target, true)
                    if (result === SeekStatus.FOUND) {
                        // if (DEBUG) {
                        //   System.out.println("  return FOUND term=" + term.utf8ToString() + " " + term);
                        // }
                        return@IOBooleanSupplier true
                    } else {
                        // if (DEBUG) {
                        //   System.out.println("  got " + result + "; return NOT_FOUND term=" +
                        // ToStringUtils.bytesRefToString(term));
                        // }
                        return@IOBooleanSupplier false
                    }
                }
            } else {
                // Follow this arc
                arc = nextArc
                term.setByteAt(targetUpto, targetLabel.toByte())
                // Aggregate output as we go:
                checkNotNull(arc.output())
                outputAccumulator.push(arc.output()!!)

                // if (DEBUG) {
                //   System.out.println("    index: follow label=" + toHex(target.bytes[target.offset +
                // targetUpto]&0xff) + " arc.output=" + arc.output + " arc.nfo=" + arc.nextFinalOutput);
                // }
                targetUpto++

                if (arc.isFinal) {
                    // if (DEBUG) System.out.println("    arc is final!");
                    outputAccumulator.push(arc.nextFinalOutput()!!)
                    currentFrame = pushFrame(arc, targetUpto)
                    outputAccumulator.pop(arc.nextFinalOutput()!!)
                    // if (DEBUG) System.out.println("    curFrame.ord=" + currentFrame.ord + " hasTerms=" +
                    // currentFrame.hasTerms);
                }
            }
        }

        // validIndexPrefix = targetUpto;
        validIndexPrefix = currentFrame.prefixLength

        currentFrame.scanToFloorFrame(target)

        // Target term is entirely contained in the index:
        if (!currentFrame.hasTerms) {
            termExists = false
            term.setLength(targetUpto)
            // if (DEBUG) {
            //   System.out.println("  FAST NOT_FOUND term=" + ToStringUtils.bytesRefToString(term));
            // }
            return null
        }

        if (prefetch) {
            currentFrame.prefetchBlock()
        }

        return IOBooleanSupplier {
            currentFrame.loadBlock()
            val result: SeekStatus = currentFrame.scanToTerm(target, true)
            if (result === SeekStatus.FOUND) {
                // if (DEBUG) {
                //   System.out.println("  return FOUND term=" + term.utf8ToString() + " " + term);
                // }
                return@IOBooleanSupplier true
            } else {
                // if (DEBUG) {
                //   System.out.println("  got result " + result + "; return NOT_FOUND term=" +
                // term.utf8ToString());
                // }

                return@IOBooleanSupplier false
            }
        }
    }

    @Throws(IOException::class)
    override fun prepareSeekExact(target: BytesRef): IOBooleanSupplier? {
        return prepareSeekExact(target, true)
    }

    @Throws(IOException::class)
    override fun seekExact(target: BytesRef): Boolean {
        val termExistsSupplier: IOBooleanSupplier? = prepareSeekExact(target, false)
        return termExistsSupplier != null && termExistsSupplier.get()
    }

    @Throws(IOException::class)
    override fun seekCeil(target: BytesRef): SeekStatus {
        checkNotNull(fr.index) { "terms index was not loaded" }

        term.grow(1 + target.length)

        require(clearEOF())

        // if (DEBUG) {
        //   System.out.println("\nBTTR.seekCeil seg=" + fr.parent.segment + " target=" +
        // fr.fieldInfo.name + ":" + ToStringUtils.bytesRefToString(target) + " current=" +
        // ToStringUtils.bytesRefToString(term) + " (exists=" + termExists +
        // ") validIndexPrefix=  " + validIndexPrefix);
        //   printSeekState(System.out);
        // }
        var arc: Arc<BytesRef>
        var targetUpto: Int

        targetBeforeCurrentLength = currentFrame.ord
        outputAccumulator.reset()

        if (currentFrame !== staticFrame) {
            // We are already seek'd; find the common
            // prefix of new seek term vs current term and
            // re-use the corresponding seek state.  For
            // example, if app first seeks to foobar, then
            // seeks to foobaz, we can re-use the seek state
            // for the first 5 bytes.

            // if (DEBUG) {
            // System.out.println("  re-use current seek state validIndexPrefix=" + validIndexPrefix);
            // }

            arc = arcs[0]!!
            require(arc.isFinal)
            outputAccumulator.push(arc.output()!!)
            targetUpto = 0

            var lastFrame = stack[0]
            require(validIndexPrefix <= term.length())

            val targetLimit: Int = min(target.length, validIndexPrefix)

            var cmp = 0

            // First compare up to valid seek frames:
            while (targetUpto < targetLimit) {
                cmp = (term.byteAt(targetUpto) and 0xFF.toByte()) - (target.bytes[target.offset + targetUpto] and 0xFF.toByte())
                // if (DEBUG) {
                // System.out.println("    cycle targetUpto=" + targetUpto + " (vs limit=" + targetLimit +
                // ") cmp=" + cmp + " (targetLabel=" + (char) (target.bytes[target.offset + targetUpto]) +
                // " vs termLabel=" + (char) (term.byteAt(targetUpto)) + ")"   + " arc.output=" + arc.output
                // + " output=" + output);
                // }
                if (cmp != 0) {
                    break
                }
                arc = arcs[1 + targetUpto]!!
                require(
                    arc.label() == (target.bytes[target.offset + targetUpto] and 0xFF.toByte()).toInt()
                ) {
                    ("arc.label="
                            + arc.label().toChar() + " targetLabel="
                            + (target.bytes[target.offset + targetUpto] and 0xFF.toByte()).toInt().toChar())
                }

                outputAccumulator.push(arc.output()!!)
                if (arc.isFinal) {
                    lastFrame = stack[1 + lastFrame!!.ord]
                }
                targetUpto++
            }

            if (cmp == 0) {
                // Second compare the rest of the term, but
                // don't save arc/output/frame:
                cmp =
                    Arrays.compareUnsigned(
                        term.bytes(),
                        targetUpto,
                        term.length(),
                        target.bytes,
                        target.offset + targetUpto,
                        target.offset + target.length
                    )
            }

            if (cmp < 0) {
                // Common case: target term is after current
                // term, ie, app is seeking multiple terms
                // in sorted order
                // if (DEBUG) {
                // System.out.println("  target is after current (shares prefixLen=" + targetUpto + ");
                // clear frame.scanned ord=" + lastFrame.ord);
                // }
                currentFrame = lastFrame!!
            } else if (cmp > 0) {
                // Uncommon case: target term
                // is before current term; this means we can
                // keep the currentFrame but we must rewind it
                // (so we scan from the start)
                targetBeforeCurrentLength = 0
                // if (DEBUG) {
                // System.out.println("  target is before current (shares prefixLen=" + targetUpto + ");
                // rewind frame ord=" + lastFrame.ord);
                // }
                currentFrame = lastFrame!!
                currentFrame.rewind()
            } else {
                // Target is exactly the same as current term
                require(term.length() == target.length)
                if (termExists) {
                    // if (DEBUG) {
                    // System.out.println("  target is same as current; return FOUND");
                    // }
                    return SeekStatus.FOUND
                } else {
                    // if (DEBUG) {
                    // System.out.println("  target is same as current but term doesn't exist");
                    // }
                }
            }
        } else {
            targetBeforeCurrentLength = -1
            arc = fr.index.getFirstArc(arcs[0]!!)

            // Emy string prefix must have an output (block) in the index!
            require(arc.isFinal)
            checkNotNull(arc.output())

            // if (DEBUG) {
            // System.out.println("    no seek state; push root frame");
            // }
            outputAccumulator.push(arc.output()!!)

            currentFrame = staticFrame

            // term.length = 0;
            targetUpto = 0
            outputAccumulator.push(arc.nextFinalOutput()!!)
            currentFrame = pushFrame(arc, 0)
            outputAccumulator.pop(arc.nextFinalOutput()!!)
        }

        // if (DEBUG) {
        // System.out.println("  start index loop targetUpto=" + targetUpto + " output=" + output +
        // " currentFrame.ord+1=" + currentFrame.ord + " targetBeforeCurrentLength=" +
        // targetBeforeCurrentLength);
        // }

        // We are done sharing the common prefix with the incoming target and where we are currently
        // seek'd; now continue walking the index:
        while (targetUpto < target.length) {
            val targetLabel: Int = (target.bytes[target.offset + targetUpto] and 0xFF.toByte()).toInt()

            val nextArc: Arc<BytesRef>? =
                fr.index.findTargetArc(targetLabel, arc, getArc(1 + targetUpto), fstReader!!)

            if (nextArc == null) {
                // Index is exhausted
                // if (DEBUG) {
                //   System.out.println("    index: index exhausted label=" + ((char) targetLabel) + " " +
                // targetLabel);
                // }

                validIndexPrefix = currentFrame.prefixLength

                // validIndexPrefix = targetUpto;
                currentFrame.scanToFloorFrame(target)

                currentFrame.loadBlock()

                // if (DEBUG) System.out.println("  now scanToTerm");
                val result: SeekStatus = currentFrame.scanToTerm(target, false)
                if (result === SeekStatus.END) {
                    term.copyBytes(target)
                    termExists = false

                    return if (next() != null) {
                        // if (DEBUG) {
                        // System.out.println("  return NOT_FOUND term=" +
                        // ToStringUtils.bytesRefToString(term));
                        // }
                        SeekStatus.NOT_FOUND
                    } else {
                        // if (DEBUG) {
                        // System.out.println("  return END");
                        // }
                        SeekStatus.END
                    }
                } else {
                    // if (DEBUG) {
                    // System.out.println("  return " + result + " term=" +
                    // ToStringUtils.bytesRefToString(term));
                    // }
                    return result
                }
            } else {
                // Follow this arc
                term.setByteAt(targetUpto, targetLabel.toByte())
                arc = nextArc
                // Aggregate output as we go:
                checkNotNull(arc.output())
                outputAccumulator.push(arc.output()!!)

                // if (DEBUG) {
                // System.out.println("    index: follow label=" + (target.bytes[target.offset +
                // targetUpto]&0xff) + " arc.output=" + arc.output + " arc.nfo=" + arc.nextFinalOutput);
                // }
                targetUpto++

                if (arc.isFinal) {
                    // if (DEBUG) System.out.println("    arc is final!");
                    outputAccumulator.push(arc.nextFinalOutput()!!)
                    currentFrame = pushFrame(arc, targetUpto)
                    outputAccumulator.pop(arc.nextFinalOutput()!!)
                    // if (DEBUG) System.out.println("    curFrame.ord=" + currentFrame.ord + " hasTerms=" +
                    // currentFrame.hasTerms);
                }
            }
        }

        // validIndexPrefix = targetUpto;
        validIndexPrefix = currentFrame.prefixLength

        currentFrame.scanToFloorFrame(target)

        currentFrame.loadBlock()

        val result: SeekStatus = currentFrame.scanToTerm(target, false)

        if (result === SeekStatus.END) {
            term.copyBytes(target)
            termExists = false
            return if (next() != null) {
                // if (DEBUG) {
                // System.out.println("  return NOT_FOUND term=" + term.get().utf8ToString() + " " + term);
                // }
                SeekStatus.NOT_FOUND
            } else {
                // if (DEBUG) {
                // System.out.println("  return END");
                // }
                SeekStatus.END
            }
        } else {
            return result
        }
    }

    /**
     * drop in replacement for java.io.PrintStream
     */
    private class PrintStream {
        fun println(msg: Any? = "") = kotlin.io.println(msg)
    }

    @Suppress("unused")
    @Throws(IOException::class)
    private fun printSeekState(out: PrintStream) {
        if (currentFrame == staticFrame) {
            out.println("  no prior seek")
        } else {
            out.println("  prior seek state:")
            var ord = 0
            var isSeekFrame = true
            while (true) {
                val f = checkNotNull(getFrame(ord))
                val prefix = BytesRef(term.get().bytes, 0, f.prefixLength)
                if (f.nextEnt == -1) {
                    out.println(
                        ("    frame "
                                + (if (isSeekFrame) "(seek)" else "(next)")
                                + " ord="
                                + ord
                                + " fp="
                                + f.fp
                                + (if (f.isFloor) (" (fpOrig=" + f.fpOrig + ")") else "")
                                + " prefixLen="
                                + f.prefixLength
                                + " prefix="
                                + prefix
                                + (if (f.nextEnt == -1) "" else (" (of " + f.entCount + ")"))
                                + " hasTerms="
                                + f.hasTerms
                                + " isFloor="
                                + f.isFloor
                                + " code="
                                + ((f.fp shl Lucene90BlockTreeTermsReader.OUTPUT_FLAGS_NUM_BITS)
                                + (if (f.hasTerms) Lucene90BlockTreeTermsReader.OUTPUT_FLAG_HAS_TERMS else 0)
                                + (if (f.isFloor) Lucene90BlockTreeTermsReader.OUTPUT_FLAG_IS_FLOOR else 0))
                                + " isLastInFloor="
                                + f.isLastInFloor
                                + " mdUpto="
                                + f.metaDataUpto
                                + " tbOrd="
                                + f.getTermBlockOrd())
                    )
                } else {
                    out.println(
                        ("    frame "
                                + (if (isSeekFrame) "(seek, loaded)" else "(next, loaded)")
                                + " ord="
                                + ord
                                + " fp="
                                + f.fp
                                + (if (f.isFloor) (" (fpOrig=" + f.fpOrig + ")") else "")
                                + " prefixLen="
                                + f.prefixLength
                                + " prefix="
                                + prefix
                                + " nextEnt="
                                + f.nextEnt
                                + (if (f.nextEnt == -1) "" else (" (of " + f.entCount + ")"))
                                + " hasTerms="
                                + f.hasTerms
                                + " isFloor="
                                + f.isFloor
                                + " code="
                                + ((f.fp shl Lucene90BlockTreeTermsReader.OUTPUT_FLAGS_NUM_BITS)
                                + (if (f.hasTerms) Lucene90BlockTreeTermsReader.OUTPUT_FLAG_HAS_TERMS else 0)
                                + (if (f.isFloor) Lucene90BlockTreeTermsReader.OUTPUT_FLAG_IS_FLOOR else 0))
                                + " lastSubFP="
                                + f.lastSubFP
                                + " isLastInFloor="
                                + f.isLastInFloor
                                + " mdUpto="
                                + f.metaDataUpto
                                + " tbOrd="
                                + f.getTermBlockOrd())
                    )
                }
                if (fr.index != null) {
                    require(!isSeekFrame || f.arc != null) { "isSeekFrame=" + isSeekFrame + " f.arc=" + f.arc }
                    if (f.prefixLength > 0 && isSeekFrame
                        && f.arc!!.label() != (term.byteAt(f.prefixLength - 1) and 0xFF.toByte()).toInt()
                    ) {
                        out.println(
                            ("      broken seek state: arc.label="
                                    + f.arc!!.label().toChar() + " vs term byte="
                                    + (term.byteAt(f.prefixLength - 1) and 0xFF.toByte()).toInt().toChar())
                        )
                        throw RuntimeException("seek state is broken")
                    }
                    val output: BytesRef? = Util.get(fr.index, prefix)
                    if (output == null) {
                        out.println("      broken seek state: prefix is not final in index")
                        throw RuntimeException("seek state is broken")
                    } else if (isSeekFrame && !f.isFloor) {
                        val reader =
                            ByteArrayDataInput(output.bytes, output.offset, output.length)
                        val codeOrig = fr.readVLongOutput(reader)
                        val code =
                            ((f.fp shl Lucene90BlockTreeTermsReader.OUTPUT_FLAGS_NUM_BITS)
                                    or (if (f.hasTerms) Lucene90BlockTreeTermsReader.OUTPUT_FLAG_HAS_TERMS.toLong() else 0L)
                                    or (if (f.isFloor) Lucene90BlockTreeTermsReader.OUTPUT_FLAG_IS_FLOOR.toLong() else 0L))
                        if (codeOrig != code) {
                            out.println(
                                ("      broken seek state: output code="
                                        + codeOrig
                                        + " doesn't match frame code="
                                        + code)
                            )
                            throw RuntimeException("seek state is broken")
                        }
                    }
                }
                if (f == currentFrame) {
                    break
                }
                if (f.prefixLength == validIndexPrefix) {
                    isSeekFrame = false
                }
                ord++
            }
        }
    }

    /* Decodes only the term bytes of the next term.  If caller then asks for
  metadata, ie docFreq, totalTermFreq or pulls a D/&PEnum, we then (lazily)
  decode all metadata up to the current term. */
    @Throws(IOException::class)
    override fun next(): BytesRef? {
        if (`in` == null) {
            // Fresh TermsEnum; seek to first term:
            val arc: Arc<BytesRef>?
            if (fr.index != null) {
                arc = fr.index.getFirstArc(arcs[0]!!)
                // Empty string prefix must have an output in the index!
                require(arc.isFinal)
            } else {
                arc = null
            }
            currentFrame = pushFrame(arc, fr.rootCode, 0)
            currentFrame.loadBlock()
        }

        targetBeforeCurrentLength = currentFrame.ord

        require(!eof)

        // if (DEBUG) {
        //   System.out.println("\nBTTR.next seg=" + fr.parent.segment + " term=" +
        // ToStringUtils.bytesRefToString(term) + " termExists=" + termExists + " field=" +
        // fr.fieldInfo.name + " termBlockOrd=" + currentFrame.state.termBlockOrd +
        // " validIndexPrefix=" + validIndexPrefix);
        //   printSeekState(System.out);
        // }
        if (currentFrame === staticFrame) {
            // If seek was previously called and the term was
            // cached, or seek(TermState) was called, usually
            // caller is just going to pull a D/&PEnum or get
            // docFreq, etc.  But, if they then call next(),
            // this method catches up all internal state so next()
            // works properly:
            // if (DEBUG) System.out.println("  re-seek to pending term=" + term.utf8ToString() + " " +
            // term);
            val result = seekExact(term.get())
            require(result)
        }

        // Pop finished blocks
        while (currentFrame.nextEnt == currentFrame.entCount) {
            if (!currentFrame.isLastInFloor) {
                // Advance to next floor block
                currentFrame.loadNextFloorBlock()
                break
            } else {
                // if (DEBUG) System.out.println("  pop frame");
                if (currentFrame.ord == 0) {
                    // if (DEBUG) System.out.println("  return null");
                    require(setEOF())
                    term.clear()
                    validIndexPrefix = 0
                    currentFrame.rewind()
                    termExists = false
                    return null
                }
                val lastFP = currentFrame.fpOrig
                currentFrame = stack[currentFrame.ord - 1]!!

                if (currentFrame.nextEnt == -1 || currentFrame.lastSubFP != lastFP) {
                    // We popped into a frame that's not loaded
                    // yet or not scan'd to the right entry
                    currentFrame.scanToFloorFrame(term.get())
                    currentFrame.loadBlock()
                    currentFrame.scanToSubBlock(lastFP)
                }

                // Note that the seek state (last seek) has been
                // invalidated beyond this depth
                validIndexPrefix = min(validIndexPrefix, currentFrame.prefixLength)
                // if (DEBUG) {
                // System.out.println("  reset validIndexPrefix=" + validIndexPrefix);
                // }
            }
        }

        while (true) {
            if (currentFrame.next()) {
                // Push to new block:
                // if (DEBUG) System.out.println("  push frame");
                currentFrame = pushFrame(null, currentFrame.lastSubFP, term.length())
                // This is a "next" frame -- even if it's
                // floor'd we must pretend it isn't so we don't
                // try to scan to the right floor frame:
                currentFrame.loadBlock()
            } else {
                // if (DEBUG) System.out.println("  return term=" + ToStringUtils.bytesRefToString(term) +
                // " currentFrame.ord=" + currentFrame.ord);
                return term.get()
            }
        }
    }

    override fun term(): BytesRef {
        require(!eof)
        return term.get()
    }

    @Throws(IOException::class)
    override fun docFreq(): Int {
        require(!eof)
        // if (DEBUG) System.out.println("BTR.docFreq");
        currentFrame.decodeMetaData()
        // if (DEBUG) System.out.println("  return " + currentFrame.state.docFreq);
        return currentFrame.state.docFreq
    }

    @Throws(IOException::class)
    override fun totalTermFreq(): Long {
        require(!eof)
        currentFrame.decodeMetaData()
        return currentFrame.state.totalTermFreq
    }

    @Throws(IOException::class)
    override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
        require(!eof)
        // if (DEBUG) {
        // System.out.println("BTTR.docs seg=" + segment);
        // }
        currentFrame.decodeMetaData()
        // if (DEBUG) {
        // System.out.println("  state=" + currentFrame.state);
        // }
        return fr.parent.postingsReader.postings(fr.fieldInfo, currentFrame.state, reuse, flags)
    }

    @Throws(IOException::class)
    override fun impacts(flags: Int): ImpactsEnum {
        require(!eof)
        // if (DEBUG) {
        // System.out.println("BTTR.docs seg=" + segment);
        // }
        currentFrame.decodeMetaData()
        // if (DEBUG) {
        // System.out.println("  state=" + currentFrame.state);
        // }
        return fr.parent.postingsReader.impacts(fr.fieldInfo, currentFrame.state, flags)
    }

    override fun seekExact(target: BytesRef, otherState: TermState) {
        // if (DEBUG) {
        //   System.out.println("BTTR.seekExact termState seg=" + segment + " target=" +
        // target.utf8ToString() + " " + target + " state=" + otherState);
        // }
        require(clearEOF())
        if (target.compareTo(term.get()) != 0 || !termExists) {
            require(otherState != null && otherState is BlockTermState)
            currentFrame = staticFrame
            currentFrame.state.copyFrom(otherState)
            term.copyBytes(target)
            currentFrame.metaDataUpto = currentFrame.getTermBlockOrd()
            require(currentFrame.metaDataUpto > 0)
            validIndexPrefix = 0
        } else {
            // if (DEBUG) {
            //   System.out.println("  skip seek: already on target state=" + currentFrame.state);
            // }
        }
    }

    @Throws(IOException::class)
    override fun termState(): TermState {
        require(!eof)
        currentFrame.decodeMetaData()
        val ts: TermState = currentFrame.state.clone()
        // if (DEBUG) System.out.println("BTTR.termState seg=" + segment + " state=" + ts);
        return ts
    }

    override fun seekExact(ord: Long) {
        throw UnsupportedOperationException()
    }

    override fun ord(): Long {
        throw UnsupportedOperationException()
    }

    class OutputAccumulator : DataInput() {
        var outputs: Array<BytesRef?> = kotlin.arrayOfNulls(16)
        var current: BytesRef? = null
        var num: Int = 0
        var outputIndex: Int = 0
        var index: Int = 0

        fun push(output: BytesRef) {
            if (output !== Lucene90BlockTreeTermsReader.NO_OUTPUT) {
                require(output.length > 0)
                outputs = ArrayUtil.grow(outputs, num + 1)
                outputs[num++] = output
            }
        }

        fun pop(output: BytesRef) {
            if (output !== Lucene90BlockTreeTermsReader.NO_OUTPUT) {
                require(num > 0)
                require(outputs[num - 1] === output)
                num--
            }
        }

        fun pop(cnt: Int) {
            require(num >= cnt)
            num -= cnt
        }

        fun outputCount(): Int {
            return num
        }

        fun reset() {
            num = 0
        }

        fun prepareRead() {
            index = 0
            outputIndex = 0
            current = outputs[0]
        }

        /**
         * Set the last arc as the source of the floorData. This won't change the reading position of
         * this [OutputAccumulator]
         */
        fun setFloorData(floorData: ByteArrayDataInput) {
            require(
                outputIndex == num - 1
            ) {
                ("floor data should be stored in last arc, get outputIndex: "
                        + outputIndex
                        + ", num: "
                        + num)
            }
            val output: BytesRef = outputs[outputIndex]!!
            floorData.reset(output.bytes, output.offset + index, output.length - index)
        }

        @Throws(IOException::class)
        override fun readByte(): Byte {
            if (index >= current!!.length) {
                current = outputs[++outputIndex]
                index = 0
            }
            return current!!.bytes[current!!.offset + index++]
        }

        @Throws(IOException::class)
        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun skipBytes(numBytes: Long) {
            throw UnsupportedOperationException()
        }
    }
}
