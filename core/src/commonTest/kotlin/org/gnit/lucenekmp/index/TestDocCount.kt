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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests the Terms.docCount statistic */
class TestDocCount : LuceneTestCase() {
    @Test
    fun testSimple() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)
        for (i in 0..<numDocs) {
            iw.addDocument(doc())
        }
        var ir: IndexReader = iw.getReader(true, false)
        verifyCount(ir)
        ir.close()
        iw.forceMerge(1)
        ir = iw.getReader(true, false)
        verifyCount(ir)
        ir.close()
        iw.close()
        dir.close()
    }

    private fun doc(): Document {
        val doc = Document()
        val numFields = TestUtil.nextInt(random(), 1, 10)
        for (i in 0..<numFields) {
            doc.add(
                newStringField(
                    "${TestUtil.nextInt(random(), 'a'.code, 'z'.code).toChar()}",
                    "${TestUtil.nextInt(random(), 'a'.code, 'z'.code).toChar()}",
                    Field.Store.NO,
                ),
            )
        }
        return doc
    }

    private fun verifyCount(ir: IndexReader) {
        val fields = FieldInfos.getIndexedFields(ir)
        for (field in fields) {
            val terms = MultiTerms.getTerms(ir, field)
            if (terms == null) {
                continue
            }
            val docCount = terms.docCount
            val visited = FixedBitSet(ir.maxDoc())
            val te = terms.iterator()
            while (te.next() != null) {
                val de = TestUtil.docs(random(), te, null, PostingsEnum.NONE.toInt())
                while (de.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    visited.set(de.docID())
                }
            }
            assertEquals(visited.cardinality(), docCount)
        }
    }
}
