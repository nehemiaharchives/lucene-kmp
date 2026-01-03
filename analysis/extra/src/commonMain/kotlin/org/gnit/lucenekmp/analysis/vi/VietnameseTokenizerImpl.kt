package org.gnit.lucenekmp.analysis.vi

import org.gnit.lucenekmp.analysis.vi.CoccocTokenizer.TokenizeOption
import org.gnit.lucenekmp.jdkport.Reader

internal class VietnameseTokenizerImpl(
    private val config: VietnameseConfig,
    input: Reader
) {
    private val option: TokenizeOption = when {
        config.splitURL -> TokenizeOption.URL
        config.splitHost -> TokenizeOption.HOST
        else -> TokenizeOption.NORMAL
    }
    private val tokenizer: CoccocTokenizer = CoccocTokenizer.getInstance()
    private val pending: MutableList<VietnameseToken> = mutableListOf()
    private var input: Reader = input
    private var pos: Int = -1

    fun getNextToken(): VietnameseToken? {
        while (pending.isEmpty()) {
            tokenize()
            if (pending.isEmpty()) {
                return null
            }
        }
        pos++
        return if (pos < pending.size) pending[pos] else null
    }

    fun reset(input: Reader) {
        this.input = input
        pending.clear()
        pos = -1
    }

    private fun tokenize() {
        val tokens = tokenize(input)
        if (tokens.isNotEmpty()) {
            pending.addAll(tokens)
        }
    }

    private fun tokenize(reader: Reader): List<VietnameseToken> {
        return tokenize(readAll(reader))
    }

    private fun tokenize(text: String): List<VietnameseToken> {
        return tokenizer.segment(text, option, config.keepPunctuation)
    }

    private fun readAll(reader: Reader): String {
        val sb = StringBuilder()
        val buffer = CharArray(2048)
        while (true) {
            val read = reader.read(buffer, 0, buffer.size)
            if (read == -1) break
            sb.appendRange(buffer, 0, read)
        }
        return sb.toString()
    }
}
