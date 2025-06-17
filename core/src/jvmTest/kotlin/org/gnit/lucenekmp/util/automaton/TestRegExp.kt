package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.gnit.lucenekmp.tests.util.LuceneTestCase

@Ignore
class TestRegExp : LuceneTestCase() {

    /** Simple smoke test for regular expression. */
    @Test
    fun testSmoke() {
        val r = RegExp("a(b+|c+)d")
        val a = r.toAutomaton()!!
        assertTrue(a.isDeterministic)
        val run = ByteRunAutomaton(a)
        assertTrue(run.run("abbbbbd".encodeToByteArray(), 0, "abbbbbd".length))
        assertTrue(run.run("acd".encodeToByteArray(), 0, "acd".length))
        assertFalse(run.run("ad".encodeToByteArray(), 0, "ad".length))
    }
}
