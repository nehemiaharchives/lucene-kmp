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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** Test indexing and searching some byte[] terms */
class TestBinaryTerms : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testBinary() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val bytes = BytesRef(2)

        for (i in 0..<256) {
            bytes.bytes[0] = i.toByte()
            bytes.bytes[1] = (255 - i).toByte()
            bytes.length = 2
            val doc = Document()
            val customType = FieldType()
            customType.setStored(true)
            doc.add(newField("id", "$i", customType))
            doc.add(newStringField("bytes", bytes, Field.Store.NO))
            iw.addDocument(doc)
        }

        val ir = iw.reader
        iw.close()

        val `is`: IndexSearcher = newSearcher(ir)

        for (i in 0..<256) {
            bytes.bytes[0] = i.toByte()
            bytes.bytes[1] = (255 - i).toByte()
            bytes.length = 2
            val docs: TopDocs = `is`.search(TermQuery(Term("bytes", bytes)), 5)
            assertEquals(1, docs.totalHits.value)
            assertEquals("$i", `is`.storedFields().document(docs.scoreDocs[0].doc).get("id"))
        }

        ir.close()
        dir.close()
    }

    @Test
    fun testToString() {
        val term = Term("foo", BytesRef(byteArrayOf(0xff.toByte(), 0xfe.toByte())))
        assertEquals("foo:[ff fe]", term.toString())
    }
}
