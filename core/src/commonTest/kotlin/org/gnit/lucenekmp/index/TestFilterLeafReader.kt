package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestFilterLeafReader : LuceneTestCase() {

    private class TestReader(reader: LeafReader) : FilterLeafReader(reader) {

        private class TestTerms(inTerms: Terms) : FilterTerms(inTerms) {
            override fun iterator(): TermsEnum {
                return TestTermsEnum(super.iterator())
            }
        }

        private class TestTermsEnum(inTermsEnum: TermsEnum) : FilterTermsEnum(inTermsEnum) {
            /** Scan for terms containing the letter 'e'. */
            override fun next(): BytesRef? {
                var text: BytesRef?
                while ((`in`.next().also { text = it }) != null) {
                    if (text!!.utf8ToString().indexOf('e') != -1) {
                        return text
                    }
                }
                return null
            }

            override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
                return TestPositions(super.postings(null, flags)!!)
            }
        }

        /** Filter that only returns odd numbered documents. */
        private class TestPositions(inPostings: PostingsEnum) : FilterPostingsEnum(inPostings) {
            /** Scan for odd numbered documents. */
            override fun nextDoc(): Int {
                var doc: Int
                while ((`in`.nextDoc().also { doc = it }) != NO_MORE_DOCS) {
                    if ((doc % 2) == 1) {
                        return doc
                    }
                }
                return NO_MORE_DOCS
            }
        }

        override fun terms(field: String?): Terms? {
            val terms = super.terms(field)
            return if (terms == null) null else TestTerms(terms)
        }

        override fun getNormValues(field: String): NumericDocValues? {
            val ndv = super.getNormValues(field) ?: return null
            val docsWithTerms = FixedBitSet(maxDoc())
            val termsEnum = terms(field)!!.iterator()
            var postings: PostingsEnum? = null
            while (termsEnum.next() != null) {
                postings = termsEnum.postings(postings, PostingsEnum.NONE.toInt())
                docsWithTerms.or(postings!!)
            }
            return object : FilterNumericDocValues(ndv) {
                override fun longValue(): Long {
                    return if (docsWithTerms.get(docID())) super.longValue() else 0L
                }
            }
        }

        override val coreCacheHelper: IndexReader.CacheHelper?
            get() = null

        override val readerCacheHelper: IndexReader.CacheHelper?
            get() = null
    }

    /**
     * Tests the IndexReader.getFieldNames implementation
     *
     * @throws Exception on error
     */
    @Test
    fun testFilterIndexReader() {
        val directory = newDirectory()

        var writer = IndexWriter(directory, newIndexWriterConfig(MockAnalyzer(random())))

        val d1 = Document()
        d1.add(newTextField("default", "one two", Field.Store.YES))
        writer.addDocument(d1)

        val d2 = Document()
        d2.add(newTextField("default", "one three", Field.Store.YES))
        writer.addDocument(d2)

        val d3 = Document()
        d3.add(newTextField("default", "two four", Field.Store.YES))
        writer.addDocument(d3)
        writer.forceMerge(1)
        writer.close()

        val target: BaseDirectoryWrapper = newDirectory()

        writer = IndexWriter(target, newIndexWriterConfig(MockAnalyzer(random())))
        TestReader(getOnlyLeafReader(DirectoryReader.open(directory))).use { reader ->
            writer.addIndexes(SlowCodecReaderWrapper.wrap(reader))
        }
        writer.close()
        val reader = DirectoryReader.open(target)

        val terms = MultiTerms.getTerms(reader, "default")!!.iterator()
        while (terms.next() != null) {
            assertTrue(terms.term()!!.utf8ToString().indexOf('e') != -1)
        }

        assertEquals(TermsEnum.SeekStatus.FOUND, terms.seekCeil(BytesRef("one")))

        val positions = terms.postings(null, PostingsEnum.ALL.toInt())
        while (positions!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            assertTrue((positions.docID() % 2) == 1)
        }

        reader.close()
        directory.close()
        target.close()
    }

    @Test
    fun testOverrideMethods() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        writer.addDocument(Document().apply { add(newTextField("default", "one three", Field.Store.YES)) })
        val reader = writer.reader
        val leafReader = getOnlyLeafReader(reader)

        val fields = object : FilterLeafReader.FilterFields(object : Fields() {
            override fun iterator(): MutableIterator<String> {
                return mutableListOf("default").iterator()
            }

            override fun terms(field: String?): Terms? {
                return if (field == "default") leafReader.terms(field) else null
            }

            override fun size(): Int {
                return 1
            }
        }) {}
        assertTrue(fields.iterator().hasNext())
        assertNotNull(fields.terms("default"))

        val terms = object : FilterLeafReader.FilterTerms(fields.terms("default")!!) {}
        assertEquals(fields.terms("default")!!.sumTotalTermFreq, terms.sumTotalTermFreq)
        assertEquals(fields.terms("default")!!.sumDocFreq, terms.sumDocFreq)
        assertEquals(fields.terms("default")!!.docCount, terms.docCount)

        val termsEnum = object : FilterLeafReader.FilterTermsEnum(terms.iterator()) {}
        assertNotNull(termsEnum.next())

        val postings = object : FilterLeafReader.FilterPostingsEnum(
            termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
        ) {}
        assertEquals(0, postings.nextDoc())

        reader.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testUnwrap() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        val dr = w.reader
        val r = dr.leaves()[0].reader()
        val r2 = object : FilterLeafReader(r) {
            override val coreCacheHelper: IndexReader.CacheHelper?
                get() = `in`.coreCacheHelper

            override val readerCacheHelper: IndexReader.CacheHelper?
                get() = `in`.readerCacheHelper
        }
        assertEquals(r, r2.delegate)
        assertEquals(r, FilterLeafReader.unwrap(r2))
        w.close()
        dr.close()
        dir.close()
    }
}
