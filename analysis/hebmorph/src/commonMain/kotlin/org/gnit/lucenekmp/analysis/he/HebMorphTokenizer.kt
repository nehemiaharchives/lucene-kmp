package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.he.datastructures.DictRadix
import org.gnit.lucenekmp.jdkport.Reader
import kotlin.math.max

class HebMorphTokenizer(private var input: Reader, private val hebrewPrefixes: MutableMap<String, Int>, specialCases: DictRadix<Byte>? = null) {
    object TokenType {
        var Hebrew = 1
        var NonHebrew = 2
        var Numeric = 4
        var Mixed = 8
        var Construct = 16
        var Acronym = 32
        var Exact = 64
        var Custom = 128
    }

    private var dataLen = 0
    private var inputOffset = 0
    private var tokenOffset = 0
    private var tokenLengthInSource = 0
    fun getOffset(): Int = tokenOffset
    fun getLengthInSource(): Int = tokenLengthInSource

    private var suffixForExactMatch: Char? = null
    fun getSuffixForExactMatch(): Char? = suffixForExactMatch
    fun setSuffixForExactMatch(suffixForExactMatch: Char?) {
        this.suffixForExactMatch = suffixForExactMatch
    }

    private val specialCases: DictRadix<Byte> = specialCases ?: DictRadix(false)
    private val dummyData: Byte = 0

    fun addSpecialCase(token: String) {
        if (token.length > TOKENIZATION_EXCEPTION_MAX_LENGTH) {
            throw IllegalArgumentException("Special tokenization rule must be at most $TOKENIZATION_EXCEPTION_MAX_LENGTH in length")
        }
        if (token.contains(" ")) throw IllegalArgumentException("Special tokenization rule cannot contain spaces")
        specialCases.addNode(token, dummyData)
    }

    fun clearSpecialCases() {
        specialCases.clear()
    }

    private val ioBuffer = CharArray(IO_BUFFER_SIZE)
    private var ioBufferIndex = 0
    private val wordBuffer = CharArray(127)
    private var currentTokenLength: Byte = 0
    private var tokenType = 0
    private val tokenizationExceptionBuffer = CharArray(TOKENIZATION_EXCEPTION_MAX_LENGTH)

    private fun isRecognizedException(prefix: CharArray, length: Byte, c: Char): Boolean {
        if (length >= TOKENIZATION_EXCEPTION_MAX_LENGTH) return false
        prefix.copyInto(tokenizationExceptionBuffer, 0, 0, length.toInt())
        tokenizationExceptionBuffer[length.toInt()] = c
        return isRecognizedException(tokenizationExceptionBuffer, length + 1, (length + 1).toByte())
    }

    private fun isRecognizedException(c: Char): Boolean {
        tokenizationExceptionBuffer[0] = c
        return isRecognizedException(tokenizationExceptionBuffer, 1, 1)
    }

    private fun isRecognizedException(token: CharArray, tokenLen: Int, length: Byte): Boolean {
        return isRecognizedException(token, tokenLen, length, false)
    }

    private fun isRecognizedException(token: CharArray, tokenLen: Int, length: Byte, exact: Boolean): Boolean {
        var i = 0
        while (i < tokenLen && HebrewUtils.isHebrewLetter(token[i])) {
            if (!isLegalPrefix(token, i + 1, hebrewPrefixes)) {
                i = 0
                break
            }
            i++
        }
        return try {
            specialCases.lookup(token, i, length.toInt() - i, i, !exact)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun nextToken(tokenString: Reference<String>): Int {
        currentTokenLength = 0
        tokenOffset = 0
        tokenType = 0
        var avoidTryingCustom = false
        while (true) {
            if (ioBufferIndex >= dataLen) {
                inputOffset += dataLen
                dataLen = input.read(ioBuffer, 0, ioBuffer.size)
                if (dataLen <= 0) {
                    dataLen = 0
                    if ((tokenType and TokenType.Custom) > 0 && currentTokenLength > 0 &&
                        !isRecognizedException(wordBuffer, wordBuffer.size, currentTokenLength, true)
                    ) {
                        abortCustomToken()
                    }
                    if (currentTokenLength.toInt() == 0) {
                        tokenString.ref = ""
                        tokenLengthInSource = 0
                        tokenOffset = inputOffset
                        return 0
                    }
                    break
                }
                ioBufferIndex = 0
            }

            var c = ioBuffer[ioBufferIndex++]
            c = HebrewCharacters.collapseAlternate(c)
            var appendCurrentChar = false

            if (currentTokenLength.toInt() == 0) {
                if (HebrewUtils.isHebrewLetter(c)) {
                    if (!HebrewUtils.isFinalHebrewLetter(c)) {
                        tokenType = tokenType or TokenType.Hebrew
                        appendCurrentChar = true
                    }
                } else if (c.isLetterOrDigit()) {
                    tokenType = tokenType or TokenType.NonHebrew
                    if (c.isDigit()) tokenType = tokenType or TokenType.Numeric
                    appendCurrentChar = true
                } else if (!avoidTryingCustom && !c.isWhitespace() && isRecognizedException(c)) {
                    tokenType = tokenType or TokenType.Custom
                    appendCurrentChar = true
                }
            } else {
                if (!avoidTryingCustom && (tokenType and TokenType.Custom) > 0 && !c.isWhitespace()) {
                    wordBuffer[currentTokenLength.toInt()] = c
                    if (!isRecognizedException(wordBuffer, wordBuffer.size, (currentTokenLength + 1).toByte())) {
                        if (!c.isLetterOrDigit()) break
                        tokenType = tokenType and TokenType.Custom.inv()
                        avoidTryingCustom = true
                        ioBufferIndex--
                        if (ioBufferIndex >= currentTokenLength) {
                            ioBufferIndex -= currentTokenLength
                            currentTokenLength = 0
                            continue
                        } else {
                            abortCustomToken()
                            continue
                        }
                    }
                    appendCurrentChar = true
                } else if (HebrewUtils.isHebrewLetter(c) || HebrewUtils.isNiqqudChar(c)) {
                    appendCurrentChar = true
                } else if (c.isLetterOrDigit()) {
                    if (tokenType == TokenType.Hebrew) tokenType = tokenType or TokenType.Mixed
                    appendCurrentChar = true
                } else if (HebrewUtils.isOfChars(c, HebrewUtils.Gershayim)) {
                    c = '"'
                    if (!HebrewUtils.isHebrewLetter(wordBuffer[currentTokenLength - 1]) &&
                        !HebrewUtils.isNiqqudChar(wordBuffer[currentTokenLength - 1])
                    ) break
                    tokenType = tokenType or TokenType.Acronym
                    appendCurrentChar = true
                } else if (HebrewUtils.isOfChars(c, HebrewUtils.Geresh)) {
                    c = '\''
                    if ((tokenType and TokenType.Hebrew) > 0) {
                        if (!HebrewUtils.isHebrewLetter(wordBuffer[currentTokenLength - 1]) &&
                            !HebrewUtils.isNiqqudChar(wordBuffer[currentTokenLength - 1]) &&
                            !HebrewUtils.isOfChars(wordBuffer[currentTokenLength - 1], HebrewUtils.Geresh)
                        ) break
                    }
                    appendCurrentChar = true
                } else if (!avoidTryingCustom && !isSuffixForExactMatch(c) && !c.isWhitespace() &&
                    isRecognizedException(wordBuffer, currentTokenLength, c)
                ) {
                    tokenType = tokenType or TokenType.Custom
                    appendCurrentChar = true
                } else {
                    if (HebrewUtils.isOfChars(c, HebrewUtils.Makaf)) {
                        tokenType = tokenType or TokenType.Construct
                        c = '-'
                    } else if (suffixForExactMatch != null && suffixForExactMatch == c) {
                        tokenType = tokenType or TokenType.Exact
                    }
                    break
                }
            }

            if (appendCurrentChar) {
                if (currentTokenLength.toInt() == 0) {
                    tokenOffset = inputOffset + ioBufferIndex - 1
                } else if (currentTokenLength == (wordBuffer.size - 1).toByte()) {
                    continue
                }
                if (HebrewUtils.isOfChars(c, HebrewUtils.Geresh)) {
                    if (wordBuffer[currentTokenLength - 1] == c) {
                        wordBuffer[currentTokenLength - 1] = '"'
                        tokenType = tokenType or TokenType.Acronym
                    } else {
                        wordBuffer[currentTokenLength.toInt()] = c
                        currentTokenLength++
                    }
                } else {
                    wordBuffer[currentTokenLength.toInt()] = c
                    currentTokenLength++
                }
            }
        }

        tokenLengthInSource = if (dataLen <= 0) {
            max(inputOffset - tokenOffset, 0)
        } else {
            max(inputOffset + ioBufferIndex - 1 - tokenOffset, 0)
        }
        if (HebrewUtils.isOfChars(wordBuffer[currentTokenLength - 1], HebrewUtils.Gershayim)) {
            currentTokenLength--
            wordBuffer[currentTokenLength.toInt()] = '\u0000'
            tokenLengthInSource = max(tokenLengthInSource - 1, 0)
        }
        if (currentTokenLength > 2 && wordBuffer[currentTokenLength - 1] == '\'') {
            if (((tokenType and TokenType.Hebrew) == 0) ||
                !HebrewUtils.isOfChars(wordBuffer[currentTokenLength - 2], HebrewUtils.LettersAcceptingGeresh)
            ) {
                currentTokenLength--
                wordBuffer[currentTokenLength.toInt()] = '\u0000'
                tokenLengthInSource = max(tokenLengthInSource - 1, 0)
            }
        }
        tokenString.ref = wordBuffer.concatToString(0, currentTokenLength.toInt())
        return tokenType
    }

    private fun abortCustomToken() {
        var start = 0
        var pos = 0
        var started = false
        while (pos + start < currentTokenLength) {
            if (!started && !HebrewUtils.isHebrewLetter(wordBuffer[start]) &&
                !HebrewUtils.isNiqqudChar(wordBuffer[start]) && !wordBuffer[start].isLetterOrDigit()
            ) {
                start++
                continue
            }
            started = true
            var c = wordBuffer[pos + start]
            if (HebrewUtils.isHebrewLetter(c) || HebrewUtils.isNiqqudChar(c)) {
                tokenType = tokenType or TokenType.Hebrew
            } else if (c.isLetterOrDigit()) {
                tokenType = if (tokenType == TokenType.Hebrew) tokenType or TokenType.Mixed else tokenType or TokenType.NonHebrew
            } else if (HebrewUtils.isOfChars(c, HebrewUtils.Gershayim)) {
                c = '"'
                tokenType = tokenType or TokenType.Acronym
            } else if (HebrewUtils.isOfChars(c, HebrewUtils.Geresh)) {
                c = '\''
            } else {
                break
            }
            wordBuffer[pos] = c
            pos++
        }
        currentTokenLength = pos.toByte()
    }

    private fun isSuffixForExactMatch(c: Char): Boolean {
        if (suffixForExactMatch == null) return false
        return c == suffixForExactMatch
    }

    fun reset(_input: Reader) {
        input = _input
        inputOffset = 0
        dataLen = 0
        ioBufferIndex = 0
        tokenOffset = 0
        tokenLengthInSource = 0
        currentTokenLength = 0
        tokenType = 0
    }

    companion object {
        val Geresh = HebrewUtils.Geresh
        val Gershayim = HebrewUtils.Gershayim
        val Makaf = HebrewUtils.Makaf
        val CharsFollowingPrefixes = HebrewUtils.CharsFollowingPrefixes
        val LettersAcceptingGeresh = HebrewUtils.LettersAcceptingGeresh

        fun isLegalPrefix(prefix: String, prefixesTree: MutableMap<String, Int>): Boolean {
            return prefixesTree.containsKey(prefix)
        }

        fun isLegalPrefix(prefix: CharArray, length: Int, prefixesTree: MutableMap<String, Int>): Boolean {
            return prefixesTree.containsKey(prefix.concatToString(0, length))
        }

        private const val IO_BUFFER_SIZE = 4096
        const val TOKENIZATION_EXCEPTION_MAX_LENGTH = 25
    }
}
