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
import org.gnit.lucenekmp.index.FilteredTermsEnum
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.StringHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Create an index with random unicode terms Generates random prefix queries, and validates against
 * a simple impl.
 */
class TestPrefixRandom : LuceneTestCase() {
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
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.KEYWORD, false))
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 50, 1000)),
            )

        val doc = Document()
        val field = newStringField("field", "", Field.Store.NO)
        doc.add(field)

        val num = atLeast(1000)
        repeat(num) {
            field.setStringValue(TestUtil.randomUnicodeString(random(), 10))
            writer.addDocument(doc)
        }
        reader = writer.getReader(true, false)
        searcher = newSearcher(reader)
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    /** a stupid prefix query that just blasts thru the terms */
    private class DumbPrefixQuery(term: Term) :
        MultiTermQuery(term.field(), CONSTANT_SCORE_BLENDED_REWRITE) {
        private val prefix: BytesRef = term.bytes()

        @Throws(IOException::class)
        override fun getTermsEnum(terms: Terms, atts: AttributeSource): TermsEnum {
            return SimplePrefixTermsEnum(terms.iterator(), prefix)
        }

        private class SimplePrefixTermsEnum(tenum: TermsEnum, private val prefix: BytesRef) :
            FilteredTermsEnum(tenum) {
            init {
                setInitialSeekTerm(BytesRef(""))
            }

            @Throws(IOException::class)
            override fun accept(term: BytesRef): AcceptStatus {
                return if (StringHelper.startsWith(term, prefix)) {
                    AcceptStatus.YES
                } else {
                    AcceptStatus.NO
                }
            }
        }

        override fun toString(field: String?): String {
            return "$field:$prefix"
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) {
                return false
            }
            val that = other as DumbPrefixQuery
            return prefix == that.prefix
        }

        override fun hashCode(): Int {
            return 31 * super.hashCode() + prefix.hashCode()
        }
    }

    /** test a bunch of random prefixes */
    @Test
    @Throws(Exception::class)
    fun testPrefixes() {
        val num = atLeast(100)
        repeat(num) {
            assertSame(TestUtil.randomUnicodeString(random(), 5))
        }
    }

    /** check that the # of hits is the same as from a very simple prefixquery implementation. */
    @Throws(IOException::class)
    private fun assertSame(prefix: String) {
        val smart = PrefixQuery(Term("field", prefix))
        val dumb = DumbPrefixQuery(Term("field", prefix))

        val smartDocs = searcher.search(smart, 25)
        val dumbDocs = searcher.search(dumb, 25)
        CheckHits.checkEqual(smart, smartDocs.scoreDocs, dumbDocs.scoreDocs)
    }
}
