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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestIsCurrent : LuceneTestCase() {
    private lateinit var writer: RandomIndexWriter
    private lateinit var directory: Directory

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        // initialize directory
        directory = newDirectory()
        writer = RandomIndexWriter(random(), directory)

        // write document
        val doc = Document()
        doc.add(newTextField("UUID", "1", Field.Store.YES))
        writer.addDocument(doc)
        writer.commit()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        writer.close()
        directory.close()
    }

    /** Failing testcase showing the trouble */
    @Test
    @Throws(Exception::class)
    fun testDeleteByTermIsCurrent() {
        // get reader
        val reader = writer.reader

        // assert index has a document and reader is up2date
        assertEquals(1, writer.docStats.numDocs, "One document should be in the index")
        assertTrue(reader.isCurrent, "One document added, reader should be current")

        // remove document
        val idTerm = Term("UUID", "1")
        writer.deleteDocuments(idTerm)
        writer.commit()

        // assert document has been deleted (index changed), reader is stale
        assertEquals(0, writer.docStats.numDocs, "Document should be removed")
        assertFalse(reader.isCurrent, "Reader should be stale")

        reader.close()
    }

    /** Testcase for example to show that writer.deleteAll() is working as expected */
    @Test
    @Throws(Exception::class)
    fun testDeleteAllIsCurrent() {
        // get reader
        val reader = writer.reader

        // assert index has a document and reader is up2date
        assertEquals(1, writer.docStats.numDocs, "One document should be in the index")
        assertTrue(reader.isCurrent, "Document added, reader should be stale ")

        // remove all documents
        writer.deleteAll()
        writer.commit()

        // assert document has been deleted (index changed), reader is stale
        assertEquals(0, writer.docStats.numDocs, "Document should be removed")
        assertFalse(reader.isCurrent, "Reader should be stale")

        reader.close()
    }
}
