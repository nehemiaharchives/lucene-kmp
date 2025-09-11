package org.gnit.lucenekmp.tests.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizer
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.store.Directory
import kotlin.random.Random

/**
 * A minimal port of Lucene's RandomIndexWriter for tests.
 *
 * This simplified implementation wraps [IndexWriter] with a basic
 * whitespace analyzer and provides only the functionality needed by
 * the tests that depend on it.
 */
class RandomIndexWriter(
    private val random: Random,
    private val dir: Directory
) : AutoCloseable {

    private val analyzer: Analyzer = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer = WhitespaceTokenizer()
            return TokenStreamComponents(tokenizer, tokenizer)
        }
    }

    val w: IndexWriter = IndexWriter(dir, IndexWriterConfig(analyzer))

    fun addDocument(doc: Iterable<IndexableField>) {
        w.addDocument(doc)
    }

    fun getReader(): DirectoryReader {
        return DirectoryReader.open(w)
    }

    override fun close() {
        w.close()
        analyzer.close()
    }
}

