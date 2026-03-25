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

import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestNGramPhraseQuery : LuceneTestCase() {
    private lateinit var reader: IndexReader
    private lateinit var directory: Directory
    private lateinit var searcher: IndexSearcher

    @BeforeTest
    @Throws(Exception::class)
    fun beforeClass() {
        directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        writer.close()
        reader = DirectoryReader.open(directory)
        searcher = IndexSearcher(reader)
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        reader.close()
        directory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRewrite() {
        // bi-gram test ABC => AB/BC => AB/BC
        val pq1 = NGramPhraseQuery(2, PhraseQuery("f", "AB", "BC"))

        var q = pq1.rewrite(searcher)
        assertSame(q, q.rewrite(searcher))
        val rewritten1 = assertIs<PhraseQuery>(q)
        assertContentEquals(arrayOf(Term("f", "AB"), Term("f", "BC")), rewritten1.terms)
        assertContentEquals(intArrayOf(0, 1), rewritten1.positions)

        // bi-gram test ABCD => AB/BC/CD => AB//CD
        val pq2 = NGramPhraseQuery(2, PhraseQuery("f", "AB", "BC", "CD"))

        q = pq2.rewrite(searcher)
        assertTrue(q is PhraseQuery)
        assertTrue(pq2 !== q)
        val rewritten2 = assertIs<PhraseQuery>(q)
        assertContentEquals(arrayOf(Term("f", "AB"), Term("f", "CD")), rewritten2.terms)
        assertContentEquals(intArrayOf(0, 2), rewritten2.positions)

        // tri-gram test ABCDEFGH => ABC/BCD/CDE/DEF/EFG/FGH => ABC///DEF//FGH
        val pq3 =
            NGramPhraseQuery(3, PhraseQuery("f", "ABC", "BCD", "CDE", "DEF", "EFG", "FGH"))

        q = pq3.rewrite(searcher)
        assertTrue(q is PhraseQuery)
        assertTrue(pq3 !== q)
        val rewritten3 = assertIs<PhraseQuery>(q)
        assertContentEquals(
            arrayOf(Term("f", "ABC"), Term("f", "DEF"), Term("f", "FGH")),
            rewritten3.terms
        )
        assertContentEquals(intArrayOf(0, 3, 5), rewritten3.positions)
    }
}
