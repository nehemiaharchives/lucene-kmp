/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TestLevenshteinAutomata : LuceneTestCase() {

    @Test
    fun testLev0() {
        assertLev("", 0)
        assertCharVectors(0)
    }

    @Test
    fun testLev1() {
        assertLev("", 1)
        assertCharVectors(1)
    }

    @Test
    fun testLev2() {
        assertLev("", 2)
        assertCharVectors(2)
    }

    // LUCENE-3094
    @Test
    fun testNoWastedStates() {
        assertFalse(
            Operations.hasDeadStatesFromInitial(LevenshteinAutomata("abc", false).toAutomaton(1)!!)
        )
    }

    /**
     * Tests all possible characteristic vectors for some n This exhaustively tests the parametric
     * transitions tables.
     */
    private fun assertCharVectors(n: Int) {
        val k = 2 * n + 1
        // use k + 2 as the exponent: the formula generates different transitions
        // for w, w-1, w-2
        val limit = 2.0.pow(k + 2).toInt()
        for (i in 0 until limit) {
            val encoded = i.toString(2)
            assertLev(encoded, n)
        }
    }

    /** Builds a DFA for some string, and checks all Lev automata up to some maximum distance. */
    private fun assertLev(s: String, maxDistance: Int) {
        val builder = LevenshteinAutomata(s, false)
        val tbuilder = LevenshteinAutomata(s, true)
        val automata = arrayOfNulls<Automaton>(maxDistance + 1)
        val tautomata = arrayOfNulls<Automaton>(maxDistance + 1)
        for (n in automata.indices) {
            automata[n] = builder.toAutomaton(n)
            tautomata[n] = tbuilder.toAutomaton(n)
            assertNotNull(automata[n])
            assertNotNull(tautomata[n])
            assertTrue(automata[n]!!.isDeterministic)
            assertTrue(tautomata[n]!!.isDeterministic)
            assertTrue(AutomatonTestUtil.isFinite(automata[n]!!))
            assertTrue(AutomatonTestUtil.isFinite(tautomata[n]!!))
            assertFalse(Operations.hasDeadStatesFromInitial(automata[n]!!))
            assertFalse(Operations.hasDeadStatesFromInitial(tautomata[n]!!))
            // check that the dfa for n-1 accepts a subset of the dfa for n
            if (n > 0) {
                assertTrue(
                    AutomatonTestUtil.subsetOf(
                        Operations.removeDeadStates(automata[n - 1]!!),
                        Operations.removeDeadStates(automata[n]!!)
                    )
                )
                assertTrue(
                    AutomatonTestUtil.subsetOf(
                        Operations.removeDeadStates(automata[n - 1]!!),
                        Operations.removeDeadStates(tautomata[n]!!)
                    )
                )
                assertTrue(
                    AutomatonTestUtil.subsetOf(
                        Operations.removeDeadStates(tautomata[n - 1]!!),
                        Operations.removeDeadStates(automata[n]!!)
                    )
                )
                assertTrue(
                    AutomatonTestUtil.subsetOf(
                        Operations.removeDeadStates(tautomata[n - 1]!!),
                        Operations.removeDeadStates(tautomata[n]!!)
                    )
                )
                assertNotSame(automata[n - 1], automata[n])
            }
            // check that Lev(N) is a subset of LevT(N)
            assertTrue(
                AutomatonTestUtil.subsetOf(
                    Operations.removeDeadStates(automata[n]!!),
                    Operations.removeDeadStates(tautomata[n]!!)
                )
            )
            // special checks for specific n
            when (n) {
                0 -> {
                    // easy, matches the string itself
                    assertTrue(
                        AutomatonTestUtil.sameLanguage(
                            Automata.makeString(s),
                            Operations.removeDeadStates(automata[0]!!)
                        )
                    )
                    assertTrue(
                        AutomatonTestUtil.sameLanguage(
                            Automata.makeString(s),
                            Operations.removeDeadStates(tautomata[0]!!)
                        )
                    )
                }

                1 -> {
                    // generate a lev1 naively, and check the accepted lang is the same.
                    assertTrue(
                        AutomatonTestUtil.sameLanguage(
                            naiveLev1(s),
                            Operations.removeDeadStates(automata[1]!!)
                        )
                    )
                    assertTrue(
                        AutomatonTestUtil.sameLanguage(
                            naiveLev1T(s),
                            Operations.removeDeadStates(tautomata[1]!!)
                        )
                    )
                }

                else -> {
                    assertBruteForce(s, automata[n]!!, n)
                    assertBruteForceT(s, tautomata[n]!!, n)
                }
            }
        }
    }

    /**
     * Return an automaton that accepts all 1-character insertions, deletions, and substitutions of s.
     */
    private fun naiveLev1(s: String): Automaton {
        return Operations.determinize(
            Operations.union(
                listOf(
                    Automata.makeString(s),
                    insertionsOf(s),
                    deletionsOf(s),
                    substitutionsOf(s)
                )
            ),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
    }

    /**
     * Return an automaton that accepts all 1-character insertions, deletions, substitutions, and
     * transpositions of s.
     */
    private fun naiveLev1T(s: String): Automaton {
        return Operations.determinize(
            Operations.union(
                listOf(
                    Automata.makeString(s),
                    insertionsOf(s),
                    deletionsOf(s),
                    substitutionsOf(s),
                    transpositionsOf(s)
                )
            ),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
    }

    /** Return an automaton that accepts all 1-character insertions of s (inserting one character) */
    private fun insertionsOf(s: String): Automaton {
        val list = mutableListOf<Automaton>()

        for (i in 0..s.length) {
            val pre = Automata.makeString(s.substring(0, i))
            val middle = Automata.makeAnyChar()
            val post = Automata.makeString(s.substring(i))
            list.add(Operations.concatenate(mutableListOf(pre, middle, post)))
        }

        return Operations.union(list)
    }

    /** Return an automaton that accepts all 1-character deletions of s (deleting one character). */
    private fun deletionsOf(s: String): Automaton {
        val list = mutableListOf<Automaton>()

        for (i in 0 until s.length) {
            val pre = Automata.makeString(s.substring(0, i))
            val post = Automata.makeString(s.substring(i + 1))
            list.add(Operations.concatenate(mutableListOf(pre, post)))
        }

        return Operations.union(list)
    }

    /**
     * Return an automaton that accepts all 1-character substitutions of s (replacing one character)
     */
    private fun substitutionsOf(s: String): Automaton {
        val list = mutableListOf<Automaton>()

        for (i in 0 until s.length) {
            val pre = Automata.makeString(s.substring(0, i))
            val middle = Automata.makeAnyChar()
            val post = Automata.makeString(s.substring(i + 1))
            list.add(Operations.concatenate(mutableListOf(pre, middle, post)))
        }

        return Operations.union(list)
    }

    /**
     * Return an automaton that accepts all transpositions of s (transposing two adjacent characters)
     */
    private fun transpositionsOf(s: String): Automaton {
        if (s.length < 2) {
            return Automata.makeEmpty()
        }
        val list = mutableListOf<Automaton>()
        for (i in 0 until s.length - 1) {
            val sb = StringBuilder()
            sb.append(s, 0, i)
            sb.append(s[i + 1])
            sb.append(s[i])
            sb.append(s, i + 2, s.length)
            val st = sb.toString()
            if (st != s) {
                list.add(Automata.makeString(st))
            }
        }
        return Operations.union(list)
    }

    private fun assertBruteForce(input: String, dfa: Automaton, distance: Int) {
        val ra = CharacterRunAutomaton(dfa)
        val maxLen = input.length + distance + 1
        val maxNum = 2.0.pow(maxLen).toInt()
        for (i in 0 until maxNum) {
            val encoded = i.toString(2)
            val accepts = ra.run(encoded)
            if (accepts) {
                assertTrue(getDistance(input, encoded) <= distance)
            } else {
                assertTrue(getDistance(input, encoded) > distance)
            }
        }
    }

    private fun assertBruteForceT(input: String, dfa: Automaton, distance: Int) {
        val ra = CharacterRunAutomaton(dfa)
        val maxLen = input.length + distance + 1
        val maxNum = 2.0.pow(maxLen).toInt()
        for (i in 0 until maxNum) {
            val encoded = i.toString(2)
            val accepts = ra.run(encoded)
            if (accepts) {
                assertTrue(getTDistance(input, encoded) <= distance)
            } else {
                assertTrue(getTDistance(input, encoded) > distance)
            }
        }
    }

    // *****************************
    // Compute Levenshtein distance: see
    // org.apache.commons.lang.StringUtils#getLevenshteinDistance(String, String)
    // *****************************
    private fun getDistance(target: String, other: String): Int {
        val sa: CharArray
        val n: Int
        var p: IntArray // 'previous' cost array, horizontally
        var d: IntArray // cost array, horizontally
        var swap: IntArray // placeholder to assist in swapping p and d

        sa = target.toCharArray()
        n = sa.size
        p = IntArray(n + 1)
        d = IntArray(n + 1)

        val m = other.length
        if (n == 0 || m == 0) {
            return if (n == m) {
                0
            } else {
                max(n, m)
            }
        }

        // indexes into strings s and t
        var i: Int // iterates through s
        var j: Int // iterates through t

        var tJ: Char // jth character of t

        var cost: Int // cost

        for (x in 0..n) {
            p[x] = x
        }

        for (y in 1..m) {
            tJ = other[y - 1]
            d[0] = y

            for (x in 1..n) {
                cost = if (sa[x - 1] == tJ) 0 else 1
                // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
                d[x] = min(min(d[x - 1] + 1, p[x] + 1), p[x - 1] + cost)
            }

            // copy current distance counts to 'previous row' distance counts
            swap = p
            p = d
            d = swap
        }

        // our last action in the above loop was to switch d and p, so p now
        // actually has the most recent cost counts
        return abs(p[n])
    }

    private fun getTDistance(target: String, other: String): Int {
        val sa: CharArray
        val n: Int
        val d: Array<IntArray> // cost array

        sa = target.toCharArray()
        n = sa.size
        val m = other.length
        d = Array(n + 1) { IntArray(m + 1) }

        if (n == 0 || m == 0) {
            return if (n == m) {
                0
            } else {
                max(n, m)
            }
        }

        // indexes into strings s and t
        var i: Int // iterates through s
        var j: Int // iterates through t

        var tJ: Char // jth character of t

        var cost: Int // cost

        for (x in 0..n) {
            d[x][0] = x
        }

        for (y in 0..m) {
            d[0][y] = y
        }

        for (y in 1..m) {
            tJ = other[y - 1]

            for (x in 1..n) {
                cost = if (sa[x - 1] == tJ) 0 else 1
                // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
                d[x][y] = min(min(d[x - 1][y] + 1, d[x][y - 1] + 1), d[x - 1][y - 1] + cost)
                // transposition
                if (
                    x > 1 &&
                    y > 1 &&
                    target[x - 1] == other[y - 2] &&
                    target[x - 2] == other[y - 1]
                ) {
                    d[x][y] = min(d[x][y], d[x - 2][y - 2] + cost)
                }
            }
        }

        // our last action in the above loop was to switch d and p, so p now
        // actually has the most recent cost counts
        return abs(d[n][m])
    }
}
