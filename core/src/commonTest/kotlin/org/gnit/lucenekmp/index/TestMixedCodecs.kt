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

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.simpletext.SimpleTextCodec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMixedCodecs : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun test() {
        val NUM_DOCS = atLeast(1000)

        val dir: Directory = newDirectory()
        var w: RandomIndexWriter? = null

        var docsLeftInThisSegment = 0

        var docUpto = 0
        while (docUpto < NUM_DOCS) {
            if (VERBOSE) {
                println("TEST: $docUpto of $NUM_DOCS")
            }
            if (docsLeftInThisSegment == 0) {
                val iwc = newIndexWriterConfig(MockAnalyzer(random()))
                if (random().nextBoolean()) {
                    // Make sure we aggressively mix in SimpleText
                    // since it has different impls for all codec
                    // formats...
                    // KMP workaround: ServiceLoader/ClassLoader is unavailable, so force codec registration.
                    SimpleTextCodec()
                    iwc.setCodec(Codec.forName("SimpleText"))
                }
                if (w != null) {
                    w.close()
                }
                w = RandomIndexWriter(random(), dir, iwc)
                docsLeftInThisSegment = TestUtil.nextInt(random(), 10, 100)
            }
            val doc = Document()
            doc.add(newStringField("id", "$docUpto", Field.Store.YES))
            w!!.addDocument(doc)
            docUpto++
            docsLeftInThisSegment--
        }

        if (VERBOSE) {
            println("\nTEST: now delete...")
        }

        // Random delete half the docs:
        val deleted = HashSet<Int>()
        while (deleted.size < NUM_DOCS / 2) {
            val toDelete = random().nextInt(NUM_DOCS)
            if (deleted.contains(toDelete).not()) {
                deleted.add(toDelete)
                w!!.deleteDocuments(Term("id", "$toDelete"))
                if (random().nextInt(17) == 6) {
                    val r = w.getReader(applyDeletions = true, writeAllDeletes = false)
                    assertEquals(NUM_DOCS - deleted.size, r.numDocs())
                    r.close()
                }
            }
        }

        w!!.close()
        dir.close()
    }
}
