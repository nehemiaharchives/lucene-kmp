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
package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.CheckIndex
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.LockObtainFailedException
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Base class for CheckIndex tests. */
open class BaseTestCheckIndex : LuceneTestCase() {
    protected open fun getDirectory(): Directory {
        throw UnsupportedOperationException("subclass must provide directory")
    }

    @Throws(IOException::class)
    open fun testDeletedDocs() {
        testDeletedDocs(getDirectory())
    }

    @Throws(IOException::class)
    open fun testDeletedDocs(dir: Directory) {
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2)
            )
        for (i in 0 until 19) {
            val doc = Document()
            val customType = FieldType(TextField.TYPE_STORED)
            customType.setStoreTermVectors(true)
            customType.setStoreTermVectorPositions(true)
            customType.setStoreTermVectorOffsets(true)
            doc.add(newField("field", "aaa$i", customType))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        writer.commit()
        writer.deleteDocuments(Term("field", "aaa5"))
        writer.close()

        val bos = ByteArrayOutputStream(1024)
        val checker = CheckIndex(dir)
        checker.setInfoStream(PrintStream(bos, false, StandardCharsets.UTF_8))
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS)
        val indexStatus = checker.checkIndex()
        if (indexStatus.clean == false) {
            println("CheckIndex failed")
            println(bos.toString(StandardCharsets.UTF_8))
            fail()
        }

        val seg = indexStatus.segmentInfos[0]
        assertTrue(seg.openReaderPassed)

        assertNotNull(seg.diagnostics)

        val fieldNormStatus = requireNotNull(seg.fieldNormStatus)
        assertNull(fieldNormStatus.error)
        assertEquals(1L, fieldNormStatus.totFields)

        val termIndexStatus = requireNotNull(seg.termIndexStatus)
        assertNull(termIndexStatus.error)
        assertEquals(18L, termIndexStatus.termCount)
        assertEquals(18L, termIndexStatus.totFreq)
        assertEquals(18L, termIndexStatus.totPos)

        val storedFieldStatus = requireNotNull(seg.storedFieldStatus)
        assertNull(storedFieldStatus.error)
        assertEquals(18, storedFieldStatus.docCount)
        assertEquals(18L, storedFieldStatus.totFields)

        val termVectorStatus = requireNotNull(seg.termVectorStatus)
        assertNull(termVectorStatus.error)
        assertEquals(18, termVectorStatus.docCount)
        assertEquals(18L, termVectorStatus.totVectors)

        val diagnostics = requireNotNull(seg.diagnostics)
        assertNotNull(diagnostics["java.runtime.version"])

        assertTrue(diagnostics.isNotEmpty())
        val onlySegments = mutableListOf<String>()
        onlySegments.add("_0")

        assertTrue(checker.checkIndex(onlySegments).clean)
        checker.close()
    }

    @Throws(IOException::class)
    open fun testChecksumsOnly() {
        testChecksumsOnly(getDirectory())
    }

    @Throws(IOException::class)
    open fun testChecksumsOnly(dir: Directory) {
        val lf = LineFileDocs(random())
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val iw = IndexWriter(dir, newIndexWriterConfig(analyzer))
        for (i in 0 until 100) {
            iw.addDocument(lf.nextDoc())
        }
        iw.addDocument(Document())
        iw.commit()
        iw.close()
        lf.close()

        val bos = ByteArrayOutputStream(1024)
        val checker = CheckIndex(dir)
        checker.setInfoStream(PrintStream(bos, false, StandardCharsets.UTF_8))
        val indexStatus = checker.checkIndex()
        assertTrue(indexStatus.clean)
        checker.close()
        analyzer.close()
    }

    @Throws(IOException::class)
    open fun testChecksumsOnlyVerbose() {
        testChecksumsOnlyVerbose(getDirectory())
    }

    @Throws(IOException::class)
    open fun testChecksumsOnlyVerbose(dir: Directory) {
        val lf = LineFileDocs(random())
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val iw = IndexWriter(dir, newIndexWriterConfig(analyzer))
        for (i in 0 until 100) {
            iw.addDocument(lf.nextDoc())
        }
        iw.addDocument(Document())
        iw.commit()
        iw.close()
        lf.close()

        val bos = ByteArrayOutputStream(1024)
        val checker = CheckIndex(dir)
        checker.setInfoStream(PrintStream(bos, true, StandardCharsets.UTF_8))
        val indexStatus = checker.checkIndex()
        assertTrue(indexStatus.clean)
        checker.close()
        analyzer.close()
    }

    @Throws(IOException::class)
    open fun testObtainsLock() {
        testObtainsLock(getDirectory())
    }

    @Throws(IOException::class)
    open fun testObtainsLock(dir: Directory) {
        val iw = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
        iw.addDocument(Document())
        iw.commit()

        // keep IW open... should not be able to obtain write lock
        expectThrows(LockObtainFailedException::class) { CheckIndex(dir) }

        iw.close()
    }
}
