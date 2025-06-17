package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.util.Locale
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

    @Test
    fun testUnicodeAsciiInsensitiveFlags() {
        var r: RegExp

        // ASCII behaves appropriately with different flags
        r = RegExp("A")
        assertFalse(ByteRunAutomaton(r.toAutomaton()!!).run("a".encodeToByteArray(), 0, 1))

        r = RegExp("A", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("a".encodeToByteArray(), 0, 1))

        r = RegExp("A", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("a".encodeToByteArray(), 0, 1))

        r = RegExp("A", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("a".encodeToByteArray(), 0, 1))

        // class 1 Unicode characters behaves appropriately with different flags
        r = RegExp("Σ")
        assertFalse(ByteRunAutomaton(r.toAutomaton()!!).run("σ".encodeToByteArray(), 0, 1))
        assertFalse(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("σ")
        assertFalse(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("Σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("σ".encodeToByteArray(), 0, 1))
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("Σ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("σ".encodeToByteArray(), 0, 1))
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("σ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("Σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("σ".encodeToByteArray(), 0, 1))
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        // class 2 Unicode characters behaves appropriately with different flags
        r = RegExp("ῼ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ῳ".encodeToByteArray(), 0, 1))

        r = RegExp("ῼ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertFalse(
            ByteRunAutomaton(r.toAutomaton()!!).run("ῼ".uppercase(Locale.ROOT).encodeToByteArray(), 0, "ῼ".uppercase(Locale.ROOT).length)
        )

        // class 3 Unicode characters behaves appropriately with different flags
        r = RegExp("ﬗ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertFalse(
            ByteRunAutomaton(r.toAutomaton()!!).run("ﬗ".uppercase(Locale.ROOT).encodeToByteArray(), 0, "ﬗ".uppercase(Locale.ROOT).length)
        )

        r = RegExp("\uD802\uDC8A", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(
            ByteRunAutomaton(r.toAutomaton()!!).run(
                "\uD802\uDC89".encodeToByteArray(),
                0,
                "\uD802\uDC89".length
            )
        )
    }
}
