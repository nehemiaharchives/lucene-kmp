package org.gnit.lucenekmp.analysis.vi

class CoccocTokenizer private constructor() {
    companion object Companion {
        const val SPACE = " "
        const val UNDERSCORE = "_"
        const val COMMA = ","
        const val DOT = "."

        fun getInstance(): CoccocTokenizer {
            return CoccocTokenizer()
        }
    }

    enum class TokenizeOption(val value: Int) {
        NORMAL(0),
        HOST(1),
        URL(2)
    }

    fun segment(text: String, option: TokenizeOption, keepPunctuation: Boolean): List<VietnameseToken> {
        return segment(text, option, keepPunctuation, false)
    }

    fun segment(
        text: String,
        option: TokenizeOption,
        keepPunctuation: Boolean,
        forTransforming: Boolean
    ): List<VietnameseToken> {
        return segment(text, forTransforming, option, keepPunctuation)
    }

    private fun segment(
        text: String,
        forTransforming: Boolean,
        option: TokenizeOption,
        keepPunctuation: Boolean
    ): List<VietnameseToken> {
        if (text.isEmpty()) return emptyList()

        val vietnameseTokens = mutableListOf<VietnameseToken>()
        var index = 0
        while (index < text.length) {
            if (startsWithHttpUrl(text, index)) {
                val end = findUrlEnd(text, index)
                vietnameseTokens.addAll(tokenizeUrl(text, index, end))
                index = end
                continue
            }

            val ch = text[index]
            if (ch.isLetterOrDigit()) {
                val start = index
                index++
                while (index < text.length && text[index].isLetterOrDigit()) {
                    index++
                }
                val term = text.substring(start, index).lowercase()
                vietnameseTokens.add(VietnameseToken(term, VietnameseToken.Type.WORD, start, index))
                continue
            }

            if (keepPunctuation && !ch.isWhitespace()) {
                vietnameseTokens.add(
                    VietnameseToken(
                        ch.toString(),
                        VietnameseToken.Type.PUNCT,
                        index,
                        index + 1
                    )
                )
            }
            index++
        }

        val merged = mergeWordBigrams(vietnameseTokens, text)
        if (forTransforming && option == TokenizeOption.NORMAL) {
            return merged + VietnameseToken.FULL_STOP
        }
        return merged
    }

    private fun mergeWordBigrams(vietnameseTokens: List<VietnameseToken>, text: String): List<VietnameseToken> {
        if (vietnameseTokens.isEmpty()) return vietnameseTokens
        val merged = mutableListOf<VietnameseToken>()
        var i = 0
        while (i < vietnameseTokens.size) {
            val current = vietnameseTokens[i]
            if (current.getType() == VietnameseToken.Type.WORD && i + 1 < vietnameseTokens.size) {
                val next = vietnameseTokens[i + 1]
                if (next.getType() == VietnameseToken.Type.WORD && onlyWhitespaceBetween(text, current, next)) {
                    val combinedText = current.getText() + SPACE + next.getText()
                    merged.add(
                        VietnameseToken(
                            combinedText,
                            VietnameseToken.Type.WORD,
                            current.getPos(),
                            next.getEndPos()
                        )
                    )
                    i += 2
                    continue
                }
            }
            merged.add(current)
            i++
        }
        return merged
    }

    private fun onlyWhitespaceBetween(text: String, left: VietnameseToken, right: VietnameseToken): Boolean {
        val start = left.getEndPos()
        val end = right.getPos()
        if (start >= end) return false
        for (i in start until end) {
            if (!text[i].isWhitespace()) return false
        }
        return true
    }

    private fun tokenizeUrl(text: String, start: Int, end: Int): List<VietnameseToken> {
        val vietnameseTokens = mutableListOf<VietnameseToken>()
        var i = start
        while (i < end) {
            while (i < end && !text[i].isLetterOrDigit()) {
                i++
            }
            val runStart = i
            while (i < end && text[i].isLetterOrDigit()) {
                i++
            }
            if (runStart < i) {
                val term = text.substring(runStart, i).lowercase()
                vietnameseTokens.add(
                    VietnameseToken(
                        term,
                        VietnameseToken.Type.WHOLE_URL,
                        runStart,
                        i
                    )
                )
            }
        }
        return vietnameseTokens
    }

    private fun startsWithHttpUrl(text: String, index: Int): Boolean {
        return text.regionMatches(index, "http://", 0, 7, ignoreCase = true) ||
            text.regionMatches(index, "https://", 0, 8, ignoreCase = true)
    }

    private fun findUrlEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && !text[i].isWhitespace()) {
            i++
        }
        return i
    }
}