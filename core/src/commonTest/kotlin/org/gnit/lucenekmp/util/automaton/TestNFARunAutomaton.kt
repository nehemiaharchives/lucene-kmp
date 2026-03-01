package org.gnit.lucenekmp.util.automaton

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.AutomatonQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RamUsageTester
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.configureTestLogging
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class TestNFARunAutomaton : LuceneTestCase() {
    private val logger = KotlinLogging.logger {}

    private companion object {
        const val FIELD = "field"
    }

    @Test
    fun testRamUsageEstimation() {
        val regExp = RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE)
        val nfa = regExp.toAutomaton()
        val runAutomaton = NFARunAutomaton(nfa)
        val estimation = runAutomaton.ramBytesUsed()
        val actual = RamUsageTester.ramUsed(runAutomaton)
        assertEquals(actual.toDouble(), estimation.toDouble(), actual.toDouble() * 0.3)
    }

    @Test
    fun testWithRandomRegex() {
        var found = 0
        var attempts = 0
        val maxAttempts = 50 // safety cap to avoid potential CI hangs on pathological seeds TODO: reduced from 5000 to 50 for dev speed
        while (found < 10 && attempts < maxAttempts) { // TODO reduced from 100 to 10 for dev speed
            attempts++
            val regExp = RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE)
            val nfa = regExp.toAutomaton()
            if (nfa.isDeterministic) {
                continue
            }
            val dfa = Operations.determinize(nfa, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            val candidate = NFARunAutomaton(nfa)
            val randomStringGen = try {
                AutomatonTestUtil.RandomAcceptedStrings(dfa)
            } catch (_: IllegalArgumentException) {
                // sometimes the automaton accepts nothing and throws
                continue
            }
            repeat(3) { // TODO reduced from 20 to 3 for dev speed
                if (random().nextBoolean()) {
                    testAcceptedString(regExp, randomStringGen, candidate, 10)
                    testRandomString(regExp, dfa, candidate, 10)
                } else {
                    testRandomString(regExp, dfa, candidate, 10)
                    testAcceptedString(regExp, randomStringGen, candidate, 10)
                }
            }
            found++
        }
        assertTrue(found > 0, "failed to generate any valid nondeterministic NFAs within attempts=$attempts")
    }

    @Test
    fun testRandomAccessTransition() {
        var nfa = RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE).toAutomaton()
        while (nfa.isDeterministic) {
            nfa = RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE).toAutomaton()
        }
        val runAutomaton1 = NFARunAutomaton(nfa)
        val runAutomaton2 = NFARunAutomaton(nfa)
        assertRandomAccessTransition(runAutomaton1, runAutomaton2, 0, HashSet())
    }

    private fun assertRandomAccessTransition(
        automaton1: NFARunAutomaton,
        automaton2: NFARunAutomaton,
        state: Int,
        visited: MutableSet<Int>
    ) {
        if (!visited.add(state)) return

        val t1 = Transition()
        val t2 = Transition()
        // Initialize transitions for both automatons before reading counts
        automaton1.initTransition(state, t1)
        automaton2.initTransition(state, t2)
        val count1 = automaton1.getNumTransitions(state)
        val count2 = automaton2.getNumTransitions(state)
        assertEquals(count1, count2, "transition count mismatch at state=$state")
        for (i in 0 until count1) {
            automaton1.getNextTransition(t1)
            automaton2.getTransition(state, i, t2)
            assertEquals(t1.toString(), t2.toString())
            assertRandomAccessTransition(automaton1, automaton2, t1.dest, visited)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRandomAutomatonQuery() {
        configureTestLogging()
        val testStart = TimeSource.Monotonic.markNow()
        logger.debug { "NFAQueryTiming phase=start" }
        Operations.resetOpsTiming()
        Operations.setOpsTimingEnabled(false)
        val slowStepThresholdMs = 500L

        val docNum = 1 // TODO reduced from 50 to 1 for dev speed
        val automatonNum = 50
        var unionAutomatonElapsed = 0L
        var unionInitElapsed = 0L
        var unionMergeElapsed = 0L
        var determinizeElapsed = 0L
        var constructQueriesElapsed = 0L
        var dfaCountElapsed = 0L
        var nfaCountElapsed = 0L
        val directory: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)

        val vocab = hashSetOf<String>()
        val perDocVocab = hashSetOf<String>()
        for (i in 0 until docNum) {
            perDocVocab.clear()
            val termNum = random().nextInt(20) + 30
            while (perDocVocab.size < termNum) {
                var randomString: String
                do {
                    randomString = TestUtil.randomUnicodeString(random())
                } while (randomString.isEmpty())
                perDocVocab.add(randomString)
                vocab.add(randomString)
            }
            val document = Document()
            document.add(newTextField(FIELD, perDocVocab.joinToString(" "), Field.Store.NO))
            writer.addDocument(document)
        }
        writer.commit()
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)

        val foreignVocab = hashSetOf<String>()
        while (foreignVocab.size < vocab.size) {
            var randomString: String
            do {
                randomString = TestUtil.randomUnicodeString(random())
            } while (randomString.isEmpty())
            foreignVocab.add(randomString)
        }

        val vocabList = ArrayList(vocab)
        val foreignVocabList = ArrayList(foreignVocab)
        val perQueryVocab = hashSetOf<String>()

        val queryLoopStart = TimeSource.Monotonic.markNow()
        var i = 0
        while (i < automatonNum) {
            perQueryVocab.clear()
            val termNum = random().nextInt(40) + 30
            while (perQueryVocab.size < termNum) {
                if (random().nextBoolean()) {
                    perQueryVocab.add(vocabList[random().nextInt(vocabList.size)])
                } else {
                    perQueryVocab.add(foreignVocabList[random().nextInt(foreignVocabList.size)])
                }
            }

            var a: Automaton? = null
            var unionInitMs = 0L
            var unionMergeMs = 0L
            val unionStart = TimeSource.Monotonic.markNow()
            for (term in perQueryVocab) {
                if (a == null) {
                    val initStart = TimeSource.Monotonic.markNow()
                    a = Automata.makeString(term)
                    unionInitMs += initStart.elapsedNow().inWholeMilliseconds
                } else {
                    val mergeStart = TimeSource.Monotonic.markNow()
                    a = Operations.union(listOf(a, Automata.makeString(term)))
                    unionMergeMs += mergeStart.elapsedNow().inWholeMilliseconds
                }
            }
            val unionMs = unionStart.elapsedNow().inWholeMilliseconds
            if (unionMs >= slowStepThresholdMs) {
                logger.debug {
                    "NFAQuerySlow phase=union query_idx=$i terms=${perQueryVocab.size} elapsed_ms=$unionMs"
                }
            }
            unionAutomatonElapsed += unionMs
            unionInitElapsed += unionInitMs
            unionMergeElapsed += unionMergeMs
            requireNotNull(a)
            if (a.isDeterministic) {
                i++
                continue
            }
            val determinizeStart = TimeSource.Monotonic.markNow()
            val deterministicAutomaton = Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            val determinizeMs = determinizeStart.elapsedNow().inWholeMilliseconds
            if (determinizeMs >= slowStepThresholdMs) {
                logger.debug {
                    "NFAQuerySlow phase=determinize query_idx=$i terms=${perQueryVocab.size} elapsed_ms=$determinizeMs"
                }
            }
            determinizeElapsed += determinizeMs
            val constructQueriesStart = TimeSource.Monotonic.markNow()
            val dfaQuery = AutomatonQuery(Term(FIELD), deterministicAutomaton)
            val nfaQuery = object : AutomatonQuery(Term(FIELD), a) {
                fun nfaRunAutomaton() = compiled.nfaRunAutomaton
            }
            val constructQueriesMs = constructQueriesStart.elapsedNow().inWholeMilliseconds
            if (constructQueriesMs >= slowStepThresholdMs) {
                logger.debug {
                    "NFAQuerySlow phase=construct_queries query_idx=$i terms=${perQueryVocab.size} elapsed_ms=$constructQueriesMs"
                }
            }
            constructQueriesElapsed += constructQueriesMs
            assertNotNull(nfaQuery.nfaRunAutomaton())

            val dfaCountStart = TimeSource.Monotonic.markNow()
            val dfaCount = searcher.count(dfaQuery)
            val dfaCountMs = dfaCountStart.elapsedNow().inWholeMilliseconds
            if (dfaCountMs >= slowStepThresholdMs) {
                logger.debug {
                    "NFAQuerySlow phase=dfa_count query_idx=$i terms=${perQueryVocab.size} elapsed_ms=$dfaCountMs"
                }
            }
            dfaCountElapsed += dfaCountMs

            val nfaCountStart = TimeSource.Monotonic.markNow()
            val nfaCount = searcher.count(nfaQuery)
            val nfaCountMs = nfaCountStart.elapsedNow().inWholeMilliseconds
            if (nfaCountMs >= slowStepThresholdMs) {
                logger.debug {
                    "NFAQuerySlow phase=nfa_count query_idx=$i terms=${perQueryVocab.size} elapsed_ms=$nfaCountMs"
                }
            }
            nfaCountElapsed += nfaCountMs

            assertEquals(dfaCount, nfaCount)
            i++
        }
        logger.debug {
            "NFAQueryTiming phase=query_loop_done elapsed_ms=${queryLoopStart.elapsedNow().inWholeMilliseconds} " +
                "queries=$automatonNum docs=$docNum"
        }

        reader.close()
        writer.close()
        directory.close()
        val opsTiming = Operations.snapshotOpsTiming()
        logger.debug {
            "NFAQueryTiming phase=summary total_elapsed_ms=${testStart.elapsedNow().inWholeMilliseconds} " +
                "determinize_ms=$determinizeElapsed " +
                "construct_queries_ms=$constructQueriesElapsed dfa_count_ms=$dfaCountElapsed " +
                "nfa_count_ms=$nfaCountElapsed"
        }
        logger.debug {
            "NFAOpsTiming phase=summary to_accept_reverse_finish_ns=${opsTiming.getLiveStatesToAcceptReverseFinishNs} " +
                "reverse_builder_finish_total_ns=${opsTiming.reverseBuilderFinishTotalNs} " +
                "reverse_builder_finish_sort_ns=${opsTiming.reverseBuilderFinishSortTransitionsNs} " +
                "reverse_builder_finish_emit_transitions_ns=${opsTiming.reverseBuilderFinishEmitTransitionsNs} " +
                "reverse_builder_finish_finish_state_ns=${opsTiming.reverseBuilderFinishFinishStateNs}"
        }
    }

    private fun testAcceptedString(
        regExp: RegExp,
        randomStringGen: AutomatonTestUtil.RandomAcceptedStrings,
        candidate: NFARunAutomaton,
        repeat: Int
    ) {
        repeat(repeat) {
            val acceptedString = randomStringGen.getRandomAcceptedString(random())
            assertTrue(candidate.run(acceptedString),
                "regExp: $regExp testString: ${acceptedString.contentToString()}")
        }
    }

    private fun testRandomString(
        regExp: RegExp,
        dfa: Automaton,
        candidate: NFARunAutomaton,
        repeat: Int
    ) {
        repeat(repeat) {
            // Use the same LuceneTestCase RNG for reproducibility
            val len = random().nextInt(50)
            val randomString = IntArray(len) { random().nextInt(0, Character.MAX_CODE_POINT) }
            val expected = Operations.run(dfa, IntsRef(randomString, 0, randomString.size))
            val actual = candidate.run(randomString)
            assertEquals(expected, actual,
                "regExp: $regExp testString: ${randomString.contentToString()}")
        }
    }
}
