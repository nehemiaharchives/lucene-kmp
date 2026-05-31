package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.RollingBuffer
import kotlin.math.max

/**
 * Converts an incoming graph token stream, such as one from [SynonymGraphFilter], into a flat
 * form so that all nodes form a single linear chain with no side paths.
 *
 * @lucene.experimental
 */
class FlattenGraphFilter(input: TokenStream) : TokenFilter(input) {

    /** Holds all tokens leaving a given input position. */
    private class InputNode : RollingBuffer.Resettable {
        val tokens: MutableList<AttributeSource.State> = mutableListOf()

        /** Our input node, or -1 if we haven't been assigned yet */
        var node: Int = -1

        /** Maximum to input node for all tokens leaving here. */
        var maxToNode: Int = -1

        /** Minimum to input node for all tokens leaving here. */
        var minToNode: Int = Int.MAX_VALUE

        /** Where we currently map to; this changes until we are finished with this position. */
        var outputNode: Int = -1

        /** Which token (index into [tokens]) we will next output. */
        var nextOut: Int = 0

        override fun reset() {
            tokens.clear()
            node = -1
            outputNode = -1
            maxToNode = -1
            minToNode = Int.MAX_VALUE
            nextOut = 0
        }
    }

    /**
     * Gathers merged input positions into a single output position, only for the current
     * "frontier" of nodes we've seen but can't yet output because they are not frozen.
     */
    private class OutputNode : RollingBuffer.Resettable {
        val inputNodes: IntArrayList = IntArrayList()

        /** Node ID for this output, or -1 if we haven't been assigned yet. */
        var node: Int = -1

        /** Which input node (index into [inputNodes]) we will next output. */
        var nextOut: Int = 0

        /** Start offset of tokens leaving this node. */
        var startOffset: Int = -1

        /** End offset of tokens arriving to this node. */
        var endOffset: Int = -1

        override fun reset() {
            inputNodes.clear()
            node = -1
            nextOut = 0
            startOffset = -1
            endOffset = -1
        }
    }

    private val inputNodes = object : RollingBuffer<InputNode>() {
        override fun newInstance(): InputNode = InputNode()
    }

    private val outputNodes = object : RollingBuffer<OutputNode>() {
        override fun newInstance(): OutputNode = OutputNode()
    }

    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLenAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    /** Which input node the last seen token leaves from */
    private var inputFrom: Int = 0

    /** We are currently releasing tokens leaving from this output node */
    private var outputFrom: Int = 0

    private var done: Boolean = false
    private var lastOutputFrom: Int = 0
    private var finalOffset: Int = 0
    private var finalPosInc: Int = 0
    private var maxLookaheadUsed: Int = 0
    private var lastStartOffset: Int = 0

    private fun releaseBufferedToken(): Boolean {
        while (outputFrom < outputNodes.getMaxPos()) {
            val output = outputNodes.get(outputFrom)
            if (output.inputNodes.isEmpty) {
                outputFrom++
                continue
            }

            var maxToNode = -1
            for (inputNodeID in output.inputNodes) {
                val inputNode = inputNodes.get(inputNodeID.value)
                assert(inputNode.outputNode == outputFrom)
                maxToNode = max(maxToNode, inputNode.maxToNode)
            }

            if (maxToNode <= inputFrom || done) {
                assert(output.nextOut < output.inputNodes.size()) {
                    "output.nextOut=${output.nextOut} vs output.inputNodes.size()=${output.inputNodes.size()}"
                }
                val inputNode = inputNodes.get(output.inputNodes.get(output.nextOut))
                if (done && inputNode.tokens.isEmpty() && outputFrom >= outputNodes.getMaxPos()) {
                    return false
                }
                if (inputNode.tokens.isEmpty()) {
                    assert(inputNode.nextOut == 0)
                    if (output.inputNodes.size() > 1) {
                        output.nextOut++
                        if (output.nextOut < output.inputNodes.size()) {
                            continue
                        }
                    }
                    freeBefore(output)
                    continue
                }

                assert(inputNode.nextOut < inputNode.tokens.size)

                restoreState(inputNode.tokens[inputNode.nextOut])

                assert(outputFrom >= lastOutputFrom)
                posIncAtt.setPositionIncrement(outputFrom - lastOutputFrom)
                val toInputNodeID = inputNode.node + posLenAtt.positionLength
                val toInputNode = inputNodes.get(toInputNodeID)

                assert(toInputNode.outputNode > outputFrom)
                posLenAtt.positionLength = toInputNode.outputNode - outputFrom
                lastOutputFrom = outputFrom
                inputNode.nextOut++

                val outputEndNode = outputNodes.get(toInputNode.outputNode)
                val startOffset = max(lastStartOffset, output.startOffset)
                val endOffset = max(startOffset, outputEndNode.endOffset)
                offsetAtt.setOffset(startOffset, endOffset)
                lastStartOffset = startOffset

                if (inputNode.nextOut == inputNode.tokens.size) {
                    output.nextOut++
                    if (output.nextOut == output.inputNodes.size()) {
                        freeBefore(output)
                    }
                }

                return true
            } else {
                return false
            }
        }
        return false
    }

    /**
     * Free input nodes before the minimum input node for the given output.
     */
    private fun freeBefore(output: OutputNode) {
        outputFrom++
        val freeBefore = output.inputNodes.stream().minOrNull()!!
        assert(outputNodes.get(outputFrom).inputNodes.stream().none { freeBefore > it }) {
            "FreeBefore $freeBefore will free in use nodes"
        }
        inputNodes.freeBefore(freeBefore)
        outputNodes.freeBefore(outputFrom)
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        while (true) {
            if (releaseBufferedToken()) {
                return true
            } else if (done) {
                return false
            }

            if (input.incrementToken()) {
                val positionIncrement = posIncAtt.getPositionIncrement()
                inputFrom += positionIncrement

                val startOffset = offsetAtt.startOffset()
                val endOffset = offsetAtt.endOffset()
                val inputTo = inputFrom + posLenAtt.positionLength

                val src = inputNodes.get(inputFrom)
                if (src.node == -1) {
                    recoverFromHole(src, startOffset, positionIncrement)
                } else {
                    var outSrc = outputNodes.get(src.outputNode)
                    if (positionIncrement > 1
                        && src.outputNode - inputNodes.get(inputFrom - positionIncrement).outputNode <= 1
                        && inputNodes.get(inputFrom - positionIncrement).minToNode != inputFrom
                    ) {
                        assert(inputNodes.get(inputFrom).tokens.isEmpty()) { "about to remove non empty edge" }
                        outSrc.inputNodes.removeElement(inputFrom)
                        src.outputNode = -1
                        val prevEndOffset = outSrc.endOffset
                        outSrc = recoverFromHole(src, startOffset, positionIncrement)
                        outSrc.endOffset = prevEndOffset
                    }

                    if (outSrc.startOffset == -1 || startOffset > outSrc.startOffset) {
                        outSrc.startOffset = max(startOffset, outSrc.startOffset)
                    }
                }

                src.tokens.add(requireNotNull(captureState()))
                src.maxToNode = max(src.maxToNode, inputTo)
                src.minToNode = minOf(src.minToNode, inputTo)
                maxLookaheadUsed = max(maxLookaheadUsed, inputNodes.getBufferSize())

                val dest = inputNodes.get(inputTo)
                if (dest.node == -1) {
                    dest.node = inputTo
                }

                val outputEndNode = src.outputNode + 1
                if (outputEndNode > dest.outputNode) {
                    if (dest.outputNode != -1) {
                        val removed = outputNodes.get(dest.outputNode).inputNodes.removeElement(inputTo)
                        check(removed)
                    }
                    outputNodes.get(outputEndNode).inputNodes.add(inputTo)
                    dest.outputNode = outputEndNode
                    assert(outputEndNode <= inputTo) {
                        "outputEndNode=$outputEndNode vs inputTo=$inputTo"
                    }
                }

                val outDest = outputNodes.get(dest.outputNode)
                if (outDest.endOffset == -1 || endOffset < outDest.endOffset) {
                    outDest.endOffset = endOffset
                }
            } else {
                input.end()
                finalPosInc = posIncAtt.getPositionIncrement()
                finalOffset = offsetAtt.endOffset()
                done = true
            }
        }
    }

    private fun recoverFromHole(src: InputNode, startOffset: Int, posinc: Int): OutputNode {
        assert(src.outputNode == -1)
        src.node = inputFrom

        val outIndex: Int
        val previousInputFrom = inputFrom - posinc
        if (previousInputFrom >= 0) {
            val offsetSrc = inputNodes.get(previousInputFrom)
            outIndex = if (offsetSrc.minToNode < inputFrom) {
                inputNodes.get(offsetSrc.minToNode).outputNode + 1
            } else {
                outputNodes.getMaxPos()
            }
        } else {
            outIndex = outputNodes.getMaxPos() + 1
        }
        val outSrc = outputNodes.get(outIndex)
        src.outputNode = outIndex

        if (outSrc.node == -1) {
            outSrc.node = src.outputNode
            outSrc.startOffset = startOffset
        } else {
            outSrc.startOffset = max(startOffset, outSrc.startOffset)
        }
        outSrc.inputNodes.add(inputFrom)
        return outSrc
    }

    @Throws(IOException::class)
    override fun end() {
        if (!done) {
            super.end()
        }

        clearAttributes()
        if (done) {
            posIncAtt.setPositionIncrement(finalPosInc)
            offsetAtt.setOffset(finalOffset, finalOffset)
        } else {
            super.end()
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        inputFrom = -1
        inputNodes.reset()
        val inNode = inputNodes.get(0)
        inNode.node = 0
        inNode.outputNode = 0

        outputNodes.reset()
        val outNode = outputNodes.get(0)
        outNode.node = 0
        outNode.inputNodes.add(0)
        outNode.startOffset = 0
        outputFrom = 0
        lastOutputFrom = -1
        done = false
        finalPosInc = -1
        finalOffset = -1
        lastStartOffset = 0
        maxLookaheadUsed = 0
    }

    /** For testing */
    fun getMaxLookaheadUsed(): Int = maxLookaheadUsed
}

