package morfologik.fsa

import org.gnit.lucenekmp.jdkport.InputStream

/**
 * FSA binary format implementation for version 5.
 */
class FSA5(stream: InputStream) : FSA() {
    companion object {
        const val DEFAULT_FILLER: Byte = '_'.code.toByte()
        const val DEFAULT_ANNOTATION: Byte = '+'.code.toByte()
        const val VERSION: Byte = 5

        const val BIT_FINAL_ARC: Int = 1 shl 0
        const val BIT_LAST_ARC: Int = 1 shl 1
        const val BIT_TARGET_NEXT: Int = 1 shl 2

        const val ADDRESS_OFFSET: Int = 1

        fun decodeFromBytes(arcs: ByteArray, start: Int, n: Int): Int {
            var r = 0
            for (i in n - 1 downTo 0) {
                r = (r shl 8) or (arcs[start + i].toInt() and 0xFF)
            }
            return r
        }
    }

    val arcs: ByteArray
    val nodeDataLength: Int
    private val flags: Set<FSAFlags>
    val gtl: Int
    val filler: Byte
    val annotation: Byte

    init {
        val fillerByte = readByte(stream)
        val annotationByte = readByte(stream)
        val hgtl = readByte(stream).toInt() and 0xFF

        var tmpFlags = mutableSetOf(FSAFlags.FLEXIBLE, FSAFlags.STOPBIT, FSAFlags.NEXTBIT)
        if ((hgtl and 0xF0) != 0) {
            tmpFlags.add(FSAFlags.NUMBERS)
        }
        flags = tmpFlags.toSet()

        nodeDataLength = (hgtl ushr 4) and 0x0F
        gtl = hgtl and 0x0F

        filler = fillerByte
        annotation = annotationByte

        arcs = readRemaining(stream)
    }

    override fun getRootNode(): Int {
        val epsilonNode = skipArc(getFirstArc(0))
        return getDestinationNodeOffset(getFirstArc(epsilonNode))
    }

    override fun getFirstArc(node: Int): Int = nodeDataLength + node

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
        check(nodeOffset != 0) { "No target node for terminal arcs." }
        return nodeOffset
    }

    override fun getArcLabel(arc: Int): Byte = arcs[arc]

    override fun isArcFinal(arc: Int): Boolean = (arcs[arc + ADDRESS_OFFSET].toInt() and BIT_FINAL_ARC) != 0

    override fun isArcTerminal(arc: Int): Boolean = getDestinationNodeOffset(arc) == 0

    override fun getRightLanguageCount(node: Int): Int {
        check(getFlags().contains(FSAFlags.NUMBERS)) { "This FSA was not compiled with NUMBERS." }
        return decodeFromBytes(arcs, node, nodeDataLength)
    }

    override fun getFlags(): Set<FSAFlags> = flags

    fun isArcLast(arc: Int): Boolean = (arcs[arc + ADDRESS_OFFSET].toInt() and BIT_LAST_ARC) != 0

    fun isNextSet(arc: Int): Boolean = (arcs[arc + ADDRESS_OFFSET].toInt() and BIT_TARGET_NEXT) != 0

    private fun getDestinationNodeOffset(arc: Int): Int {
        return if (isNextSet(arc)) {
            skipArc(arc)
        } else {
            decodeFromBytes(arcs, arc + ADDRESS_OFFSET, gtl) ushr 3
        }
    }

    private fun skipArc(offset: Int): Int {
        return offset + if (isNextSet(offset)) {
            1 + 1
        } else {
            1 + gtl
        }
    }

    private fun readByte(stream: InputStream): Byte {
        val value = stream.read()
        if (value < 0) throw okio.IOException("Unexpected end of stream")
        return value.toByte()
    }
}
