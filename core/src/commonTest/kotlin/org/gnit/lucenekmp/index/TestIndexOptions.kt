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

import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIndexOptions : LuceneTestCase() {

    @Test
    fun testChangeIndexOptionsViaAddDocument() {
        for (from in IndexOptions.entries) {
            for (to in IndexOptions.entries) {
                doTestChangeIndexOptionsViaAddDocument(from, to)
            }
        }
    }

    private fun doTestChangeIndexOptionsViaAddDocument(from: IndexOptions, to: IndexOptions) {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val ft1 = FieldType(TextField.TYPE_STORED)
        ft1.setIndexOptions(from)
        w.addDocument(mutableListOf(Field("foo", "bar", ft1)))
        val ft2 = FieldType(TextField.TYPE_STORED)
        ft2.setIndexOptions(to)
        if (from == to) {
            w.addDocument(mutableListOf(Field("foo", "bar", ft2))) // no exception
        } else {
            val e = expectThrows(IllegalArgumentException::class) {
                w.addDocument(mutableListOf(Field("foo", "bar", ft2)))
            }
            assertEquals(
                "Inconsistency of field data structures across documents for field [foo] of doc [1]." +
                    " index options: expected '$from', but it has '$to'.",
                e.message
            )
        }
        w.close()
        dir.close()
    }

    @Test
    fun testChangeIndexOptionsViaAddIndexesCodecReader() {
        for (from in IndexOptions.entries) {
            for (to in IndexOptions.entries) {
                doTestChangeIndexOptionsAddIndexesCodecReader(from, to)
            }
        }
    }

    private fun doTestChangeIndexOptionsAddIndexesCodecReader(from: IndexOptions, to: IndexOptions) {
        val dir1 = newDirectory()
        val w1 = IndexWriter(dir1, newIndexWriterConfig())
        val ft1 = FieldType(TextField.TYPE_STORED)
        ft1.setIndexOptions(from)
        w1.addDocument(mutableListOf(Field("foo", "bar", ft1)))

        val dir2 = newDirectory()
        val w2 = IndexWriter(dir2, newIndexWriterConfig())
        val ft2 = FieldType(TextField.TYPE_STORED)
        ft2.setIndexOptions(to)
        w2.addDocument(mutableListOf(Field("foo", "bar", ft2)))

        (getOnlyLeafReader(DirectoryReader.open(w2)) as CodecReader).use { cr ->
            if (from == to) {
                w1.addIndexes(cr) // no exception
                w1.forceMerge(1)
                getOnlyLeafReader(DirectoryReader.open(w1)).use { r ->
                    val expected = if (from == IndexOptions.NONE) to else from
                    assertEquals(expected, r.fieldInfos.fieldInfo("foo")!!.indexOptions)
                }
            } else {
                val e = expectThrows(IllegalArgumentException::class) {
                    w1.addIndexes(cr)
                }
                assertEquals(
                    "cannot change field \"foo\" from index options=$from to inconsistent index options=$to",
                    e.message
                )
            }
        }

        IOUtils.close(w1, w2, dir1, dir2)
    }

    @Test
    fun testChangeIndexOptionsViaAddIndexesDirectory() {
        for (from in IndexOptions.entries) {
            for (to in IndexOptions.entries) {
                doTestChangeIndexOptionsAddIndexesDirectory(from, to)
            }
        }
    }

    private fun doTestChangeIndexOptionsAddIndexesDirectory(from: IndexOptions, to: IndexOptions) {
        val dir1 = newDirectory()
        val w1 = IndexWriter(dir1, newIndexWriterConfig())
        val ft1 = FieldType(TextField.TYPE_STORED)
        ft1.setIndexOptions(from)
        w1.addDocument(mutableListOf(Field("foo", "bar", ft1)))

        val dir2 = newDirectory()
        val w2 = IndexWriter(dir2, newIndexWriterConfig())
        val ft2 = FieldType(TextField.TYPE_STORED)
        ft2.setIndexOptions(to)
        w2.addDocument(mutableListOf(Field("foo", "bar", ft2)))
        w2.close()

        if (from == to) {
            w1.addIndexes(dir2) // no exception
            w1.forceMerge(1)
            getOnlyLeafReader(DirectoryReader.open(w1)).use { r ->
                val expected = if (from == IndexOptions.NONE) to else from
                assertEquals(expected, r.fieldInfos.fieldInfo("foo")!!.indexOptions)
            }
        } else {
            val e = expectThrows(IllegalArgumentException::class) {
                w1.addIndexes(dir2)
            }
            assertEquals(
                "cannot change field \"foo\" from index options=$from to inconsistent index options=$to",
                e.message
            )
        }

        IOUtils.close(w1, dir1, dir2)
    }
}
