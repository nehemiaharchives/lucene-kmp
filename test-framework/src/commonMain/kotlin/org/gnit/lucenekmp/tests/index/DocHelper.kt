package org.gnit.lucenekmp.tests.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.random.Random

/** Helper functions for tests that handles documents */
object DocHelper {
    const val TEXT_FIELD_1_KEY = "textField1"
    const val FIELD_1_TEXT = "field one text"
    val textField1 = TextField(TEXT_FIELD_1_KEY, FIELD_1_TEXT, Field.Store.YES)

    const val TEXT_FIELD_2_KEY = "textField2"
    const val FIELD_2_TEXT = "field field field two text"
    val TEXT_TYPE_STORED_WITH_TVS = FieldType(TextField.TYPE_STORED).apply {
        setStoreTermVectors(true)
        setStoreTermVectorPositions(true)
        setStoreTermVectorOffsets(true)
        freeze()
    }
    val textField2 = Field(TEXT_FIELD_2_KEY, FIELD_2_TEXT, TEXT_TYPE_STORED_WITH_TVS)

    const val UNSTORED_FIELD_1_KEY = "unstored1"
    private const val UNSTORED_1_FIELD_TEXT = "unstored field 1"
    val unstoredField1 = TextField(UNSTORED_FIELD_1_KEY, UNSTORED_1_FIELD_TEXT, Field.Store.NO)

    const val UNSTORED_FIELD_2_KEY = "unstored2"
    private const val UNSTORED_2_FIELD_TEXT = "unstored field 2"
    val unstoredField2 = TextField(UNSTORED_FIELD_2_KEY, UNSTORED_2_FIELD_TEXT, Field.Store.NO)

    private val fields = arrayOf<IndexableField>(textField1, textField2, unstoredField1, unstoredField2)

    val unstored: MutableMap<String, IndexableField> = mutableMapOf(
        UNSTORED_FIELD_1_KEY to unstoredField1,
        UNSTORED_FIELD_2_KEY to unstoredField2
    )

    fun setupDoc(doc: Document) {
        for (f in fields) {
            doc.add(f)
        }
    }

    fun writeDoc(random: Random, dir: Directory, doc: Document) {
        val analyzer = object : Analyzer(PER_FIELD_REUSE_STRATEGY) {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
                return TokenStreamComponents(tokenizer)
            }

            override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
                return `in`
            }
        }
        val writer = IndexWriter(dir, IndexWriterConfig(analyzer).setSimilarity(IndexSearcher.getDefaultSimilarity()))
        writer.addDocument(doc)
        writer.commit()
        writer.close()
    }

    fun numFields(doc: Document): Int {
        return doc.getFields().size
    }
}
