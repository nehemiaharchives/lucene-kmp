package morfologik.fsa

/**
 * FSA automaton flags. Where applicable, flags follow Daciuk's `fsa` package.
 */
enum class FSAFlags(val bits: Int) {
    FLEXIBLE(1 shl 0),
    STOPBIT(1 shl 1),
    NEXTBIT(1 shl 2),
    TAILS(1 shl 3),
    NUMBERS(1 shl 8),
    SEPARATORS(1 shl 9);

    fun isSet(flags: Int): Boolean = (flags and bits) != 0

    companion object {
        fun asShort(flags: Set<FSAFlags>): Short {
            var value = 0
            for (f in flags) {
                value = value or f.bits
            }
            return value.toShort()
        }
    }
}
