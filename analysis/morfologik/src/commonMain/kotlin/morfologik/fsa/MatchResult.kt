package morfologik.fsa

/**
 * A matching result returned from [FSATraversal].
 */
class MatchResult {
    companion object {
        const val EXACT_MATCH: Int = 0
        const val NO_MATCH: Int = -1
        const val AUTOMATON_HAS_PREFIX: Int = -3
        const val SEQUENCE_IS_A_PREFIX: Int = -4
    }

    var kind: Int = NO_MATCH
    var index: Int = 0
    var node: Int = 0

    constructor()

    constructor(kind: Int) {
        reset(kind, 0, 0)
    }

    constructor(kind: Int, index: Int, node: Int) {
        reset(kind, index, node)
    }

    internal fun reset(kind: Int, index: Int, node: Int) {
        this.kind = kind
        this.index = index
        this.node = node
    }
}
