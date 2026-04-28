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
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests [Terms.sumDocFreq]
 *
 * @lucene.experimental
 */
class TestSumDocFreq : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testSumDocFreq() {
        val numDocs = atLeast(500)

        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        val doc = Document()
        val id = newStringField("id", "", Field.Store.NO)
        val field1 = newTextField("foo", "", Field.Store.NO)
        val field2 = newTextField("bar", "", Field.Store.NO)
        doc.add(id)
        doc.add(field1)
        doc.add(field2)
        for (i in 0..<numDocs) {
            id.setStringValue("$i")
            var ch1 = TestUtil.nextInt(random(), 'a'.code, 'z'.code).toChar()
            var ch2 = TestUtil.nextInt(random(), 'a'.code, 'z'.code).toChar()
            field1.setStringValue("$ch1 $ch2")
            ch1 = TestUtil.nextInt(random(), 'a'.code, 'z'.code).toChar()
            ch2 = TestUtil.nextInt(random(), 'a'.code, 'z'.code).toChar()
            field2.setStringValue("$ch1 $ch2")
            writer.addDocument(doc)
        }

        var ir: IndexReader = writer.reader

        assertSumDocFreq(ir)
        ir.close()

        val numDeletions = atLeast(20)
        for (i in 0..<numDeletions) {
            writer.deleteDocuments(Term("id", "${random().nextInt(numDocs)}"))
        }
        writer.forceMerge(1)
        writer.close()

        ir = DirectoryReader.open(dir)
        assertSumDocFreq(ir)
        ir.close()
        dir.close()
    }

    @Throws(Exception::class)
    private fun assertSumDocFreq(ir: IndexReader) {
        // compute sumDocFreq across all fields
        val fields = FieldInfos.getIndexedFields(ir)
        for (f in fields) {
            val terms = checkNotNull(MultiTerms.getTerms(ir, f))
            val sumDocFreq = terms.sumDocFreq
            if (sumDocFreq == -1L) {
                if (VERBOSE) {
                    println("skipping field: $f, codec does not support sumDocFreq")
                }
                continue
            }

            var computedSumDocFreq = 0L
            val termsEnum = terms.iterator()
            while (termsEnum.next() != null) {
                computedSumDocFreq += termsEnum.docFreq().toLong()
            }
            assertEquals(computedSumDocFreq, sumDocFreq)
        }
    }
}

