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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.index.DocHelper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Version
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestSegmentReader : LuceneTestCase() {
    private lateinit var dir: Directory
    private val testDoc = Document()
    private var reader: SegmentReader? = null

    // TODO: Setup the reader w/ multiple documents
    @BeforeTest
    fun setUp() {
        dir = newDirectory()
        DocHelper.setupDoc(testDoc)
        val info = DocHelper.writeDoc(random(), dir, testDoc)
        reader = SegmentReader(info, Version.LATEST.major, IOContext.DEFAULT)
    }

    @AfterTest
    fun tearDown() {
        reader?.close()
        dir.close()
    }

    @Test
    fun test() {
        assertTrue(this::dir.isInitialized)
        assertNotNull(reader)
        assertTrue(DocHelper.nameValues.isNotEmpty())
        assertEquals(DocHelper.all.size, DocHelper.numFields(testDoc))
    }

    @Test
    fun testDocument() {
        val reader = requireNotNull(reader)
        assertEquals(1, reader.numDocs())
        assertTrue(reader.maxDoc() >= 1)
        val result = reader.storedFields().document(0)
        assertNotNull(result)
        // There are 2 unstored fields on the document that are not preserved across writing
        assertEquals(DocHelper.numFields(testDoc) - DocHelper.unstored.size, DocHelper.numFields(result))

        val fields = result.getFields()
        for (field in fields) {
            assertNotNull(field)
            assertTrue(DocHelper.nameValues.containsKey(field.name()))
        }
    }

    @Test
    fun testGetFieldNameVariations() {
        val reader = requireNotNull(reader)
        val allFieldNames: MutableCollection<String> = HashSet()
        val indexedFieldNames: MutableCollection<String> = HashSet()
        val notIndexedFieldNames: MutableCollection<String> = HashSet()
        val tvFieldNames: MutableCollection<String> = HashSet()
        val noTVFieldNames: MutableCollection<String> = HashSet()

        for (fieldInfo in reader.fieldInfos) {
            val name = fieldInfo.name
            allFieldNames.add(name)
            if (fieldInfo.indexOptions != IndexOptions.NONE) {
                indexedFieldNames.add(name)
            } else {
                notIndexedFieldNames.add(name)
            }
            if (fieldInfo.hasTermVectors()) {
                tvFieldNames.add(name)
            } else if (fieldInfo.indexOptions != IndexOptions.NONE) {
                noTVFieldNames.add(name)
            }
        }

        assertEquals(DocHelper.all.size, allFieldNames.size)
        for (s in allFieldNames) {
            assertTrue(DocHelper.nameValues.containsKey(s) || s.isEmpty())
        }

        assertEquals(DocHelper.indexed.size, indexedFieldNames.size)
        for (s in indexedFieldNames) {
            assertTrue(DocHelper.indexed.containsKey(s) || s.isEmpty())
        }

        assertEquals(DocHelper.unindexed.size, notIndexedFieldNames.size)
        // Get all indexed fields that are storing term vectors
        assertEquals(DocHelper.termvector.size, tvFieldNames.size)

        assertEquals(DocHelper.notermvector.size, noTVFieldNames.size)
    }

    @Test
    fun testTerms() {
        val reader = requireNotNull(reader)
        val fields = FieldInfos.getIndexedFields(reader)
        for (field in fields) {
            val terms = MultiTerms.getTerms(reader, field)
            assertNotNull(terms)
            val termsEnum = terms.iterator()
            while (termsEnum.next() != null) {
                val term = requireNotNull(termsEnum.term())
                assertNotNull(term)
                val fieldValue = DocHelper.nameValues[field] as String
                assertTrue(fieldValue.indexOf(term.utf8ToString()) != -1)
            }
        }

        var termDocs =
            TestUtil.docs(random(), reader, DocHelper.TEXT_FIELD_1_KEY, BytesRef("field"), null, 0)
        assertNotNull(termDocs)
        assertTrue(termDocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)

        termDocs =
            TestUtil.docs(
                random(),
                reader,
                DocHelper.NO_NORMS_KEY,
                BytesRef(DocHelper.NO_NORMS_TEXT),
                null,
                0,
            )

        assertNotNull(termDocs)
        assertTrue(termDocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)

        val positions = requireNotNull(MultiTerms.getTermPostingsEnum(reader, DocHelper.TEXT_FIELD_1_KEY, BytesRef("field")))
        // NOTE: prior rev of this test was failing to first
        // call next here:
        assertTrue(positions.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(0, positions.docID())
        assertTrue(positions.nextPosition() >= 0)
    }

    @Test
    fun testNorms() {
        // TODO: Not sure how these work/should be tested
        /*
            try {
              byte [] norms = reader.norms(DocHelper.TEXT_FIELD_1_KEY);
              System.out.println("Norms: " + norms);
              assertTrue(norms != null);
            } catch (IOException e) {
              e.printStackTrace();
              assertTrue(false);
            }
        */

        checkNorms(requireNotNull(reader))
    }

    @Test
    fun testTermVectors() {
        val reader = requireNotNull(reader)
        val result = requireNotNull(requireNotNull(reader.termVectors().get(0)).terms(DocHelper.TEXT_FIELD_2_KEY))
        assertNotNull(result)
        assertEquals(3L, result.size())
        val termsEnum = result.iterator()
        while (termsEnum.next() != null) {
            val term = requireNotNull(termsEnum.term()).utf8ToString()
            val freq = termsEnum.totalTermFreq().toInt()
            assertTrue(DocHelper.FIELD_2_TEXT.indexOf(term) != -1)
            assertTrue(freq > 0)
        }

        val results = reader.termVectors().get(0)
        assertNotNull(results)
        assertEquals(3, results.size(), "We do not have 3 term freq vectors")
    }

    @Test
    fun testOutOfBoundsAccess() {
        val reader = requireNotNull(reader)
        val numDocs = reader.maxDoc()

        expectThrows(IndexOutOfBoundsException::class) {
            reader.storedFields().document(-1)
        }

        expectThrows(IndexOutOfBoundsException::class) {
            reader.termVectors().get(-1)
        }

        expectThrows(IndexOutOfBoundsException::class) {
            reader.storedFields().document(numDocs)
        }

        expectThrows(IndexOutOfBoundsException::class) {
            reader.termVectors().get(numDocs)
        }
    }

    companion object {
        fun checkNorms(reader: LeafReader) {
            // test omit norms
            for (i in DocHelper.fields.indices) {
                val f = DocHelper.fields[i]!!
                if (f.fieldType().indexOptions() != IndexOptions.NONE) {
                    assertEquals(reader.getNormValues(f.name()) != null, !f.fieldType().omitNorms())
                    assertEquals(reader.getNormValues(f.name()) != null, !DocHelper.noNorms.containsKey(f.name()))
                    if (reader.getNormValues(f.name()) == null) {
                        // test for norms of null
                        val norms = MultiDocValues.getNormValues(reader, f.name())
                        assertNull(norms)
                    }
                }
            }
        }
    }
}

