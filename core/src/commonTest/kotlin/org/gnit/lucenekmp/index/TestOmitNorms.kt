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
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestOmitNorms : LuceneTestCase() {

    // Tests that merging of docs with different omitNorms throws error
    @Test
    @Throws(Exception::class)
    fun testMixedMergeThrowsError() {
        val ram = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val writer =
            IndexWriter(
                ram,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(3)
                    .setMergePolicy(newLogMergePolicy(2)),
            )
        val d = Document()

        // this field will have norms
        val fieldType1 = FieldType(TextField.TYPE_NOT_STORED)
        fieldType1.setOmitNorms(false)
        fieldType1.setStoreTermVectors(false)
        val f1 = Field("f1", "This field has norms", fieldType1)
        d.add(f1)

        // this field will NOT have norms
        val fieldType2 = FieldType(TextField.TYPE_NOT_STORED)
        fieldType2.setOmitNorms(true)
        fieldType2.setStoreTermVectors(false)
        val f2 = Field("f2", "This field has NO norms in all docs", fieldType2)
        d.add(f2)

        for (i in 0..<30) {
            writer.addDocument(d)
        }

        // reverse omitNorms options for f1 and f2
        val d2 = Document()
        d2.add(Field("f1", "This field has NO norms", fieldType2))
        d2.add(Field("f2", "This field has norms", fieldType1))

        val exception =
            expectThrows(IllegalArgumentException::class) {
                writer.addDocument(d2)
            }
        assertEquals(
            "cannot change field \"f1\" from omitNorms=false to inconsistent omitNorms=true",
            exception.message,
        )

        writer.forceMerge(1)
        writer.close()

        val reader = getOnlyLeafReader(DirectoryReader.open(ram))
        val fi = reader.fieldInfos
        // assert original omitNorms
        assertTrue(!fi.fieldInfo("f1")!!.omitsNorms(), "OmitNorms field bit must not be set.")
        assertTrue(fi.fieldInfo("f2")!!.omitsNorms(), "OmitNorms field bit must be set.")

        reader.close()
        ram.close()
    }

    // Make sure first adding docs that do not omitNorms for
    // field X, then adding docs that do omitNorms for that same
    // field,
    @Test
    @Throws(Exception::class)
    fun testMixedRAM() {
        val ram = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val writer =
            IndexWriter(
                ram,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(newLogMergePolicy(2)),
            )
        val d = Document()

        // this field will have norms
        val f1 = newTextField("f1", "This field has norms", Field.Store.NO)
        d.add(f1)

        // this field will NOT have norms

        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setOmitNorms(true)
        val f2 = newField("f2", "This field has NO norms in all docs", customType)
        d.add(f2)

        for (i in 0..<5) {
            writer.addDocument(d)
        }

        for (i in 0..<20) {
            writer.addDocument(d)
        }

        // force merge
        writer.forceMerge(1)

        // flush
        writer.close()

        val reader = getOnlyLeafReader(DirectoryReader.open(ram))
        val fi = reader.fieldInfos
        assertTrue(!fi.fieldInfo("f1")!!.omitsNorms(), "OmitNorms field bit should not be set.")
        assertTrue(fi.fieldInfo("f2")!!.omitsNorms(), "OmitNorms field bit should be set.")

        reader.close()
        ram.close()
    }

    @Throws(Throwable::class)
    private fun assertNoNrm(dir: Directory) {
        val files = dir.listAll()
        for (i in files.indices) {
            // TODO: this relies upon filenames
            assertFalse(files[i].endsWith(".nrm") || files[i].endsWith(".len"))
        }
    }

    // Verifies no *.nrm exists when all fields omit norms:
    @Test
    @Throws(Throwable::class)
    fun testNoNrmFile() {
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

        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setOmitNorms(true)
        val f1 = newField("f1", "This field has no norms", customType)
        d.add(f1)

        for (i in 0..<30) {
            writer.addDocument(d)
        }

        writer.commit()

        assertNoNrm(ram)

        // force merge
        writer.forceMerge(1)
        // flush
        writer.close()

        assertNoNrm(ram)
        ram.close()
    }
}
