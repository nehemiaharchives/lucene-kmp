package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus
import org.gnit.lucenekmp.jdkport.SortedSet
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.search.AutomatonQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil.randomRegexp
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil.sameLanguage
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.Operations.determinize
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestTermsEnum2 : LuceneTestCase() {

    private lateinit var dir: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher
    private lateinit var terms: SortedSet<BytesRef> // the terms we put in the index
    private lateinit var termsAutomaton: Automaton // automata of the same
    var numIterations: Int = 0

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {

        numIterations = atLeast(50)
        dir = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.KEYWORD, false))
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 50, 1000))
            )
        val doc = Document()
        val field = newStringField("field", "", Field.Store.YES)
        doc.add(field)
        terms = TreeSet<BytesRef>()

        val num = atLeast(200)
        for (i in 0..<num) {
            val s = TestUtil.randomUnicodeString(random())
            field.setStringValue(s)
            terms.add(BytesRef(s))
            writer.addDocument(doc)
        }

        termsAutomaton = Automata.makeStringUnion(terms)

        reader = writer.reader
        searcher = newSearcher(reader)
        writer.close()
    }

    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    /** tests a pre-intersected automaton against the original  */
    @Test
    @Throws(Exception::class)
    fun testFiniteVersusInfinite() {
        for (i in 0..<numIterations) {
            val reg = randomRegexp(random())
            val automaton =
                determinize(
                    RegExp(reg, RegExp.NONE).toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            val matchedTerms: MutableList<BytesRef> = mutableListOf()
            for (t in terms) {
                if (Operations.run(automaton, t.utf8ToString())) {
                    matchedTerms.add(t)
                }
            }

            val alternate: Automaton = Automata.makeStringUnion(matchedTerms)
            // System.out.println("match " + matchedTerms.size() + " " + alternate.getNumberOfStates() + "
            // states, sigma=" + alternate.getStartPoints().length);
            // AutomatonTestUtil.minimizeSimple(alternate);
            // System.out.println("minimize done");
            val a1 = AutomatonQuery(Term("field", ""), automaton)
            val a2 = AutomatonQuery(Term("field", ""), alternate)

            val origHits: Array<ScoreDoc> = searcher.search(a1, 25).scoreDocs
            val newHits: Array<ScoreDoc> = searcher.search(a2, 25).scoreDocs
            CheckHits.checkEqual(a1, origHits, newHits)
        }
    }

    /** seeks to every term accepted by some automata  */
    @Test
    @Throws(Exception::class)
    fun testSeeking() {
        for (i in 0..<numIterations) {
            val reg = randomRegexp(random())
            val automaton =
                determinize(
                    RegExp(reg, RegExp.NONE).toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            val te: TermsEnum = MultiTerms.getTerms(reader, "field")!!.iterator()
            val unsortedTerms: ArrayList<BytesRef> = ArrayList<BytesRef>(terms)
            unsortedTerms.shuffle(random())

            for (term in unsortedTerms) {
                if (Operations.run(automaton, term.utf8ToString())) {
                    // term is accepted
                    if (random().nextBoolean()) {
                        // seek exact
                        assertTrue(te.seekExact(term))
                    } else {
                        // seek ceil
                        assertEquals(SeekStatus.FOUND, te.seekCeil(term))
                        assertEquals(term, te.term())
                    }
                }
            }
        }
    }

    /** mixes up seek and next for all terms  */
    @Test
    @Throws(Exception::class)
    fun testSeekingAndNexting() {
        for (i in 0..<numIterations) {
            val te: TermsEnum = MultiTerms.getTerms(reader, "field")!!.iterator()

            for (term in terms) {
                val c = random().nextInt(3)
                if (c == 0) {
                    assertEquals(term, te.next())
                } else if (c == 1) {
                    assertEquals(SeekStatus.FOUND, te.seekCeil(term))
                    assertEquals(term, te.term())
                } else {
                    assertTrue(te.seekExact(term))
                }
            }
        }
    }

    /** tests intersect: TODO start at a random term!  */
    @Test
    @Throws(Exception::class)
    fun testIntersect() {
        for (i in 0..<numIterations) {
            val reg = randomRegexp(random())
            var automaton = RegExp(reg, RegExp.NONE).toAutomaton()
            automaton = determinize(automaton, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            val ca = CompiledAutomaton(automaton, false, false)
            val te: TermsEnum = MultiTerms.getTerms(reader, "field")!!.intersect(ca, null)
            val expected =
                determinize(
                    Operations.intersection(termsAutomaton, automaton), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            val found: TreeSet<BytesRef> = TreeSet<BytesRef>()
            while (te.next() != null) {
                found.add(BytesRef.deepCopyOf(te.term()!!))
            }

            val actual =
                determinize(Automata.makeStringUnion(found), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            assertTrue(sameLanguage(expected, actual))
        }
    }
}
