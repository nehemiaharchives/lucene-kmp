package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Closeable
import org.gnit.lucenekmp.jdkport.incrementAndGet
import kotlin.random.Random

/**
 * Simplified placeholder for Lucene's LineFileDocs.
 * TODO: implement full functionality.
 */
class LineFileDocs(private val random: Random) : Closeable {
    private val id = AtomicInteger(0)

    fun nextDoc(): Document {
        val doc = Document()
        val docId = id.incrementAndGet() - 1
        doc.add(TextField("body", "body$docId", Store.NO))
        doc.add(StringField("docid", docId.toString(), Store.YES))
        return doc
    }

    override fun close() {
        // no resources to close in this placeholder
    }
}

