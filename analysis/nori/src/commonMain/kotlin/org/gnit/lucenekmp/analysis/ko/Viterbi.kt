package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.ko.dict.CharacterDefinition
import org.gnit.lucenekmp.analysis.ko.dict.KoMorphData
import org.gnit.lucenekmp.analysis.ko.dict.TokenInfoDictionary
import org.gnit.lucenekmp.analysis.ko.dict.UnknownDictionary
import org.gnit.lucenekmp.analysis.ko.dict.UserDictionary
import org.gnit.lucenekmp.analysis.morph.ConnectionCosts
import org.gnit.lucenekmp.analysis.morph.Dictionary
import org.gnit.lucenekmp.analysis.morph.GraphvizFormatter
import org.gnit.lucenekmp.analysis.morph.MorphData
import org.gnit.lucenekmp.analysis.morph.TokenInfoFST
import org.gnit.lucenekmp.analysis.morph.TokenType
import org.gnit.lucenekmp.analysis.morph.Viterbi
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.fst.FST
import okio.IOException

/** Viterbi subclass for Korean morphological analysis. */
internal class Viterbi(
    fst: TokenInfoFST,
    fstReader: FST.BytesReader,
    dictionary: TokenInfoDictionary,
    userFST: TokenInfoFST?,
    userFSTReader: FST.BytesReader?,
    userDictionary: UserDictionary?,
    costs: ConnectionCosts,
    private val unkDictionary: UnknownDictionary,
    private val characterDefinition: CharacterDefinition,
    private val discardPunctuation: Boolean,
    private val mode: KoreanTokenizer.DecompoundMode,
    private val outputUnknownUnigrams: Boolean
) : Viterbi<Token, Viterbi.Position>(
    fst,
    fstReader,
    dictionary,
    userFST,
    userFSTReader,
    userDictionary,
    costs,
    { Position() }
) {
    private val dictionaryMap: MutableMap<TokenType, Dictionary<out KoMorphData>> = mutableMapOf()
    private var dotOut: GraphvizFormatter<KoMorphData>? = null

    init {
        enableSpacePenaltyFactor = true
        outputLongestUserEntryOnly = true
        dictionaryMap[TokenType.KNOWN] = dictionary
        dictionaryMap[TokenType.UNKNOWN] = unkDictionary
        if (userDictionary != null) {
            dictionaryMap[TokenType.USER] = userDictionary
        }
    }

    @Throws(IOException::class)
    override fun processUnknownWord(anyMatches: Boolean, posData: Position): Int {
        val firstCharacter = buffer.get(pos).toChar()
        if (!anyMatches || characterDefinition.isInvoke(firstCharacter)) {
            var characterId = characterDefinition.getCharacterClass(firstCharacter)
            val unknownWordLength: Int
            if (!characterDefinition.isGroup(firstCharacter)) {
                unknownWordLength = 1
            } else {
                var length = 1
                var scriptCode = Character.Companion.UnicodeScript.of(firstCharacter.code)
                val isPunct = isPunctuation(firstCharacter)
                val isDigit = Character.isDigit(firstCharacter.code)
                var posAhead = pos + 1
                while (length < MAX_UNKNOWN_WORD_LENGTH) {
                    val next = buffer.get(posAhead)
                    if (next == -1) {
                        break
                    }
                    val ch = next.toChar()
                    val chType = Character.getType(ch.code)
                    val sc = Character.Companion.UnicodeScript.of(next)
                    val sameScript = isSameScript(scriptCode!!, sc!!) || chType == Character.NON_SPACING_MARK.toInt()
                    if (sameScript && isPunctuation(ch, chType) == isPunct && Character.isDigit(ch.code) == isDigit && characterDefinition.isGroup(ch)) {
                        length++
                    } else {
                        break
                    }
                    if (isCommonOrInherited(scriptCode) && !isCommonOrInherited(sc)) {
                        scriptCode = sc
                        characterId = characterDefinition.getCharacterClass(ch)
                    }
                    posAhead++
                }
                unknownWordLength = length
            }

            unkDictionary.lookupWordIds(characterId.toInt(), wordIdRef)
            for (ofs in 0 until wordIdRef.length) {
                add(
                    unkDictionary.getMorphAttributes(),
                    posData,
                    pos,
                    pos + unknownWordLength,
                    wordIdRef.ints[wordIdRef.offset + ofs],
                    TokenType.UNKNOWN,
                    false
                )
            }
            return unknownWordLength
        }
        return 0
    }

    fun setGraphvizFormatter(dotOut: GraphvizFormatter<KoMorphData>?) {
        this.dotOut = dotOut
    }

    @Throws(IOException::class)
    override fun backtrace(endPosData: Position, fromIDX: Int) {
        val endPos = endPosData.pos
        if (endPos == lastBackTracePos) {
            return
        }
        val fragment = buffer.get(lastBackTracePos, endPos - lastBackTracePos)

        if (dotOut != null) {
            dotOut!!.onBacktrace(::getDict, positions, lastBackTracePos, endPosData, fromIDX, fragment, end)
        }

        var pos = endPos
        var bestIDX = fromIDX

        while (pos > lastBackTracePos) {
            val posData = positions.get(pos)
            val backPos = posData.backPos[bestIDX]
            val backWordPos = posData.backWordPos[bestIDX]
            val length = pos - backWordPos
            val backType = posData.backType[bestIDX]
            val backID = posData.backID[bestIDX]
            val nextBestIDX = posData.backIndex[bestIDX]
            val fragmentOffset = backWordPos - lastBackTracePos

            val dict = getDict(backType)

            if (outputUnknownUnigrams && backType == TokenType.UNKNOWN) {
                var i = length - 1
                while (i >= 0) {
                    var charLen = 1
                    if (i > 0 && fragment[fragmentOffset + i].isLowSurrogate()) {
                        i--
                        charLen = 2
                    }
                    val token = DictionaryToken(
                        TokenType.UNKNOWN,
                        unkDictionary.getMorphAttributes(),
                        CharacterDefinition.NGRAM.toInt(),
                        fragment,
                        fragmentOffset + i,
                        charLen,
                        backWordPos + i,
                        backWordPos + i + charLen
                    )
                    pending.add(token)
                    i--
                }
            } else {
                val token = DictionaryToken(
                    backType,
                    dict.getMorphAttributes(),
                    backID,
                    fragment,
                    fragmentOffset,
                    length,
                    backWordPos,
                    backWordPos + length
                )
                if (token.posType == POS.Type.MORPHEME || mode == KoreanTokenizer.DecompoundMode.NONE) {
                    if (!shouldFilterToken(token)) {
                        pending.add(token)
                    }
                } else {
                    val morphemes = token.morphemeArray
                    if (morphemes == null) {
                        pending.add(token)
                    } else {
                        var endOffset = backWordPos + length
                        var posLen = 0
                        for (i in morphemes.indices.reversed()) {
                            val morpheme = morphemes[i]
                            val compoundToken: Token = if (token.posType == POS.Type.COMPOUND) {
                                DecompoundToken(
                                    morpheme.posTag,
                                    morpheme.surfaceForm,
                                    endOffset - morpheme.surfaceForm.length,
                                    endOffset,
                                    backType
                                )
                            } else {
                                DecompoundToken(
                                    morpheme.posTag,
                                    morpheme.surfaceForm,
                                    token.startOffset,
                                    token.endOffset,
                                    backType
                                )
                            }
                            if (i == 0 && mode == KoreanTokenizer.DecompoundMode.MIXED) {
                                compoundToken.positionIncrement = 0
                            }
                            posLen++
                            endOffset -= morpheme.surfaceForm.length
                            pending.add(compoundToken)
                        }
                        if (mode == KoreanTokenizer.DecompoundMode.MIXED) {
                            token.positionLength = maxOf(1, posLen)
                            pending.add(token)
                        }
                    }
                }
            }

            if (!discardPunctuation && backWordPos != backPos) {
                val offset = backPos - lastBackTracePos
                val len = backWordPos - backPos
                unkDictionary.lookupWordIds(characterDefinition.getCharacterClass(' ').toInt(), wordIdRef)
                val spaceToken = DictionaryToken(
                    TokenType.UNKNOWN,
                    unkDictionary.getMorphAttributes(),
                    wordIdRef.ints[wordIdRef.offset],
                    fragment,
                    offset,
                    len,
                    backPos,
                    backPos + len
                )
                pending.add(spaceToken)
            }

            pos = backPos
            bestIDX = nextBestIDX
        }

        lastBackTracePos = endPos
        buffer.freeBefore(endPos)
        positions.freeBefore(endPos)
    }

    override fun computeSpacePenalty(morphData: MorphData, wordID: Int, numSpaces: Int): Int {
        val leftPOS = (morphData as KoMorphData).getLeftPOS(wordID)
        var spacePenalty = 0
        if (numSpaces > 0) {
            when (leftPOS) {
                POS.Tag.EP,
                POS.Tag.EF,
                POS.Tag.EC,
                POS.Tag.ETN,
                POS.Tag.ETM,
                POS.Tag.JKS,
                POS.Tag.JKC,
                POS.Tag.JKG,
                POS.Tag.JKO,
                POS.Tag.JKB,
                POS.Tag.JKV,
                POS.Tag.JKQ,
                POS.Tag.JX,
                POS.Tag.JC,
                POS.Tag.VCP,
                POS.Tag.XSA,
                POS.Tag.XSN,
                POS.Tag.XSV -> spacePenalty = 3000
                else -> Unit
            }
        }
        return spacePenalty
    }

    private fun getDict(type: TokenType): Dictionary<out KoMorphData> {
        return dictionaryMap[type] ?: throw IllegalStateException("Dictionary missing for $type")
    }

    private fun shouldFilterToken(token: Token): Boolean {
        return discardPunctuation && isPunctuation(token.surfaceForm[token.offset])
    }

    private fun isPunctuation(ch: Char): Boolean = isPunctuation(ch, Character.getType(ch.code))

    private fun isPunctuation(ch: Char, cid: Int): Boolean {
        if (ch.code == 0x318D) return true
        return when (cid.toByte()) {
            Character.SPACE_SEPARATOR,
            Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR,
            Character.CONTROL,
            Character.FORMAT,
            Character.DASH_PUNCTUATION,
            Character.START_PUNCTUATION,
            Character.END_PUNCTUATION,
            Character.CONNECTOR_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
            Character.MATH_SYMBOL,
            Character.CURRENCY_SYMBOL,
            Character.MODIFIER_SYMBOL,
            Character.OTHER_SYMBOL,
            Character.INITIAL_QUOTE_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION -> true
            else -> false
        }
    }

    private fun isCommonOrInherited(script: Character.Companion.UnicodeScript): Boolean {
        return script == Character.Companion.UnicodeScript.INHERITED || script == Character.Companion.UnicodeScript.COMMON
    }

    private fun isSameScript(scriptOne: Character.Companion.UnicodeScript, scriptTwo: Character.Companion.UnicodeScript): Boolean {
        return scriptOne == scriptTwo || isCommonOrInherited(scriptOne) || isCommonOrInherited(scriptTwo)
    }
}
