package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * Holds one transition from an [Automaton]. This is typically used temporarily when iterating
 * through transitions by invoking [Automaton.initTransition] and [ ][Automaton.getNextTransition].
 */
class Transition
/** Sole constructor.  */
    : Accountable {
    /** Source state.  */
    var source: Int = 0

    /** Destination state.  */
    var dest: Int = 0

    /** Minimum accepted label (inclusive).  */
    var min: Int = 0

    /** Maximum accepted label (inclusive).  */
    var max: Int = 0

    /**
     * Remembers where we are in the iteration; init to -1 to provoke exception if nextTransition is
     * called without first initTransition.
     */
    var transitionUpto: Int = -1

    override fun toString(): String {
        return source.toString() + " --> " + dest + " " + min.toChar() + "-" + max.toChar()
    }

    public override fun ramBytesUsed(): Long {
        return BYTES_USED
    }

    companion object {
        /** static estimation of bytes used  */
        val BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(Transition::class)
    }
}
