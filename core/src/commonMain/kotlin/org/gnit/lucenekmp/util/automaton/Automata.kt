package org.gnit.lucenekmp.util.automaton

import okio.IOException
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.codePointAt
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.automaton.Automaton.Builder


/**
 * Construction of basic automata.
 *
 * @lucene.experimental
 */
object Automata {
    /**
     * [.makeStringUnion] limits terms of this max length to ensure the stack doesn't
     * overflow while building, since our algorithm currently relies on recursion.
     */
    const val MAX_STRING_UNION_TERM_LENGTH: Int = 1000

    /** Returns a new (deterministic) automaton with the empty language.  */
    fun makeEmpty(): Automaton {
        val a = Automaton()
        a.finishState()
        return a
    }

    /** Returns a new (deterministic) automaton that accepts only the empty string.  */
    fun makeEmptyString(): Automaton {
        val a = Automaton()
        a.createState()
        a.setAccept(0, true)
        return a
    }

    /** Returns a new (deterministic) automaton that accepts all strings.  */
    fun makeAnyString(): Automaton {
        val a = Automaton()
        val s = a.createState()
        a.setAccept(s, true)
        a.addTransition(s, s, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
        a.finishState()
        return a
    }

    /** Returns a new (deterministic) automaton that accepts all binary terms.  */
    fun makeAnyBinary(): Automaton {
        val a = Automaton()
        val s = a.createState()
        a.setAccept(s, true)
        a.addTransition(s, s, 0, 255)
        a.finishState()
        return a
    }

    /**
     * Returns a new (deterministic) automaton that accepts all binary terms except the empty string.
     */
    fun makeNonEmptyBinary(): Automaton {
        val a = Automaton()
        val s1 = a.createState()
        val s2 = a.createState()
        a.setAccept(s2, true)
        a.addTransition(s1, s2, 0, 255)
        a.addTransition(s2, s2, 0, 255)
        a.finishState()
        return a
    }

    /** Returns a new (deterministic) automaton that accepts any single codepoint.  */
    fun makeAnyChar(): Automaton {
        return makeCharRange(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
    }

    /** Accept any single character starting from the specified state, returning the new state  */
    fun appendAnyChar(a: Automaton, state: Int): Int {
        val newState = a.createState()
        a.addTransition(state, newState, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
        return newState
    }

    /** Returns a new (deterministic) automaton that accepts a single codepoint of the given value.  */
    fun makeChar(c: Int): Automaton {
        return makeCharRange(c, c)
    }

    /** Appends the specified character to the specified state, returning a new state.  */
    fun appendChar(a: Automaton, state: Int, c: Int): Int {
        val newState = a.createState()
        a.addTransition(state, newState, c, c)
        return newState
    }

    /**
     * Returns a new (deterministic) automaton that accepts a single codepoint whose value is in the
     * given interval (including both end points).
     */
    fun makeCharRange(min: Int, max: Int): Automaton {
        if (min > max) {
            return makeEmpty()
        }
        val a = Automaton()
        val s1 = a.createState()
        val s2 = a.createState()
        a.setAccept(s2, true)
        a.addTransition(s1, s2, min, max)
        a.finishState()
        return a
    }

    /** Returns a new minimal automaton that accepts any of the provided codepoints  */
    fun makeCharSet(codepoints: IntArray): Automaton {
        return makeCharClass(codepoints, codepoints)
    }

    /** Returns a new minimal automaton that accepts any of the codepoint ranges  */
    fun makeCharClass(starts: IntArray, ends: IntArray): Automaton {
        require(starts.size == ends.size) { "starts must match ends" }
        if (starts.size == 0) {
            return makeEmpty()
        }
        val a = Automaton()
        val s1 = a.createState()
        val s2 = a.createState()
        a.setAccept(s2, true)
        for (i in starts.indices) {
            a.addTransition(s1, s2, starts[i], ends[i])
        }
        a.finishState()
        return a
    }

    /**
     * Constructs sub-automaton corresponding to decimal numbers of length x.substring(n).length().
     */
    private fun anyOfRightLength(builder: Automaton.Builder, x: String, n: Int): Int {
        val s = builder.createState()
        if (x.length == n) {
            builder.setAccept(s, true)
        } else {
            builder.addTransition(s, anyOfRightLength(builder, x, n + 1), '0'.code, '9'.code)
        }
        return s
    }

    /**
     * Constructs sub-automaton corresponding to decimal numbers of value at least x.substring(n) and
     * length x.substring(n).length().
     */
    private fun atLeast(
        builder: Automaton.Builder, x: String, n: Int, initials: MutableCollection<Int>, zeros: Boolean
    ): Int {
        val s = builder.createState()
        if (x.length == n) {
            builder.setAccept(s, true)
        } else {
            if (zeros) {
                initials.add(s)
            }
            val c = x.get(n)
            builder.addTransition(s, atLeast(builder, x, n + 1, initials, zeros && c == '0'), c.code)
            if (c < '9') {
                builder.addTransition(s, anyOfRightLength(builder, x, n + 1), (c.code + 1).toChar().code, '9'.code)
            }
        }
        return s
    }

    /**
     * Constructs sub-automaton corresponding to decimal numbers of value at most x.substring(n) and
     * length x.substring(n).length().
     */
    private fun atMost(builder: Automaton.Builder, x: String, n: Int): Int {
        val s = builder.createState()
        if (x.length == n) {
            builder.setAccept(s, true)
        } else {
            val c = x[n]
            builder.addTransition(s, atMost(builder, x, n + 1), c.code)
            if (c > '0') {
                builder.addTransition(s, anyOfRightLength(builder, x, n + 1), '0'.code, (c.code - 1).toChar().code)
            }
        }
        return s
    }

    /**
     * Constructs sub-automaton corresponding to decimal numbers of value between x.substring(n) and
     * y.substring(n) and of length x.substring(n).length() (which must be equal to
     * y.substring(n).length()).
     */
    private fun between(
        builder: Automaton.Builder,
        x: String,
        y: String,
        n: Int,
        initials: MutableCollection<Int>,
        zeros: Boolean
    ): Int {
        val s = builder.createState()
        if (x.length == n) {
            builder.setAccept(s, true)
        } else {
            if (zeros) {
                initials.add(s)
            }
            val cx = x.get(n)
            val cy = y.get(n)
            if (cx == cy) {
                builder.addTransition(s, between(builder, x, y, n + 1, initials, zeros && cx == '0'), cx.code)
            } else { // cx<cy
                builder.addTransition(s, atLeast(builder, x, n + 1, initials, zeros && cx == '0'), cx.code)
                builder.addTransition(s, atMost(builder, y, n + 1), cy.code)
                if (cx.code + 1 < cy.code) {
                    builder.addTransition(
                        s, anyOfRightLength(builder, x, n + 1), (cx.code + 1).toChar().code, (cy.code - 1).toChar().code
                    )
                }
            }
        }

        return s
    }

    private fun suffixIsZeros(br: BytesRef, len: Int): Boolean {
        for (i in len..<br.length) {
            if (br.bytes[br.offset + i] != 0.toByte()) {
                return false
            }
        }

        return true
    }

    /**
     * Creates a new deterministic, minimal automaton accepting all binary terms in the specified
     * interval. Note that unlike [.makeDecimalInterval], the returned automaton is infinite,
     * because terms behave like floating point numbers leading with a decimal point. However, in the
     * special case where min == max, and both are inclusive, the automata will be finite and accept
     * exactly one term.
     */
    fun makeBinaryInterval(
        min: BytesRef?, minInclusive: Boolean, max: BytesRef?, maxInclusive: Boolean
    ): Automaton {
        var min: BytesRef? = min
        var minInclusive = minInclusive
        require(!(min == null && minInclusive == false)) { "minInclusive must be true when min is null (open ended)" }

        require(!(max == null && maxInclusive == false)) { "maxInclusive must be true when max is null (open ended)" }

        if (min == null) {
            min = BytesRef()
            minInclusive = true
        }

        val cmp: Int
        if (max != null) {
            cmp = min.compareTo(max)
        } else {
            cmp = -1
            if (min.length === 0) {
                if (minInclusive) {
                    return makeAnyBinary()
                } else {
                    return makeNonEmptyBinary()
                }
            }
        }

        if (cmp == 0) {
            if (minInclusive == false || maxInclusive == false) {
                return makeEmpty()
            } else {
                return makeBinary(min)
            }
        } else if (cmp > 0) {
            // max < min
            return makeEmpty()
        }

        if (max != null && StringHelper.startsWith(max, min) && suffixIsZeros(max, min.length)) {
            // Finite case: no sink state!

            var maxLength: Int = max.length

            // the == case was handled above
            require(maxLength > min.length)

            //  bar -> bar\0+
            if (maxInclusive == false) {
                maxLength--
            }

            if (maxLength == min.length) {
                if (minInclusive == false) {
                    return makeEmpty()
                } else {
                    return makeBinary(min)
                }
            }

            val a = Automaton()
            var lastState = a.createState()
            for (i in 0..<min.length) {
                val state = a.createState()
                val label: Int = min.bytes[min.offset + i].toInt() and 0xff
                a.addTransition(lastState, state, label)
                lastState = state
            }

            if (minInclusive) {
                a.setAccept(lastState, true)
            }

            for (i in min.length..<maxLength) {
                val state = a.createState()
                a.addTransition(lastState, state, 0)
                a.setAccept(state, true)
                lastState = state
            }
            a.finishState()
            return a
        }

        val a = Automaton()
        val startState = a.createState()

        val sinkState = a.createState()
        a.setAccept(sinkState, true)

        // This state accepts all suffixes:
        a.addTransition(sinkState, sinkState, 0, 255)

        var equalPrefix = true
        var lastState = startState
        var firstMaxState = -1
        var sharedPrefixLength = 0
        for (i in 0..<min.length) {
            val minLabel: Int = min.bytes[min.offset + i].toInt() and 0xff

            val maxLabel: Int
            if (max != null && equalPrefix && i < max.length) {
                maxLabel = max.bytes[max.offset + i].toInt() and 0xff
            } else {
                maxLabel = -1
            }

            val nextState: Int
            if (minInclusive && i == min.length - 1 && (equalPrefix == false || minLabel != maxLabel)) {
                nextState = sinkState
            } else {
                nextState = a.createState()
            }

            if (equalPrefix) {
                if (minLabel == maxLabel) {
                    // Still in shared prefix
                    a.addTransition(lastState, nextState, minLabel)
                } else if (max == null) {
                    equalPrefix = false
                    sharedPrefixLength = 0
                    a.addTransition(lastState, sinkState, minLabel + 1, 0xff)
                    a.addTransition(lastState, nextState, minLabel)
                } else {
                    // This is the first point where min & max diverge:
                    require(maxLabel > minLabel)

                    a.addTransition(lastState, nextState, minLabel)

                    if (maxLabel > minLabel + 1) {
                        a.addTransition(lastState, sinkState, minLabel + 1, maxLabel - 1)
                    }

                    // Now fork off path for max:
                    if (maxInclusive || i < max.length - 1) {
                        firstMaxState = a.createState()
                        if (i < max.length - 1) {
                            a.setAccept(firstMaxState, true)
                        }
                        a.addTransition(lastState, firstMaxState, maxLabel)
                    }
                    equalPrefix = false
                    sharedPrefixLength = i
                }
            } else {
                // OK, already diverged:
                a.addTransition(lastState, nextState, minLabel)
                if (minLabel < 255) {
                    a.addTransition(lastState, sinkState, minLabel + 1, 255)
                }
            }
            lastState = nextState
        }

        // Accept any suffix appended to the min term:
        if (equalPrefix == false && lastState != sinkState && lastState != startState) {
            a.addTransition(lastState, sinkState, 0, 255)
        }

        if (minInclusive) {
            // Accept exactly the min term:
            a.setAccept(lastState, true)
        }

        if (max != null) {
            // Now do max:

            if (firstMaxState == -1) {
                // Min was a full prefix of max
                sharedPrefixLength = min.length
            } else {
                lastState = firstMaxState
                sharedPrefixLength++
            }
            for (i in sharedPrefixLength..<max.length) {
                val maxLabel: Int = max.bytes[max.offset + i].toInt() and 0xff
                if (maxLabel > 0) {
                    a.addTransition(lastState, sinkState, 0, maxLabel - 1)
                }
                if (maxInclusive || i < max.length - 1) {
                    val nextState = a.createState()
                    if (i < max.length - 1) {
                        a.setAccept(nextState, true)
                    }
                    a.addTransition(lastState, nextState, maxLabel)
                    lastState = nextState
                }
            }

            if (maxInclusive) {
                a.setAccept(lastState, true)
            }
        }

        a.finishState()

        require(a.isDeterministic) { a.toDot() }

        return a
    }

    /**
     * Returns a new automaton that accepts strings representing decimal (base 10) non-negative
     * integers in the given interval.
     *
     * @param min minimal value of interval
     * @param max maximal value of interval (both end points are included in the interval)
     * @param digits if &gt; 0, use fixed number of digits (strings must be prefixed by 0's to obtain
     * the right length) - otherwise, the number of digits is not fixed (any number of leading 0s
     * is accepted)
     * @exception IllegalArgumentException if min &gt; max or if numbers in the interval cannot be
     * expressed with the given fixed number of digits
     */
    @Throws(IllegalArgumentException::class)
    fun makeDecimalInterval(min: Int, max: Int, digits: Int): Automaton {
        // TODO: can this be improved to always return a DFA?
        var x = min.toString()
        var y = max.toString()
        require(!(min > max || (digits > 0 && y.length > digits)))
        val d: Int
        if (digits > 0) d = digits
        else d = y.length
        val bx = StringBuilder()
        for (i in x.length..<d) {
            bx.append('0')
        }
        bx.append(x)
        x = bx.toString()
        val by = StringBuilder()
        for (i in y.length..<d) {
            by.append('0')
        }
        by.append(y)
        y = by.toString()

        val builder = Builder()

        if (digits <= 0) {
            // Reserve the "real" initial state:
            builder.createState()
        }

        val initials: MutableCollection<Int> = mutableListOf()

        between(builder, x, y, 0, initials, digits <= 0)

        val a1 = builder.finish()

        if (digits <= 0) {
            a1.addTransition(0, 0, '0'.code)
            for (p in initials) {
                a1.addEpsilon(0, p)
            }
            a1.finishState()
        }

        return Operations.removeDeadStates(a1)
    }

    /** Returns a new (deterministic) automaton that accepts the single given string.  */
    fun makeString(s: String): Automaton {
        val a = Automaton()
        var lastState = a.createState()
        var i = 0
        var cp = 0
        while (i < s.length) {
            val state = a.createState()
            cp = s.codePointAt(i)
            a.addTransition(lastState, state, cp)
            lastState = state
            i += Character.charCount(cp)
        }

        a.setAccept(lastState, true)
        a.finishState()

        require(a.isDeterministic)
        require(Operations.hasDeadStates(a) === false)

        return a
    }

    /** Returns a new (deterministic) automaton that accepts the single given binary term.  */
    fun makeBinary(term: BytesRef): Automaton {
        val a = Automaton()
        var lastState = a.createState()
        for (i in 0..<term.length) {
            val state = a.createState()
            val label: Int = term.bytes[term.offset + i].toInt() and 0xff
            a.addTransition(lastState, state, label)
            lastState = state
        }

        a.setAccept(lastState, true)
        a.finishState()

        require(a.isDeterministic)
        require(Operations.hasDeadStates(a) === false)

        return a
    }

    /**
     * Returns a new (deterministic) automaton that accepts the single given string from the specified
     * unicode code points.
     */
    fun makeString(word: IntArray, offset: Int, length: Int): Automaton {
        val a = Automaton()
        a.createState()
        var s = 0
        for (i in offset..<offset + length) {
            val s2 = a.createState()
            a.addTransition(s, s2, word[i])
            s = s2
        }
        a.setAccept(s, true)
        a.finishState()

        return a
    }

    /**
     * Returns a new (deterministic and minimal) automaton that accepts the union of the given
     * collection of [BytesRef]s representing UTF-8 encoded strings.
     *
     * @param utf8Strings The input strings, UTF-8 encoded. The collection must be in sorted order.
     * @return An [Automaton] accepting all input strings. The resulting automaton is codepoint
     * based (full unicode codepoints on transitions).
     */
    fun makeStringUnion(utf8Strings: Iterable<BytesRef>): Automaton {
        if (utf8Strings.iterator().hasNext() == false) {
            return makeEmpty()
        } else {
            return StringsToAutomaton.build(utf8Strings, false)
        }
    }

    /**
     * Returns a new (deterministic and minimal) automaton that accepts the union of the given
     * collection of [BytesRef]s representing UTF-8 encoded strings. The resulting automaton
     * will be built in a binary representation.
     *
     * @param utf8Strings The input strings, UTF-8 encoded. The collection must be in sorted order.
     * @return An [Automaton] accepting all input strings. The resulting automaton is binary
     * based (UTF-8 encoded byte transition labels).
     */
    fun makeBinaryStringUnion(utf8Strings: Iterable<BytesRef>): Automaton {
        if (utf8Strings.iterator().hasNext() == false) {
            return makeEmpty()
        } else {
            return StringsToAutomaton.build(utf8Strings, true)
        }
    }

    /**
     * Returns a new (deterministic and minimal) automaton that accepts the union of the given
     * iterator of [BytesRef]s representing UTF-8 encoded strings.
     *
     * @param utf8Strings The input strings, UTF-8 encoded. The iterator must be in sorted order.
     * @return An [Automaton] accepting all input strings. The resulting automaton is codepoint
     * based (full unicode codepoints on transitions).
     */
    @Throws(IOException::class)
    fun makeStringUnion(utf8Strings: BytesRefIterator): Automaton {
        return StringsToAutomaton.build(utf8Strings, false)
    }

    /**
     * Returns a new (deterministic and minimal) automaton that accepts the union of the given
     * iterator of [BytesRef]s representing UTF-8 encoded strings. The resulting automaton will
     * be built in a binary representation.
     *
     * @param utf8Strings The input strings, UTF-8 encoded. The iterator must be in sorted order.
     * @return An [Automaton] accepting all input strings. The resulting automaton is binary
     * based (UTF-8 encoded byte transition labels).
     */
    @Throws(IOException::class)
    fun makeBinaryStringUnion(utf8Strings: BytesRefIterator): Automaton {
        return StringsToAutomaton.build(utf8Strings, true)
    }
}
