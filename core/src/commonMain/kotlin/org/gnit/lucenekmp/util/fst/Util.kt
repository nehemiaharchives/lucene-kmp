package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.FST.Arc
import org.gnit.lucenekmp.util.fst.FST.Arc.BitTable
import org.gnit.lucenekmp.util.fst.FST.BytesReader
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Writer
import org.gnit.lucenekmp.jdkport.toHexString
import org.gnit.lucenekmp.jdkport.TreeSet
import okio.IOException
import kotlin.experimental.and

/**
 * Static helper methods.
 *
 * @lucene.experimental
 */
object Util {
    /** Looks up the output for this input, or null if the input is not accepted.  */
    @Throws(IOException::class)
    fun <T> get(fst: FST<T>, input: IntsRef): T? {
        // TODO: would be nice not to alloc this on every lookup

        val arc = fst.getFirstArc(Arc())

        val fstReader: BytesReader = fst.getBytesReader()

        // Accumulate output as we go
        var output = fst.outputs.noOutput
        for (i in 0..<input.length) {
            if (fst.findTargetArc(input.ints[input.offset + i], arc, arc, fstReader) == null) {
                return null
            }
            output = fst.outputs.add(output, arc.output()!!)
        }

        return if (arc.isFinal) {
            fst.outputs.add(output, arc.nextFinalOutput()!!)
        } else {
            null
        }
    }

    // TODO: maybe a CharsRef version for BYTE2
    /** Looks up the output for this input, or null if the input is not accepted  */
    @Throws(IOException::class)
    fun <T> get(fst: FST<T>, input: BytesRef): T? {
        require(fst.metadata.inputType === FST.INPUT_TYPE.BYTE1)

        val fstReader: BytesReader = fst.getBytesReader()

        // TODO: would be nice not to alloc this on every lookup
        val arc = fst.getFirstArc(Arc())

        // Accumulate output as we go
        var output = fst.outputs.noOutput
        for (i in 0..<input.length) {
            if (fst.findTargetArc((input.bytes[i + input.offset] and 0xFF.toByte()).toInt(), arc, arc, fstReader) == null) {
                return null
            }
            output = fst.outputs.add(output, arc.output()!!)
        }

        return if (arc.isFinal) {
            fst.outputs.add(output, arc.nextFinalOutput()!!)
        } else {
            null
        }
    }

    /** Starting from node, find the top N min cost completions to a final node.  */
    @Throws(IOException::class)
    fun <T> shortestPaths(
        fst: FST<T>,
        fromNode: Arc<T>,
        startOutput: T,
        comparator: Comparator<T>,
        topN: Int,
        allowEmptyString: Boolean
    ): TopResults<T> {
        // All paths are kept, so we can pass topN for
        // maxQueueDepth and the pruning is admissible:

        val searcher = TopNSearcher<T>(fst, topN, topN, comparator)

        // since this search is initialized with a single start node
        // it is okay to start with an empty input path here
        searcher.addStartPaths(fromNode, startOutput, allowEmptyString, IntsRefBuilder())
        return searcher.search()
    }

    /**
     * Dumps an [FST] to a GraphViz's `dot` language description for visualization.
     * Example of use:
     *
     * <pre class="prettyprint">
     * PrintWriter pw = new PrintWriter(&quot;out.dot&quot;);
     * Util.toDot(fst, pw, true, true);
     * pw.close();
    </pre> *
     *
     * and then, from command line:
     *
     * <pre>
     * dot -Tpng -o out.png out.dot
    </pre> *
     *
     *
     * Note: larger FSTs (a few thousand nodes) won't even render, don't bother.
     *
     * @param sameRank If `true`, the resulting `dot` file will try to order
     * states in layers of breadth-first traversal. This may mess up arcs, but makes the output
     * FST's structure a bit clearer.
     * @param labelStates If `true` states will have labels equal to their offsets in their
     * binary format. Expands the graph considerably.
     * @see [graphviz project](http://www.graphviz.org/)
     */
    @Throws(IOException::class)
    fun <T> toDot(fst: FST<T>, out: Writer, sameRank: Boolean, labelStates: Boolean) {
        val expandedNodeColor = "blue"

        // This is the start arc in the automaton (from the epsilon state to the first state
        // with outgoing transitions.
        val startArc = fst.getFirstArc(Arc())

        // A queue of transitions to consider for the next level.
        val thisLevelQueue: MutableList<Arc<T>> = ArrayList()

        // A queue of transitions to consider when processing the next level.
        val nextLevelQueue: MutableList<Arc<T>> = ArrayList()
        nextLevelQueue.add(startArc)

        // System.out.println("toDot: startArc: " + startArc);

        // A list of states on the same level (for ranking).
        val sameLevelStates = IntArrayList()

        // A bitset of already seen states (target offset).
        val seen = BitSet()
        seen.set(startArc.target().toInt())

        // Shape for states.
        val stateShape = "circle"
        val finalStateShape = "doublecircle"

        // Emit DOT prologue.
        out.write("digraph FST {\n")
        out.write("  rankdir = LR; splines=true; concentrate=true; ordering=out; ranksep=2.5; \n")

        if (!labelStates) {
            out.write("  node [shape=circle, width=.2, height=.2, style=filled]\n")
        }

        emitDotState(out, "initial", "point", "white", "")

        val NO_OUTPUT = fst.outputs.noOutput
        val r: BytesReader = fst.getBytesReader()

        // final FST.Arc<T> scratchArc = new FST.Arc<>();
        run {
            val stateColor: String? = if (fst.isExpandedTarget(startArc, r)) {
                expandedNodeColor
            } else {
                null
            }

            val isFinal: Boolean
            val finalOutput: T?
            if (startArc.isFinal) {
                isFinal = true
                finalOutput = if (startArc.nextFinalOutput() === NO_OUTPUT) null else startArc.nextFinalOutput()
            } else {
                isFinal = false
                finalOutput = null
            }
            Util.emitDotState(
                out,
                startArc.target().toString(),
                if (isFinal) finalStateShape else stateShape,
                stateColor,
                if (finalOutput == null) "" else fst.outputs.outputToString(finalOutput)
            )
        }

        out.write("  initial -> " + startArc.target() + "\n")

        var level = 0

        while (!nextLevelQueue.isEmpty()) {
            // we could double buffer here, but it doesn't matter probably.
            // System.out.println("next level=" + level);
            thisLevelQueue.addAll(nextLevelQueue)
            nextLevelQueue.clear()

            level++
            out.write("\n  // Transitions and states at level: $level\n")
            while (!thisLevelQueue.isEmpty()) {
                val arc = thisLevelQueue.removeAt(thisLevelQueue.size - 1)
                // System.out.println("  pop: " + arc);
                if (FST.targetHasArcs(arc)) {
                    // scan all target arcs
                    // System.out.println("  readFirstTarget...");

                    val node = arc.target()

                    fst.readFirstRealTargetArc(arc.target(), arc, r)

                    // System.out.println("    firstTarget: " + arc);
                    while (true) {
                        // System.out.println("  cycle arc=" + arc);
                        // Emit the unseen state and add it to the queue for the next level.

                        if (arc.target() >= 0 && !seen.get(arc.target().toInt())) {
                            /*
                                         boolean isFinal = false;
                                         T finalOutput = null;
                                         fst.readFirstTargetArc(arc, scratchArc);
                                         if (scratchArc.isFinal() && fst.targetHasArcs(scratchArc)) {
                                           // target is final
                                           isFinal = true;
                                           finalOutput = scratchArc.output == NO_OUTPUT  null : scratchArc.output;
                                           System.out.println("dot hit final label=" + (char) scratchArc.label);
                                         }
                                         */
                            val stateColor: String? = if (fst.isExpandedTarget(arc, r)) {
                                expandedNodeColor
                            } else {
                                null
                            }
                            val finalOutput: String = if (arc.nextFinalOutput() != null && arc.nextFinalOutput() !== NO_OUTPUT) {
                                fst.outputs.outputToString(arc.nextFinalOutput()!!)
                            } else {
                                ""
                            }

                            emitDotState(out, arc.target().toString(), stateShape, stateColor, finalOutput)
                            // To see the node address, use this instead:
                            // emitDotState(out, Integer.toString(arc.target), stateShape, stateColor,
                            // String.valueOf(arc.target));
                            seen.set(arc.target().toInt())
                            nextLevelQueue.add(Arc<T>().copyFrom(arc))
                            sameLevelStates.add(arc.target().toInt())
                        }

                        var outs: String
                        outs = if (arc.output() !== NO_OUTPUT) {
                            "/" + fst.outputs.outputToString(arc.output()!!)
                        } else {
                            ""
                        }

                        if (!FST.targetHasArcs(arc) && arc.isFinal && arc.nextFinalOutput() !== NO_OUTPUT) {
                            // Tricky special case: sometimes, due to
                            // pruning, the builder can [sillily] produce
                            // an FST with an arc into the final end state
                            // (-1) but also with a next final output; in
                            // this case we pull that output up onto this
                            // arc
                            outs = outs + "/[" + fst.outputs.outputToString(arc.nextFinalOutput()!!) + "]"
                        }
                        val arcColor: String = if (arc.flag(FST.BIT_TARGET_NEXT)) {
                            "red"
                        } else {
                            "black"
                        }

                        require(arc.label() != FST.END_LABEL)
                        out.write(
                            ("  "
                                    + node
                                    + " -> "
                                    + arc.target()
                                    + " [label=\""
                                    + printableLabel(arc.label())
                                    + outs
                                    + "\""
                                    + (if (arc.isFinal) " style=\"bold\"" else "")
                                    + " color=\""
                                    + arcColor
                                    + "\"]\n")
                        )

                        // Break the loop if we're on the last arc of this state.
                        if (arc.isLast) {
                            // System.out.println("    break");
                            break
                        }
                        fst.readNextRealArc(arc, r)
                    }
                }
            }

            // Emit state ranking information.
            if (sameRank && sameLevelStates.size() > 1) {
                out.write("  {rank=same; ")
                for (state in sameLevelStates) {
                    out.write("${state.value}; ")
                }
                out.write(" }\n")
            }
            sameLevelStates.clear()
        }

        // Emit terminating state (always there anyway).
        out.write("  -1 [style=filled, color=black, shape=doublecircle, label=\"\"]\n\n")
        out.write("  {rank=sink; -1 }\n")

        out.write("}\n")
        out.flush()
    }

    /** Emit a single state in the `dot` language.  */
    @Throws(IOException::class)
    private fun emitDotState(
        out: Writer, name: String, shape: String?, color: String?, label: String?
    ) {
        out.write(
            ("  "
                    + name
                    + " ["
                    + (if (shape != null) "shape=$shape" else "")
                    + " "
                    + (if (color != null) "color=$color" else "")
                    + " "
                    + (if (label != null) "label=\"$label\"" else "label=\"\"")
                    + " "
                    + "]\n")
        )
    }

    /** Ensures an arc's label is indeed printable (dot uses US-ASCII).  */
    private fun printableLabel(label: Int): String {
        // Any ordinary ascii character, except for " or \, are
        // printed as the character; else, as a hex string:
        if (label >= 0x20 && label <= 0x7d && label != 0x22 && label != 0x5c) { // " OR \
            return label.toChar().toString() // TODO not sure if this way is correct
        }
        return "0x" + Int.toHexString(label)
    }

    /** Just maps each UTF16 unit (char) to the ints in an IntsRef.  */
    fun toUTF16(s: CharSequence, scratch: IntsRefBuilder): IntsRef {
        val charLimit = s.length
        scratch.setLength(charLimit)
        scratch.growNoCopy(charLimit)
        for (idx in 0..<charLimit) {
            scratch.setIntAt(idx, s[idx].digitToInt())
        }
        return scratch.get()
    }

    /**
     * Decodes the Unicode codepoints from the provided CharSequence and places them in the provided
     * scratch IntsRef, which must not be null, returning it.
     */
    fun toUTF32(s: CharSequence, scratch: IntsRefBuilder): IntsRef {
        var charIdx = 0
        var intIdx = 0
        val charLimit = s.length
        while (charIdx < charLimit) {
            scratch.grow(intIdx + 1)
            val utf32: Int = Character.codePointAt(s, charIdx)
            scratch.setIntAt(intIdx, utf32)
            charIdx += Character.charCount(utf32)
            intIdx++
        }
        scratch.setLength(intIdx)
        return scratch.get()
    }

    /**
     * Decodes the Unicode codepoints from the provided char[] and places them in the provided scratch
     * IntsRef, which must not be null, returning it.
     */
    fun toUTF32(s: CharArray, offset: Int, length: Int, scratch: IntsRefBuilder): IntsRef {
        var charIdx = offset
        var intIdx = 0
        val charLimit = offset + length
        while (charIdx < charLimit) {
            scratch.grow(intIdx + 1)
            val utf32: Int = Character.codePointAt(s, charIdx, charLimit)
            scratch.setIntAt(intIdx, utf32)
            charIdx += Character.charCount(utf32)
            intIdx++
        }
        scratch.setLength(intIdx)
        return scratch.get()
    }

    /** Just takes unsigned byte values from the BytesRef and converts into an IntsRef.  */
    fun toIntsRef(input: BytesRef, scratch: IntsRefBuilder): IntsRef {
        scratch.growNoCopy(input.length)
        for (i in 0..<input.length) {
            scratch.setIntAt(i, (input.bytes[i + input.offset] and 0xFF.toByte()).toInt())
        }
        scratch.setLength(input.length)
        return scratch.get()
    }

    /** Just converts IntsRef to BytesRef; you must ensure the int values fit into a byte.  */
    fun toBytesRef(input: IntsRef, scratch: BytesRefBuilder): BytesRef {
        scratch.growNoCopy(input.length)
        for (i in 0..<input.length) {
            val value: Int = input.ints[i + input.offset]
            // NOTE: we allow -128 to 255
            require(value >= Byte.Companion.MIN_VALUE && value <= 255) { "value $value doesn't fit into byte" }
            scratch.setByteAt(i, value.toByte())
        }
        scratch.setLength(input.length)
        return scratch.get()
    }

    // Uncomment for debugging:
    /*
  public static <T> void dotToFile(FST<T> fst, String filePath) throws IOException {
    Writer w = new OutputStreamWriter(new FileOutputStream(filePath));
    toDot(fst, w, true, true);
    w.close();
  }
  */
    /**
     * Reads the first arc greater or equal than the given label into the provided arc in place and
     * returns it iff found, otherwise return `null`.
     *
     * @param label the label to ceil on
     * @param fst the fst to operate on
     * @param follow the arc to follow reading the label from
     * @param arc the arc to read into in place
     * @param in the fst's [BytesReader]
     */
    @Throws(IOException::class)
    fun <T> readCeilArc(
        label: Int, fst: FST<T>, follow: Arc<T>, arc: Arc<T>, `in`: BytesReader
    ): Arc<T>? {
        if (label == FST.END_LABEL) {
            return FST.readEndArc(follow, arc)
        }
        if (!FST.targetHasArcs(follow)) {
            return null
        }
        fst.readFirstTargetArc(follow, arc, `in`)
        if (arc.bytesPerArc() != 0 && arc.label() != FST.END_LABEL) {
            if (arc.nodeFlags() == FST.ARCS_FOR_DIRECT_ADDRESSING) {
                // Fixed length arcs in a direct addressing node.
                val targetIndex: Int = label - arc.label()
                if (targetIndex >= arc.numArcs()) {
                    return null
                } else if (targetIndex < 0) {
                    return arc
                } else {
                    if (BitTable.isBitSet(targetIndex, arc, `in`)) {
                        fst.readArcByDirectAddressing(arc, `in`, targetIndex)
                        require(arc.label() == label)
                    } else {
                        val ceilIndex: Int = BitTable.nextBitSet(targetIndex, arc, `in`)
                        require(ceilIndex != -1)
                        fst.readArcByDirectAddressing(arc, `in`, ceilIndex)
                        require(arc.label() > label)
                    }
                    return arc
                }
            } else if (arc.nodeFlags() == FST.ARCS_FOR_CONTINUOUS) {
                val targetIndex: Int = label - arc.label()
                if (targetIndex >= arc.numArcs()) {
                    return null
                } else if (targetIndex < 0) {
                    return arc
                } else {
                    fst.readArcByContinuous(arc, `in`, targetIndex)
                    require(arc.label() == label)
                    return arc
                }
            }
            // Fixed length arcs in a binary search node.
            var idx = binarySearch(fst, arc, label)
            if (idx >= 0) {
                return fst.readArcByIndex(arc, `in`, idx)
            }
            idx = -1 - idx
            if (idx == arc.numArcs()) {
                // DEAD END!
                return null
            }
            return fst.readArcByIndex(arc, `in`, idx)
        }

        // Variable length arcs in a linear scan list,
        // or special arc with label == FST.END_LABEL.
        fst.readFirstRealTargetArc(follow.target(), arc, `in`)

        while (true) {
            // System.out.println("  non-bs cycle");
            if (arc.label() >= label) {
                // System.out.println("    found!");
                return arc
            } else if (arc.isLast) {
                return null
            } else {
                fst.readNextRealArc(arc, `in`)
            }
        }
    }

    /**
     * Perform a binary search of Arcs encoded as a packed array
     *
     * @param fst the FST from which to read
     * @param arc the starting arc; sibling arcs greater than this will be searched. Usually the first
     * arc in the array.
     * @param targetLabel the label to search for
     * @param <T> the output type of the FST
     * @return the index of the Arc having the target label, or if no Arc has the matching label,
     * `-1 - idx)`, where `idx` is the index of the Arc with the next highest label,
     * or the total number of arcs if the target label exceeds the maximum.
     * @throws IOException when the FST reader does
    </T> */
    @Throws(IOException::class)
    fun <T> binarySearch(fst: FST<T>, arc: Arc<T>, targetLabel: Int): Int {
        require(
            arc.nodeFlags() == FST.ARCS_FOR_BINARY_SEARCH
        ) {
            ("Arc is not encoded as packed array for binary search (nodeFlags="
                    + arc.nodeFlags()
                    + ")")
        }
        val `in`: BytesReader = fst.getBytesReader()
        var low = arc.arcIdx()
        var mid: Int
        var high = arc.numArcs() - 1
        while (low <= high) {
            mid = (low + high) ushr 1
            `in`.position = arc.posArcsStart()
            `in`.skipBytes(arc.bytesPerArc().toLong() * mid + 1)
            val midLabel = fst.readLabel(`in`)
            val cmp = midLabel - targetLabel
            if (cmp < 0) {
                low = mid + 1
            } else if (cmp > 0) {
                high = mid - 1
            } else {
                return mid
            }
        }
        return -1 - low
    }

    /**
     * Represents a path in TopNSearcher.
     *
     * @lucene.experimental
     */
    class FSTPath<T> internal constructor(
        /** Holds cost plus any usage-specific output:  */
        var output: T,
        arc: Arc<T>,
        val input: IntsRefBuilder,
        val boost: Float,
        val context: CharSequence?,
        // Custom int payload for consumers; the NRT suggester uses this to record if this path has
        // already enumerated a surface form
        var payload: Int
    ) {
        /** Holds the last arc appended to this path  */
        var arc: Arc<T> = Arc<T>().copyFrom(arc)

        fun newPath(output: T, input: IntsRefBuilder): FSTPath<T> {
            return FSTPath(output, this.arc, input, this.boost, this.context, this.payload)
        }

        override fun toString(): String {
            return ("input="
                    + input.get()
                    + " output="
                    + output
                    + " context="
                    + context
                    + " boost="
                    + boost
                    + " payload="
                    + payload)
        }
    }

    /** Compares first by the provided comparator, and then tie breaks by path.input.  */
    private class TieBreakByInputComparator<T>(val comparator: Comparator<T>) :
        Comparator<FSTPath<T>> {
        override fun compare(a: FSTPath<T>, b: FSTPath<T>): Int {
            val cmp: Int = comparator.compare(a.output, b.output)
            return if (cmp == 0) {
                a.input.get().compareTo(b.input.get())
            } else {
                cmp
            }
        }

    }

    /** Utility class to find top N shortest paths from start point(s).  */
    open class TopNSearcher<T>(
        private val fst: FST<T>,
        private val topN: Int,
        private val maxQueueDepth: Int,
        private val comparator: Comparator<T>,
        private val pathComparator: Comparator<FSTPath<T>>
    ) {
        private val bytesReader: BytesReader = fst.getBytesReader()

        private val scratchArc: Arc<T> = Arc()

        var queue: TreeSet<FSTPath<T>>?

        /**
         * Creates an unbounded TopNSearcher
         *
         * @param fst the [org.apache.lucene.util.fst.FST] to search on
         * @param topN the number of top scoring entries to retrieve
         * @param maxQueueDepth the maximum size of the queue of possible top entries
         * @param comparator the comparator to select the top N
         */
        constructor(fst: FST<T>, topN: Int, maxQueueDepth: Int, comparator: Comparator<T>) : this(
            fst,
            topN,
            maxQueueDepth,
            comparator,
            TieBreakByInputComparator<T>(comparator)
        )

        init {
            queue = TreeSet<FSTPath<T>>(pathComparator)
        }

        // If back plus this arc is competitive then add to queue:
        protected fun addIfCompetitive(path: FSTPath<T>) {
            checkNotNull(queue)

            val output = fst.outputs.add(path.output, path.arc.output()!!)

            if (queue!!.size == maxQueueDepth) {
                val bottom: FSTPath<T> = queue!!.last()
                val comp: Int = pathComparator.compare(path, bottom)
                if (comp > 0) {
                    // Doesn't compete
                    return
                } else if (comp == 0) {
                    // Tie break by alpha sort on the input:
                    path.input.append(path.arc.label())
                    val cmp = bottom.input.get().compareTo(path.input.get())
                    path.input.setLength(path.input.length() - 1)

                    // We should never see dups:
                    require(cmp != 0)

                    if (cmp < 0) {
                        // Doesn't compete
                        return
                    }
                }
                // Competes
            }

            // else ... Queue isn't full yet, so any path we hit competes:

            // copy over the current input to the new input
            // and add the arc.label to the end
            val newInput = IntsRefBuilder()
            newInput.copyInts(path.input.get())
            newInput.append(path.arc.label())

            val newPath = path.newPath(output, newInput)
            if (acceptPartialPath(newPath)) {
                queue!!.add(newPath)
                if (queue!!.size == maxQueueDepth + 1) {
                    queue!!.pollLast()
                }
            }
        }

        @Throws(IOException::class)
        fun addStartPaths(
            node: Arc<T>, startOutput: T, allowEmptyString: Boolean, input: IntsRefBuilder
        ) {
            addStartPaths(node, startOutput, allowEmptyString, input, 0f, null, -1)
        }

        /**
         * Adds all leaving arcs, including 'finished' arc, if the node is final, from this node into
         * the queue!!.
         */
        @Throws(IOException::class)
        fun addStartPaths(
            node: Arc<T>,
            startOutput: T,
            allowEmptyString: Boolean,
            input: IntsRefBuilder,
            boost: Float,
            context: CharSequence?,
            payload: Int
        ) {
            // De-dup NO_OUTPUT since it must be a singleton:

            var startOutput = startOutput
            if (startOutput == fst.outputs.noOutput) {
                startOutput = fst.outputs.noOutput
            }

            val path = FSTPath(startOutput, node, input, boost, context, payload)
            fst.readFirstTargetArc(node, path.arc, bytesReader)

            // Bootstrap: find the min starting arc
            while (true) {
                if (allowEmptyString || path.arc.label() != FST.END_LABEL) {
                    addIfCompetitive(path)
                }
                if (path.arc.isLast) {
                    break
                }
                fst.readNextArc(path.arc, bytesReader)
            }
        }

        @Throws(IOException::class)
        fun search(): TopResults<T> {
            val results: MutableList<Result<T>> = ArrayList<Result<T>>()

            val fstReader: BytesReader = fst.getBytesReader()
            val NO_OUTPUT = fst.outputs.noOutput

            // TODO: we could enable FST to sorting arcs by weight
            // as it freezes... can easily do this on first pass
            // (w/o requiring rewrite)

            // TODO: maybe we should make an FST.INPUT_TYPE.BYTE0.5!
            // (nibbles)
            var rejectCount = 0

            // For each top N path:
            while (results.size < topN) {

                if (queue == null) {
                    // Ran out of paths
                    break
                }

                // Remove top path since we are now going to
                // pursue it:
                val path = queue!!.pollFirst()

                if (path == null) {
                    // There were less than topN paths available:
                    break
                }

                // System.out.println("pop path=" + path + " arc=" + path.arc.output);
                if (!acceptPartialPath(path)) {
                    continue
                }

                if (path.arc.label() == FST.END_LABEL) {
                    // Empty string!
                    path.input.setLength(path.input.length() - 1)
                    results.add(Result(path.input.get(), path.output))
                    continue
                }

                if (results.size == topN - 1 && maxQueueDepth == topN) {
                    // Last path -- don't bother w/ queue anymore:
                    queue = null
                }

                // We take path and find its "0 output completion",
                // ie, just keep traversing the first arc with
                // NO_OUTPUT that we can find, since this must lead
                // to the minimum path that completes from
                // path.arc.

                // For each input letter:
                while (true) {
                    fst.readFirstTargetArc(path.arc, path.arc, fstReader)

                    // For each arc leaving this node:
                    var foundZero = false
                    var arcCopyIsPending = false
                    while (true) {
                        // tricky: instead of comparing output == 0, we must
                        // express it via the comparator compare(output, 0) == 0
                        if (comparator.compare(NO_OUTPUT, path.arc.output()!!) == 0) {
                            if (queue == null) {
                                foundZero = true
                                break
                            } else if (!foundZero) {
                                arcCopyIsPending = true
                                foundZero = true
                            } else {
                                addIfCompetitive(path)
                            }
                        } else if (queue != null) {
                            addIfCompetitive(path)
                        }
                        if (path.arc.isLast) {
                            break
                        }
                        if (arcCopyIsPending) {
                            scratchArc.copyFrom(path.arc)
                            arcCopyIsPending = false
                        }
                        fst.readNextArc(path.arc, fstReader)
                    }

                    require(foundZero)

                    if (queue != null && !arcCopyIsPending) {
                        path.arc.copyFrom(scratchArc)
                    }

                    if (path.arc.label() == FST.END_LABEL) {
                        // Add final output:
                        path.output = fst.outputs.add(path.output, path.arc.output()!!)
                        if (acceptResult(path)) {
                            results.add(Result(path.input.get(), path.output))
                        } else {
                            rejectCount++
                        }
                        break
                    } else {
                        path.input.append(path.arc.label())
                        path.output = fst.outputs.add(path.output, path.arc.output()!!)
                        if (!acceptPartialPath(path)) {
                            break
                        }
                    }
                }
            }
            return TopResults(rejectCount + topN <= maxQueueDepth, results)
        }

        protected fun acceptResult(path: FSTPath<T>): Boolean {
            return acceptResult(path.input.get(), path.output)
        }

        /** Override this to prevent considering a path before it's complete  */
        protected fun acceptPartialPath(path: FSTPath<T>): Boolean {
            return true
        }

        protected fun acceptResult(input: IntsRef, output: T): Boolean {
            return true
        }
    }

    /**
     * Holds a single input (IntsRef) + output, returned by [shortestPaths()][.shortestPaths].
     */
    class Result<T>(val input: IntsRef, val output: T)

    /** Holds the results for a top N search using [TopNSearcher]  */
    class TopResults<T> internal constructor(
        /**
         * `true` iff this is a complete result ie. if the specified queue size was large
         * enough to find the complete list of results. This might be `false` if the [ ] rejected too many results.
         */
        val isComplete: Boolean,
        /** The top results  */
        val topN: MutableList<Result<T>>
    ) : Iterable<Result<T>> {
        override fun iterator(): MutableIterator<Result<T>> {
            return topN.iterator()
        }
    }
}
