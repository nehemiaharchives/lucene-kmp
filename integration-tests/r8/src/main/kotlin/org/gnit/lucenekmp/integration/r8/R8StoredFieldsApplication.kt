package org.gnit.lucenekmp.integration.r8

import android.app.Application
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.store.ByteBuffersDirectory

class R8StoredFieldsApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val failure = runCatching { verifyStoredFields() }.exceptionOrNull()
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(COMPLETED_KEY, true)
            .putString(FAILURE_KEY, failure?.stackTraceToString())
            .commit()
    }

    private fun verifyStoredFields() {
        ByteBuffersDirectory().use { directory ->
            IndexWriter(directory, IndexWriterConfig()).use { writer ->
                val document = Document()
                document.add(StoredField("book", 43))
                document.add(StoredField("chapter", 3))
                document.add(StoredField("verse", 16))
                writer.addDocument(document)
            }

            DirectoryReader.open(directory).use { reader ->
                val hits = IndexSearcher(reader).search(MatchAllDocsQuery(), 1).scoreDocs
                check(hits.size == 1) { "Expected one search hit but got ${hits.size}" }

                val document = reader.storedFields().document(hits[0].doc)
                check(document.getField("book")?.numericValue()?.toInt() == 43)
                check(document.getField("chapter")?.numericValue()?.toInt() == 3)
                check(document.getField("verse")?.numericValue()?.toInt() == 16)
            }
        }
    }

    companion object {
        const val PREFERENCES_NAME = "r8-stored-fields"
        const val COMPLETED_KEY = "completed"
        const val FAILURE_KEY = "failure"
    }
}
