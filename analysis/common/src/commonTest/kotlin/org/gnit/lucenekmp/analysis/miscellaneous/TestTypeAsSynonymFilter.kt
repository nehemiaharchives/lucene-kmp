package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test

/**
 * Test that this filter moves the value in type to a synonym token with the same offsets. This is
 * rarely useful by itself, but in combination with another filter that updates the type value with
 * an appropriate synonym can be used to identify synonyms before tokens are modified by further
 * analysis, and then add them at the end, ensuring that the synonym value has not been subjected to
 * the intervening analysis. This typically applies when the analysis would remove characters that
 * should remain in the synonym.
 */
class TestTypeAsSynonymFilter : BaseTokenStreamTestCase() {
    /**
     * Test the straight forward case with the simplest constructor. Simply converts every type to a
     * synonym. Typically one wants to also set an ignore list containing "word" unless that default
     * value is removed by prior analysis.
     */
    @Test
    @Throws(Exception::class)
    fun testSimple() {
        val token = Token("foo", 0, 2)
        token.setType("bar")
        val token2 = Token("foo", 4, 6)
        token2.flags = 5
        var ts: TokenStream = CannedTokenStream(token, token2)
        ts = TypeAsSynonymFilter(ts)

        // "word" is the default type!
        assertTokenStreamContents(
            ts,
            arrayOf("foo", "bar", "foo", "word"),
            intArrayOf(0, 0, 4, 4),
            intArrayOf(2, 2, 6, 6),
            types = null, // not testing types
            posIncrements = intArrayOf(1, 0, 1, 0),
            posLengths = null, // positions
            finalOffset = null,
            finalPosInc = null,
            keywordAtts = null,
            graphOffsetsAreCorrect = false,
            payloads = null,
            flags = intArrayOf(0, 0, 5, 5),
            boost = null
        )
    }

    /**
     * Tests that we can add a prefix to the synonym (for example, to keep it from ever matching user
     * input directly), and test that we can ignore a list of type values we don't wish to turn into
     * synonyms.
     */
    @Test
    @Throws(Exception::class)
    fun testWithPrefixAndIgnore() {
        val tokens = arrayOf(
            Token("foo", 1, 3),
            Token("foo", 5, 7),
            Token("foo", 9, 11),
        )
        tokens[0].setType("bar")
        tokens[2].setType("ignoreme")
        var ts: TokenStream = CannedTokenStream(*tokens)
        ts = TypeAsSynonymFilter(ts, "pfx_", setOf("word", "ignoreme"), 0)

        assertTokenStreamContents(
            ts,
            arrayOf("foo", "pfx_bar", "foo", "foo"),
            intArrayOf(1, 1, 5, 9),
            intArrayOf(3, 3, 7, 11),
            posIncrements = intArrayOf(1, 0, 1, 1)
        )
    }

    /**
     * Analysis chains that make use of flags may or may not want flags transferred to the synonym to
     * be created. This tests the mask that can be used to control which flag bits are transferred.
     */
    @Test
    @Throws(Exception::class)
    fun testFlagMask() {
        val token = Token("foo", 0, 2)
        token.setType("bar")
        token.flags = 7
        val token2 = Token("foo", 4, 6)
        var ts: TokenStream = CannedTokenStream(token, token2)

        ts = TypeAsSynonymFilter(ts, "", emptySet(), 5)

        // "word" is the default type!
        assertTokenStreamContents(
            ts,
            arrayOf("foo", "bar", "foo", "word"),
            intArrayOf(0, 0, 4, 4),
            intArrayOf(2, 2, 6, 6),
            types = null, // not testing types
            posIncrements = null,
            posLengths = null, // positions tested above
            finalOffset = null,
            finalPosInc = null,
            keywordAtts = null,
            graphOffsetsAreCorrect = false,
            payloads = null,
            flags = intArrayOf(7, 5, 0, 0),
            boost = null
        )
    }
}
