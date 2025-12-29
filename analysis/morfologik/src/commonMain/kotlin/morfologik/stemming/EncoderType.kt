package morfologik.stemming

/**
 * Known [ISequenceEncoder]s.
 */
enum class EncoderType {
    SUFFIX {
        override fun get(): ISequenceEncoder = TrimSuffixEncoder()
        override val prefixBytes: Int = 1
    },
    PREFIX {
        override fun get(): ISequenceEncoder = TrimPrefixAndSuffixEncoder()
        override val prefixBytes: Int = 2
    },
    INFIX {
        override fun get(): ISequenceEncoder = TrimInfixAndSuffixEncoder()
        override val prefixBytes: Int = 3
    },
    NONE {
        override fun get(): ISequenceEncoder = NoEncoder()
        override val prefixBytes: Int = 0
    };

    abstract fun get(): ISequenceEncoder
    abstract val prefixBytes: Int
}
