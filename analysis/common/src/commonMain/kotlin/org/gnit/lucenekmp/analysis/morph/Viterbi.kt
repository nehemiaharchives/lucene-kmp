package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.analysis.util.RollingCharBuffer
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.fst.FST
import okio.IOException

/**
 * Performs Viterbi algorithm for morphological Tokenizers.
 */
abstract class Viterbi<T : Token, U : Viterbi.Position>(
    private val fst: TokenInfoFST,
    private val fstReader: FST.BytesReader,
    private val dictionary: BinaryDictionary<out MorphData>,
    private val userFST: TokenInfoFST?,
    private val userFSTReader: FST.BytesReader?,
    private val userDictionary: Dictionary<out MorphData>?,
    protected val costs: ConnectionCosts,
    positionFactory: () -> U
) {
    companion object {
        protected const val VERBOSE = false
        protected const val MAX_UNKNOWN_WORD_LENGTH = 1024
        private const val MAX_BACKTRACE_GAP = 1024
    }

    private val arc: FST.Arc<Long> = FST.Arc()
    protected val wordIdRef: IntsRef = IntsRef()

    protected val buffer: RollingCharBuffer = RollingCharBuffer()
    protected val positions: WrappedPositionArray<U> = WrappedPositionArray(positionFactory)

    var end: Boolean = false
    protected var lastBackTracePos: Int = 0
    var pos: Int = 0

    val pending: MutableList<T> = ArrayList()

    protected var outputNBest: Boolean = false
    protected var enableSpacePenaltyFactor: Boolean = false
    protected var outputLongestUserEntryOnly: Boolean = false

    @Throws(IOException::class)
    fun forward() {
        if (VERBOSE) {
            println("\nPARSE")
        }

        var unknownWordEndIndex = -1
        var userWordMaxPosAhead = -1

        while (buffer.get(pos) != -1) {
            val posData = positions.get(pos)
            val isFrontier = positions.getNextPos() == pos + 1

            if (posData.count == 0) {
                pos++
                continue
            }

            if (pos > lastBackTracePos && posData.count == 1 && isFrontier) {
                if (outputNBest) {
                    backtraceNBest(posData, false)
                }
                backtrace(posData, 0)
                if (outputNBest) {
                    fixupPendingList()
                }
                posData.costs.fill(0, 0, posData.count)
                if (pending.isNotEmpty()) {
                    return
                }
            }

            if (pos - lastBackTracePos >= MAX_BACKTRACE_GAP) {
                var leastIDX = -1
                var leastCost = Int.MAX_VALUE
                var leastPosData: Position? = null
                for (pos2 in pos until positions.getNextPos()) {
                    val posData2 = positions.get(pos2)
                    for (idx in 0 until posData2.count) {
                        val cost = posData2.costs[idx]
                        if (cost < leastCost) {
                            leastCost = cost
                            leastIDX = idx
                            leastPosData = posData2
                        }
                    }
                }

                if (leastIDX == -1 || leastPosData == null) {
                    throw IllegalStateException("No live path found")
                }

                if (outputNBest) {
                    backtraceNBest(leastPosData, false)
                }

                for (pos2 in pos until positions.getNextPos()) {
                    val posData2 = positions.get(pos2)
                    if (posData2 !== leastPosData) {
                        posData2.reset()
                    } else {
                        if (leastIDX != 0) {
                            posData2.costs[0] = posData2.costs[leastIDX]
                            posData2.lastRightID[0] = posData2.lastRightID[leastIDX]
                            posData2.backPos[0] = posData2.backPos[leastIDX]
                            posData2.backWordPos[0] = posData2.backWordPos[leastIDX]
                            posData2.backIndex[0] = posData2.backIndex[leastIDX]
                            posData2.backID[0] = posData2.backID[leastIDX]
                            posData2.backType[0] = posData2.backType[leastIDX]
                        }
                        posData2.count = 1
                    }
                }

                backtrace(leastPosData, 0)
                if (outputNBest) {
                    fixupPendingList()
                }

                Arrays.fill(leastPosData.costs, 0, leastPosData.count, 0)

                if (pos != leastPosData.pos) {
                    pos = leastPosData.pos
                }
                if (pending.isNotEmpty()) {
                    return
                } else {
                    continue
                }
            }

            if (enableSpacePenaltyFactor && Character.getType(buffer.get(pos)) == Character.SPACE_SEPARATOR.toInt()) {
                if (buffer.get(++pos) == -1) {
                    pos = posData.pos
                }
            }

            var anyMatches = false

            if (userFST != null && userFSTReader != null && userDictionary != null) {
                userFST.getFirstArc(arc)
                var output = 0L
                var maxPosAhead = 0
                var outputMaxPosAhead = 0L
                var arcFinalOutMaxPosAhead = 0L

                var posAhead = pos
                while (true) {
                    val ch = buffer.get(posAhead)
                    if (ch == -1) break
                    if (userFST.findTargetArc(ch, arc, arc, posAhead == pos, userFSTReader) == null) {
                        break
                    }
                    output += arc.output() ?: 0L
                    if (arc.isFinal) {
                        maxPosAhead = posAhead
                        outputMaxPosAhead = output
                        arcFinalOutMaxPosAhead = arc.nextFinalOutput() ?: 0L
                        anyMatches = true
                        if (!outputLongestUserEntryOnly) {
                            add(
                                userDictionary.getMorphAttributes(),
                                posData,
                                pos,
                                posAhead + 1,
                                (output + (arc.nextFinalOutput() ?: 0L)).toInt(),
                                TokenType.USER,
                                false
                            )
                        }
                    }
                    posAhead++
                }

                if (anyMatches && maxPosAhead > userWordMaxPosAhead) {
                    if (outputLongestUserEntryOnly) {
                        add(
                            userDictionary.getMorphAttributes(),
                            posData,
                            pos,
                            maxPosAhead + 1,
                            (outputMaxPosAhead + arcFinalOutMaxPosAhead).toInt(),
                            TokenType.USER,
                            false
                        )
                    }
                    userWordMaxPosAhead = maxOf(userWordMaxPosAhead, maxPosAhead)
                }
            }

            if (!anyMatches) {
                fst.getFirstArc(arc)
                var output = 0L
                var posAhead = pos
                while (true) {
                    val ch = buffer.get(posAhead)
                    if (ch == -1) break
                    if (fst.findTargetArc(ch, arc, arc, posAhead == pos, fstReader) == null) {
                        break
                    }
                    output += arc.output() ?: 0L
                    if (arc.isFinal) {
                        dictionary.lookupWordIds((output + (arc.nextFinalOutput() ?: 0L)).toInt(), wordIdRef)
                        for (ofs in 0 until wordIdRef.length) {
                            add(
                                dictionary.getMorphAttributes(),
                                posData,
                                pos,
                                posAhead + 1,
                                wordIdRef.ints[wordIdRef.offset + ofs],
                                TokenType.KNOWN,
                                false
                            )
                            anyMatches = true
                        }
                    }
                    posAhead++
                }
            }

            if (!shouldSkipProcessUnknownWord(unknownWordEndIndex, posData)) {
                val unknownWordLength = processUnknownWord(anyMatches, posData)
                unknownWordEndIndex = posData.pos + unknownWordLength
            }
            pos++
        }

        end = true

        if (pos > 0) {
            val endPosData = positions.get(pos)
            var leastCost = Int.MAX_VALUE
            var leastIDX = -1
            for (idx in 0 until endPosData.count) {
                val cost = endPosData.costs[idx] + costs.get(endPosData.lastRightID[idx], 0)
                if (cost < leastCost) {
                    leastCost = cost
                    leastIDX = idx
                }
            }

            if (leastIDX == -1) {
                // No path to EOS; return no tokens for this input.
                return
            }

            if (outputNBest) {
                backtraceNBest(endPosData, true)
            }
            backtrace(endPosData, leastIDX)
            if (outputNBest) {
                fixupPendingList()
            }
        }
    }

    protected open fun shouldSkipProcessUnknownWord(unknownWordEndIndex: Int, posData: Position): Boolean {
        return unknownWordEndIndex > posData.pos
    }

    @Throws(IOException::class)
    protected abstract fun processUnknownWord(anyMatches: Boolean, posData: Position): Int

    @Throws(IOException::class)
    protected abstract fun backtrace(endPosData: Position, fromIDX: Int)

    @Throws(IOException::class)
    protected open fun backtraceNBest(endPosData: Position, useEOS: Boolean) {
        throw UnsupportedOperationException()
    }

    protected open fun fixupPendingList() {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    protected fun add(
        morphData: MorphData,
        fromPosData: Position,
        wordPos: Int,
        endPos: Int,
        wordID: Int,
        type: TokenType,
        addPenalty: Boolean
    ) {
        val wordCost = morphData.getWordCost(wordID)
        val leftID = morphData.getLeftId(wordID)
        var leastCost = Int.MAX_VALUE
        var leastIDX = -1
        for (idx in 0 until fromPosData.count) {
            val numSpaces = wordPos - fromPosData.pos
            val cost =
                fromPosData.costs[idx] +
                    costs.get(fromPosData.lastRightID[idx], leftID) +
                    computeSpacePenalty(morphData, wordID, numSpaces)
            if (cost < leastCost) {
                leastCost = cost
                leastIDX = idx
            }
        }

        leastCost += wordCost

        if (addPenalty && type != TokenType.USER) {
            val penalty = computePenalty(fromPosData.pos, endPos - fromPosData.pos)
            leastCost += penalty
        }

        positions.get(endPos).add(
            leastCost,
            morphData.getRightId(wordID),
            fromPosData.pos,
            wordPos,
            leastIDX,
            wordID,
            type
        )
    }

    protected open fun computeSpacePenalty(morphData: MorphData, wordID: Int, numSpaces: Int): Int = 0

    @Throws(IOException::class)
    protected open fun computePenalty(pos: Int, length: Int): Int = 0

    open fun isEnd(): Boolean = end

    open fun isOutputNBest(): Boolean = outputNBest

    fun resetBuffer(reader: Reader) {
        buffer.reset(reader)
    }

    fun resetState() {
        positions.reset()
        pos = 0
        end = false
        lastBackTracePos = 0
        pending.clear()

        positions.get(0).add(0, 0, -1, -1, -1, -1, TokenType.KNOWN)
    }

    open class Position {
        var pos: Int = 0
        var count: Int = 0
        var costs: IntArray = IntArray(8)
        var lastRightID: IntArray = IntArray(8)
        var backPos: IntArray = IntArray(8)
        var backWordPos: IntArray = IntArray(8)
        var backIndex: IntArray = IntArray(8)
        var backID: IntArray = IntArray(8)
        var backType: Array<TokenType> = Array(8) { TokenType.KNOWN }

        private fun grow() {
            costs = ArrayUtil.grow(costs, 1 + count)
            lastRightID = ArrayUtil.grow(lastRightID, 1 + count)
            backPos = ArrayUtil.grow(backPos, 1 + count)
            backWordPos = ArrayUtil.grow(backWordPos, 1 + count)
            backIndex = ArrayUtil.grow(backIndex, 1 + count)
            backID = ArrayUtil.grow(backID, 1 + count)

            val newBackType = Array(backID.size) { TokenType.KNOWN }
            for (i in backType.indices) {
                newBackType[i] = backType[i]
            }
            backType = newBackType
        }

        fun add(
            cost: Int,
            lastRightID: Int,
            backPos: Int,
            backRPos: Int,
            backIndex: Int,
            backID: Int,
            backType: TokenType
        ) {
            if (count == costs.size) {
                grow()
            }
            this.costs[count] = cost
            this.lastRightID[count] = lastRightID
            this.backPos[count] = backPos
            this.backWordPos[count] = backRPos
            this.backIndex[count] = backIndex
            this.backID[count] = backID
            this.backType[count] = backType
            count++
        }

        open fun reset() {
            count = 0
        }

    }

    class WrappedPositionArray<U : Position>(private val positionFactory: () -> U) {
        private var positions: Array<U?> = arrayOfNulls<Position>(8) as Array<U?>
        private var nextWrite = 0
        private var nextPos = 0
        private var count = 0

        init {
            for (i in positions.indices) {
                positions[i] = positionFactory()
            }
        }

        fun reset() {
            nextWrite--
            while (count > 0) {
                if (nextWrite == -1) {
                    nextWrite = positions.size - 1
                }
                positions[nextWrite]?.reset()
                count--
                nextWrite--
            }
            nextWrite = 0
            nextPos = 0
            count = 0
        }

        fun get(pos: Int): U {
            while (pos >= nextPos) {
                if (count == positions.size) {
                    val newSize = ArrayUtil.oversize(1 + count, RamUsageEstimator.NUM_BYTES_OBJECT_REF)
                    val newPositions: Array<U?> = arrayOfNulls<Position>(newSize) as Array<U?>
                    val tail = positions.size - nextWrite
                    for (i in 0 until tail) {
                        newPositions[i] = positions[nextWrite + i]
                    }
                    for (i in 0 until nextWrite) {
                        newPositions[tail + i] = positions[i]
                    }
                    for (i in positions.size until newPositions.size) {
                        newPositions[i] = positionFactory()
                    }
                    nextWrite = positions.size
                    positions = newPositions
                }
                if (nextWrite == positions.size) {
                    nextWrite = 0
                }
                positions[nextWrite]!!.pos = nextPos++
                positions[nextWrite]!!.count = 0
                nextWrite++
                count++
            }
            val index = getIndex(pos)
            return positions[index]!!
        }

        fun getNextPos(): Int = nextPos

        private fun getIndex(pos: Int): Int {
            var index = nextWrite - (nextPos - pos)
            if (index < 0) {
                index += positions.size
            }
            return index
        }

        fun freeBefore(pos: Int) {
            val toFree = count - (nextPos - pos)
            var index = nextWrite - count
            if (index < 0) {
                index += positions.size
            }
            for (i in 0 until toFree) {
                if (index == positions.size) {
                    index = 0
                }
                positions[index]?.reset()
                index++
            }
            count -= toFree
        }
    }
}
