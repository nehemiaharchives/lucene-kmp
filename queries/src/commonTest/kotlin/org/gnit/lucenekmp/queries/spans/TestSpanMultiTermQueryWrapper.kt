package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.FuzzyQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.RegexpQuery
import org.gnit.lucenekmp.search.WildcardQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests for [SpanMultiTermQueryWrapper], wrapping a few MultiTermQueries. */
class TestSpanMultiTermQueryWrapper : LuceneTestCase() {
    private lateinit var directory: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher

    @BeforeTest
    fun setUp() {
        directory = newDirectory()
        val iw = RandomIndexWriter(random(), directory)
        val doc = Document()
        val field = newTextField("field", "", Field.Store.NO)
        doc.add(field)

        field.setStringValue("quick brown fox")
        iw.addDocument(doc)
        field.setStringValue("jumps over lazy broun dog")
        iw.addDocument(doc)
        field.setStringValue("jumps over extremely very lazy broxn dog")
        iw.addDocument(doc)
        reader = iw.reader
        iw.close()
        searcher = newSearcher(reader)
    }

    @AfterTest
    fun tearDown() {
        reader.close()
        directory.close()
    }

    @Test
    fun testWildcard() {
        val wq = WildcardQuery(Term("field", "bro?n"))
        val swq: SpanQuery = SpanMultiTermQueryWrapper(wq)
        // will only match quick brown fox
        val sfq = SpanFirstQuery(swq, 2)
        assertEquals(1, searcher.count(sfq))
    }

    @Test
    fun testPrefix() {
        val wq = WildcardQuery(Term("field", "extrem*"))
        val swq: SpanQuery = SpanMultiTermQueryWrapper(wq)
        // will only match "jumps over extremely very lazy broxn dog"
        val sfq = SpanFirstQuery(swq, 3)
        assertEquals(1, searcher.count(sfq))
    }

    @Test
    fun testFuzzy() {
        val fq = FuzzyQuery(Term("field", "broan"))
        val sfq: SpanQuery = SpanMultiTermQueryWrapper(fq)
        // will not match quick brown fox
        val sprq = SpanPositionRangeQuery(sfq, 3, 6)
        assertEquals(2, searcher.count(sprq))
    }

    @Test
    fun testFuzzy2() {
        // maximum of 1 term expansion
        val fq = FuzzyQuery(Term("field", "broan"), 1, 0, 1, false)
        val sfq: SpanQuery = SpanMultiTermQueryWrapper(fq)
        // will only match jumps over lazy broun dog
        val sprq = SpanPositionRangeQuery(sfq, 0, 100)
        assertEquals(1, searcher.count(sprq))
    }

    @Test
    fun testNoSuchMultiTermsInNear() {
        // test to make sure non existent multiterms aren't throwing null pointer exceptions
        val fuzzyNoSuch = FuzzyQuery(Term("field", "noSuch"), 1, 0, 1, false)
        val spanNoSuch: SpanQuery = SpanMultiTermQueryWrapper(fuzzyNoSuch)
        val term: SpanQuery = SpanTermQuery(Term("field", "brown"))
        var near: SpanQuery = SpanNearQuery(arrayOf(term, spanNoSuch), 1, true)
        assertEquals(0, searcher.count(near))
        // flip order
        near = SpanNearQuery(arrayOf(spanNoSuch, term), 1, true)
        assertEquals(0, searcher.count(near))

        val wcNoSuch = WildcardQuery(Term("field", "noSuch*"))
        val spanWCNoSuch: SpanQuery = SpanMultiTermQueryWrapper(wcNoSuch)
        near = SpanNearQuery(arrayOf(term, spanWCNoSuch), 1, true)
        assertEquals(0, searcher.count(near))

        val rgxNoSuch = RegexpQuery(Term("field", "noSuch"))
        val spanRgxNoSuch: SpanQuery = SpanMultiTermQueryWrapper(rgxNoSuch)
        near = SpanNearQuery(arrayOf(term, spanRgxNoSuch), 1, true)
        assertEquals(0, searcher.count(near))

        val prfxNoSuch = PrefixQuery(Term("field", "noSuch"))
        val spanPrfxNoSuch: SpanQuery = SpanMultiTermQueryWrapper(prfxNoSuch)
        near = SpanNearQuery(arrayOf(term, spanPrfxNoSuch), 1, true)
        assertEquals(0, searcher.count(near))

        // test single noSuch
        near = SpanNearQuery(arrayOf(spanPrfxNoSuch), 1, true)
        assertEquals(0, searcher.count(near))

        // test double noSuch
        near = SpanNearQuery(arrayOf(spanPrfxNoSuch, spanPrfxNoSuch), 1, true)
        assertEquals(0, searcher.count(near))
    }

    @Test
    fun testNoSuchMultiTermsInNotNear() {
        // test to make sure non existent multiterms aren't throwing non-matching field exceptions
        val fuzzyNoSuch = FuzzyQuery(Term("field", "noSuch"), 1, 0, 1, false)
        val spanNoSuch: SpanQuery = SpanMultiTermQueryWrapper(fuzzyNoSuch)
        val term: SpanQuery = SpanTermQuery(Term("field", "brown"))
        var notNear = SpanNotQuery(term, spanNoSuch, 0, 0)
        assertEquals(1, searcher.count(notNear))

        // flip
        notNear = SpanNotQuery(spanNoSuch, term, 0, 0)
        assertEquals(0, searcher.count(notNear))

        // both noSuch
        notNear = SpanNotQuery(spanNoSuch, spanNoSuch, 0, 0)
        assertEquals(0, searcher.count(notNear))

        val wcNoSuch = WildcardQuery(Term("field", "noSuch*"))
        val spanWCNoSuch: SpanQuery = SpanMultiTermQueryWrapper(wcNoSuch)
        notNear = SpanNotQuery(term, spanWCNoSuch, 0, 0)
        assertEquals(1, searcher.count(notNear))

        val rgxNoSuch = RegexpQuery(Term("field", "noSuch"))
        val spanRgxNoSuch: SpanQuery = SpanMultiTermQueryWrapper(rgxNoSuch)
        notNear = SpanNotQuery(term, spanRgxNoSuch, 1, 1)
        assertEquals(1, searcher.count(notNear))

        val prfxNoSuch = PrefixQuery(Term("field", "noSuch"))
        val spanPrfxNoSuch: SpanQuery = SpanMultiTermQueryWrapper(prfxNoSuch)
        notNear = SpanNotQuery(term, spanPrfxNoSuch, 1, 1)
        assertEquals(1, searcher.count(notNear))
    }

    @Test
    fun testNoSuchMultiTermsInOr() {
        // test to make sure non existent multiterms aren't throwing null pointer exceptions
        val fuzzyNoSuch = FuzzyQuery(Term("field", "noSuch"), 1, 0, 1, false)
        val spanNoSuch: SpanQuery = SpanMultiTermQueryWrapper(fuzzyNoSuch)
        val term: SpanQuery = SpanTermQuery(Term("field", "brown"))
        var near = SpanOrQuery(term, spanNoSuch)
        assertEquals(1, searcher.count(near))

        // flip
        near = SpanOrQuery(spanNoSuch, term)
        assertEquals(1, searcher.count(near))

        val wcNoSuch = WildcardQuery(Term("field", "noSuch*"))
        val spanWCNoSuch: SpanQuery = SpanMultiTermQueryWrapper(wcNoSuch)
        near = SpanOrQuery(term, spanWCNoSuch)
        assertEquals(1, searcher.count(near))

        val rgxNoSuch = RegexpQuery(Term("field", "noSuch"))
        val spanRgxNoSuch: SpanQuery = SpanMultiTermQueryWrapper(rgxNoSuch)
        near = SpanOrQuery(term, spanRgxNoSuch)
        assertEquals(1, searcher.count(near))

        val prfxNoSuch = PrefixQuery(Term("field", "noSuch"))
        val spanPrfxNoSuch: SpanQuery = SpanMultiTermQueryWrapper(prfxNoSuch)
        near = SpanOrQuery(term, spanPrfxNoSuch)
        assertEquals(1, searcher.count(near))

        near = SpanOrQuery(spanPrfxNoSuch)
        assertEquals(0, searcher.count(near))

        near = SpanOrQuery(spanPrfxNoSuch, spanPrfxNoSuch)
        assertEquals(0, searcher.count(near))
    }

    @Test
    fun testNoSuchMultiTermsInSpanFirst() {
        // this hasn't been a problem
        val fuzzyNoSuch = FuzzyQuery(Term("field", "noSuch"), 1, 0, 1, false)
        val spanNoSuch: SpanQuery = SpanMultiTermQueryWrapper(fuzzyNoSuch)
        var spanFirst: SpanQuery = SpanFirstQuery(spanNoSuch, 10)

        assertEquals(0, searcher.count(spanFirst))

        val wcNoSuch = WildcardQuery(Term("field", "noSuch*"))
        val spanWCNoSuch: SpanQuery = SpanMultiTermQueryWrapper(wcNoSuch)
        spanFirst = SpanFirstQuery(spanWCNoSuch, 10)
        assertEquals(0, searcher.count(spanFirst))

        val rgxNoSuch = RegexpQuery(Term("field", "noSuch"))
        val spanRgxNoSuch: SpanQuery = SpanMultiTermQueryWrapper(rgxNoSuch)
        spanFirst = SpanFirstQuery(spanRgxNoSuch, 10)
        assertEquals(0, searcher.count(spanFirst))

        val prfxNoSuch = PrefixQuery(Term("field", "noSuch"))
        val spanPrfxNoSuch: SpanQuery = SpanMultiTermQueryWrapper(prfxNoSuch)
        spanFirst = SpanFirstQuery(spanPrfxNoSuch, 10)
        assertEquals(0, searcher.count(spanFirst))
    }

    @Test
    fun testWrappedQueryIsNotModified() {
        val pq = PrefixQuery(Term("field", "test"))
        val pqHash = pq.hashCode()
        val wrapper = SpanMultiTermQueryWrapper(pq)
        assertEquals(pqHash, pq.hashCode())
        wrapper.setRewriteMethod(
            object : SpanMultiTermQueryWrapper.SpanRewriteMethod() {
                @Throws(IOException::class)
                override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): SpanQuery {
                    throw UnsupportedOperationException()
                }
            },
        )
        assertEquals(pqHash, pq.hashCode())
    }
}
