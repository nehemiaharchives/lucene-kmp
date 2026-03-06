package org.gnit.lucenekmp.search

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.search.RandomApproximationQuery
import org.gnit.lucenekmp.tests.search.SearchEquivalenceTestBase
import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.time.TimeSource
import kotlin.test.Test

/*
Native performance bottleneck track down status:

Done fix:
1. Hot-path `KotlinLogging.logger {}` creation inside `TermStates.build(...)`.
   - File: `core/src/commonMain/kotlin/org/gnit/lucenekmp/index/TermStates.kt`
   - Before:
     - JVM `testNestedExclusion`: about `657 ms`
     - Native `testNestedExclusion`: about `1m 4s`
   - After:
     - JVM: not remeasured here, expected effectively unchanged
     - Native `testNestedExclusion`: about `21.5 s`
   - Result: real improvement; native saved about `42.5 s`.

2. Per-instance logger creation in the sorted-search collector path.
   - Files:
     - `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/FieldValueHitQueue.kt`
     - `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/TopFieldCollector.kt`
   - Before:
     - JVM `testNestedExclusion`: about `2.2 s` suite, `471 ms` body
     - Native `testNestedExclusion`: about `29.0 s` suite, collector path showing:
       - `topFieldCollectorManager.newCollector queueMs ~32–76 ms`
       - `topFieldCollectorManager.newCollector collectorMs ~65–157 ms`
   - After:
     - JVM `testNestedExclusion`: about `3.0 s` suite, `516 ms` body
     - Native `testNestedExclusion`: about `3.0 s` suite, `364 ms` body
   - Result: real improvement; the collector hotspot was mostly logger allocation plus noisy debug logging inside queue/collector setup.

Tried but not improvement:
3. Comment out remaining high-volume zero-cost debug emitters in `AssertingIndexSearcher`, `AssertingWeight`, `RandomApproximationQuery`, and `IndexSearcher`.
   - Files:
     - `test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/search/AssertingIndexSearcher.kt`
     - `test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/search/AssertingWeight.kt`
     - `test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/search/RandomApproximationQuery.kt`
     - `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/IndexSearcher.kt`
   - Before:
     - JVM `testNestedExclusion`: about `657 ms`
     - Native `testNestedExclusion`: about `21.5 s`
   - After:
     - JVM: not remeasured here
     - Native `testNestedExclusion`: about `25.2 s`
   - Result: no credible win; this is not the current bottleneck.

Measured hotspot path:
4. `BooleanWeight.init` around `PhraseQuery`.
   - File: `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/BooleanWeight.kt`
   - JVM vs native:
     - JVM: effectively near `0–1 ms` at log granularity
     - Native phrase child: about `125–167 ms`
     - Native total boolean init: about `281–350 ms`
   - Status: measured hotspot; used to navigate deeper.

5. `PhraseQuery.getStats(...)`.
   - File: `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/PhraseQuery.kt`
   - JVM vs native:
     - JVM: effectively near `0 ms` at log threshold
     - Native: about `70–179 ms`
   - Internal native split:
     - `buildStatesMs` dominated
     - `termStatsMs = 0`
     - `simScorerMs = 0`
   - Status: measured hotspot; led directly to `TermStates.build(...)`.

6. `TermStates.build(...)`.
   - File: `core/src/commonMain/kotlin/org/gnit/lucenekmp/index/TermStates.kt`
   - JVM vs native:
     - JVM: not surfacing as expensive
     - Native: dominated `PhraseQuery.getStats`
   - Status: hotspot found; one real sub-bottleneck inside it was fixed, but remaining non-logging cost is still TODO.

7. Sorted-search helper path before collector fix.
   - Files:
     - `test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/search/SearchEquivalenceTestBase.kt`
     - `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/IndexSearcher.kt`
     - `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/TopFieldCollectorManager.kt`
   - JVM vs native before collector fix:
     - helper `search(...)` call on JVM: usually `0–5 ms`
     - helper `search(...)` call on Native: usually `95–120 ms`
     - `indexSearcher.search.sort searchMs` on Native: `100–176 ms`, outlier `235 ms`
     - `topFieldCollectorManager.newCollector` on Native:
       - `queueMs ~32–76 ms`
       - `collectorMs ~65–157 ms`
   - Status: this was the navigation point that led to the collector-path logger fix.

Looked hot before, mostly resolved now:
8. `IndexSearcher.search.setup -> createWeightMs`.
   - File: `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/IndexSearcher.kt`
   - Before cleanup:
     - JVM: mostly `0–4 ms`
     - Native: often `110–240 ms`
   - After `TermStates.build` logger fix:
     - JVM: unchanged
     - Native: mostly `0–3 ms`
   - Status: no longer the current bottleneck.

Not hotspots:
9. `BM25Similarity.scorer(...)`.
   - File: `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/similarities/BM25Similarity.kt`
   - JVM vs native:
     - JVM: no meaningful hotspot
     - Native: no meaningful hotspot
   - Status: ruled out.

10. `IndexSearcher.collectionStatistics(field)`, RNG split, random approximation advance, and scoring itself.
   - Files:
     - `core/src/commonMain/kotlin/org/gnit/lucenekmp/search/IndexSearcher.kt`
     - `test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/search/RandomApproximationQuery.kt`
   - JVM vs native:
     - JVM: negligible
     - Native: negligible or not dominant
   - Status: ruled out.

Current overall numbers:
11. Whole-method status.
   - Original real baseline:
     - JVM `testNestedExclusion`: about `657 ms`
     - Native `testNestedExclusion`: about `54–57 s`
   - After `TermStates.build` logger fix:
     - JVM: about same
     - Native: about `21.5 s`
   - After collector-path logger fix:
     - JVM `testNestedExclusion`: about `3.0 s` suite, `516 ms` body
     - Native `testNestedExclusion`: about `3.0 s` suite, `364 ms` body
   - Current conclusion: there is no longer a stable large native-only slowdown in the test body.

TODO next:
12. Setup-path verification.
    - Current pair of reruns shows:
      - Native `beforeClass`: `1717 ms`
      - JVM `beforeClass`: `1864 ms`
    - Native branch details:
      - `randomIndexWriter.init totalMs=204`
      - `randomIndexWriter.getReader branch=nrt totalMs=732`
    - JVM branch details:
      - `randomIndexWriter.init totalMs=305`
      - `randomIndexWriter.getReader branch=nrt totalMs=307` with `commitMs=296`
    - These runs took different randomized paths and do not currently prove a native-only bottleneck.
    - Only descend further if a repeatable setup hotspot reappears across multiple runs with comparable branches.
*/


/** Basic equivalence tests for approximations. */
class TestApproximationSearchEquivalence : SearchEquivalenceTestBase() {

    init {
        configureTestLogging()
    }

    private val logger = KotlinLogging.logger {}

    private inline fun measurePhase(phase: String, block: () -> Unit) {
        val mark = TimeSource.Monotonic.markNow()
        block()
        logger.debug { "phase=testApproximationSearchEquivalence elapsedMs=${mark.elapsedNow().inWholeMilliseconds} name=$phase" }
    }

    @Test
    @Throws(Exception::class)
    fun testConjunction() {
        measurePhase("testConjunction") {
            val t1 = randomTerm()
            val t2 = randomTerm()
            val q1 = TermQuery(t1)
            val q2 = TermQuery(t2)

            val bq1 = BooleanQuery.Builder()
            bq1.add(q1, BooleanClause.Occur.MUST)
            bq1.add(q2, BooleanClause.Occur.MUST)

            val bq2 = BooleanQuery.Builder()
            bq2.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
            bq2.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST)

            assertSameScores(bq1.build(), bq2.build())
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNestedConjunction() {
        measurePhase("testNestedConjunction") {
            val t1 = randomTerm()
            var t2: Term
            do {
                t2 = randomTerm()
            } while (t1 == t2)
            val t3 = randomTerm()
            val q1 = TermQuery(t1)
            val q2 = TermQuery(t2)
            val q3 = TermQuery(t3)

            val bq1 = BooleanQuery.Builder()
            bq1.add(q1, BooleanClause.Occur.MUST)
            bq1.add(q2, BooleanClause.Occur.MUST)

            val bq2 = BooleanQuery.Builder()
            bq2.add(bq1.build(), BooleanClause.Occur.MUST)
            bq2.add(q3, BooleanClause.Occur.MUST)

            val bq3 = BooleanQuery.Builder()
            bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
            bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST)

            val bq4 = BooleanQuery.Builder()
            bq4.add(bq3.build(), BooleanClause.Occur.MUST)
            bq4.add(q3, BooleanClause.Occur.MUST)

            assertSameScores(bq2.build(), bq4.build())
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDisjunction() {
        measurePhase("testDisjunction") {
            val t1 = randomTerm()
            val t2 = randomTerm()
            val q1 = TermQuery(t1)
            val q2 = TermQuery(t2)

            val bq1 = BooleanQuery.Builder()
            bq1.add(q1, BooleanClause.Occur.SHOULD)
            bq1.add(q2, BooleanClause.Occur.SHOULD)

            val bq2 = BooleanQuery.Builder()
            bq2.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.SHOULD)
            bq2.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.SHOULD)

            assertSameScores(bq1.build(), bq2.build())
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNestedDisjunction() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.SHOULD)
        bq1.add(q2, BooleanClause.Occur.SHOULD)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.SHOULD)
        bq2.add(q3, BooleanClause.Occur.SHOULD)

        val bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.SHOULD)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.SHOULD)

        val bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.SHOULD)
        bq4.add(q3, BooleanClause.Occur.SHOULD)

        assertSameScores(bq2.build(), bq4.build())
    }

    @Test
    @Throws(Exception::class)
    fun testDisjunctionInConjunction() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.SHOULD)
        bq1.add(q2, BooleanClause.Occur.SHOULD)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.MUST)
        bq2.add(q3, BooleanClause.Occur.MUST)

        val bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.SHOULD)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.SHOULD)

        val bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.MUST)
        bq4.add(q3, BooleanClause.Occur.MUST)

        assertSameScores(bq2.build(), bq4.build())
    }

    @Test
    @Throws(Exception::class)
    fun testConjunctionInDisjunction() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.MUST)
        bq1.add(q2, BooleanClause.Occur.MUST)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.SHOULD)
        bq2.add(q3, BooleanClause.Occur.SHOULD)

        val bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST)

        val bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.SHOULD)
        bq4.add(q3, BooleanClause.Occur.SHOULD)

        assertSameScores(bq2.build(), bq4.build())
    }

    @Test
    @Throws(Exception::class)
    fun testConstantScore() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)

        val bq1 = BooleanQuery.Builder()
        bq1.add(ConstantScoreQuery(q1), BooleanClause.Occur.MUST)
        bq1.add(ConstantScoreQuery(q2), BooleanClause.Occur.MUST)

        val bq2 = BooleanQuery.Builder()
        bq2.add(ConstantScoreQuery(RandomApproximationQuery(q1, random())), BooleanClause.Occur.MUST)
        bq2.add(ConstantScoreQuery(RandomApproximationQuery(q2, random())), BooleanClause.Occur.MUST)

        assertSameScores(bq1.build(), bq2.build())
    }

    @Test
    @Throws(Exception::class)
    fun testExclusion() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.MUST)
        bq1.add(q2, BooleanClause.Occur.MUST_NOT)

        val bq2 = BooleanQuery.Builder()
        bq2.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq2.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST_NOT)

        assertSameScores(bq1.build(), bq2.build())
    }

    @Test
    @Throws(Exception::class)
    fun testNestedExclusion() {
        measurePhase("testNestedExclusion") {
            val t1 = randomTerm()
            var t2: Term
            do {
                t2 = randomTerm()
            } while (t1 == t2)
            val t3 = randomTerm()
            val q1 = TermQuery(t1)
            val q2 = TermQuery(t2)
            val q3 = TermQuery(t3)

            val bq1 = BooleanQuery.Builder()
            bq1.add(q1, BooleanClause.Occur.MUST)
            bq1.add(q2, BooleanClause.Occur.MUST_NOT)

            val bq2 = BooleanQuery.Builder()
            bq2.add(bq1.build(), BooleanClause.Occur.MUST)
            bq2.add(q3, BooleanClause.Occur.MUST)

            // Both req and excl have approximations
            measurePhase("testNestedExclusion.bothApprox") {
                var bq3 = BooleanQuery.Builder()
                bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
                bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST_NOT)

                var bq4 = BooleanQuery.Builder()
                bq4.add(bq3.build(), BooleanClause.Occur.MUST)
                bq4.add(q3, BooleanClause.Occur.MUST)

                assertSameScores(bq2.build(), bq4.build())
            }

            // Only req has an approximation
            measurePhase("testNestedExclusion.reqApproxOnly") {
                val bq3 = BooleanQuery.Builder()
                bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
                bq3.add(q2, BooleanClause.Occur.MUST_NOT)

                val bq4 = BooleanQuery.Builder()
                bq4.add(bq3.build(), BooleanClause.Occur.MUST)
                bq4.add(q3, BooleanClause.Occur.MUST)

                assertSameScores(bq2.build(), bq4.build())
            }

            // Only excl has an approximation
            measurePhase("testNestedExclusion.exclApproxOnly") {
                val bq3 = BooleanQuery.Builder()
                bq3.add(q1, BooleanClause.Occur.MUST)
                bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST_NOT)

                val bq4 = BooleanQuery.Builder()
                bq4.add(bq3.build(), BooleanClause.Occur.MUST)
                bq4.add(q3, BooleanClause.Occur.MUST)

                assertSameScores(bq2.build(), bq4.build())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testReqOpt() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.MUST)
        bq1.add(q2, BooleanClause.Occur.SHOULD)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.MUST)
        bq2.add(q3, BooleanClause.Occur.MUST)

        val bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.SHOULD)

        val bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.MUST)
        bq4.add(q3, BooleanClause.Occur.MUST)

        assertSameScores(bq2.build(), bq4.build())
    }
}
