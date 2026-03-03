package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.util.AttributeSource
import kotlin.collections.ArrayDeque

/**
 * An abstract TokenFilter that exposes its input stream as a graph
 *
 * Call [incrementBaseToken] to move the root of the graph to the next position in the
 * TokenStream, [incrementGraphToken] to move along the current graph, and [incrementGraph]
 * to reset to the next graph based at the current root.
 *
 * For example, given the stream 'a b/c:2 d e`, then with the base token at 'a',
 * incrementGraphToken() will produce the stream 'a b d e', and then after calling incrementGraph()
 * will produce the stream 'a c e'.
 */
abstract class GraphTokenFilter(input: TokenStream) : TokenFilter(input) {

    private val tokenPool: ArrayDeque<Token> = ArrayDeque()
    private val currentGraph: MutableList<Token> = mutableListOf()

    /** The maximum permitted number of routes through a graph */
    companion object {
        const val MAX_GRAPH_STACK_SIZE: Int = 1000

        /** The maximum permitted read-ahead in the token stream */
        const val MAX_TOKEN_CACHE_SIZE: Int = 100
    }

    private var baseToken: Token? = null
    private var graphDepth: Int = 0
    private var graphPos: Int = 0
    private var trailingPositions: Int = -1
    private var finalOffsets: Int = -1

    private var stackSize: Int = 0
    private var cacheSize: Int = 0

    private val posIncAtt: PositionIncrementAttribute = input.addAttribute(PositionIncrementAttribute::class)
    private val offsetAtt: OffsetAttribute = input.addAttribute(OffsetAttribute::class)

    /**
     * Move the root of the graph to the next token in the wrapped TokenStream
     *
     * @return `false` if the underlying stream is exhausted
     */
    @Throws(IOException::class)
    fun incrementBaseToken(): Boolean {
        stackSize = 0
        graphDepth = 0
        graphPos = 0
        val oldBase = baseToken
        baseToken = nextTokenInStream(baseToken)
        if (baseToken == null) {
            return false
        }
        currentGraph.clear()
        currentGraph.add(baseToken!!)
        baseToken!!.attSource.copyTo(this)
        recycleToken(oldBase)
        return true
    }

    /**
     * Move to the next token in the current route through the graph
     *
     * @return `false` if there are not more tokens in the current graph
     */
    @Throws(IOException::class)
    fun incrementGraphToken(): Boolean {
        if (graphPos < graphDepth) {
            graphPos++
            currentGraph[graphPos].attSource.copyTo(this)
            return true
        }
        val token = nextTokenInGraph(currentGraph[graphDepth])
        if (token == null) {
            return false
        }
        graphDepth++
        graphPos++
        currentGraph.add(graphDepth, token)
        token.attSource.copyTo(this)
        return true
    }

    /**
     * Reset to the root token again, and move down the next route through the graph
     *
     * @return false if there are no more routes through the graph
     */
    @Throws(IOException::class)
    fun incrementGraph(): Boolean {
        if (baseToken == null) {
            return false
        }
        graphPos = 0
        for (i in graphDepth downTo 1) {
            if (lastInStack(currentGraph[i]) == false) {
                currentGraph[i] = nextTokenInStream(currentGraph[i])!!
                for (j in (i + 1) until graphDepth) {
                    currentGraph[j] = nextTokenInGraph(currentGraph[j])!!
                }
                if (stackSize++ > MAX_GRAPH_STACK_SIZE) {
                    throw IllegalStateException("Too many graph paths (> $MAX_GRAPH_STACK_SIZE)")
                }
                currentGraph[0].attSource.copyTo(this)
                graphDepth = i
                return true
            }
        }
        return false
    }

    /**
     * Return the number of trailing positions at the end of the graph
     *
     * NB this should only be called after [incrementGraphToken] has returned `false`
     */
    fun getTrailingPositions(): Int {
        return trailingPositions
    }

    @Throws(IOException::class)
    override fun end() {
        if (trailingPositions == -1) {
            input.end()
            trailingPositions = posIncAtt.getPositionIncrement()
            finalOffsets = offsetAtt.endOffset()
        } else {
            endAttributes()
            this.posIncAtt.setPositionIncrement(trailingPositions)
            this.offsetAtt.setOffset(finalOffsets, finalOffsets)
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        input.reset()
        // new attributes can be added between reset() calls, so we can't reuse
        // token objects from a previous run
        tokenPool.clear()
        cacheSize = 0
        graphDepth = 0
        trailingPositions = -1
        finalOffsets = -1
        baseToken = null
    }

    fun cachedTokenCount(): Int {
        return cacheSize
    }

    private fun newToken(): Token {
        if (tokenPool.size == 0) {
            cacheSize++
            if (cacheSize > MAX_TOKEN_CACHE_SIZE) {
                throw IllegalStateException("Too many cached tokens (> $MAX_TOKEN_CACHE_SIZE)")
            }
            return Token(this.cloneAttributes())
        }
        val token = tokenPool.removeFirst()
        token.reset(input)
        return token
    }

    private fun recycleToken(token: Token?) {
        if (token == null) {
            return
        }
        token.nextToken = null
        tokenPool.add(token)
    }

    @Throws(IOException::class)
    private fun nextTokenInGraph(token: Token): Token? {
        var tokenVar: Token? = token
        var remaining = token.length()
        do {
            tokenVar = nextTokenInStream(tokenVar)
            if (tokenVar == null) {
                return null
            }
            remaining -= tokenVar.posInc()
        } while (remaining > 0)
        return tokenVar
    }

    // check if the next token in the tokenstream is at the same position as this one
    @Throws(IOException::class)
    private fun lastInStack(token: Token): Boolean {
        val next = nextTokenInStream(token)
        return next == null || next.posInc() != 0
    }

    @Throws(IOException::class)
    private fun nextTokenInStream(token: Token?): Token? {
        if (token != null && token.nextToken != null) {
            return token.nextToken
        }
        if (this.trailingPositions != -1) {
            // already hit the end
            return null
        }
        if (input.incrementToken() == false) {
            input.end()
            trailingPositions = posIncAtt.getPositionIncrement()
            finalOffsets = offsetAtt.endOffset()
            return null
        }
        if (token == null) {
            return newToken()
        }
        token.nextToken = newToken()
        return token.nextToken
    }

    private class Token(val attSource: AttributeSource) {

        val posIncAtt: PositionIncrementAttribute = attSource.addAttribute(PositionIncrementAttribute::class)
        val lengthAtt: PositionLengthAttribute? = if (attSource.hasAttribute(PositionLengthAttribute::class)) {
            attSource.addAttribute(PositionLengthAttribute::class)
        } else {
            null
        }
        var nextToken: Token? = null

        fun posInc(): Int {
            return this.posIncAtt.getPositionIncrement()
        }

        fun length(): Int {
            if (this.lengthAtt == null) {
                return 1
            }
            return this.lengthAtt.positionLength
        }

        fun reset(attSource: AttributeSource) {
            attSource.copyTo(this.attSource)
            this.nextToken = null
        }

        override fun toString(): String {
            return attSource.toString()
        }
    }
}
