package org.gnit.lucenekmp.analysis.cn.smart

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * Factory for HMMChineseTokenizer
 *
 * @lucene.experimental
 */
class HMMChineseTokenizerFactory : TokenizerFactory {
    companion object {
        /** SPI name */
        const val NAME: String = "hmmChinese"
    }

    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(factory: AttributeFactory): Tokenizer {
        return HMMChineseTokenizer(factory)
    }
}
