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
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Create an index with terms from 000-999. Generates random regexps according to simple patterns,
 * and validates the correct number of hits are returned.
 */
class TestRegexpRandom : LuceneTestCase() {
    private lateinit var searcher: IndexSearcher
    private lateinit var reader: IndexReader
    private lateinit var dir: Directory

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 50, 1000)),
            )

        val doc = Document()
        val customType = FieldType(TextField.TYPE_STORED)
        customType.setOmitNorms(true)
        val field = newField("field", "", customType)
        doc.add(field)

        for (i in 0..<1000) {
            field.setStringValue(i.toString().padStart(3, '0'))
            writer.addDocument(doc)
        }

        reader = writer.reader
        writer.close()
        searcher = newSearcher(reader)
    }

    private fun N(): Char {
        return (0x30 + random().nextInt(10)).toChar()
    }

    private fun fillPattern(wildcardPattern: String): String {
        val sb = StringBuilder()
        for (i in 0..<wildcardPattern.length) {
            when (wildcardPattern[i]) {
                'N' -> sb.append(N())
                else -> sb.append(wildcardPattern[i])
            }
        }
        return sb.toString()
    }

    @Throws(Exception::class)
    private fun assertPatternHits(pattern: String, numHits: Int) {
        val wq: Query = RegexpQuery(Term("field", fillPattern(pattern)))
        val docs = searcher.search(wq, 25)
        assertEquals(numHits.toLong(), docs.totalHits.value, "Incorrect hits for pattern: $pattern")
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRegexps() {
        val num = atLeast(1)
        for (i in 0..<num) {
            assertPatternHits("NNN", 1)
            assertPatternHits(".NN", 10)
            assertPatternHits("N.N", 10)
            assertPatternHits("NN.", 10)
        }

        for (i in 0..<num) {
            assertPatternHits(".{1,2}N", 100)
            assertPatternHits("N.{1,2}", 100)
            assertPatternHits(".{1,3}", 1000)

            assertPatternHits("NN[3-7]", 5)
            assertPatternHits("N[2-6][3-7]", 25)
            assertPatternHits("[1-5][2-6][3-7]", 125)
            assertPatternHits("[0-4][3-7][4-8]", 125)
            assertPatternHits("[2-6][0-4]N", 25)
            assertPatternHits("[2-6]NN", 5)

            assertPatternHits("NN.*", 10)
            assertPatternHits("N.*", 100)
            assertPatternHits(".*", 1000)

            assertPatternHits(".*NN", 10)
            assertPatternHits(".*N", 100)

            assertPatternHits("N.*N", 10)

            // combo of ? and * operators
            assertPatternHits(".N.*", 100)
            assertPatternHits("N..*", 100)

            assertPatternHits(".*N.", 100)
            assertPatternHits(".*..", 1000)
            assertPatternHits(".*.N", 100)
        }
    }
}
