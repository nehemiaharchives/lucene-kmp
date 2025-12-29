package morfologik.fsa

import org.gnit.lucenekmp.jdkport.InputStream

/**
 * CFSA (Compact Finite State Automaton) binary format implementation.
 */
class CFSA(stream: InputStream) : FSA() {
    companion object {
        const val VERSION: Byte = 0xC5.toByte()
        const val BIT_FINAL_ARC: Int = 1 shl 0
        const val BIT_LAST_ARC: Int = 1 shl 1
        const val BIT_TARGET_NEXT: Int = 1 shl 2
    }

    val arcs: ByteArray
    val nodeDataLength: Int
    private val flags: Set<FSAFlags>
    val gtl: Int
    val labelMapping: ByteArray

    init {
        readByte(stream) // filler
        readByte(stream) // annotation
        val hgtl = readByte(stream).toInt() and 0xFF

        val tmpFlags = mutableSetOf(FSAFlags.FLEXIBLE, FSAFlags.STOPBIT, FSAFlags.NEXTBIT)
        if ((hgtl and 0xF0) != 0) {
            nodeDataLength = (hgtl ushr 4) and 0x0F
            gtl = hgtl and 0x0F
            tmpFlags.add(FSAFlags.NUMBERS)
        } else {
            nodeDataLength = 0
            gtl = hgtl and 0x0F
        }
        flags = tmpFlags.toSet()

        labelMapping = ByteArray(1 shl 5)
        readFully(stream, labelMapping)

        arcs = readRemaining(stream)
    }

    override fun getRootNode(): Int {
        val epsilonNode = skipArc(getFirstArc(0))
        return getDestinationNodeOffset(getFirstArc(epsilonNode))
    }

    override fun getFirstArc(node: Int): Int = nodeDataLength + node

    override fun getNextArc(arc: Int): Int = if (isArcLast(arc)) 0 else skipArc(arc)

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
        if (nodeOffset == 0) {
            throw RuntimeException("This is a terminal arc [$arc]")
        }
        return nodeOffset
    }

    override fun getArcLabel(arc: Int): Byte {
        return if (isNextSet(arc) && isLabelCompressed(arc)) {
            labelMapping[(arcs[arc].toInt() ushr 3) and 0x1F]
        } else {
            arcs[arc + 1]
        }
    }

    override fun getRightLanguageCount(node: Int): Int {
        check(getFlags().contains(FSAFlags.NUMBERS)) { "This FSA was not compiled with NUMBERS." }
        return FSA5.decodeFromBytes(arcs, node, nodeDataLength)
    }

    override fun isArcFinal(arc: Int): Boolean = (arcs[arc].toInt() and BIT_FINAL_ARC) != 0

    override fun isArcTerminal(arc: Int): Boolean = getDestinationNodeOffset(arc) == 0

    fun isArcLast(arc: Int): Boolean = (arcs[arc].toInt() and BIT_LAST_ARC) != 0

    fun isNextSet(arc: Int): Boolean = (arcs[arc].toInt() and BIT_TARGET_NEXT) != 0

    fun isLabelCompressed(arc: Int): Boolean {
        check(isNextSet(arc)) { "Only applicable to arcs with NEXT bit." }
        return (arcs[arc].toInt() and (-1 shl 3)) != 0
    }

    override fun getFlags(): Set<FSAFlags> = flags

    private fun getDestinationNodeOffset(arc: Int): Int {
        return if (isNextSet(arc)) {
            skipArc(arc)
        } else {
            var r = 0
            for (i in gtl - 1 downTo 1) {
                r = (r shl 8) or (arcs[arc + 1 + i].toInt() and 0xFF)
            }
            r = (r shl 8) or (arcs[arc].toInt() and 0xFF)
            r ushr 3
        }
    }

    private fun skipArc(offset: Int): Int {
        var off = offset
        if (isNextSet(off)) {
            off += if (isLabelCompressed(off)) 1 else 2
        } else {
            off += 1 + gtl
        }
        return off
    }

    private fun readByte(stream: InputStream): Byte {
        val value = stream.read()
        if (value < 0) throw okio.IOException("Unexpected end of stream")
        return value.toByte()
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
