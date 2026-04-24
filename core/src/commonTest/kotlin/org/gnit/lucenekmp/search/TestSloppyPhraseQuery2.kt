package org.gnit.lucenekmp.search

import kotlin.random.Random
import kotlin.test.Test
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.search.SearchEquivalenceTestBase
import org.gnit.lucenekmp.tests.util.TestUtil

/** random sloppy phrase query tests */
class TestSloppyPhraseQuery2 : SearchEquivalenceTestBase() {
    /** "A B"~N ⊆ "A B"~N+1 */
    @Test
    fun testIncreasingSloppiness() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        for (i in 0..<10) {
            val q1 = PhraseQuery(i, t1.field(), t1.bytes(), t2.bytes())
            val q2 = PhraseQuery(i + 1, t1.field(), t1.bytes(), t2.bytes())
            assertSubsetOf(q1, q2)
        }
    }

    /** same as the above with posincr */
    @Test
    fun testIncreasingSloppinessWithHoles() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        for (i in 0..<10) {
            val builder = PhraseQuery.Builder()
            builder.add(t1, 0)
            builder.add(t2, 2)
            builder.setSlop(i)
            val q1 = builder.build()
            builder.setSlop(i + 1)
            val q2 = builder.build()
            assertSubsetOf(q1, q2)
        }
    }

    /** "A B C"~N ⊆ "A B C"~N+1 */
    @Test
    fun testIncreasingSloppiness3() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val t3 = randomTerm()
        for (i in 0..<10) {
            val q1 = PhraseQuery(i, t1.field(), t1.bytes(), t2.bytes(), t3.bytes())
            val q2 = PhraseQuery(i + 1, t1.field(), t1.bytes(), t2.bytes(), t3.bytes())
            assertSubsetOf(q1, q2)
            assertSubsetOf(q1, q2)
        }
    }

    /** same as the above with posincr */
    @Test
    fun testIncreasingSloppiness3WithHoles() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val t3 = randomTerm()
        val pos1 = 1 + random().nextInt(3)
        val pos2 = pos1 + 1 + random().nextInt(3)
        for (i in 0..<10) {
            val builder = PhraseQuery.Builder()
            builder.add(t1, 0)
            builder.add(t2, pos1)
            builder.add(t3, pos2)
            builder.setSlop(i)
            val q1 = builder.build()
            builder.setSlop(i + 1)
            val q2 = builder.build()
            assertSubsetOf(q1, q2)
        }
    }

    /** "A A"~N ⊆ "A A"~N+1 */
    @Test
    fun testRepetitiveIncreasingSloppiness() {
        val t = randomTerm()
        for (i in 0..<10) {
            val q1 = PhraseQuery(i, t.field(), t.bytes(), t.bytes())
            val q2 = PhraseQuery(i + 1, t.field(), t.bytes(), t.bytes())
            assertSubsetOf(q1, q2)
        }
    }

    /** same as the above with posincr */
    @Test
    fun testRepetitiveIncreasingSloppinessWithHoles() {
        val t = randomTerm()
        for (i in 0..<10) {
            val builder = PhraseQuery.Builder()
            builder.add(t, 0)
            builder.add(t, 2)
            builder.setSlop(i)
            val q1 = builder.build()
            builder.setSlop(i + 1)
            val q2 = builder.build()
            assertSubsetOf(q1, q2)
        }
    }

    /** "A A A"~N ⊆ "A A A"~N+1 */
    @Test
    fun testRepetitiveIncreasingSloppiness3() {
        val t = randomTerm()
        for (i in 0..<10) {
            val q1 = PhraseQuery(i, t.field(), t.bytes(), t.bytes(), t.bytes())
            val q2 = PhraseQuery(i + 1, t.field(), t.bytes(), t.bytes(), t.bytes())
            assertSubsetOf(q1, q2)
            assertSubsetOf(q1, q2)
        }
    }

    /** same as the above with posincr */
    @Test
    fun testRepetitiveIncreasingSloppiness3WithHoles() {
        val t = randomTerm()
        val pos1 = 1 + random().nextInt(3)
        val pos2 = pos1 + 1 + random().nextInt(3)
        for (i in 0..<10) {
            val builder = PhraseQuery.Builder()
            builder.add(t, 0)
            builder.add(t, pos1)
            builder.add(t, pos2)
            builder.setSlop(i)
            val q1 = builder.build()
            builder.setSlop(i + 1)
            val q2 = builder.build()
            assertSubsetOf(q1, q2)
            assertSubsetOf(q1, q2)
        }
    }

    /** MultiPhraseQuery~N ⊆ MultiPhraseQuery~N+1 */
    @Test
    fun testRandomIncreasingSloppiness() {
        val seed = random().nextLong()
        for (i in 0..<10) {
            var q1 = randomPhraseQuery(seed)
            var q2 = randomPhraseQuery(seed)
            q1 = MultiPhraseQuery.Builder(q1).setSlop(i).build()
            q2 = MultiPhraseQuery.Builder(q2).setSlop(i + 1).build()
            assertSubsetOf(q1, q2)
        }
    }

    private fun randomPhraseQuery(seed: Long): MultiPhraseQuery {
        val random = Random(seed)
        val length = TestUtil.nextInt(random, 2, 5)
        val pqb = MultiPhraseQuery.Builder()
        var position = 0
        for (i in 0..<length) {
            val depth = TestUtil.nextInt(random, 1, 3)
            val terms = Array(depth) { j ->
                Term("field", "${TestUtil.nextInt(random, 'a'.code, 'z'.code).toChar()}")
            }
            pqb.add(terms, position)
            position += TestUtil.nextInt(random, 1, 3)
        }
        return pqb.build()
    }
}
