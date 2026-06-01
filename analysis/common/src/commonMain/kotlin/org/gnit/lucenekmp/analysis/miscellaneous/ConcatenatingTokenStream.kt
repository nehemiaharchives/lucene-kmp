package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.IOUtils
import kotlin.reflect.KClass

/**
 * A TokenStream that takes an array of input TokenStreams as sources, and concatenates them
 * together.
 *
 * <p>Offsets from the second and subsequent sources are incremented to behave as if all the inputs
 * were from a single source.
 *
 * <p>All of the input TokenStreams must have the same attribute implementations
 */
class ConcatenatingTokenStream(vararg sources: TokenStream) : TokenStream(combineSources(*sources)) {
    private val sources: Array<out TokenStream> = sources
    private val sourceOffsets: Array<OffsetAttribute?>
    private val sourceIncrements: Array<PositionIncrementAttribute?>
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)

    private var currentSource = 0
    private var offsetIncrement = 0
    private var initialPositionIncrement = 1

    init {
        this.sourceOffsets = arrayOfNulls(sources.size)
        this.sourceIncrements = arrayOfNulls(sources.size)
        for (i in sources.indices) {
            this.sourceOffsets[i] = sources[i].addAttribute(OffsetAttribute::class)
            this.sourceIncrements[i] = sources[i].addAttribute(PositionIncrementAttribute::class)
        }
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        var newSource = false
        while (!sources[currentSource].incrementToken()) {
            if (currentSource >= sources.size - 1) return false
            sources[currentSource].end()
            initialPositionIncrement = sourceIncrements[currentSource]!!.getPositionIncrement()
            val att = sourceOffsets[currentSource]
            if (att != null) {
                offsetIncrement += att.endOffset()
            }
            currentSource++
            newSource = true
        }

        clearAttributes()
        sources[currentSource].copyTo(this)
        offsetAtt.setOffset(
            offsetAtt.startOffset() + offsetIncrement,
            offsetAtt.endOffset() + offsetIncrement
        )
        if (newSource) {
            val posInc = posIncAtt.getPositionIncrement()
            posIncAtt.setPositionIncrement(posInc + initialPositionIncrement)
        }

        return true
    }

    @Throws(IOException::class)
    override fun end() {
        sources[currentSource].end()
        val finalOffset = sourceOffsets[currentSource]!!.endOffset() + offsetIncrement
        val finalPosInc = sourceIncrements[currentSource]!!.getPositionIncrement()
        super.end()
        offsetAtt.setOffset(finalOffset, finalOffset)
        posIncAtt.setPositionIncrement(finalPosInc)
    }

    @Throws(IOException::class)
    override fun reset() {
        for (source in sources) {
            source.reset()
        }
        super.reset()
        currentSource = 0
        offsetIncrement = 0
    }

    override fun close() {
        try {
            IOUtils.close(sources.asList())
        } finally {
            super.close()
        }
    }

    companion object {
        private fun combineSources(vararg sources: TokenStream): AttributeSource {
            val base = sources[0].cloneAttributes()
            try {
                for (i in 1 until sources.size) {
                    val it: Iterator<Any> = sources[i].attributeClassesIterator
                    while (it.hasNext()) {
                        @Suppress("UNCHECKED_CAST")
                        val attributeClass = it.next() as KClass<out Attribute>
                        val sourceAttribute = sources[i].getAttribute(attributeClass)
                        val baseAttribute = base.addAttribute(attributeClass)
                        require(sourceAttribute == null || sourceAttribute::class == baseAttribute::class) {
                            "Attempted to concatenate TokenStreams with different attribute types"
                        }
                    }
                    sources[i].copyTo(base)
                }
                return base
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Attempted to concatenate TokenStreams with different attribute types",
                    e
                )
            }
        }
    }
}
