package org.gnit.lucenekmp.util.automaton

import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.UnicodeUtil.UTF8CodePoint
import org.gnit.lucenekmp.util.automaton.Automaton.Builder

/**
 * Builds a minimal, deterministic [Automaton] that accepts a set of strings using the
 * algorithm described in [Incremental Construction
 * of Minimal Acyclic Finite-State Automata by Daciuk, Mihov, Watson and Watson](https://aclanthology.org/J00-1002.pdf). This requires
 * sorted input data, but is very fast (nearly linear with the input size). Also offers the ability
 * to directly build a binary [Automaton] representation. Users should access this
 * functionality through [Automata] static methods.
 *
 * @see Automata.makeStringUnion
 * @see Automata.makeBinaryStringUnion
 * @see Automata.makeStringUnion
 * @see Automata.makeBinaryStringUnion
 */
internal class StringsToAutomaton
/** The default constructor is private. Use static methods directly.  */
private constructor() {
    /** DFSA state with `char` labels on transitions.  */
    private class State {
        /**
         * Labels of outgoing transitions. Indexed identically to [.states]. Labels must be sorted
         * lexicographically.
         */
        var labels: IntArray = NO_LABELS

        /** States reachable from outgoing transitions. Indexed identically to [.labels].  */
        var states: Array<State> = NO_STATES

        /** `true` if this state corresponds to the end of at least one input sequence.  */
        var is_final: Boolean = false

        /**
         * Returns the target state of a transition leaving this state and labeled with `label
        ` * . If no such transition exists, returns `null`.
         */
        fun getState(label: Int): State? {
            val index: Int = Arrays.binarySearch(labels, label)
            return if (index >= 0) states[index] else null
        }

        /**
         * Two states are equal if:
         *
         *
         *  * they have an identical number of outgoing transitions, labeled with the same labels
         *  * corresponding outgoing transitions lead to the same states (to states with an identical
         * right-language).
         *
         */
        override fun equals(obj: Any?): Boolean {
            val other = obj as State
            return is_final == other.is_final && this.labels.contentEquals(other.labels) && referenceEquals(
                this.states,
                other.states
            )
        }

        /** Compute the hash code of the *current* status of this state.  */
        override fun hashCode(): Int {
            var hash = if (is_final) 1 else 0

            hash = hash * 31 + this.labels.size
            for (c in this.labels) hash = hash * 31 + c

            /*
             * Compare the right-language of this state using reference-identity of
             * outgoing states. This is possible because states are interned (stored
             * in registry) and traversed in post-order, so any outgoing transitions
             * are already interned.
             */
            for (s in this.states) {
                hash = hash * 31 + s.hashCode()
            }

            return hash
        }

        /** Return `true` if this state has any children (outgoing transitions).  */
        fun hasChildren(): Boolean {
            return labels.isNotEmpty()
        }

        /**
         * Create a new outgoing transition labeled `label` and return the newly created
         * target state for this transition.
         */
        fun newState(label: Int): State {
            val index = Arrays.binarySearch(labels, label)
            require(index < 0) { "State already has transition labeled: $label" }

            val insertIndex = -(index + 1)

            val newLabels = IntArray(labels.size + 1)
            val newStates = arrayOfNulls<State?>(states.size + 1)

            // copy before insertion point
            labels.copyInto(destination = newLabels, destinationOffset = 0, startIndex = 0, endIndex = insertIndex)
            states.copyInto(destination = newStates, destinationOffset = 0, startIndex = 0, endIndex = insertIndex)

            // insert new element
            newLabels[insertIndex] = label
            val s = State()
            newStates[insertIndex] = s

            // copy after insertion point
            if (insertIndex < labels.size) {
                labels.copyInto(
                    destination = newLabels,
                    destinationOffset = insertIndex + 1,
                    startIndex = insertIndex,
                    endIndex = labels.size
                )
                states.copyInto(
                    destination = newStates,
                    destinationOffset = insertIndex + 1,
                    startIndex = insertIndex,
                    endIndex = states.size
                )
            }

            this.labels = newLabels
            @Suppress("UNCHECKED_CAST")
            this.states = newStates as Array<State>
            return s
        }

        /** Return the most recent transitions's target state.  */
        fun lastChild(): State {
            require(hasChildren()) { "No outgoing transitions." }
            return states[states.size - 1]
        }

        /**
         * Return the associated state if the most recent transition is labeled with `label`.
         */
        fun lastChild(label: Int): State? {
            val index = labels.size - 1
            if (index >= 0 && labels[index] == label) {
                return states[index]
            }
            return null
        }

        /**
         * Replace the last added outgoing transition's target state with the given state.  */
        fun replaceLastChild(state: State?) {
            require(hasChildren()) { "No outgoing transitions." }
            states[states.size - 1] = state!!
        }

        companion object {
            /** An empty set of labels.  */
            private val NO_LABELS = IntArray(0)

            /** An empty set of states.  */
            private val NO_STATES: Array<State> = emptyArray()

            /**
             * Not in use, this was added only for porting progress script to acknowledge that this method was ported
             */
            private fun referenceEquals(a1: Array<Any>, a2: Array<Any>): Boolean {
                val a1StateArray = a1 as Array<State>
                val a2StateArray = a2 as Array<State>
                return referenceEquals(a1StateArray, a2StateArray)
            }

            /** Compare two lists of objects for reference-equality.  */
            private fun referenceEquals(a1: Array<State>, a2: Array<State>): Boolean {
                if (a1.size != a2.size) {
                    return false
                }

                for (i in a1.indices) {
                    if (a1[i] !== a2[i]) {
                        return false
                    }
                }

                return true
            }
        }
    }

    /** A "registry" for state interning.  */
    private var stateRegistry: MutableMap<State?, State?>? = mutableMapOf<State?, State?>()

    /** Root automaton state.  */
    private val root = State()

    /** Used for input order checking (only through assertions right now)  */
    private var previous: BytesRefBuilder? = null

    /** Copy `current` into an internal buffer.  */
    private fun setPrevious(current: BytesRef): Boolean {
        if (previous == null) {
            previous = BytesRefBuilder()
        }
        previous!!.copyBytes(current)
        return true
    }

    /**
     * Called after adding all terms. Performs final minimization and converts to a standard [ ] instance.
     */
    private fun completeAndConvert(): Automaton {
        // Final minimization:
        checkNotNull(this.stateRegistry)
        if (root.hasChildren()) replaceOrRegister(root)
        stateRegistry = null

        // Convert:
        val a = Builder()
        convert(a, root, mutableMapOf())
        return a.finish()
    }

    private fun add(current: BytesRef, asBinary: Boolean) {
        require(!(current.length > Automata.MAX_STRING_UNION_TERM_LENGTH)) {
            ("This builder doesn't allow terms that are larger than "
                    + Automata.MAX_STRING_UNION_TERM_LENGTH
                    + " UTF-8 bytes, got "
                    + current)
        }
        checkNotNull(stateRegistry) { "Automaton already built." }
        require(
            !(previous != null && previous!!.get().compareTo(current) > 0)
        ) { "Input must be in sorted UTF-8 order: " + previous!!.get() + " >= " + current }
        setPrevious(current)

        // Reusable codepoint information if we're building a non-binary based automaton
        var codePoint: UTF8CodePoint? = null

        // Descend in the automaton (find matching prefix).
        val bytes: ByteArray = current.bytes
        var pos: Int = current.offset
        val max: Int = current.offset + current.length
        var next: State? = null
        var state: State = root
        if (asBinary) {
            while (pos < max && (state.getState(bytes[pos].toInt() and 0xff).also { next = it }) != null) {
                state = next!!
                pos++
            }
        } else {
            while (pos < max) {
                codePoint = UnicodeUtil.codePointAt(bytes, pos, codePoint)
                next = state.getState(codePoint.codePoint)
                if (next == null) {
                    break
                }
                state = next
                pos += codePoint.numBytes
            }
        }

        if (state.hasChildren()) replaceOrRegister(state)

        // Add suffix
        if (asBinary) {
            while (pos < max) {
                state = state.newState(bytes[pos].toInt() and 0xff)
                pos++
            }
        } else {
            while (pos < max) {
                codePoint = UnicodeUtil.codePointAt(bytes, pos, codePoint)
                state = state.newState(codePoint.codePoint)
                pos += codePoint.numBytes
            }
        }
        state.is_final = true
    }

    /**
     * Replace last child of `state` with an already registered state or stateRegistry the
     * last child state.
     */
    private fun replaceOrRegister(state: State) {
        val child = state.lastChild()

        if (child.hasChildren()) replaceOrRegister(child)

        val registered: State? = stateRegistry!![child]
        if (registered != null) {
            state.replaceLastChild(registered)
        } else {
            stateRegistry!!.put(child, child)
        }
    }

    companion object {
        /** Internal recursive traversal for conversion.  */
        private fun convert(
            a: Builder, s: State, visited: MutableMap<State?, Int?>
        ): Int {
            var converted: Int? = visited[s]
            if (converted != null) {
                return converted
            }

            converted = a.createState()
            a.setAccept(converted, s.is_final)

            visited.put(s, converted)
            var i = 0
            val labels = s.labels
            for (target in s.states) {
                a.addTransition(converted, convert(a, target, visited), labels[i++])
            }

            return converted
        }

        /**
         * Build a minimal, deterministic automaton from a sorted list of [BytesRef] representing
         * strings in UTF-8. These strings must be binary-sorted. Creates an [Automaton] with either
         * UTF-8 codepoints as transition labels or binary (compiled) transition labels based on `asBinary`.
         */
        fun build(input: Iterable<BytesRef>, asBinary: Boolean): Automaton {
            val builder = StringsToAutomaton()

            for (b in input) {
                builder.add(b, asBinary)
            }

            return builder.completeAndConvert()
        }

        /**
         * Build a minimal, deterministic automaton from a sorted list of [BytesRef] representing
         * strings in UTF-8. These strings must be binary-sorted. Creates an [Automaton] with either
         * UTF-8 codepoints as transition labels or binary (compiled) transition labels based on `asBinary`.
         */
        @Throws(IOException::class)
        fun build(input: BytesRefIterator, asBinary: Boolean): Automaton {
            val builder = StringsToAutomaton()

            var b: BytesRef? = input.next()
            while (b != null) {
                builder.add(b, asBinary)
                b = input.next()
            }

            return builder.completeAndConvert()
        }
    }
}
