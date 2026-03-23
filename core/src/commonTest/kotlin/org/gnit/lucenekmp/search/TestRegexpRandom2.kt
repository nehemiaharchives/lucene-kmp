package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.index.FilteredTermsEnum
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Create an index with random unicode terms Generates random regexps, and validates against a
 * simple impl.
 */
open class TestRegexpRandom2 : LuceneTestCase() {
    protected lateinit var searcher1: IndexSearcher
    protected lateinit var searcher2: IndexSearcher
    protected lateinit var searcher3: IndexSearcher
    private lateinit var reader: IndexReader
    private lateinit var dir: Directory
    protected lateinit var fieldName: String

    @BeforeTest
    @Throws(Exception::class)
    fun setUpTestRegexpRandom2() {
        dir = newDirectory()
        fieldName = if (random().nextBoolean()) "field" else "" // sometimes use an empty string as field name
        val writer =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.KEYWORD, false))
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 50, 1000)),
            )
        val doc = Document()
        val field = newStringField(fieldName, "", Field.Store.NO)
        doc.add(field)
        val dvField = SortedDocValuesField(fieldName, newBytesRef())
        doc.add(dvField)
        val terms = ArrayList<String>()
        val num = atLeast(200)
        for (i in 0..<num) {
            val s = TestUtil.randomUnicodeString(random())
            field.setStringValue(s)
            dvField.setBytesValue(newBytesRef(s))
            terms.add(s)
            writer.addDocument(doc)
        }

        if (VERBOSE) {
            // utf16 order
            terms.sort()
            println("UTF16 order:")
            for (s in terms) {
                println("  ${UnicodeUtil.toHexString(s)}")
            }
        }

        reader = writer.reader
        searcher1 = newSearcher(reader)
        searcher2 = newSearcher(reader)
        searcher3 = newSearcher(reader)
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDownTestRegexpRandom2() {
        reader.close()
        dir.close()
    }

    /** a stupid regexp query that just blasts thru the terms */
    private class DumbRegexpQuery(term: Term, flags: Int) :
        MultiTermQuery(term.field(), MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE) {
        private val automaton: Automaton

        init {
            val re = RegExp(term.text(), flags)
            automaton =
                Operations.determinize(
                    re.toAutomaton()!!,
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                )
        }

        @Throws(IOException::class)
        override fun getTermsEnum(terms: Terms, atts: AttributeSource): TermsEnum {
            return SimpleAutomatonTermsEnum(terms.iterator())
        }

        private inner class SimpleAutomatonTermsEnum(tenum: TermsEnum) : FilteredTermsEnum(tenum) {
            var runAutomaton = CharacterRunAutomaton(automaton)
            var utf16 = CharsRefBuilder()

            init {
                setInitialSeekTerm(newBytesRef(""))
            }

            @Throws(IOException::class)
            override fun accept(term: BytesRef): AcceptStatus {
                utf16.copyUTF8Bytes(term.bytes, term.offset, term.length)
                return if (runAutomaton.run(utf16.chars(), 0, utf16.length())) {
                    AcceptStatus.YES
                } else {
                    AcceptStatus.NO
                }
            }
        }

        override fun toString(field: String?): String {
            return "$field$automaton"
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) {
                return false
            }
            val that = other as DumbRegexpQuery
            return automaton == that.automaton
        }

        override fun hashCode(): Int {
            return 31 * super.hashCode() + automaton.hashCode()
        }
    }

    /** test a bunch of random regular expressions */
    @Test
    @Throws(Exception::class)
    open fun testRegexps() {
        val num = atLeast(200)
        for (i in 0..<num) {
            val reg = AutomatonTestUtil.randomRegexp(random())
            if (VERBOSE) {
                println("TEST: regexp='$reg'")
            }
            assertSame(reg)
        }
    }

    /** check that the # of hits is the same as from a very simple regexpquery implementation. */
    @Throws(IOException::class)
    protected open fun assertSame(regexp: String) {
        val smart = RegexpQuery(Term(fieldName, regexp), RegExp.NONE)
        val nfaQuery =
            RegexpQuery(
                Term(fieldName, regexp),
                RegExp.NONE,
                0,
                RegexpQuery.DEFAULT_PROVIDER,
                0,
                MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE,
                false,
            )
        val dumb = DumbRegexpQuery(Term(fieldName, regexp), RegExp.NONE)

        val smartDocs = searcher1.search(smart, 25)
        val dumbDocs = searcher2.search(dumb, 25)
        val nfaDocs = searcher3.search(nfaQuery, 25)

        CheckHits.checkEqual(smart, smartDocs.scoreDocs, dumbDocs.scoreDocs)
        CheckHits.checkEqual(nfaQuery, nfaDocs.scoreDocs, dumbDocs.scoreDocs)
    }
}
