package org.gnit.lucenekmp.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.SingleTermsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.Rethrow
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.Operations
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestAutomatonQuery : LuceneTestCase() {
    private lateinit var directory: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher

    companion object {
        private const val FN = "field"
    }

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        val doc = Document()
        val titleField = newTextField("title", "some title", Field.Store.NO)
        val field = newTextField(FN, "this is document one 2345", Field.Store.NO)
        val footerField = newTextField("footer", "a footer", Field.Store.NO)
        doc.add(titleField)
        doc.add(field)
        doc.add(footerField)
        writer.addDocument(doc)
        field.setStringValue("some text from doc two a short piece 5678.91")
        writer.addDocument(doc)
        field.setStringValue("doc three has some different stuff with numbers 1234 5678.9 and letter b")
        writer.addDocument(doc)
        reader = writer.getReader(true, false)
        searcher = newSearcher(reader)
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        directory.close()
    }

    private fun newTerm(value: String): Term {
        return Term(FN, value)
    }

    @Throws(IOException::class)
    private fun automatonQueryNrHits(query: AutomatonQuery): Long {
        if (VERBOSE) {
            println("TEST: run aq=$query")
        }
        return searcher.search(query, 5).totalHits.value
    }

    @Throws(IOException::class)
    private fun assertAutomatonHits(expected: Int, automaton: Automaton) {
        assertEquals(
            expected.toLong(),
            automatonQueryNrHits(
                AutomatonQuery(newTerm("bogus"), automaton, false, MultiTermQuery.SCORING_BOOLEAN_REWRITE)
            )
        )
        assertEquals(
            expected.toLong(),
            automatonQueryNrHits(
                AutomatonQuery(newTerm("bogus"), automaton, false, MultiTermQuery.CONSTANT_SCORE_REWRITE)
            )
        )
        assertEquals(
            expected.toLong(),
            automatonQueryNrHits(
                AutomatonQuery(
                    newTerm("bogus"),
                    automaton,
                    false,
                    MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE
                )
            )
        )
        assertEquals(
            expected.toLong(),
            automatonQueryNrHits(
                AutomatonQuery(
                    newTerm("bogus"),
                    automaton,
                    false,
                    MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE
                )
            )
        )
    }

    /** Test some very simple automata. */
    @Test
    @Throws(IOException::class)
    fun testAutomata() {
        assertAutomatonHits(0, Automata.makeEmpty())
        assertAutomatonHits(0, Automata.makeEmptyString())
        assertAutomatonHits(2, Automata.makeAnyChar())
        assertAutomatonHits(3, Automata.makeAnyString())
        assertAutomatonHits(2, Automata.makeString("doc"))
        assertAutomatonHits(1, Automata.makeChar('a'.code))
        assertAutomatonHits(2, Automata.makeCharRange('a'.code, 'b'.code))
        assertAutomatonHits(2, Automata.makeDecimalInterval(1233, 2346, 0))
        assertAutomatonHits(
            1,
            Operations.determinize(
                Automata.makeDecimalInterval(0, 2000, 0),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
            )
        )
        assertAutomatonHits(2, Operations.union(listOf(Automata.makeChar('a'.code), Automata.makeChar('b'.code))))
        assertAutomatonHits(0, Operations.intersection(Automata.makeChar('a'.code), Automata.makeChar('b'.code)))
        assertAutomatonHits(
            1,
            Operations.minus(
                Automata.makeCharRange('a'.code, 'b'.code),
                Automata.makeChar('a'.code),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
            )
        )
    }

    @Test
    fun testEquals() {
        val a1 = AutomatonQuery(newTerm("foobar"), Automata.makeString("foobar"))
        val a2 = a1
        val a3 = AutomatonQuery(
            newTerm("foobar"),
            Operations.concatenate(mutableListOf(Automata.makeString("foo"), Automata.makeString("bar")))
        )
        val a4 = AutomatonQuery(newTerm("foobar"), Automata.makeString("different"))
        val a5 = AutomatonQuery(newTerm("blah"), Automata.makeString("foobar"))

        assertEquals(a1.hashCode(), a2.hashCode())
        assertEquals(a1, a2)

        assertEquals(a1.hashCode(), a3.hashCode())
        assertEquals(a1, a3)

        val w1: AutomatonQuery = WildcardQuery(newTerm("foobar"))
        val w2: AutomatonQuery = RegexpQuery(newTerm("foobar"))

        assertFalse(a1.equals(w1))
        assertFalse(a1.equals(w2))
        assertFalse(w1.equals(w2))
        assertFalse(a1.equals(a4))
        assertFalse(a1.equals(a5))
        assertFalse(a1.equals(null))
    }

    /** Test that rewriting to a single term works as expected, preserves MultiTermQuery semantics. */
    @Test
    @Throws(IOException::class)
    fun testRewriteSingleTerm() {
        val aq = AutomatonQuery(newTerm("bogus"), Automata.makeString("piece"))
        val terms: Terms = MultiTerms.getTerms(searcher.indexReader, FN)!!
        assertTrue(aq.getTermsEnum(terms) is SingleTermsEnum)
        assertEquals(1L, automatonQueryNrHits(aq))
    }

    /**
     * Test that rewriting to a prefix query works as expected, preserves MultiTermQuery semantics.
     */
    @Test
    @Throws(IOException::class)
    fun testRewritePrefix() {
        val pfx = Automata.makeString("do")
        val prefixAutomaton = Operations.concatenate(mutableListOf(pfx, Automata.makeAnyString()))
        val aq = AutomatonQuery(newTerm("bogus"), prefixAutomaton)
        assertEquals(3L, automatonQueryNrHits(aq))
    }

    /** Test handling of the empty language */
    @Test
    @Throws(IOException::class)
    fun testEmptyOptimization() {
        val aq = AutomatonQuery(newTerm("bogus"), Automata.makeEmpty())
        val terms: Terms = MultiTerms.getTerms(searcher.indexReader, FN)!!
        assertSame(TermsEnum.EMPTY, aq.getTermsEnum(terms))
        assertEquals(0L, automatonQueryNrHits(aq))
    }

    @Test
    @Throws(Exception::class)
    fun testHashCodeWithThreads() = runBlocking {
        val queries = arrayOfNulls<AutomatonQuery>(atLeast(100))
        for (i in queries.indices) {
            queries[i] = AutomatonQuery(
                Term("bogus", "bogus"),
                Operations.determinize(
                    AutomatonTestUtil.randomAutomaton(random()),
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            )
        }
        val startingGun = CountDownLatch(1)
        val numThreads = TestUtil.nextInt(random(), 2, 5)
        val jobs = ArrayList<kotlinx.coroutines.Job>()
        repeat(numThreads) {
            val job = launch(Dispatchers.Default) {
                try {
                    startingGun.await()
                    for (i in queries.indices) {
                        queries[i]!!.hashCode()
                    }
                } catch (e: Exception) {
                    Rethrow.rethrow(e)
                }
            }
            jobs.add(job)
        }
        startingGun.countDown()
        for (job in jobs) {
            job.join()
        }
    }

    @Test
    fun testBiggishAutomaton() {
        val numTerms = if (TEST_NIGHTLY) 3000 else 500
        val terms: MutableList<BytesRef> = ArrayList()
        while (terms.size < numTerms) {
            terms.add(BytesRef(TestUtil.randomUnicodeString(random())))
        }
        terms.sort()
        AutomatonQuery(Term("foo", "bar"), Automata.makeStringUnion(terms))
    }
}
