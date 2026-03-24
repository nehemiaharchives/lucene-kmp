package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.automaton.ByteRunAutomaton
import org.gnit.lucenekmp.util.automaton.LevenshteinAutomata
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Tests [FuzzyQuery]. */
class TestFuzzyQuery : LuceneTestCase() {
    @Test
    fun testBasicPrefix() {
        val directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        addDoc("abc", writer)
        val reader = writer.reader
        val searcher = newSearcher(reader)
        writer.close()

        val query = FuzzyQuery(Term("field", "abc"), FuzzyQuery.defaultMaxEdits, 1)
        val hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)
        reader.close()
        directory.close()
    }

    @Test
    fun testFuzziness() {
        val directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newMergePolicy(random(), false)),
            )
        addDoc("aaaaa", writer)
        addDoc("aaaab", writer)
        addDoc("aaabb", writer)
        addDoc("aabbb", writer)
        addDoc("abbbb", writer)
        addDoc("bbbbb", writer)
        addDoc("ddddd", writer)

        val reader = writer.reader
        val searcher = newSearcher(reader)
        writer.close()

        var query = FuzzyQuery(Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 0)
        var hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)

        query = FuzzyQuery(Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 1)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        query = FuzzyQuery(Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 2)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        query = FuzzyQuery(Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 3)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        query = FuzzyQuery(Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 4)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(2, hits.size)
        query = FuzzyQuery(Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 5)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)
        query = FuzzyQuery(Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 6)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)

        query = FuzzyQuery(Term("field", "bbbbb"), FuzzyQuery.defaultMaxEdits, 0)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size, "3 documents should match")
        var order = listOf("bbbbb", "abbbb", "aabbb")
        val storedFields = searcher.storedFields()
        for (i in hits.indices) {
            val term = storedFields.document(hits[i].doc).get("field")
            assertEquals(order[i], term)
        }

        query = FuzzyQuery(Term("field", "bbbbb"), FuzzyQuery.defaultMaxEdits, 0, 2, false)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(2, hits.size, "only 2 documents should match")
        order = listOf("bbbbb", "abbbb")
        for (i in hits.indices) {
            val term = storedFields.document(hits[i].doc).get("field")
            assertEquals(order[i], term)
        }

        query = FuzzyQuery(Term("field", "xxxxx"), FuzzyQuery.defaultMaxEdits, 0)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(0, hits.size)
        query = FuzzyQuery(Term("field", "aaccc"), FuzzyQuery.defaultMaxEdits, 0)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(0, hits.size)

        query = FuzzyQuery(Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 0)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        assertEquals("aaaaa", storedFields.document(hits[0].doc).get("field"))
        assertEquals("aaaab", storedFields.document(hits[1].doc).get("field"))
        assertEquals("aaabb", storedFields.document(hits[2].doc).get("field"))

        query = FuzzyQuery(Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 0)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        assertEquals("aaaaa", storedFields.document(hits[0].doc).get("field"))
        assertEquals("aaaab", storedFields.document(hits[1].doc).get("field"))
        assertEquals("aaabb", storedFields.document(hits[2].doc).get("field"))

        query = FuzzyQuery(Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 1)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        assertEquals("aaaaa", storedFields.document(hits[0].doc).get("field"))
        assertEquals("aaaab", storedFields.document(hits[1].doc).get("field"))
        assertEquals("aaabb", storedFields.document(hits[2].doc).get("field"))
        query = FuzzyQuery(Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 2)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        assertEquals("aaaaa", storedFields.document(hits[0].doc).get("field"))
        assertEquals("aaaab", storedFields.document(hits[1].doc).get("field"))
        assertEquals("aaabb", storedFields.document(hits[2].doc).get("field"))
        query = FuzzyQuery(Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 3)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        assertEquals("aaaaa", storedFields.document(hits[0].doc).get("field"))
        assertEquals("aaaab", storedFields.document(hits[1].doc).get("field"))
        assertEquals("aaabb", storedFields.document(hits[2].doc).get("field"))
        query = FuzzyQuery(Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 4)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(2, hits.size)
        assertEquals("aaaaa", storedFields.document(hits[0].doc).get("field"))
        assertEquals("aaaab", storedFields.document(hits[1].doc).get("field"))
        query = FuzzyQuery(Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 5)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(0, hits.size)

        query = FuzzyQuery(Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 0)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)
        assertEquals("ddddd", storedFields.document(hits[0].doc).get("field"))

        query = FuzzyQuery(Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 1)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)
        assertEquals("ddddd", storedFields.document(hits[0].doc).get("field"))
        query = FuzzyQuery(Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 2)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)
        assertEquals("ddddd", storedFields.document(hits[0].doc).get("field"))
        query = FuzzyQuery(Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 3)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)
        assertEquals("ddddd", storedFields.document(hits[0].doc).get("field"))
        query = FuzzyQuery(Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 4)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)
        assertEquals("ddddd", storedFields.document(hits[0].doc).get("field"))
        query = FuzzyQuery(Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 5)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(0, hits.size)

        query = FuzzyQuery(Term("anotherfield", "ddddX"), FuzzyQuery.defaultMaxEdits, 0)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(0, hits.size)

        reader.close()
        directory.close()
    }

    @Test
    fun testPrefixLengthEqualStringLength() {
        val directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        addDoc("b*a", writer)
        addDoc("b*ab", writer)
        addDoc("b*abc", writer)
        addDoc("b*abcd", writer)
        val multibyte = "아프리카코끼리속"
        addDoc(multibyte, writer)
        val reader = writer.reader
        val searcher = newSearcher(reader)
        writer.close()

        var maxEdits = 0
        var prefixLength = 3
        var query = FuzzyQuery(Term("field", "b*a"), maxEdits, prefixLength)
        var hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)

        maxEdits = 1
        query = FuzzyQuery(Term("field", "b*a"), maxEdits, prefixLength)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(2, hits.size)

        maxEdits = 2
        query = FuzzyQuery(Term("field", "b*a"), maxEdits, prefixLength)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)

        maxEdits = 1
        prefixLength = multibyte.length - 1
        query = FuzzyQuery(Term("field", multibyte.substring(0, prefixLength)), maxEdits, prefixLength)
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size)

        reader.close()
        directory.close()
    }

    @Test
    fun test2() {
        val directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.KEYWORD, false)),
            )
        addDoc("LANGE", writer)
        addDoc("LUETH", writer)
        addDoc("PIRSING", writer)
        addDoc("RIEGEL", writer)
        addDoc("TRZECZIAK", writer)
        addDoc("WALKER", writer)
        addDoc("WBR", writer)
        addDoc("WE", writer)
        addDoc("WEB", writer)
        addDoc("WEBE", writer)
        addDoc("WEBER", writer)
        addDoc("WEBERE", writer)
        addDoc("WEBREE", writer)
        addDoc("WEBEREI", writer)
        addDoc("WBRE", writer)
        addDoc("WITTKOPF", writer)
        addDoc("WOJNAROWSKI", writer)
        addDoc("WRICKE", writer)

        val reader = writer.reader
        val searcher = newSearcher(reader)
        writer.close()

        val query = FuzzyQuery(Term("field", "WEBER"), 2, 1)
        val hits = searcher.search(query, 1000).scoreDocs
        assertEquals(8, hits.size)

        reader.close()
        directory.close()
    }

    @Test
    fun testSingleQueryExactMatchScoresHighest() {
        val directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        addDoc("smith", writer)
        addDoc("smith", writer)
        addDoc("smith", writer)
        addDoc("smith", writer)
        addDoc("smith", writer)
        addDoc("smith", writer)
        addDoc("smythe", writer)
        addDoc("smdssasd", writer)

        val reader = writer.reader
        val searcher = newSearcher(reader)
        searcher.similarity = ClassicSimilarity()
        writer.close()
        val searchTerms = arrayOf("smith", "smythe", "smdssasd")
        val storedFields = reader.storedFields()
        for (searchTerm in searchTerms) {
            val query = FuzzyQuery(Term("field", searchTerm), 2, 1)
            val hits = searcher.search(query, 1000).scoreDocs
            val bestDoc = storedFields.document(hits[0].doc)
            assertTrue(hits.isNotEmpty())
            val topMatch = bestDoc.get("field")
            assertEquals(searchTerm, topMatch)
            if (hits.size > 1) {
                val worstDoc = storedFields.document(hits[hits.size - 1].doc)
                val worstMatch = worstDoc.get("field")
                assertNotEquals(searchTerm, worstMatch)
            }
        }
        reader.close()
        directory.close()
    }

    @Test
    fun testMultipleQueriesIdfWorks() {
        val directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)

        addDoc("michael smith", writer)
        addDoc("michael lucero", writer)
        addDoc("doug cutting", writer)
        addDoc("doug cuttin", writer)
        addDoc("michael wardle", writer)
        addDoc("micheal vegas", writer)
        addDoc("michael lydon", writer)

        val reader = writer.reader
        val searcher = newSearcher(reader)
        searcher.similarity = ClassicSimilarity()

        writer.close()

        val query = BooleanQuery.Builder()
        val commonSearchTerm = "michael"
        val commonQuery = FuzzyQuery(Term("field", commonSearchTerm), 2, 1)
        query.add(commonQuery, Occur.SHOULD)

        val rareSearchTerm = "cutting"
        val rareQuery = FuzzyQuery(Term("field", rareSearchTerm), 2, 1)
        query.add(rareQuery, Occur.SHOULD)
        val hits = searcher.search(query.build(), 1000).scoreDocs

        assertEquals(7, hits.size)
        val bestDoc = searcher.storedFields().document(hits[0].doc)
        val topMatch = bestDoc.get("field")
        assertTrue(topMatch!!.contains(rareSearchTerm))

        val runnerUpDoc = searcher.storedFields().document(hits[1].doc)
        val runnerUpMatch = runnerUpDoc.get("field")
        assertTrue(runnerUpMatch!!.contains("cuttin"))

        val worstDoc = searcher.storedFields().document(hits[hits.size - 1].doc)
        val worstMatch = worstDoc.get("field")
        assertTrue(worstMatch!!.contains("micheal"))

        reader.close()
        directory.close()
    }

    @Test
    fun testTieBreaker() {
        val directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        addDoc("a123456", writer)
        addDoc("c123456", writer)
        addDoc("d123456", writer)
        addDoc("e123456", writer)

        val directory2 = newDirectory()
        val writer2 = RandomIndexWriter(random(), directory2)
        addDoc("a123456", writer2)
        addDoc("b123456", writer2)
        addDoc("b123456", writer2)
        addDoc("b123456", writer2)
        addDoc("c123456", writer2)
        addDoc("f123456", writer2)

        val ir1 = writer.reader
        val ir2 = writer2.reader

        val mr = MultiReader(ir1, ir2)
        val searcher = newSearcher(mr)
        val fq = FuzzyQuery(Term("field", "z123456"), 1, 0, 2, false)
        val docs = searcher.search(fq, 2)
        assertEquals(5L, docs.totalHits.value)
        mr.close()
        ir1.close()
        ir2.close()
        writer.close()
        writer2.close()
        directory.close()
        directory2.close()
    }

    @Test
    fun testBoostOnlyRewrite() {
        val directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        addDoc("Lucene", writer)
        addDoc("Lucene", writer)
        addDoc("Lucenne", writer)

        val reader = writer.reader
        val searcher = newSearcher(reader)
        writer.close()

        val query =
            FuzzyQuery(
                Term("field", "lucene"),
                FuzzyQuery.defaultMaxEdits,
                FuzzyQuery.defaultPrefixLength,
                FuzzyQuery.defaultMaxExpansions,
                FuzzyQuery.defaultTranspositions,
                MultiTermQuery.TopTermsBoostOnlyBooleanQueryRewrite(50),
            )
        val hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size)
        assertEquals("Lucene", reader.storedFields().document(hits[0].doc).get("field"))
        assertEquals("Lucene", reader.storedFields().document(hits[1].doc).get("field"))
        assertEquals("Lucenne", reader.storedFields().document(hits[2].doc).get("field"))
        reader.close()
        directory.close()
    }

    @Test
    fun testGiga() {
        val index = newDirectory()
        val w = RandomIndexWriter(random(), index)

        addDoc("Lucene in Action", w)
        addDoc("Lucene for Dummies", w)
        addDoc("Giga byte", w)
        addDoc("ManagingGigabytesManagingGigabyte", w)
        addDoc("ManagingGigabytesManagingGigabytes", w)
        addDoc("The Art of Computer Science", w)
        addDoc("J. K. Rowling", w)
        addDoc("JK Rowling", w)
        addDoc("Joanne K Roling", w)
        addDoc("Bruce Willis", w)
        addDoc("Willis bruce", w)
        addDoc("Brute willis", w)
        addDoc("B. willis", w)
        val r = w.reader
        w.close()

        val q: Query = FuzzyQuery(Term("field", "giga"), 0)

        val searcher = newSearcher(r)
        val hits = searcher.search(q, 10).scoreDocs
        assertEquals(1, hits.size)
        assertEquals("Giga byte", searcher.storedFields().document(hits[0].doc).get("field"))
        r.close()
        w.close()
        index.close()
    }

    @Test
    fun testDistanceAsEditsSearching() {
        val index = newDirectory()
        val w = RandomIndexWriter(random(), index)
        addDoc("foobar", w)
        addDoc("test", w)
        addDoc("working", w)
        val reader = w.reader
        val searcher = newSearcher(reader)
        w.close()

        var q = FuzzyQuery(Term("field", "fouba"), 2)
        var hits = searcher.search(q, 10).scoreDocs
        assertEquals(1, hits.size)
        assertEquals("foobar", searcher.storedFields().document(hits[0].doc).get("field"))

        q = FuzzyQuery(Term("field", "foubara"), 2)
        hits = searcher.search(q, 10).scoreDocs
        assertEquals(1, hits.size)
        assertEquals("foobar", searcher.storedFields().document(hits[0].doc).get("field"))

        expectThrows(IllegalArgumentException::class) {
            FuzzyQuery(Term("field", "t"), 3)
        }

        reader.close()
        index.close()
    }

    @Test
    fun testValidation() {
        var expected =
            expectThrows(IllegalArgumentException::class) {
                FuzzyQuery(Term("field", "foo"), -1, 0, 1, false)
            }
        assertTrue(expected.message!!.contains("maxEdits"))

        expected =
            expectThrows(IllegalArgumentException::class) {
                FuzzyQuery(
                    Term("field", "foo"),
                    LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE + 1,
                    0,
                    1,
                    false,
                )
            }
        assertTrue(expected.message!!.contains("maxEdits must be between"))

        expected =
            expectThrows(IllegalArgumentException::class) {
                FuzzyQuery(Term("field", "foo"), 1, -1, 1, false)
            }
        assertTrue(expected.message!!.contains("prefixLength cannot be negative"))

        expected =
            expectThrows(IllegalArgumentException::class) {
                FuzzyQuery(Term("field", "foo"), 1, 0, -1, false)
            }
        assertTrue(expected.message!!.contains("maxExpansions must be positive"))

        expected =
            expectThrows(IllegalArgumentException::class) {
                FuzzyQuery(Term("field", "foo"), 1, 0, -1, false)
            }
        assertTrue(expected.message!!.contains("maxExpansions must be positive"))
    }

    @Test
    fun testRandom() {
        val digits = TestUtil.nextInt(random(), 2, 3)
        val vocabularySize = digits shl 7
        val numTerms = min(atLeast(100), vocabularySize)
        val terms = HashSet<String>()
        while (terms.size < numTerms) {
            terms.add(randomSimpleString(digits))
        }

        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        for (term in terms) {
            val doc = Document()
            doc.add(StringField("field", term, Field.Store.YES))
            w.addDocument(doc)
        }
        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val iters = atLeast(200)
        for (iter in 0..<iters) {
            val queryTerm = randomSimpleString(digits)
            val prefixLength = random().nextInt(queryTerm.length)
            val queryPrefix = queryTerm.substring(0, prefixLength)

            val expected = Array(3) { ArrayList<TermAndScore>() }
            for (term in terms) {
                if (!term.startsWith(queryPrefix)) {
                    continue
                }
                var ed = getDistance(term, queryTerm)
                val score = 1f - ed.toFloat() / min(queryTerm.length, term.length).toFloat()
                while (ed < 3) {
                    expected[ed].add(TermAndScore(term, score))
                    ed++
                }
            }

            for (ed in 0..<3) {
                expected[ed].sort()
                val queueSize = TestUtil.nextInt(random(), 1, terms.size)
                val query = FuzzyQuery(Term("field", queryTerm), ed, prefixLength, queueSize, true)
                val hits = s.search(query, terms.size)
                val actual = HashSet<String>()
                val storedFields = s.storedFields()
                for (hit in hits.scoreDocs) {
                    val doc = storedFields.document(hit.doc)
                    actual.add(doc.get("field")!!)
                }
                val expectedTop = HashSet<String>()
                val limit = min(queueSize, expected[ed].size)
                for (i in 0..<limit) {
                    expectedTop.add(expected[ed][i].term)
                }

                if (actual != expectedTop) {
                    val sb = StringBuilder()
                    sb.append(
                        "FAILED: query=$queryTerm ed=$ed queueSize=$queueSize vs expected match size=${expected[ed].size} prefixLength=$prefixLength\n",
                    )

                    var first = true
                    for (term in actual) {
                        if (!expectedTop.contains(term)) {
                            if (first) {
                                sb.append("  these matched but shouldn't:\n")
                                first = false
                            }
                            sb.append("    $term\n")
                        }
                    }
                    first = true
                    for (term in expectedTop) {
                        if (!actual.contains(term)) {
                            if (first) {
                                sb.append("  these did not match but should:\n")
                                first = false
                            }
                            sb.append("    $term\n")
                        }
                    }
                    throw AssertionError(sb.toString())
                }
            }
        }

        IOUtils.close(r, dir)
    }

    @Test
    fun testVisitor() {
        val q = FuzzyQuery(Term("field", "blob"), 2)
        var visited = false
        q.visit(
            object : QueryVisitor() {
                override fun consumeTermsMatching(
                    query: Query,
                    field: String,
                    automaton: () -> ByteRunAutomaton,
                ) {
                    visited = true
                    val a = automaton()
                    assertMatches(a, "blob")
                    assertMatches(a, "bolb")
                    assertMatches(a, "blobby")
                    assertNoMatches(a, "bolbby")
                }
            },
        )
        assertTrue(visited)
    }

    private fun addDoc(text: String, writer: RandomIndexWriter) {
        val doc = Document()
        doc.add(newTextField("field", text, Field.Store.YES))
        writer.addDocument(doc)
    }

    private fun randomSimpleString(digits: Int): String {
        val termLength = TestUtil.nextInt(random(), 1, 8)
        val chars = CharArray(termLength)
        for (i in 0..<termLength) {
            chars[i] = ('a'.code + random().nextInt(digits)).toChar()
        }
        return chars.concatToString()
    }

    private data class TermAndScore(val term: String, val score: Float) : Comparable<TermAndScore> {
        override fun compareTo(other: TermAndScore): Int {
            return if (score > other.score) {
                -1
            } else if (score < other.score) {
                1
            } else {
                term.compareTo(other.term)
            }
        }
    }

    private fun getDistance(target: String, other: String): Int {
        val targetPoints = toIntsRef(target)
        val otherPoints = toIntsRef(other)
        val n = targetPoints.length
        val m = otherPoints.length
        val d = Array(n + 1) { IntArray(m + 1) }

        if (n == 0 || m == 0) {
            return if (n == m) 0 else max(n, m)
        }

        for (i in 0..n) {
            d[i][0] = i
        }

        for (j in 0..m) {
            d[0][j] = j
        }

        for (j in 1..m) {
            val t_j = otherPoints.ints[j - 1]

            for (i in 1..n) {
                val cost = if (targetPoints.ints[i - 1] == t_j) 0 else 1
                d[i][j] = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
                if (
                    i > 1 &&
                    j > 1 &&
                    targetPoints.ints[i - 1] == otherPoints.ints[j - 2] &&
                    targetPoints.ints[i - 2] == otherPoints.ints[j - 1]
                ) {
                    d[i][j] = min(d[i][j], d[i - 2][j - 2] + cost)
                }
            }
        }

        return d[n][m]
    }

    private fun toIntsRef(s: String): IntsRef {
        val ref = IntsRef(s.length)
        val utf16Len = s.length
        var i = 0
        var cp: Int
        while (i < utf16Len) {
            cp = Character.codePointAt(s, i)
            ref.ints[ref.length++] = cp
            i += Character.charCount(cp)
        }
        return ref
    }

    private fun assertMatches(automaton: ByteRunAutomaton, text: String) {
        val b = newBytesRef(text)
        assertTrue(automaton.run(b.bytes, b.offset, b.length))
    }

    private fun assertNoMatches(automaton: ByteRunAutomaton, text: String) {
        val b = newBytesRef(text)
        assertFalse(automaton.run(b.bytes, b.offset, b.length))
    }
}
