package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFuzzyTermOnShortTerms : LuceneTestCase() {
    companion object {
        private const val FIELD = "field"
    }

    @Test
    fun test() {
        // proves rule that edit distance between the two terms
        // must be > smaller term for there to be a match
        val a = getAnalyzer()
        // these work
        countHits(a, arrayOf("abc"), FuzzyQuery(Term(FIELD, "ab"), 1), 1)
        countHits(a, arrayOf("ab"), FuzzyQuery(Term(FIELD, "abc"), 1), 1)

        countHits(a, arrayOf("abcde"), FuzzyQuery(Term(FIELD, "abc"), 2), 1)
        countHits(a, arrayOf("abc"), FuzzyQuery(Term(FIELD, "abcde"), 2), 1)

        // LUCENE-7439: these now work as well:

        countHits(a, arrayOf("ab"), FuzzyQuery(Term(FIELD, "a"), 1), 1)
        countHits(a, arrayOf("a"), FuzzyQuery(Term(FIELD, "ab"), 1), 1)

        countHits(a, arrayOf("abc"), FuzzyQuery(Term(FIELD, "a"), 2), 1)
        countHits(a, arrayOf("a"), FuzzyQuery(Term(FIELD, "abc"), 2), 1)

        countHits(a, arrayOf("abcd"), FuzzyQuery(Term(FIELD, "ab"), 2), 1)
        countHits(a, arrayOf("ab"), FuzzyQuery(Term(FIELD, "abcd"), 2), 1)
    }

    private fun countHits(analyzer: Analyzer, docs: Array<String>, q: Query, expected: Int) {
        val d = getDirectory(analyzer, docs)
        val r: IndexReader = DirectoryReader.open(d)
        val s = IndexSearcher(r)
        val totalHits = s.count(q)
        assertEquals(expected, totalHits, q.toString())
        r.close()
        d.close()
    }

    fun getAnalyzer(): Analyzer {
        return object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.SIMPLE, true)
                return TokenStreamComponents(tokenizer, tokenizer)
            }
        }
    }

    @Throws(IOException::class)
    fun getDirectory(analyzer: Analyzer, vals: Array<String>): Directory {
        val directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
                    .setMergePolicy(newLogMergePolicy()),
            )

        for (s in vals) {
            val d = Document()
            d.add(newTextField(FIELD, s, Field.Store.YES))
            writer.addDocument(d)
        }
        writer.close()
        return directory
    }
}
