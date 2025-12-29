package morfologik.fsa

import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.InputStream

/**
 * Top abstract class for handling finite state automata.
 */
abstract class FSA : Iterable<org.gnit.lucenekmp.jdkport.ByteBuffer> {
    abstract fun getRootNode(): Int
    abstract fun getFirstArc(node: Int): Int
    abstract fun getNextArc(arc: Int): Int
    abstract fun getArc(node: Int, label: Byte): Int
    abstract fun getArcLabel(arc: Int): Byte
    abstract fun isArcFinal(arc: Int): Boolean
    abstract fun isArcTerminal(arc: Int): Boolean
    abstract fun getEndNode(arc: Int): Int
    abstract fun getFlags(): Set<FSAFlags>

    open fun getArcCount(node: Int): Int {
        var count = 0
        var arc = getFirstArc(node)
        while (arc != 0) {
            count++
            arc = getNextArc(arc)
        }
        return count
    }

    open fun getRightLanguageCount(node: Int): Int {
        throw UnsupportedOperationException("Automaton not compiled with ${FSAFlags.NUMBERS}")
    }

    fun getSequences(node: Int): Iterable<org.gnit.lucenekmp.jdkport.ByteBuffer> {
        if (node == 0) return emptyList()
        return Iterable { ByteSequenceIterator(this, node) }
    }

    fun getSequences(): Iterable<org.gnit.lucenekmp.jdkport.ByteBuffer> = getSequences(getRootNode())

    override fun iterator(): Iterator<org.gnit.lucenekmp.jdkport.ByteBuffer> = getSequences().iterator()

    fun <T : StateVisitor> visitAllStates(v: T): T = visitInPostOrder(v)

    fun <T : StateVisitor> visitInPostOrder(v: T): T = visitInPostOrder(v, getRootNode())

    fun <T : StateVisitor> visitInPostOrder(v: T, node: Int): T {
        visitInPostOrder(v, node, BitSet())
        return v
    }

    private fun visitInPostOrder(v: StateVisitor, node: Int, visited: BitSet): Boolean {
        if (visited.get(node)) return true
        visited.set(node)

        var arc = getFirstArc(node)
        while (arc != 0) {
            if (!isArcTerminal(arc)) {
                if (!visitInPostOrder(v, getEndNode(arc), visited)) {
                    return false
                }
            }
            arc = getNextArc(arc)
        }

        return v.accept(node)
    }

    fun <T : StateVisitor> visitInPreOrder(v: T): T = visitInPreOrder(v, getRootNode())

    fun <T : StateVisitor> visitInPreOrder(v: T, node: Int): T {
        visitInPreOrder(v, node, BitSet())
        return v
    }

    private fun visitInPreOrder(v: StateVisitor, node: Int, visited: BitSet) {
        if (visited.get(node)) return
        visited.set(node)

        if (v.accept(node)) {
            var arc = getFirstArc(node)
            while (arc != 0) {
                if (!isArcTerminal(arc)) {
                    visitInPreOrder(v, getEndNode(arc), visited)
                }
                arc = getNextArc(arc)
            }
        }
    }

    companion object {
        @Throws(okio.IOException::class)
        fun readRemaining(input: InputStream): ByteArray {
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(1024 * 8)
            while (true) {
                val len = input.read(buffer)
                if (len < 0) break
                baos.write(buffer, 0, len)
            }
            return baos.toByteArray()
        }

        @Throws(okio.IOException::class)
        fun read(stream: InputStream): FSA {
            val header = FSAHeader.read(stream)
            return when (header.version) {
                FSA5.VERSION -> FSA5(stream)
                CFSA.VERSION -> CFSA(stream)
                CFSA2.VERSION -> CFSA2(stream)
                else -> {
                    val versionHex = (header.version.toInt() and 0xFF).toString(16).padStart(2, '0')
                    throw okio.IOException(
                        "Unsupported automaton version: 0x$versionHex"
                    )
                }
            }
        }

    }
}
