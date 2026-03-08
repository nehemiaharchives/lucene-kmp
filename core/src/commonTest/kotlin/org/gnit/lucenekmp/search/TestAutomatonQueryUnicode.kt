package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test the automaton query for several unicode corner cases, specifically enumerating
 * strings/indexes containing supplementary characters, and the differences between UTF-8/UTF-32 and
 * UTF-16 binary sort order.
 */
class TestAutomatonQueryUnicode : LuceneTestCase() {
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher
    private lateinit var directory: Directory

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
        val field = newTextField(FN, "", Field.Store.NO)
        val footerField = newTextField("footer", "a footer", Field.Store.NO)
        doc.add(titleField)
        doc.add(field)
        doc.add(footerField)
        field.setStringValue("\uD866\uDF05abcdef")
        writer.addDocument(doc)
        field.setStringValue("\uD866\uDF06ghijkl")
        writer.addDocument(doc)
        // this sorts before the previous two in UTF-8/UTF-32, but after in UTF-16!!!
        field.setStringValue("\uFB94mnopqr")
        writer.addDocument(doc)
        field.setStringValue("\uFB95stuvwx") // this one too.
        writer.addDocument(doc)
        field.setStringValue("a\uFFFCbc")
        writer.addDocument(doc)
        field.setStringValue("a\uFFFDbc")
        writer.addDocument(doc)
        field.setStringValue("a\uFFFEbc")
        writer.addDocument(doc)
        field.setStringValue("a\uFB94bc")
        writer.addDocument(doc)
        field.setStringValue("bacadaba")
        writer.addDocument(doc)
        field.setStringValue("\uFFFD")
        writer.addDocument(doc)
        field.setStringValue("\uFFFD\uD866\uDF05")
        writer.addDocument(doc)
        field.setStringValue("\uFFFD\uFFFD")
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

    /**
     * Test that AutomatonQuery interacts with lucene's sort order correctly.
     *
     * This expression matches something either starting with the arabic presentation forms block,
     * or a supplementary character.
     */
    @Test
    @Throws(IOException::class)
    fun testSortOrder() {
        val a = RegExp("((\uD866\uDF05)|\uFB94).*").toAutomaton()
        assertAutomatonHits(2, a)
    }
}
