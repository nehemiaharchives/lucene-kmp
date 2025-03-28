package org.gnit.lucenekmp.util.automaton

/** Interface accessing the transitions of an automaton  */
interface TransitionAccessor {
    /**
     * Initialize the provided Transition to iterate through all transitions leaving the specified
     * state. You must call [.getNextTransition] to get each transition. Returns the number of
     * transitions leaving this state.
     */
    fun initTransition(state: Int, t: Transition): Int

    /** Iterate to the next transition after the provided one  */
    fun getNextTransition(t: Transition)

    /** How many transitions this state has.  */
    fun getNumTransitions(state: Int): Int

    /**
     * Fill the provided [Transition] with the index'th transition leaving the specified state.
     */
    fun getTransition(state: Int, index: Int, t: Transition)
}
