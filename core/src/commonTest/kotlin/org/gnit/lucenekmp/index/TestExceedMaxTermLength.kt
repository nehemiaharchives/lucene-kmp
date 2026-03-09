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
package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests that a useful exception is thrown when attempting to index a term that is too large
 *
 * @see IndexWriter.MAX_TERM_LENGTH
 */
class TestExceedMaxTermLength : LuceneTestCase() {
    companion object {
        private const val minTestTermLength = IndexWriter.MAX_TERM_LENGTH + 1
        private const val maxTestTermLength = IndexWriter.MAX_TERM_LENGTH * 2
    }

    private lateinit var dir: Directory

    @BeforeTest
    fun createDir() {
        dir = newDirectory()
    }

    @AfterTest
    fun destroyDir() {
        dir.close()
    }

    @Test
    fun testTokenStream() {
        val mockAnalyzer = MockAnalyzer(random())
        mockAnalyzer.setMaxTokenLength(Int.MAX_VALUE)
        val w = IndexWriter(dir, newIndexWriterConfig(random(), mockAnalyzer))
        try {
            val ft = FieldType()
            ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
            ft.setStored(random().nextBoolean())
            ft.freeze()

            val doc = Document()
            if (random().nextBoolean()) {
                // totally ok short field value
                doc.add(
                    Field(
                        TestUtil.randomSimpleString(random(), 1, 10),
                        TestUtil.randomSimpleString(random(), 1, 10),
                        ft
                    )
                )
            }
            // problematic field
            val name = TestUtil.randomSimpleString(random(), 1, 50)
            val value = TestUtil.randomSimpleString(random(), minTestTermLength, maxTestTermLength)
            val f = Field(name, value, ft)
            if (random().nextBoolean()) {
                // totally ok short field value
                doc.add(
                    Field(
                        TestUtil.randomSimpleString(random(), 1, 10),
                        TestUtil.randomSimpleString(random(), 1, 10),
                        ft
                    )
                )
            }
            doc.add(f)

            val expected = expectThrows(IllegalArgumentException::class) {
                w.addDocument(doc)
            }
            val maxLengthMsg = IndexWriter.MAX_TERM_LENGTH.toString()
            val msg = expected.message!!
            assertTrue(msg.contains("immense term"), "IllegalArgumentException didn't mention 'immense term': $msg")
            assertTrue(
                msg.contains(maxLengthMsg),
                "IllegalArgumentException didn't mention max length ($maxLengthMsg): $msg"
            )
            assertTrue(
                msg.contains(name),
                "IllegalArgumentException didn't mention field name ($name): $msg"
            )
            assertTrue(
                msg.contains("bytes can be at most") && msg.contains("in length; got"),
                "IllegalArgumentException didn't mention original message: $msg"
            )
        } finally {
            w.close()
        }
    }

    @Test
    fun testBinaryValue() {
        val w = IndexWriter(dir, newIndexWriterConfig())
        try {
            val ft = FieldType()
            ft.setIndexOptions(
                RandomPicks.randomFrom(
                    random(),
                    arrayOf(IndexOptions.DOCS, IndexOptions.DOCS_AND_FREQS)
                )
            )
            ft.setStored(random().nextBoolean())
            ft.setTokenized(false)
            ft.freeze()

            val doc = Document()
            if (random().nextBoolean()) {
                // totally ok short field value
                doc.add(
                    Field(
                        TestUtil.randomSimpleString(random(), 1, 10),
                        TestUtil.randomBinaryTerm(random(), 10),
                        ft
                    )
                )
            }
            // problematic field
            val name = TestUtil.randomSimpleString(random(), 1, 50)
            val value = TestUtil.randomBinaryTerm(
                random(),
                TestUtil.nextInt(random(), minTestTermLength, maxTestTermLength)
            )
            val f = Field(name, value, ft)
            if (random().nextBoolean()) {
                // totally ok short field value
                doc.add(
                    Field(
                        TestUtil.randomSimpleString(random(), 1, 10),
                        TestUtil.randomBinaryTerm(random(), 10),
                        ft
                    )
                )
            }
            doc.add(f)

            val expected = expectThrows(IllegalArgumentException::class) {
                w.addDocument(doc)
            }
            val maxLengthMsg = IndexWriter.MAX_TERM_LENGTH.toString()
            val msg = expected.message!!
            assertTrue(msg.contains("immense term"), "IllegalArgumentException didn't mention 'immense term': $msg")
            assertTrue(
                msg.contains(maxLengthMsg),
                "IllegalArgumentException didn't mention max length ($maxLengthMsg): $msg"
            )
            assertTrue(
                msg.contains(name),
                "IllegalArgumentException didn't mention field name ($name): $msg"
            )
            assertTrue(
                msg.contains("bytes can be at most") && msg.contains("in length; got"),
                "IllegalArgumentException didn't mention original message: $msg"
            )
        } finally {
            w.close()
        }
    }
}
