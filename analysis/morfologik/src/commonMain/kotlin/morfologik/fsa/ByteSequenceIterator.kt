package morfologik.fsa

import org.gnit.lucenekmp.jdkport.ByteBuffer
import kotlin.NoSuchElementException

/**
 * An iterator that traverses the right language of a given node (all sequences
 * reachable from a given node).
 */
class ByteSequenceIterator(private val fsa: FSA, node: Int = fsa.getRootNode()) : Iterator<ByteBuffer> {
    private companion object {
        const val EXPECTED_MAX_STATES = 15
    }

    private var nextElement: ByteBuffer? = null
    private var buffer: ByteArray = ByteArray(EXPECTED_MAX_STATES)
    private var bufferWrapper: ByteBuffer = ByteBuffer.wrap(buffer)
    private var arcs: IntArray = IntArray(EXPECTED_MAX_STATES)
    private var position: Int = 0

    init {
        if (fsa.getFirstArc(node) != 0) {
            restartFrom(node)
        }
    }

    fun restartFrom(node: Int): ByteSequenceIterator {
        position = 0
        bufferWrapper.clear()
        nextElement = null
        pushNode(node)
        return this
    }

    override fun hasNext(): Boolean {
        if (nextElement == null) {
            nextElement = advance()
        }
        return nextElement != null
    }

    override fun next(): ByteBuffer {
        val cached = nextElement
        if (cached != null) {
            nextElement = null
            return cached
        }
        val advanced = advance() ?: throw NoSuchElementException()
        return advanced
    }

    private fun advance(): ByteBuffer? {
        if (position == 0) return null

        while (position > 0) {
            val lastIndex = position - 1
            val arc = arcs[lastIndex]

            if (arc == 0) {
                position--
                continue
            }

            arcs[lastIndex] = fsa.getNextArc(arc)

            if (lastIndex >= buffer.size) {
                buffer = buffer.copyOf(buffer.size + EXPECTED_MAX_STATES)
                bufferWrapper = ByteBuffer.wrap(buffer)
            }
            buffer[lastIndex] = fsa.getArcLabel(arc)

            if (!fsa.isArcTerminal(arc)) {
                pushNode(fsa.getEndNode(arc))
            }

            if (fsa.isArcFinal(arc)) {
                bufferWrapper.clear()
                bufferWrapper.limit(lastIndex + 1)
                return bufferWrapper
            }
        }

        return null
    }

    private fun pushNode(node: Int) {
        if (position == arcs.size) {
            arcs = arcs.copyOf(arcs.size + EXPECTED_MAX_STATES)
        }
        arcs[position++] = fsa.getFirstArc(node)
    }
}
