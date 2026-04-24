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

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestPayloadsOnVectors : LuceneTestCase() {

    /** some docs have payload att, some not */
    @Test
    fun testMixupDocs() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setMergePolicy(newLogMergePolicy())
        val writer = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorPayloads(true)
        customType.setStoreTermVectorOffsets(random().nextBoolean())
        var ts: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, true)
        (ts as Tokenizer).setReader(StringReader("here we go"))
        val field = Field("field", ts, customType)
        doc.add(field)
        writer.addDocument(doc)

        val withPayload = Token("withPayload", 0, 11)
        withPayload.payload = BytesRef("test")
        ts = CannedTokenStream(withPayload)
        assertTrue(ts.hasAttribute(PayloadAttribute::class))
        field.setTokenStream(ts)
        writer.addDocument(doc)

        ts = MockTokenizer(MockTokenizer.WHITESPACE, true)
        (ts as Tokenizer).setReader(StringReader("another"))
        field.setTokenStream(ts)
        writer.addDocument(doc)

        val reader = writer.reader
        val terms = reader.termVectors().get(1, "field")
        assert(terms != null)
        val termsEnum = terms!!.iterator()
        assertTrue(termsEnum.seekExact(BytesRef("withPayload")))
        val de = termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
        assertEquals(0, de.nextDoc())
        assertEquals(0, de.nextPosition())
        assertEquals(BytesRef("test"), de.payload)
        writer.close()
        reader.close()
        dir.close()
    }

    /** some field instances have payload att, some not */
    @Test
    fun testMixupMultiValued() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorPayloads(true)
        customType.setStoreTermVectorOffsets(random().nextBoolean())
        var ts: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, true)
        (ts as Tokenizer).setReader(StringReader("here we go"))
        val field = Field("field", ts, customType)
        doc.add(field)
        val withPayload = Token("withPayload", 0, 11)
        withPayload.payload = BytesRef("test")
        ts = CannedTokenStream(withPayload)
        assertTrue(ts.hasAttribute(PayloadAttribute::class))
        val field2 = Field("field", ts, customType)
        doc.add(field2)
        ts = MockTokenizer(MockTokenizer.WHITESPACE, true)
        (ts as Tokenizer).setReader(StringReader("nopayload"))
        val field3 = Field("field", ts, customType)
        doc.add(field3)
        writer.addDocument(doc)
        val reader = writer.reader
        val terms = reader.termVectors().get(0, "field")
        assert(terms != null)
        val termsEnum = terms!!.iterator()
        assertTrue(termsEnum.seekExact(BytesRef("withPayload")))
        val de = termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
        assertEquals(0, de.nextDoc())
        assertEquals(3, de.nextPosition())
        assertEquals(BytesRef("test"), de.payload)
        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    fun testPayloadsWithoutPositions() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(false)
        customType.setStoreTermVectorPayloads(true)
        customType.setStoreTermVectorOffsets(random().nextBoolean())
        doc.add(Field("field", "foo", customType))

        expectThrows(IllegalArgumentException::class) {
            writer.addDocument(doc)
        }

        writer.close()
        dir.close()
    }
}
