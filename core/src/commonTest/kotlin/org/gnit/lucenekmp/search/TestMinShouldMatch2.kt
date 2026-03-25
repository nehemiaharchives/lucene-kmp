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
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.BulkScorerWrapperScorer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** tests BooleanScorer2's minShouldMatch */
class TestMinShouldMatch2 : LuceneTestCase() {
    enum class Mode {
        SCORER,
        BULK_SCORER,
        DOC_VALUES,
    }

    @Throws(Exception::class)
    fun beforeClass() {
        if (dir != null) {
            return
        }
        dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir!!)
        val numDocs = atLeast(300)
        repeat(numDocs) {
            val doc = Document()

            addSome(doc, alwaysTerms)

            if (random().nextInt(100) < 90) {
                addSome(doc, commonTerms)
            }
            if (random().nextInt(100) < 50) {
                addSome(doc, mediumTerms)
            }
            if (random().nextInt(100) < 10) {
                addSome(doc, rareTerms)
            }
            iw.addDocument(doc)
        }
        iw.forceMerge(1)
        iw.close()
        r = DirectoryReader.open(dir!!)
        reader = getOnlyLeafReader(r!!)
        searcher = IndexSearcher(reader!!)
        searcher!!.similarity = ClassicSimilarity()
    }

    @Throws(Exception::class)
    fun afterClass() {
        reader?.close()
        dir?.close()
        searcher = null
        reader = null
        r = null
        dir = null
    }

    private fun addSome(doc: Document, values: Array<String>) {
        val list = values.toMutableList()
        list.shuffle(random())
        val howMany = TestUtil.nextInt(random(), 1, list.size)
        for (i in 0..<howMany) {
            doc.add(StringField("field", list[i], Field.Store.NO))
            doc.add(SortedSetDocValuesField("dv", BytesRef(list[i])))
        }
    }

    @Throws(Exception::class)
    private fun scorer(values: Array<String>, minShouldMatch: Int, mode: Mode): Scorer? {
        val bq = BooleanQuery.Builder()
        for (value in values) {
            bq.add(TermQuery(Term("field", value)), BooleanClause.Occur.SHOULD)
        }
        bq.setMinimumNumberShouldMatch(minShouldMatch)

        val weight = searcher!!.createWeight(searcher!!.rewrite(bq.build()), ScoreMode.COMPLETE, 1f) as BooleanWeight

        return when (mode) {
            Mode.DOC_VALUES -> SlowMinShouldMatchScorer(weight, reader!!, searcher!!)
            Mode.SCORER -> weight.scorer(reader!!.context)
            Mode.BULK_SCORER -> {
                val ss = weight.scorerSupplier(reader!!.context)
                val bulkScorer = ss?.bulkScorer()
                if (bulkScorer == null) {
                    if (weight.scorer(reader!!.context) != null) {
                        throw AssertionError("BooleanScorer should be applicable for this query")
                    }
                    null
                } else {
                    BulkScorerWrapperScorer(bulkScorer, TestUtil.nextInt(random(), 1, 100))
                }
            }
        }
    }

    @Throws(Exception::class)
    private fun assertNext(expected: Scorer, actual: Scorer?) {
        if (actual == null) {
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, expected.iterator().nextDoc())
            return
        }
        var doc: Int
        val expectedIt = expected.iterator()
        val actualIt = actual.iterator()
        while (expectedIt.nextDoc().also { doc = it } != DocIdSetIterator.NO_MORE_DOCS) {
            assertEquals(doc, actualIt.nextDoc())
            val expectedScore = expected.score()
            val actualScore = actual.score()
            assertEquals(expectedScore.toDouble(), actualScore.toDouble(), 0.0)
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, actualIt.nextDoc())
    }

    @Throws(Exception::class)
    private fun assertAdvance(expected: Scorer, actual: Scorer?, amount: Int) {
        if (actual == null) {
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, expected.iterator().nextDoc())
            return
        }
        val expectedIt = expected.iterator()
        val actualIt = actual.iterator()
        var prevDoc = 0
        var doc: Int
        while (expectedIt.advance(prevDoc + amount).also { doc = it } != DocIdSetIterator.NO_MORE_DOCS) {
            assertEquals(doc, actualIt.advance(prevDoc + amount))
            val expectedScore = expected.score()
            val actualScore = actual.score()
            assertEquals(expectedScore.toDouble(), actualScore.toDouble(), 0.0)
            prevDoc = doc
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, actualIt.advance(prevDoc + amount))
    }

    /** simple test for next(): minShouldMatch=2 on 3 terms (one common, one medium, one rare) */
    @Test
    @Throws(Exception::class)
    fun testNextCMR2() {
        beforeClass()
        for (common in commonTerms.indices) {
            for (medium in mediumTerms.indices) {
                for (rare in rareTerms.indices) {
                    var expected = scorer(arrayOf(commonTerms[common], mediumTerms[medium], rareTerms[rare]), 2, Mode.DOC_VALUES)!!
                    var actual = scorer(arrayOf(commonTerms[common], mediumTerms[medium], rareTerms[rare]), 2, Mode.SCORER)
                    assertNext(expected, actual)

                    expected = scorer(arrayOf(commonTerms[common], mediumTerms[medium], rareTerms[rare]), 2, Mode.DOC_VALUES)!!
                    actual = scorer(arrayOf(commonTerms[common], mediumTerms[medium], rareTerms[rare]), 2, Mode.BULK_SCORER)
                    assertNext(expected, actual)
                }
            }
        }
    }

    /** simple test for advance(): minShouldMatch=2 on 3 terms (one common, one medium, one rare) */
    @Test
    @Throws(Exception::class)
    fun testAdvanceCMR2() {
        beforeClass()
        for (amount in 25..<200 step 25) {
            for (common in commonTerms.indices) {
                for (medium in mediumTerms.indices) {
                    for (rare in rareTerms.indices) {
                        var expected = scorer(arrayOf(commonTerms[common], mediumTerms[medium], rareTerms[rare]), 2, Mode.DOC_VALUES)!!
                        var actual = scorer(arrayOf(commonTerms[common], mediumTerms[medium], rareTerms[rare]), 2, Mode.SCORER)
                        assertAdvance(expected, actual, amount)

                        expected = scorer(arrayOf(commonTerms[common], mediumTerms[medium], rareTerms[rare]), 2, Mode.DOC_VALUES)!!
                        actual = scorer(arrayOf(commonTerms[common], mediumTerms[medium], rareTerms[rare]), 2, Mode.BULK_SCORER)
                        assertAdvance(expected, actual, amount)
                    }
                }
            }
        }
    }

    /** test next with giant bq of all terms with varying minShouldMatch */
    @Test
    @Throws(Exception::class)
    fun testNextAllTerms() {
        beforeClass()
        val termsList = mutableListOf<String>()
        termsList.addAll(commonTerms)
        termsList.addAll(mediumTerms)
        termsList.addAll(rareTerms)
        val terms = termsList.toTypedArray()

        for (minNrShouldMatch in 1..<terms.size) {
            var expected = scorer(terms, minNrShouldMatch, Mode.DOC_VALUES)!!
            var actual = scorer(terms, minNrShouldMatch, Mode.SCORER)
            assertNext(expected, actual)

            expected = scorer(terms, minNrShouldMatch, Mode.DOC_VALUES)!!
            actual = scorer(terms, minNrShouldMatch, Mode.BULK_SCORER)
            assertNext(expected, actual)
        }
    }

    /** test advance with giant bq of all terms with varying minShouldMatch */
    @Test
    @Throws(Exception::class)
    fun testAdvanceAllTerms() {
        beforeClass()
        val termsList = mutableListOf<String>()
        termsList.addAll(commonTerms)
        termsList.addAll(mediumTerms)
        termsList.addAll(rareTerms)
        val terms = termsList.toTypedArray()

        for (amount in 25..<200 step 25) {
            for (minNrShouldMatch in 1..<terms.size) {
                var expected = scorer(terms, minNrShouldMatch, Mode.DOC_VALUES)!!
                var actual = scorer(terms, minNrShouldMatch, Mode.SCORER)
                assertAdvance(expected, actual, amount)

                expected = scorer(terms, minNrShouldMatch, Mode.DOC_VALUES)!!
                actual = scorer(terms, minNrShouldMatch, Mode.BULK_SCORER)
                assertAdvance(expected, actual, amount)
            }
        }
    }

    /** test next with varying numbers of terms with varying minShouldMatch */
    @Test
    @Throws(Exception::class)
    fun testNextVaryingNumberOfTerms() {
        beforeClass()
        val termsList = mutableListOf<String>()
        termsList.addAll(commonTerms)
        termsList.addAll(mediumTerms)
        termsList.addAll(rareTerms)
        termsList.shuffle(random())
        for (numTerms in 2..termsList.size) {
            val terms = termsList.subList(0, numTerms).toTypedArray()
            for (minNrShouldMatch in 1..<terms.size) {
                var expected = scorer(terms, minNrShouldMatch, Mode.DOC_VALUES)!!
                var actual = scorer(terms, minNrShouldMatch, Mode.SCORER)
                assertNext(expected, actual)

                expected = scorer(terms, minNrShouldMatch, Mode.DOC_VALUES)!!
                actual = scorer(terms, minNrShouldMatch, Mode.BULK_SCORER)
                assertNext(expected, actual)
            }
        }
    }

    /** test advance with varying numbers of terms with varying minShouldMatch */
    @Nightly
    @Test
    @Throws(Exception::class)
    fun testAdvanceVaryingNumberOfTerms() {
        beforeClass()
        val termsList = mutableListOf<String>()
        termsList.addAll(commonTerms)
        termsList.addAll(mediumTerms)
        termsList.addAll(rareTerms)
        termsList.shuffle(random())

        for (amount in 25..<50 step 25) { // TODO reduced from <200 to <50 for dev speed
            for (numTerms in 2..termsList.size) {
                val terms = termsList.subList(0, numTerms).toTypedArray()
                for (minNrShouldMatch in 1..<terms.size) {
                    var expected = scorer(terms, minNrShouldMatch, Mode.DOC_VALUES)!!
                    var actual = scorer(terms, minNrShouldMatch, Mode.SCORER)
                    assertAdvance(expected, actual, amount)

                    expected = scorer(terms, minNrShouldMatch, Mode.DOC_VALUES)!!
                    actual = scorer(terms, minNrShouldMatch, Mode.SCORER)
                    assertAdvance(expected, actual, amount)
                }
            }
        }
    }

    // TODO: more tests

    // a slow min-should match scorer that uses a docvalues field.
    // later, we can make debugging easier as it can record the set of ords it currently matched
    // and e.g. print out their values and so on for the document
    private class SlowMinShouldMatchScorer(
        weight: BooleanWeight,
        reader: LeafReader,
        searcher: IndexSearcher,
    ) : Scorer() {
        var currentDoc = -1 // current docid
        var currentMatched = -1 // current number of terms matched

        val dv: SortedSetDocValues = reader.getSortedSetDocValues("dv")!!
        val maxDoc = reader.maxDoc()

        val ords = mutableSetOf<Long>()
        val sims: Array<SimScorer?> = arrayOfNulls(dv.valueCount.toInt())
        val norms: NumericDocValues? = reader.getNormValues("field")
        val minNrShouldMatch: Int = weight.query.minimumNumberShouldMatch

        var score = Float.NaN.toDouble()

        init {
            val bq = weight.query
            for (clause in bq.clauses()) {
                assert(!clause.isProhibited)
                assert(!clause.isRequired)
                val term = (clause.query as TermQuery).getTerm()
                val ord = dv.lookupTerm(term.bytes())
                if (ord >= 0) {
                    val success = ords.add(ord)
                    assert(success) // no dups
                    val ts = TermStates.build(searcher, term, true)
                    sims[ord.toInt()] =
                        weight.similarity.scorer(
                            1f,
                            searcher.collectionStatistics("field")!!,
                            searcher.termStatistics(term, ts.docFreq(), ts.totalTermFreq()),
                        )
                }
            }
        }

        @Throws(IOException::class)
        override fun score(): Float {
            assert(score != 0.0) { currentMatched.toString() }
            return score.toFloat()
        }

        @Throws(IOException::class)
        override fun getMaxScore(upTo: Int): Float {
            return Float.POSITIVE_INFINITY
        }

        override fun docID(): Int {
            return currentDoc
        }

        override fun iterator(): DocIdSetIterator {
            return object : DocIdSetIterator() {
                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    assert(currentDoc != NO_MORE_DOCS)
                    for (doc in currentDoc + 1..<maxDoc) {
                        currentDoc = doc
                        currentMatched = 0
                        score = 0.0
                        if (currentDoc > dv.docID()) {
                            dv.advance(currentDoc)
                        }
                        if (currentDoc != dv.docID()) {
                            continue
                        }
                        var norm = 1L
                        if (norms != null && norms.advanceExact(currentDoc)) {
                            norm = norms.longValue()
                        }
                        repeat(dv.docValueCount()) {
                            val ord = dv.nextOrd()
                            if (ords.contains(ord)) {
                                currentMatched++
                                score += sims[ord.toInt()]!!.score(1f, norm)
                            }
                        }
                        if (currentMatched >= minNrShouldMatch) {
                            return currentDoc
                        }
                    }
                    currentDoc = NO_MORE_DOCS
                    return currentDoc
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    var doc: Int
                    while (nextDoc().also { doc = it } < target) {
                    }
                    return doc
                }

                override fun cost(): Long {
                    return maxDoc.toLong()
                }

                override fun docID(): Int {
                    return currentDoc
                }
            }
        }
    }

    companion object {
        var dir: Directory? = null
        var r: DirectoryReader? = null
        var reader: LeafReader? = null
        var searcher: IndexSearcher? = null

        val alwaysTerms = arrayOf("a")
        val commonTerms = arrayOf("b", "c", "d")
        val mediumTerms = arrayOf("e", "f", "g")
        val rareTerms =
            arrayOf(
                "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            )
    }
}
