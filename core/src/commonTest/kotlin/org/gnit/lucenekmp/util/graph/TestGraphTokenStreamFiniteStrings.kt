package org.gnit.lucenekmp.util.graph

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [GraphTokenStreamFiniteStrings] tests. */
class TestGraphTokenStreamFiniteStrings : LuceneTestCase() {

    private fun token(term: String, posInc: Int, posLength: Int): Token {
        val t = Token(term, 0, term.length)
        t.setPositionIncrement(posInc)
        t.positionLength = posLength
        return t
    }

    private fun assertTokenStream(ts: TokenStream, terms: Array<String>, increments: IntArray) {
        // verify no nulls and arrays same length
        assertNotNull(ts)
        assertNotNull(terms)
        assertNotNull(increments)
        assertEquals(terms.size, increments.size)
        val termAtt = ts.addAttribute(CharTermAttribute::class)
        val incrAtt = ts.addAttribute(PositionIncrementAttribute::class)
        val lenAtt = ts.addAttribute(PositionLengthAttribute::class)
        var offset = 0
        while (ts.incrementToken()) {
            // verify term and increment
            assert(offset < terms.size)
            assertEquals(terms[offset], termAtt.toString())
            assertEquals(increments[offset], incrAtt.getPositionIncrement())
            assertEquals(1, lenAtt.positionLength) // we always output linear token streams
            offset++
        }

        // make sure we processed all items
        assertEquals(offset, terms.size)
    }

    @Test
    fun testIllegalState() {
        expectThrows(IllegalStateException::class) {
            val ts: TokenStream = CannedTokenStream(token("a", 0, 1), token("b", 1, 1))
            GraphTokenStreamFiniteStrings(ts).finiteStrings
        }
    }

    @Test
    fun testEmpty() {
        val ts: TokenStream = CannedTokenStream()
        val graph = GraphTokenStreamFiniteStrings(ts)
        val it = graph.finiteStrings
        assertFalse(it.hasNext())
        assertContentEquals(intArrayOf(), graph.articulationPoints())
    }

    @Test
    fun testSingleGraph() {
        val ts: TokenStream =
            CannedTokenStream(
                token("fast", 1, 1),
                token("wi", 1, 1),
                token("wifi", 0, 2),
                token("fi", 1, 1),
                token("network", 1, 1)
            )

        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wi", "fi", "network"), intArrayOf(1, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wifi", "network"), intArrayOf(1, 1, 1))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(1, 3), points)

        assertFalse(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast"), intArrayOf(1))
        assertFalse(it.hasNext())
        var terms = graph.getTerms("field", 0)
        assertContentEquals(arrayOf(Term("field", "fast")), terms)

        assertTrue(graph.hasSidePath(1))
        it = graph.getFiniteStrings(1, 3)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wi", "fi"), intArrayOf(1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wifi"), intArrayOf(1))
        assertFalse(it.hasNext())

        assertFalse(graph.hasSidePath(3))
        it = graph.getFiniteStrings(3, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("network"), intArrayOf(1))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 3)
        assertContentEquals(arrayOf(Term("field", "network")), terms)
    }

    @Test
    fun testSingleGraphWithGap() {
        // "hey the fast wifi network", where "the" removed
        val ts: TokenStream =
            CannedTokenStream(
                token("hey", 1, 1),
                token("fast", 2, 1),
                token("wi", 1, 1),
                token("wifi", 0, 2),
                token("fi", 1, 1),
                token("network", 1, 1)
            )

        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("hey", "fast", "wi", "fi", "network"), intArrayOf(1, 2, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("hey", "fast", "wifi", "network"), intArrayOf(1, 2, 1, 1))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(1, 2, 4), points)

        assertFalse(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("hey"), intArrayOf(1))
        assertFalse(it.hasNext())
        var terms = graph.getTerms("field", 0)
        assertContentEquals(arrayOf(Term("field", "hey")), terms)

        assertFalse(graph.hasSidePath(1))
        it = graph.getFiniteStrings(1, 2)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast"), intArrayOf(2))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 1)
        assertContentEquals(arrayOf(Term("field", "fast")), terms)

        assertTrue(graph.hasSidePath(2))
        it = graph.getFiniteStrings(2, 4)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wi", "fi"), intArrayOf(1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wifi"), intArrayOf(1))
        assertFalse(it.hasNext())

        assertFalse(graph.hasSidePath(4))
        it = graph.getFiniteStrings(4, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("network"), intArrayOf(1))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 4)
        assertContentEquals(arrayOf(Term("field", "network")), terms)
    }

    @Test
    fun testGraphAndGapSameToken() {
        val ts: TokenStream =
            CannedTokenStream(
                token("fast", 1, 1),
                token("wi", 2, 1),
                token("wifi", 0, 2),
                token("fi", 1, 1),
                token("network", 1, 1)
            )

        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wi", "fi", "network"), intArrayOf(1, 2, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wifi", "network"), intArrayOf(1, 2, 1))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(1, 3), points)

        assertFalse(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast"), intArrayOf(1))
        assertFalse(it.hasNext())
        var terms = graph.getTerms("field", 0)
        assertContentEquals(arrayOf(Term("field", "fast")), terms)

        assertTrue(graph.hasSidePath(1))
        it = graph.getFiniteStrings(1, 3)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wi", "fi"), intArrayOf(2, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wifi"), intArrayOf(2))
        assertFalse(it.hasNext())

        assertFalse(graph.hasSidePath(3))
        it = graph.getFiniteStrings(3, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("network"), intArrayOf(1))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 3)
        assertContentEquals(arrayOf(Term("field", "network")), terms)
    }

    @Test
    fun testGraphAndGapSameTokenTerm() {
        val ts: TokenStream =
            CannedTokenStream(
                token("a", 1, 1),
                token("b", 1, 1),
                token("c", 2, 1),
                token("a", 0, 2),
                token("d", 1, 1)
            )

        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("a", "b", "c", "d"), intArrayOf(1, 1, 2, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("a", "b", "a"), intArrayOf(1, 1, 2))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(1, 2), points)

        assertFalse(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("a"), intArrayOf(1))
        assertFalse(it.hasNext())
        var terms = graph.getTerms("field", 0)
        assertContentEquals(arrayOf(Term("field", "a")), terms)

        assertFalse(graph.hasSidePath(1))
        it = graph.getFiniteStrings(1, 2)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("b"), intArrayOf(1))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 1)
        assertContentEquals(arrayOf(Term("field", "b")), terms)

        assertTrue(graph.hasSidePath(2))
        it = graph.getFiniteStrings(2, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("c", "d"), intArrayOf(2, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("a"), intArrayOf(2))
        assertFalse(it.hasNext())
    }

    @Test
    fun testStackedGraph() {
        val ts: TokenStream =
            CannedTokenStream(
                token("fast", 1, 1),
                token("wi", 1, 1),
                token("wifi", 0, 2),
                token("wireless", 0, 2),
                token("fi", 1, 1),
                token("network", 1, 1)
            )

        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wi", "fi", "network"), intArrayOf(1, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wifi", "network"), intArrayOf(1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wireless", "network"), intArrayOf(1, 1, 1))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(1, 3), points)

        assertFalse(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast"), intArrayOf(1))
        assertFalse(it.hasNext())
        var terms = graph.getTerms("field", 0)
        assertContentEquals(arrayOf(Term("field", "fast")), terms)

        assertTrue(graph.hasSidePath(1))
        it = graph.getFiniteStrings(1, 3)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wi", "fi"), intArrayOf(1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wifi"), intArrayOf(1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wireless"), intArrayOf(1))
        assertFalse(it.hasNext())

        assertFalse(graph.hasSidePath(3))
        it = graph.getFiniteStrings(3, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("network"), intArrayOf(1))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 3)
        assertContentEquals(arrayOf(Term("field", "network")), terms)
    }

    @Test
    fun testStackedGraphWithGap() {
        val ts: TokenStream =
            CannedTokenStream(
                token("fast", 1, 1),
                token("wi", 2, 1),
                token("wifi", 0, 2),
                token("wireless", 0, 2),
                token("fi", 1, 1),
                token("network", 1, 1)
            )

        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wi", "fi", "network"), intArrayOf(1, 2, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wifi", "network"), intArrayOf(1, 2, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wireless", "network"), intArrayOf(1, 2, 1))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(1, 3), points)

        assertFalse(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast"), intArrayOf(1))
        assertFalse(it.hasNext())
        var terms = graph.getTerms("field", 0)
        assertContentEquals(arrayOf(Term("field", "fast")), terms)

        assertTrue(graph.hasSidePath(1))
        it = graph.getFiniteStrings(1, 3)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wi", "fi"), intArrayOf(2, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wifi"), intArrayOf(2))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wireless"), intArrayOf(2))
        assertFalse(it.hasNext())

        assertFalse(graph.hasSidePath(3))
        it = graph.getFiniteStrings(3, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("network"), intArrayOf(1))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 3)
        assertContentEquals(arrayOf(Term("field", "network")), terms)
    }

    @Test
    fun testStackedGraphWithRepeat() {
        val ts: TokenStream =
            CannedTokenStream(
                token("ny", 1, 4),
                token("new", 0, 1),
                token("new", 0, 3),
                token("york", 1, 1),
                token("city", 1, 2),
                token("york", 1, 1),
                token("is", 1, 1),
                token("great", 1, 1)
            )

        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("ny", "is", "great"), intArrayOf(1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("new", "york", "city", "is", "great"), intArrayOf(1, 1, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("new", "york", "is", "great"), intArrayOf(1, 1, 1, 1))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(4, 5), points)

        assertTrue(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 4)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("ny"), intArrayOf(1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("new", "york", "city"), intArrayOf(1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("new", "york"), intArrayOf(1, 1))
        assertFalse(it.hasNext())

        assertFalse(graph.hasSidePath(4))
        it = graph.getFiniteStrings(4, 5)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("is"), intArrayOf(1))
        assertFalse(it.hasNext())
        var terms = graph.getTerms("field", 4)
        assertContentEquals(arrayOf(Term("field", "is")), terms)

        assertFalse(graph.hasSidePath(5))
        it = graph.getFiniteStrings(5, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("great"), intArrayOf(1))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 5)
        assertContentEquals(arrayOf(Term("field", "great")), terms)
    }

    @Test
    fun testGraphWithRegularSynonym() {
        val ts: TokenStream =
            CannedTokenStream(
                token("fast", 1, 1),
                token("speedy", 0, 1),
                token("wi", 1, 1),
                token("wifi", 0, 2),
                token("fi", 1, 1),
                token("network", 1, 1)
            )

        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wi", "fi", "network"), intArrayOf(1, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wifi", "network"), intArrayOf(1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("speedy", "wi", "fi", "network"), intArrayOf(1, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("speedy", "wifi", "network"), intArrayOf(1, 1, 1))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(1, 3), points)

        assertFalse(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast"), intArrayOf(1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("speedy"), intArrayOf(1))
        assertFalse(it.hasNext())
        var terms = graph.getTerms("field", 0)
        assertContentEquals(arrayOf(Term("field", "fast"), Term("field", "speedy")), terms)

        assertTrue(graph.hasSidePath(1))
        it = graph.getFiniteStrings(1, 3)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wi", "fi"), intArrayOf(1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wifi"), intArrayOf(1))
        assertFalse(it.hasNext())

        assertFalse(graph.hasSidePath(3))
        it = graph.getFiniteStrings(3, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("network"), intArrayOf(1))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 3)
        assertContentEquals(arrayOf(Term("field", "network")), terms)
    }

    @Test
    fun testMultiGraph() {
        val ts: TokenStream =
            CannedTokenStream(
                token("turbo", 1, 1),
                token("fast", 0, 2),
                token("charged", 1, 1),
                token("wi", 1, 1),
                token("wifi", 0, 2),
                token("fi", 1, 1),
                token("network", 1, 1)
            )

        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("turbo", "charged", "wi", "fi", "network"), intArrayOf(1, 1, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("turbo", "charged", "wifi", "network"), intArrayOf(1, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wi", "fi", "network"), intArrayOf(1, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast", "wifi", "network"), intArrayOf(1, 1, 1))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(2, 4), points)

        assertTrue(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 2)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("turbo", "charged"), intArrayOf(1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("fast"), intArrayOf(1))
        assertFalse(it.hasNext())

        assertTrue(graph.hasSidePath(2))
        it = graph.getFiniteStrings(2, 4)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wi", "fi"), intArrayOf(1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("wifi"), intArrayOf(1))
        assertFalse(it.hasNext())

        assertFalse(graph.hasSidePath(4))
        it = graph.getFiniteStrings(4, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("network"), intArrayOf(1))
        assertFalse(it.hasNext())
        val terms = graph.getTerms("field", 4)
        assertContentEquals(arrayOf(Term("field", "network")), terms)
    }

    @Test
    fun testMultipleSidePaths() {
        // 0   1        2    3         4    5  6         7  8
        // the ny:4/new york wifi:5/wi fi:4 [] wifi:2/wi fi network
        val ts: TokenStream =
            CannedTokenStream(
                token("the", 1, 1),
                token("ny", 1, 4),
                token("new", 0, 1),
                token("york", 1, 1),
                token("wifi", 1, 5),
                token("wi", 0, 1),
                token("fi", 1, 4),
                token("wifi", 2, 2),
                token("wi", 0, 1),
                token("fi", 1, 1),
                token("network", 1, 1)
            )
        val graph = GraphTokenStreamFiniteStrings(ts)

        var it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("the", "ny", "wifi", "network"), intArrayOf(1, 1, 2, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("the", "ny", "wi", "fi", "network"), intArrayOf(1, 1, 2, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("the", "new", "york", "wifi", "network"), intArrayOf(1, 1, 1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("the", "new", "york", "wi", "fi", "network"), intArrayOf(1, 1, 1, 1, 1, 1))
        assertFalse(it.hasNext())

        val points = graph.articulationPoints()
        assertContentEquals(intArrayOf(1, 7), points)

        assertFalse(graph.hasSidePath(0))
        it = graph.getFiniteStrings(0, 1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("the"), intArrayOf(1))
        assertFalse(it.hasNext())
        var terms = graph.getTerms("field", 0)
        assertContentEquals(arrayOf(Term("field", "the")), terms)

        assertTrue(graph.hasSidePath(1))
        it = graph.getFiniteStrings(1, 7)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("ny", "wifi"), intArrayOf(1, 2))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("ny", "wi", "fi"), intArrayOf(1, 2, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("new", "york", "wifi"), intArrayOf(1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("new", "york", "wi", "fi"), intArrayOf(1, 1, 1, 1))
        assertFalse(it.hasNext())

        assertFalse(graph.hasSidePath(7))
        it = graph.getFiniteStrings(7, -1)
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("network"), intArrayOf(1))
        assertFalse(it.hasNext())
        terms = graph.getTerms("field", 7)
        assertContentEquals(arrayOf(Term("field", "network")), terms)
    }

    @Test
    fun testSidePathWithGap() {
        // 0    1               2  3  4             5
        // king alfred:3/alfred [] [] great/awesome ruled
        val cts =
            CannedTokenStream(
                token("king", 1, 1),
                token("alfred", 1, 4),
                token("alfred", 0, 1),
                token("great", 3, 1),
                token("awesome", 0, 1),
                token("ruled", 1, 1)
            )
        val graph = GraphTokenStreamFiniteStrings(cts)
        val it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("king", "alfred", "ruled"), intArrayOf(1, 1, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("king", "alfred", "great", "ruled"), intArrayOf(1, 1, 3, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("king", "alfred", "awesome", "ruled"), intArrayOf(1, 1, 3, 1))
        assertFalse(it.hasNext())
    }

    @Test
    fun testMultipleSidePathsWithGaps() {
        // king alfred:4/alfred [] [] saxons:3 [] wessex ruled
        val cts =
            CannedTokenStream(
                token("king", 1, 1),
                token("alfred", 1, 4),
                token("alfred", 0, 1),
                token("saxons", 3, 3),
                token("wessex", 2, 1),
                token("ruled", 1, 1)
            )
        val graph = GraphTokenStreamFiniteStrings(cts)
        val it = graph.finiteStrings
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("king", "alfred", "wessex", "ruled"), intArrayOf(1, 1, 2, 1))
        assertTrue(it.hasNext())
        assertTokenStream(it.next(), arrayOf("king", "alfred", "saxons", "ruled"), intArrayOf(1, 1, 3, 1))
        assertFalse(it.hasNext())
    }

    @Test
    fun testLongTokenStreamStackOverflowError() {
        val tokens = mutableListOf<Token>()
        tokens.add(token("fast", 1, 1))
        tokens.add(token("wi", 1, 1))
        tokens.add(token("wifi", 0, 2))
        tokens.add(token("fi", 1, 1))

        // Add in too many tokens to get a high depth graph
        for (i in 0 until 1024 + 1) {
            tokens.add(token("network", 1, 1))
        }

        val ts: TokenStream = CannedTokenStream(*tokens.toTypedArray())
        val graph = GraphTokenStreamFiniteStrings(ts)

        expectThrows(IllegalArgumentException::class) { graph.articulationPoints() }
    }
}
