package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.CharFilterFactory
import org.gnit.lucenekmp.jdkport.Reader
import kotlin.properties.Delegates

/** Factory for [JapaneseIterationMarkCharFilter]. */
class JapaneseIterationMarkCharFilterFactory : CharFilterFactory {
    private var normalizeKanji by Delegates.notNull<Boolean>()
    private var normalizeKana by Delegates.notNull<Boolean>()

    /** Creates a new JapaneseIterationMarkCharFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        normalizeKanji = getBoolean(args, NORMALIZE_KANJI_PARAM, JapaneseIterationMarkCharFilter.NORMALIZE_KANJI_DEFAULT)
        normalizeKana = getBoolean(args, NORMALIZE_KANA_PARAM, JapaneseIterationMarkCharFilter.NORMALIZE_KANA_DEFAULT)
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: Reader): Reader {
        return JapaneseIterationMarkCharFilter(input, normalizeKanji, normalizeKana)
    }

    override fun normalize(input: Reader): Reader {
        return create(input)
    }

    companion object {
        const val NAME: String = "japaneseIterationMark"
        private const val NORMALIZE_KANJI_PARAM: String = "normalizeKanji"
        private const val NORMALIZE_KANA_PARAM: String = "normalizeKana"
    }
}
