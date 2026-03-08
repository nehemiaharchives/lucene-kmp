package org.gnit.lucenekmp.tests.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.random.Random

/** Helper functions for tests that handles documents */
object DocHelper {
    private val INDEX_WRITER_ACCESS = TestSecrets.getIndexWriterAccess()

    val customType: FieldType
    const val FIELD_1_TEXT = "field one text"
    const val TEXT_FIELD_1_KEY = "textField1"
    val textField1: Field

    init {
        customType = FieldType(TextField.TYPE_STORED)
        textField1 = Field(TEXT_FIELD_1_KEY, FIELD_1_TEXT, customType)
    }

    val TEXT_TYPE_STORED_WITH_TVS: FieldType
    const val FIELD_2_TEXT = "field field field two text"

    // Fields will be lexicographically sorted.  So, the order is: field, text, two
    val FIELD_2_FREQS = intArrayOf(3, 1, 1)
    const val TEXT_FIELD_2_KEY = "textField2"
    val textField2: Field

    init {
        TEXT_TYPE_STORED_WITH_TVS = FieldType(TextField.TYPE_STORED)
        TEXT_TYPE_STORED_WITH_TVS.setStoreTermVectors(true)
        TEXT_TYPE_STORED_WITH_TVS.setStoreTermVectorPositions(true)
        TEXT_TYPE_STORED_WITH_TVS.setStoreTermVectorOffsets(true)
        TEXT_TYPE_STORED_WITH_TVS.freeze()
        textField2 = Field(TEXT_FIELD_2_KEY, FIELD_2_TEXT, TEXT_TYPE_STORED_WITH_TVS)
    }

    val customType3: FieldType
    const val FIELD_3_TEXT = "aaaNoNorms aaaNoNorms bbbNoNorms"
    const val TEXT_FIELD_3_KEY = "textField3"
    val textField3: Field

    init {
        customType3 = FieldType(TextField.TYPE_STORED)
        customType3.setOmitNorms(true)
        textField3 = Field(TEXT_FIELD_3_KEY, FIELD_3_TEXT, customType3)
    }

    const val KEYWORD_TEXT = "Keyword"
    const val KEYWORD_FIELD_KEY = "keyField"
    val keyField: Field = StringField(KEYWORD_FIELD_KEY, KEYWORD_TEXT, Field.Store.YES)

    val customType5: FieldType
    const val NO_NORMS_TEXT = "omitNormsText"
    const val NO_NORMS_KEY = "omitNorms"
    val noNormsField: Field

    init {
        customType5 = FieldType(TextField.TYPE_STORED)
        customType5.setOmitNorms(true)
        customType5.setTokenized(false)
        noNormsField = Field(NO_NORMS_KEY, NO_NORMS_TEXT, customType5)
    }

    val customType6: FieldType
    const val NO_TF_TEXT = "analyzed with no tf and positions"
    const val NO_TF_KEY = "omitTermFreqAndPositions"
    val noTFField: Field

    init {
        customType6 = FieldType(TextField.TYPE_STORED)
        customType6.setIndexOptions(IndexOptions.DOCS)
        noTFField = Field(NO_TF_KEY, NO_TF_TEXT, customType6)
    }

    val customType7: FieldType
    const val UNINDEXED_FIELD_TEXT = "unindexed field text"
    const val UNINDEXED_FIELD_KEY = "unIndField"
    val unIndField: Field

    init {
        customType7 = FieldType()
        customType7.setStored(true)
        unIndField = Field(UNINDEXED_FIELD_KEY, UNINDEXED_FIELD_TEXT, customType7)
    }

    val STRING_TYPE_STORED_WITH_TVS: FieldType

    init {
        STRING_TYPE_STORED_WITH_TVS = FieldType(StringField.TYPE_STORED)
        STRING_TYPE_STORED_WITH_TVS.setStoreTermVectors(true)
        STRING_TYPE_STORED_WITH_TVS.setStoreTermVectorPositions(true)
        STRING_TYPE_STORED_WITH_TVS.setStoreTermVectorOffsets(true)
        STRING_TYPE_STORED_WITH_TVS.freeze()
    }

    const val UNSTORED_1_FIELD_TEXT = "unstored field text"
    const val UNSTORED_FIELD_1_KEY = "unStoredField1"
    val unStoredField1: Field = TextField(UNSTORED_FIELD_1_KEY, UNSTORED_1_FIELD_TEXT, Field.Store.NO)

    val customType8: FieldType
    const val UNSTORED_2_FIELD_TEXT = "unstored field text"
    const val UNSTORED_FIELD_2_KEY = "unStoredField2"
    val unStoredField2: Field

    init {
        customType8 = FieldType(TextField.TYPE_NOT_STORED)
        customType8.setStoreTermVectors(true)
        unStoredField2 = Field(UNSTORED_FIELD_2_KEY, UNSTORED_2_FIELD_TEXT, customType8)
    }

    const val LAZY_FIELD_BINARY_KEY = "lazyFieldBinary"
    var LAZY_FIELD_BINARY_BYTES: ByteArray = byteArrayOf()
    lateinit var lazyFieldBinary: Field

    const val LAZY_FIELD_KEY = "lazyField"
    const val LAZY_FIELD_TEXT = "These are some field bytes"
    val lazyField: Field = Field(LAZY_FIELD_KEY, LAZY_FIELD_TEXT, customType)

    const val LARGE_LAZY_FIELD_KEY = "largeLazyField"
    lateinit var LARGE_LAZY_FIELD_TEXT: String
    lateinit var largeLazyField: Field

    const val FIELD_UTF1_TEXT = "field one \u4e00text"
    const val TEXT_FIELD_UTF1_KEY = "textField1Utf8"
    val textUtfField1: Field = Field(TEXT_FIELD_UTF1_KEY, FIELD_UTF1_TEXT, customType)

    const val FIELD_UTF2_TEXT = "field field field \u4e00two text"
    val FIELD_UTF2_FREQS = intArrayOf(3, 1, 1)
    const val TEXT_FIELD_UTF2_KEY = "textField2Utf8"
    val textUtfField2: Field = Field(TEXT_FIELD_UTF2_KEY, FIELD_UTF2_TEXT, TEXT_TYPE_STORED_WITH_TVS)

    val nameValues = HashMap<String, Any>()

    val fields =
        arrayOfNulls<Field>(14).also { fields ->
            fields[0] = textField1
            fields[1] = textField2
            fields[2] = textField3
            fields[3] = keyField
            fields[4] = noNormsField
            fields[5] = noTFField
            fields[6] = unIndField
            fields[7] = unStoredField1
            fields[8] = unStoredField2
            fields[9] = textUtfField1
            fields[10] = textUtfField2
            fields[11] = lazyField
        }

    val all = HashMap<String, IndexableField>()
    val indexed = HashMap<String, IndexableField>()
    val stored = HashMap<String, IndexableField>()
    val unstored = HashMap<String, IndexableField>()
    val unindexed = HashMap<String, IndexableField>()
    val termvector = HashMap<String, IndexableField>()
    val notermvector = HashMap<String, IndexableField>()
    val lazy = HashMap<String, IndexableField>()
    val noNorms = HashMap<String, IndexableField>()
    val noTf = HashMap<String, IndexableField>()

    init {
        val buffer = "Lazily loading lengths of language in lieu of laughing ".repeat(10000)

        LAZY_FIELD_BINARY_BYTES = "These are some binary field bytes".encodeToByteArray()
        lazyFieldBinary = StoredField(LAZY_FIELD_BINARY_KEY, LAZY_FIELD_BINARY_BYTES)
        fields[fields.size - 2] = lazyFieldBinary
        LARGE_LAZY_FIELD_TEXT = buffer
        largeLazyField = Field(LARGE_LAZY_FIELD_KEY, LARGE_LAZY_FIELD_TEXT, customType)
        fields[fields.size - 1] = largeLazyField
        for (f in fields) {
            add(all, f!!)
            if (f.fieldType().indexOptions() != IndexOptions.NONE) add(indexed, f) else add(unindexed, f)
            if (f.fieldType().storeTermVectors()) add(termvector, f)
            if (f.fieldType().indexOptions() != IndexOptions.NONE && !f.fieldType().storeTermVectors()) add(notermvector, f)
            if (f.fieldType().stored()) add(stored, f) else add(unstored, f)
            if (f.fieldType().indexOptions() == IndexOptions.DOCS) add(noTf, f)
            if (f.fieldType().omitNorms()) add(noNorms, f)
            if (f.fieldType().indexOptions() == IndexOptions.DOCS) add(noTf, f)
        }

        nameValues[TEXT_FIELD_1_KEY] = FIELD_1_TEXT
        nameValues[TEXT_FIELD_2_KEY] = FIELD_2_TEXT
        nameValues[TEXT_FIELD_3_KEY] = FIELD_3_TEXT
        nameValues[KEYWORD_FIELD_KEY] = KEYWORD_TEXT
        nameValues[NO_NORMS_KEY] = NO_NORMS_TEXT
        nameValues[NO_TF_KEY] = NO_TF_TEXT
        nameValues[UNINDEXED_FIELD_KEY] = UNINDEXED_FIELD_TEXT
        nameValues[UNSTORED_FIELD_1_KEY] = UNSTORED_1_FIELD_TEXT
        nameValues[UNSTORED_FIELD_2_KEY] = UNSTORED_2_FIELD_TEXT
        nameValues[LAZY_FIELD_KEY] = LAZY_FIELD_TEXT
        nameValues[LAZY_FIELD_BINARY_KEY] = LAZY_FIELD_BINARY_BYTES
        nameValues[LARGE_LAZY_FIELD_KEY] = LARGE_LAZY_FIELD_TEXT
        nameValues[TEXT_FIELD_UTF1_KEY] = FIELD_UTF1_TEXT
        nameValues[TEXT_FIELD_UTF2_KEY] = FIELD_UTF2_TEXT
    }

    private fun add(map: MutableMap<String, IndexableField>, field: IndexableField) {
        map[field.name()] = field
    }

    /**
     * Adds the fields above to a document
     *
     * @param doc The document to write
     */
    fun setupDoc(doc: Document) {
        for (field in fields) {
            doc.add(field!!)
        }
    }

    /**
     * Writes the document to the directory using a segment named "test"; returns the SegmentInfo
     * describing the new segment
     */
    fun writeDoc(random: Random, dir: Directory, doc: Document): SegmentCommitInfo {
        return writeDoc(dir, MockAnalyzer(random, MockTokenizer.WHITESPACE, false), null, doc)
    }

    /**
     * Writes the document to the directory using the analyzer and the similarity score; returns the
     * SegmentInfo describing the new segment
     */
    fun writeDoc(
        dir: Directory,
        analyzer: Analyzer,
        similarity: Similarity?,
        doc: Document,
    ): SegmentCommitInfo {
        val writer =
            IndexWriter(
                dir,
                IndexWriterConfig(analyzer)
                    .setSimilarity(similarity ?: IndexSearcher.defaultSimilarity),
            )
        writer.addDocument(doc)
        writer.commit()
        val info = INDEX_WRITER_ACCESS.newestSegment(writer)
        writer.close()
        return info
    }

    fun numFields(doc: Document): Int {
        return doc.getFields().size
    }

    fun createDocument(n: Int, indexName: String, numFields: Int): Document {
        val sb = StringBuilder()
        val doc = Document()
        doc.add(Field("id", n.toString(), STRING_TYPE_STORED_WITH_TVS))
        doc.add(Field("indexname", indexName, STRING_TYPE_STORED_WITH_TVS))
        sb.append("a")
        sb.append(n)
        doc.add(Field("field1", sb.toString(), TEXT_TYPE_STORED_WITH_TVS))
        sb.append(" b")
        sb.append(n)
        for (i in 1..<numFields) {
            doc.add(Field("field${i + 1}", sb.toString(), TEXT_TYPE_STORED_WITH_TVS))
        }
        return doc
    }
}
