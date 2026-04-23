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

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * @lucene.experimental
 */
class TestOmitPositions : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testBasic() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        val f = newField("foo", "this is a test test", ft)
        doc.add(f)
        for (i in 0..<100) {
            w.addDocument(doc)
        }

        val reader = w.getReader(true, false)
        w.close()

        assertNotNull(MultiTerms.getTermPostingsEnum(reader, "foo", BytesRef("test")))

        val de =
            TestUtil.docs(
                random(),
                reader,
                "foo",
                BytesRef("test"),
                null,
                PostingsEnum.FREQS.toInt(),
            )!!
        while (de.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            assertEquals(2, de.freq())
        }

        reader.close()
        dir.close()
    }

    // Tests whether the DocumentWriter correctly enable the
    // omitTermFreqAndPositions bit in the FieldInfo
    @Test
    @Throws(Exception::class)
    fun testPositions() {
        val ram = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val writer = IndexWriter(ram, newIndexWriterConfig(analyzer))
        val d = Document()

        // f1: docs only
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS)

        val f1 = newField("f1", "This field has docs only", ft)
        d.add(f1)

        val ft2 = FieldType(TextField.TYPE_NOT_STORED)
        ft2.setIndexOptions(IndexOptions.DOCS_AND_FREQS)

        // f2: docs and freqs
        val f2 = newField("f2", "This field has docs and freqs", ft2)
        d.add(f2)

        val ft3 = FieldType(TextField.TYPE_NOT_STORED)
        ft3.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)

        // f3: docs/freqs/positions
        val f3 = newField("f3", "This field has docs and freqs and positions", ft3)
        d.add(f3)

        writer.addDocument(d)
        writer.forceMerge(1)
        // flush
        writer.close()

        val reader = getOnlyLeafReader(DirectoryReader.open(ram))
        val fi = reader.fieldInfos
        // docs + docs = docs
        assertEquals(IndexOptions.DOCS, fi.fieldInfo("f1")!!.indexOptions)
        // docs/freqs + docs/freqs = docs/freqs
        assertEquals(IndexOptions.DOCS_AND_FREQS, fi.fieldInfo("f2")!!.indexOptions)
        // docs/freqs/pos + docs/freqs/pos = docs/freqs/pos
        assertEquals(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, fi.fieldInfo("f3")!!.indexOptions)

        reader.close()
        ram.close()
    }

    @Throws(Throwable::class)
    private fun assertNoPrx(dir: Directory) {
        val files = dir.listAll()
        for (i in files.indices) {
            assertFalse(files[i].endsWith(".prx"))
            assertFalse(files[i].endsWith(".pos"))
        }
    }

    // Verifies no *.prx exists when all fields omit term positions:
    @Test
    @Throws(Throwable::class)
    fun testNoPrxFile() {
        val ram = newDirectory()

        val analyzer: Analyzer = MockAnalyzer(random())
        val writer =
            IndexWriter(
                ram,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(3)
                    .setMergePolicy(newLogMergePolicy()),
            )
        val lmp = writer.config.mergePolicy as LogMergePolicy
        lmp.mergeFactor = 2
        lmp.noCFSRatio = 0.0
        val d = Document()

        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        val f1 = newField("f1", "This field has term freqs", ft)
        d.add(f1)

        for (i in 0..<30) writer.addDocument(d)

        writer.commit()

        assertNoPrx(ram)

        writer.close()
        ram.close()
    }
}
