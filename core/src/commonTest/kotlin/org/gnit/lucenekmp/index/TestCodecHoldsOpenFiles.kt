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
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestCodecHoldsOpenFiles : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun test() {
        val d: BaseDirectoryWrapper = newDirectory()
        d.checkIndexOnClose = false
        // we nuke files, but verify the reader still works
        val w = RandomIndexWriter(random(), d)
        val numDocs = atLeast(100)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(newField("foo", "bar", TextField.TYPE_NOT_STORED))
            doc.add(IntPoint("doc", i))
            doc.add(IntPoint("doc2d", i, i))
            doc.add(NumericDocValuesField("dv", i.toLong()))
            w.addDocument(doc)
        }

        val r = w.reader
        w.commit()
        w.close()

        for (name in d.listAll()) {
            d.deleteFile(name)
        }

        for (cxt in r.leaves()) {
            TestUtil.checkReader(cxt.reader())
        }

        r.close()
        d.close()
    }
}
