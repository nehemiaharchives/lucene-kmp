package org.gnit.lucenekmp.tests.util

import kotlin.random.Random
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField

/**
 * Simplified port of Lucene's LineFileDocs for generating random documents.
 */
class LineFileDocs(random: Random) {
    private val random: Random = Random(random.nextLong())

    fun close() {
        // No resources to release in this simplified version
    }

    /**
     * Returns a new [Document] with a single text field containing random unicode content.
     */
    fun nextDoc(): Document {
        val doc = Document()
        val content = TestUtil.randomUnicodeString(random, 100)
        doc.add(TextField("body", content, Field.Store.NO))
        return doc
    }
}
