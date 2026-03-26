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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.AutomatonProvider
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Some simple regex tests, mostly converted from contrib's TestRegexQuery. */
class TestRegexpQuery : LuceneTestCase() {
    private lateinit var searcher: IndexSearcher
    private lateinit var reader: IndexReader
    private lateinit var directory: Directory

    companion object {
        private const val FN = "field"
    }

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        val doc = Document()
        doc.add(
            newTextField(
                FN,
                "the quick brown fox jumps over the lazy ??? dog 493432 49344 [foo] 12.3 \\ ς",
                Field.Store.NO,
            ),
        )
        writer.addDocument(doc)
        reader = writer.getReader(true, false)
        writer.close()
        searcher = newSearcher(reader)
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        directory.close()
    }

    private fun newTerm(value: String): Term {
        return Term(FN, value)
    }

    @Throws(Exception::class)
    private fun regexQueryNrHits(regex: String): Long {
        val query = RegexpQuery(newTerm(regex))
        return searcher.count(query).toLong()
    }

    @Throws(Exception::class)
    private fun caseInsensitiveRegexQueryNrHits(regex: String): Long {
        val query =
            RegexpQuery(
                newTerm(regex),
                RegExp.ALL,
                RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE,
                RegexpQuery.DEFAULT_PROVIDER,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE,
                random().nextBoolean(),
            )
        return searcher.count(query).toLong()
    }

    @Test
    @Throws(Exception::class)
    fun testRegex1() {
        assertEquals(1, regexQueryNrHits("q.[aeiou]c.*"))
    }

    @Test
    @Throws(Exception::class)
    fun testRegex2() {
        assertEquals(0, regexQueryNrHits(".[aeiou]c.*"))
    }

    @Test
    @Throws(Exception::class)
    fun testRegex3() {
        assertEquals(0, regexQueryNrHits("q.[aeiou]c"))
    }

    @Test
    @Throws(Exception::class)
    fun testNumericRange() {
        assertEquals(1, regexQueryNrHits("<420000-600000>"))
        assertEquals(0, regexQueryNrHits("<493433-600000>"))
    }

    @Test
    @Throws(Exception::class)
    fun testCharacterClasses() {
        assertEquals(0, regexQueryNrHits("\\d"))
        assertEquals(1, regexQueryNrHits("\\d*"))
        assertEquals(1, regexQueryNrHits("\\d{6}"))
        assertEquals(1, regexQueryNrHits("[a\\d]{6}"))
        assertEquals(1, regexQueryNrHits("\\d{2,7}"))
        assertEquals(0, regexQueryNrHits("\\d{4}"))
        assertEquals(0, regexQueryNrHits("\\dog"))
        assertEquals(1, regexQueryNrHits("493\\d32"))

        assertEquals(1, regexQueryNrHits("\\wox"))
        assertEquals(1, regexQueryNrHits("493\\w32"))
        assertEquals(1, regexQueryNrHits("\\?\\?\\?"))
        assertEquals(1, regexQueryNrHits("\\?\\W\\?"))
        assertEquals(1, regexQueryNrHits("\\?\\S\\?"))

        assertEquals(1, regexQueryNrHits("\\[foo\\]"))
        assertEquals(1, regexQueryNrHits("\\[\\w{3}\\]"))

        assertEquals(0, regexQueryNrHits("\\s.*")) // no matches because all whitespace stripped
        assertEquals(1, regexQueryNrHits("\\S*ck")) // matches quick
        assertEquals(1, regexQueryNrHits("[\\d\\.]{3,10}")) // matches 12.3
        assertEquals(1, regexQueryNrHits("\\d{1,3}(\\.(\\d{1,2}))+")) // matches 12.3

        assertEquals(1, regexQueryNrHits("\\\\"))
        assertEquals(1, regexQueryNrHits("\\\\.*"))

        val expected =
            expectThrows(IllegalArgumentException::class) {
                regexQueryNrHits("\\p")
            }
        assertTrue(expected.message!!.contains("invalid character class"))
    }

    @Test
    @Throws(Exception::class)
    fun testCaseInsensitive() {
        assertEquals(0, regexQueryNrHits("Quick"))
        assertEquals(1, caseInsensitiveRegexQueryNrHits("Quick"))
        assertEquals(1, caseInsensitiveRegexQueryNrHits("Σ"))
        assertEquals(1, caseInsensitiveRegexQueryNrHits("σ"))
    }

    @Test
    @Throws(Exception::class)
    fun testRegexNegatedCharacterClass() {
        assertEquals(1, regexQueryNrHits("[^a-z]"))
        assertEquals(1, regexQueryNrHits("[^03ad]"))
    }

    @Test
    @Throws(Exception::class)
    fun testCustomProvider() {
        val myProvider =
            object : AutomatonProvider {
                // automaton that matches quick or brown
                private val quickBrownAutomaton: Automaton =
                    Operations.union(
                        listOf(
                            Automata.makeString("quick"),
                            Automata.makeString("brown"),
                            Automata.makeString("bob"),
                        ),
                    )

                override fun getAutomaton(name: String): Automaton? {
                    return if (name == "quickBrown") quickBrownAutomaton else null
                }
            }
        val query =
            RegexpQuery(
                newTerm("<quickBrown>"),
                RegExp.ALL,
                myProvider,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
            )
        assertEquals(1L, searcher.search(query, 5).totalHits.value)
    }

    /**
     * Test a corner case for backtracking: In this case the term dictionary has 493432 followed by
     * 49344. When backtracking from 49343... to 4934, it's necessary to test that 4934 itself is ok
     * before trying to append more characters.
     */
    @Test
    @Throws(Exception::class)
    fun testBacktracking() {
        assertEquals(1, regexQueryNrHits("4934[314]"))
    }

    /** Test worst-case for getCommonSuffix optimization */
    @Test
    fun testSlowCommonSuffix() {
        expectThrows(TooComplexToDeterminizeException::class) {
            RegexpQuery(Term("stringvalue", "(.*a){2000}"))
        }
    }
}
