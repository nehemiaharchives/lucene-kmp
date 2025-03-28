package org.gnit.lucenekmp.util.automaton


/**
 * Pair of states.
 *
 * @lucene.experimental
 */
class StatePair {
    // only mike knows what it does (do not expose)
    var s: Int

    /** first state  */
    val s1: Int

    /** second state  */
    val s2: Int

    internal constructor(s: Int, s1: Int, s2: Int) {
        this.s = s
        this.s1 = s1
        this.s2 = s2
    }

    /**
     * Constructs a new state pair.
     *
     * @param s1 first state
     * @param s2 second state
     */
    constructor(s1: Int, s2: Int) {
        this.s1 = s1
        this.s2 = s2
        this.s = -1
    }

    /**
     * Checks for equality.
     *
     * @param obj object to compare with
     * @return true if `obj` represents the same pair of states as this pair
     */
    override fun equals(obj: Any?): Boolean {
        if (obj is StatePair) {
            return obj.s1 == s1 && obj.s2 == s2
        } else {
            return false
        }
    }

    /**
     * Returns hash code.
     *
     * @return hash code
     */
    override fun hashCode(): Int {
        // Don't use s1 ^ s2 since it's vulnerable to the case where s1 == s2 always --> hashCode = 0,
        // e.g. if you call AutomatonTestUtil.sameLanguage,
        // passing the same automaton against itself:
        return s1 * 31 + s2
    }

    override fun toString(): String {
        return "StatePair(s1=$s1 s2=$s2)"
    }
}
