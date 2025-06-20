package org.gnit.lucenekmp.tests.util.automaton

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import org.gnit.lucenekmp.util.automaton.StatePair
import org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.util.fst.Util
import org.gnit.lucenekmp.jdkport.assert
import kotlin.experimental.ExperimentalNativeApi

object AutomatonTestUtil {
  /** Default maximum number of states that {@link Operations#determinize} should create. */
  const val DEFAULT_MAX_DETERMINIZED_STATES = 1000000

  /** Maximum level of recursion allowed in recursive operations. */
  private const val MAX_RECURSION_LEVEL = 1000

  /** Returns random string, including full unicode range. */
  fun randomRegexp(r: Random): String {
    while (true) {
      val regexp = randomRegexpString(r)
      // we will also generate some undefined unicode queries
      if (!UnicodeUtil.validUTF16String(regexp)) continue
      try {
        RegExp(regexp, RegExp.NONE)
        return regexp
      } catch (
          @Suppress("UNUSED_PARAMETER") e: Exception
      ) {
      }
    }
  }

  private fun randomRegexpString(r: Random): String {
    val end = r.nextInt(20)
    if (end == 0) {
      // allow 0 length
      return ""
    }
    val buffer = CharArray(end)
    var i = 0
    while (i < end) {
      val t = r.nextInt(15)
      if (t == 0 && i < end - 1) {
        // Make a surrogate pair
        // High surrogate
        buffer[i++] = r.nextInt(0xd800, 0xdbff + 1).toChar()
        // Low surrogate
        buffer[i] = r.nextInt(0xdc00, 0xdfff + 1).toChar()
      } else if (t <= 1) buffer[i] = r.nextInt(0x80).toChar()
      else if (t == 2) buffer[i] = r.nextInt(0x80, 0x800).toChar()
      else if (t == 3) buffer[i] = r.nextInt(0x800, 0xd7ff + 1).toChar()
      else if (t == 4) buffer[i] = r.nextInt(0xe000, 0xffff + 1).toChar()
      else if (t == 5) buffer[i] = '.'
      else if (t == 6) buffer[i] = '?'
      else if (t == 7) buffer[i] = '*'
      else if (t == 8) buffer[i] = '+'
      else if (t == 9) buffer[i] = '('
      else if (t == 10) buffer[i] = ')'
      else if (t == 11) buffer[i] = '-'
      else if (t == 12) buffer[i] = '['
      else if (t == 13) buffer[i] = ']'
      else if (t == 14) buffer[i] = '|'
      i++
    }
    return String.fromCharArray(buffer, 0, end)
  }

  /**
   * picks a random int code point, avoiding surrogates; throws IllegalArgumentException if this
   * transition only accepts surrogates
   */
  private fun getRandomCodePoint(r: Random, min: Int, max: Int): Int {
    val code: Int
    if (max < UnicodeUtil.UNI_SUR_HIGH_START || min > UnicodeUtil.UNI_SUR_HIGH_END) {
      // easy: entire range is before or after surrogates
      code = min + r.nextInt(max - min + 1)
    } else if (min >= UnicodeUtil.UNI_SUR_HIGH_START) {
      if (max > UnicodeUtil.UNI_SUR_LOW_END) {
        // after surrogates
        code = 1 + UnicodeUtil.UNI_SUR_LOW_END + r.nextInt(max - UnicodeUtil.UNI_SUR_LOW_END)
      } else {
        throw IllegalArgumentException("transition accepts only surrogates: min=$min max=$max")
      }
    } else if (max <= UnicodeUtil.UNI_SUR_LOW_END) {
      if (min < UnicodeUtil.UNI_SUR_HIGH_START) {
        // before surrogates
        code = min + r.nextInt(UnicodeUtil.UNI_SUR_HIGH_START - min)
      } else {
        throw IllegalArgumentException("transition accepts only surrogates: min=$min max=$max")
      }
    } else {
      // range includes all surrogates
      val gap1 = UnicodeUtil.UNI_SUR_HIGH_START - min
      val gap2 = max - UnicodeUtil.UNI_SUR_LOW_END
      val c = r.nextInt(gap1 + gap2)
      code =
          if (c < gap1) {
            min + c
          } else {
            UnicodeUtil.UNI_SUR_LOW_END + c - gap1 + 1
          }
    }

    assert(
        code >= min &&
            code <= max &&
            (code < UnicodeUtil.UNI_SUR_HIGH_START || code > UnicodeUtil.UNI_SUR_LOW_END)) {
      "code=$code min=$min max=$max"
    }
    return code
  }

  /**
   * Lets you retrieve random strings accepted by an Automaton.
   *
   * Once created, call [getRandomAcceptedString] to get a new string (in UTF-32 codepoints).
   */
  class RandomAcceptedStrings(private val a: Automaton) {

    private val leadsToAccept: MutableMap<Transition, Boolean>
    private val transitions: Array<Array<Transition>>

    private data class ArrivingTransition(val from: Int, val t: Transition)

    init {
      if (a.numStates == 0) {
        throw IllegalArgumentException("this automaton accepts nothing")
      }
      this.transitions = a.sortedTransitions

      leadsToAccept = mutableMapOf()
      val allArriving = mutableMapOf<Int, MutableList<ArrivingTransition>>()

      val q = ArrayDeque<Int>()
      val seen = mutableSetOf<Int>()

      // reverse map the transitions, so we can quickly look
      // up all arriving transitions to a given state
      val numStates = a.numStates
      for (s in 0 until numStates) {
        for (t in transitions[s]) {
          val tl = allArriving.getOrPut(t.dest) { mutableListOf() }
          tl.add(ArrivingTransition(s, t))
        }
        if (a.isAccept(s)) {
          q.add(s)
          seen.add(s)
        }
      }

      // Breadth-first search, from accept states,
      // backwards:
      while (q.isNotEmpty()) {
        val s = q.removeFirst()
        val arriving = allArriving[s]
        if (arriving != null) {
          for (at in arriving) {
            val from = at.from
            if (!seen.contains(from)) {
              q.add(from)
              seen.add(from)
              leadsToAccept[at.t] = true
            }
          }
        }
      }
    }

    fun getRandomAcceptedString(r: Random): IntArray {
      var codePoints = IntArray(0)
      var codepointCount = 0

      var s = 0

      while (true) {

        if (a.isAccept(s)) {
          if (a.getNumTransitions(s) == 0) {
            // stop now
            break
          } else {
            if (r.nextBoolean()) {
              break
            }
          }
        }

        if (a.getNumTransitions(s) == 0) {
          throw RuntimeException("this automaton has dead states")
        }

        val cheat = r.nextBoolean()

        val t: Transition
        if (cheat) {
          // pick a transition that we know is the fastest
          // path to an accept state
          val toAccept = mutableListOf<Transition>()
          for (t0 in transitions[s]) {
            if (leadsToAccept.containsKey(t0)) {
              toAccept.add(t0)
            }
          }
          t =
              if (toAccept.isEmpty()) {
                // this is OK -- it means we jumped into a cycle
                transitions[s][r.nextInt(transitions[s].size)]
              } else {
                toAccept[r.nextInt(toAccept.size)]
              }
        } else {
          t = transitions[s][r.nextInt(transitions[s].size)]
        }
        codePoints = ArrayUtil.grow(codePoints, codepointCount + 1)
        codePoints[codepointCount++] = getRandomCodePoint(r, t.min, t.max)
        s = t.dest
      }
      return ArrayUtil.copyOfSubArray(codePoints, 0, codepointCount)
    }
  }

  private fun randomSingleAutomaton(random: Random): Automaton {
    while (true) {
      try {
        var a1 = RegExp(randomRegexp(random), RegExp.NONE).toAutomaton()!!
        if (random.nextBoolean()) {
          a1 = Operations.complement(a1, DEFAULT_MAX_DETERMINIZED_STATES)
        }
        return a1
      } catch (
          @Suppress("UNUSED_PARAMETER") tctde: TooComplexToDeterminizeException
      ) { // This can (rarely) happen if the random regexp is too hard; just try again...
      }
    }
  }

  /** return a random NFA/DFA for testing */
  fun randomAutomaton(random: Random): Automaton {
    // get two random Automata from regexps
    val a1 = randomSingleAutomaton(random)
    val a2 = randomSingleAutomaton(random)

    // combine them in random ways
    return when (random.nextInt(4)) {
      0 -> Operations.concatenate(mutableListOf(a1, a2))
      1 -> Operations.union(mutableListOf(a1, a2))
      2 -> Operations.intersection(a1, a2)
      else -> Operations.minus(a1, a2, DEFAULT_MAX_DETERMINIZED_STATES)
    }
  }

  /**
   * Original brics implementation of reverse(). It tries to satisfy multiple use-cases by
   * populating a set of initial states too.
   */
  fun reverseOriginal(a: Automaton, initialStates: MutableSet<Int>?): Automaton {

    if (Operations.isEmpty(a)) {
      return Automaton()
    }

    val numStates = a.numStates

    // Build a new automaton with all edges reversed
    val builder = Automaton.Builder()

    // Initial node; we'll add epsilon transitions in the end:
    builder.createState()

    for (s in 0 until numStates) {
      builder.createState()
    }

    // Old initial state becomes new accept state:
    builder.setAccept(1, true)

    val t = Transition()
    for (s in 0 until numStates) {
      val numTransitions = a.getNumTransitions(s)
      a.initTransition(s, t)
      for (i in 0 until numTransitions) {
        a.getNextTransition(t)
        builder.addTransition(t.dest + 1, s + 1, t.min, t.max)
      }
    }

    val result = builder.finish()

    var s = 0
    val acceptStates = a.acceptStates
    while (s < numStates) {
      s = acceptStates.nextSetBit(s)
      if (s == -1) break
      result.addEpsilon(0, s + 1)
      initialStates?.add(s + 1)
      s++
    }

    result.finishState()

    return result
  }

  /** Simple, original brics implementation of Brzozowski minimize() */
  fun minimizeSimple(a: Automaton): Automaton {
    var automaton = a
    val initialSet = mutableSetOf<Int>()
    automaton = determinizeSimple(reverseOriginal(automaton, initialSet), initialSet)
    initialSet.clear()
    automaton = determinizeSimple(reverseOriginal(automaton, initialSet), initialSet)
    return automaton
  }

  /** Simple, original brics implementation of determinize() */
  fun determinizeSimple(a: Automaton): Automaton {
    val initialset = mutableSetOf<Int>()
    initialset.add(0)
    return determinizeSimple(a, initialset)
  }

  /**
   * Simple, original brics implementation of determinize() Determinizes the given automaton using
   * the given set of initial states.
   */
  fun determinizeSimple(a: Automaton, initialset: Set<Int>): Automaton {
    if (a.numStates == 0) {
      return a
    }
    val points = a.getStartPoints()
    // subset construction
    val sets = mutableMapOf<Set<Int>, Set<Int>>()
    val worklist = ArrayDeque<Set<Int>>()
    val newstate = mutableMapOf<Set<Int>, Int>()
    sets[initialset] = initialset
    worklist.add(initialset)
    val result = Automaton.Builder()
    result.createState()
    newstate[initialset] = 0
    val t = Transition()
    while (worklist.isNotEmpty()) {
      val s = worklist.removeFirst()
      val r = newstate[s]!!
      for (q in s) {
        if (a.isAccept(q)) {
          result.setAccept(r, true)
          break
        }
      }
      for (n in points.indices) {
        val p = mutableSetOf<Int>()
        for (q in s) {
          val count = a.initTransition(q, t)
          for (i in 0 until count) {
            a.getNextTransition(t)
            if (t.min <= points[n] && points[n] <= t.max) {
              p.add(t.dest)
            }
          }
        }

        if (!sets.containsKey(p)) {
          sets[p] = p
          worklist.add(p)
          newstate[p] = result.createState()
        }
        val q = newstate[p]!!
        val min = points[n]
        val max =
            if (n + 1 < points.size) {
              points[n + 1] - 1
            } else {
              0x10FFFF // Character.MAX_CODE_POINT
            }
        result.addTransition(r, q, min, max)
      }
    }

    return Operations.removeDeadStates(result.finish())
  }

  /** Returns true if the automaton is deterministic. */
  fun isDeterministicSlow(a: Automaton): Boolean {
    val t = Transition()
    val numStates = a.numStates
    for (s in 0 until numStates) {
      val count = a.initTransition(s, t)
      var lastMax = -1
      for (i in 0 until count) {
        a.getNextTransition(t)
        if (t.min <= lastMax) {
          assert(!a.isDeterministic)
          return false
        }
        lastMax = t.max
      }
    }

    assert(a.isDeterministic)
    return true
  }

  /**
   * Returns true if these two automata accept exactly the same language. This is a costly
   * computation! Both automata must be determinized and have no dead states!
   */
  fun sameLanguage(a1: Automaton, a2: Automaton): Boolean {
    if (a1 === a2) {
      return true
    }
    return subsetOf(a2, a1) && subsetOf(a1, a2)
  }

  /**
   * Returns true if the language of `a1` is a subset of the language of `a2`. Both automata must be
   * determinized and must have no dead states.
   *
   * Complexity: quadratic in number of states.
   */
  fun subsetOf(a1: Automaton, a2: Automaton): Boolean {
    if (!a1.isDeterministic) {
      throw IllegalArgumentException("a1 must be deterministic")
    }
    if (!a2.isDeterministic) {
      throw IllegalArgumentException("a2 must be deterministic")
    }
    assert(!Operations.hasDeadStatesFromInitial(a1))
    assert(!Operations.hasDeadStatesFromInitial(a2))
    if (a1.numStates == 0) {
      // Empty language is always a subset of any other language
      return true
    } else if (a2.numStates == 0) {
      return Operations.isEmpty(a1)
    }

    // TODO: cutover to iterators instead
    val transitions1 = a1.sortedTransitions
    val transitions2 = a2.sortedTransitions
    val worklist = ArrayDeque<StatePair>()
    val visited = mutableSetOf<StatePair>()
    var p = StatePair(0, 0)
    worklist.add(p)
    visited.add(p)
    while (worklist.isNotEmpty()) {
      p = worklist.removeFirst()
      if (a1.isAccept(p.s1) && !a2.isAccept(p.s2)) {
        return false
      }
      val t1 = transitions1[p.s1]
      val t2 = transitions2[p.s2]
      var b2 = 0
      for (n1 in t1.indices) {
        while (b2 < t2.size && t2[b2].max < t1[n1].min) {
          b2++
        }
        var min1 = t1[n1].min
        var max1 = t1[n1].max

        var n2 = b2
        while (n2 < t2.size && t1[n1].max >= t2[n2].min) {
          if (t2[n2].min > min1) {
            return false
          }
          if (t2[n2].max < 0x10FFFF) // Character.MAX_CODE_POINT
          {
            min1 = t2[n2].max + 1
          } else {
            min1 = 0x10FFFF // Character.MAX_CODE_POINT
            max1 = 0 // Character.MIN_CODE_POINT
          }
          val q = StatePair(t1[n1].dest, t2[n2].dest)
          if (!visited.contains(q)) {
            worklist.add(q)
            visited.add(q)
          }
          n2++
        }
        if (min1 <= max1) {
          return false
        }
      }
    }
    return true
  }

  fun assertCleanDFA(a: Automaton) {
    assertCleanNFA(a)
    assertTrue(a.isDeterministic, "must be deterministic")
  }

  fun assertMinimalDFA(a: Automaton) {
    assertCleanDFA(a)
    val minimized = minimizeSimple(a)
    assertEquals(minimized.numStates, a.numStates)
  }

  fun assertCleanNFA(a: Automaton) {
    assertFalse(Operations.hasDeadStatesFromInitial(a), "has dead states reachable from initial")
    assertFalse(Operations.hasDeadStatesToAccept(a), "has dead states leading to accept")
    assertFalse(Operations.hasDeadStates(a), "has unreachable dead states (ghost states)")
  }

  /**
   * Checks that an automaton has no detached states that are unreachable from the initial state.
   */
  fun assertNoDetachedStates(a: Automaton) {
    val a2 = Operations.removeDeadStates(a)
    assert(a.numStates == a2.numStates) {
      "automaton has ${a.numStates - a2.numStates} detached states"
    }
  }

  fun isFinite(a: Automaton): Boolean {
    if (a.numStates == 0) return true
    return isFinite(Transition(), a, 0, BitSet(a.numStates), BitSet(a.numStates), 0)
  }

  private fun isFinite(
      scratch: Transition,
      a: Automaton,
      state: Int,
      path: BitSet,
      visited: BitSet,
      level: Int
  ): Boolean {
    if (level > MAX_RECURSION_LEVEL) {
      throw IllegalArgumentException("input automaton is too large: $level")
    }
    path.set(state)
    val numTransitions = a.initTransition(state, scratch)
    for (i in 0 until numTransitions) {
      a.getTransition(state, i, scratch)
      if (path.get(scratch.dest) ||
          (!visited.get(scratch.dest) &&
              !isFinite(scratch, a, scratch.dest, path, visited, level + 1))) {
        return false
      }
    }
    path.clear(state)
    visited.set(state)
    return true
  }

  /**
   * Simple, original implementation of getFiniteStrings.
   *
   * Returns the set of accepted strings, assuming that at most `limit` strings are accepted. If
   * more than `limit` strings are accepted, the first limit strings found are returned. If
   * `limit`<0, then the limit is infinite.
   *
   * This implementation is recursive: it uses one stack frame for each digit in the returned
   * strings (ie, max is the max length returned string).
   */
  fun getFiniteStringsRecursive(a: Automaton, limit: Int): Set<IntsRef> {
    val strings = mutableSetOf<IntsRef>()
    if (a.isAccept(0)) {
      strings.add(IntsRef())
      if (limit == 0) return strings
    }
    if (!getFiniteStrings(a, 0, mutableSetOf(), strings, IntsRefBuilder(), limit)) {
      return strings
    }
    return strings
  }

  /**
   * Returns the strings that can be produced from the given state, or false if more than `limit`
   * strings are found. `limit`<0 means "infinite".
   */
  fun getFiniteStrings(
      a: Automaton,
      s: Int,
      pathstates: MutableSet<Int>,
      strings: MutableSet<IntsRef>,
      path: IntsRefBuilder,
      limit: Int
  ): Boolean {
    pathstates.add(s)
    val t = Transition()
    val count = a.initTransition(s, t)
    for (i in 0 until count) {
      a.getNextTransition(t)
      if (pathstates.contains(t.dest)) {
        return false
      }
      for (n in t.min..t.max) {
        path.append(n)
        if (a.isAccept(t.dest)) {
          strings.add(path.toIntsRef())
          if (limit >= 0 && strings.size > limit) {
            return false
          }
        }
        if (!getFiniteStrings(a, t.dest, pathstates, strings, path, limit)) {
          return false
        }
        path.setLength(path.length() - 1)
      }
    }
    pathstates.remove(s)
    return true
  }

  fun assertMatches(a: Automaton, vararg strings: String) {
    val expected = mutableSetOf<IntsRef>()
    val builder = IntsRefBuilder()
    for (s in strings) {
      builder.clear()
      Util.toUTF32(s, builder)
      expected.add(builder.toIntsRef())
    }
    assertTrue(isFinite(a), "automaton must be finite")
    assertEquals(expected, getFiniteStringsRecursive(a, -1))
  }
}

// TODO adding this for compile/test to pass. replace with test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/jdkport/Assert.kt in main branch when merged
private fun assert(
    condition: Boolean,
    message: () -> String? = { "Assertion failed" }
) {
  if (!condition) {
    throw AssertionError(message())
  }
}

@OptIn(ExperimentalNativeApi::class)
class RandomAcceptedStrings(private val a: Automaton) {
    private val leadsToAccept = mutableMapOf<Transition, Boolean>()
    private val transitions: Array<Array<Transition>> = a.sortedTransitions

    init {
        if (a.numStates == 0) {
            throw IllegalArgumentException("this automaton accepts nothing")
        }
        val allArriving = mutableMapOf<Int, MutableList<ArrivingTransition>>()
        val q = ArrayDeque<Int>()
        val seen = HashSet<Int>()
        val numStates = a.numStates
        for (s in 0 until numStates) {
            for (t in transitions[s]) {
                val tl = allArriving.getOrPut(t.dest) { mutableListOf() }
                tl.add(ArrivingTransition(s, t))
            }
            if (a.isAccept(s)) {
                q.add(s)
                seen.add(s)
            }
        }
        while (q.isNotEmpty()) {
            val s = q.removeFirst()
            val arriving = allArriving[s]
            if (arriving != null) {
                for (at in arriving) {
                    val from = at.from
                    if (!seen.contains(from)) {
                        q.add(from)
                        seen.add(from)
                        leadsToAccept[at.t] = true
                    }
                }
            }
        }
    }

    fun getRandomAcceptedString(r: Random): IntArray {
        var codePoints = IntArray(0)
        var codepointCount = 0
        var s = 0
        while (true) {
            if (a.isAccept(s)) {
                if (a.getNumTransitions(s) == 0) {
                    break
                } else if (r.nextBoolean()) {
                    break
                }
            }
            if (a.getNumTransitions(s) == 0) {
                throw RuntimeException("this automaton has dead states")
            }
            val cheat = r.nextBoolean()
            val t: Transition = if (cheat) {
                val toAccept = mutableListOf<Transition>()
                for (t0 in transitions[s]) {
                    if (leadsToAccept.containsKey(t0)) {
                        toAccept.add(t0)
                    }
                }
                if (toAccept.isEmpty()) {
                    transitions[s][r.nextInt(transitions[s].size)]
                } else {
                    toAccept[r.nextInt(toAccept.size)]
                }
            } else {
                transitions[s][r.nextInt(transitions[s].size)]
            }
            codePoints = ArrayUtil.grow(codePoints, codepointCount + 1)
            codePoints[codepointCount++] = getRandomCodePoint(r, t.min, t.max)
            s = t.dest
        }
        return ArrayUtil.copyOfSubArray(codePoints, 0, codepointCount)
    }

    private fun getRandomCodePoint(r: Random, min: Int, max: Int): Int {
        val code: Int
        if (max < UnicodeUtil.UNI_SUR_HIGH_START || min > UnicodeUtil.UNI_SUR_HIGH_END) {
            code = min + r.nextInt(max - min + 1)
        } else if (min >= UnicodeUtil.UNI_SUR_HIGH_START) {
            if (max > UnicodeUtil.UNI_SUR_LOW_END) {
                code = 1 + UnicodeUtil.UNI_SUR_LOW_END + r.nextInt(max - UnicodeUtil.UNI_SUR_LOW_END)
            } else {
                throw IllegalArgumentException("transition accepts only surrogates: min=$min max=$max")
            }
        } else if (max <= UnicodeUtil.UNI_SUR_LOW_END) {
            if (min < UnicodeUtil.UNI_SUR_HIGH_START) {
                code = min + r.nextInt(UnicodeUtil.UNI_SUR_HIGH_START - min)
            } else {
                throw IllegalArgumentException("transition accepts only surrogates: min=$min max=$max")
            }
        } else {
            val gap1 = UnicodeUtil.UNI_SUR_HIGH_START - min
            val gap2 = max - UnicodeUtil.UNI_SUR_LOW_END
            val c = r.nextInt(gap1 + gap2)
            code = if (c < gap1) {
                min + c
            } else {
                UnicodeUtil.UNI_SUR_LOW_END + c - gap1 + 1
            }
        }
        assert(code >= min && code <= max && (code < UnicodeUtil.UNI_SUR_HIGH_START || code > UnicodeUtil.UNI_SUR_LOW_END))
        return code
    }

    private class ArrivingTransition(val from: Int, val t: Transition)
}
