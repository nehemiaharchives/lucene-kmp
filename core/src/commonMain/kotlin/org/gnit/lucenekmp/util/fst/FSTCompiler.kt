package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.FST.Companion.ARCS_FOR_BINARY_SEARCH
import org.gnit.lucenekmp.util.fst.FST.Companion.ARCS_FOR_CONTINUOUS
import org.gnit.lucenekmp.util.fst.FST.Companion.ARCS_FOR_DIRECT_ADDRESSING
import org.gnit.lucenekmp.util.fst.FST.Companion.BIT_ARC_HAS_FINAL_OUTPUT
import org.gnit.lucenekmp.util.fst.FST.Companion.BIT_ARC_HAS_OUTPUT
import org.gnit.lucenekmp.util.fst.FST.Companion.BIT_FINAL_ARC
import org.gnit.lucenekmp.util.fst.FST.Companion.BIT_LAST_ARC
import org.gnit.lucenekmp.util.fst.FST.Companion.BIT_STOP_NODE
import org.gnit.lucenekmp.util.fst.FST.Companion.BIT_TARGET_NEXT
import org.gnit.lucenekmp.util.fst.FST.Companion.FINAL_END_NODE
import org.gnit.lucenekmp.util.fst.FST.Companion.NON_FINAL_END_NODE
import org.gnit.lucenekmp.util.fst.FST.Companion.getNumPresenceBytes
import org.gnit.lucenekmp.util.fst.FST.INPUT_TYPE
import org.gnit.lucenekmp.util.fst.FST.FSTMetadata
import org.gnit.lucenekmp.util.fst.FST.BytesReader
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import kotlin.experimental.or
import kotlin.math.max

/**
 * Builds a minimal FST (maps an IntsRef term to an arbitrary output) from pre-sorted terms with
 * outputs. The FST becomes an FSA if you use NoOutputs. The FST is written on-the-fly into a
 * compact serialized format byte array, which can be saved to / loaded from a Directory or used
 * directly for traversal. The FST is always finite (no cycles).
 *
 *
 * NOTE: The algorithm is described at
 * http://citeseerx.ist.psu.edu/viewdoc/summarydoi=10.1.1.24.3698
 *
 *
 * The parameterized type T is the output type. See the subclasses of [Outputs].
 *
 *
 * FSTs larger than 2.1GB are now possible (as of Lucene 4.2). FSTs containing more than 2.1B
 * nodes are also now possible, however they cannot be packed.
 *
 *
 * It now supports 3 different workflows:
 *
 *
 * - Build FST and use it immediately entirely in RAM and then discard it
 *
 *
 * - Build FST and use it immediately entirely in RAM and also save it to other DataOutput, and
 * load it later and use it
 *
 *
 * - Build FST but stream it immediately to disk (except the FSTMetaData, to be saved at the
 * end). In order to use it, you need to construct the corresponding DataInput and use the FST
 * constructor to read it.
 *
 * @lucene.experimental
 */
class FSTCompiler<T> private constructor(
    inputType: INPUT_TYPE,
    suffixRAMLimitMB: Double,
    outputs: Outputs<T>,
    val allowFixedLengthArcs: Boolean,
    dataOutput: DataOutput,
    val directAddressingMaxOversizingFactor: Float,
    val version: Int
) {
    private val suffixDedupCache: FSTSuffixNodeCache<T>?

    // a temporary FST used during building for FSTSuffixNodeCache cache
    val fst: FST<T>
    private val NO_OUTPUT: T

    // private static final boolean DEBUG = true;
    private val lastInput: IntsRefBuilder = IntsRefBuilder()

    // indicates whether we are not yet to write the padding byte
    private var paddingBytePending: Boolean

    // NOTE: cutting this over to ArrayList instead loses ~6%
    // in build performance on 9.8M Wikipedia terms; so we
    // left this as an array:
    // current "frontier"
    private var frontier: Array<UnCompiledNode<T>>

    // Used for the BIT_TARGET_NEXT optimization (whereby
    // instead of storing the address of the target node for
    // a given arc, we mark a single bit noting that the next
    // node in the byte[] is the target node):
    var lastFrozenNode: Long = 0

    // Reused temporarily while building the FST:
    var numBytesPerArc: IntArray = IntArray(4)
    var numLabelBytesPerArc: IntArray = IntArray(numBytesPerArc.size)
    val fixedLengthArcsBuffer: FixedLengthArcsBuffer = FixedLengthArcsBuffer()

    var arcCount: Long = 0
    var nodeCount: Long = 0
    var binarySearchNodeCount: Long = 0
    var directAddressingNodeCount: Long = 0
    var continuousNodeCount: Long = 0

    var directAddressingExpansionCredit: Long = 0

    // the DataOutput to stream the FST bytes to
    val dataOutput: DataOutput

    // buffer to store bytes for the one node we are currently writing
    val scratchBytes: GrowableByteArrayDataOutput = GrowableByteArrayDataOutput()

    private var numBytesWritten: Long = 0

    init {
        // pad: ensure no node gets address 0 which is reserved to mean
        // the stop state w/ no arcs. the actual byte will be written lazily
        numBytesWritten++
        paddingBytePending = true
        this.dataOutput = dataOutput
        fst =
            FST(FSTMetadata(inputType, outputs, null, -1, version, 0), NULL_FST_READER)
        require(!(suffixRAMLimitMB < 0)) { "ramLimitMB must be >= 0; got: $suffixRAMLimitMB" }
        suffixDedupCache = if (suffixRAMLimitMB > 0) {
            FSTSuffixNodeCache(this, suffixRAMLimitMB)
        } else {
            null
        }
        NO_OUTPUT = outputs.getNoOutput()

        val f = kotlin.arrayOfNulls<UnCompiledNode<*>>(10) as Array<UnCompiledNode<T>>
        frontier = f
        for (idx in frontier.indices) {
            frontier[idx] = UnCompiledNode<T>(this, idx)
        }
    }

    /**
     * This class is used for FST backed by non-FSTReader DataOutput. It does not allow getting the
     * reverse BytesReader nor writing to a DataOutput.
     */
    private class NullFSTReader : FSTReader {
        override fun ramBytesUsed(): Long {
            return 0
        }

        override fun getReverseBytesReader(): BytesReader {
            throw UnsupportedOperationException(
                "FST was not constructed with getOnHeapReaderWriter()"
            )
        }

        override fun writeTo(out: DataOutput) {
            throw UnsupportedOperationException(
                "FST was not constructed with getOnHeapReaderWriter()"
            )
        }
    }

    /**
     * Get the respective [FSTReader] of the [DataOutput]. To call this method, you need
     * to use the default DataOutput or [.getOnHeapReaderWriter], otherwise we will throw
     * an exception.
     *
     * @return the DataOutput as FSTReader
     * @throws IllegalStateException if the DataOutput does not implement FSTReader
     */
    fun getFSTReader(): FSTReader {
        if (dataOutput is FSTReader) {
            return dataOutput as FSTReader
        }
        throw IllegalStateException(
            "The DataOutput must implement FSTReader, but got $dataOutput"
        )
    }

    /**
     * Fluent-style constructor for FST [FSTCompiler].
     *
     *
     * Creates an FST/FSA builder with all the possible tuning and construction tweaks. Read
     * parameter documentation carefully.
     */
    class Builder<T>(private val inputType: INPUT_TYPE, private val outputs: Outputs<T>) {
        private var suffixRAMLimitMB = 32.0
        private var allowFixedLengthArcs = true
        private var dataOutput: DataOutput? = null
        private var directAddressingMaxOversizingFactor = DIRECT_ADDRESSING_MAX_OVERSIZING_FACTOR
        private var version = FST.VERSION_CURRENT

        /**
         * The approximate maximum amount of RAM (in MB) to use holding the suffix cache, which enables
         * the FST to share common suffixes. Pass [Double.POSITIVE_INFINITY] to keep all suffixes
         * and create an exactly minimal FST. In this case, the amount of RAM actually used will be
         * bounded by the number of unique suffixes. If you pass a value smaller than the builder would
         * use, the least recently used suffixes will be discarded, thus reducing suffix sharing and
         * creating a non-minimal FST. In this case, the larger the limit, the closer the FST will be to
         * its true minimal size, with diminishing returns as you increase the limit. Pass `0` to
         * disable suffix sharing entirely, but note that the resulting FST can be substantially larger
         * than the minimal FST.
         *
         *
         * Note that this is not a precise limit. The current implementation uses hash tables to map
         * the suffixes, and approximates the rough overhead (unused slots) in the hash table.
         *
         *
         * Default = `32.0` MB.
         */
        fun suffixRAMLimitMB(mb: Double): Builder<T> {
            require(!(mb < 0)) { "suffixRAMLimitMB must be >= 0; got: $mb" }
            this.suffixRAMLimitMB = mb
            return this
        }

        /**
         * Pass `false` to disable the fixed length arc optimization (binary search or direct
         * addressing) while building the FST; this will make the resulting FST smaller but slower to
         * traverse.
         *
         *
         * Default = `true`.
         */
        fun allowFixedLengthArcs(allowFixedLengthArcs: Boolean): Builder<T> {
            this.allowFixedLengthArcs = allowFixedLengthArcs
            return this
        }

        /**
         * Set the [DataOutput] which is used for low-level writing of FST. If you want the FST to
         * be immediately readable, you need to use [FSTCompiler.getOnHeapReaderWriter].
         *
         *
         * Otherwise you need to construct the corresponding [ ] and use the FST constructor to read it.
         *
         * @param dataOutput the DataOutput
         * @return this builder
         * @see FSTCompiler.getOnHeapReaderWriter
         */
        fun dataOutput(dataOutput: DataOutput): Builder<T> {
            this.dataOutput = dataOutput
            return this
        }

        /**
         * Overrides the default the maximum oversizing of fixed array allowed to enable direct
         * addressing of arcs instead of binary search.
         *
         *
         * Setting this factor to a negative value (e.g. -1) effectively disables direct addressing,
         * only binary search nodes will be created.
         *
         *
         * This factor does not determine whether to encode a node with a list of variable length
         * arcs or with fixed length arcs. It only determines the effective encoding of a node that is
         * already known to be encoded with fixed length arcs.
         *
         *
         * Default = 1.
         */
        fun directAddressingMaxOversizingFactor(factor: Float): Builder<T> {
            this.directAddressingMaxOversizingFactor = factor
            return this
        }

        /** Expert: Set the codec version. *  */
        fun setVersion(version: Int): Builder<T> {
            require(!(version < FST.VERSION_90 || version > FST.VERSION_CURRENT)) {
                ("Expected version in range ["
                        + FST.VERSION_90
                        + ", "
                        + FST.VERSION_CURRENT
                        + "], got "
                        + version)
            }
            this.version = version
            return this
        }

        /** Creates a new [FSTCompiler].  */
        fun build(): FSTCompiler<T> {
            // create a default DataOutput if not specified
            if (dataOutput == null) {
                dataOutput = getOnHeapReaderWriter(15)
            }
            return FSTCompiler(
                inputType,
                suffixRAMLimitMB,
                outputs,
                allowFixedLengthArcs,
                dataOutput!!,
                directAddressingMaxOversizingFactor,
                version
            )
        }
    }

    fun getNodeCount(): Long {
        // 1+ in order to count the -1 implicit final node
        return 1 + nodeCount
    }

    @Throws(IOException::class)
    private fun compileNode(nodeIn: UnCompiledNode<T>): CompiledNode {
        val node: Long
        val bytesPosStart = numBytesWritten
        if (suffixDedupCache != null) {
            if (nodeIn.numArcs == 0) {
                node = addNode(nodeIn)
                lastFrozenNode = node
            } else {
                node = suffixDedupCache.add(nodeIn)
            }
        } else {
            node = addNode(nodeIn)
        }

        require(node != -2L)

        val bytesPosEnd = numBytesWritten
        if (bytesPosEnd != bytesPosStart) {
            // The FST added a new node:
            require(bytesPosEnd > bytesPosStart)
            lastFrozenNode = node
        }

        nodeIn.clear()

        val fn = CompiledNode()
        fn.node = node
        return fn
    }

    // serializes new node by appending its bytes to the end
    // of the current byte[]
    @Throws(IOException::class)
    fun addNode(nodeIn: UnCompiledNode<T>): Long {
        // System.out.println("FST.addNode pos=" + bytes.getPosition() + " numArcs=" + nodeIn.numArcs);
        if (nodeIn.numArcs == 0) {
            return if (nodeIn.isFinal) {
                FINAL_END_NODE
            } else {
                NON_FINAL_END_NODE
            }
        }
        // reset the scratch writer to prepare for new write
        scratchBytes.setPosition(0)

        val doFixedLengthArcs = shouldExpandNodeWithFixedLengthArcs(nodeIn)
        if (doFixedLengthArcs) {
            // System.out.println("  fixed length arcs");
            if (numBytesPerArc.size < nodeIn.numArcs) {
                numBytesPerArc = IntArray(ArrayUtil.oversize(nodeIn.numArcs, Int.SIZE_BYTES))
                numLabelBytesPerArc = IntArray(numBytesPerArc.size)
            }
        }

        arcCount += nodeIn.numArcs.toLong()

        val lastArc = nodeIn.numArcs - 1

        var lastArcStart: Long = 0
        var maxBytesPerArc = 0
        var maxBytesPerArcWithoutLabel = 0
        for (arcIdx in 0..<nodeIn.numArcs) {
            val arc = nodeIn.arcs[arcIdx]
            val target = arc.target as CompiledNode
            var flags = 0

            // System.out.println("  arc " + arcIdx + " label=" + arc.label + " -> target=" +
            // target.node);
            if (arcIdx == lastArc) {
                flags += BIT_LAST_ARC
            }

            if (lastFrozenNode == target.node && !doFixedLengthArcs) {
                // TODO: for better perf (but more RAM used) we
                // could avoid this except when arc is "near" the
                // last arc:
                flags += BIT_TARGET_NEXT
            }

            if (arc.isFinal) {
                flags += BIT_FINAL_ARC
                if (arc.nextFinalOutput !== NO_OUTPUT) {
                    flags += BIT_ARC_HAS_FINAL_OUTPUT
                }
            } else {
                require(arc.nextFinalOutput === NO_OUTPUT)
            }

            val targetHasArcs = target.node > 0

            if (!targetHasArcs) {
                flags += BIT_STOP_NODE
            }

            if (arc.output !== NO_OUTPUT) {
                flags += BIT_ARC_HAS_OUTPUT
            }

            scratchBytes.writeByte(flags.toByte())
            val labelStart: Long = scratchBytes.getPosition().toLong()
            writeLabel(scratchBytes, arc.label)
            val numLabelBytes = (scratchBytes.getPosition() - labelStart).toInt()

            // System.out.println("  write arc: label=" + (char) arc.label + " flags=" + flags + "
            // target=" + target.node + " pos=" + bytes.getPosition() + " output=" +
            // outputs.outputToString(arc.output));
            if (arc.output !== NO_OUTPUT) {
                fst.outputs.write(arc.output!!, scratchBytes)
                // System.out.println("    write output");
            }

            if (arc.nextFinalOutput !== NO_OUTPUT) {
                // System.out.println("    write final output");
                fst.outputs.writeFinalOutput(arc.nextFinalOutput!!, scratchBytes)
            }

            if (targetHasArcs && (flags and BIT_TARGET_NEXT) == 0) {
                require(target.node > 0)
                // System.out.println("    write target");
                scratchBytes.writeVLong(target.node)
            }

            // just write the arcs "like normal" on first pass, but record how many bytes each one took
            // and max byte size:
            if (doFixedLengthArcs) {
                val numArcBytes = (scratchBytes.getPosition() - lastArcStart).toInt()
                numBytesPerArc[arcIdx] = numArcBytes
                numLabelBytesPerArc[arcIdx] = numLabelBytes
                lastArcStart = scratchBytes.getPosition().toLong()
                maxBytesPerArc = max(maxBytesPerArc, numArcBytes)
                maxBytesPerArcWithoutLabel = max(maxBytesPerArcWithoutLabel, numArcBytes - numLabelBytes)
                // System.out.println("    arcBytes=" + numArcBytes + " labelBytes=" + numLabelBytes);
            }
        }

        // TODO: try to avoid wasteful cases: disable doFixedLengthArcs in that case
        /*
     *
     * LUCENE-4682: what is a fair heuristic here
     * It could involve some of these:
     * 1. how "busy" the node is: nodeIn.inputCount relative to frontier[0].inputCount
     * 2. how much binSearch saves over scan: nodeIn.numArcs
     * 3. waste: numBytes vs numBytesExpanded
     *
     * the one below just looks at #3
    if (doFixedLengthArcs) {
      // rough heuristic: make this 1.25 "waste factor" a parameter to the phd ctor
      int numBytes = lastArcStart - startAddress;
      int numBytesExpanded = maxBytesPerArc * nodeIn.numArcs;
      if (numBytesExpanded > numBytes*1.25) {
        doFixedLengthArcs = false;
      }
    }
    */
        if (doFixedLengthArcs) {
            require(maxBytesPerArc > 0)

            // 2nd pass just "expands" all arcs to take up a fixed byte size
            val labelRange = nodeIn.arcs[nodeIn.numArcs - 1].label - nodeIn.arcs[0].label + 1
            require(labelRange > 0)
            val continuousLabel = labelRange == nodeIn.numArcs
            if (continuousLabel && version >= FST.VERSION_CONTINUOUS_ARCS) {
                writeNodeForDirectAddressingOrContinuous(
                    nodeIn, maxBytesPerArcWithoutLabel, labelRange, true
                )
                continuousNodeCount++
            } else if (shouldExpandNodeWithDirectAddressing(
                    nodeIn, maxBytesPerArc, maxBytesPerArcWithoutLabel, labelRange
                )
            ) {
                writeNodeForDirectAddressingOrContinuous(
                    nodeIn, maxBytesPerArcWithoutLabel, labelRange, false
                )
                directAddressingNodeCount++
            } else {
                writeNodeForBinarySearch(nodeIn, maxBytesPerArc)
                binarySearchNodeCount++
            }
        }

        reverseScratchBytes()
        // write the padding byte if needed
        if (paddingBytePending) {
            writePaddingByte()
        }
        scratchBytes.writeTo(dataOutput)
        numBytesWritten += scratchBytes.getPosition()

        nodeCount++
        return numBytesWritten - 1
    }

    /**
     * Write the padding byte, ensure no node gets address 0 which is reserved to mean the stop state
     * w/ no arcs
     */
    @Throws(IOException::class)
    private fun writePaddingByte() {
        require(paddingBytePending)
        dataOutput.writeByte(0.toByte())
        paddingBytePending = false
    }

    @Throws(IOException::class)
    private fun writeLabel(out: DataOutput, v: Int) {
        require(v >= 0) { "v=$v" }
        if (fst.metadata.inputType === INPUT_TYPE.BYTE1) {
            require(v <= 255) { "v=$v" }
            out.writeByte(v.toByte())
        } else if (fst.metadata.inputType === INPUT_TYPE.BYTE2) {
            require(v <= 65535) { "v=$v" }
            out.writeShort(v.toShort())
        } else {
            out.writeVInt(v)
        }
    }

    /**
     * Returns whether the given node should be expanded with fixed length arcs. Nodes will be
     * expanded depending on their depth (distance from the root node) and their number of arcs.
     *
     *
     * Nodes with fixed length arcs use more space, because they encode all arcs with a fixed
     * number of bytes, but they allow either binary search or direct addressing on the arcs (instead
     * of linear scan) on lookup by arc label.
     */
    private fun shouldExpandNodeWithFixedLengthArcs(node: UnCompiledNode<T>): Boolean {
        return allowFixedLengthArcs
                && ((node.depth <= FIXED_LENGTH_ARC_SHALLOW_DEPTH
                && node.numArcs >= FIXED_LENGTH_ARC_SHALLOW_NUM_ARCS)
                || node.numArcs >= FIXED_LENGTH_ARC_DEEP_NUM_ARCS)
    }

    /**
     * Returns whether the given node should be expanded with direct addressing instead of binary
     * search.
     *
     *
     * Prefer direct addressing for performance if it does not oversize binary search byte size too
     * much, so that the arcs can be directly addressed by label.
     *
     * @see FSTCompiler.getDirectAddressingMaxOversizingFactor
     */
    private fun shouldExpandNodeWithDirectAddressing(
        nodeIn: UnCompiledNode<T>,
        numBytesPerArc: Int,
        maxBytesPerArcWithoutLabel: Int,
        labelRange: Int
    ): Boolean {
        // Anticipate precisely the size of the encodings.
        val sizeForBinarySearch = numBytesPerArc * nodeIn.numArcs
        val sizeForDirectAddressing: Int =
            (getNumPresenceBytes(labelRange)
                    + numLabelBytesPerArc[0]
                    + maxBytesPerArcWithoutLabel * nodeIn.numArcs)

        // Determine the allowed oversize compared to binary search.
        // This is defined by a parameter of FST Builder (default 1: no oversize).
        val allowedOversize = (sizeForBinarySearch * this.directAddressingMaxOversizingFactor).toInt()
        val expansionCost = sizeForDirectAddressing - allowedOversize

        // Select direct addressing if either:
        // - Direct addressing size is smaller than binary search.
        //   In this case, increment the credit by the reduced size (to use it later).
        // - Direct addressing size is larger than binary search, but the positive credit allows the
        // oversizing.
        //   In this case, decrement the credit by the oversize.
        // In addition, do not try to oversize to a clearly too large node size
        // (this is the DIRECT_ADDRESSING_MAX_OVERSIZE_WITH_CREDIT_FACTOR parameter).
        if (expansionCost <= 0
            || (directAddressingExpansionCredit >= expansionCost
                    && (sizeForDirectAddressing
                    <= allowedOversize * DIRECT_ADDRESSING_MAX_OVERSIZE_WITH_CREDIT_FACTOR))
        ) {
            directAddressingExpansionCredit -= expansionCost.toLong()
            return true
        }
        return false
    }

    private fun writeNodeForBinarySearch(nodeIn: UnCompiledNode<T>, maxBytesPerArc: Int) {
        // Build the header in a buffer.
        // It is a false/special arc which is in fact a node header with node flags followed by node
        // metadata.
        fixedLengthArcsBuffer
            .resetPosition()
            .writeByte(ARCS_FOR_BINARY_SEARCH)
            .writeVInt(nodeIn.numArcs)
            .writeVInt(maxBytesPerArc)
        val headerLen = fixedLengthArcsBuffer.position

        // Expand the arcs in place, backwards.
        var srcPos: Int = scratchBytes.getPosition()
        var destPos = headerLen + nodeIn.numArcs * maxBytesPerArc
        require(destPos >= srcPos)
        if (destPos > srcPos) {
            scratchBytes.setPosition(destPos)
            for (arcIdx in nodeIn.numArcs - 1 downTo 0) {
                destPos -= maxBytesPerArc
                val arcLen = numBytesPerArc[arcIdx]
                srcPos -= arcLen
                if (srcPos != destPos) {
                    require(
                        destPos > srcPos
                    ) {
                        ("destPos="
                                + destPos
                                + " srcPos="
                                + srcPos
                                + " arcIdx="
                                + arcIdx
                                + " maxBytesPerArc="
                                + maxBytesPerArc
                                + " arcLen="
                                + arcLen
                                + " nodeIn.numArcs="
                                + nodeIn.numArcs)
                    }
                    // copy the bytes from srcPos to destPos, essentially expanding the arc from variable
                    // length to fixed length
                    writeScratchBytes(destPos, scratchBytes.getBytes(), srcPos, arcLen)
                }
            }
        }

        // Finally write the header
        writeScratchBytes(0, fixedLengthArcsBuffer.bytes, 0, headerLen)
    }

    /**
     * Reverse the scratch bytes in place. This operation does not affect scratchBytes.getPosition().
     */
    private fun reverseScratchBytes() {
        val pos: Int = scratchBytes.getPosition()
        val bytes: ByteArray = scratchBytes.getBytes()
        val limit = pos / 2
        for (i in 0..<limit) {
            val b = bytes[i]
            bytes[i] = bytes[pos - 1 - i]
            bytes[pos - 1 - i] = b
        }
    }

    /**
     * Write bytes from a source byte[] to the scratch bytes. The written bytes must fit within what
     * was already written in the scratch bytes.
     *
     *
     * This operation does not affect scratchBytes.getPosition().
     *
     * @param destPos the position in the scratch bytes
     * @param bytes the source byte[]
     * @param offset the offset inside the source byte[]
     * @param length the number of bytes to write
     */
    private fun writeScratchBytes(destPos: Int, bytes: ByteArray, offset: Int, length: Int) {
        require(destPos + length <= scratchBytes.getPosition())
        System.arraycopy(bytes, offset, scratchBytes.getBytes(), destPos, length)
    }

    private fun writeNodeForDirectAddressingOrContinuous(
        nodeIn: UnCompiledNode<T>,
        maxBytesPerArcWithoutLabel: Int,
        labelRange: Int,
        continuous: Boolean
    ) {
        // Expand the arcs backwards in a buffer because we remove the labels.
        // So the obtained arcs might occupy less space. This is the reason why this
        // whole method is more complex.
        // Drop the label bytes since we can infer the label based on the arc index,
        // the presence bits, and the first label. Keep the first label.
        val headerMaxLen = 11
        val numPresenceBytes = if (continuous) 0 else getNumPresenceBytes(labelRange)
        var srcPos: Int = scratchBytes.getPosition()
        val totalArcBytes = numLabelBytesPerArc[0] + nodeIn.numArcs * maxBytesPerArcWithoutLabel
        var bufferOffset = headerMaxLen + numPresenceBytes + totalArcBytes
        val buffer = fixedLengthArcsBuffer.ensureCapacity(bufferOffset).bytes
        // Copy the arcs to the buffer, dropping all labels except first one.
        for (arcIdx in nodeIn.numArcs - 1 downTo 0) {
            bufferOffset -= maxBytesPerArcWithoutLabel
            val srcArcLen = numBytesPerArc[arcIdx]
            srcPos -= srcArcLen
            val labelLen = numLabelBytesPerArc[arcIdx]
            // Copy the flags.
            scratchBytes.writeTo(srcPos, buffer, bufferOffset, 1)
            // Skip the label, copy the remaining.
            val remainingArcLen = srcArcLen - 1 - labelLen
            if (remainingArcLen != 0) {
                scratchBytes.writeTo(srcPos + 1 + labelLen, buffer, bufferOffset + 1, remainingArcLen)
            }
            if (arcIdx == 0) {
                // Copy the label of the first arc only.
                bufferOffset -= labelLen
                scratchBytes.writeTo(srcPos + 1, buffer, bufferOffset, labelLen)
            }
        }
        require(bufferOffset == headerMaxLen + numPresenceBytes)

        // Build the header in the buffer.
        // It is a false/special arc which is in fact a node header with node flags followed by node
        // metadata.
        fixedLengthArcsBuffer
            .resetPosition()
            .writeByte(if (continuous) ARCS_FOR_CONTINUOUS else ARCS_FOR_DIRECT_ADDRESSING)
            .writeVInt(labelRange) // labelRange instead of numArcs.
            .writeVInt(
                maxBytesPerArcWithoutLabel
            ) // maxBytesPerArcWithoutLabel instead of maxBytesPerArc.
        val headerLen = fixedLengthArcsBuffer.position

        // Write the header.
        scratchBytes.setPosition(0)
        scratchBytes.writeBytes(fixedLengthArcsBuffer.bytes, 0, headerLen)

        // Write the presence bits
        if (!continuous) {
            writePresenceBits(nodeIn)
            require(scratchBytes.getPosition() == headerLen + numPresenceBytes)
        }

        // Write the first label and the arcs.
        scratchBytes.writeBytes(fixedLengthArcsBuffer.bytes, bufferOffset, totalArcBytes)
        require(scratchBytes.getPosition() == headerLen + numPresenceBytes + totalArcBytes)
    }

    private fun writePresenceBits(nodeIn: UnCompiledNode<T>) {
        var presenceBits: Byte = 1 // The first arc is always present.
        var presenceIndex = 0
        var previousLabel = nodeIn.arcs[0].label
        for (arcIdx in 1..<nodeIn.numArcs) {
            val label = nodeIn.arcs[arcIdx].label
            require(label > previousLabel)
            presenceIndex += label - previousLabel
            while (presenceIndex >= Byte.SIZE_BITS) {
                scratchBytes.writeByte(presenceBits)
                presenceBits = 0
                presenceIndex -= Byte.SIZE_BITS
            }
            // Set the bit at presenceIndex to flag that the corresponding arc is present.
            presenceBits = presenceBits or (1 shl presenceIndex).toByte()
            previousLabel = label
        }
        require(presenceIndex == (nodeIn.arcs[nodeIn.numArcs - 1].label - nodeIn.arcs[0].label) % 8)
        require(
            presenceBits.toInt() != 0 // The last byte is not 0.
        )
        require(
            (presenceBits.toInt() and (1 shl presenceIndex)) != 0 // The last arc is always present.
        )
        scratchBytes.writeByte(presenceBits)
    }

    @Throws(IOException::class)
    private fun freezeTail(prefixLenPlus1: Int) {
        val downTo = max(1, prefixLenPlus1)

        for (idx in lastInput.length() downTo downTo) {
            val node = frontier[idx]
            val prevIdx = idx - 1
            val parent = frontier[prevIdx]

            val nextFinalOutput = node.output

            // We "fake" the node as being final if it has no
            // outgoing arcs; in theory we could leave it
            // as non-final (the FST can represent this), but
            // FSTEnum, Util, etc., have trouble w/ non-final
            // dead-end states:

            // TODO: is node.numArcs == 0 always false  we no longer prune any nodes from FST:
            val isFinal = node.isFinal || node.numArcs == 0

            // this node makes it and we now compile it.  first,
            // compile any targets that were previously
            // undecided:
            parent.replaceLast(lastInput.intAt(prevIdx), compileNode(node), nextFinalOutput, isFinal)
        }
    }

    /**
     * Add the next input/output pair. The provided input must be sorted after the previous one
     * according to [IntsRef.compareTo]. It's also OK to add the same input twice in a row with
     * different outputs, as long as [Outputs] implements the [Outputs.merge] method. Note
     * that input is fully consumed after this method is returned (so caller is free to reuse), but
     * output is not. So if your outputs are changeable (eg [ByteSequenceOutputs] or [ ]) then you cannot reuse across calls.
     */
    @Throws(IOException::class)
    fun add(input: IntsRef, output: T) {
        // De-dup NO_OUTPUT since it must be a singleton:
        var output = output
        if (output == NO_OUTPUT) {
            output = NO_OUTPUT
        }

        require(
            lastInput.length() == 0 || input >= lastInput.get()
        ) { "inputs are added out of order lastInput=" + lastInput.get() + " vs input=" + input }
        require(validOutput(output))

        // System.out.println("\nadd: " + input);
        if (input.length == 0) {
            // empty input: only allowed as first input.  we have
            // to special case this because the packed FST
            // format cannot represent the empty input since
            // 'finalness' is stored on the incoming arc, not on
            // the node
            frontier[0].isFinal = true
            setEmptyOutput(output)
            return
        }

        // compare shared prefix length
        var pos = 0
        if (lastInput.length() > 0) {
            val mismatch: Int =
                Arrays.mismatch(
                    lastInput.ints(), 0, lastInput.length(), input.ints, input.offset, input.length
                )
            pos += if (mismatch == -1) lastInput.length() else mismatch
        }
        val prefixLenPlus1 = pos + 1

        if (frontier.size < input.length + 1) {
            val next: Array<UnCompiledNode<T>> = ArrayUtil.grow(frontier, input.length + 1)
            for (idx in frontier.size..<next.size) {
                next[idx] = UnCompiledNode(this, idx)
            }
            frontier = next
        }

        // minimize/compile states from previous input's
        // orphan'd suffix
        freezeTail(prefixLenPlus1)

        // init tail states for current input
        for (idx in prefixLenPlus1..input.length) {
            frontier[idx - 1].addArc(input.ints[input.offset + idx - 1], frontier[idx])
        }

        val lastNode = frontier[input.length]
        if (lastInput.length() != input.length || prefixLenPlus1 != input.length + 1) {
            lastNode.isFinal = true
            lastNode.output = NO_OUTPUT
        }

        // push conflicting outputs forward, only as far as
        // needed
        for (idx in 1..<prefixLenPlus1) {
            val node = frontier[idx]
            val parentNode = frontier[idx - 1]

            val lastOutput = parentNode.getLastOutput(input.ints[input.offset + idx - 1])
            require(validOutput(lastOutput!!))

            val commonOutputPrefix: T

            if (lastOutput !== NO_OUTPUT) {
                commonOutputPrefix = fst.outputs.common(output, lastOutput)
                require(validOutput(commonOutputPrefix))
                val wordSuffix = fst.outputs.subtract(lastOutput, commonOutputPrefix)
                require(validOutput(wordSuffix))
                parentNode.setLastOutput(input.ints[input.offset + idx - 1], commonOutputPrefix)
                node.prependOutput(wordSuffix)
            } else {
                commonOutputPrefix = NO_OUTPUT
            }

            output = fst.outputs.subtract(output, commonOutputPrefix)
            require(validOutput(output))
        }

        if (lastInput.length() == input.length && prefixLenPlus1 == 1 + input.length) {
            // same input more than 1 time in a row, mapping to
            // multiple outputs
            lastNode.output = fst.outputs.merge(lastNode.output, output)
        } else {
            // this new arc is private to this new input; set its
            // arc output to the leftover output:
            frontier[prefixLenPlus1 - 1].setLastOutput(
                input.ints[input.offset + prefixLenPlus1 - 1], output
            )
        }

        // save last input
        lastInput.copyInts(input)
    }

    fun setEmptyOutput(v: T) {
        if (fst.metadata.emptyOutput != null) {
            fst.metadata.emptyOutput = fst.outputs.merge(fst.metadata.emptyOutput!!, v)
        } else {
            fst.metadata.emptyOutput = v
        }
    }

    fun finish(newStartNode: Long) {
        var newStartNode = newStartNode
        require(newStartNode <= numBytesWritten)
        check(fst.metadata.startNode == -1L) { "already finished" }
        if (newStartNode == FINAL_END_NODE && fst.metadata.emptyOutput != null) {
            newStartNode = 0
        }
        fst.metadata.startNode = newStartNode
        fst.metadata.numBytes = numBytesWritten
        // freeze the dataOutput if applicable
        if (dataOutput is ReadWriteDataOutput) {
            dataOutput.freeze()
        }
    }

    private fun validOutput(output: T): Boolean {
        return output === NO_OUTPUT || output != NO_OUTPUT
    }

    /**
     * Returns the metadata of the final FST. NOTE: this will return null if nothing is accepted by
     * the FST themselves.
     *
     *
     * To create the FST, you need to:
     *
     *
     * - If a FSTReader DataOutput was used, such as the one returned by [ ][.getOnHeapReaderWriter]
     *
     * <pre class="prettyprint">
     * fstMetadata = fstCompiler.compile();
     * fst = FST.fromFSTReader(fstMetadata, fstCompiler.getFSTReader());
    </pre> *
     *
     *
     * - If a non-FSTReader DataOutput was used, such as [ ], you need to first create the corresponding [ ], such as [org.apache.lucene.store.IndexInput] then
     * pass it to the FST construct
     *
     * <pre class="prettyprint">
     * fstMetadata = fstCompiler.compile();
     * fst = new FST&lt;&gt;(fstMetadata, dataInput, new OffHeapFSTStore());
    </pre> *
     */
    @Throws(IOException::class)
    fun compile(): FSTMetadata<T>? {
        val root = frontier[0]

        // minimize nodes in the last word's suffix
        freezeTail(0)
        if (root.numArcs == 0) {
            if (fst.metadata.emptyOutput == null) {
                // return null for completely empty FST which accepts nothing
                return null
            } else {
                // we haven't written the padding byte so far, but the FST is still valid
                writePaddingByte()
            }
        }

        // if (DEBUG) System.out.println("  builder.finish root.isFinal=" + root.isFinal + "
        // root.output=" + root.output);
        finish(compileNode(root).node)

        return fst.metadata
    }

    /** Expert: holds a pending (seen but not yet serialized) arc.  */
    class Arc<T> {
        var label: Int = 0 // really an "unsigned" byte
        var target: Node? = null
        var isFinal: Boolean = false
        var output: T? = null
        var nextFinalOutput: T? = null
    }

    // NOTE: not many instances of Node or CompiledNode are in
    // memory while the FST is being built; it's only the
    // current "frontier":
    interface Node {
        fun isCompiled(): Boolean
    }

    fun fstRamBytesUsed(): Long {
        var ramBytesUsed: Long = scratchBytes.ramBytesUsed()
        if (dataOutput is Accountable) {
            ramBytesUsed += (dataOutput as Accountable).ramBytesUsed()
        }
        return ramBytesUsed
    }

    fun fstSizeInBytes(): Long {
        return numBytesWritten
    }

    internal class CompiledNode : Node {
        var node: Long = 0

        override fun isCompiled(): Boolean {
            return true
        }
    }

    /** Expert: holds a pending (seen but not yet serialized) Node.  */
    class UnCompiledNode<T>(val owner: FSTCompiler<T>, depth: Int) : Node {
        var numArcs: Int = 0
        var arcs: Array<Arc<T>>

        // TODO: instead of recording isFinal/output on the
        // node, maybe we should use -1 arc to mean "end" (like
        // we do when reading the FST).  Would simplify much
        // code here...
        var output: T
        var isFinal: Boolean = false

        /** This node's depth, starting from the automaton root.  */
        val depth: Int

        /**
         * @param depth The node's depth starting from the automaton root. Needed for LUCENE-2934 (node
         * expansion based on conditions other than the fanout size).
         */
        init {
            arcs = kotlin.arrayOfNulls<Arc<T>>(1) as Array<Arc<T>>
            arcs[0] = Arc<T>()
            output = owner.NO_OUTPUT
            this.depth = depth
        }

        override fun isCompiled(): Boolean {
            return false
        }

        fun clear() {
            numArcs = 0
            isFinal = false
            output = owner.NO_OUTPUT

            // We don't clear the depth here because it never changes
            // for nodes on the frontier (even when reused).
        }

        fun getLastOutput(labelToMatch: Int): T? {
            require(numArcs > 0)
            require(arcs[numArcs - 1].label == labelToMatch)
            return arcs[numArcs - 1].output
        }

        fun addArc(label: Int, target: Node) {
            require(label >= 0)
            require(
                numArcs == 0 || label > arcs[numArcs - 1].label
            ) {
                ("arc[numArcs-1].label="
                        + arcs[numArcs - 1].label
                        + " new label="
                        + label
                        + " numArcs="
                        + numArcs)
            }
            if (numArcs == arcs.size) {
                val newArcs: Array<Arc<T>> = ArrayUtil.grow(arcs)
                for (arcIdx in numArcs..<newArcs.size) {
                    newArcs[arcIdx] = Arc()
                }
                arcs = newArcs
            }
            val arc = arcs[numArcs++]
            arc.label = label
            arc.target = target
            arc.nextFinalOutput = owner.NO_OUTPUT
            arc.output = arc.nextFinalOutput
            arc.isFinal = false
        }

        fun replaceLast(labelToMatch: Int, target: Node, nextFinalOutput: T, isFinal: Boolean) {
            require(numArcs > 0)
            val arc = arcs[numArcs - 1]
            require(arc.label == labelToMatch) { "arc.label=" + arc.label + " vs " + labelToMatch }
            arc.target = target
            // assert target.node != -2;
            arc.nextFinalOutput = nextFinalOutput
            arc.isFinal = isFinal
        }

        fun setLastOutput(labelToMatch: Int, newOutput: T) {
            require(owner.validOutput(newOutput))
            require(numArcs > 0)
            val arc = arcs[numArcs - 1]
            require(arc.label == labelToMatch)
            arc.output = newOutput
        }

        // pushes an output prefix forward onto all arcs
        fun prependOutput(outputPrefix: T) {
            require(owner.validOutput(outputPrefix))

            for (arcIdx in 0..<numArcs) {
                arcs[arcIdx].output = owner.fst.outputs.add(outputPrefix, arcs[arcIdx].output!!)
                require(owner.validOutput(arcs[arcIdx].output!!))
            }

            if (isFinal) {
                output = owner.fst.outputs.add(outputPrefix, output)
                require(owner.validOutput(output))
            }
        }
    }

    /**
     * Reusable buffer for building nodes with fixed length arcs (binary search or direct addressing).
     */
    class FixedLengthArcsBuffer {
        /** Gets the internal byte array.  */
        // Initial capacity is the max length required for the header of a node with fixed length arcs:
        // header(byte) + numArcs(vint) + numBytes(vint)
        var bytes: ByteArray = ByteArray(11)
            private set
        private val bado: ByteArrayDataOutput = ByteArrayDataOutput(bytes)

        /** Ensures the capacity of the internal byte array. Enlarges it if needed.  */
        fun ensureCapacity(capacity: Int): FixedLengthArcsBuffer {
            if (bytes.size < capacity) {
                bytes = ByteArray(ArrayUtil.oversize(capacity, Byte.SIZE_BYTES))
                bado.reset(bytes)
            }
            return this
        }

        fun resetPosition(): FixedLengthArcsBuffer {
            bado.reset(bytes)
            return this
        }

        fun writeByte(b: Byte): FixedLengthArcsBuffer {
            bado.writeByte(b)
            return this
        }

        fun writeVInt(i: Int): FixedLengthArcsBuffer {
            try {
                bado.writeVInt(i)
            } catch (e: IOException) { // Never thrown.
                throw RuntimeException(e)
            }
            return this
        }

        val position: Int
            get() = bado.getPosition()
    }

    companion object {
        const val DIRECT_ADDRESSING_MAX_OVERSIZING_FACTOR: Float = 1f

        /**
         * @see .shouldExpandNodeWithFixedLengthArcs
         */
        const val FIXED_LENGTH_ARC_SHALLOW_DEPTH: Int = 3 // 0 => only root node.

        /**
         * @see .shouldExpandNodeWithFixedLengthArcs
         */
        const val FIXED_LENGTH_ARC_SHALLOW_NUM_ARCS: Int = 5

        /**
         * @see .shouldExpandNodeWithFixedLengthArcs
         */
        const val FIXED_LENGTH_ARC_DEEP_NUM_ARCS: Int = 10

        /**
         * Maximum oversizing factor allowed for direct addressing compared to binary search when
         * expansion credits allow the oversizing. This factor prevents expansions that are obviously too
         * costly even if there are sufficient credits.
         *
         * @see .shouldExpandNodeWithDirectAddressing
         */
        private const val DIRECT_ADDRESSING_MAX_OVERSIZE_WITH_CREDIT_FACTOR = 1.66f

        // a FSTReader used when a non-FSTReader DataOutput is configured.
        // it will throw exceptions if attempt to call getReverseBytesReader() or writeTo(DataOutput)
        private val NULL_FST_READER: FSTReader = NullFSTReader()

        /**
         * Get an on-heap DataOutput that allows the FST to be read immediately after writing, and also
         * optionally saved to an external DataOutput.
         *
         * @param blockBits how many bits wide to make each block of the DataOutput
         * @return the DataOutput
         */
        fun getOnHeapReaderWriter(blockBits: Int): DataOutput {
            return ReadWriteDataOutput(blockBits)
        }
    }
}
