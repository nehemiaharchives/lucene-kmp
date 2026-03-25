/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.English
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class TestLongValuesSource : LuceneTestCase() {
    companion object {
        private const val LEAST_LONG_VALUE = 45L
    }

    private lateinit var dir: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = TestUtil.nextInt(random(), 2049, 4000)
        for (i in 0..<numDocs) {
            val document = Document()
            document.add(newTextField("english", English.intToEnglish(i), Field.Store.NO))
            document.add(newTextField("oddeven", if (i % 2 == 0) "even" else "odd", Field.Store.NO))
            document.add(NumericDocValuesField("int", random().nextInt().toLong()))
            document.add(NumericDocValuesField("long", random().nextLong()))
            if (i == 545) {
                document.add(NumericDocValuesField("onefield", LEAST_LONG_VALUE))
            }
            iw.addDocument(document)
        }
        reader = iw.reader
        iw.close()
        searcher = newSearcher(reader)
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSortMissingZeroDefault() {
        // docs w/no value get default missing value = 0

        val onefield = LongValuesSource.fromLongField("onefield")
        // sort decreasing
        var results = searcher.search(MatchAllDocsQuery(), 1, Sort(onefield.getSortField(true)))
        var first = results.scoreDocs[0] as FieldDoc
        assertEquals(LEAST_LONG_VALUE, first.fields!![0])

        // sort increasing
        results = searcher.search(MatchAllDocsQuery(), 1, Sort(onefield.getSortField(false)))
        first = results.scoreDocs[0] as FieldDoc
        assertEquals(0L, first.fields!![0])
    }

    @Test
    @Throws(Exception::class)
    fun testSortMissingExplicit() {
        // docs w/no value get provided missing value

        val onefield = LongValuesSource.fromLongField("onefield")

        // sort decreasing, missing last
        var oneFieldSort = onefield.getSortField(true)
        oneFieldSort.missingValue = Long.MIN_VALUE

        var results = searcher.search(MatchAllDocsQuery(), 1, Sort(oneFieldSort))
        var first = results.scoreDocs[0] as FieldDoc
        assertEquals(LEAST_LONG_VALUE, first.fields!![0])

        // sort increasing, missing last
        oneFieldSort = onefield.getSortField(false)
        oneFieldSort.missingValue = Long.MAX_VALUE

        results = searcher.search(MatchAllDocsQuery(), 1, Sort(oneFieldSort))
        first = results.scoreDocs[0] as FieldDoc
        assertEquals(LEAST_LONG_VALUE, first.fields!![0])
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleFieldEquivalences() {
        checkSorts(
            MatchAllDocsQuery(),
            Sort(SortField("int", SortField.Type.INT, random().nextBoolean()))
        )
        checkSorts(
            MatchAllDocsQuery(),
            Sort(SortField("long", SortField.Type.LONG, random().nextBoolean()))
        )
    }

    @Test
    fun testHashCodeAndEquals() {
        val vs1 = LongValuesSource.fromLongField("long")
        val vs2 = LongValuesSource.fromLongField("long")
        assertEquals(vs1, vs2)
        assertEquals(vs1.hashCode(), vs2.hashCode())
        val v3 = LongValuesSource.fromLongField("int")
        assertFalse(vs1 == v3)
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleFieldSortables() {
        val n = atLeast(4)
        repeat(n) {
            val sort = randomSort()
            checkSorts(MatchAllDocsQuery(), sort)
            checkSorts(TermQuery(Term("english", "one")), sort)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRewriteSame() {
        val longField = LongValuesSource.constant(1L).getSortField(false)
        assertSame(longField, longField.rewrite(searcher))
    }

    @Throws(Exception::class)
    fun randomSort(): Sort {
        val reversed = random().nextBoolean()
        val fields = arrayOf(
            SortField("int", SortField.Type.INT, reversed),
            SortField("long", SortField.Type.LONG, reversed)
        )
        fields.shuffle(random())
        val numSorts = TestUtil.nextInt(random(), 1, fields.size)
        return Sort(*ArrayUtil.copyOfSubArray(fields, 0, numSorts))
    }

    // Take a Sort, and replace any field sorts with Sortables
    fun convertSortToSortable(sort: Sort): Sort {
        val original = sort.sort
        val mutated = arrayOfNulls<SortField>(original.size)
        for (i in mutated.indices) {
            if (random().nextInt(3) > 0) {
                val s = original[i]
                val reverse = s.type == SortField.Type.SCORE || s.reverse
                when (s.type) {
                    SortField.Type.INT -> mutated[i] = LongValuesSource.fromIntField(s.field!!).getSortField(reverse)
                    SortField.Type.LONG -> mutated[i] = LongValuesSource.fromLongField(s.field!!).getSortField(reverse)
                    SortField.Type.CUSTOM,
                    SortField.Type.DOUBLE,
                    SortField.Type.FLOAT,
                    SortField.Type.DOC,
                    SortField.Type.REWRITEABLE,
                    SortField.Type.STRING,
                    SortField.Type.STRING_VAL,
                    SortField.Type.SCORE -> mutated[i] = original[i]
                }
            } else {
                mutated[i] = original[i]
            }
        }

        return Sort(*mutated.requireNoNulls())
    }

    @Throws(Exception::class)
    fun checkSorts(query: Query, sort: Sort) {
        val size = TestUtil.nextInt(random(), 1, searcher.indexReader.maxDoc() / 5)
        val mutatedSort = convertSortToSortable(sort)
        var actual: TopDocs = searcher.search(query, size, mutatedSort, random().nextBoolean())
        var expected: TopDocs = searcher.search(query, size, sort, random().nextBoolean())

        CheckHits.checkEqual(query, expected.scoreDocs, actual.scoreDocs)

        if (size.toLong() < actual.totalHits.value) {
            expected = searcher.searchAfter(expected.scoreDocs[size - 1], query, size, sort)
            actual = searcher.searchAfter(actual.scoreDocs[size - 1], query, size, mutatedSort)
            CheckHits.checkEqual(query, expected.scoreDocs, actual.scoreDocs)
        }
    }
}
