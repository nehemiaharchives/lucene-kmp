package morfologik.fsa

import org.gnit.lucenekmp.jdkport.InputStream

/**
 * CFSA (Compact Finite State Automaton) binary format implementation, version 2.
 */
class CFSA2(stream: InputStream) : FSA() {
    companion object {
        const val VERSION: Byte = 0xC6.toByte()

        const val BIT_TARGET_NEXT: Int = 1 shl 7
        const val BIT_LAST_ARC: Int = 1 shl 6
        const val BIT_FINAL_ARC: Int = 1 shl 5

        const val LABEL_INDEX_BITS: Int = 5
        const val LABEL_INDEX_MASK: Int = (1 shl LABEL_INDEX_BITS) - 1
        const val LABEL_INDEX_SIZE: Int = (1 shl LABEL_INDEX_BITS) - 1

        fun readVInt(array: ByteArray, offset: Int): Int {
            var off = offset
            var b = array[off]
            var value = b.toInt() and 0x7F
            var shift = 7
            while (b < 0) {
                b = array[++off]
                value = value or ((b.toInt() and 0x7F) shl shift)
                shift += 7
            }
            return value
        }

        fun vIntLength(value: Int): Int {
            require(value >= 0) { "Can't v-code negative ints." }
            var v = value
            var bytes = 1
            while (v >= 0x80) {
                v = v ushr 7
                bytes++
            }
            return bytes
        }
    }

    val arcs: ByteArray
    private val flags: Set<FSAFlags>
    val labelMapping: ByteArray
    private val hasNumbers: Boolean
    private val epsilon: Int = 0

    init {
        val flagBits = readShort(stream)
        val tmpFlags = mutableSetOf<FSAFlags>()
        for (f in FSAFlags.values()) {
            if (f.isSet(flagBits.toInt())) {
                tmpFlags.add(f)
            }
        }
        if (flagBits.toInt() != FSAFlags.asShort(tmpFlags).toInt()) {
            throw okio.IOException("Unrecognized flags: 0x${flagBits.toString(16)}")
        }
        flags = tmpFlags.toSet()
        hasNumbers = flags.contains(FSAFlags.NUMBERS)

        val labelMappingSize = readUnsignedByte(stream)
        labelMapping = ByteArray(labelMappingSize)
        readFully(stream, labelMapping)

        arcs = readRemaining(stream)
    }

    override fun getRootNode(): Int {
        return getDestinationNodeOffset(getFirstArc(epsilon))
    }

    override fun getFirstArc(node: Int): Int {
        return if (hasNumbers) skipVInt(node) else node
    }

    override fun getNextArc(arc: Int): Int {
        return if (isArcLast(arc)) 0 else skipArc(arc)
    }

    override fun getArc(node: Int, label: Byte): Int {
        var arc = getFirstArc(node)
        while (arc != 0) {
            if (getArcLabel(arc) == label) return arc
            arc = getNextArc(arc)
        }
        return 0
    }

    override fun getEndNode(arc: Int): Int {
        val nodeOffset = getDestinationNodeOffset(arc)
        check(nodeOffset != 0) { "Can't follow a terminal arc: $arc" }
        check(nodeOffset < arcs.size) { "Node out of bounds." }
        return nodeOffset
    }

    override fun getArcLabel(arc: Int): Byte {
        val index = arcs[arc].toInt() and LABEL_INDEX_MASK
        return if (index > 0) labelMapping[index] else arcs[arc + 1]
    }

    override fun getRightLanguageCount(node: Int): Int {
        check(getFlags().contains(FSAFlags.NUMBERS)) { "This FSA was not compiled with NUMBERS." }
        return readVInt(arcs, node)
    }

    override fun isArcFinal(arc: Int): Boolean = (arcs[arc].toInt() and BIT_FINAL_ARC) != 0

    override fun isArcTerminal(arc: Int): Boolean = getDestinationNodeOffset(arc) == 0

    fun isArcLast(arc: Int): Boolean = (arcs[arc].toInt() and BIT_LAST_ARC) != 0

    fun isNextSet(arc: Int): Boolean = (arcs[arc].toInt() and BIT_TARGET_NEXT) != 0

    override fun getFlags(): Set<FSAFlags> = flags

    private fun getDestinationNodeOffset(arc: Int): Int {
        return if (isNextSet(arc)) {
            var currentArc = arc
            while (!isArcLast(currentArc)) {
                currentArc = getNextArc(currentArc)
            }
            skipArc(currentArc)
        } else {
            val labelIsExplicit = (arcs[arc].toInt() and LABEL_INDEX_MASK) == 0
            readVInt(arcs, arc + if (labelIsExplicit) 2 else 1)
        }
    }

    private fun skipArc(offset: Int): Int {
        var off = offset
        val flag = arcs[off++]
        if ((flag.toInt() and LABEL_INDEX_MASK) == 0) {
            off++
        }
        if ((flag.toInt() and BIT_TARGET_NEXT) == 0) {
            off = skipVInt(off)
        }
        check(off < arcs.size)
        return off
    }

    private fun skipVInt(offset: Int): Int {
        var off = offset
        while (arcs[off++] < 0) {
            // keep skipping
        }
        return off
    }

    private fun readUnsignedByte(stream: InputStream): Int {
        val v = stream.read()
        if (v < 0) throw okio.IOException("Unexpected end of stream")
        return v and 0xFF
    }

    private fun readShort(stream: InputStream): Short {
        val hi = readUnsignedByte(stream)
        val lo = readUnsignedByte(stream)
        return ((hi shl 8) or lo).toShort()
    }

    private fun readFully(stream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read < 0) throw okio.IOException("Unexpected end of stream")
            offset += read
        }
    }
}
