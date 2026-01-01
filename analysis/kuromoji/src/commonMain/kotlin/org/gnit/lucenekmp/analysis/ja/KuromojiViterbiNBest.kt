package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.ja.dict.CharacterDefinition
import org.gnit.lucenekmp.analysis.ja.dict.JaMorphData
import org.gnit.lucenekmp.analysis.ja.dict.TokenInfoDictionary
import org.gnit.lucenekmp.analysis.ja.dict.UnknownDictionary
import org.gnit.lucenekmp.analysis.ja.dict.UserDictionary
import org.gnit.lucenekmp.analysis.morph.ConnectionCosts
import org.gnit.lucenekmp.analysis.morph.Dictionary
import org.gnit.lucenekmp.analysis.morph.GraphvizFormatter
import org.gnit.lucenekmp.analysis.morph.TokenInfoFST
import org.gnit.lucenekmp.analysis.morph.TokenType
import org.gnit.lucenekmp.analysis.morph.ViterbiNBest
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.fst.FST

/** Kuromoji-specific ViterbiNBest implementation. */
internal class KuromojiViterbiNBest(
    fst: TokenInfoFST,
    fstReader: FST.BytesReader,
    dictionary: TokenInfoDictionary,
    userFST: TokenInfoFST?,
    userFSTReader: FST.BytesReader?,
    private val userDictionary: UserDictionary?,
    costs: ConnectionCosts,
    private val unkDictionary: UnknownDictionary,
    private val characterDefinition: CharacterDefinition,
    private val discardPunctuation: Boolean,
    private val searchMode: Boolean,
    private val extendedMode: Boolean,
    private val outputCompounds: Boolean
) : ViterbiNBest<Token, JaMorphData>(
    fst,
    fstReader,
    dictionary,
    userFST,
    userFSTReader,
    userDictionary,
    costs
) {

    private val SEARCH_MODE_KANJI_LENGTH = 2
    private val SEARCH_MODE_OTHER_LENGTH = 7
    private val SEARCH_MODE_KANJI_PENALTY = 3000
    private val SEARCH_MODE_OTHER_PENALTY = 1700

    private val wordIdRefLocal = IntsRef()
    private var dotOut: GraphvizFormatter<JaMorphData>? = null

    init {
        @Suppress("UNCHECKED_CAST")
        dictionaryMap[TokenType.KNOWN] = dictionary as Dictionary<out JaMorphData>
        @Suppress("UNCHECKED_CAST")
        dictionaryMap[TokenType.UNKNOWN] = unkDictionary as Dictionary<out JaMorphData>
        @Suppress("UNCHECKED_CAST")
        if (userDictionary != null) {
            dictionaryMap[TokenType.USER] = userDictionary as Dictionary<out JaMorphData>
        }
    }

    override fun shouldSkipProcessUnknownWord(unknownWordEndIndex: Int, posData: Position): Boolean {
        return !searchMode && super.shouldSkipProcessUnknownWord(unknownWordEndIndex, posData)
    }

    @Throws(IOException::class)
    override fun computePenalty(pos: Int, length: Int): Int {
        if (length > SEARCH_MODE_KANJI_LENGTH) {
            var allKanji = true
            val endPos = pos + length
            for (pos2 in pos until endPos) {
                if (!characterDefinition.isKanji(buffer.get(pos2).toChar())) {
                    allKanji = false
                    break
                }
            }
            if (allKanji) {
                return (length - SEARCH_MODE_KANJI_LENGTH) * SEARCH_MODE_KANJI_PENALTY
            } else if (length > SEARCH_MODE_OTHER_LENGTH) {
                return (length - SEARCH_MODE_OTHER_LENGTH) * SEARCH_MODE_OTHER_PENALTY
            }
        }
        return 0
    }

    @Throws(IOException::class)
    private fun computeSecondBestThreshold(pos: Int, length: Int): Int {
        // Same strategy as upstream: use the penalty as the allowed extra cost.
        return computePenalty(pos, length)
    }

    @Throws(IOException::class)
    override fun processUnknownWord(anyMatches: Boolean, posData: Position): Int {
        val firstCharacter = buffer.get(pos).toChar()
        if (!anyMatches || characterDefinition.isInvoke(firstCharacter)) {
            val characterId = characterDefinition.getCharacterClass(firstCharacter)
            val isPunct = isPunctuation(firstCharacter)

            val unknownWordLength: Int = if (!characterDefinition.isGroup(firstCharacter)) {
                1
            } else {
                var unknownWordLength = 1
                var posAhead = pos + 1
                while (unknownWordLength < MAX_UNKNOWN_WORD_LENGTH) {
                    val ch = buffer.get(posAhead)
                    if (ch == -1) break
                    val c = ch.toChar()
                    if (characterId == characterDefinition.getCharacterClass(c) && isPunctuation(c) == isPunct) {
                        unknownWordLength++
                    } else {
                        break
                    }
                    posAhead++
                }
                unknownWordLength
            }

            unkDictionary.lookupWordIds(characterId.toInt(), wordIdRefLocal)
            for (ofs in 0 until wordIdRefLocal.length) {
                add(
                    unkDictionary.getMorphAttributes(),
                    posData,
                    pos,
                    posData.pos + unknownWordLength,
                    wordIdRefLocal.ints[wordIdRefLocal.offset + ofs],
                    TokenType.UNKNOWN,
                    false
                )
            }

            return unknownWordLength
        }
        return 0
    }

    @Throws(IOException::class)
    override fun backtrace(endPosData: Position, fromIDX: Int) {
        val endPos = endPosData.pos
        if (endPos == lastBackTracePos) {
            return
        }

        val fragment = buffer.get(lastBackTracePos, endPos - lastBackTracePos)
        dotOut?.onBacktrace(
            { type -> getDict(type) },
            positions,
            lastBackTracePos,
            endPosData,
            fromIDX,
            fragment,
            end
        )

        var pos = endPos
        var bestIDX = fromIDX
        var altToken: Token? = null

        var lastLeftWordID = -1
        var backCount = 0

        while (pos > lastBackTracePos) {
            val posData = positions.get(pos)

            var backPos = posData.backPos[bestIDX]
            var length = pos - backPos
            var backType = posData.backType[bestIDX]
            var backID = posData.backID[bestIDX]
            var nextBestIDX = posData.backIndex[bestIDX]

            if (searchMode && altToken == null && backType != TokenType.USER) {
                val penalty = computeSecondBestThreshold(backPos, pos - backPos)
                if (penalty > 0) {
                    var maxCost = posData.costs[bestIDX] + penalty
                    if (lastLeftWordID != -1) {
                        maxCost += costs.get(getDict(backType).getRightId(backID), lastLeftWordID)
                    }

                    pruneAndRescore(backPos, pos, posData.backIndex[bestIDX])

                    var leastCost = Int.MAX_VALUE
                    var leastIDX = -1
                    for (idx in 0 until posData.count) {
                        var cost = posData.costs[idx]
                        if (lastLeftWordID != -1) {
                            cost += costs.get(
                                getDict(posData.backType[idx]).getRightId(posData.backID[idx]),
                                lastLeftWordID
                            )
                        }
                        if (cost < leastCost) {
                            leastCost = cost
                            leastIDX = idx
                        }
                    }

                    if (leastIDX != -1 && leastCost <= maxCost && posData.backPos[leastIDX] != backPos) {
                        altToken = Token(
                            fragment,
                            backPos - lastBackTracePos,
                            length,
                            backPos,
                            backPos + length,
                            backID,
                            backType,
                            getDict(backType).getMorphAttributes()
                        )

                        bestIDX = leastIDX
                        nextBestIDX = posData.backIndex[bestIDX]

                        backPos = posData.backPos[bestIDX]
                        length = pos - backPos
                        backType = posData.backType[bestIDX]
                        backID = posData.backID[bestIDX]
                        backCount = 0
                    }
                }
            }

            val offset = backPos - lastBackTracePos

            if (altToken != null && altToken.startOffset >= backPos) {
                if (outputCompounds) {
                    if (backCount > 0) {
                        backCount++
                        altToken.positionLength = backCount
                        pending.add(altToken)
                    } else {
                        // all punctuation compound
                    }
                }
                altToken = null
            }

            val dict = getDict(backType)

            if (backType == TokenType.USER) {
                val ud = userDictionary ?: error("userDictionary is null")
                val wordIDAndLength = ud.lookupSegmentation(backID)
                val wordID = wordIDAndLength[0]
                var current = 0
                for (j in 1 until wordIDAndLength.size) {
                    val len = wordIDAndLength[j]
                    val startOffset = current + backPos
                    pending.add(
                        Token(
                            fragment,
                            current + offset,
                            len,
                            startOffset,
                            startOffset + len,
                            wordID + j - 1,
                            TokenType.USER,
                            dict.getMorphAttributes()
                        )
                    )
                    current += len
                }
                // reverse the slice we just added
                pending.subList(pending.size - (wordIDAndLength.size - 1), pending.size).reverse()
                backCount += wordIDAndLength.size - 1
            } else {
                if (extendedMode && backType == TokenType.UNKNOWN) {
                    var unigramTokenCount = 0
                    var i = length - 1
                    while (i >= 0) {
                        var charLen = 1
                        if (i > 0 && fragment[offset + i].isLowSurrogate()) {
                            i--
                            charLen = 2
                        }
                        if (!discardPunctuation || !isPunctuation(fragment[offset + i])) {
                            val startOffset = backPos + i
                            pending.add(
                                Token(
                                    fragment,
                                    offset + i,
                                    charLen,
                                    startOffset,
                                    startOffset + charLen,
                                    CharacterDefinition.NGRAM.toInt(),
                                    TokenType.UNKNOWN,
                                    unkDictionary.getMorphAttributes()
                                )
                            )
                            unigramTokenCount++
                        }
                        i--
                    }
                    backCount += unigramTokenCount
                } else if (!discardPunctuation || length == 0 || !isPunctuation(fragment[offset])) {
                    pending.add(
                        Token(
                            fragment,
                            offset,
                            length,
                            backPos,
                            backPos + length,
                            backID,
                            backType,
                            dict.getMorphAttributes()
                        )
                    )
                    backCount++
                }
            }

            lastLeftWordID = dict.getLeftId(backID)
            pos = backPos
            bestIDX = nextBestIDX
        }

        lastBackTracePos = endPos
        buffer.freeBefore(endPos)
        positions.freeBefore(endPos)
    }

    @Throws(IOException::class)
    private fun pruneAndRescore(startPos: Int, endPos: Int, bestStartIDX: Int) {
        var pos = endPos
        while (pos > startPos) {
            val posData = positions.get(pos)
            for (arcIDX in 0 until posData.count) {
                val backPos = posData.backPos[arcIDX]
                if (backPos >= startPos) {
                    positions.get(backPos).addForward(
                        pos,
                        arcIDX,
                        posData.backID[arcIDX],
                        posData.backType[arcIDX]
                    )
                }
            }
            posData.count = 0
            pos--
        }

        for (pos2 in startPos until endPos) {
            val posData = positions.get(pos2)
            if (posData.count == 0) {
                posData.forwardCount = 0
                continue
            }

            if (pos2 == startPos) {
                val rightID = if (startPos == 0) {
                    0
                } else {
                    getDict(posData.backType[bestStartIDX]).getRightId(posData.backID[bestStartIDX])
                }
                val pathCost = posData.costs[bestStartIDX]
                for (forwardArcIDX in 0 until posData.forwardCount) {
                    val forwardType = posData.forwardType[forwardArcIDX]
                    val dict2 = getDict(forwardType)
                    val wordID = posData.forwardID[forwardArcIDX]
                    val toPos = posData.forwardPos[forwardArcIDX]
                    val newCost =
                        pathCost +
                            dict2.getWordCost(wordID) +
                            costs.get(rightID, dict2.getLeftId(wordID)) +
                            computePenalty(pos2, toPos - pos2)
                    positions.get(toPos).add(
                        newCost,
                        dict2.getRightId(wordID),
                        pos2,
                        -1,
                        bestStartIDX,
                        wordID,
                        forwardType
                    )
                }
            } else {
                for (forwardArcIDX in 0 until posData.forwardCount) {
                    val forwardType = posData.forwardType[forwardArcIDX]
                    val toPos = posData.forwardPos[forwardArcIDX]
                    add(
                        getDict(forwardType).getMorphAttributes(),
                        posData,
                        pos2,
                        toPos,
                        posData.forwardID[forwardArcIDX],
                        forwardType,
                        true
                    )
                }
            }
            posData.forwardCount = 0
        }
    }

    override fun registerNode(node: Int, fragment: CharArray) {
        val lattice = lattice ?: return
        val left = lattice.getNodeLeft(node)
        val right = lattice.getNodeRight(node)
        val type = lattice.getNodeDicType(node)

        if (!discardPunctuation || !isPunctuation(fragment[left])) {
            if (type == TokenType.USER) {
                val ud = userDictionary ?: return
                val wordIDAndLength = ud.lookupSegmentation(lattice.getNodeWordID(node))
                val wordID = wordIDAndLength[0]

                pending.add(
                    Token(
                        fragment,
                        left,
                        right - left,
                        lattice.getRootBase() + left,
                        lattice.getRootBase() + right,
                        wordID,
                        TokenType.USER,
                        ud.getMorphAttributes()
                    )
                )

                var current = 0
                for (j in 1 until wordIDAndLength.size) {
                    val len = wordIDAndLength[j]
                    if (len < right - left) {
                        val startOffset = lattice.getRootBase() + current + left
                        pending.add(
                            Token(
                                fragment,
                                current + left,
                                len,
                                startOffset,
                                startOffset + len,
                                wordID + j - 1,
                                TokenType.USER,
                                ud.getMorphAttributes()
                            )
                        )
                    }
                    current += len
                }
            } else {
                pending.add(
                    Token(
                        fragment,
                        left,
                        right - left,
                        lattice.getRootBase() + left,
                        lattice.getRootBase() + right,
                        lattice.getNodeWordID(node),
                        type,
                        getDict(type).getMorphAttributes()
                    )
                )
            }
        }
    }

    fun getDict(type: TokenType): Dictionary<out JaMorphData> {
        @Suppress("UNCHECKED_CAST")
        return dictionaryMap[type] as Dictionary<out JaMorphData>
    }

    fun setNBestCostValue(value: Int) {
        super.setNBestCost(value)
    }

    fun getNBestCostValue(): Int = super.getNBestCost()

    fun isEndOfInput(): Boolean = end

    fun isOutputNBestEnabled(): Boolean = outputNBest

    fun setGraphvizFormatter(formatter: GraphvizFormatter<JaMorphData>?) {
        dotOut = formatter
    }

    companion object {
        fun isPunctuation(ch: Char): Boolean {
            return when (ch.category) {
                CharCategory.SPACE_SEPARATOR,
                CharCategory.LINE_SEPARATOR,
                CharCategory.PARAGRAPH_SEPARATOR,
                CharCategory.CONTROL,
                CharCategory.FORMAT,
                CharCategory.DASH_PUNCTUATION,
                CharCategory.START_PUNCTUATION,
                CharCategory.END_PUNCTUATION,
                CharCategory.CONNECTOR_PUNCTUATION,
                CharCategory.OTHER_PUNCTUATION,
                CharCategory.MATH_SYMBOL,
                CharCategory.CURRENCY_SYMBOL,
                CharCategory.MODIFIER_SYMBOL,
                CharCategory.OTHER_SYMBOL,
                CharCategory.INITIAL_QUOTE_PUNCTUATION,
                CharCategory.FINAL_QUOTE_PUNCTUATION -> true

                else -> false
            }
        }
    }
}
