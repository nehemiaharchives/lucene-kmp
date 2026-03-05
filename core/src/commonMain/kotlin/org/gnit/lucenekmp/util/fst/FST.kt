package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.InputStreamDataInput
import org.gnit.lucenekmp.store.OutputStreamDataOutput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.Constants
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.fst.FST.Arc.BitTable
import org.gnit.lucenekmp.util.fst.FSTCompiler.Companion.getOnHeapReaderWriter
import org.gnit.lucenekmp.jdkport.BufferedOutputStream
import org.gnit.lucenekmp.jdkport.BufferedInputStream
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.reverseBytes
import kotlin.experimental.and
import kotlin.experimental.or
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import okio.Path
import org.gnit.lucenekmp.jdkport.toHexString

// TODO: break this into WritableFST and ReadOnlyFST.. then
// we can have subclasses of ReadOnlyFST to handle the
// different byte[] level encodings (packed or
// not)... and things like nodeCount, arcCount are read only
// TODO: if FST is pure prefix trie we can do a more compact
// job, ie, once we are at a 'suffix only', just store the
// completion labels as a string not as a series of arcs.
// NOTE: while the FST is able to represent a non-final
// dead-end state (NON_FINAL_END_NODE=0), the layers above
// (FSTEnum, Util) have problems with this!!
/**
 * Represents an finite state machine (FST), using a compact byte[] format.
 *
 *
 * The format is similar to what's used by Morfologik
 * (https://github.com/morfologik/morfologik-stemming).
 *
 *
 * See the [package documentation][org.apache.lucene.util.fst] for some simple examples.
 *
 * @lucene.experimental
 */
class FST<T> internal constructor(metadata: FSTMetadata<T>, fstReader: FSTReader) : Accountable {
    val metadata: FSTMetadata<T>

    /** Specifies allowed range of each int input label for this FST.  */
    enum class INPUT_TYPE {
        BYTE1,
        BYTE2,
        BYTE4
    }

    /** The reader of the FST, used to read bytes from the underlying FST storage  */
    private val fstReader: FSTReader

    val outputs: Outputs<T>

    /** Represents a single arc.  */
    class Arc<T> {
        // *** Arc fields.
        var label = 0

        var output: T? = null

        var target: Long = 0

        var flags: Byte = 0

        var nextFinalOutput: T? = null

        var nextArc: Long = 0

        var nodeFlags: Byte = 0

        // *** Fields for arcs belonging to a node with fixed length arcs.
        // So only valid when bytesPerArc != 0.
        // nodeFlags == ARCS_FOR_BINARY_SEARCH || nodeFlags == ARCS_FOR_DIRECT_ADDRESSING.
        var bytesPerArc = 0

        var posArcsStart: Long = 0

        var arcIdx = 0

        var numArcs = 0

        // *** Fields for a direct addressing node. nodeFlags == ARCS_FOR_DIRECT_ADDRESSING.
        /**
         * Start position in the [FST.BytesReader] of the presence bits for a direct addressing
         * node, aka the bit-table
         */
        var bitTableStart: Long = 0

        /** First label of a direct addressing node.  */
        var firstLabel = 0

        /**
         * Index of the current label of a direct addressing node. While [.arcIdx] is the current
         * index in the label range, [.presenceIndex] is its corresponding index in the list of
         * actually present labels. It is equal to the number of bits set before the bit at [ ][.arcIdx] in the bit-table. This field is a cache to avoid to count bits set repeatedly when
         * iterating the next arcs.
         */
        var presenceIndex = 0

        /** Returns this  */
        fun copyFrom(other: Arc<T>): Arc<T> {
            label = other.label()
            target = other.target()
            flags = other.flags()
            output = other.output()
            nextFinalOutput = other.nextFinalOutput()
            nextArc = other.nextArc()
            nodeFlags = other.nodeFlags()
            bytesPerArc = other.bytesPerArc()

            // Fields for arcs belonging to a node with fixed length arcs.
            // We could avoid copying them if bytesPerArc() == 0 (this was the case with previous code,
            // and the current code
            // still supports that), but it may actually help external uses of FST to have consistent arc
            // state, and debugging
            // is easier.
            posArcsStart = other.posArcsStart()
            arcIdx = other.arcIdx()
            numArcs = other.numArcs()
            bitTableStart = other.bitTableStart
            firstLabel = other.firstLabel()
            presenceIndex = other.presenceIndex

            return this
        }

        fun flag(flag: Int): Boolean {
            return flag(flags.toInt(), flag)
        }

        val isLast: Boolean
            get() = flag(BIT_LAST_ARC)

        val isFinal: Boolean
            get() = flag(BIT_FINAL_ARC)

        override fun toString(): String {
            val b = StringBuilder()
            b.append(" target=").append(target())
            b.append(" label=0x").append(Int.toHexString(label()))
            if (flag(BIT_FINAL_ARC)) {
                b.append(" final")
            }
            if (flag(BIT_LAST_ARC)) {
                b.append(" last")
            }
            if (flag(BIT_TARGET_NEXT)) {
                b.append(" targetNext")
            }
            if (flag(BIT_STOP_NODE)) {
                b.append(" stop")
            }
            if (flag(BIT_ARC_HAS_OUTPUT)) {
                b.append(" output=").append(output())
            }
            if (flag(BIT_ARC_HAS_FINAL_OUTPUT)) {
                b.append(" nextFinalOutput=").append(nextFinalOutput())
            }
            if (bytesPerArc() != 0) {
                b.append(" arcArray(idx=")
                    .append(arcIdx())
                    .append(" of ")
                    .append(numArcs())
                    .append(")")
                    .append("(")
                    .append(
                        if (nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING)
                            "da"
                        else
                            if (nodeFlags() == ARCS_FOR_CONTINUOUS) "cs" else "bs"
                    )
                    .append(")")
            }
            return b.toString()
        }

        fun label(): Int {
            return label
        }

        fun output(): T? {
            return output
        }

        /** Ord/address to target node.  */
        fun target(): Long {
            return target
        }

        fun flags(): Byte {
            return flags
        }

        fun nextFinalOutput(): T? {
            return nextFinalOutput
        }

        /**
         * Address (into the byte[]) of the next arc - only for list of variable length arc. Or
         * ord/address to the next node if label == [.END_LABEL].
         */
        fun nextArc(): Long {
            return nextArc
        }

        /** Where we are in the array; only valid if bytesPerArc != 0.  */
        fun arcIdx(): Int {
            return arcIdx
        }

        /**
         * Node header flags. Only meaningful to check if the value is either [ ][.ARCS_FOR_BINARY_SEARCH] or [.ARCS_FOR_DIRECT_ADDRESSING] or [ ][.ARCS_FOR_CONTINUOUS] (other value when bytesPerArc == 0).
         */
        fun nodeFlags(): Byte {
            return nodeFlags
        }

        /** Where the first arc in the array starts; only valid if bytesPerArc != 0  */
        fun posArcsStart(): Long {
            return posArcsStart
        }

        /**
         * Non-zero if this arc is part of a node with fixed length arcs, which means all arcs for the
         * node are encoded with a fixed number of bytes so that we binary search or direct address. We
         * do when there are enough arcs leaving one node. It wastes some bytes but gives faster
         * lookups.
         */
        fun bytesPerArc(): Int {
            return bytesPerArc
        }

        /**
         * How many arcs; only valid if bytesPerArc != 0 (fixed length arcs). For a node designed for
         * binary search this is the array size. For a node designed for direct addressing, this is the
         * label range.
         */
        fun numArcs(): Int {
            return numArcs
        }

        /**
         * First label of a direct addressing node. Only valid if nodeFlags == [ ][.ARCS_FOR_DIRECT_ADDRESSING] or [.ARCS_FOR_CONTINUOUS].
         */
        fun firstLabel(): Int {
            return firstLabel
        }

        /**
         * Helper methods to read the bit-table of a direct addressing node. Only valid for [Arc]
         * with [Arc.nodeFlags] == `ARCS_FOR_DIRECT_ADDRESSING`.
         */
        internal object BitTable {
            /** See [BitTableUtil.isBitSet].  */
            @Throws(IOException::class)
            fun isBitSet(bitIndex: Int, arc: Arc<*>, `in`: BytesReader): Boolean {
                require(arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING)
                `in`.position = arc.bitTableStart
                return BitTableUtil.isBitSet(bitIndex, `in`)
            }

            /**
             * See [BitTableUtil.countBits]. The count of bit set is the
             * number of arcs of a direct addressing node.
             */
            @Throws(IOException::class)
            fun countBits(arc: Arc<*>, `in`: BytesReader): Int {
                require(arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING)
                `in`.position = arc.bitTableStart
                return BitTableUtil.countBits(getNumPresenceBytes(arc.numArcs()), `in`)
            }

            /** See [BitTableUtil.countBitsUpTo].  */
            @Throws(IOException::class)
            fun countBitsUpTo(bitIndex: Int, arc: Arc<*>, `in`: BytesReader): Int {
                require(arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING)
                `in`.position = arc.bitTableStart
                return BitTableUtil.countBitsUpTo(bitIndex, `in`)
            }

            /** See [BitTableUtil.nextBitSet].  */
            @Throws(IOException::class)
            fun nextBitSet(bitIndex: Int, arc: Arc<*>, `in`: BytesReader): Int {
                require(arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING)
                `in`.position = arc.bitTableStart
                return BitTableUtil.nextBitSet(bitIndex, getNumPresenceBytes(arc.numArcs()), `in`)
            }

            /** See [BitTableUtil.previousBitSet].  */
            @Throws(IOException::class)
            fun previousBitSet(bitIndex: Int, arc: Arc<*>, `in`: BytesReader): Int {
                require(arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING)
                `in`.position = arc.bitTableStart
                return BitTableUtil.previousBitSet(bitIndex, `in`)
            }

            /** Asserts the bit-table of the provided [Arc] is valid.  */
            @Throws(IOException::class)
            fun assertIsValid(arc: Arc<*>, `in`: BytesReader): Boolean {
                require(arc.bytesPerArc() > 0)
                require(arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING)
                // First bit must be set.
                require(isBitSet(0, arc, `in`))
                // Last bit must be set.
                require(isBitSet(arc.numArcs() - 1, arc, `in`))
                // No bit set after the last arc.
                require(nextBitSet(arc.numArcs() - 1, arc, `in`) == -1)
                return true
            }
        }
    }

    /**
     * Load a previously saved FST with a DataInput for metdata using an [OnHeapFSTStore] with
     * maxBlockBits set to [.DEFAULT_MAX_BLOCK_BITS]
     */
    constructor(metadata: FSTMetadata<T>, `in`: DataInput) : this(
        metadata,
        OnHeapFSTStore(DEFAULT_MAX_BLOCK_BITS, `in`, metadata.numBytes)
    )

    /** Create the FST with a metadata object and a FSTReader.  */
    init {
        checkNotNull(fstReader)
        this.metadata = metadata
        this.outputs = metadata.outputs
        this.fstReader = fstReader
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + fstReader.ramBytesUsed()
    }

    override fun toString(): String {
        return this::class.simpleName + "(input=" + metadata.inputType + ",output=" + outputs
    }

    fun numBytes(): Long {
        return metadata.numBytes
    }

    fun getEmptyOutput(): T? = metadata.emptyOutput

    /**
     * Save the FST to DataOutput.
     *
     * @param metaOut the DataOutput to write the metadata to
     * @param out the DataOutput to write the FST bytes to
     */
    @Throws(IOException::class)
    fun save(metaOut: DataOutput, out: DataOutput) {
        metadata.save(metaOut)
        fstReader.writeTo(out)
    }

    /** Writes an automaton to a file.  */
    @Throws(IOException::class)
    fun save(path: Path) {
        BufferedOutputStream(Files.newOutputStream(path)).use { os ->
            val out: DataOutput = OutputStreamDataOutput(os)
            save(out, out)
        }
    }

    /** Reads one BYTE1/2/4 label from the provided [DataInput].  */
    @Throws(IOException::class)
    fun readLabel(`in`: DataInput): Int {
        val v: Int = if (metadata.inputType == INPUT_TYPE.BYTE1) {
            // Unsigned byte:
            `in`.readByte().toInt() and 0xFF
        } else if (metadata.inputType == INPUT_TYPE.BYTE2) {
            // Unsigned short:
            if (metadata.version < VERSION_LITTLE_ENDIAN) {
                Short.reverseBytes(`in`.readShort()).toInt() and 0xFFFF
            } else {
                `in`.readShort().toInt() and 0xFFFF
            }
        } else {
            `in`.readVInt()
        }
        return v
    }

    /**
     * Reads the presence bits of a direct-addressing node. Actually we don't read them here, we just
     * keep the pointer to the bit-table start and we skip them.
     */
    @Throws(IOException::class)
    private fun readPresenceBytes(arc: Arc<T>, `in`: BytesReader) {
        require(arc.bytesPerArc() > 0)
        require(arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING)
        arc.bitTableStart = `in`.position
        `in`.skipBytes(getNumPresenceBytes(arc.numArcs()).toLong())
    }

    /** Fills virtual 'start' arc, ie, an empty incoming arc to the FST's start node  */
    fun getFirstArc(arc: Arc<T>): Arc<T> {
        val NO_OUTPUT: T = outputs.noOutput

        if (metadata.emptyOutput != null) {
            arc.flags = (BIT_FINAL_ARC or BIT_LAST_ARC).toByte()
            arc.nextFinalOutput = metadata.emptyOutput
            if (metadata.emptyOutput !== NO_OUTPUT) {
                arc.flags = (arc.flags().toInt() or BIT_ARC_HAS_FINAL_OUTPUT).toByte()
            }
        } else {
            arc.flags = BIT_LAST_ARC.toByte()
            arc.nextFinalOutput = NO_OUTPUT
        }
        arc.output = NO_OUTPUT

        // If there are no nodes, ie, the FST only accepts the
        // empty string, then startNode is 0
        arc.target = metadata.startNode
        return arc
    }

    /**
     * Follows the `follow` arc and reads the last arc of its target; this changes the
     * provided `arc` (2nd arg) in-place and returns it.
     *
     * @return Returns the second argument (`arc`).
     */
    @Throws(IOException::class)
    fun readLastTargetArc(follow: Arc<T>, arc: Arc<T>, `in`: BytesReader): Arc<T> {
        // System.out.println("readLast");
        if (!targetHasArcs(follow)) {
            // System.out.println("  end node");
            require(follow.isFinal)
            arc.label = END_LABEL
            arc.target = FINAL_END_NODE
            arc.output = follow.nextFinalOutput()
            arc.flags = BIT_LAST_ARC.toByte()
            arc.nodeFlags = arc.flags
        } else {
            `in`.position = follow.target()
            arc.nodeFlags = `in`.readByte()
            val flags = arc.nodeFlags
            if (flags == ARCS_FOR_BINARY_SEARCH || flags == ARCS_FOR_DIRECT_ADDRESSING || flags == ARCS_FOR_CONTINUOUS) {
                // Special arc which is actually a node header for fixed length arcs.
                // Jump straight to end to find the last arc.
                arc.numArcs = `in`.readVInt()
                arc.bytesPerArc = `in`.readVInt()
                // System.out.println("  array numArcs=" + arc.numArcs + " bpa=" + arc.bytesPerArc);
                if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
                    readPresenceBytes(arc, `in`)
                    arc.firstLabel = readLabel(`in`)
                    arc.posArcsStart = `in`.position
                    readLastArcByDirectAddressing(arc, `in`)
                } else if (flags == ARCS_FOR_BINARY_SEARCH) {
                    arc.arcIdx = arc.numArcs() - 2
                    arc.posArcsStart = `in`.position
                    readNextRealArc(arc, `in`)
                } else {
                    arc.firstLabel = readLabel(`in`)
                    arc.posArcsStart = `in`.position
                    readLastArcByContinuous(arc, `in`)
                }
            } else {
                arc.flags = flags
                // non-array: linear scan
                arc.bytesPerArc = 0
                // System.out.println("  scan");
                while (!arc.isLast) {
                    // skip this arc:
                    readLabel(`in`)
                    if (arc.flag(BIT_ARC_HAS_OUTPUT)) {
                        outputs.skipOutput(`in`)
                    }
                    if (arc.flag(BIT_ARC_HAS_FINAL_OUTPUT)) {
                        outputs.skipFinalOutput(`in`)
                    }
                    if (arc.flag(BIT_STOP_NODE)) {
                    } else if (arc.flag(BIT_TARGET_NEXT)) {
                    } else {
                        readUnpackedNodeTarget(`in`)
                    }
                    arc.flags = `in`.readByte()
                }
                // Undo the byte flags we read:
                `in`.skipBytes(-1)
                arc.nextArc = `in`.position
                readNextRealArc(arc, `in`)
            }
            require(arc.isLast)
        }
        return arc
    }

    @Throws(IOException::class)
    private fun readUnpackedNodeTarget(`in`: BytesReader): Long {
        return `in`.readVLong()
    }

    /**
     * Follow the `follow` arc and read the first arc of its target; this changes the
     * provided `arc` (2nd arg) in-place and returns it.
     *
     * @return Returns the second argument (`arc`).
     */
    @Throws(IOException::class)
    fun readFirstTargetArc(follow: Arc<T>, arc: Arc<T>, `in`: BytesReader): Arc<T> {
        // int pos = address;
        // System.out.println("    readFirstTarget follow.target=" + follow.target + " isFinal=" +
        // follow.isFinal());
        if (follow.isFinal) {
            // Insert "fake" final first arc:
            arc.label = END_LABEL
            arc.output = follow.nextFinalOutput()
            arc.flags = BIT_FINAL_ARC.toByte()
            if (follow.target() <= 0) {
                arc.flags = arc.flags or BIT_LAST_ARC.toByte()
            } else {
                // NOTE: nextArc is a node (not an address!) in this case:
                arc.nextArc = follow.target()
            }
            arc.target = FINAL_END_NODE
            arc.nodeFlags = arc.flags
            // System.out.println("    insert isFinal; nextArc=" + follow.target + " isLast=" +
            // arc.isLast() + " output=" + outputs.outputToString(arc.output));
            return arc
        } else {
            return readFirstRealTargetArc(follow.target(), arc, `in`)
        }
    }

    @Throws(IOException::class)
    private fun readFirstArcInfo(nodeAddress: Long, arc: Arc<T>, `in`: BytesReader) {
        `in`.position = nodeAddress

        arc.nodeFlags = `in`.readByte()
        val flags = arc.nodeFlags
        if (flags == ARCS_FOR_BINARY_SEARCH || flags == ARCS_FOR_DIRECT_ADDRESSING || flags == ARCS_FOR_CONTINUOUS) {
            // Special arc which is actually a node header for fixed length arcs.
            arc.numArcs = `in`.readVInt()
            arc.bytesPerArc = `in`.readVInt()
            arc.arcIdx = -1
            if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
                readPresenceBytes(arc, `in`)
                arc.firstLabel = readLabel(`in`)
                arc.presenceIndex = -1
            } else if (flags == ARCS_FOR_CONTINUOUS) {
                arc.firstLabel = readLabel(`in`)
            }
            arc.posArcsStart = `in`.position
        } else {
            arc.nextArc = nodeAddress
            arc.bytesPerArc = 0
        }
    }

    @Throws(IOException::class)
    fun readFirstRealTargetArc(nodeAddress: Long, arc: Arc<T>, `in`: BytesReader): Arc<T> {
        readFirstArcInfo(nodeAddress, arc, `in`)
        return readNextRealArc(arc, `in`)
    }

    /**
     * Returns whether `arc`'s target points to a node in expanded format (fixed length
     * arcs).
     */
    @Throws(IOException::class)
    fun isExpandedTarget(follow: Arc<T>, `in`: BytesReader): Boolean {
        if (!targetHasArcs(follow)) {
            return false
        } else {
            `in`.position = follow.target()
            val flags: Byte = `in`.readByte()
            return flags == ARCS_FOR_BINARY_SEARCH || flags == ARCS_FOR_DIRECT_ADDRESSING || flags == ARCS_FOR_CONTINUOUS
        }
    }

    /** In-place read; returns the arc.  */
    @Throws(IOException::class)
    fun readNextArc(arc: Arc<T>, `in`: BytesReader): Arc<T> {
        if (arc.label() == END_LABEL) {
            // This was a fake inserted "final" arc
            require(arc.nextArc() > 0) { "cannot readNextArc when arc.isLast()=true" }
            return readFirstRealTargetArc(arc.nextArc(), arc, `in`)
        } else {
            return readNextRealArc(arc, `in`)
        }
    }

    /** Peeks at next arc's label; does not alter arc. Do not call this if arc.isLast()!  */
    @Throws(IOException::class)
    fun readNextArcLabel(arc: Arc<T>, `in`: BytesReader): Int {
        require(!arc.isLast)

        if (arc.label() == END_LABEL) {
            // System.out.println("    nextArc fake " + arc.nextArc);
            // Next arc is the first arc of a node.
            // Position to read the first arc label.

            `in`.position = arc.nextArc()
            val flags: Byte = `in`.readByte()
            if (flags == ARCS_FOR_BINARY_SEARCH || flags == ARCS_FOR_DIRECT_ADDRESSING || flags == ARCS_FOR_CONTINUOUS) {
                // System.out.println("    nextArc fixed length arc");
                // Special arc which is actually a node header for fixed length arcs.
                val numArcs: Int = `in`.readVInt()
                `in`.readVInt() // Skip bytesPerArc.
                if (flags == ARCS_FOR_BINARY_SEARCH) {
                    `in`.readByte() // Skip arc flags.
                } else if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
                    `in`.skipBytes(getNumPresenceBytes(numArcs).toLong())
                } // Nothing to do for ARCS_FOR_CONTINUOUS
            }
        } else {
            when (arc.nodeFlags()) {
                ARCS_FOR_BINARY_SEARCH ->           // Point to next arc, -1 to skip arc flags.
                    `in`.position = arc.posArcsStart() - (1 + arc.arcIdx()) * arc.bytesPerArc().toLong() - 1

                ARCS_FOR_DIRECT_ADDRESSING -> {
                    // Direct addressing node. The label is not stored but rather inferred
                    // based on first label and arc index in the range.
                    require(BitTable.assertIsValid(arc, `in`))
                    require(BitTable.isBitSet(arc.arcIdx(), arc, `in`))
                    val nextIndex: Int = BitTable.nextBitSet(arc.arcIdx(), arc, `in`)
                    require(nextIndex != -1)
                    return arc.firstLabel() + nextIndex
                }

                ARCS_FOR_CONTINUOUS -> return arc.firstLabel() + arc.arcIdx() + 1
                else -> {
                    // Variable length arcs - linear search.
                    require(arc.bytesPerArc() == 0)
                    // Arcs have variable length.
                    // System.out.println("    nextArc real list");
                    // Position to next arc, -1 to skip flags.
                    `in`.position = arc.nextArc() - 1
                }
            }
        }
        return readLabel(`in`)
    }

    @Throws(IOException::class)
    fun readArcByIndex(arc: Arc<T>, `in`: BytesReader, idx: Int): Arc<T> {
        require(arc.bytesPerArc() > 0)
        require(arc.nodeFlags() == ARCS_FOR_BINARY_SEARCH)
        require(idx >= 0 && idx < arc.numArcs())
        `in`.position = arc.posArcsStart() - idx * arc.bytesPerArc().toLong()
        arc.arcIdx = idx
        arc.flags = `in`.readByte()
        return readArc(arc, `in`)
    }

    /**
     * Reads a Continuous node arc, with the provided index in the label range.
     *
     * @param rangeIndex The index of the arc in the label range. It must be within the label range.
     */
    @Throws(IOException::class)
    fun readArcByContinuous(arc: Arc<T>, `in`: BytesReader, rangeIndex: Int): Arc<T> {
        require(rangeIndex >= 0 && rangeIndex < arc.numArcs())
        `in`.position = arc.posArcsStart() - rangeIndex * arc.bytesPerArc().toLong()
        arc.arcIdx = rangeIndex
        arc.flags = `in`.readByte()
        return readArc(arc, `in`)
    }

    /**
     * Reads a present direct addressing node arc, with the provided index in the label range.
     *
     * @param rangeIndex The index of the arc in the label range. It must be present. The real arc
     * offset is computed based on the presence bits of the direct addressing node.
     */
    @Throws(IOException::class)
    fun readArcByDirectAddressing(arc: Arc<T>, `in`: BytesReader, rangeIndex: Int): Arc<T> {
        require(BitTable.assertIsValid(arc, `in`))
        require(rangeIndex >= 0 && rangeIndex < arc.numArcs())
        require(BitTable.isBitSet(rangeIndex, arc, `in`))
        val presenceIndex: Int = BitTable.countBitsUpTo(rangeIndex, arc, `in`)
        return readArcByDirectAddressing(arc, `in`, rangeIndex, presenceIndex)
    }

    /**
     * Reads a present direct addressing node arc, with the provided index in the label range and its
     * corresponding presence index (which is the count of presence bits before it).
     */
    @Throws(IOException::class)
    private fun readArcByDirectAddressing(
        arc: Arc<T>, `in`: BytesReader, rangeIndex: Int, presenceIndex: Int
    ): Arc<T> {
        `in`.position = arc.posArcsStart() - presenceIndex * arc.bytesPerArc().toLong()
        arc.arcIdx = rangeIndex
        arc.presenceIndex = presenceIndex
        arc.flags = `in`.readByte()
        return readArc(arc, `in`)
    }

    /**
     * Reads the last arc of a direct addressing node. This method is equivalent to call [ ][.readArcByDirectAddressing] with `rangeIndex` equal to `arc.numArcs() - 1`, but it is faster.
     */
    @Throws(IOException::class)
    fun readLastArcByDirectAddressing(arc: Arc<T>, `in`: BytesReader): Arc<T> {
        require(BitTable.assertIsValid(arc, `in`))
        val presenceIndex: Int = BitTable.countBits(arc, `in`) - 1
        return readArcByDirectAddressing(arc, `in`, arc.numArcs() - 1, presenceIndex)
    }

    /** Reads the last arc of a continuous node.  */
    @Throws(IOException::class)
    fun readLastArcByContinuous(arc: Arc<T>, `in`: BytesReader): Arc<T> {
        return readArcByContinuous(arc, `in`, arc.numArcs() - 1)
    }

    /** Never returns null, but you should never call this if arc.isLast() is true.  */
    @Throws(IOException::class)
    fun readNextRealArc(arc: Arc<T>, `in`: BytesReader): Arc<T> {
        val readNextRealArcStartNs = System.nanoTime()
        val stats = findTargetArcPerfStats
        stats.readNextRealArcCalls++
        // TODO: can't assert this because we call from readFirstArc
        // assert !flag(arc.flags, BIT_LAST_ARC);

        when (arc.nodeFlags()) {
            ARCS_FOR_BINARY_SEARCH, ARCS_FOR_CONTINUOUS -> {
                stats.readNextRealArcFixedCalls++
                val fixedHeaderStartNs = System.nanoTime()
                require(arc.bytesPerArc() > 0)
                arc.arcIdx++
                require(arc.arcIdx() >= 0 && arc.arcIdx() < arc.numArcs())
                `in`.position = arc.posArcsStart() - arc.arcIdx() * arc.bytesPerArc().toLong()
                arc.flags = `in`.readByte()
                stats.readNextRealArcFixedHeaderNs += System.nanoTime() - fixedHeaderStartNs
                val fixedReadArcStartNs = System.nanoTime()
                val result = readArc(arc, `in`)
                stats.readNextRealArcFixedReadArcNs += System.nanoTime() - fixedReadArcStartNs
                stats.readNextRealArcTotalNs += System.nanoTime() - readNextRealArcStartNs
                return result
            }

            ARCS_FOR_DIRECT_ADDRESSING -> {
                stats.readNextRealArcDirectCalls++
                val directStartNs = System.nanoTime()
                require(BitTable.assertIsValid(arc, `in`))
                require(arc.arcIdx() == -1 || BitTable.isBitSet(arc.arcIdx(), arc, `in`))
                val nextIndex: Int = BitTable.nextBitSet(arc.arcIdx(), arc, `in`)
                val result = readArcByDirectAddressing(arc, `in`, nextIndex, arc.presenceIndex + 1)
                stats.readNextRealArcDirectNs += System.nanoTime() - directStartNs
                stats.readNextRealArcTotalNs += System.nanoTime() - readNextRealArcStartNs
                return result
            }

            else -> {
                stats.readNextRealArcLinearCalls++
                val linearHeaderStartNs = System.nanoTime()
                // Variable length arcs - linear search.
                require(arc.bytesPerArc() == 0)
                `in`.position = arc.nextArc()
                arc.flags = `in`.readByte()
                stats.readNextRealArcLinearHeaderNs += System.nanoTime() - linearHeaderStartNs
                val linearReadArcStartNs = System.nanoTime()
                val result = readArc(arc, `in`)
                stats.readNextRealArcLinearReadArcNs += System.nanoTime() - linearReadArcStartNs
                stats.readNextRealArcTotalNs += System.nanoTime() - readNextRealArcStartNs
                return result
            }
        }
    }

    /**
     * Reads an arc. <br></br>
     * Precondition: The arc flags byte has already been read and set; the given BytesReader is
     * positioned just after the arc flags byte.
     */
    @Throws(IOException::class)
    private fun readArc(arc: Arc<T>, `in`: BytesReader): Arc<T> {
        val readArcStartNs = System.nanoTime()
        val stats = findTargetArcPerfStats
        stats.readArcCalls++
        if (arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING || arc.nodeFlags() == ARCS_FOR_CONTINUOUS) {
            stats.readArcFixedLabelCalls++
            arc.label = arc.firstLabel() + arc.arcIdx()
        } else {
            stats.readArcReadLabelCalls++
            val readLabelStartNs = System.nanoTime()
            arc.label = readLabel(`in`)
            stats.readArcReadLabelNs += System.nanoTime() - readLabelStartNs
        }

        if (arc.flag(BIT_ARC_HAS_OUTPUT)) {
            stats.readArcOutputReadCalls++
            val outputReadStartNs = System.nanoTime()
            arc.output = outputs.read(`in`)
            stats.readArcOutputReadNs += System.nanoTime() - outputReadStartNs
        } else {
            arc.output = outputs.noOutput
        }

        if (arc.flag(BIT_ARC_HAS_FINAL_OUTPUT)) {
            stats.readArcFinalOutputReadCalls++
            val finalOutputReadStartNs = System.nanoTime()
            arc.nextFinalOutput = outputs.readFinalOutput(`in`)
            stats.readArcFinalOutputReadNs += System.nanoTime() - finalOutputReadStartNs
        } else {
            arc.nextFinalOutput = outputs.noOutput
        }

        if (arc.flag(BIT_STOP_NODE)) {
            stats.readArcStopNodeCalls++
            if (arc.flag(BIT_FINAL_ARC)) {
                arc.target = FINAL_END_NODE
            } else {
                arc.target = NON_FINAL_END_NODE
            }
            arc.nextArc = `in`.position // Only useful for list.
        } else if (arc.flag(BIT_TARGET_NEXT)) {
            stats.readArcTargetNextCalls++
            arc.nextArc = `in`.position // Only useful for list.
            // TODO: would be nice to make this lazy -- maybe
            // caller doesn't need the target and is scanning arcs...
            if (!arc.flag(BIT_LAST_ARC)) {
                if (arc.bytesPerArc() == 0) {
                    // must scan
                    val seekToNextNodeStartNs = System.nanoTime()
                    seekToNextNode(`in`)
                    stats.readArcTargetNextSeekToNextNodeNs += System.nanoTime() - seekToNextNodeStartNs
                } else {
                    val targetNextPosCalcStartNs = System.nanoTime()
                    val numArcs =
                        if (arc.nodeFlags == ARCS_FOR_DIRECT_ADDRESSING)
                            BitTable.countBits(arc, `in`)
                        else
                            arc.numArcs()
                    `in`.position = arc.posArcsStart() - arc.bytesPerArc() * numArcs.toLong()
                    stats.readArcTargetNextPositionCalcNs += System.nanoTime() - targetNextPosCalcStartNs
                }
            }
            arc.target = `in`.position
        } else {
            stats.readArcUnpackedTargetCalls++
            val unpackedTargetStartNs = System.nanoTime()
            arc.target = readUnpackedNodeTarget(`in`)
            stats.readArcUnpackedTargetNs += System.nanoTime() - unpackedTargetStartNs
            arc.nextArc = `in`.position // Only useful for list.
        }
        stats.readArcTotalNs += System.nanoTime() - readArcStartNs
        return arc
    }

    // TODO: could we somehow [partially] tableize arc lookups
    // like automaton
    /**
     * Finds an arc leaving the incoming arc, replacing the arc in place. This returns null if the arc
     * was not found, else the incoming arc.
     */
    @Throws(IOException::class)
    fun findTargetArc(labelToMatch: Int, follow: Arc<T>, arc: Arc<T>, `in`: BytesReader): Arc<T>? {
        val totalStartNs = System.nanoTime()
        var readNodeHeaderNs = 0L
        var directNs = 0L
        var directHeaderNs = 0L
        var directRangeCheckNs = 0L
        var directBitCheckNs = 0L
        var directReadArcNs = 0L
        var binaryNs = 0L
        var binaryHeaderNs = 0L
        var binaryLoopNs = 0L
        var binaryReadArcNs = 0L
        var binaryIterations = 0L
        var continuousNs = 0L
        var continuousHeaderNs = 0L
        var continuousReadArcNs = 0L
        var linearNs = 0L
        var linearReadFirstArcInfoNs = 0L
        var linearLoopNs = 0L
        var linearSkipNs = 0L
        var linearReadArcNs = 0L
        var linearIterations = 0L
        var endLabelPath = false
        var noArcsPath = false
        var directPath = false
        var binaryPath = false
        var continuousPath = false
        var linearPath = false

        fun record(result: Arc<T>?): Arc<T>? {
            val stats = findTargetArcPerfStats
            stats.calls++
            if (endLabelPath) {
                stats.endLabelCalls++
            }
            if (noArcsPath) {
                stats.noArcsReturns++
            }
            if (directPath) {
                stats.directCalls++
            }
            if (binaryPath) {
                stats.binaryCalls++
            }
            if (continuousPath) {
                stats.continuousCalls++
            }
            if (linearPath) {
                stats.linearCalls++
            }
            if (result == null) {
                stats.nullReturns++
            } else {
                stats.nonNullReturns++
            }
            stats.readNodeHeaderNs += readNodeHeaderNs
            stats.directNs += directNs
            stats.directHeaderNs += directHeaderNs
            stats.directRangeCheckNs += directRangeCheckNs
            stats.directBitCheckNs += directBitCheckNs
            stats.directReadArcNs += directReadArcNs
            stats.binaryNs += binaryNs
            stats.binaryHeaderNs += binaryHeaderNs
            stats.binaryLoopNs += binaryLoopNs
            stats.binaryReadArcNs += binaryReadArcNs
            stats.binaryIterations += binaryIterations
            stats.continuousNs += continuousNs
            stats.continuousHeaderNs += continuousHeaderNs
            stats.continuousReadArcNs += continuousReadArcNs
            stats.linearNs += linearNs
            stats.linearReadFirstArcInfoNs += linearReadFirstArcInfoNs
            stats.linearLoopNs += linearLoopNs
            stats.linearSkipNs += linearSkipNs
            stats.linearReadArcNs += linearReadArcNs
            stats.linearIterations += linearIterations
            stats.totalNs += System.nanoTime() - totalStartNs
            return result
        }

        if (labelToMatch == END_LABEL) {
            endLabelPath = true
            if (follow.isFinal) {
                if (follow.target() <= 0) {
                    arc.flags = BIT_LAST_ARC.toByte()
                } else {
                    arc.flags = 0
                    // NOTE: nextArc is a node (not an address!) in this case:
                    arc.nextArc = follow.target()
                }
                arc.output = follow.nextFinalOutput()
                arc.label = END_LABEL
                arc.nodeFlags = arc.flags
                return record(arc)
            } else {
                return record(null)
            }
        }

        if (!targetHasArcs(follow)) {
            noArcsPath = true
            return record(null)
        }

        `in`.position = follow.target()

        // System.out.println("fta label=" + (char) labelToMatch);
        val readNodeHeaderStartNs = System.nanoTime()
        arc.nodeFlags = `in`.readByte()
        readNodeHeaderNs += System.nanoTime() - readNodeHeaderStartNs
        var flags = arc.nodeFlags
        if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
            directPath = true
            val directStartNs = System.nanoTime()
            val directHeaderStartNs = System.nanoTime()
            arc.numArcs = `in`.readVInt() // This is in fact the label range.
            arc.bytesPerArc = `in`.readVInt()
            readPresenceBytes(arc, `in`)
            arc.firstLabel = readLabel(`in`)
            arc.posArcsStart = `in`.position
            directHeaderNs += System.nanoTime() - directHeaderStartNs

            val rangeCheckStartNs = System.nanoTime()
            val arcIndex = labelToMatch - arc.firstLabel()
            if (arcIndex < 0 || arcIndex >= arc.numArcs()) {
                directRangeCheckNs += System.nanoTime() - rangeCheckStartNs
                directNs += System.nanoTime() - directStartNs
                return record(null) // Before or after label range.
            }
            directRangeCheckNs += System.nanoTime() - rangeCheckStartNs

            val directBitCheckStartNs = System.nanoTime()
            if (!BitTable.isBitSet(arcIndex, arc, `in`)) {
                directBitCheckNs += System.nanoTime() - directBitCheckStartNs
                directNs += System.nanoTime() - directStartNs
                return record(null) // Arc missing in the range.
            }
            directBitCheckNs += System.nanoTime() - directBitCheckStartNs

            val directReadArcStartNs = System.nanoTime()
            val result = readArcByDirectAddressing(arc, `in`, arcIndex)
            directReadArcNs += System.nanoTime() - directReadArcStartNs
            directNs += System.nanoTime() - directStartNs
            return record(result)
        } else if (flags == ARCS_FOR_BINARY_SEARCH) {
            binaryPath = true
            val binaryStartNs = System.nanoTime()
            val binaryHeaderStartNs = System.nanoTime()
            arc.numArcs = `in`.readVInt()
            arc.bytesPerArc = `in`.readVInt()
            arc.posArcsStart = `in`.position
            binaryHeaderNs += System.nanoTime() - binaryHeaderStartNs

            // Array is sparse; do binary search:
            var low = 0
            var high = arc.numArcs() - 1
            while (low <= high) {
                // System.out.println("    cycle");
                binaryIterations++
                val binaryLoopStartNs = System.nanoTime()
                val mid = (low + high) ushr 1
                // +1 to skip over flags
                `in`.position = arc.posArcsStart() - (arc.bytesPerArc() * mid + 1)
                val midLabel = readLabel(`in`)
                val cmp = midLabel - labelToMatch
                binaryLoopNs += System.nanoTime() - binaryLoopStartNs
                if (cmp < 0) {
                    low = mid + 1
                } else if (cmp > 0) {
                    high = mid - 1
                } else {
                    arc.arcIdx = mid - 1
                    // System.out.println("    found!");
                    val binaryReadArcStartNs = System.nanoTime()
                    val result = readNextRealArc(arc, `in`)
                    binaryReadArcNs += System.nanoTime() - binaryReadArcStartNs
                    binaryNs += System.nanoTime() - binaryStartNs
                    return record(result)
                }
            }
            binaryNs += System.nanoTime() - binaryStartNs
            return record(null)
        } else if (flags == ARCS_FOR_CONTINUOUS) {
            continuousPath = true
            val continuousStartNs = System.nanoTime()
            val continuousHeaderStartNs = System.nanoTime()
            arc.numArcs = `in`.readVInt()
            arc.bytesPerArc = `in`.readVInt()
            arc.firstLabel = readLabel(`in`)
            arc.posArcsStart = `in`.position
            continuousHeaderNs += System.nanoTime() - continuousHeaderStartNs
            val arcIndex = labelToMatch - arc.firstLabel()
            if (arcIndex < 0 || arcIndex >= arc.numArcs()) {
                continuousNs += System.nanoTime() - continuousStartNs
                return record(null) // Before or after label range.
            }
            arc.arcIdx = arcIndex - 1
            val continuousReadArcStartNs = System.nanoTime()
            val result = readNextRealArc(arc, `in`)
            continuousReadArcNs += System.nanoTime() - continuousReadArcStartNs
            continuousNs += System.nanoTime() - continuousStartNs
            return record(result)
        }

        // Linear scan
        linearPath = true
        val linearStartNs = System.nanoTime()
        val linearReadFirstArcInfoStartNs = System.nanoTime()
        readFirstArcInfo(follow.target(), arc, `in`)
        linearReadFirstArcInfoNs += System.nanoTime() - linearReadFirstArcInfoStartNs
        `in`.position = arc.nextArc()
        while (true) {
            linearIterations++
            val linearLoopStartNs = System.nanoTime()
            require(arc.bytesPerArc() == 0)
            arc.flags = `in`.readByte()
            flags = arc.flags
            val pos = `in`.position
            val label = readLabel(`in`)
            if (label == labelToMatch) {
                `in`.position = pos
                val linearReadArcStartNs = System.nanoTime()
                val result = readArc(arc, `in`)
                linearReadArcNs += System.nanoTime() - linearReadArcStartNs
                linearLoopNs += System.nanoTime() - linearLoopStartNs
                linearNs += System.nanoTime() - linearStartNs
                return record(result)
            } else if (label > labelToMatch) {
                linearLoopNs += System.nanoTime() - linearLoopStartNs
                linearNs += System.nanoTime() - linearStartNs
                return record(null)
            } else if (arc.isLast) {
                linearLoopNs += System.nanoTime() - linearLoopStartNs
                linearNs += System.nanoTime() - linearStartNs
                return record(null)
            } else {
                val linearSkipStartNs = System.nanoTime()
                if (flag(flags.toInt(), BIT_ARC_HAS_OUTPUT)) {
                    outputs.skipOutput(`in`)
                }
                if (flag(flags.toInt(), BIT_ARC_HAS_FINAL_OUTPUT)) {
                    outputs.skipFinalOutput(`in`)
                }
                if (!flag(flags.toInt(), BIT_STOP_NODE) && !flag(flags.toInt(), BIT_TARGET_NEXT)) {
                    readUnpackedNodeTarget(`in`)
                }
                linearSkipNs += System.nanoTime() - linearSkipStartNs
                linearLoopNs += System.nanoTime() - linearLoopStartNs
            }
        }
    }

    @Throws(IOException::class)
    private fun seekToNextNode(`in`: BytesReader) {
        while (true) {
            val flags: Int = `in`.readByte().toInt()
            readLabel(`in`)

            if (flag(flags, BIT_ARC_HAS_OUTPUT)) {
                outputs.skipOutput(`in`)
            }

            if (flag(flags, BIT_ARC_HAS_FINAL_OUTPUT)) {
                outputs.skipFinalOutput(`in`)
            }

            if (!flag(flags, BIT_STOP_NODE) && !flag(flags, BIT_TARGET_NEXT)) {
                readUnpackedNodeTarget(`in`)
            }

            if (flag(flags, BIT_LAST_ARC)) {
                return
            }
        }
    }

    fun getBytesReader(): BytesReader
            /** Returns a [BytesReader] for this FST, positioned at position 0.  */
            = fstReader.getReverseBytesReader()

    /** Reads bytes stored in an FST.  */
    abstract class BytesReader : DataInput() {
        /** Get current read position.  */
        //abstract fun getPosition(): Long

        /** Set current read position.  */
        //abstract fun setPosition(pos: Long)

        abstract var position: Long
    }

    /**
     * Represents the FST metadata.
     *
     * @param <T> the FST output type
    </T> */
    class FSTMetadata<T>(
        val inputType: INPUT_TYPE,
        val outputs: Outputs<T>,
        // if non-null, this FST accepts the empty string and
        // produces this output
        var emptyOutput: T?,
        startNode: Long,
        version: Int,
        numBytes: Long
    ) {
        /**
         * Returns the version constant of the binary format this FST was written in. See the `static final int VERSION` constants in FST's javadoc, e.g. [ ][FST.VERSION_CONTINUOUS_ARCS].
         */
        val version: Int

        var startNode: Long
        var numBytes: Long

        init {
            this.emptyOutput = emptyOutput
            this.startNode = startNode
            this.version = version
            this.numBytes = numBytes
        }

        /**
         * Save the metadata to a DataOutput
         *
         * @param metaOut the DataOutput to write the metadata to
         */
        @Throws(IOException::class)
        fun save(metaOut: DataOutput) {
            CodecUtil.writeHeader(metaOut, FILE_FORMAT_NAME, VERSION_CURRENT)
            // TODO: really we should encode this as an arc, arriving
            // to the root node, instead of special casing here:
            if (emptyOutput != null) {
                // Accepts empty string
                metaOut.writeByte(1.toByte())

                // Serialize empty-string output:
                val ros = ByteBuffersDataOutput()
                outputs.writeFinalOutput(emptyOutput!!, ros)
                val emptyOutputBytes: ByteArray = ros.toArrayCopy()
                val emptyLen = emptyOutputBytes.size

                // reverse
                val stopAt = emptyLen / 2
                var upto = 0
                while (upto < stopAt) {
                    val b = emptyOutputBytes[upto]
                    emptyOutputBytes[upto] = emptyOutputBytes[emptyLen - upto - 1]
                    emptyOutputBytes[emptyLen - upto - 1] = b
                    upto++
                }
                metaOut.writeVInt(emptyLen)
                metaOut.writeBytes(emptyOutputBytes, 0, emptyLen)
            } else {
                metaOut.writeByte(0.toByte())
            }
            val t: Byte = if (inputType == INPUT_TYPE.BYTE1) {
                0
            } else if (inputType == INPUT_TYPE.BYTE2) {
                1
            } else {
                2
            }
            metaOut.writeByte(t)
            metaOut.writeVLong(startNode)
            metaOut.writeVLong(numBytes)
        }
    }

    private class FindTargetArcPerfStats {
        var calls: Long = 0
        var endLabelCalls: Long = 0
        var noArcsReturns: Long = 0
        var directCalls: Long = 0
        var binaryCalls: Long = 0
        var continuousCalls: Long = 0
        var linearCalls: Long = 0
        var nullReturns: Long = 0
        var nonNullReturns: Long = 0
        var readNodeHeaderNs: Long = 0
        var directNs: Long = 0
        var directHeaderNs: Long = 0
        var directRangeCheckNs: Long = 0
        var directBitCheckNs: Long = 0
        var directReadArcNs: Long = 0
        var binaryNs: Long = 0
        var binaryHeaderNs: Long = 0
        var binaryLoopNs: Long = 0
        var binaryReadArcNs: Long = 0
        var binaryIterations: Long = 0
        var continuousNs: Long = 0
        var continuousHeaderNs: Long = 0
        var continuousReadArcNs: Long = 0
        var linearNs: Long = 0
        var linearReadFirstArcInfoNs: Long = 0
        var linearLoopNs: Long = 0
        var linearSkipNs: Long = 0
        var linearReadArcNs: Long = 0
        var linearIterations: Long = 0
        var readNextRealArcCalls: Long = 0
        var readNextRealArcFixedCalls: Long = 0
        var readNextRealArcDirectCalls: Long = 0
        var readNextRealArcLinearCalls: Long = 0
        var readNextRealArcFixedHeaderNs: Long = 0
        var readNextRealArcFixedReadArcNs: Long = 0
        var readNextRealArcDirectNs: Long = 0
        var readNextRealArcLinearHeaderNs: Long = 0
        var readNextRealArcLinearReadArcNs: Long = 0
        var readNextRealArcTotalNs: Long = 0
        var readArcCalls: Long = 0
        var readArcFixedLabelCalls: Long = 0
        var readArcReadLabelCalls: Long = 0
        var readArcReadLabelNs: Long = 0
        var readArcOutputReadCalls: Long = 0
        var readArcOutputReadNs: Long = 0
        var readArcFinalOutputReadCalls: Long = 0
        var readArcFinalOutputReadNs: Long = 0
        var readArcStopNodeCalls: Long = 0
        var readArcTargetNextCalls: Long = 0
        var readArcTargetNextSeekToNextNodeNs: Long = 0
        var readArcTargetNextPositionCalcNs: Long = 0
        var readArcUnpackedTargetCalls: Long = 0
        var readArcUnpackedTargetNs: Long = 0
        var readArcTotalNs: Long = 0
        var totalNs: Long = 0

        fun reset() {
            calls = 0
            endLabelCalls = 0
            noArcsReturns = 0
            directCalls = 0
            binaryCalls = 0
            continuousCalls = 0
            linearCalls = 0
            nullReturns = 0
            nonNullReturns = 0
            readNodeHeaderNs = 0
            directNs = 0
            directHeaderNs = 0
            directRangeCheckNs = 0
            directBitCheckNs = 0
            directReadArcNs = 0
            binaryNs = 0
            binaryHeaderNs = 0
            binaryLoopNs = 0
            binaryReadArcNs = 0
            binaryIterations = 0
            continuousNs = 0
            continuousHeaderNs = 0
            continuousReadArcNs = 0
            linearNs = 0
            linearReadFirstArcInfoNs = 0
            linearLoopNs = 0
            linearSkipNs = 0
            linearReadArcNs = 0
            linearIterations = 0
            readNextRealArcCalls = 0
            readNextRealArcFixedCalls = 0
            readNextRealArcDirectCalls = 0
            readNextRealArcLinearCalls = 0
            readNextRealArcFixedHeaderNs = 0
            readNextRealArcFixedReadArcNs = 0
            readNextRealArcDirectNs = 0
            readNextRealArcLinearHeaderNs = 0
            readNextRealArcLinearReadArcNs = 0
            readNextRealArcTotalNs = 0
            readArcCalls = 0
            readArcFixedLabelCalls = 0
            readArcReadLabelCalls = 0
            readArcReadLabelNs = 0
            readArcOutputReadCalls = 0
            readArcOutputReadNs = 0
            readArcFinalOutputReadCalls = 0
            readArcFinalOutputReadNs = 0
            readArcStopNodeCalls = 0
            readArcTargetNextCalls = 0
            readArcTargetNextSeekToNextNodeNs = 0
            readArcTargetNextPositionCalcNs = 0
            readArcUnpackedTargetCalls = 0
            readArcUnpackedTargetNs = 0
            readArcTotalNs = 0
            totalNs = 0
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(FST::class)
        private val findTargetArcPerfLogger = KotlinLogging.logger {}
        private val findTargetArcPerfStats = FindTargetArcPerfStats()

        fun resetFindTargetArcPerfStats() {
            findTargetArcPerfStats.reset()
        }

        fun logFindTargetArcPerfStats(tag: String = "") {
            if (!findTargetArcPerfLogger.isDebugEnabled()) {
                return
            }
            val stats = findTargetArcPerfStats
            if (stats.calls == 0L) {
                findTargetArcPerfLogger.debug { "phase=fst.findTargetArc.stats tag=$tag calls=0" }
                return
            }
            fun toMs(ns: Long): Long = TimeUnit.NANOSECONDS.toMillis(ns)
            findTargetArcPerfLogger.debug {
                "phase=fst.findTargetArc.stats tag=$tag calls=${stats.calls} " +
                    "endLabelCalls=${stats.endLabelCalls} " +
                    "noArcsReturns=${stats.noArcsReturns} " +
                    "directCalls=${stats.directCalls} " +
                    "binaryCalls=${stats.binaryCalls} " +
                    "continuousCalls=${stats.continuousCalls} " +
                    "linearCalls=${stats.linearCalls} " +
                    "nullReturns=${stats.nullReturns} " +
                    "nonNullReturns=${stats.nonNullReturns} " +
                    "readNodeHeaderMs=${toMs(stats.readNodeHeaderNs)} " +
                    "directMs=${toMs(stats.directNs)} " +
                    "directHeaderMs=${toMs(stats.directHeaderNs)} " +
                    "directRangeCheckMs=${toMs(stats.directRangeCheckNs)} " +
                    "directBitCheckMs=${toMs(stats.directBitCheckNs)} " +
                    "directReadArcMs=${toMs(stats.directReadArcNs)} " +
                    "binaryMs=${toMs(stats.binaryNs)} " +
                    "binaryHeaderMs=${toMs(stats.binaryHeaderNs)} " +
                    "binaryLoopMs=${toMs(stats.binaryLoopNs)} " +
                    "binaryReadArcMs=${toMs(stats.binaryReadArcNs)} " +
                    "binaryIterations=${stats.binaryIterations} " +
                    "continuousMs=${toMs(stats.continuousNs)} " +
                    "continuousHeaderMs=${toMs(stats.continuousHeaderNs)} " +
                    "continuousReadArcMs=${toMs(stats.continuousReadArcNs)} " +
                    "linearMs=${toMs(stats.linearNs)} " +
                    "linearReadFirstArcInfoMs=${toMs(stats.linearReadFirstArcInfoNs)} " +
                    "linearLoopMs=${toMs(stats.linearLoopNs)} " +
                    "linearSkipMs=${toMs(stats.linearSkipNs)} " +
                    "linearReadArcMs=${toMs(stats.linearReadArcNs)} " +
                    "linearIterations=${stats.linearIterations} " +
                    "readNextRealArcCalls=${stats.readNextRealArcCalls} " +
                    "readNextRealArcFixedCalls=${stats.readNextRealArcFixedCalls} " +
                    "readNextRealArcDirectCalls=${stats.readNextRealArcDirectCalls} " +
                    "readNextRealArcLinearCalls=${stats.readNextRealArcLinearCalls} " +
                    "readNextRealArcFixedHeaderMs=${toMs(stats.readNextRealArcFixedHeaderNs)} " +
                    "readNextRealArcFixedReadArcMs=${toMs(stats.readNextRealArcFixedReadArcNs)} " +
                    "readNextRealArcDirectMs=${toMs(stats.readNextRealArcDirectNs)} " +
                    "readNextRealArcLinearHeaderMs=${toMs(stats.readNextRealArcLinearHeaderNs)} " +
                    "readNextRealArcLinearReadArcMs=${toMs(stats.readNextRealArcLinearReadArcNs)} " +
                    "readNextRealArcTotalMs=${toMs(stats.readNextRealArcTotalNs)} " +
                    "readArcCalls=${stats.readArcCalls} " +
                    "readArcFixedLabelCalls=${stats.readArcFixedLabelCalls} " +
                    "readArcReadLabelCalls=${stats.readArcReadLabelCalls} " +
                    "readArcReadLabelMs=${toMs(stats.readArcReadLabelNs)} " +
                    "readArcOutputReadCalls=${stats.readArcOutputReadCalls} " +
                    "readArcOutputReadMs=${toMs(stats.readArcOutputReadNs)} " +
                    "readArcFinalOutputReadCalls=${stats.readArcFinalOutputReadCalls} " +
                    "readArcFinalOutputReadMs=${toMs(stats.readArcFinalOutputReadNs)} " +
                    "readArcStopNodeCalls=${stats.readArcStopNodeCalls} " +
                    "readArcTargetNextCalls=${stats.readArcTargetNextCalls} " +
                    "readArcTargetNextSeekToNextNodeMs=${toMs(stats.readArcTargetNextSeekToNextNodeNs)} " +
                    "readArcTargetNextPositionCalcMs=${toMs(stats.readArcTargetNextPositionCalcNs)} " +
                    "readArcUnpackedTargetCalls=${stats.readArcUnpackedTargetCalls} " +
                    "readArcUnpackedTargetMs=${toMs(stats.readArcUnpackedTargetNs)} " +
                    "readArcTotalMs=${toMs(stats.readArcTotalNs)} " +
                    "totalMs=${toMs(stats.totalNs)}"
            }
        }

        const val BIT_FINAL_ARC: Int = 1 shl 0
        const val BIT_LAST_ARC: Int = 1 shl 1
        const val BIT_TARGET_NEXT: Int = 1 shl 2

        // TODO: we can free up a bit if we can nuke this:
        const val BIT_STOP_NODE: Int = 1 shl 3

        /** This flag is set if the arc has an output.  */
        const val BIT_ARC_HAS_OUTPUT: Int = 1 shl 4

        const val BIT_ARC_HAS_FINAL_OUTPUT: Int = 1 shl 5

        /**
         * Value of the arc flags to declare a node with fixed length (sparse) arcs designed for binary
         * search.
         */
        // We use this as a marker because this one flag is illegal by itself.
        const val ARCS_FOR_BINARY_SEARCH: Byte = BIT_ARC_HAS_FINAL_OUTPUT.toByte()

        /**
         * Value of the arc flags to declare a node with fixed length dense arcs and bit table designed
         * for direct addressing.
         */
        const val ARCS_FOR_DIRECT_ADDRESSING: Byte = (1 shl 6).toByte()

        /**
         * Value of the arc flags to declare a node with continuous arcs designed for pos the arc directly
         * with labelToPos - firstLabel. like [.ARCS_FOR_BINARY_SEARCH] we use flag combinations
         * that will not occur at the same time.
         */
        const val ARCS_FOR_CONTINUOUS: Byte = (ARCS_FOR_DIRECT_ADDRESSING + ARCS_FOR_BINARY_SEARCH).toByte()

        // Increment version to change it
        private const val FILE_FORMAT_NAME = "FST"

        /** First supported version, this is the version that was used when releasing Lucene 7.0.  */
        const val VERSION_START: Int = 6

        // Version 7 introduced direct addressing for arcs, but it's not recorded here because it doesn't
        // need version checks on the read side, it uses new flag values on arcs instead.
        private const val VERSION_LITTLE_ENDIAN = 8

        /** Version that started storing continuous arcs.  */
        const val VERSION_CONTINUOUS_ARCS: Int = 9

        /** Current version.  */
        const val VERSION_CURRENT: Int = VERSION_CONTINUOUS_ARCS

        /** Version that was used when releasing Lucene 9.0.  */
        const val VERSION_90: Int = VERSION_LITTLE_ENDIAN

        // Never serialized; just used to represent the virtual
        // final node w/ no arcs:
        const val FINAL_END_NODE: Long = -1

        // Never serialized; just used to represent the virtual
        // non-final node w/ no arcs:
        const val NON_FINAL_END_NODE: Long = 0

        /** If arc has this label then that arc is final/accepted  */
        const val END_LABEL: Int = -1

        private fun flag(flags: Int, bit: Int): Boolean {
            return (flags and bit) != 0
        }

        private val DEFAULT_MAX_BLOCK_BITS = if (Constants.JRE_IS_64BIT) 30 else 28

        /**
         * Create a FST from a [FSTReader]. Return null if the metadata is null.
         *
         * @param fstMetadata the metadata
         * @param fstReader the FSTReader
         * @return the FST
         */
        fun <T> fromFSTReader(fstMetadata: FSTMetadata<T>?, fstReader: FSTReader): FST<T>? {
            // FSTMetadata could be null if there is no node accepted by the FST
            if (fstMetadata == null) {
                return null
            }
            return FST(fstMetadata, fstReader)
        }

        /**
         * Read the FST metadata from DataInput
         *
         * @param metaIn the DataInput of the metadata
         * @param outputs the FST outputs
         * @return the FST metadata
         * @param <T> the output type
         * @throws IOException if exception occurred during parsing
        </T> */
        @Throws(IOException::class)
        fun <T> readMetadata(metaIn: DataInput, outputs: Outputs<T>): FSTMetadata<T> {
            // NOTE: only reads formats VERSION_START up to VERSION_CURRENT; we don't have
            // back-compat promise for FSTs (they are experimental), but we are sometimes able to offer it
            val version: Int = CodecUtil.checkHeader(metaIn, FILE_FORMAT_NAME, VERSION_START, VERSION_CURRENT)
            val emptyOutput: T?
            if (metaIn.readByte() == 1.toByte()) {
                // accepts empty string
                // 1 KB blocks:
                val emptyBytes: ReadWriteDataOutput = getOnHeapReaderWriter(10) as ReadWriteDataOutput
                val numBytes: Int = metaIn.readVInt()
                emptyBytes.copyBytes(metaIn, numBytes.toLong())

                emptyBytes.freeze()

                // De-serialize empty-string output:
                val reader: BytesReader = emptyBytes.getReverseBytesReader()
                // NoOutputs uses 0 bytes when writing its output,
                // so we have to check here else BytesStore gets
                // angry:
                if (numBytes > 0) {
                    reader.position = (numBytes - 1).toLong()
                }
                emptyOutput = outputs.readFinalOutput(reader)
            } else {
                emptyOutput = null
            }
            val inputType: INPUT_TYPE
            val t: Byte = metaIn.readByte()
            inputType =
                when (t) {
                    0.toByte() -> INPUT_TYPE.BYTE1
                    1.toByte() -> INPUT_TYPE.BYTE2
                    2.toByte() -> INPUT_TYPE.BYTE4
                    else -> throw CorruptIndexException("invalid input type $t", metaIn)
                }
            val startNode: Long = metaIn.readVLong()
            val numBytes: Long = metaIn.readVLong()
            return FSTMetadata(inputType, outputs, emptyOutput, startNode, version, numBytes)
        }

        /** Reads an automaton from a file.  */
        @Throws(IOException::class)
        fun <T> read(path: Path, outputs: Outputs<T>): FST<T> {
            Files.newInputStream(path).use { `is` ->
                val `in`: DataInput = InputStreamDataInput(BufferedInputStream(`is`))
                return FST(readMetadata(`in`, outputs), `in`)
            }
        }

        /** returns true if the node at this address has any outgoing arcs  */
        fun <T> targetHasArcs(arc: Arc<T>): Boolean {
            return arc.target() > 0
        }

        /**
         * Gets the number of bytes required to flag the presence of each arc in the given label range,
         * one bit per arc.
         */
        fun getNumPresenceBytes(labelRange: Int): Int {
            require(labelRange >= 0)
            return (labelRange + 7) shr 3
        }

        fun <T> readEndArc(follow: Arc<T>, arc: Arc<T>): Arc<T>? {
            if (follow.isFinal) {
                if (follow.target() <= 0) {
                    arc.flags = BIT_LAST_ARC.toByte()
                } else {
                    arc.flags = 0
                    // NOTE: nextArc is a node (not an address!) in this case:
                    arc.nextArc = follow.target()
                }
                arc.output = follow.nextFinalOutput()
                arc.label = END_LABEL
                return arc
            } else {
                return null
            }
        }
    }
}
