package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.fst.FST.Arc
import org.gnit.lucenekmp.util.fst.FST.Arc.BitTable
import org.gnit.lucenekmp.util.fst.FST.BytesReader
import okio.IOException
import org.gnit.lucenekmp.jdkport.System

/**
 * Can next() and advance() through the terms in an FST
 *
 * @lucene.experimental
 */
abstract class FSTEnum<T>(protected val fst: FST<T>) {
    protected var arcs: Array<Arc<T>?> = kotlin.arrayOfNulls<Arc<T>>(10)

    // outputs are cumulative
    protected var output: Array<T> = kotlin.arrayOfNulls<Any>(10) as Array<T>

    protected val NO_OUTPUT: T = fst.outputs.noOutput
    protected val fstReader: BytesReader = fst.getBytesReader()

    protected var upto: Int = 0
    var targetLength: Int = 0

    /**
     * doFloor controls the behavior of advance: if it's true doFloor is true, advance positions to
     * the biggest term before target.
     */
    init {
        fst.getFirstArc(getArc(0))
        output[0] = NO_OUTPUT
    }

    protected abstract fun getTargetLabel(): Int

    protected abstract fun getCurrentLabel(): Int

    protected abstract fun setCurrentLabel(label: Int)

    protected abstract fun grow()

    /** Rewinds enum state to match the shared prefix between current term and target term  */
    @Throws(IOException::class)
    private fun rewindPrefix() {
        if (upto == 0) {
            // System.out.println("  init");
            upto = 1
            fst.readFirstTargetArc(getArc(0), getArc(1), fstReader)
            return
        }

        // System.out.println("  rewind upto=" + upto + " vs targetLength=" + targetLength);
        val currentLimit = upto
        upto = 1
        while (upto < currentLimit && upto <= targetLength + 1) {
            val cmp = this.getCurrentLabel() - this.getTargetLabel()
            if (cmp < 0) {
                // seek forward
                // System.out.println("    seek fwd");
                break
            } else if (cmp > 0) {
                // seek backwards -- reset this arc to the first arc
                val arc = getArc(upto)
                fst.readFirstTargetArc(getArc(upto - 1), arc, fstReader)
                // System.out.println("    seek first arc");
                break
            }
            upto++
        }
        // System.out.println("  fall through upto=" + upto);
    }

    @Throws(IOException::class)
    protected fun doNext() {
        // System.out.println("FE: next upto=" + upto);
        if (upto == 0) {
            // System.out.println("  init");
            upto = 1
            fst.readFirstTargetArc(getArc(0), getArc(1), fstReader)
        } else {
            // pop
            // System.out.println("  check pop curArc target=" + arcs[upto].target + " label=" +
            // arcs[upto].label + " isLast=" + arcs[upto].isLast);
            while (arcs[upto]!!.isLast) {
                upto--
                if (upto == 0) {
                    // System.out.println("  eof");
                    return
                }
            }
            fst.readNextArc(arcs[upto]!!, fstReader)
        }

        pushFirst()
    }

    // TODO: should we return a status here (SEEK_FOUND / SEEK_NOT_FOUND /
    // SEEK_END)  saves the eq check above
    /** Seeks to smallest term that's &gt;= target.  */
    @Throws(IOException::class)
    protected fun doSeekCeil() {
        // System.out.println("    advance len=" + target.length + " curlen=" + current.length);

        // TODO: possibly caller could/should provide common
        // prefix length  ie this work may be redundant if
        // caller is in fact intersecting against its own
        // automaton

        // System.out.println("FE.seekCeil upto=" + upto);

        // Save time by starting at the end of the shared prefix
        // b/w our current term & the target:

        rewindPrefix()

        // System.out.println("  after rewind upto=" + upto);
        var arc: Arc<T>? = getArc(upto)

        // System.out.println("  init targetLabel=" + targetLabel);

        // Now scan forward, matching the new suffix of the target
        while (arc != null) {
            val targetLabel = this.getTargetLabel()
            // System.out.println("  cycle upto=" + upto + " arc.label=" + arc.label + " (" + (char)
            // arc.label + ") vs targetLabel=" + targetLabel);
            if (arc.bytesPerArc() != 0 && arc.label() != FST.END_LABEL) {
                // Arcs are in an array
                val `in`: BytesReader = fst.getBytesReader()
                if (arc.nodeFlags() == FST.ARCS_FOR_DIRECT_ADDRESSING) {
                    arc = doSeekCeilArrayDirectAddressing(arc, targetLabel, `in`)
                } else if (arc.nodeFlags() == FST.ARCS_FOR_BINARY_SEARCH) {
                    arc = doSeekCeilArrayPacked(arc, targetLabel, `in`)
                } else {
                    require(arc.nodeFlags() == FST.ARCS_FOR_CONTINUOUS)
                    arc = doSeekCeilArrayContinuous(arc, targetLabel, `in`)
                }
            } else {
                arc = doSeekCeilList(arc, targetLabel)
            }
        }
    }

    @Throws(IOException::class)
    private fun doSeekCeilArrayContinuous(
        arc: Arc<T>, targetLabel: Int, `in`: BytesReader
    ): Arc<T>? {
        val targetIndex = targetLabel - arc.firstLabel()
        if (targetIndex >= arc.numArcs()) {
            rollbackToLastForkThenPush()
            return null
        } else {
            if (targetIndex < 0) {
                fst.readArcByContinuous(arc, `in`, 0)
                require(arc.label() > targetLabel)
                pushFirst()
                return null
            } else {
                fst.readArcByContinuous(arc, `in`, targetIndex)
                require(arc.label() == targetLabel)
                // found -- copy pasta from below
                output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
                if (targetLabel == FST.END_LABEL) {
                    return null
                }
                this.setCurrentLabel(arc.label())
                incr()
                return fst.readFirstTargetArc(arc, getArc(upto), fstReader)
            }
        }
    }

    @Throws(IOException::class)
    private fun doSeekCeilArrayDirectAddressing(
        arc: Arc<T>, targetLabel: Int, `in`: BytesReader
    ): Arc<T>? {
        // The array is addressed directly by label, with presence bits to compute the actual arc
        // offset.

        var targetIndex = targetLabel - arc.firstLabel()
        if (targetIndex >= arc.numArcs()) {
            rollbackToLastForkThenPush()
            return null
        } else {
            if (targetIndex < 0) {
                targetIndex = -1
            } else if (BitTable.isBitSet(targetIndex, arc, `in`)) {
                fst.readArcByDirectAddressing(arc, `in`, targetIndex)
                require(arc.label() == targetLabel)
                // found -- copy pasta from below
                output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
                if (targetLabel == FST.END_LABEL) {
                    return null
                }
                this.setCurrentLabel(arc.label())
                incr()
                return fst.readFirstTargetArc(arc, getArc(upto), fstReader)
            }
            // Not found, return the next arc (ceil).
            val ceilIndex: Int = BitTable.nextBitSet(targetIndex, arc, `in`)
            require(ceilIndex != -1)
            fst.readArcByDirectAddressing(arc, `in`, ceilIndex)
            require(arc.label() > targetLabel)
            pushFirst()
            return null
        }
    }

    @Throws(IOException::class)
    private fun doSeekCeilArrayPacked(
        arc: Arc<T>, targetLabel: Int, `in`: BytesReader
    ): Arc<T>? {
        // The array is packed -- use binary search to find the target.
        var idx = Util.binarySearch(fst, arc, targetLabel)
        if (idx >= 0) {
            // Match
            fst.readArcByIndex(arc, `in`, idx)
            require(arc.arcIdx() == idx)
            require(
                arc.label() == targetLabel
            ) { "arc.label=" + arc.label() + " vs targetLabel=" + targetLabel + " mid=" + idx }
            output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
            if (targetLabel == FST.END_LABEL) {
                return null
            }
            this.setCurrentLabel(arc.label())
            incr()
            return fst.readFirstTargetArc(arc, getArc(upto), fstReader)
        }
        idx = -1 - idx
        if (idx == arc.numArcs()) {
            // Dead end
            fst.readArcByIndex(arc, `in`, idx - 1)
            require(arc.isLast)
            // Dead end (target is after the last arc);
            // rollback to last fork then push
            upto--
            while (true) {
                if (upto == 0) {
                    return null
                }
                val prevArc = getArc(upto)
                // System.out.println("  rollback upto=" + upto + " arc.label=" + prevArc.label + "
                // isLast=" + prevArc.isLast);
                if (!prevArc.isLast) {
                    fst.readNextArc(prevArc, fstReader)
                    pushFirst()
                    return null
                }
                upto--
            }
        } else {
            // Ceiling - arc with least higher label
            fst.readArcByIndex(arc, `in`, idx)
            require(arc.label() > targetLabel)
            pushFirst()
            return null
        }
    }

    @Throws(IOException::class)
    private fun doSeekCeilList(arc: Arc<T>, targetLabel: Int): Arc<T>? {
        // Arcs are not array'd -- must do linear scan:
        if (arc.label() == targetLabel) {
            // recurse
            output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
            if (targetLabel == FST.END_LABEL) {
                return null
            }
            this.setCurrentLabel(arc.label())
            incr()
            return fst.readFirstTargetArc(arc, getArc(upto), fstReader)
        } else if (arc.label() > targetLabel) {
            pushFirst()
            return null
        } else if (arc.isLast) {
            // Dead end (target is after the last arc);
            // rollback to last fork then push
            upto--
            while (true) {
                if (upto == 0) {
                    return null
                }
                val prevArc = getArc(upto)
                // System.out.println("  rollback upto=" + upto + " arc.label=" + prevArc.label + "
                // isLast=" + prevArc.isLast);
                if (!prevArc.isLast) {
                    fst.readNextArc(prevArc, fstReader)
                    pushFirst()
                    return null
                }
                upto--
            }
        } else {
            // keep scanning
            // System.out.println("    next scan");
            fst.readNextArc(arc, fstReader)
        }
        return arc
    }

    // Todo: should we return a status here (SEEK_FOUND / SEEK_NOT_FOUND /
    // SEEK_END)  saves the eq check above
    /** Seeks to largest term that's &lt;= target.  */
    @Throws(IOException::class)
    fun doSeekFloor() {
        // TODO: possibly caller could/should provide common
        // prefix length  ie this work may be redundant if
        // caller is in fact intersecting against its own
        // automaton
        // System.out.println("FE: seek floor upto=" + upto);

        // Save CPU by starting at the end of the shared prefix
        // b/w our current term & the target:

        rewindPrefix()

        // System.out.println("FE: after rewind upto=" + upto);
        var arc: Arc<T>? = getArc(upto)

        // System.out.println("FE: init targetLabel=" + targetLabel);

        // Now scan forward, matching the new suffix of the target
        while (arc != null) {
            // System.out.println("  cycle upto=" + upto + " arc.label=" + arc.label + " (" + (char)
            // arc.label + ") targetLabel=" + targetLabel + " isLast=" + arc.isLast + " bba=" +
            // arc.bytesPerArc);
            val targetLabel = this.getTargetLabel()

            if (arc.bytesPerArc() != 0 && arc.label() != FST.END_LABEL) {
                // Arcs are in an array
                val `in`: BytesReader = fst.getBytesReader()
                if (arc.nodeFlags() == FST.ARCS_FOR_DIRECT_ADDRESSING) {
                    arc = doSeekFloorArrayDirectAddressing(arc, targetLabel, `in`)
                } else if (arc.nodeFlags() == FST.ARCS_FOR_BINARY_SEARCH) {
                    arc = doSeekFloorArrayPacked(arc, targetLabel, `in`)
                } else {
                    require(arc.nodeFlags() == FST.ARCS_FOR_CONTINUOUS)
                    arc = doSeekFloorContinuous(arc, targetLabel, `in`)
                }
            } else {
                arc = doSeekFloorList(arc, targetLabel)
            }
        }
    }

    @Throws(IOException::class)
    private fun doSeekFloorContinuous(arc: Arc<T>, targetLabel: Int, `in`: BytesReader): Arc<T>? {
        val targetIndex = targetLabel - arc.firstLabel()
        if (targetIndex < 0) {
            // Before first arc.
            return backtrackToFloorArc(arc, targetLabel, `in`)
        } else if (targetIndex >= arc.numArcs()) {
            // After last arc.
            fst.readLastArcByContinuous(arc, `in`)
            require(arc.label() < targetLabel)
            require(arc.isLast)
            pushLast()
            return null
        } else {
            // Within label range.
            fst.readArcByContinuous(arc, `in`, targetIndex)
            require(arc.label() == targetLabel)
            // found -- copy pasta from below
            output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
            if (targetLabel == FST.END_LABEL) {
                return null
            }
            this.setCurrentLabel(arc.label())
            incr()
            return fst.readFirstTargetArc(arc, getArc(upto), fstReader)
        }
    }

    @Throws(IOException::class)
    private fun doSeekFloorArrayDirectAddressing(
        arc: Arc<T>, targetLabel: Int, `in`: BytesReader
    ): Arc<T>? {
        // The array is addressed directly by label, with presence bits to compute the actual arc
        // offset.

        val targetIndex = targetLabel - arc.firstLabel()
        if (targetIndex < 0) {
            // Before first arc.
            return backtrackToFloorArc(arc, targetLabel, `in`)
        } else if (targetIndex >= arc.numArcs()) {
            // After last arc.
            fst.readLastArcByDirectAddressing(arc, `in`)
            require(arc.label() < targetLabel)
            require(arc.isLast)
            pushLast()
            return null
        } else {
            // Within label range.
            if (BitTable.isBitSet(targetIndex, arc, `in`)) {
                fst.readArcByDirectAddressing(arc, `in`, targetIndex)
                require(arc.label() == targetLabel)
                // found -- copy pasta from below
                output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
                if (targetLabel == FST.END_LABEL) {
                    return null
                }
                this.setCurrentLabel(arc.label())
                incr()
                return fst.readFirstTargetArc(arc, getArc(upto), fstReader)
            }
            // Scan backwards to find a floor arc.
            val floorIndex: Int = BitTable.previousBitSet(targetIndex, arc, `in`)
            require(floorIndex != -1)
            fst.readArcByDirectAddressing(arc, `in`, floorIndex)
            require(arc.label() < targetLabel)
            require(arc.isLast || fst.readNextArcLabel(arc, `in`) > targetLabel)
            pushLast()
            return null
        }
    }

    /**
     * Target is beyond the last arc, out of label range. Dead end (target is after the last arc);
     * rollback to last fork then push
     */
    @Throws(IOException::class)
    private fun rollbackToLastForkThenPush() {
        upto--
        while (true) {
            if (upto == 0) {
                return
            }
            val prevArc = getArc(upto)
            // System.out.println("  rollback upto=" + upto + " arc.label=" + prevArc.label + "
            // isLast=" + prevArc.isLast);
            if (!prevArc.isLast) {
                fst.readNextArc(prevArc, fstReader)
                pushFirst()
                return
            }
            upto--
        }
    }

    /**
     * Backtracks until it finds a node which first arc is before our target label.` Then on the node,
     * finds the arc just before the targetLabel.
     *
     * @return null to continue the seek floor recursion loop.
     */
    @Throws(IOException::class)
    private fun backtrackToFloorArc(arc: Arc<T>, targetLabel: Int, `in`: BytesReader): Arc<T>? {
        var arc = arc
        var targetLabel = targetLabel
        while (true) {
            // First, walk backwards until we find a node which first arc is before our target label.
            fst.readFirstTargetArc(getArc(upto - 1), arc, fstReader)
            if (arc.label() < targetLabel) {
                // Then on this node, find the arc just before the targetLabel.
                if (!arc.isLast) {
                    if (arc.bytesPerArc() != 0 && arc.label() != FST.END_LABEL) {
                        if (arc.nodeFlags() == FST.ARCS_FOR_BINARY_SEARCH) {
                            findNextFloorArcBinarySearch(arc, targetLabel, `in`)
                        } else if (arc.nodeFlags() == FST.ARCS_FOR_DIRECT_ADDRESSING) {
                            findNextFloorArcDirectAddressing(arc, targetLabel, `in`)
                        } else {
                            require(arc.nodeFlags() == FST.ARCS_FOR_CONTINUOUS)
                            findNextFloorArcContinuous(arc, targetLabel, `in`)
                        }
                    } else {
                        while (!arc.isLast && fst.readNextArcLabel(arc, `in`) < targetLabel) {
                            fst.readNextArc(arc, fstReader)
                        }
                    }
                }
                require(arc.label() < targetLabel)
                require(arc.isLast || fst.readNextArcLabel(arc, `in`) >= targetLabel)
                pushLast()
                return null
            }
            upto--
            if (upto == 0) {
                return null
            }
            targetLabel = this.getTargetLabel()
            arc = getArc(upto)
        }
    }

    /**
     * Finds and reads an arc on the current node which label is strictly less than the given label.
     * Skips the first arc, finds next floor arc; or none if the floor arc is the first arc itself (in
     * this case it has already been read).
     *
     *
     * Precondition: the given arc is the first arc of the node.
     */
    @Throws(IOException::class)
    private fun findNextFloorArcDirectAddressing(
        arc: Arc<T>, targetLabel: Int, `in`: BytesReader
    ) {
        require(arc.nodeFlags() == FST.ARCS_FOR_DIRECT_ADDRESSING)
        require(arc.label() != FST.END_LABEL)
        require(arc.label() == arc.firstLabel())
        if (arc.numArcs() > 1) {
            val targetIndex = targetLabel - arc.firstLabel()
            require(targetIndex >= 0)
            if (targetIndex >= arc.numArcs()) {
                // Beyond last arc. Take last arc.
                fst.readLastArcByDirectAddressing(arc, `in`)
            } else {
                // Take the preceding arc, even if the target is present.
                val floorIndex: Int = BitTable.previousBitSet(targetIndex, arc, `in`)
                if (floorIndex > 0) {
                    fst.readArcByDirectAddressing(arc, `in`, floorIndex)
                }
            }
        }
    }

    /** Same as [.findNextFloorArcDirectAddressing] for continuous node.  */
    @Throws(IOException::class)
    private fun findNextFloorArcContinuous(arc: Arc<T>, targetLabel: Int, `in`: BytesReader) {
        require(arc.nodeFlags() == FST.ARCS_FOR_CONTINUOUS)
        require(arc.label() != FST.END_LABEL)
        require(arc.label() == arc.firstLabel())
        if (arc.numArcs() > 1) {
            val targetIndex = targetLabel - arc.firstLabel()
            require(targetIndex >= 0)
            if (targetIndex >= arc.numArcs()) {
                // Beyond last arc. Take last arc.
                fst.readLastArcByContinuous(arc, `in`)
            } else {
                fst.readArcByContinuous(arc, `in`, targetIndex - 1)
            }
        }
    }

    /** Same as [.findNextFloorArcDirectAddressing] for binary search node.  */
    @Throws(IOException::class)
    private fun findNextFloorArcBinarySearch(arc: Arc<T>, targetLabel: Int, `in`: BytesReader) {
        require(arc.nodeFlags() == FST.ARCS_FOR_BINARY_SEARCH)
        require(arc.label() != FST.END_LABEL)
        require(arc.arcIdx() == 0)
        if (arc.numArcs() > 1) {
            val idx = Util.binarySearch(fst, arc, targetLabel)
            require(idx != -1)
            if (idx > 1) {
                fst.readArcByIndex(arc, `in`, idx - 1)
            } else if (idx < -2) {
                fst.readArcByIndex(arc, `in`, -2 - idx)
            }
        }
    }

    @Throws(IOException::class)
    private fun doSeekFloorArrayPacked(
        arc: Arc<T>, targetLabel: Int, `in`: BytesReader
    ): Arc<T>? {
        // Arcs are fixed array -- use binary search to find the target.
        val idx = Util.binarySearch(fst, arc, targetLabel)

        if (idx >= 0) {
            // Match -- recurse
            // System.out.println("  match!  arcIdx=" + idx);
            fst.readArcByIndex(arc, `in`, idx)
            require(arc.arcIdx() == idx)
            require(
                arc.label() == targetLabel
            ) { "arc.label=" + arc.label() + " vs targetLabel=" + targetLabel + " mid=" + idx }
            output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
            if (targetLabel == FST.END_LABEL) {
                return null
            }
            this.setCurrentLabel(arc.label())
            incr()
            return fst.readFirstTargetArc(arc, getArc(upto), fstReader)
        } else if (idx == -1) {
            // Before first arc.
            return backtrackToFloorArc(arc, targetLabel, `in`)
        } else {
            // There is a floor arc; idx will be (-1 - (floor + 1)).
            fst.readArcByIndex(arc, `in`, -2 - idx)
            require(arc.isLast || fst.readNextArcLabel(arc, `in`) > targetLabel)
            require(
                arc.label() < targetLabel
            ) { "arc.label=" + arc.label() + " vs targetLabel=" + targetLabel }
            pushLast()
            return null
        }
    }

    @Throws(IOException::class)
    private fun doSeekFloorList(arc: Arc<T>, targetLabel: Int): Arc<T>? {
        var arc = arc
        var targetLabel = targetLabel
        if (arc.label() == targetLabel) {
            // Match -- recurse
            output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
            if (targetLabel == FST.END_LABEL) {
                return null
            }
            this.setCurrentLabel(arc.label())
            incr()
            return fst.readFirstTargetArc(arc, getArc(upto), fstReader)
        } else if (arc.label() > targetLabel) {
            // TODO: if each arc could somehow read the arc just
            // before, we can save this re-scan.  The ceil case
            // doesn't need this because it reads the next arc
            // instead:
            while (true) {
                // First, walk backwards until we find a first arc
                // that's before our target label:
                fst.readFirstTargetArc(getArc(upto - 1), arc, fstReader)
                if (arc.label() < targetLabel) {
                    // Then, scan forwards to the arc just before
                    // the targetLabel:
                    while (!arc.isLast && fst.readNextArcLabel(arc, fstReader) < targetLabel) {
                        fst.readNextArc(arc, fstReader)
                    }
                    pushLast()
                    return null
                }
                upto--
                if (upto == 0) {
                    return null
                }
                targetLabel = this.getTargetLabel()
                arc = getArc(upto)
            }
        } else if (!arc.isLast) {
            // System.out.println("  check next label=" + fst.readNextArcLabel(arc) + " (" + (char)
            // fst.readNextArcLabel(arc) + ")");
            if (fst.readNextArcLabel(arc, fstReader) > targetLabel) {
                pushLast()
                return null
            } else {
                // keep scanning
                return fst.readNextArc(arc, fstReader)
            }
        } else {
            pushLast()
            return null
        }
    }

    /** Seeks to exactly target term.  */
    @Throws(IOException::class)
    fun doSeekExact(): Boolean {
        // TODO: possibly caller could/should provide common
        // prefix length  ie this work may be redundant if
        // caller is in fact intersecting against its own
        // automaton

        // System.out.println("FE: seek exact upto=" + upto);

        // Save time by starting at the end of the shared prefix
        // b/w our current term & the target:

        rewindPrefix()

        // System.out.println("FE: after rewind upto=" + upto);
        var arc: Arc<T> = getArc(upto - 1)
        var targetLabel = this.getTargetLabel()

        val fstReader: BytesReader = fst.getBytesReader()

        while (true) {
            // System.out.println("  cycle target=" + (targetLabel == -1  "-1" : (char) targetLabel));
            val nextArc = fst.findTargetArc(targetLabel, arc, getArc(upto), fstReader)
            if (nextArc == null) {
                // short circuit
                // upto--;
                // upto = 0;
                fst.readFirstTargetArc(arc, getArc(upto), fstReader)
                // System.out.println("  no match upto=" + upto);
                return false
            }
            // Match -- recurse:
            output[upto] = fst.outputs.add(output[upto - 1], nextArc.output()!!)
            if (targetLabel == FST.END_LABEL) {
                // System.out.println("  return found; upto=" + upto + " output=" + output[upto] + "
                // nextArc=" + nextArc.isLast);
                return true
            }
            this.setCurrentLabel(targetLabel)
            incr()
            targetLabel = this.getTargetLabel()
            arc = nextArc
        }
    }

    private fun incr() {
        upto++
        grow()
        if (arcs.size <= upto) {
            val newArcs: Array<Arc<T>?> =
                kotlin.arrayOfNulls(ArrayUtil.oversize(1 + upto, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
            System.arraycopy(arcs, 0, newArcs, 0, arcs.size)
            arcs = newArcs
        }
        if (output.size <= upto) {
            val newOutput =
                kotlin.arrayOfNulls<Any>(
                    ArrayUtil.oversize(
                        1 + upto,
                        RamUsageEstimator.NUM_BYTES_OBJECT_REF
                    )
                ) as Array<T>
            System.arraycopy(output, 0, newOutput, 0, output.size)
            output = newOutput
        }
    }

    // Appends current arc, and then recurses from its target,
    // appending first arc all the way to the final node
    @Throws(IOException::class)
    private fun pushFirst() {
        var arc = checkNotNull(arcs[upto])
        while (true) {
            output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
            if (arc.label() == FST.END_LABEL) {
                // Final node
                break
            }
            // System.out.println("  pushFirst label=" + (char) arc.label + " upto=" + upto + " output=" +
            // fst.outputs.outputToString(output[upto]));
            this.setCurrentLabel(arc.label())
            incr()

            val nextArc = getArc(upto)
            fst.readFirstTargetArc(arc, nextArc, fstReader)
            arc = nextArc
        }
    }

    // Recurses from current arc, appending last arc all the
    // way to the first final node
    @Throws(IOException::class)
    private fun pushLast() {
        var arc = checkNotNull(arcs[upto])
        while (true) {
            this.setCurrentLabel(arc.label())
            output[upto] = fst.outputs.add(output[upto - 1], arc.output()!!)
            if (arc.label() == FST.END_LABEL) {
                // Final node
                break
            }
            incr()

            arc = fst.readLastTargetArc(arc, getArc(upto), fstReader)
        }
    }

    private fun getArc(idx: Int): Arc<T> {
        if (arcs[idx] == null) {
            arcs[idx] = Arc()
        }
        return arcs[idx]!!
    }
}
