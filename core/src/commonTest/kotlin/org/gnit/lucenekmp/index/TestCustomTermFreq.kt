package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestCustomTermFreq : LuceneTestCase() {

    private class CannedTermFreqs(
        private val terms: Array<String>,
        private val termFreqs: IntArray,
    ) : TokenStream() {
        private val termAtt = addAttribute(CharTermAttribute::class)
        private val termFreqAtt = addAttribute(TermFrequencyAttribute::class)
        private var upto = 0

        init {
            assert(terms.size == termFreqs.size)
        }

        override fun incrementToken(): Boolean {
            if (upto == terms.size) {
                return false
            }

            clearAttributes()

            termAtt.append(terms[upto])
            termFreqAtt.termFrequency = termFreqs[upto]

            upto++
            return true
        }

        override fun reset() {
            upto = 0
        }
    }

    @Test
    fun testSingletonTermsOneDoc() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        val doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        val field =
            Field(
                "field",
                CannedTermFreqs(arrayOf("foo", "bar"), intArrayOf(42, 128)),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        var postings =
            MultiTerms.getTermPostingsEnum(r, "field", newBytesRef("bar"), PostingsEnum.FREQS.toInt())
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(128, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        postings =
            MultiTerms.getTermPostingsEnum(r, "field", newBytesRef("foo"), PostingsEnum.FREQS.toInt())
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(42, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        IOUtils.close(r, w, dir)
    }

    @Test
    fun testSingletonTermsTwoDocs() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        var field =
            Field(
                "field",
                CannedTermFreqs(arrayOf("foo", "bar"), intArrayOf(42, 128)),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)

        doc = Document()
        field =
            Field(
                "field",
                CannedTermFreqs(arrayOf("foo", "bar"), intArrayOf(50, 50)),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        var postings =
            MultiTerms.getTermPostingsEnum(r, "field", newBytesRef("bar"), PostingsEnum.FREQS.toInt())
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(128, postings.freq())
        assertEquals(1, postings.nextDoc())
        assertEquals(50, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        postings =
            MultiTerms.getTermPostingsEnum(r, "field", newBytesRef("foo"), PostingsEnum.FREQS.toInt())
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(42, postings.freq())
        assertEquals(1, postings.nextDoc())
        assertEquals(50, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        IOUtils.close(r, w, dir)
    }

    @Test
    fun testRepeatTermsOneDoc() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        val doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        val field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(42, 128, 17, 100),
                ),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        var postings =
            MultiTerms.getTermPostingsEnum(r, "field", newBytesRef("bar"), PostingsEnum.FREQS.toInt())
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(228, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        postings =
            MultiTerms.getTermPostingsEnum(r, "field", newBytesRef("foo"), PostingsEnum.FREQS.toInt())
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(59, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        IOUtils.close(r, w, dir)
    }

    @Test
    fun testRepeatTermsTwoDocs() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        var field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(42, 128, 17, 100),
                ),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)

        doc = Document()
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(50, 60, 70, 80),
                ),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        var postings =
            MultiTerms.getTermPostingsEnum(r, "field", newBytesRef("bar"), PostingsEnum.FREQS.toInt())
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(228, postings.freq())
        assertEquals(1, postings.nextDoc())
        assertEquals(140, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        postings =
            MultiTerms.getTermPostingsEnum(r, "field", newBytesRef("foo"), PostingsEnum.FREQS.toInt())
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(59, postings.freq())
        assertEquals(1, postings.nextDoc())
        assertEquals(120, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        IOUtils.close(r, w, dir)
    }

    @Test
    fun testTotalTermFreq() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        var field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(42, 128, 17, 100),
                ),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)

        doc = Document()
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(50, 60, 70, 80),
                ),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)

        val r = DirectoryReader.open(w)

        val termsEnum = MultiTerms.getTerms(r, "field")!!.iterator()
        assertTrue(termsEnum.seekExact(newBytesRef("foo")))
        assertEquals(179, termsEnum.totalTermFreq())
        assertTrue(termsEnum.seekExact(newBytesRef("bar")))
        assertEquals(368, termsEnum.totalTermFreq())

        IOUtils.close(r, w, dir)
    }

    // you can't index proximity with custom term freqs:
    @Test
    fun testInvalidProx() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        val doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        val field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(42, 128, 17, 100),
                ),
                fieldType,
            )
        doc.add(field)
        val e =
            expectThrows(IllegalStateException::class) {
                w.addDocument(doc)
            }
        assertEquals(
            "field \"field\": cannot index positions while using custom TermFrequencyAttribute",
            e.message,
        )
        IOUtils.close(w, dir)
    }

    // you can't index DOCS_ONLY with custom term freq
    @Test
    fun testInvalidDocsOnly() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        val doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS)
        val field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(42, 128, 17, 100),
                ),
                fieldType,
            )
        doc.add(field)
        val e =
            expectThrows(IllegalStateException::class) {
                w.addDocument(doc)
            }
        assertEquals(
            "field \"field\": must index term freq while using custom TermFrequencyAttribute",
            e.message,
        )
        IOUtils.close(w, dir)
    }

    // sum of term freqs must fit in an int
    @Test
    fun testOverflowInt() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS)

        val doc = Document()
        doc.add(Field("field", "this field should be indexed", fieldType))
        w.addDocument(doc)

        val doc2 = Document()
        val field =
            Field(
                "field",
                CannedTermFreqs(arrayOf("foo", "bar"), intArrayOf(3, Int.MAX_VALUE)),
                fieldType,
            )
        doc2.add(field)
        expectThrows(IllegalArgumentException::class) {
            w.addDocument(doc2)
        }

        val r = DirectoryReader.open(w)
        assertEquals(1, r.numDocs())

        IOUtils.close(r, w, dir)
    }

    @Test
    fun testInvalidTermVectorPositions() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        val doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        fieldType.setStoreTermVectors(true)
        fieldType.setStoreTermVectorPositions(true)
        val field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(42, 128, 17, 100),
                ),
                fieldType,
            )
        doc.add(field)
        val e =
            expectThrows(IllegalArgumentException::class) {
                w.addDocument(doc)
            }
        assertEquals(
            "field \"field\": cannot index term vector positions while using custom TermFrequencyAttribute",
            e.message,
        )
        IOUtils.close(w, dir)
    }

    @Test
    fun testInvalidTermVectorOffsets() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        val doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        fieldType.setStoreTermVectors(true)
        fieldType.setStoreTermVectorOffsets(true)
        val field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(42, 128, 17, 100),
                ),
                fieldType,
            )
        doc.add(field)
        val e =
            expectThrows(IllegalArgumentException::class) {
                w.addDocument(doc)
            }
        assertEquals(
            "field \"field\": cannot index term vector offsets while using custom TermFrequencyAttribute",
            e.message,
        )
        IOUtils.close(w, dir)
    }

    @Test
    fun testTermVectors() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        fieldType.setStoreTermVectors(true)
        var field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(42, 128, 17, 100),
                ),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)

        doc = Document()
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(50, 60, 70, 80),
                ),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)

        val r = DirectoryReader.open(w)

        var fields = r.termVectors().get(0)
        var termsEnum = fields!!.terms("field")!!.iterator()
        assertTrue(termsEnum.seekExact(newBytesRef("bar")))
        assertEquals(228, termsEnum.totalTermFreq())
        var postings = termsEnum.postings(null)
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(228, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        assertTrue(termsEnum.seekExact(newBytesRef("foo")))
        assertEquals(59, termsEnum.totalTermFreq())
        postings = termsEnum.postings(null)
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(59, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        fields = r.termVectors().get(1)
        termsEnum = fields!!.terms("field")!!.iterator()
        assertTrue(termsEnum.seekExact(newBytesRef("bar")))
        assertEquals(140, termsEnum.totalTermFreq())
        postings = termsEnum.postings(null)
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(140, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        assertTrue(termsEnum.seekExact(newBytesRef("foo")))
        assertEquals(120, termsEnum.totalTermFreq())
        postings = termsEnum.postings(null)
        assertNotNull(postings)
        assertEquals(0, postings.nextDoc())
        assertEquals(120, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())

        IOUtils.close(r, w, dir)
    }

    /** Similarity holds onto the FieldInvertState for subsequent verification. */
    private class NeverForgetsSimilarity : Similarity() {
        var lastState: FieldInvertState? = null

        override fun computeNorm(state: FieldInvertState): Long {
            this.lastState = state
            return 1
        }

        override fun scorer(
            boost: Float,
            collectionStats: CollectionStatistics,
            vararg termStats: TermStatistics
        ): SimScorer {
            throw UnsupportedOperationException()
        }

        companion object {
            val INSTANCE = NeverForgetsSimilarity()
        }
    }

    @Test
    fun testFieldInvertState() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setSimilarity(NeverForgetsSimilarity.INSTANCE)
        val w = IndexWriter(dir, iwc)

        val doc = Document()
        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        val field =
            Field(
                "field",
                CannedTermFreqs(
                    arrayOf("foo", "bar", "foo", "bar"),
                    intArrayOf(42, 128, 17, 100),
                ),
                fieldType,
            )
        doc.add(field)
        w.addDocument(doc)
        val fis = NeverForgetsSimilarity.INSTANCE.lastState!!
        assertEquals(228, fis.maxTermFrequency)
        assertEquals(2, fis.uniqueTermCount)
        assertEquals(0, fis.numOverlap)
        assertEquals(287, fis.length)

        IOUtils.close(w, dir)
    }
}
